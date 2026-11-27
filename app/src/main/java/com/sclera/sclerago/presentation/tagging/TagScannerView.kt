package com.sclera.sclerago.presentation.tagging

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ZoomState
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sclera.sclerago.domain.model.TagScannerFailure
import com.sclera.sclerago.domain.model.TagKind
import com.sclera.sclerago.domain.usecase.tagging.TagScan
import com.sclera.sclerago.presentation.components.CameraPreview
import com.sclera.sclerago.services.tagging.TagFrameAnalyzer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * The camera preview that reads a QR code or a barcode ([TagFrameAnalyzer]). It asks for the camera at the moment it opens and
 * reports [TagScannerFailure.CAMERA_DENIED] if refused; the camera is released when it leaves the screen. The zoom shows
 * [zoomBadgeTop] down while pinching.
 */
@Composable
fun TagScannerView(
    kind: TagKind,
    torchOn: Boolean,
    onScan: (TagScan) -> Unit,
    onFailure: (TagScannerFailure) -> Unit,
    modifier: Modifier = Modifier,
    zoomBadgeTop: Dp = 72.dp,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scanner: TagScannerViewModel = hiltViewModel()
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val currentFailure by rememberUpdatedState(onFailure)
    val currentScan by rememberUpdatedState(onScan)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) granted = true else currentFailure(TagScannerFailure.CAMERA_DENIED)
    }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }
    if (!granted) return

    val controller = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        }
    }
    DisposableEffect(controller, kind) {
        val executor = Executors.newSingleThreadExecutor()
        val main = Handler(Looper.getMainLooper())
        // A QR code's direction is measured from the whole sensor view (4:3), which the camera calibration describes; a barcode is read from 16:9.
        controller.setImageAnalysisResolutionSelector(analysisResolution(isQr = kind == TagKind.QR))
        // The rotation-vector sensor listens only while the screen is resumed and is released with the camera.
        val meter = if (kind == TagKind.QR) scanner.newMeter() else null
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> meter?.resume()
                Lifecycle.Event.ON_PAUSE -> meter?.pause()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(lifecycleObserver)
        // CameraX only lets the main thread read the zoom, so the analysis thread reads a copy kept up to date here.
        val zoom = AtomicReference(1f)
        val zoomObserver = Observer<ZoomState> { zoom.set(it.zoomRatio) }
        controller.zoomState.observe(owner, zoomObserver)
        val analyzer = TagFrameAnalyzer(kind, meter, { zoom.get() }) { scan -> main.post { currentScan(scan) } }
        controller.setImageAnalysisAnalyzer(executor, analyzer)
        controller.bindToLifecycle(owner)
        onDispose {
            owner.lifecycle.removeObserver(lifecycleObserver)
            controller.zoomState.removeObserver(zoomObserver)
            meter?.close()
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            executor.execute(analyzer::close)
            executor.shutdown()
        }
    }
    // Again whenever the camera is bound anew (another kind of code), so the light stays as the button shows.
    LaunchedEffect(torchOn, kind) { controller.enableTorch(torchOn) }

    // Tap to focus and pinch to zoom on a small, close-up or far-off code.
    CameraPreview(controller, modifier, zoomBadgeTop)
}

private fun analysisResolution(isQr: Boolean): ResolutionSelector = ResolutionSelector.Builder()
    .setAspectRatioStrategy(if (isQr) AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY else AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
    .setResolutionStrategy(
        ResolutionStrategy(
            if (isQr) Size(ANALYSIS_WIDTH, ANALYSIS_WIDTH * 3 / 4) else Size(ANALYSIS_WIDTH, ANALYSIS_HEIGHT),
            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
        ),
    )
    .build()

private const val ANALYSIS_WIDTH = 1280
private const val ANALYSIS_HEIGHT = 720
