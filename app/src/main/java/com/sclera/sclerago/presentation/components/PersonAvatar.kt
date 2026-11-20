package com.sclera.sclerago.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** "DA" for dev.admin (first and last part of the name), "?" when there is nothing to show. */
fun initialsOf(name: String): String {
    val parts = name.split(' ', '.', '_', '-').filter { it.isNotBlank() }
    return listOfNotNull(parts.firstOrNull(), parts.drop(1).lastOrNull()).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
}

/** The tints a person's avatar takes; the same person always gets the same one (see [personTint]). */
private val PersonTints = listOf(C.Blue, C.Cyan, C.Purple, C.Orange, C.Green, C.Red)

/** [key]'s (a user id, else an email) avatar tint, as the web picks a person's colour from a hash of their id. */
fun personTint(key: String): Color {
    var hash = 0L
    key.forEach { hash = (hash * 31 + it.code) and 0xFFFFFFFFL }
    return PersonTints[(hash % PersonTints.size).toInt()]
}

/**
 * A person's initials ([initials]) in a circle washed with their [tint]. [ring] draws a firmer border (a person picked
 * in a filter).
 */
@Composable
fun PersonAvatar(initials: String, tint: Color, modifier: Modifier = Modifier, size: Dp = 40.dp, textSize: Int = 14, ring: Color? = null) {
    Box(
        modifier.size(size).clip(CircleShape).background(tint.copy(alpha = 0.18f))
            .border(if (ring != null) 2.dp else 1.dp, ring ?: tint.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { Txt(initials, textSize, 700, tint) }
}
