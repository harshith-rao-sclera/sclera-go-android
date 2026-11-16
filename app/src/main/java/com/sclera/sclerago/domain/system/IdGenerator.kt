package com.sclera.sclerago.domain.system

/** Makes unique ids for new records; injected so ids are predictable in tests. */
fun interface IdGenerator {
    /** A new id starting with [prefix] ("prefix-<id>"), or the bare id when [prefix] is empty; never the same twice. */
    fun next(prefix: String): String
}

/** A new bare id (a time-ordered UUID in the app), for records stored with text primary keys. */
fun IdGenerator.next(): String = next("")
