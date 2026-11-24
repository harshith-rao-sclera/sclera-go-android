package com.sclera.sclerago.presentation.config

import com.sclera.sclerago.presentation.components.pressClickable
import com.sclera.sclerago.presentation.components.PRESSED_CARD
import com.sclera.sclerago.presentation.components.SkeletonText
import com.sclera.sclerago.presentation.components.pageGutter
import androidx.compose.ui.res.stringResource
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.FieldKind
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.presentation.components.CfgCard
import com.sclera.sclerago.presentation.components.OnOffSwitch
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
    FieldKind.CURRENCY -> Icons.Banknote
    FieldKind.DATE -> Icons.Calendar
    FieldKind.TEXTAREA -> Icons.TextLines
    FieldKind.CHECKBOX -> Icons.CheckSquare
    FieldKind.MULTI -> Icons.ListCheck
    FieldKind.YESNO -> Icons.Toggles
    FieldKind.TIME -> Icons.Clock
    FieldKind.URL -> Icons.Globe
    FieldKind.IMAGE -> Icons.Gallery
    FieldKind.VIDEO -> Icons.Video
    FieldKind.VOICE_MEMO -> Icons.Mic
    FieldKind.KEY_VALUE -> Icons.Table
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
        Txt(kind.shown, 10, 600, C.Soft)
    }
}

/** One row of a Configuration list, carded like an Assets list row: 4dp from the screen edge, 3dp apart. */
internal fun Modifier.listCard() =
    padding(horizontal = 4.dp, vertical = 1.5.dp).fillMaxWidth().clip(r(8)).background(C.Surface).border(1.dp, C.Border, r(8))

/** Cancel + a yellow main button along the bottom of a sheet; the main button changes the set-up, so it needs Configure. */
@Composable
internal fun SheetButtons(yes: String, enabled: Boolean, onNo: () -> Unit, no: String = stringResource(R.string.cfg_cancel), onYes: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().topBorder(C.Border).padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Pill(no, onNo, height = 40.dp, hPad = 18.dp, size = 14)
        ConfigGate(Modifier.weight(1f)) {
            Pill(
                yes, { if (enabled) onYes() }, Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f),
                bg = C.Yellow, fg = C.OnYellow, height = 40.dp, size = 13, weight = 700,
            )
        }
    }
}

// ---------------------------------------------------------------- Property Settings tab

@Composable
fun PropertySettingsPage(vm: AppViewModel, cfg: ConfigViewModel) {
    val bt = vm.onboardingOn(Tab.BT)
    val net = vm.onboardingOn(Tab.NET)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = pageGutter()).padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Txt(stringResource(R.string.cfgt_onboarding_intro, vm.propName), 11, 400, C.Muted, maxLines = Int.MAX_VALUE)
        CfgCard {
            SettingsHeader(
                stringResource(R.string.cfgt_network_onboarding),
                stringResource(
                    when {
                        bt && net -> R.string.cfgt_onboarding_both_on
                        bt -> R.string.cfgt_onboarding_bt_only
                        net -> R.string.cfgt_onboarding_wifi_only
                        else -> R.string.cfgt_onboarding_both_off
                    },
                ),
            )
            OnboardRow(Icons.Bluetooth, stringResource(R.string.cfgt_bt_onboarding), stringResource(R.string.cfgt_bt_onboarding_hint), bt, vm.onboardingSaving(Tab.BT)) { vm.setOnboarding(Tab.BT, it) }
            OnboardRow(Icons.Wifi, stringResource(R.string.cfgt_wifi_onboarding), stringResource(R.string.cfgt_wifi_onboarding_hint), net, vm.onboardingSaving(Tab.NET)) { vm.setOnboarding(Tab.NET, it) }
        }
        // The property's AI switch (the web's AI group): a setting at the server; unknown until the settings are read (then it
        // can't be changed). Saved, the asset screens' AI buttons follow at once.
        val ai = cfg.aiValidation
        CfgCard {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt(stringResource(R.string.cfg_ai_title), 13, 700)
                if (ai == null && cfg.photos.load == LoadState.LOADING) {
                    SkeletonText(stringResource(R.string.cfg_loading), Modifier.padding(top = 4.dp), lines = 1)
                } else {
                    Txt(
                        stringResource(if (ai != null) R.string.cfg_ai_validation_hint else R.string.cfg_settings_load_failed),
                        11, 400, C.Muted, maxLines = Int.MAX_VALUE,
                    )
                }
            }
            OnboardRow(Icons.Sparkles, stringResource(R.string.cfg_ai_validation), stringResource(R.string.cfg_ai_validation_row), ai == true, cfg.aiSaving) {
                if (ai != null) cfg.setAiValidation(it)
            }
        }
        // Units (the web's Units switch): US or metric for the fields a type brings; unknown until the settings are read.
        val metric = cfg.metricUnits
        CfgCard {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt(stringResource(R.string.cfg_units_title), 13, 700)
                if (metric == null && cfg.photos.load == LoadState.LOADING) {
                    SkeletonText(stringResource(R.string.cfg_loading), Modifier.padding(top = 4.dp), lines = 1)
                } else {
                    Txt(
                        stringResource(if (metric != null) R.string.cfg_units_hint else R.string.cfg_settings_load_failed),
                        11, 400, C.Muted, maxLines = Int.MAX_VALUE,
                    )
                }
            }
            OnboardRow(Icons.Globe, stringResource(R.string.cfg_units_metric), stringResource(R.string.cfg_units_metric_row), metric == true, cfg.unitsSaving) {
                if (metric != null) cfg.setMetricUnits(it)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Txt(stringResource(R.string.cfgt_on_the_phone), 10, 600, C.Muted, spacing = 0.6f)
            CfgCard {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    listOf(
                        Triple(Tab.BT, stringResource(R.string.nav_bluetooth), Icons.Bluetooth), Triple(Tab.NET, stringResource(R.string.nav_network), Icons.Wifi),
                        Triple(Tab.ASSETS, stringResource(R.string.label_assets), Icons.Box),
                        Triple(Tab.CAMERA, stringResource(R.string.nav_camera), Icons.Camera), Triple(Tab.FILE, stringResource(R.string.nav_file), Icons.FileUpload),
                    ).filter { it.first in vm.navTabs }.forEach { (_, label, icon) ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.height(22.dp), contentAlignment = Alignment.Center) { LineIcon(icon, 16.dp, C.Text, 2f) }
                            Txt(label, 10, 600, C.Muted)
                        }
                    }
                }
            }
            Txt(stringResource(R.string.cfgt_onboarding_footer), 11, 400, C.Dim, maxLines = Int.MAX_VALUE)
        }
    }
}

/** A settings card's title with its optional line under it. */
@Composable
private fun SettingsHeader(title: String, sub: String?) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Txt(title, 15, 700)
        sub?.let { Txt(it, 12, 400, C.Muted, maxLines = Int.MAX_VALUE) }
    }
}

/** A setting's switch row; changing it needs Configure (greyed out, with why, without it). */
@Composable
private fun OnboardRow(icon: Ico, title: String, body: String, on: Boolean, busy: Boolean = false, onChange: (Boolean) -> Unit) = ConfigGate(Modifier.fillMaxWidth()) {
    // While its save is on its way the row is dimmed and takes no taps, as the web's switch.
    Row(
        Modifier.pressClickable(pressed = PRESSED_CARD, enabled = !busy) { onChange(!on) }.fillMaxWidth().topBorder(C.Border).alpha(if (busy) 0.6f else 1f)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(36.dp).clip(r(9)).background(if (on) C.YTintBg else C.Raised), contentAlignment = Alignment.Center) {
            LineIcon(icon, 16.dp, if (on) C.Yellow else C.Dim, 2.2f)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(title, 12, 600)
            Txt(body, 11, 400, C.Muted, maxLines = Int.MAX_VALUE)
        }
        OnOffSwitch(on) { if (!busy) onChange(it) }
    }
}
