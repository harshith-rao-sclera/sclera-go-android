package com.sclera.sclerago.presentation.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sclera.sclerago.domain.model.AssetListData
import com.sclera.sclerago.domain.model.EmptyReason
import com.sclera.sclerago.presentation.assets.AssetListBinding
import com.sclera.sclerago.presentation.integration.ManagedSummaryViewModel

private val NoAssets = AssetListData(emptyList(), emptyList(), EmptyReason.ASSETS_TAB)

/**
 * What the bottom tab [tab] needs to show its asset list: the rows and chips, the selection, and the
 * actions. Tapping a row opens it, or (while assets are selected) picks it; its validation mark opens it on its
 * Validation tab.
 */
@Composable
fun rememberAssetListBinding(
    app: AppViewModel,
    tab: Tab,
    list: AssetListViewModel = hiltViewModel(),
    summary: ManagedSummaryViewModel = hiltViewModel(),
): AssetListBinding {
    val s by list.state.collectAsStateWithLifecycle()
    return AssetListBinding(
        data = s.lists[tab] ?: NoAssets,
        picked = s.picked,
        query = s.query,
        onChip = { list.setStatusFilter(tab, it.status) },
        onChangeRequests = { list.selectChangeRequests(tab) },
        onValidation = { list.selectValidation(tab, it) },
        onManaged = { list.selectManaged(tab) },
        onOnboarded = { list.selectOnboarded(tab, it) },
        onToggle = list::toggle,
        onOpen = { id -> if (s.selecting) list.toggle(id) else app.openAsset(id) },
        // A row's validation mark opens the asset on its Validation tab (the web's badge); while selecting it picks, as the row.
        loading = s.loading,
        refreshing = s.refreshing,
        onRefresh = list::refresh,
        // The info button beside the chips: the Managed Assets Summary (moved to Managed assets, and back).
        onInfo = { summary.open(app.currentProp, app.placeScope) },
    )
}
