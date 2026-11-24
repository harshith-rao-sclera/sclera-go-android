package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.AssetConfig
import com.sclera.sclerago.domain.model.LocNode
import com.sclera.sclerago.domain.model.OnboardingChannel
import com.sclera.sclerago.domain.model.Property
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/** The properties the user can work on, from the server (kept for offline use). Failures throw [com.sclera.sclerago.domain.model.OnlineException]. */
interface PropertyRepository {
    val properties: StateFlow<List<Property>>

    /**
     * The ids of the properties the server lists for the user, each time a fresh list comes from it (at start, on return
     * to the app, on a re-check); never the copy kept on the device. A property the user lost access to (or that was
     * deleted) is missing from it.
     */
    val listed: Flow<Set<String>> get() = emptyFlow()

    /** Loads the properties again; the list shown before stays when it fails. */
    suspend fun refresh()

    /** Creates [property] on the server; returns it with the id it was given. Needs a connection. */
    suspend fun add(property: Property): Property

    /** The property [id] as the server has it now. Needs a connection. */
    suspend fun get(id: String): Property

    /** Saves [property]'s details on the server; returns them as saved. Needs a connection. */
    suspend fun update(property: Property): Property

    /** Deletes the property [id] on the server. Needs a connection. */
    suspend fun delete(id: String)
}

/** The building → floor → location tree of the downloaded property, for the location picker (Configuration reads the server). */
interface LocationRepository {
    /** The places field workers can pick (hidden buildings, floors and locations left out). */
    val tree: StateFlow<List<LocNode>>
}

/** The asset fields of the downloaded property, from the offline data (Configuration changes them at the server). */
interface AssetConfigRepository {
    val config: StateFlow<AssetConfig>
}

/** Property Settings: the ways of adding assets each property has switched off (the server keeps them). */
interface OnboardingRepository {
    /** Property id → channels switched off there. */
    val disabled: StateFlow<Map<String, Set<OnboardingChannel>>>

    /** Throws [com.sclera.sclerago.domain.model.OnlineException]; needs a connection. */
    suspend fun setEnabled(prop: String, channel: OnboardingChannel, enabled: Boolean)
}
