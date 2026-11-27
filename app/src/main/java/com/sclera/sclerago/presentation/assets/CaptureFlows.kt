package com.sclera.sclerago.presentation.assets

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.CircularProgressIndicator
import com.sclera.sclerago.presentation.components.SkeletonBlock
import com.sclera.sclerago.services.media.MediaLoader
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.runtime.mutableStateListOf
import androidx.camera.view.PreviewView
import com.sclera.sclerago.presentation.components.pressClickable
import com.sclera.sclerago.presentation.components.LocalToaster
import com.sclera.sclerago.presentation.components.ToastKind
import com.sclera.sclerago.presentation.common.UiText
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Size
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.video.AudioConfig
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import com.sclera.sclerago.presentation.components.CameraPreview
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.presentation.components.MediaImage
import com.sclera.sclerago.presentation.components.AccessGate
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.PullRefresh
import com.sclera.sclerago.presentation.components.RoundButton
import com.sclera.sclerago.presentation.components.SegHeader
import com.sclera.sclerago.presentation.components.fabShadow
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.fileimport.FileImportReview
import com.sclera.sclerago.presentation.fileimport.FileStep
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
fun CameraPage(vm: AppViewModel, shots: FileImportViewModel, online: Boolean) {
    val s = vm.state
    // Many assets sends the shots to be read as files are, which only AI does: the switch is read again as the camera opens.
    LaunchedEffect(s.camOpen) { if (s.camOpen) shots.refreshAi() }
    // Many assets: the shots handed over are read by the server, as files are on the File tab.
    LaunchedEffect(s.camToSend) {
        s.camToSend?.let { sent ->
            shots.readShots(sent.map { it.uri })
            vm.shotsSent()
        }
    }
    when {
        s.camReview -> FileImportReview(
            shots, onAddFiles = vm::backToCamera, onClose = vm::closeCamReview, flash = vm::flash, online = online,
            addMore = stringResource(R.string.camera_take_more),
        )
        // Shots still waiting in the review (before they are read) can be gone back to from the camera.
        s.camOpen -> CameraView(
            vm, waiting = if (s.camMany && shots.state.step == FileStep.PICK) shots.state.files.size else 0, onDiscardReview = shots::discard,
            aiOn = shots.aiOn,
        )
        else -> SubList(vm, Tab.CAMERA, stringResource(R.string.nav_camera), vm::openCam)
    }
}

private fun hasCameraPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

@Composable
private fun CameraView(vm: AppViewModel, waiting: Int, onDiscardReview: () -> Unit, aiOn: Boolean) {
    val s = vm.state
    val cap by vm.newAssetPhotoCap.collectAsStateWithLifecycle()
    BackHandler(enabled = vm.cameraHasShots) { vm.cancelCam() }
    Box(Modifier.fillMaxSize()) {
        CameraCapture(
            // Only run the camera while this tab is actually on screen (the pager also composes neighbours), and not
            // under the crop screen.
            active = s.tab == Tab.CAMERA && s.pv == null && s.sheet == Sheet.NONE && !vm.showDetail && s.cropShot == null,
            captures = s.captures, selected = s.camSel,
            videoMode = s.videoMode, onVideoMode = vm::setVideoMode,
            onCapture = vm::addCapture, onToggle = vm::toggleStrip, onGalleryPicked = vm::addPicked,
            onClose = vm::cancelCam, onDone = { vm.doneCapturing(reviewWaiting = waiting > 0) },
            many = s.camMany, onMany = { vm.setCamMany(it, reviewWaiting = waiting > 0, discardReview = onDiscardReview) }, waiting = waiting,
            manyAiOff = !aiOn, onManyBlocked = { vm.flash(UiText.Res(R.string.ai_off)) },
            // A photo in the strip can be cropped before it makes the asset (Single) or is sent to be read (Multiple).
            onCrop = vm::openCrop,
            checking = s.camSorting,
            // Single: one asset takes up to every photo kind's limit together; the shutter waits for an untick at it.
            maxPhotos = cap.takeUnless { s.camMany },
            onFull = { cap?.let(vm::cameraFull) },
        )
    }
}

/** The most the camera's gallery lets be picked at once. */
private const val GALLERY_MAX = 20

/** Multiple's videos are recorded at 720p (or the nearest the camera has), small enough to send as they are. */
private val MANY_VIDEO_QUALITY = QualitySelector.from(Quality.HD, FallbackStrategy.higherQualityOrLowerThan(Quality.HD))

/** Where a Multiple video stops: just under the server's upload limit ([MAX_DOCUMENT_UPLOAD_MB]), with room for the file's ending. */
private const val MANY_VIDEO_MAX_BYTES = (MAX_DOCUMENT_UPLOAD_MB - 2) * 1024L * 1024L

/** The size the strip's thumbnails are read at (pixels). */
private const val STRIP_PX = 240

/** The largest photo taken, 4:3 (about 5 MP): the camera's biggest size up to this, never its full 12 MP or more. */
private const val PHOTO_MAX_WIDTH = 2560
private const val PHOTO_MAX_HEIGHT = 1920

/**
 * Photos smaller than the sensor's full size: each shot is quicker to take and save (several in a row don't queue as
 * long), and quicker to upload and for the AI to read, while a nameplate taken close up stays readable. The camera's
 * largest 4:3 size up to [PHOTO_MAX_WIDTH] x [PHOTO_MAX_HEIGHT] (a Galaxy Z Fold3 offers 1920 x 1440 there); one with
 * none that small takes its smallest above.
 */
private fun photoResolution(): ResolutionSelector = ResolutionSelector.Builder()
    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
    .setResolutionStrategy(
        ResolutionStrategy(Size(PHOTO_MAX_WIDTH, PHOTO_MAX_HEIGHT), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER),
    )
    .build()

/** A shot being saved, shown in the strip by [frame] (what the preview showed as it was taken; null: none). */
private class PendingShot(val id: String, val frame: ImageBitmap?) {
    /** Its crop badge was tapped before it was saved: it opens to crop as soon as it is. */
    var cropAsked by mutableStateOf(false)
}

/** Over a strip shot still being saved or checked against the photo rules: dimmed, with a small spinner. */
@Composable
private fun StripChecking() {
    Box(Modifier.fillMaxSize().background(Color(0x73141518)), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(22.dp), color = C.White, strokeWidth = 2.5.dp)
    }
}

/** The yellow crop badge in a strip photo's top corner. */
@Composable
private fun BoxScope.CropBadge(onClick: () -> Unit) {
    val cropLabel = stringResource(R.string.crop_open)
    Box(
        Modifier.align(Alignment.TopEnd).padding(3.dp).size(28.dp).clip(CircleShape).background(C.Yellow)
            .pressClickable(shape = CircleShape, onClick = onClick).semantics { contentDescription = cropLabel },
        contentAlignment = Alignment.Center,
    ) { LineIcon(Icons.Crop, 15.dp, C.OnYellow, 2.4f) }
}

/** What [view] shows now, small enough for the strip; null when there is nothing to take. Main thread. */
private fun previewFrame(view: PreviewView?): ImageBitmap? {
    val full = runCatching { view?.bitmap }.getOrNull() ?: return null
    val scale = STRIP_PX.toFloat() / minOf(full.width, full.height)
    if (scale >= 1f) return full.asImageBitmap()
    val small = Bitmap.createScaledBitmap(full, (full.width * scale).toInt().coerceAtLeast(1), (full.height * scale).toInt().coerceAtLeast(1), true)
    if (small !== full) full.recycle()
    return small.asImageBitmap()
}

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
    /** The Camera tab's One asset (false) / Many assets (true) switch; null (an asset's Add photo): no switch. */
    many: Boolean? = null,
    onMany: (Boolean) -> Unit = {},
    /**
     * The property's AI is off: Many assets (its shots are read by the server with AI, as the File tab's photos) is greyed
     * out, and a tap calls [onManyBlocked] to say why, as the web's AI buttons.
     */
    manyAiOff: Boolean = false,
    onManyBlocked: () -> Unit = {},
    /** Multiple: shots already waiting in the review ("Take more"); the tick then shows and goes back to it. */
    waiting: Int = 0,
    /** Opened from an asset (its Add photo): the shots join that asset, so the hint says so instead of "start a new asset". */
    addingToAsset: Boolean = false,
    /** Crops a photo of the strip (its crop badge); null: no badge. Videos never have one. */
    onCrop: ((Media) -> Unit)? = null,
    /**
     * Shots of the strip still being read and checked against the photo rules: a spinner instead of a tick, and not in
     * the count until they are ticked (so it never shows more than the asset can take). Done waits for them.
     */
    checking: Set<String> = emptySet(),
    /**
     * The most photos the shots may come to (ticked, being saved or being checked); at it the shutter is greyed out for
     * photos and a tap calls [onFull] instead, until one is unticked. Null: no limit. Videos aren't counted.
     */
    maxPhotos: Int? = null,
    onFull: () -> Unit = {},
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val activity = LocalActivity.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // Photos taken and still being saved: in the count at once, so it keeps up with a quick burst of shots.
    var saving by remember { mutableIntStateOf(0) }
    // The same shots in the strip, in the order taken: a frame of the preview stands in for each until its photo is saved
    // and its thumbnail read, so the strip keeps up with the count.
    val pending = remember { mutableStateListOf<PendingShot>() }
    // Saved shots whose thumbnail is still being read: the preview's frame taken with each, shown until it is.
    val frames = remember { mutableStateMapOf<String, ImageBitmap>() }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    // Done tapped while some were still being saved: it goes on once they are all in, so none is left behind.
    var doneAsked by remember { mutableStateOf(false) }
    LaunchedEffect(saving, doneAsked, checking) {
        if (doneAsked && saving == 0 && checking.isEmpty()) {
            doneAsked = false
            onDone()
        }
    }


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
            imageCaptureResolutionSelector = photoResolution()
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
    // Multiple sends each video to the server as it is, so it is recorded small (720p) to begin with; Single's are
    // re-encoded when they join the asset, so they keep the camera's usual quality.
    LaunchedEffect(many) {
        controller.videoCaptureQualitySelector = if (many == true) MANY_VIDEO_QUALITY else Recorder.DEFAULT_QUALITY_SELECTOR
    }
    LaunchedEffect(backLens) {
        controller.cameraSelector = if (backLens) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
    }
    // Flash on keeps the light on while aiming, for photos as for video; the photo is taken in that light. Set again when the
    // camera is rebound (mode, lens, back on screen), once it is ready (the first time it opens it isn't yet).
    LaunchedEffect(flashOn, videoMode, backLens, active) {
        controller.imageCaptureFlashMode = ImageCapture.FLASH_MODE_OFF
        val on = flashOn && active
        controller.initializationFuture.addListener({ controller.enableTorch(on) }, ContextCompat.getMainExecutor(context))
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
        // Counted from the tap: the camera saves shots one after another, so a quick burst lands a moment later.
        saving++
        lateinit var shot: PendingShot
        // The picture is asked for first, so nothing holds up the shutter.
        controller.takePicture(
            ImageCapture.OutputFileOptions.Builder(f).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                // Shots go straight into the strip as soon as they are saved (several can be taken in a row); one not
                // wanted is unticked there. Until its thumbnail is read, the preview's frame stays in its place.
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val m = Media("c-${f.name}", Uri.fromFile(f).toString(), video = false, owned = true)
                    shot.frame?.let { frames[m.id] = it }
                    onCapture(m)
                    pending.remove(shot)
                    if (shot.cropAsked) onCrop?.invoke(m)
                    saving--
                    scope.launch {
                        MediaLoader.load(context, m.uri, video = false, maxPx = STRIP_PX)
                        frames.remove(m.id)
                    }
                }

                override fun onError(e: ImageCaptureException) {
                    pending.remove(shot)
                    saving--
                    f.delete()
                    toaster.show(UiText.Res(R.string.camera_photo_failed), ToastKind.PROBLEM)
                }
            },
        )
        // Then what the preview shows now stands in for it in the strip until it is saved (the callbacks above come later,
        // on this same thread).
        shot = PendingShot("p-${f.name}", previewFrame(previewView))
        pending += shot
    }

    fun toggleRecording() {
        recording?.let { it.stop(); return }
        val f = File(captureDir(context), "VID_${stamp()}.mp4")
        recNanos = 0L
        // A video stops on its own, and what was recorded is kept: an asset's at 10 seconds (MAX_CLIP_MS); Multiple's, which
        // go straight to the server as files, have no time limit and stop just under its upload limit instead.
        val output = FileOutputOptions.Builder(f).apply {
            if (many == true) setFileSizeLimit(MANY_VIDEO_MAX_BYTES) else setDurationLimitMillis(MAX_CLIP_MS)
        }.build()
        recording = controller.startRecording(
            output,
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
                        if (ev.error == VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED) {
                            toaster.show(UiText.Res(R.string.camera_video_size_limit, listOf(MAX_DOCUMENT_UPLOAD_MB)), ToastKind.INFO)
                        }
                    } else {
                        f.delete()
                        toaster.show(UiText.Res(R.string.camera_video_too_short), ToastKind.PROBLEM)
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
    // Photos taken that count or may yet: ticked, still being saved, or still being checked.
    val photosTaken = captures.count { !it.video && (it.id in selected || it.id in checking) } + saving
    val photosFull = maxPhotos != null && !videoMode && photosTaken >= maxPhotos

    Box(Modifier.fillMaxSize().background(Color.Black).clipToBounds()) {
        if (granted) {
            CameraPreview(controller, Modifier.fillMaxSize(), onView = { previewView = it })
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
                        // Multiple's videos have no time limit, so only how long it has run.
                        val elapsed = formatDuration(recNanos / 1_000_000)
                        Txt(if (many == true) elapsed else elapsed + " / " + formatDuration(MAX_CLIP_MS), 12, 700, C.Red)
                    }
                } else if (many != null) {
                    // Single / Multiple asset(s), between close and flash.
                    Row(Modifier.clip(Capsule).background(Glass).padding(3.dp)) {
                        ModeChip(stringResource(R.string.camera_single), on = !many) { onMany(false) }
                        AiOffGate(manyAiOff && !many, onManyBlocked) {
                            ModeChip(stringResource(R.string.camera_multiple), on = many) { onMany(true) }
                        }
                    }
                }
            }
            RoundButton(48.dp, Glass, { flashOn = !flashOn }) {
                LineIcon(if (flashOn) Icons.Flash else Icons.FlashOff, 20.dp, if (flashOn) C.Yellow else C.Text)
            }
        }

        // Multiple after "Take more": the shots already in the review count too, and the tick goes back to it.
        // Only ticked shots count: one still being saved or checked may yet be turned away (the asset's photos are full).
        val total = chosen + waiting
        val busy = saving > 0 || checking.isNotEmpty()
        if ((total > 0 || busy) && !isRecording) {
            // Done: a yellow tick, with how many shots are ticked in a badge on its top corner (read out as one button).
            // Single opens the new asset; Multiple opens the review, where shots can be removed before they are sent.
            val done = pluralStringResource(R.plurals.camera_use_shots, total, total)
            Box(
                Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 228.dp)
                    .semantics(mergeDescendants = true) { contentDescription = done },
            ) {
                Box(
                    Modifier.size(60.dp).fabShadow().pressClickable(shape = CircleShape) { if (busy) doneAsked = true else onDone() }
                        .background(C.Yellow),
                    contentAlignment = Alignment.Center,
                ) { LineIcon(Icons.Check, 26.dp, C.OnYellow, 3f) }
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).defaultMinSize(minWidth = 24.dp).height(24.dp)
                        .clip(Capsule).background(C.Bg).border(2.dp, C.Yellow, Capsule).padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center,
                ) { Txt(total.toString(), 12, 800, C.Yellow, maxLines = 1) }
            }
        }

        // Captures strip
        if (captures.isEmpty() && pending.isEmpty()) {
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 160.dp).clip(Capsule).background(Glass)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                val hint = if (addingToAsset) R.string.capture_add_hint else R.string.capture_start_hint
                Txt(stringResource(hint), 11, 600, C.Soft, maxLines = Int.MAX_VALUE)
            }
        } else {
            LazyRow(
                Modifier.align(Alignment.BottomStart).padding(bottom = 136.dp).fillMaxWidth().height(78.dp),
                contentPadding = PaddingValues(horizontal = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                items(captures, key = { it.id }) { m ->
                    val on = m.id in selected
                    Box(Modifier.size(78.dp).pressClickable(shape = r(4)) { onToggle(m.id) }) {
                        val frame = frames[m.id]
                        if (frame != null) Image(frame, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else MediaImage(m, Modifier.fillMaxSize(), maxPx = STRIP_PX)
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
                        if (m.id in checking) StripChecking()
                        else if (on) {
                            Box(Modifier.fillMaxSize().background(Color(0x73141518)), contentAlignment = Alignment.Center) {
                                LineIcon(Icons.Check, 28.dp, C.White, 2.6f)
                            }
                        }
                        // Crop: a yellow badge in the photo's corner opens it to crop.
                        if (onCrop != null && !m.video) CropBadge { onCrop(m) }
                    }
                }
                // Shots still being saved: the preview's frame (a placeholder when there was none), with a spinner.
                // Their crop badge shows at once too; tapped, the photo opens to crop as soon as it is saved.
                items(pending, key = { it.id }) { p ->
                    Box(Modifier.size(78.dp).clip(r(4))) {
                        p.frame?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                            ?: SkeletonBlock(Modifier.fillMaxSize(), RectangleShape)
                        StripChecking()
                        if (onCrop != null) CropBadge { p.cropAsked = true }
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
                        toaster.show(UiText.Res(R.string.camera_room_full, listOf(0)), ToastKind.PROBLEM)
                    galleryLimit == 1 -> galleryOne.launch(PickVisualMediaRequest(only))
                    else -> gallery.launch(PickVisualMediaRequest(only))
                }
            }) { LineIcon(Icons.Gallery, 22.dp, C.Text) }
            Box(
                // At the photo limit the shutter is greyed out, and a tap says to untick one first.
                Modifier.size(76.dp).alpha(if (granted && !photosFull) 1f else 0.4f).pressClickable(shape = CircleShape, enabled = granted) {
                    when {
                        videoMode -> toggleRecording()
                        photosFull -> onFull()
                        else -> takePhoto()
                    }
                }.border(4.dp, C.Text, CircleShape)
                    
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
            ModePill(stringResource(R.string.camera_mode_video), videoMode) { if (!isRecording) onVideoMode(true) }
            ModePill(stringResource(R.string.camera_mode_photo), !videoMode) { if (!isRecording) onVideoMode(false) }
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
        Txt(stringResource(R.string.camera_off_title), 16, 700)
        Txt(
            stringResource(if (blocked) R.string.camera_off_blocked else R.string.camera_off_ask),
            12, 400, C.Muted, maxLines = Int.MAX_VALUE, align = TextAlign.Center,
        )
        Pill(
            stringResource(if (blocked) R.string.camera_open_settings else R.string.camera_allow), onAllow,
            bg = C.Yellow, fg = C.OnYellow, height = 44.dp, hPad = 20.dp, size = 14, weight = 700,
        )
    }
}

/** One side of the camera's One asset / Many assets switch. */
@Composable
private fun ModeChip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(Capsule).pressClickable(shape = Capsule, onClick = onClick).background(if (on) C.Yellow else Color.Transparent)
            .heightIn(min = 32.dp).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Txt(label, 12, if (on) 800 else 600, if (on) C.OnYellow else C.Soft, maxLines = 1) }
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
        s.fileOpen -> FileWaiting(vm, files)
        else -> SubList(vm, Tab.FILE, stringResource(R.string.nav_file), vm::openFile, Icons.FileUploadBadge)
    }
}

/** Shown behind the system file manager while it is open. */
@Composable
private fun FileWaiting(vm: AppViewModel, files: FileImportViewModel) {
    // The AI switch is read again as the file manager opens: off, only Excel and CSV can be read.
    LaunchedEffect(Unit) { files.refreshAi() }
    Column(
        Modifier.fillMaxSize().background(Color(0xFF151619)).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        LineIcon(Icons.FileUpload, 40.dp, C.Yellow, 1.6f)
        Txt(stringResource(R.string.file_choose_title), 16, 700)
        Txt(
            stringResource(if (files.aiOn) R.string.file_import_hint else R.string.file_import_hint_ai_off, MAX_DOCUMENT_UPLOAD_MB),
            12, 400, C.Muted, maxLines = Int.MAX_VALUE, align = TextAlign.Center,
        )
        // Adding assets needs Collect: for a group without it Browse stays, greyed out, and a tap says why.
        AccessGate(vm.collectBlock) {
            Pill(stringResource(R.string.file_browse_files), vm::openFile, bg = C.Yellow, fg = C.OnYellow, height = 44.dp, hPad = 20.dp, size = 14, weight = 700)
        }
        Pill(stringResource(R.string.action_cancel), vm::cancelFile, bg = Color.Transparent, fg = C.Muted, height = 40.dp)
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
                SegHeader(listOf(stringResource(R.string.label_assets), label), 0, icons = listOf(null, labelIcon)) { if (it == 1) onOpen() }
            }
        }
    }
}

