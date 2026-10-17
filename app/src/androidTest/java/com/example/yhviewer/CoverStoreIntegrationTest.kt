package com.example.yhviewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import java.util.zip.CRC32

@RunWith(AndroidJUnit4::class)
class CoverStoreIntegrationTest {
    private val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "cover-test-${UUID.randomUUID()}").apply { mkdirs() }
    private val store = CoverStore(File(root, "covers"))
    @After fun cleanup() { root.deleteRecursively() }

    private fun image(width: Int, height: Int, color: Int = Color.RED): File {
        val source = File(root, "source.png")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(color)
            source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
        return source
    }

    @Test fun generatesBoundedThumbnailAndReplacesSameId() {
        assertTrue(store.generate(7, image(1200, 1800)))
        BitmapFactory.decodeFile(store.file(7).path).let { result ->
            assertNotNull(result)
            try { assertEquals(300, result.width); assertEquals(450, result.height) }
            finally { result.recycle() }
        }
        assertTrue(store.generate(7, image(100, 150, Color.BLUE)))
        BitmapFactory.decodeFile(store.file(7).path).let { result ->
            assertNotNull(result)
            try {
                assertEquals(100, result.width); assertEquals(150, result.height)
                assertTrue(Color.blue(result.getPixel(50, 75)) > 200)
            } finally { result.recycle() }
        }
        assertFalse(File(store.file(7).path + ".tmp").exists())
        store.delete(7)
        assertFalse(store.exists(7))
    }

    /** A valid PNG IHDR declares huge bounds without allocating the bitmap in this test. */
    private fun oversizedHeader(): File {
        val source = File(root, "huge.png")
        val payload = ByteArrayOutputStream().also { bytes -> DataOutputStream(bytes).use {
            it.writeInt(20000); it.writeInt(20000); it.write(byteArrayOf(8, 2, 0, 0, 0))
        } }.toByteArray()
        DataOutputStream(source.outputStream()).use {
            it.write(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
            val tag = "IHDR".toByteArray()
            it.writeInt(payload.size); it.write(tag); it.write(payload)
            val crc = CRC32().apply { update(tag); update(payload) }
            it.writeInt(crc.value.toInt())
            val data = ByteArrayOutputStream().also { bytes ->
                java.util.zip.DeflaterOutputStream(bytes).use { stream -> stream.write(byteArrayOf(0, 0, 0, 0)) }
            }.toByteArray()
            val dataTag = "IDAT".toByteArray()
            it.writeInt(data.size); it.write(dataTag); it.write(data)
            it.writeInt(CRC32().apply { update(dataTag); update(data) }.value.toInt())
            it.writeInt(0); val end = "IEND".toByteArray(); it.write(end)
            it.writeInt(CRC32().apply { update(end) }.value.toInt())
        }
        return source
    }

    @Test fun rejectsHugeBoundsOversizedFilesAndInvalidImages() {
        val huge = oversizedHeader()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(huge.path, bounds)
        assertEquals(20000, bounds.outWidth)
        assertEquals(20000, bounds.outHeight)
        assertFalse(store.generate(1, huge))
        val bytes = File(root, "too-large.png")
        RandomAccessFile(bytes, "rw").use { it.setLength(CoverStore.MAX_SOURCE_BYTES + 1) }
        assertFalse(store.generate(2, bytes))
        assertFalse(store.generate(3, File(root, "missing.png")))
        val invalid = File(root, "invalid.png").apply { writeText("not an image") }
        assertFalse(store.generate(4, invalid))
        assertFalse(store.exists(1)); assertFalse(store.exists(2)); assertFalse(store.exists(3)); assertFalse(store.exists(4))
    }

    @Test fun narrowTallCoverHasBoundedThumbnailHeight() {
        assertTrue(store.generate(8, image(100, 40000)))
        BitmapFactory.decodeFile(store.file(8).path).let { result ->
            assertNotNull(result)
            try {
                assertTrue(result.width <= CoverStore.COVER_WIDTH)
                assertTrue(result.height <= CoverStore.COVER_MAX_HEIGHT)
            } finally { result.recycle() }
        }
    }

    @Test fun cleanupKeepsOnlyLiveCoversAndRemovesTemporaryFiles() {
        assertTrue(store.generate(1, image(100, 150)))
        assertTrue(store.generate(2, image(100, 150)))
        File(store.file(1).path + ".tmp").writeText("interrupted")
        assertEquals(2, store.cleanOrphans(setOf(1)))
        assertTrue(store.exists(1)); assertFalse(store.exists(2))
        assertFalse(File(store.file(1).path + ".tmp").exists())
    }
}
