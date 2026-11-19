package com.sclera.sclerago.presentation.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.ChangeRequest
import com.sclera.sclerago.presentation.components.LabeledInput
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.agoText
import com.sclera.sclerago.presentation.components.blockSwipe
import com.sclera.sclerago.presentation.components.fabShadow
import com.sclera.sclerago.presentation.components.noRippleClick
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** What the change request pieces do: open the dialog, edit its note, send or cancel it, withdraw / decline, reopen. */
class ChangeRequestActions(
    val onOpen: () -> Unit = {},
    val onNote: (String) -> Unit = {},
    val onSend: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onClose: () -> Unit = {},
    val onReopen: () -> Unit = {},
)

/** Test tags of the change request pieces of the asset details. */
object ChangeRequestTags {
    const val LINE = "change-request"
    const val ASK = "change-request-ask"
    const val CLOSE = "change-request-close"
    const val REOPEN = "change-request-reopen"
    const val NOTE = "change-request-note"
    const val SEND = "change-request-send"
}

/** What a change request says: who asked (you, [requester] by name when known, or someone) and when, and the note. */
@Composable
internal fun ChangeRequestLine(request: ChangeRequest, mine: Boolean, requester: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().testTag(ChangeRequestTags.LINE).clip(r(10)).background(C.WarnRow).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LineIcon(Icons.Alert, 16.dp, C.Orange, 2.2f, Modifier.padding(top = 1.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            val ago = agoText(request.requestedAt)
            Txt(
                when {
                    mine -> stringResource(R.string.change_request_mine, ago)
                    requester != null -> stringResource(R.string.change_request_by, requester, ago)
                    else -> stringResource(R.string.change_request_other, ago)
                },
                12, 700, C.Orange, maxLines = 2,
            )
            // As the web: what needs changing, or that nothing was said.
            val note = request.note?.takeIf { it.isNotBlank() }
            if (note != null) Txt(stringResource(R.string.change_request_note_line, note), 12, 400, C.Soft, maxLines = 6)
            else Txt(stringResource(R.string.change_request_note_none), 12, 400, C.Muted, italic = true)
        }
    }
}

/**
 * For an admin (who can change a submitted asset): the change request someone made, with Decline (or Withdraw, for
 * their own) and Approve, which moves the asset back to draft, assigns it to whoever asked and closes the request.
 */
@Composable
internal fun AdminChangeRequestCard(
    request: ChangeRequest,
    mine: Boolean,
    requester: String?,
    onClose: () -> Unit,
    onReopen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().clip(r(14)).background(C.Surface).border(1.dp, C.Border, r(14)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ChangeRequestLine(request, mine, requester)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(
                stringResource(if (mine) R.string.change_request_withdraw else R.string.change_request_decline), onClose,
                Modifier.weight(1f).testTag(ChangeRequestTags.CLOSE), height = 48.dp, size = 14, weight = 700,
            )
            Pill(
                stringResource(R.string.change_request_reopen), onReopen, Modifier.weight(1f).testTag(ChangeRequestTags.REOPEN),
                bg = C.Yellow, fg = C.OnYellow, height = 48.dp, size = 14, weight = 700,
            )
        }
    }
}

/** Asking an admin to reopen a submitted asset: what needs changing (optional), then Send request or Cancel. */
@Composable
internal fun ChangeRequestDialog(
    note: String,
    onNote: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    /** The heading; one asset's by default (the selection's says how many). */
    title: String = stringResource(R.string.change_request_title),
) {
    Box(Modifier.fillMaxSize().background(C.Scrim).noRippleClick(onCancel).blockSwipe().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 360.dp).fillMaxWidth().fabShadow(r(18), 16.dp).clip(r(18)).background(C.Surface)
                .border(1.dp, C.Border2, r(18)).noRippleClick { }.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(44.dp).background(C.WarnRow, CircleShape), contentAlignment = Alignment.Center) {
                LineIcon(Icons.Pencil, 20.dp, C.Orange, 2.2f)
            }
            Txt(title, 16, 700)
            Txt(stringResource(R.string.change_request_body), 13, 400, C.Muted, maxLines = 4)
            Box(Modifier.testTag(ChangeRequestTags.NOTE)) {
                LabeledInput(
                    stringResource(R.string.change_request_note_label), note, onNote, stringResource(R.string.change_request_note_hint),
                    singleLine = false, minLines = 3,
                )
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill(stringResource(R.string.action_cancel), onCancel, Modifier.weight(1f), height = 46.dp, size = 14)
                Pill(
                    stringResource(R.string.change_request_send), onSend, Modifier.weight(1f).testTag(ChangeRequestTags.SEND),
                    bg = C.Yellow, fg = C.OnYellow, height = 46.dp, size = 14, weight = 700,
                )
            }
        }
    }
}
