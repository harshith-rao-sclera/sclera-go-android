package com.sclera.sclerago.presentation.components

import android.content.res.Resources
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.icons.Ico
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** What a toast says about what happened: it worked, it didn't, or just a note. */
enum class ToastKind { SUCCESS, PROBLEM, INFO }

/** A toast's one button (e.g. "Open settings"); tapping it also closes the toast. */
class ToastAction(val label: UiText, val onClick: () -> Unit)

/** One toast: its [text], its [kind] (null: worked out from the text, see [toastKindOf]) and an optional [action]. */
class ToastMessage internal constructor(val id: Long, val text: UiText, val kind: ToastKind?, val action: ToastAction?)

/** Test tags of the toast. */
object ToastTags {
    const val TOAST = "app-toast"
    const val ACTION = "app-toast-action"
}

/**
 * The app's one toast: what any screen shows with [show], drawn by [ToastHost] at the very top of the screen above
 * everything else. A new toast replaces the one showing; the same text again while it shows is ignored.
 */
@Stable
class Toaster {
    var current by mutableStateOf<ToastMessage?>(null)
        private set
    private var lastId = 0L

    fun show(text: UiText, kind: ToastKind? = null, action: ToastAction? = null) {
        val showing = current
        if (showing != null && action == null && showing.action == null && showing.text == text) return
        current = ToastMessage(++lastId, text, kind, action)
    }

    /** Closes toast [id] (swiped away, timed out or its button tapped); a newer toast stays. */
    fun dismiss(id: Long) {
        if (current?.id == id) current = null
    }
}

/** The toaster of [ToastHost]; outside one (previews), toasts go nowhere. */
val LocalToaster = staticCompositionLocalOf { Toaster() }

/** How long a toast stays: a note or success briefly, a problem longer, one with a button long enough to reach it. */
private const val SHORT_MS = 3_000L
private const val PROBLEM_MS = 4_500L
private const val ACTION_MS = 8_000L

/** Wraps the whole app: [content], with the toast drawn over all of it at the top of the screen. */
@Composable
fun ToastHost(content: @Composable () -> Unit) {
    val toaster = remember { Toaster() }
    CompositionLocalProvider(LocalToaster provides toaster) {
        Box(Modifier.fillMaxSize()) {
            content()
            ToastLayer(toaster)
        }
    }
}

/**
 * Shows [text] as a toast each time it changes to a new message ([visible] false or null text: nothing new). For the
 * screens whose view models hold their message as state.
 */
@Composable
fun ToastEffect(text: UiText?, visible: Boolean = true, kind: ToastKind? = null) {
    val toaster = LocalToaster.current
    LaunchedEffect(text, visible) { if (visible && text != null) toaster.show(text, kind) }
}

@Composable
private fun BoxScope.ToastLayer(toaster: Toaster) {
    val message = toaster.current
    // The last toast stays drawn while it slides out.
    var last by remember { mutableStateOf<ToastMessage?>(null) }
    LaunchedEffect(message) { if (message != null) last = message }
    AnimatedVisibility(
        message != null,
        Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp, start = 12.dp, end = 12.dp),
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        (message ?: last)?.let { m -> key(m.id) { SwipeableToast(m, toaster) } }
    }
}

/** A toast that goes by itself after a while, or when swiped up or to either side. */
@Composable
private fun SwipeableToast(m: ToastMessage, toaster: Toaster) {
    val resources = LocalContext.current.resources
    val kind = m.kind ?: toastKindOf(m.text, resources)
    LaunchedEffect(m.id) {
        delay(if (m.action != null) ACTION_MS else if (kind == ToastKind.PROBLEM) PROBLEM_MS else SHORT_MS)
        toaster.dismiss(m.id)
    }
    val scope = rememberCoroutineScope()
    val (sideways, upward) = with(LocalDensity.current) { 96.dp.toPx() to 32.dp.toPx() }
    var dx by remember { mutableFloatStateOf(0f) }
    var dy by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier.fillMaxWidth().offset { IntOffset(dx.roundToInt(), dy.roundToInt()) }
            .alpha(1f - (abs(dx) / (sideways * 3)).coerceIn(0f, 0.6f))
            .pointerInput(m.id) {
                detectDragGestures(
                    onDragEnd = {
                        if (abs(dx) > sideways || -dy > upward) toaster.dismiss(m.id)
                        else scope.launch {
                            val fromX = dx
                            val fromY = dy
                            animate(1f, 0f) { f, _ -> dx = fromX * f; dy = fromY * f }
                        }
                    },
                    onDragCancel = { dx = 0f; dy = 0f },
                ) { change, drag ->
                    change.consume()
                    dx += drag.x
                    dy = (dy + drag.y).coerceAtMost(0f)
                }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        ToastCard(m, kind) { toaster.dismiss(m.id) }
    }
}

/** The toast itself: a dark card, an icon in its kind's colour, the text, and the button when there is one. */
@Composable
private fun ToastCard(m: ToastMessage, kind: ToastKind, close: () -> Unit) {
    val (icon, tint, wash, ring) = when (kind) {
        ToastKind.SUCCESS -> ToastLook(Icons.Check, C.Green, Color(0xFF16301F), Color(0xFF2F6B46))
        ToastKind.PROBLEM -> ToastLook(Icons.Alert, C.Orange, Color(0xFF3A2412), Color(0xFF7A4A22))
        ToastKind.INFO -> ToastLook(Icons.Info, C.Yellow, C.YTintBg, C.YTintBd)
    }
    Row(
        Modifier.widthIn(max = 480.dp).fillMaxWidth().fabShadow(r(12)).clip(r(12)).background(C.Surface).border(1.dp, C.Border2, r(12))
            .semantics { liveRegion = LiveRegionMode.Polite }.testTag(ToastTags.TOAST)
            .padding(start = 12.dp, end = if (m.action != null) 8.dp else 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(wash).border(1.dp, ring, CircleShape), contentAlignment = Alignment.Center) {
            LineIcon(icon, 13.dp, tint, 2.6f)
        }
        Txt(m.text.asString(), 13, 600, modifier = Modifier.weight(1f), maxLines = 4)
        m.action?.let { a ->
            Pill(
                a.label.asString(), { a.onClick(); close() }, Modifier.testTag(ToastTags.ACTION),
                bg = C.Yellow, fg = C.OnYellow, height = 32.dp, hPad = 12.dp, size = 12, weight = 700,
            )
        }
    }
}

private data class ToastLook(val icon: Ico, val tint: Color, val wash: Color, val ring: Color)

/** Words in a message's resource name (or text) that say something went wrong. */
private val PROBLEM_WORDS = listOf(
    "error", "fail", "offline", "couldn", "denied", "blocked", "unavailable", "full", "limit", "taken", "forbidden",
    "save_first", "not_assigned", "too_large", "too_long", "too_short", "expired", "invalid", "refused",
)

/**
 * Whether [text] reports a problem, so it shows orange with a warning: its string's resource name (or, for text not
 * yet in resources, the text) says error, failed, offline, full and the like. Everything else shows as done.
 */
fun toastKindOf(text: UiText, resources: Resources): ToastKind {
    val words = when (text) {
        is UiText.Res -> runCatching { resources.getResourceEntryName(text.id) }.getOrDefault("")
        is UiText.Plural -> runCatching { resources.getResourceEntryName(text.id) }.getOrDefault("")
        is UiText.Raw -> text.text
    }.lowercase()
    return if (PROBLEM_WORDS.any { it in words }) ToastKind.PROBLEM else ToastKind.SUCCESS
}
