package com.sclera.sclerago.presentation.tagging

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.TagKind
import com.sclera.sclerago.presentation.components.bottomBorder
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Ico
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** What the Tags bar does. */
class TagsActions(
    /** The user tapped the icon of a kind of tag to attach. */
    val onStart: (TagKind) -> Unit = {},
)

object TagTags {
    fun icon(kind: TagKind) = "tag-icon-${kind.name.lowercase()}"
    const val BAR = "tag-bar"
    const val SCREEN = "tag-scan-screen"
    const val CLOSE = "tag-close"
    const val TORCH = "tag-torch"
    const val NFC_DIALOG = "tag-nfc-dialog"
    const val NFC_CANCEL = "tag-nfc-cancel"
    const val WORKING = "tag-working"
    /** The tagged / refused message: the app's shared toast. */
    const val TOAST = com.sclera.sclerago.presentation.components.ToastTags.TOAST
    const val SHEET_PRIMARY = "tag-sheet-primary"
    const val SHEET_SECONDARY = "tag-sheet-secondary"
}

/**
 * The Tags bar at the top of an asset's Info tab (the design's): "Tags" and how many of the three kinds the asset has, then one icon
 * per kind: green once tagged, yellow while not. Tapping an icon starts that kind; with [enabled] false (a submitted or signed-off asset) the icons are dimmed and do nothing.
 */
@Composable
fun TagsBar(state: TagUiState, actions: TagsActions, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(
        modifier.fillMaxWidth().background(C.Surface).topBorder(C.Border).bottomBorder(C.Border).height(48.dp).padding(start = 12.dp, end = 6.dp)
            .testTag(TagTags.BAR),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Txt(stringResource(R.string.tag_bar_title), 15, 700)
            Txt(stringResource(R.string.tag_bar_count, state.tagged.size, TagKind.entries.size), 11, 500, C.Muted, Modifier.padding(start = 6.dp))
        }
        TagKind.entries.forEach { kind ->
            val tagged = kind in state.tagged
            val description = stringResource(if (tagged) taggedText(kind) else tagKindName(kind))
            Box(
                Modifier.size(width = 44.dp, height = 48.dp).alpha(if (enabled) 1f else DISABLED_ALPHA).clickable(enabled = enabled) { actions.onStart(kind) }
                    .semantics { contentDescription = description }
                    .testTag(TagTags.icon(kind)),
                contentAlignment = Alignment.Center,
            ) {
                LineIcon(iconOf(kind), 22.dp, if (tagged) C.Green else C.Yellow, 1.6f)
            }
        }
    }
}

private const val DISABLED_ALPHA = 0.4f

private fun iconOf(kind: TagKind): Ico = when (kind) {
    TagKind.QR -> Icons.Qr
    TagKind.BARCODE -> Icons.Barcode
    TagKind.NFC -> Icons.Nfc
}

internal fun tagKindName(kind: TagKind): Int = when (kind) {
    TagKind.QR -> R.string.tag_kind_qr
    TagKind.BARCODE -> R.string.tag_kind_barcode
    TagKind.NFC -> R.string.tag_kind_nfc
}

internal fun taggedText(kind: TagKind): Int = when (kind) {
    TagKind.QR -> R.string.tag_tagged_qr
    TagKind.BARCODE -> R.string.tag_tagged_barcode
    TagKind.NFC -> R.string.tag_tagged_nfc
}
