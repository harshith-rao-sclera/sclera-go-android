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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.AssetActivity
import com.sclera.sclerago.domain.model.AssetDocument
import com.sclera.sclerago.domain.model.AssetLock
import com.sclera.sclerago.domain.model.LOCATION_TAG_KEY
import com.sclera.sclerago.domain.model.DocumentSource
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.Status

import com.sclera.sclerago.domain.validation.isServerId
import com.sclera.sclerago.presentation.components.AccessGate
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.presentation.components.ConfirmDialog
import com.sclera.sclerago.presentation.components.PullRefresh
import com.sclera.sclerago.presentation.components.LocalReadOnly
import com.sclera.sclerago.presentation.components.OfflineBanner
import com.sclera.sclerago.presentation.components.OfflineGate
import com.sclera.sclerago.presentation.components.PlainField
import com.sclera.sclerago.presentation.components.ReportUnsaved
import com.sclera.sclerago.presentation.components.RoundButton
import com.sclera.sclerago.presentation.components.bottomBorder
import com.sclera.sclerago.presentation.components.pageGutter
import com.sclera.sclerago.presentation.components.readableWidth
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.model.OnlineOnly
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
) {
    // Still being read from the property's data: the page's shape in placeholders (back already works).
    val asset = state.asset
    if (asset == null) {
        AssetDetailSkeleton(actions.onBack, modifier)
        return
    }
    val isNew = state.isNew
    val locked = state.locked
    // Photos, videos and documents: nobody adds or removes them on a signed-off asset (the web's); otherwise whoever may not
    // change the asset sees their buttons greyed out, saying why.
    val mediaClosed = !isNew && asset.st == Status.VALIDATED
    val b = state.buttons
    val tabs = detailTabs(state)
    // Going from one asset to the next keeps the tab that's open (Media stays Media), as on the web; a newly opened details screen starts on Info.
    var chosen by rememberSaveable { mutableStateOf(DetailTabKind.INFO) }
    val tab = shownTab(tabs, chosen)
    val onInfo = tab == DetailTabKind.INFO
    val keyboardUp = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val focusManager = LocalFocusManager.current
    var keyboardWasUp by remember { mutableStateOf(false) }
    LaunchedEffect(keyboardUp) {
        if (keyboardWasUp && !keyboardUp) focusManager.clearFocus()
        keyboardWasUp = keyboardUp
    }
    var modal by remember(asset.id) { mutableStateOf(DetailModal.NONE) }
    // The type row (system, asset or sub type) whose picker is open.
    var typePick by remember(asset.id) { mutableStateOf<TypePick?>(null) }
    // The Location tag sheet is open.
    var tagging by remember(asset.id) { mutableStateOf(false) }
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
                    // A reviewer declines or approves it; whoever asked withdraws it. What can't apply to this user isn't shown.
                    declineBlocked = b.decline, approveBlocked = b.approve, withdrawBlocked = b.withdraw,
                    showDecline = BarAction.DECLINE !in b.hidden, showApprove = BarAction.APPROVE !in b.hidden,
                    onClose = actions.changeRequest.onClose,
                    onReopen = { modal = DetailModal.APPROVE_REQUEST },
                )
            }
            // Read-only for this user (someone else's, or submitted): the same kind of line says why, unless a change
            // request already shows. One in Managed Assets says so the same way, on every tab.
            if (state.managed && !isNew) ManagedBanner()
            else state.lock?.takeIf { locked && !isNew && request == null && it != AssetLock.MANAGED }?.let { LockBanner(it) }
            // The bar follows the pages live: while they move it shows the page they are heading to (past halfway on a drag,
            // straight away for a tapped tab), not the one picked when they stop.
            val barTab = if (pager.isScrollInProgress) kinds.getOrNull(pager.targetPage) ?: tab else tab
            if (tabs.isNotEmpty()) DetailTabBar(tabs, barTab) { chosen = it }
            CompositionLocalProvider(LocalReadOnly provides locked) {
                // Track focus to prevent pull-to-refresh while a field is being edited.
                var editing by remember { mutableStateOf(false) }
                // Pull down on any tab to read the asset again, as on the asset list (not while adding one: nothing saved yet).
                PullRefresh(state.refreshing, actions.onRefresh, Modifier.weight(1f).fillMaxWidth(), enabled = !isNew && !editing) {
                    HorizontalPager(
                        pager, Modifier.fillMaxSize().onFocusChanged { editing = it.hasFocus },
                        key = { kinds.getOrElse(it) { DetailTabKind.INFO } },
                    ) { page ->
                        val pageTab = kinds.getOrElse(page) { DetailTabKind.INFO }
                        // Room at the end so the last card scrolls clear of the tab's floating buttons (as shown below).
                        val fabRoom = when (pageTab) {
                            DetailTabKind.INFO -> formFloatingButtonsClearance()
                            DetailTabKind.VALIDATION ->
                                if (isServerId(asset.id)) validateFabClearance(state.validation, state.validating) else 0.dp
                            DetailTabKind.DOCUMENTS -> if (mediaClosed) 0.dp else uploadDocumentFabClearance()
                            DetailTabKind.MEDIA -> if (mediaClosed) 0.dp else mediaFabsClearance()
                            else -> 0.dp
                        }
                        Column(
                            // The Info sections run edge to edge on a phone; the other tabs keep a margin, as on the web.
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(
                                horizontal = when (pageTab) {
                                    DetailTabKind.INFO, DetailTabKind.MEDIA -> pageGutter()
                                    DetailTabKind.COMMENTS -> 0.dp // the comments run the full width
                                    else -> maxOf(pageGutter(), PhoneSide)
                                },
                            )
                                .padding(top = 12.dp, bottom = maxOf(fabRoom, 12.dp)),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            val open = { i: Int -> preview = i }
                            when (pageTab) {
                                DetailTabKind.INFO -> {
                                    // The Tags bar first, once the asset has been added (not while a new one is being filled in),
                                    // then the photos, with a + that opens the camera; what it takes is read and sorted into
                                    // nameplate, label ID or asset (one that can't be read goes to Other photos).
                                    // Tagging changes the asset (its tag flags), so only for whoever may change it (collect,
                                    // theirs or nobody's unless a reviewer, not in Managed Assets): a submitted or signed-off
                                    // asset only by a reviewer. Anyone else sees the icons greyed out, saying why.
                                    if (!isNew) TagsBar(tags, actions.tags, blocked = b.change)
                                    InfoPhotoStrip(
                                        state.media, onAdd = if (mediaClosed) null else actions.onAddSortedPhoto, onOpen = open,
                                        onDownload = actions.onDownloadMedia, onRemove = if (mediaClosed) null else ({ removeMedia = it }),
                                        blocked = b.change,
                                    )
                                    // A step that failed or needs a look shows first; tapping it opens the Validation tab. (Not
                                    // for one in Managed Assets: its checks went with the move, and the line at the top says so.)
                                    if (!state.managed) ValidationBanner(state.validation) { chosen = DetailTabKind.VALIDATION }
                                    AssetInfoTab(state, actions, ui, onModal = { modal = it }, onRemoveField = { removeField = it }, onPickType = { typePick = it }, onTagLocation = { tagging = true })
                                }
                                DetailTabKind.VALIDATION -> {
                                    // Validating again is done by the server: offline the last synced results show under a banner.
                                    if (!state.online) OfflineBanner(OnlineOnly.REVALIDATE)
                                    // The link to the run opens the Validation page, so only for who may check (that page
                                    // needs it), and not for one in Managed Assets (the web's mayCheck): greyed out, saying why.
                                    ValidationTab(
                                        state.validation, onOpenResult = actions.onOpenResult, validating = state.validating,
                                        openBlocked = b.validate,
                                    )
                                }
                                // Every document, from the server (not synced: offline a banner says why none show), as the
                                // web's tab: the file the asset was read from first (it shows in Media too, as on the web),
                                // then the ones added to it, its web pages and manuals.
                                DetailTabKind.DOCUMENTS -> {
                                    if (state.documentsUi.offline) OfflineBanner(OnlineOnly.DOCUMENTS)
                                    // A file kept on the phone opens in the media viewer and is removed as media; the
                                    // server's documents are fetched to open and removed there.
                                    fun onPhone(d: AssetDocument) = state.media.indexOfFirst { d.onDevice && it.id == d.id }
                                    DocumentsTab(
                                        state.documents.filterNot { it.isSourcePhoto }, state.documentsUi,
                                        mediaClosed,
                                        { d ->
                                            val i = onPhone(d)
                                            when {
                                                i >= 0 -> preview = i
                                                d.previewable -> actions.onPreviewSource(d)
                                                else -> actions.onOpenDocument(d)
                                            }
                                        },
                                        { d -> state.media.getOrNull(onPhone(d))?.let { removeMedia = it } ?: run { removeDoc = d } },
                                        searching = state.aiBusy || state.manualsSearching, lockReason = b.change, collectReason = b.collect,
                                    )
                                }
                                DetailTabKind.MEDIA -> MediaTab(
                                    state.media, state.photoRules, open, sorting = state.sortingPhotos,
                                    // Every file the asset was read from, whatever its type, under Source file.
                                    sourceFiles = state.documents.filter { it.source == DocumentSource.SOURCE_FILE },
                                    // A photo or video previews in the app; any other file opens in another app.
                                    onOpenSource = { if (it.previewable) actions.onPreviewSource(it) else actions.onOpenDocument(it) },
                                    openingId = state.documentsUi.opening,
                                    // Photos can be dragged between the kinds (as the web's photo kind): a new asset's are saved
                                    // with it, a saved asset's straight away. Not on a read-only or signed-off asset, nor for
                                    // whoever may not change it.
                                    onMove = if (!locked && !mediaClosed && (isNew || b.change == null)) actions.onMovePhoto else null,
                                    // Holding a photo or file: Download, and Remove (greyed out, saying why, when the asset is
                                    // read-only for this user; none on a signed-off asset).
                                    onDownload = actions.onDownloadMedia, onRemove = if (mediaClosed) null else ({ removeMedia = it }),
                                    removeBlocked = b.change,
                                )
                                DetailTabKind.COMMENTS -> {
                                    // The list runs edge to edge; the box keeps the other tabs' margin. None on a signed-off
                                    // asset or one in Managed Assets: its comments are read-only (the web's). Greyed out,
                                    // saying why, for a group that can't comment.
                                    if (!state.managed && asset.st != Status.VALIDATED) {
                                        CommentBox(
                                            state.commentDraft, state.commentError, state.commentPosting, actions.onCommentChange, actions.onPostComment,
                                            Modifier.padding(horizontal = maxOf(pageGutter(), PhoneSide)), blocked = b.comment,
                                        )
                                    }
                                    // As the web: a signed-off asset's comments can't be changed or removed.
                                    CommentList(
                                        state.activity, readOnly = asset.st == Status.VALIDATED || state.managed, onSave = actions.onUpdateComment,
                                        onDelete = { removeComment = it }, posting = state.commentPosting, deleteBlocked = b.removeOthersComment,
                                    )
                                }
                                DetailTabKind.HISTORY -> HistoryList(state.activity)
                                else -> AssetInfoTab(state, actions, ui, onModal = { modal = it }, onRemoveField = { removeField = it }, onPickType = { typePick = it }, onTagLocation = { tagging = true })
                            }
                        }
                    }
                }
            }
        }

        // Keep the floating controls behind the keyboard instead of moving them over the form.
        if (!keyboardUp) FabSwap(onInfo) {
            Box(Modifier.fillMaxWidth()) {
                FormFloatingButtons(
                    aiBusy = state.aiBusy, aiNotice = null, listening = mic.listening, heard = mic.heard,
                    onAi = actions.onAutofill, onMic = actions.onMic, onType = actions.onType,
                    modifier = Modifier.align(Alignment.BottomEnd), reading = state.voiceReading, offline = !state.online,
                    // The property's AI switch off: both stay, dimmed (AI suggestions still opens a look-up's results).
                    aiOff = !state.aiUsable, voiceOff = !state.aiOn, blocked = b.change,
                    // A review closed without applying it waits behind its button: a small dot says so.
                    aiKept = state.aiKept, voiceKept = state.voiceKept,
                )
            }
        }
        // Validation: a floating ▶ that validates the asset once the server has it (as Info's Validate); greyed out, saying
        // why, for whoever may not run checks.
        FabSwap(tab == DetailTabKind.VALIDATION && isServerId(asset.id)) {
            Box(Modifier.fillMaxWidth()) {
                ValidateFab(
                    state.validation, state.validating, offline = !state.online, onValidate = actions.onRevalidate,
                    onOffline = { actions.onOffline(OnlineOnly.VALIDATE) }, modifier = Modifier.align(Alignment.BottomEnd), blocked = b.validate,
                )
            }
        }
        // Documents and Media add with floating buttons, like the other pages. Documents' + keeps files as Media's Add file
        // does (on the phone, synced with the asset), so it works offline and on a new asset too. Whoever may not change the
        // asset sees both greyed out, saying why.
        FabSwap(!mediaClosed && tab == DetailTabKind.DOCUMENTS) {
            Box(Modifier.fillMaxWidth()) {
                AccessGate(b.change, Modifier.align(Alignment.BottomEnd)) {
                    UploadDocumentFab(actions.onUploadDocument)
                }
            }
        }
        FabSwap(!mediaClosed && tab == DetailTabKind.MEDIA) {
            Box(Modifier.fillMaxWidth()) {
                MediaFabs(
                    state.media, state.photoRules, actions.onAddPhoto, actions.onPickFiles, actions.onRecordMemo,
                    Modifier.align(Alignment.BottomEnd), blocked = b.change,
                )
            }
        }

        state.ai?.let { AiSuggestDialog(it, state, actions.ai) }
        state.jacs?.let { JacsPickerSheet(it, state.value("jacs"), actions.jacs.picker) }
        typePick?.let { p -> TypePickerSheet(p, state.formats, state::value, actions.onEdit) { typePick = null } }
        if (tagging) LocationTagSheet(state.value(LOCATION_TAG_KEY), onSave = { actions.onEdit(LOCATION_TAG_KEY, it) }) { tagging = false }
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
                // A file added to the asset is one of its documents too: its Open fetches it from the server and opens it
                // in another app, as from the Documents tab. One the server doesn't have yet opens from the device.
                onOpenFile = m.takeIf { it.isDoc }?.let { x -> state.documentsUi.documents.firstOrNull { it.id == x.id } }
                    ?.let { doc -> { actions.onOpenDocument(doc) } },
                onDownload = actions.onDownloadMedia,
                // As the web: media can be removed unless the asset is signed off (a new asset's too, before it is saved);
                // greyed out, saying why, for whoever may not change it.
                onRemove = if (mediaClosed) null else ({ removeMedia = it }), removeBlocked = b.change,
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

        DetailModalDialog(modal, state, actions) { modal = DetailModal.NONE }
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
        // A yellow outlined text pill (the design canvas's); the tap area is the full 48dp high.
        // Also for one in Managed Assets (the copies are new drafts; the original doesn't change), as the web's; greyed out,
        // saying why, for a group that can't collect (the copies would be refused).
        if (!state.isNew) {
            val cloneAlpha by animateFloatAsState(if (state.dirtyCount == 0) 1f else 0.4f, tween(120), label = "clone")
            AccessGate(state.buttons.collect) {
                Box(
                    Modifier.heightIn(min = 48.dp).pressClickable(shape = Capsule, role = Role.Button, onClick = onClone)
                        .alpha(cloneAlpha).testTag(DetailTags.CLONE),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        Modifier.height(28.dp).clip(Capsule).background(C.YTintBg).border(1.dp, C.YTintBd, Capsule)
                            .padding(start = 8.dp, end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        LineIcon(Icons.Clone, 13.dp, C.Yellow, 2.2f)
                        Txt(stringResource(R.string.clone_action), 12, 700, C.Yellow)
                    }
                }
            }
        }
    }
}

/** The side margin of every tab but Info on a phone. */
private val PhoneSide = 12.dp
