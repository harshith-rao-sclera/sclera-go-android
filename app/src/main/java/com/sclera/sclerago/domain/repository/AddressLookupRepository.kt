package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.PlaceName
import com.sclera.sclerago.domain.model.Town

/** The server's countries, states and towns, for a property's address. Every call can throw OnlineException. */
interface AddressLookupRepository {
    /** Every country, by name. */
    suspend fun countries(): List<PlaceName>

    /** The states of [country] (its code or its name), by name. */
    suspend fun states(country: String): List<PlaceName>

    /** Up to [limit] towns of [country] (in [state] when given) matching [query], largest first. */
    suspend fun cities(country: String, state: String?, query: String, limit: Int): List<Town>
}
