package com.sclera.sclerago.data.mapper

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * One database row read by column name, with no Android types so the mappers work on any cursor.
 * A getter returns null when the column is missing or null.
 */
interface DbRow {
    fun string(column: String): String?
    fun int(column: String): Int?
    fun long(column: String): Long?
    fun double(column: String): Double?

    /** True for 1 / true; missing or null is false. */
    fun bool(column: String): Boolean = (int(column) ?: 0) != 0
}

/** A [DbRow] over a map of column name to value (SQLite types: Long, Double, String, null). */
class MapRow(private val values: Map<String, Any?>) : DbRow {
    override fun string(column: String): String? = values[column]?.toString()
    override fun int(column: String): Int? = number(column)?.toInt()
    override fun long(column: String): Long? = number(column)?.toLong()
    override fun double(column: String): Double? = number(column)?.toDouble()

    private fun number(column: String): Number? = when (val v = values[column]) {
        is Number -> v
        is Boolean -> if (v) 1 else 0
        is String -> v.toDoubleOrNull()
        else -> null
    }
}

/** Epoch millis of a stored timestamp ("2026-11-12 13:32:32.581028", UTC unless it carries an offset), or null. */
fun parseDbTime(text: String?): Long? {
    val t = text?.trim()?.takeIf { it.isNotEmpty() }?.replace(' ', 'T') ?: return null
    return runCatching { OffsetDateTime.parse(t).toInstant().toEpochMilli() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(t).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
}

/** Epoch millis as a stored timestamp ("yyyy-MM-dd HH:mm:ss.SSS", UTC), for writing timestamp columns. */
fun formatDbTime(millis: Long): String =
    LocalDateTime.ofEpochSecond(Math.floorDiv(millis, 1000L), (Math.floorMod(millis, 1000L) * 1_000_000L).toInt(), ZoneOffset.UTC)
        .toString().replace('T', ' ')
