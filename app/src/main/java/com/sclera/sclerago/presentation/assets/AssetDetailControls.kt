package com.sclera.sclerago.presentation.assets

import com.sclera.sclerago.presentation.components.pressClickable
import com.sclera.sclerago.presentation.components.PRESSED_CARD
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
import com.sclera.sclerago.domain.model.AssetLock
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.common.formatDayAndTime
import com.sclera.sclerago.presentation.common.is24Hour
import com.sclera.sclerago.presentation.components.AccessGate
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.presentation.components.OfflineGate
import com.sclera.sclerago.presentation.components.bottomBorder
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
 * Why this user can't change the asset, as one line under the top bar like the change request banner: "No edit access ·
 * Assigned to someone else" (or "· Submitted"). A tap opens the full reason. Validate and Request change stay in the
 * asset bar. A submitted asset says only a reviewer can change it (a reviewer's is never locked), as the web's lockedReason.
 */
@Composable
internal fun LockBanner(lock: AssetLock, modifier: Modifier = Modifier) {
    val (short, body) = when (lock) {
        AssetLock.NOT_ASSIGNED -> R.string.lock_banner_not_assigned to R.string.asset_lock_not_assigned
        AssetLock.NO_PERMISSION -> R.string.lock_banner_no_permission to R.string.lock_banner_no_permission_body
        AssetLock.OFF_PREMISE -> R.string.lock_banner_off_premise to R.string.lock_banner_off_premise_body
        else -> R.string.lock_banner_submitted to R.string.detail_submitted_body
    }
    FoldingBanner(Icons.Lock, C.Orange, C.WarnRow, R.string.lock_banner_title, short, body, modifier.testTag(DetailTags.LOCK_BANNER))
}

/**
 * An asset in Managed Assets, as one slim line under the top bar like the lock and change request banners: "In
 * Managed Assets · Read-only here". A tap opens the rest: it comes back as a draft if Managed Assets sends it back.
 */
@Composable
internal fun ManagedBanner(modifier: Modifier = Modifier) {
    FoldingBanner(
        Icons.Managed, C.Yellow, C.YTintBg, R.string.managed_note_title, R.string.managed_banner_short, R.string.managed_note_body,
        modifier.testTag(DetailTags.MANAGED_NOTE),
    )
}

/** The slim line under the top bar: [icon], [title] · [short] and a chevron; a tap folds [body] open or shut. */
@Composable
private fun FoldingBanner(
    icon: Ico,
    tint: Color,
    background: Color,
    @StringRes title: Int,
    @StringRes short: Int,
    @StringRes body: Int,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val toggle = stringResource(if (open) R.string.change_banner_collapse else R.string.change_banner_expand)
    Column(modifier.fillMaxWidth().background(background).bottomBorder(tint.copy(alpha = 0.35f))) {
        Row(
            Modifier.pressClickable(pressed = PRESSED_CARD, onClickLabel = toggle) { open = !open }.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LineIcon(icon, 15.dp, tint, 2.2f)
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Txt(stringResource(title), 13, 700, tint)
                Txt("·", 12, 400, C.Soft)
                Txt(stringResource(short), 12, 400, C.Soft, Modifier.weight(1f, fill = false), maxLines = 1)
            }
            LineIcon(Icons.ChevronDown, 15.dp, tint, 2.6f, Modifier.rotate(if (open) 180f else 0f))
        }
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Txt(stringResource(body), 12, 400, C.Soft, Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp), maxLines = Int.MAX_VALUE)
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
        if (asset.managed) {
            // In Managed Assets: that is where it stands here (the web's "In Managed Assets").
            Row(
                Modifier.clip(Capsule).background(C.YTintBg).border(1.dp, C.Yellow.copy(alpha = 0.7f), Capsule)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LineIcon(Icons.Managed, 11.dp, C.Yellow, 2.4f)
                Txt(stringResource(R.string.managed_status), 10, 700, C.Yellow)
            }
        } else {
            Box(
                Modifier.clip(Capsule).background(st.chipBg).border(1.dp, st.chipBd, Capsule).padding(horizontal = 8.dp, vertical = 4.dp),
            ) { Txt(stringResource(st.labelRes), 10, 700, st.chipFg) }
        }
        InfoChip(stringResource(asset.src.viaRes))
        asset.updatedAt?.let { InfoChip(stringResource(R.string.detail_updated, formatDayAndTime(it, locale, use24Hour = is24Hour())), Modifier.weight(1f, fill = false)) }
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
    val fg by animateColorAsState(if (selected) C.Yellow else C.Muted, tween(120), label = "tab-fg")
    val dot by animateColorAsState(if (selected) C.YTintBg else Color.Transparent, tween(120), label = "tab-dot")
    val line by animateFloatAsState(if (selected) 1f else 0f, tween(140), label = "tab-line")
    Box(modifier.fillMaxHeight().selectable(selected, role = Role.Tab, onClick = onClick).padding(horizontal = 1.dp)) {
        val content: @Composable () -> Unit = {
            Box {
                Box(
                    Modifier.size(26.dp).background(dot, CircleShape),
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
            // Six tabs share a phone's width: a long label ("Documents", "Validation") shrinks a little to show in full rather
            // than being cut off.
            Txt(label, if (compact) 12 else 11, if (selected) 700 else 500, fg, maxLines = 1, minSize = 8)
        }
        if (compact) {
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { content() }
        } else {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) { content() }
        }
        if (line > 0f) {
            Box(
                Modifier.align(Alignment.BottomCenter).padding(horizontal = 8.dp).fillMaxWidth().height(3.dp)
                    .graphicsLayer { scaleX = line; alpha = line }
                    .background(C.Yellow, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
            )
        }
    }
}

/**
 * The two floating buttons: AI fill above, voice below, with a short message beside the active one ([heard]: the words
 * heard so far). Both need the server: [offline], each is dimmed with the offline badge, and a tap says why in its bubble.
 * Both ask the AI: with the property's AI switch off they stay, dimmed, and a tap says "AI off. An admin can turn it on."
 * ([aiOff]: the AI button, which stays usable while a look-up's results wait; [voiceOff]: the voice button).
 * [blocked]: the signed-in user may not change the asset, so both are greyed out and a tap says why.
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
    /** A long press on the mic: describe the asset by typing instead. */
    onType: () -> Unit = {},
    /** What was said is being read by the AI. */
    reading: Boolean = false,
    offline: Boolean = false,
    aiOff: Boolean = false,
    voiceOff: Boolean = false,
    blocked: UiText? = null,
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
    // Which button says AI is off in its bubble, for a few seconds after a tap.
    var offTip by remember { mutableStateOf<FloatingButton?>(null) }
    LaunchedEffect(offTip, aiOff, voiceOff) {
        if (!aiOff && !voiceOff) offTip = null
        if (offTip != null) {
            delay(OFFLINE_TIP_MS)
            offTip = null
        }
    }
    val aiOffText = UiText.Res(R.string.ai_off)
    Column(modifier.padding(end = 16.dp, bottom = 16.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // A look-up or recording already running carries on (and can be stopped) whatever the connection.
        val aiOffline = offline && !aiBusy
        val aiWhy = when {
            why == OnlineOnly.AI_SUGGESTIONS -> UiText.Res(OnlineOnly.AI_SUGGESTIONS.message)
            offTip == FloatingButton.AI -> aiOffText
            else -> aiNotice
        }
        AiFillButton(
            aiBusy, aiWhy, onAi, aiOffline, onBlocked = { why = OnlineOnly.AI_SUGGESTIONS },
            aiOff = aiOff && !aiBusy, onAiOff = { offTip = FloatingButton.AI }, access = blocked,
        )
        MicButton(
            listening, reading, heard, onMic, offline && !listening && !reading, onType = onType,
            why = when {
                why == OnlineOnly.VOICE_FILL -> stringResource(OnlineOnly.VOICE_FILL.message)
                offTip == FloatingButton.MIC -> aiOffText.asString()
                else -> null
            },
            onBlocked = { why = OnlineOnly.VOICE_FILL },
            aiOff = voiceOff && !listening && !reading, onAiOff = { offTip = FloatingButton.MIC }, access = blocked,
        )
    }
}

/** The floating buttons, to say which one shows the AI-off reason. */
private enum class FloatingButton { AI, MIC }

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
private fun AiFillButton(
    busy: Boolean,
    notice: UiText?,
    onClick: () -> Unit,
    offline: Boolean,
    onBlocked: () -> Unit,
    aiOff: Boolean,
    onAiOff: () -> Unit,
    access: UiText?,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val tip = if (busy) stringResource(R.string.detail_ai_busy) else notice?.asString()
        if (tip != null) Bubble(tip, Int.MAX_VALUE)
        val description = stringResource(R.string.detail_ai_fill)
        AccessGate(access) {
            OfflineGate(offline, onBlocked) {
                AiOffGate(aiOff, onAiOff) {
                    Box(
                        Modifier.size(54.dp).testTag(FloatingTags.AI).fabShadow().pressClickable(shape = CircleShape, onClick = onClick).background(C.Surface)
                            .border(1.dp, C.Border2, CircleShape)
                            .semantics { contentDescription = description },
                        contentAlignment = Alignment.Center,
                    ) {
                        // Working (a look-up, or filling the form from new photos): a cyan ring spins round the border
                        // and the sparkle blinks.
                        if (busy) {
                            val blink by rememberInfiniteTransition(label = "ai")
                                .animateFloat(1f, 0.25f, infiniteRepeatable(tween(550), RepeatMode.Reverse), label = "blink")
                            CircularProgressIndicator(Modifier.matchParentSize(), color = C.Cyan, strokeWidth = 2.dp)
                            LineIcon(Icons.Sparkles, 27.dp, C.Cyan, 2.2f, Modifier.alpha(blink))
                        } else {
                            LineIcon(Icons.Sparkles, 27.dp, C.Cyan, 2.2f)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The voice button, like play and stop: tap to start recording, talk about the asset as long as needed (pauses don't
 * end it), tap ■ to stop. Then the AI reads it ([reading]) and the voice fill sheet shows what it found.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MicButton(
    listening: Boolean,
    reading: Boolean,
    heard: String,
    onClick: () -> Unit,
    offline: Boolean,
    onType: () -> Unit,
    why: String?,
    onBlocked: () -> Unit,
    aiOff: Boolean,
    onAiOff: () -> Unit,
    access: UiText?,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            why != null -> Bubble(why, Int.MAX_VALUE)
            reading -> Bubble(stringResource(R.string.voice_reading), Int.MAX_VALUE)
            listening -> Bubble(heard.ifBlank { stringResource(R.string.voice_listening) }, 3)
        }
        val description = stringResource(if (listening) R.string.voice_stop else R.string.detail_voice_fill)
        val typeLabel = stringResource(R.string.describe_type_instead)
        AccessGate(access) {
            OfflineGate(offline, onBlocked) {
                AiOffGate(aiOff, onAiOff) {
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
                                .semantics { contentDescription = description }
                                // A long press types the description instead (noisy sites, or when speaking won't do).
                                .combinedClickable(
                                    enabled = !reading, onLongClickLabel = typeLabel,
                                    onLongClick = { if (!listening) onType() }, onClick = onClick,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (listening) LineIcon(Icons.Stop, 24.dp, C.White, 2.2f) else LineIcon(Icons.Mic, 24.dp, C.OnYellow, 2.2f)
                        }
                    }
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
