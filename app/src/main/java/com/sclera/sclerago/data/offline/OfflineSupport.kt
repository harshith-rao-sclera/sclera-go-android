package com.sclera.sclerago.data.offline

import com.sclera.sclerago.data.mapper.DbRow
import com.sclera.sclerago.data.sync.AdcDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** Most ids bound to one `IN (...)` list; longer lists are queried in chunks. */
private const val MAX_IN_ARGS = 400

/** Loads straight away and again whenever one of [tables] changes (changes arriving while loading are merged), on [io]. */
internal fun <T> AdcDatabase.watch(io: CoroutineDispatcher, vararg tables: String, load: () -> T): Flow<T> =
    observe(*tables).map { load() }.distinctUntilChanged().conflate().flowOn(io)

/** [block]'s result, or [empty] when the offline database is not open yet. */
internal inline fun <T> AdcDatabase.whenOpen(empty: T, block: () -> T): T = if (isOpen) block() else empty

/** [block]'s result, or [empty] when the database is not open or lacks [table]. */
internal inline fun <T> AdcDatabase.whenTable(table: String, empty: T, block: () -> T): T =
    if (isOpen && hasTable(table)) block() else empty

/** The condition that leaves out rows the sync marked removed: only when [table] has that column. */
internal fun AdcDatabase.notRemoved(table: String, alias: String = table): String =
    if (hasColumn(table, "is_removed")) "$alias.is_removed = 0" else "1 = 1"

/** "?, ?, ?" for [count] bound values. */
internal fun placeholders(count: Int): String = List(count) { "?" }.joinToString(", ")

/** A LIKE pattern for rows containing [text]; `%`, `_` and `\` in it match themselves (use with `ESCAPE '\'`). */
internal fun likeContains(text: String): String =
    "%" + text.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

/** Rows from [sql] run once per chunk of [ids]; [sql] holds `%IN%`, replaced by the placeholders of that chunk. */
internal fun AdcDatabase.queryIn(sql: String, ids: Collection<String>): List<DbRow> =
    ids.distinct().chunked(MAX_IN_ARGS).flatMap { chunk -> query(sql.replace("%IN%", placeholders(chunk.size)), chunk) }
