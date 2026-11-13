package com.sclera.sclerago.data.sync

import android.content.ContentValues
import android.database.Cursor
import com.sclera.sclerago.data.mapper.DbRow
import com.sclera.sclerago.data.mapper.MapRow
import io.requery.android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.flow.Flow
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

    val isOpen: Boolean get() = bridge.connection() != null

    private fun db(): SQLiteDatabase = bridge.connection() ?: throw IllegalStateException("The offline database is not open")

    fun hasTable(name: String): Boolean =
        query("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", listOf(name)).isNotEmpty()

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

    /** Runs [block] as one transaction (rolled back if it throws); observers are told once, after it commits. */
    fun <T> inTransaction(block: AdcDatabase.() -> T): T = lock.withLock {
        val conn = db()
        depth++
        var committed = false
        conn.beginTransaction()
        try {
            val result = block()
            conn.setTransactionSuccessful()
            committed = true
            result
        } finally {
            conn.endTransaction()
            depth--
            if (depth == 0) {
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
