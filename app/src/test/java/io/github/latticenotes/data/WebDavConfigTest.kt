package io.github.latticenotes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WebDavConfigTest {
    @Test
    fun httpsUrl_isAccepted() {
        val url = WebDavConfig(
            fileUrl = "https://dav.example.com/notes/quyang-backup.json",
            username = "user",
            password = "pass",
        ).validatedUrl()
        assertEquals("https", url.protocol)
    }

    @Test
    fun remotePlainHttp_isRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            WebDavConfig("http://example.com/backup.json", "u", "p").validatedUrl()
        }
    }

    @Test
    fun loopbackPlainHttp_isAcceptedForLocalTesting() {
        val url = WebDavConfig(
            "http://127.0.0.1:8080/backup.json",
            "",
            "",
        ).validatedUrl()
        assertEquals("http", url.protocol)
    }
}
