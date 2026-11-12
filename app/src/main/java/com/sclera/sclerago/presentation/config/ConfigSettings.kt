package com.sclera.sclerago.presentation.config

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.data.*
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.presentation.components.CfgCard
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Ico
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.main.AppViewModel
import com.sclera.sclerago.presentation.main.Tab
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/*
 * Configuration → Property Settings (which ways of adding assets the phone offers), plus small
 * pieces the Configuration tabs share.
 */

// ---------------------------------------------------------------- Shared bits

/** Icon for a field's kind, as shown on its chip. */
internal fun FieldKind.icon(): Ico = when (this) {
    FieldKind.TEXT -> Icons.TextAa
    FieldKind.DROPDOWN -> Icons.ChevronCircle
    FieldKind.NUMBER -> Icons.Hash
    FieldKind.DATE -> Icons.Calendar
    FieldKind.TEXTAREA -> Icons.TextLines
    FieldKind.CHECKBOX -> Icons.CheckSquare
    FieldKind.MULTI -> Icons.ListCheck
    FieldKind.YESNO -> Icons.Toggles
    FieldKind.TIME -> Icons.Clock
    FieldKind.URL -> Icons.Globe
}

internal val FieldKind.shown: String get() = if (this == FieldKind.TEXTAREA) "Text area" else label

/** A field's kind as a small chip: icon + name. */
@Composable
internal fun KindChip(kind: FieldKind) {
    Row(
        Modifier.clip(r(6)).background(C.Raised).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LineIcon(kind.icon(), 11.dp, C.Soft, 2.4f)
        Txt(kind.shown, 11, 600, C.Soft)
    }
}

/** One row of a Configuration list, carded like an Assets list row: 4dp from the screen edge, 3dp apart. */
internal fun Modifier.listCard() =
    padding(horizontal = 4.dp, vertical = 1.5.dp).fillMaxWidth().clip(r(8)).background(C.Surface).border(1.dp, C.Border, r(8))

/** An on/off switch with a yellow track when on. */
@Composable
internal fun OnOffSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    val track by animateColorAsState(if (on) C.Yellow else C.RadioOff, label = "switch")
    val x by animateDpAsState(if (on) 22.dp else 2.dp, label = "thumb")
    Box(Modifier.size(50.dp, 30.dp).clip(Capsule).background(track).clickable { onChange(!on) }) {
        Box(Modifier.padding(start = x, top = 3.dp).size(24.dp).clip(CircleShape).background(Color.White))
    }
}

/** Cancel + a yellow main button along the bottom of a sheet. */
@Composable
internal fun SheetButtons(yes: String, enabled: Boolean, onNo: () -> Unit, no: String = "Cancel", onYes: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).topBorder(C.Border).padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Pill(no, onNo, height = 40.dp, hPad = 18.dp)
        Pill(
            yes, { if (enabled) onYes() }, Modifier.weight(1f).alpha(if (enabled) 1f else 0.4f),
            bg = C.Yellow, fg = C.OnYellow, height = 40.dp, size = 13, weight = 700,
        )
    }
}

// ---------------------------------------------------------------- Property Settings tab

@Composable
fun PropertySettingsPage(vm: AppViewModel) {
    val bt = vm.onboardingOn(Tab.BT)
    val net = vm.onboardingOn(Tab.NET)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Txt("Choose how people can add assets in ${vm.propName} from their phone.", 12, 400, C.Muted, Modifier.padding(horizontal = 12.dp), maxLines = 3)
        CfgCard {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt("Network onboarding", 14, 700)
                Txt(
                    when {
                        bt && net -> "Both are on, so the phone shows its Bluetooth and Network tabs for this property."
                        bt -> "Only Bluetooth is on, so the phone hides its Network tab for this property."
                        net -> "Only Wi-Fi is on, so the phone hides its Bluetooth tab for this property."
                        else -> "Both are off, so the phone hides its Bluetooth and Network tabs for this property."
                    },
                    12, 400, C.Muted, maxLines = 3,
                )
            }
            OnboardRow(Icons.Bluetooth, "Bluetooth onboarding", "People can add assets by finding them over Bluetooth on their phone.", bt) { vm.setOnboarding(Tab.BT, it) }
            OnboardRow(Icons.Wifi, "Wi-Fi onboarding", "People can add assets by finding them on the Wi-Fi network.", net) { vm.setOnboarding(Tab.NET, it) }
        }
        Txt("ON THE PHONE", 11, 700, C.Muted, Modifier.padding(start = 12.dp, top = 6.dp), spacing = 0.6f)
        CfgCard {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                listOf(
                    Triple(Tab.BT, "Bluetooth", Icons.Bluetooth), Triple(Tab.NET, "Network", Icons.Wifi), Triple(Tab.ASSETS, "Assets", Icons.Box),
                    Triple(Tab.CAMERA, "Camera", Icons.Camera), Triple(Tab.FILE, "File", Icons.FileUpload),
                ).filter { it.first in vm.navTabs }.forEach { (_, label, icon) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LineIcon(icon, 20.dp, C.Soft, 2f)
                        Txt(label, 11, 600, C.Muted)
                    }
                }
            }
        }
        Txt("Turning onboarding off only hides it on the phone; assets already added stay as they are.", 12, 400, C.Muted, Modifier.padding(horizontal = 12.dp), maxLines = 3)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun OnboardRow(icon: Ico, title: String, body: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().topBorder(C.Border).clickable { onChange(!on) }.padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(38.dp).clip(r(10)).background(C.YTintBg), contentAlignment = Alignment.Center) { LineIcon(icon, 19.dp, C.Yellow, 2.1f) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(title, 13, 700)
            Txt(body, 12, 400, C.Muted, maxLines = 2)
        }
        OnOffSwitch(on, onChange)
    }
}
