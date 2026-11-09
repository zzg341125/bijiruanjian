package io.github.latticenotes.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Base64

data class WebDavConfig(
    val fileUrl: String,
    val username: String,
    val password: String,
) {
    fun validatedUrl(): URL {
        val uri = URI(fileUrl.trim())
        require(uri.scheme == "https" || (uri.scheme == "http" && isLocalHost(uri.host))) {
            "WebDAV 地址必须使用 HTTPS；HTTP 仅允许 localhost、127.0.0.1 或 10.0.2.2"
        }
        require(!uri.host.isNullOrBlank()) { "WebDAV 地址无效" }
        return uri.toURL()
    }

    private fun isLocalHost(host: String?): Boolean =
        host in setOf("localhost", "127.0.0.1", "10.0.2.2")
}

class WebDavClient(private val config: WebDavConfig) {
    suspend fun testConnection() = withContext(Dispatchers.IO) {
        val connection = open("OPTIONS")
        try {
            val code = connection.responseCode
            require(code in 200..399) { "WebDAV 连接失败：HTTP $code" }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun upload(content: String) = withContext(Dispatchers.IO) {
        val body = content.toByteArray(Charsets.UTF_8)
        val connection = open("PUT")
        try {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            require(code in 200..299) { "WebDAV 上传失败：HTTP $code" }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun download(): String = withContext(Dispatchers.IO) {
        val connection = open("GET")
        try {
            val code = connection.responseCode
            require(code in 200..299) { "WebDAV 下载失败：HTTP $code" }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun downloadOrNull(): String? = withContext(Dispatchers.IO) {
        val connection = open("GET")
        try {
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) return@withContext null
            require(code in 200..299) { "WebDAV 下载失败：HTTP $code" }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(method: String): HttpURLConnection {
        val connection = config.validatedUrl().openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "QuyangNotes/0.2.3")
        if (config.username.isNotBlank() || config.password.isNotBlank()) {
            val token = Base64.getEncoder().encodeToString(
                "${config.username}:${config.password}".toByteArray(Charsets.UTF_8),
            )
            connection.setRequestProperty("Authorization", "Basic $token")
        }
        return connection
    }
}
