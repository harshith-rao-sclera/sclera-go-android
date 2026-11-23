package com.sclera.sclerago.presentation.components

import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import com.sclera.sclerago.presentation.theme.Txt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.presentation.icons.Ico
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C

/** Floating action buttons of the design's "Floating actions" section (54dp circles, search capsule). */
val FabSize = 54.dp

/**
 * The floating run button (validating an asset): a yellow capsule with ▶ and [label]; while [busy] a spinner takes the
 * ▶'s place and it can't be tapped, nor while not [enabled].
 */
@Composable
fun RunFab(label: String, busy: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(
        modifier.height(52.dp).fabShadow(Capsule).pressClickable(shape = Capsule, enabled = enabled && !busy, role = Role.Button, onClick = onClick)
            .background(if (enabled || busy) C.Yellow else C.PrimaryOffBg).padding(start = 16.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val fg = if (enabled || busy) C.OnYellow else C.PrimaryOffFg
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = fg, strokeWidth = 2.5.dp)
        else LineIcon(Icons.Play, 18.dp, fg, 2.4f)
        Txt(label, 14, 800, fg)
    }
}

/**
 * A filter button: a dark circle with a funnel; while [count] choices are on, the funnel is yellow with a count badge.
 * [description] says what it filters (and how many choices are on) for screen readers. [modifier] places it (with its
 * badge); [buttonModifier] goes on the button itself (its test tag).
 */
@Composable
fun FilterFab(count: Int, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, buttonModifier: Modifier = Modifier) {
    Box(modifier) {
        CircleFab(
            Icons.Filter, description, onClick, buttonModifier,
            bg = if (count > 0) C.YTintBg else C.Btn, fg = if (count > 0) C.Yellow else C.Text, iconSize = 20.dp, stroke = 2.4f,
        )
        if (count > 0) {
            Box(
                Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).background(C.Yellow),
                contentAlignment = Alignment.Center,
            ) { Txt(count.toString(), 11, 700, C.OnYellow) }
        }
    }
}

/** Widest the expanded search capsule gets. */
val SearchCapsuleWidth = 330.dp

/**
 * A 54dp circular floating button with one icon; [description] is its accessibility label. While [busy] it shows a
 * spinner in place of the icon and can't be tapped.
 */
@Composable
fun CircleFab(
    icon: Ico,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bg: Color = C.Yellow,
    fg: Color = C.OnYellow,
    iconSize: Dp = 24.dp,
    stroke: Float = 2.8f,
    busy: Boolean = false,
) {
    val press = remember { MutableInteractionSource() }
    Box(
        modifier.pressScale(press).size(FabSize).fabShadow().clip(CircleShape).background(bg)
            .semantics { contentDescription = description }.clickable(press, LocalIndication.current, enabled = !busy, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(iconSize), color = fg, strokeWidth = 2.5.dp)
        else LineIcon(icon, iconSize, fg, stroke)
    }
}

/**
 * The search button that opens into a capsule with a text field and a close button. Closing clears
 * the query. [open] is remembered across rotation.
 */
@Composable
fun SearchFab(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    openDescription: String,
    closeDescription: String,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    SearchFab(open, { open = it }, query, onQueryChange, placeholder, openDescription, closeDescription, modifier)
}

/** [SearchFab] whose open state belongs to the caller (e.g. so the back button and other actions can close it). */
@Composable
fun SearchFab(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    openDescription: String,
    closeDescription: String,
    modifier: Modifier = Modifier,
) {
    if (!open) {
        CircleFab(Icons.Search, openDescription, { onOpenChange(true) }, modifier, bg = C.Btn, fg = C.Text, iconSize = 20.dp, stroke = 2.4f)
        return
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        modifier.widthIn(max = SearchCapsuleWidth).fillMaxWidth().height(FabSize).fabShadow(Capsule).clip(Capsule)
            .background(C.Surface).border(1.5.dp, C.Yellow, Capsule).padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LineIcon(Icons.Search, 17.dp, C.Yellow, 2.4f)
        PlainField(query, onQueryChange, placeholder, Modifier.weight(1f).focusRequester(focus))
        RoundButton(
            42.dp, C.Btn, { onQueryChange(""); onOpenChange(false) },
            Modifier.semantics { contentDescription = closeDescription },
        ) { LineIcon(Icons.Close, 14.dp, C.Text, 2.6f) }
    }
}

/** One choice of a [FabMenu]: its label, icon and what it does ([tag] is its test tag). */
class FabAction(
    val label: String,
    val icon: Ico,
    val tag: String,
    val onClick: () -> Unit,
    /** Non-null while the action needs the server and the device is offline: dimmed with a badge, a tap calls this (to say why). */
    val onBlocked: (() -> Unit)?,
    /** A short note after the label, in small grey text (e.g. "0 of 5 added"). */
    val note: String? = null,
    /** With a subtitle the action is the design's two-line card: right-aligned title and subtitle, then a round [iconColor] icon. */
    val subtitle: String? = null,
    val iconColor: Color = C.Yellow,
) {
    /** An action that works offline; [onClick] last, so it can be passed as a trailing lambda. */
    constructor(label: String, icon: Ico, tag: String, onClick: () -> Unit) : this(label, icon, tag, onClick, null)
}

/**
 * A yellow + floating button that opens into its [actions] above it, each a dark pill with its label and a yellow
 * icon; open, the + turns into an X that closes it (as does Back). Choosing an action closes it first.
 */
@Composable
fun FabMenu(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    actions: List<FabAction>,
    openDescription: String,
    closeDescription: String,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = open) { onOpenChange(false) }
    val turn by animateFloatAsState(if (open) 45f else 0f, label = "fab-menu")
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnimatedVisibility(open, enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut()) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                actions.forEach { a ->
                    // Offline, an online-only action stays in the menu (and the menu open) and a tap says why.
                    OfflineGate(a.onBlocked != null, { a.onBlocked?.invoke() }) {
                        val click = {
                            onOpenChange(false)
                            a.onClick()
                        }
                        if (a.subtitle != null) FabCard(a, a.subtitle, click) else Row(
                            Modifier.height(48.dp).testTag(a.tag).fabShadow(Capsule)
                                .pressClickable(shape = Capsule, onClick = click)
                                .background(C.Btn).border(1.dp, C.Border2, Capsule)
                                .padding(start = 18.dp, end = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Txt(a.label, 14, 700)
                            a.note?.let { Txt(it, 11, 600, C.Muted) }
                            LineIcon(a.icon, 17.dp, C.Yellow, 2.2f)
                        }
                    }
                }
            }
        }
        val description = if (open) closeDescription else openDescription
        Box(
            Modifier.size(FabSize).fabShadow().pressClickable(shape = CircleShape) { onOpenChange(!open) }.background(C.Yellow)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) { LineIcon(Icons.Plus, 24.dp, C.OnYellow, 2.8f, Modifier.rotate(turn)) }
    }
}

/** A [FabAction] with a subtitle, as the design's menu cards: dark pill, two right-aligned lines, then a 40dp round icon. */
@Composable
private fun FabCard(a: FabAction, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.testTag(a.tag).fabShadow(Capsule, 8.dp).clip(Capsule).background(C.Surface).border(1.dp, C.Border, Capsule)
            .clickable(onClick = onClick).padding(start = 16.dp, top = 7.dp, end = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(horizontalAlignment = Alignment.End) {
            Txt(a.label, 14, 700, align = TextAlign.End)
            Txt(subtitle, 11, 400, C.Muted, align = TextAlign.End)
        }
        Box(Modifier.size(40.dp).clip(CircleShape).background(C.Raised), contentAlignment = Alignment.Center) {
            LineIcon(a.icon, 20.dp, a.iconColor, 2f)
        }
    }
}
