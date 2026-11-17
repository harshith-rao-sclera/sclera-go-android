package com.sclera.sclerago.domain.usecase.property

import com.sclera.sclerago.domain.model.AddressSuggestion
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.PlaceName
import com.sclera.sclerago.domain.repository.AddressLookupRepository
import java.text.Normalizer
import javax.inject.Inject

// Suggestions under a property's City, State and Country fields, as the web's AddressFields offers them on the new API
// (without Google): the server's lists, names starting with what was typed first.

/** No case, no accents: "sao" finds "São Paulo". */
private fun fold(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").trim().lowercase()

/** Names starting with [text] first, then names containing it. */
private fun startsFirst(items: List<PlaceName>, text: String): List<AddressSuggestion> {
    val wanted = fold(text)
    val (starts, rest) = items.partition { fold(it.name).startsWith(wanted) }
    return (starts + rest.filter { fold(it.name).contains(wanted) }).map { AddressSuggestion(it.name, code = it.code) }
}

/** Every country matching [text] (all of them before anything is typed). Throws [OnlineException]. */
class SuggestCountriesUseCase @Inject constructor(private val repo: AddressLookupRepository) {
    suspend operator fun invoke(text: String): List<AddressSuggestion> = startsFirst(repo.countries(), text)
}

/** The states of [country] matching [text]; none until a country is given. Throws [OnlineException]. */
class SuggestStatesUseCase @Inject constructor(private val repo: AddressLookupRepository) {
    suspend operator fun invoke(country: String, text: String): List<AddressSuggestion> =
        if (country.isBlank()) emptyList() else startsFirst(repo.states(country), text)
}

/**
 * Up to [LIMIT] towns of [country] (in [state] when given) matching [text], each with its state (shown when it differs
 * from the one chosen). A state the server doesn't know: the whole country's towns instead. None until a country is
 * given. Throws [OnlineException].
 */
class SuggestCitiesUseCase @Inject constructor(private val repo: AddressLookupRepository) {
    suspend operator fun invoke(country: String, state: String, text: String): List<AddressSuggestion> {
        if (country.isBlank()) return emptyList()
        val towns = try {
            repo.cities(country, state.ifBlank { null }, text, LIMIT)
        } catch (e: OnlineException) {
            if (state.isBlank()) throw e
            repo.cities(country, null, text, LIMIT)
        }
        return towns.map { t ->
            AddressSuggestion(t.name, detail = t.state?.takeUnless { it.equals(state.trim(), ignoreCase = true) }, state = t.state)
        }
    }

    companion object {
        const val LIMIT = 50
    }
}

/** The address suggestion use cases, for injecting as one. */
class AddressUseCases @Inject constructor(
    val countries: SuggestCountriesUseCase,
    val states: SuggestStatesUseCase,
    val cities: SuggestCitiesUseCase,
)
