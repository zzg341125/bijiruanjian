package io.github.latticenotes.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerTest {
    @Test
    fun versionCodeMatchesReleaseWorkflow() {
        assertEquals(10_000, UpdateManager.versionCode("0.10.0"))
        assertEquals(10_001, UpdateManager.versionCode("0.10.1"))
        assertEquals(11_000, UpdateManager.versionCode("0.11.0"))
    }

    @Test
    fun parsesNewerReleaseApkAndDigest() {
        val update = UpdateManager.parseRelease(RELEASE_JSON, 10_000)

        requireNotNull(update)
        assertEquals("0.11.0", update.versionName)
        assertEquals(11_000, update.versionCode)
        assertEquals("QuyangNotes-v0.11.0-release.apk", update.apkName)
        assertEquals("abc123", update.sha256)
        assertTrue(update.releaseNotes.contains("在线更新"))
    }

    @Test
    fun ignoresCurrentRelease() {
        assertNull(UpdateManager.parseRelease(RELEASE_JSON, 11_000))
    }

    @Test
    fun ignoresReleaseWithoutApkAsset() {
        val json = RELEASE_JSON.replace(
            "\"name\": \"QuyangNotes-v0.11.0-release.apk\"",
            "\"name\": \"notes.zip\"",
        )
        assertNull(UpdateManager.parseRelease(json, 10_000))
    }

    private companion object {
        val RELEASE_JSON = """
            {
              "tag_name": "v0.11.0",
              "name": "曲阳便签 v0.11.0",
              "body": "修复编辑器滚动并添加在线更新。",
              "assets": [
                {
                  "name": "QuyangNotes-v0.11.0-release.apk",
                  "browser_download_url": "https://example.com/app.apk",
                  "digest": "sha256:abc123"
                },
                {
                  "name": "SHA256SUMS.txt",
                  "browser_download_url": "https://example.com/SHA256SUMS.txt"
                }
              ]
            }
        """.trimIndent()
    }
}
