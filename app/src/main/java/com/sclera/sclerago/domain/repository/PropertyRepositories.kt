package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.AssetConfig
import com.sclera.sclerago.domain.model.LocNode
import com.sclera.sclerago.domain.model.OnboardingChannel
import com.sclera.sclerago.domain.model.Property
import kotlinx.coroutines.flow.StateFlow

/** The properties the user can work on: the organisation's (from the server) and any added on this device. */
interface PropertyRepository {
    val properties: StateFlow<List<Property>>

    /** Saves a property added on this device; it stays unsynced until it reaches the server. */
    fun add(property: Property)

    /**
     * Replaces the saved server properties with [organisationId]'s current list (every page).
     * Throws [com.sclera.sclerago.domain.model.AuthError] when it can't; the saved list is then kept.
     */
    suspend fun refresh(organisationId: String)
}

/** Each property's building → floor → location tree (Configuration → Building info). */
interface LocationRepository {
    /** Trees edited so far, per property id. */
    val trees: StateFlow<Map<String, List<LocNode>>>

    /** [prop]'s tree: the edited one, else the one it came with. */
    fun tree(prop: String): List<LocNode>

    /** Replaces [prop]'s tree with [f] applied to it. */
    fun edit(prop: String, f: (List<LocNode>) -> List<LocNode>)

    /** A new, unused node id starting with [prefix] ("b", "f" or "l"). */
    fun newNodeId(prefix: String): String
}

/** Configuration → Asset fields, photos and geofencing. */
interface AssetConfigRepository {
    val config: StateFlow<AssetConfig>

    fun update(f: (AssetConfig) -> AssetConfig)
}

/** Property Settings: the ways of adding assets each property has switched off. */
interface OnboardingRepository {
    /** Property id → channels switched off there. */
    val disabled: StateFlow<Map<String, Set<OnboardingChannel>>>

    fun setEnabled(prop: String, channel: OnboardingChannel, enabled: Boolean)
}
