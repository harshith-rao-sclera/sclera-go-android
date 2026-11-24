package com.sclera.sclerago.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.sclera.sclerago.domain.model.AccessBlock
import com.sclera.sclerago.domain.model.AssetLock
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.model.reason

/**
 * Wraps a button the signed-in user may not be allowed to use (the User Access Guide). Allowed ([reason] null), the button
 * is left exactly as it is. Blocked, it stays where it is, dimmed, and a tap only shows [reason] as a toast; the button's
 * own action never runs. [modifier] sizes the tappable area; the button sits in its middle. When an [OfflineGate] also
 * applies, put this gate outside it, so the access reason (which going online won't fix) is the one a tap shows.
 */
@Composable
fun AccessGate(reason: UiText?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(if (reason != null) Modifier.alpha(BLOCKED_DIM) else Modifier) { content() }
        if (reason != null) {
            val toaster = LocalToaster.current
            val description = reason.asString()
            // Sits over the button so its own click never fires; a tap explains instead.
            Box(
                Modifier.matchParentSize().testTag(AccessGateTags.GATE)
                    .semantics {
                        contentDescription = description
                        role = Role.Button
                    }
                    .noRippleClick { toaster.show(reason, ToastKind.INFO) },
            )
        }
    }
}

/** [AccessGate] for a button that needs a permission ([block] from `PermissionGuard.block`). */
@Composable
fun AccessGate(block: AccessBlock?, modifier: Modifier = Modifier, content: @Composable () -> Unit) =
    AccessGate(block.reason(), modifier, content)

/** [AccessGate] for a button that changes an asset ([lock] from `assetLock`). */
@Composable
fun AccessGate(lock: AssetLock?, modifier: Modifier = Modifier, content: @Composable () -> Unit) =
    AccessGate(lock.reason(), modifier, content)

/** How faded a blocked button is: the same as an offline one. */
private const val BLOCKED_DIM = 0.45f

/** Test tags of the access tap guard. */
object AccessGateTags {
    const val GATE = "access-gate"
}
