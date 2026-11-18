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
