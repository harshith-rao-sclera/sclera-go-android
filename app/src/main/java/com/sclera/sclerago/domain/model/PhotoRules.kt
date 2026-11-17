package com.sclera.sclerago.domain.model

/** The kinds of photo a property's photo rules count (the server's `asset_media.category`). */
enum class PhotoKind(val db: String) {
    NAMEPLATE("nameplate"), ASSET("asset"), LABEL_ID("label_id");

    companion object {
        fun of(text: String?): PhotoKind? = entries.firstOrNull { it.db == text }
    }
}

/**
 * This photo as an equipment photo ([PhotoKind.ASSET]) when it answers no field and has no kind yet. The server reads
 * nameplate, label and equipment photos when they reach it (for validation and classifying); a photo without a kind is never read.
 */
fun Media.withDefaultKind(): Media =
    if (!video && !isDoc && !audio && fieldId == null && category == null) copy(category = PhotoKind.ASSET) else this

/** How many photos of [kind] each asset needs ([min]) and may have ([max]), as the property's settings say. */
data class PhotoRule(val kind: PhotoKind, val min: Int, val max: Int)

/** The server's rules when a property has set none: no photo needed, up to five of each kind. */
val DEFAULT_PHOTO_RULES: List<PhotoRule> = PhotoKind.entries.map { PhotoRule(it, min = 0, max = 5) }
