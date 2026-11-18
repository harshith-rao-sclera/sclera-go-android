package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.local.TypeFieldsStore
import com.sclera.sclerago.data.offline.AssetFileStore
import com.sclera.sclerago.data.offline.MediaBlobs
import com.sclera.sclerago.data.remote.adc.AdcClassifyApi
import com.sclera.sclerago.data.remote.adc.ClassifyPhoto
import com.sclera.sclerago.data.remote.adc.SuggestDetailsDto
import com.sclera.sclerago.data.remote.adc.SuggestDto
import com.sclera.sclerago.data.remote.adc.TypeFieldDto
import com.sclera.sclerago.di.IoDispatcher
import com.sclera.sclerago.domain.model.ClassificationResult
import com.sclera.sclerago.domain.model.ClassifyDetails
import com.sclera.sclerago.domain.model.JacsSuggestion
import com.sclera.sclerago.domain.model.MAX_CLASSIFY_PHOTOS
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.MAX_AI_PHOTO_BYTES
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.PhotoReading
import com.sclera.sclerago.domain.model.SuggestionSource
import com.sclera.sclerago.domain.model.TypeField
import com.sclera.sclerago.domain.model.TypeSuggestion
import com.sclera.sclerago.domain.repository.ClassificationRepository
import com.sclera.sclerago.domain.repository.PropertyDownloadRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Classification at the server for the current property (`/classification/suggest`, `/master-formats/…/fields`), with
 * each type's fields kept on the device ([TypeFieldsStore]) for picking a type and saving an asset offline.
 */
class ClassificationRepositoryImpl @Inject constructor(
    private val api: AdcClassifyApi,
    private val files: AssetFileStore,
    private val blobs: MediaBlobs,
    private val store: TypeFieldsStore,
    private val downloads: PropertyDownloadRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ClassificationRepository {
    private fun property(): String? = downloads.downloads.value.currentId

    override suspend fun suggest(details: ClassifyDetails, photos: List<Media>, masterFormatId: String?, assetId: String?): ClassificationResult {
        val property = property() ?: throw OnlineException(OnlineError.NotFound)
        val answer = try {
            // An asset on the server: it reads its own photos; only those not uploaded yet go with it (sent ones come
            // first and aren't checked against its own, so an uploaded one would be read twice).
            val fresh = if (assetId != null) photos.filter { blobs.waitingUpload(MEDIA_TABLE, it.id) } else photos
            api.suggest(property, details.toDto(), read(fresh), masterFormatId, assetId)
        } catch (e: OnlineException) {
            // The asset hasn't reached the server yet: ask as for one still on the phone, with every photo here.
            if (assetId == null || e.error != OnlineError.NotFound) throw e
            val sent = read(photos)
            if (masterFormatId == null && sent.isEmpty() && details.isEmpty) throw OnlineException(OnlineError.Invalid)
            api.suggest(property, details.toDto(), sent, masterFormatId, assetId = null)
        }
        withContext(io) { answer.suggestions.forEach { store.put(property, it.masterFormatId, it.fields.map { f -> f.toField() }) } }
        return answer.toResult()
    }

    /** The photos on this device that can be sent: the server reads at most three, up to 10 MB each. */
    private suspend fun read(photos: List<Media>): List<ClassifyPhoto> = withContext(io) {
        photos.asSequence().mapNotNull { files.photo(it, MAX_AI_PHOTO_BYTES) }.take(MAX_CLASSIFY_PHOTOS)
            .map { ClassifyPhoto(it.filename, it.contentType, it.bytes) }.toList()
    }

    override suspend fun typeFields(masterFormatId: String): List<TypeField> {
        val property = property() ?: return emptyList()
        withContext(io) { store.get(property, masterFormatId) }?.let { return it }
        return try {
            val fields = api.typeFields(property, masterFormatId).fields.map { it.toField() }
            withContext(io) { store.put(property, masterFormatId, fields) }
            fields
        } catch (_: OnlineException) {
            // Offline, or a type the server no longer has: no extra fields (the server adds them when the asset syncs).
            emptyList()
        }
    }

    override suspend fun refresh() {
        val property = property() ?: return
        try {
            val all = api.allTypeFields(property).associate { it.masterFormatId to it.fields.map { f -> f.toField() } }
            withContext(io) { store.putAll(property, all) }
        } catch (_: OnlineException) {
            // Kept as before: the next refresh with a connection brings them.
        }
    }

    /** What is typed, as the server takes it: each core detail at most [MAX_DETAIL] characters. */
    private fun ClassifyDetails.toDto() = SuggestDetailsDto(
        name = name.orNull()?.take(MAX_DETAIL), manufacturer = manufacturer.orNull()?.take(MAX_DETAIL),
        model = model.orNull()?.take(MAX_DETAIL), serialNumber = serialNumber.orNull()?.take(MAX_DETAIL),
        other = other.mapValues { it.value.trim() }.filter { it.key.isNotBlank() && it.value.isNotEmpty() }.takeIf { it.isNotEmpty() },
    )

    private fun SuggestDto.toResult() = ClassificationResult(
        suggestions = suggestions.map { s ->
            TypeSuggestion(
                masterFormatId = s.masterFormatId, systemType = s.systemType, assetType = s.assetType.orNull(), subtype = s.subtype.orNull(),
                confidence = s.confidence.coerceIn(0.0, 1.0), reason = s.reason, source = sourceOf(s.source), fields = s.fields.map { it.toField() },
            )
        },
        jacsCodes = jacsCodes.map { JacsSuggestion(it.code, it.title.orNull(), it.confidence.coerceIn(0.0, 1.0), it.reason.orNull()) },
        message = message.orNull(),
        read = read?.let { PhotoReading(it.manufacturer.orNull(), it.model.orNull(), it.kind.orNull()) }
            ?.takeIf { it.manufacturer != null || it.model != null || it.kind != null },
    )

    private fun sourceOf(text: String) = when (text) {
        "same_model" -> SuggestionSource.SAME_MODEL
        "chosen" -> SuggestionSource.CHOSEN
        else -> SuggestionSource.AI
    }

    private fun TypeFieldDto.toField() = TypeField(
        name = name.trim(), type = type, options = options.orEmpty().map { it.label }.filter { it.isNotBlank() },
        helpText = helpText.orNull(), position = position,
    )

    private fun String?.orNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private companion object {
        const val MEDIA_TABLE = "asset_media"
        /** The server's limit for name, manufacturer, model and serial number (`ClassifyDetails`). */
        const val MAX_DETAIL = 300
    }
}
