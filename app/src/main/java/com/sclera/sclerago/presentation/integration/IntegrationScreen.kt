package com.sclera.sclerago.presentation.integration

import com.sclera.sclerago.presentation.components.ToastEffect
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.VdmsChoice
import com.sclera.sclerago.presentation.common.ConnectivityViewModel
import com.sclera.sclerago.presentation.components.BottomSheet
import com.sclera.sclerago.presentation.components.ChoiceList
import com.sclera.sclerago.presentation.components.CircleFab
import com.sclera.sclerago.presentation.components.ConfirmDialog
import com.sclera.sclerago.presentation.components.EmptyNote
import com.sclera.sclerago.presentation.components.GuardedClose
import com.sclera.sclerago.presentation.components.ListLoader
import com.sclera.sclerago.presentation.components.OfflineBanner
import com.sclera.sclerago.presentation.components.OfflineGate
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.SearchField
import com.sclera.sclerago.presentation.components.SheetHeader
import com.sclera.sclerago.presentation.components.SubPageBar
import com.sclera.sclerago.presentation.components.pageGutter
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.model.OnlineOnly
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/**
 * The Integration page (side menu → Integration), the same as the web's: a floating + to add an integration (only while
 * the property has no link), and the link's card (logo, organisation name, info → View Integration). A link can't be changed here.
 * Once linked, the Buildings, floors & locations card sends places to VDMS or imports them from it ([PlacesCard]).
 */
@Composable
fun IntegrationScreen(
    propertyId: String,
    propName: String,
    onClose: () -> Unit,
    /** The header's menu button: opens the side menu. */
    onMenu: () -> Unit = {},
    vm: IntegrationViewModel = hiltViewModel(),
    places: PlacesImportViewModel = hiltViewModel(),
    connectivity: ConnectivityViewModel = hiltViewModel(),
) {
    LaunchedEffect(propertyId) { vm.bind(propertyId) }
    val s = vm.state
    // Buildings, floors and locations are read once the property is known to be linked.
    val linkId = s.link?.id
    LaunchedEffect(propertyId, linkId) { if (linkId != null) places.bind(propertyId) }
    val p = places.state
    val online by connectivity.online.collectAsStateWithLifecycle()
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(C.Bg).testTag("integration_page")) {
        Column(Modifier.fillMaxSize()) {
            SubPageBar(stringResource(R.string.int_title), propName, onMenu)
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = pageGutter())
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 60.dp), // room for the floating +
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // The link is read and made on the server: offline the page says so (what was last read still shows).
                if (!online) OfflineBanner(OnlineOnly.INTEGRATION)
                when {
                    s.loading -> ListLoader(stringResource(R.string.int_loading))
                    s.link == null -> EmptyNote(stringResource(R.string.int_none))
                    else -> {
                        LinkCard(vm.nameOf(s.link), s.link.vdmsId)
                        PlacesCard(p, online, places)
                    }
                }
            }
        }
        // Add Integration: the floating + as on the other pages. Always there, as the web's button; greyed out and inert
        // once the property is linked, while loading, or for the "user" role.
        val canAdd = s.link == null && !s.loading && s.canAdd
        OfflineGate(
            !online && canAdd, { vm.explainOffline(OnlineOnly.INTEGRATION) },
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 14.dp, bottom = 14.dp),
        ) {
            CircleFab(
                Icons.Plus, stringResource(R.string.int_add_title), { if (canAdd) vm.startAdding() },
                Modifier.alpha(if (canAdd) 1f else 0.4f).testTag("integration_add"),
            )
        }
        s.adding?.let { a -> AddSheet(a, vm.nameOf(null), vm) }
        p.sheet?.let { sh -> PlacesImportSheet(sh, p, places) }
        if (p.historyOpen) PlacesHistorySheet(propertyId, places::closeHistory)
        ToastEffect(s.message)
        ToastEffect(p.message)
    }
}

/** The link's card: the Sclera logo, then the integration (the organisation) and its VDMS, shown in the card itself. */
@Composable
private fun LinkCard(name: String, vdmsId: String) {
    Column(
        Modifier.fillMaxWidth().clip(r(14)).background(C.Surface).border(1.dp, C.Border, r(14)).padding(14.dp).testTag("integration_card"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(painterResource(R.drawable.login_logo), contentDescription = null, modifier = Modifier.height(34.dp).aspectRatio(512f / 357f))
        ReadOnly(stringResource(R.string.int_field_integration), name, inline = true)
        ReadOnly(stringResource(R.string.int_field_vdms), vdmsId, inline = true)
    }
}

/** A read-only value in a bordered box; [inline] puts the label to its left (the link card), else above it. */
@Composable
private fun ReadOnly(label: String, value: String, inline: Boolean = false) {
    val box: @Composable (Modifier) -> Unit = { m ->
        Box(m.fillMaxWidth().heightIn(min = 40.dp).clip(r(10)).background(C.Bg).border(1.dp, C.Border2, r(10)).padding(horizontal = 12.dp, vertical = 11.dp), contentAlignment = Alignment.CenterStart) {
            if (inline) Txt(value.ifBlank { "—" }, 14, 600, maxLines = Int.MAX_VALUE) else Txt(value.ifBlank { "—" }, 13, 500, C.Soft, maxLines = Int.MAX_VALUE)
        }
    }
    if (inline) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Txt(label, 13, 400, C.Muted, Modifier.width(90.dp), maxLines = 2)
            box(Modifier.weight(1f))
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Txt(label, 12, 600, C.Muted)
            box(Modifier)
        }
    }
}

/** Add Integration, as the web's: the organisation (fixed), a VDMS picked from the ones not linked yet, then Done → confirm → Link. */
@Composable
private fun AddSheet(a: AddLinkState, organisation: String, vm: IntegrationViewModel) {
    GuardedClose(a.picked != null, vm::closeAdding) { close ->
        BottomSheet(close) {
            SheetHeader(stringResource(R.string.int_add_title), null, close)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ReadOnly(stringResource(R.string.int_field_integration), organisation)
                Txt(stringResource(R.string.int_field_vdms), 12, 600, C.Muted)
                SearchField(a.query, vm::setQuery, stringResource(R.string.int_select))
                when {
                    a.searching -> Txt(stringResource(R.string.int_loading), 12, 400, C.Muted, Modifier.padding(8.dp))
                    a.choices.isEmpty() -> Txt(stringResource(R.string.int_no_options), 12, 400, C.Muted, Modifier.padding(8.dp), maxLines = Int.MAX_VALUE)
                }
                ChoiceList(a.choices, { it.label() }, { it.vdmsId == a.picked?.vdmsId }, vm::pick)
            }
            Column(Modifier.fillMaxWidth().topBorder(C.Border).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth().clip(r(10)).background(C.WarnRow).padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LineIcon(Icons.Alert, 15.dp, C.Orange, 2.2f)
                    Txt(stringResource(R.string.int_once_note), 12, 500, C.Soft, Modifier.weight(1f), maxLines = Int.MAX_VALUE)
                }
                val ok = a.picked != null
                Pill(
                    stringResource(R.string.int_done), { if (ok) vm.askConfirm() },
                    Modifier.fillMaxWidth().alpha(if (ok) 1f else 0.4f), bg = C.Yellow, fg = C.OnYellow, height = 44.dp, weight = 700,
                )
            }
        }
        if (a.confirm) a.picked?.let { p ->
            ConfirmDialog(
                icon = Icons.Link, iconBg = C.YTintBg, iconFg = C.Yellow,
                title = stringResource(R.string.int_confirm_title, p.vdmsId),
                body = stringResource(R.string.int_confirm_body),
                no = stringResource(R.string.int_cancel), yes = stringResource(R.string.int_link),
                onNo = vm::cancelConfirm, onYes = vm::link, yesBg = C.Yellow,
            )
        }
    }
}

/** The web's option label: "property name - VDMS id" (just the id when the VDMS has no property name). */
private fun VdmsChoice.label(): String = propertyName?.let { "$it - $vdmsId" } ?: vdmsId
