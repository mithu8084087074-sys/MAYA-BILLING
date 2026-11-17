package com.example.util

import java.security.MessageDigest
import java.security.SecureRandom

object SecurityUtils {
    // Pre-computed salted SHA-256 digest of the initial Owner password.
    // Plaintext password is NEVER embedded in frontend source code.
    const val INITIAL_OWNER_SALT = "BharatBill_Owner_Salt_2026"
    const val INITIAL_OWNER_PASSWORD_HASH =
        "b04820202d6193cc263cc8717176cefe0fb4b0ca3bf37d871113c603753e6d73"

    private val random = SecureRandom()

    fun generateSalt(byteLength: Int = 16): String {
        val bytes = ByteArray(byteLength)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hashSecret(secret: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = "$salt:$secret".toByteArray(Charsets.UTF_8)
        return digest.digest(input).joinToString("") { "%02x".format(it) }
    }

    fun verifySecret(input: String, salt: String, expectedHash: String): Boolean {
        if (input.isEmpty() || salt.isEmpty() || expectedHash.isEmpty()) return false
        val actualHash = hashSecret(input, salt)
        return MessageDigest.isEqual(
            actualHash.toByteArray(Charsets.UTF_8),
            expectedHash.toByteArray(Charsets.UTF_8)
        )
    }

    fun generateTwoFactorCode(): String {
        val code = 100000 + random.nextInt(900000)
        return code.toString()
    }
}
