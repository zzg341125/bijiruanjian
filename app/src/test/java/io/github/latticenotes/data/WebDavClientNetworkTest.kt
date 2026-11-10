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
        server.enqueue(MockResponse().setResponseCode(207))
        server.enqueue(MockResponse().setResponseCode(201))
        server.enqueue(MockResponse().setResponseCode(200).setBody("# Note\n\ncontent"))

        val client = WebDavClient(
            WebDavConfig(
                fileUrl = server.url("/notes").toString(),
                username = "user",
                password = "pass",
            ),
        )
        val fileUrl = client.markdownFileUrl("note-1.md")

        client.testConnection()
        client.uploadTo(fileUrl, "# Note\n\ncontent")
        assertEquals("# Note\n\ncontent", client.downloadFrom(fileUrl))

        val propfind = server.takeRequest()
        val put = server.takeRequest()
        val get = server.takeRequest()
        assertEquals("PROPFIND", propfind.method)
        assertEquals("/notes/", propfind.path)
        assertEquals("0", propfind.getHeader("Depth"))
        assertEquals("PUT", put.method)
        assertEquals("/notes/note-1.md", put.path)
        assertEquals("GET", get.method)
        assertEquals("/notes/note-1.md", get.path)
        assertTrue(put.getHeader("Authorization")!!.startsWith("Basic "))
        assertTrue(put.getHeader("Content-Type")!!.startsWith("text/markdown"))
        assertEquals("# Note\n\ncontent", put.body.readUtf8())
    }

    @Test
    fun listsAllMarkdownFilesInConfiguredDirectory() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(207)
                .setBody(
                    """
                    <d:multistatus xmlns:d="DAV:">
                      <d:response><d:href>/notes/</d:href></d:response>
                      <d:response><d:href>/notes/note-1.md</d:href></d:response>
                      <d:response><d:href>/notes/note-22.md</d:href></d:response>
                      <d:response><d:href>/notes/QQ邮箱登录授权码.md</d:href></d:response>
                      <d:response><d:href>/notes/other.json</d:href></d:response>
                    </d:multistatus>
                    """.trimIndent(),
                ),
        )
        val client = WebDavClient(
            WebDavConfig(server.url("/notes").toString(), "user", "pass"),
        )

        val files = client.listMarkdownFiles()

        assertEquals(
            listOf("note-1.md", "note-22.md", "QQ邮箱登录授权码.md"),
            files.map { it.name },
        )
        val request = server.takeRequest()
        assertEquals("PROPFIND", request.method)
        assertEquals("1", request.getHeader("Depth"))
    }

    @Test
    fun listsFiftyOneMarkdownFilesWithoutTruncation() = runBlocking {
        val body = buildString {
            append("""<d:multistatus xmlns:d="DAV:">""")
            repeat(51) { index ->
                append(
                    """
                    <d:response>
                      <d:href>/notes/记事-$index.md</d:href>
                      <d:propstat><d:prop>
                        <d:getetag>"etag-$index"</d:getetag>
                        <d:getcontentlength>100</d:getcontentlength>
                      </d:prop></d:propstat>
                    </d:response>
                    """.trimIndent(),
                )
            }
            append("</d:multistatus>")
        }
        server.enqueue(MockResponse().setResponseCode(207).setBody(body))
        val client = WebDavClient(
            WebDavConfig(server.url("/notes").toString(), "user", "pass"),
        )

        val files = client.listMarkdownFiles()

        assertEquals(51, files.size)
        assertEquals("\"etag-0\"|100", files.first().fingerprint)
    }
}
