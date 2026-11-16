package com.sclera.sclerago.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sclera.sclerago.data.local.EncryptedCache
import com.sclera.sclerago.data.offline.PropertyOfflineSource
import com.sclera.sclerago.data.online.OnlineSession
import com.sclera.sclerago.data.online.PropertyStore
import com.sclera.sclerago.data.remote.adc.AccessTokenProvider
import com.sclera.sclerago.data.remote.adc.AdcApi
import com.sclera.sclerago.data.remote.adc.AdcApiService
import com.sclera.sclerago.data.remote.adc.AdcJson
import com.sclera.sclerago.data.remote.adc.OnlineCall
import com.sclera.sclerago.data.remote.adc.PropertyDto
import com.sclera.sclerago.data.remote.adc.forAdc
import com.sclera.sclerago.data.sync.AdcDatabase
import com.sclera.sclerago.data.sync.SyncCredentials
import com.sclera.sclerago.data.sync.SyncCredentialsProvider
import com.sclera.sclerago.data.sync.SyncManagerBridge
import com.sclera.sclerago.data.sync.TableInvalidations
import com.sclera.sclerago.data.sync.TestDatabase
import com.sclera.sclerago.domain.model.OnboardingChannel
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.testutil.FakeNetworkMonitor
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import java.net.InetSocketAddress
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The properties store with no offline database open, so they come from the API (a local server here) and the last
 * list is kept in the encrypted cache (in memory here) to show first and offline.
 */
@RunWith(AndroidJUnit4::class)
class PropertyStoreTest {
    private lateinit var server: HttpServer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val credentials = MutableStateFlow<SyncCredentials?>(null)
    private val cache = MemoryCache()
    private val requests = mutableListOf<String>()

    /** The properties the server lists; [listStatus] other than 200 fails the list instead. */
    @Volatile private var listed = """{"data":[],"nextCursor":null,"hasMore":false}"""
    @Volatile private var listStatus = 200

    /** Released by the test to let the server answer the list call (so what shows before it can be checked). */
    @Volatile private var gate = CountDownLatch(0)

    private class MemoryCache : EncryptedCache {
        val files = ConcurrentHashMap<String, String>()
        override fun read(name: String): String? = files[name]
        override fun write(name: String, text: String) {
            files[name] = text
        }
    }

    private fun dtoJson(id: String, name: String) = """{"id":"$id","name":"$name","settings":{"bluetoothOnboarding":true,"wifiOnboarding":true}}"""
    private fun pageOf(vararg props: Pair<String, String>) =
        """{"data":[${props.joinToString(",") { dtoJson(it.first, it.second) }}],"nextCursor":null,"hasMore":false}"""

    private fun signIn(org: String = "org-1") {
        credentials.value = SyncCredentials("a@b.com", "u1", "Admin", org, "tok", "sid")
    }

    private fun store(): PropertyStore {
        val tokens = object : AccessTokenProvider {
            override suspend fun accessToken(rejected: String?) = "tok"
        }
        val service = Retrofit.Builder().baseUrl("http://127.0.0.1:${server.address.port}/")
            .client(OkHttpClient().forAdc(tokens)).build().create(AdcApiService::class.java)
        val api = AdcApi(service, OnlineCall(FakeNetworkMonitor(), Dispatchers.IO))
        val provider = object : SyncCredentialsProvider {
            override val credentials: StateFlow<SyncCredentials?> = this@PropertyStoreTest.credentials
        }
        val bridge = SyncManagerBridge(ApplicationProvider.getApplicationContext(), Optional.empty<TestDatabase>())
        val db = AdcDatabase(bridge, TableInvalidations())
        return PropertyStore(api, OnlineSession(provider, scope), cache, PropertyOfflineSource(db, Dispatchers.IO), db, scope, Dispatchers.IO)
    }

    private fun PropertyStore.awaitNames(vararg names: String) = runBlocking {
        withTimeout(10_000) { properties.first { list -> list.map { it.name } == names.toList() } }
    }

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            val path = ex.requestURI.rawPath
            synchronized(requests) { requests += "${ex.requestMethod} $path" }
            val (code, body) = when {
                ex.requestMethod == "GET" && path == "/organisations/org-1/properties" -> {
                    gate.await(10, TimeUnit.SECONDS)
                    listStatus to listed
                }
                ex.requestMethod == "POST" && path == "/organisations/org-1/properties" -> 201 to dtoJson("p-new", "Depot")
                ex.requestMethod == "PATCH" && path.endsWith("/settings") -> 200 to """{"bluetoothOnboarding":false,"wifiOnboarding":true}"""
                else -> 404 to "{}"
            }
            val bytes = body.toByteArray()
            ex.sendResponseHeaders(code, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
            ex.close()
        }
        server.start()
    }

    @After
    fun stop() {
        scope.cancel()
        server.stop(0)
    }

    @Test
    fun signingIn_loadsTheServerList_andKeepsItForNextTime() {
        listed = pageOf("p1" to "Tower", "p2" to "Depot")
        val store = store()
        signIn()
        store.awaitNames("Tower", "Depot")
        val saved = AdcJson.decodeFromString(ListSerializer(PropertyDto.serializer()), cache.files.getValue("properties"))
        assertEquals(listOf("p1", "p2"), saved.map { it.id })
    }

    @Test
    fun theSavedList_showsFirst_thenTheServersReplacesIt() {
        cache.write("properties", "[${dtoJson("p1", "Old name")}]")
        listed = pageOf("p1" to "New name")
        gate = CountDownLatch(1)
        val store = store()
        signIn()
        store.awaitNames("Old name") // shown while the server hasn't answered
        gate.countDown()
        store.awaitNames("New name")
    }

    @Test
    fun aFailedRefresh_keepsTheSavedList_andSaysWhy() = runBlocking {
        cache.write("properties", "[${dtoJson("p1", "Tower")}]")
        listStatus = 500
        val store = store()
        signIn()
        store.awaitNames("Tower")
        try {
            store.refresh()
            fail("expected a server error")
        } catch (e: OnlineException) {
            assertEquals(OnlineError.Server, e.error)
        }
        assertEquals(listOf("Tower"), store.properties.value.map { it.name })
    }

    @Test
    fun signingOut_forgetsTheList() {
        listed = pageOf("p1" to "Tower")
        val store = store()
        signIn()
        store.awaitNames("Tower")
        credentials.value = null
        store.awaitNames()
    }

    @Test
    fun aListArrivingAfterSignOut_isDropped() = runBlocking {
        listed = pageOf("p1" to "Tower")
        gate = CountDownLatch(1)
        val store = store()
        signIn()
        withTimeout(10_000) { while (synchronized(requests) { requests.isEmpty() }) kotlinx.coroutines.yield() }
        credentials.value = null
        gate.countDown()
        // Signing in as someone else's organisation now: the late list for org-1 must not show.
        signIn("org-2")
        withTimeout(10_000) { while (synchronized(requests) { requests.none { "org-2" in it } }) kotlinx.coroutines.yield() }
        assertTrue(store.properties.value.none { it.name == "Tower" })
    }

    @Test
    fun create_addsTheServersProperty_inNameOrder() = runBlocking {
        listed = pageOf("p1" to "Tower")
        val store = store()
        signIn()
        store.awaitNames("Tower")
        val created = store.create(Property("", "Depot", false))
        assertEquals("p-new", created.id)
        store.awaitNames("Depot", "Tower")
        assertTrue(synchronized(requests) { "POST /organisations/org-1/properties" in requests })
    }

    @Test
    fun setOnboarding_keepsWhatTheServerSaved() = runBlocking {
        listed = pageOf("p1" to "Tower")
        val store = store()
        signIn()
        store.awaitNames("Tower")
        assertTrue(store.disabled.value["p1"].orEmpty().isEmpty())
        store.setOnboarding("p1", OnboardingChannel.BLUETOOTH, enabled = false)
        assertEquals(setOf(OnboardingChannel.BLUETOOTH), store.disabled.value["p1"])
    }
}
