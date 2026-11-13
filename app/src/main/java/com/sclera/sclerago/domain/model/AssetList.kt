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

data class AssetListData(val items: List<Asset>, val chips: List<StatusCount>, val empty: EmptyReason)

/** What one list page asks for: [sources] null = every way of onboarding (the Assets tab). */
data class AssetListQuery(
    val sources: Set<Source>?,
    val location: LocationFilter = LocationFilter.All,
    val text: String = "",
    val status: Status? = null,
)
