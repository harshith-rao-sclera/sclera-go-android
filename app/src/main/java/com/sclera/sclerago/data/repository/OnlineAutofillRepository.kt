package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.offline.FieldsOfflineSource
import com.sclera.sclerago.data.remote.adc.AdcJobsApi
import com.sclera.sclerago.data.remote.adc.LookupJobDto
import com.sclera.sclerago.data.remote.adc.pollUntil
import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.repository.AutofillRepository
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject

/**
 * Suggestions from the server's spec look-up: it searches the web for the asset's make and model, in the
 * background, and this polls the job until it has finished. Only the make, model and serial number typed in
 * are sent; nothing is saved by the lookup. The server names fields by id, so the form's field keys are turned
 * into ids with the property's `asset_fields` rows going out and back again for the suggestions.
 */
class OnlineAutofillRepository @Inject constructor(
    private val api: AdcJobsApi,
    private val fields: FieldsOfflineSource,
) : AutofillRepository {

    override suspend fun suggest(asset: Asset, values: Map<String, String>): Map<String, String> {
        val idByKey = fields.idsByKey(asset.id)
        val typed = JsonObject(
            SENT_KEYS.mapNotNull { key ->
                val id = idByKey[key] ?: return@mapNotNull null
                values[key]?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_VALUE)?.let { id to JsonPrimitive(it) }
            }.toMap(),
        )
        val started = api.startLookup(asset.propertyId, asset.id, typed)
        val job = if (started.isFinished()) started
        else pollUntil(LIMIT_MS, { it.isFinished() }) { api.lookup(asset.propertyId, started.id) }
        if (job.status != COMPLETED) throw OnlineException(OnlineError.Server)
        try {
            api.lookupSeen(asset.propertyId, job.id)
        } catch (_: OnlineException) {
            // Only tidies the asset's "ready" mark on the server; the suggestions are already here.
        }
        return job.suggestions(values, idByKey.entries.associate { (key, id) -> id to key })
    }

    private fun LookupJobDto.isFinished() = status == COMPLETED || status == FAILED

    /** The suggested values the form can take, by form key: short text for fields that are still empty (or "Generic"). */
    private fun LookupJobDto.suggestions(current: Map<String, String>, keyById: Map<String, String>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for ((id, element) in values) {
            if (out.size >= MAX_FIELDS) break
            val key = keyById[id] ?: continue // a field this property's data does not have
            val text = (element as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.trim()
            val empty = current[key].isNullOrBlank() || current[key] == "Generic"
            if (empty && !text.isNullOrEmpty() && text.length <= MAX_VALUE) out[key] = text
        }
        return out
    }

    private companion object {
        val SENT_KEYS = listOf("make", "model", "serial")
        const val COMPLETED = "completed"
        const val FAILED = "failed"
        const val LIMIT_MS = 180_000L
        const val MAX_VALUE = 500
        const val MAX_FIELDS = 40
    }
}
