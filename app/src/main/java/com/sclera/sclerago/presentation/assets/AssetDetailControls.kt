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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.ChangeRequest
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
        modifier.fillMaxWidth().clip(r(14)).background(C.Surface).border(1.dp, C.Border2, r(14)).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/**
 * End of a submitted asset form: it is read-only now; a recollect request would reopen it. [onValidate] (null hides it)
 * runs checks on it, which changes nothing on the asset, so it stays offered as on the web.
 */
@Composable
fun SubmittedCard(
    modifier: Modifier,
    onValidate: (() -> Unit)? = null,
    /** Someone's request to reopen it, shown under the header; with none, Request change asks for one. */
    request: ChangeRequest? = null,
    mine: Boolean = false,
    /** Who made [request] (name, else email), when known on this device. */
    requester: String? = null,
    onRequestChange: () -> Unit = {},
    onWithdraw: () -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth().clip(r(14)).background(Status.SUBMITTED.chipBg).border(1.dp, Status.SUBMITTED.chipBd, r(14)).padding(10.dp),
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
        request?.let { ChangeRequestLine(it, mine, requester) }
        // Request change while nobody has asked; Withdraw for one's own request; nothing for someone else's.
        val ask = request == null
        if (onValidate != null || ask || mine) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onValidate?.let { Pill(stringResource(R.string.action_validate), it, Modifier.weight(1f), height = 48.dp, size = 14, weight = 700) }
                if (ask) {
                    Pill(
                        stringResource(R.string.change_request_action), onRequestChange, Modifier.weight(1f).testTag(ChangeRequestTags.ASK),
                        height = 48.dp, size = 14, weight = 700,
                    )
                } else if (mine) {
                    Pill(
                        stringResource(R.string.change_request_withdraw), onWithdraw, Modifier.weight(1f).testTag(ChangeRequestTags.CLOSE),
                        height = 48.dp, size = 14, weight = 700,
                    )
                }
            }
        }
    }
}

/** The asset status, how it was onboarded and when it was last changed (when that is known). */
@Composable
fun StatusLine(asset: Asset, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    // One line: the status, how it was added and when it was updated; the last one shortens before anything wraps.
    Row(
        modifier.testTag(DetailTags.STATUS_LINE),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val st = asset.st
        Box(
            Modifier.clip(Capsule).background(st.chipBg).border(1.dp, st.chipBd, Capsule).padding(horizontal = 8.dp, vertical = 4.dp),
        ) { Txt(stringResource(st.labelRes), 10, 700, st.chipFg) }
        InfoChip(stringResource(asset.src.viaRes))
        asset.updatedAt?.let { InfoChip(stringResource(R.string.detail_updated, formatDayAndTime(it, locale)), Modifier.weight(1f, fill = false)) }
    }
}

@Composable
private fun InfoChip(text: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(Capsule).background(C.Raised).padding(horizontal = 8.dp, vertical = 4.dp)) { Txt(text, 10, 600, C.Soft) }
}

/**
 * One tab of the asset details: its icon above its name, with a small badge on the icon. [fails] is the Validation
 * tab's failed-check badge (null = none shown, 0 = a tick); [count] how many items the tab lists (shown when above 0).
 */
@Composable
fun DetailTab(
    label: String,
    icon: Ico,
    selected: Boolean,
    fails: Int?,
    modifier: Modifier,
    /** The icon beside the label (a lower bar) instead of above it. */
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val fg = if (selected) C.Yellow else C.Muted
    Box(modifier.fillMaxHeight().selectable(selected, role = Role.Tab, onClick = onClick).padding(horizontal = 2.dp)) {
        val content: @Composable () -> Unit = {
            Box {
                Box(
                    Modifier.size(26.dp).background(if (selected) C.YTintBg else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { LineIcon(icon, 16.dp, fg, 2.4f) }
                val badge = Modifier.align(Alignment.TopEnd).offset(x = 7.dp, y = (-3).dp).heightIn(min = 15.dp).widthIn(min = 15.dp)
                // Only the Validation tab has a badge: its failed checks, or a tick when all passed.
                when {
                    fails == null -> Unit
                    fails > 0 -> Box(badge.background(C.Red, Capsule).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                        Txt(fails.toString(), 9, 800, C.OnYellow)
                    }
                    else -> Box(badge.background(C.Green, CircleShape), contentAlignment = Alignment.Center) {
                        LineIcon(Icons.Check, 9.dp, C.OnYellow, 3.6f)
                    }
                }
            }
            Txt(label, if (compact) 12 else 11, if (selected) 700 else 500, fg, maxLines = 1)
        }
        if (compact) {
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { content() }
        } else {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) { content() }
        }
        if (selected) {
            Box(
                Modifier.align(Alignment.BottomCenter).padding(horizontal = 8.dp).fillMaxWidth().height(3.dp)
                    .background(C.Yellow, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
            )
        }
    }
}

/** The two floating buttons: AI fill above, voice below, with a short message beside the active one ([heard]: the words heard so far). */
@Composable
fun FormFloatingButtons(
    aiBusy: Boolean,
    aiNotice: UiText?,
    listening: Boolean,
    heard: String,
    onAi: () -> Unit,
    onMic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(end = 16.dp, bottom = 16.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AiFillButton(aiBusy, aiNotice, onAi)
        MicButton(listening, heard, onMic)
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
private fun MicButton(listening: Boolean, heard: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (listening) Bubble(heard.ifBlank { stringResource(R.string.detail_listening) }, 3)
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
                // Faded while listening, so the mic shows it is on.
                Modifier.size(54.dp).testTag(FloatingTags.MIC).alpha(if (listening) 0.6f else 1f).fabShadow().clip(CircleShape).background(C.Yellow)
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
