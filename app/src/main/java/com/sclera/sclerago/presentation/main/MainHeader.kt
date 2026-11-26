package com.sclera.sclerago.presentation.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.presentation.components.RoundButton
import com.sclera.sclerago.presentation.components.ScleraGoLogo
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
    const val OUTSIDE_GEOFENCE = "dash-outside-geofence"
    fun location(filter: LocFilter) = "dash-loc-${filter.name}"
    fun chevron(filter: LocFilter) = "dash-loc-chevron-${filter.name}"
    const val ADD = "dash-add"
    const val FILTER = "dash-filter"
    const val DOCK = "dash-dock"
    const val SELECT_ALL = "dash-select-all"
    fun nav(tab: Tab) = "dash-nav-${tab.name}"
}

/** The top bar height of the design (menu and logo row), and the height of the location tab row under it. */
private val TopRowHeight = 50.dp
private val TabRowHeight = 44.dp

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
    /** A collector outside the property's geofence (view only): "Outside geofence" after a lock, under the name. */
    outsideGeofence: Boolean = false,
) {
    val menuLabel = stringResource(R.string.dash_menu)
    Column(
        modifier.fillMaxWidth().background(C.Surface).bottomBorder(C.Border).statusBarsPadding(),
    ) {
        Box(Modifier.fillMaxWidth().height(TopRowHeight)) {
            Box(Modifier.align(Alignment.Center).readableWidth().fillMaxHeight()) {
                RoundButton(
                    48.dp, Color.Transparent, onMenu,
                    Modifier.align(Alignment.CenterStart).padding(start = 15.dp).testTag(DashTags.MENU)
                        .semantics { contentDescription = menuLabel },
                    shape = r(10),
                ) { LineIcon(Icons.Menu, 24.dp, C.Text, 2.4f) }
                // The open property's name in the middle, with the building icon (as the other pages show it), clear of
                // the menu button and the logo.
                if (propertyName.isNotBlank()) {
                    Column(
                        Modifier.align(Alignment.Center).padding(horizontal = 76.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            // The row takes the whole width between the paddings (its name can shrink): kept in the middle.
                            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                        ) {
                            // The property, in white: the same icon and size as the property line under the other pages' titles.
                            LineIcon(Icons.Building, 12.dp, C.Text, 1.9f)
                            Txt(propertyName, 11, 500, C.Text, modifier = Modifier.weight(1f, fill = false), maxLines = 1)
                        }
                        // Away from a property with geofencing on, a collector only looks: said under its name.
                        if (outsideGeofence) {
                            Row(
                                Modifier.testTag(DashTags.OUTSIDE_GEOFENCE),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                LineIcon(Icons.Lock, 11.dp, C.Orange, 2.2f)
                                Txt(stringResource(R.string.premise_header_off), 11, 500, C.Orange, maxLines = 1)
                            }
                        }
                    }
                }
                ScleraGoLogo(Modifier.align(Alignment.CenterEnd).padding(end = 28.dp), height = 25.dp)
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
        ) { color, weight -> Txt(propertyName, 14, weight, color, maxLines = 1) }
        LocCell(
            LocFilter.SPEC, selected, LocWeights.SPEC, Icons.Pin, divider = true, onPick, onChevron,
        ) { color, weight ->
            Column {
                if (shown.size > 1) Txt(shown[shown.size - 2], 11, 500, C.Muted, maxLines = 1) // the design: the parent (floor) over the place
                Txt(shown.last(), 14, weight, color, maxLines = 1)
            }
        }
        LocCell(
            LocFilter.NONE, selected, LocWeights.NONE, Icons.Unmapped, divider = false, onPick, null,
        ) { color, weight -> Txt(stringResource(R.string.dash_unmapped), 14, weight, color, maxLines = 1) }
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
    // The label and the chevron are pressed apart but show one press shade over the whole tab.
    val press = remember { MutableInteractionSource() }
    Row(
        Modifier.weight(weight).fillMaxHeight().background(bg).indication(press, LocalIndication.current)
            .then(if (divider) Modifier.endBorder(C.Border) else Modifier).bottomBorder(bar, 3.dp),
    ) {
        Row(
            Modifier.weight(1f).fillMaxHeight().testTag(DashTags.location(filter))
                .clickable(interactionSource = press, indication = null) { onPick(filter) }
                .padding(start = 10.dp, end = if (on && onChevron != null) 0.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        ) {
            LineIcon(icon, 17.dp, ic, 2.3f)
            Box(Modifier.weight(1f, fill = false)) { label(fg, if (on) 700 else 500) }
        }
        if (on && onChevron != null) {
            Box(
                Modifier.width(34.dp).fillMaxHeight().testTag(DashTags.chevron(filter))
                    .clickable(interactionSource = press, indication = null) { onChevron(filter) },
                contentAlignment = Alignment.Center,
            ) { LineIcon(Icons.ChevronDown, 15.dp, fg, 2.8f) }
        }
    }
}
