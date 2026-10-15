package com.example.yhviewer

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShelfDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: BookDao

    @Before
    fun setUp() {
        // 内存数据库：每个测试一张白纸，跑完即丢
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).build()
        dao = db.bookDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun book(name: String, opened: Long, fav: Boolean = false, uri: String = "content://t/$name") =
        Book(
            sourceUri = uri, name = name, type = DocType.HTML, localFile = name,
            sizeBytes = 1, addedAt = 0, lastOpenedAt = opened, favorite = fav,
        )

    private suspend fun names(query: String = "", favOnly: Boolean = false) =
        dao.observe(escapeLike(query), favOnly).first().map { it.name }

    @Test
    fun ordersByLastOpenedDesc() = runBlocking<Unit> {
        dao.insert(book("a", 1)); dao.insert(book("b", 3)); dao.insert(book("c", 2))
        assertEquals(listOf("b", "c", "a"), names())
    }

    @Test
    fun searchIsSubstringAndCaseInsensitive() = runBlocking<Unit> {
        dao.insert(book("Weekly-Report.html", 1)); dao.insert(book("notes.md", 2))
        assertEquals(listOf("Weekly-Report.html"), names("report"))
    }

    @Test
    fun percentIsLiteral() = runBlocking<Unit> {
        dao.insert(book("50% off", 1)); dao.insert(book("500 items", 2))
        assertEquals(listOf("50% off"), names("50%"))
    }

    @Test
    fun underscoreIsLiteral() = runBlocking<Unit> {
        dao.insert(book("a_b", 1)); dao.insert(book("axb", 2))
        assertEquals(listOf("a_b"), names("a_b"))
    }

    @Test
    fun favoritesOnly() = runBlocking<Unit> {
        dao.insert(book("a", 1, fav = true)); dao.insert(book("b", 2))
        assertEquals(listOf("a"), names(favOnly = true))
    }

    @Test
    fun setFavoriteAndTouch() = runBlocking<Unit> {
        val id = dao.insert(book("a", 1))
        dao.setFavorite(id, true)
        dao.touch(id, 99)
        val b = dao.get(id)!!
        assertEquals(true, b.favorite)
        assertEquals(99L, b.lastOpenedAt)
    }

    @Test(expected = SQLiteConstraintException::class)
    fun sourceUriIsUnique() = runBlocking<Unit> {
        dao.insert(book("a", 1, uri = "content://same"))
        dao.insert(book("b", 2, uri = "content://same"))
    }
}
