package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.JacsCatalogCode
import kotlinx.coroutines.flow.StateFlow
import com.sclera.sclerago.domain.model.JacsPage
import com.sclera.sclerago.domain.model.JacsType

/**
 * The JACS catalogue: kept whole on the phone ([catalog]) and asked of the server again when it changes. [typeFor] needs a
 * connection and throws [com.sclera.sclerago.domain.model.OnlineException].
 */
interface JacsRepository {
    /**
     * The whole JACS catalogue as kept on the phone (`GET /jacs-codes/catalog`), so codes are searched and named offline;
     * empty until it has been downloaded once. It is downloaded on its own (at start, sign-in and back online).
     */
    val catalog: StateFlow<List<JacsCatalogCode>>

    /** Code → title, from [catalog]. */
    val titles: StateFlow<Map<String, String>>


    /**
     * Codes matching [query] (every word, any order; all codes when blank), best first, from [cursor] (null: the first page):
     * from the kept [catalog] (also offline), else from the server.
     */
    suspend fun search(query: String, cursor: String?): JacsPage

    /** The current property's type for the JACS [code] as the server finds it (needs a connection); null when it has none. */
    suspend fun typeFor(code: String): JacsType?
}
