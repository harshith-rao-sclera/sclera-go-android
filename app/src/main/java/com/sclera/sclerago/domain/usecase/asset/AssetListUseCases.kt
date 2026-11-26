package com.sclera.sclerago.domain.usecase.asset

import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.AssetCatalog
import com.sclera.sclerago.domain.model.AssetListData
import com.sclera.sclerago.domain.model.AssetListQuery
import com.sclera.sclerago.domain.model.EmptyReason
import com.sclera.sclerago.domain.model.LocationFilter
import com.sclera.sclerago.domain.model.MasterFormat
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.Source
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.model.StatusCount
import com.sclera.sclerago.domain.model.ToggleChip
import com.sclera.sclerago.domain.model.ValidationMark
import com.sclera.sclerago.domain.model.hasText
import com.sclera.sclerago.domain.model.holds
import com.sclera.sclerago.domain.model.isIn
import com.sclera.sclerago.domain.model.unplaced
import com.sclera.sclerago.domain.usecase.access.ObserveAccessChangesUseCase
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The ways of onboarding the Assets tab has chips for, as the web's optional tabs: over Bluetooth, over a Wi-Fi network. */
private val ONBOARDED_CHIPS = listOf(Source.BT, Source.WIFI)

/** The status chips in the web's order (its status tabs): Draft, Submitted, Exception, Validated, then AI suggestions. */
private val CHIP_ORDER = listOf(Status.DRAFT, Status.SUBMITTED, Status.EXCEPTION, Status.VALIDATED, Status.AI)

/**
 * The rows and chips of one asset list: [assets] of the tab's sources, narrowed by location, search text and the chosen
 * chip. The assets in Managed Assets ([managed], never in [assets]) are listed only while the Managed assets chip is on
 * ([AssetListQuery.managed]), on their own; its count follows the location and the filter, as the other chips. The
 * chip counts (status and Change requests) follow the chosen location (all of the property,
 * one building, floor or location, or the unmapped assets) and the search text, as the web's status tabs do. The "AI suggestion" chip counts every asset with the AI flag (see [isIn]),
 * so it can overlap the other chips. The Change requests chip shows for everyone, with 0 while there are none (whoever
 * the user is: a chip is never hidden by role). The Failed validation and
 * Needs a look chips show while some assets have that mark. The filter sheet's choices ([AssetListQuery.filter], read
 * against the type catalogue [formats]) narrow the list and the chip counts alike, as the location does.
 */
class BuildAssetListUseCase @Inject constructor() {
    operator fun invoke(
        assets: List<Asset>,
        query: AssetListQuery,
        formats: Map<String, MasterFormat> = emptyMap(),
        managed: List<Asset> = emptyList(),
    ): AssetListData {
        val text = query.text.trim().lowercase()
        // As the API's `q`: the name, manufacturer, model, serial number, description or any other answer.
        fun searched(list: List<Asset>) = if (text.isEmpty()) list else list.filter { it.hasText(text) }
        // The tab's assets in the chosen location that pass the filter and the search: what the chips count (as the web's
        // status tabs, which ask the summary with the list's filters and search, but not their own status).
        val tab = searched(scope(assets, query).filter { query.filter.matches(it, formats) })
        // The same of the assets in Managed Assets: what the Managed assets chip counts.
        val inManaged = searched(scope(managed, query).filter { query.filter.matches(it, formats) })
        val managedOn = query.managed
        // Bluetooth / Wi-Fi: on the Assets tab only (the other tabs already list one way of onboarding each).
        val onboardedOn = query.onboarded?.takeIf { query.sources == null && !managedOn }
        val tabOn = managedOn || onboardedOn != null
        val changesOn = query.changeRequests && !tabOn
        val chosen = query.status.takeUnless { changesOn || tabOn }
        val markOn = query.validation?.takeIf { chosen == null && !changesOn && !tabOn }
        val chips = listOf(StatusCount(null, tab.size, chosen == null && !changesOn && markOn == null && !tabOn)) +
            CHIP_ORDER.map { st -> StatusCount(st, tab.count { it.isIn(st) }, chosen == st) }
        val reason = when {
            text.isNotEmpty() -> EmptyReason.SEARCH
            chosen != null || changesOn || markOn != null || tabOn -> EmptyReason.STATUS
            !query.filter.isEmpty -> EmptyReason.FILTER
            query.sources == null -> EmptyReason.ASSETS_TAB
            else -> EmptyReason.SOURCE_TAB
        }
        val items = if (managedOn) inManaged else tab.filter {
            (chosen == null || it.isIn(chosen)) && (!changesOn || it.changeRequest != null) &&
                (markOn == null || it.validationMark == markOn) && (onboardedOn == null || it.src == onboardedOn)
        }
        val changeCount = tab.count { it.changeRequest != null }
        val changeChip = ToggleChip(changeCount, changesOn)
        val markChips = ValidationMark.entries.associateWith { m -> ToggleChip(tab.count { it.validationMark == m }, markOn == m) }
            .filterValues { it.count > 0 || it.selected }
        // As the web's optional Managed assets tab: shown while there are some, or while it is chosen.
        val managedChip = ToggleChip(inManaged.size, managedOn).takeIf { it.count > 0 || it.selected }
        // The Bluetooth and Wi-Fi chips (the Assets tab only), always shown, with 0 when none were onboarded that way.
        val onboardedChips = if (query.sources != null) emptyMap() else ONBOARDED_CHIPS
            .associateWith { s -> ToggleChip(tab.count { it.src == s }, onboardedOn == s) }
        return AssetListData(items, chips, reason, changeChip, markChips, managedChip, onboardedChips)
    }

    /** The tab's assets ([AssetListQuery.sources]) in the chosen location, before the filter, chips and search. */
    fun scope(assets: List<Asset>, query: AssetListQuery): List<Asset> =
        assets.filter { (query.sources == null || it.src in query.sources) && inLocation(it, query.location) }

    /** By building, floor and location id, as the API's filters (see [LocationFilter]). */
    private fun inLocation(a: Asset, filter: LocationFilter) = when (filter) {
        LocationFilter.All -> true
        is LocationFilter.Specific -> filter.place.holds(a)
        LocationFilter.Unmapped -> a.unplaced
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

/**
 * What the signed-in user's access says, for the asset lists' selection: which permissions they have, whether they are a
 * reviewer (Validate, as the group grants it) and who they are (whose assets count as assigned to someone else). All
 * false (and no one) while it isn't known.
 */
data class SelectionAccess(
    val collect: Boolean = false,
    val check: Boolean = false,
    val validate: Boolean = false,
    val reviewer: Boolean = false,
    val userId: String? = null,
)

/**
 * [SelectionAccess], again whenever the access is fetched or changes, so what the selection offers (and which assets can
 * be ticked) follows the user's group at once rather than when the list next changes.
 */
class ObserveSelectionAccessUseCase @Inject constructor(
    private val changes: ObserveAccessChangesUseCase,
    private val guard: PermissionGuard,
) {
    operator fun invoke(): Flow<SelectionAccess> = changes().map { now() }.distinctUntilChanged()

    /** The access as it is now. */
    fun now(): SelectionAccess = SelectionAccess(
        collect = guard.allows(Permission.COLLECT),
        check = guard.allows(Permission.CHECK),
        validate = guard.allows(Permission.VALIDATE),
        reviewer = guard.isReviewer(),
        userId = guard.userId(),
    )
}

/**
 * The catalogue as the lists show it with the property's AI switch [aiOn]: off, no asset is offered AI suggestions (the
 * server's ai_fillable_condition is false for every asset then), so the AI suggestion chip counts none.
 */
fun AssetCatalog.withAiSwitch(aiOn: Boolean): AssetCatalog = if (aiOn) this else copy(
    assets = assets.map { if (it.aiSuggestion) it.copy(aiSuggestion = false) else it },
    managed = managed.map { if (it.aiSuggestion) it.copy(aiSuggestion = false) else it },
)

/**
 * As the web's selection bar, Submit can't be used (it shows greyed out, "Some are already submitted") while any of
 * [assets] is no longer being collected: submitted, an exception or signed off. The server would leave those as they are.
 */
fun someAlreadySubmitted(assets: Collection<Asset>): Boolean =
    assets.any { it.st != Status.DRAFT && it.st != Status.INPUT && it.st != Status.AI }
