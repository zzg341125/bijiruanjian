package io.github.latticenotes.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.latticenotes.BuildConfig
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class CredentialVault(context: Context) {
    private val preferences = context.getSharedPreferences("secure_credentials", Context.MODE_PRIVATE)
    private val alias = "quyang_notes_webdav"

    fun saveWebDavPassword(password: String) {
        preferences.edit().apply {
            if (password.isBlank()) {
                remove(PLAIN_PASSWORD_KEY)
            } else {
                putString(PLAIN_PASSWORD_KEY, password)
            }
            remove(LEGACY_IV_KEY)
            remove(LEGACY_CIPHERTEXT_KEY)
        }.apply()
        deleteLegacyKey()
    }

    fun loadWebDavPassword(): String {
        if (preferences.contains(PLAIN_PASSWORD_KEY)) {
            return preferences.getString(PLAIN_PASSWORD_KEY, "").orEmpty()
        }
        return decryptLegacyPassword()
    }

    fun loadWebDavPasswordOrDefault(): String {
        val password = loadWebDavPassword()
        if (!preferences.contains(PLAIN_PASSWORD_KEY) && password.isNotBlank()) {
            saveWebDavPassword(password)
        }
        return password.ifBlank { BuildConfig.DEFAULT_WEBDAV_PASSWORD }
    }

    fun clearWebDavPassword() {
        preferences.edit().clear().apply()
        deleteLegacyKey()
    }

    private fun decryptLegacyPassword(): String {
        val iv = preferences.getString(LEGACY_IV_KEY, null) ?: return ""
        val ciphertext = preferences.getString(LEGACY_CIPHERTEXT_KEY, null) ?: return ""
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)),
            )
            String(
                cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)),
                Charsets.UTF_8,
            )
        }.getOrDefault("")
    }

    private fun deleteLegacyKey() {
        runCatching {
            KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
                if (containsAlias(alias)) deleteEntry(alias)
            }
        }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(alias, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore",
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val PLAIN_PASSWORD_KEY = "webdav_password"
        private const val LEGACY_IV_KEY = "webdav_iv"
        private const val LEGACY_CIPHERTEXT_KEY = "webdav_ciphertext"
    }
}
