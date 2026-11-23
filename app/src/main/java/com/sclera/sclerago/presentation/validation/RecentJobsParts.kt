package com.sclera.sclerago.presentation.validation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.sclera.sclerago.R
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.components.LocalToaster
import com.sclera.sclerago.presentation.components.ToastAction
import com.sclera.sclerago.presentation.components.ToastKind
import com.sclera.sclerago.presentation.components.Toaster

// Recent jobs on the Validation page: the banner said when a run starts (the web's jobBanner). Its filters are the
// Filter jobs sheet (JobFilterSheet.kt).

/**
 * Says [banner] (a run started, or saved to run later) as a toast, as the web's job banner: "{name} started · {which
 * assets}" or "{name} saved · Ready to run", with a button that opens it ([onOpen] with the run's id; null for the
 * Validation page) when it has somewhere to go. [onShown] once it has been said.
 */
@Composable
internal fun JobBannerEffect(banner: JobBanner?, onOpen: ((String?) -> Unit)?, onShown: () -> Unit) {
    val toaster = LocalToaster.current
    LaunchedEffect(banner?.id) {
        val b = banner ?: return@LaunchedEffect
        toaster.showJobBanner(b, onOpen)
        onShown()
    }
}

/** Says [b] as a toast (see [JobBannerEffect]), with its button when it has somewhere to go. */
internal fun Toaster.showJobBanner(b: JobBanner, onOpen: ((String?) -> Unit)?) {
    val text = if (b.ready) UiText.Res(R.string.val_banner_saved, listOf(b.run.name))
    else UiText.Res(R.string.val_banner_started, listOf(b.run.name, ValidationViewModel.scopeText(b.run)))
    val action = onOpen?.takeIf { b.open }?.let { open ->
        if (b.ready) ToastAction(UiText.Res(R.string.val_banner_open_validation)) { open(null) }
        else ToastAction(UiText.Res(R.string.val_banner_view_job)) { open(b.run.id) }
    }
    show(text, ToastKind.SUCCESS, action)
}
