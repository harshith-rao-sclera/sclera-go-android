package com.sclera.sclerago.data.offline

import android.content.Context
import com.sclera.sclerago.data.sync.AdcDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The files of asset media and documents, kept only in the offline database's `multimedia` table (the sync engine
 * uploads them from there, and downloads the server's there). Screens open them through [uri], which the app's private
 * media provider serves from the database; nothing else keeps a copy.
 */
@Singleton
class MediaBlobs @Inject constructor(private val db: AdcDatabase, @ApplicationContext private val context: Context) {

    /** What a file is: its type, its name (from its row) and its size. */
    data class Info(val contentType: String?, val fileName: String?, val sizeBytes: Long)

    /**
     * The file of row [rowId] of [table] is still waiting to be uploaded, so the server doesn't have it yet (the engine
     * flags `media_changes` until it is sent). False when the database has no file for it.
     */
    fun waitingUpload(table: String, rowId: String): Boolean {
        if (table !in TABLES || !db.isOpen || !db.hasTable(MULTIMEDIA)) return false
        if (!db.hasColumn(MULTIMEDIA, "media_changes")) return true
        return db.queryOne(WAITING, listOf(table, rowId)) != null
    }

    /** Where the file of row [rowId] of [table] is opened: `content://<app>.media/<table>/<rowId>`. */
    fun uri(table: String, rowId: String): String = "content://${authority(context)}/$table/$rowId"

    /** The file of row [rowId] of [table], or null when the database has none for it (or isn't open). */
    fun info(table: String, rowId: String): Info? {
        if (table !in TABLES || !db.isOpen || !db.hasTable(MULTIMEDIA)) return null
        val row = db.queryOne(INFO, listOf(table, rowId)) ?: return null
        val name = if (db.hasTable(table)) db.queryOne("SELECT filename FROM \"$table\" WHERE id = ?", listOf(rowId))?.string("filename") else null
        return Info(row.string("content_type"), name?.takeIf { it.isNotBlank() }, row.long("size") ?: 0L)
    }

    /** Streams the file of row [rowId] of [table] into [out]; false when there is none. */
    fun copy(table: String, rowId: String, out: OutputStream): Boolean {
        if (table !in TABLES || !db.isOpen || !db.hasTable(MULTIMEDIA)) return false
        return db.copyBlob(MULTIMEDIA, CONTENT, WHERE, listOf(table, rowId), out)
    }

    /** Whether the database holds the file of media row [rowId] (so an older copy of it on the device can go). */
    fun has(rowId: String): Boolean =
        db.isOpen && db.hasTable(MULTIMEDIA) && db.queryOne(HAS, listOf(rowId)) != null

    companion object {
        /** The tables whose rows have files here. */
        val TABLES = setOf("asset_media", "documents")

        /** The media provider's authority for this app. */
        fun authority(context: Context): String = context.packageName + ".media"

        private const val MULTIMEDIA = "multimedia"
        private const val WAITING = "SELECT 1 FROM multimedia WHERE table_name = ? AND row_id = ? AND media_changes IS NOT NULL LIMIT 1"
        private const val CONTENT = "multimedia_content"
        private const val WHERE = "table_name = ? AND row_id = ? AND multimedia_content IS NOT NULL ORDER BY \"order\""
        private const val INFO =
            "SELECT content_type, length(multimedia_content) AS size FROM multimedia WHERE $WHERE LIMIT 1"
        private const val HAS = "SELECT 1 FROM multimedia WHERE row_id = ? AND multimedia_content IS NOT NULL LIMIT 1"
    }
}
