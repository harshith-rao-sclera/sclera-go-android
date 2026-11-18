package com.sclera.sclerago.presentation.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.presentation.icons.Ico
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/**
 * One item in a ⋮ menu: icon, text, and what it does. [danger] items are red; [divider] draws a line above;
 * [selected] marks the current choice (yellow, with a tick).
 */
internal class MenuAct(
    val icon: Ico,
    val label: String,
    val danger: Boolean = false,
    val enabled: Boolean = true,
    val divider: Boolean = false,
    val selected: Boolean = false,
    val go: () -> Unit,
)

/** A rounded pop-up menu with an icon beside each item. */
@Composable
internal fun IconMenu(open: Boolean, onDismiss: () -> Unit, items: List<MenuAct>) {
    DropdownMenu(
        open, onDismiss,
        modifier = Modifier.widthIn(min = 230.dp),
        shape = r(24),
        containerColor = Color(0xFF38393E),
    ) {
        items.forEach { m ->
            if (m.divider) HorizontalDivider(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = C.Border2)
            DropdownMenuItem(
                text = {
                    Txt(
                        m.label, 13, if (m.selected) 700 else 500,
                        when {
                            !m.enabled -> C.Dim
                            m.danger -> C.Red
                            m.selected -> C.Yellow
                            else -> C.Text
                        },
                        maxLines = 1,
                    )
                },
                leadingIcon = { LineIcon(m.icon, 19.dp, if (m.enabled) C.Yellow else C.Yellow.copy(alpha = 0.6f), 2f) },
                trailingIcon = if (m.selected) ({ LineIcon(Icons.Check, 16.dp, C.Yellow, 2.6f) }) else null,
                enabled = m.enabled,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                onClick = { onDismiss(); m.go() },
            )
        }
    }
}
