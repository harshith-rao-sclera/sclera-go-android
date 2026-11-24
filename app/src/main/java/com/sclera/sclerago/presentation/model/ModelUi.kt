package com.sclera.sclerago.presentation.model

import androidx.compose.ui.graphics.Color
import com.sclera.sclerago.domain.model.Source
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.presentation.icons.Ico
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.theme.C

// How the domain enums look on screen: colours and icons stay out of the domain layer.

// ---------------------------------------------------------------- Status

private class StatusLook(val color: Color, val icon: Ico, val chipBg: Color, val chipBd: Color, val chipFg: Color = color)

/** The web's status colours (its --status-* tones): Submitted blue, Validated green, AI purple. */
private val SubmittedBlue = Color(0xFF5AA9FF)
private val ValidatedGreen = Color(0xFF4CC38A)
private val AiPurple = Color(0xFFA48BF5)

private val Status.look: StatusLook
    get() = when (this) {
        Status.AI -> StatusLook(AiPurple, Icons.StatusAi, Color(0xFF211E2E), Color(0xFF383150))
        Status.VALIDATED -> StatusLook(ValidatedGreen, Icons.StatusValidated, Color(0xFF17241D), Color(0xFF27402F))
        Status.SUBMITTED -> StatusLook(SubmittedBlue, Icons.StatusSubmitted, Color(0xFF15202C), Color(0xFF24384F))
        Status.DRAFT -> StatusLook(C.Muted, Icons.StatusDraft, C.Raised, C.Border2, C.Soft)
        Status.INPUT -> StatusLook(C.Orange, Icons.Alert, C.WarnRow, Color(0xFF4A3420))
        Status.EXCEPTION -> StatusLook(C.Red, Icons.StatusException, Color(0xFF2A1919), Color(0xFF4A2827))
    }

val Status.color: Color get() = look.color
val Status.icon: Ico get() = look.icon
val Status.chipBg: Color get() = look.chipBg
val Status.chipBd: Color get() = look.chipBd
val Status.chipFg: Color get() = look.chipFg

// ---------------------------------------------------------------- Source

val Source.icon: Ico
    get() = when (this) {
        Source.CAMERA -> Icons.Camera
        Source.BT -> Icons.Bluetooth
        Source.WIFI -> Icons.Wifi
        Source.AP -> Icons.AccessPoint
        Source.FILE -> Icons.Imported
        Source.MANUAL -> Icons.Keyboard
    }

/** The web's teal for Wi-Fi and file-imported assets (its "file" tone). */
private val SourceTeal = Color(0xFF3CC8C0)

val Source.color: Color
    get() = when (this) {
        Source.CAMERA -> Color(0xFFF28FB8)
        Source.BT -> Color(0xFF5E9DFF)
        // Wi-Fi and access points are both network scans, so they share the web's Wi-Fi colour.
        Source.WIFI, Source.AP -> SourceTeal
        Source.FILE -> SourceTeal
        Source.MANUAL -> C.Soft
    }

// ---------------------------------------------------------------- Text

/** The status as the user reads it (the domain's [Status.label] is English only). */
@get:androidx.annotation.StringRes
val Status.labelRes: Int
    get() = when (this) {
        Status.AI -> com.sclera.sclerago.R.string.status_ai
        Status.VALIDATED -> com.sclera.sclerago.R.string.status_validated
        Status.SUBMITTED -> com.sclera.sclerago.R.string.status_submitted
        Status.DRAFT -> com.sclera.sclerago.R.string.status_draft
        Status.INPUT -> com.sclera.sclerago.R.string.status_input
        Status.EXCEPTION -> com.sclera.sclerago.R.string.status_exception
    }

/** "via Camera", "Added manually", ... for each way of onboarding. */
@get:androidx.annotation.StringRes
val Source.viaRes: Int
    get() = when (this) {
        Source.CAMERA -> com.sclera.sclerago.R.string.source_camera
        Source.BT -> com.sclera.sclerago.R.string.source_bt
        Source.WIFI -> com.sclera.sclerago.R.string.source_wifi
        Source.AP -> com.sclera.sclerago.R.string.source_ap
        Source.FILE -> com.sclera.sclerago.R.string.source_file
        Source.MANUAL -> com.sclera.sclerago.R.string.source_manual
    }
