package com.sclera.sclerago.presentation.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.MediaSource
import com.sclera.sclerago.domain.usecase.media.MediaSourceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Where an asset's photos, videos, voice memos and files are shown from: the offline database's copy, or the server's
 * while the sync hasn't brought the file down yet ([MediaSourceUseCase]). Each file is worked out once per screen and
 * shared by everything showing it (its tile and the viewer); it stops looking a few seconds after nothing shows it.
 */
@HiltViewModel
class MediaSourceViewModel @Inject constructor(private val sourceOf: MediaSourceUseCase) : ViewModel() {

    private data class Key(val assetId: String, val mediaId: String, val uri: String, val preview: Boolean)

    /** Read and written on the main thread only (from composition and the screen's callbacks). */
    private val sources = mutableMapOf<Key, StateFlow<MediaSource>>()

    fun source(assetId: String, media: Media, preview: Boolean): StateFlow<MediaSource> =
        sources.getOrPut(Key(assetId, media.id, media.uri, preview)) {
            sourceOf(assetId, media, preview).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), MediaSource.Loading)
        }

    /** [media] with its whole file reachable for Download: the server's copy (fetched now if need be) when the device has none. */
    suspend fun downloadable(assetId: String, media: Media): Media =
        when (val s = source(assetId, media, preview = false).first { it != MediaSource.Loading }) {
            is MediaSource.Server -> media.copy(uri = s.uri)
            else -> media
        }

    private companion object {
        const val STOP_MS = 5_000L
    }
}
