package com.sclera.sclerago.domain.model

// The places import on the Integration page: buildings, floors and locations don't sync with the linked VDMS, they are
// sent there or imported from there by hand, as on the web.

enum class PlaceKind { BUILDING, FLOOR, LOCATION }

/** Where a place stands against the other side. */
enum class PlaceStatus {
    /** Not on the other side. */
    NEW,

    /** There, with the same name and parent. */
    SAME,

    /** There under the same id, but renamed or under another parent since: choosing it updates the other side. */
    CHANGED,

    /** Linked before, but deleted on the other side since: it can be sent or imported again. */
    GONE,
}

/** Which way places are copied: this property's to VDMS, or VDMS's into this property. */
enum class PlacesDirection { TO_VDMS, FROM_VDMS }

/** A building (children: floors), floor (children: locations) or location; [otherName] is its name on the other side when changed. */
data class PlaceNode(
    val kind: PlaceKind,
    val id: String,
    val name: String,
    val status: PlaceStatus,
    val otherName: String? = null,
    val children: List<PlaceNode> = emptyList(),
)

/** A place as chosen: its kind and its id on the side it is chosen from. */
data class PlaceKey(val kind: PlaceKind, val id: String)

/** Why a chosen place was skipped. */
enum class PlaceSkipReason { EXISTS, PARENT_MISSING, NOT_FOUND, ERROR, OTHER }

/**
 * One place a send or import added, updated or skipped ([reason] only for skipped ones); in a send's list of what was
 * sent, [update] says it was sent to update the place VDMS has.
 */
data class PlaceOutcome(
    val kind: PlaceKind?,
    val id: String,
    val name: String?,
    val reason: PlaceSkipReason? = null,
    val update: Boolean = false,
)

data class PlacesImportResult(
    val imported: List<PlaceOutcome> = emptyList(),
    val updated: List<PlaceOutcome> = emptyList(),
    val skipped: List<PlaceOutcome> = emptyList(),
)

enum class PlacesPushStatus { PENDING, RUNNING, DONE }

/** A send to VDMS, which VDMS works on in the background; [result] is filled in once it is [PlacesPushStatus.DONE]. */
data class PlacesPush(
    val id: String,
    val status: PlacesPushStatus,
    val requestedAt: Long?,
    val startedAt: Long?,
    val finishedAt: Long?,
    val result: PlacesImportResult,
) {
    /** VDMS has reported on it. */
    val finished: Boolean get() = status == PlacesPushStatus.DONE

    /**
     * VDMS is still working on it at [now]: not reported yet, and not given up on. The server stops waiting after
     * [PUSH_STALE_MS] from when VDMS started it (or it was asked for) and then lets a new send replace it.
     */
    fun isActive(now: Long): Boolean {
        if (finished) return false
        val since = startedAt ?: requestedAt ?: return true
        return now - since < PUSH_STALE_MS
    }

    companion object {
        /** The server's places_import.PUSH_STALE_SECONDS. */
        const val PUSH_STALE_MS = 10 * 60 * 1000L
    }
}

/**
 * Both sides: this property's places ([adc], each saying whether VDMS has it) and VDMS's as it last sent them at
 * [snapshotAt] ([vdms], each saying whether this property has it; a null [snapshotAt]: VDMS hasn't sent them yet), and
 * the latest send to VDMS.
 */
data class VdmsPlaces(
    val snapshotAt: Long?,
    val snapshotRequestedAt: Long?,
    val adc: List<PlaceNode>,
    val vdms: List<PlaceNode>,
    val push: PlacesPush?,
) {
    /** The tree places are chosen from when copying [direction]. */
    fun treeFor(direction: PlacesDirection): List<PlaceNode> = if (direction == PlacesDirection.TO_VDMS) adc else vdms
}

// ---------------------------------------------------------------- History

/** How a send or import went: VDMS hasn't read it, is adding, finished, or didn't report back in time. */
enum class TransferStatus { PENDING, RUNNING, DONE, NO_REPLY, OTHER }

/** How many places a transfer sent (into VDMS only), added, updated and skipped. */
data class TransferCounts(val sending: Int = 0, val imported: Int = 0, val updated: Int = 0, val skipped: Int = 0)

/** One send to VDMS or import from it, as the history lists it; [forMove] when it went with assets moved to Managed Assets. */
data class PlacesTransfer(
    val id: String,
    val direction: PlacesDirection,
    val forMove: Boolean,
    val status: TransferStatus,
    val counts: TransferCounts,
    val createdAt: Long?,
    val startedAt: Long?,
    val finishedAt: Long?,
    /** Who asked for it (null when the server doesn't know). */
    val byName: String?,
) {
    /** VDMS hasn't reported on it yet. */
    val waiting: Boolean get() = status == TransferStatus.PENDING || status == TransferStatus.RUNNING
}

/** One page of the history, newest first, and how many there are in all. */
data class PlacesHistoryPage(val items: List<PlacesTransfer>, val total: Int)

/** A transfer with its places: what was sent (into VDMS, until it reports), added, updated and skipped. */
data class PlacesTransferDetail(
    val transfer: PlacesTransfer,
    val sending: List<PlaceOutcome>,
    val result: PlacesImportResult,
)

/** How many buildings, floors and locations. */
data class PlaceCounts(val buildings: Int = 0, val floors: Int = 0, val locations: Int = 0) {
    val isEmpty: Boolean get() = buildings <= 0 && floors <= 0 && locations <= 0
}

/** What moving assets to Managed Assets would take: the [assets], and the [places] VDMS lacks, added there first. */
data class MovePreview(
    val assets: Int,
    val places: PlaceCounts,
    /** An earlier send of places to VDMS hasn't finished: a move that needs places is refused until it has. */
    val placesInProgress: Boolean,
)

/** A move to Managed Assets: how many assets will move (null: the server didn't say), and the places sent to VDMS first. */
data class MoveOutcome(val moved: Int?, val placesSent: PlaceCounts = PlaceCounts())
