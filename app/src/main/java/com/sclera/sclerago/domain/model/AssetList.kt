package com.sclera.sclerago.domain.model

/** Which assets the Assets pages show by location: everything, the chosen locations, or the unmapped ones. */
sealed interface LocationFilter {
    data object All : LocationFilter
    data class Specific(val locations: List<String>) : LocationFilter
    data object Unmapped : LocationFilter
}

/** Why an asset list is empty, so the screen can say it in the user's language. */
enum class EmptyReason { SEARCH, STATUS, ASSETS_TAB, SOURCE_TAB }

/** One status chip: [status] null is "All"; [count] is how many assets match the other filters. */
data class StatusCount(val status: Status?, val count: Int, val selected: Boolean)

/** [recollect]: the Recollect chip (how many were sent back, and whether the list is narrowed to them). */
data class AssetListData(
    val items: List<Asset>,
    val chips: List<StatusCount>,
    val empty: EmptyReason,
    val recollect: RecollectCount = RecollectCount(0, false),
)

/** The Recollect chip: [count] assets sent back to be collected again; [selected] when the list shows only them. */
data class RecollectCount(val count: Int, val selected: Boolean)

/**
 * What one list page asks for: [sources] null = every way of onboarding (the Assets tab). [recollect] narrows the list to
 * assets sent back to be collected again; as on the web it goes with "All" only, so a [status] turns it off.
 */
data class AssetListQuery(
    val sources: Set<Source>?,
    val location: LocationFilter = LocationFilter.All,
    val text: String = "",
    val status: Status? = null,
    val recollect: Boolean = false,
)
