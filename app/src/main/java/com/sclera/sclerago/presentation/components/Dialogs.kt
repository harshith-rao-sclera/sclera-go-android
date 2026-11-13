package com.sclera.sclerago.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.presentation.icons.Ico
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

// Confirmation dialogs shared across screens.

/** Asked before leaving something with unsaved changes. System back means "Keep editing". */
@Composable
fun DiscardChangesDialog(
    onKeep: () -> Unit,
    onDiscard: () -> Unit,
    title: String = "Discard your changes?",
    body: String = "You’ve changed something that isn’t saved yet. If you leave now, those changes are lost.",
) {
    BackHandler(onBack = onKeep)
    ConfirmDialog(
        icon = Icons.Alert, iconBg = C.WarnRow, iconFg = C.Orange, title = title, body = body,
        no = "Keep editing", yes = "Discard", onNo = onKeep, onYes = onDiscard,
    )
}

/**
 * Leaving a form: while [dirty] (something was entered or changed), closing asks [DiscardChangesDialog]
 * first; otherwise it just closes. Use the `close` handed to [content] for every way out (system back,
 * tapping outside, ×, Cancel, Done). Call it where its siblings stack (a Box), so the dialog draws on top.
 */
@Composable
fun GuardedClose(dirty: Boolean, onClose: () -> Unit, content: @Composable (close: () -> Unit) -> Unit) {
    var asking by remember { mutableStateOf(false) }
    val isDirty by rememberUpdatedState(dirty)
    val leave by rememberUpdatedState(onClose)
    val close = remember { { if (isDirty) asking = true else leave() } }
    content(close)
    if (asking) DiscardChangesDialog(onKeep = { asking = false }, onDiscard = { asking = false; leave() })
}

@Composable
fun ConfirmDialog(
    icon: Ico,
    iconBg: Color,
    iconFg: Color,
    title: String,
    body: String,
    no: String,
    yes: String,
    onNo: () -> Unit,
    onYes: () -> Unit,
    /** Colour of the confirm button (red for destructive actions). */
    yesBg: Color = C.Red,
) {
    Box(Modifier.fillMaxSize().background(C.Scrim).noRippleClick(onNo).blockSwipe().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 320.dp).fillMaxWidth().fabShadow(r(18), 16.dp).clip(r(18)).background(C.Surface)
                .border(1.dp, C.Border2, r(18)).noRippleClick { }.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(44.dp).background(iconBg, CircleShape), contentAlignment = Alignment.Center) {
                LineIcon(icon, 20.dp, iconFg, 2.2f)
            }
            Txt(title, 18, 700)
            Txt(body, 14, 400, C.Muted, maxLines = 4, lineHeight = androidx.compose.ui.unit.TextUnit(20f, androidx.compose.ui.unit.TextUnitType.Sp))
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill(no, onNo, Modifier.weight(1f), height = 46.dp, size = 14)
                Pill(yes, onYes, Modifier.weight(1f), bg = yesBg, fg = C.OnYellow, height = 46.dp, size = 14, weight = 700)
            }
        }
    }
}
