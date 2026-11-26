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
    // AI and units are settings at the server, unknown until they are read (then they can't be changed).
    val reading = cfg.photos.load == LoadState.LOADING
    val ai = cfg.aiValidation
    val metric = cfg.metricUnits
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = pageGutter())
            .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        SettingsSection(stringResource(R.string.cfgs_phone_onboarding)) {
            SettingRow(
                stringResource(R.string.cfgs_bluetooth), stringResource(R.string.cfgs_bluetooth_sub),
                vm.onboardingOn(Tab.BT), busy = vm.onboardingSaving(Tab.BT),
            ) { vm.setOnboarding(Tab.BT, it) }
            SettingRow(
                stringResource(R.string.cfgs_wifi), stringResource(R.string.cfgs_wifi_sub),
                vm.onboardingOn(Tab.NET), busy = vm.onboardingSaving(Tab.NET), divider = true,
            ) { vm.setOnboarding(Tab.NET, it) }
        }
        // Saved, the asset screens' AI buttons follow at once.
        SettingsSection(stringResource(R.string.cfg_ai_title)) {
            SettingRow(
                stringResource(R.string.cfg_ai_validation), settingSub(ai, reading, R.string.cfg_ai_validation_row),
                ai == true, busy = cfg.aiSaving, known = ai != null, loading = ai == null && reading,
            ) { cfg.setAiValidation(it) }
        }
        // US or metric for the fields a type brings.
        SettingsSection(stringResource(R.string.cfg_units_title)) {
            SettingRow(
                stringResource(R.string.cfg_units_metric), settingSub(metric, reading, R.string.cfgs_units_sub),
                metric == true, busy = cfg.unitsSaving, known = metric != null, loading = metric == null && reading,
            ) { cfg.setMetricUnits(it) }
        }
    }
}

/** A server setting's line: [sub] once read, or that it couldn't be read. */
@Composable
private fun settingSub(value: Boolean?, reading: Boolean, sub: Int): String =
    stringResource(if (value == null && !reading) R.string.cfg_settings_load_failed else sub)

/** A section of settings: its name in small capitals above a card of rows. */
@Composable
private fun SettingsSection(title: String, rows: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Txt(title.uppercase(), 11, 600, C.Muted, Modifier.padding(start = 4.dp), spacing = 0.6f)
        CfgCard(content = rows)
    }
}

/**
 * A setting: its name and one short line, with its switch. Changing it needs Configure (greyed out, with why, without
 * it); while its save is on its way it is dimmed and takes no taps; a server setting not read yet ([known] false) can't
 * be changed, and shows a placeholder line while it is being read ([loading]).
 */
@Composable
private fun SettingRow(
    title: String,
    sub: String,
    on: Boolean,
    busy: Boolean = false,
    divider: Boolean = false,
    known: Boolean = true,
    loading: Boolean = false,
    onChange: (Boolean) -> Unit,
) = ConfigGate(Modifier.fillMaxWidth()) {
    val usable = known && !busy
    Row(
        Modifier.pressClickable(pressed = PRESSED_CARD, enabled = usable) { onChange(!on) }.fillMaxWidth()
            .then(if (divider) Modifier.topBorder(C.Border) else Modifier).alpha(if (busy || !known) 0.6f else 1f)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(title, 15, 600)
            if (loading) SkeletonText(stringResource(R.string.cfg_loading), Modifier.padding(top = 2.dp), lines = 1)
            else Txt(sub, 12, 400, C.Muted, maxLines = 2)
        }
        OnOffSwitch(on) { if (usable) onChange(it) }
    }
}
