package io.github.latticenotes.backup

import io.github.latticenotes.data.BackupPayload
import org.json.JSONObject
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object EncryptedBackupCodec {
    private const val FORMAT = "quyang-encrypted-backup"
    private const val VERSION = 1
    private const val KDF = "PBKDF2WithHmacSHA256"
    private const val CIPHER = "AES/GCM/NoPadding"
    private const val KEY_BITS = 256
    private const val ITERATIONS = 210_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private val additionalData = "曲阳便签完整备份:v1".toByteArray(Charsets.UTF_8)
    private val secureRandom = SecureRandom()

    fun encode(payload: BackupPayload, password: String): ByteArray {
        require(password.length >= 8) { "备份密码至少需要 8 个字符" }
        val salt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
        val iv = ByteArray(IV_BYTES).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(CIPHER).apply {
            init(
                Cipher.ENCRYPT_MODE,
                deriveKey(password, salt),
                GCMParameterSpec(TAG_BITS, iv),
            )
            updateAAD(additionalData)
        }
        val ciphertext = cipher.doFinal(
            BackupManager.encode(payload).toByteArray(Charsets.UTF_8),
        )
        return JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("kdf", KDF)
            put("iterations", ITERATIONS)
            put("salt", Base64.getEncoder().encodeToString(salt))
            put("iv", Base64.getEncoder().encodeToString(iv))
            put("ciphertext", Base64.getEncoder().encodeToString(ciphertext))
        }.toString(2).toByteArray(Charsets.UTF_8)
    }

    fun decode(data: ByteArray, password: String): BackupPayload {
        val envelope = JSONObject(data.toString(Charsets.UTF_8))
        require(envelope.getString("format") == FORMAT) { "不是曲阳便签加密备份" }
        require(envelope.getInt("version") == VERSION) { "不支持的备份版本" }
        require(envelope.getString("kdf") == KDF) { "不支持的密钥派生方式" }
        require(envelope.getInt("iterations") == ITERATIONS) { "不支持的备份加密参数" }

        val salt = Base64.getDecoder().decode(envelope.getString("salt"))
        val iv = Base64.getDecoder().decode(envelope.getString("iv"))
        val ciphertext = Base64.getDecoder().decode(envelope.getString("ciphertext"))
        require(salt.size == SALT_BYTES && iv.size == IV_BYTES) { "备份加密参数无效" }

        val cipher = Cipher.getInstance(CIPHER).apply {
            init(
                Cipher.DECRYPT_MODE,
                deriveKey(password, salt),
                GCMParameterSpec(TAG_BITS, iv),
            )
            updateAAD(additionalData)
        }
        val plaintext = try {
            cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
        } catch (error: GeneralSecurityException) {
            throw IllegalArgumentException("备份密码不正确或文件已损坏", error)
        }
        return BackupManager.decode(plaintext)
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            val encoded = SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
            SecretKeySpec(encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
