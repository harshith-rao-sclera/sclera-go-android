package com.sclera.sclerago.data.repository

import android.content.Context
import android.net.Uri
import com.sclera.sclerago.domain.model.Source
import com.sclera.sclerago.domain.model.sourceToDb
import com.sclera.sclerago.data.offline.FieldsOfflineSource
import com.sclera.sclerago.data.remote.adc.AcceptResultDto
import com.sclera.sclerago.data.remote.adc.AdcDocumentsApi
import com.sclera.sclerago.data.remote.adc.DocumentDto
import com.sclera.sclerago.data.remote.adc.DraftDto
import com.sclera.sclerago.domain.model.AssetDraft
import com.sclera.sclerago.domain.model.DocumentStatus
import com.sclera.sclerago.domain.model.DraftExtra
import com.sclera.sclerago.domain.model.DraftField
import com.sclera.sclerago.domain.model.DraftsAccepted
import com.sclera.sclerago.domain.model.ImportedFile
import com.sclera.sclerago.domain.model.NAME_FIELD_KEY
import com.sclera.sclerago.domain.model.UploadedDocument
import com.sclera.sclerago.domain.repository.FileImportRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.FileNotFoundException
import javax.inject.Inject

/**
 * The server's collect-from-files flow. A picked file is streamed from the file manager's content URI straight into
 * the upload (nothing is copied to the device). A draft's name is its value for the property's Name field, whose id
 * comes from the downloaded property's fields.
 */
class FileImportRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: AdcDocumentsApi,
    private val fields: FieldsOfflineSource,
) : FileImportRepository {

    override suspend fun upload(property: String, file: ImportedFile, onProgress: (Int) -> Unit, source: Source): UploadedDocument {
        val uri = Uri.parse(file.uri)
        val open = { context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException("no stream") }
        // A file is the server's default ("document"); only another source is said.
        val from = sourceToDb(source).takeIf { source != Source.FILE }
        return api.upload(property, file.name, file.mime, file.size, open, onProgress, from).toDomain()
    }

    override suspend fun document(property: String, documentId: String): UploadedDocument =
        api.document(property, documentId).toDomain()

    override suspend fun drafts(property: String, documentId: String): List<AssetDraft> {
        val nameField = nameFieldId()
        return api.pendingDrafts(property, documentId).map { it.toDomain(nameField) }
    }

    override suspend fun fields(): List<DraftField> = fields.activeStandard()

    override suspend fun updateDraft(property: String, draftId: String, values: Map<String, String?>): AssetDraft =
        api.updateDraft(property, draftId, values).toDomain(nameFieldId())

    private suspend fun nameFieldId(): String? = fields.activeStandard().firstOrNull { it.key == NAME_FIELD_KEY }?.id

    override suspend fun accept(property: String, ids: List<String>, createFields: Boolean): DraftsAccepted =
        api.accept(property, ids, createFields).toDomain()

    override suspend fun discard(property: String, ids: List<String>): Int = api.discard(property, ids).discarded

    private fun DocumentDto.toDomain() = UploadedDocument(
        id = id,
        filename = filename,
        kind = kind,
        status = when (status) {
            "ready" -> DocumentStatus.READY
            "failed" -> DocumentStatus.FAILED
            else -> DocumentStatus.PROCESSING
        },
        statusDetail = statusDetail?.trim()?.takeIf { it.isNotEmpty() },
        draftCount = draftCount,
    )

    private fun DraftDto.toDomain(nameField: String?): AssetDraft {
        val texts = values.mapNotNull { (field, value) -> text(value)?.let { field to it } }.toMap()
        return AssetDraft(
            id = id,
            documentId = documentId,
            position = position,
            name = nameField?.let { texts[it] }.orEmpty(),
            values = texts,
            confidence = confidence,
            buildingName = buildingName,
            floorName = floorName,
            locationName = locationName,
            systemTypeName = systemTypeName,
            assetTypeName = assetTypeName,
            assetSubtypeName = assetSubtypeName,
            extraFields = extraFields.map { DraftExtra(it.name, it.value) },
            sourceRef = sourceRef,
            notes = notes,
            photos = photos,
            typeMatched = typeMatched,
            typeNamed = typeNamed?.takeIf { it.isNotBlank() },
        )
    }

    private fun AcceptResultDto.toDomain() = DraftsAccepted(
        added = assets.size,
        placesCreated = placesCreated,
        unplaced = unplaced,
        typesCreated = typesCreated,
        unclassified = unclassified,
        fieldsCreated = fieldsCreated,
        leftOut = leftOut,
    )

    /** A value as text: lists joined with ", "; null when empty. */
    private fun text(value: JsonElement): String? = when (value) {
        is JsonNull -> null
        is JsonPrimitive -> value.contentOrNull
        is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString(", ")
        is JsonObject -> null
    }?.trim()?.takeIf { it.isNotEmpty() }

}
