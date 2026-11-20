package com.sclera.sclerago.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.model.OnlineOnly
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/**
 * Wraps an online-only button. Online, the button is left exactly as it is. Offline ([offline]), it is dimmed and stays
 * tappable, but a tap only calls [onBlocked] (which says why, see [OnlineOnly]); the button's own action doesn't run and
 * opens nothing. [modifier] sizes the tappable area; the button sits in its middle.
 */
@Composable
fun OfflineGate(
    offline: Boolean,
    onBlocked: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(if (offline) Modifier.alpha(OFFLINE_DIM) else Modifier) { content() }
        if (offline) {
            val description = stringResource(R.string.offline_needs_connection)
            // Sits over the button so its own click never fires; a tap explains instead.
            Box(
                Modifier.matchParentSize().testTag(OfflineTags.GATE)
                    .semantics {
                        contentDescription = description
                        role = Role.Button
                    }
                    .noRippleClick(onBlocked),
            )
        }
    }
}

/** The orange banner at the top of a page (or part of one) that only works online: "You're offline. [why] Try again…". */
@Composable
fun OfflineBanner(reason: OnlineOnly, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(r(10)).background(C.WarnRow).border(1.dp, C.Orange.copy(alpha = 0.35f), r(10))
            .padding(horizontal = 12.dp, vertical = 10.dp).testTag(OfflineTags.BANNER),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineIcon(Icons.NoWifi, 18.dp, C.Orange, 2.2f)
        Txt(stringResource(reason.message), 12, 500, C.Soft, Modifier.weight(1f), maxLines = 4)
    }
}

private const val OFFLINE_DIM = 0.45f

/** Test tags of the offline tap guard and the banner. */
object OfflineTags {
    const val GATE = "offline-gate"
    const val BANNER = "offline-banner"
}
