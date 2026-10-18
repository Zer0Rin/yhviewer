package com.example.yhviewer

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

sealed interface Screen {
    data object Shelf : Screen
    data object Reader : Screen
}

sealed interface ReaderState {
    data object Loading : ReaderState
    data class Ready(val doc: OpenedDoc) : ReaderState
    data class Error(val message: String) : ReaderState
}

class ShelfViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ShelfRepository(app, AppDatabase.get(app))
    private val themeStore = ThemeStore(app)
    private val readingStore = ReadingPrefsStore(app)
    private val prefs = app.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)

    /** 启动时先清理孤儿文件；导入要等它做完，避免误删正在写入的新副本 */
    private val startup = viewModelScope.async {
        try { repo.cleanOrphans(); DocProfiles.cleanup(app, repo.idsWithPersistentStorage()); null }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { e }
    }

    val covers: CoverStore get() = repo.covers

    init {
        viewModelScope.launch {
            // A failed cleanup must remain visible to open/import and must not trigger metadata work.
            if (startup.await() == null) {
                for (backfill in listOf<suspend () -> Int>(repo::backfillKinds, repo::backfillCovers)) {
                    try { backfill() }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { Log.w("ShelfMetadata", "Unable to backfill shelf metadata", e) }
                }
            }
        }
    }

    // ---------------- 偏好 ----------------
    var theme by mutableStateOf(themeStore.load())
        private set
    var wrap by mutableStateOf(prefs.getBoolean(KEY_WRAP, false))
        private set

    var reading by mutableStateOf(readingStore.load())
        private set

    fun updateReading(value: ReadingPrefs) {
        reading = value.normalized()
        readingStore.save(reading)
    }

    fun selectTheme(t: ReaderTheme) { theme = t; themeStore.save(t) }
    fun toggleWrap() { wrap = !wrap; prefs.edit().putBoolean(KEY_WRAP, wrap).apply() }

    // ---------------- 书架 ----------------
    var query by mutableStateOf("")
        private set

    @OptIn(ExperimentalCoroutinesApi::class)
    val books: StateFlow<List<Book>?> =
        snapshotFlow { query }
            .flatMapLatest { q -> repo.observe(q, false) }
            .stateIn<List<Book>?>(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun updateQuery(q: String) { query = q }
    fun toggleFavorite(book: Book) { viewModelScope.launch { repo.setFavorite(book.id, !book.favorite) } }
    fun delete(book: Book) { viewModelScope.launch {
        DocProfiles.revoke(getApplication(), book.id)
        repo.delete(book.id)
    } }

    val groups: StateFlow<List<ShelfGroup>?> = repo.observeGroups()
        .stateIn<List<ShelfGroup>?>(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private suspend fun <T> shelfResult(block: suspend () -> T): Result<T> = try {
        startup.await()?.let { throw it }
        Result.success(block())
    } catch (e: CancellationException) { throw e }
      catch (e: Exception) { Result.failure(e) }

    suspend fun createGroup(name: String, moveIds: List<Long> = emptyList()): Result<Long> = shelfResult {
        repo.createGroupAndMove(name, moveIds)
    }

    suspend fun renameGroup(id: Long, name: String): Result<Unit> = shelfResult { repo.renameGroup(id, name) }
    suspend fun groupMemberCount(id: Long): Result<Int> = shelfResult {
        repo.observe("", false).first().count { it.groupId == id }
    }

    suspend fun deleteGroup(id: Long): Result<Unit> = shelfResult { repo.deleteGroup(id) }
    suspend fun moveToGroup(ids: List<Long>, groupId: Long?): Result<Unit> = shelfResult { repo.moveToGroup(ids, groupId) }
    suspend fun setFavorite(ids: List<Long>, favorite: Boolean): Result<Unit> = shelfResult { repo.setFavorite(ids, favorite) }
    suspend fun deleteMany(books: List<Book>): Result<Unit> = shelfResult {
        deleteShelfItems(books.map { it.id }, delete = repo::delete, revoke = { id ->
            withContext(Dispatchers.Main.immediate) { DocProfiles.revoke(getApplication(), id) }
        })
    }

    fun setPermissions(bookId: Long, permissions: DocPermissions) {
        viewModelScope.launch {
            val bounded = AppCapabilities.effective(permissions)
            // Invalidate storage before suspension so quick regrant cannot reuse a revoked profile.
            if (!bounded.persistentStorage || !bounded.scripts) DocProfiles.revoke(getApplication(), bookId)
            repo.setPermissions(bookId, bounded)
            val cur = current()
            if (cur is OpenedDoc.Single && cur.bookId == bookId) {
                flushPosition()
                documentSession++ // stale callbacks from the previous permissions cannot overwrite position
                reader = ReaderState.Ready(cur.copy(permissions = bounded))
            }
        }
    }

    // ---------------- 导航 + 阅读 ----------------
    var screen by mutableStateOf<Screen>(Screen.Shelf)
        private set
    var reader by mutableStateOf<ReaderState>(ReaderState.Loading)
        private set

    /** 当前章节（单页文档恒为 0） */
    var chapter by mutableIntStateOf(0)
        private set

    var documentSession by mutableIntStateOf(0)
        private set
    var chapterAnchor by mutableStateOf<String?>(null)
        private set

    // 最新滚动比例。刻意不用 Compose state：每次滚动都触发重组没有意义，只在切章或重建 WebView 时读一次
    private var ratio = 0f
    private var loadJob: Job? = null
    private var saveJob: Job? = null

    fun onRendererGone(forSession: Int, forChapter: Int) {
        if (forSession != documentSession || forChapter != chapter || current() == null) return
        flushPosition()
        reader = ReaderState.Error(RENDERER_GONE_MESSAGE)
    }

    fun initialRatio(): Float = ratio

    // ---------------- v10：多选导入、文件夹导入 ----------------

    /** 正在导入时不为 null，书架底部显示进度 */
    var importProgress by mutableStateOf<ImportProgress?>(null)
        private set
    /** 导入结束后的报告，用户关闭前一直显示 */
    var importReport by mutableStateOf<ImportReport?>(null)
        private set
    private var importJob: Job? = null

    fun dismissImportReport() { importReport = null }

    /** 多选：只选了一个文件时和以前一样直接打开；多个文件留在书架，导入完显示报告 */
    fun importMany(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (uris.size == 1) return importAndOpen(uris[0])
        runImport { onProgress -> repo.importMany(uris, onProgress).first }
    }

    fun importFolder(treeUri: Uri) = runImport { onProgress -> repo.importFolder(treeUri, onProgress) }

    private fun runImport(block: suspend ((ImportProgress) -> Unit) -> ImportReport) {
        if (importJob?.isActive == true) return
        importReport = null
        importProgress = ImportProgress(0, 0, "")
        importJob = viewModelScope.launch {
            importReport = try {
                startup.await()?.let { throw it }
                // 进度回调可能来自 IO 线程：切回主线程再写 Compose 状态
                block { p -> viewModelScope.launch { if (importJob?.isActive == true) importProgress = p } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ImportReport(0, listOf("导入" to (e.message ?: e.javaClass.simpleName)), emptyList())
            } finally {
                importProgress = null
            }
        }
    }

    // ---------------- v10：书签、跳转、搜索、阅读页提示 ----------------

    /** 书签跳转到当前章时，章节号不变：用这个计数让阅读页以新的比例重建 WebView */
    var jumpTick by mutableIntStateOf(0)
        private set

    /** 跳到指定章节和章内比例（书签、搜索结果） */
    fun jumpTo(index: Int, r: Float) {
        val doc = current() ?: return
        if (index !in 0 until doc.chapterCount) return
        chapterAnchor = null
        chapter = index
        ratio = if (r.isFinite()) r.coerceIn(0f, 1f) else 0f
        jumpTick++
        flushPosition()
    }

    fun bookmarks(bookId: Long) = repo.observeBookmarks(bookId)

    /** Feedback is delivered only from the persisted transaction result. */
    fun addBookmark(chapterTitle: String?, onResult: (Result<Boolean>) -> Unit) {
        val doc = current() ?: run {
            onResult(Result.failure(IOException("当前没有打开的文档")))
            return
        }
        val b = Bookmark(bookId = doc.bookId, chapter = chapter, ratio = ratio,
            label = BookmarkRules.label(chapterTitle, doc.name, ratio), createdAt = 0)
        viewModelScope.launch {
            val result = try { Result.success(repo.addBookmark(b)) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { Result.failure(e) }
            onResult(result)
        }
    }

    fun deleteBookmark(id: Long) { viewModelScope.launch { runCatching { repo.deleteBookmark(id) } } }

    /** 搜索结果跳转后，新页面加载完要高亮的词，以及要跳到本章第几个命中 */
    var pendingFind by mutableStateOf<Pair<String, Int>?>(null)
        private set
    fun consumePendingFind() { pendingFind = null }

    private var textCache: Pair<Int, List<String>>? = null

    /** 全书搜索（TXT / EPUB）。章节纯文本按阅读会话缓存，同一本书第二次搜索不再读文件 */
    suspend fun searchBook(query: String): List<SearchHit> {
        val doc = current() ?: return emptyList()
        val session = documentSession
        val texts = textCache?.takeIf { it.first == session }?.second
            ?: repo.chapterTexts(doc).also { textCache = session to it }
        return withContext(Dispatchers.Default) { BookSearch.search(texts, query) }
    }

    /** 打开一条搜索结果：跳到那一章，加载完后高亮，并跳到本章第 n 个命中 */
    fun openHit(hit: SearchHit, query: String, nthInChapter: Int) {
        pendingFind = query to nthInChapter
        jumpTo(hit.chapter, 0f)
    }

    /** SVG：显示图片还是源码 */
    var svgSource by mutableStateOf(false)
        private set
    fun toggleSvgSource() { svgSource = !svgSource }

    /** 单文件导入的 HTML 引用了同文件夹资源、但没有被带进来的路径 */
    var missingResources by mutableStateOf<List<String>>(emptyList())
        private set
    fun reportMissing(paths: List<String>) { missingResources = paths }

    var editorSession by mutableStateOf<EditorSession?>(null)
        private set

    fun startEditor(doc: OpenedDoc.Single, template: String? = null): EditorSession {
        return editorSession ?: EditorSession(getApplication(), doc, wrap, theme, viewModelScope, template)
            .also { editorSession = it }
    }

    fun cancelEditorLeave() {
        editorSession?.let { it.confirmLeave = false; it.incomingUri = null }
    }

    fun finishEditor(saved: Boolean = false) {
        val session = editorSession ?: return
        val incoming = session.incomingUri
        val savedBookId = session.doc.bookId.takeIf { saved }
        editorSession = null
        session.close()
        if (incoming != null) load {
            // Saving and then reimporting the same source would immediately overwrite the save.
            val existing = savedBookId?.let {
                AppDatabase.get(getApplication()).bookDao().findBySource(incoming.toString())
            }
            repo.open(if (existing?.id == savedBookId && existing != null) existing.id else repo.import(incoming))
        }
    }

    fun importAndOpen(uri: Uri) {
        editorSession?.let {
            it.incomingUri = uri
            it.confirmLeave = true
            return
        }
        load { repo.open(repo.import(uri)) }
    }
    fun openBook(id: Long) = load { repo.open(id) }

    /** 保存期间允许导航；只有原来的阅读会话仍在显示时才同步它的文本。 */
    suspend fun saveEdits(bookId: Long, text: String): Result<Unit> {
        val session = documentSession
        return try {
            startup.await()?.let { throw it }
            repo.updateContent(bookId, text)
            val cur = current()
            if (session == documentSession && cur is OpenedDoc.Single && cur.bookId == bookId) {
                reader = ReaderState.Ready(cur.copy(doc = cur.doc.copy(text = text)))
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 写到 CreateDocument 选择的新位置，关闭输出流成功后再导入书架。 */
    suspend fun saveAs(uri: Uri, text: String): Result<Long> {
        var outputWritten = false
        return try {
            startup.await()?.let { throw it }
            val bytes = editorTextBytes(text)
            val app = getApplication<Application>()
            // Guard every known original, including the document the user just navigated away from.
            if (AppDatabase.get(app).bookDao().findBySource(uri.toString()) != null) {
                throw IOException("另存为请选择新文件，不能覆盖原文件")
            }
            withContext(Dispatchers.IO) {
                val output = app.contentResolver.openOutputStream(uri, "wt")
                    ?: throw IOException("无法写入所选位置")
                output.use { it.write(bytes) }
                outputWritten = true
            }
            Result.success(repo.import(uri))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(if (outputWritten) {
                IOException("文件已写入所选位置，但加入书架失败：${e.message ?: e.javaClass.simpleName}", e)
            } else e)
        }
    }

    private fun load(block: suspend () -> OpenedDoc) {
        flushPosition() // 从一本直接跳到另一本时，先保存上一本的位置
        loadJob?.cancel()
        screen = Screen.Reader
        reader = ReaderState.Loading
        documentSession++
        chapterAnchor = null
        svgSource = false
        missingResources = emptyList()
        pendingFind = null
        loadJob = viewModelScope.launch {
            reader = try {
                startup.await()?.let { throw it }
                PositionWrites.awaitPending()
                val doc = block()
                chapter = doc.locator.chapter.coerceIn(0, doc.chapterCount - 1)
                // 书变短了（重新导入）导致章节号被修正时，章内比例也作废
                ratio = if (doc.locator.chapter == chapter) doc.locator.ratio else 0f
                ReaderState.Ready(doc)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ReaderState.Error(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /**
     * WebView 滚动回调。带上"这是哪一章的滚动"：切章瞬间旧 WebView 可能还会发一两个滚动事件，
     * 不过滤的话会把旧章节的比例写到新章节上。
     */
    fun onScroll(forSession: Int, forChapter: Int, r: Float) {
        if (forSession != documentSession || forChapter != chapter) return
        val doc = current() ?: return
        ratio = if (r.isFinite()) r.coerceIn(0f, 1f) else 0f
        val locator = Locator(chapter, ratio)
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(800) // 防抖：停止滚动 0.8 秒后才写库
            PositionWrites.enqueue(repo, doc.bookId, locator, doc.chapterCount)
        }
    }

    /** 立即保存（离开阅读页、切章、App 进入后台时调用） */
    fun flushPosition() {
        val doc = current() ?: return
        saveJob?.cancel()
        val locator = Locator(chapter, ratio)
        PositionWrites.enqueue(repo, doc.bookId, locator, doc.chapterCount)
    }

    fun goToChapter(index: Int, anchor: String? = null, atEnd: Boolean = false) {
        val doc = current() ?: return
        if (index !in 0 until doc.chapterCount || index == chapter) return
        chapterAnchor = anchor
        chapter = index
        ratio = if (atEnd) 1f else 0f
        flushPosition()
    }

    fun nextChapter() = goToChapter(chapter + 1)
    fun prevChapter() = goToChapter(chapter - 1)
    fun prevChapterFromStart() = goToChapter(chapter - 1, atEnd = true)

    /** EPUB 内部链接：指向另一章就切过去；同一章（页内锚点）或不在目录里的文件交给 WebView */
    fun onBookLink(path: String, fragment: String?): Boolean {
        val doc = current() as? OpenedDoc.Epub ?: return false
        val index = doc.chapters.indexOfFirst { it.path == path }
        if (index < 0 || index == chapter) return false
        goToChapter(index, fragment)
        return true
    }

    fun backToShelf() {
        editorSession?.close()
        editorSession = null
        flushPosition()
        loadJob?.cancel()
        screen = Screen.Shelf
    }

    override fun onCleared() {
        editorSession?.close()
        editorSession = null
        super.onCleared()
    }

    private fun current(): OpenedDoc? =
        if (screen == Screen.Reader) (reader as? ReaderState.Ready)?.doc else null

    private companion object {
        const val KEY_WRAP = "code_wrap"
    }
}

// App-lifetime ordered writes: onStop/back saves must outlive Activity/ViewModel teardown.
private object PositionWrites {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    suspend fun awaitPending() { lock.withLock { } }

    fun enqueue(repo: ShelfRepository, id: Long, locator: Locator, chapters: Int) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            lock.withLock {
                try { repo.savePosition(id, locator, chapters) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { Log.w("ReaderProgress", "Unable to save reading position", e) }
            }
        }
    }
}

/** Finish only the started item across caller cancellation; never revoke a profile for a failed delete. */
internal suspend fun deleteShelfItems(
    ids: List<Long>,
    delete: suspend (Long) -> Unit,
    revoke: suspend (Long) -> Unit,
) {
    for (id in ids) {
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable) {
            delete(id)
            revoke(id)
        }
    }
}
