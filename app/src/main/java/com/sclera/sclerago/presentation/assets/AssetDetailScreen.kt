package com.sclera.sclerago.presentation.assets

import com.sclera.sclerago.presentation.components.pressClickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.AssetActivity
import com.sclera.sclerago.domain.model.AssetDocument
import com.sclera.sclerago.domain.model.AssetLock
import com.sclera.sclerago.domain.model.DocumentSource
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.model.isSubmittedOrSignedOff
import com.sclera.sclerago.domain.validation.isServerId
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.presentation.components.ConfirmDialog
import com.sclera.sclerago.presentation.components.PullRefresh
import com.sclera.sclerago.presentation.components.DiscardChangesDialog
import com.sclera.sclerago.presentation.components.LocalReadOnly
import com.sclera.sclerago.presentation.components.OfflineBanner
import com.sclera.sclerago.presentation.components.PlainField
import com.sclera.sclerago.presentation.components.ReportUnsaved
import com.sclera.sclerago.presentation.components.RoundButton
import com.sclera.sclerago.presentation.components.bottomBorder
import com.sclera.sclerago.presentation.components.pageGutter
import com.sclera.sclerago.presentation.components.readableWidth
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.model.OnlineOnly
import com.sclera.sclerago.presentation.model.chipBg
import com.sclera.sclerago.presentation.tagging.TagUiState
import com.sclera.sclerago.presentation.tagging.TagsBar
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/**
 * One asset details screen: its name, the tabs [detailTabs] works out for it (Info, Validation, Documents, Media,
 * Comments, History), the floating AI / voice buttons and the confirmations. Stateless apart from what is on screen
 * (tab, open sections, dialogs); the data and what the buttons do come in as [state] and [actions].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AssetDetailScreen(
    state: AssetDetailState,
    actions: AssetDetailActions,
    modifier: Modifier = Modifier,
    mic: MicState = MicState(),
    tags: TagUiState = TagUiState(),
    /** A tab to open on (the list's validation badge opens Validation); [onTabOpened] once it is chosen. */
    openTab: DetailTabKind? = null,
    onTabOpened: () -> Unit = {},
) {
    // Still being read from the property's data: the page's shape in placeholders (back already works).
    val asset = state.asset
    if (asset == null) {
        AssetDetailSkeleton(actions.onBack, modifier)
        return
    }
    val isNew = state.isNew
    val locked = state.locked
    // Photos, videos and documents: read-only like the rest, and on a signed-off asset for an admin too (the web's).
    val mediaLocked = locked || (!isNew && asset.st == Status.VALIDATED)
    val tabs = detailTabs(state)
    // Going from one asset to the next keeps the tab that's open (Media stays Media), as on the web; a newly opened details screen starts on Info.
    var chosen by rememberSaveable { mutableStateOf(DetailTabKind.INFO) }
    val tab = shownTab(tabs, chosen)
    val onInfo = tab == DetailTabKind.INFO
    var modal by remember(asset.id) { mutableStateOf(DetailModal.NONE) }
    var preview by remember(asset.id) { mutableStateOf<Int?>(null) }
    val ui = remember(asset.id) { FormUi() }

    // The web page or uploaded file waiting for its removal to be confirmed.
    var removeDoc by remember(asset.id) { mutableStateOf<AssetDocument?>(null) }
    var removeField by remember(asset.id) { mutableStateOf<FieldCfg?>(null) }
    // The photo, video, voice memo or file waiting for its removal to be confirmed.
    var removeMedia by remember(asset.id) { mutableStateOf<Media?>(null) }
    // The comment waiting for its removal to be confirmed.
    var removeComment by remember(asset.id) { mutableStateOf<AssetActivity?>(null) }

    // The tabs are pages: tap a tab or swipe sideways. A tapped tab slides the pages there; a finished swipe picks the tab
    // the pages rest on. Each page scrolls on its own and opens at its top.
    val kinds = tabs.map { it.kind }
    val pager = key(kinds) { rememberPagerState(initialPage = kinds.indexOf(tab).coerceAtLeast(0)) { kinds.size.coerceAtLeast(1) } }
    LaunchedEffect(tab, pager) {
        val i = kinds.indexOf(tab)
        if (i >= 0 && pager.currentPage != i) pager.animateScrollToPage(i, animationSpec = tween(200, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) kinds.getOrNull(pager.currentPage)?.let { if (it != chosen) chosen = it }
        }
    }
    // Opened on a given tab (the list's validation badge: Validation). After the pager's own effects, so its first reading
    // of the page it rests on doesn't put the tab back; the pager then slides there.
    LaunchedEffect(asset.id, openTab) {
        if (openTab != null) {
            chosen = openTab
            onTabOpened()
        }
    }

    // Leaving asks first only when something would be lost: a new asset once anything has been entered
    // or attached, the photos it was opened with included (defaults and values filled in from a scanned device
    // don't count), an existing one while it has unsaved changes or a comment typed but not posted. Otherwise back
    // just goes back.
    val lost = state.dirtyCount > 0 || state.commentDraft.isNotBlank() ||
        (isNew && (state.media.isNotEmpty() || state.newPicks.isNotEmpty()))
    // Counted while something would be lost, so a bottom tab or a side-menu page asks before leaving it too.
    ReportUnsaved(lost)
    fun back() {
        if (lost) {
            modal = DetailModal.DISCARD
        } else {
            actions.onBack()
        }
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
            TopBar(state, onBack = ::back, onName = { actions.onEdit("name", it) }, onClone = actions.onOpenClone)
            // A change request on a submitted (or signed-off) asset: one line under the top bar on every tab, opened on a tap.
            // (None for one in Managed Assets: it is read-only here.)
            val request = asset.changeRequest?.takeIf { !isNew && !state.managed && (asset.st == Status.SUBMITTED || asset.st == Status.VALIDATED) }
            request?.let {
                ChangeRequestBanner(
                    it, state.changeRequestMine, state.changeRequester,
                    // An admin can change a submitted asset (it isn't locked for them): theirs to decline or reopen.
                    admin = !locked,
                    onClose = actions.changeRequest.onClose,
                    onReopen = { modal = DetailModal.APPROVE_REQUEST },
                )
            }
            // Read-only for this user (someone else's, or submitted): the same kind of line says why, unless a change
            // request already shows. One in Managed Assets says so the same way, on every tab.
            if (state.managed && !isNew) ManagedBanner()
            else state.lock?.takeIf { locked && !isNew && request == null && it != AssetLock.MANAGED }?.let { LockBanner(it, reviewer = state.reviewer) }
            // The bar follows the pages live: while they move it shows the page they are heading to (past halfway on a drag,
            // straight away for a tapped tab), not the one picked when they stop.
            val barTab = if (pager.isScrollInProgress) kinds.getOrNull(pager.targetPage) ?: tab else tab
            if (tabs.isNotEmpty()) DetailTabBar(tabs, barTab) { chosen = it }
            CompositionLocalProvider(LocalReadOnly provides locked) {
                // While a field is being edited, sideways drags belong to it (moving the cursor, scrolling a long value),
                // so the pages don't swipe; the tabs still switch them.
                var editing by remember { mutableStateOf(false) }
                // Pull down on any tab to read the asset again, as on the asset list (not while adding one: nothing saved yet).
                PullRefresh(state.refreshing, actions.onRefresh, Modifier.weight(1f).fillMaxWidth(), enabled = !isNew && !editing) {
                    HorizontalPager(
                        pager, Modifier.fillMaxSize().onFocusChanged { editing = it.hasFocus },
                        userScrollEnabled = !editing, key = { kinds.getOrElse(it) { DetailTabKind.INFO } },
                    ) { page ->
                        val pageTab = kinds.getOrElse(page) { DetailTabKind.INFO }
                        Column(
                            // Room at the end so the last card scrolls clear of the AI and mic buttons.
                            // The Info sections run edge to edge on a phone; the other tabs keep a margin, as on the web.
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(
                                horizontal = when (pageTab) {
                                    DetailTabKind.INFO, DetailTabKind.MEDIA -> pageGutter()
                                    DetailTabKind.COMMENTS -> 0.dp // the comments run the full width
                                    else -> maxOf(pageGutter(), PhoneSide)
                                },
                            )
                                .padding(top = 12.dp, bottom = 160.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            val open = { i: Int -> preview = i }
                            when (pageTab) {
                                DetailTabKind.INFO -> {
                                    // The Tags bar first (a new asset has its id already, so it can be tagged too), then the photos,
                                    // with a + that opens the camera (as the Media tab's Camera, into Other photos).
                                    // Tagging changes the asset (its tag flags), so only for whoever may change it (collect,
                                    // theirs or nobody's unless an admin, not in Managed Assets), and, as the old app, never
                                    // a submitted or signed-off asset.
                                    TagsBar(tags, actions.tags, enabled = state.canEdit && !isSubmittedOrSignedOff(asset.st))
                                    InfoPhotoStrip(state.media, onAdd = if (mediaLocked) null else ({ actions.onAddPhoto(null) }), onOpen = open)
                                    // A step that failed or needs a look shows first; tapping it opens the Validation tab. (Not
                                    // for one in Managed Assets: its checks went with the move, and the line at the top says so.)
                                    if (!state.managed) ValidationBanner(state.validation) { chosen = DetailTabKind.VALIDATION }
                                    AssetInfoTab(state, actions, ui, onModal = { modal = it }, onRemoveField = { removeField = it })
                                }
                                DetailTabKind.VALIDATION -> {
                                    // Validating again is done by the server: offline the last synced results show under a banner.
                                    if (!state.online) OfflineBanner(OnlineOnly.REVALIDATE)
                                    // The link to the run opens the Validation page, so only for who may check (that page
                                    // needs it), and not for one in Managed Assets (the web's mayCheck).
                                    ValidationTab(
                                    state.validation,
                                    onOpenResult = actions.onOpenResult.takeIf { state.canCheck && !state.managed },
                                    validating = state.validating,
                                )
                                }
                                // The documents added to the asset and its web documents; its source file shows in Media, under Videos.
                                DetailTabKind.DOCUMENTS -> DocumentsTab(
                                state.documents.filter { it.source != DocumentSource.SOURCE_FILE }, mediaLocked, { removeDoc = it },
                                searching = state.aiBusy || state.manualsSearching,
                            )
                                DetailTabKind.MEDIA -> MediaTab(
                                    state.media, state.photoRules, open, sorting = state.sortingPhotos,
                                    sourceFile = state.documents.firstOrNull { it.source == DocumentSource.SOURCE_FILE },
                                    // The uploaded file isn't the asset's: who may delete it is the server's rule for files
                                    // (its `removable`), whether or not the asset can be changed; not from Managed Assets.
                                    canRemoveSource = !state.managed, onRemoveSource = { removeDoc = it },
                                    // Only while adding an asset: its photos can be dragged between the kinds.
                                    onMove = if (isNew && !locked) actions.onMovePhoto else null,
                                )
                                DetailTabKind.COMMENTS -> {
                                    // The list runs edge to edge; the box keeps the other tabs' margin. None for one in Managed
                                    // Assets: its comments are read-only (the web's).
                                    if (state.mayComment) {
                                        CommentBox(
                                            state.commentDraft, state.commentError, state.commentPosting, actions.onCommentChange, actions.onPostComment,
                                            Modifier.padding(horizontal = maxOf(pageGutter(), PhoneSide)),
                                        )
                                    }
                                    // As the web: a signed-off asset's comments can't be changed or removed.
                                    CommentList(
                                        state.activity, readOnly = asset.st == Status.VALIDATED || state.managed, onSave = actions.onUpdateComment,
                                        onDelete = { removeComment = it }, posting = state.commentPosting,
                                    )
                                }
                                DetailTabKind.HISTORY -> HistoryList(state.activity)
                                else -> AssetInfoTab(state, actions, ui, onModal = { modal = it }, onRemoveField = { removeField = it })
                            }
                        }
                    }
                }
            }
        }

        FabSwap(onInfo && !locked) {
            Box(Modifier.fillMaxWidth()) {
                FormFloatingButtons(
                    aiBusy = state.aiBusy, aiNotice = null, listening = mic.listening, heard = mic.heard,
                    onAi = actions.onAutofill, onMic = actions.onMic, onType = actions.onType,
                    modifier = Modifier.align(Alignment.BottomEnd), reading = state.voiceReading, offline = !state.online,
                    // The property's AI switch off: both stay, dimmed (AI suggestions still opens a look-up's results).
                    aiOff = !state.aiUsable, voiceOff = !state.aiOn,
                )
            }
        }
        // Validation: a floating ▶ that validates the asset, for anyone who may run checks once the server has it (as Info's Validate).
        FabSwap(tab == DetailTabKind.VALIDATION && state.canCheck && isServerId(asset.id)) {
            Box(Modifier.fillMaxWidth()) {
                ValidateFab(
                    state.validation, state.validating, offline = !state.online, onValidate = actions.onRevalidate,
                    onOffline = { actions.onOffline(OnlineOnly.VALIDATE) }, modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }
        // Documents and Media add with floating buttons, like the other pages.
        FabSwap(!mediaLocked && tab == DetailTabKind.DOCUMENTS) {
            Box(Modifier.fillMaxWidth()) {
                UploadDocumentFab(actions.onUploadDocument, Modifier.align(Alignment.BottomEnd))
            }
        }
        FabSwap(!mediaLocked && tab == DetailTabKind.MEDIA) {
            Box(Modifier.fillMaxWidth()) {
                MediaFabs(
                    state.media, state.photoRules, actions.onAddPhoto, actions.onPickFiles, actions.onRecordMemo,
                    Modifier.align(Alignment.BottomEnd),
                )
            }
        }

        state.ai?.let { AiSuggestDialog(it, state, actions.ai) }
        state.jacs?.let { JacsPickerSheet(it, state.value("jacs"), actions.jacs.picker) }
        state.voice?.let { VoiceFillSheet(it, state, actions.voice) }
        state.typing?.let { text ->
            DescribeSheet(text, state.voiceReading, actions.typing.onText, actions.typing.onFill, actions.typing.onClose)
        }
        if (state.cloneOpen) CloneSheet(state, actions.onClone, actions.onCloseClone)
        if (state.addFieldOpen) AddFieldSheet(state, actions.addField)

        removeField?.let { f ->
            ConfirmDialog(
                icon = Icons.Trash, iconBg = C.DangerBg, iconFg = C.Red,
                title = stringResource(R.string.own_field_remove_title, f.label), body = stringResource(R.string.own_field_remove_body),
                no = stringResource(R.string.action_cancel), yes = stringResource(R.string.action_delete),
                onNo = { removeField = null },
                onYes = {
                    removeField = null
                    actions.addField.onRemove(f.key)
                },
            )
        }

        removeDoc?.let { doc ->
            ConfirmDialog(
                icon = Icons.Trash, iconBg = C.DangerBg, iconFg = C.Red,
                title = stringResource(R.string.doc_delete_title), body = stringResource(R.string.doc_delete_body),
                no = stringResource(R.string.action_cancel), yes = stringResource(R.string.action_delete),
                onNo = { removeDoc = null },
                onYes = {
                    removeDoc = null
                    actions.onRemoveDocument(doc)
                },
            )
        }

        preview?.let { i ->
            state.media.getOrNull(i)?.let { m -> PhotoViewer(
                m, onClose = { preview = null },
                onDownload = actions.onDownloadMedia,
                // As the web: media can be removed unless the asset is read-only (a new asset's too, before it is saved).
                onRemove = if (mediaLocked) null else ({ removeMedia = it }),
            ) }
        }
        removeMedia?.let { m ->
            ConfirmDialog(
                icon = Icons.Trash, iconBg = C.DangerBg, iconFg = C.Red,
                title = removeMediaTitle(m), body = stringResource(R.string.media_remove_body),
                no = stringResource(R.string.action_cancel), yes = stringResource(R.string.media_remove),
                onNo = { removeMedia = null },
                onYes = {
                    removeMedia = null
                    preview = null
                    actions.onRemoveMedia(m)
                },
            )
        }
        removeComment?.let { c ->
            ConfirmDialog(
                icon = Icons.Trash, iconBg = C.DangerBg, iconFg = C.Red,
                title = stringResource(R.string.detail_comment_delete_title),
                body = stringResource(R.string.detail_comment_delete_body, c.text.let { if (it.length > 80) it.take(80) + "…" else it }),
                no = stringResource(R.string.action_cancel), yes = stringResource(R.string.action_delete),
                onNo = { removeComment = null },
                onYes = {
                    removeComment = null
                    actions.onRemoveComment(c)
                },
            )
        }

        state.changeNote?.let { note ->
            ChangeRequestDialog(
                note, actions.changeRequest.onNote, actions.changeRequest.onSend, actions.changeRequest.onCancel,
                // An update starts from what the request said.
                dirty = note != state.asset?.changeRequest?.note.orEmpty(),
            )
        }

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
            // Back to draft (the web's dialog).
            DetailModal.MOVE_TO_DRAFT -> ConfirmDialog(
                icon = Icons.Refresh, iconBg = C.WarnRow, iconFg = C.Orange,
                title = stringResource(R.string.change_action_move_to_draft),
                body = stringResource(R.string.move_draft_body),
                no = stringResource(R.string.action_cancel), yes = stringResource(R.string.change_action_move_to_draft), yesBg = C.Yellow,
                onNo = { modal = DetailModal.NONE },
                onYes = {
                    modal = DetailModal.NONE
                    actions.onMoveToDraft()
                },
            )
            // An admin's sign-off without a validation run: say what happens first (the web's dialog).
            DetailModal.MARK_VALIDATED -> ConfirmDialog(
                icon = Icons.ShieldCheck, iconBg = Status.VALIDATED.chipBg, iconFg = C.Purple,
                title = stringResource(R.string.sign_off_action),
                body = pluralStringResource(R.plurals.sign_off_body, 1),
                no = stringResource(R.string.action_cancel), yes = stringResource(R.string.sign_off_action), yesBg = C.Yellow,
                onNo = { modal = DetailModal.NONE },
                onYes = {
                    modal = DetailModal.NONE
                    actions.onMarkValidated()
                },
            )
            // Approving a change request: back to Draft, with whoever asked (the web's dialog).
            DetailModal.APPROVE_REQUEST -> ConfirmDialog(
                icon = Icons.CheckCircle, iconBg = C.WarnRow, iconFg = C.Orange,
                title = stringResource(R.string.change_bulk_approve_title),
                body = pluralStringResource(
                    R.plurals.change_bulk_approve_body, 1, 1, state.changeRequester ?: stringResource(R.string.change_whoever_asked),
                ),
                no = stringResource(R.string.action_cancel), yes = stringResource(R.string.change_request_reopen), yesBg = C.Yellow,
                onNo = { modal = DetailModal.NONE },
                onYes = {
                    modal = DetailModal.NONE
                    actions.changeRequest.onReopen()
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
                    // With photos or videos taken for it, say they go too.
                    body = stringResource(if (state.media.isNotEmpty()) R.string.detail_discard_photos_body else R.string.detail_discard_body),
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

/**
 * A page's floating buttons, along the bottom: they fade in as they rise a little into place when their tab opens (or the
 * asset can be changed again), and fade out as they sink when it closes; switching tabs crossfades one set into the other.
 */
@Composable
private fun BoxScope.FabSwap(visible: Boolean, content: @Composable () -> Unit) {
    val ease = FastOutSlowInEasing
    AnimatedVisibility(
        visible, Modifier.align(Alignment.BottomCenter).readableWidth(),
        enter = fadeIn(tween(FAB_IN_MS, easing = ease)) + slideInVertically(tween(FAB_IN_MS, easing = ease)) { it / 6 },
        exit = fadeOut(tween(FAB_OUT_MS, easing = ease)) + slideOutVertically(tween(FAB_OUT_MS, easing = ease)) { it / 6 },
    ) { content() }
}

/** How long the floating buttons take to come in and to go. */
private const val FAB_IN_MS = 260
private const val FAB_OUT_MS = 180

@Composable
private fun TopBar(state: AssetDetailState, onBack: () -> Unit, onName: (String) -> Unit, onClone: () -> Unit) {
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
        // Clone (the web's header button): copies of this asset as saved; with unsaved changes it is dimmed and says why.
        // A yellow outlined pill with its icon and label, as the iOS app's; the tap area is the full 48dp high.
        // Also for one in Managed Assets (the copies are new drafts; the original doesn't change), as the web's; not for a
        // group that can't collect (the copies would be refused).
        if (state.canClone) {
            val cloneAlpha by animateFloatAsState(if (state.dirtyCount == 0) 1f else 0.4f, tween(120), label = "clone")
            Box(
                Modifier.heightIn(min = 48.dp).pressClickable(shape = Capsule, role = Role.Button, onClick = onClone)
                    .alpha(cloneAlpha).testTag(DetailTags.CLONE),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    Modifier.height(28.dp).clip(Capsule).background(C.YTintBg).border(1.dp, C.Yellow.copy(alpha = 0.7f), Capsule)
                        .padding(horizontal = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    LineIcon(Icons.Clone, 13.dp, C.Yellow, 2.2f)
                    Txt(stringResource(R.string.clone_action), 12, 700, C.Yellow)
                }
            }
        }
    }
}

/** The side margin of every tab but Info on a phone. */
private val PhoneSide = 12.dp
