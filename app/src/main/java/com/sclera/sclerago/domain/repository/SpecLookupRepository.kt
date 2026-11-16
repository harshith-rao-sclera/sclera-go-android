package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.SpecLookup

/**
 * Looking up an asset model's published specs at the server (the web's AI suggestions). Every call needs a connection
 * and throws [com.sclera.sclerago.domain.model.OnlineException]: Invalid when the asset has no model to look up.
 */
interface SpecLookupRepository {
    /** The asset's newest look-up, or null when it never had one. */
    suspend fun latest(assetId: String): SpecLookup?

    /** Starts a look-up (or returns the one already running); [typed] are manufacturer / model values by field key. */
    suspend fun start(assetId: String, typed: Map<String, String>): SpecLookup

    /** The look-up [lookupId] of [assetId] as it is now. */
    suspend fun get(assetId: String, lookupId: String): SpecLookup

    /** Tells the server the suggestions were shown. */
    suspend fun seen(lookupId: String)
}
