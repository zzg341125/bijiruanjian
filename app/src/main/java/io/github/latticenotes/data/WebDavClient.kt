package io.github.latticenotes.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

data class WebDavConfig(
    val fileUrl: String,
    val username: String,
    val password: String,
) {
    fun directoryUrl(): URL {
        val uri = URI(fileUrl.trim())
        require(uri.scheme == "https" || (uri.scheme == "http" && isPrivateHost(uri.host))) {
            "WebDAV 地址必须使用 HTTPS；局域网调试可使用私有 IP"
        }
        require(!uri.host.isNullOrBlank()) { "WebDAV 地址无效" }
        require(uri.query == null && uri.fragment == null) {
            "WebDAV 地址不能包含查询参数或片段"
        }
        val path = uri.path.orEmpty()
        val directoryPath = if (path.endsWith("/")) path else "$path/"
        return URI(
            uri.scheme,
            uri.authority,
            directoryPath,
            null,
            null,
        ).toURL()
    }

    private fun isPrivateHost(host: String?): Boolean {
        if (host in setOf("localhost", "127.0.0.1", "10.0.2.2")) return true
        val parts = host?.split('.')?.mapNotNull(String::toIntOrNull) ?: return false
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        return parts[0] == 10 ||
            (parts[0] == 192 && parts[1] == 168) ||
            (parts[0] == 172 && parts[1] in 16..31)
    }

}

class WebDavClient(private val config: WebDavConfig) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun testConnection() = withContext(Dispatchers.IO) {
        val request = authenticatedRequest(config.directoryUrl())
            .header("Depth", "0")
            .header("Accept", "application/xml, text/xml")
            .method("PROPFIND", PROPFIND_BODY.toRequestBody(XML_MEDIA_TYPE))
            .build()
        client.newCall(request).execute().use { response ->
            require(response.isSuccessful) {
                when (response.code) {
                    401, 403 -> "WebDAV 认证失败：HTTP ${response.code}。请检查用户名和应用密码。"
                    else -> "WebDAV 连接失败：HTTP ${response.code}"
                }
            }
        }
    }

    suspend fun uploadTo(url: URL, content: String) = withContext(Dispatchers.IO) {
        val request = authenticatedRequest(url)
            .put(content.toRequestBody(MARKDOWN_MEDIA_TYPE))
            .build()
        client.newCall(request).execute().use { response ->
            require(response.isSuccessful) {
                if (response.code == 403) {
                    "WebDAV 上传失败：HTTP 403。请检查应用密码及目录写入权限。"
                } else {
                    "WebDAV 上传失败：HTTP ${response.code}"
                }
            }
        }
    }

    suspend fun downloadFrom(url: URL): String = withContext(Dispatchers.IO) {
        val request = authenticatedRequest(url).get().build()
        client.newCall(request).execute().use { response ->
            require(response.isSuccessful) {
                if (response.code == 403) {
                    "WebDAV 下载失败：HTTP 403。请检查应用密码及目录读取权限。"
                } else {
                    "WebDAV 下载失败：HTTP ${response.code}"
                }
            }
            response.body?.string() ?: error("WebDAV 下载内容为空")
        }
    }

    suspend fun listMarkdownFiles(): List<WebDavRemoteFile> = withContext(Dispatchers.IO) {
        val directory = config.directoryUrl()
        val request = authenticatedRequest(directory)
            .header("Depth", "1")
            .header("Accept", "application/xml, text/xml")
            .method("PROPFIND", PROPFIND_BODY.toRequestBody(XML_MEDIA_TYPE))
            .build()
        client.newCall(request).execute().use { response ->
            require(response.isSuccessful) {
                if (response.code == 401 || response.code == 403) {
                    "WebDAV 认证失败：HTTP ${response.code}。请检查用户名和应用密码。"
                } else {
                    "WebDAV 目录读取失败：HTTP ${response.code}"
                }
            }
            val body = response.body?.string().orEmpty()
            responsePattern.findAll(body).mapNotNull { responseMatch ->
                val block = responseMatch.groupValues[1]
                val hrefMatch = hrefPattern.find(block) ?: return@mapNotNull null
                val href = decodeXml(hrefMatch.groupValues[1])
                val path = runCatching {
                    URLDecoder.decode(URI(href).path.orEmpty(), Charsets.UTF_8.name())
                }.getOrDefault(URI(href).path.orEmpty())
                val fileName = path.substringAfterLast('/')
                if (!fileName.endsWith(".md", ignoreCase = true)) {
                    return@mapNotNull null
                }
                WebDavRemoteFile(
                    name = fileName,
                    url = directory.toURI().resolve(href).toURL(),
                    fingerprint = listOfNotNull(
                        propertyValue(block, etagPattern),
                        propertyValue(block, lastModifiedPattern),
                        propertyValue(block, contentLengthPattern),
                    ).joinToString("|").ifBlank { fileName },
                )
            }.toList()
        }
    }

    fun markdownFileUrl(fileName: String): URL =
        config.directoryUrl().toURI().resolve(fileName).toURL()

    private fun authenticatedRequest(url: URL): Request.Builder =
        Request.Builder()
            .url(url)
            .header("User-Agent", "QuyangNotes/0.5.1")
            .apply {
                if (config.username.isNotBlank() || config.password.isNotBlank()) {
                    header("Authorization", Credentials.basic(config.username, config.password))
                }
            }

    private companion object {
        val MARKDOWN_MEDIA_TYPE = "text/markdown; charset=utf-8".toMediaType()
        val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaType()
        const val PROPFIND_BODY = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:">
              <d:prop>
                <d:getetag/>
                <d:getlastmodified/>
                <d:getcontentlength/>
                <d:resourcetype/>
              </d:prop>
            </d:propfind>
        """
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
    }
}

data class WebDavRemoteFile(
    val name: String,
    val url: URL,
    val fingerprint: String = "",
)
