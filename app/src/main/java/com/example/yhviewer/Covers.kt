package com.example.yhviewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/*
 * 书架封面。
 *
 *  - EPUB 自带封面：导入时缩成宽 COVER_WIDTH 的 JPEG，存到 filesDir/covers/<bookId>.jpg
 *  - TXT、没有封面的 EPUB：不存文件，书架上用书名现场画一张"排版封面"（CoverPalette 选底色）
 *
 * 封面按 bookId 命名而不是跟着 localFile：重新导入同一本书时 id 不变，直接覆盖；
 * 删除书时一起删；启动时清掉 id 已不存在的封面。
 */

// ======================= 纯逻辑（JVM 可测） =======================

/** 排版封面的配色。同一个书名永远得到同一组颜色 */
enum class CoverPalette(val background: Long, val foreground: Long) {
    OXBLOOD(0xFF5F1017, 0xFFF1EBDF),
    INDIGO(0xFF1C2A55, 0xFFECEFF6),
    VELLUM(0xFFD8CDB9, 0xFF211A16),
    MOSS(0xFF2B3A2E, 0xFFE9E3D2),
    ;

    companion object {
        /** 自己算哈希，不依赖 String.hashCode 以外的东西；floorMod 保证负数也落在范围内 */
        fun forTitle(title: String): CoverPalette {
            var h = 0
            for (c in title.trim()) h = 31 * h + c.code
            return entries[Math.floorMod(h, entries.size)]
        }
    }
}

/**
 * 从文件名里整理出书名和作者，用于 TXT（EPUB 用 OPF 里的元数据）。
 * 只处理常见命名，认不出来就原样返回去掉扩展名的文件名，作者为 null。
 *   《三体》作者：刘慈欣.txt   → 三体 / 刘慈欣
 *   《三体》（校对版）.txt      → 三体 / null
 *   三体 作者：刘慈欣.txt       → 三体 / 刘慈欣
 *   三体 - 刘慈欣.txt           → 三体 / 刘慈欣
 */
object BookTitles {
    data class Parsed(val title: String, val author: String?)

    private val BRACKETED = Regex("《([^》]+)》")
    private val AUTHOR_TAG = Regex("""作者\s*[:：]\s*([^\s（(\[【]+)""")
    private val DASH = Regex("""^(.+?)\s+[-–—]\s+([^-–—]+)$""")

    fun parse(fileName: String): Parsed {
        val base = fileName.substringBeforeLast('.').trim().ifEmpty { fileName }
        val author = AUTHOR_TAG.find(base)?.groupValues?.get(1)?.trim()?.ifEmpty { null }
        BRACKETED.find(base)?.let { return Parsed(it.groupValues[1].trim(), author) }
        if (author != null) {
            val title = base.substringBefore("作者").trim().trimEnd('-', '_', ' ').ifEmpty { base }
            return Parsed(title, author)
        }
        DASH.matchEntire(base)?.let { return Parsed(it.groupValues[1].trim(), it.groupValues[2].trim()) }
        return Parsed(base, null)
    }

    /** 书架上显示的书名和作者：EPUB 用元数据，其他用文件名解析 */
    fun display(book: Book): Parsed =
        if (book.type == DocType.EPUB) Parsed(book.name, book.author)
        else parse(book.name).let { if (book.author != null) it.copy(author = book.author) else it }
}

/** 计算 BitmapFactory 的采样率：2 的幂，解码后宽度不小于目标宽度 */
internal fun sampleSizeFor(width: Int, targetWidth: Int): Int {
    require(width > 0 && targetWidth > 0)
    var s = 1
    while (width.toLong() / (s.toLong() * 2) >= targetWidth) s *= 2
    return s
}

// ======================= 文件 =======================

class CoverStore(private val dir: File) {
    internal var revision by mutableLongStateOf(0L)
        private set

    fun file(bookId: Long) = File(dir, "$bookId.jpg")

    fun exists(bookId: Long) = file(bookId).isFile

    /**
     * 把书里的封面缩成缩略图。图片是不可信输入，所以：
     *  - 先只读尺寸（inJustDecodeBounds），拒绝超大像素数，防"解码炸弹"把内存吃光
     *  - 按采样率解码，内存里只出现缩小后的位图
     *  - 先写 .tmp 再 rename
     * 返回是否成功；失败不抛异常，书照样进书架，只是显示排版封面。
     */
    fun generate(bookId: Long, source: File): Boolean {
        var decoded: Bitmap? = null
        var scaled: Bitmap? = null
        val tmp = File(dir, "$bookId.jpg.tmp")
        return try {
            require(source.isFile && source.length() <= MAX_SOURCE_BYTES)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, bounds)
            val w = bounds.outWidth
            val h = bounds.outHeight
            require(w > 0 && h > 0 && w.toLong() * h <= MAX_SOURCE_PIXELS)
            // Bound BOTH dimensions: a narrow, very tall image can pass the source pixel cap.
            val opts = BitmapFactory.Options().apply {
                inSampleSize = maxOf(sampleSizeFor(w, COVER_WIDTH), sampleSizeFor(h, COVER_MAX_HEIGHT))
            }
            val image = BitmapFactory.decodeFile(source.path, opts) ?: throw IOException("decode failed")
            decoded = image
            val ratio = minOf(1.0, COVER_WIDTH.toDouble() / image.width, COVER_MAX_HEIGHT.toDouble() / image.height)
            val thumbnail = if (ratio < 1.0) Bitmap.createScaledBitmap(
                image, (image.width * ratio).toInt().coerceAtLeast(1),
                (image.height * ratio).toInt().coerceAtLeast(1), true,
            ) else image
            scaled = thumbnail
            if (!dir.isDirectory && !dir.mkdirs()) throw IOException("cover directory unavailable")
            tmp.outputStream().use {
                if (!thumbnail.compress(Bitmap.CompressFormat.JPEG, 85, it)) throw IOException("compress failed")
            }
            if (!tmp.renameTo(file(bookId))) throw IOException("rename failed")
            changed(bookId)
            true
        } catch (_: Exception) {
            false
        } finally {
            if (scaled !== decoded) scaled?.recycle()
            decoded?.recycle()
            runCatching { tmp.delete() }
        }
    }

    private fun changed(bookId: Long) {
        CoverCache.invalidate(file(bookId).absolutePath)
        revision++
    }

    fun delete(bookId: Long) {
        file(bookId).delete()
        File(dir, "$bookId.jpg.tmp").delete()
        changed(bookId)
    }

    /** 删掉书已不在书架上的封面和中断留下的 .tmp */
    fun cleanOrphans(existingIds: Set<Long>): Int =
        dir.listFiles().orEmpty().count { f ->
            val id = f.name.removeSuffix(".jpg").toLongOrNull()
            if ((id == null || id !in existingIds) && f.delete()) {
                CoverCache.invalidate(f.absolutePath)
                revision++
                true
            } else false
        }

    companion object {
        const val COVER_MAX_HEIGHT = 900
        const val COVER_WIDTH = 300            // 书架上最大的封面是 96dp 宽，3x 屏约 290px
        const val MAX_SOURCE_BYTES = 20L * 1024 * 1024
        const val MAX_SOURCE_PIXELS = 40_000_000L  // 约 8000×5000
    }
}

// ======================= Compose 加载 =======================

/** 解码后的缩略图缓存：每张约 300×450×4 ≈ 540 KB，留 16 MB 足够一屏多 */
internal object CoverCache {
    private val cache = object : LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    fun get(id: String): ImageBitmap? = cache.get(id)
    fun put(id: String, b: ImageBitmap) { cache.put(id, b) }
    fun invalidate(path: String) {
        cache.snapshot().keys.filter { it.startsWith("$path:") }.forEach { cache.remove(it) }
    }
}

/**
 * 读一本书的封面；没有封面文件时值为 null（调用方画排版封面）。
 * key 带上 localFile：重新导入后封面重新生成，这里也会重新读。
 */
@Composable
fun rememberCover(store: CoverStore, book: Book): State<ImageBitmap?> {
    val revision = store.revision
    val key = "${store.file(book.id).absolutePath}:$revision:${book.localFile}"
    return produceState<ImageBitmap?>(initialValue = null, key1 = key, key2 = book.coverChecked) {
        if (book.type != DocType.EPUB) { value = null; return@produceState }
        value = CoverCache.get(key) ?: withContext(Dispatchers.IO) {
            val f = store.file(book.id)
            if (!f.isFile) null
            else BitmapFactory.decodeFile(f.path)?.asImageBitmap()?.also { CoverCache.put(key, it) }
        }
    }
}
