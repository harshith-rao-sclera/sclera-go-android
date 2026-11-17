package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.local.TypeFieldsStore
import com.sclera.sclerago.data.offline.AssetFileStore
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
    private val store: TypeFieldsStore,
    private val downloads: PropertyDownloadRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ClassificationRepository {
    private fun property(): String? = downloads.downloads.value.currentId

    override suspend fun suggest(details: ClassifyDetails, photos: List<Media>, masterFormatId: String?): ClassificationResult {
        val property = property() ?: throw OnlineException(OnlineError.NotFound)
        // Only photos on this device can be sent; the server reads at most three, up to 10 MB each.
        val sent = withContext(io) {
            photos.asSequence().mapNotNull { files.photo(it, MAX_PHOTO_BYTES) }.take(MAX_CLASSIFY_PHOTOS)
                .map { ClassifyPhoto(it.filename, it.contentType, it.bytes) }.toList()
        }
        val answer = api.suggest(property, details.toDto(), sent, masterFormatId)
        withContext(io) { answer.suggestions.forEach { store.put(property, it.masterFormatId, it.fields.map { f -> f.toField() }) } }
        return answer.toResult()
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

    private fun ClassifyDetails.toDto() = SuggestDetailsDto(
        name = name.orNull(), manufacturer = manufacturer.orNull(), model = model.orNull(), serialNumber = serialNumber.orNull(),
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
        read = read?.let { PhotoReading(it.manufacturer.orNull(), it.model.orNull(), it.kind.orNull()) }
            ?.takeIf { it.manufacturer != null || it.model != null || it.kind != null },
        message = message.orNull(),
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
        const val MAX_PHOTO_BYTES = 10L * 1024 * 1024
    }
}
