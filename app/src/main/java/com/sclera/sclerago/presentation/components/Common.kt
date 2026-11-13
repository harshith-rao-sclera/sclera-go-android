package com.sclera.sclerago.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sclera.sclerago.R
import com.sclera.sclerago.presentation.icons.Ico
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.ScleraFonts
import com.sclera.sclerago.presentation.theme.Txt

val Capsule = RoundedCornerShape(percent = 50)
fun r(dp: Int) = RoundedCornerShape(dp.dp)

fun Modifier.bottomBorder(color: Color, width: Dp = 1.dp) = drawBehind {
    val w = width.toPx()
    drawRect(color, Offset(0f, size.height - w), Size(size.width, w))
}

fun Modifier.topBorder(color: Color, width: Dp = 1.dp) = drawBehind {
    drawRect(color, Offset.Zero, Size(size.width, width.toPx()))
}

fun Modifier.endBorder(color: Color, width: Dp = 1.dp) = drawBehind {
    val w = width.toPx()
    drawRect(color, Offset(size.width - w, 0f), Size(w, size.height))
}

fun Modifier.dashedBorder(color: Color, width: Dp, radius: Dp) = drawBehind {
    val w = width.toPx()
    drawRoundRect(
        color, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
    )
}

/** CSS-style `outline` drawn outside the box (used for OCR highlights). */
fun Modifier.outline(color: Color, width: Dp = 1.5.dp, offset: Dp = 2.dp) = drawBehind {
    val o = offset.toPx() + width.toPx() / 2
    drawRect(color, Offset(-o, -o), Size(size.width + 2 * o, size.height + 2 * o), style = Stroke(width.toPx()))
}

/**
 * Keeps sideways drags inside this element (a sheet or dialog shown over swipeable pages), so a
 * swipe on it doesn't change the page behind. Lists inside still scroll sideways; they get the drag first.
 */
fun Modifier.blockSwipe() = pointerInput(Unit) { detectHorizontalDragGestures { change, _ -> change.consume() } }

fun Modifier.noRippleClick(onClick: () -> Unit) = composed {
    clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
}

fun Modifier.fabShadow(shape: Shape = CircleShape, elevation: Dp = 10.dp) =
    shadow(elevation, shape, ambientColor = Color.Black, spotColor = Color.Black)

@Composable
fun Pill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bg: Color = C.Btn,
    fg: Color = C.Text,
    height: Dp = 36.dp,
    hPad: Dp = 12.dp,
    size: Int = 13,
    weight: Int = 600,
) {
    Box(
        modifier.height(height).clip(Capsule).background(bg).clickable(onClick = onClick).padding(horizontal = hPad),
        contentAlignment = Alignment.Center,
    ) { Txt(text, size, weight, fg) }
}

@Composable
fun RoundButton(
    size: Dp,
    bg: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier.size(size).clip(shape).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
fun CloseButton(onClick: () -> Unit, size: Dp = 44.dp, iconSize: Dp = 18.dp, bg: Color = C.Btn) {
    RoundButton(size, bg, onClick) { LineIcon(Icons.Close, iconSize, C.Text, 2.6f) }
}

@Composable
fun RadioDot(on: Boolean) {
    Box(
        Modifier.size(22.dp).border(2.dp, if (on) C.Yellow else C.RadioOff, CircleShape),
        contentAlignment = Alignment.Center,
    ) { if (on) Box(Modifier.size(10.dp).background(C.Yellow, CircleShape)) }
}

@Composable
fun CheckBox(on: Boolean, shape: Shape = r(7), offColor: Color = C.RadioOff, checkSize: Dp = 14.dp) {
    Box(
        Modifier.size(24.dp).clip(shape).background(if (on) C.Yellow else Color.Transparent)
            .border(2.dp, if (on) C.Yellow else offColor, shape),
        contentAlignment = Alignment.Center,
    ) { if (on) LineIcon(Icons.Check, checkSize, C.OnYellow, 3.4f) }
}

@Composable
fun Segmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, icons: List<Ico?> = emptyList()) {
    Row(
        Modifier.width(200.dp).clip(r(10)).background(C.Surface).border(1.dp, C.Border, r(10)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        labels.forEachIndexed { i, l ->
            val on = i == selected
            val bg by animateColorAsState(if (on) C.Yellow else Color.Transparent, label = "seg")
            Box(
                Modifier.weight(1f).height(30.dp).clip(r(7)).background(bg).clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                val fg = if (on) C.OnYellow else C.Muted
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    icons.getOrNull(i)?.let { LineIcon(it, 18.dp, fg, 2.2f) }
                    Txt(l, 13, 700, fg)
                }
            }
        }
    }
}

/** Sticky header holding the Assets / Scan style segmented control. */
@Composable
fun SegHeader(labels: List<String>, selected: Int, top: Dp = 10.dp, icons: List<Ico?> = emptyList(), onSelect: (Int) -> Unit) {
    Box(
        Modifier.fillMaxWidth().background(C.Bg).padding(start = 12.dp, end = 12.dp, top = top, bottom = 2.dp),
        contentAlignment = Alignment.Center,
    ) { Segmented(labels, selected, onSelect, icons) }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, height: Dp = 44.dp) {
    Row(
        modifier.fillMaxWidth().height(height).clip(r(12)).background(C.Bg).border(1.dp, C.Border, r(12))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LineIcon(Icons.Search, 18.dp, C.Muted)
        PlainField(value, onChange, placeholder, Modifier.weight(1f), size = 14)
    }
}

/** When true, fields below are shown but can't be edited (e.g. a submitted asset). */
val LocalReadOnly = androidx.compose.runtime.compositionLocalOf { false }

@Composable
fun PlainField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    size: Int = 15,
    weight: Int = 400,
    mono: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    readOnly: Boolean = false,
    keyboard: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboard),
        readOnly = readOnly || LocalReadOnly.current,
        singleLine = singleLine,
        minLines = minLines,
        cursorBrush = SolidColor(C.Yellow),
        textStyle = TextStyle(
            color = C.Text, fontSize = size.sp, fontWeight = FontWeight(weight),
            fontFamily = if (mono) androidx.compose.ui.text.font.FontFamily.Monospace else ScleraFonts.Inter,
        ),
        decorationBox = { inner ->
            Box(contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart) {
                if (value.isEmpty()) Txt(placeholder, size, 400, C.Muted, maxLines = minLines)
                inner()
            }
        },
    )
}

@Composable
fun SignalBars(lvl: Int) {
    Row(
        Modifier.size(40.dp).clip(r(10)).background(C.Raised).padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Bottom,
    ) {
        for (i in 1..4) {
            Box(Modifier.width(4.dp).height((4 + i * 4).dp).clip(r(1)).background(if (i <= lvl) C.Yellow else C.Border2))
        }
    }
}

/** The VOLTLINE nameplate artwork used in photo previews. */
@Composable
fun NameplateLarge() {
    val ink = Color(0xFF1E2126)
    Column(
        Modifier.width(300.dp)
            .drawBehind {
                drawRoundRect(Color(0xFF9AA0A7), Offset(0f, 2.dp.toPx()), size, CornerRadius(8.dp.toPx()))
            }
            .background(Color(0xFFC9CDD2), r(8))
            .padding(horizontal = 24.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Txt("VOLTLINE MOTORS", 18, 700, ink, mono = true)
        Txt("3~ INDUCTION MOTOR", 12, 500, ink, mono = true)
        Txt("TYPE VM-160L", 14, 700, ink, mono = true)
        Txt("S/N 24A-118903", 14, 700, ink, mono = true)
        Txt("15 kW  415 V  50 Hz", 14, 700, ink, mono = true)
        Txt("1460 RPM  IP55", 14, 700, ink, mono = true)
    }
}

@Composable
fun Toast(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(Capsule).background(Color(0xFF17241D)).border(1.dp, Color(0xFF27402F), Capsule)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LineIcon(Icons.Check, 15.dp, C.Green, 2.8f)
        Txt(text, 13, 700, C.Green)
    }
}

/** Grab handle at the top of bottom sheets. */
@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(modifier.size(40.dp, 4.dp).clip(r(2)).background(C.Border2))
}

/**
 * Bottom action bar for assets: a leading area (selection count, or a single asset's status),
 * then Validate, Assign, Submit and Delete, each in its own color. Used by list selection and asset details.
 * Pass null for an action to leave it out (e.g. Submit on an asset that is already submitted).
 */
@Composable
fun ActionDock(
    onValidate: (() -> Unit)?,
    onAssign: () -> Unit,
    onSubmit: (() -> Unit)?,
    onDelete: () -> Unit,
    floating: Boolean = false,
    leading: @Composable RowScope.() -> Unit,
) {
    // Floating: inset from the edges with rounded corners and a shadow; otherwise flush with a top border.
    val shape = r(14)
    val frame = if (floating) {
        Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 8.dp).fillMaxWidth().height(60.dp)
            .fabShadow(shape, 10.dp).clip(shape).background(C.Surface).border(1.dp, C.Border2, shape)
    } else {
        Modifier.fillMaxWidth().height(60.dp).background(C.Surface).topBorder(C.Border2)
    }
    Row(
        frame.padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        onValidate?.let { DockAction(stringResource(R.string.action_validate), Icons.ShieldCheck, C.Purple, it) }
        DockAction(stringResource(R.string.action_assign), Icons.UserPlus, C.Cyan, onAssign)
        onSubmit?.let { DockAction(stringResource(R.string.action_submit), Icons.Upload, C.Blue, it) }
        DockAction(stringResource(R.string.action_delete), Icons.Trash, C.Red, onDelete)
    }
}

/**
 * The same four actions as a rounded card inside a form (asset details): actions spread evenly, no leading area.
 * Pass null for an action to leave it out.
 */
@Composable
fun ActionCard(
    onValidate: (() -> Unit)?,
    onAssign: () -> Unit,
    onSubmit: (() -> Unit)?,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = r(14)
    Row(
        modifier.fillMaxWidth().height(64.dp).clip(shape).background(C.Surface).border(1.dp, C.Border2, shape)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val item = Modifier.weight(1f)
        // Validate stays in place but greyed out when the asset is already validated.
        DockAction(stringResource(R.string.action_validate), Icons.ShieldCheck, C.Purple, onValidate ?: {}, item.alpha(if (onValidate != null) 1f else 0.35f), enabled = onValidate != null)
        DockAction(stringResource(R.string.action_assign), Icons.UserPlus, C.Cyan, onAssign, item)
        onSubmit?.let { DockAction(stringResource(R.string.action_submit), Icons.Upload, C.Blue, it, item) }
        DockAction(stringResource(R.string.action_delete), Icons.Trash, C.Red, onDelete, item)
    }
}

@Composable
private fun DockAction(
    label: String,
    icon: com.sclera.sclerago.presentation.icons.Ico,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.width(56.dp),
    enabled: Boolean = true,
) {
    Column(
        modifier.fillMaxHeight().padding(vertical = 4.dp).clip(r(10)).clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        LineIcon(icon, 21.dp, color, 2.1f)
        Txt(label, 11, 700, color)
    }
}
