package com.sclera.sclerago.domain.usecase.media

import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.PhotoKind
import com.sclera.sclerago.domain.model.PhotoRefusal
import com.sclera.sclerago.domain.model.photoRefusal
import com.sclera.sclerago.domain.repository.PhotoRulesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** A shot just taken in the camera, sorted into its kind, and why it can't be kept with the others (null: it can). */
data class SortedShot(val shot: Media, val refusal: PhotoRefusal?)

/**
 * The camera's check as each shot is taken: the photo is read on the device and sorted into nameplate, label ID or asset
 * (or given the kind of the card the camera was opened from), then checked against the property's photo rules beside the
 * photos the asset already has and the shots already ticked, so a sixth nameplate photo where five are allowed is
 * refused there and then. Videos are never refused.
 */
class SortShotUseCase @Inject constructor(private val sort: SortPhotosUseCase, private val rules: PhotoRulesRepository) {
    /**
     * Sorts [shot] and checks it beside [have] (the asset's photos and the ticked shots); [kind] is the kind of the card
     * the camera was opened from (null: the kind read off the photo, or none at all when [readKind] is false: Other photos).
     */
    suspend operator fun invoke(shot: Media, have: List<Media>, kind: PhotoKind? = null, readKind: Boolean = true): SortedShot {
        if (shot.video) return SortedShot(shot, null)
        val sorted = when {
            kind != null -> shot.copy(category = kind)
            !readKind -> shot.copy(category = null)
            else -> sort(listOf(shot), fallback = null).first()
        }
        return SortedShot(sorted, refusal(sorted, have))
    }

    /** Why [shot] (already sorted) can't be ticked beside [have]; null when it can. */
    suspend fun refusal(shot: Media, have: List<Media>): PhotoRefusal? =
        if (shot.video) null else photoRefusal(rules.rules().first(), have, shot.category)
}
