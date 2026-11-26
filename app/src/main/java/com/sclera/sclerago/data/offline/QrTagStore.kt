package com.sclera.sclerago.data.offline

import com.sclera.sclerago.data.sync.SyncManagerBridge
import com.sclera.sclerago.domain.model.TaggingError
import com.sclera.sclerago.domain.model.TaggingException
import com.sclera.sclerago.domain.model.TagValue
import com.sclera.sclerago.domain.model.TagWrite
import com.sclera.sclerago.domain.system.TimeProvider
import io.requery.android.database.sqlite.SQLiteDatabase
import java.util.UUID
import javax.inject.Inject
import android.util.Base64

/** What the phone knows about one QR code. */
class QrTagRow(
    /** The asset holding the code; null or empty when nothing does. */
    val assetId: String?,
    /** The property the code belongs to. */
    val propertyId: String?,
    /** The code is held by an Asset Data Collection record, not by the main VDMS system. */
    val isAdc: Boolean,
)

/** The QR tags kept on the phone for tagging without a connection. A tag saved here is marked `sync_status = '1'`. */
interface QrTagStore {
    /** The row for the code, or null when the phone has never seen it. */
    fun row(tag: TagValue): QrTagRow?

    /**
     * Tags the asset with the code and marks the change as waiting to be sent. A code that belongs to another property, or that the
     * main VDMS system holds, is refused with a [TaggingException].
     */
    fun save(write: TagWrite, photo: ByteArray, direction: String, user: String)

    /** How many QR codes of either kind the asset holds. */
    fun tagCount(asset: String): Int

    /**
     * Records where a code the phone already has was placed (its `movable` column: 1 movable, 0 fixed) after the server took the
     * tag. The row is not marked to be sent again; a code the phone doesn't have yet comes down with the next sync.
     */
    fun setMovable(tag: TagValue, movable: Boolean)
}

/** The QR tables of the global database (`qr_code_table` for Sclera's codes, `client_qr_code_table` for the customer's own). */
class GlobalQrTagStore @Inject constructor(private val bridge: SyncManagerBridge, private val time: TimeProvider) : QrTagStore {
    private fun db(): SQLiteDatabase = bridge.globalDatabase() ?: throw TaggingException(TaggingError.NEEDS_CONNECTION)

    override fun row(tag: TagValue): QrTagRow? {
        val sql = if (tag.isSystem) {
            "SELECT device_id, vdms_id, adc_qr_code_check FROM qr_code_table WHERE id = ?"
        } else {
            "SELECT device_id, vdms_id, adc_client_qr_code_check FROM client_qr_code_table WHERE client_qr_code_id = ?"
        }
        return db().rawQuery(sql, arrayOf(tag.id)).use { c ->
            if (c.moveToFirst()) QrTagRow(c.getStringOrNull(0), c.getStringOrNull(1), !c.isNull(2) && c.getLong(2) == ADC_MARKER) else null
        }
    }

    override fun save(write: TagWrite, photo: ByteArray, direction: String, user: String) {
        val db = db()
        val existing = row(write.tag)
        // The server refuses a code that belongs to another property when it syncs (826); say so now. A code with no property yet is free.
        val stored = existing?.propertyId
        if (!stored.isNullOrEmpty() && stored != write.property) throw TaggingException(TaggingError.TAKEN_BY_OTHER_PROPERTY)
        val movable = movableValue(write.placement.isMovable)
        val image = Base64.encodeToString(photo, Base64.NO_WRAP)
        val now = time.nowMillis()
        db.beginTransaction()
        try {
            if (write.tag.isSystem) saveSystem(db, write, existing, movable, image, direction, user, now)
            else saveClient(db, write, existing, movable, image, direction, user, now)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun tagCount(asset: String): Int {
        val db = db()
        fun count(table: String) = db.rawQuery("SELECT COUNT(*) FROM $table WHERE device_id = ?", arrayOf(asset)).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        return count("qr_code_table") + count("client_qr_code_table")
    }

    override fun setMovable(tag: TagValue, movable: Boolean) {
        val sql = if (tag.isSystem) "UPDATE qr_code_table SET movable = ? WHERE id = ?"
        else "UPDATE client_qr_code_table SET movable = ? WHERE client_qr_code_id = ?"
        db().execSQL(sql, arrayOf(movableValue(movable), tag.id))
    }

    private fun saveSystem(db: SQLiteDatabase, w: TagWrite, existing: QrTagRow?, movable: Int, image: String, direction: String, user: String, now: Long) {
        db.execSQL(
            """UPDATE qr_code_table SET device_id = ?, vdms_id = ?, direction = ?, movable = ?, image = ?, updated_by = ?,
               updated_time = ?, sync_status = ? WHERE id = ? AND adc_qr_code_check = 1""",
            arrayOf(w.asset, w.property, direction, movable, image, user, now, PENDING_SYNC, w.tag.id),
        )
        // A row that exists but wasn't updated is held by the main VDMS system.
        if (existing != null && changes(db) == 0) throw TaggingException(TaggingError.TAKEN_BY_VDMS)
        if (existing != null) return
        db.execSQL(
            """INSERT INTO qr_code_table (id, device_id, vdms_id, direction, movable, image, updated_by, updated_time,
               adc_qr_code_check, sync_status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?)""",
            arrayOf(w.tag.id, w.asset, w.property, direction, movable, image, user, now, PENDING_SYNC),
        )
    }

    private fun saveClient(db: SQLiteDatabase, w: TagWrite, existing: QrTagRow?, movable: Int, image: String, direction: String, user: String, now: Long) {
        db.execSQL(
            """UPDATE client_qr_code_table SET device_id = ?, vdms_id = ?, direction = ?, movable = ?, image = ?, updated_by = ?,
               updated_at = ?, sync_status = ? WHERE client_qr_code_id = ? AND adc_client_qr_code_check = 1""",
            arrayOf(w.asset, w.property, direction, movable, image, user, now, PENDING_SYNC, w.tag.id),
        )
        if (existing != null && changes(db) == 0) throw TaggingException(TaggingError.TAKEN_BY_VDMS)
        if (existing != null) return
        db.execSQL(
            """INSERT INTO client_qr_code_table (id, client_qr_code_id, device_id, vdms_id, direction, movable, image, added_by, added_at,
               updated_by, updated_at, adc_client_qr_code_check, sync_status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?)""",
            arrayOf(UUID.randomUUID().toString(), w.tag.id, w.asset, w.property, direction, movable, image, user, now, user, now, PENDING_SYNC),
        )
    }

    /** The `movable` column's value: 1 for a movable code, 0 for a fixed one. */
    private fun movableValue(movable: Boolean): Int = if (movable) 1 else 0

    private fun changes(db: SQLiteDatabase): Int = db.rawQuery("SELECT changes()", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    private fun android.database.Cursor.getStringOrNull(i: Int): String? = if (isNull(i)) null else getString(i)

    private companion object {
        const val ADC_MARKER = 1L
        const val PENDING_SYNC = "1"
    }
}
