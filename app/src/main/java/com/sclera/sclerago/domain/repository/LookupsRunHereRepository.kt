package com.sclera.sclerago.domain.repository

/**
 * The AI look-ups started on this phone whose suggestions haven't been applied yet, one per asset: the AI button's dot
 * shows for those (not for a look-up the server has from elsewhere), also after the asset was left and opened again.
 * Kept in memory only, for the session.
 */
interface LookupsRunHereRepository {
    /** The look-up started here for [assetId] and not applied yet; null when there is none. */
    fun lookupFor(assetId: String): String?

    /** [lookupId] was started here for [assetId] (it replaces one started before). */
    fun keep(assetId: String, lookupId: String)

    /** [assetId]'s suggestions were applied, or its look-up came to nothing. */
    fun forget(assetId: String)
}
