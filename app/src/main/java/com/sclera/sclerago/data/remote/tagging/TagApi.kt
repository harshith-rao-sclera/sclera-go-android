package com.sclera.sclerago.data.remote.tagging

import com.sclera.sclerago.data.remote.adc.AdcJson
import com.sclera.sclerago.data.remote.adc.OnlineCall
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import android.util.Log
import com.sclera.sclerago.domain.model.OnlineException
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/** The tag calls as typed results. Every call can throw [com.sclera.sclerago.domain.model.OnlineException]; see [OnlineCall]. */
@Singleton
class TagApi @Inject constructor(private val service: TagService, private val call: OnlineCall) {
    suspend fun check(path: String, asset: String, property: String): TagCheckDto? = logged("check", path) {
        call.json({ service.check(path, asset, property) }) { AdcJson.decodeFromString(TagCheckEnvelopeDto.serializer(), it).data }
    }

    suspend fun register(path: String, body: JsonObject) {
        logged("register", path) { call.unit { service.register(path, body.toString().toRequestBody(JSON)) } }
    }

    /** [body] holds updatedBy, deviceId, existingCollectionId, movable, direction and propertyId (see `TaggingRepositoryImpl`). */
    suspend fun write(path: String, body: JsonObject, photo: ByteArray?): TagUpdateEnvelopeDto {
        val json = body.toString().toRequestBody(JSON)
        return logged("write", path) { send(path, json, photo) }
    }

    private suspend fun send(path: String, json: okhttp3.RequestBody, photo: ByteArray?): TagUpdateEnvelopeDto =
        if (photo == null) {
            call.json({ service.write(path, json) }, ::updateOf)
        } else {
            val parts = listOf(
                MultipartBody.Part.create(Headers.headersOf("Content-Disposition", "form-data; name=\"body\""), json),
                MultipartBody.Part.createFormData("image", "qr.jpg", photo.toRequestBody(JPEG)),
            )
            call.json({ service.writeWithPhoto(path, parts, WRITE_TIMEOUT_SECONDS) }, ::updateOf)
        }

    /** Debug builds only (R8 removes Log.d): which call failed and why, by the folder and action name; never the tag's id. */
    private suspend fun <T> logged(what: String, path: String, block: suspend () -> T): T = try {
        block()
    } catch (e: OnlineException) {
        Log.d(LOG, "$what ${path.substringBefore('/')} failed: ${e.error}")
        throw e
    }

    /**
     * Read leniently: the status and counts differ by outcome, and a body that doesn't fit must not hide the status (826: someone else
     * got the tag).
     */
    internal fun updateOf(text: String): TagUpdateEnvelopeDto {
        val root = runCatching { AdcJson.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return TagUpdateEnvelopeDto()
        val status = (root["status"] as? JsonPrimitive)?.intOrNull
        val data = (root["data"] as? JsonObject)?.let { runCatching { AdcJson.decodeFromJsonElement(TagUpdateDto.serializer(), it) }.getOrNull() }
        return TagUpdateEnvelopeDto(status, data)
    }

    private companion object {
        const val LOG = "Tagging"
        val JSON = "application/json".toMediaType()
        val JPEG = "image/jpeg".toMediaType()
        const val WRITE_TIMEOUT_SECONDS = "60"
    }
}
