package com.sclera.sclerago.domain.usecase.media

import com.sclera.sclerago.domain.model.photoCap
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import com.sclera.sclerago.domain.model.cameraPhotoRefusal
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
    suspend operator fun invoke(
        shot: Media,
        have: List<Media>,
        kind: PhotoKind? = null,
        readKind: Boolean = true,
        /** A new asset from the camera: past its kind's limit a shot goes to Other photos; refused only past every limit together. */
        newAsset: Boolean = false,
    ): SortedShot {
        if (shot.video) return SortedShot(shot, null)
        val sorted = when {
            kind != null -> shot.copy(category = kind)
            !readKind -> shot.copy(category = null)
            else -> sort(listOf(shot), fallback = null).first()
        }
        return SortedShot(sorted, refusal(sorted, have, newAsset))
    }

    /** Why [shot] (already sorted) can't be ticked beside [have]; null when it can. */
    /** The most photos one new asset takes from the camera ([photoCap]), as the property's photo rules change. */
    fun newAssetCap(): Flow<Int> = rules.rules().map(::photoCap)

    suspend fun refusal(shot: Media, have: List<Media>, newAsset: Boolean = false): PhotoRefusal? = when {
        shot.video -> null
        newAsset -> cameraPhotoRefusal(rules.rules().first(), have)
        else -> photoRefusal(rules.rules().first(), have, shot.category)
    }
}
