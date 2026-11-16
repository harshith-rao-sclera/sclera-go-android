package com.sclera.sclerago.data.sync

import android.content.ContentValues
import android.database.Cursor
import com.sclera.sclerago.data.mapper.DbClock
import com.sclera.sclerago.data.mapper.DbRow
import com.sclera.sclerago.data.mapper.MapRow
import io.requery.android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.withLock

/**
 * The offline database the sync engine downloads, as repositories use it. Calls are blocking: run them on the
 * IO dispatcher. Writes made here are picked up by the engine's change triggers and pushed to the server, and
 * tell [observe] listeners which tables changed. Writers are serialised by one lock; [inTransaction] groups them.
 */
@Singleton
class AdcDatabase @Inject constructor(
    private val bridge: SyncManagerBridge,
    private val invalidations: TableInvalidations,
) {
    private val lock = ReentrantLock()
    private var depth = 0
    private val pending = mutableSetOf<String>()
    private val knownColumns = ConcurrentHashMap.newKeySet<String>()

    /**
     * The connection the running transaction began on. Its reads and writes stay on it even when another property is
     * selected meanwhile, so one transaction never lands in two properties' databases. Only the thread holding [lock]
     * (the one running the transaction) uses it; other threads read the selected property's database as usual.
     */
    private var pinned: SQLiteDatabase? = null

    val isOpen: Boolean get() = bridge.connection() != null

    private fun db(): SQLiteDatabase {
        val conn = pinned?.takeIf { lock.isHeldByCurrentThread } ?: bridge.connection() ?: throw IllegalStateException("The offline database is not open")
        if (clockOf !== conn) learnClock(conn)
        return conn
    }

    /** The connection [DbClock] was last learnt from. */
    @Volatile
    private var clockOf: SQLiteDatabase? = null

    /**
     * Learns the offset the sync server wrote [conn]'s times in: rows only the server makes have ids that carry their
     * true time (UUIDv7, milliseconds since 1970 in UTC), so their `created_at` minus that time is the offset (the most
     * common one, to the nearest quarter of an hour). With no such rows yet, this device's offset is used.
     */
    private fun learnClock(conn: SQLiteDatabase) {
        clockOf = conn
        val gaps = mutableListOf<Long>()
        SERVER_ONLY_TABLES.forEach { table ->
            runCatching {
                conn.rawQuery("SELECT id, created_at FROM $table ORDER BY id DESC LIMIT 20", emptyArray()).use { c ->
                    while (c.moveToNext()) {
                        val idTime = uuidV7Millis(c.getString(0)) ?: continue
                        val local = runCatching { LocalDateTime.parse(c.getString(1).trim().replace(' ', 'T')) }.getOrNull() ?: continue
                        gaps += local.toInstant(ZoneOffset.UTC).toEpochMilli() - idTime
                    }
                }
            }
        }
        DbClock.offset = gaps.sorted().getOrNull(gaps.size / 2)?.let { gap ->
            val quarters = Math.round(gap / QUARTER_HOUR_MS.toDouble())
            runCatching { ZoneOffset.ofTotalSeconds((quarters * QUARTER_HOUR_MS / 1000).toInt()) }.getOrNull()
        } ?: OffsetDateTime.now().offset
    }

    /** The time in a version 7 UUID's first 48 bits, or null for any other id. */
    private fun uuidV7Millis(id: String?): Long? {
        val hex = id?.replace("-", "")?.takeIf { it.length == 32 && it[12] == '7' } ?: return null
        return hex.take(12).toLongOrNull(16)
    }

    fun hasTable(name: String): Boolean =
        query("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", listOf(name)).isNotEmpty()

    /** True when [table] has [column]. A found column is remembered; a missing one is looked up again, as a later sync may add it. */
    fun hasColumn(table: String, column: String): Boolean {
        val key = "$table.$column"
        if (key in knownColumns) return true
        val found = query("SELECT 1 FROM pragma_table_info(?) WHERE name = ?", listOf(table, column)).isNotEmpty()
        if (found) knownColumns += key
        return found
    }

    /** Rows of [sql]; [args] are bound as text, so they must not be null. */
    fun query(sql: String, args: List<Any> = emptyList()): List<DbRow> =
        db().rawQuery(sql, args.map { it.toString() }.toTypedArray()).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.toRow()) }
        }

    fun queryOne(sql: String, args: List<Any> = emptyList()): DbRow? = query(sql, args).firstOrNull()

    /** Runs [sql]; list the tables it changes in [affects] so observers are told. */
    fun execute(sql: String, args: List<Any?> = emptyList(), affects: Set<String> = emptySet()) = write(affects) {
        db().execSQL(sql, args.toTypedArray())
    }

    fun insert(table: String, values: Map<String, Any?>, replace: Boolean = false): Long = write(setOf(table)) {
        db().insertWithOnConflict(table, null, values.toContentValues(), if (replace) SQLiteDatabase.CONFLICT_REPLACE else SQLiteDatabase.CONFLICT_NONE)
    }

    fun update(table: String, values: Map<String, Any?>, where: String, whereArgs: List<Any> = emptyList()): Int = write(setOf(table)) {
        db().update(table, values.toContentValues(), where, whereArgs.map { it.toString() }.toTypedArray())
    }

    fun delete(table: String, where: String, whereArgs: List<Any> = emptyList()): Int = write(setOf(table)) {
        db().delete(table, where, whereArgs.map { it.toString() }.toTypedArray())
    }

    /**
     * Runs [block] as one transaction (rolled back if it throws), all of it on the database it began on; observers
     * are told once, after it commits.
     */
    fun <T> inTransaction(block: AdcDatabase.() -> T): T = lock.withLock {
        val conn = db()
        // Begun before anything is recorded, so a connection closed by a property switch leaves no state behind.
        conn.beginTransaction()
        pinned = conn
        depth++
        var committed = false
        try {
            val result = block()
            conn.setTransactionSuccessful()
            committed = true
            result
        } finally {
            conn.endTransaction()
            depth--
            if (depth == 0) {
                pinned = null
                val changed = pending.toSet()
                pending.clear()
                if (committed) invalidations.emit(changed)
            }
        }
    }

    /** Emits once straight away, then whenever one of [tables] changes. */
    fun observe(vararg tables: String): Flow<Unit> = invalidations.observe(*tables)

    private fun <T> write(tables: Set<String>, action: () -> T): T = lock.withLock {
        val result = action()
        if (depth > 0) pending += tables else invalidations.emit(tables)
        result
    }
}

/** Tables only the server writes rows to: their ids and times tell the offset its times are in. */
private val SERVER_ONLY_TABLES = listOf("validation_jobs", "validation_results", "lookup_jobs", "manual_jobs", "master_formats")

private const val QUARTER_HOUR_MS = 15 * 60 * 1000L

private fun Cursor.toRow(): DbRow = MapRow(
    (0 until columnCount).associate { i ->
        getColumnName(i) to when (getType(i)) {
            Cursor.FIELD_TYPE_INTEGER -> getLong(i)
            Cursor.FIELD_TYPE_FLOAT -> getDouble(i)
            Cursor.FIELD_TYPE_STRING -> getString(i)
            Cursor.FIELD_TYPE_BLOB -> getBlob(i)
            else -> null
        }
    },
)

private fun Map<String, Any?>.toContentValues() = ContentValues().also { cv ->
    forEach { (key, value) ->
        when (value) {
            null -> cv.putNull(key)
            is Boolean -> cv.put(key, if (value) 1 else 0)
            is Int -> cv.put(key, value)
            is Long -> cv.put(key, value)
            is Double -> cv.put(key, value)
            is Float -> cv.put(key, value)
            is ByteArray -> cv.put(key, value)
            else -> cv.put(key, value.toString())
        }
    }
}
