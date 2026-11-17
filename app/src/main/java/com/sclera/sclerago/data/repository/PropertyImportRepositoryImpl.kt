package com.sclera.sclerago.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.sclera.sclerago.data.online.OnlineSession
import com.sclera.sclerago.data.online.PropertyStore
import com.sclera.sclerago.data.remote.adc.AdcApi
import com.sclera.sclerago.data.remote.adc.ImportColumnDto
import com.sclera.sclerago.di.IoDispatcher
import com.sclera.sclerago.domain.limits.MAX_IMPORT_BYTES
import com.sclera.sclerago.domain.model.ImportColumn
import com.sclera.sclerago.domain.model.ImportResult
import com.sclera.sclerago.domain.model.ImportSuggestion
import com.sclera.sclerago.domain.model.ImportTarget
import com.sclera.sclerago.domain.model.MatchHow
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.repository.PropertyImportRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

/**
 * Importing properties through `…/organisations/{org}/properties/import…`, as the web's Import does on the new API. The
 * picked file is read here (at most [MAX_IMPORT_BYTES]); once rows are imported the property list is read again.
 */
class PropertyImportRepositoryImpl @Inject constructor(
    private val api: AdcApi,
    private val session: OnlineSession,
    private val properties: PropertyStore,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : PropertyImportRepository {

    override suspend fun saveTemplate(destination: String) {
        val sheet = api.propertyImportTemplate(session.organisation())
        withContext(io) {
            try {
                context.contentResolver.openOutputStream(Uri.parse(destination))?.use { it.write(sheet) }
                    ?: throw OnlineException(OnlineError.Invalid)
            } catch (e: IOException) {
                throw OnlineException(OnlineError.Invalid, e)
            } catch (e: SecurityException) {
                throw OnlineException(OnlineError.Invalid, e)
            }
        }
    }

    override suspend fun targets(): List<ImportTarget> =
        api.propertyImportColumns(session.organisation())
            .filter { it.key.isNotBlank() }
            .map { ImportTarget(it.key.trim(), it.header.trim().ifEmpty { it.key.trim() }) }

    override suspend fun suggest(file: String): ImportSuggestion {
        val (name, bytes) = read(file)
        val found = api.suggestPropertyImport(session.organisation(), name, bytes)
        return ImportSuggestion(found.columns.filter { it.heading.isNotBlank() }.map { it.toColumn() }, found.aiUsed, found.aiNote?.takeIf { it.isNotBlank() })
    }

    override suspend fun import(file: String, mapping: Map<String, String>): ImportResult {
        val (name, bytes) = read(file)
        val done = api.importProperties(session.organisation(), name, bytes, mapping)
        // The new properties show in the list straight away (the web reads its list again too).
        try {
            properties.refresh()
        } catch (_: OnlineException) {
            // the import itself worked; the list catches up on the next read
        }
        return ImportResult(done.created.size, done.skipped.map { it.name }.filter { it.isNotBlank() })
    }

    /** The picked file's name and bytes; a file that can't be read, or is larger than the limit, is refused. */
    private suspend fun read(file: String): Pair<String, ByteArray> = withContext(io) {
        val uri = Uri.parse(file)
        try {
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }?.takeIf { it.isNotBlank() } ?: DEFAULT_NAME
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val data = input.readNBytesCapped(MAX_IMPORT_BYTES + 1)
                if (data.size > MAX_IMPORT_BYTES) throw OnlineException(OnlineError.Invalid)
                data
            } ?: throw OnlineException(OnlineError.Invalid)
            name to bytes
        } catch (e: IOException) {
            throw OnlineException(OnlineError.Invalid, e)
        } catch (e: SecurityException) {
            throw OnlineException(OnlineError.Invalid, e)
        }
    }

    private fun InputStream.readNBytesCapped(limit: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER)
        var total = 0L
        while (total < limit) {
            val n = read(buffer, 0, minOf(buffer.size.toLong(), limit - total).toInt())
            if (n < 0) break
            out.write(buffer, 0, n)
            total += n
        }
        return out.toByteArray()
    }

    private fun ImportColumnDto.toColumn() = ImportColumn(
        heading = heading.trim(),
        samples = samples.mapNotNull { (it as? JsonPrimitive)?.content?.trim()?.takeIf { s -> s.isNotEmpty() } },
        target = target?.trim()?.takeIf { it.isNotEmpty() },
        how = when (how) {
            "name" -> MatchHow.HEADING
            "ai" -> MatchHow.AI
            "values" -> MatchHow.VALUES
            else -> null
        },
    )

    private companion object {
        const val DEFAULT_NAME = "properties.xlsx"
        const val BUFFER = 64 * 1024
    }
}
