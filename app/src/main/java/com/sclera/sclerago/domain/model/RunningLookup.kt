package com.sclera.sclerago.domain.model

/**
 * An AI look-up still running on this phone, as its searching sheet shows it: when it started (so its progress and time
 * carry on, not start again, when the asset is opened again), what it looks up, and whether it reads photos first.
 */
data class RunningLookup(
    val startedAt: Long,
    val fromPhoto: Boolean,
    val make: String = "",
    val model: String = "",
)
