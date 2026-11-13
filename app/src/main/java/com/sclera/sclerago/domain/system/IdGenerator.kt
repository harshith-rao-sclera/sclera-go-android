package com.sclera.sclerago.domain.system

/** Makes unique ids for new records; injected so ids are predictable in tests. */
fun interface IdGenerator {
    /** A new id starting with [prefix], never the same twice. */
    fun next(prefix: String): String
}
