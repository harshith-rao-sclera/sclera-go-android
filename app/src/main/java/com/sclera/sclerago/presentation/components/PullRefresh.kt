package com.sclera.sclerago.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.presentation.theme.C
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** How far a list must be pulled down before letting go refreshes it: half as far again as Material's 80dp, so only a firm pull does. */
val RefreshPullDistance = 120.dp

/** The gap the list rests at, above it the spinner, while a refresh runs. */
private val RefreshRestGap = 64.dp

private val SpinnerSize = 28.dp

/** How far past [RefreshPullDistance] the list can be pulled (as a multiple of it). */
private const val MAX_PULL = 1.6f

private const val HAND_BACK_WAIT_MS = 600L

const val PULL_REFRESH_TAG = "pull-refresh"

/**
 * Pull to refresh for a scrolling list or page. Pulling [content] down from its top moves the whole list down with the
 * finger, and a ring in the gap above it fills as the pull nears [RefreshPullDistance]. Letting go past it calls
 * [onRefresh]: the list glides to a small gap with the ring turning while [refreshing], then slides back up. Letting
 * go before it springs the list back. [content] must scroll (a LazyColumn or verticalScroll).
 *
 * Material's node only detects the gesture; the list's position is worked out here, because on release the node
 * hides its own state before [refreshing] turns true, which would make the list jump up and down again.
 */
@Composable
fun PullRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    val density = LocalDensity.current
    val pullPx = with(density) { RefreshPullDistance.toPx() }
    val restPx = with(density) { RefreshRestGap.toPx() }
    val spinnerPx = with(density) { SpinnerSize.toPx() }

    // Where the list rests during a refresh and while it slides back afterwards; the finger decides otherwise.
    val settle = remember { Animatable(0f) }
    var settling by remember { mutableStateOf(false) }
    val refreshingNow by rememberUpdatedState(refreshing)
    fun dragPx() = state.distanceFraction.coerceAtMost(MAX_PULL) * pullPx
    fun offsetPx() = if (refreshingNow || settling) settle.value else dragPx()

    LaunchedEffect(refreshing) {
        if (refreshing) {
            // From wherever the finger let go (or the top, for a refresh not started by a pull) to the resting gap.
            settle.snapTo(maxOf(dragPx(), settle.value))
            settling = true
            settle.animateTo(restPx, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
        } else if (settling) {
            settle.animateTo(0f, tween(durationMillis = 250))
            // Hand back to the finger only once the node's own value is back at the top too, so nothing jumps.
            withTimeoutOrNull(HAND_BACK_WAIT_MS) { snapshotFlow { state.distanceFraction }.first { it <= 0f } }
            settling = false
        }
    }

    val showSpinner by remember { derivedStateOf { refreshingNow || settling || state.distanceFraction > 0f } }
    Box(modifier.testTag(PULL_REFRESH_TAG).clipToBounds().pullToRefresh(refreshing, state, enabled, RefreshPullDistance, onRefresh)) {
        Box(Modifier.matchParentSize().graphicsLayer { translationY = offsetPx() }) { content() }
        if (showSpinner) {
            // Centred in the gap above the list, fading in as the gap opens.
            Box(
                Modifier.align(Alignment.TopCenter).size(SpinnerSize).graphicsLayer {
                    val gap = offsetPx()
                    translationY = (gap - spinnerPx) / 2f
                    alpha = (gap / restPx).coerceIn(0f, 1f)
                },
            ) {
                if (refreshing) {
                    CircularProgressIndicator(Modifier.matchParentSize(), color = C.Yellow, strokeWidth = 2.5.dp)
                } else {
                    CircularProgressIndicator(
                        progress = { state.distanceFraction.coerceAtMost(1f) },
                        modifier = Modifier.matchParentSize(),
                        color = C.Yellow,
                        strokeWidth = 2.5.dp,
                        trackColor = C.Border,
                    )
                }
            }
        }
    }
}
