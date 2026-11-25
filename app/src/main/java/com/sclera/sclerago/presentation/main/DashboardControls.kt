package com.sclera.sclerago.presentation.main

import com.sclera.sclerago.presentation.components.FilterFab
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.presentation.model.OnlineOnly
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.components.AccessGate
import com.sclera.sclerago.presentation.components.ActionDock
import com.sclera.sclerago.presentation.components.DockAccess
import com.sclera.sclerago.presentation.components.SelectionSummary
import com.sclera.sclerago.presentation.components.BottomTabBar
import com.sclera.sclerago.presentation.components.CircleFab
import com.sclera.sclerago.presentation.components.NavItem
import com.sclera.sclerago.presentation.components.SearchFab
import com.sclera.sclerago.presentation.icons.Ico
import com.sclera.sclerago.presentation.icons.Icons
import kotlin.math.abs

private data class NavSpec(val tab: Tab, @param:StringRes val label: Int, val icon: Ico, val iconSize: Dp = 21.dp)

private val NAV = listOf(
    NavSpec(Tab.BT, R.string.nav_bluetooth, Icons.Bluetooth),
    NavSpec(Tab.NET, R.string.nav_network, Icons.Wifi),
    NavSpec(Tab.ASSETS, R.string.label_assets, Icons.Box),
    NavSpec(Tab.CAMERA, R.string.nav_camera, Icons.Camera),
    NavSpec(Tab.FILE, R.string.nav_file, Icons.FileUploadBadgeSolid, iconSize = 27.dp),
)

/**
 * The bottom tabs, icon only with the label of the active one. They follow the pager's live position
 * ([position], e.g. 2.4 = 40% of the way from the third tab to the fourth), so while you swipe the
 * highlight slides between icons with your finger.
 */
@Composable
fun DashboardNav(tabs: List<Tab>, position: Float, onTab: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val shown = NAV.filter { it.tab in tabs }
    BottomTabBar(modifier) {
        shown.forEachIndexed { i, n ->
            val t = (1f - abs(position - i)).coerceIn(0f, 1f)
            NavItem(
                stringResource(n.label), n.icon, n.iconSize, t, first = i == 0, last = i == shown.size - 1,
                modifier = Modifier.testTag(DashTags.nav(n.tab)),
            ) { onTab(n.tab) }
        }
    }
}

/**
 * The +, filter and search buttons, stacked in the bottom corner; the search button opens into a capsule. The filter
 * button turns yellow with the number of choices made while a filter is on ([filterCount]); null [onFilter] hides it.
 * They are not shown while assets are being selected (the selection dock takes their place).
 */
@Composable
fun DashboardFabs(
    searchOpen: Boolean,
    onSearchOpenChange: (Boolean) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    /** The "+" (add an asset); null hides it. */
    onAdd: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onFilter: (() -> Unit)? = null,
    filterCount: Int = 0,
    /** Why the "+" is greyed out (a group without Collect, or away from a geofenced site); a tap says why. */
    addBlock: UiText? = null,
) {
    Column(
        modifier.navigationBarsPadding().imePadding().padding(end = 14.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        onAdd?.let { AccessGate(addBlock) { CircleFab(Icons.Plus, stringResource(R.string.dash_add_asset), it, Modifier.testTag(DashTags.ADD)) } }
        onFilter?.let {
            val description = if (filterCount > 0) pluralStringResource(R.plurals.filter_open_active, filterCount, filterCount)
            else stringResource(R.string.filter_open)
            FilterFab(filterCount, description, it, buttonModifier = Modifier.testTag(DashTags.FILTER))
        }
        SearchFab(
            searchOpen, onSearchOpenChange, query, onQueryChange, stringResource(R.string.dash_search_hint),
            stringResource(R.string.action_search), stringResource(R.string.action_close_search),
        )
    }
}

/** Docked above the tabs while assets are selected: clear, count + select all, then the actions. */
@Composable
fun SelectionDock(
    count: Int,
    visible: Int,
    allPicked: Boolean,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
    /** Run a validation on the selected assets; null hides it. */
    onValidate: (() -> Unit)?,
    /** Assign, Submit and Delete; null hides each (not offered for what is selected). */
    onAssign: (() -> Unit)?,
    onSubmit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
    /** Move to managed assets; null hides it (nothing selected is validated). */
    onMove: (() -> Unit)? = null,
    /** Greys Move out (not linked, link unknown, or not on the Validated filter); a tap then says why. */
    moveDimmed: Boolean = false,
    /** Edit the selected assets' location (the web's Edit); null hides it. */
    onEdit: (() -> Unit)? = null,
    /** Offline: Validate, Assign and Move are dimmed with a badge, and a tap says why ([onOffline]). */
    offline: Boolean = false,
    onOffline: (OnlineOnly) -> Unit = {},
    /** The change-request actions; null hides it (nothing selected is submitted). */
    onChange: (() -> Unit)? = null,
    /** Greys Submit out (a ticked asset is already submitted, an exception or signed off); a tap then says why. */
    submitDimmed: Boolean = false,
    /** Assign reads "Assign to me"; [assignDimmed] greys it out (a tap then says why). */
    assignToMe: Boolean = false,
    assignDimmed: Boolean = false,
    /** Why each action is greyed out for the signed-in user's group (the User Access Guide); a tap says why. */
    access: DockAccess = DockAccess(),
    /** "Assign to me" beside Assign; null leaves it out. [takeAccess] greys it out (a tap then says why). */
    onTake: (() -> Unit)? = null,
    takeAccess: UiText? = null,
) {
    Column(modifier.testTag(DashTags.DOCK)) {
        ActionDock(
            onValidate = onValidate, onAssign = onAssign, onSubmit = onSubmit, onDelete = onDelete, floating = true,
            onMove = onMove, moveDimmed = moveDimmed, onEdit = onEdit, offline = offline, onOffline = onOffline, onChange = onChange,
            submitDimmed = submitDimmed, assignToMe = assignToMe, assignDimmed = assignDimmed, access = access,
            onTake = onTake, takeAccess = takeAccess,
        ) {
            SelectionSummary(count, visible, allPicked, onClear, onSelectAll, Modifier.testTag(DashTags.SELECT_ALL))
        }
    }
}

/** What deleting the selected assets asks, as the title and body of the confirmation. */
@Composable
fun deleteSelectionTitle(count: Int): String = pluralStringResource(R.plurals.dash_delete_title, count, count)

@Composable
fun deleteSelectionBody(count: Int): String = pluralStringResource(R.plurals.dash_delete_body, count, count)
