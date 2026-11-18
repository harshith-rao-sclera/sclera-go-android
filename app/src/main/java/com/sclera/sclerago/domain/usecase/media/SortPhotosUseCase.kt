package com.sclera.sclerago.domain.usecase.media

import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.PhotoKind
import com.sclera.sclerago.domain.photos.PhotoReader
import com.sclera.sclerago.domain.photos.isSortable
import com.sclera.sclerago.domain.photos.photoKindOf
import javax.inject.Inject

/**
 * Sorts photos into nameplate, label ID or asset by reading each one on the device ([photoKindOf]), so it works offline.
 * Only photos without a kind are read ([isSortable]); videos, voice memos, files, photos answering a field and photos
 * that already have a kind (added from a kind's card) are returned as they are.
 */
class SortPhotosUseCase @Inject constructor(private val reader: PhotoReader) {
    /** Whether any of [media] would be read (so a screen can say it is sorting). */
    fun needed(media: List<Media>): Boolean = media.any { it.isSortable() }

    /** [media] with each sortable photo given the kind read off it; one that can't be read gets [fallback]. */
    suspend operator fun invoke(media: List<Media>, fallback: PhotoKind?): List<Media> = media.map { m ->
        if (!m.isSortable()) m
        else m.copy(category = reader.read(m.uri)?.let(::photoKindOf) ?: fallback)
    }
}
