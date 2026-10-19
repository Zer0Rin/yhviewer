package com.example.yhviewer

import org.junit.Assert.*
import org.junit.Test

class FindReplayTest {
    @Test fun activeQueryReplaysWithFreshCountsAfterEveryReplacement() {
        val state = FindState().apply { open = true; query = "term"; current = 3; total = 12; lastSearched = "term" }
        repeat(3) {
            state.pageLoading()
            assertEquals(0, state.current)
            assertEquals(0, state.total)
            assertFalse(state.pageReady)
            assertNull(state.lastSearched)
            assertEquals("term" to 0, state.readyRequest(null))
            assertTrue(state.pageReady)
            assertEquals("term", state.lastSearched)
        }
    }

    @Test fun wholeBookNthHitTakesPrecedenceOverActivePageQuery() {
        val state = FindState().apply { open = true; query = "old" }
        state.pageLoading()
        assertEquals("new" to 2, state.readyRequest("new" to 2))
        assertEquals("new", state.query)
        assertEquals("new", state.lastSearched)
        assertTrue(state.open)
    }

    @Test fun closedOrBlankSearchDoesNotReplayOnReady() {
        val state = FindState().apply { query = "term" }
        state.pageLoading()
        assertNull(state.readyRequest(null))
        state.open = true
        state.query = "   "
        assertNull(state.readyRequest(null))
    }
}
