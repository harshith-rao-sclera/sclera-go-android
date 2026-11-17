package com.sclera.sclerago.presentation.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.presentation.components.CloseButton
import com.sclera.sclerago.presentation.components.MediaFull
import com.sclera.sclerago.presentation.components.MediaImage
import com.sclera.sclerago.presentation.components.RoundButton
import com.sclera.sclerago.presentation.components.noRippleClick
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.icons.Ico
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import com.sclera.sclerago.domain.model.formatDuration
import com.sclera.sclerago.services.media.MediaDurations
import java.util.Locale

/** Test tags of the asset photos. */
object PhotoTags {
    const val VIEWER = "detail-photo-viewer"
    const val DOWNLOAD = "detail-photo-download"
    const val REMOVE = "detail-photo-remove"
    fun tile(index: Int) = "detail-photo-$index"
}

/** How an attached file is named: a document by its file name, a video or photo by its kind. */
@Composable
private fun Media.title(): String = when {
    isDoc -> fileName ?: stringResource(R.string.detail_document)
    video -> stringResource(R.string.detail_video)
    else -> stringResource(R.string.detail_photo)
}

/**
 * [m]'s length in milliseconds: its row's, else read from its file (media added on iOS saves no length); 0 while it
 * isn't known, so nothing shows rather than "0:00".
 */
@Composable
internal fun lengthMs(m: Media): Long {
    if (m.durationMs > 0 || !(m.video || m.audio)) return m.durationMs
    val context = LocalContext.current
    val read by produceState(0L, m.uri) { value = MediaDurations.of(context, m.uri) ?: 0L }
    return read
}

/** One attachment as a square tile ([modifier] sets its size): the picture (or a document's type), a video's length, and an expand mark. */
@Composable
internal fun MediaTile(m: Media, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val title = m.title()
    Box(modifier.clip(r(12)).background(C.Raised).semantics { contentDescription = title }.clickable(onClick = onOpen)) {
        if (m.isDoc) Txt(m.ext, 11, 800, C.Yellow, Modifier.align(Alignment.Center))
        else MediaImage(m, Modifier.fillMaxSize(), maxPx = 240)
        if (m.video) Txt(
            lengthMs(m).takeIf { it > 0 }?.let { stringResource(R.string.detail_video_badge, formatDuration(it)) }
                ?: stringResource(R.string.detail_video).uppercase(Locale.getDefault()),
            9, 700, C.White,
            Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp), spacing = 0.4f,
        )
        Box(
            Modifier.align(Alignment.TopEnd).padding(5.dp).size(18.dp).background(C.shade(0.55f), r(5)),
            contentAlignment = Alignment.Center,
        ) { LineIcon(Icons.Expand, 11.dp, C.White, 2.6f) }
    }
}

/** The title of the confirmation before removing [m]: a file or voice memo by its name, a photo or video by its kind. */
@Composable
internal fun removeMediaTitle(m: Media): String = when {
    m.isDoc -> stringResource(R.string.media_remove_named, m.title())
    m.video -> stringResource(R.string.media_remove_video)
    else -> stringResource(R.string.media_remove_photo)
}

/**
 * Full-screen view of one attachment, with arrows to the neighbours, as the web's media viewer: Download saves a copy,
 * and Remove (null: the asset is read-only) asks to delete it from the asset.
 */
@Composable
fun PhotoViewer(
    media: List<Media>,
    index: Int,
    onIndex: (Int) -> Unit,
    onClose: () -> Unit,
    onDownload: (Media) -> Unit,
    onRemove: ((Media) -> Unit)?,
) {
    val m = media[index]
    Column(Modifier.fillMaxSize().testTag(PhotoTags.VIEWER).background(C.shade(0.96f)).noRippleClick { }) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Txt(m.title(), 15, 700)
                Txt(stringResource(R.string.detail_photo_of, index + 1, media.size), 11, 400, C.Muted)
                m.credit?.let { Txt(it, 10, 400, C.Muted, maxLines = 2) }
            }
            HeaderButton(Icons.Download, stringResource(R.string.media_download), C.Text, Modifier.testTag(PhotoTags.DOWNLOAD)) { onDownload(m) }
            onRemove?.let { remove ->
                HeaderButton(Icons.Trash, stringResource(R.string.media_remove), C.Red, Modifier.testTag(PhotoTags.REMOVE)) { remove(m) }
            }
            CloseButton(onClose)
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.fillMaxWidth().heightIn(max = 440.dp).fillMaxHeight().clip(r(14)).background(C.Raised),
                contentAlignment = Alignment.Center,
            ) { MediaFull(m) }
            NavArrow(Icons.ChevronLeft, stringResource(R.string.detail_prev), index > 0, Modifier.align(Alignment.CenterStart).padding(start = 6.dp)) {
                if (index > 0) onIndex(index - 1)
            }
            NavArrow(Icons.ChevronRight, stringResource(R.string.detail_next), index < media.size - 1, Modifier.align(Alignment.CenterEnd).padding(end = 6.dp)) {
                if (index < media.size - 1) onIndex(index + 1)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            media.indices.forEach { i ->
                Box(Modifier.size(if (i == index) 18.dp else 6.dp, 6.dp).clip(r(3)).background(if (i == index) C.Yellow else C.RadioOff))
            }
        }
    }
}

/** A round header button like the viewer's Close, for an icon-only action ([description] is read out). */
@Composable
private fun HeaderButton(icon: Ico, description: String, tint: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    RoundButton(48.dp, C.Btn, onClick, modifier.semantics { contentDescription = description }) { LineIcon(icon, 18.dp, tint, 2.6f) }
}

@Composable
private fun NavArrow(icon: Ico, description: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    RoundButton(
        48.dp, C.shade(0.7f), onClick,
        modifier.alpha(if (enabled) 1f else 0.3f).semantics { contentDescription = description },
    ) { LineIcon(icon, 20.dp, C.Text, 2.6f) }
}
