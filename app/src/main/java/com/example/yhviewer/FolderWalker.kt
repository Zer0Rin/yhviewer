package com.example.yhviewer

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * v10：遍历用户用"导入文件夹"（ACTION_OPEN_DOCUMENT_TREE）选中的目录树。
 * 只读，不申请持久授权：导入时把需要的文件复制进书架，之后不再访问原文件夹。
 *
 * DocumentsProvider 返回的名字、大小都不可信：名字过 FolderImportPlanner.safeName，
 * 大小只用于规划，真正复制时仍由 DocumentLoader 边读边数字节。
 */
object FolderWalker {

    data class Found(val uri: Uri, val entry: FolderEntry)

    private val COLUMNS = arrayOf(
        Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_MIME_TYPE,
        Document.COLUMN_SIZE,
    )

    suspend fun walk(
        context: Context,
        treeUri: Uri,
        limits: FolderImportPlanner.Limits = FolderImportPlanner.Limits(),
    ): List<Found> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val out = ArrayList<Found>()
        // 栈代替递归；目录和文件都计入遍历上限，避免空目录树或循环标识无限扩张。
        val stack = ArrayDeque<Triple<String, String, Int>>()
        val metadata = ProviderMetadataBudget()
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        if (!metadata.accept(rootId, "")) return@withContext listOf(Found(treeUri, FolderEntry("文件夹", 0, "文件夹标识超过限制")))
        stack.addLast(Triple(rootId, "", 0))
        val guard = FolderTraversalGuard(limits.maxEntries)
        val paths = HashSet<String>()
        while (stack.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val (docId, prefix, depth) = stack.removeLast()
            if (!guard.visitDirectory(docId)) continue
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val cursor = resolver.query(children, COLUMNS, null, null, null)
                ?: throw IOException("无法读取文件夹：$prefix")
            cursor.use { c ->
                while (c.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    if (!guard.allowEntry()) break
                    val id = c.getString(0)
                    val rawName = c.getString(1)
                    val name = FolderImportPlanner.safeName(rawName)
                    if (!ProviderMetadataBudget.validId(id) || name == null) {
                        out += Found(treeUri, FolderEntry("无效条目", 0, "非法文件名或标识"))
                        continue
                    }
                    if (FolderImportPlanner.ignored(name)) continue
                    val rel = if (prefix.isEmpty()) name else "$prefix/$name"
                    if (!metadata.accept(id, rel)) {
                        out += Found(treeUri, FolderEntry("无效条目", 0, "文件夹元数据超过限制"))
                        continue
                    }
                    if (!paths.add(rel)) {
                        out += Found(treeUri, FolderEntry(rel, 0, "重复文件路径"))
                        continue
                    }
                    if (c.getString(2) == Document.MIME_TYPE_DIR) {
                        if (depth < limits.maxDepth) stack.addLast(Triple(id!!, rel, depth + 1))
                        else out += Found(treeUri, FolderEntry(rel, 0, "目录层级太深"))
                    } else {
                        val size = if (c.isNull(3)) 0L else c.getLong(3).coerceAtLeast(0)
                        out += Found(DocumentsContract.buildDocumentUriUsingTree(treeUri, id!!), FolderEntry(rel, size))
                    }
                }
            }
            if (guard.exhausted) {
                out += Found(treeUri, FolderEntry("文件夹", 0, "文件夹遍历超过 ${limits.maxEntries} 个条目"))
                break
            }
        }
        out.sortedBy { it.entry.relPath }
    }

    /** 文件夹显示名（导入报告里用） */
    fun folderName(context: Context, treeUri: Uri): String = runCatching {
        val id = DocumentsContract.getTreeDocumentId(treeUri)
        if (!ProviderMetadataBudget.validId(id)) return@runCatching "文件夹"
        val doc = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
        context.contentResolver.query(doc, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) FolderImportPlanner.safeName(c.getString(0)) else null
        }
    }.getOrNull() ?: "文件夹"
}
