package com.sclera.sclerago.presentation.integration

import com.sclera.sclerago.presentation.common.formatDateAndTime
import com.sclera.sclerago.presentation.common.is24Hour
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.PlaceOutcome
import com.sclera.sclerago.domain.model.PlaceSkipReason
import com.sclera.sclerago.domain.model.PlacesDirection
import com.sclera.sclerago.domain.model.PlacesTransfer
import com.sclera.sclerago.domain.model.PlacesTransferDetail
import com.sclera.sclerago.domain.model.TransferStatus
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.components.BottomSheet
import com.sclera.sclerago.presentation.components.ListLoader
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.PersonAvatar
import com.sclera.sclerago.presentation.components.Segmented
import com.sclera.sclerago.presentation.components.SheetHeader
import com.sclera.sclerago.presentation.components.TintChip
import com.sclera.sclerago.presentation.components.ToastEffect
import com.sclera.sclerago.presentation.components.agoText
import com.sclera.sclerago.presentation.components.initialsOf
import com.sclera.sclerago.presentation.components.personTint
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/**
 * Buildings, floors & locations history (Integration page → History), as the web's: every send to VDMS and import from
 * it, newest first, narrowed by direction, a page at a time. Each is a card (when, which way, who, how it went, and how
 * many were added, updated and skipped) that opens to list its places. Docked on phones, centred on tablets.
 */
@Composable
internal fun PlacesHistorySheet(propertyId: String, onClose: () -> Unit, vm: PlacesHistoryViewModel = hiltViewModel()) {
    LaunchedEffect(propertyId) { vm.start(propertyId) }
    DisposableEffect(Unit) { onDispose { vm.stop() } }
    val s = vm.state
    BottomSheet(onClose) {
        SheetHeader(stringResource(R.string.ph_title), null, onClose)
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp).testTag("places_history_sheet")) {
            val filters = listOf(null, PlacesDirection.TO_VDMS, PlacesDirection.FROM_VDMS)
            Segmented(
                listOf(stringResource(R.string.ph_filter_all), stringResource(R.string.ph_filter_sent), stringResource(R.string.ph_filter_imported)),
                filters.indexOf(s.filter), { vm.setFilter(filters[it]) }, modifier = Modifier.fillMaxWidth(),
            )
            Txt(
                if (s.loading) stringResource(R.string.int_loading) else pluralStringResource(R.plurals.ph_total, s.total, s.total),
                12, 500, C.Muted, Modifier.padding(vertical = 8.dp),
            )
            when {
                s.loading && s.items.isEmpty() -> ListLoader(stringResource(R.string.int_loading))
                s.failed && s.items.isEmpty() -> Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Txt(stringResource(R.string.ph_load_failed), 13, 400, C.Muted, maxLines = Int.MAX_VALUE)
                    LinkText(stringResource(R.string.pi_try_again), onClick = { vm.load() })
                }
                s.items.isEmpty() -> Txt(
                    stringResource(
                        when (s.filter) {
                            PlacesDirection.TO_VDMS -> R.string.ph_empty_sent
                            PlacesDirection.FROM_VDMS -> R.string.ph_empty_imported
                            null -> R.string.ph_empty
                        },
                    ),
                    13, 400, C.Muted, Modifier.padding(vertical = 16.dp), maxLines = Int.MAX_VALUE,
                )
                else -> LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(s.items, key = { it.id }) { t -> TransferCard(t, s.open == t.id, s.details[t.id], vm) }
                    val left = s.total - s.items.size
                    if (left > 0) item {
                        Pill(
                            if (s.loadingMore) stringResource(R.string.int_loading) else stringResource(R.string.ph_show_more, left),
                            vm::loadMore, Modifier.fillMaxWidth().testTag("places_history_more"), height = 48.dp,
                        )
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().topBorder(C.Border).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Pill(stringResource(R.string.ph_close), onClose, Modifier.fillMaxWidth(), height = 48.dp, weight = 700)
        }
    }
    ToastEffect(s.message)
}

/** One transfer: tapping it shows or hides its places. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TransferCard(t: PlacesTransfer, open: Boolean, detail: TransferDetailState?, vm: PlacesHistoryViewModel) {
    val rot by animateFloatAsState(if (open) 90f else 0f, tween(200), label = "places_history")
    val openState = stringResource(if (open) R.string.ph_expanded else R.string.ph_collapsed)
    Column(Modifier.fillMaxWidth().clip(r(14)).background(C.Surface).border(1.dp, C.Border, r(14)).testTag("places_transfer")) {
        Column(
            Modifier.fillMaxWidth().clickable(role = Role.Button) { vm.toggle(t.id) }.semantics { stateDescription = openState }
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LineIcon(Icons.ChevronRight, 14.dp, C.Muted, 2.6f, Modifier.rotate(rot))
                Column(Modifier.weight(1f)) {
                    Txt(agoText(t.createdAt).ifEmpty { exactTime(t.createdAt) }, 14, 700)
                    exactTime(t.createdAt).takeIf { it.isNotEmpty() }?.let { Txt(it, 11, 400, C.Muted) }
                }
                StatusPill(t)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                DirectionChip(t.direction)
                if (t.forMove) TintChip(stringResource(R.string.ph_with_move), bg = C.Raised, fg = C.Soft)
                val by = t.byName ?: stringResource(if (t.forMove) R.string.ph_by_move else R.string.ph_by_someone)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PersonAvatar(initialsOf(by.substringBefore('@')), personTint(by), size = 22.dp, textSize = 9)
                    Txt(by, 12, 500, C.Soft)
                }
            }
            Counts(t)
        }
        if (open) Column(Modifier.fillMaxWidth().topBorder(C.Border).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (detail) {
                null, TransferDetailState.Loading -> Txt(stringResource(R.string.ph_detail_loading), 12, 400, C.Muted)
                TransferDetailState.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt(stringResource(R.string.ph_detail_failed), 12, 400, C.Muted, Modifier.weight(1f), maxLines = Int.MAX_VALUE)
                    LinkText(stringResource(R.string.pi_try_again)) { vm.retryDetail(t.id) }
                }
                is TransferDetailState.Loaded -> DetailSections(t, detail.detail)
            }
        }
    }
}

/** "→ To VDMS" or "← From VDMS". */
@Composable
private fun DirectionChip(direction: PlacesDirection) {
    val toVdms = direction == PlacesDirection.TO_VDMS
    val color = if (toVdms) C.Blue else C.Green
    Row(
        Modifier.clip(CircleShape).background(color.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LineIcon(Icons.ArrowRight, 12.dp, color, 2.4f, if (toVdms) Modifier else Modifier.rotate(180f))
        Txt(stringResource(if (toVdms) R.string.ph_to_vdms else R.string.ph_from_vdms), 11, 600, color)
    }
}

/** How it went, as the web words it: Done, Skipped (nothing added or updated), Waiting for VDMS, VDMS is adding, No reply. */
@Composable
private fun StatusPill(t: PlacesTransfer) {
    val (label, color) = when (t.status) {
        TransferStatus.DONE ->
            if (t.counts.skipped > 0 && t.counts.imported + t.counts.updated == 0) R.string.ph_status_skipped to C.Orange
            else R.string.ph_status_done to C.Green
        TransferStatus.PENDING -> R.string.ph_status_pending to C.Yellow
        TransferStatus.RUNNING -> R.string.ph_status_running to C.Yellow
        TransferStatus.NO_REPLY -> R.string.ph_status_no_reply to C.Red
        TransferStatus.OTHER -> R.string.ph_status_other to C.Muted
    }
    TintChip(stringResource(label), bg = color.copy(alpha = 0.15f), fg = color)
}

/** Added (for a send VDMS hasn't reported on: how many were sent), updated and skipped. */
@Composable
private fun Counts(t: PlacesTransfer) {
    val unreported = t.direction == PlacesDirection.TO_VDMS && t.status != TransferStatus.DONE
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (unreported) Count(stringResource(R.string.ph_count_sent), t.counts.sending, C.Yellow)
        else Count(stringResource(R.string.ph_count_added), t.counts.imported, C.Green)
        Count(stringResource(R.string.ph_count_updated), t.counts.updated, C.Green)
        Count(stringResource(R.string.ph_count_skipped), t.counts.skipped, C.Orange)
    }
}

@Composable
private fun Count(label: String, value: Int, tone: Color) {
    Column {
        Txt(value.toString(), 15, 700, if (value > 0) tone else C.Dim)
        Txt(label, 11, 500, C.Muted)
    }
}

/** The transfer's places by section, empty ones left out; what was sent shows only until VDMS reports. */
@Composable
private fun DetailSections(t: PlacesTransfer, d: PlacesTransferDetail) {
    val toVdms = t.direction == PlacesDirection.TO_VDMS
    val sections = buildList {
        if (toVdms && t.status != TransferStatus.DONE && d.sending.isNotEmpty()) add(Triple(R.string.ph_section_sending, C.Yellow, d.sending))
        if (d.result.imported.isNotEmpty()) add(Triple(if (toVdms) R.string.ph_section_added_vdms else R.string.ph_section_added_here, C.Green, d.result.imported))
        if (d.result.updated.isNotEmpty()) add(Triple(if (toVdms) R.string.ph_section_updated_vdms else R.string.ph_section_updated_here, C.Green, d.result.updated))
        if (d.result.skipped.isNotEmpty()) add(Triple(R.string.ph_section_skipped, C.Orange, d.result.skipped))
    }
    if (t.status == TransferStatus.NO_REPLY) Txt(stringResource(R.string.ph_no_reply_note), 12, 400, C.Soft, maxLines = Int.MAX_VALUE)
    if (sections.isEmpty()) {
        Txt(stringResource(R.string.ph_nothing), 12, 400, C.Muted, maxLines = Int.MAX_VALUE)
        return
    }
    sections.forEach { (title, tone, places) ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Txt(stringResource(title), 12, 700, tone)
                Txt(places.size.toString(), 11, 600, C.Muted)
            }
            places.forEach { PlaceLine(it) }
        }
    }
}

/** "Location · Lobby · already there". */
@Composable
private fun PlaceLine(o: PlaceOutcome) {
    Row(Modifier.heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Txt(kindLabel(o.kind).asString(), 11, 600, C.Muted)
        Txt(o.name ?: o.id, 12, 500, C.Text, Modifier.weight(1f), maxLines = 2)
        val note = when {
            o.update -> stringResource(R.string.ph_reason_update)
            o.reason != null -> stringResource(
                when (o.reason) {
                    PlaceSkipReason.EXISTS -> R.string.ph_reason_exists
                    PlaceSkipReason.PARENT_MISSING -> R.string.ph_reason_parent
                    PlaceSkipReason.NOT_FOUND -> R.string.ph_reason_not_found
                    PlaceSkipReason.ERROR -> R.string.ph_reason_error
                    PlaceSkipReason.OTHER -> R.string.ph_reason_other
                },
            )
            else -> null
        }
        note?.let { Txt(it, 11, 400, C.Muted, maxLines = Int.MAX_VALUE) }
    }
}

/** "20 Nov 2026, 14:05" in the device's language and clock; "" when unknown. */
@Composable
private fun exactTime(at: Long?): String {
    if (at == null) return ""
    val locale = LocalConfiguration.current.locales[0]
    return formatDateAndTime(at, locale, use24Hour = is24Hour())
}
