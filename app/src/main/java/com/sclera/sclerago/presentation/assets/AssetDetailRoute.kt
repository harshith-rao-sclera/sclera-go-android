package com.sclera.sclerago.presentation.assets

import android.widget.Toast

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.LocValue
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.components.SaveMediaAs
import com.sclera.sclerago.services.media.describeAttachments
import com.sclera.sclerago.services.media.describeDocument
import com.sclera.sclerago.services.media.describeVisualMedia
import kotlinx.coroutines.launch

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
    onCreate: (name: String, detail: String, media: List<Media>, values: Map<String, String>, submit: Boolean, exception: Boolean, place: LocValue?) -> Unit,
    onDelete: (String) -> Unit,
    onNotice: (UiText) -> Unit,
    /** A place picked for this asset in the location sheet, to keep as an unsaved change; [onPlaceTaken] once kept. */
    pickedPlace: LocValue? = null,
    onPlaceTaken: () -> Unit = {},
    /** Assign the asset to a person (the web's Assign asset). */
    onAssign: () -> Unit = {},
    /** Validate: run checks on this asset (the web's asset bar opens Run validation on it). */
    onValidate: () -> Unit = {},
    vm: AssetDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(asset.id, isNew) { vm.open(asset, isNew) }
    DisposableEffect(vm) { onDispose { vm.close() } }
    LaunchedEffect(vm) { vm.notices.collect(onNotice) }
    LaunchedEffect(pickedPlace) {
        pickedPlace?.let {
            vm.choosePlace(it)
            onPlaceTaken()
        }
    }
    val state by vm.state.collectAsStateWithLifecycle()
    // The view model catches up one frame after the asset changes; until then there is nothing to show.
    if (state.asset?.id != asset.id) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun attach(media: List<Media>) = vm.attach(media)
    val voice = rememberVoiceInput(onHeard = vm::heard, onFailed = onNotice)

    // "Add photo" opens the same camera as the Camera tab; its captures attach to this asset.
    var camOpen by remember(asset.id) { mutableStateOf(false) }
    var memoOpen by remember(asset.id) { mutableStateOf(false) }
    var camCaps by remember(asset.id) { mutableStateOf(listOf<Media>()) }
    var camSel by remember(asset.id) { mutableStateOf(setOf<String>()) }
    var camVideo by remember(asset.id) { mutableStateOf(false) }
    // Gallery and file picks come straight back to these details, attached.
    val galleryPick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICKS)) { uris ->
        if (uris.isNotEmpty()) scope.launch { attach(describeVisualMedia(context, uris)) }
    }
    val filePick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch { attach(describeAttachments(context, uris)) }
    }
    // The Documents tab takes one file: an asset has one source document.
    val documentPick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val doc = describeDocument(context, uri)
            if (doc != null) vm.attachDocument(doc) else attach(describeAttachments(context, listOf(uri)))
        }
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
        onEdit = vm::edit,
        onDiscardEdits = vm::discardEdits,
        onSave = vm::save,
        onSubmit = vm::submit,
        onCreate = { submit ->
            val serial = state.value("serial")
            onCreate(
                state.value("name"), if (serial.isNotEmpty()) "S/N $serial" else state.value("model"),
                state.newPicks, state.values, submit, submit && !state.complete, state.pendingPlace,
            )
        },
        onValidate = onValidate,
        onDelete = { onDelete(asset.id) },
        changeRequest = ChangeRequestActions(
            onOpen = vm::openChangeRequest, onNote = vm::editChangeNote, onSend = vm::sendChangeRequest,
            onCancel = vm::cancelChangeRequest, onClose = vm::closeChangeRequest, onReopen = vm::reopenAsset,
        ),
        onAutofill = vm::openAi,
        ai = AiDialogActions(
            onClose = vm::closeAi, onIdentity = vm::editAiIdentity, onLookUp = vm::lookUp, onChangeModel = vm::changeAiModel,
            onToggle = vm::toggleAiPick, onToggleType = vm::toggleAiType, onEdit = vm::editAiPick, onApply = vm::applyAi,
        ),
        onMic = voice::toggle,
        voice = VoiceFillActions(
            onToggle = vm::toggleVoicePick, onApply = vm::applyVoice, onClose = vm::closeVoice,
            onSpeakAgain = {
                vm.closeVoice()
                voice.toggle()
            },
        ),
        onTakePhoto = { camOpen = true },
        onRecordMemo = { memoOpen = true },
        onPickGallery = { galleryPick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
        onPickFiles = { filePick.launch(arrayOf("*/*")) },
        onUploadDocument = { documentPick.launch(arrayOf("*/*")) },
        onCommentChange = vm::editComment,
        onPostComment = vm::postComment,
        onUpdateComment = vm::updateComment,
        onRemoveComment = vm::removeComment,
        onRevalidate = vm::revalidate,
        onRemoveDocument = vm::removeDocument,
        onDownloadMedia = { m ->
            downloading = m
            mediaSave.launch(m)
        },
        onRemoveMedia = vm::removeMedia,
        onSuggestType = vm::openClassify,
        onOffline = { onNotice(UiText.Res(it.message)) },
        jacs = JacsActions(
            onOpen = vm::openJacs,
            picker = JacsPickerActions(onQuery = vm::searchJacs, onMore = vm::moreJacs, onPick = vm::pickJacs, onClose = vm::closeJacs),
        ),
        classify = ClassifyActions(
            onPickType = vm::pickSuggestedType, onPickCode = vm::pickSuggestedCode,
        ),
        onAddPhoto = { kind ->
            // What the camera takes goes to [kind]'s card (Other photos for none); photo, video and gallery are in the camera.
            vm.choosePhotoKind(kind)
            camVideo = false
            camOpen = true
        },
    )
    AssetDetailScreen(state, actions, mic = voice.state)

    if (memoOpen) {
        VoiceMemoSheet(onSaved = { memo -> attach(listOf(memo)) }, onClose = { memoOpen = false }, onFailed = onNotice)
    }

    if (camOpen) {
        // The camera sits above the details, so its back button is checked first.
        BackHandler { camOpen = false }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            CameraCapture(
                active = true,
                captures = camCaps, selected = camSel,
                videoMode = camVideo, onVideoMode = { camVideo = it },
                // Each shot joins the strip, ticked while the kind (or asset) has room; Done adds the ticked ones.
                onCapture = { m ->
                    camCaps = camCaps + m
                    if (m.video || selectedPhotos(camCaps, camSel) < vm.cameraRoom()) camSel = camSel + m.id
                    else Toast.makeText(context, context.getString(R.string.camera_room_full, vm.cameraRoom()), Toast.LENGTH_SHORT).show()
                },
                onToggle = { id ->
                    val m = camCaps.firstOrNull { it.id == id }
                    camSel = when {
                        id in camSel -> camSel - id
                        m == null || m.video || selectedPhotos(camCaps, camSel) < vm.cameraRoom() -> camSel + id
                        else -> {
                            Toast.makeText(context, context.getString(R.string.camera_room_full, vm.cameraRoom()), Toast.LENGTH_SHORT).show()
                            camSel
                        }
                    }
                },
                onGalleryPicked = { uris ->
                    if (uris.isNotEmpty()) scope.launch {
                        attach(describeVisualMedia(context, uris))
                        camOpen = false
                    }
                },
                onClose = { camOpen = false },
                onDone = {
                    attach(camCaps.filter { it.id in camSel })
                    camCaps = camCaps.filter { it.id !in camSel }
                    camSel = emptySet()
                    camOpen = false
                },
            )
        }
    }
}

private const val MAX_PICKS = 20

/** How many photos (not videos) of [caps] are ticked in [selected]. */
private fun selectedPhotos(caps: List<Media>, selected: Set<String>): Int = caps.count { it.id in selected && !it.video }
