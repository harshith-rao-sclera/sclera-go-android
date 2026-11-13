package com.sclera.sclerago.presentation.model

import androidx.compose.ui.graphics.Color
import com.sclera.sclerago.domain.model.AssetType
import com.sclera.sclerago.domain.model.Source
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.presentation.icons.Ico
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.theme.C

// How the domain enums look on screen: colours and icons stay out of the domain layer.

// ---------------------------------------------------------------- Status

private class StatusLook(val color: Color, val icon: Ico, val chipBg: Color, val chipBd: Color, val chipFg: Color = color)

private val Status.look: StatusLook
    get() = when (this) {
        Status.AI -> StatusLook(C.Cyan, Icons.Sparkles, Color(0xFF132629), Color(0xFF1F4247))
        Status.VALIDATED -> StatusLook(C.Purple, Icons.ShieldCheck, Color(0xFF211E2E), Color(0xFF383150))
        Status.SUBMITTED -> StatusLook(C.Green, Icons.CheckCircle, Color(0xFF17241D), Color(0xFF27402F))
        Status.DRAFT -> StatusLook(C.Muted, Icons.Pencil, C.Raised, C.Border2, C.Soft)
        Status.INPUT -> StatusLook(C.Orange, Icons.Alert, C.WarnRow, Color(0xFF4A3420))
        Status.EXCEPTION -> StatusLook(C.Red, Icons.OctagonX, Color(0xFF2A1919), Color(0xFF4A2827))
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
        Source.FILE -> Icons.FileDoc
        Source.MANUAL -> Icons.Keyboard
    }

val Source.color: Color
    get() = when (this) {
        Source.CAMERA -> Color(0xFFF28FB8)
        Source.BT -> Color(0xFF5E9DFF)
        Source.WIFI, Source.AP -> Color(0xFF4CC487)
        Source.FILE -> Color(0xFFFF9A45)
        Source.MANUAL -> C.Soft
    }

// ---------------------------------------------------------------- Asset type

val AssetType.icon: Ico
    get() = when (this) {
        AssetType.MOTOR -> Icons.Motor
        AssetType.PUMP -> Icons.Pump
        AssetType.CHILLER -> Icons.Chiller
        AssetType.FAN -> Icons.Fan
        AssetType.CONTROLLER -> Icons.Controller
        AssetType.CCTV -> Icons.Cctv
        AssetType.PANEL -> Icons.Panel
        AssetType.AP -> Icons.AccessPoint
        AssetType.COMPUTER -> Icons.Computer
        AssetType.PHONE -> Icons.Phone
        AssetType.SPEAKER -> Icons.Speaker
        AssetType.SENSOR -> Icons.Sensor
        AssetType.GENERIC -> Icons.Box
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
