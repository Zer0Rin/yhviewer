package com.example.yhviewer

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class BundleStorageRegressionTest {
    @Test fun replacingOneDocumentRetainsEveryFileInReferencedOldBundle() {
        val root = Files.createTempDirectory("bundle-retain").toFile()
        try {
            File(root, "old/a.html").apply { parentFile!!.mkdirs(); writeText("linked page") }
            File(root, "old/b.html").writeText("remaining book")
            BundleStorage.removeUnreferenced(root, "old/a.html", listOf("old/b.html", "new/a.html"))
            assertTrue(File(root, "old/a.html").exists())
            BundleStorage.removeUnreferenced(root, "old/b.html", listOf("new/a.html"))
            assertFalse(File(root, "old").exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun editingDoesNotOverwriteSiblingNamedLikeTemporaryFile() {
        val root = Files.createTempDirectory("bundle-edit").toFile()
        try {
            val doc = File(root, "page.html").apply { writeText("old") }
            val sibling = File(root, "page.html.yhtmp").apply { writeText("resource") }
            BundleStorage.replace(doc, "new".toByteArray())
            assertEquals("new", doc.readText())
            assertEquals("resource", sibling.readText())
        } finally { root.deleteRecursively() }
    }

    @Test fun bundlePathsRejectUntrustedSegments() {
        listOf("../bad", "a/b", "a\\b", "\u0000").forEach { bundle ->
            assertThrows(java.io.IOException::class.java) { BundlePaths.localFile(bundle, "a.html") }
        }
        listOf("a\\b", "a/../b", "a/\u0000", "a//b").forEach { rel ->
            assertThrows(java.io.IOException::class.java) { BundlePaths.localFile("bundle", rel) }
        }
    }

    @Test fun bundleBudgetCountsActualBytesAndRejectsOverflow() {
        val budget = BundleCopyBudget(10)
        budget.add(7)
        assertEquals(3, budget.remaining)
        assertThrows(java.io.IOException::class.java) { budget.add(4) }
        assertEquals(3, budget.remaining)
        budget.add(3)
        assertEquals(0, budget.remaining)
    }

    @Test fun rejectedOversizedDocumentDoesNotConsumeBundleFileSlot() {
        val plan = FolderImportPlanner.plan(listOf(
            FolderEntry("big.html", 11), FolderEntry("small.html", 5), FolderEntry("image.png", 5),
        ), FolderImportPlanner.Limits(maxBundleFiles = 2, maxBundleBytes = 10))
        assertEquals(listOf("small.html"), plan.bundleDocs.map { it.entry.relPath })
        assertEquals(listOf("image.png"), plan.resources.map { it.entry.relPath })
    }

    @Test fun resourceTxtCannotUseStandaloneLargerLimit() {
        val plan = FolderImportPlanner.plan(listOf(
            FolderEntry("a.html", 1), FolderEntry("assets/data.txt", DocumentLoader.MAX_TEXT_BYTES + 1),
        ))
        assertEquals(EntryRole.SKIP, plan.entries.last().role)
    }

    @Test fun bundledMarkdownReceivesLiveThemeUpdates() {
        assertTrue(PageSource.BundleFile(File("bundle"), "doc.md", "<html></html>").supportsTemplateUpdates())
        assertFalse(PageSource.BookFile(File("book"), "chapter.xhtml").supportsTemplateUpdates())
    }

    @Test fun traversalGuardBoundsDirectoriesAndCycles() {
        val guard = FolderTraversalGuard(3)
        assertTrue(guard.visitDirectory("root"))
        assertFalse(guard.visitDirectory("root"))
        assertTrue(guard.visitDirectory("a"))
        assertTrue(guard.visitDirectory("b"))
        assertFalse(guard.visitDirectory("c"))
        assertTrue(guard.exhausted)
        val rows = FolderTraversalGuard(3)
        repeat(3) { assertTrue(rows.allowEntry()) }
        assertFalse(rows.allowEntry())
        assertTrue(rows.exhausted)
    }
}
