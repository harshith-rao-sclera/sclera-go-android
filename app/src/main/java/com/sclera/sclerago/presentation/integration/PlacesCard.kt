package com.sclera.sclerago.presentation.integration

import com.sclera.sclerago.presentation.components.pressClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.AccessBlock
import com.sclera.sclerago.domain.model.PlacesDirection
import com.sclera.sclerago.presentation.components.AccessGate
import com.sclera.sclerago.presentation.components.RoundButton
import com.sclera.sclerago.presentation.components.SkeletonLine
import com.sclera.sclerago.presentation.components.agoText
import com.sclera.sclerago.presentation.components.OfflineGate
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/**
 * Buildings, floors & locations on the Integration page (shown once the property is linked), as the iOS app's: they
 * don't sync with VDMS, so Export and Import open the sheet to pick them, and History (the round
 * button beside them) lists every send and import. Under the title, how old VDMS's list is.
 */
@Composable
internal fun PlacesCard(s: PlacesUiState, online: Boolean, vm: PlacesImportViewModel) {
    Column(
        Modifier.fillMaxWidth().clip(r(16)).background(C.Surface).border(1.dp, C.Border, r(16)).padding(14.dp).testTag("places_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Txt(stringResource(R.string.pi_section_title), 18, 700, maxLines = 2)
            ListAge(s)
        }
        val ready = !s.loading && s.places != null
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HistoryButton(online, vm)
            CardButton(
                stringResource(R.string.pi_send), ready && !s.pushActive, s.access, online, vm::explainOffline,
                Modifier.weight(1f).testTag("places_send"),
            ) { vm.open(PlacesDirection.TO_VDMS) }
            CardButton(
                stringResource(R.string.pi_import), ready, s.access, online, vm::explainOffline,
                Modifier.weight(1f).testTag("places_import"),
            ) { vm.open(PlacesDirection.FROM_VDMS) }
        }
        if (s.failed && s.places == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(stringResource(R.string.pi_load_failed), 12, 400, C.Soft, Modifier.weight(1f), maxLines = Int.MAX_VALUE)
                LinkText(stringResource(R.string.pi_try_again), onClick = vm::load)
            }
        }
    }
}

/** How old VDMS's list of buildings, floors and locations is ("VDMS's list is from 22 h ago"); a placeholder while read. */
@Composable
private fun ListAge(s: PlacesUiState) {
    val places = s.places
    when {
        places == null && s.loading -> SkeletonLine(0.5f, 11.dp, Modifier.padding(vertical = 3.dp))
        places == null -> Unit // The failed line under the buttons says so.
        places.snapshotAt == null -> Txt(stringResource(R.string.pi_no_snapshot), 13, 400, C.Muted, maxLines = Int.MAX_VALUE)
        else -> Txt(stringResource(R.string.pi_card_list_from, agoText(places.snapshotAt)), 13, 400, C.Muted, maxLines = 2)
    }
}

/** History: every send to and import from VDMS, as a round button. */
@Composable
private fun HistoryButton(online: Boolean, vm: PlacesImportViewModel) {
    val label = stringResource(R.string.pi_history)
    OfflineGate(!online, vm::explainOffline) {
        RoundButton(
            34.dp, C.Btn, vm::openHistory,
            Modifier.semantics { contentDescription = label; role = Role.Button }.testTag("places_history"),
        ) { LineIcon(Icons.History, 16.dp, C.Text, 2.2f) }
    }
}

/**
 * Send to VDMS / Import from VDMS: they need Configure ([access] says why not, greyed out), then the server (greyed out
 * offline); [enabled] false while the places aren't there yet.
 */
@Composable
private fun CardButton(text: String, enabled: Boolean, access: AccessBlock?, online: Boolean, onOffline: () -> Unit, modifier: Modifier, onClick: () -> Unit) {
    AccessGate(access, modifier) {
        OfflineGate(!online && enabled && access == null, onOffline) {
            Pill(
                text, { if (enabled) onClick() },
                // The access gate dims it already; not twice.
                Modifier.fillMaxWidth().alpha(if (enabled || access != null) 1f else 0.4f).semantics { role = Role.Button },
                bg = C.Yellow, fg = C.OnYellow, height = 34.dp, hPad = 8.dp, size = 12, weight = 700,
            )
        }
    }
}

/** A skipped place, with a warning mark. */
@Composable
internal fun SkippedLine(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        LineIcon(Icons.Alert, 14.dp, C.Orange, 2.2f, Modifier.padding(top = 1.dp))
        Txt(text, 12, 400, C.Soft, Modifier.weight(1f), maxLines = Int.MAX_VALUE)
    }
}

/** A text button in the card's or the sheet's lines (Refresh, Try again, Select all…), with a full-size touch target. */
@Composable
internal fun LinkText(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Int = 12,
    /** The space either side of the text (0 to line the text up with the lines above it). */
    hPad: Dp = 8.dp,
    onClick: () -> Unit,
) {
    Box(
        modifier.heightIn(min = 48.dp).pressClickable(shape = r(8), enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = hPad),
        contentAlignment = Alignment.Center,
    ) { Txt(text, size, 700, if (enabled) C.Yellow else C.Muted) }
}

