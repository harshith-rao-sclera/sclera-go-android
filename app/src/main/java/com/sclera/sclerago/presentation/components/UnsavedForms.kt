package com.sclera.sclerago.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * How many forms on screen have changes that aren't saved yet. Each form reports itself while it has some
 * ([ReportUnsaved]; [GuardedClose] does it for every form built on it), so leaving the page another way (a bottom tab,
 * a side-menu page) can ask before throwing them away, as Back and × already do.
 */
class UnsavedForms {
    private var count by mutableIntStateOf(0)

    /** Some form on screen has unsaved changes. */
    val any: Boolean get() = count > 0

    internal fun add() {
        count++
    }

    internal fun remove() {
        count = (count - 1).coerceAtLeast(0)
    }
}

/** The app's [UnsavedForms]; none outside the signed-in app (previews, the login screen), where nothing is counted. */
val LocalUnsavedForms = staticCompositionLocalOf<UnsavedForms?> { null }

/** Counts this form in [LocalUnsavedForms] while [dirty]. */
@Composable
fun ReportUnsaved(dirty: Boolean) {
    val forms = LocalUnsavedForms.current ?: return
    DisposableEffect(forms, dirty) {
        if (dirty) forms.add()
        onDispose { if (dirty) forms.remove() }
    }
}
