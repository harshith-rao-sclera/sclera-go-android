package com.sclera.sclerago.presentation.integration

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.sclera.sclerago.domain.model.PlacesPush
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.components.ListLoader
import com.sclera.sclerago.presentation.components.OfflineGate
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.agoText
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import kotlinx.coroutines.delay

/**
 * Buildings, floors & locations on the Integration page (shown once the property is linked), as the web's: they don't
 * sync with VDMS, so "Send to VDMS" and "Import from VDMS" open the sheet to pick them, the VDMS list's age shows with
 * Refresh, and the latest send to VDMS says how it went.
 */
@Composable
internal fun PlacesCard(s: PlacesUiState, online: Boolean, vm: PlacesImportViewModel) {
    // Re-reads "updated 3 min ago" now and then, as the web does.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(AGO_TICK_MS)
            now = System.currentTimeMillis()
        }
    }
    Column(
        Modifier.fillMaxWidth().clip(r(14)).background(C.Surface).border(1.dp, C.Border, r(14)).padding(14.dp).testTag("places_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Txt(stringResource(R.string.pi_section_title), 15, 700, maxLines = 2)
            Txt(stringResource(R.string.pi_section_intro), 12, 400, C.Muted, maxLines = 4)
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
            s.loading && s.places == null -> ListLoader(stringResource(R.string.int_loading))
            s.failed && s.places == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(stringResource(R.string.pi_load_failed), 12, 400, C.Soft, Modifier.weight(1f), maxLines = 3)
                LinkText(stringResource(R.string.pi_try_again), onClick = vm::load)
            }
            else -> {
                val ago = agoText(s.places?.snapshotAt, now)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val meta = when {
                        s.refreshing -> stringResource(R.string.pi_asking)
                        ago.isNotEmpty() -> stringResource(R.string.pi_list_updated, ago)
                        else -> stringResource(R.string.pi_no_list)
                    }
                    Txt(meta, 12, 400, C.Soft, Modifier.weight(1f), maxLines = 3)
                    LinkText(stringResource(R.string.pi_refresh), enabled = !s.refreshing && s.canImport && online, onClick = vm::refresh)
                }
                if (s.refreshLate) Txt(stringResource(R.string.pi_refresh_late), 12, 400, C.Muted, maxLines = 3)
                s.places?.push?.let { LastPush(it, now) }
            }
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

/** The latest send to VDMS: still running, or how many were added, updated and skipped (the skipped ones on demand). */
@Composable
private fun LastPush(push: PlacesPush, now: Long) {
    if (push.active) {
        val ago = agoText(push.requestedAt, now).ifEmpty { stringResource(R.string.val_ago_now) }
        Txt(stringResource(R.string.pi_push_running, ago), 12, 500, C.Soft, Modifier.testTag("places_push_running"), maxLines = 3)
        return
    }
    val r = push.result
    val ago = agoText(push.finishedAt ?: push.requestedAt, now)
    val added = r.imported.size
    val updated = r.updated.size
    val skipped = r.skipped.size
    val line = when {
        updated > 0 && skipped > 0 -> stringResource(R.string.pi_last_push_both, ago, added, updated, skipped)
        updated > 0 -> stringResource(R.string.pi_last_push_updated, ago, added, updated)
        skipped > 0 -> stringResource(R.string.pi_last_push_skipped, ago, added, skipped)
        else -> stringResource(R.string.pi_last_push, ago, added)
    }
    var showSkipped by rememberSaveable(push.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Txt(line, 12, 500, C.Soft, maxLines = 3)
        if (skipped > 0) {
            LinkText(stringResource(if (showSkipped) R.string.pi_hide_skipped else R.string.pi_show_skipped)) { showSkipped = !showSkipped }
            if (showSkipped) r.skipped.forEach { SkippedLine(skippedText(it, PlacesDirection.TO_VDMS).asString()) }
        }
    }
}

/** A skipped place, with a warning mark. */
@Composable
internal fun SkippedLine(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        LineIcon(Icons.Alert, 14.dp, C.Orange, 2.2f, Modifier.padding(top = 1.dp))
        Txt(text, 12, 400, C.Soft, Modifier.weight(1f), maxLines = 3)
    }
}

/** A text button in the card's or the sheet's lines (Refresh, Try again, Select all…), with a full-size touch target. */
@Composable
internal fun LinkText(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 48.dp).clip(r(8)).clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Txt(text, 12, 700, if (enabled) C.Yellow else C.Muted) }
}

private const val AGO_TICK_MS = 30_000L
