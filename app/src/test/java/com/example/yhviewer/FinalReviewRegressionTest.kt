package com.example.yhviewer

import org.junit.Assert.*
import org.junit.Test

class FinalReviewRegressionTest {
    @Test fun oversizedProviderMetadataNeverConsumesRetentionBudget() {
        val budget = ProviderMetadataBudget()
        repeat(3000) {
            assertFalse(budget.accept("$it" + "x".repeat(100_000), "a.html"))
            assertNull(FolderImportPlanner.safeName("$it" + "x".repeat(100_000)))
        }
        assertEquals(0, budget.retainedChars)
        assertFalse(budget.accept("root", "x".repeat(2049)))
        assertNull(FolderImportPlanner.safeName("界".repeat(86)))
        repeat(3000) { budget.accept("$it" + "x".repeat(4000), "accepted.html") }
        assertTrue(budget.retainedChars <= 512 * 1024)
        assertFalse(budget.accept("x".repeat(4097), ""))
    }

    @Test fun missingFloodQueuesOnlyOneSnapshotThenStopsGrowing() {
        val missing = MissingResourceAccumulator()
        var queued = 0
        repeat(10000) { if (missing.add("/doc/missing-$it" + "x".repeat(1000))) queued++ }
        assertEquals(1, queued)
        val snapshot = missing.drainNotification()
        assertEquals(9, snapshot.size)
        assertTrue(snapshot.all { it.length <= 160 })
        assertTrue(snapshot.last().contains("更多"))
        assertTrue(MissingResources.message(snapshot, true).contains("至少 9"))
        repeat(10000) { assertFalse(missing.add("/doc/later-$it")) }
        assertEquals(snapshot, missing.drainNotification())
    }

    @Test fun interleavedNotificationsStillHaveFixedTotalBound() {
        val missing = MissingResourceAccumulator()
        var notifications = 0
        repeat(1000) {
            if (missing.add("/bundle/missing-$it")) {
                notifications++
                missing.drainNotification()
            }
        }
        assertEquals(9, notifications)
    }

    @Test fun stagedBatchRetainsOnlyCommitFields() {
        val staged = (1..10).map { StagedBundleDocument.from("doc$it.html", 20L * 1024 * 1024, "<html>" + "x".repeat(2 * 1024 * 1024)) }
        assertEquals(10, staged.size)
        assertTrue(staged.all { it.kind == ContentKind.WEB && it.size == 20L * 1024 * 1024 })
        // A structural regression: no LoadedDoc or body/sample field can survive staging.
        assertEquals(setOf("relPath", "name", "type", "size", "kind"),
            StagedBundleDocument::class.java.declaredFields.filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }.map { it.name }.toSet())
    }

    @Test fun txtTitleAndTrimmedBodyHaveMatchingNativeOrdinals() {
        val text = TxtSearchText.rendered("第一章 needle", "　 needle first \r\n\n\tneedle second\u00a0")
        assertEquals("第一章 needle\nneedle first\nneedle second", text)
        val hits = BookSearch.search(listOf(text), "needle")
        assertEquals(3, hits.size)
        assertTrue(hits[2].offset > hits[1].offset)
        assertEquals(1, BookSearch.search(listOf(text), "第一章").size)
    }
}
