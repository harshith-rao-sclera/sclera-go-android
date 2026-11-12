package com.sclera.sclerago.presentation.scan

import com.sclera.sclerago.presentation.model.icon
import com.sclera.sclerago.domain.model.Device
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C

/**
 * A scan row's icon tile: the same tile and asset-type icon the Assets list shows (see AssetRow),
 * so a device looks the same on the scan page as the asset it becomes. A device the scan can't
 * identify (most Bluetooth devices) shows the generic asset icon.
 */
@Composable
fun DeviceIconTile(d: Device) {
    Box(Modifier.size(42.dp).clip(r(11)).background(C.Raised), contentAlignment = Alignment.Center) {
        LineIcon(d.type.icon, 21.dp, C.Yellow, 1.9f)
    }
}
