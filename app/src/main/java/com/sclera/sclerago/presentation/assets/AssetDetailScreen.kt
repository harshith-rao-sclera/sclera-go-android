package com.sclera.sclerago.presentation.assets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.withFrameNanos
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.validation.AssetCheck
import com.sclera.sclerago.domain.validation.FixTarget
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.presentation.components.ConfirmDialog
import com.sclera.sclerago.presentation.components.LocalReadOnly
import com.sclera.sclerago.presentation.components.PlainField
import com.sclera.sclerago.presentation.components.RoundButton
import com.sclera.sclerago.presentation.components.bottomBorder
import com.sclera.sclerago.presentation.components.pageGutter
import com.sclera.sclerago.presentation.components.readableWidth
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.model.chipBg
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/**
 * One asset details screen: its name, the Info and Validation tabs, the floating AI / voice buttons and the
 * confirmations. Stateless apart from what is on screen (tab, open sections, dialogs); the data and
 * what the buttons do come in as [state] and [actions].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AssetDetailScreen(state: AssetDetailState, actions: AssetDetailActions, modifier: Modifier = Modifier) {
    val asset = state.asset ?: return
    val isNew = state.isNew
    val locked = state.locked
    var tab by rememberSaveable(asset.id) { mutableIntStateOf(0) }
    var modal by remember(asset.id) { mutableStateOf(DetailModal.NONE) }
    var preview by remember(asset.id) { mutableStateOf<Int?>(null) }
    val ui = remember(asset.id) { FormUi() }
    val scroll = rememberScrollState()

    // Each tab opens at its top, unless a Fix button is taking the user to a field.
    LaunchedEffect(tab) { if (ui.pending == null) scroll.scrollTo(0) }
    LaunchedEffect(ui.pending, tab) {
        val key = ui.pending ?: return@LaunchedEffect
        if (tab != 0) return@LaunchedEffect
        withFrameNanos { } // let the Info tab and the opened section lay out first
        withFrameNanos { }
        ui.requester(key).bringIntoView()
        ui.pending = null
    }

    fun fix(check: AssetCheck) {
        if (locked) return
        when (val target = check.fix) {
            null -> {}
            FixTarget.LOCATION -> actions.onChangeLoc()
            FixTarget.PHOTOS -> actions.onTakePhoto()
            else -> {
                val section = target.section ?: return
                ui.collapsed = ui.collapsed - section
                ui.pending = check.fixField ?: section
                tab = 0
            }
        }
    }

    fun back() {
        if (isNew) modal = DetailModal.DISCARD else actions.onBack()
    }

    BackHandler {
        when {
            modal != DetailModal.NONE -> modal = DetailModal.NONE
            preview != null -> preview = null
            else -> back()
        }
    }

    Box(modifier.fillMaxSize().background(C.Bg)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(state, onBack = ::back, onName = { actions.onEdit("name", it) })
            if (!isNew) {
                Row(Modifier.fillMaxWidth().height(48.dp).background(C.Strip).bottomBorder(C.Border)) {
                    DetailTab(stringResource(R.string.detail_tab_info), Icons.Info, tab == 0, null, Modifier.weight(1f).testTag(DetailTags.TAB_INFO)) { tab = 0 }
                    DetailTab(
                        stringResource(R.string.detail_tab_validation), Icons.ShieldCheck, tab == 1, state.checks?.failed,
                        Modifier.weight(1f).testTag(DetailTags.TAB_VALIDATION),
                    ) { tab = 1 }
                }
            }
            CompositionLocalProvider(LocalReadOnly provides locked) {
                Column(
                    // Room at the end so the last card scrolls clear of the AI and mic buttons.
                    Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = pageGutter()).padding(top = 12.dp, bottom = 160.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val checks = state.checks
                    if (tab == 1 && !isNew && checks != null) {
                        AssetChecksPanel(checks, onFix = if (locked) null else ::fix, onValidate = actions.onValidate)
                    } else {
                        AssetInfoTab(state, actions, ui, onModal = { modal = it }, onPreview = { preview = it })
                    }
                }
            }
        }

        if ((isNew || tab == 0) && !locked) {
            Box(Modifier.align(Alignment.BottomCenter).readableWidth()) {
                FormFloatingButtons(
                    aiBusy = state.aiBusy, aiNotice = state.aiNotice, listening = ui.listening,
                    onAi = actions.onAutofill, onMic = { ui.listening = !ui.listening },
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }

        preview?.let { i -> if (i in state.media.indices) PhotoViewer(state.media, i, onIndex = { preview = it }, onClose = { preview = null }) }

        when (modal) {
            DetailModal.SUBMIT -> ConfirmDialog(
                icon = Icons.CheckCircle, iconBg = Status.SUBMITTED.chipBg, iconFg = C.Green,
                title = stringResource(R.string.detail_submit_title),
                body = stringResource(R.string.detail_submit_body),
                no = stringResource(R.string.action_keep_editing), yes = stringResource(R.string.action_submit), yesBg = C.Yellow,
                onNo = { modal = DetailModal.NONE },
                onYes = {
                    modal = DetailModal.NONE
                    if (isNew) actions.onCreate(true) else actions.onSubmit()
                },
            )
            DetailModal.DELETE -> ConfirmDialog(
                icon = Icons.Trash, iconBg = C.DangerBg, iconFg = C.Red,
                title = stringResource(R.string.detail_delete_title),
                body = state.value("name").ifEmpty { null }?.let { stringResource(R.string.detail_delete_body, it) }
                    ?: stringResource(R.string.detail_delete_body_unnamed),
                no = stringResource(R.string.action_cancel), yes = stringResource(R.string.action_delete),
                onNo = { modal = DetailModal.NONE },
                onYes = {
                    modal = DetailModal.NONE
                    actions.onDelete()
                },
            )
            DetailModal.DISCARD -> ConfirmDialog(
                icon = Icons.Alert, iconBg = C.WarnRow, iconFg = C.Orange,
                title = stringResource(R.string.detail_discard_title),
                body = stringResource(R.string.detail_discard_body),
                no = stringResource(R.string.action_keep_editing), yes = stringResource(R.string.action_discard),
                onNo = { modal = DetailModal.NONE },
                onYes = {
                    modal = DetailModal.NONE
                    actions.onBack()
                },
            )
            DetailModal.NONE -> {}
        }
    }
}

@Composable
private fun TopBar(state: AssetDetailState, onBack: () -> Unit, onName: (String) -> Unit) {
    val name = state.value("name")
    val headLine = when {
        state.isNew -> if (name.isNotEmpty()) C.Border2 else C.Yellow
        state.dirty("name") -> C.Yellow
        else -> C.Border2
    }
    val backLabel = stringResource(R.string.detail_back)
    Row(
        Modifier.fillMaxWidth().height(56.dp).background(C.Bg).bottomBorder(C.Border).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RoundButton(48.dp, Color.Transparent, onBack, Modifier.semantics { contentDescription = backLabel }) {
            LineIcon(Icons.ChevronLeft, 19.dp, C.Yellow, 3f)
        }
        PlainField(
            name, onName, stringResource(R.string.detail_name_hint),
            Modifier.weight(1f).padding(end = 6.dp).height(40.dp).bottomBorder(headLine, 1.5.dp).padding(horizontal = 2.dp),
            size = 19, weight = 700, readOnly = state.locked,
        )
        if (state.dirtyCount == 0 && state.savedFlash) {
            Row(
                Modifier.clip(Capsule).background(C.GreenBg).border(1.dp, C.GreenBd, Capsule).padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LineIcon(Icons.Check, 11.dp, C.Green, 3f)
                Txt(stringResource(R.string.detail_saved), 11, 700, C.Green)
            }
        }
    }
}
