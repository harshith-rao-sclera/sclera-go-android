package com.sclera.sclerago.data.remote.adc

import kotlinx.serialization.Serializable

// `/properties/{id}/geofence` (camelCase). GeoJSON positions are [longitude, latitude].

/** The fences as a GeoJSON MultiPolygon: polygons of rings of [longitude, latitude]. */
@Serializable
data class FencesDto(
    val type: String = "MultiPolygon",
    val coordinates: List<List<List<List<Double>>>> = emptyList(),
)

@Serializable
data class GeofenceDto(val enforced: Boolean = false, val fences: FencesDto? = null)

// `/properties/{id}/fields`: a standard asset field. `checks` is kept as JSON (read by the field checks mapper).

@Serializable
data class CfgOptionDto(val label: String, val color: String? = null)

@Serializable
data class CfgFieldDto(
    val id: String,
    val key: String = "",
    val name: String = "",
    val type: String = "",
    val options: List<CfgOptionDto>? = null,
    val isRequired: Boolean = false,
    val isActive: Boolean = true,
    val isBuiltIn: Boolean = false,
    val position: Int = 0,
    val helpText: String? = null,
    val defaultValue: String? = null,
    val checks: kotlinx.serialization.json.JsonObject? = null,
)

// `/properties/{id}/buildings|floors|locations`.

@Serializable
data class BuildingDto(
    val id: String,
    val name: String = "",
    val code: String? = null,
    val isActive: Boolean = true,
    val position: Int = 0,
    /** VDMS has (or had) it too; deleting it here leaves VDMS's copy. */
    val linkedToVdms: Boolean = false,
)

@Serializable
data class FloorDto(
    val id: String,
    val buildingId: String = "",
    val name: String = "",
    val code: String? = null,
    val type: String? = null,
    val level: Int? = null,
    val isActive: Boolean = true,
    val position: Int = 0,
    val linkedToVdms: Boolean = false,
)

@Serializable
data class LocationDto(
    val id: String,
    val floorId: String = "",
    val name: String = "",
    val code: String? = null,
    val type: String? = null,
    val isActive: Boolean = true,
    val position: Int = 0,
    val linkedToVdms: Boolean = false,
)

// Spreadsheet imports: `POST /properties/{id}/places/import` and `POST /properties/{id}/fields/import/read`.

/** A row the server left out: its number as Excel counts rows, what it names and why (the server's words, not shown). */
@Serializable
data class SheetSkipDto(val row: Int = 0, val name: String = "", val reason: String = "")

/** What `/places/import` made, the rows it skipped (locations already there) and the columns it didn't read. */
@Serializable
data class PlaceImportResultDto(
    val buildingsCreated: Int = 0,
    val floorsCreated: Int = 0,
    val locationsCreated: Int = 0,
    val skipped: List<SheetSkipDto> = emptyList(),
    val ignoredColumns: List<String> = emptyList(),
)

/** A field read from the sheet, ready for `POST /fields/bulk` (nothing is made by reading). */
@Serializable
data class FieldImportRowDto(
    val row: Int = 0,
    val name: String = "",
    val type: String = "",
    val options: List<CfgOptionDto>? = null,
    val isRequired: Boolean = false,
    val isActive: Boolean = true,
    val helpText: String? = null,
    val defaultValue: String? = null,
)

/** What `/fields/import/read` read: the new fields, the rows it skipped and the columns it didn't read. */
@Serializable
data class FieldImportReadDto(
    val fields: List<FieldImportRowDto> = emptyList(),
    val skipped: List<SheetSkipDto> = emptyList(),
    val ignoredColumns: List<String> = emptyList(),
)

/** One problem of a refused sheet (`422`): only the row is read, the server's message is never shown. */
@Serializable
data class SheetProblemDto(val row: Int? = null)

/** The `errors` of a refused sheet's problem JSON. */
@Serializable
data class SheetProblemsDto(val errors: List<SheetProblemDto> = emptyList())
