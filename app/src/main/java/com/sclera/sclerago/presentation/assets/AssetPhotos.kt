package com.sclera.sclerago.presentation.assets

import com.sclera.sclerago.presentation.components.pressClickable
import androidx.compose.foundation.background
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.components.AccessGate
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.presentation.components.CloseButton
import com.sclera.sclerago.presentation.components.MediaFull
import com.sclera.sclerago.presentation.components.MediaImage
import com.sclera.sclerago.presentation.components.RoundButton
import com.sclera.sclerago.presentation.components.bottomBorder
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.components.dashedBorder
import com.sclera.sclerago.presentation.components.noRippleClick
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.icons.Ico
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import androidx.compose.runtime.getValue
import com.sclera.sclerago.presentation.components.MenuAct
import com.sclera.sclerago.presentation.components.IconMenu
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
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
    const val STRIP = "detail-photo-strip"
    const val ADD = "detail-photo-add"
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

/**
 * One attachment as a square tile ([modifier] sets its size): the picture (or a document's type), a video's length, and an
 * expand mark. [label] is a chip along the bottom (the Info strip's photo kind, e.g. "NAMEPLATE"), with a smaller mark.
 * Tapping it opens it; holding it lists what can be done with it ([menu]). With [drag] (a new asset's photo cards) holding
 * and dragging moves it to another card ([card] is its own), and holding without dragging lists the same [menu].
 */
@Composable
internal fun MediaTile(
    m: Media,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    menu: List<MenuAct> = emptyList(),
    drag: PhotoDrag? = null,
    card: String = "",
) {
    val title = m.title()
    var menuOpen by remember(m.id) { mutableStateOf(false) }
    val hold = if (menu.isEmpty()) null else ({ menuOpen = true })
    // A draggable photo's hold is the drag's: picked up and let go without moving, its menu opens. The finger lifting
    // then also counts as a tap, which must not open the photo, so the tap right after a pick-up is ignored.
    var pickedUp by remember(m.id) { mutableStateOf(false) }
    val held = if (drag != null) {
        Modifier.draggablePhoto(
            drag, m, card,
            onPickUp = { pickedUp = true },
            onHold = { pickedUp = false; hold?.invoke() },
            onMoved = { pickedUp = false },
        )
    } else Modifier
    val tap: () -> Unit = {
        if (pickedUp) {
            pickedUp = false
        } else {
            onOpen()
        }
    }
    val actionsLabel = stringResource(R.string.media_actions)
    Box(
        modifier.then(held).pressClickable(shape = r(12), onLongClick = if (drag == null) hold else null, onLongClickLabel = actionsLabel, onClick = tap)
            .background(C.Raised).semantics { contentDescription = title },
    ) {
        IconMenu(menuOpen, { menuOpen = false }, menu)
        if (m.isDoc) Txt(m.ext, 11, 800, C.Yellow, Modifier.align(Alignment.Center))
        else MediaImage(m, Modifier.fillMaxSize(), maxPx = 240)
        if (m.video) Txt(
            lengthMs(m).takeIf { it > 0 }?.let { stringResource(R.string.detail_video_badge, formatDuration(it)) }
                ?: stringResource(R.string.detail_video).uppercase(Locale.getDefault()),
            9, 700, C.White,
            Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp), spacing = 0.4f,
        )
        if (label != null) Txt(
            label, 9, 700, C.White,
            Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).background(C.shade(0.55f), Capsule).padding(horizontal = 6.dp, vertical = 1.dp),
            spacing = 0.4f,
        )
        Box(
            Modifier.align(Alignment.TopEnd).padding(5.dp).size(18.dp).background(C.shade(0.55f), r(5)),
            contentAlignment = Alignment.Center,
        ) { if (label != null) LineIcon(Icons.Expand, 9.dp, C.White, 3.4f) else LineIcon(Icons.Expand, 11.dp, C.White, 2.6f) }
    }
}

/** Size of a tile in the Info tab's photo strip. */
private val StripTile = 76.dp

/**
 * The Info tab's photo strip: the asset's photos and videos as square tiles (a photo with a kind shows its name along the
 * bottom), then a dashed + that opens the camera ([onAdd]; null where nobody may add one, e.g. a signed-off asset); a
 * tile opens full screen ([onOpen] takes its place in [media]). Nothing shows for such an asset with none. [blocked]: the
 * signed-in user may not change the asset, so the + and Remove are greyed out and a tap says why.
 */
@Composable
internal fun InfoPhotoStrip(
    media: List<Media>,
    onAdd: (() -> Unit)?,
    onOpen: (Int) -> Unit,
    /** What holding a tile offers besides opening it: saving a copy, and removing it (null: nobody may). */
    onDownload: ((Media) -> Unit)? = null,
    onRemove: ((Media) -> Unit)? = null,
    blocked: UiText? = null,
) {
    val shown = media.withIndex().filter { (_, m) -> !m.isDoc && !m.audio }
    if (onAdd == null && shown.isEmpty()) return
    val addLabel = stringResource(R.string.detail_menu_camera)
    Row(
        // A full-width band like the Info sections under it.
        Modifier.fillMaxWidth().testTag(PhotoTags.STRIP).background(C.Surface).topBorder(C.Border).bottomBorder(C.Border)
            .horizontalScroll(rememberScrollState()).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        shown.forEach { (i, m) ->
            MediaTile(
                m, { onOpen(i) }, Modifier.size(StripTile).testTag(PhotoTags.tile(i)),
                label = if (m.video) null else m.category?.let { stringResource(it.nameRes()).uppercase(Locale.getDefault()) },
                menu = mediaMenu(m, { onOpen(i) }, onDownload, onRemove, removeBlocked = blocked),
            )
        }
        onAdd?.let { add ->
            AccessGate(blocked) {
                Box(
                    Modifier.size(StripTile).testTag(PhotoTags.ADD).pressClickable(shape = r(12), onClick = add).dashedBorder(C.Border2, 1.dp, 12.dp)
                        .semantics { contentDescription = addLabel },
                    contentAlignment = Alignment.Center,
                ) { LineIcon(Icons.Plus, 24.dp, C.Yellow, 2.6f) }
            }
        }
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
 * Full-screen view of the one attachment [m] that was tapped (no swiping to the others), as the web's media viewer:
 * Download saves a copy, and Remove (null: nobody may) asks to delete it from the asset; [removeBlocked]: the signed-in
 * user may not change the asset, so Remove is greyed out and a tap says why.
 */
@Composable
fun PhotoViewer(
    m: Media,
    onClose: () -> Unit,
    /** Saves a copy; null hides Download (a file picked from the phone is already there). */
    onDownload: ((Media) -> Unit)?,
    onRemove: ((Media) -> Unit)?,
    removeBlocked: UiText? = null,
) {
    Column(Modifier.fillMaxSize().testTag(PhotoTags.VIEWER).background(C.shade(0.96f)).noRippleClick { }) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Txt(m.title(), 15, 700)
                m.credit?.let { Txt(it, 10, 400, C.Muted, maxLines = Int.MAX_VALUE) }
            }
            onDownload?.let { download ->
                HeaderButton(Icons.Download, stringResource(R.string.media_download), C.Text, Modifier.testTag(PhotoTags.DOWNLOAD)) { download(m) }
            }
            onRemove?.let { remove ->
                AccessGate(removeBlocked) {
                    HeaderButton(Icons.Trash, stringResource(R.string.media_remove), C.Red, Modifier.testTag(PhotoTags.REMOVE)) { remove(m) }
                }
            }
            CloseButton(onClose)
        }
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.fillMaxWidth().heightIn(max = 440.dp).fillMaxHeight().clip(r(14)).background(C.Raised),
                contentAlignment = Alignment.Center,
            ) { key(m.id) { MediaFull(m) } }
        }
    }
}

/** A round header button like the viewer's Close, for an icon-only action ([description] is read out). */
@Composable
private fun HeaderButton(icon: Ico, description: String, tint: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    RoundButton(48.dp, C.Btn, onClick, modifier.semantics { contentDescription = description }) { LineIcon(icon, 18.dp, tint, 2.6f) }
}
