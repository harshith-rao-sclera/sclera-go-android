package com.sclera.sclerago.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import com.sclera.sclerago.presentation.common.UiText

/**
 * Why each [ActionDock] action is greyed out for the signed-in user (the User Access Guide), null where it can be used.
 * A greyed-out action keeps its place and a tap says why ([AccessGate]); offline badges give way to it.
 */
data class DockAccess(
    val validate: UiText? = null,
    val edit: UiText? = null,
    val assign: UiText? = null,
    val submit: UiText? = null,
    val change: UiText? = null,
    val move: UiText? = null,
    val delete: UiText? = null,
)

/**
 * An [ActionDock] action behind [AccessGate]: untouched (sized by [modifier]) while [reason] is null; otherwise the gate
 * takes [modifier] and the action fills it.
 */
@Composable
internal fun DockGate(reason: UiText?, modifier: Modifier, content: @Composable (Modifier) -> Unit) {
    if (reason == null) content(modifier) else AccessGate(reason, modifier) { content(Modifier.fillMaxWidth()) }
}
