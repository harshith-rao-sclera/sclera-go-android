package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.BuildingInfo
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.Geofence
import com.sclera.sclerago.domain.model.LocNode
import com.sclera.sclerago.domain.model.NewLocation
import com.sclera.sclerago.domain.model.NodeKind
import com.sclera.sclerago.domain.model.PlaceChange
import com.sclera.sclerago.domain.model.PhotoRule
import com.sclera.sclerago.domain.model.PropertySettings

/**
 * A property's settings at the server (Configuration → Asset fields photo rules, Settings → AI in validation).
 * Every call needs a connection and throws [com.sclera.sclerago.domain.model.OnlineException].
 */
interface PropertySettingsRepository {
    suspend fun load(propertyId: String): PropertySettings

    /** Replaces the photo rules (every kind is sent); returns the settings the server kept. */
    suspend fun savePhotoRules(propertyId: String, rules: List<PhotoRule>): PropertySettings

    /** Turns AI in validation on or off; returns the settings the server kept. */
    suspend fun setAiValidation(propertyId: String, on: Boolean): PropertySettings
}

/**
 * A property's geofence at the server (Configuration → Geofencing). Every call needs a connection and throws
 * [com.sclera.sclerago.domain.model.OnlineException].
 */
interface GeofenceRepository {
    suspend fun load(propertyId: String): Geofence

    /** Replaces every fence and the enforcement switch with [geofence]; returns what the server kept. */
    suspend fun save(propertyId: String, geofence: Geofence): Geofence
}

/**
 * Configuration → Asset fields at the server: the property's standard fields (`/properties/{id}/fields`). Every call
 * needs a connection and throws [com.sclera.sclerago.domain.model.OnlineException].
 */
interface ConfigFieldsRepository {
    /** The standard fields the app has an input for, in their order. */
    suspend fun load(propertyId: String): List<FieldCfg>

    /** Adds [fields] together (all or none); returns them as the server made them. */
    suspend fun create(propertyId: String, fields: List<FieldCfg>): List<FieldCfg>

    /** Sends what differs between [before] and [after] (same field, [FieldCfg.id]); returns the field as the server kept it. */
    suspend fun update(propertyId: String, before: FieldCfg, after: FieldCfg): FieldCfg

    /** Deletes a field (refused with a conflict when it is built in or assets have answered it). */
    suspend fun delete(propertyId: String, id: String)
}

/**
 * Configuration → Building info at the server: the property's buildings, floors and locations, hidden ones too.
 * Every call needs a connection and throws [com.sclera.sclerago.domain.model.OnlineException]; a name already used
 * next to it, or a place that still holds others, is refused with a conflict.
 */
interface ConfigPlacesRepository {
    /** Buildings → floors → locations, in their order. Buildings carry the details kept on this phone ([LocNode.info]). */
    suspend fun load(propertyId: String): List<LocNode>

    /** Adds a building (with details kept on this phone, when given); returns its id. */
    suspend fun addBuilding(propertyId: String, name: String, info: BuildingInfo?): String

    /** Adds a floor to [buildingId]; returns its id. */
    suspend fun addFloor(propertyId: String, buildingId: String, name: String, type: String = "", code: String = ""): String

    /** Adds a location to [floorId]; returns its id. */
    suspend fun addLocation(propertyId: String, floorId: String, location: NewLocation): String

    suspend fun update(propertyId: String, kind: NodeKind, id: String, change: PlaceChange)

    /** Keeps a building's details (type, address, contact, access) on this phone for the session: the server has no place for them. */
    fun saveBuildingInfo(buildingId: String, info: BuildingInfo)

    suspend fun delete(propertyId: String, kind: NodeKind, id: String)
}
