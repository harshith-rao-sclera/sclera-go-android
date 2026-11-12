package com.sclera.sclerago.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Tablet layout basics: phones keep one column; wider screens lay cards out in columns, keep
// reading content to a comfortable width, and keep sheets phone-sized.

/** Widest a bottom sheet gets; on tablets it sits centred at this width. */
val SheetMaxWidth = 640.dp

/** Widest single-column page content (forms, settings, validation) gets. */
val ReadableWidth = 760.dp

/** How many columns card lists use at this width: 1 on phones, 2 from 600dp, 3 from 1100dp. */
@Composable
fun listColumns(): Int {
    val w = LocalConfiguration.current.screenWidthDp
    return when {
        w >= 1100 -> 3
        w >= 600 -> 2
        else -> 1
    }
}

/** Side margin that centres content of at most [maxContent] on wide screens (0 on phones). */
@Composable
fun pageGutter(maxContent: Dp = ReadableWidth): Dp {
    val w = LocalConfiguration.current.screenWidthDp.dp
    return ((w - maxContent) / 2).coerceAtLeast(0.dp)
}

/**
 * Lays [items] out [columns] to a row, each cell an equal share of the width; a short last row keeps
 * the cells the same width. With one column it is just the items, one per row.
 */
@Composable
fun <T> GridRow(items: List<T>, columns: Int, modifier: Modifier = Modifier, spacing: Dp = 0.dp, cell: @Composable (T) -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
        items.forEach { Box(Modifier.weight(1f)) { cell(it) } }
        repeat(columns - items.size) { Spacer(Modifier.weight(1f)) }
    }
}

/**
 * [items] in a lazy list, [columns] to a row ([GridRow]) on wide screens and one per row on phones.
 * [key] must be unique per item, as for `items`.
 */
fun <T> LazyListScope.gridItems(
    items: List<T>,
    columns: Int,
    key: (T) -> Any,
    spacing: Dp = 0.dp,
    content: @Composable (T) -> Unit,
) {
    if (columns <= 1) {
        items(items, key = key) { content(it) }
    } else {
        items(items.chunked(columns), key = { row -> "row-${key(row.first())}" }) { row ->
            GridRow(row, columns, spacing = spacing) { content(it) }
        }
    }
}

/** Widest card-grid pages (Property list, People and access) get before they are centred. */
val WideWidth = 1200.dp
