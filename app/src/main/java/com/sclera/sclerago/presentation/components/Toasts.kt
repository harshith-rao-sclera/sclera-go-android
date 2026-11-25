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
import androidx.compose.foundation.layout.navigationBarsPadding
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

/** Where a toast sits: the top of the screen for most, the bottom for confirmations the design places there. */
enum class ToastPosition { TOP, BOTTOM }

/** A toast's one button (e.g. "Open settings"); tapping it also closes the toast. */
class ToastAction(val label: UiText, val onClick: () -> Unit)

/** One toast: its [text], its [kind] (null: worked out from the text, see [toastKindOf]) and an optional [action]. */
class ToastMessage internal constructor(
    val id: Long, val text: UiText, val kind: ToastKind?, val action: ToastAction?, val position: ToastPosition,
)

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

    fun show(text: UiText, kind: ToastKind? = null, action: ToastAction? = null, position: ToastPosition = ToastPosition.TOP) {
        val showing = current
        if (showing != null && action == null && showing.action == null && showing.text == text) return
        current = ToastMessage(++lastId, text, kind, action, position)
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
    val bottom = (message ?: last)?.position == ToastPosition.BOTTOM
    val edge = if (bottom) 1 else -1
    AnimatedVisibility(
        message != null,
        if (bottom) Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 88.dp, start = 16.dp, end = 16.dp)
        else Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp, start = 12.dp, end = 12.dp),
        enter = slideInVertically { edge * it } + fadeIn(),
        exit = slideOutVertically { edge * it } + fadeOut(),
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
    val atBottom = m.position == ToastPosition.BOTTOM
    var dx by remember { mutableFloatStateOf(0f) }
    var dy by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier.fillMaxWidth().offset { IntOffset(dx.roundToInt(), dy.roundToInt()) }
            .alpha(1f - (abs(dx) / (sideways * 3)).coerceIn(0f, 0.6f))
            .pointerInput(m.id) {
                detectDragGestures(
                    onDragEnd = {
                        if (abs(dx) > sideways || (if (atBottom) dy else -dy) > upward) toaster.dismiss(m.id)
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
                    dy = if (atBottom) (dy + drag.y).coerceAtLeast(0f) else (dy + drag.y).coerceAtMost(0f)
                }
            },
        contentAlignment = if (atBottom) Alignment.BottomCenter else Alignment.TopCenter,
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
    // The bottom toast is the larger card of the tagging design: 16dp corners, 28dp icon, 15sp text, raised fill.
    val large = m.position == ToastPosition.BOTTOM
    val radius = r(if (large) 16 else 12)
    Row(
        Modifier.widthIn(max = 480.dp).fillMaxWidth().fabShadow(radius).clip(radius).background(if (large) C.Raised else C.Surface)
            .border(1.dp, C.Border2, radius)
            .semantics { liveRegion = LiveRegionMode.Polite }.testTag(ToastTags.TOAST)
            .padding(
                start = if (large) 16.dp else 12.dp, end = if (m.action != null) 8.dp else if (large) 16.dp else 14.dp,
                top = if (large) 14.dp else 10.dp, bottom = if (large) 14.dp else 10.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (large) 12.dp else 10.dp),
    ) {
        Box(Modifier.size(if (large) 28.dp else 24.dp).clip(CircleShape).background(wash).border(1.dp, ring, CircleShape), contentAlignment = Alignment.Center) {
            LineIcon(icon, if (large) 15.dp else 13.dp, tint, if (large) 3.2f else 2.6f)
        }
        Txt(m.text.asString(), if (large) 15 else 13, 600, modifier = Modifier.weight(1f), maxLines = Int.MAX_VALUE)
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

/** Words in a message's resource name that say an attempt failed: a problem even when it is also an access reason. */
private val FAILURE_WORDS = listOf("error", "fail", "couldn")

/**
 * Resource-name prefixes of the messages that say why the user can't do something (their group, the asset's state:
 * the User Access Guide's reasons, as a tap on a greyed-out control or a refused selection says them), or that some of
 * what they asked for was left as it is for such a reason (a bulk edit's skipped assets). They are notes, not failures
 * or successes, as the web shows them in a plain toast.
 */
private val ACCESS_REASONS = listOf(
    "access_block_", "access_decline_own", "access_request_change_reviewer", "access_take_unavailable", "access_withdraw_not_yours",
    "asset_lock_", "assign_admin_only", "assign_take_", "change_block_", "sign_off_block_", "managed_not_selectable", "view_only_",
    "bulk_skipped", "bulk_updated_and_skipped",
    // The camera's Single / Multiple can't change yet (shots in the strip, or waiting in the review).
    "camera_mode_",
)

/**
 * How [text] shows, from its string's resource name (or, for text not yet in resources, the text): orange with a warning
 * when it says something failed or went wrong (error, failed, offline, full and the like); yellow with an info mark when
 * it says why the user can't do something ([ACCESS_REASONS]); otherwise as done.
 */
fun toastKindOf(text: UiText, resources: Resources): ToastKind {
    val words = when (text) {
        is UiText.Res -> runCatching { resources.getResourceEntryName(text.id) }.getOrDefault("")
        is UiText.Plural -> runCatching { resources.getResourceEntryName(text.id) }.getOrDefault("")
        is UiText.Raw -> text.text
    }.lowercase()
    return when {
        FAILURE_WORDS.any { it in words } -> ToastKind.PROBLEM
        ACCESS_REASONS.any { words.startsWith(it) } -> ToastKind.INFO
        PROBLEM_WORDS.any { it in words } -> ToastKind.PROBLEM
        else -> ToastKind.SUCCESS
    }
}
