package com.sclera.sclerago.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/**
 * A small, minimal pick list (Add property's type and region, Add Integration's VDMS, Move to managed assets' network
 * and assignee, Assign asset's assignee, Clone's choices): the options in one bordered box with small corners, divided by
 * thin lines. A small radio dot, or a checkbox with [multi] (several can be ticked), marks the picked ones; [subtitle]
 * adds a muted line under an option; [enabled] false greys an option out and ignores its taps.
 */
@Composable
internal fun <T> ChoiceList(
    items: List<T>,
    title: (T) -> String,
    isOn: (T) -> Boolean,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
    multi: Boolean = false,
    enabled: (T) -> Boolean = { true },
    subtitle: (T) -> String? = { null },
) {
    if (items.isEmpty()) return
    Column(modifier.fillMaxWidth().clip(r(8)).background(C.Bg).border(1.dp, C.Border, r(8))) {
        items.forEachIndexed { i, item ->
            val on = isOn(item)
            val active = enabled(item)
            Row(
                Modifier.fillMaxWidth().then(if (i > 0) Modifier.topBorder(C.Border) else Modifier)
                    .clickable(enabled = active, role = if (multi) Role.Checkbox else Role.RadioButton) { onPick(item) }
                    .alpha(if (active) 1f else 0.4f)
                    .heightIn(min = 40.dp).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (multi) CheckBox(on, r(4), checkSize = 10.dp, size = 16.dp, stroke = 1.5.dp) else SmallRadio(on)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Txt(title(item), 13, if (on) 600 else 500, maxLines = 2)
                    subtitle(item)?.let { Txt(it, 11, 400, C.Muted, maxLines = Int.MAX_VALUE) }
                }
            }
        }
    }
}

/** The list's radio: a 16dp ring with a yellow dot when [on]. */
@Composable
private fun SmallRadio(on: Boolean) {
    Box(Modifier.size(16.dp).border(1.5.dp, if (on) C.Yellow else C.RadioOff, CircleShape), contentAlignment = Alignment.Center) {
        if (on) Box(Modifier.size(8.dp).background(C.Yellow, CircleShape))
    }
}
