package com.sclera.sclerago.presentation.tagging

import com.sclera.sclerago.presentation.components.pressClickable
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
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.components.AccessGate
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
    /** The user picked where the QR code goes in the QR icon's popover: movable (true) or fixed (false). */
    val onPlacement: (movable: Boolean) -> Unit = {},
    /** The QR popover was closed without a choice. */
    val onDismissPlacement: () -> Unit = {},
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
    const val PLACEMENT_MOVABLE = "tag-placement-movable"
    const val PLACEMENT_FIXED = "tag-placement-fixed"
}

/**
 * The Tags bar at the top of an asset's Info tab (the design's): "Tags" and how many of the three kinds the asset has, then one icon
 * per kind: green once tagged, yellow while not. Tapping an icon starts that kind (the QR icon first opens its placement popover:
 * movable or fixed); with [blocked] (an asset the user may not
 * change: someone else's, a submitted or signed-off one for anyone but a reviewer, or a view-only group) the icons are greyed
 * out and a tap says why.
 */
@Composable
fun TagsBar(state: TagUiState, actions: TagsActions, modifier: Modifier = Modifier, blocked: UiText? = null) {
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
            AccessGate(blocked) {
                Box(
                    Modifier.pressClickable { actions.onStart(kind) }.size(width = 44.dp, height = 48.dp)
                        .semantics { contentDescription = description }
                        .testTag(TagTags.icon(kind)),
                    contentAlignment = Alignment.Center,
                ) {
                    LineIcon(iconOf(kind), 22.dp, if (tagged) C.Green else C.Yellow, 1.6f)
                    if (kind == TagKind.QR && state.phase == TagPhase.ChoosingPlacement) QrPlacementPopover(actions)
                }
            }
        }
    }
}

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
