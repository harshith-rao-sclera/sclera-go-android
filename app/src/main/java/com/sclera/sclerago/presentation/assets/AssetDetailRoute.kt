package com.sclera.sclerago.presentation.assets

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
import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.services.media.describeAttachments
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
    prefill: Map<String, String>,
    onBack: () -> Unit,
    onChangeLoc: () -> Unit,
    onCreate: (name: String, detail: String, media: List<Media>, values: Map<String, String>, submit: Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onNotice: (UiText) -> Unit,
    vm: AssetDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(asset.id, isNew) { vm.open(asset, isNew, prefill) }
    DisposableEffect(vm) { onDispose { vm.close() } }
    LaunchedEffect(vm) { vm.notices.collect(onNotice) }
    val state by vm.state.collectAsStateWithLifecycle()
    // The view model catches up one frame after the asset changes; until then there is nothing to show.
    if (state.asset?.id != asset.id) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun attach(media: List<Media>) = vm.attach(media)

    // "Add photo" opens the same camera as the Camera tab; its captures attach to this asset.
    var camOpen by remember(asset.id) { mutableStateOf(false) }
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

    val actions = AssetDetailActions(
        onBack = onBack,
        onChangeLoc = onChangeLoc,
        onEdit = vm::edit,
        onDiscardEdits = vm::discardEdits,
        onSave = vm::save,
        onSubmit = vm::submit,
        onCreate = { submit ->
            val serial = state.value("serial")
            onCreate(
                state.value("name"), if (serial.isNotEmpty()) "S/N $serial" else state.value("model"),
                state.newPicks, state.values, submit,
            )
        },
        onValidate = vm::validate,
        onDelete = { onDelete(asset.id) },
        onAutofill = vm::autofill,
        onTakePhoto = { camOpen = true },
        onPickGallery = { galleryPick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
        onPickFiles = { filePick.launch(arrayOf("*/*")) },
    )
    AssetDetailScreen(state, actions)

    if (camOpen) {
        // The camera sits above the details, so its back button is checked first.
        BackHandler { camOpen = false }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            CameraCapture(
                active = true,
                captures = camCaps, selected = camSel,
                videoMode = camVideo, onVideoMode = { camVideo = it },
                onCapture = { m -> attach(listOf(m)); camOpen = false },
                onToggle = { id -> camSel = if (id in camSel) camSel - id else camSel + id },
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
