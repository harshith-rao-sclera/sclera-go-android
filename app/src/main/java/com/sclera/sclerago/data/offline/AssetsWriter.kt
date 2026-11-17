package com.sclera.sclerago.data.offline

import com.sclera.sclerago.data.mapper.DbJson
import com.sclera.sclerago.data.mapper.DbRow
import com.sclera.sclerago.data.mapper.answersJson
import com.sclera.sclerago.data.mapper.formatDbTime
import com.sclera.sclerago.data.mapper.splitAssetValues
import com.sclera.sclerago.data.sync.AdcDatabase
import com.sclera.sclerago.data.sync.SyncCredentialsProvider
import com.sclera.sclerago.domain.model.DataError
import com.sclera.sclerago.domain.model.DataException
import com.sclera.sclerago.domain.model.IMAGE_KIND
import com.sclera.sclerago.domain.model.documentKind
import com.sclera.sclerago.domain.model.LocValue
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.NewAsset
import com.sclera.sclerago.domain.model.NodeKind
import com.sclera.sclerago.domain.model.PhotoKind
import com.sclera.sclerago.domain.model.PhotoRule
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.model.photosShort
import com.sclera.sclerago.domain.model.sourceToDb
import com.sclera.sclerago.domain.model.statusToDb
import com.sclera.sclerago.domain.model.formatFor
import com.sclera.sclerago.domain.model.typeFieldName
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
    typeFieldsLookup: TypeFieldsLookup = TypeFieldsLookup { emptyList() },
) {
    private val typeFields = TypeFieldsWriter(typeFieldsLookup, ids)

    /** Adds [items] with their values, media and the scanned device each was made from. */
    suspend fun add(items: List<NewAsset>) {
        if (items.isEmpty()) return
        db.write(io) {
            val now = now()
            items.forEach { insertAsset(it, now) }
        }
        files.discardOriginals(items.flatMap { it.asset.media })
    }

    /**
     * Keeps the details form [values] (app field keys) of asset [id]; nothing is written when nothing changed. A new type
     * brings its fields (and the old type's empty ones go), and answers to type fields not made yet ([typeAnswers]) go
     * under the new fields' keys.
     */
    suspend fun saveDetails(id: String, values: Map<String, String>) = db.write(io) {
        val row = assetState(id) ?: throw DataException(DataError.FAILED)
        val valuesRow = queryOne(ASSET_VALUES_ROW, listOf(id))
        val typed = typeAnswers(values)
        val plain = values.filterKeys { typeFieldName(it) == null }
        val split = splitAssetValues(plain)
        val now = now()

        val oldColumns = COLUMN_KEYS.associateWith { row.string(it).orEmpty() }
        val newColumns = oldColumns + split.columns.mapValues { it.value.trim() }
        val oldExtra = DbJson.stringMap(valuesRow?.answersJson())
        val newExtra = oldExtra.toMutableMap().also { map -> split.extra.forEach { (k, v) -> if (v.isBlank()) map.remove(k) else map[k] = v } }
        val classification = classify(plain, row.string("master_format_id"), row.string("jacs_code_id"), row.string("classification_source"))
        val oldFormat = row.id("master_format_id")
        val typeChange = if (classification.formatId != oldFormat || typed.values.any { it.isNotBlank() }) {
            with(typeFields) { applyType(id, classification.formatId, newExtra, now) }
        } else null
        typeChange?.let { putTypeAnswers(newExtra, typed, it) }

        val changes = diff(
            id,
            COLUMN_KEYS.associateWith { oldColumns.getValue(it) to newColumns.getValue(it) } +
                (oldExtra.keys + newExtra.keys).associateWith { (oldExtra[it].orEmpty()) to (newExtra[it].orEmpty()) },
        ).toMutableList()
        if (classification.formatId != oldFormat) changes += Change("Master format", null, formatLabel(oldFormat), formatLabel(classification.formatId)) to null
        val oldJacs = row.id("jacs_code_id")
        if (classification.jacs != oldJacs) changes += Change("JACS code", null, oldJacs, classification.jacs) to null
        if (changes.isEmpty()) return@write

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
        typeChange?.takeIf { it.changed }?.let { typeHistory(id, classification.formatId, it, now) }
    }

    /**
     * Moves [assetIds] to [status], with the flags that go with it (see `statusToDb`); assets already there are left alone.
     * Submitting follows the server's rule (`POST /assets/bulk/submit`): only drafted or exception assets are submitted; one
     * with a mandatory field empty, or fewer photos of a kind than [photoRules] ask for, becomes an exception when
     * [allowExceptions] and is left as it is otherwise; assets that are already submitted or validated stay as they are.
     */
    suspend fun setStatus(
        assetIds: Collection<String>,
        status: Status,
        allowExceptions: Boolean = true,
        photoRules: List<PhotoRule> = emptyList(),
    ) = db.write(io) {
        val now = now()
        val required = if (status == Status.SUBMITTED) requiredKeys() else null
        assetIds.forEach { id ->
            val row = assetState(id) ?: return@forEach
            val moved = if (required == null) status else submitTarget(row, id, required, photoRules, allowExceptions) ?: return@forEach
            val target = statusToDb(moved)
            val unchanged = row.string("status") == target.status && row.bool("validated") == target.validated &&
                (target.aiSuggestion == null || row.bool("ai_suggestion") == target.aiSuggestion)
            if (unchanged) return@forEach
            execute(
                ASSET_UPDATE_STATUS,
                listOf(target.status, target.validated.bit(), target.aiSuggestion?.bit(), now, id),
                setOf("assets"),
            )
            statusLine(moved, wasDrafted = row.string("status") == "drafted")?.let { (action, text) -> history(id, action, text, null, now) }
        }
    }

    /** What submitting asset [id] makes of it, or null to leave it alone. */
    private fun AdcDatabase.submitTarget(
        row: DbRow,
        id: String,
        required: RequiredKeys,
        photoRules: List<PhotoRule>,
        allowExceptions: Boolean,
    ): Status? {
        if (row.string("status") != "drafted" && row.string("status") != "exception") return null
        val answers = DbJson.stringMap(queryOne(ASSET_VALUES_ROW, listOf(id))?.answersJson())
        val complete = required.missingFor(id, row, answers).isEmpty() && photosShort(photoRules, photoCounts(id)).isEmpty()
        return when {
            complete -> Status.SUBMITTED
            allowExceptions -> Status.EXCEPTION
            else -> null
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
        val mediaIds = mutableListOf<String>()
        db.write(io) {
            val now = now()
            assetIds.forEach { id ->
                if (!exists(ASSET_STATE.live("assets"), listOf(id))) return@forEach
                query(MEDIA_OF_ASSET.live("asset_media"), listOf(id)).forEach { m ->
                    val mediaId = m.string("id").orEmpty()
                    mediaIds += mediaId
                    // The file's only copy is its blob here.
                    if (hasTable("multimedia")) execute(MULTIMEDIA_DELETE, listOf(mediaId), setOf("multimedia"))
                }
                ASSET_CHILDREN.forEach { removeRows(it, "asset_id = ?", listOf(id), now) }
                removeRows("assets", "id = ?", listOf(id), now)
            }
        }
        files.forget(mediaIds)
    }

    /** Adds [media] to asset [id]: each file goes into the database (its only copy), where the sync uploads it from. */
    suspend fun attachMedia(id: String, media: List<Media>) {
        if (media.isEmpty()) return
        db.write(io) {
            if (!exists(ASSET_STATE.live("assets"), listOf(id))) throw DataException(DataError.FAILED)
            val now = now()
            media.forEach { insertMedia(id, it, now) }
        }
        files.discardOriginals(media)
    }

    /** Adds the comment [text] (already checked) to asset [assetId], written by the signed-in user; the sync pushes it. */
    suspend fun addComment(assetId: String, text: String) = db.write(io) {
        if (!exists(ASSET_STATE.live("assets"), listOf(assetId))) throw DataException(DataError.FAILED)
        val now = now()
        execute(COMMENT_INSERT, listOf(ids.next(), assetId, text, user.userId(), now, now), setOf("asset_activity"))
    }

    /** Takes the web page [documentId] off its asset (marked removed for the sync, as the server's DELETE does). */
    suspend fun removeWebDocument(documentId: String) = db.write(io) {
        removeRows("web_documents", "id = ? AND asset_id IS NOT NULL AND asset_id <> ''", listOf(documentId), now())
    }

    // ---- inside a transaction

    private fun AdcDatabase.insertAsset(item: NewAsset, now: String) {
        val asset = item.asset
        val typed = typeAnswers(item.values)
        val plain = item.values.filterKeys { typeFieldName(it) == null }
        val split = splitAssetValues(plain)
        val place = placeColumns(asset.locationId ?: asset.floorId ?: asset.buildingId)
        val classification = classify(plain, asset.masterFormatId, null, null)
        val status = statusToDb(asset.st)
        val extra = split.extra.filterValues { it.isNotBlank() }.toMutableMap()
        execute(
            ASSET_INSERT,
            listOf(
                asset.id, (split.columns["name"]?.trim()?.takeIf { it.isNotEmpty() } ?: asset.name).ifBlank { null },
                place.building, place.floor, place.location, status.status, sourceToDb(asset.src), item.deviceId, user.userId(),
                now, now, split.columns["manufacturer"].orNull(), split.columns["model"].orNull(), split.columns["serial_number"].orNull(),
                now, classification.source, classification.formatId, classification.jacs,
                // The status sets the AI flag where it decides it; otherwise the new asset's own flag is kept.
                status.validated.bit(), (status.aiSuggestion ?: asset.aiSuggestion).bit(),
            ),
            setOf("assets"),
        )
        // A new asset of a type gets the type's fields, as the server's POST /assets does.
        val typeChange = classification.formatId?.let { with(typeFields) { applyType(asset.id, it, extra, now) } }
        typeChange?.let { putTypeAnswers(extra, typed, it) }
        execute(withAnswers(ASSET_VALUES_INSERT), listOf(asset.id, "{}", "{}", DbJson.write(extra)), setOf("asset_values"))
        history(asset.id, "created", "Asset created", null, now)
        typeChange?.takeIf { it.changed }?.let { typeHistory(asset.id, classification.formatId, it, now) }
        asset.media.forEach { insertMedia(asset.id, it, now) }
    }

    /**
     * Adds [media] as a row of asset [assetId] with its file in `multimedia` (the only copy: screens read it from there
     * through the media provider, and the sync uploads it from there). Without a `multimedia` table the file has nowhere
     * to go, so it is refused.
     */
    private fun AdcDatabase.insertMedia(assetId: String, media: Media, now: String) {
        if (!hasTable("multimedia")) throw DataException(DataError.FAILED)
        val mediaId = ids.next()
        val stored = files.read(media) ?: throw DataException(DataError.FAILED)
        // A file uploaded from the Documents tab is the asset's source document, when the server takes it as a document
        // (a photo stays a photo, which the Media tab shows).
        if (media.sourceDocument && hasTable("documents")) {
            documentKind(stored.filename, stored.contentType)?.takeIf { it != IMAGE_KIND }?.let { docKind ->
                return insertDocument(assetId, mediaId, stored, docKind, now)
            }
        }
        val kind = when {
            media.video -> "video"
            media.audio || stored.contentType?.startsWith("audio/") == true -> "audio"
            media.isDoc -> "document"
            else -> "image"
        }
        val position = queryOne(MEDIA_NEXT_POSITION.live("asset_media"), listOf(assetId))?.int("p") ?: 0
        val row = linkedMapOf<String, Any?>(
            "id" to mediaId, "asset_id" to assetId, "kind" to kind, "filename" to stored.filename, "content_type" to stored.contentType,
            "size_bytes" to stored.sizeBytes, "width" to stored.width, "height" to stored.height,
            "duration_seconds" to (if (media.video || media.audio) media.durationMs / 1000.0 else null), "position" to position,
            "created_by" to user.userId(), "version" to 1, "created_at" to now, "updated_at" to now,
        )
        // Only a photo that answers no field can be of a kind the photo rules count.
        if (kind == "image" && media.fieldId == null && media.category != null && hasColumn("asset_media", "category")) row["category"] = media.category.db
        execute("INSERT INTO asset_media (${row.keys.joinToString()}) VALUES (${placeholders(row.size)})", row.values.toList(), setOf("asset_media"))
        queueUpload("asset_media", mediaId, stored, position)
    }

    /**
     * Keeps [stored] as asset [assetId]'s source document, as the server links an asset to the file it was read from:
     * a `documents` row [id], its file queued under the same id, and `assets.source_document_id` set to it (an asset
     * has one, so a new upload takes the place of the last).
     */
    private fun AdcDatabase.insertDocument(assetId: String, id: String, stored: PickedContent, kind: String, now: String) {
        execute(DOCUMENT_INSERT, listOf(id, stored.filename, stored.contentType, stored.sizeBytes, kind, user.userId(), now, now), setOf("documents"))
        queueUpload("documents", id, stored, 0)
        execute(ASSET_SET_SOURCE_DOCUMENT, listOf(id, now, assetId), setOf("assets"))
    }

    /**
     * Keeps [stored] as the file of row [id] of [table], its only copy on the device, and so queues it for upload: the
     * engine sends photos under 10MB with its image sync, videos and bigger files with its large-file sync.
     */
    private fun AdcDatabase.queueUpload(table: String, id: String, stored: PickedContent, order: Int) {
        execute(
            MULTIMEDIA_INSERT,
            listOf(id, stored.bytes, table, imageColumn(table), id, stored.contentType ?: "application/octet-stream", order),
            setOf("multimedia"),
        )
    }

    /** The column of [table] the server's image configuration says holds the file; `object_path` when it says nothing. */
    private fun AdcDatabase.imageColumn(table: String): String {
        if (!hasTable("global_timestamp")) return DEFAULT_IMAGE_COLUMN
        val config = DbJson.obj(queryOne(IMAGE_CONFIG)?.string("image_config"))
        return ((config[table] as? JsonArray)?.firstOrNull() as? JsonPrimitive)?.contentOrNull ?: DEFAULT_IMAGE_COLUMN
    }

    private fun AdcDatabase.writeValues(assetId: String, row: DbRow?, values: Map<String, String>, changedKeys: List<String?>) {
        val edited = changedKeys.filterNotNull().toSet()
        val confidence = DbJson.doubleMap(row?.string("confidence")).filterKeys { it !in edited }
        val sources = DbJson.stringMap(row?.string("sources")).filterKeys { it !in edited }
        val json = DbJson.write(values)
        if (row == null) {
            execute(withAnswers(ASSET_VALUES_INSERT), listOf(assetId, DbJson.writeDoubles(confidence), DbJson.write(sources), json), setOf("asset_values"))
        } else {
            execute(withAnswers(ASSET_VALUES_UPDATE), listOf(json, DbJson.writeDoubles(confidence), DbJson.write(sources), assetId), setOf("asset_values"))
        }
    }

    /** The server's history line for type fields added or removed (`type_fields`). */
    private fun AdcDatabase.typeHistory(assetId: String, formatId: String?, change: TypeChange, now: String) {
        val parts = buildList {
            if (change.added > 0) add("added ${change.added} field(s) for ${formatLabel(formatId) ?: "its type"}")
            if (change.removed > 0) add("removed ${change.removed} empty field(s) of its old type")
        }
        val text = parts.joinToString("; ").replaceFirstChar { it.uppercase() }
        history(assetId, "type_fields", text, null, now)
    }

    /** Puts the [typed] answers (by type field name) into [answers] under the keys the asset's fields now have; blank ones are dropped. */
    private fun putTypeAnswers(answers: MutableMap<String, String>, typed: Map<String, String>, change: TypeChange) {
        typed.forEach { (name, value) ->
            val key = change.keysByName[name.trim().lowercase()] ?: return@forEach
            if (value.isBlank()) answers.remove(key) else answers[key] = value.trim()
        }
    }

    private fun AdcDatabase.history(assetId: String, action: String, text: String, changes: String?, now: String) {
        execute(ACTIVITY_INSERT, listOf(ids.next(), assetId, action, text, changes, user.userId(), now, now), setOf("asset_activity"))
    }

    /** How many photos of each kind asset [id] has (none counted where the database has no photo kinds yet). */
    private fun AdcDatabase.photoCounts(id: String): Map<PhotoKind, Int> {
        if (!hasColumn("asset_media", "category")) return emptyMap()
        return query(PHOTO_COUNTS.live("asset_media"), listOf(id))
            .mapNotNull { r -> PhotoKind.of(r.string("category"))?.let { it to (r.int("n") ?: 0) } }
            .toMap()
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
        val match = formats.activeNow().formatFor(values)
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
