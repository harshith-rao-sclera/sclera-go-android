package com.sclera.sclerago.domain.model

/** Which assets the Assets pages show by location: everything, the chosen place, or the unmapped ones. */
sealed interface LocationFilter {
    data object All : LocationFilter

    /**
     * The assets in [place], by id, as the API's `buildingId` / `floorId` / `locationId` filters: a building keeps every
     * asset in it, whether it sits on the building itself, on one of its floors or in one of their locations.
     */
    data class Specific(val place: PlaceScope) : LocationFilter

    /** The assets placed nowhere: no building, floor or location. */
    data object Unmapped : LocationFilter
}

/** One building, floor or location as a [PlaceScope] of that [kind]; [id] empty is the whole property. */
fun placeScopeOf(kind: NodeKind?, id: String): PlaceScope = when {
    id.isEmpty() || kind == null -> PlaceScope()
    kind == NodeKind.BUILDING -> PlaceScope(buildingIds = listOf(id))
    kind == NodeKind.FLOOR -> PlaceScope(floorIds = listOf(id))
    else -> PlaceScope(locationIds = listOf(id))
}

/**
 * Whether [asset] is in these places, as the API filters (`Asset.building_id IN buildingIds AND floor_id IN floorIds AND
 * location_id IN locationIds`, each only when given): an empty scope holds every asset.
 */
fun PlaceScope.holds(asset: Asset): Boolean =
    (buildingIds.isEmpty() || asset.buildingId in buildingIds) &&
        (floorIds.isEmpty() || asset.floorId in floorIds) &&
        (locationIds.isEmpty() || asset.locationId in locationIds)

/** The asset is placed nowhere: no building, floor or location (the Unmapped tab). */
val Asset.unplaced: Boolean get() = buildingId == null && floorId == null && locationId == null

/** Why an asset list is empty, so the screen can say it in the user's language. */
enum class EmptyReason { SEARCH, STATUS, FILTER, ASSETS_TAB, SOURCE_TAB }

/**
 * One status chip: [status] null is "All"; [count] is how many of the tab's assets are in it. It follows the chosen
 * location and the filter sheet, but not the search (see BuildAssetListUseCase).
 */
data class StatusCount(val status: Status?, val count: Int, val selected: Boolean)

/**
 * [changeRequests]: the Change requests chip; null hides it (as on the web: shown to admins, and to anyone else only
 * while there are some). [validation]: the Failed validation and Needs a look chips, each only while some assets have
 * that mark (or it is chosen).
 */
data class AssetListData(
    val items: List<Asset>,
    val chips: List<StatusCount>,
    val empty: EmptyReason,
    val changeRequests: ToggleChip? = null,
    val validation: Map<ValidationMark, ToggleChip> = emptyMap(),
    /**
     * The Managed assets chip: the assets in Managed Assets that match the other choices (the web's managedCount); null
     * hides it (none, and it isn't chosen). While it is [ToggleChip.selected], [items] are those assets, read-only.
     */
    val managed: ToggleChip? = null,
    /**
     * The Bluetooth and Wi-Fi chips of the Assets tab (the web's optional status tabs): how many of its assets were
     * onboarded each way. Both always show (0 when there are none); while one is [ToggleChip.selected], [items] are those
     * assets.
     */
    val onboarded: Map<Source, ToggleChip> = emptyMap(),
)

/** A chip that narrows the list to some assets: [count] of them; [selected] when the list shows only them. */
data class ToggleChip(val count: Int, val selected: Boolean)

/**
 * What one list page asks for: [sources] null = every way of onboarding (the Assets tab).
 * [changeRequests] narrows it to assets someone asked an admin to reopen (the web's Change requests tab, which is one
 * of its tabs, so it turns the status off). [showChangeRequests]: that chip shows even with none (admins and reviewers).
 * [validation] narrows it to the assets whose validation has that mark; it goes with "All" only.
 */
data class AssetListQuery(
    val sources: Set<Source>?,
    val location: LocationFilter = LocationFilter.All,
    val text: String = "",
    val status: Status? = null,
    val changeRequests: Boolean = false,
    val showChangeRequests: Boolean = false,
    val validation: ValidationMark? = null,
    /** The filter sheet's choices (people and types); they narrow the chips as well as the rows. */
    val filter: AssetFilter = AssetFilter(),
    /**
     * Narrows it to the assets in Managed Assets (the web's Managed assets tab, one of its tabs: it turns the status,
     * Change requests and validation chips off). Those are never in the list otherwise.
     */
    val managed: Boolean = false,
    /**
     * Narrows the Assets tab to the assets onboarded this way, [Source.BT] or [Source.WIFI] (the web's Bluetooth and Wi-Fi
     * tabs, each one of its tabs: it turns the status, Change requests and validation chips off).
     */
    val onboarded: Source? = null,
)
