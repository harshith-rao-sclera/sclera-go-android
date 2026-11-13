package com.sclera.sclerago.presentation.main

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sclera.sclerago.R
import com.sclera.sclerago.presentation.components.ActionDock
import com.sclera.sclerago.presentation.components.SelectionSummary
import com.sclera.sclerago.presentation.components.BottomTabBar
import com.sclera.sclerago.presentation.components.CircleFab
import com.sclera.sclerago.presentation.components.NavItem
import com.sclera.sclerago.presentation.components.RoundButton
import com.sclera.sclerago.presentation.components.SearchFab
import com.sclera.sclerago.presentation.icons.Ico
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
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
 * The + and search buttons, stacked in the bottom corner; the search button opens into a capsule. They
 * are not shown while assets are being selected (the selection dock takes their place).
 */
@Composable
fun DashboardFabs(
    searchOpen: Boolean,
    onSearchOpenChange: (Boolean) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.navigationBarsPadding().imePadding().padding(end = 14.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CircleFab(Icons.Plus, stringResource(R.string.dash_add_asset), onAdd, Modifier.testTag(DashTags.ADD))
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
    onValidate: () -> Unit,
    onAssign: () -> Unit,
    onSubmit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.testTag(DashTags.DOCK)) {
        ActionDock(onValidate = onValidate, onAssign = onAssign, onSubmit = onSubmit, onDelete = onDelete, floating = true) {
            SelectionSummary(count, visible, allPicked, onClear, onSelectAll, Modifier.testTag(DashTags.SELECT_ALL))
        }
    }
}

/** What deleting the selected assets asks, as the title and body of the confirmation. */
@Composable
fun deleteSelectionTitle(count: Int): String = pluralStringResource(R.plurals.dash_delete_title, count, count)

@Composable
fun deleteSelectionBody(count: Int): String = pluralStringResource(R.plurals.dash_delete_body, count, count)
