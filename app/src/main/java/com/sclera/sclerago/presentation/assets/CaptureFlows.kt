package com.sclera.sclerago.presentation.assets

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.video.AudioConfig
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.limits.MAX_CLIP_MS
import com.sclera.sclerago.domain.model.MAX_DOCUMENT_UPLOAD_MB
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.formatDuration
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.presentation.components.MediaImage
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.PullRefresh
import com.sclera.sclerago.presentation.components.RoundButton
import com.sclera.sclerago.presentation.components.SegHeader
import com.sclera.sclerago.presentation.components.fabShadow
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.fileimport.FileImportReview
import com.sclera.sclerago.presentation.fileimport.FileImportViewModel
import com.sclera.sclerago.presentation.icons.Ico
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.main.AppViewModel
import com.sclera.sclerago.presentation.main.Sheet
import com.sclera.sclerago.presentation.main.Tab
import com.sclera.sclerago.presentation.main.rememberAssetListBinding
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import com.sclera.sclerago.services.media.captureDir
import com.sclera.sclerago.services.media.stamp
import java.io.File
import kotlinx.coroutines.launch

private val Glass = Color(0xB3141518) // rgba(20,21,24,0.7)

// ---------------------------------------------------------------- Camera

@Composable
fun CameraPage(vm: AppViewModel) {
    val s = vm.state
    when {
        s.camOpen -> CameraView(vm)
        else -> SubList(vm, Tab.CAMERA, "Camera", vm::openCam)
    }
}

private fun hasCameraPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

@Composable
private fun CameraView(vm: AppViewModel) {
    val s = vm.state
    BackHandler(enabled = vm.cameraHasShots) { vm.cancelCam() }
    CameraCapture(
        // Only run the camera while this tab is actually on screen (the pager also composes neighbours).
        active = s.tab == Tab.CAMERA && s.pv == null && s.sheet == Sheet.NONE && !vm.showDetail,
        captures = s.captures, selected = s.camSel,
        videoMode = s.videoMode, onVideoMode = vm::setVideoMode,
        onCapture = vm::addCapture, onToggle = vm::toggleStrip, onGalleryPicked = vm::addPicked,
        onClose = vm::cancelCam, onDone = vm::doneCapturing,
    )
}

/** The most the camera's gallery lets be picked at once. */
private const val GALLERY_MAX = 20

/**
 * The live camera screen: preview, photo/video capture, flash, lens switch, gallery import and the
 * strip of captures. Used by the Camera tab and by "Add photo" on an asset.
 */
@Composable
fun CameraCapture(
    active: Boolean,
    captures: List<Media>,
    selected: Set<String>,
    videoMode: Boolean,
    onVideoMode: (Boolean) -> Unit,
    onCapture: (Media) -> Unit,
    onToggle: (String) -> Unit,
    onGalleryPicked: (List<Uri>) -> Unit,
    onClose: () -> Unit,
    onDone: () -> Unit,
    /**
     * Opened for one kind of photo (a Media tab's Nameplate, Asset or Label ID): how many more photos that kind takes.
     * The gallery then shows photos only and lets that many be picked. Null: photos and videos, up to [GALLERY_MAX].
     */
    galleryLimit: Int? = null,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()


    // --- Permission
    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    var blocked by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        blocked = !ok && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
    }
    LaunchedEffect(active) { if (active && !granted) permLauncher.launch(Manifest.permission.CAMERA) }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) granted = hasCameraPermission(context) }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    // --- CameraX
    val controller = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            imageCaptureFlashMode = ImageCapture.FLASH_MODE_OFF
        }
    }
    var backLens by remember { mutableStateOf(true) }
    var flashOn by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var recNanos by remember { mutableLongStateOf(0L) }
    val shutterFlash = remember { Animatable(0f) }

    LaunchedEffect(videoMode) {
        controller.setEnabledUseCases(if (videoMode) CameraController.VIDEO_CAPTURE else CameraController.IMAGE_CAPTURE)
    }
    LaunchedEffect(backLens) {
        controller.cameraSelector = if (backLens) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
    }
    LaunchedEffect(flashOn, videoMode, active) {
        if (videoMode) {
            controller.imageCaptureFlashMode = ImageCapture.FLASH_MODE_OFF
            controller.enableTorch(flashOn && active)
        } else {
            controller.enableTorch(false)
            controller.imageCaptureFlashMode = if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        }
    }
    DisposableEffect(active, granted, owner) {
        if (active && granted) controller.bindToLifecycle(owner)
        onDispose {
            recording?.stop()
            controller.unbind()
        }
    }

    fun takePhoto() {
        val f = File(captureDir(context), "IMG_${stamp()}.jpg")
        scope.launch { shutterFlash.snapTo(0.8f); shutterFlash.animateTo(0f, tween(250)) }
        controller.takePicture(
            ImageCapture.OutputFileOptions.Builder(f).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                // Shots go straight into the strip (several can be taken in a row); one not wanted is unticked there.
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    onCapture(Media("c-${f.name}", Uri.fromFile(f).toString(), video = false, owned = true))
                }

                override fun onError(e: ImageCaptureException) {
                    f.delete()
                    Toast.makeText(context, "Couldn't take the photo. Try again.", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    fun toggleRecording() {
        recording?.let { it.stop(); return }
        val f = File(captureDir(context), "VID_${stamp()}.mp4")
        recNanos = 0L
        recording = controller.startRecording(
            // A video stops on its own at 10 seconds (MAX_CLIP_MS); what was recorded is kept.
            FileOutputOptions.Builder(f).setDurationLimitMillis(MAX_CLIP_MS).build(),
            AudioConfig.AUDIO_DISABLED,
            ContextCompat.getMainExecutor(context),
        ) { ev ->
            when (ev) {
                is VideoRecordEvent.Status -> recNanos = ev.recordingStats.recordedDurationNanos
                is VideoRecordEvent.Finalize -> {
                    recording = null
                    val ms = ev.recordingStats.recordedDurationNanos / 1_000_000
                    if (ev.error != VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA && f.exists() && f.length() > 0) {
                        onCapture(Media("c-${f.name}", Uri.fromFile(f).toString(), video = true, durationMs = ms, owned = true))
                    } else {
                        f.delete()
                        Toast.makeText(context, "Video too short — hold for a moment longer.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // The system picker takes at least 2 when several may be picked, so a kind with room for one picks a single photo.
    val gallery = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia((galleryLimit ?: GALLERY_MAX).coerceIn(2, GALLERY_MAX)),
    ) { onGalleryPicked(it) }
    val galleryOne = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> onGalleryPicked(listOfNotNull(uri)) }

    val chosen = captures.count { it.id in selected }
    val isRecording = recording != null

    Box(Modifier.fillMaxSize().background(Color.Black).clipToBounds()) {
        if (granted) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        this.controller = controller
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().alpha(shutterFlash.value).background(Color.White))
        } else {
            PermissionPrompt(
                blocked = blocked,
                onAllow = {
                    if (blocked) {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        )
                    } else permLauncher.launch(Manifest.permission.CAMERA)
                },
                modifier = Modifier.align(Alignment.Center).padding(bottom = 140.dp),
            )
        }

        // Top bar
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RoundButton(48.dp, Glass, { if (!isRecording) onClose() }) { LineIcon(Icons.Close, 22.dp, C.Text, 2.4f) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (isRecording) {
                    Row(
                        Modifier.clip(Capsule).background(Color(0xCC2A1919)).border(1.dp, Color(0xFF4A2827), Capsule)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.size(8.dp).background(C.Red, CircleShape))
                        Txt(formatDuration(recNanos / 1_000_000) + " / " + formatDuration(MAX_CLIP_MS), 12, 700, C.Red)
                    }
                }
            }
            RoundButton(48.dp, Glass, { flashOn = !flashOn }) {
                LineIcon(if (flashOn) Icons.Flash else Icons.FlashOff, 20.dp, if (flashOn) C.Yellow else C.Text)
            }
        }

        if (chosen > 0 && !isRecording) {
            Box(
                Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 228.dp).size(60.dp).fabShadow()
                    .clip(CircleShape).background(C.Yellow).clickable(onClick = onDone),
                contentAlignment = Alignment.Center,
            ) {
                LineIcon(Icons.Check, 26.dp, C.OnYellow, 3f)
                Txt(chosen.toString(), 11, 800, C.OnYellow, Modifier.align(Alignment.BottomEnd).padding(end = 9.dp, bottom = 6.dp))
            }
        }

        // Captures strip
        if (captures.isEmpty()) {
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 160.dp).clip(Capsule).background(Glass)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) { Txt("Take a photo or video to start a new asset", 11, 600, C.Soft) }
        } else {
            LazyRow(
                Modifier.align(Alignment.BottomStart).padding(bottom = 136.dp).fillMaxWidth().height(78.dp),
                contentPadding = PaddingValues(horizontal = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                items(captures, key = { it.id }) { m ->
                    val on = m.id in selected
                    Box(Modifier.size(78.dp).clip(r(4)).clickable { onToggle(m.id) }) {
                        MediaImage(m, Modifier.fillMaxSize(), maxPx = 240)
                        if (m.video) {
                            Row(
                                Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                LineIcon(Icons.Video, 14.dp, C.White)
                                Txt(m.durationText, 10, 700, C.White)
                            }
                        }
                        if (on) {
                            Box(Modifier.fillMaxSize().background(Color(0x73141518)), contentAlignment = Alignment.Center) {
                                LineIcon(Icons.Check, 28.dp, C.White, 2.6f)
                            }
                        }
                    }
                }
            }
        }

        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(130.dp).background(Color(0xB8141518)))
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 50.dp).fillMaxWidth().height(76.dp).padding(horizontal = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val side = Color(0xE63C3F45)
            RoundButton(52.dp, side, {
                // In Video mode the gallery shows only videos (they are easy to miss among the photos otherwise); for one
                // kind of photo it shows photos only, as many as that kind still takes.
                val only = when {
                    galleryLimit != null -> ActivityResultContracts.PickVisualMedia.ImageOnly
                    videoMode -> ActivityResultContracts.PickVisualMedia.VideoOnly
                    else -> ActivityResultContracts.PickVisualMedia.ImageAndVideo
                }
                when {
                    isRecording -> Unit
                    galleryLimit != null && galleryLimit <= 0 ->
                        Toast.makeText(context, context.getString(R.string.camera_room_full, 0), Toast.LENGTH_SHORT).show()
                    galleryLimit == 1 -> galleryOne.launch(PickVisualMediaRequest(only))
                    else -> gallery.launch(PickVisualMediaRequest(only))
                }
            }) { LineIcon(Icons.Gallery, 22.dp, C.Text) }
            Box(
                Modifier.size(76.dp).alpha(if (granted) 1f else 0.4f).clip(CircleShape).border(4.dp, C.Text, CircleShape)
                    .clickable(enabled = granted) { if (videoMode) toggleRecording() else takePhoto() }
                    .padding(9.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isRecording) Box(Modifier.size(26.dp).background(C.Red, r(6)))
                else Box(Modifier.fillMaxSize().background(if (videoMode) C.Red else C.Text, CircleShape))
            }
            RoundButton(52.dp, side, { if (!isRecording) backLens = !backLens }) { LineIcon(Icons.SwitchCamera, 22.dp, C.Text) }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).alpha(if (isRecording) 0.4f else 1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ModePill("Video", videoMode) { if (!isRecording) onVideoMode(true) }
            ModePill("Photo", !videoMode) { if (!isRecording) onVideoMode(false) }
        }
    }
}

@Composable
private fun PermissionPrompt(blocked: Boolean, onAllow: () -> Unit, modifier: Modifier) {
    Column(
        modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LineIcon(Icons.Camera, 40.dp, C.Muted, 1.8f)
        Txt("Camera access is off", 16, 700)
        Txt(
            if (blocked) "Turn on Camera for Sclera Go in Settings to photograph nameplates and assets."
            else "Allow the camera to photograph nameplates and assets.",
            12, 400, C.Muted, maxLines = 3, align = TextAlign.Center,
        )
        Pill(
            if (blocked) "Open settings" else "Allow camera", onAllow,
            bg = C.Yellow, fg = C.OnYellow, height = 44.dp, hPad = 20.dp, size = 14, weight = 700,
        )
    }
}

@Composable
private fun ModePill(label: String, on: Boolean, onClick: () -> Unit) {
    Pill(label, onClick, bg = if (on) C.Yellow else Color.Transparent, fg = if (on) C.OnYellow else C.Muted, hPad = 18.dp, size = 14, weight = 700)
}

// ---------------------------------------------------------------- File

@Composable
fun FilePage(vm: AppViewModel, files: FileImportViewModel, online: Boolean) {
    val s = vm.state
    when {
        s.revFile -> FileImportReview(files, onAddFiles = vm::backToFiles, onClose = vm::closeFiles, flash = vm::flash, online = online)
        s.fileOpen -> FileWaiting(vm)
        else -> SubList(vm, Tab.FILE, "File", vm::openFile, Icons.FileUploadBadge)
    }
}

/** Shown behind the system file manager while it is open. */
@Composable
private fun FileWaiting(vm: AppViewModel) {
    Column(
        Modifier.fillMaxSize().background(Color(0xFF1B1D21)).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        LineIcon(Icons.FileUpload, 40.dp, C.Muted, 1.8f)
        Txt("Import from files", 16, 700)
        Txt(stringResource(R.string.file_import_hint, MAX_DOCUMENT_UPLOAD_MB), 12, 400, C.Muted, maxLines = 3, align = TextAlign.Center)
        Pill("Open file manager", vm::openFile, bg = C.Yellow, fg = C.OnYellow, height = 44.dp, hPad = 20.dp, size = 14, weight = 700)
        Pill("Cancel", vm::cancelFile, bg = Color.Transparent, fg = C.Muted, height = 40.dp)
    }
}

// ---------------------------------------------------------------- Shared

/** Camera / File tab once the capture UI is closed: an Assets list with a way back in. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SubList(vm: AppViewModel, tab: Tab, label: String, onOpen: () -> Unit, labelIcon: Ico? = null) {
    val list = rememberAssetListBinding(vm, tab)
    PullRefresh(list.refreshing, list.onRefresh, Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            assetList(list) {
                SegHeader(listOf("Assets", label), 0, icons = listOf(null, labelIcon)) { if (it == 1) onOpen() }
            }
        }
    }
}

