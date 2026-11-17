package com.sclera.sclerago.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/**
 * A compact pick list (Add Integration's VDMS, Move to managed assets' network and assignee, Assign asset's assignee):
 * the options in one bordered box, divided by thin lines with no gaps between them. Each row is still at least 48dp tall
 * to tap; the picked one is tinted.
 */
@Composable
internal fun <T> ChoiceList(
    items: List<T>,
    title: (T) -> String,
    isOn: (T) -> Boolean,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    Column(modifier.fillMaxWidth().clip(r(12)).background(C.Bg).border(1.dp, C.Border, r(12))) {
        items.forEachIndexed { i, item ->
            val on = isOn(item)
            Row(
                Modifier.fillMaxWidth().then(if (i > 0) Modifier.topBorder(C.Border) else Modifier)
                    .background(if (on) C.YTintBg else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onPick(item) }
                    .heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RadioDot(on)
                Txt(title(item), 13, if (on) 700 else 500, if (on) C.Yellow else C.Text, Modifier.weight(1f), maxLines = 2)
            }
        }
    }
}
