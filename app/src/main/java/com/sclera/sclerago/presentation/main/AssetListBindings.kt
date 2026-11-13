package com.sclera.sclerago.presentation.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sclera.sclerago.domain.model.AssetListData
import com.sclera.sclerago.domain.model.EmptyReason
import com.sclera.sclerago.presentation.assets.AssetListBinding

private val NoAssets = AssetListData(emptyList(), emptyList(), EmptyReason.ASSETS_TAB)

/**
 * What the bottom tab [tab] needs to show its asset list: the rows and chips, the selection, and the
 * actions. Tapping a row opens it, or (while assets are selected) picks it.
 */
@Composable
fun rememberAssetListBinding(app: AppViewModel, tab: Tab, list: AssetListViewModel = hiltViewModel()): AssetListBinding {
    val s by list.state.collectAsStateWithLifecycle()
    return AssetListBinding(
        data = s.lists[tab] ?: NoAssets,
        picked = s.picked,
        query = s.query,
        onChip = { list.setStatusFilter(tab, it.status) },
        onToggle = list::toggle,
        onOpen = { id -> if (s.selecting) list.toggle(id) else app.openAsset(id) },
    )
}
