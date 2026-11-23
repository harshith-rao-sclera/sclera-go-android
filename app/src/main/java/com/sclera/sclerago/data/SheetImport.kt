package com.sclera.sclerago.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.sclera.sclerago.di.IoDispatcher
import com.sclera.sclerago.domain.limits.MAX_IMPORT_BYTES
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/** A spreadsheet (or something inside it) is bigger than the app will read. */
class SheetTooLargeException(message: String) : IllegalArgumentException(message)

/** All of this stream, or a [SheetTooLargeException] as soon as it passes [max] bytes. */
internal fun InputStream.readBounded(max: Long): ByteArray {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val n = read(buf)
        if (n < 0) break
        total += n
        if (total > max) throw SheetTooLargeException("The file is larger than the allowed -eMB")
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}

/** A spreadsheet the user picked, read to send to the server: the [name] its provider gave and its [bytes]. */
class SheetUpload(val name: String, val bytes: ByteArray)

/**
 * Reads a spreadsheet the user picked for one of the server's imports (the server reads it; nothing is parsed here).
 * The file is untrusted: at most [MAX_IMPORT_BYTES] is read, and it must be an .xlsx (a zip inside).
 */
@Singleton
class SheetUploads @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** The .xlsx at [uri] (a content URI); a file that can't be read, is too large or isn't an .xlsx throws [OnlineError.Invalid]. */
    suspend fun read(uri: String): SheetUpload = withContext(io) {
        val parsed = Uri.parse(uri)
        val resolver = context.contentResolver
        try {
            val name = resolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_NAME
            val bytes = resolver.openInputStream(parsed)?.use { it.readBounded(MAX_IMPORT_BYTES) } ?: throw OnlineException(OnlineError.Invalid)
            if (!name.endsWith(XLSX_EXTENSION, ignoreCase = true) || !bytes.isZip()) throw OnlineException(OnlineError.Invalid)
            SheetUpload(name, bytes)
        } catch (e: SheetTooLargeException) {
            throw OnlineException(OnlineError.Invalid, e)
        } catch (e: IOException) {
            throw OnlineException(OnlineError.Invalid, e)
        } catch (e: SecurityException) {
            throw OnlineException(OnlineError.Invalid, e)
        }
    }

    /** An .xlsx is a zip: it starts with the local file header `PK\u0003\u0004`. */
    private fun ByteArray.isZip(): Boolean =
        size >= ZIP_MAGIC.size && ZIP_MAGIC.indices.all { this[it] == ZIP_MAGIC[it] }

    private companion object {
        const val DEFAULT_NAME = "sheet.xlsx"
        const val XLSX_EXTENSION = ".xlsx"
        val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    }
}
