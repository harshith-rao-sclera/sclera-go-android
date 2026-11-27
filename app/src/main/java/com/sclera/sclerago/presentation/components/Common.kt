package com.sclera.sclerago.presentation.components

import com.sclera.sclerago.presentation.model.color
import com.sclera.sclerago.domain.model.Status
import androidx.compose.foundation.LocalIndication
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sclera.sclerago.R
import com.sclera.sclerago.presentation.icons.Ico
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.model.OnlineOnly
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

/**
 * For a page drawn over another one (a side-menu page over the dashboard): every touch on it stays on it, so a tap on
 * a bare part (a bar, a gap between cards) can't reach a button of the page underneath.
 */
fun Modifier.coverPageBehind() = pointerInput(Unit) { detectTapGestures() }

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
    val press = remember { MutableInteractionSource() }
    Box(
        modifier.pressScale(press).height(height).clip(Capsule).background(bg)
            .clickable(press, LocalIndication.current, onClick = onClick).padding(horizontal = hPad),
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
    val press = remember { MutableInteractionSource() }
    Box(
        modifier.pressScale(press).size(size).clip(shape).background(bg).clickable(press, LocalIndication.current, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
fun CloseButton(onClick: () -> Unit, size: Dp = 48.dp, iconSize: Dp = 18.dp, bg: Color = C.Btn) {
    val label = stringResource(R.string.action_close)
    RoundButton(size, bg, onClick, Modifier.semantics { contentDescription = label }) { LineIcon(Icons.Close, iconSize, C.Text, 2.6f) }
}

@Composable
fun RadioDot(on: Boolean) {
    val edge by animateColorAsState(if (on) C.Yellow else C.RadioOff, tween(180), label = "radio-edge")
    val dot by animateFloatAsState(if (on) 1f else 0f, tween(180), label = "radio-dot")
    Box(
        Modifier.size(22.dp).border(2.dp, edge, CircleShape),
        contentAlignment = Alignment.Center,
    ) { if (dot > 0f) Box(Modifier.size(10.dp).graphicsLayer { scaleX = dot; scaleY = dot }.background(C.Yellow, CircleShape)) }
}

/** A tick box; [partly] (when not [on]) shows a dash, for a parent some of whose children are ticked. */
@Composable
fun CheckBox(
    on: Boolean,
    shape: Shape = r(7),
    offColor: Color = C.RadioOff,
    checkSize: Dp = 14.dp,
    size: Dp = 24.dp,
    stroke: Dp = 2.dp,
    partly: Boolean = false,
) {
    val fill by animateColorAsState(if (on) C.Yellow else Color.Transparent, tween(180), label = "check-fill")
    val edge by animateColorAsState(if (on || partly) C.Yellow else offColor, tween(180), label = "check-edge")
    val mark by animateFloatAsState(if (on || partly) 1f else 0f, tween(180), label = "check-mark")
    Box(
        Modifier.size(size).clip(shape).background(fill).border(stroke, edge, shape),
        contentAlignment = Alignment.Center,
    ) {
        val grow = Modifier.graphicsLayer { scaleX = 0.5f + 0.5f * mark; scaleY = scaleX; alpha = mark }
        if (on) LineIcon(Icons.Check, checkSize, C.OnYellow, 3.4f, grow)
        else if (partly) LineIcon(Icons.Minus, checkSize, C.Yellow, 3.4f, grow)
    }
}

@Composable
fun Segmented(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    icons: List<Ico?> = emptyList(),
    /** Its width: 200dp, or e.g. the full width. */
    modifier: Modifier = Modifier.width(200.dp),
) {
    Row(
        modifier.clip(r(10)).background(C.Surface).border(1.dp, C.Border, r(10)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        labels.forEachIndexed { i, l ->
            val on = i == selected
            val bg by animateColorAsState(if (on) C.Yellow else Color.Transparent, label = "seg")
            Box(
                Modifier.weight(1f).height(30.dp).pressClickable(shape = r(7)) { onSelect(i) }.background(bg),
                contentAlignment = Alignment.Center,
            ) {
                val fg = if (on) C.OnYellow else C.Muted
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    icons.getOrNull(i)?.let { LineIcon(it, 18.dp, fg, 2.2f) }
                    Txt(l, 12, 700, fg)
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
fun SearchField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    iconColor: Color = C.Muted,
    /** When set, the keyboard's search key calls it. */
    onSubmit: (() -> Unit)? = null,
    bg: Color = C.Bg,
) {
    Row(
        modifier.fillMaxWidth().height(height).clip(r(12)).background(bg).border(1.dp, C.Border, r(12))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LineIcon(Icons.Search, 18.dp, iconColor)
        PlainField(value, onChange, placeholder, Modifier.weight(1f), size = 13, onSubmit = onSubmit)
    }
}

/** When true, fields below are shown but can't be edited (e.g. a submitted asset). */
val LocalReadOnly = androidx.compose.runtime.compositionLocalOf { false }

/**
 * A bare text field. It keeps the text being typed itself, so typing never waits for [value] to come back from a view
 * model (which made the cursor jump): a [value] that is one of the texts just typed coming back is ignored, any other
 * (an AI fill, a discard, another asset) replaces the text.
 */
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
    /** When set, the keyboard shows a search key that calls it (e.g. the geofence address search). */
    onSubmit: (() -> Unit)? = null,
    /** When set, an edit whose text it refuses is undone as it's typed (e.g. letters in a number). */
    accept: ((String) -> Boolean)? = null,
) {
    // The state-based text field: a one-line value scrolls as the cursor or its handle is dragged towards a hidden end,
    // so the cursor can be put anywhere in a long value (the value-based field didn't scroll while the handle moved).
    // Not being edited, a field shows its text from the start (a long one-line value isn't scrolled to its end);
    // being edited, a value that comes in from outside puts the cursor at its end, ready to type on.
    val state = rememberTextFieldState(value, TextRange.Zero)
    var focused by remember { mutableStateOf(false) }
    // What was typed and sent to [onChange] but hasn't come back as [value] yet, oldest first.
    val typed = remember { ArrayDeque<String>() }
    // A value put in from outside, so it isn't sent back as typed.
    var applied by remember { mutableStateOf<String?>(null) }
    val send by rememberUpdatedState(onChange)
    val current by rememberUpdatedState(value)
    LaunchedEffect(value) {
        val now = state.text.toString()
        if (value == now) {
            typed.clear()
            return@LaunchedEffect
        }
        val echo = typed.indexOf(value)
        if (echo >= 0) {
            repeat(echo + 1) { typed.removeFirst() }
            return@LaunchedEffect
        }
        typed.clear()
        applied = value
        state.edit {
            replace(0, length, value)
            selection = if (focused) TextRange(value.length) else TextRange.Zero
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect { text ->
            when {
                text == applied -> applied = null
                text == current && typed.isEmpty() -> Unit
                else -> {
                    typed.addLast(text)
                    send(text)
                }
            }
        }
    }
    BasicTextField(
        state = state,
        modifier = modifier.onFocusChanged { f ->
            if (focused && !f.isFocused) state.edit { selection = TextRange.Zero }
            focused = f.isFocused
        },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = keyboard,
            imeAction = if (onSubmit != null) androidx.compose.ui.text.input.ImeAction.Search else androidx.compose.ui.text.input.ImeAction.Default,
        ),
        onKeyboardAction = onSubmit?.let { submit -> KeyboardActionHandler { submit() } },
        inputTransformation = accept?.let { ok ->
            androidx.compose.foundation.text.input.InputTransformation { if (!ok(asCharSequence().toString())) revertAllChanges() }
        },
        readOnly = readOnly || LocalReadOnly.current,
        lineLimits = if (singleLine) TextFieldLineLimits.SingleLine else TextFieldLineLimits.MultiLine(minHeightInLines = minLines),
        cursorBrush = SolidColor(C.Yellow),
        textStyle = TextStyle(
            color = C.Text, fontSize = size.sp, fontWeight = FontWeight(weight),
            fontFamily = if (mono) androidx.compose.ui.text.font.FontFamily.Monospace else ScleraFonts.Inter,
        ),
        decorator = { inner ->
            Box(contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart) {
                if (state.text.isEmpty()) Txt(placeholder, size, 400, C.Muted, maxLines = if (singleLine) 1 else Int.MAX_VALUE)
                inner()
            }
        },
    )
}

/** A signal strength as four small bars, [lvl] of them lit (0–4), to sit beside a name. */
@Composable
fun SignalBars(lvl: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.height(12.dp),
        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        for (i in 1..4) {
            Box(Modifier.width(2.5.dp).height((3 * i).dp).clip(r(1)).background(if (i <= lvl) C.Yellow else C.Border2))
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

/** Grab handle at the top of bottom sheets. */
@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(modifier.size(40.dp, 4.dp).clip(r(2)).background(C.Border2))
}

/**
 * Bottom action bar: a leading area (selection count, or a single asset's status), then Validate, Edit, Assign,
 * Submit, Move and Delete (the web's selection bar order), each in its own color. Used by list selection (assets, groups) and asset details.
 * Pass null for an action to leave it out (e.g. Submit on an asset that is already submitted; groups only Delete).
 */
@Composable
fun ActionDock(
    onValidate: (() -> Unit)?,
    onAssign: (() -> Unit)?,
    onSubmit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    floating: Boolean = false,
    deleteModifier: Modifier = Modifier.width(56.dp),
    /** Move to managed assets; null hides it. [moveDimmed] greys it out (it still answers a tap, to say why). */
    onMove: (() -> Unit)? = null,
    moveDimmed: Boolean = false,
    /** Edit the selected assets (their location); null hides it. */
    onEdit: (() -> Unit)? = null,
    /** Offline: Validate, Assign and Move need the server, so they are dimmed with a badge and a tap says why ([onOffline]). */
    offline: Boolean = false,
    onOffline: (OnlineOnly) -> Unit = {},
    /** The change-request actions for submitted assets (request, move to draft, decline, withdraw); null hides it. */
    onChange: (() -> Unit)? = null,
    /** Greys Submit out (as the web's "Some are already submitted"); it still answers a tap, to say why. */
    submitDimmed: Boolean = false,
    /** Assign reads "Assign to me" (someone who isn't an admin takes the assets for themselves). */
    assignToMe: Boolean = false,
    /** Greys Assign out ("Assign to me" can't take these); it still answers a tap, to say why. */
    assignDimmed: Boolean = false,
    /** Why each action is greyed out for the signed-in user (the User Access Guide); a tap says why. */
    access: DockAccess = DockAccess(),
    /** "Assign to me", its own action after Assign (someone who isn't a reviewer takes the assets); null leaves it out. */
    onTake: (() -> Unit)? = null,
    /** Why "Assign to me" is greyed out for the signed-in user ([AccessGate]); null when they may use it. */
    takeAccess: com.sclera.sclerago.presentation.common.UiText? = null,
    leading: @Composable RowScope.() -> Unit,
) {
    // Floating: the selection dock, flush with the screen edges: a tinted summary strip over the action row; otherwise one row with a top border.
    val actionsRow: @Composable RowScope.() -> Unit = {
        // With more actions each one is a little narrower, so the selection summary keeps room on phones.
        val actions = listOfNotNull(onValidate, onEdit, onAssign, onTake, onSubmit, onChange, onMove, onDelete).size
        val narrow = actions >= 5
        val item = if (floating) Modifier.weight(1f) else Modifier.width(if (actions >= 6) 46.dp else if (narrow) 50.dp else 56.dp)
        // Narrow, a long label ("Assign to me") wraps onto a second, slightly smaller line rather than being cut off.
        val lines = if (narrow) 2 else 1
        val size = if (narrow) 11 else 12
        // An action the user's group can't use is greyed out by its access gate alone (no offline badge or dimming on top).
        onValidate?.let {
            DockGate(access.validate, item) { m ->
                OfflineGate(offline && access.validate == null, { onOffline(OnlineOnly.VALIDATE) }, m) {
                    DockAction(stringResource(R.string.action_validate), Icons.ValidationChecks, C.Yellow, it, Modifier.fillMaxWidth(), lines, size)
                }
            }
        }
        onEdit?.let { DockGate(access.edit, item) { m -> DockAction(stringResource(R.string.action_edit), Icons.Pencil, C.Orange, it, m, lines, size) } }
        onAssign?.let {
            DockGate(access.assign, item) { m ->
                OfflineGate(offline && access.assign == null, { onOffline(OnlineOnly.ASSIGN) }, m) {
                    val label = stringResource(if (assignToMe) R.string.action_assign_to_me else R.string.action_assign)
                    val dim = assignDimmed && !offline && access.assign == null
                    DockAction(label, Icons.UserPlus, C.Cyan, it, Modifier.fillMaxWidth().alpha(if (dim) 0.35f else 1f), lines, size)
                }
            }
        }
        onTake?.let {
            DockGate(takeAccess, item) { m ->
                OfflineGate(offline && takeAccess == null, { onOffline(OnlineOnly.ASSIGN) }, m) {
                    DockAction(stringResource(R.string.action_assign_to_me), Icons.UserPlus, C.Cyan, it, Modifier.fillMaxWidth(), lines, size)
                }
            }
        }
        onSubmit?.let {
            DockGate(access.submit, item) { m ->
                DockAction(
                    stringResource(R.string.action_submit), Icons.StatusSubmitted, Status.SUBMITTED.color, it,
                    m.alpha(if (submitDimmed && access.submit == null) 0.35f else 1f), lines, size,
                )
            }
        }
        onChange?.let { DockGate(access.change, item) { m -> DockAction(stringResource(R.string.action_change), Icons.ChangeRequest, C.Orange, it, m, lines, size) } }
        onMove?.let {
            DockGate(access.move, item) { m ->
                OfflineGate(offline && access.move == null, { onOffline(OnlineOnly.MOVE_TO_MANAGED) }, m) {
                    val dim = moveDimmed && !offline && access.move == null
                    DockAction(stringResource(R.string.mv_action), Icons.Managed, C.Yellow, it, Modifier.fillMaxWidth().alpha(if (dim) 0.35f else 1f), lines, size)
                }
            }
        }
        onDelete?.let {
            DockGate(access.delete, if (narrow || floating) item else deleteModifier) { m ->
                DockAction(stringResource(R.string.action_delete), Icons.Trash, C.Red, it, m, lines, size)
            }
        }
    }
    if (floating) {
        Column(Modifier.fillMaxWidth().background(C.Surface)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).background(C.YTintBg).topBorder(C.YTintBd).padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = { leading() },
            )
            // As tall as the asset bar's action card, so a label that wraps to two lines fits under its icon.
            Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, content = actionsRow)
        }
    } else {
        Row(
            Modifier.fillMaxWidth().height(60.dp).background(C.Surface).topBorder(C.Border2).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading()
            actionsRow()
        }
    }
}

/** One action of the asset bar ([ActionCard]). [online]: it needs the server, so offline it is dimmed with a badge. */
class DockItem(
    val label: String,
    val icon: com.sclera.sclerago.presentation.icons.Ico,
    val color: Color,
    val onClick: () -> Unit,
    val online: OnlineOnly? = null,
    val tag: String? = null,
    /** Why the signed-in user can't use it ([AccessGate]): greyed out, and a tap says this; null when they can. */
    val blocked: com.sclera.sclerago.presentation.common.UiText? = null,
)

/**
 * The asset's actions as a rounded card inside a form (asset details), as the web's asset bar: those that apply, in
 * order, spread evenly (at most five apply to one asset). An action that needs the server is dimmed offline with a
 * badge, and a tap says why ([onOffline]); one the user may not use ([DockItem.blocked]) is greyed out and says why.
 */
@Composable
fun ActionCard(
    items: List<DockItem>,
    modifier: Modifier = Modifier,
    offline: Boolean = false,
    onOffline: (OnlineOnly) -> Unit = {},
) {
    if (items.isEmpty()) return
    val shape = r(14)
    Row(
        modifier.fillMaxWidth().height(64.dp).clip(shape).background(C.Surface).border(1.dp, C.Border2, shape)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val item = Modifier.weight(1f)
        // Offline, a gated action shrinks to its icon and label so the badge sits on it; online it fills its cell.
        val gated = if (offline) Modifier.widthIn(min = 64.dp) else Modifier.fillMaxWidth()
        items.forEach { a ->
            val tagged = a.tag?.let { Modifier.testTag(it) } ?: Modifier
            val online = a.online
            val blocked = a.blocked
            when {
                // Not this user's to use: greyed out, and a tap says why (going online wouldn't change that).
                blocked != null -> AccessGate(blocked, item.fillMaxHeight()) {
                    DockAction(a.label, a.icon, a.color, a.onClick, Modifier.fillMaxWidth().then(tagged), lines = 2, labelSize = 11, labelWeight = 700)
                }
                online != null -> OfflineGate(offline, { onOffline(online) }, item.fillMaxHeight()) {
                    DockAction(a.label, a.icon, a.color, a.onClick, gated.then(tagged), lines = 2, labelSize = 11, labelWeight = 700)
                }
                else -> DockAction(a.label, a.icon, a.color, a.onClick, item.then(tagged), lines = 2, labelSize = 11, labelWeight = 700)
            }
        }
    }
}

@Composable
private fun DockAction(
    label: String,
    icon: com.sclera.sclerago.presentation.icons.Ico,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.width(56.dp),
    /** Lines the label may take (a long one, "Move to Managed assets", wraps in the asset bar). */
    lines: Int = 1,
    labelSize: Int = 12,
    labelWeight: Int = 600,
) {
    Column(
        modifier.fillMaxHeight().padding(vertical = 4.dp).pressClickable(shape = r(10), onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        LineIcon(icon, 21.dp, color, 2.1f)
        Txt(label, labelSize, labelWeight, color, maxLines = lines, align = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
