package com.sclera.sclerago.presentation.integration

import com.sclera.sclerago.presentation.components.pressClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.PlacesDirection
import com.sclera.sclerago.presentation.components.SkeletonList
import com.sclera.sclerago.presentation.components.OfflineGate
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/**
 * Buildings, floors & locations on the Integration page (shown once the property is linked), as the web's: they don't
 * sync with VDMS, so "Send to VDMS" and "Import from VDMS" open the sheet to pick them, and History lists every send and
 * import.
 */
@Composable
internal fun PlacesCard(s: PlacesUiState, online: Boolean, vm: PlacesImportViewModel) {
    Column(
        Modifier.fillMaxWidth().clip(r(14)).background(C.Surface).border(1.dp, C.Border, r(14)).padding(14.dp).testTag("places_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(stringResource(R.string.pi_section_title), 15, 700, modifier = Modifier.weight(1f), maxLines = 2)
            HistoryButton(online, vm)
        }
        val ready = s.canImport && !s.loading && s.places != null
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CardButton(
                stringResource(R.string.pi_send), ready && !s.pushActive, online, vm::explainOffline,
                Modifier.weight(1f).testTag("places_send"),
            ) { vm.open(PlacesDirection.TO_VDMS) }
            CardButton(
                stringResource(R.string.pi_import), ready, online, vm::explainOffline,
                Modifier.weight(1f).testTag("places_import"),
            ) { vm.open(PlacesDirection.FROM_VDMS) }
        }
        when {
            s.loading && s.places == null -> SkeletonList(stringResource(R.string.int_loading), count = 3, lines = 1, card = false, gap = 2.dp)
            s.failed && s.places == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(stringResource(R.string.pi_load_failed), 12, 400, C.Soft, Modifier.weight(1f), maxLines = Int.MAX_VALUE)
                LinkText(stringResource(R.string.pi_try_again), onClick = vm::load)
            }
        }
    }
}

/** History: every send to and import from VDMS. */
@Composable
private fun HistoryButton(online: Boolean, vm: PlacesImportViewModel) {
    OfflineGate(!online, vm::explainOffline) {
        Row(
            Modifier.heightIn(min = 48.dp).pressClickable(shape = r(10), role = Role.Button, onClick = vm::openHistory)
                .padding(horizontal = 10.dp).testTag("places_history"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LineIcon(Icons.Clock, 16.dp, C.Yellow, 2.2f)
            Txt(stringResource(R.string.pi_history), 12, 700, C.Yellow)
        }
    }
}

@Composable
private fun CardButton(text: String, enabled: Boolean, online: Boolean, onOffline: () -> Unit, modifier: Modifier, onClick: () -> Unit) {
    OfflineGate(!online && enabled, onOffline, modifier) {
        Pill(
            text, { if (enabled) onClick() },
            Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f).semantics { role = Role.Button },
            bg = C.Btn, height = 48.dp, weight = 700,
        )
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
internal fun LinkText(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 48.dp).pressClickable(shape = r(8), enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Txt(text, 12, 700, if (enabled) C.Yellow else C.Muted) }
}

