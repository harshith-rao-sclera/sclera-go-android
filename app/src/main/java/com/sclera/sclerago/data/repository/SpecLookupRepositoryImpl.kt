package com.sclera.sclerago.data.repository

import com.sclera.sclerago.domain.model.ModelManuals
import com.sclera.sclerago.domain.model.AssetDocument
import com.sclera.sclerago.domain.model.DocumentSource
import com.sclera.sclerago.domain.model.ManualKind
import com.sclera.sclerago.domain.model.DocumentFileStatus
import com.sclera.sclerago.data.mapper.parseDbTime
import com.sclera.sclerago.data.offline.AssetFileStore
import com.sclera.sclerago.data.offline.FieldsOfflineSource
import com.sclera.sclerago.data.remote.adc.AdcClassifyApi
import com.sclera.sclerago.data.remote.adc.AdcJobsApi
import com.sclera.sclerago.data.remote.adc.ClassifyPhoto
import com.sclera.sclerago.data.remote.adc.LookupJobDto
import com.sclera.sclerago.data.remote.adc.SpecSuggestionsDto
import com.sclera.sclerago.data.remote.adc.TypeSuggestionDto
import com.sclera.sclerago.di.IoDispatcher
import com.sclera.sclerago.domain.model.LookupStatus
import com.sclera.sclerago.domain.model.LookupType
import com.sclera.sclerago.domain.model.MAX_AI_PHOTO_BYTES
import com.sclera.sclerago.domain.model.MAX_CLASSIFY_PHOTOS
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.PhotoNote
import com.sclera.sclerago.domain.model.SpecLookup
import com.sclera.sclerago.domain.model.typeFieldKey
import com.sclera.sclerago.domain.model.withoutScanIdentifiers
import com.sclera.sclerago.domain.repository.PropertyDownloadRepository
import com.sclera.sclerago.domain.repository.SpecLookupRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The server's spec look-ups for the current property: the background jobs of an asset it has (`/assets/{id}/lookup-jobs`,
 * `/lookup-jobs/{id}`) and the phone's look-up from the photos here (`/lookup`). The server names fields by id; they are
 * turned into the app's field keys (and back) with the asset's `asset_fields` rows, and a suggested type's fields by name.
 */
class SpecLookupRepositoryImpl @Inject constructor(
    private val api: AdcJobsApi,
    private val classify: AdcClassifyApi,
    private val fields: FieldsOfflineSource,
    private val files: AssetFileStore,
    private val downloads: PropertyDownloadRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : SpecLookupRepository {
    private fun property(): String = downloads.downloads.value.currentId ?: throw OnlineException(OnlineError.NotFound)

    override suspend fun latest(assetId: String): SpecLookup? = api.latestLookup(property(), assetId)?.toLookup(keys(assetId))

    override suspend fun start(assetId: String, typed: Map<String, String>): SpecLookup {
        val keys = keys(assetId)
        // A scan's identifying lines (IP, MAC, BSSID, names) never go to the AI.
        val body = JsonObject(typed.mapNotNull { (key, v) -> keys.idByKey[key]?.let { it to JsonPrimitive(withoutScanIdentifiers(v)) } }.toMap())
        return api.startLookup(property(), assetId, body).toLookup(keys)
    }

    override suspend fun get(assetId: String, lookupId: String): SpecLookup = api.lookup(property(), lookupId).toLookup(keys(assetId))

    override suspend fun seen(lookupId: String) {
        api.lookupSeen(property(), lookupId)
    }

    override suspend fun manuals(assetId: String): ModelManuals = api.assetManuals(property(), assetId).let { dto ->
        ModelManuals(
            manuals = dto.manuals.map { m ->
                AssetDocument(
                    id = m.id, source = DocumentSource.MANUAL, title = m.title, url = m.url, publisher = m.publisher,
                    official = true, manualKind = ManualKind.of(m.kind),
                    model = listOf(m.manufacturer, m.model).filter { it.isNotBlank() }.joinToString(" ").ifEmpty { null },
                    fileStatus = if (m.hasFile) DocumentFileStatus.SAVED else DocumentFileStatus.LINK, hasFile = m.hasFile,
                )
            },
            searching = dto.job?.status in MANUAL_SEARCHING,
        )
    }

    override suspend fun lookUpOnPhone(assetId: String, photos: List<Media>, typed: Map<String, String>, masterFormatId: String?): SpecLookup {
        val property = property()
        // The asset's type's fields answer by the type's own field ids: named here, so their values go in its fields.
        val typeNames = masterFormatId?.let { mf -> classify.typeFields(property, mf).fields.associate { it.id to it.name.trim() } }.orEmpty()
        val keys = keys(assetId, typeNames)
        // Only photos on this device can be sent; the server reads at most three, up to 10 MB each.
        val sent = withContext(io) {
            photos.asSequence().mapNotNull { files.photo(it, MAX_AI_PHOTO_BYTES) }.take(MAX_CLASSIFY_PHOTOS)
                .map { ClassifyPhoto(it.filename, it.contentType, it.bytes) }.toList()
        }
        // The server knows only the active standard fields and (with a type) that type's own field rows; any other id
        // (the asset's own fields, which it may not have yet) is refused (422).
        val standard = fields.standard().filter { it.visible }.mapNotNull { f -> keys.idByKey[f.key]?.let { id -> id to f.key } }
        val ofType = keys.typeNames.map { (id, name) -> id to (keys.keyByName[name.lowercase()] ?: typeFieldKey(name)) }
        val values = JsonObject(
            (standard + ofType).mapNotNull { (id, key) ->
                typed[key]?.let(::withoutScanIdentifiers)?.takeIf { it.isNotBlank() }?.let { id to JsonPrimitive(it) }
            }.toMap(),
        )
        return api.lookUpOnPhone(property, sent, values, masterFormatId).toLookup(keys)
    }

    /**
     * The asset's fields: id by form key, and form key by name (to put a type's values in its fields). [typeNames] are
     * the names of its type's own field rows, by id, for a look-up that filled them.
     */
    private class Keys(val idByKey: Map<String, String>, val keyByName: Map<String, String>, val typeNames: Map<String, String>) {
        val keyById: Map<String, String> = idByKey.entries.associate { (k, id) -> id to k }
    }

    private suspend fun keys(assetId: String, typeNames: Map<String, String> = emptyMap()) = Keys(
        fields.idsByKey(assetId),
        fields.effective(assetId).associate { it.label.trim().lowercase() to it.key },
        typeNames,
    )

    private fun LookupJobDto.toLookup(keys: Keys) = lookupOf(keys, values, confidence, sources, suggestedType).copy(
        id = id,
        status = LookupStatus.of(status),
        statusDetail = statusDetail?.takeIf { it.isNotBlank() },
        product = product?.takeIf { it.isNotBlank() },
        message = message?.takeIf { it.isNotBlank() },
        typed = typedValues.mapNotNull { (id, v) -> keys.keyById[id]?.let { k -> text(v)?.let { k to it } } }.toMap(),
        seen = seenAt != null,
        createdAt = parseDbTime(createdAt),
        manualsSearch = manualJobId != null,
    )

    /** The phone's look-up as a finished [SpecLookup] (not a job: nothing to mark as seen at the server). */
    private fun SpecSuggestionsDto.toLookup(keys: Keys) = lookupOf(keys, values, confidence, sources, suggestedType).copy(
        product = product?.takeIf { it.isNotBlank() },
        message = message?.takeIf { it.isNotBlank() },
        createdAt = System.currentTimeMillis(),
        differs = differs.mapNotNull { keys.keyFor(it, suggestedType) }.toSet(),
        fromPhoto = fromPhoto.mapNotNull { keys.keyFor(it, suggestedType) }.toSet(),
        extra = extra.mapNotNull { e ->
            val name = e["name"]?.trim().orEmpty()
            val value = e["value"]?.trim().orEmpty()
            if (name.isEmpty() || value.isEmpty()) null else PhotoNote(name, value)
        },
        manualsSearch = manualJobId != null,
    )

    /**
     * The suggestions keyed by the app's field keys: the asset's own fields' values, and the suggested type's apart
     * (they go into the form only with the type). Fields this property's data doesn't have are left out.
     */
    private fun lookupOf(
        keys: Keys,
        values: Map<String, JsonElement>,
        confidence: Map<String, Double>,
        sources: Map<String, String>,
        type: TypeSuggestionDto?,
    ): SpecLookup {
        val typeIds = type?.fields?.map { it.id }.orEmpty().toSet()
        fun <T> Map<String, T>.byKey(): Map<String, T> = mapNotNull { (id, v) -> keys.keyFor(id, type)?.let { it to v } }.toMap()
        val texts = values.mapNotNull { (id, v) -> text(v)?.let { id to it } }.toMap()
        return SpecLookup(
            id = "",
            status = LookupStatus.COMPLETED,
            statusDetail = null,
            product = null,
            message = null,
            values = texts.filterKeys { it !in typeIds }.byKey(),
            confidence = confidence.byKey(),
            sources = sources.byKey(),
            typed = emptyMap(),
            seen = true,
            createdAt = null,
            suggestedType = type?.let { t ->
                LookupType(
                    masterFormatId = t.masterFormatId,
                    label = t.label?.trim()?.takeIf { it.isNotEmpty() },
                    systemType = t.systemType,
                    assetType = t.assetType?.takeIf { it.isNotBlank() },
                    subtype = t.subtype?.takeIf { it.isNotBlank() },
                    confidence = t.confidence.coerceIn(0.0, 1.0),
                    reason = t.reason,
                    jacsCode = t.jacsCode?.code?.takeIf { it.isNotBlank() },
                    values = texts.filterKeys { it in typeIds }.byKey(),
                )
            },
        )
    }

    /**
     * The form key of field [id]: one of the asset's, else one of its type's or the suggested type's, by name (as the web
     * fills them): the asset's field of that name, else the type field's key.
     */
    private fun Keys.keyFor(id: String, type: TypeSuggestionDto?): String? = keyById[id]
        ?: (typeNames[id] ?: type?.fields?.firstOrNull { it.id == id }?.name?.trim())
            ?.let { name -> keyByName[name.lowercase()] ?: typeFieldKey(name) }

    /** A suggested value as text: a list joined with commas; nothing for empty. */
    private fun text(e: JsonElement): String? = when (e) {
        is JsonNull -> null
        is JsonPrimitive -> e.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        is JsonArray -> e.mapNotNull(::text).joinToString(",").takeIf { it.isNotEmpty() }
        is JsonObject -> null
    }
}

/** A manual search still under way, as the server names it. */
private val MANUAL_SEARCHING = setOf("queued", "running")
