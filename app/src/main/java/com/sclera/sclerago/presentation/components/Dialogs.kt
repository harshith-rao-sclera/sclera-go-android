package com.sclera.sclerago.presentation.components

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
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

// Confirmation dialogs shared across screens.

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
