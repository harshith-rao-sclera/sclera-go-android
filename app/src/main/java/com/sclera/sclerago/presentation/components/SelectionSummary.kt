package com.sclera.sclerago.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
 * Leading part of a selection dock: clear (×), "N selected", and "Select all M" / "Clear all" under it.
 * Takes the dock's free width, so the dock's actions sit at its end.
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
    RoundButton(48.dp, Color.Transparent, onClear, Modifier.semantics { contentDescription = clearLabel }) {
        LineIcon(Icons.Close, 18.dp, C.Text, 2.4f)
    }
    Column(Modifier.weight(1f).padding(start = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Txt(stringResource(R.string.dash_selected, count), 14, 700)
        Text(
            if (allPicked) stringResource(R.string.dash_clear_all) else stringResource(R.string.dash_select_all, selectable),
            selectAllModifier.clickable(onClick = onSelectAll),
            color = C.Soft, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            textDecoration = TextDecoration.Underline, maxLines = 1,
        )
    }
}
