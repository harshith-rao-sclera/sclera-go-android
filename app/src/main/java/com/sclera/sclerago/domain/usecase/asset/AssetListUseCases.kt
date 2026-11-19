package com.sclera.sclerago.domain.usecase.asset

import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.AssetListData
import com.sclera.sclerago.domain.model.AssetListQuery
import com.sclera.sclerago.domain.model.EmptyReason
import com.sclera.sclerago.domain.model.LocationFilter
import com.sclera.sclerago.domain.model.ToggleChip
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.model.StatusCount
import com.sclera.sclerago.domain.model.isIn
import javax.inject.Inject

/**
 * The rows and chips of one asset list: [assets] of the tab's sources, narrowed by location, search text and the chosen
 * chip. The chip counts (status, Recollect and Change requests) follow the chosen location (all of the property,
 * one location or the unmapped assets) but not the search text. The "AI suggestion" chip counts every asset with the AI flag (see [isIn]),
 * so it can overlap the other chips. The Change requests chip shows for admins ([AssetListQuery.showChangeRequests]),
 * who act on the requests, and for anyone else only while there are some (as on the web).
 */
class BuildAssetListUseCase @Inject constructor() {
    operator fun invoke(assets: List<Asset>, query: AssetListQuery): AssetListData {
        val text = query.text.trim().lowercase()
        // The tab's assets in the chosen location: what the chips count.
        val tab = assets.filter { (query.sources == null || it.src in query.sources) && inLocation(it, query.location) }
        val shown = tab.filter { text.isEmpty() || "${it.name} ${it.detail}".lowercase().contains(text) }
        val changesOn = query.changeRequests
        val chosen = query.status.takeUnless { changesOn }
        val recollectOn = query.recollect && chosen == null && !changesOn
        val chips = listOf(StatusCount(null, tab.size, chosen == null && !recollectOn && !changesOn)) +
            Status.entries.filter { it != Status.INPUT }.map { st -> StatusCount(st, tab.count { it.isIn(st) }, chosen == st) }
        val reason = when {
            text.isNotEmpty() -> EmptyReason.SEARCH
            chosen != null || recollectOn || changesOn -> EmptyReason.STATUS
            query.sources == null -> EmptyReason.ASSETS_TAB
            else -> EmptyReason.SOURCE_TAB
        }
        val items = shown.filter {
            (chosen == null || it.isIn(chosen)) && (!recollectOn || it.recollect) && (!changesOn || it.changeRequest != null)
        }
        val changeCount = tab.count { it.changeRequest != null }
        val changeChip = ToggleChip(changeCount, changesOn).takeIf { query.showChangeRequests || changeCount > 0 || changesOn }
        return AssetListData(items, chips, reason, ToggleChip(tab.count { it.recollect }, recollectOn), changeChip)
    }

    private fun inLocation(a: Asset, filter: LocationFilter) = when (filter) {
        LocationFilter.All -> true
        is LocationFilter.Specific -> a.loc != null && a.loc in filter.locations
        LocationFilter.Unmapped -> a.loc == null
    }
}

/** Picks or un-picks one asset in a selection. */
class ToggleSelectionUseCase @Inject constructor() {
    /** An asset that isn't [selectable] (someone else's, for a user who isn't an admin) can be unpicked but not picked. */
    operator fun invoke(picked: Set<String>, id: String, selectable: Boolean = true): Set<String> = when {
        id in picked -> picked - id
        selectable -> picked + id
        else -> picked
    }
}

/**
 * "Select all" / "Clear all": everything visible that is [selectable] is picked, or (when it all already is) nothing.
 * As the web's select-all, assets that aren't selectable are skipped.
 */
class SelectAllUseCase @Inject constructor() {
    operator fun invoke(picked: Set<String>, visible: List<String>, selectable: (String) -> Boolean = { true }): Set<String> {
        val pickable = visible.filter(selectable)
        return if (pickable.isNotEmpty() && picked.containsAll(pickable)) emptySet() else picked + pickable
    }
}
