package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.mapper.toAssetResults
import com.sclera.sclerago.data.mapper.validationJobBody
import com.sclera.sclerago.data.offline.ValidationOfflineSource
import com.sclera.sclerago.data.remote.adc.AdcJobsApi
import com.sclera.sclerago.data.remote.adc.pollUntil
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.repository.OnlineValidation
import com.sclera.sclerago.domain.repository.OnlineValidationRun
import javax.inject.Inject

/**
 * Starts the run at the server (`POST /validation-jobs`), polls the job until it has finished, then takes its
 * results from the offline database when sync has already brought them, or else from the API.
 */
class OnlineValidationImpl @Inject constructor(
    private val api: AdcJobsApi,
    private val offline: ValidationOfflineSource,
) : OnlineValidation {

    override suspend fun run(propertyId: String, name: String, ruleIds: List<String>, assetIds: List<String>): OnlineValidationRun {
        val body = validationJobBody(name, ruleIds, assetIds) ?: throw OnlineException(OnlineError.Invalid)
        val started = api.startValidation(propertyId, body)
        val job = pollUntil(LIMIT_MS, { it.status == COMPLETED || it.status == FAILED }) { api.validation(propertyId, started.id) }
        if (job.status != COMPLETED) throw OnlineException(OnlineError.Server)
        val stored = offline.results(job.id)
        val results = stored.ifEmpty { api.validationResults(propertyId, job.id).toAssetResults(api.validationIssues(propertyId, job.id)) }
        return OnlineValidationRun(job.id, results)
    }

    private companion object {
        const val COMPLETED = "completed"
        const val FAILED = "failed"
        const val LIMIT_MS = 600_000L
    }
}
