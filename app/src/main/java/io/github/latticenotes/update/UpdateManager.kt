package io.github.latticenotes.update

import android.content.Context
import android.content.pm.PackageManager
import io.github.latticenotes.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class AppUpdate(
    val versionName: String,
    val versionCode: Int,
    val releaseName: String,
    val releaseNotes: String,
    val apkName: String,
    val apkUrl: String,
    val sha256: String?,
    val checksumsUrl: String?,
)

object UpdateManager {
    const val RELEASES_API =
        "https://api.github.com/repos/zzg341125/bijiruanjian/releases/latest"
    private const val UPDATE_PREFS = "online_updates"
    private const val LAST_AUTO_CHECK = "last_auto_check"
    private const val AUTO_CHECK_INTERVAL_MILLIS = 24L * 60 * 60 * 1000

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    suspend fun checkForUpdate(
        currentVersionCode: Int = BuildConfig.VERSION_CODE,
    ): AppUpdate? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(RELEASES_API)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "QuyangNotes/${BuildConfig.VERSION_NAME}")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error(
                    if (response.code == 403) {
                        "GitHub 暂时限制了更新检查，请稍后再试"
                    } else {
                        "检查更新失败（HTTP ${response.code}）"
                    },
                )
            }
            parseRelease(response.body?.string() ?: error("更新服务返回空内容"), currentVersionCode)
        }
    }

    internal fun parseRelease(jsonText: String, currentVersionCode: Int): AppUpdate? {
        val release = JSONObject(jsonText)
        val tag = release.optString("tag_name").trim()
        val versionName = tag.removePrefix("v").substringBefore('-')
        val versionCode = versionCode(versionName)
        if (versionCode <= currentVersionCode) return null

        val assets = release.optJSONArray("assets") ?: return null
        var apkName: String? = null
        var apkUrl: String? = null
        var digest: String? = null
        var checksumsUrl: String? = null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url")
            when {
                name.endsWith(".apk", ignoreCase = true) && apkName == null -> {
                    apkName = name
                    apkUrl = url
                    digest = asset.optString("digest")
                        .takeIf { it.startsWith("sha256:", ignoreCase = true) }
                        ?.substringAfter(':')
                }
                name.equals("SHA256SUMS.txt", ignoreCase = true) -> checksumsUrl = url
            }
        }
        return AppUpdate(
            versionName = versionName,
            versionCode = versionCode,
            releaseName = release.optString("name").ifBlank { "曲阳便签 $versionName" },
            releaseNotes = release.optString("body").trim(),
            apkName = apkName ?: return null,
            apkUrl = apkUrl ?: return null,
            sha256 = digest?.lowercase(),
            checksumsUrl = checksumsUrl,
        )
    }

    internal fun versionCode(versionName: String): Int {
        val parts = versionName.split('.').map { it.toIntOrNull() ?: 0 }
        val major = parts.getOrElse(0) { 0 }
        val minor = parts.getOrElse(1) { 0 }
        val patch = parts.getOrElse(2) { 0 }
        return major * 1_000_000 + minor * 1_000 + patch
    }

    suspend fun download(
        context: Context,
        update: AppUpdate,
        onProgress: (Float?) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        directory.listFiles()?.forEach { it.delete() }
        val safeName = update.apkName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val target = File(directory, safeName)
        val partial = File(directory, "$safeName.part")
        val digest = MessageDigest.getInstance("SHA-256")
        val request = Request.Builder()
            .url(update.apkUrl)
            .header("User-Agent", "QuyangNotes/${BuildConfig.VERSION_NAME}")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("下载更新失败（HTTP ${response.code}）")
                val body = response.body ?: error("更新文件内容为空")
                val total = body.contentLength()
                partial.outputStream().buffered().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            downloaded += count
                            onProgress(
                                if (total > 0) {
                                    (downloaded.toFloat() / total).coerceIn(0f, 1f)
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            }
            val actual = digest.digest().toHex()
            val expected = update.sha256 ?: fetchChecksum(update)
            require(actual.equals(expected, ignoreCase = true)) {
                "更新文件校验失败，请稍后重试"
            }
            verifyPackage(context, partial, update)
            if (target.exists()) target.delete()
            check(partial.renameTo(target)) { "无法保存更新文件" }
            target
        } catch (error: Throwable) {
            partial.delete()
            throw error
        }
    }

    private fun fetchChecksum(update: AppUpdate): String {
        val url = update.checksumsUrl ?: error("发布页缺少 SHA-256 校验文件")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "QuyangNotes/${BuildConfig.VERSION_NAME}")
            .build()
        val text = client.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "下载校验文件失败（HTTP ${response.code}）" }
            response.body?.string().orEmpty()
        }
        val checksum = text.lineSequence().firstOrNull { line ->
            line.substringAfterLast(' ').trim().trimStart('*') == update.apkName
        }?.substringBefore(' ')?.trim()
        require(checksum?.matches(Regex("[a-fA-F0-9]{64}")) == true) {
            "校验文件中没有 ${update.apkName} 的 SHA-256"
        }
        return checksum
    }

    private fun verifyPackage(context: Context, file: File, update: AppUpdate) {
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: error("下载的安装包无法读取")
        require(archive.packageName == BuildConfig.APPLICATION_ID) {
            "下载的安装包不属于曲阳便签"
        }
        require(archive.longVersionCode >= update.versionCode) {
            "下载的安装包版本不正确"
        }
        val installed = context.packageManager.getPackageInfo(
            BuildConfig.APPLICATION_ID,
            flags,
        )
        require(signatureHashes(installed) == signatureHashes(archive)) {
            "当前安装包与在线正式版签名不一致；若是调试版，请先导出备份并安装正式版"
        }
    }

    private fun signatureHashes(info: android.content.pm.PackageInfo): Set<String> =
        info.signingInfo?.apkContentsSigners
            ?.map { signature ->
                MessageDigest.getInstance("SHA-256")
                    .digest(signature.toByteArray())
                    .toHex()
            }
            ?.toSet()
            .orEmpty()

    fun shouldAutoCheck(context: Context): Boolean {
        val last = context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
            .getLong(LAST_AUTO_CHECK, 0L)
        return System.currentTimeMillis() - last >= AUTO_CHECK_INTERVAL_MILLIS
    }

    fun markAutoChecked(context: Context) {
        context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(LAST_AUTO_CHECK, System.currentTimeMillis())
            .apply()
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
