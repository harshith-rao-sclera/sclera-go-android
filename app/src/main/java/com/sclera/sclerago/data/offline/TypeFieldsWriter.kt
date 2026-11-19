package com.sclera.sclerago.data.offline

import com.sclera.sclerago.data.sync.AdcDatabase
import com.sclera.sclerago.domain.model.TypeField
import com.sclera.sclerago.domain.model.typeFieldName
import com.sclera.sclerago.domain.system.IdGenerator
import com.sclera.sclerago.domain.system.next
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The fields each type of the current property brings, as kept on the device. Blocking. */
fun interface TypeFieldsLookup {
    /** The fields of the type [masterFormatId]; empty when none are kept (the server adds them once the asset syncs). */
    fun fieldsOf(masterFormatId: String): List<TypeField>
}

/** What making an asset's fields fit its type did: each own field's key by lower-case name, and how many were added and removed. */
internal class TypeChange(val keysByName: Map<String, String>, val added: Int, val removed: Int) {
    val changed: Boolean get() = added > 0 || removed > 0
}

/** The answers of fields that don't exist yet, by type field name ([typeFieldName] of their form key). */
internal fun typeAnswers(values: Map<String, String>): Map<String, String> =
    values.mapNotNull { (k, v) -> typeFieldName(k)?.let { it to v } }.toMap()

/**
 * Makes an asset's own fields fit its type in the writer's transaction, as the server's `apply_type` does, so the sync
 * writes what a server-side classify would: the type's fields it lacks are added (never mandatory, `is_standard` NULL,
 * a new 10-hex key, `master_format_id` the type), and fields another type brought are removed while still empty.
 */
internal class TypeFieldsWriter(private val lookup: TypeFieldsLookup, private val ids: IdGenerator) {

    /** Fits asset [assetId]'s own fields to its type [newFormat] (null: none); [answers] are its answers by field key, to tell empty fields. Safe to repeat. */
    fun AdcDatabase.applyType(assetId: String, newFormat: String?, answers: Map<String, String>, now: String): TypeChange {
        if (!hasTable(TABLE)) return TypeChange(emptyMap(), 0, 0)
        val typeColumn = hasColumn(TABLE, "master_format_id")
        val wanted = newFormat?.let(lookup::fieldsOf).orEmpty().sortedBy { it.position }.filter { it.name.isNotBlank() }.distinctBy { it.name.lowercase() }
        val wantedNames = wanted.map { it.name.lowercase() }.toSet()
        val own = query(live(OWN_FIELDS, TABLE), listOf(assetId))

        var removed = 0
        val kept = own.filter { f ->
            val from = if (typeColumn) f.string("master_format_id")?.takeIf { it.isNotBlank() } else null
            when {
                from == null || from == newFormat -> true
                f.string("name").orEmpty().lowercase() in wantedNames -> {
                    execute(FIELD_RETYPE, listOf(newFormat, now, f.string("id").orEmpty()), setOf(TABLE))
                    true
                }
                answers[f.string("key").orEmpty()].isNullOrBlank() -> {
                    removeRows(TABLE, "id = ?", listOf(f.string("id").orEmpty()), now)
                    removed++
                    false
                }
                else -> true
            }
        }

        val keys = kept.associate { it.string("name").orEmpty().lowercase() to it.string("key").orEmpty() }.toMutableMap()
        val taken = keys.keys + query(live(STANDARD_FIELD_NAMES, TABLE)).map { it.string("name").orEmpty().lowercase() }
        var position = 1 + (kept.mapNotNull { it.int("position") }.maxOrNull() ?: -1)
        var added = 0
        wanted.filter { it.name.lowercase() !in taken }.forEach { field ->
            val key = freeKey()
            val row = linkedMapOf<String, Any?>(
                "id" to ids.next(), "key" to key, "asset_id" to assetId, "name" to field.name, "type" to field.type,
                "options" to optionsJson(field.options), "is_required" to 0, "is_active" to 1, "is_standard" to null, "is_builtin" to 0,
                "position" to position++, "help_text" to field.helpText, "checks" to "{}", "aliases" to "[]",
                "version" to 1, "created_at" to now, "updated_at" to now,
            )
            if (typeColumn) row["master_format_id"] = newFormat
            execute("INSERT INTO $TABLE (${row.keys.joinToString()}) VALUES (${placeholders(row.size)})", row.values.toList(), setOf(TABLE))
            keys[field.name.lowercase()] = key
            added++
        }
        return TypeChange(keys, added, removed)
    }

    /**
     * A copy (Clone) takes the answers to [sourceId]'s own fields (its type's, or added to it), as the web's clone does: by
     * name, each goes to [copyId]'s own field of that name (its type's, already added), and a field the copy hasn't got is
     * added to it with its answer. [answers] (the copy's, by field key) are moved to the copy's keys in place. A name a
     * standard field already has is left out (the server refuses it there).
     */
    fun AdcDatabase.copyOwnFields(sourceId: String, copyId: String, answers: MutableMap<String, String>, now: String) {
        if (!hasTable(TABLE)) return
        val answered = query(live(OWN_FIELDS, TABLE), listOf(sourceId)).filter { !answers[it.string("key").orEmpty()].isNullOrBlank() }
        if (answered.isEmpty()) return
        val copyKeys = query(live(OWN_FIELDS, TABLE), listOf(copyId))
            .associate { it.string("name").orEmpty().lowercase() to it.string("key").orEmpty() }.toMutableMap()
        val standard = query(live(STANDARD_FIELD_NAMES, TABLE)).map { it.string("name").orEmpty().lowercase() }.toSet()
        var position = 1 + (query(live(OWN_FIELDS, TABLE), listOf(copyId)).mapNotNull { it.int("position") }.maxOrNull() ?: -1)
        answered.forEach { f ->
            val name = f.string("name").orEmpty()
            val sourceKey = f.string("key").orEmpty()
            val value = answers.getValue(sourceKey)
            val target = copyKeys[name.lowercase()] ?: run {
                if (name.isBlank() || name.lowercase() in standard) return@forEach
                val key = freeKey()
                val row = linkedMapOf<String, Any?>(
                    "id" to ids.next(), "key" to key, "asset_id" to copyId, "name" to name, "type" to f.string("type"),
                    "options" to f.string("options"), "is_required" to 0, "is_active" to 1, "is_standard" to null, "is_builtin" to 0,
                    "position" to position++, "help_text" to f.string("help_text"), "checks" to "{}", "aliases" to "[]",
                    "version" to 1, "created_at" to now, "updated_at" to now,
                )
                execute("INSERT INTO $TABLE (${row.keys.joinToString()}) VALUES (${placeholders(row.size)})", row.values.toList(), setOf(TABLE))
                copyKeys[name.lowercase()] = key
                key
            }
            if (target != sourceKey) {
                answers.remove(sourceKey)
                answers[target] = value
            }
        }
    }

    /** The server's key for an asset's own field: 10 lower-case hex characters (the random end of a new id), not yet taken. */
    private fun AdcDatabase.freeKey(): String {
        while (true) {
            val key = ids.next().filter { it.isLetterOrDigit() }.lowercase().takeLast(KEY_LENGTH)
            if (!exists(live(FIELD_KEY_TAKEN, TABLE), listOf(key))) return key
        }
    }

    /** `[{label}]`, as the server writes a dropdown's choices; null when there are none. */
    private fun optionsJson(options: List<String>): String? =
        if (options.isEmpty()) null else JsonArray(options.map { label -> buildJsonObject { put("label", label) } }).toString()

    private companion object {
        const val TABLE = "asset_fields"
        const val KEY_LENGTH = 10
    }
}
