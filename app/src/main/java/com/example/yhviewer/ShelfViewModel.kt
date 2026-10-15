package com.example.yhviewer

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Shelf : Screen
    data object Reader : Screen
}

sealed interface ReaderState {
    data object Loading : ReaderState
    data class Ready(val doc: LoadedDoc) : ReaderState
    data class Error(val message: String) : ReaderState
}

/**
 * ViewModel 活得比 Activity 长：旋转屏幕时 Activity 重建，这里的状态不丢。
 * 类比后端：Activity 像一次请求，ViewModel 像会话。
 */
class ShelfViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ShelfRepository(app, AppDatabase.get(app))

    // ---- 书架 ----
    // 输入框状态用 Compose state（不用 StateFlow），避免输入法光标跳动的已知问题
    var query by mutableStateOf("")
        private set
    var favoritesOnly by mutableStateOf(false)
        private set

    /** null = 数据库还没返回第一批数据（区分"加载中"和"真的是空书架"，避免空状态闪一下） */
    @OptIn(ExperimentalCoroutinesApi::class)
    val books: StateFlow<List<Book>?> =
        snapshotFlow { query to favoritesOnly }
            .flatMapLatest { (q, fav) -> repo.observe(q, fav) } // 条件一变，自动切换到新查询
            .stateIn<List<Book>?>(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun updateQuery(q: String) { query = q }
    fun toggleFavoritesOnly() { favoritesOnly = !favoritesOnly }
    fun toggleFavorite(book: Book) { viewModelScope.launch { repo.setFavorite(book.id, !book.favorite) } }
    fun delete(book: Book) { viewModelScope.launch { repo.delete(book.id) } }

    // ---- 导航 + 阅读 ----
    var screen by mutableStateOf<Screen>(Screen.Shelf)
        private set
    var reader by mutableStateOf<ReaderState>(ReaderState.Loading)
        private set
    private var loadJob: Job? = null

    fun importAndOpen(uri: Uri) = load { repo.open(repo.import(uri)) }
    fun openBook(id: Long) = load { repo.open(id) }

    fun backToShelf() {
        loadJob?.cancel()
        screen = Screen.Shelf
    }

    private fun load(block: suspend () -> LoadedDoc) {
        loadJob?.cancel() // 连续打开两个文件：只保留最后一次
        screen = Screen.Reader
        reader = ReaderState.Loading
        loadJob = viewModelScope.launch {
            reader = try {
                ReaderState.Ready(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ReaderState.Error(e.message ?: e.javaClass.simpleName)
            }
        }
    }
}
