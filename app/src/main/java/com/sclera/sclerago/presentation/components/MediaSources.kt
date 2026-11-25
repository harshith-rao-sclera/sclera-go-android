package com.sclera.sclerago.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.MediaSource
import kotlinx.coroutines.flow.StateFlow

/**
 * Where the asset media shown under it is read from (the offline database first, else the server), as a screen's view
 * model works it out. [preview]: a thumbnail, for which a photo's smaller version will do.
 */
fun interface MediaSources {
    fun source(media: Media, preview: Boolean): StateFlow<MediaSource>
}

/** The [MediaSources] of the screen above; null where media is read from its own URI as it is (captures, picked files). */
val LocalMediaSources = staticCompositionLocalOf<MediaSources?> { null }

/** The largest thumbnail the server's smaller version of a photo serves (it is at most 1280 px on its longest side). */
internal const val SERVER_PREVIEW_PX = 1280

/**
 * [media] as it is shown: as it is while the device has its file, else pointing at the copy fetched from the server; null
 * while that copy is being fetched. With no copy anywhere (offline) it stays as it is, so the usual placeholder shows.
 */
@Composable
fun shownMedia(media: Media, preview: Boolean): Media? {
    val sources = LocalMediaSources.current ?: return media
    val source by remember(sources, media, preview) { sources.source(media, preview) }.collectAsStateWithLifecycle()
    return when (val s = source) {
        is MediaSource.Server -> media.copy(uri = s.uri)
        MediaSource.Loading -> null
        is MediaSource.Local, MediaSource.Missing -> media
    }
}
