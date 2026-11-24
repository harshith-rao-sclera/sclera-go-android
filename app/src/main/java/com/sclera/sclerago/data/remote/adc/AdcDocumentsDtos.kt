package com.sclera.sclerago.data.remote.adc

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// The collect-from-files calls' JSON (the server's schemas/documents.py). Only what the app reads is declared.

/** An uploaded file: [status] is processing, ready or failed; [statusDetail] says why it failed. */
@Serializable
data class DocumentDto(
    val id: String,
    val filename: String = "",
    val kind: String = "",
    val status: String = "",
    val statusDetail: String? = null,
    val draftCount: Int = 0,
)

/** Something the file said that no field holds. */
@Serializable
data class ExtraFieldDto(val name: String = "", val value: String = "")

/** An asset read from a file: [values] and [confidence] are keyed by the server's field id. */
@Serializable
data class DraftDto(
    val id: String,
    val documentId: String? = null,
    val position: Int = 0,
    val values: Map<String, JsonElement> = emptyMap(),
    val confidence: Map<String, Double> = emptyMap(),
    val buildingName: String? = null,
    val floorName: String? = null,
    val locationName: String? = null,
    val systemTypeName: String? = null,
    val assetTypeName: String? = null,
    val assetSubtypeName: String? = null,
    val extraFields: List<ExtraFieldDto> = emptyList(),
    val sourceRef: String? = null,
    val notes: String? = null,
    val photos: Int = 0,
    /** The property's type it gets (the type names above are that type's). */
    val masterFormatId: String? = null,
    /** Whether the type the file names is one of the property's: false = added without a type; null = the file names none. */
    val typeMatched: Boolean? = null,
    /** What the file called its type, when it matched one of the property's. */
    val typeNamed: String? = null,
)

@Serializable
data class DraftPageDto(val data: List<DraftDto> = emptyList())

/** What adding drafts did; [assets] are the assets added (only counted here: they reach the device by sync). */
@Serializable
data class AcceptResultDto(
    val assets: List<JsonElement> = emptyList(),
    val placesCreated: Int = 0,
    val unplaced: Int = 0,
    val typesCreated: Int = 0,
    val unclassified: Int = 0,
    val fieldsCreated: Int = 0,
    val leftOut: List<String> = emptyList(),
)

@Serializable
data class DiscardResultDto(val discarded: Int = 0)

// An asset's Documents tab (the server's schemas/assets.py, media.py).

/** The file an asset was read from (`GET /assets/{id}/files`): [kind] is spreadsheet, pdf, image, video, word or text. */
@Serializable
data class AssetFileDto(
    val id: String,
    val name: String = "",
    val kind: String = "",
    val contentType: String? = null,
    val sizeBytes: Long? = null,
    val createdAt: String? = null,
    /** For a photo: a resized JPEG or PNG (base64, at most 1280px) to show inline. */
    val preview: String? = null,
)

/** A web page an asset's values came from; [fileStatus] is pending, saved or link. */
@Serializable
data class WebDocumentDto(
    val id: String,
    val title: String = "",
    val url: String? = null,
    val publisher: String? = null,
    val official: Boolean = false,
    val fieldIds: List<String> = emptyList(),
    val fileStatus: String? = null,
    val hasFile: Boolean = false,
    val filename: String? = null,
    val sizeBytes: Long? = null,
)


/**
 * One of an asset's media (`GET /assets/{id}/media`, media.py's MediaOut): [kind] is image, video, audio or document;
 * [fieldId] is set when it answers a field (not a document added to the asset).
 */
@Serializable
data class MediaDto(
    val id: String,
    val fieldId: String? = null,
    val kind: String = "",
    val filename: String = "",
    val contentType: String? = null,
    val sizeBytes: Long? = null,
    val createdBy: String? = null,
    val createdAt: String? = null,
)
