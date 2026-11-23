package com.sclera.sclerago.presentation.property

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.PropertySummary
import com.sclera.sclerago.domain.model.inProgress
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.components.BottomSheet
import com.sclera.sclerago.presentation.components.CloseButton
import com.sclera.sclerago.presentation.components.RadioDot
import com.sclera.sclerago.presentation.components.SearchField
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.main.AppViewModel
import com.sclera.sclerago.presentation.model.OnlineOnly
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** Test tags of the Select property sheet. */
object PropertySheetTags {
    const val SHEET = "property_sheet"
    const val LOCATION = "property_sheet_location"
    const val SEARCH = "property_sheet_search"
    const val EMPTY = "property_sheet_empty"
    fun row(id: String) = "property_sheet_row_$id"
}

/**
 * The dashboard header's property dropdown: pick the property to work on. A downloaded property becomes the current
 * one (as in the Property list); another is downloaded first, and offline it can't be used.
 */
@Composable
fun PropertySheet(
    app: AppViewModel,
    onClose: () -> Unit,
    vm: PropertyViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { onDispose(vm::reset) }
    val notice = state.notice?.asString()
    LaunchedEffect(notice) {
        if (notice != null) {
            app.flash(notice)
            vm.consumeNotice()
        }
    }
    PropertySheetContent(
        state, app.currentProp, onClose, vm::setQuery,
        onPick = { id ->
            app.openPropertyFromList(id)
            onClose()
        },
        onDownload = vm::download,
        onOffline = { vm.explainOffline(OnlineOnly.DOWNLOAD_PROPERTY) },
    )
}

/** The sheet itself, stateless. */
@Composable
internal fun PropertySheetContent(
    state: PropertyListState,
    currentId: String,
    onClose: () -> Unit,
    onQuery: (String) -> Unit,
    onPick: (String) -> Unit,
    onDownload: (String) -> Unit,
    onOffline: () -> Unit,
) {
    BottomSheet(onClose, maxHeightFraction = 0.8f, background = C.SheetBg) {
        Column(Modifier.testTag(PropertySheetTags.SHEET).padding(horizontal = 16.dp).padding(top = 6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Txt(stringResource(R.string.psheet_title), 22, 700, modifier = Modifier.weight(1f))
                CloseButton(onClose)
            }
            Box(Modifier.padding(top = 12.dp)) { LocationCard() }
            SearchField(
                state.query, onQuery, stringResource(R.string.property_list_search_hint),
                Modifier.padding(top = 12.dp, bottom = 12.dp).testTag(PropertySheetTags.SEARCH),
            )
        }
        if (state.rows.isEmpty() && !state.loading) {
            val text = if (state.query.isBlank()) stringResource(R.string.property_none)
            else stringResource(R.string.property_none_match, state.query.trim())
            Txt(
                text, 13, 400, C.Muted,
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp).testTag(PropertySheetTags.EMPTY),
                maxLines = Int.MAX_VALUE, align = TextAlign.Center,
            )
        }
        LazyColumn(
            Modifier.weight(1f, fill = false),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(state.rows, key = { it.property.id }) { row ->
                val id = row.property.id
                val download = state.downloads.active[id]
                val downloaded = id in state.downloads.downloadedIds
                val unavailable = !state.online && !downloaded && !download.inProgress
                PropertyOption(
                    row, current = id == currentId, downloaded = downloaded, downloading = download.inProgress,
                    unavailable = unavailable,
                    onClick = when {
                        unavailable -> onOffline
                        downloaded -> ({ onPick(id) })
                        download.inProgress -> ({})
                        else -> ({ onDownload(id) })
                    },
                )
            }
        }
    }
}

/**
 * "Use my location": picks the property nearest the phone. The app has no property coordinates or phone-position
 * reading yet, so the card is shown but disabled, with the reason.
 */
@Composable
private fun LocationCard() {
    Row(
        Modifier.fillMaxWidth().testTag(PropertySheetTags.LOCATION).alpha(0.6f).clip(r(14)).background(C.YTintBg)
            .border(1.dp, C.YTintBd, r(14)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(r(12)).background(C.Yellow), contentAlignment = Alignment.Center) {
            LineIcon(Icons.Crosshair, 20.dp, C.OnYellow, 2.4f)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(stringResource(R.string.psheet_use_location), 15, 700)
            Txt(stringResource(R.string.psheet_location_unavailable), 12, 400, C.Muted, maxLines = 3)
        }
    }
}

/** A property: building, name and assets, a GPS badge when it has a geofence, then the pick mark (or its download state). */
@Composable
private fun PropertyOption(
    row: PropertySummary,
    current: Boolean,
    downloaded: Boolean,
    downloading: Boolean,
    unavailable: Boolean,
    onClick: () -> Unit,
) {
    val p = row.property
    val currentLabel = stringResource(R.string.menu_current_property)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(r(12)).background(if (current) C.YTintBg else C.SheetBg)
            .alpha(if (unavailable) 0.26f else 1f)
            .semantics {
                selected = current
                if (current) contentDescription = "${p.name}, $currentLabel"
            }
            .testTag(PropertySheetTags.row(p.id)).clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).clip(r(10)).background(C.Raised), contentAlignment = Alignment.Center) {
            LineIcon(Icons.Building, 16.dp, if (current) C.Yellow else C.Muted, 2.3f)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(p.name, 15, if (current) 700 else 500, if (current) C.Yellow else C.Text)
            Txt(
                if (row.assets > 0) pluralStringResource(R.plurals.asset_count, row.assets, row.assets)
                else stringResource(R.string.psheet_no_assets),
                12, 400, C.Muted,
            )
        }
        if (p.gps) {
            Txt(
                stringResource(R.string.psheet_gps_badge), 10, 700, C.Yellow,
                Modifier.clip(r(6)).background(C.YTintBg).border(1.dp, C.YTintBd, r(6)).padding(horizontal = 6.dp, vertical = 2.dp),
                spacing = 0.4f,
            )
        }
        when {
            downloading -> CircularProgressIndicator(Modifier.size(22.dp), color = C.Yellow, strokeWidth = 2.5.dp)
            downloaded -> RadioDot(current)
            else -> {
                val label = stringResource(R.string.property_download, p.name)
                Box(Modifier.size(22.dp).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
                    LineIcon(Icons.Download, 20.dp, C.Yellow, 2.2f)
                }
            }
        }
    }
}
