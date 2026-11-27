package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.RunningLookup
import com.sclera.sclerago.domain.model.SpecLookup

/**
 * The AI look-ups started on this phone whose suggestions haven't been applied yet, one per asset: the AI button's dot
 * shows for those (not for a look-up the server has from elsewhere), also after the asset was left and opened again.
 * A look-up the server keeps (a job, with an id) is read back from it; the phone's look-up from photos isn't kept
 * by the server (no id), so what it found is held here. Kept in memory only, for the session.
 */
interface LookupsRunHereRepository {
    /**
     * Runs [lookUp] for [assetId] on past the screen that started it (leaving the asset doesn't stop it), and keeps what
     * it gives as [keep] does. Waiting for it here can be given up; the look-up carries on. [info] is how it is shown
     * while it runs ([running]).
     */
    suspend fun run(assetId: String, info: RunningLookup, lookUp: suspend () -> SpecLookup): SpecLookup

    /** The look-up still running here for [assetId], as it started; null when none is running. */
    fun running(assetId: String): RunningLookup?

    /** Waits for the look-up still running for [assetId] and gives what it found; null when none is running. */
    suspend fun awaitRunning(assetId: String): SpecLookup?

    /** The look-up started here for [assetId] and not applied yet; null when there is none. */
    fun lookupFor(assetId: String): SpecLookup?

    /** [lookup] was started here for [assetId] (it replaces one started before). */
    fun keep(assetId: String, lookup: SpecLookup)

    /** [assetId]'s suggestions were applied, or its look-up came to nothing. */
    fun forget(assetId: String)
}
