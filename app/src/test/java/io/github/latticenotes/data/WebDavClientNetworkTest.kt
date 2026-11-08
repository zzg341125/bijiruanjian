package io.github.latticenotes.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WebDavClientNetworkTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun connectUploadAndDownload_useExpectedHttpMethods() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(201))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))

        val client = WebDavClient(
            WebDavConfig(
                fileUrl = server.url("/notes/backup.json").toString(),
                username = "user",
                password = "pass",
            ),
        )

        client.testConnection()
        client.upload("""{"notes":[]}""")
        assertEquals("""{"ok":true}""", client.download())

        val options = server.takeRequest()
        val put = server.takeRequest()
        val get = server.takeRequest()
        assertEquals("OPTIONS", options.method)
        assertEquals("PUT", put.method)
        assertEquals("GET", get.method)
        assertTrue(put.getHeader("Authorization")!!.startsWith("Basic "))
        assertEquals("""{"notes":[]}""", put.body.readUtf8())
    }
}
