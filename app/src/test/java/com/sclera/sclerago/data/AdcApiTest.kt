package com.sclera.sclerago.data

import com.sclera.sclerago.data.remote.adc.AccessTokenProvider
import com.sclera.sclerago.data.remote.adc.AdcApi
import com.sclera.sclerago.data.remote.adc.AdcApiService
import com.sclera.sclerago.data.remote.adc.OnlineCall
import com.sclera.sclerago.data.remote.adc.forAdc
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.testutil.FakeNetworkMonitor
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import java.net.InetSocketAddress

/** The ADC client (Retrofit service, token handling, [OnlineCall] error mapping, properties paging) against a local server. */
class AdcApiTest {
    private lateinit var server: HttpServer
    private val seen = mutableListOf<String>()
    private val seenAuth = mutableListOf<String?>()
    private val network = FakeNetworkMonitor()

    /** Cursor (null = first page) → response body. */
    private var pages: Map<String?, String> = emptyMap()

    /** A status (and Retry-After) to answer every call with instead of the pages; null answers normally. */
    private var status: Int? = null
    private var retryAfter: String? = null

    /** Token "old" is refused with a 401 when set, so the client must fetch a new one. */
    private var refuseOld = false

    private val tokens = object : AccessTokenProvider {
        val asked = mutableListOf<String?>()
        override suspend fun accessToken(rejected: String?): String {
            asked += rejected
            return if (rejected == null) "old" else "new"
        }
    }

    private fun api(): AdcApi {
        val client = OkHttpClient().forAdc(tokens)
        val service = Retrofit.Builder().baseUrl("http://127.0.0.1:${server.address.port}/").client(client).build()
            .create(AdcApiService::class.java)
        return AdcApi(service, OnlineCall(network, Dispatchers.Unconfined))
    }

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            val query = ex.requestURI.rawQuery.orEmpty()
            val auth = ex.requestHeaders.getFirst("Authorization")
            seen += ex.requestURI.rawPath + "?" + query
            seenAuth += auth
            val cursor = query.split('&').firstOrNull { it.startsWith("cursor=") }?.substringAfter('=')
            val code = when {
                refuseOld && auth == "Bearer old" -> 401
                else -> status ?: 200
            }
            retryAfter?.let { ex.responseHeaders.add("Retry-After", it) }
            val body = (if (code == 200) pages[cursor] ?: "not json" else """{"type":"x"}""").toByteArray()
            ex.sendResponseHeaders(code, body.size.toLong())
            ex.responseBody.use { it.write(body) }
            ex.close()
        }
        server.start()
    }

    @After
    fun stop() = server.stop(0)

    private fun page(vararg ids: String, next: String?, hasMore: Boolean) =
        """{"data":[${ids.joinToString(",") { """{"id":"$it","name":"$it"}""" }}],"nextCursor":${next?.let { "\"$it\"" }},"hasMore":$hasMore}"""

    private fun assertFails(expected: OnlineError, block: suspend () -> Unit) = runBlocking {
        try {
            block()
            fail("expected $expected")
        } catch (e: OnlineException) {
            assertEquals(expected, e.error)
        }
    }

    @Test
    fun properties_followTheCursorUntilHasMoreIsFalse_andSendTheBearerToken() = runBlocking {
        pages = mapOf(
            null to page("a", "b", next = "c2", hasMore = true),
            "c2" to page("c", next = "c3", hasMore = true),
            "c3" to page("d", next = null, hasMore = false),
        )
        val list = api().properties("org-1")
        assertEquals(listOf("a", "b", "c", "d"), list.map { it.id })
        assertEquals(3, seen.size)
        assertTrue(seen.all { it.startsWith("/organisations/org-1/properties?") })
        assertTrue(seen.first().contains("limit=200"))
        assertTrue(seenAuth.all { it == "Bearer old" }) // the token goes in the header, never in the URL
        assertTrue(seen.none { "old" in it })
    }

    @Test
    fun properties_stopWithoutANextCursor_evenIfHasMoreSaysOtherwise() = runBlocking {
        pages = mapOf(null to page("a", next = null, hasMore = true))
        assertEquals(listOf("a"), api().properties("org-1").map { it.id })
        assertEquals(1, seen.size)
    }

    @Test
    fun aRefusedToken_isRenewedOnce_andTheCallRetried() = runBlocking {
        refuseOld = true
        pages = mapOf(null to page("a", next = null, hasMore = false))
        assertEquals(listOf("a"), api().properties("org-1").map { it.id })
        assertEquals(listOf("Bearer old", "Bearer new"), seenAuth)
        assertEquals(listOf(null, "old"), tokens.asked)
    }

    @Test
    fun aBodyThatIsNotJson_isAServerError() = assertFails(OnlineError.Server) {
        pages = mapOf(null to "not json")
        api().properties("org-1")
    }

    @Test
    fun httpFailures_mapToTheirErrors() {
        status = 403
        assertFails(OnlineError.Forbidden) { api().properties("org-1") }
        status = 404
        assertFails(OnlineError.NotFound) { api().property("p1") }
        status = 422
        assertFails(OnlineError.Invalid) { api().property("p1") }
        status = 500
        assertFails(OnlineError.Server) { api().property("p1") }
        status = 503
        retryAfter = "30"
        assertFails(OnlineError.Unavailable(30)) { api().property("p1") }
    }

    @Test
    fun offline_noRequestIsSent() {
        network.online = false
        assertFails(OnlineError.Offline) { api().properties("org-1") }
        assertTrue(seen.isEmpty())
    }

    @Test
    fun aClosedServer_isOffline() {
        val api = api()
        server.stop(0)
        assertFails(OnlineError.Offline) { api.properties("org-1") }
    }
}
