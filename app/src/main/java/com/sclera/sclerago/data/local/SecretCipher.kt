package com.sclera.sclerago.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts the small secrets the app keeps on the device (tokens, session ids, user details). */
interface SecretCipher {
    /** Encrypted, printable form of [plain]; only [decrypt] can read it. */
    fun encrypt(plain: String): String

    /** The text [encrypt] was given, or null when [token] isn't ours or can no longer be read (e.g. the key was lost). */
    fun decrypt(token: String): String?
}

/**
 * AES-256-GCM with a random 12-byte IV per message; the output is `Base64(iv || ciphertext+tag)`.
 * The key comes from [keyProvider], so tests can supply an in-memory key and the app a Keystore one.
 */
class AesGcmSecretCipher(private val keyProvider: () -> SecretKey) : SecretCipher {
    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, keyProvider()) }
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + body, Base64.NO_WRAP)
    }

    override fun decrypt(token: String): String? = runCatching {
        val bytes = Base64.decode(token, Base64.NO_WRAP)
        require(bytes.size > IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, keyProvider(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        }
        String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
    }.getOrNull()

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

/** A non-exportable AES key held by the Android Keystore (hardware-backed where the device has it). */
class KeystoreKeyProvider(private val alias: String = DEFAULT_ALIAS) : () -> SecretKey {
    override fun invoke(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply { init(spec) }.generateKey()
    }

    companion object {
        const val DEFAULT_ALIAS = "sclera_auth_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}

/** The cipher the app uses: AES-GCM with a Keystore key. */
fun keystoreSecretCipher(): SecretCipher = AesGcmSecretCipher(KeystoreKeyProvider())
