package com.sclera.sclerago.presentation.components

import com.sclera.sclerago.domain.model.Media
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.services.media.MediaLoader

/** Thumbnail / full image for a photo or the first frame of a video. */
@Composable
fun MediaImage(
    media: Media,
    modifier: Modifier = Modifier,
    maxPx: Int = 320,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: Color = C.Raised,
) {
    val context = LocalContext.current
    val bmp by produceState(if (media.isDoc) null else MediaLoader.cached(media.uri, maxPx), media.uri, maxPx) {
        if (value == null && !media.isDoc) value = MediaLoader.load(context, media.uri, media.video, maxPx)
    }
    Box(modifier.background(placeholder)) {
        bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = contentScale) }
    }
}
