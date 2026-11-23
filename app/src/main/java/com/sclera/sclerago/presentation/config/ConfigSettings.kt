package com.sclera.sclerago.presentation.config

import com.sclera.sclerago.presentation.components.pageGutter
import androidx.compose.ui.res.stringResource
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.FieldKind
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
import com.sclera.sclerago.presentation.components.Capsule
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

/** Cancel + a yellow main button along the bottom of a sheet. */
@Composable
internal fun SheetButtons(yes: String, enabled: Boolean, onNo: () -> Unit, no: String = stringResource(R.string.cfg_cancel), onYes: () -> Unit) {
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
fun PropertySettingsPage(vm: AppViewModel, cfg: ConfigViewModel) {
    val bt = vm.onboardingOn(Tab.BT)
    val net = vm.onboardingOn(Tab.NET)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = pageGutter()).padding(horizontal = 4.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Txt(stringResource(R.string.cfgt_onboarding_intro, vm.propName), 11, 400, C.Muted, Modifier.padding(horizontal = 12.dp), maxLines = Int.MAX_VALUE)
        CfgCard {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt(stringResource(R.string.cfgt_network_onboarding), 13, 700)
                Txt(
                    stringResource(
                        when {
                            bt && net -> R.string.cfgt_onboarding_both_on
                            bt -> R.string.cfgt_onboarding_bt_only
                            net -> R.string.cfgt_onboarding_wifi_only
                            else -> R.string.cfgt_onboarding_both_off
                        },
                    ),
                    11, 400, C.Muted, maxLines = Int.MAX_VALUE,
                )
            }
            OnboardRow(Icons.Bluetooth, stringResource(R.string.cfgt_bt_onboarding), stringResource(R.string.cfgt_bt_onboarding_hint), bt, vm.onboardingSaving(Tab.BT)) { vm.setOnboarding(Tab.BT, it) }
            OnboardRow(Icons.Wifi, stringResource(R.string.cfgt_wifi_onboarding), stringResource(R.string.cfgt_wifi_onboarding_hint), net, vm.onboardingSaving(Tab.NET)) { vm.setOnboarding(Tab.NET, it) }
        }
        // AI in validation: a property setting at the server; unknown until the settings are read (then it can't be changed).
        val ai = cfg.aiValidation
        CfgCard {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt(stringResource(R.string.cfg_validation_title), 13, 700)
                Txt(
                    when {
                        ai != null -> stringResource(R.string.cfg_ai_validation_hint)
                        cfg.photos.load == LoadState.LOADING -> stringResource(R.string.cfg_loading)
                        else -> stringResource(R.string.cfg_settings_load_failed)
                    },
                    11, 400, C.Muted, maxLines = Int.MAX_VALUE,
                )
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
                Txt(
                    when {
                        metric != null -> stringResource(R.string.cfg_units_hint)
                        cfg.photos.load == LoadState.LOADING -> stringResource(R.string.cfg_loading)
                        else -> stringResource(R.string.cfg_settings_load_failed)
                    },
                    11, 400, C.Muted, maxLines = Int.MAX_VALUE,
                )
            }
            OnboardRow(Icons.Globe, stringResource(R.string.cfg_units_metric), stringResource(R.string.cfg_units_metric_row), metric == true, cfg.unitsSaving) {
                if (metric != null) cfg.setMetricUnits(it)
            }
        }
        Txt(stringResource(R.string.cfgt_on_the_phone), 10, 700, C.Muted, Modifier.padding(start = 12.dp, top = 6.dp), spacing = 0.6f)
        CfgCard {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                listOf(
                    Triple(Tab.BT, stringResource(R.string.nav_bluetooth), Icons.Bluetooth), Triple(Tab.NET, stringResource(R.string.nav_network), Icons.Wifi),
                    Triple(Tab.ASSETS, stringResource(R.string.label_assets), Icons.Box),
                    Triple(Tab.CAMERA, stringResource(R.string.nav_camera), Icons.Camera), Triple(Tab.FILE, stringResource(R.string.nav_file), Icons.FileUpload),
                ).filter { it.first in vm.navTabs }.forEach { (_, label, icon) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LineIcon(icon, 20.dp, C.Soft, 2f)
                        Txt(label, 10, 600, C.Muted)
                    }
                }
            }
        }
        Txt(stringResource(R.string.cfgt_onboarding_footer), 11, 400, C.Muted, Modifier.padding(horizontal = 12.dp), maxLines = Int.MAX_VALUE)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun OnboardRow(icon: Ico, title: String, body: String, on: Boolean, busy: Boolean = false, onChange: (Boolean) -> Unit) {
    // While its save is on its way the row is dimmed and takes no taps, as the web's switch.
    Row(
        Modifier.fillMaxWidth().topBorder(C.Border).clickable(enabled = !busy) { onChange(!on) }.alpha(if (busy) 0.6f else 1f)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(38.dp).clip(r(10)).background(C.YTintBg), contentAlignment = Alignment.Center) { LineIcon(icon, 19.dp, C.Yellow, 2.1f) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(title, 12, 700)
            Txt(body, 11, 400, C.Muted, maxLines = Int.MAX_VALUE)
        }
        OnOffSwitch(on) { if (!busy) onChange(it) }
    }
}
