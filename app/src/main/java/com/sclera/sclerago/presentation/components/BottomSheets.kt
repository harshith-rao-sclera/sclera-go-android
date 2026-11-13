package com.sclera.sclerago.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

// Bottom sheet container and header shared across screens.

/** A bottom sheet over a dimmed backdrop. Back and a tap on the backdrop close it. */
@Composable
internal fun BottomSheet(
    onDismiss: () -> Unit,
    maxHeightFraction: Float = 0.92f,
    background: Color = C.Surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize().background(C.Scrim).noRippleClick(onDismiss).blockSwipe().imePadding()) {
        Column(
            sheetFrame(background, heightFraction = maxHeightFraction),
        ) {
            if (isTablet()) Spacer(Modifier.height(12.dp)) else SheetHandle(Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp, bottom = 4.dp))
            content()
        }
    }
}

@Composable
internal fun SheetHeader(title: String, sub: String?, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(title, 18, 700)
            sub?.let { Txt(it, 13, 400, C.Muted, maxLines = 2) }
        }
        CloseButton(onClose, size = 36.dp, iconSize = 16.dp)
    }
}
