package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.validation.AssetRunRecord
import com.sclera.sclerago.domain.validation.StepIssue
import kotlinx.coroutines.flow.Flow

/** One asset's validation: the runs synced with the property's data, and validating it again at the server. */
interface AssetValidationRepository {
    /** The asset's recent results (newest first) and the problems found on it, again whenever they change. */
    fun runs(assetId: String): Flow<Pair<List<AssetRunRecord>, List<StepIssue>>>

    /**
     * Validates [assetId] again at the server ([fromStart]: every step, else from its first step to look at) and waits
     * for the run to finish; its results come with the next sync. Throws [com.sclera.sclerago.domain.model.OnlineException].
     */
    suspend fun revalidate(assetId: String, fromStart: Boolean)
}
