package com.sclera.sclerago.data.offline

import com.sclera.sclerago.data.mapper.DbJson
import com.sclera.sclerago.data.mapper.DbRow
import com.sclera.sclerago.data.mapper.formatDbTime
import com.sclera.sclerago.data.mapper.splitAssetValues
import com.sclera.sclerago.data.sync.AdcDatabase
import com.sclera.sclerago.data.sync.SyncCredentialsProvider
import com.sclera.sclerago.domain.model.DataError
import com.sclera.sclerago.domain.model.DataException
import com.sclera.sclerago.domain.model.LocValue
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.NewAsset
import com.sclera.sclerago.domain.model.NodeKind
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.model.sourceToDb
import com.sclera.sclerago.domain.model.statusFromDb
import com.sclera.sclerago.domain.model.statusToDb
import com.sclera.sclerago.domain.system.IdGenerator
import com.sclera.sclerago.domain.system.TimeProvider
import com.sclera.sclerago.domain.system.next
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** Where an asset's place sits in the tree: the building, floor and location ids (any may be null). */
private class PlaceColumns(val building: String?, val floor: String?, val location: String?)

/** The master format and JACS code an asset ends up with. */
private class Classification(val formatId: String?, val jacs: String?, val source: String?)

/** The links from an asset to the rows that hang off it; removing the asset removes them too (the server's CASCADE rules). */
private val ASSET_CHILDREN = listOf(
    "asset_values", "asset_media", "asset_activity", "asset_fields", "validation_issues", "validation_results", "lookup_jobs", "web_documents",
)

private const val GENERIC = "Generic"

/**
 * Writes assets to the offline database: add, edit the details, status and place, delete and attach media. Every
 * call is one transaction, so the engine's triggers see a consistent change and push it. Rows are written the way
 * the server writes them (UUIDv7 ids, UTC timestamps, version + 1, a history line), and removing is marking removed.
 */
class AssetsWriter(
    private val db: AdcDatabase,
    private val io: CoroutineDispatcher,
    private val time: TimeProvider,
    private val ids: IdGenerator,
    private val user: SyncCredentialsProvider,
    private val places: PlacesOfflineSource,
    private val formats: MasterFormatsOfflineSource,
    private val files: AssetFileStore,
) {
    /** Adds [items] with their values, media and the scanned device each was made from. */
    suspend fun add(items: List<NewAsset>) {
        if (items.isEmpty()) return
        writeFiles { created ->
            val now = now()
            items.forEach { insertAsset(it, now, created) }
        }
        files.discardOriginals(items.flatMap { it.asset.media })
    }

    /** Keeps the details form [values] (app field keys) of asset [id]; nothing is written when nothing changed. */
    suspend fun saveDetails(id: String, values: Map<String, String>) = db.write(io) {
        val row = assetState(id) ?: throw DataException(DataError.FAILED)
        val valuesRow = queryOne(ASSET_VALUES_ROW, listOf(id))
        val split = splitAssetValues(values)

        val oldColumns = COLUMN_KEYS.associateWith { row.string(it).orEmpty() }
        val newColumns = oldColumns + split.columns.mapValues { it.value.trim() }
        val oldExtra = DbJson.stringMap(valuesRow?.string("values"))
        val newExtra = oldExtra.toMutableMap().also { map -> split.extra.forEach { (k, v) -> if (v.isBlank()) map.remove(k) else map[k] = v } }
        val classification = classify(values, row.string("master_format_id"), row.string("jacs_code_id"), row.string("classification_source"))

        val changes = diff(
            id,
            COLUMN_KEYS.associateWith { oldColumns.getValue(it) to newColumns.getValue(it) } +
                (oldExtra.keys + newExtra.keys).associateWith { (oldExtra[it].orEmpty()) to (newExtra[it].orEmpty()) },
        ).toMutableList()
        val oldFormat = row.id("master_format_id")
        if (classification.formatId != oldFormat) changes += Change("Master format", null, formatLabel(oldFormat), formatLabel(classification.formatId)) to null
        val oldJacs = row.id("jacs_code_id")
        if (classification.jacs != oldJacs) changes += Change("JACS code", null, oldJacs, classification.jacs) to null
        if (changes.isEmpty()) return@write

        val now = now()
        execute(
            ASSET_UPDATE_VALUES,
            listOf(
                keep(row, "name", newColumns.getValue("name")), keep(row, "manufacturer", newColumns.getValue("manufacturer")),
                keep(row, "model", newColumns.getValue("model")), keep(row, "serial_number", newColumns.getValue("serial_number")),
                keep(row, "master_format_id", classification.formatId), keep(row, "jacs_code_id", classification.jacs),
                keep(row, "classification_source", classification.source), now, now, id,
            ),
            setOf("assets"),
        )
        if (newExtra != oldExtra) writeValues(id, valuesRow, newExtra, changedKeys = changes.map { it.second })
        history(id, "updated", "Asset updated", changesJson(changes.map { it.first }), now)
    }

    /** Moves [assetIds] to [status], with the flags that go with it (see `statusToDb`); assets already there are left alone. */
    suspend fun setStatus(assetIds: Collection<String>, status: Status) = db.write(io) {
        val target = statusToDb(status)
        val now = now()
        assetIds.forEach { id ->
            val row = assetState(id) ?: return@forEach
            val current = statusFromDb(row.string("status"), row.bool("needs_review"), row.bool("validated"), row.bool("ai_suggestion"))
            if (current == status) return@forEach
            execute(
                ASSET_UPDATE_STATUS,
                listOf(target.status, target.validated.bit(), target.aiSuggestion?.bit(), target.needsReview.bit(), now, id),
                setOf("assets"),
            )
            statusLine(status, wasDrafted = row.string("status") == "drafted")?.let { (action, text) -> history(id, action, text, null, now) }
        }
    }

    /** Puts [assetIds] in the place [location] names (no place id clears it). */
    suspend fun assignLocation(assetIds: Collection<String>, location: LocValue) = db.write(io) {
        val place = placeColumns(location.placeId)
        val now = now()
        assetIds.forEach { id ->
            val row = assetState(id) ?: return@forEach
            val old = PlaceColumns(row.id("building_id"), row.id("floor_id"), row.id("location_id"))
            if (old.building == place.building && old.floor == place.floor && old.location == place.location) return@forEach
            execute(
                ASSET_UPDATE_PLACE,
                listOf(keep(row, "building_id", place.building), keep(row, "floor_id", place.floor), keep(row, "location_id", place.location), now, now, id),
                setOf("assets"),
            )
            val changes = listOf(
                Triple("Building", PLACE_NAME_BUILDING, old.building to place.building),
                Triple("Floor", PLACE_NAME_FLOOR, old.floor to place.floor),
                Triple("Location", PLACE_NAME_LOCATION, old.location to place.location),
            ).filter { (_, _, moved) -> moved.first != moved.second }
                .map { (label, sql, moved) -> Change(label, null, placeName(sql, moved.first), placeName(sql, moved.second)) }
            history(id, "updated", "Asset updated", changesJson(changes), now)
        }
    }

    /** Removes [assetIds] and what hangs off them (values, media and their files, history, extra fields, validation results). */
    suspend fun delete(assetIds: Collection<String>) {
        val keys = mutableListOf<String>()
        db.write(io) {
            val now = now()
            assetIds.forEach { id ->
                if (!exists(ASSET_STATE.live("assets"), listOf(id))) return@forEach
                query(MEDIA_OF_ASSET.live("asset_media"), listOf(id)).forEach { m ->
                    localKey(m.string("object_path").orEmpty())?.let(keys::add)
                    if (hasTable("multimedia")) execute(MULTIMEDIA_DELETE, listOf(m.string("id").orEmpty()), setOf("multimedia"))
                }
                ASSET_CHILDREN.forEach { removeRows(it, "asset_id = ?", listOf(id), now) }
                removeRows("assets", "id = ?", listOf(id), now)
            }
        }
        files.delete(keys)
    }

    /** Adds [media] to asset [id]: each file is copied into private storage and registered for upload. */
    suspend fun attachMedia(id: String, media: List<Media>) {
        if (media.isEmpty()) return
        writeFiles { created ->
            if (!exists(ASSET_STATE.live("assets"), listOf(id))) throw DataException(DataError.FAILED)
            val now = now()
            media.forEach { insertMedia(id, it, now, created) }
        }
        files.discardOriginals(media)
    }

    // ---- inside a transaction

    private fun AdcDatabase.insertAsset(item: NewAsset, now: String, created: MutableList<String>) {
        val asset = item.asset
        val split = splitAssetValues(item.values)
        val place = placeColumns(asset.locationId ?: asset.floorId ?: asset.buildingId)
        val classification = classify(item.values, asset.masterFormatId, null, null)
        val status = statusToDb(asset.st)
        val extra = split.extra.filterValues { it.isNotBlank() }
        execute(
            ASSET_INSERT,
            listOf(
                asset.id, (split.columns["name"]?.trim()?.takeIf { it.isNotEmpty() } ?: asset.name).ifBlank { null },
                place.building, place.floor, place.location, status.status, sourceToDb(asset.src), item.deviceId, user.userId(),
                now, now, split.columns["manufacturer"].orNull(), split.columns["model"].orNull(), split.columns["serial_number"].orNull(),
                status.needsReview.bit(), now, classification.source, classification.formatId, classification.jacs,
                status.validated.bit(), (status.aiSuggestion ?: false).bit(),
            ),
            setOf("assets"),
        )
        execute(ASSET_VALUES_INSERT, listOf(asset.id, "{}", "{}", DbJson.write(extra)), setOf("asset_values"))
        history(asset.id, "created", "Asset created", null, now)
        asset.media.forEach { insertMedia(asset.id, it, now, created) }
    }

    private fun AdcDatabase.insertMedia(assetId: String, media: Media, now: String, created: MutableList<String>) {
        val mediaId = ids.next()
        val stored = files.store(media, mediaId) ?: throw DataException(DataError.FAILED)
        created += stored.key
        val kind = when {
            media.video -> "video"
            stored.contentType?.startsWith("audio/") == true -> "audio"
            media.isDoc -> "document"
            else -> "image"
        }
        val position = queryOne(MEDIA_NEXT_POSITION.live("asset_media"), listOf(assetId))?.int("p") ?: 0
        execute(
            MEDIA_INSERT,
            listOf(
                mediaId, assetId, kind, stored.filename, stored.contentType, stored.sizeBytes, localObjectPath(stored.key),
                stored.width, stored.height, if (media.video) media.durationMs / 1000.0 else null, position, user.userId(), now, now,
            ),
            setOf("asset_media"),
        )
        if (kind != "video" && hasTable("multimedia")) {
            files.bytes(stored.key)?.let { bytes ->
                execute(
                    MULTIMEDIA_INSERT,
                    listOf(mediaId, bytes, imageColumn(), mediaId, stored.contentType ?: "application/octet-stream", position),
                    setOf("multimedia"),
                )
            }
        }
    }

    /** The column of `asset_media` the server's image configuration says holds the file; `object_path` when it says nothing. */
    private fun AdcDatabase.imageColumn(): String {
        if (!hasTable("global_timestamp")) return DEFAULT_IMAGE_COLUMN
        val config = DbJson.obj(queryOne(IMAGE_CONFIG)?.string("image_config"))
        return ((config["asset_media"] as? JsonArray)?.firstOrNull() as? JsonPrimitive)?.contentOrNull ?: DEFAULT_IMAGE_COLUMN
    }

    private fun AdcDatabase.writeValues(assetId: String, row: DbRow?, values: Map<String, String>, changedKeys: List<String?>) {
        val edited = changedKeys.filterNotNull().toSet()
        val confidence = DbJson.doubleMap(row?.string("confidence")).filterKeys { it !in edited }
        val sources = DbJson.stringMap(row?.string("sources")).filterKeys { it !in edited }
        val json = DbJson.write(values)
        if (row == null) {
            execute(ASSET_VALUES_INSERT, listOf(assetId, DbJson.writeDoubles(confidence), DbJson.write(sources), json), setOf("asset_values"))
        } else {
            execute(ASSET_VALUES_UPDATE, listOf(json, DbJson.writeDoubles(confidence), DbJson.write(sources), assetId), setOf("asset_values"))
        }
    }

    private fun AdcDatabase.history(assetId: String, action: String, text: String, changes: String?, now: String) {
        execute(ACTIVITY_INSERT, listOf(ids.next(), assetId, action, text, changes, user.userId(), now, now), setOf("asset_activity"))
    }

    private fun AdcDatabase.assetState(id: String): DbRow? = queryOne(ASSET_STATE.live("assets"), listOf(id))

    private fun AdcDatabase.placeColumns(placeId: String?): PlaceColumns {
        val steps = placeId?.let(places::pathNow).orEmpty()
        return PlaceColumns(
            steps.firstOrNull { it.kind == NodeKind.BUILDING }?.id,
            steps.firstOrNull { it.kind == NodeKind.FLOOR }?.id,
            steps.firstOrNull { it.kind == NodeKind.LOCATION }?.id,
        )
    }

    private fun AdcDatabase.placeName(sql: String, id: String?): String? = id?.let { queryOne(sql, listOf(it))?.string("name") }

    /** The master format and JACS code [values] choose (system, asset and sub type; "Generic" is no choice); what the asset has when they choose none. */
    private fun classify(values: Map<String, String>, formatId: String?, jacs: String?, source: String?): Classification {
        fun pick(key: String) = values[key]?.trim()?.takeIf { it.isNotEmpty() && it != GENERIC }
        val system = pick("sys")
        val match = system?.let { s ->
            formats.activeNow().firstOrNull {
                it.systemType.equals(s, true) && it.assetType.equals(pick("typ"), true) && it.subtype.equals(pick("sub"), true)
            }
        }
        val format = match?.id ?: formatId?.takeIf { it.isNotBlank() }
        val code = if ("jacs" in values) values["jacs"].orNull() else jacs?.takeIf { it.isNotBlank() }
        return Classification(format, code, if (match != null) "manual" else source?.takeIf { it.isNotBlank() })
    }

    /** The changes between old and new answers keyed by database field key, as (history entry, key) pairs. */
    private fun AdcDatabase.diff(assetId: String, answers: Map<String, Pair<String, String>>): List<Pair<Change, String?>> {
        val names = query(FIELD_NAMES.live("asset_fields"), listOf(assetId)).associateBy { it.string("key").orEmpty() }
        return answers.filter { (_, v) -> v.first != v.second }.map { (key, v) ->
            val field = names[key]
            Change(field?.string("name") ?: key, field?.string("id"), v.first.ifEmpty { null }, v.second.ifEmpty { null }) to key
        }
    }

    // ---- helpers

    private fun formatLabel(id: String?): String? = id?.let { formats.activeNow().firstOrNull { f -> f.id == it }?.label }

    private fun now() = formatDbTime(time.nowMillis())

    private suspend fun <T> writeFiles(block: AdcDatabase.(created: MutableList<String>) -> T): T {
        val created = mutableListOf<String>()
        try {
            return db.write(io) { block(created) }
        } catch (e: Throwable) {
            files.delete(created)
            throw e
        }
    }

    private fun String.live(table: String) = replace("%LIVE%", db.notRemoved(table))

    private fun String?.orNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun DbRow.id(column: String): String? = string(column)?.takeIf { it.isNotBlank() }

    /**
     * What to write to [column] of [row] for [value]: null for a blank value, except that a column the server left
     * empty (not null) stays as it is, so saving does not push a change for it.
     */
    private fun keep(row: DbRow, column: String, value: String?): String? =
        if (value.isNullOrBlank()) row.string(column)?.takeIf { it.isBlank() } else value

    private fun statusLine(status: Status, wasDrafted: Boolean): Pair<String, String>? = when (status) {
        Status.SUBMITTED -> "submitted" to "Asset submitted"
        Status.EXCEPTION -> "exception" to "Asset submitted with exception"
        Status.VALIDATED -> "validated" to "Asset validated"
        Status.DRAFT, Status.INPUT, Status.AI -> if (wasDrafted) null else "drafted" to "Asset moved back to draft"
    }

    private class Change(val field: String, val fieldId: String?, val from: String?, val to: String?)

    /** `[{field, fieldId, from, to}]`, the server's history format. */
    private fun changesJson(changes: List<Change>): String? = if (changes.isEmpty()) null else JsonArray(
        changes.map {
            buildJsonObject {
                put("field", it.field)
                put("fieldId", it.fieldId?.let(::JsonPrimitive) ?: JsonNull)
                put("from", it.from?.let(::JsonPrimitive) ?: JsonNull)
                put("to", it.to?.let(::JsonPrimitive) ?: JsonNull)
            }
        },
    ).toString()

    private companion object {
        const val DEFAULT_IMAGE_COLUMN = "object_path"

        /** The standard answers kept in columns of `assets`. */
        val COLUMN_KEYS = listOf("name", "manufacturer", "model", "serial_number")
    }
}
