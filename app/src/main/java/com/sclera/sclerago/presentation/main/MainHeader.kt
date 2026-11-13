package com.sclera.sclerago.presentation.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.presentation.components.RoundButton
import com.sclera.sclerago.presentation.components.bottomBorder
import com.sclera.sclerago.presentation.components.endBorder
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.readableWidth
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Ico
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** Test tags of the dashboard header and tabs. */
object DashTags {
    const val MENU = "dash-menu"
    fun location(filter: LocFilter) = "dash-loc-${filter.name}"
    fun chevron(filter: LocFilter) = "dash-loc-chevron-${filter.name}"
    const val ADD = "dash-add"
    const val DOCK = "dash-dock"
    const val SELECT_ALL = "dash-select-all"
    fun nav(tab: Tab) = "dash-nav-${tab.name}"
}

/** The top bar height of the design (menu and logo row), and the height of the location tab row under it. */
private val TopRowHeight = 59.dp
private val TabRowHeight = 49.dp

/**
 * The dashboard header: the menu button and the logo, then the three location tabs (the property,
 * a chosen location, unmapped). [path] is the chosen location as building / floor / location, or null
 * when none is chosen yet. On tablets the content stays as wide as the lists below it.
 */
@Composable
fun DashboardHeader(
    propertyName: String,
    path: List<String>?,
    selected: LocFilter,
    onMenu: () -> Unit,
    onPick: (LocFilter) -> Unit,
    onChevron: (LocFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val menuLabel = stringResource(R.string.dash_menu)
    Column(
        modifier.fillMaxWidth().background(C.Surface).bottomBorder(C.Border).statusBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(TopRowHeight)) {
            Box(Modifier.align(Alignment.Center).readableWidth().fillMaxHeight()) {
                RoundButton(
                    48.dp, Color.Transparent, onMenu,
                    Modifier.align(Alignment.CenterStart).padding(start = 15.dp).testTag(DashTags.MENU)
                        .semantics { contentDescription = menuLabel },
                    shape = r(10),
                ) { LineIcon(Icons.Menu, 24.dp, C.Text, 2.4f) }
                ScleraGoLogo(Modifier.align(Alignment.CenterEnd).padding(end = 28.dp))
            }
        }
        LocationTabs(propertyName, path, selected, onPick, onChevron)
    }
}

@Composable
private fun LocationTabs(
    propertyName: String,
    path: List<String>?,
    selected: LocFilter,
    onPick: (LocFilter) -> Unit,
    onChevron: (LocFilter) -> Unit,
) {
    val shown = path ?: listOf(stringResource(R.string.dash_choose_location))
    Row(Modifier.fillMaxWidth().height(TabRowHeight).topBorder(C.Border).background(C.Strip)) {
        LocCell(
            LocFilter.ALL, selected, LocWeights.ALL, Icons.Property, divider = true, onPick, onChevron,
        ) { color, weight -> Txt(propertyName, 13, weight, color, maxLines = 1) }
        LocCell(
            LocFilter.SPEC, selected, LocWeights.SPEC, Icons.Pin, divider = true, onPick, onChevron,
        ) { color, weight ->
            Column {
                if (shown.size > 1) Txt(shown.dropLast(1).joinToString(" / "), 10, 500, C.Muted, maxLines = 1)
                Txt(shown.last(), 13, weight, color, maxLines = 1)
            }
        }
        LocCell(
            LocFilter.NONE, selected, LocWeights.NONE, Icons.Unmapped, divider = false, onPick, null,
        ) { color, weight -> Txt(stringResource(R.string.dash_unmapped), 13, weight, color, maxLines = 1) }
    }
}

/** Widths of the three tabs (the design: 139, 111 and 139dp on a 390dp phone). */
private object LocWeights {
    const val ALL = 1.25f
    const val SPEC = 1f
    const val NONE = 1.25f
}

@Composable
private fun RowScope.LocCell(
    filter: LocFilter,
    selected: LocFilter,
    weight: Float,
    icon: Ico,
    divider: Boolean,
    onPick: (LocFilter) -> Unit,
    onChevron: ((LocFilter) -> Unit)?,
    label: @Composable (Color, Int) -> Unit,
) {
    val on = filter == selected
    val bg by animateColorAsState(if (on) C.Raised else Color.Transparent, tween(300), label = "bg")
    val fg by animateColorAsState(if (on) C.Yellow else C.Soft, tween(300), label = "fg")
    val ic by animateColorAsState(if (on) C.Yellow else C.Muted, tween(300), label = "ic")
    val bar by animateColorAsState(if (on) C.Yellow else Color.Transparent, tween(300), label = "bar")
    Row(
        Modifier.weight(weight).fillMaxHeight().background(bg).then(if (divider) Modifier.endBorder(C.Border) else Modifier)
            .bottomBorder(bar, 3.dp),
    ) {
        Row(
            Modifier.weight(1f).fillMaxHeight().testTag(DashTags.location(filter)).clickable { onPick(filter) }
                .padding(start = 6.dp, end = if (on && onChevron != null) 0.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        ) {
            LineIcon(icon, 17.dp, ic, 2.3f)
            Box(Modifier.weight(1f, fill = false)) { label(fg, if (on) 700 else 500) }
        }
        if (on && onChevron != null) {
            Box(
                Modifier.width(34.dp).fillMaxHeight().testTag(DashTags.chevron(filter)).clickable { onChevron(filter) },
                contentAlignment = Alignment.Center,
            ) { LineIcon(Icons.ChevronDown, 15.dp, fg, 2.8f) }
        }
    }
}

/** The Sclera Go mark (same artwork as the login screen and app icon) with "GO" in its right loop. */
@Composable
private fun ScleraGoLogo(modifier: Modifier = Modifier) {
    Box(modifier.height(25.dp).aspectRatio(512f / 357f)) {
        Image(
            painterResource(R.drawable.login_logo),
            contentDescription = "Sclera Go", // Product name: not translated.
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        )
        // Centre of the right loop in login_logo.png (512 × 357): about (395, 230).
        Txt("GO", 5, 800, C.Surface, Modifier.align(BiasAlignment(0.54f, 0.29f)))
    }
}
