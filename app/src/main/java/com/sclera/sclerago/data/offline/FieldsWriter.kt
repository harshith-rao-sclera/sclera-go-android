package com.sclera.sclerago.data.offline

import com.sclera.sclerago.data.mapper.DbJson
import com.sclera.sclerago.data.mapper.DbRow
import com.sclera.sclerago.data.mapper.FieldKeys
import com.sclera.sclerago.data.mapper.formatDbTime
import com.sclera.sclerago.data.mapper.mergeChecks
import com.sclera.sclerago.data.mapper.optionsJson
import com.sclera.sclerago.data.mapper.toDbType
import com.sclera.sclerago.data.sync.AdcDatabase
import com.sclera.sclerago.domain.model.DataError
import com.sclera.sclerago.domain.model.DataException
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.system.IdGenerator
import com.sclera.sclerago.domain.system.TimeProvider
import com.sclera.sclerago.domain.system.next
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Writes the asset fields every asset has (`asset_fields` rows of no one asset): a change to the list of
 * [FieldCfg] becomes inserts for new fields, updates for changed or moved ones and removals of deleted ones.
 * Fields built into the property are never removed.
 */
class FieldsWriter(
    private val db: AdcDatabase,
    private val io: CoroutineDispatcher,
    private val time: TimeProvider,
    private val ids: IdGenerator,
) {
    /** Makes the fields [after] what [before] was: only what differs is written. */
    suspend fun apply(before: List<FieldCfg>, after: List<FieldCfg>) {
        if (before == after) return
        db.write(io) {
            val now = formatDbTime(time.nowMillis())
            val rows = query(live(STANDARD_FIELD_ROWS, "asset_fields")).associateBy { FieldKeys.toApp(it.string("key").orEmpty()) }
            val old = before.associateBy { it.key }
            val reordered = before.map { it.key } != after.map { it.key }

            (old.keys - after.map { it.key }.toSet()).forEach { key ->
                rows[key]?.takeIf { !it.bool("is_builtin") }?.let { remove(it, now) }
            }
            after.forEachIndexed { position, f ->
                val row = rows[f.key]
                when {
                    row == null -> insert(f, position, now)
                    f != old[f.key] || reordered -> update(row, f, if (reordered) position else row.int("position") ?: position, now)
                }
            }
        }
    }

    /** Removes a field; refused (IN_USE) while an asset still has an answer to it (the server's rule: answers go only on request). */
    private fun AdcDatabase.remove(row: DbRow, now: String) {
        if (hasAnswers(row.string("key").orEmpty())) throw DataException(DataError.IN_USE)
        removeRows("asset_fields", "id = ?", listOf(row.string("id").orEmpty()), now)
    }

    private fun AdcDatabase.hasAnswers(key: String): Boolean =
        hasTable("asset_values") && query(FIELD_ANSWERS.replace("%LIVE%", notRemoved("assets", "a")), listOf(likeContains("\"$key\":")))
            .any { DbJson.stringMap(it.string("v"))[key].orEmpty().isNotBlank() }

    private fun AdcDatabase.insert(f: FieldCfg, position: Int, now: String) {
        val key = freeKey(f.label)
        if (f.label.isBlank()) throw DataException(DataError.FAILED)
        if (exists(live(FIELD_NAME_TAKEN, "asset_fields"), listOf(f.label, ""))) {
            throw DataException(DataError.NAME_TAKEN)
        }
        execute(
            FIELD_INSERT,
            listOf(
                ids.next(), key, f.label, f.kind.toDbType(), f.optionsJson(), f.required.bit(), f.visible.bit(), position,
                f.tooltip.orNull(), f.defaultValue.orNull(), f.mergeChecks(null), now, now,
            ),
            setOf("asset_fields"),
        )
    }

    private fun AdcDatabase.update(row: DbRow, f: FieldCfg, position: Int, now: String) {
        val id = row.string("id").orEmpty()
        if (f.label.isBlank()) throw DataException(DataError.FAILED)
        if (exists(live(FIELD_NAME_TAKEN, "asset_fields"), listOf(f.label, id))) throw DataException(DataError.NAME_TAKEN)
        execute(
            FIELD_UPDATE,
            listOf(
                f.label, f.optionsJson(row.string("options")),
                f.required.bit(), f.visible.bit(), position, f.tooltip.orNull(), f.defaultValue.orNull(), f.mergeChecks(row.string("checks")), now, id,
            ),
            setOf("asset_fields"),
        )
    }

    /** The server's key for a new field: its name in lower case with runs of other characters as "_" (at most 60), "_2", "_3" … when taken. */
    private fun AdcDatabase.freeKey(label: String): String {
        val base = label.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(MAX_KEY).ifEmpty { "field" }
        var key = base
        var n = 2
        while (exists(live(FIELD_KEY_TAKEN, "asset_fields"), listOf(key))) key = "${base}_${n++}"
        return key
    }

    private fun String.orNull(): String? = trim().takeIf { it.isNotEmpty() }

    private companion object {
        const val MAX_KEY = 60
    }
}
