package com.sclera.sclerago.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sclera.sclerago.testutil.newTestKey
import com.sclera.sclerago.testutil.testCipher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** MASVS-CRYPTO: AES-256-GCM, random IV per message, tampering and wrong keys are detected. */
@RunWith(AndroidJUnit4::class)
class SecretCipherTest {
    private val cipher = testCipher()

    @Test
    fun encrypt_thenDecrypt_roundTrips() {
        listOf("", "token-123", "ünïcødé ✓ 漢字", "x".repeat(10_000)).forEach {
            assertEquals(it, cipher.decrypt(cipher.encrypt(it)))
        }
    }

    @Test
    fun ciphertext_doesNotContainThePlainText() {
        val secret = "super-secret-access-token"
        assertFalse(cipher.encrypt(secret).contains(secret))
    }

    @Test
    fun sameText_encryptsDifferentlyEachTime() {
        assertNotEquals(cipher.encrypt("same"), cipher.encrypt("same"))
    }

    @Test
    fun tamperedCiphertext_isRejected() {
        val token = cipher.encrypt("hello")
        val bytes = android.util.Base64.decode(token, android.util.Base64.NO_WRAP)
        bytes[bytes.size - 1] = (bytes.last().toInt() xor 1).toByte()
        assertNull(cipher.decrypt(android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)))
    }

    @Test
    fun otherKey_cannotDecrypt() {
        val token = cipher.encrypt("hello")
        assertNull(testCipher(newTestKey()).decrypt(token))
    }

    @Test
    fun garbage_isRejectedWithoutThrowing() {
        listOf("", "not base64!!", "AAAA", "short").forEach { assertNull(it, cipher.decrypt(it)) }
    }
}
