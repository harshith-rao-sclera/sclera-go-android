package com.sclera.sclerago.presentation.assets

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.common.formatDayAndTime
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.fabShadow
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.icons.Ico
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.model.chipBd
import com.sclera.sclerago.presentation.model.chipBg
import com.sclera.sclerago.presentation.model.chipFg
import com.sclera.sclerago.presentation.model.labelRes
import com.sclera.sclerago.presentation.model.viaRes
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** The Save / Submit card at the end of an asset form. */
@Composable
fun SaveCard(modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(r(14)).background(C.Surface).border(1.dp, C.Border2, r(14)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** End of a submitted asset form: it is read-only now; a recollect request would reopen it. */
@Composable
fun SubmittedCard(onRecollect: () -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxWidth().clip(r(14)).background(Status.SUBMITTED.chipBg).border(1.dp, Status.SUBMITTED.chipBd, r(14)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(38.dp).clip(r(10)).background(C.GreenBg), contentAlignment = Alignment.Center) {
                LineIcon(Icons.CheckCircle, 20.dp, C.Green, 2.2f)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt(stringResource(R.string.status_submitted), 14, 700, C.Green)
                Txt(stringResource(R.string.detail_submitted_body), 11, 400, C.Muted, maxLines = 3)
            }
        }
        Pill(stringResource(R.string.detail_recollect), onRecollect, Modifier.fillMaxWidth(), height = 48.dp, size = 14, weight = 700)
    }
}

/** The asset status, how it was onboarded and when it was last changed (when that is known). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatusLine(asset: Asset, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    FlowRow(
        modifier.testTag(DetailTags.STATUS_LINE),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val st = asset.st
        Box(
            Modifier.clip(Capsule).background(st.chipBg).border(1.dp, st.chipBd, Capsule).padding(horizontal = 10.dp, vertical = 4.dp),
        ) { Txt(stringResource(st.labelRes), 11, 700, st.chipFg) }
        InfoChip(stringResource(asset.src.viaRes))
        asset.updatedAt?.let { InfoChip(stringResource(R.string.detail_updated, formatDayAndTime(it, locale))) }
    }
}

@Composable
private fun InfoChip(text: String) {
    Box(Modifier.clip(Capsule).background(C.Raised).padding(horizontal = 10.dp, vertical = 4.dp)) { Txt(text, 11, 600, C.Soft) }
}

/** An Info / Validation tab; [fails] is the failed-check badge (null = none shown, 0 = a tick). */
@Composable
fun DetailTab(label: String, icon: Ico, selected: Boolean, fails: Int?, modifier: Modifier, onClick: () -> Unit) {
    val fg = if (selected) C.Yellow else C.Muted
    Box(modifier.fillMaxHeight().clickable(onClick = onClick)) {
        Row(
            Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            LineIcon(icon, 15.dp, fg, 2.4f)
            Txt(label, 14, if (selected) 700 else 500, fg)
            when {
                fails == null -> {}
                fails > 0 -> Box(
                    Modifier.heightIn(min = 18.dp).widthIn(min = 18.dp).background(C.Red, Capsule).padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center,
                ) { Txt(fails.toString(), 10, 800, C.OnYellow) }
                else -> Box(Modifier.size(18.dp).background(C.Green, CircleShape), contentAlignment = Alignment.Center) {
                    LineIcon(Icons.Check, 11.dp, C.OnYellow, 3.6f)
                }
            }
        }
        if (selected) {
            Box(
                Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp).fillMaxWidth().height(3.dp)
                    .background(C.Yellow, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
            )
        }
    }
}

/** The two floating buttons: AI fill above, voice below, with a short message beside the active one. */
@Composable
fun FormFloatingButtons(
    aiBusy: Boolean,
    aiNotice: UiText?,
    listening: Boolean,
    onAi: () -> Unit,
    onMic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(end = 16.dp, bottom = 16.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AiFillButton(aiBusy, aiNotice, onAi)
        MicButton(listening, onMic)
    }
}

@Composable
private fun Bubble(text: String, maxLines: Int) {
    Txt(
        text, 11, 500, C.Soft,
        Modifier.widthIn(max = 210.dp).fabShadow(r(12)).clip(r(12)).background(C.Surface).border(1.dp, C.Border2, r(12))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        maxLines = maxLines,
    )
}

/** Small floating button above the mic: fills empty fields with AI suggestions. */
@Composable
private fun AiFillButton(busy: Boolean, notice: UiText?, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val tip = if (busy) stringResource(R.string.detail_ai_busy) else notice?.asString()
        if (tip != null) Bubble(tip, 2)
        val inf = rememberInfiniteTransition(label = "ai")
        val spin by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "spin")
        val description = stringResource(R.string.detail_ai_fill)
        Box(
            Modifier.size(54.dp).testTag(FloatingTags.AI).fabShadow().clip(CircleShape).background(C.Surface).border(1.dp, C.Border2, CircleShape)
                .semantics { contentDescription = description }.clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { LineIcon(Icons.Sparkles, 27.dp, C.Cyan, 2.2f, if (busy) Modifier.rotate(spin) else Modifier) }
    }
}

@Composable
private fun MicButton(listening: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (listening) Bubble(stringResource(R.string.detail_listening), 3)
        val description = stringResource(R.string.detail_voice_fill)
        Box(contentAlignment = Alignment.Center) {
            if (listening) {
                val inf = rememberInfiniteTransition(label = "mic")
                val p by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "p")
                Canvas(Modifier.size(54.dp)) {
                    listOf(p, (p + 0.5f) % 1f).forEach { t ->
                        drawCircle(C.Yellow.copy(alpha = 0.8f * (1f - t)), (27f + 18f * t).dp.toPx(), style = Stroke(2.dp.toPx()))
                    }
                }
            }
            Box(
                Modifier.size(54.dp).testTag(FloatingTags.MIC).fabShadow().clip(CircleShape).background(C.Yellow)
                    .semantics { contentDescription = description }.clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) { LineIcon(Icons.Mic, 24.dp, C.OnYellow, 2.2f) }
        }
    }
}

/** Test tags of the floating buttons. */
object FloatingTags {
    const val AI = "detail-ai"
    const val MIC = "detail-mic"
}
