package com.sclera.sclerago.domain.model

/** Which assets the Assets pages show by location: everything, the chosen locations, or the unmapped ones. */
sealed interface LocationFilter {
    data object All : LocationFilter
    data class Specific(val locations: List<String>) : LocationFilter
    data object Unmapped : LocationFilter
}

/** Why an asset list is empty, so the screen can say it in the user's language. */
enum class EmptyReason { SEARCH, STATUS, FILTER, ASSETS_TAB, SOURCE_TAB }

/**
 * One status chip: [status] null is "All"; [count] is how many of the tab's assets are in it. As the web's tabs (the
 * server's /assets/summary, asked for the whole property), it doesn't follow the chosen location or the search.
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
)

/** A chip that narrows the list to some assets: [count] of them; [selected] when the list shows only them. */
data class ToggleChip(val count: Int, val selected: Boolean)

/**
 * What one list page asks for: [sources] null = every way of onboarding (the Assets tab).
 * [changeRequests] narrows it to assets someone asked an admin to reopen (the web's Change requests tab, which is one
 * of its tabs, so it turns the status off). [showChangeRequests]: that chip shows even with none (admins).
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
)
