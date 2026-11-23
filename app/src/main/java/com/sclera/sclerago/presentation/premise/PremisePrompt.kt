package com.sclera.sclerago.presentation.premise

import android.Manifest
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sclera.sclerago.presentation.components.LocationPermissionSheet
import com.sclera.sclerago.presentation.components.openAppSettings

/**
 * Explains why a collector's location is wanted (the property has geofencing on), then shows Android's own prompt. When
 * Android will no longer show it, the answer opens the app's settings instead. Shows nothing when no permission is needed.
 */
@Composable
fun PremisePrompt(vm: PremiseViewModel) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val ask by vm.askLocation.collectAsStateWithLifecycle()
    var systemPromptOpen by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        systemPromptOpen = false
        vm.onPermissionResult(granted)
        if (!granted && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)
        ) {
            openAppSettings(context)
        }
    }
    if (ask && !systemPromptOpen) {
        LocationPermissionSheet(
            onContinue = {
                systemPromptOpen = true
                permission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            },
            onNotNow = vm::onNotNow,
        )
    }
}
