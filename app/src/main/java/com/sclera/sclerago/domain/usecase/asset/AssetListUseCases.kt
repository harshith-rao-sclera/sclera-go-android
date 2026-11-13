package com.sclera.sclerago.domain.usecase.asset

import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.AssetListData
import com.sclera.sclerago.domain.model.AssetListQuery
import com.sclera.sclerago.domain.model.EmptyReason
import com.sclera.sclerago.domain.model.LocationFilter
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.model.StatusCount
import javax.inject.Inject

/**
 * The rows and status chips of one asset list: [assets] narrowed by source, location and search text.
 * The chip counts follow those filters (so they follow the search), the rows also follow the chosen status.
 */
class BuildAssetListUseCase @Inject constructor() {
    operator fun invoke(assets: List<Asset>, query: AssetListQuery): AssetListData {
        val text = query.text.trim().lowercase()
        val base = assets
            .filter { query.sources == null || it.src in query.sources }
            .filter { inLocation(it, query.location) }
            .filter { text.isEmpty() || "${it.name} ${it.detail}".lowercase().contains(text) }
        val chosen = query.status
        val chips = listOf(StatusCount(null, base.size, chosen == null)) +
            Status.entries.filter { it != Status.INPUT }.map { st -> StatusCount(st, base.count { it.st == st }, chosen == st) }
        val reason = when {
            text.isNotEmpty() -> EmptyReason.SEARCH
            chosen != null -> EmptyReason.STATUS
            query.sources == null -> EmptyReason.ASSETS_TAB
            else -> EmptyReason.SOURCE_TAB
        }
        return AssetListData(base.filter { chosen == null || it.st == chosen }, chips, reason)
    }

    private fun inLocation(a: Asset, filter: LocationFilter) = when (filter) {
        LocationFilter.All -> true
        is LocationFilter.Specific -> a.loc != null && a.loc in filter.locations
        LocationFilter.Unmapped -> a.loc == null
    }
}

/** Picks or un-picks one asset in a selection. */
class ToggleSelectionUseCase @Inject constructor() {
    operator fun invoke(picked: Set<Int>, id: Int): Set<Int> = if (id in picked) picked - id else picked + id
}

/** "Select all" / "Clear all": everything visible is picked, or (when it all already is) nothing. */
class SelectAllUseCase @Inject constructor() {
    operator fun invoke(picked: Set<Int>, visible: List<Int>): Set<Int> =
        if (visible.isNotEmpty() && picked.containsAll(visible)) emptySet() else picked + visible
}
