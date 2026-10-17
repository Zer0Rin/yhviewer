package com.example.yhviewer

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfDeletionTest {
    @Test fun cancellationCompletesStartedDeletionAndLeavesRemainingProfiles() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            deleteShelfItems(listOf(1L, 2L), delete = { id ->
                events += "delete:$id"
                entered.complete(Unit)
                release.await()
                events += "deleted:$id"
            }, revoke = { id -> events += "revoke:$id" })
        }
        entered.await()
        job.cancel()
        release.complete(Unit)
        job.cancelAndJoin()
        assertEquals(listOf("delete:1", "deleted:1", "revoke:1"), events)
        assertTrue(job.isCancelled)
    }

    @Test fun failedDeletionDoesNotRevokeAnyRemainingProfile() = runBlocking {
        val events = mutableListOf<String>()
        try {
            deleteShelfItems(listOf(1L, 2L), delete = { id ->
                events += "delete:$id"
                error("database failure")
            }, revoke = { id -> events += "revoke:$id" })
            error("Expected deletion failure")
        } catch (e: IllegalStateException) {
            assertEquals("database failure", e.message)
        }
        assertEquals(listOf("delete:1"), events)
    }
}
