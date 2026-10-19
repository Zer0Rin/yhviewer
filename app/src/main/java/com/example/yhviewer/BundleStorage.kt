package com.example.yhviewer

import java.io.File
import java.io.IOException

/** File operations shared by bundle import, replacement and cleanup. */
internal object BundleStorage {
    fun removeUnreferenced(dir: File, localFile: String, referenced: List<String>) {
        val top = BundlePaths.topLevel(localFile)
        if (referenced.none { BundlePaths.topLevel(it) == top }) {
            val target = File(dir, top)
            if (target.canonicalFile.parentFile != dir.canonicalFile) throw IOException("非法副本路径")
            target.deleteRecursively()
        }
    }

    fun replace(target: File, bytes: ByteArray) {
        val tmp = File.createTempFile(".yh-edit-", ".tmp", target.parentFile)
        try {
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(target)) throw IOException("保存副本失败")
        } finally { tmp.delete() }
    }
}

internal class BundleCopyBudget(private val limit: Long) {
    var remaining = limit
        private set
    fun add(bytes: Long) {
        if (bytes < 0 || bytes > remaining) throw IOException("文件夹副本超过大小限制")
        remaining -= bytes
    }
}

internal class FolderTraversalGuard(private val limit: Int) {
    private val directories = HashSet<String>()
    var exhausted = false
        private set
    private var entries = 0
    fun allowEntry(): Boolean {
        if (entries >= limit) { exhausted = true; return false }
        entries++
        return true
    }
    fun visitDirectory(id: String): Boolean {
        if (id in directories) return false
        if (directories.size >= limit) { exhausted = true; return false }
        return directories.add(id)
    }
}
