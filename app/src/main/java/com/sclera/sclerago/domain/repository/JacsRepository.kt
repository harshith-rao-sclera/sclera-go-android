package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.JacsPage
import com.sclera.sclerago.domain.model.JacsType

/**
 * The JACS catalog at the server. Every call needs a connection and throws
 * [com.sclera.sclerago.domain.model.OnlineException].
 */
interface JacsRepository {
    /** Codes matching [query] (every word, any order; all codes when blank), best first, from [cursor] (null: the first page). */
    suspend fun search(query: String, cursor: String?): JacsPage

    /** The current property's type for the JACS [code]; null when it has none. */
    suspend fun typeFor(code: String): JacsType?
}
