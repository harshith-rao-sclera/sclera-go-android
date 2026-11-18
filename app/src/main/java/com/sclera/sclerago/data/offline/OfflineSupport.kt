package com.sclera.sclerago.data.offline

import com.sclera.sclerago.data.mapper.DbRow
import com.sclera.sclerago.data.sync.AdcDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transform
import kotlin.coroutines.cancellation.CancellationException

/** Most ids bound to one `IN (...)` list; longer lists are queried in chunks. */
private const val MAX_IN_ARGS = 400

/**
 * Loads straight away and again whenever one of [tables] changes (changes arriving while loading are merged), on [io].
 * A load that fails (say the property was switched and its connection closed mid-query) is skipped: the last value
 * stays and the next change loads again, so the screen's data never stops updating.
 */
internal fun <T> AdcDatabase.watch(io: CoroutineDispatcher, vararg tables: String, load: () -> T): Flow<T> =
    observe(*tables).transform {
        val loaded = try {
            Loaded(load())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (loaded != null) emit(loaded.value)
    }.distinctUntilChanged().conflate().flowOn(io)

/** A load's result, so a failed load (null) is told apart from one that found nothing (a null [value]). */
private class Loaded<T>(val value: T)

/** [block]'s result, or [empty] when the offline database is not open yet. */
internal inline fun <T> AdcDatabase.whenOpen(empty: T, block: () -> T): T = if (isOpen) block() else empty

/** [block]'s result, or [empty] when the database is not open or lacks [table]. */
internal inline fun <T> AdcDatabase.whenTable(table: String, empty: T, block: () -> T): T =
    if (isOpen && hasTable(table)) block() else empty

/** The condition that leaves out rows the sync marked removed: only when [table] has that column. */
internal fun AdcDatabase.notRemoved(table: String, alias: String = table): String =
    if (hasColumn(table, "is_removed")) "$alias.is_removed = 0" else "1 = 1"

/**
 * `assets` rows (alias [alias]) that are in ADC, as the server sees them: not removed, and not handed to Managed Assets
 * (its db/managed.py: only vdms_state 0 never moved, 1 being moved and 4 returned are seen).
 */
internal fun AdcDatabase.assetsInAdc(alias: String = "assets"): String {
    val live = notRemoved("assets", alias)
    return if (hasColumn("assets", "vdms_state")) "$live AND COALESCE($alias.vdms_state, 0) IN (0, 1, 4)" else live
}

/** [assetsInAdc], less the assets read from a file still waiting for review: what the server lists and counts. */
internal fun AdcDatabase.listedAssets(alias: String = "assets"): String {
    val inAdc = assetsInAdc(alias)
    return if (hasColumn("assets", "needs_review")) "$inAdc AND COALESCE($alias.needs_review, 0) = 0" else inAdc
}

/**
 * `asset_values`' answers column, quoted: `field_values` since the server renamed it (its migration t0008: `values`
 * is a reserved word), `values` in a database downloaded before that.
 */
internal fun AdcDatabase.answersColumn(): String = if (hasColumn("asset_values", "field_values")) "\"field_values\"" else "\"values\""

/** [sql] with each `%ANSWERS%` as [answersColumn]. */
internal fun AdcDatabase.withAnswers(sql: String): String = sql.replace("%ANSWERS%", answersColumn())

/** "?, ?, ?" for [count] bound values. */
internal fun placeholders(count: Int): String = List(count) { "?" }.joinToString(", ")

/** A LIKE pattern for rows containing [text]; `%`, `_` and `\` in it match themselves (use with `ESCAPE '\'`). */
internal fun likeContains(text: String): String =
    "%" + text.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

/** Rows from [sql] run once per chunk of [ids]; [sql] holds `%IN%`, replaced by the placeholders of that chunk. */
internal fun AdcDatabase.queryIn(sql: String, ids: Collection<String>): List<DbRow> =
    ids.distinct().chunked(MAX_IN_ARGS).flatMap { chunk -> query(sql.replace("%IN%", placeholders(chunk.size)), chunk) }
