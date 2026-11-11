package com.example.sclerago.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetSocketAddress

/** [http] against a local server. */
@RunWith(AndroidJUnit4::class)
class HttpTest {
    private lateinit var server: HttpServer
    private var status = 200
    private var responseBody = "{}"
    private var seenMethod: String? = null
    private var seenCookie: String? = null
    private var seenBody: String? = null

    private val url get() = "http://127.0.0.1:${server.address.port}/sclera/api/session"

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            seenMethod = ex.requestMethod
            seenCookie = ex.requestHeaders.getFirst("Cookie")
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

    @Test
    fun get_sendsHeadersAndParsesJson() {
        responseBody = """{"data":{"sessionId":"s-1"}}"""
        val res = http("GET", url, mapOf("Cookie" to "sessionId=s-1"))
        assertEquals(200, res.code)
        assertEquals("s-1", res.json!!.getJSONObject("data").getString("sessionId"))
        assertEquals("GET", seenMethod)
        assertEquals("sessionId=s-1", seenCookie)
    }

    @Test
    fun post_sendsBody() {
        http("POST", url, emptyMap(), """{"a":1}""")
        assertEquals("POST", seenMethod)
        assertEquals("""{"a":1}""", seenBody)
    }

    @Test
    fun errorStatus_readsErrorBody() {
        status = 500
        responseBody = """{"status":821}"""
        val res = http("GET", url, emptyMap())
        assertEquals(500, res.code)
        assertEquals(821, res.bodyStatus)
    }

    @Test
    fun nonJsonBody_givesNullJson() {
        responseBody = "<html>not json</html>"
        assertNull(http("GET", url, emptyMap()).json)
    }

    @Test
    fun connectionRefused_isNetworkError() {
        val deadUrl = url
        server.stop(0)
        try {
            http("GET", deadUrl, emptyMap())
            fail("expected AuthError.Network")
        } catch (e: AuthError.Network) {
            // expected
        }
    }
}
