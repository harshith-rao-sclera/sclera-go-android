package com.sclera.sclerago.data.remote.adc

import kotlinx.serialization.Serializable

// The places import between a property and its linked VDMS (`/v1/properties/{p}/integration/places…`), as the server
// sends it. Buildings, floors and locations don't sync with VDMS: they are sent or imported by hand.

/**
 * A building (with [floors]), floor (with [locations]) or location. [status] is new, same, changed or gone (an older API
 * sends only [exists]); [otherName] is its name on the other side when changed.
 */
@Serializable
data class PlaceNodeDto(
    val id: String = "",
    val name: String = "",
    val exists: Boolean = false,
    val status: String? = null,
    val otherName: String? = null,
    val floors: List<PlaceNodeDto> = emptyList(),
    val locations: List<PlaceNodeDto> = emptyList(),
)

/** One place a send or import added, updated or skipped ([reason]: exists, parent_missing, not_found or error). */
@Serializable
data class PlaceResultDto(
    val kind: String = "",
    val id: String = "",
    val name: String? = null,
    val reason: String? = null,
    /** In a transfer's list of what was sent: sent to update the place VDMS has. */
    val update: Boolean = false,
)

/** The latest send to VDMS; [status] is pending, running or done. */
@Serializable
data class PlacesPushDto(
    val id: String = "",
    val status: String = "",
    val requestedAt: String? = null,
    val startedAt: String? = null,
    val finishedAt: String? = null,
    val imported: List<PlaceResultDto> = emptyList(),
    val updated: List<PlaceResultDto> = emptyList(),
    val skipped: List<PlaceResultDto> = emptyList(),
)

/** Both sides: [adc] (exists = VDMS has it) and [vdms] as VDMS last sent it at [snapshotAt] (exists = the property has it). */
@Serializable
data class PlacesDto(
    val snapshotAt: String? = null,
    val snapshotRequestedAt: String? = null,
    val adc: List<PlaceNodeDto> = emptyList(),
    val vdms: List<PlaceNodeDto> = emptyList(),
    val push: PlacesPushDto? = null,
)

@Serializable
data class PlacesRefreshDto(val snapshotRequestedAt: String? = null)

@Serializable
data class PlacesImportDto(
    val imported: List<PlaceResultDto> = emptyList(),
    val updated: List<PlaceResultDto> = emptyList(),
    val skipped: List<PlaceResultDto> = emptyList(),
)

@Serializable
data class TransferCountsDto(val sending: Int = 0, val imported: Int = 0, val updated: Int = 0, val skipped: Int = 0)

/**
 * One send to VDMS or import from it (`…/places/history`): [direction] to_vdms or from_vdms, [status] pending, running,
 * done or no_reply. The detail (`…/places/history/{id}`) also has the places, in [sending], [imported], [updated] and [skipped].
 */
@Serializable
data class PlacesTransferDto(
    val id: String = "",
    val direction: String = "",
    val forMove: Boolean = false,
    val status: String = "",
    val counts: TransferCountsDto = TransferCountsDto(),
    val createdAt: String? = null,
    val startedAt: String? = null,
    val finishedAt: String? = null,
    val createdByName: String? = null,
    val sending: List<PlaceResultDto> = emptyList(),
    val imported: List<PlaceResultDto> = emptyList(),
    val updated: List<PlaceResultDto> = emptyList(),
    val skipped: List<PlaceResultDto> = emptyList(),
)

@Serializable
data class PlacesHistoryDto(val items: List<PlacesTransferDto> = emptyList(), val total: Int = 0)

@Serializable
data class PlaceCountsDto(val buildings: Int = 0, val floors: Int = 0, val locations: Int = 0)

/** What a move to Managed Assets would take: the assets, and the places VDMS lacks that go there first. */
@Serializable
data class MovePreviewDto(val assets: Int = 0, val places: PlaceCountsDto = PlaceCountsDto(), val placesInProgress: Boolean = false)
