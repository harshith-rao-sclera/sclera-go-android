package com.sclera.sclerago.presentation.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import com.sclera.sclerago.presentation.components.dashedBorder
import com.sclera.sclerago.presentation.components.noRippleClick
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.icons.Ico
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** Test tags of the asset photos. */
object PhotoTags {
    const val ADD = "detail-photo-add"
    const val VIEWER = "detail-photo-viewer"
    const val EMPTY = "detail-photo-empty"
    fun tile(index: Int) = "detail-photo-$index"
}

/** How an attached file is named: a document by its file name, a video or photo by its kind. */
@Composable
private fun Media.title(): String = when {
    isDoc -> fileName ?: stringResource(R.string.detail_document)
    video -> stringResource(R.string.detail_video)
    else -> stringResource(R.string.detail_photo)
}

/** The asset real photos, videos and documents, then the dashed button that adds more (unless the asset is locked). */
@Composable
fun PhotoStrip(
    media: List<Media>,
    locked: Boolean,
    onOpen: (Int) -> Unit,
    onTakePhoto: () -> Unit,
    onPickGallery: () -> Unit,
    onPickFiles: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        media.forEachIndexed { i, m ->
            val title = m.title()
            Box(
                Modifier.size(76.dp).testTag(PhotoTags.tile(i)).clip(r(12)).background(C.Raised)
                    .semantics { contentDescription = title }.clickable { onOpen(i) },
            ) {
                if (m.isDoc) Txt(m.ext, 11, 800, C.Yellow, Modifier.align(Alignment.Center))
                else MediaImage(m, Modifier.fillMaxSize(), maxPx = 240)
                if (m.video) Txt(
                    stringResource(R.string.detail_video_badge, m.durationText), 9, 700, C.White,
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp), spacing = 0.4f,
                )
                Box(
                    Modifier.align(Alignment.TopEnd).padding(5.dp).size(18.dp).background(C.shade(0.55f), r(5)),
                    contentAlignment = Alignment.Center,
                ) { LineIcon(Icons.Expand, 11.dp, C.White, 2.6f) }
            }
        }
        if (!locked) Box {
            Column(
                Modifier.size(76.dp).testTag(PhotoTags.ADD).clip(r(12)).background(C.ImportStrip).dashedBorder(C.YTintBd, 1.5.dp, 12.dp)
                    .clickable { menu = true },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                LineIcon(Icons.Plus, 20.dp, C.Yellow, 2.4f)
                Txt(stringResource(R.string.detail_add_photo), 10, 700, C.Yellow)
            }
            DropdownMenu(menu, { menu = false }, containerColor = C.Raised) {
                listOf(
                    Triple(Icons.Camera, R.string.detail_menu_camera, onTakePhoto),
                    Triple(Icons.Gallery, R.string.detail_menu_gallery, onPickGallery),
                    Triple(Icons.FileDoc, R.string.detail_menu_files, onPickFiles),
                ).forEach { (icon, label, go) ->
                    DropdownMenuItem(
                        text = { Txt(stringResource(label), 13, 500) },
                        leadingIcon = { LineIcon(icon, 18.dp, C.Yellow, 2.2f) },
                        onClick = { menu = false; go() },
                    )
                }
            }
        }
        if (media.isEmpty()) {
            Txt(
                stringResource(R.string.detail_no_photos), 12, 400, C.Muted,
                Modifier.align(Alignment.CenterVertically).widthIn(max = 220.dp).testTag(PhotoTags.EMPTY), maxLines = 3,
            )
        }
    }
}

/** Full-screen view of one attachment, with arrows to the neighbours. */
@Composable
fun PhotoViewer(media: List<Media>, index: Int, onIndex: (Int) -> Unit, onClose: () -> Unit) {
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

@Composable
private fun NavArrow(icon: Ico, description: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    RoundButton(
        48.dp, C.shade(0.7f), onClick,
        modifier.alpha(if (enabled) 1f else 0.3f).semantics { contentDescription = description },
    ) { LineIcon(icon, 20.dp, C.Text, 2.6f) }
}
