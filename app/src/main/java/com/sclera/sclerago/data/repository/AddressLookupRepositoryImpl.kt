package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.remote.adc.AdcApi
import com.sclera.sclerago.data.remote.adc.PlaceNameDto
import com.sclera.sclerago.domain.model.PlaceName
import com.sclera.sclerago.domain.model.Town
import com.sclera.sclerago.domain.repository.AddressLookupRepository
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Countries, states and towns from `GET /countries…`. The country and state lists are kept once read (they don't
 * change), as the web keeps them; towns are asked for as the user types.
 */
@Singleton
class AddressLookupRepositoryImpl @Inject constructor(private val api: AdcApi) : AddressLookupRepository {
    private var countryList: List<PlaceName>? = null
    private val stateLists = ConcurrentHashMap<String, List<PlaceName>>()

    override suspend fun countries(): List<PlaceName> =
        countryList ?: api.countries().mapNotNull { it.toPlace() }.also { countryList = it }

    override suspend fun states(country: String): List<PlaceName> {
        val key = country.trim().lowercase()
        return stateLists[key] ?: api.states(country.trim()).mapNotNull { it.toPlace() }.also { stateLists[key] = it }
    }

    override suspend fun cities(country: String, state: String?, query: String, limit: Int): List<Town> =
        api.cities(country.trim(), state?.trim()?.takeIf { it.isNotEmpty() }, query.trim().takeIf { it.isNotEmpty() }, limit)
            .filter { it.name.isNotBlank() }
            .map { Town(it.name.trim(), it.state?.trim()?.takeIf { s -> s.isNotEmpty() }) }

    private fun PlaceNameDto.toPlace(): PlaceName? =
        name.trim().takeIf { it.isNotEmpty() }?.let { PlaceName(it, code?.trim()?.takeIf { c -> c.isNotEmpty() }) }
}
