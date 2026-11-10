package io.github.latticenotes.data

import io.github.latticenotes.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

private const val SYNC_FOLDER_NAME = "曲阳便签"

data class WebDavConfig(
    val fileUrl: String,
    val username: String,
    val password: String,
) {
    fun directoryUrl(): URL {
        val normalized = normalizeUrlInput(fileUrl)
        val base = normalized.toHttpUrlOrNull()
            ?: throw IllegalArgumentException(
                "WebDAV 地址格式不正确，请填写完整地址，例如 https://dav.jianguoyun.com/dav/",
            )
        require(base.scheme == "https" || (base.scheme == "http" && isPrivateHost(base.host))) {
            "WebDAV 地址必须使用 HTTPS；局域网调试可使用私有 IP"
        }
        require(base.query == null && base.fragment == null) {
            "WebDAV 地址不能包含查询参数或片段"
        }
        val isJianguoyunRoot = base.host.equals("dav.jianguoyun.com", ignoreCase = true) &&
            base.encodedPath.trimEnd('/') == "/dav"
        val lastSegment = base.pathSegments.lastOrNull { it.isNotBlank() }
        val directory = if (!isJianguoyunRoot || lastSegment == SYNC_FOLDER_NAME) {
            val builder = base.newBuilder()
            if (!base.encodedPath.endsWith("/")) builder.addPathSegment("")
            builder.build()
        } else {
            base.newBuilder()
                .addPathSegment(SYNC_FOLDER_NAME)
                .addPathSegment("")
                .build()
        }
        return directory.toUrl()
    }

    private fun normalizeUrlInput(value: String): String {
        var normalized = value
            .replace('\u00A0', ' ')
            .filterNot { it in INVISIBLE_URL_CHARACTERS }
            .trim()
        if (normalized.startsWith("[") && "](" in normalized) {
            normalized = normalized.substringAfter("](").substringBefore(")")
        }
        normalized = normalized.trim('<', '>', '"', '\'')
        if (!normalized.startsWith("https://", ignoreCase = true) &&
            !normalized.startsWith("http://", ignoreCase = true)
        ) {
            normalized = "https://$normalized"
        }
        return normalized
    }

    private fun isPrivateHost(host: String?): Boolean {
        if (host in setOf("localhost", "127.0.0.1", "10.0.2.2")) return true
        val parts = host?.split('.')?.mapNotNull(String::toIntOrNull) ?: return false
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        return parts[0] == 10 ||
            (parts[0] == 192 && parts[1] == 168) ||
            (parts[0] == 172 && parts[1] in 16..31)
    }

    private companion object {
        val INVISIBLE_URL_CHARACTERS = setOf(
            '\uFEFF',
            '\u200B',
            '\u200C',
            '\u200D',
            '\u200E',
            '\u200F',
            '\u2060',
        )
    }

}

class WebDavClient(
    private val config: WebDavConfig,
    private val requestSpacingMillis: Long = 150,
) {
    var directoryPageCount: Int = 0
        private set
    var skippedNonNoteFileCount: Int = 0
        private set
    var binaryFiles: List<WebDavRemoteFile> = emptyList()
        private set

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun testConnection() = withContext(Dispatchers.IO) {
        loadDirectoryListing()
    }

    suspend fun uploadTo(url: URL, content: String) = withContext(Dispatchers.IO) {
        val request = authenticatedRequest(url)
            .put(content.toRequestBody(MARKDOWN_MEDIA_TYPE))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val message = if (response.code == 403) {
                    "WebDAV 上传失败：HTTP 403。请检查应用密码及目录写入权限。"
                } else {
                    "WebDAV 上传失败：HTTP ${response.code}"
                }
                throw WebDavHttpException(
                    response.code,
                    message,
                    response.header("Retry-After")?.toLongOrNull(),
                )
            }
        }
    }

    suspend fun uploadBytes(
        url: URL,
        bytes: ByteArray,
        mimeType: String?,
    ) = withContext(Dispatchers.IO) {
        val mediaType = (mimeType?.takeIf { it.isNotBlank() }
            ?: "application/octet-stream").toMediaType()
        val request = authenticatedRequest(url)
            .put(bytes.toRequestBody(mediaType))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw WebDavHttpException(
                    response.code,
                    "WebDAV 文件上传失败：HTTP ${response.code}",
                    response.header("Retry-After")?.toLongOrNull(),
                )
            }
        }
    }

    suspend fun uploadMarkdownFiles(
        files: Map<String, String>,
    ): WebDavBatchUploadResult = withContext(Dispatchers.IO) {
        if (files.isEmpty()) return@withContext WebDavBatchUploadResult()

        loadDirectoryListing()
        val failedNames = linkedSetOf<String>()
        var uploadedCount = 0
        var rateLimited = false
        files.forEach { (name, content) ->
            if (rateLimited) {
                failedNames += name
                return@forEach
            }
            try {
                uploadTo(markdownFileUrl(name), content)
                uploadedCount++
            } catch (error: Throwable) {
                failedNames += name
                if (error is WebDavHttpException && error.code == 429) {
                    rateLimited = true
                }
            }
            if (requestSpacingMillis > 0) delay(requestSpacingMillis)
        }

        val refreshedFiles = if (uploadedCount > 0) {
            runCatching { listMarkdownFiles() }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        WebDavBatchUploadResult(
            uploadedCount = uploadedCount,
            failedNames = failedNames,
            remoteFiles = refreshedFiles.associateBy { it.name },
        )
    }

    suspend fun downloadFrom(url: URL): String = withContext(Dispatchers.IO) {
        val request = authenticatedRequest(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val message = if (response.code == 403) {
                    "WebDAV 下载失败：HTTP 403。请检查应用密码及目录读取权限。"
                } else {
                    "WebDAV 下载失败：HTTP ${response.code}"
                }
                throw WebDavHttpException(
                    response.code,
                    message,
                    response.header("Retry-After")?.toLongOrNull(),
                )
            }
            response.body?.string() ?: error("WebDAV 下载内容为空")
        }
    }

    suspend fun downloadBytes(url: URL): ByteArray = withContext(Dispatchers.IO) {
        val request = authenticatedRequest(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw WebDavHttpException(
                    response.code,
                    "WebDAV 文件下载失败：HTTP ${response.code}",
                    response.header("Retry-After")?.toLongOrNull(),
                )
            }
            response.body?.bytes() ?: throw IllegalStateException("WebDAV 文件内容为空")
        }
    }

    suspend fun deleteFile(url: URL) = withContext(Dispatchers.IO) {
        val request = authenticatedRequest(url).delete().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 404) {
                throw WebDavHttpException(
                    response.code,
                    "WebDAV 删除失败：HTTP ${response.code}",
                    response.header("Retry-After")?.toLongOrNull(),
                )
            }
        }
    }

    suspend fun downloadMarkdownFiles(
        files: List<WebDavRemoteFile>,
    ): WebDavBatchDownloadResult = withContext(Dispatchers.IO) {
        val contents = linkedMapOf<String, String>()
        val failedNames = linkedSetOf<String>()
        var rateLimited = false
        files.forEach { file ->
            if (rateLimited) {
                failedNames += file.name
                return@forEach
            }
            try {
                contents[file.name] = downloadFrom(file.url)
            } catch (error: Throwable) {
                failedNames += file.name
                if (error is WebDavHttpException && error.code == 429) {
                    rateLimited = true
                }
            }
            if (requestSpacingMillis > 0) delay(requestSpacingMillis)
        }
        WebDavBatchDownloadResult(contents, failedNames)
    }

    suspend fun listMarkdownFiles(): List<WebDavRemoteFile> = withContext(Dispatchers.IO) {
        val baseDirectory = config.directoryUrl()
        val queue = ArrayDeque<URL>().apply { add(baseDirectory) }
        val visitedDirectories = mutableSetOf<String>()
        val files = mutableListOf<WebDavRemoteFile>()
        val binaryFiles = mutableListOf<WebDavRemoteFile>()
        var scannedPages = 0
        var skippedFiles = 0
        this@WebDavClient.binaryFiles = emptyList()
        while (queue.isNotEmpty()) {
            val directory = queue.removeFirst()
            if (!visitedDirectories.add(directory.toString())) continue
            val pages = loadDirectoryListings(
                directory = directory,
                createIfMissing = directory == baseDirectory,
            )
            if (pages.isEmpty()) continue
            scannedPages += pages.size
            pages.forEach { body ->
                responsePattern.findAll(body).forEach entryLoop@ { responseMatch ->
                    val block = responseMatch.groupValues[1]
                    val hrefMatch = hrefPattern.find(block) ?: return@entryLoop
                    val href = decodeXml(hrefMatch.groupValues[1])
                    val entryUrl = directory.toURI().resolve(href).toURL()
                    requireSameServer(entryUrl, baseDirectory)
                    val isDirectory = href.endsWith("/") ||
                        collectionPattern.containsMatchIn(block)
                    if (isDirectory) {
                        if (entryUrl.toString() != directory.toString()) {
                            queue.add(entryUrl)
                        }
                        return@entryLoop
                    }
                    val entryPath = entryUrl.path
                    val contentType = propertyValue(block, contentTypePattern)
                        ?.substringBefore(';')
                        ?.trim()
                        ?.lowercase()
                    val isTextNote = entryPath.endsWith(".md", ignoreCase = true) ||
                        entryPath.endsWith(".markdown", ignoreCase = true) ||
                        entryPath.endsWith(".txt", ignoreCase = true) ||
                        contentType == "text/markdown" ||
                        contentType == "text/plain"
                    val relativePath = runCatching {
                        URLDecoder.decode(
                            baseDirectory.toURI().relativize(entryUrl.toURI()).path,
                            Charsets.UTF_8.name(),
                        )
                    }.getOrNull().orEmpty()
                    if (
                        relativePath.isBlank() ||
                        relativePath.startsWith("../") ||
                        relativePath == ".."
                    ) {
                        return@entryLoop
                    }
                    val remoteFile = WebDavRemoteFile(
                        name = relativePath,
                        url = entryUrl,
                        fingerprint = listOfNotNull(
                            propertyValue(block, etagPattern),
                            propertyValue(block, lastModifiedPattern),
                            propertyValue(block, contentLengthPattern),
                        ).joinToString("|").ifBlank { relativePath },
                    )
                    if (isTextNote) {
                        files += remoteFile
                    } else {
                        binaryFiles += remoteFile
                        skippedFiles++
                    }
                }
            }
        }
        directoryPageCount = scannedPages
        skippedNonNoteFileCount = skippedFiles
        this@WebDavClient.binaryFiles = binaryFiles.distinctBy { it.name }
        files.distinctBy { it.name }
    }

    fun markdownFileUrl(fileName: String): URL =
        config.directoryUrl().toURI().resolve(fileName).toURL()

    private fun loadDirectoryListing(): String {
        val directory = config.directoryUrl()
        return loadFirstDirectoryResponse(directory, createIfMissing = true).body
    }

    private fun loadDirectoryListings(
        directory: URL,
        createIfMissing: Boolean,
    ): List<String> {
        val pages = mutableListOf<String>()
        val visitedUrls = mutableSetOf<String>()
        val baseUrl = config.directoryUrl()
        var current = loadFirstDirectoryResponse(directory, createIfMissing)
        if (!current.successful) {
            if (current.code == 400 || current.code == 404) return emptyList()
            throw WebDavHttpException(
                current.code,
                directoryErrorMessage(current.code),
                current.retryAfterSeconds,
            )
        }
        while (true) {
            pages += current.body
            val nextUrl = current.nextUrl ?: break
            requireSameServer(nextUrl, baseUrl)
            require(visitedUrls.add(nextUrl.toString())) {
                "WebDAV 分页地址发生循环：$nextUrl"
            }
            require(pages.size < MAX_DIRECTORY_PAGES) {
                "WebDAV 分页数量超过安全上限"
            }
            current = propfind(nextUrl)
            require(current.successful) {
                throw WebDavHttpException(
                    current.code,
                    "WebDAV 分页读取失败：HTTP ${current.code}",
                    current.retryAfterSeconds,
                )
            }
        }
        return pages
    }

    private fun loadFirstDirectoryResponse(
        directory: URL,
        createIfMissing: Boolean,
    ): DirectoryResponse {
        val first = propfind(directory)
        if (first.successful) return first

        return when (first.code) {
            400, 404 -> {
                if (!createIfMissing) return first
                createDirectory(directory)
                propfind(directory).also { retried ->
                    require(retried.successful) {
                        directoryErrorMessage(retried.code)
                    }
                }
            }
            else -> throw WebDavHttpException(
                first.code,
                directoryErrorMessage(first.code),
                first.retryAfterSeconds,
            )
        }
    }

    private fun requireSameServer(url: URL, baseUrl: URL) {
        require(
            url.protocol == baseUrl.protocol &&
                url.host == baseUrl.host &&
                url.port == baseUrl.port,
        ) {
            "WebDAV 地址与同步服务器不一致"
        }
    }

    private fun propfind(directory: URL): DirectoryResponse {
        val request = authenticatedRequest(directory)
            .header("Depth", "1")
            .header("Accept", "text/plain,application/xml")
            .method("PROPFIND", null)
            .build()
        return client.newCall(request).execute().use { response ->
            DirectoryResponse(
                successful = response.isSuccessful,
                body = response.body?.string().orEmpty(),
                code = response.code,
                nextUrl = parseNextLink(
                    response.headers("Link").joinToString(","),
                    directory,
                ),
                retryAfterSeconds = response.header("Retry-After")?.toLongOrNull(),
            )
        }
    }

    private fun createDirectory(directory: URL) {
        val request = authenticatedRequest(directory)
            .method("MKCOL", ByteArray(0).toRequestBody(null))
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code !in setOf(200, 201, 405)) {
                val message = when (response.code) {
                    401, 403 -> "WebDAV 认证失败：HTTP ${response.code}。请检查用户名和应用密码。"
                    else -> "无法创建 WebDAV 同步文件夹：HTTP ${response.code}"
                }
                throw WebDavHttpException(
                    response.code,
                    message,
                    response.header("Retry-After")?.toLongOrNull(),
                )
            }
        }
    }

    private fun directoryErrorMessage(code: Int): String = when (code) {
        400 -> "WebDAV 目录读取失败：HTTP 400。请确认地址是网盘的 WebDAV 根地址，例如 https://dav.jianguoyun.com/dav/"
        401, 403 -> "WebDAV 认证失败：HTTP $code。请检查用户名和应用密码。"
        404 -> "WebDAV 目录不存在且无法创建：HTTP 404"
        409 -> "WebDAV 上级目录不存在：HTTP 409"
        429 -> "WebDAV 请求过于频繁：HTTP 429，请稍后再试"
        503 -> "WebDAV 服务暂时不可用：HTTP 503。坚果云可能正在限流，请等待后重试"
        else -> "WebDAV 目录读取失败：HTTP $code"
    }

    private fun authenticatedRequest(url: URL): Request.Builder =
        Request.Builder()
            .url(url)
            .header("User-Agent", "QuyangNotes/${BuildConfig.VERSION_NAME}")
            .apply {
                if (config.username.isNotBlank() || config.password.isNotBlank()) {
                    header("Authorization", Credentials.basic(config.username, config.password))
                }
            }

    private companion object {
        val MARKDOWN_MEDIA_TYPE = "text/markdown; charset=utf-8".toMediaType()
        val hrefPattern = Regex(
            """<(?:[A-Za-z0-9_-]+:)?href[^>]*>(.*?)</(?:[A-Za-z0-9_-]+:)?href>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val responsePattern = Regex(
            """<(?:[A-Za-z0-9_-]+:)?response[^>]*>(.*?)</(?:[A-Za-z0-9_-]+:)?response>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val etagPattern = tagPattern("getetag")
        val lastModifiedPattern = tagPattern("getlastmodified")
        val contentLengthPattern = tagPattern("getcontentlength")
        val contentTypePattern = tagPattern("getcontenttype")
        val collectionPattern = Regex(
            """<(?:[A-Za-z0-9_-]+:)?collection(?:\s[^>]*)?>""",
            RegexOption.IGNORE_CASE,
        )
        val linkEntryPattern = Regex("""<\s*([^>]+)\s*>\s*;\s*([^,<]+)""")
        val relPattern = Regex("""rel\s*=\s*"?([^";,]+)""", RegexOption.IGNORE_CASE)
        const val MAX_DIRECTORY_PAGES = 1_000

        fun tagPattern(name: String): Regex = Regex(
            """<(?:[A-Za-z0-9_-]+:)?$name[^>]*>(.*?)</(?:[A-Za-z0-9_-]+:)?$name>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )

        fun propertyValue(block: String, pattern: Regex): String? =
            pattern.find(block)?.groupValues?.get(1)?.let(::decodeXml)?.trim()

        fun decodeXml(value: String): String = value
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")

        fun parseNextLink(header: String, currentUrl: URL): URL? {
            if (header.isBlank()) return null
            return linkEntryPattern.findAll(header).firstNotNullOfOrNull { match ->
                val rel = relPattern.find(match.groupValues[2])
                    ?.groupValues
                    ?.get(1)
                    ?.split(Regex("\\s+"))
                    ?.any { it.equals("next", ignoreCase = true) }
                    ?: false
                if (!rel) return@firstNotNullOfOrNull null
                val target = URI(match.groupValues[1].trim())
                if (target.isAbsolute) target.toURL() else currentUrl.toURI().resolve(target).toURL()
            }
        }
    }
}

data class WebDavRemoteFile(
    val name: String,
    val url: URL,
    val fingerprint: String = "",
)

data class WebDavBatchUploadResult(
    val uploadedCount: Int = 0,
    val failedNames: Set<String> = emptySet(),
    val remoteFiles: Map<String, WebDavRemoteFile> = emptyMap(),
)

data class WebDavBatchDownloadResult(
    val contents: Map<String, String> = emptyMap(),
    val failedNames: Set<String> = emptySet(),
)

class WebDavHttpException(
    val code: Int,
    override val message: String,
    val retryAfterSeconds: Long? = null,
) : IllegalStateException(message)

private data class DirectoryResponse(
    val successful: Boolean,
    val body: String,
    val code: Int,
    val nextUrl: URL? = null,
    val retryAfterSeconds: Long? = null,
)
