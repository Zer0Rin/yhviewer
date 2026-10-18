package com.example.yhviewer

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class BookmarkFeedbackTest {
    @Test fun committedInsertAndDuplicateHaveDistinctFeedback() {
        assertEquals("已添加书签", BookmarkFeedback.message(Result.success(true)))
        assertEquals("这里已有书签", BookmarkFeedback.message(Result.success(false)))
    }

    @Test fun storageFailureCannotProduceSuccessfulFeedback() {
        val message = BookmarkFeedback.message(Result.failure(IOException("disk full")))
        assertTrue(message.contains("失败"))
        assertTrue(message.contains("disk full"))
        assertFalse(message.contains("已添加"))
    }
}
