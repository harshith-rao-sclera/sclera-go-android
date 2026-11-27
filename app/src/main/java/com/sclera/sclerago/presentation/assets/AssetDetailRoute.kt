package com.sclera.sclerago.presentation.assets

import com.sclera.sclerago.domain.model.ATTACHABLE_FILE_TYPES
import com.sclera.sclerago.domain.model.TagKind
import com.sclera.sclerago.services.tagging.NfcTagReader
import com.sclera.sclerago.presentation.components.DiscardChangesDialog
import com.sclera.sclerago.presentation.components.LocalToaster
import com.sclera.sclerago.presentation.components.ToastKind

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.ScanFacts
import com.sclera.sclerago.domain.model.LocValue
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.components.LocalMediaSources
import com.sclera.sclerago.presentation.components.MediaSources
import com.sclera.sclerago.presentation.components.SaveMediaAs
import com.sclera.sclerago.presentation.tagging.TagFlowActions
import com.sclera.sclerago.presentation.tagging.TagFlowHost
import com.sclera.sclerago.presentation.tagging.TagViewModel
import com.sclera.sclerago.presentation.tagging.TagsActions
import com.sclera.sclerago.services.media.describeAttachments
import com.sclera.sclerago.services.media.describeVisualMedia
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Hooks [AssetDetailScreen] up: the view model for the form, the camera, the gallery and file pickers for
 * adding photos, and creating or deleting the asset (which change what the dashboard shows, so the
 * caller does them).
 */
@Composable
fun AssetDetailRoute(
    asset: Asset,
    isNew: Boolean,
    onBack: () -> Unit,
    onChangeLoc: () -> Unit,
    /** [exception]: submitted with a mandatory field empty or too few photos, so it goes in as an exception. */
    /** Creates the new asset; [place] is the location picked in its form (null: where it started). */
    /**
     * Saves the new asset. [shots] are the photos it was opened with as the page has them (sorted, or moved between kinds
     * by hand), kept as they are; null while they are still being sorted.
     */
    onCreate: (
        name: String, detail: String, media: List<Media>, values: Map<String, String>, submit: Boolean, exception: Boolean, place: LocValue?,
        shots: List<Media>?,
    ) -> Unit,
    onDelete: (String) -> Unit,
    onNotice: (UiText) -> Unit,
    /** A place picked for this asset in the location sheet, to keep as an unsaved change; [onPlaceTaken] once kept. */
    pickedPlace: LocValue? = null,
    onPlaceTaken: () -> Unit = {},
    /** Assign the asset to a person (the web's Assign asset). */
    onAssign: (() -> Unit)? = null,
    /** [onAssign] is "Assign to me" (the user isn't an admin). */
    assignToMe: Boolean = false,
    /** Move the validated asset to Managed assets; null when it isn't offered. */
    onMove: (() -> Unit)? = null,
    /** Why Move to Managed assets can't be used now (no Configure, property not linked…): greyed out, a tap says this. */
    moveReason: UiText? = null,
    /** Why Assign (or Assign to me) can't be used now: greyed out, a tap says this. */
    assignReason: UiText? = null,
    /** Validate: run checks on this asset (the web's asset bar opens Run validation on it). */
    onValidate: () -> Unit = {},
    /** "Open in Validation": the Validation page on the run [jobId] with the asset's result open. */
    onOpenResult: (jobId: String, assetId: String) -> Unit = { _, _ -> },
    /** What a new asset's form starts with (a scanned device's make, model, note, BLE tag / IP). */
    prefill: Map<String, String> = emptyMap(),
    /** What the scan found out about the device a new asset is made from: the AI is asked about it once it opens. */
    scan: ScanFacts? = null,
    /** A photo a new asset was opened with (the Camera tab's shots) was removed on its page. */
    onDropCapture: (Media) -> Unit = {},
    vm: AssetDetailViewModel = hiltViewModel(),
    tagVm: TagViewModel = hiltViewModel(),
    mediaVm: MediaSourceViewModel = hiltViewModel(),
) {
    // A new asset's place is the top bar's location, so it is passed again when another location tab is chosen.
    LaunchedEffect(asset.id, isNew, if (isNew) asset.locationId else null) { vm.open(asset, isNew, prefill, scan) }
    DisposableEffect(vm) { onDispose { vm.close() } }
    LaunchedEffect(vm) { vm.notices.collect(onNotice) }
    // Shots left out over the photo limits are taken off the new asset's shots (and their files deleted).
    LaunchedEffect(vm) { vm.droppedCaptures.collect(onDropCapture) }
    // A document fetched from the server opens in an app the user picks.
    val viewer = LocalContext.current
    val openWith = stringResource(R.string.doc_open_with)
    LaunchedEffect(vm) { vm.openedDocuments.collect { openFile(viewer, it.uri, it.contentType, openWith) } }
    // Saved, or its status changed: back to the list, which shows it where it now belongs.
    LaunchedEffect(vm) {
        vm.finished.collect {
            onNotice(it)
            onBack()
        }
    }
    LaunchedEffect(pickedPlace) {
        pickedPlace?.let {
            vm.choosePlace(it)
            onPlaceTaken()
        }
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val tagState by tagVm.state.collectAsStateWithLifecycle()
    // The Tags bar follows the asset's tag columns only (qr_code_tag, barcode_tag, nfc_tag): set when a tag is written or
    // moved, and by a sync. They come back with the asset whenever they change, so the bar updates while it is open.
    LaunchedEffect(asset.id, asset.tagged) { tagVm.open(asset.id, asset.propertyId, asset.tagged) }
    // The view model catches up one frame after the asset changes; until then there is nothing to show.
    if (state.asset?.id != asset.id) return

    val context = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    fun attach(media: List<Media>) = vm.attach(media)
    val voice = rememberVoiceInput(onHeard = vm::heard, onFailed = onNotice)

    // "Add photo" opens the same camera as the Camera tab; its captures attach to this asset.
    var camOpen by remember(asset.id) { mutableStateOf(false) }
    var memoOpen by remember(asset.id) { mutableStateOf(false) }
    var camCaps by remember(asset.id) { mutableStateOf(listOf<Media>()) }
    var camSel by remember(asset.id) { mutableStateOf(setOf<String>()) }
    // Shots still being read and checked against the photo rules: not ticked or counted yet.
    var camChecking by remember(asset.id) { mutableStateOf(setOf<String>()) }
    var camVideo by remember(asset.id) { mutableStateOf(false) }
    // One shot is sorted and checked at a time, so two quick shots can't both take the last room of a kind.
    val shotLock = remember(asset.id) { Mutex() }
    // The strip's photo being cropped (its crop screen over the camera); null: none.
    var cropShot by remember(asset.id) { mutableStateOf<Media?>(null) }
    // Closing the camera with shots in its strip asks first, as the Camera tab does.
    var askDiscardShots by remember(asset.id) { mutableStateOf(false) }

    // The camera closes and every shot left in its strip goes (the app's own files deleted), so the next Add photo
    // starts empty.
    fun discardStrip() {
        camCaps.forEach(vm::discardShot)
        camCaps = emptyList()
        camSel = emptySet()
        askDiscardShots = false
        camOpen = false
    }

    fun closeCamera() {
        if (camCaps.isEmpty()) camOpen = false else askDiscardShots = true
    }

    // A shot joins the strip and is read on the device (nameplate, label ID or asset, or the card's kind): it is ticked
    // while its kind and the asset have room, else the camera says why.
    fun sortIntoStrip(m: Media) {
        camChecking = camChecking + m.id
        scope.launch {
            shotLock.withLock {
                try {
                    val sorted = vm.sortShot(m, camCaps.filter { it.id in camSel && it.id != m.id })
                    if (camCaps.none { it.id == m.id }) return@withLock
                    camCaps = camCaps.map { if (it.id == m.id) sorted.shot else it }
                    if (sorted.refusal == null) camSel = camSel + m.id
                    else toaster.show(sorted.refusal.notice(), ToastKind.PROBLEM)
                } finally {
                    camChecking = camChecking - m.id
                }
            }
        }
    }
    // Gallery and file picks come straight back to these details, attached.
    val galleryPick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICKS)) { uris ->
        if (uris.isNotEmpty()) scope.launch { attach(describeVisualMedia(context, uris)) }
    }
    val filePick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch { vm.attachFiles(describeAttachments(context, uris)) }
    }

    // The viewer's Download: the system's save dialog, then the file is copied where the user chose.
    var downloading by remember(asset.id) { mutableStateOf<Media?>(null) }
    val mediaSave = rememberLauncherForActivityResult(SaveMediaAs()) { uri ->
        downloading?.let { vm.downloadMedia(it, uri?.toString()) }
        downloading = null
    }

    val actions = AssetDetailActions(
        onBack = onBack,
        onChangeLoc = onChangeLoc,
        onAssign = onAssign,
        assignToMe = assignToMe,
        onEdit = vm::edit,
        onDiscardEdits = vm::discardEdits,
        onRefresh = vm::refresh,
        onSave = vm::save,
        onSubmit = vm::submit,
        onMoveToDraft = vm::moveToDraft,
        onMarkValidated = vm::markValidated,
        onMove = onMove,
        moveReason = moveReason,
        assignReason = assignReason,
        onCreate = { submit ->
            val serial = state.value("serial")
            onCreate(
                state.value("name"), if (serial.isNotEmpty()) "S/N $serial" else state.value("model"),
                state.newPicks, state.values, submit, submit && !state.complete, state.pendingPlace,
                state.asset?.media?.takeIf { !state.sortingPhotos },
            )
        },
        onValidate = onValidate,
        onOpenResult = { jobId -> onOpenResult(jobId, asset.id) },
        onDelete = { onDelete(asset.id) },
        changeRequest = ChangeRequestActions(
            onOpen = vm::openChangeRequest, onNote = vm::editChangeNote, onSend = vm::sendChangeRequest,
            onCancel = vm::cancelChangeRequest, onClose = vm::closeChangeRequest, onReopen = vm::reopenAsset,
        ),
        onAutofill = vm::openAi,
        ai = AiDialogActions(
            onClose = vm::closeAi, onIdentity = vm::editAiIdentity, onLookUp = vm::lookUp, onChangeModel = vm::changeAiModel,
            onToggle = vm::toggleAiPick, onToggleType = vm::toggleAiType, onEdit = vm::editAiPick, onApply = vm::applyAi,
            onToggleJacs = vm::toggleAiJacs,
        ),
        // A review closed without applying it opens again from the mic, rather than listening anew.
        onMic = { if (state.voiceKept) vm.reopenVoice() else voice.toggle() },
        onType = { vm.openTyping() },
        voice = VoiceFillActions(
            onToggle = vm::toggleVoicePick, onApply = vm::applyVoice, onClose = vm::closeVoice,
            onSpeakAgain = {
                vm.discardVoice()
                voice.toggle()
            },
            onEditText = { vm.openTyping(state.voice?.said.orEmpty()) },
        ),
        typing = DescribeActions(onText = vm::setTyping, onFill = vm::submitTyping, onClose = vm::closeTyping),
        onTakePhoto = { camOpen = true },
        onRecordMemo = { memoOpen = true },
        onPickGallery = { galleryPick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
        // Excel, CSV, PDF, Word and text only (checked again, with the 10 MB limit, once picked).
        onPickFiles = { filePick.launch(ATTACHABLE_FILE_TYPES.toTypedArray()) },
        // Documents' +: the same files as Media's Add file, kept the same way.
        onUploadDocument = { filePick.launch(ATTACHABLE_FILE_TYPES.toTypedArray()) },
        onCommentChange = vm::editComment,
        onPostComment = vm::postComment,
        onUpdateComment = vm::updateComment,
        onRemoveComment = vm::removeComment,
        onRevalidate = vm::revalidate,
        onRemoveDocument = vm::removeDocument,
        onOpenDocument = vm::openDocument,
        onPreviewSource = vm::previewSource,
        onDownloadMedia = { m ->
            // A file the offline database doesn't have yet is saved from the server's copy (fetched first if need be).
            scope.launch {
                val file = mediaVm.downloadable(asset.id, m)
                downloading = file
                mediaSave.launch(file)
            }
        },
        onRemoveMedia = { vm.removeMedia(it, onDropCapture) },
        onMovePhoto = vm::movePhoto,
        onSuggestType = vm::openClassify,
        onOffline = { onNotice(UiText.Res(it.message)) },
        onOpenClone = vm::openClone,
        onClone = vm::clone,
        onCloseClone = vm::closeClone,
        addField = AddFieldActions(onOpen = vm::openAddField, onAdd = vm::addOwnField, onNameChange = vm::clearAddFieldError, onClose = vm::closeAddField, onRemove = vm::removeOwnField),
        jacs = JacsActions(
            onOpen = vm::openJacs,
            picker = JacsPickerActions(onQuery = vm::searchJacs, onMore = vm::moreJacs, onPick = vm::pickJacs, onClose = vm::closeJacs),
        ),
        classify = ClassifyActions(
            onPickType = vm::pickSuggestedType, onPickCode = vm::pickSuggestedCode,
        ),
        // NFC is checked first: switched off (or missing), the message shows without the NFC dialog flashing up.
        tags = TagsActions(
            onStart = { kind -> tagVm.start(kind, if (kind == TagKind.NFC) NfcTagReader.problem(context) else null) },
            onPlacement = tagVm::choosePlacement,
            onDismissPlacement = tagVm::cancel,
        ),
        onAddPhoto = { kind ->
            // What the camera takes goes to [kind]'s card; Camera (no kind) puts it in Other photos as it is, unsorted.
            // Photo, video and gallery are in the camera.
            if (kind != null) vm.choosePhotoKind(kind) else vm.chooseOtherPhotos()
            camVideo = false
            camOpen = true
        },
        // The Info tab's +: no kind given, so each photo is read on the device and sorted into its card.
        onAddSortedPhoto = {
            vm.choosePhotoKind(null)
            camVideo = false
            camOpen = true
        },
    )
    // The asset's media is shown from the offline database, or from the server while the sync hasn't brought it down.
    val mediaSources = remember(asset.id, mediaVm) { MediaSources { m, preview -> mediaVm.source(asset.id, m, preview) } }
    CompositionLocalProvider(LocalMediaSources provides mediaSources) {
        AssetDetailScreen(state, actions, mic = voice.state, tags = tagState)
    }
    TagFlowHost(
        tagState,
        TagFlowActions(
            onCancel = tagVm::cancel, onScan = tagVm::scanned, onScannerFailure = tagVm::scannerFailed, onNfcRead = tagVm::nfcRead,
            onNfcFailure = tagVm::nfcFailed, onConfirmRescan = tagVm::confirmRescan, onConfirmMove = tagVm::confirmMove,
            onToastShown = tagVm::toastShown,
        ),
    )

    // A photo or video the asset was read from, in the viewer the asset's own media use (no Download or Remove: it isn't
    // one of the asset's media, and source files aren't removed here).
    val sourcePreview by vm.sourcePreview.collectAsStateWithLifecycle()
    sourcePreview?.let { m ->
        BackHandler(onBack = vm::closeSourcePreview)
        PhotoViewer(m, onClose = vm::closeSourcePreview, onDownload = null, onRemove = null)
    }

    if (memoOpen) {
        VoiceMemoSheet(onSaved = { memo -> attach(listOf(memo)) }, onClose = { memoOpen = false }, onFailed = onNotice)
    }

    if (camOpen) {
        // The camera sits above the details, so its back button is checked first.
        BackHandler { closeCamera() }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            CameraCapture(
                // Not under the crop screen.
                active = cropShot == null,
                addingToAsset = true,
                captures = camCaps, selected = camSel,
                videoMode = camVideo, onVideoMode = { camVideo = it },
                // Each shot joins the strip and is read on the device as it is taken. Done adds the ticked ones.
                onCapture = { m ->
                    // Saved only after the camera was closed: it isn't wanted.
                    if (!camOpen) vm.discardShot(m)
                    else {
                        camCaps = camCaps + m
                        sortIntoStrip(m)
                    }
                },
                // A photo in the strip can be cropped before it is added, as in the Camera tab.
                onCrop = { m -> if (!m.video && camCaps.any { it.id == m.id }) cropShot = m },
                checking = camChecking,
                onToggle = { id ->
                    val m = camCaps.firstOrNull { it.id == id }
                    if (id in camChecking) Unit // ticked by itself when it fits
                    else if (id in camSel || m == null) camSel = camSel - id
                    else scope.launch {
                        shotLock.withLock {
                            val refusal = vm.shotRefusal(m, camCaps.filter { it.id in camSel })
                            if (refusal == null) camSel = camSel + id else toaster.show(refusal.notice(), ToastKind.PROBLEM)
                        }
                    }
                },
                onGalleryPicked = { uris ->
                    if (uris.isNotEmpty()) scope.launch {
                        attach(describeVisualMedia(context, uris))
                        camOpen = false
                    }
                },
                onClose = ::closeCamera,
                galleryLimit = vm.galleryLimit(),
                // The ticked shots join the asset and the unticked ones go, as in the Camera tab.
                onDone = {
                    attach(camCaps.filter { it.id in camSel })
                    camCaps = camCaps.filter { it.id !in camSel }
                    discardStrip()
                },
            )
        }
        // The cropped photo takes the original's place in the strip and is read again (what it shows changed, so its kind
        // may have); the original's file goes. Its own back button closes it before the camera's.
        cropShot?.let { shot ->
            CropShotScreen(shot, onCancel = { cropShot = null }) { cropped ->
                cropShot = null
                if (camCaps.none { it.id == shot.id }) {
                    vm.discardShot(cropped)
                    return@CropShotScreen
                }
                camCaps = camCaps.map { if (it.id == shot.id) cropped else it }
                camSel = camSel - shot.id
                vm.discardShot(shot)
                sortIntoStrip(cropped)
            }
        }
        if (askDiscardShots) {
            DiscardChangesDialog(
                onKeep = { askDiscardShots = false }, onDiscard = ::discardStrip,
                title = stringResource(R.string.camera_discard_title), body = stringResource(R.string.camera_discard_body_asset),
                keep = stringResource(R.string.camera_discard_keep),
            )
        }
    }
}

private const val MAX_PICKS = 20

/** How many photos (not videos) of [caps] are ticked in [selected]. */
