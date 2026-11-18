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

/** A photo of the asset itself (not a video, voice memo or file, nor a field's answer): the kind that photo rules count. */
private fun Media.isAssetPhoto(): Boolean = !video && !audio && !isDoc && fieldId == null

/**
 * How many more photos of [kind] an asset holding [have] may take: its rule's most less those it has (none for a kind
 * the rules don't allow); for no kind (Other photos, sorted when added), what is left of [MAX_PHOTOS_PER_ASSET].
 */
fun photoRoom(rules: List<PhotoRule>, have: List<Media>, kind: PhotoKind?): Int {
    val photos = have.filter { it.isAssetPhoto() }
    val left = if (kind == null) MAX_PHOTOS_PER_ASSET - photos.size
    else (rules.firstOrNull { it.kind == kind }?.max ?: MAX_PHOTOS_PER_ASSET) - photos.count { it.category == kind }
    return left.coerceAtLeast(0)
}

/**
 * [adding] kept within the photo rules beside [have], in order: a photo whose kind (from its card, or sorted off the
 * photo) already has the most the rules allow goes to no kind (Other photos), which the server takes without a limit,
 * rather than refusing the lot. Returns the photos, and how many were moved.
 */
fun fitPhotoRules(rules: List<PhotoRule>, have: List<Media>, adding: List<Media>): Pair<List<Media>, Int> {
    val counts = PhotoKind.entries.associateWith { k -> have.count { it.isAssetPhoto() && it.category == k } }.toMutableMap()
    var moved = 0
    val fitted = adding.map { m ->
        val kind = m.category?.takeIf { m.isAssetPhoto() } ?: return@map m
        val max = rules.firstOrNull { it.kind == kind }?.max ?: return@map m
        if (counts.getValue(kind) < max) {
            counts[kind] = counts.getValue(kind) + 1
            m
        } else {
            moved++
            m.copy(category = null)
        }
    }
    return fitted to moved
}

/** How many photos of [kind] each asset needs ([min]) and may have ([max]), as the property's settings say. */
data class PhotoRule(val kind: PhotoKind, val min: Int, val max: Int)

/** The server's rules when a property has set none: no photo needed, up to five of each kind. */
val DEFAULT_PHOTO_RULES: List<PhotoRule> = PhotoKind.entries.map { PhotoRule(it, min = 0, max = 5) }
