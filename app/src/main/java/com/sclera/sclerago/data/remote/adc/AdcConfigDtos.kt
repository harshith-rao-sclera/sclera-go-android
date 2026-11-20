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
