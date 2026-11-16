package com.sclera.sclerago.data.local

import android.content.Context
import com.sclera.sclerago.domain.repository.UserDataCleaner
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Small named texts kept on the device, encrypted, so the last data from the server shows offline. Blocking: call on the IO dispatcher. */
interface EncryptedCache {
    /** The text saved under [name], or null when there is none or it can no longer be decrypted. */
    fun read(name: String): String?

    fun write(name: String, text: String)
}

/**
 * [EncryptedCache] in app-private files (`filesDir/online_cache`), each text sealed with the AES-GCM Keystore
 * [SecretCipher] (the key never leaves the device). It holds contact details, access codes and people's emails,
 * so it is deleted on sign-out; the app is excluded from backups (`allowBackup="false"`).
 */
@Singleton
class EncryptedFileCache @Inject constructor(
    @ApplicationContext context: Context,
    private val cipher: SecretCipher,
) : EncryptedCache, UserDataCleaner {
    private val dir = File(context.filesDir, DIR)

    override fun read(name: String): String? = runCatching { cipher.decrypt(File(dir, "$name.enc").readText()) }.getOrNull()

    override fun write(name: String, text: String) {
        dir.mkdirs()
        val target = File(dir, "$name.enc")
        val temp = File(dir, "$name.enc.tmp")
        temp.writeText(cipher.encrypt(text))
        if (!temp.renameTo(target)) {
            temp.delete()
            throw IOException("cache not saved")
        }
    }

    override fun clear() {
        dir.deleteRecursively()
    }

    private companion object {
        const val DIR = "online_cache"
    }
}
