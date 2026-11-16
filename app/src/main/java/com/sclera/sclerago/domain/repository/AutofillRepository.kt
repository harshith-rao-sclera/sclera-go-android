package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.Asset

/** Suggested values for an asset's empty fields, looked up online (a search that can take a while). */
interface AutofillRepository {
    /**
     * Field key → suggested value, only for fields that are still empty in [values]; never overwrites.
     * Throws [com.sclera.sclerago.domain.model.OnlineException]: Offline without a connection, NotFound when the
     * asset is not on the server yet, Unavailable when the lookup is not set up there. Cancelling stops the wait.
     */
    suspend fun suggest(asset: Asset, values: Map<String, String>): Map<String, String>
}
