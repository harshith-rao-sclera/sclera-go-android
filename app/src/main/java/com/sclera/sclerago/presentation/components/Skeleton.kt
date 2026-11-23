package com.sclera.sclerago.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.presentation.theme.C

/** What a skeleton row starts with: nothing, a rounded square (an icon tile) or a circle (an avatar). */
enum class SkeletonLead { NONE, TILE, CIRCLE }

/** How long one sweep of the shimmer takes. */
private const val SHIMMER_MS = 1_100

/** First and second lines' widths (part of the row), varied so the rows don't look stamped out. */
private val FirstLine = listOf(0.62f, 0.48f, 0.7f, 0.55f, 0.66f)
private val SecondLine = listOf(0.4f, 0.32f, 0.46f, 0.36f, 0.28f)

/**
 * A grey shape that shimmers while what it stands for loads: a lighter band sweeps across it, start to end. Size it
 * with [modifier]; [shape] rounds it (small corners, as text lines have).
 */
@Composable
fun SkeletonBlock(modifier: Modifier, shape: Shape = r(6)) {
    val sweep by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(SHIMMER_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep",
    )
    Box(
        modifier.clip(shape).background(C.Raised).drawBehind {
            val band = size.width * 0.6f
            val x = sweep * size.width
            drawRect(
                Brush.linearGradient(
                    listOf(C.Raised, C.Border2, C.Raised),
                    start = Offset(x - band, 0f), end = Offset(x, size.height),
                ),
            )
        },
    )
}

/** One placeholder text line, [fraction] of the width it is given, [height] tall. */
@Composable
fun SkeletonLine(fraction: Float = 1f, height: Dp = 10.dp, modifier: Modifier = Modifier) {
    SkeletonBlock(modifier.fillMaxWidth(fraction).height(height))
}

/**
 * One placeholder row: a [lead] (icon tile or avatar), [lines] lines of text and, with [trailing], a small chip at the
 * end. With [card] it sits in a bordered card like the list's own rows; [index] varies the line widths.
 */
@Composable
fun SkeletonRow(
    modifier: Modifier = Modifier,
    lead: SkeletonLead = SkeletonLead.TILE,
    lines: Int = 2,
    trailing: Boolean = false,
    card: Boolean = true,
    index: Int = 0,
) {
    val frame = if (card) Modifier.clip(r(12)).background(C.Surface).border(1.dp, C.Border, r(12)).padding(10.dp) else Modifier.padding(vertical = 6.dp)
    Row(
        modifier.fillMaxWidth().then(frame),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (lead) {
            SkeletonLead.TILE -> SkeletonBlock(Modifier.size(34.dp), r(9))
            SkeletonLead.CIRCLE -> SkeletonBlock(Modifier.size(34.dp), CircleShape)
            SkeletonLead.NONE -> Unit
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            repeat(lines.coerceAtLeast(1)) { line ->
                when (line) {
                    0 -> SkeletonLine(FirstLine[index % FirstLine.size], 11.dp)
                    else -> SkeletonLine(SecondLine[(index + line) % SecondLine.size], 8.dp)
                }
            }
        }
        if (trailing) SkeletonBlock(Modifier.width(54.dp).height(18.dp), Capsule)
    }
}

/**
 * A list still loading: [count] placeholder rows ([SkeletonRow]) [gap] apart, read out as [label] (e.g. "Loading
 * properties…") so a screen reader says what is coming. Keeps the list's place, so nothing jumps when it arrives.
 */
@Composable
fun SkeletonList(
    label: String,
    modifier: Modifier = Modifier,
    count: Int = 5,
    lead: SkeletonLead = SkeletonLead.TILE,
    lines: Int = 2,
    trailing: Boolean = false,
    card: Boolean = true,
    gap: Dp = 8.dp,
) {
    Column(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        repeat(count) { i -> SkeletonRow(lead = lead, lines = lines, trailing = trailing, card = card, index = i) }
    }
}

/** Placeholder text: [lines] lines (the last one shorter), read out as [label]. For a block of text still loading. */
@Composable
fun SkeletonText(label: String, modifier: Modifier = Modifier, lines: Int = 3) {
    Column(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(lines) { i -> SkeletonLine(if (i == lines - 1) 0.55f else 0.9f - (i % 2) * 0.12f, 9.dp) }
    }
}

/** A row of [count] placeholder cards [width] by [height] (cards that scroll sideways, like Validation's templates). */
@Composable
fun SkeletonCards(label: String, modifier: Modifier = Modifier, count: Int = 3, width: Dp = 150.dp, height: Dp = 110.dp) {
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(count) { SkeletonBlock(Modifier.width(width).height(height), r(14)) }
    }
}
