package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.AssetType

/** Suggested values for an asset's empty fields (a service that may take a moment). */
interface AutofillRepository {
    /** Field key → suggested value, only for fields that are still empty in [values]; never overwrites. */
    suspend fun suggest(type: AssetType, values: Map<String, String>): Map<String, String>
}
