package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.Currency

/** The currencies a currency field can be in, from the server. Every call can throw OnlineException. */
interface CurrencyRepository {
    /** Every currency, by name. */
    suspend fun currencies(): List<Currency>
}
