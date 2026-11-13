package com.sclera.sclerago.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.VIEW_ONLY_COLOR
import com.sclera.sclerago.presentation.icons.Ico
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

// Building blocks shared by Property list and People and access, as drawn in the
// "Sclera VDMS Mobile" design canvas.

/** Opacity of a field that can't be edited right now (the design's locked and waiting fields). */
private const val DimmedAlpha = 0.55f

private val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/** A 24×24 stroke icon from an SVG path (used for the per-permission icons). */
internal fun iconOf(path: String) = Ico(Ico.Part(path))

internal fun Long.asColor() = Color(this)

/** App bar of the side-menu pages: back button, then the page title over the current property. */
@Composable
internal fun SubPageBar(title: String, property: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(C.Surface).bottomBorder(C.Border).statusBarsPadding().height(64.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RoundButton(44.dp, C.Btn, onBack, Modifier.border(1.dp, C.Border2, CircleShape)) {
            LineIcon(Icons.ChevronLeft, 20.dp, C.Yellow, 2.4f)
        }
        Column(Modifier.weight(1f).padding(end = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Txt(title, 16, 700)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                LineIcon(Icons.Building, 12.dp, C.Muted, 1.9f)
                Txt(property, 11, 500, C.Muted)
            }
        }
    }
}

/** Page title (22 by default) with an optional count or description under it. */
@Composable
internal fun PageHeading(title: String, sub: String?, modifier: Modifier = Modifier, titleSize: Int = 22, gap: Dp = 2.dp) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        Txt(title, titleSize, 700)
        sub?.let { Txt(it, 13, 400, C.Muted, maxLines = 4) }
    }
}

/** Tile with a tinted icon, a number and what it counts (the 2-up tiles of the list pages). */
@Composable
internal fun StatCard(icon: Ico, tint: Color, value: String, label: String, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(r(12)).background(C.Surface).border(1.dp, C.Border, r(12)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(36.dp).clip(r(10)).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            LineIcon(icon, 16.dp, tint, 1.9f)
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Txt(value, 20, 700)
            Txt(label, 12, 400, C.Muted)
        }
    }
}

/** Two [StatCard]s side by side, equal width. */
@Composable
internal fun StatRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** Rounded label in [color] with an icon (permissions, groups, asset counts); [small] for compact rows. */
@Composable
internal fun ColorChip(label: String, color: Color, icon: Ico?, small: Boolean = false, iconSize: Dp = if (small) 11.dp else 12.dp) {
    Row(
        Modifier.clip(CircleShape).background(color.copy(alpha = 0.15f)).border(1.dp, color.copy(alpha = 0.3f), CircleShape)
            .padding(horizontal = if (small) 8.dp else 10.dp, vertical = if (small) 3.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (small) 4.dp else 5.dp),
    ) {
        icon?.let { LineIcon(it, iconSize, color, 2.2f) }
        Txt(label, if (small) 11 else 12, 600, color)
    }
}

/** Small tinted label ("2 chosen", "Needs all properties", "None yet"). */
@Composable
internal fun TintChip(text: String, bg: Color = C.YTintBg, fg: Color = C.TintText) {
    Box(Modifier.clip(CircleShape).background(bg).padding(horizontal = 8.dp, vertical = 3.dp)) { Txt(text, 11, 600, fg) }
}

@Composable
internal fun FieldLabel(text: String, optional: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Txt(text, 14, 600)
        if (optional) Txt(" (optional)", 14, 400, C.Muted)
    }
}

/** Text input of the sheets: 48 high, dark, rounded 12. */
@Composable
internal fun SheetInput(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean = true,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    PlainField(
        value, onChange, placeholder,
        Modifier.fillMaxWidth().height(48.dp).alpha(if (enabled) 1f else 0.55f).clip(r(12)).background(C.Bg)
            .border(1.dp, C.Border, r(12)).padding(horizontal = 14.dp).wrapContentHeight(Alignment.CenterVertically),
        size = 15, readOnly = !enabled, keyboard = keyboard,
    )
}

/** A field that opens a picker. */
@Composable
internal fun SheetSelect(value: String, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(r(12)).background(C.Bg)
            .border(1.dp, if (active) C.YTintBd else C.Border, r(12)).clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(value, 15, 400, modifier = Modifier.weight(1f))
        LineIcon(Icons.ChevronDown, 14.dp, C.Muted, 2.6f)
    }
}

/** Ring with a yellow dot when [on] (the design's radio). */
@Composable
internal fun RadioRing(on: Boolean, size: Dp = 22.dp) {
    Box(Modifier.size(size).border(2.dp, if (on) C.Yellow else C.RadioOff, CircleShape), contentAlignment = Alignment.Center) {
        if (on) Box(Modifier.size(size * 0.45f).background(C.Yellow, CircleShape))
    }
}

/** Bottom sheet listing [options] (name to description); the [selected] one is marked. */
@Composable
internal fun ChoiceSheet(
    title: String,
    options: List<Pair<String, String?>>,
    selected: String?,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
    /** Options that are shown but can't be picked. */
    disabled: Set<String> = emptySet(),
) {
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(C.Overlay).noRippleClick(onClose)) {
        Column(
            sheetFrame(C.SheetBg, radius = 28.dp, heightFraction = 0.85f)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!isTablet()) Box(Modifier.align(Alignment.CenterHorizontally).size(36.dp, 5.dp).clip(r(3)).background(C.SheetHandle))
            Txt(title, 17, 700)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (name, sub) ->
                    val on = name == selected
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp).alpha(if (name in disabled) DimmedAlpha else 1f).clip(r(14)).background(if (on) C.YTintBg else C.FieldRowWash)
                            .border(1.dp, if (on) C.YTintBd else C.Border, r(14)).clickable(enabled = name !in disabled) { onPick(name) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Txt(name, 15, 600)
                            sub?.let { Txt(it, 13, 400, C.Muted, maxLines = 2) }
                        }
                        RadioRing(on)
                    }
                }
            }
        }
    }
}

/**
 * Modal sheet over the page (top 56dp of the page stays visible): handle, header with title,
 * subtitle and close button, scrolling [content], and a footer with Cancel and the [primary]
 * action. [error] is shown above the footer; [progress] (0–1) draws the yellow bar under the header.
 */
@Composable
internal fun FormSheet(
    title: String,
    sub: String?,
    primary: String,
    primaryEnabled: Boolean,
    error: String?,
    onClose: () -> Unit,
    onPrimary: () -> Unit,
    leadingIcon: Ico? = null,
    progress: Float? = null,
    bodyBackground: Color = C.SheetBg,
    bodyPadding: PaddingValues = PaddingValues(16.dp),
    bodySpacing: Dp = 18.dp,
    primaryModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(C.Overlay).noRippleClick(onClose)) {
        Column(
            (if (isTablet()) sheetFrame(C.SheetBg, radius = 28.dp, heightFraction = 0.9f, fill = true)
            else Modifier.fillMaxSize().statusBarsPadding().padding(top = 56.dp).clip(SheetShape).background(C.SheetBg)
                .noRippleClick { }.navigationBarsPadding()).imePadding(),
        ) {
            if (!isTablet()) Box(Modifier.padding(top = 6.dp).align(Alignment.CenterHorizontally).size(36.dp, 5.dp).clip(r(3)).background(C.SheetHandle))
            Row(
                Modifier.fillMaxWidth().then(if (progress == null) Modifier.bottomBorder(C.Border) else Modifier).padding(16.dp, 14.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                leadingIcon?.let {
                    Box(
                        Modifier.size(44.dp).clip(r(13)).background(C.YTintBg).border(1.dp, C.YTintBd, r(13)),
                        contentAlignment = Alignment.Center,
                    ) { LineIcon(it, 20.dp, C.Yellow, 1.9f) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Txt(title, 19, 700)
                    sub?.let { Txt(it, 13, 400, C.Muted, maxLines = 3) }
                }
                CloseButton(onClose, size = 36.dp, iconSize = 14.dp)
            }
            progress?.let { p ->
                Box(Modifier.fillMaxWidth().height(3.dp).background(C.Border)) {
                    Box(Modifier.fillMaxWidth(p.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)).background(C.Yellow))
                }
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().background(bodyBackground).verticalScroll(rememberScrollState()).padding(bodyPadding),
                verticalArrangement = Arrangement.spacedBy(bodySpacing),
                content = content,
            )
            error?.let {
                Row(
                    Modifier.fillMaxWidth().background(C.DangerBg).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LineIcon(Icons.Alert, 16.dp, C.Red, 2.2f)
                    Txt(it, 13, 500, C.Text, Modifier.weight(1f), maxLines = 3)
                }
            }
            Row(
                Modifier.fillMaxWidth().topBorder(C.Border).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Pill(stringResource(R.string.action_cancel), onClose, Modifier.weight(1f), bg = C.Btn, height = 50.dp, size = 15, weight = 600)
                Pill(
                    primary, onPrimary, Modifier.weight(1f).then(primaryModifier),
                    bg = if (primaryEnabled) C.Yellow else C.PrimaryOffBg, fg = if (primaryEnabled) C.OnYellow else C.PrimaryOffFg,
                    height = 50.dp, size = 15, weight = 700,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Sectioned forms (Add property, people, groups)

/** A titled group of fields (full-width band with dividers); [collapsible] ones fold under a chevron. */
@Composable
internal fun FormGroup(
    title: String,
    icon: Ico,
    collapsible: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var open by rememberSaveable(title) { mutableStateOf(true) }
    val chevron by animateFloatAsState(if (open) 0f else -90f, label = "group")
    Column(Modifier.fillMaxWidth().background(C.Surface).topBorder(C.Border).bottomBorder(C.Border).padding(bottom = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().height(52.dp).then(if (collapsible) Modifier.clickable { open = !open } else Modifier)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(28.dp).clip(r(8)).background(C.YTintBg), contentAlignment = Alignment.Center) { LineIcon(icon, 15.dp, C.Yellow, 1.9f) }
            Txt(title, 15, 700, modifier = Modifier.weight(1f))
            trailing?.invoke()
            if (collapsible) LineIcon(Icons.ChevronDown, 14.dp, C.Muted, 2.6f, Modifier.rotate(chevron))
        }
        AnimatedVisibility(open) { Column(content = content) }
    }
}

/** Row: label on the left (104dp), the input on the right. */
@Composable
internal fun InlineField(label: String, required: Boolean = false, dim: Boolean = false, input: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).topBorder(C.Border).alpha(if (dim) DimmedAlpha else 1f).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.width(104.dp)) {
            Txt(label, 13, 500, C.Muted)
            if (required) Txt("*", 13, 600, C.Yellow, Modifier.padding(start = 2.dp))
        }
        input()
    }
}

@Composable
internal fun RowScope.InlineInput(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    PlainField(value, onChange, placeholder, Modifier.weight(1f).then(modifier), size = 15, readOnly = !enabled, keyboard = keyboard)
}

@Composable
internal fun RowScope.InlineSelect(
    value: String,
    placeholder: String,
    enabled: Boolean = true,
    chevron: Boolean = true,
    upDown: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier.weight(1f).heightIn(min = 44.dp).clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(value.ifEmpty { placeholder }, 15, 400, if (value.isEmpty()) C.Dim else C.Text, Modifier.weight(1f))
        if (chevron && upDown) LineIcon(Icons.ChevronUpDown, 12.dp, C.Yellow, 2.4f)
        else if (chevron) LineIcon(Icons.ChevronDown, 14.dp, C.Muted, 2.6f)
    }
}

/** A section's status on the right of its title: "Ready" in green, "1 required" in yellow, "Locked" in grey. */
enum class PillTone { READY, NEEDED, NEUTRAL }

@Composable
internal fun StatusPill(text: String, tone: PillTone) {
    val c = when (tone) {
        PillTone.READY -> C.Green
        PillTone.NEEDED -> C.Yellow
        PillTone.NEUTRAL -> VIEW_ONLY_COLOR.asColor()
    }
    TintChip(text, bg = c.copy(alpha = 0.15f), fg = c)
}
