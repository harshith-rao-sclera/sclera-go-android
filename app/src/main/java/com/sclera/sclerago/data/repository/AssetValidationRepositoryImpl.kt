package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.offline.ValidationOfflineSource
import com.sclera.sclerago.data.remote.adc.AdcJobsApi
import com.sclera.sclerago.data.remote.adc.pollUntil
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.repository.AssetValidationRepository
import com.sclera.sclerago.domain.repository.PropertyDownloadRepository
import com.sclera.sclerago.domain.validation.AssetRunRecord
import com.sclera.sclerago.domain.validation.StepIssue
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Reads an asset's validation runs from the offline database (`validation_results`, `validation_jobs`,
 * `validation_issues`); validating again goes to the server (`POST /assets/{id}/revalidate`) for the current property.
 */
class AssetValidationRepositoryImpl @Inject constructor(
    private val offline: ValidationOfflineSource,
    private val api: AdcJobsApi,
    private val downloads: PropertyDownloadRepository,
) : AssetValidationRepository {
    override fun runs(assetId: String): Flow<Pair<List<AssetRunRecord>, List<StepIssue>>> = offline.assetRunsFlow(assetId)

    override suspend fun revalidate(assetId: String, fromStart: Boolean) {
        val property = downloads.downloads.value.currentId ?: throw OnlineException(OnlineError.NotFound)
        val started = api.revalidate(property, assetId, fromStart)
        val job = pollUntil(LIMIT_MS, { it.status == COMPLETED || it.status == FAILED }) { api.validation(property, started.id) }
        if (job.status != COMPLETED) throw OnlineException(OnlineError.Server)
    }

    private companion object {
        const val COMPLETED = "completed"
        const val FAILED = "failed"
        const val LIMIT_MS = 180_000L
    }
}
