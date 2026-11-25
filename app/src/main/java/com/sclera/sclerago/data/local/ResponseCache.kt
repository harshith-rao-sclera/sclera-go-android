package com.sclera.sclerago.data.local

import android.content.Context
import com.sclera.sclerago.domain.repository.UserDataCleaner
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The last answer the server gave to each read, so it can be shown when there is no connection. Keyed by a request's own
 * key (the signed-in user, the method and the URL); what is kept for one key is never seen under another. Blocking: call
 * off the main thread.
 */
interface ResponseCache {
    /** The text last saved for [key], or null when there is none or it can no longer be decrypted. */
    fun read(key: String): String?

    /** Saves [text] as the answer for [key], replacing the one before. */
    fun write(key: String, text: String)
}

/**
 * [ResponseCache] in app-private files (`filesDir/online_cache/responses`, next to [EncryptedFileCache]'s texts, and
 * wiped with them), each answer sealed with the same AES-GCM Keystore [SecretCipher] (the key never leaves the device).
 * The answers hold access codes, contacts, people and locations, so: each file is named by a SHA-256 of its key (no ids or
 * URLs on disk), everything goes on sign-out, and the app is excluded from backups (`allowBackup="false"`, backup rules).
 * The oldest go once there are more than [MAX_ENTRIES] or they take more than [MAX_TOTAL_BYTES].
 */
@Singleton
class EncryptedResponseCache @Inject constructor(
    @ApplicationContext context: Context,
    private val cipher: SecretCipher,
) : ResponseCache, UserDataCleaner {
    private val dir = File(File(context.filesDir, PARENT), DIR)

    override fun read(key: String): String? = runCatching { cipher.decrypt(file(key).readText()) }.getOrNull()

    override fun write(key: String, text: String) {
        dir.mkdirs()
        val target = file(key)
        // Named per thread, so two answers to the same read arriving together don't write into one file.
        val temp = File(dir, "${target.name}.${Thread.currentThread().id}.tmp")
        temp.writeText(cipher.encrypt(text))
        if (!temp.renameTo(target)) {
            temp.delete()
            throw IOException("response not saved")
        }
        prune(keep = target)
    }

    override fun clear() {
        dir.deleteRecursively()
    }

    private fun file(key: String) = File(dir, sha256(key) + EXT)

    /** Drops the oldest answers while there are too many or they take too much room. */
    private fun prune(keep: File) {
        var count = 0
        var total = 0L
        dir.listFiles()?.filter { it.isFile && it.name.endsWith(EXT) }
            ?.sortedByDescending { if (it == keep) Long.MAX_VALUE else it.lastModified() }
            ?.forEach {
                count++
                total += it.length()
                if ((count > MAX_ENTRIES || total > MAX_TOTAL_BYTES) && it != keep) it.delete()
            }
    }

    private companion object {
        const val PARENT = "online_cache"
        const val DIR = "responses"
        const val EXT = ".enc"
        const val MAX_ENTRIES = 500
        const val MAX_TOTAL_BYTES = 50L * 1024 * 1024

        fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
