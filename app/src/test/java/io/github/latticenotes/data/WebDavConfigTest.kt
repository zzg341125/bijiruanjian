package io.github.latticenotes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WebDavConfigTest {
    @Test
    fun httpsUrl_isAccepted() {
        val url = WebDavConfig(
            fileUrl = "https://dav.example.com/notes",
            username = "user",
            password = "pass",
        ).directoryUrl()
        assertEquals("https", url.protocol)
    }

    @Test
    fun remotePlainHttp_isRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            WebDavConfig("http://example.com/notes", "u", "p").directoryUrl()
        }
    }

    @Test
    fun loopbackPlainHttp_isAcceptedForLocalTesting() {
        val url = WebDavConfig(
            "http://127.0.0.1:8080/notes",
            "",
            "",
        ).directoryUrl()
        assertEquals("http", url.protocol)
    }

    @Test
    fun privateLanHttp_isAcceptedForPersonalWebDav() {
        val url = WebDavConfig(
            "http://192.168.1.50:9799/notes/",
            "user",
            "pass",
        ).directoryUrl()

        assertEquals("http", url.protocol)
        assertEquals(
            "http://192.168.1.50:9799/notes/",
            url.toString(),
        )
    }

    @Test
    fun directoryUrl_normalizesTrailingSlash() {
        val url = WebDavConfig(
            "https://dav.jianguoyun.com/dav/biji",
            "u",
            "p",
        ).directoryUrl()

        assertEquals(
            "https://dav.jianguoyun.com/dav/biji/",
            url.toString(),
        )
    }
}
