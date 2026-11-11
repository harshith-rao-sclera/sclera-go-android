package com.example.sclerago.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.sclerago.data.AssetType
import com.example.sclerago.data.Device
import com.example.sclerago.scan.BLE_ID
import com.example.sclerago.ui.icons.Icons
import com.example.sclerago.ui.icons.LineIcon
import com.example.sclerago.ui.theme.C

/**
 * A scan row's icon tile: the same tile and asset-type icon the Assets list shows (see AssetRow),
 * so a device looks the same on the scan page as the asset it becomes. A Bluetooth device whose
 * name doesn't say what it is (most of them) shows the Bluetooth icon rather than the generic box.
 */
@Composable
fun DeviceIconTile(d: Device) {
    val icon = if (d.id.startsWith(BLE_ID) && d.type == AssetType.GENERIC) Icons.Bluetooth else d.type.icon
    Box(Modifier.size(42.dp).clip(r(11)).background(C.Raised), contentAlignment = Alignment.Center) {
        LineIcon(icon, 21.dp, C.Yellow, 1.9f)
    }
}
