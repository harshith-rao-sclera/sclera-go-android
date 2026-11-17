package com.sclera.sclerago.data.remote.adc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The classification calls' JSON. Only what the app reads is declared.

/** One choice of a dropdown field. */
@Serializable
data class FieldOptionDto(val label: String = "")

/** A field a type brings; [type] is the `asset_fields.type` text. */
@Serializable
data class TypeFieldDto(
    val name: String,
    val type: String,
    val options: List<FieldOptionDto>? = null,
    val helpText: String? = null,
    val position: Int = 0,
)

@Serializable
data class SuggestedTypeDto(
    val masterFormatId: String,
    val systemType: String = "",
    val assetType: String? = null,
    val subtype: String? = null,
    val confidence: Double = 0.0,
    val reason: String = "",
    /** same_model, ai or chosen. */
    @SerialName("from") val source: String = "",
    val fields: List<TypeFieldDto> = emptyList(),
)

@Serializable
data class SuggestedCodeDto(val code: String, val title: String? = null, val confidence: Double = 0.0, val reason: String? = null)

@Serializable
data class PhotoReadDto(val manufacturer: String? = null, val model: String? = null, val kind: String? = null)

/** `POST /classification/suggest`. */
@Serializable
data class SuggestDto(
    val suggestions: List<SuggestedTypeDto> = emptyList(),
    val jacsCodes: List<SuggestedCodeDto> = emptyList(),
    val read: PhotoReadDto? = null,
    val message: String? = null,
)

/** The `details` part of a suggestion request; empty values are left out. */
@Serializable
data class SuggestDetailsDto(
    val name: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val serialNumber: String? = null,
    val other: Map<String, String>? = null,
)

/** One type's fields (`GET /master-formats/{id}/fields`, and each item of the all-types list). */
@Serializable
data class MasterFormatFieldsDto(val masterFormatId: String, val fields: List<TypeFieldDto> = emptyList())

@Serializable
data class MasterFormatFieldsListDto(val data: List<MasterFormatFieldsDto> = emptyList())

/** A photo sent with a suggestion request. */
class ClassifyPhoto(val filename: String, val contentType: String, val bytes: ByteArray)
