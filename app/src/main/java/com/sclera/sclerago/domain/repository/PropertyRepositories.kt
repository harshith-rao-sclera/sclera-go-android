package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.AssetConfig
import com.sclera.sclerago.domain.model.BuildingInfo
import com.sclera.sclerago.domain.model.LocNode
import com.sclera.sclerago.domain.model.NewLocation
import com.sclera.sclerago.domain.model.OnboardingChannel
import com.sclera.sclerago.domain.model.PlaceRow
import com.sclera.sclerago.domain.model.Property
import kotlinx.coroutines.flow.StateFlow

/** The properties the user can work on, from the server (kept for offline use). Failures throw [com.sclera.sclerago.domain.model.OnlineException]. */
interface PropertyRepository {
    val properties: StateFlow<List<Property>>

    /** Loads the properties again; the list shown before stays when it fails. */
    suspend fun refresh()

    /** Creates [property] on the server; returns it with the id it was given. Needs a connection. */
    suspend fun add(property: Property): Property
}

/** The building → floor → location tree of the downloaded property (Configuration → Building info). Writes throw [com.sclera.sclerago.domain.model.DataException]. */
interface LocationRepository {
    val tree: StateFlow<List<LocNode>>

    /** Adds a building, with its details when given; returns its id. */
    suspend fun addBuilding(name: String, info: BuildingInfo?): String

    /** Adds a floor to a building; returns its id. */
    suspend fun addFloor(buildingId: String, name: String): String

    suspend fun saveBuilding(id: String, name: String, info: BuildingInfo)

    /** The building's floors in their new order, with names, types and codes; floors left out are removed. */
    suspend fun saveFloors(buildingId: String, floors: List<LocNode>)

    suspend fun renameNode(id: String, name: String)

    suspend fun updateLocation(id: String, name: String, type: String, code: String)

    /** Removes buildings, floors or locations (a node with floors, locations or assets left in it is refused). */
    suspend fun deleteNodes(ids: Set<String>)

    /** Adds [locations] to a floor; returns how many. */
    suspend fun addLocations(floorId: String, locations: List<NewLocation>): Int

    /** Adds the buildings, floors and locations of [rows] that are not there yet (matched by name); returns how many were new. */
    suspend fun importRows(rows: List<PlaceRow>): Int
}

/** Configuration → Asset fields, photos and geofencing. Only the fields are in the offline data. */
interface AssetConfigRepository {
    val config: StateFlow<AssetConfig>

    /** Applies [f] to the configuration and keeps the result. Throws [com.sclera.sclerago.domain.model.DataException]. */
    suspend fun update(f: (AssetConfig) -> AssetConfig)
}

/** Property Settings: the ways of adding assets each property has switched off (the server keeps them). */
interface OnboardingRepository {
    /** Property id → channels switched off there. */
    val disabled: StateFlow<Map<String, Set<OnboardingChannel>>>

    /** Throws [com.sclera.sclerago.domain.model.OnlineException]; needs a connection. */
    suspend fun setEnabled(prop: String, channel: OnboardingChannel, enabled: Boolean)
}
