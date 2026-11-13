package com.sclera.sclerago.presentation.components

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

/** A 54dp circular floating button with one icon; [description] is its accessibility label. */
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
) {
    Box(
        modifier.size(FabSize).fabShadow().clip(CircleShape).background(bg)
            .semantics { contentDescription = description }.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { LineIcon(icon, iconSize, fg, stroke) }
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
    if (!open) {
        CircleFab(Icons.Search, openDescription, { open = true }, modifier, bg = C.Btn, fg = C.Text, iconSize = 20.dp, stroke = 2.4f)
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
            42.dp, C.Btn, { onQueryChange(""); open = false },
            Modifier.semantics { contentDescription = closeDescription },
        ) { LineIcon(Icons.Close, 14.dp, C.Text, 2.6f) }
    }
}
