package com.sclera.sclerago.domain.system

/** The current time; injected so time-dependent rules are deterministic in tests. */
fun interface TimeProvider {
    fun nowMillis(): Long
}
