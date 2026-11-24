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

/** Why one more photo can't join an asset: its kind already has the most the property allows, or the asset has. */
sealed interface PhotoRefusal {
    /** [kind] already has [max] photos, the most the property allows (0: the property doesn't collect that kind). */
    data class KindFull(val kind: PhotoKind, val max: Int) : PhotoRefusal

    /** The asset already has [MAX_PHOTOS_PER_ASSET] photos. */
    data object AssetFull : PhotoRefusal
}

/**
 * Why one more photo of [kind] (null: no kind, Other photos) can't join an asset holding [have], as the server's
 * _check_room refuses it; null when it fits.
 */
fun photoRefusal(rules: List<PhotoRule>, have: List<Media>, kind: PhotoKind?): PhotoRefusal? = when {
    photoRoom(rules, have, null) <= 0 -> PhotoRefusal.AssetFull
    kind != null && photoRoom(rules, have, kind) <= 0 ->
        PhotoRefusal.KindFull(kind, rules.firstOrNull { it.kind == kind }?.max ?: MAX_PHOTOS_PER_ASSET)
    else -> null
}

/**
 * What [fitPhotoRules] let in ([kept], in order) and turned away ([refused]), with the most photos the property allows
 * of each kind it turned photos away from ([limits]; 0: it doesn't collect that kind).
 */
data class FittedPhotos(val kept: List<Media>, val refused: List<Media>, val limits: Map<PhotoKind, Int> = emptyMap()) {
    /** How many photos of each kind were turned away. */
    val refusedByKind: Map<PhotoKind, Int> get() = refused.mapNotNull { it.category }.groupingBy { it }.eachCount()
}

/**
 * [adding] kept within the photo rules beside [have], in order, as the server's _check_room refuses one more photo of a
 * kind than the property allows: a photo whose kind (from its card, or sorted off the photo) already has the most the
 * rules allow, or that the property doesn't collect, is refused (say a sixth nameplate photo when five are allowed). The
 * other photos, videos, voice memos and files all go in.
 */
fun fitPhotoRules(rules: List<PhotoRule>, have: List<Media>, adding: List<Media>): FittedPhotos {
    val counts = PhotoKind.entries.associateWith { k -> have.count { it.isAssetPhoto() && it.category == k } }.toMutableMap()
    val (kept, refused) = adding.partition { m ->
        val kind = m.category?.takeIf { m.isAssetPhoto() } ?: return@partition true
        val max = rules.firstOrNull { it.kind == kind }?.max ?: return@partition true
        (counts.getValue(kind) < max).also { fits -> if (fits) counts[kind] = counts.getValue(kind) + 1 }
    }
    val limits = refused.mapNotNull { it.category }.distinct().associateWith { k -> rules.firstOrNull { it.kind == k }?.max ?: 0 }
    return FittedPhotos(kept, refused, limits)
}

/** How many photos of [kind] each asset needs ([min]) and may have ([max]), as the property's settings say. */
data class PhotoRule(val kind: PhotoKind, val min: Int, val max: Int)

/** The server's rules when a property has set none: no photo needed, up to five of each kind. */
val DEFAULT_PHOTO_RULES: List<PhotoRule> = PhotoKind.entries.map { PhotoRule(it, min = 0, max = 5) }
