package com.sclera.sclerago.domain.model

/**
 * The server's limit on one uploaded file (its `max_upload_mb` setting, 200 by default): `POST /documents` refuses a
 * bigger one, so the app doesn't send it.
 */
const val MAX_DOCUMENT_UPLOAD_MB = 200

/** A value read from a file that the AI is less sure of than this needs checking (the web highlights it). */
const val UNSURE_BELOW = 0.7

/** Why a picked file isn't sent: the server would refuse it with the same reason (`POST /documents` → 422). */
enum class UploadProblem {
    /** Not a spreadsheet, PDF, photo, video, Word or text file (the server's `readers.kind_of` gives no kind). */
    UNREADABLE,
    EMPTY,
    /** Over [MAX_DOCUMENT_UPLOAD_MB]. */
    TOO_LARGE,
}

/** How the server's reading of an uploaded file stands (`documents.status`). */
enum class DocumentStatus { PROCESSING, READY, FAILED }

/** A file uploaded to collect assets from (the server's `DocumentOut`). [statusDetail] says why reading it failed. */
data class UploadedDocument(
    val id: String,
    val filename: String,
    val kind: String,
    val status: DocumentStatus,
    val statusDetail: String?,
    val draftCount: Int,
)

/** A field of the property that values read from a file go in (an active standard field), by the server's id. */
data class DraftField(val id: String, val key: String, val name: String)

/** The key of the Name field (a draft's name, and the first column the web shows). */
const val NAME_FIELD_KEY = "name"

/** Something a file said about an asset that no field holds yet; it can become a field when the asset is added. */
data class DraftExtra(val name: String, val value: String)

/**
 * An asset read from a file, waiting for review (the server's `DraftOut`, an asset with `needs_review`). [values] and
 * [confidence] are by field id; [name] is its Name field's value. The places and types are the names the file gave.
 */
data class AssetDraft(
    val id: String,
    val documentId: String?,
    val position: Int,
    val name: String,
    val values: Map<String, String>,
    val confidence: Map<String, Double>,
    val buildingName: String? = null,
    val floorName: String? = null,
    val locationName: String? = null,
    val systemTypeName: String? = null,
    val assetTypeName: String? = null,
    val assetSubtypeName: String? = null,
    val extraFields: List<DraftExtra> = emptyList(),
    val sourceRef: String? = null,
    val notes: String? = null,
    /** Its photos: the pictures on its row of a spreadsheet. */
    val photos: Int = 0,
) {
    /** Building › floor › location, as far as the file said. */
    val place: List<String> get() = listOfNotNull(buildingName, floorName, locationName).filter { it.isNotBlank() }

    /** System type › asset type › subtype, as far as the file said. */
    val type: List<String> get() = listOfNotNull(systemTypeName, assetTypeName, assetSubtypeName).filter { it.isNotBlank() }

    /** How many of its values the AI was unsure of. */
    val unsure: Int get() = values.count { (field, value) -> value.isNotBlank() && (confidence[field] ?: 1.0) < UNSURE_BELOW }
}

/** What adding drafts as assets did (the server's `AcceptResult`). */
data class DraftsAccepted(
    val added: Int,
    val placesCreated: Int,
    val unplaced: Int,
    val typesCreated: Int,
    val unclassified: Int,
    val fieldsCreated: Int,
    /** Names in the files that weren't turned into fields. */
    val leftOut: List<String>,
)
