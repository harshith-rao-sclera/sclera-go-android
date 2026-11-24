package com.sclera.sclerago.presentation.validation

import com.sclera.sclerago.presentation.components.LocalToaster
import com.sclera.sclerago.presentation.components.ToastEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.validation.isServerId
import com.sclera.sclerago.presentation.common.UiText

/**
 * Validate from Data Collection (the web's QuickValidateModal): the Run validation sheet over the list, with
 * [assetIds] chosen (those the server has). When a run starts the sheet closes and the web's job banner says so, with a
 * button: View job ([onOpenJob] with the run's id) or, for one saved to run later, Open Validation ([onOpenJob] with
 * null). Without [onOpenJob], [onNotice] gets where to follow it instead. [onClose] when it is dismissed, or when
 * validation can't be loaded (as the web, with a note).
 */
@Composable
fun QuickValidate(
    propertyId: String,
    assets: List<Asset>,
    assetIds: List<String>,
    onNotice: (UiText) -> Unit,
    onClose: () -> Unit,
    onOpenJob: ((String?) -> Unit)? = null,
    vm: ValidationViewModel = hiltViewModel(),
) {
    val s = vm.state
    var opened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(propertyId) { vm.bind(propertyId) }
    // Opens once the templates are in.
    LaunchedEffect(s.catalogLoaded, s.catalogFailed, s.offline) {
        // The current state (bind may just have switched property), not the one this composition saw.
        val now = vm.state
        if (opened || now.propertyId != propertyId) return@LaunchedEffect
        when {
            now.catalogLoaded -> {
                vm.openRunSheet(assetIds = assetIds.filter(::isServerId).toSet(), quick = true)
                opened = true
            }
            now.catalogFailed || now.offline -> {
                onNotice(UiText.Res(R.string.val_quick_unavailable))
                onClose()
            }
        }
    }
    val sheet = s.runSheet?.takeIf { it.quick }
    val toaster = LocalToaster.current
    LaunchedEffect(opened, sheet == null, s.quickStarted) {
        s.quickStarted?.let {
            // With somewhere to open it, the web's banner says it (said here, before this closes); else the plain note.
            val banner = vm.state.banner?.takeIf { b -> b.quick }
            if (onOpenJob != null && banner != null) toaster.showJobBanner(banner, onOpenJob) else onNotice(it)
            vm.bannerShown()
            vm.quickShown()
        }
        // The sheet as it is now: when it has just been opened in this same frame, the composition's `sheet` is still null.
        if (opened && vm.state.runSheet?.quick != true) onClose()
    }
    Box(Modifier.fillMaxSize()) {
        sheet?.let { RunSheetView(vm, s, it, assets) }
        sheet?.takeIf { it.picking }?.let { AssetPicker(vm, it, assets) }
        // The sheet's own messages (a run that couldn't start).
        ToastEffect(s.message, visible = sheet != null)
    }
}
