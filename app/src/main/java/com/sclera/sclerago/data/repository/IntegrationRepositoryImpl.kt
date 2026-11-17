package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.mapper.parseDbTime
import com.sclera.sclerago.data.remote.adc.AdcIntegrationApi
import com.sclera.sclerago.data.remote.adc.IntegrationDto
import com.sclera.sclerago.domain.model.VdmsChoice
import com.sclera.sclerago.domain.model.VdmsLink
import com.sclera.sclerago.domain.repository.IntegrationRepository
import javax.inject.Inject

/** The property's VDMS link at the server; online only, as the web's Integration page. */
class IntegrationRepositoryImpl @Inject constructor(private val api: AdcIntegrationApi) : IntegrationRepository {
    override suspend fun link(propertyId: String): VdmsLink? = api.integration(propertyId).firstOrNull()?.toDomain()

    override suspend fun available(propertyId: String, key: String?): List<VdmsChoice> =
        api.available(propertyId, key?.trim()?.take(MAX_KEY)?.takeIf { it.isNotEmpty() })
            .filter { it.vdmsId.isNotBlank() }
            .map { VdmsChoice(it.vdmsId.take(MAX_ID), it.propertyName?.takeIf { n -> n.isNotBlank() && n != "null" }?.take(MAX_ID)) }

    override suspend fun linkVdms(propertyId: String, vdmsId: String): VdmsLink = api.link(propertyId, vdmsId.trim()).toDomain()

    private fun IntegrationDto.toDomain() = VdmsLink(id, vdmsId.take(MAX_ID), organisationId.take(MAX_ID), parseDbTime(createdAt))

    private companion object {
        const val MAX_KEY = 200
        const val MAX_ID = 255
    }
}
