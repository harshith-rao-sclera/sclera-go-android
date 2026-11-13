package com.sclera.sclerago.data.offline

import com.sclera.sclerago.data.sync.AdcDatabase
import com.sclera.sclerago.data.sync.SyncCredentialsProvider
import com.sclera.sclerago.domain.model.DataError
import com.sclera.sclerago.domain.model.DataException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Runs [block] as one transaction of the offline database on [io]. The engine's change triggers record what
 * [block] writes, so it is pushed to the server later. Throws [DataException]: NOT_READY while the database is
 * not open, the exception [block] threw when it is one, else FAILED (the cause is kept, never its data).
 */
internal suspend fun <T> AdcDatabase.write(io: CoroutineDispatcher, block: AdcDatabase.() -> T): T = withContext(io) {
    if (!isOpen) throw DataException(DataError.NOT_READY)
    try {
        inTransaction(block)
    } catch (e: DataException) {
        throw e
    } catch (e: Exception) {
        throw DataException(DataError.FAILED, e)
    }
}

/**
 * Marks the rows of [table] that match [where] removed, as the server does: `is_removed = 1`, `version + 1` and
 * `updated_at` ([now]) where the table has those columns. The engine pushes that change, and the server and every
 * other phone then drop the row. A table without the `is_removed` column (before the engine has added it) loses the rows.
 */
internal fun AdcDatabase.removeRows(table: String, where: String, args: List<Any>, now: String) {
    if (!hasTable(table)) return
    if (!hasColumn(table, "is_removed")) {
        execute("DELETE FROM $table WHERE $where", args, setOf(table))
        return
    }
    val touched = hasColumn(table, "updated_at")
    val sets = buildList {
        add("is_removed = 1")
        if (hasColumn(table, "version")) add("version = version + 1")
        if (touched) add("updated_at = ?")
    }
    execute(
        "UPDATE $table SET ${sets.joinToString()} WHERE ($where) AND is_removed = 0",
        (if (touched) listOf<Any>(now) else emptyList()) + args,
        setOf(table),
    )
}

/** The id of the signed-in user, who is recorded as the creator of new rows; null when signed out. */
internal fun SyncCredentialsProvider.userId(): String? = credentials.value?.userId?.takeIf { it.isNotBlank() }

/** True when [sql] finds a row. */
internal fun AdcDatabase.exists(sql: String, args: List<Any> = emptyList()): Boolean = queryOne(sql, args) != null

/** 1 or 0 for a flag column. */
internal fun Boolean.bit(): Int = if (this) 1 else 0

/** [sql] with `%LIVE%` replaced by the condition that leaves out the rows of [table] marked removed. */
internal fun AdcDatabase.live(sql: String, table: String): String = sql.replace("%LIVE%", notRemoved(table))
