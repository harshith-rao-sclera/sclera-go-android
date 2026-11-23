package com.sclera.sclerago.presentation.assets

import com.sclera.sclerago.presentation.common.is24Hour
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.ChangeRequest
import com.sclera.sclerago.presentation.common.formatDateAndTime
import com.sclera.sclerago.presentation.components.GuardedClose
import com.sclera.sclerago.presentation.components.LabeledInput
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.blockSwipe
import com.sclera.sclerago.presentation.components.bottomBorder
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
    const val BANNER = "change-request-banner"
    const val ASK = "change-request-ask"
    const val CLOSE = "change-request-close"
    const val REOPEN = "change-request-reopen"
    const val NOTE = "change-request-note"
    const val SEND = "change-request-send"
}

/**
 * What a change request says, as the web's request line: "Requested by [requester] on <date and time>" (you, when it is
 * yours and the name isn't on this phone; someone, when nobody is known), and the note. [banner] adds the "Change
 * requested" heading the web's banner has on the open asset.
 */
@Composable
internal fun ChangeRequestLine(
    request: ChangeRequest,
    mine: Boolean,
    requester: String? = null,
    modifier: Modifier = Modifier,
    banner: Boolean = false,
    /** Its warning icon; left out under the change request banner, whose line already has one. */
    icon: Boolean = true,
) {
    Row(
        modifier.fillMaxWidth().testTag(ChangeRequestTags.LINE).clip(r(10)).background(C.WarnRow).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon) LineIcon(Icons.Alert, 16.dp, C.Orange, 2.2f, Modifier.padding(top = 1.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (banner) Txt(stringResource(R.string.dash_change_requested_badge), 13, 700, C.Orange)
            val who = requester ?: stringResource(if (mine) R.string.change_request_you else R.string.change_request_someone)
            val locale = LocalConfiguration.current.locales[0]
            Txt(
                request.requestedAt?.let { stringResource(R.string.change_request_by_on, who, formatDateAndTime(it, locale, use24Hour = is24Hour())) }
                    ?: stringResource(R.string.change_request_by, who),
                12, if (banner) 400 else 700, C.Orange, maxLines = Int.MAX_VALUE,
            )
            // As the web: what needs changing, or that nothing was said.
            val note = request.note?.takeIf { it.isNotBlank() }
            if (note != null) Txt(stringResource(R.string.change_request_note_line, note), 12, 400, C.Soft, maxLines = Int.MAX_VALUE)
            else Txt(stringResource(R.string.change_request_note_none), 12, 400, C.Muted, italic = true, maxLines = Int.MAX_VALUE)
        }
    }
}

/**
 * A change request on a submitted (or signed-off) asset, as one slim line under the top bar on every tab: "Change
 * requested · <who>". Tapping it opens the rest: who asked and when, the note, and what can be done. An admin, who can
 * change the asset ([admin]), declines it (withdraws, when it is their own) or moves the asset back to draft for whoever
 * asked; someone else withdraws their own request; anyone else just reads it.
 */
@Composable
internal fun ChangeRequestBanner(
    request: ChangeRequest,
    mine: Boolean,
    requester: String?,
    admin: Boolean,
    onClose: () -> Unit,
    onReopen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val who = requester ?: stringResource(if (mine) R.string.change_request_you else R.string.change_request_someone)
    val toggle = stringResource(if (open) R.string.change_banner_collapse else R.string.change_banner_expand)
    Column(modifier.fillMaxWidth().background(C.WarnRow).bottomBorder(C.Orange.copy(alpha = 0.35f)).testTag(ChangeRequestTags.BANNER)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClickLabel = toggle) { open = !open }.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LineIcon(Icons.Alert, 16.dp, C.Orange, 2.2f)
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Txt(stringResource(R.string.dash_change_requested_badge), 13, 700, C.Orange)
                Txt("· $who", 12, 400, C.Soft, Modifier.weight(1f, fill = false))
            }
            LineIcon(Icons.ChevronDown, 15.dp, C.Orange, 2.6f, Modifier.rotate(if (open) 180f else 0f))
        }
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChangeRequestLine(request, mine, requester, icon = false)
                // Small labelled pills at the end (32dp tall; each still answers a 48dp touch).
                if (admin || mine) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)) {
                        Pill(
                            stringResource(if (mine) R.string.change_request_withdraw else R.string.change_request_decline), onClose,
                            Modifier.minimumInteractiveComponentSize().testTag(ChangeRequestTags.CLOSE),
                            height = 32.dp, hPad = 14.dp, size = 12, weight = 700,
                        )
                        if (admin) {
                            Pill(
                                stringResource(R.string.change_request_reopen), onReopen,
                                Modifier.minimumInteractiveComponentSize().testTag(ChangeRequestTags.REOPEN),
                                bg = C.Yellow, fg = C.OnYellow, height = 32.dp, hPad = 14.dp, size = 12, weight = 700,
                            )
                        }
                    }
                }
            }
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
    /** The note differs from what it started as: Back, Cancel or a tap outside asks before discarding it. */
    dirty: Boolean = false,
) = GuardedClose(dirty, onCancel) { close ->
    BackHandler(onBack = close)
    Box(Modifier.fillMaxSize().background(C.Scrim).noRippleClick(close).blockSwipe().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 360.dp).fillMaxWidth().fabShadow(r(18), 16.dp).clip(r(18)).background(C.Surface)
                .border(1.dp, C.Border2, r(18)).noRippleClick { }.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(44.dp).background(C.WarnRow, CircleShape), contentAlignment = Alignment.Center) {
                LineIcon(Icons.Pencil, 20.dp, C.Orange, 2.2f)
            }
            Txt(title, 16, 700)
            Txt(stringResource(R.string.change_request_body), 13, 400, C.Muted, maxLines = Int.MAX_VALUE)
            Box(Modifier.testTag(ChangeRequestTags.NOTE)) {
                LabeledInput(
                    stringResource(R.string.change_request_note_label), note, onNote, stringResource(R.string.change_request_note_hint),
                    singleLine = false, minLines = 3,
                )
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill(stringResource(R.string.action_cancel), close, Modifier.weight(1f), height = 46.dp, size = 14)
                Pill(
                    stringResource(R.string.change_request_send), onSend, Modifier.weight(1f).testTag(ChangeRequestTags.SEND),
                    bg = C.Yellow, fg = C.OnYellow, height = 46.dp, size = 14, weight = 700,
                )
            }
        }
    }
}
