package com.sclera.sclerago.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sclera.sclerago.data.remote.AuthApi
import com.sclera.sclerago.data.remote.AuthConfig
import com.sclera.sclerago.domain.model.AuthError
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetSocketAddress

/** [AuthApi] over Retrofit: against a local server, and the exact URLs it sends to Sclera. */
@RunWith(AndroidJUnit4::class)
class AuthApiTest {
    private lateinit var server: HttpServer
    private var status = 200
    private var responseBody = "{}"
    private var seenMethod: String? = null
    private var seenPath: String? = null
    private var seenHeaders: Map<String, String?> = emptyMap()
    private var seenBody: String? = null

    private val baseUrl get() = "http://127.0.0.1:${server.address.port}/sclera/api/"
    private val api get() = AuthApi(AuthApi.service(baseUrl))

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            seenMethod = ex.requestMethod
            seenPath = ex.requestURI.rawPath + (ex.requestURI.rawQuery?.let { "?$it" } ?: "")
            seenHeaders = listOf("Cookie", "Authorization", "Content-Type", "Accept")
                .associateWith { ex.requestHeaders.getFirst(it) }
            seenBody = ex.requestBody.readBytes().decodeToString()
            val bytes = responseBody.toByteArray()
            ex.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) ex.responseBody.use { it.write(bytes) }
            ex.close()
        }
        server.start()
    }

    @After
    fun stop() = server.stop(0)

    // ---- Round trips against a local server

    @Test
    fun session_sendsCookieAndParsesJson() = runBlocking {
        responseBody = """{"data":{"sessionId":"s-1"}}"""
        val res = api.session("sessionId=s-1")
        assertEquals(200, res.code)
        assertEquals("s-1", res.json!!.getJSONObject("data").getString("sessionId"))
        assertEquals("GET", seenMethod)
        assertEquals("/sclera/api/session", seenPath)
        assertEquals("sessionId=s-1", seenHeaders["Cookie"])
        assertEquals("application/json", seenHeaders["Accept"])
    }

    @Test
    fun token_isPostWithCookie() = runBlocking {
        api.token("sessionId=s-1")
        assertEquals("POST", seenMethod)
        assertEquals("/sclera/api/token", seenPath)
        assertEquals("sessionId=s-1", seenHeaders["Cookie"])
        assertEquals("", seenBody)
    }

    @Test
    fun authenticated_post_sendsBearerAndJsonBody() = runBlocking {
        api.authenticated("POST", baseUrl + "things?loggedInUser=a%40b.com", "tok", """{"a":1}""")
        assertEquals("POST", seenMethod)
        assertEquals("/sclera/api/things?loggedInUser=a%40b.com", seenPath)
        assertEquals("Bearer tok", seenHeaders["Authorization"])
        assertEquals("application/json", seenHeaders["Content-Type"])
        assertEquals("""{"a":1}""", seenBody)
    }

    @Test
    fun errorStatus_readsErrorBody() = runBlocking {
        status = 500
        responseBody = """{"status":821}"""
        val res = api.session("c")
        assertEquals(500, res.code)
        assertEquals(821, res.bodyStatus)
    }

    @Test
    fun nonJsonBody_givesNullJson() = runBlocking {
        responseBody = "<html>not json</html>"
        assertNull(api.session("c").json)
    }

    @Test
    fun connectionRefused_isNetworkError() = runBlocking {
        val dead = api
        server.stop(0)
        try {
            dead.session("c")
            fail("expected AuthError.Network")
        } catch (e: AuthError.Network) {
            // expected
        }
    }

    // ---- The URLs sent to Sclera (same as the web app / Sclera app)

    /** Requests [block] makes against the real base URL, captured before they leave the device. */
    private fun sent(block: suspend AuthApi.() -> Unit): Request {
        var captured: Request? = null
        val client = AuthApi.okHttpClient().newBuilder().addInterceptor { chain ->
            captured = chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("{}".toResponseBody()).build()
        }.build()
        runBlocking { AuthApi(AuthApi.service(client = client)).block() }
        return captured!!
    }

    @Test
    fun userInfoUrl_matchesScleraApp() {
        val req = sent { userInfo("masteruser.brijesh@sclera.com", "tok", "sessionId=s") }
        assertEquals("https://qa-accounts.sclera.com/sclera/api/userInfo?loggedInUser=masteruser.brijesh%40sclera.com", req.url.toString())
        assertEquals("Bearer tok", req.header("Authorization"))
        assertEquals("sessionId=s", req.header("Cookie"))
    }

    @Test
    fun logoutUrl_encodesEmail() {
        val req = sent { logout("a@b.com", mapOf("Cookie" to "sessionId=s")) }
        assertEquals("${AuthConfig.SCLERA_API}/user/a%40b.com/logoutUserByEmail?loggedInUser=a%40b.com", req.url.toString())
        assertEquals("POST", req.method)
    }

    @Test
    fun sessionAndTokenUrls() {
        assertEquals("${AuthConfig.SCLERA_API}/session", sent { session("c") }.url.toString())
        assertEquals("${AuthConfig.SCLERA_API}/token", sent { token("c") }.url.toString())
    }
}
