package com.sclera.sclerago.domain.model

/** The kinds of photo a property's photo rules count (the server's `asset_media.category`). */
enum class PhotoKind(val db: String) {
    NAMEPLATE("nameplate"), ASSET("asset"), LABEL_ID("label_id");

    companion object {
        fun of(text: String?): PhotoKind? = entries.firstOrNull { it.db == text }
    }
}

/**
 * The photos sent for the AI to read (at most [max]), as the iOS app picks them: one from each group in turn —
 * nameplate, label, asset, then other photos (no kind yet), each newest first — then a second from each, and so on.
 * E.g. 3 nameplate, 1 label, 1 asset → nameplate, label, asset; 4 nameplate only → 3 nameplate. A photo answering a
 * field is not sent. Used to classify an asset, to look up its specs and to fill the form from new photos.
 */
fun readablePhotos(media: List<Media>, max: Int): List<Media> {
    val photos = media.filter { !it.video && !it.audio && !it.isDoc && it.fieldId == null }
    val groups = PHOTO_GROUPS.map { kind -> photos.filter { it.category == kind }.sortedByDescending { it.createdAt } }
    val picked = mutableListOf<Media>()
    var round = 0
    while (picked.size < max && groups.any { it.size > round }) {
        groups.forEach { group -> if (picked.size < max) group.getOrNull(round)?.let(picked::add) }
        round++
    }
    return picked
}

/** The order photos are taken in, a group at a time; null is a photo without a kind yet ("Other photos"). */
private val PHOTO_GROUPS = listOf(PhotoKind.NAMEPLATE, PhotoKind.LABEL_ID, PhotoKind.ASSET, null)

/** How many photos of [kind] each asset needs ([min]) and may have ([max]), as the property's settings say. */
data class PhotoRule(val kind: PhotoKind, val min: Int, val max: Int)

/** The server's rules when a property has set none: no photo needed, up to five of each kind. */
val DEFAULT_PHOTO_RULES: List<PhotoRule> = PhotoKind.entries.map { PhotoRule(it, min = 0, max = 5) }
