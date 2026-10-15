package com.example.yhviewer

import org.junit.Assert.assertEquals
import org.junit.Test

class DocTypesTest {
    @Test fun byExtension() {
        assertEquals(DocType.HTML, DocTypes.detect("a.HTML", null))
        assertEquals(DocType.MARKDOWN, DocTypes.detect("a.md", null))
        assertEquals(DocType.EPUB, DocTypes.detect("book.epub", null))
        assertEquals(DocType.CODE, DocTypes.detect("Main.kt", null))
        assertEquals(DocType.TXT, DocTypes.detect("novel.txt", null))
    }

    @Test fun codeFileNameBeatsTxt() {
        assertEquals(DocType.CODE, DocTypes.detect("CMakeLists.txt", "text/plain"))
    }

    @Test fun extensionBeatsMime() {
        // 很多文件管理器把 .md 报成 text/plain
        assertEquals(DocType.MARKDOWN, DocTypes.detect("notes.md", "text/plain"))
    }

    @Test fun mimeFallback() {
        assertEquals(DocType.HTML, DocTypes.detect("download", "text/html"))
        assertEquals(DocType.EPUB, DocTypes.detect("1234", "application/epub+zip"))
        assertEquals(DocType.UNKNOWN, DocTypes.detect("README", "text/plain"))
    }
}
