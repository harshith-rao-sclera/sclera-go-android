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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.AssetDocument
import com.sclera.sclerago.domain.model.Status
import androidx.compose.ui.platform.testTag
import com.sclera.sclerago.presentation.components.BottomSheet
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.presentation.components.CircleFab
import com.sclera.sclerago.presentation.components.SheetHeader
import com.sclera.sclerago.presentation.components.ConfirmDialog
import com.sclera.sclerago.presentation.components.DiscardChangesDialog
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
 * One asset details screen: its name, the tabs [detailTabs] works out for it (Info, Validation, Documents, Media,
 * Comments, History), the floating AI / voice buttons and the confirmations. Stateless apart from what is on screen
 * (tab, open sections, dialogs); the data and what the buttons do come in as [state] and [actions].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AssetDetailScreen(state: AssetDetailState, actions: AssetDetailActions, modifier: Modifier = Modifier, mic: MicState = MicState()) {
    val asset = state.asset ?: return
    val isNew = state.isNew
    val locked = state.locked
    val tabs = detailTabs(state)
    var chosen by rememberSaveable(asset.id) { mutableStateOf(DetailTabKind.INFO) }
    val tab = shownTab(tabs, chosen)
    val onInfo = tab == DetailTabKind.INFO
    var modal by remember(asset.id) { mutableStateOf(DetailModal.NONE) }
    var preview by remember(asset.id) { mutableStateOf<Int?>(null) }
    val ui = remember(asset.id) { FormUi() }
    val scroll = rememberScrollState()

    // The web page waiting for its removal to be confirmed.
    var removeDoc by remember(asset.id) { mutableStateOf<AssetDocument?>(null) }

    // Each tab opens at its top.
    LaunchedEffect(tab) { scroll.scrollTo(0) }

    // Leaving asks first only when something would be lost: a new asset once anything has been entered
    // or attached (defaults and values filled in from a scanned device don't count), an existing one
    // while it has unsaved changes. Otherwise back just goes back.
    fun back() {
        if (state.dirtyCount > 0 || (isNew && state.newPicks.isNotEmpty())) modal = DetailModal.DISCARD else actions.onBack()
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
            if (tabs.isNotEmpty()) DetailTabBar(tabs, tab) { chosen = it }
            CompositionLocalProvider(LocalReadOnly provides locked) {
                Column(
                    // Room at the end so the last card scrolls clear of the AI and mic buttons.
                    // The Info sections run edge to edge on a phone; the other tabs keep a margin, as on the web.
                    Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = if (onInfo) pageGutter() else maxOf(pageGutter(), PhoneSide))
                        .padding(top = 12.dp, bottom = 160.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val open = { i: Int -> preview = i }
                    when {
                        onInfo -> {
                            // A step that failed or needs a look shows first; tapping it opens the Validation tab.
                            ValidationBanner(state.validation) { chosen = DetailTabKind.VALIDATION }
                            AssetInfoTab(state, actions, ui, onModal = { modal = it })
                        }
                        tab == DetailTabKind.VALIDATION -> ValidationTab(state.validation, state.validating, actions.onRevalidate.takeIf { state.canCheck })
                        tab == DetailTabKind.DOCUMENTS -> DocumentsTab(state.documents, locked) { removeDoc = it }
                        tab == DetailTabKind.MEDIA -> MediaTab(state.media, state.photoRules, open)
                        tab == DetailTabKind.COMMENTS -> CommentList(state.activity)
                        tab == DetailTabKind.HISTORY -> HistoryList(state.activity)
                        else -> AssetInfoTab(state, actions, ui, onModal = { modal = it })
                    }
                }
            }
        }

        if (onInfo && !locked) {
            Box(Modifier.align(Alignment.BottomCenter).readableWidth()) {
                FormFloatingButtons(
                    aiBusy = state.aiBusy, aiNotice = null, listening = mic.listening, heard = mic.heard,
                    onAi = actions.onAutofill, onMic = actions.onMic,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }
        // Comments: the + opens the sheet a comment is written in (open on submitted assets too: the server takes comments on any asset).
        if (!isNew && tab == DetailTabKind.COMMENTS) {
            Box(Modifier.align(Alignment.BottomCenter).readableWidth()) {
                CircleFab(
                    Icons.Plus, stringResource(R.string.detail_add_comment), { actions.onCommentSheet(true) },
                    Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp).testTag("comment-add"),
                )
            }
        }
        if (state.commentOpen) {
            BottomSheet({ actions.onCommentSheet(false) }) {
                SheetHeader(stringResource(R.string.detail_add_comment), null) { actions.onCommentSheet(false) }
                Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 20.dp)) {
                    CommentBox(state.commentDraft, state.commentError, state.commentPosting, actions.onCommentChange, actions.onPostComment)
                }
            }
        }
        // Documents and Media add with floating buttons, like the other pages.
        if (!locked && tab == DetailTabKind.DOCUMENTS) {
            Box(Modifier.align(Alignment.BottomCenter).readableWidth()) {
                UploadDocumentFab(actions.onUploadDocument, Modifier.align(Alignment.BottomEnd))
            }
        }
        if (!locked && tab == DetailTabKind.MEDIA) {
            Box(Modifier.align(Alignment.BottomCenter).readableWidth()) {
                MediaFabs(
                    state.media, state.photoRules, actions.onAddPhoto, actions.onPickFiles, actions.onRecordMemo,
                    Modifier.align(Alignment.BottomEnd),
                )
            }
        }

        state.ai?.let { AiSuggestDialog(it, state, actions.ai) }
        state.voice?.let { VoiceFillSheet(it, state, actions.voice) }
        state.classify?.let { ClassifySheet(it, actions.classify) }

        removeDoc?.let { doc ->
            ConfirmDialog(
                icon = Icons.Trash, iconBg = C.DangerBg, iconFg = C.Red,
                title = stringResource(R.string.doc_delete_title), body = stringResource(R.string.doc_delete_body),
                no = stringResource(R.string.action_cancel), yes = stringResource(R.string.action_delete),
                onNo = { removeDoc = null },
                onYes = {
                    removeDoc = null
                    actions.onRemoveWebPage(doc.id)
                },
            )
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
            // Incomplete: the server submits it as an exception, so say so (as the web's "missing mandatory fields" check).
            DetailModal.SUBMIT_EXCEPTION -> ConfirmDialog(
                icon = Icons.Alert, iconBg = C.WarnRow, iconFg = C.Orange,
                title = stringResource(R.string.detail_submit_exception_title),
                body = stringResource(R.string.detail_submit_exception_body),
                no = stringResource(R.string.action_keep_editing), yes = stringResource(R.string.action_submit_exception), yesBg = C.Yellow,
                onNo = { modal = DetailModal.NONE },
                onYes = {
                    modal = DetailModal.NONE
                    if (isNew) actions.onCreate(true) else actions.onSubmit()
                },
            )
            DetailModal.SAVE_MISSING -> ConfirmDialog(
                icon = Icons.Alert, iconBg = C.WarnRow, iconFg = C.Orange,
                title = stringResource(R.string.detail_save_missing_title),
                body = stringResource(R.string.detail_save_missing_body),
                no = stringResource(R.string.action_keep_editing), yes = stringResource(R.string.action_save), yesBg = C.Yellow,
                onNo = { modal = DetailModal.NONE },
                onYes = {
                    modal = DetailModal.NONE
                    actions.onSave()
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
            DetailModal.DISCARD -> if (isNew) {
                ConfirmDialog(
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
            } else {
                DiscardChangesDialog(
                    onKeep = { modal = DetailModal.NONE },
                    onDiscard = {
                        modal = DetailModal.NONE
                        actions.onDiscardEdits()
                        actions.onBack()
                    },
                )
            }
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
            size = 17, weight = 700, readOnly = state.locked,
        )
        if (state.dirtyCount == 0 && state.savedFlash) {
            Row(
                Modifier.clip(Capsule).background(C.GreenBg).border(1.dp, C.GreenBd, Capsule).padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LineIcon(Icons.Check, 11.dp, C.Green, 3f)
                Txt(stringResource(R.string.detail_saved), 10, 700, C.Green)
            }
        }
    }
}

/** The side margin of every tab but Info on a phone. */
private val PhoneSide = 12.dp
