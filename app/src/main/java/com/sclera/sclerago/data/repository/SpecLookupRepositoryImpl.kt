package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.mapper.parseDbTime
import com.sclera.sclerago.data.offline.FieldsOfflineSource
import com.sclera.sclerago.data.remote.adc.AdcJobsApi
import com.sclera.sclerago.data.remote.adc.LookupJobDto
import com.sclera.sclerago.domain.model.LookupStatus
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.SpecLookup
import com.sclera.sclerago.domain.repository.PropertyDownloadRepository
import com.sclera.sclerago.domain.repository.SpecLookupRepository
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject

/**
 * The server's lookup jobs for the current property (`/assets/{id}/lookup-jobs`, `/lookup-jobs/{id}`). The server names
 * fields by id; they are turned into the app's field keys (and back) with the asset's `asset_fields` rows.
 */
class SpecLookupRepositoryImpl @Inject constructor(
    private val api: AdcJobsApi,
    private val fields: FieldsOfflineSource,
    private val downloads: PropertyDownloadRepository,
) : SpecLookupRepository {
    private fun property(): String = downloads.downloads.value.currentId ?: throw OnlineException(OnlineError.NotFound)

    override suspend fun latest(assetId: String): SpecLookup? =
        api.latestLookup(property(), assetId)?.toLookup(keyById(assetId))

    override suspend fun start(assetId: String, typed: Map<String, String>): SpecLookup {
        val idByKey = fields.idsByKey(assetId)
        val body = JsonObject(typed.mapNotNull { (key, v) -> idByKey[key]?.let { it to JsonPrimitive(v) } }.toMap())
        return api.startLookup(property(), assetId, body).toLookup(idByKey.entries.associate { (k, id) -> id to k })
    }

    override suspend fun get(assetId: String, lookupId: String): SpecLookup = api.lookup(property(), lookupId).toLookup(keyById(assetId))

    override suspend fun seen(lookupId: String) {
        api.lookupSeen(property(), lookupId)
    }

    private suspend fun keyById(assetId: String) = fields.idsByKey(assetId).entries.associate { (k, id) -> id to k }

    /** The job with its maps keyed by the app's field keys (fields this property's data doesn't have are left out). */
    private fun LookupJobDto.toLookup(keyById: Map<String, String>): SpecLookup {
        fun <T> Map<String, T>.byKey(): Map<String, T> = mapNotNull { (id, v) -> keyById[id]?.let { it to v } }.toMap()
        return SpecLookup(
            id = id,
            status = LookupStatus.of(status),
            statusDetail = statusDetail?.takeIf { it.isNotBlank() },
            product = product?.takeIf { it.isNotBlank() },
            message = message?.takeIf { it.isNotBlank() },
            values = values.mapNotNull { (id, v) -> text(v)?.let { id to it } }.toMap().byKey(),
            confidence = confidence.byKey(),
            sources = sources.byKey(),
            typed = typedValues.mapNotNull { (id, v) -> text(v)?.let { id to it } }.toMap().byKey(),
            seen = seenAt != null,
            createdAt = parseDbTime(createdAt),
        )
    }

    /** A suggested value as text: a list joined with commas; nothing for empty. */
    private fun text(e: JsonElement): String? = when (e) {
        is JsonNull -> null
        is JsonPrimitive -> e.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        is JsonArray -> e.mapNotNull(::text).joinToString(",").takeIf { it.isNotEmpty() }
        is JsonObject -> null
    }
}
