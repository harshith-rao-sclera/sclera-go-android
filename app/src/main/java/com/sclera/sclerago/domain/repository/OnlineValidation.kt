package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.validation.AssetResult

/** A validation run the server did: its id there and how each asset did. */
data class OnlineValidationRun(val jobId: String, val results: List<AssetResult>)

/** Runs validation on the server (the full run, with the checks that need other assets or the web). */
interface OnlineValidation {
    /**
     * Runs the rules [ruleIds] on the assets [assetIds] of [propertyId], waits for the run to finish and returns
     * what it found. Throws [com.sclera.sclerago.domain.model.OnlineException]: Offline without a connection,
     * Invalid when none of the rules or assets are known to the server. Cancelling stops the wait.
     */
    suspend fun run(propertyId: String, name: String, ruleIds: List<String>, assetIds: List<String>): OnlineValidationRun
}
