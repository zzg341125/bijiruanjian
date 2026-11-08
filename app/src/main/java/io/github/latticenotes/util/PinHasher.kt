package io.github.latticenotes.util

import java.security.SecureRandom
import java.security.MessageDigest
import java.security.spec.KeySpec
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PinHasher {
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256

    fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun hash(pin: String, salt: String): String {
        val saltBytes = Base64.getDecoder().decode(salt)
        val spec: KeySpec = PBEKeySpec(pin.toCharArray(), saltBytes, ITERATIONS, KEY_BITS)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec)
            .encoded
        return Base64.getEncoder().encodeToString(key)
    }

    fun verify(pin: String, salt: String, expected: String): Boolean = runCatching {
        MessageDigest.isEqual(
            Base64.getDecoder().decode(hash(pin, salt)),
            Base64.getDecoder().decode(expected),
        )
    }.getOrDefault(false)
}
