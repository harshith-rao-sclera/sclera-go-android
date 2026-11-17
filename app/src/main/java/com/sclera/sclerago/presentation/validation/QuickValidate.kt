package com.sclera.sclerago.presentation.validation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.components.Toast

/**
 * Validate from Data Collection (the web's QuickValidateModal): the Run validation sheet over the list, with
 * [assetIds] chosen (those the server has). When a run starts the sheet closes and [onNotice] gets where to follow it;
 * [onClose] when it is dismissed, or when validation can't be loaded (as the web, with a note).
 */
@Composable
fun QuickValidate(
    propertyId: String,
    assets: List<Asset>,
    assetIds: List<String>,
    onNotice: (UiText) -> Unit,
    onClose: () -> Unit,
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
    LaunchedEffect(opened, sheet == null, s.quickStarted) {
        s.quickStarted?.let {
            onNotice(it)
            vm.quickShown()
        }
        if (opened && sheet == null) onClose()
    }
    Box(Modifier.fillMaxSize()) {
        sheet?.let { RunSheetView(vm, s, it, assets) }
        sheet?.takeIf { it.picking }?.let { AssetPicker(vm, it, assets) }
        // The sheet's own messages (a run that couldn't start).
        AnimatedVisibility(
            sheet != null && s.message != null,
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp),
            enter = fadeIn() + slideInVertically { -it / 2 }, exit = fadeOut(),
        ) { s.message?.let { Toast(it.asString()) } }
    }
}
