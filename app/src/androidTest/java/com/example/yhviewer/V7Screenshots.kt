package com.example.yhviewer

import android.graphics.Bitmap
import android.content.ContentValues
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry

/** Explicit acceptance runs can keep native captures; normal regression runs leave no images. */
internal fun exportV7Screenshot(name: String) {
    if (InstrumentationRegistry.getArguments().getString("v7Screenshots") != "true") return
    val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        ?: error("Unable to capture $name")
    val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/YHViewerV7Screenshots")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }) ?: error("Unable to create screenshot destination")
    try {
        resolver.openOutputStream(uri)!!.use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        throw e
    } finally { bitmap.recycle() }
}
