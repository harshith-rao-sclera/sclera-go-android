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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** Where an asset's place sits in the tree: the building, floor and location ids (any may be null). */
private data class PlaceColumns(val building: String?, val floor: String?, val location: String?)

/** The master format and JACS code an asset ends up with. */
private class Classification(val formatId: String?, val jacs: String?, val source: String?)

/** The links from an asset to the rows that hang off it; removing the asset removes them too (the server's CASCADE rules). */
private val ASSET_CHILDREN = listOf(
    "asset_values", "asset_media", "asset_activity", "asset_fields", "validation_issues", "validation_results", "lookup_jobs", "web_documents",
)

/**
 * Writes assets to the offline database: add, edit the details, status and place, delete, and attach or remove media. Every
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
        // Videos are compressed first (outside the write, as it takes a while); the copies go once they are kept or not.
        val ready = items.map { it.copy(asset = it.asset.copy(media = files.prepare(it.asset.media))) }
        val originals = items.flatMap { it.asset.media }
        try {
            db.write(io) {
                val now = now()
                ready.forEach { insertAsset(it, now) }
            }
            files.discardOriginals(originals)
        } finally {
            files.discardOriginals(ready.flatMap { it.asset.media } - originals.toSet())
        }
    }

    /**
     * Keeps the details form [values] (app field keys) of asset [id]; nothing is written when nothing changed. A new type
     * brings its fields (and the old type's empty ones go), and answers to type fields not made yet ([typeAnswers]) go
     * under the new fields' keys.
     */
    suspend fun saveDetails(
        id: String,
        values: Map<String, String>,
        sources: Map<String, String> = emptyMap(),
        place: LocValue? = null,
    ) = db.write(io) {
        val row = assetState(id) ?: throw DataException(DataError.FAILED)
        val valuesRow = queryOne(ASSET_VALUES_ROW, listOf(id))
        // The fields before the edit: its history line lists their changes (a new type's fields have their own line).
        val historyFields = query(HISTORY_FIELDS.live("asset_fields"), listOf(id))
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

        val changedKeys = (COLUMN_KEYS.filter { oldColumns.getValue(it) != newColumns.getValue(it) } +
            (oldExtra.keys + newExtra.keys).filter { oldExtra[it].orEmpty() != newExtra[it].orEmpty() }).toSet()
        val retyped = classification.formatId != oldFormat
        val oldJacs = row.id("jacs_code_id")
        val recoded = classification.jacs != oldJacs
        val oldPlace = PlaceColumns(row.id("building_id"), row.id("floor_id"), row.id("location_id"))
        val newPlace = place?.let { placeColumns(it.placeId) } ?: oldPlace
        val moved = newPlace != oldPlace
        if (changedKeys.isEmpty() && !retyped && !recoded && !moved) return@write

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
        // The place goes in with the rest of the edit, as the web sends it in the same PATCH.
        if (moved) {
            execute(
                ASSET_SET_PLACE,
                listOf(keep(row, "building_id", newPlace.building), keep(row, "floor_id", newPlace.floor), keep(row, "location_id", newPlace.location), id),
                setOf("assets"),
            )
        }
        // Where applied AI suggestions came from (database key -> page), for values set here and real web pages only.
        val pages = splitAssetValues(sources).let { it.columns + it.extra }
            .filter { (key, _) -> key in changedKeys && !(if (key in COLUMN_KEYS) newColumns[key] else newExtra[key]).isNullOrBlank() }
            .mapNotNull { (key, url) -> webLink(url)?.let { key to it } }.toMap()
        val merged = mergeAnswers(valuesRow, oldExtra, newExtra)
        if (newExtra != oldExtra || pages.isNotEmpty()) writeValues(id, valuesRow, merged, changedKeys = changedKeys.toList(), pages = pages)
        keepSources(id, pages, newColumns["manufacturer"], newColumns["model"], user.userId(), ids::next, now)
        // One line for everything the edit changed, as the server's record_edit: answers in form order with their stored
        // values, then the place, the master format and the JACS code. Nothing when only unlisted answers changed.
        val lines = answerChanges(historyFields, row, newColumns, DbJson.obj(valuesRow?.answersJson()), merged) +
            placeChanges(oldPlace, newPlace) +
            listOfNotNull(
                Change("Master format", null, text(formatLabel(oldFormat)), text(formatLabel(classification.formatId))).takeIf { retyped },
                Change("JACS code", null, text(oldJacs), text(classification.jacs)).takeIf { recoded },
            )
        if (lines.isNotEmpty()) history(id, "updated", "Asset updated", changesJson(lines), now)
        typeChange?.takeIf { it.changed }?.let { typeHistory(id, classification.formatId, it, now) }
        refreshAiSuggestion(id, row.bool("ai_suggestion"))
    }

    /** Keeps asset [id]'s `ai_suggestion` right after its answers or fields changed, as the server's refresh_ai_suggestion. */
    private fun AdcDatabase.refreshAiSuggestion(id: String, current: Boolean) {
        val fillable = aiFillable(id)
        if (fillable != current) execute(ASSET_SET_AI_SUGGESTION, listOf(fillable.bit(), id), setOf("assets"))
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
            // Back in draft is what a change request asked for, so it closes (the server's close_change_request).
            if (target.status == "drafted" && hasColumn("assets", "change_requested_at")) execute(ASSET_CLOSE_CHANGE_REQUEST, listOf(id), setOf("assets"))
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
            history(id, "updated", "Asset updated", changesJson(placeChanges(old, place)), now)
        }
    }

    /**
     * Asks an admin to reopen submitted asset [id], with [note] saying what needs changing, as the server's
     * POST …/change-request: the asset stays submitted, and asking again replaces the note.
     */
    suspend fun requestChange(id: String, note: String?) = db.write(io) {
        if (assetState(id) == null || !hasColumn("assets", "change_requested_at")) throw DataException(DataError.FAILED)
        val now = now()
        execute(ASSET_REQUEST_CHANGE, listOf(user.userId(), now, note, now, id), setOf("assets"))
        history(id, "change_requested", note?.let { "Change requested: $it" } ?: "Change requested", null, now)
    }

    /**
     * Closes asset [id]'s change request without reopening it, as the server's DELETE …/change-request: [declined] by an
     * admin, else withdrawn by whoever asked.
     */
    suspend fun closeChangeRequest(id: String, declined: Boolean) = db.write(io) {
        if (assetState(id) == null || !hasColumn("assets", "change_requested_at")) throw DataException(DataError.FAILED)
        val now = now()
        execute(ASSET_DROP_CHANGE_REQUEST, listOf(now, id), setOf("assets"))
        if (declined) history(id, "change_declined", "Change request declined", null, now)
        else history(id, "change_withdrawn", "Change request withdrawn", null, now)
    }

    /**
     * Deletes [assetIds] (marked removed for the sync) with what hangs off them. In a property linked to a VDMS
     * ([vdmsLinked]) each is first marked as being deleted, which VDMS confirms, as the server's DELETE does.
     */
    suspend fun delete(assetIds: Collection<String>, vdmsLinked: Boolean = false) {
        val mediaIds = mutableListOf<String>()
        db.write(io) {
            val now = now()
            assetIds.forEach { id ->
                if (!exists(ASSET_STATE.live("assets"), listOf(id))) return@forEach
                query(MEDIA_OF_ASSET.live("asset_media"), listOf(id)).forEach { m ->
                    val mediaId = m.string("id").orEmpty()
                    mediaIds += mediaId
                    clearFile(mediaId)
                    removeRows(MEDIA_READINGS, "media_id = ?", listOf(mediaId), now)
                }
                ASSET_CHILDREN.forEach { removeRows(it, "asset_id = ?", listOf(id), now) }
                if (vdmsLinked && hasColumn("assets", "vdms_state")) execute(ASSET_VDMS_DELETING, listOf(id), setOf("assets"))
                removeRows("assets", "id = ?", listOf(id), now)
            }
        }
        files.forget(mediaIds)
    }

    /** Adds [media] to asset [id]: each file goes into the database (its only copy), where the sync uploads it from. */
    suspend fun attachMedia(id: String, media: List<Media>) {
        if (media.isEmpty()) return
        // Videos are compressed first (outside the write, as it takes a while); the copies go once they are kept or not.
        val ready = files.prepare(media)
        try {
            db.write(io) {
                if (!exists(ASSET_STATE.live("assets"), listOf(id))) throw DataException(DataError.FAILED)
                val now = now()
                ready.forEach { insertMedia(id, it, now) }
            }
            files.discardOriginals(media)
        } finally {
            files.discardOriginals(ready - media.toSet())
        }
    }

    /**
     * Removes the media [mediaId] of asset [assetId] and its file, as the server's `DELETE …/media/{id}`: a photo, video
     * or voice memo that answers a field is taken out of that field's answer too. The sync pushes the change.
     */
    suspend fun removeMedia(assetId: String, mediaId: String) {
        db.write(io) {
            val row = queryOne(MEDIA_ROW.live("asset_media"), listOf(mediaId, assetId)) ?: throw DataException(DataError.FAILED)
            val now = now()
            row.id("field_id")?.let { dropFromAnswer(assetId, it, mediaId, now) }
            clearFile(mediaId)
            // What the AI read off the photo goes with it (the server's CASCADE from asset_media).
            removeRows(MEDIA_READINGS, "media_id = ?", listOf(mediaId), now)
            removeRows("asset_media", "id = ?", listOf(mediaId), now)
        }
        files.forget(listOf(mediaId))
    }

    /** Adds the comment [text] (already checked) to asset [assetId], written by the signed-in user; the sync pushes it. */
    suspend fun addComment(assetId: String, text: String) = db.write(io) {
        if (!exists(ASSET_STATE.live("assets"), listOf(assetId))) throw DataException(DataError.FAILED)
        val now = now()
        execute(COMMENT_INSERT, listOf(ids.next(), assetId, text, user.userId(), now, now), setOf("asset_activity"))
    }

    /** Changes comment [commentId] of asset [assetId] to [text] (already checked); nothing is written when it is the same. */
    suspend fun updateComment(assetId: String, commentId: String, text: String) = db.write(io) {
        val row = queryOne(COMMENT_ROW.live("asset_activity"), listOf(commentId, assetId)) ?: throw DataException(DataError.FAILED)
        if (row.string("text") == text) return@write
        execute(COMMENT_UPDATE.live("asset_activity"), listOf(text, now(), commentId, assetId), setOf("asset_activity"))
    }

    /** Removes comment [commentId] of asset [assetId]: marked removed for the sync, as the server's DELETE does. */
    suspend fun removeComment(assetId: String, commentId: String) = db.write(io) {
        if (queryOne(COMMENT_ROW.live("asset_activity"), listOf(commentId, assetId)) == null) throw DataException(DataError.FAILED)
        removeRows("asset_activity", "id = ? AND asset_id = ? AND kind = 'comment'", listOf(commentId, assetId), now())
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
                // As the server's POST /assets: an asset created with a type was classified by hand.
                now, classification.formatId?.let { "manual" }, classification.formatId, classification.jacs,
                // The AI flag is worked out below, once the answers and the type's fields are in.
                status.validated.bit(), (status.aiSuggestion ?: asset.aiSuggestion).bit(),
                // Assigned to whoever adds it, as the server's POST /assets.
                user.userId(),
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
        refreshAiSuggestion(asset.id, status.aiSuggestion ?: asset.aiSuggestion)
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

    /**
     * The answers JSON to store after an edit, as the server keeps them: every answer the form didn't change stays exactly
     * as stored (lists, numbers and true/false included, so a media field's ids stay a list), a changed one keeps the type
     * its old value had, and a cleared one goes. [oldExtra] and [newExtra] are the answers as text before and after.
     */
    private fun mergeAnswers(row: DbRow?, oldExtra: Map<String, String>, newExtra: Map<String, String>): JsonObject {
        val stored = DbJson.obj(row?.answersJson())
        val merged = stored.toMutableMap()
        (oldExtra.keys + newExtra.keys).filter { oldExtra[it].orEmpty() != newExtra[it].orEmpty() }.forEach { key ->
            val text = newExtra[key]
            if (text.isNullOrBlank()) merged.remove(key) else merged[key] = typedLike(stored[key], text)
        }
        return JsonObject(merged)
    }

    /** [text] as the JSON type [old] had: a number, true/false or a list stays one; anything else (or nothing before) is text. */
    private fun typedLike(old: JsonElement?, text: String): JsonElement = when {
        old is JsonArray -> JsonArray(text.split(",").map { it.trim() }.filter { it.isNotEmpty() }.map(::JsonPrimitive))
        old is JsonPrimitive && !old.isString && old.booleanOrNull != null -> text.toBooleanStrictOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(text)
        old is JsonPrimitive && !old.isString -> (text.toLongOrNull() ?: text.toDoubleOrNull())?.let(::JsonPrimitive) ?: JsonPrimitive(text)
        else -> JsonPrimitive(text)
    }

    /**
     * Writes the answers [values]. A value set here drops how sure a reader was of the old one and where it came from,
     * unless [pages] says where it came from (an applied AI suggestion), as the server's write_values does.
     */
    private fun AdcDatabase.writeValues(
        assetId: String,
        row: DbRow?,
        values: JsonObject,
        changedKeys: List<String?>,
        pages: Map<String, String> = emptyMap(),
    ) {
        val edited = changedKeys.filterNotNull().toSet()
        val confidence = DbJson.doubleMap(row?.string("confidence")).filterKeys { it !in edited }
        val sources = DbJson.stringMap(row?.string("sources")).filterKeys { it !in edited } + pages
        val json = values.toString()
        if (row == null) {
            execute(withAnswers(ASSET_VALUES_INSERT), listOf(assetId, DbJson.writeDoubles(confidence), DbJson.write(sources), json), setOf("asset_values"))
        } else {
            execute(withAnswers(ASSET_VALUES_UPDATE), listOf(json, DbJson.writeDoubles(confidence), DbJson.write(sources), assetId), setOf("asset_values"))
        }
    }

    /**
     * Drops media [mediaId]'s file, its only copy, by setting its `multimedia` content to NULL; the engine's trigger
     * queues that for the sync, which sends it to the server.
     */
    private fun AdcDatabase.clearFile(mediaId: String) {
        if (!hasTable("multimedia")) return
        execute(MULTIMEDIA_CLEAR, listOf(mediaId), setOf("multimedia"))
    }

    /**
     * Takes [mediaId] out of asset [assetId]'s answer to the media field [fieldId] (a list of media ids), as the server's
     * media DELETE does: an answer left empty goes, and so do the answer's confidence and source.
     */
    private fun AdcDatabase.dropFromAnswer(assetId: String, fieldId: String, mediaId: String, now: String) {
        val key = queryOne(FIELD_KEY, listOf(fieldId))?.string("key") ?: return
        val valuesRow = queryOne(ASSET_VALUES_ROW, listOf(assetId)) ?: return
        val answers = DbJson.obj(valuesRow.answersJson())
        val current = answers[key] as? JsonArray ?: return
        val left = current.filterNot { (it as? JsonPrimitive)?.contentOrNull == mediaId }
        if (left.size == current.size) return
        val updated = JsonObject(if (left.isEmpty()) answers - key else answers + (key to JsonArray(left)))
        val confidence = DbJson.doubleMap(valuesRow.string("confidence")) - key
        val sources = DbJson.stringMap(valuesRow.string("sources")) - key
        execute(withAnswers(ASSET_VALUES_UPDATE), listOf(updated.toString(), DbJson.writeDoubles(confidence), DbJson.write(sources), assetId), setOf("asset_values"))
        execute(ASSET_TOUCH_VALUES, listOf(now, now, assetId), setOf("assets"))
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
        val column = if (hasColumn("asset_activity", "field_changes")) "field_changes" else "changes"
        execute(ACTIVITY_INSERT.replace("%CHANGES%", column), listOf(ids.next(), assetId, action, text, changes, user.userId(), now, now), setOf("asset_activity"))
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
        val old = formatId?.takeIf { it.isNotBlank() }
        val format = match?.id ?: old
        val code = if ("jacs" in values) values["jacs"].orNull() else jacs?.takeIf { it.isNotBlank() }
        // As the server's edit: a new type is "manual" (none: no source); an unchanged one keeps where it came from.
        val classified = when {
            format == old -> source?.takeIf { it.isNotBlank() }
            format != null -> "manual"
            else -> null
        }
        return Classification(format, code, classified)
    }

    /**
     * The answers an edit changed, in the order of [fields] (form order), each with its old and new value as stored: the
     * core columns as text, the rest as their JSON. Photo, video and voice memo fields are left out (their answer is a
     * list of media ids), as the server's changes_since.
     */
    private fun answerChanges(fields: List<DbRow>, row: DbRow, newColumns: Map<String, String>, before: JsonObject, after: JsonObject): List<Change> =
        fields.filter { it.string("type") !in MEDIA_FIELD_TYPES }.mapNotNull { f ->
            val key = f.string("key") ?: return@mapNotNull null
            val old = if (key in COLUMN_KEYS) text(row.string(key)) else before[key].orNull()
            val new = if (key in COLUMN_KEYS) text(newColumns[key]) else after[key].orNull()
            if (old == new) null else Change(f.string("name") ?: key, f.string("id"), old, new)
        }

    /** A place's change as history lines: Building, Floor and Location, by name, for the levels that changed. */
    private fun AdcDatabase.placeChanges(old: PlaceColumns, new: PlaceColumns): List<Change> = listOf(
        Triple("Building", PLACE_NAME_BUILDING, old.building to new.building),
        Triple("Floor", PLACE_NAME_FLOOR, old.floor to new.floor),
        Triple("Location", PLACE_NAME_LOCATION, old.location to new.location),
    ).filter { (_, _, moved) -> moved.first != moved.second }
        .map { (label, sql, moved) -> Change(label, null, text(placeName(sql, moved.first)), text(placeName(sql, moved.second))) }

    private fun text(value: String?): JsonElement? = value?.trim()?.takeIf { it.isNotEmpty() }?.let(::JsonPrimitive)

    private fun JsonElement?.orNull(): JsonElement? = this?.takeUnless { it is JsonNull }

    // ---- helpers

    /** A master format as one line, "HVAC › Chillers › Air-cooled" (a retired one too), as the server's label_of. */
    private fun formatLabel(id: String?): String? = id?.let { formats.byId()[it] }
        ?.let { f -> listOfNotNull(f.systemType, f.assetType, f.subtype).filter { it.isNotBlank() }.joinToString(" › ") }
        ?.takeIf { it.isNotEmpty() }

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
        // Signing off happens in a validation run, which writes its own line (the use case refuses this move).
        Status.VALIDATED -> null
        Status.DRAFT, Status.INPUT, Status.AI -> if (wasDrafted) null else "drafted" to "Asset moved back to draft"
    }

    /** One change in a history line: [from] and [to] as the server stores them (text, a number, a list...), null for none. */
    private class Change(val field: String, val fieldId: String?, val from: JsonElement?, val to: JsonElement?)

    /** `[{field, fieldId, from, to}]`, the server's history format. */
    private fun changesJson(changes: List<Change>): String? = if (changes.isEmpty()) null else JsonArray(
        changes.map {
            buildJsonObject {
                put("field", it.field)
                put("fieldId", it.fieldId?.let(::JsonPrimitive) ?: JsonNull)
                put("from", it.from ?: JsonNull)
                put("to", it.to ?: JsonNull)
            }
        },
    ).toString()

    private companion object {
        const val DEFAULT_IMAGE_COLUMN = "object_path"

        /** Fields whose answer is a list of media ids: left out of history, as on the server. */
        val MEDIA_FIELD_TYPES = setOf("image", "video", "voice_memo")

        /** What the AI read off each photo, one row per media (`media_id`); removed with its media. */
        const val MEDIA_READINGS = "media_readings"

        /** The standard answers kept in columns of `assets`. */
        val COLUMN_KEYS = listOf("name", "manufacturer", "model", "serial_number")
    }
}
