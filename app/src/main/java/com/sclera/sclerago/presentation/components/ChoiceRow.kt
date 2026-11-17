package com.sclera.sclerago.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** One option in a pick list (Add Integration's VDMS, Move to managed assets' network and assignee, Assign asset's assignee). */
@Composable
internal fun ChoiceRow(title: String, on: Boolean, onPick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(r(12)).background(if (on) C.YTintBg else C.Bg).border(1.dp, if (on) C.Yellow else C.Border, r(12))
            .clickable(role = Role.RadioButton, onClick = onPick).heightIn(min = 52.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RadioDot(on)
        Txt(title, 13, 700, if (on) C.Yellow else C.Text, Modifier.weight(1f), maxLines = 2)
    }
}
