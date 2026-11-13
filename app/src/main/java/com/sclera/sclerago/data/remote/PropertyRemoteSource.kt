package com.sclera.sclerago.data.remote

import com.sclera.sclerago.data.local.PropertyRecord
import com.sclera.sclerago.data.mapper.propertyPage
import com.sclera.sclerago.domain.model.AuthError
import javax.inject.Inject

/** The organisation's properties on the server. */
interface PropertyRemoteSource {
    /** Every property of [organisationId], all pages. Throws [AuthError] when a page can't be fetched. */
    suspend fun fetchAll(organisationId: String): List<PropertyRecord>
}

/**
 * Reads `GET /api/v1/organisations/{orgId}/properties` page by page: each page's `nextCursor` is sent as
 * the next page's cursor, until a page says `hasMore: false`, comes back empty or repeats a cursor.
 */
class AdcPropertyRemoteSource @Inject constructor(
    private val adc: AdcApi,
    private val auth: AuthorizedCaller,
) : PropertyRemoteSource {

    override suspend fun fetchAll(organisationId: String): List<PropertyRecord> {
        val all = LinkedHashMap<String, PropertyRecord>()
        val seenCursors = mutableSetOf<String>()
        var cursor: String? = null
        repeat(MAX_PAGES) {
            val res = auth.call { token -> adc.properties(token, organisationId, cursor) }
            val json = res.json ?: throw AuthError.Server("properties: HTTP ${res.code} without a JSON body")
            val page = propertyPage(json)
            page.records.forEach { all[it.id] = it }
            val next = page.nextCursor
            if (!page.hasMore || next == null || page.records.isEmpty() || !seenCursors.add(next)) return all.values.toList()
            cursor = next
        }
        return all.values.toList()
    }

    private companion object {
        /** A guard against a server that never stops paging: 16 × 500 = 8,000 properties. */
        const val MAX_PAGES = 500
    }
}
