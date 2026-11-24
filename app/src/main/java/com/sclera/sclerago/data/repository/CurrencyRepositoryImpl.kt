package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.remote.adc.AdcConfigApi
import com.sclera.sclerago.domain.model.Currency
import com.sclera.sclerago.domain.repository.CurrencyRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The currencies from `GET /currencies`. The list is kept once read: it changes only when the API is updated (the
 * server lets it be cached for a day) and holds nothing about the user.
 */
@Singleton
class CurrencyRepositoryImpl @Inject constructor(private val api: AdcConfigApi) : CurrencyRepository {
    @Volatile private var list: List<Currency>? = null

    override suspend fun currencies(): List<Currency> = list ?: api.currencies()
        .mapNotNull { c -> c.code.trim().takeIf { it.isNotEmpty() }?.let { Currency(it, c.name.trim().ifEmpty { it }) } }
        .also { list = it }
}
