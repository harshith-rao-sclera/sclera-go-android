package com.sclera.sclerago.presentation.components

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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.presentation.icons.Ico
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C

/** Floating action buttons of the design's "Floating actions" section (54dp circles, search capsule). */
val FabSize = 54.dp

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
    Box(
        modifier.size(FabSize).fabShadow().clip(CircleShape).background(bg)
            .semantics { contentDescription = description }.clickable(enabled = !busy, onClick = onClick),
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
                    OfflineGate(a.onBlocked != null, { a.onBlocked?.invoke() }, badgeOffset = 2.dp) {
                        Row(
                            Modifier.height(48.dp).testTag(a.tag).fabShadow(Capsule).clip(Capsule).background(C.Btn).border(1.dp, C.Border2, Capsule)
                                .clickable {
                                    onOpenChange(false)
                                    a.onClick()
                                }
                                .padding(start = 18.dp, end = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Txt(a.label, 14, 700)
                            LineIcon(a.icon, 17.dp, C.Yellow, 2.2f)
                        }
                    }
                }
            }
        }
        val description = if (open) closeDescription else openDescription
        Box(
            Modifier.size(FabSize).fabShadow().clip(CircleShape).background(C.Yellow)
                .semantics { contentDescription = description }.clickable { onOpenChange(!open) },
            contentAlignment = Alignment.Center,
        ) { LineIcon(Icons.Plus, 24.dp, C.OnYellow, 2.8f, Modifier.rotate(turn)) }
    }
}
