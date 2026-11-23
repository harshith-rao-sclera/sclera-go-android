package com.sclera.sclerago.presentation.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.presentation.components.BottomSheet
import com.sclera.sclerago.presentation.icons.Ico
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import com.sclera.sclerago.services.scan.ScanService

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
    @param:StringRes val sheetTitle: Int,
    @param:StringRes val sheetBody: Int,
    val blockedIcon: Ico,
    @param:StringRes val blockedTitle: Int,
    @param:StringRes val blockedBody: Int,
    @param:StringRes val settingsStep: Int,
)

private fun textFor(t: ScanService.Target): ScanPermText = when (t) {
    ScanService.Target.BLUETOOTH -> ScanPermText(
        Icons.Bluetooth,
        R.string.scrt_perm_bt_title,
        R.string.scrt_perm_bt_body,
        Icons.Bluetooth, R.string.scrt_perm_bt_blocked_title,
        R.string.scrt_perm_bt_blocked_body,
        R.string.scrt_perm_bt_settings_step,
    )
    ScanService.Target.NETWORK -> ScanPermText(
        Icons.Wifi,
        R.string.scrt_perm_net_title,
        R.string.scrt_perm_net_body,
        Icons.Wifi, R.string.scrt_perm_net_blocked_title,
        R.string.scrt_perm_net_blocked_body,
        R.string.scrt_perm_net_settings_step,
    )
    ScanService.Target.ACCESS_POINTS -> ScanPermText(
        Icons.AccessPoint,
        R.string.scrt_perm_ap_title,
        R.string.scrt_perm_ap_body,
        Icons.Unmapped, R.string.scrt_perm_ap_blocked_title,
        R.string.scrt_perm_ap_blocked_body,
        R.string.scrt_perm_ap_settings_step,
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
            Txt(stringResource(t.sheetTitle), 17, 700, maxLines = 2, align = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Txt(stringResource(t.sheetBody), 13, 400, C.Muted, maxLines = Int.MAX_VALUE, align = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill(stringResource(R.string.scrt_perm_not_now), onNotNow, Modifier.weight(1f), height = 48.dp, size = 14)
                Pill(stringResource(R.string.scrt_perm_allow), onContinue, Modifier.weight(1.4f), bg = C.Yellow, fg = C.OnYellow, height = 48.dp, size = 14, weight = 700)
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
            .border(1.dp, C.Border, r(16)).padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(RedBg).border(1.dp, RedBd, CircleShape),
            contentAlignment = Alignment.Center,
        ) { LineIcon(t.blockedIcon, 32.dp, C.Red, 2f) }
        Spacer(Modifier.height(12.dp))
        Txt(stringResource(t.blockedTitle), 16, 700, maxLines = 2, align = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Txt(stringResource(t.blockedBody), 12, 400, C.Muted, maxLines = Int.MAX_VALUE, align = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Column(
            Modifier.fillMaxWidth().clip(r(12)).background(C.Bg).border(1.dp, C.Border, r(12)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Txt(stringResource(R.string.scrt_perm_in_settings), 12, 700, C.Soft)
            Txt(stringResource(R.string.scrt_perm_step_open_permissions), 12, 400, C.Muted, maxLines = Int.MAX_VALUE)
            Txt(stringResource(R.string.scrt_perm_step_two, stringResource(t.settingsStep)), 12, 400, C.Muted, maxLines = Int.MAX_VALUE)
        }
        Spacer(Modifier.height(14.dp))
        Pill(stringResource(R.string.camera_open_settings), { openAppSettings(context) }, Modifier.fillMaxWidth(), bg = C.Yellow, fg = C.OnYellow, height = 46.dp, size = 14, weight = 700)
        Spacer(Modifier.height(4.dp))
        Pill(stringResource(R.string.ai_try_again), onRetry, bg = Color.Transparent, fg = C.Yellow, height = 40.dp, size = 14, weight = 700)
    }
}
