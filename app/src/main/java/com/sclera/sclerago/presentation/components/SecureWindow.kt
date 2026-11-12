package com.sclera.sclerago.presentation.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import com.sclera.sclerago.BuildConfig

/** Counts the secure screens that are showing, so a screen closing doesn't clear the flag another one still needs. */
private object SecureWindowCount {
    private var count = 0

    fun enter(activity: Activity) {
        if (count++ == 0) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    fun leave(activity: Activity) {
        if (--count <= 0) {
            count = 0
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * While this is in the composition the window can't be screenshotted, screen-recorded or shown in the
 * recent-apps list. Put it on screens that show emails, access codes or other personal data.
 * Release builds only, so debug builds can still be screenshotted while developing.
 */
@Composable
fun SecureWindow() {
    if (BuildConfig.DEBUG) return
    val view = LocalView.current
    DisposableEffect(view) {
        val activity = view.context.findActivity()
        activity?.let(SecureWindowCount::enter)
        onDispose { activity?.let(SecureWindowCount::leave) }
    }
}
