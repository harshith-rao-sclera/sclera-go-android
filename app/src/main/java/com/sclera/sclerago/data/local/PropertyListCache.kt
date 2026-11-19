package com.sclera.sclerago.data.local

import com.sclera.sclerago.data.remote.adc.PropertyDto

/**
 * Where the organisation's property list is kept on the device, to show first and offline ([PropertyTable]:
 * global_config.db's `properties` table). Blocking: call off the main thread.
 */
interface PropertyListCache {
    /** The properties kept, in the server's order. */
    fun read(): List<PropertyDto>

    /** Replaces the properties kept with [list], for [organisationId]; those in [synced] (downloaded here) are marked synced. */
    fun replace(list: List<PropertyDto>, organisationId: String?, synced: Set<String>)

    /** Marks the properties in [synced] as synced (downloaded to this device) and every other one as not. */
    fun markSynced(synced: Set<String>)

    /** Forgets every property (sign-out). */
    fun clear()
}
