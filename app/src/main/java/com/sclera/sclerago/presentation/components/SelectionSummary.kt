package com.sclera.sclerago.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sclera.sclerago.R
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/**
 * Summary strip of a selection dock: clear (x), "N of M selected" and "Select all" / "Clear all", in yellow on the tint.
 * Takes the strip's free width.
 */
@Composable
fun RowScope.SelectionSummary(
    count: Int,
    selectable: Int,
    allPicked: Boolean,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
    selectAllModifier: Modifier = Modifier,
) {
    val clearLabel = stringResource(R.string.dash_clear_selection)
    RoundButton(32.dp, Color.Transparent, onClear, Modifier.semantics { contentDescription = clearLabel }) {
        LineIcon(Icons.Close, 16.dp, C.Yellow, 2.4f)
    }
    Txt(stringResource(R.string.dash_selected_of, count, selectable), 13, 700, C.Yellow, Modifier.weight(1f).padding(start = 2.dp))
    Text(
        if (allPicked) stringResource(R.string.dash_clear_all) else stringResource(R.string.dash_select_all_short),
        selectAllModifier.clickable(onClick = onSelectAll),
        color = C.Yellow, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        textDecoration = TextDecoration.Underline, maxLines = 1,
    )
}
