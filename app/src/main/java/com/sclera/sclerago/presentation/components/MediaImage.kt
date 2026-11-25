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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.services.media.MediaLoader

/** A picture being read: what there is so far, and whether the read is over (a document has nothing to read). */
private class Thumb(val bitmap: ImageBitmap?, val done: Boolean) {
    companion object {
        fun of(media: Media, maxPx: Int): Thumb =
            if (media.isDoc) Thumb(null, done = true) else MediaLoader.cached(media.uri, maxPx).let { Thumb(it, done = it != null) }
    }
}

/**
 * Thumbnail / full image for a photo or the first frame of a video; it shimmers while it is read. An asset's photo the
 * offline database doesn't have yet is read from the server's copy ([shownMedia]), shimmering while that is fetched.
 */
@Composable
fun MediaImage(
    media: Media,
    modifier: Modifier = Modifier,
    maxPx: Int = 320,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: Color = C.Raised,
) {
    val shown = if (media.isDoc) media else shownMedia(media, preview = maxPx <= SERVER_PREVIEW_PX)
    if (shown == null) {
        SkeletonBlock(modifier, RectangleShape)
        return
    }
    MediaPicture(shown, modifier, maxPx, contentScale, placeholder)
}

/** [media]'s picture, read from its own URI. */
@Composable
private fun MediaPicture(media: Media, modifier: Modifier, maxPx: Int, contentScale: ContentScale, placeholder: Color) {
    val context = LocalContext.current
    // produceState keeps its last value when the keys change, so each new photo sets its own picture (not the last one's).
    // It also says whether the read is over, so a picture that can't be read stops shimmering.
    val read by produceState(Thumb.of(media, maxPx), media.uri, maxPx) {
        value = Thumb.of(media, maxPx)
        if (!value.done) value = Thumb(MediaLoader.load(context, media.uri, media.video, maxPx), done = true)
    }
    val bmp = read.bitmap
    if (bmp == null && !read.done) {
        // Still being read from the phone: a shimmering placeholder the size of the picture.
        SkeletonBlock(modifier, RectangleShape)
        return
    }
    Box(modifier.background(placeholder)) {
        bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = contentScale) }
    }
}
