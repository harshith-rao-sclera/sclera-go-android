package com.sclera.sclerago.domain.model

/** Which assets the Assets pages show by location: everything, the chosen locations, or the unmapped ones. */
sealed interface LocationFilter {
    data object All : LocationFilter
    data class Specific(val locations: List<String>) : LocationFilter
    data object Unmapped : LocationFilter
}

/** Why an asset list is empty, so the screen can say it in the user's language. */
enum class EmptyReason { SEARCH, STATUS, ASSETS_TAB, SOURCE_TAB }

/**
 * One status chip: [status] null is "All"; [count] is how many of the tab's assets are in it. As the web's tabs (the
 * server's /assets/summary, asked for the whole property), it doesn't follow the chosen location or the search.
 */
data class StatusCount(val status: Status?, val count: Int, val selected: Boolean)

/**
 * [recollect]: the Recollect chip (how many were sent back, and whether the list is narrowed to them).
 * [changeRequests]: the Change requests chip; null hides it (as on the web: shown to admins, and to anyone else only
 * while there are some).
 */
data class AssetListData(
    val items: List<Asset>,
    val chips: List<StatusCount>,
    val empty: EmptyReason,
    val recollect: ToggleChip = ToggleChip(0, false),
    val changeRequests: ToggleChip? = null,
)

/** A chip that narrows the list to some assets: [count] of them; [selected] when the list shows only them. */
data class ToggleChip(val count: Int, val selected: Boolean)

/**
 * What one list page asks for: [sources] null = every way of onboarding (the Assets tab). [recollect] narrows the list to
 * assets sent back to be collected again; as on the web it goes with "All" only, so a [status] turns it off.
 * [changeRequests] narrows it to assets someone asked an admin to reopen (the web's Change requests tab, which is one
 * of its tabs, so it turns the status and Recollect off). [showChangeRequests]: that chip shows even with none (admins).
 */
data class AssetListQuery(
    val sources: Set<Source>?,
    val location: LocationFilter = LocationFilter.All,
    val text: String = "",
    val status: Status? = null,
    val recollect: Boolean = false,
    val changeRequests: Boolean = false,
    val showChangeRequests: Boolean = false,
)
