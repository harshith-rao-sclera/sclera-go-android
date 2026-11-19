package com.sclera.sclerago.presentation.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C

/**
 * A scan row's icon tile: the Assets list's tile with the generic asset icon (the cube). A scanned device is not an
 * asset yet, so it has no type (master format) to take the server's icon from.
 */
@Composable
fun DeviceIconTile() {
    Box(Modifier.size(42.dp).clip(r(11)).background(C.Raised), contentAlignment = Alignment.Center) {
        LineIcon(Icons.Box, 21.dp, C.Yellow, 1.9f)
    }
}
