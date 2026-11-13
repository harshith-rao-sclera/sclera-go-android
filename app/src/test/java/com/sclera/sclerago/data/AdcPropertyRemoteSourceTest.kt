package com.sclera.sclerago.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sclera.sclerago.data.remote.AdcApi
import com.sclera.sclerago.data.remote.AdcPropertyRemoteSource
import com.sclera.sclerago.data.remote.ApiEndpoint
import com.sclera.sclerago.data.remote.AuthorizedCaller
import com.sclera.sclerago.domain.model.AuthError
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetSocketAddress

/** The ADC client and the properties paging loop, against a local server. */
@RunWith(AndroidJUnit4::class)
class AdcPropertyRemoteSourceTest {
    private lateinit var server: HttpServer
    private val seen = mutableListOf<String>()
    private val seenAuth = mutableListOf<String?>()

    /** Cursor (null = first page) → response body. */
    private var pages: Map<String?, String> = emptyMap()

    private val api get() = AdcApi(AdcApi.service("http://127.0.0.1:${server.address.port}/"))
    private val caller = AuthorizedCaller { request -> request("tok") }

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            val query = ex.requestURI.rawQuery.orEmpty()
            seen += ex.requestURI.rawPath + "?" + query
            seenAuth += ex.requestHeaders.getFirst("Authorization")
            val cursor = query.split('&').firstOrNull { it.startsWith("cursor=") }?.substringAfter('=')
            val body = (if (ex.requestURI.rawPath.endsWith("/me")) """{"organisationId":"85161001"}""" else pages[cursor] ?: "not json").toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
            ex.close()
        }
        server.start()
    }

    @After
    fun stop() = server.stop(0)

    private fun page(vararg ids: String, next: String?, hasMore: Boolean) =
        """{"data":[${ids.joinToString(",") { """{"id":"$it","name":"$it"}""" }}],"nextCursor":${next?.let { "\"$it\"" }},"hasMore":$hasMore}"""

    @Test
    fun me_sendsTheBearerToken() = runBlocking {
        val res = api.me("tok")
        assertEquals("85161001", res.json!!.getString("organisationId"))
        assertEquals("/api/v1/me?", seen.single())
        assertEquals("Bearer tok", seenAuth.single())
    }

    @Test
    fun fetchAll_followsTheCursorUntilHasMoreIsFalse() = runBlocking {
        pages = mapOf(
            null to page("a", "b", next = "c1", hasMore = true),
            "c1" to page("c", next = "c2", hasMore = true),
            "c2" to page("d", next = null, hasMore = false),
        )
        val all = AdcPropertyRemoteSource(api, caller).fetchAll("85161001")
        assertEquals(listOf("a", "b", "c", "d"), all.map { it.id })
        assertTrue(all.all { it.isSynced })
        assertEquals(
            listOf(
                "/api/v1/organisations/85161001/properties?limit=${ApiEndpoint.PROPERTIES_PAGE_SIZE}",
                "/api/v1/organisations/85161001/properties?limit=16&cursor=c1",
                "/api/v1/organisations/85161001/properties?limit=16&cursor=c2",
            ),
            seen,
        )
        assertTrue(seenAuth.all { it == "Bearer tok" })
    }

    @Test
    fun fetchAll_stopsOnARepeatedCursorOrAnEmptyPage() = runBlocking {
        pages = mapOf(null to page("a", next = "c1", hasMore = true), "c1" to page("b", next = "c1", hasMore = true))
        assertEquals(listOf("a", "b"), AdcPropertyRemoteSource(api, caller).fetchAll("o").map { it.id })

        seen.clear()
        pages = mapOf(null to page(next = "c1", hasMore = true))
        assertTrue(AdcPropertyRemoteSource(api, caller).fetchAll("o").isEmpty())
        assertEquals(1, seen.size)
    }

    @Test
    fun fetchAll_keepsOneRowPerId() = runBlocking {
        pages = mapOf(null to page("a", next = "c1", hasMore = true), "c1" to page("a", "b", next = null, hasMore = false))
        assertEquals(listOf("a", "b"), AdcPropertyRemoteSource(api, caller).fetchAll("o").map { it.id })
    }

    @Test
    fun fetchAll_withoutJson_isAServerError() = runBlocking {
        try {
            AdcPropertyRemoteSource(api, caller).fetchAll("o")
            fail("expected AuthError.Server")
        } catch (_: AuthError.Server) {
            // expected
        }
    }

    @Test
    fun fetchAll_passesOnTheCallersErrors() = runBlocking {
        val failing = AuthorizedCaller { throw AuthError.SessionExpired() }
        try {
            AdcPropertyRemoteSource(api, failing).fetchAll("o")
            fail("expected AuthError.SessionExpired")
        } catch (_: AuthError.SessionExpired) {
            // expected
        }
    }
}
