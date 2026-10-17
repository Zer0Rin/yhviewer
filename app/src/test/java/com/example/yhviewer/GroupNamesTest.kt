package com.example.yhviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GroupNamesTest {
    private val existing = listOf(ShelfGroup(1, "工作", 0), ShelfGroup(2, "Kotlin", 0))

    @Test fun accepts() {
        assertNull(GroupNames.validate("待读", existing))
        assertNull(GroupNames.validate("  待  读  ", existing))
        assertEquals("待 读", GroupNames.normalize("  待  读  "))
        assertNull(GroupNames.validate("字".repeat(GroupNames.MAX_LENGTH), existing))
    }

    @Test fun rejects() {
        assertNotNull(GroupNames.validate("   ", existing))
        assertNotNull(GroupNames.validate("字".repeat(GroupNames.MAX_LENGTH + 1), existing))
        GroupNames.RESERVED.forEach { assertNotNull(GroupNames.validate(it, existing)) }
        assertNotNull(GroupNames.validate("工作", existing))
        assertNotNull(GroupNames.validate("kotlin ", existing)) // 忽略大小写和首尾空格
    }

    @Test fun renamingKeepsOwnName() {
        assertNull(GroupNames.validate("KOTLIN", existing, renamingId = 2))  // 只改大小写
        assertNotNull(GroupNames.validate("工作", existing, renamingId = 2)) // 和别的分组重名
    }
}
