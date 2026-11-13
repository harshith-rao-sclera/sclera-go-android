package com.sclera.sclerago.data.offline

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
                rows[key]?.takeIf { !it.bool("is_builtin") }?.let { removeRows("asset_fields", "id = ?", listOf(it.string("id").orEmpty()), now) }
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

    private fun AdcDatabase.insert(f: FieldCfg, position: Int, now: String) {
        val key = FieldKeys.toDb(f.key)
        if (f.label.isBlank()) throw DataException(DataError.FAILED)
        if (exists(live(FIELD_KEY_TAKEN, "asset_fields"), listOf(key)) || exists(live(FIELD_NAME_TAKEN, "asset_fields"), listOf(f.label, ""))) {
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
                f.label, f.kind.toDbType(), f.optionsJson(),
                f.required.bit(), f.visible.bit(), position, f.tooltip.orNull(), f.defaultValue.orNull(), f.mergeChecks(row.string("checks")), now, id,
            ),
            setOf("asset_fields"),
        )
    }

    private fun String.orNull(): String? = trim().takeIf { it.isNotEmpty() }
}
