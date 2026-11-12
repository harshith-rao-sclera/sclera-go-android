package com.example.sclerago.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.sclerago.presentation.icons.Ico
import com.example.sclerago.presentation.icons.Icons
import com.example.sclerago.presentation.icons.LineIcon
import com.example.sclerago.presentation.theme.C
import com.example.sclerago.presentation.theme.Txt

// Building blocks shared by Property list and People and access, as drawn in the
// "Sclera VDMS Mobile" design canvas.

internal val SheetBg = Color(0xFF1B1D21)
internal val SheetHandleColor = Color(0xFF45464C)
internal val TintText = Color(0xFFD8CFA8)
internal val PrimaryOffBg = Color(0xFF5C4A10)
internal val PrimaryOffFg = Color(0xFFC9B57A)
internal val CardWash = Color(0x80272A2F)
internal val DangerBg = Color(0xFF2A1919)
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

@Composable
internal fun PageHeading(title: String, sub: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Txt(title, 24, 700)
        sub?.let { Txt(it, 13, 400, C.Muted, maxLines = 4) }
    }
}

/** Tile with an icon, a number and what it counts. */
@Composable
internal fun StatCard(icon: Ico, tint: Color, value: String, label: String, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(r(16)).background(C.Surface).border(1.dp, C.Border, r(16)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(r(12)).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            LineIcon(icon, 18.dp, tint, 1.9f)
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Txt(value, 20, 700)
            Txt(label, 12, 400, C.Muted)
        }
    }
}

/** Yellow floating button with a plus and a label, bottom-right. */
@Composable
internal fun AdminFab(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.navigationBarsPadding().padding(end = 20.dp, bottom = 32.dp).height(56.dp).fabShadow(r(28)).clip(r(28))
            .background(C.Yellow).clickable(onClick = onClick).padding(start = 18.dp, end = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LineIcon(Icons.Plus, 20.dp, C.OnYellow, 2.4f)
        Txt(label, 15, 700, C.OnYellow)
    }
}

/** Rounded label in [color] with an icon (permissions, groups, asset counts). */
@Composable
internal fun ColorChip(label: String, color: Color, icon: Ico?) {
    Row(
        Modifier.clip(CircleShape).background(color.copy(alpha = 0.15f)).border(1.dp, color.copy(alpha = 0.3f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        icon?.let { LineIcon(it, 12.dp, color, 2.2f) }
        Txt(label, 12, 600, color)
    }
}

/** Small tinted label ("2 chosen", "Needs all properties", "None yet"). */
@Composable
internal fun TintChip(text: String, bg: Color = C.YTintBg, fg: Color = TintText) {
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
) {
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Color(0x99000000)).noRippleClick(onClose)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.85f).wrapContentHeight(Alignment.Bottom)
                .clip(SheetShape).background(SheetBg).topBorder(C.Border).noRippleClick { }.navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(36.dp, 5.dp).clip(r(3)).background(SheetHandleColor))
            Txt(title, 17, 700)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (name, sub) ->
                    val on = name == selected
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(r(14)).background(if (on) C.YTintBg else Color(0x99272A2F))
                            .border(1.dp, if (on) C.YTintBd else C.Border, r(14)).clickable { onPick(name) }
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
    bodyBackground: Color = SheetBg,
    bodyPadding: PaddingValues = PaddingValues(16.dp),
    bodySpacing: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Color(0x99000000)).noRippleClick(onClose)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(top = 56.dp).clip(SheetShape).background(SheetBg)
                .noRippleClick { }.navigationBarsPadding().imePadding(),
        ) {
            Box(Modifier.padding(top = 6.dp).align(Alignment.CenterHorizontally).size(36.dp, 5.dp).clip(r(3)).background(SheetHandleColor))
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
                    Modifier.fillMaxWidth().background(DangerBg).padding(horizontal = 16.dp, vertical = 10.dp),
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
                Pill("Cancel", onClose, Modifier.weight(1f), bg = C.Btn, height = 50.dp, size = 15, weight = 600)
                Pill(
                    primary, onPrimary, Modifier.weight(1f),
                    bg = if (primaryEnabled) C.Yellow else PrimaryOffBg, fg = if (primaryEnabled) C.OnYellow else PrimaryOffFg,
                    height = 50.dp, size = 15, weight = 700,
                )
            }
        }
    }
}
