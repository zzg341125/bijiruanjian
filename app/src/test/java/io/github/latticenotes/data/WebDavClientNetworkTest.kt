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
        assertEquals("1", propfind.getHeader("Depth"))
        assertEquals("PUT", put.method)
        assertEquals(
            "/notes/note-1.md",
            put.path,
        )
        assertEquals("GET", get.method)
        assertEquals(
            "/notes/note-1.md",
            get.path,
        )
        assertTrue(put.getHeader("Authorization")!!.startsWith("Basic "))
        assertTrue(put.getHeader("Content-Type")!!.startsWith("text/markdown"))
        assertEquals("# Note\n\ncontent", put.body.readUtf8())
    }

    @Test
    fun connect_createsConfiguredSyncFolder() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(201))
        server.enqueue(MockResponse().setResponseCode(207))
        val client = WebDavClient(
            WebDavConfig(
                fileUrl = server.url("/notes").toString(),
                username = "user",
                password = "pass",
            ),
        )

        client.testConnection()

        val propfind = server.takeRequest()
        val mkcol = server.takeRequest()
        val retriedPropfind = server.takeRequest()
        assertEquals("PROPFIND", propfind.method)
        assertEquals("/notes/", propfind.path)
        assertEquals("MKCOL", mkcol.method)
        assertEquals("/notes/", mkcol.path)
        assertEquals("PROPFIND", retriedPropfind.method)
        assertEquals("/notes/", retriedPropfind.path)
    }

    @Test
    fun connect_createsFolderWhenMissingDirectoryReturnsHttp400() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400))
        server.enqueue(MockResponse().setResponseCode(201))
        server.enqueue(MockResponse().setResponseCode(207))
        val client = WebDavClient(
            WebDavConfig(
                fileUrl = server.url("/notes").toString(),
                username = "user",
                password = "pass",
            ),
        )

        client.testConnection()

        assertEquals("PROPFIND", server.takeRequest().method)
        assertEquals("MKCOL", server.takeRequest().method)
        assertEquals("PROPFIND", server.takeRequest().method)
    }

    @Test
    fun propfind_matchesObsidianRequestWithoutXmlBody() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(207))
        val client = WebDavClient(
            WebDavConfig(server.url("/notes").toString(), "user", "pass"),
        )

        client.testConnection()

        val request = server.takeRequest()
        assertEquals("PROPFIND", request.method)
        assertEquals("1", request.getHeader("Depth"))
        assertEquals("text/plain,application/xml", request.getHeader("Accept"))
        assertEquals(0L, request.bodySize)
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

    @Test
    fun followsLinkHeaderPaginationUntilAllMarkdownFilesAreLoaded() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(207)
                .addHeader("Link", "<${server.url("/notes?page=2")}>; rel=\"next\"")
                .setBody(
                    """
                    <d:multistatus xmlns:d="DAV:">
                      <d:response><d:href>/notes/note-1.md</d:href></d:response>
                    </d:multistatus>
                    """.trimIndent(),
                ),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(207)
                .setBody(
                    """
                    <d:multistatus xmlns:d="DAV:">
                      <d:response><d:href>/notes/note-2.md</d:href></d:response>
                      <d:response><d:href>/notes/note-3.md</d:href></d:response>
                    </d:multistatus>
                    """.trimIndent(),
                ),
        )
        val client = WebDavClient(
            WebDavConfig(server.url("/notes").toString(), "user", "pass"),
        )

        val files = client.listMarkdownFiles()

        assertEquals(listOf("note-1.md", "note-2.md", "note-3.md"), files.map { it.name })
        assertEquals(2, client.directoryPageCount)
        assertEquals("/notes/", server.takeRequest().path)
        assertEquals("/notes?page=2", server.takeRequest().path)
    }

    @Test
    fun recursivelyListsMarkdownFilesInSubfolders() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(207)
                .setBody(
                    """
                    <d:multistatus xmlns:d="DAV:">
                      <d:response>
                        <d:href>/notes/root.md</d:href>
                        <d:propstat><d:prop><d:resourcetype/></d:prop></d:propstat>
                      </d:response>
                      <d:response>
                        <d:href>/notes/folder/</d:href>
                        <d:propstat><d:prop>
                          <d:resourcetype><d:collection/></d:resourcetype>
                        </d:prop></d:propstat>
                      </d:response>
                    </d:multistatus>
                    """.trimIndent(),
                ),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(207)
                .setBody(
                    """
                    <d:multistatus xmlns:d="DAV:">
                      <d:response><d:href>/notes/folder/nested.md</d:href></d:response>
                    </d:multistatus>
                    """.trimIndent(),
                ),
        )
        val client = WebDavClient(
            WebDavConfig(server.url("/notes").toString(), "user", "pass"),
        )

        val files = client.listMarkdownFiles()

        assertEquals(listOf("root.md", "folder/nested.md"), files.map { it.name })
        assertEquals("/notes/", server.takeRequest().path)
        assertEquals("/notes/folder/", server.takeRequest().path)
    }

    @Test
    fun listsMarkdownAndPlainTextNotesButReportsBinarySkippedFiles() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(207)
                .setBody(
                    """
                    <d:multistatus xmlns:d="DAV:">
                      <d:response><d:href>/notes/note.md</d:href></d:response>
                      <d:response><d:href>/notes/readme.markdown</d:href></d:response>
                      <d:response><d:href>/notes/plain.txt</d:href></d:response>
                      <d:response><d:href>/notes/photo.png</d:href></d:response>
                    </d:multistatus>
                    """.trimIndent(),
                ),
        )
        val client = WebDavClient(
            WebDavConfig(server.url("/notes").toString(), "user", "pass"),
        )

        val files = client.listMarkdownFiles()

        assertEquals(
            listOf("note.md", "readme.markdown", "plain.txt"),
            files.map { it.name },
        )
        assertEquals(listOf("photo.png"), client.binaryFiles.map { it.name })
        assertEquals(1, client.skippedNonNoteFileCount)
    }

    @Test
    fun uploadsFiftyOneFilesInOneBatchAndRefreshesRemoteFingerprints() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(207))
        repeat(51) {
            server.enqueue(MockResponse().setResponseCode(201))
        }
        val remoteBody = buildString {
            append("""<d:multistatus xmlns:d="DAV:">""")
            repeat(51) { index ->
                append(
                    """
                    <d:response>
                      <d:href>/notes/note-$index.md</d:href>
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
        server.enqueue(MockResponse().setResponseCode(207).setBody(remoteBody))
        val client = WebDavClient(
            config = WebDavConfig(server.url("/notes").toString(), "user", "pass"),
            requestSpacingMillis = 0,
        )

        val result = client.uploadMarkdownFiles(
            (0 until 51).associate { index -> "note-$index.md" to "# 记事 $index" },
        )

        assertEquals(51, result.uploadedCount)
        assertTrue(result.failedNames.isEmpty())
        assertEquals("\"etag-49\"|100", result.remoteFiles["note-49.md"]?.fingerprint)
        assertEquals("PROPFIND", server.takeRequest().method)
        repeat(51) {
            assertEquals("PUT", server.takeRequest().method)
        }
        assertEquals("PROPFIND", server.takeRequest().method)
    }

    @Test
    fun batchUploadStopsAfterRateLimitAndMarksRemainingFilesForRetry() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(207))
        server.enqueue(MockResponse().setResponseCode(429))
        val client = WebDavClient(
            config = WebDavConfig(server.url("/notes").toString(), "user", "pass"),
            requestSpacingMillis = 0,
        )

        val result = client.uploadMarkdownFiles(
            linkedMapOf(
                "note-1.md" to "# 一",
                "note-2.md" to "# 二",
            ),
        )

        assertEquals(setOf("note-1.md", "note-2.md"), result.failedNames)
        assertEquals(0, result.uploadedCount)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun batchDownloadStopsAfterRateLimitAndMarksRemainingFilesForRetry() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429))
        val client = WebDavClient(
            config = WebDavConfig(server.url("/notes").toString(), "user", "pass"),
            requestSpacingMillis = 0,
        )
        val files = listOf(
            WebDavRemoteFile("note-1.md", client.markdownFileUrl("note-1.md")),
            WebDavRemoteFile("note-2.md", client.markdownFileUrl("note-2.md")),
        )

        val result = client.downloadMarkdownFiles(files)

        assertEquals(setOf("note-1.md", "note-2.md"), result.failedNames)
        assertTrue(result.contents.isEmpty())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun deletesRemoteMarkdownFileUsingWebDavDelete() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        val client = WebDavClient(
            config = WebDavConfig(server.url("/notes").toString(), "user", "pass"),
            requestSpacingMillis = 0,
        )

        client.deleteFile(client.markdownFileUrl("note-1.md"))

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals(
            "/notes/note-1.md",
            request.path,
        )
    }

    @Test
    fun directoryServiceUnavailable_returnsTypedRetryInformation() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(503)
                .addHeader("Retry-After", "120"),
        )
        val client = WebDavClient(
            WebDavConfig(server.url("/notes").toString(), "user", "pass"),
        )

        val error = try {
            client.testConnection()
            null
        } catch (caught: WebDavHttpException) {
            caught
        }

        assertEquals(503, error?.code)
        assertEquals(120L, error?.retryAfterSeconds)
    }
}
