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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import com.sclera.sclerago.presentation.components.OfflineGate
import com.sclera.sclerago.presentation.components.fabShadow
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.icons.Ico
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.model.OnlineOnly
import com.sclera.sclerago.presentation.model.chipBd
import com.sclera.sclerago.presentation.model.chipBg
import com.sclera.sclerago.presentation.model.chipFg
import com.sclera.sclerago.presentation.model.labelRes
import com.sclera.sclerago.presentation.model.viaRes
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import kotlinx.coroutines.delay

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
 * End of the form of an asset assigned to someone else: read-only for anyone but an admin (the server's check_editable,
 * the web's lock note). What can still be done (Validate) is in the asset bar under it.
 */
@Composable
fun NotAssignedCard(modifier: Modifier) {
    Column(
        modifier.fillMaxWidth().clip(r(14)).background(C.WarnRow).border(1.dp, C.Border, r(14)).padding(10.dp)
            .testTag(DetailTags.NOT_ASSIGNED),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(38.dp).clip(r(10)).background(C.WarnRow), contentAlignment = Alignment.Center) {
                LineIcon(Icons.Lock, 20.dp, C.Orange, 2.2f)
            }
            Txt(stringResource(R.string.asset_lock_not_assigned), 12, 500, C.Orange, maxLines = 3, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * End of a submitted asset form: it is read-only now; a change request would reopen it. Validate, Request change and
 * Withdraw are in the asset bar under it; a request already made shows in the banner under the top bar.
 */
@Composable
fun SubmittedCard(modifier: Modifier) {
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
    Box(modifier.fillMaxHeight().selectable(selected, role = Role.Tab, onClick = onClick).padding(horizontal = 1.dp)) {
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
            // Six tabs share a phone's width, so the labels under the icons stay small enough for "Validation" to fit.
            Txt(label, if (compact) 12 else 10, if (selected) (if (compact) 700 else 600) else 500, fg, maxLines = 1)
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

/**
 * The two floating buttons: AI fill above, voice below, with a short message beside the active one ([heard]: the words
 * heard so far). Both need the server: [offline], each is dimmed with the offline badge, and a tap says why in its bubble.
 */
@Composable
fun FormFloatingButtons(
    aiBusy: Boolean,
    aiNotice: UiText?,
    listening: Boolean,
    heard: String,
    onAi: () -> Unit,
    onMic: () -> Unit,
    modifier: Modifier = Modifier,
    /** What was said is being read by the AI. */
    reading: Boolean = false,
    offline: Boolean = false,
) {
    // Which button's offline reason shows in its bubble, for a few seconds after a tap.
    var why by remember { mutableStateOf<OnlineOnly?>(null) }
    LaunchedEffect(why, offline) {
        if (!offline) why = null
        if (why != null) {
            delay(OFFLINE_TIP_MS)
            why = null
        }
    }
    Column(modifier.padding(end = 16.dp, bottom = 16.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // A look-up or recording already running carries on (and can be stopped) whatever the connection.
        val aiOffline = offline && !aiBusy
        val aiWhy = if (why == OnlineOnly.AI_SUGGESTIONS) UiText.Res(OnlineOnly.AI_SUGGESTIONS.message) else aiNotice
        AiFillButton(aiBusy, aiWhy, onAi, aiOffline, onBlocked = { why = OnlineOnly.AI_SUGGESTIONS })
        MicButton(
            listening, reading, heard, onMic, offline && !listening && !reading,
            why = if (why == OnlineOnly.VOICE_FILL) stringResource(OnlineOnly.VOICE_FILL.message) else null,
            onBlocked = { why = OnlineOnly.VOICE_FILL },
        )
    }
}

/** How long a floating button's offline reason stays in its bubble. */
private const val OFFLINE_TIP_MS = 4_000L

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
private fun AiFillButton(busy: Boolean, notice: UiText?, onClick: () -> Unit, offline: Boolean, onBlocked: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val tip = if (busy) stringResource(R.string.detail_ai_busy) else notice?.asString()
        if (tip != null) Bubble(tip, if (offline) 4 else 2)
        val inf = rememberInfiniteTransition(label = "ai")
        // Working (a look-up, or filling the form from new photos): the sparkle blinks inside a cyan ring.
        val blink by inf.animateFloat(1f, 0.25f, infiniteRepeatable(tween(550), RepeatMode.Reverse), label = "blink")
        val description = stringResource(R.string.detail_ai_fill)
        OfflineGate(offline, onBlocked, badgeOffset = 2.dp) {
            Box(
                Modifier.size(54.dp).testTag(FloatingTags.AI).fabShadow().clip(CircleShape).background(C.Surface)
                    .border(if (busy) 2.dp else 1.dp, if (busy) C.Cyan.copy(alpha = 0.4f + 0.6f * blink) else C.Border2, CircleShape)
                    .semantics { contentDescription = description }.clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) { LineIcon(Icons.Sparkles, 27.dp, C.Cyan, 2.2f, if (busy) Modifier.alpha(blink) else Modifier) }
        }
    }
}

/**
 * The voice button, like play and stop: tap to start recording, talk about the asset as long as needed (pauses don't
 * end it), tap ■ to stop. Then the AI reads it ([reading]) and the voice fill sheet shows what it found.
 */
@Composable
private fun MicButton(
    listening: Boolean,
    reading: Boolean,
    heard: String,
    onClick: () -> Unit,
    offline: Boolean,
    why: String?,
    onBlocked: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            why != null -> Bubble(why, 4)
            reading -> Bubble(stringResource(R.string.voice_reading), 2)
            listening -> Bubble(heard.ifBlank { stringResource(R.string.voice_listening) }, 3)
        }
        val description = stringResource(if (listening) R.string.voice_stop else R.string.detail_voice_fill)
        OfflineGate(offline, onBlocked, badgeOffset = 2.dp) {
            Box(contentAlignment = Alignment.Center) {
                if (listening) {
                    val inf = rememberInfiniteTransition(label = "mic")
                    val p by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "p")
                    Canvas(Modifier.size(54.dp)) {
                        listOf(p, (p + 0.5f) % 1f).forEach { t ->
                            drawCircle(C.Red.copy(alpha = 0.8f * (1f - t)), (27f + 18f * t).dp.toPx(), style = Stroke(2.dp.toPx()))
                        }
                    }
                }
                Box(
                    // Recording: a stop square to tap when done. Reading: dimmed and not tappable until the sheet shows.
                    Modifier.size(54.dp).testTag(FloatingTags.MIC).alpha(if (reading) 0.5f else 1f).fabShadow().clip(CircleShape)
                        .background(if (listening) C.Red else C.Yellow)
                        .semantics { contentDescription = description }.clickable(enabled = !reading, onClick = onClick),
                    contentAlignment = Alignment.Center,
                ) {
                    if (listening) LineIcon(Icons.Stop, 24.dp, C.White, 2.2f) else LineIcon(Icons.Mic, 24.dp, C.OnYellow, 2.2f)
                }
            }
        }
    }
}

/** Test tags of the floating buttons. */
object FloatingTags {
    const val AI = "detail-ai"
    const val MIC = "detail-mic"
}
