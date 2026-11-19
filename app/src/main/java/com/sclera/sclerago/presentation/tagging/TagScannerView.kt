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
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sclera.sclerago.domain.model.TagScannerFailure
import com.sclera.sclerago.domain.model.TagKind
import com.sclera.sclerago.domain.usecase.tagging.TagScan
import com.sclera.sclerago.services.tagging.TagFrameAnalyzer
import java.util.concurrent.Executors

/**
 * The camera preview that reads a QR code or a barcode ([TagFrameAnalyzer]). It asks for the camera at the moment it opens and
 * reports [TagScannerFailure.CAMERA_DENIED] if refused; the camera is released when it leaves the screen.
 */
@Composable
fun TagScannerView(
    kind: TagKind,
    torchOn: Boolean,
    onScan: (TagScan) -> Unit,
    onFailure: (TagScannerFailure) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
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
            imageAnalysisTargetSize = CameraController.OutputSize(Size(ANALYSIS_WIDTH, ANALYSIS_HEIGHT))
        }
    }
    DisposableEffect(controller, kind) {
        val executor = Executors.newSingleThreadExecutor()
        val main = Handler(Looper.getMainLooper())
        val analyzer = TagFrameAnalyzer(kind) { scan -> main.post { currentScan(scan) } }
        controller.setImageAnalysisAnalyzer(executor, analyzer)
        controller.bindToLifecycle(owner)
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            executor.execute(analyzer::close)
            executor.shutdown()
        }
    }
    LaunchedEffect(torchOn) { controller.enableTorch(torchOn) }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                this.controller = controller
            }
        },
        modifier = modifier,
    )
}

private const val ANALYSIS_WIDTH = 1280
private const val ANALYSIS_HEIGHT = 720
