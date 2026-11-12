package com.example.sclerago.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.sclerago.services.scan.ScanService
import com.example.sclerago.ui.icons.Ico
import com.example.sclerago.ui.icons.Icons
import com.example.sclerago.ui.icons.LineIcon
import com.example.sclerago.ui.theme.C
import com.example.sclerago.ui.theme.Txt

/*
 * Permission UI:
 *  - PermissionSheet: explains a scan's permission just before Android's own prompt.
 *  - BlockedCard: replaces a scan page's radar card when its permission was refused.
 */

private val RedBg = Color(0xFF2A1919)
private val RedBd = Color(0xFF4A2827)

/** What to say about one scan's permission. */
private class ScanPermText(
    val icon: Ico,
    val sheetTitle: String,
    val sheetBody: String,
    val blockedIcon: Ico,
    val blockedTitle: String,
    val blockedBody: String,
    val settingsStep: String,
)

private fun textFor(t: ScanService.Target): ScanPermText = when (t) {
    ScanService.Target.BLUETOOTH -> ScanPermText(
        Icons.Bluetooth,
        "Find Bluetooth tags nearby",
        "To see asset tags and sensors near you.",
        Icons.Bluetooth, "Nearby devices is blocked",
        "Sclera Go needs Nearby devices to find Bluetooth tags and sensors.",
        "Allow Nearby devices",
    )
    ScanService.Target.NETWORK -> ScanPermText(
        Icons.Wifi,
        "Find devices on this network",
        "To find devices on the Wi-Fi you're connected to.",
        Icons.Wifi, "Local network is blocked",
        "Android won't let Sclera Go reach other devices on your Wi-Fi without it.",
        "Allow access to the local network",
    )
    ScanService.Target.ACCESS_POINTS -> ScanPermText(
        Icons.AccessPoint,
        "See Wi-Fi access points",
        "To list the access points in range.",
        Icons.Unmapped, "Location is blocked",
        "Android only lists access points to apps with Location access.",
        "Allow Location while using the app",
    )
}

/** Opens this app's page in Android Settings, where refused permissions can be allowed. */
fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

// ---------------------------------------------------------------- Explainer sheet

/** Shown just before Android's permission prompt for [target]'s scan. */
@Composable
fun PermissionSheet(target: ScanService.Target, onContinue: () -> Unit, onNotNow: () -> Unit) {
    val t = textFor(target)
    BottomSheet(onNotNow) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(64.dp).clip(r(18)).background(C.YTintBg).border(1.dp, C.YTintBd, r(18)),
                contentAlignment = Alignment.Center,
            ) { LineIcon(t.icon, 32.dp, C.Yellow, 2f) }
            Spacer(Modifier.height(14.dp))
            Txt(t.sheetTitle, 19, 700, maxLines = 2, align = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Txt(t.sheetBody, 14, 400, C.Muted, maxLines = 2, align = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("Not now", onNotNow, Modifier.weight(1f), height = 48.dp, size = 15)
                Pill("Allow", onContinue, Modifier.weight(1.4f), bg = C.Yellow, fg = C.OnYellow, height = 48.dp, size = 15, weight = 700)
            }
        }
    }
}

// ---------------------------------------------------------------- Refused card

/** Takes the radar card's place when [target]'s permission was refused. */
@Composable
fun BlockedCard(target: ScanService.Target, onRetry: () -> Unit) {
    val t = textFor(target)
    val context = LocalContext.current
    Column(
        Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp).fillMaxWidth().clip(r(16)).background(C.Surface)
            .border(1.dp, C.Border, r(16)).padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(RedBg).border(1.dp, RedBd, CircleShape),
            contentAlignment = Alignment.Center,
        ) { LineIcon(t.blockedIcon, 32.dp, C.Red, 2f) }
        Spacer(Modifier.height(12.dp))
        Txt(t.blockedTitle, 17, 700, maxLines = 2, align = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Txt(t.blockedBody, 13, 400, C.Muted, maxLines = 3, align = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Column(
            Modifier.fillMaxWidth().clip(r(12)).background(C.Bg).border(1.dp, C.Border, r(12)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Txt("In Settings", 13, 700, C.Soft)
            Txt("1 · Open Permissions", 13, 400, C.Muted)
            Txt("2 · ${t.settingsStep}", 13, 400, C.Muted, maxLines = 2)
        }
        Spacer(Modifier.height(14.dp))
        Pill("Open settings", { openAppSettings(context) }, Modifier.fillMaxWidth(), bg = C.Yellow, fg = C.OnYellow, height = 46.dp, size = 15, weight = 700)
        Spacer(Modifier.height(4.dp))
        Pill("Try again", onRetry, bg = Color.Transparent, fg = C.Yellow, height = 40.dp, size = 14, weight = 700)
    }
}
