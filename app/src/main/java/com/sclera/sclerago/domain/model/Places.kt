package com.sclera.sclerago.domain.model

// Countries, states and towns offered while typing a property's address (the server's lists, as the web uses them).

/** A country or a state: its [name] and, when the server gives one, its [code] (a country's ISO code). */
data class PlaceName(val name: String, val code: String? = null)

/** A town and the [state] it is in. */
data class Town(val name: String, val state: String?)

/** A suggestion under an address field: the [name] to fill in, a [detail] line, and what picking it also sets. */
data class AddressSuggestion(
    val name: String,
    val detail: String? = null,
    /** A country's ISO code (sets the region). */
    val code: String? = null,
    /** A town's state (fills the State field). */
    val state: String? = null,
)
