package com.sclera.sclerago.presentation.config

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import com.sclera.sclerago.domain.model.AccessBlock
import com.sclera.sclerago.presentation.components.AccessGate
import com.sclera.sclerago.presentation.components.OfflineGate

/**
 * Why the signed-in user can't change the property's set-up (the Configure permission), null when they can. Configuration
 * provides it ([ConfigViewModel.configureBlock]) to all its tabs and sheets; anywhere it isn't provided nothing is allowed.
 */
internal val LocalConfigBlock = compositionLocalOf<AccessBlock?> { AccessBlock.CHECKING }

/**
 * A Configuration button that changes the property's set-up. Without Configure it stays where it is, greyed out, and a
 * tap says why ([AccessGate]); otherwise, when it needs the server and the phone is [offline], it is greyed out and a tap
 * runs [onOffline] ([OfflineGate]). [modifier] sizes the tappable area, as for those gates.
 */
@Composable
internal fun ConfigGate(
    modifier: Modifier = Modifier,
    offline: Boolean = false,
    onOffline: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val block = LocalConfigBlock.current
    AccessGate(block, modifier) {
        OfflineGate(offline && block == null, onOffline) { content() }
    }
}
