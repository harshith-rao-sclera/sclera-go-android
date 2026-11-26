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
    /** The type's own field row; a look-up's values for the type are keyed by it. */
    val id: String = "",
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

/** `POST /classification/suggest`. */
@Serializable
data class SuggestDto(
    val suggestions: List<SuggestedTypeDto> = emptyList(),
    val jacsCodes: List<SuggestedCodeDto> = emptyList(),
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


/**
 * The type a spec look-up thinks the asset is, when it isn't the one it has (`suggestedType`): values for its fields
 * are keyed by [fields] ids.
 */
@Serializable
data class TypeSuggestionDto(
    val masterFormatId: String,
    /** The type as one line, “HVAC › Chillers › Air-cooled”. */
    val label: String? = null,
    val systemType: String = "",
    val assetType: String? = null,
    val subtype: String? = null,
    val confidence: Double = 0.0,
    val reason: String = "",
    /** same_model or ai. */
    @SerialName("from") val source: String = "",
    val jacsCode: SuggestedCodeDto? = null,
    val fields: List<TypeFieldDto> = emptyList(),
)

/** A JACS catalog code (`GET /jacs-codes`). */
@Serializable
data class JacsCodeDto(val code: String = "", val title: String? = null)

/** A master format as an asset shows it. */
@Serializable
data class MasterFormatBriefDto(val id: String, val systemType: String = "", val assetType: String? = null, val subtype: String? = null)

/** `GET /properties/{id}/jacs-codes/{code}/master-format`. */
@Serializable
data class JacsMasterFormatDto(val code: String = "", val masterFormat: MasterFormatBriefDto? = null)

/** One asset icon (`GET /v1/asset-icons`). */
@Serializable
data class AssetIconDto(val name: String = "", val label: String = "")

/** A sub type of the classification, with its icon. */
@Serializable
data class ClassSubtypeDto(val masterFormatId: String, val name: String = "", val icon: String = "generic")

/** An asset type of the classification, with its icon and sub types. */
@Serializable
data class ClassAssetTypeDto(
    val name: String = "",
    val icon: String = "generic",
    val masterFormatId: String? = null,
    val subtypes: List<ClassSubtypeDto> = emptyList(),
)

/** A system type of the classification, with its icon and asset types. */
@Serializable
data class ClassSystemTypeDto(
    val name: String = "",
    val icon: String = "generic",
    val masterFormatId: String? = null,
    val assetTypes: List<ClassAssetTypeDto> = emptyList(),
)

/** `GET /properties/{id}/classification`: the property's types grouped by name. */
@Serializable
data class ClassificationDto(val systemTypes: List<ClassSystemTypeDto> = emptyList())

/** A photo sent with a suggestion request. */
class ClassifyPhoto(val filename: String, val contentType: String, val bytes: ByteArray)

/** One type's fields (`GET /master-formats/{id}/fields`). */
@Serializable
data class MasterFormatFieldsDto(val masterFormatId: String, val fields: List<TypeFieldDto> = emptyList())
