package com.sclera.sclerago.presentation.assets

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.sclera.sclerago.R
import com.sclera.sclerago.presentation.components.noRippleClick

/**
 * Wraps a button that asks the AI, as the web's aiGate: with the property's AI switch on it is left exactly as it is.
 * Off ([aiOff]), it stays where it is, dimmed, and a tap only calls [onBlocked] (which says "AI off. An admin can turn it
 * on."); the button's own action doesn't run. Like the offline gate, which sits outside it when both apply.
 */
@Composable
internal fun AiOffGate(aiOff: Boolean, onBlocked: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(if (aiOff) Modifier.alpha(AI_OFF_DIM) else Modifier) { content() }
        if (aiOff) {
            val description = stringResource(R.string.ai_off)
            // Sits over the button so its own click never fires; a tap explains instead.
            Box(
                Modifier.matchParentSize().testTag(AiOffTags.GATE)
                    .semantics {
                        contentDescription = description
                        role = Role.Button
                    }
                    .noRippleClick(onBlocked),
            )
        }
    }
}

private const val AI_OFF_DIM = 0.45f

/** Test tags of the AI-off tap guard. */
object AiOffTags {
    const val GATE = "ai-off-gate"
}
