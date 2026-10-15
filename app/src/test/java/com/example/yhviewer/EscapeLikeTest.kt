package com.example.yhviewer

import org.junit.Assert.assertEquals
import org.junit.Test

class EscapeLikeTest {

    @Test
    fun plainTextUnchanged() = assertEquals("report 中文", escapeLike("report 中文"))

    @Test
    fun percentAndUnderscore() = assertEquals("50\\% a\\_b", escapeLike("50% a_b"))

    @Test
    fun backslashEscapedFirst() {
        // 输入 a\%  →  a\\\%   （反斜杠先变成两个，再给 % 加一个）
        assertEquals("a\\\\\\%", escapeLike("a\\%"))
    }

    @Test
    fun empty() = assertEquals("", escapeLike(""))
}
