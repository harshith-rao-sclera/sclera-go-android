package com.sclera.sclerago.data.mapper

import com.sclera.sclerago.data.remote.adc.MovePreviewDto
import com.sclera.sclerago.data.remote.adc.PlaceCountsDto
import com.sclera.sclerago.data.remote.adc.PlaceNodeDto
import com.sclera.sclerago.data.remote.adc.PlaceResultDto
import com.sclera.sclerago.data.remote.adc.PlacesDto
import com.sclera.sclerago.data.remote.adc.PlacesImportDto
import com.sclera.sclerago.data.remote.adc.PlacesPushDto
import com.sclera.sclerago.domain.model.MovePreview
import com.sclera.sclerago.domain.model.PlaceCounts
import com.sclera.sclerago.domain.model.PlaceKey
import com.sclera.sclerago.domain.model.PlaceKind
import com.sclera.sclerago.domain.model.PlaceNode
import com.sclera.sclerago.domain.model.PlaceOutcome
import com.sclera.sclerago.domain.model.PlaceSkipReason
import com.sclera.sclerago.domain.model.PlaceStatus
import com.sclera.sclerago.domain.model.PlacesImportResult
import com.sclera.sclerago.domain.model.PlacesPush
import com.sclera.sclerago.domain.model.PlacesPushStatus
import com.sclera.sclerago.domain.model.VdmsPlaces
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray

// The places import (`…/integration/places`) between the server's JSON and the domain. The server's JSON is untrusted:
// places without an id are dropped and texts are capped.

private const val MAX_TEXT = 255

fun PlacesDto.toDomain() = VdmsPlaces(
    snapshotAt = parseDbTime(snapshotAt),
    snapshotRequestedAt = parseDbTime(snapshotRequestedAt),
    adc = adc.toBuildings(),
    vdms = vdms.toBuildings(),
    push = push?.toDomain(),
)

private fun List<PlaceNodeDto>.toBuildings() = toPlaces(PlaceKind.BUILDING) { building ->
    building.floors.toPlaces(PlaceKind.FLOOR) { floor -> floor.locations.toPlaces(PlaceKind.LOCATION) { emptyList() } }
}

private fun List<PlaceNodeDto>.toPlaces(kind: PlaceKind, children: (PlaceNodeDto) -> List<PlaceNode>): List<PlaceNode> =
    filter { it.id.isNotBlank() }.map { dto ->
        PlaceNode(
            kind = kind,
            id = dto.id.take(MAX_TEXT),
            name = dto.name.take(MAX_TEXT),
            status = placeStatus(dto.status, dto.exists),
            otherName = dto.otherName?.takeIf { it.isNotBlank() }?.take(MAX_TEXT),
            children = children(dto),
        )
    }

/** An older API sends only `exists`; a status this app doesn't know is read the same way. */
private fun placeStatus(status: String?, exists: Boolean): PlaceStatus = when (status?.lowercase()) {
    "new" -> PlaceStatus.NEW
    "same" -> PlaceStatus.SAME
    "changed" -> PlaceStatus.CHANGED
    "gone" -> PlaceStatus.GONE
    else -> if (exists) PlaceStatus.SAME else PlaceStatus.NEW
}

fun PlacesPushDto.toDomain() = PlacesPush(
    id = id,
    status = when (status.lowercase()) {
        "pending" -> PlacesPushStatus.PENDING
        "running" -> PlacesPushStatus.RUNNING
        else -> PlacesPushStatus.DONE
    },
    requestedAt = parseDbTime(requestedAt),
    finishedAt = parseDbTime(finishedAt),
    result = PlacesImportResult(imported.toOutcomes(), updated.toOutcomes(), skipped.toOutcomes()),
)

fun PlacesImportDto.toDomain() = PlacesImportResult(imported.toOutcomes(), updated.toOutcomes(), skipped.toOutcomes())

private fun List<PlaceResultDto>.toOutcomes() = map { r ->
    PlaceOutcome(
        kind = when (r.kind.lowercase()) {
            "building" -> PlaceKind.BUILDING
            "floor" -> PlaceKind.FLOOR
            "location" -> PlaceKind.LOCATION
            else -> null
        },
        id = r.id.take(MAX_TEXT),
        name = r.name?.takeIf { it.isNotBlank() }?.take(MAX_TEXT),
        reason = when (r.reason?.lowercase()) {
            null, "" -> null
            "exists" -> PlaceSkipReason.EXISTS
            "parent_missing" -> PlaceSkipReason.PARENT_MISSING
            "not_found" -> PlaceSkipReason.NOT_FOUND
            "error" -> PlaceSkipReason.ERROR
            else -> PlaceSkipReason.OTHER
        },
    )
}

fun PlaceCountsDto.toDomain() = PlaceCounts(buildings.coerceAtLeast(0), floors.coerceAtLeast(0), locations.coerceAtLeast(0))

fun MovePreviewDto.toDomain() = MovePreview(assets.coerceAtLeast(0), places.toDomain(), placesInProgress)

/** The places chosen to send or import, as the server takes them: `{ buildingIds, floorIds, locationIds }`. */
fun placesSelectionBody(chosen: Collection<PlaceKey>): JsonObject = buildJsonObject {
    fun ids(kind: PlaceKind) = chosen.filter { it.kind == kind && it.id.isNotBlank() }.map { it.id }.distinct()
    putJsonArray("buildingIds") { ids(PlaceKind.BUILDING).forEach { add(it) } }
    putJsonArray("floorIds") { ids(PlaceKind.FLOOR).forEach { add(it) } }
    putJsonArray("locationIds") { ids(PlaceKind.LOCATION).forEach { add(it) } }
}
