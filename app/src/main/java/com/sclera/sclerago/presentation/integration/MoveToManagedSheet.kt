package com.sclera.sclerago.presentation.integration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.MovePreview
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.components.SkeletonLead
import com.sclera.sclerago.presentation.components.SkeletonList
import com.sclera.sclerago.presentation.components.AccessGate
import com.sclera.sclerago.presentation.components.BottomSheet
import com.sclera.sclerago.presentation.components.ChoiceList
import com.sclera.sclerago.presentation.components.GuardedClose
import com.sclera.sclerago.presentation.components.LoadFailedNote
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.SearchField
import com.sclera.sclerago.presentation.components.SheetHeader
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/**
 * Move to managed assets, as the web's: the network name (required, from the linked VDMS), the assignee (optional,
 * searched by email), then Submit.
 */
@Composable
fun MoveToManagedSheet(
    networks: List<String>,
    s: MoveSheetState,
    vm: MoveToManagedViewModel,
    onSubmitted: () -> Unit,
    /** The networks are still being fetched: placeholder rows instead of "No options". */
    networksLoading: Boolean = false,
    /** The networks couldn't be read: says so, with Try again, instead of "No options". */
    networksFailed: Boolean = false,
) {
    GuardedClose(s.network != null || s.assignee != null, vm::close) { close ->
        BottomSheet(close) {
            SheetHeader(stringResource(R.string.mv_title), null, close, titleSize = 20)
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).testTag("move_sheet"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Txt(stringResource(R.string.mv_network_label), 12, 600, C.Muted)
                if (networks.isEmpty()) {
                    when {
                        networksLoading -> SkeletonList(stringResource(R.string.int_loading), count = 2, lead = SkeletonLead.NONE, lines = 1, card = false, gap = 2.dp)
                        networksFailed -> LoadFailedNote(stringResource(R.string.mv_networks_failed), vm::retryNetworks)
                        else -> Txt(stringResource(R.string.int_no_options), 12, 400, C.Muted, Modifier.padding(8.dp), maxLines = Int.MAX_VALUE)
                    }
                }
                // Searched by name on the phone; the one picked stays in the list.
                var networkQuery by rememberSaveable { mutableStateOf("") }
                val q = networkQuery.trim()
                val shownNetworks = if (q.isEmpty()) networks else networks.filter { it.contains(q, ignoreCase = true) || it == s.network }
                if (networks.isNotEmpty()) {
                    SearchField(networkQuery, { networkQuery = it }, stringResource(R.string.mv_search_networks), Modifier.testTag("move_network_search"))
                }
                // The web shows each name capitalised and sends it as it is.
                ChoiceList(shownNetworks, { n -> n.replaceFirstChar { it.uppercaseChar() } }, { it == s.network }, vm::pickNetwork)
                if (networks.isNotEmpty() && shownNetworks.isEmpty()) {
                    Txt(stringResource(R.string.cfgt_no_match), 12, 400, C.Muted, Modifier.padding(8.dp), maxLines = Int.MAX_VALUE)
                }

                Txt(stringResource(R.string.mv_assignee_label), 12, 600, C.Muted, Modifier.padding(top = 6.dp))
                SearchField(s.query, vm::setQuery, stringResource(R.string.int_select))
                // While a search runs, the last search's people (which may not match any more) give way to placeholders;
                // the one picked stays.
                val shown = if (s.searching) s.assignees.filter { it.email == s.assignee } else s.assignees
                ChoiceList(shown, { it.email }, { it.email == s.assignee }, onPick = { vm.pickAssignee(it.email) })
                when {
                    s.searching -> SkeletonList(stringResource(R.string.int_loading), count = 3, lead = SkeletonLead.NONE, lines = 1, card = false, gap = 2.dp)
                    s.assignees.isEmpty() -> Txt(stringResource(R.string.int_no_options), 12, 400, C.Muted, Modifier.padding(8.dp), maxLines = Int.MAX_VALUE)
                }
            }
            Column(Modifier.fillMaxWidth().topBorder(C.Border).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MovePlacesNote(s.preview)
                val ok = s.network != null
                // Moving needs Configure: should the access change while the sheet is open, Submit is greyed out with why.
                AccessGate(vm.state.access, Modifier.fillMaxWidth()) {
                    Pill(
                        stringResource(R.string.mv_submit), { if (vm.submit()) onSubmitted() },
                        Modifier.fillMaxWidth().alpha(if (ok) 1f else 0.4f), bg = C.Yellow, fg = C.OnYellow, height = 44.dp, weight = 700,
                    )
                }
            }
        }
    }
}

/** The buildings, floors and locations VDMS lacks, added there before the assets (or that an earlier send is still running). */
@Composable
private fun MovePlacesNote(preview: MovePreview?) {
    val places = preview?.let { placeCountsText(it.places) } ?: return
    val text = if (preview.placesInProgress) stringResource(R.string.mv_places_busy)
    else stringResource(R.string.mv_places_first, places.asString())
    Row(
        Modifier.fillMaxWidth().clip(r(10)).background(C.WarnRow).padding(10.dp).testTag("move_places_note"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineIcon(Icons.Info, 15.dp, C.Orange, 2.2f)
        Txt(text, 12, 500, C.Soft, Modifier.weight(1f), maxLines = Int.MAX_VALUE)
    }
}
