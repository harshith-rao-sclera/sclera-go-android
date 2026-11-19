package com.sclera.sclerago.data.tagging

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sclera.sclerago.data.remote.adc.AccessTokenProvider
import com.sclera.sclerago.data.remote.adc.OnlineCall
import com.sclera.sclerago.data.remote.adc.forAdc
import com.sclera.sclerago.data.remote.tagging.TagApi
import com.sclera.sclerago.data.remote.tagging.TagCheckDto
import com.sclera.sclerago.data.remote.tagging.TagPaths
import com.sclera.sclerago.data.remote.tagging.TagService
import com.sclera.sclerago.data.repository.TaggingRepositoryImpl
import com.sclera.sclerago.data.sync.SyncCredentials
import com.sclera.sclerago.data.sync.SyncCredentialsProvider
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.QrMeasurement
import com.sclera.sclerago.domain.model.TagPhoto
import com.sclera.sclerago.domain.model.TagPlacement
import com.sclera.sclerago.domain.model.TagUse
import com.sclera.sclerago.domain.model.TagValue
import com.sclera.sclerago.domain.model.TagWrite
import com.sclera.sclerago.domain.model.TaggingError
import com.sclera.sclerago.domain.model.TaggingException
import com.sclera.sclerago.testutil.FakeNetworkMonitor
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import java.net.InetSocketAddress

/** The tag calls against a local server: the exact paths, headers and bodies they send, and how answers and failures map. */
@RunWith(AndroidJUnit4::class)
class TaggingApiTest {
    private lateinit var server: HttpServer
    private var status = 200
    private var answer = "{}"
    private var method: String? = null
    private var path: String? = null
    private var query: String? = null
    private var auth: String? = null
    private var contentType: String? = null
    private var body = ""

    private val creds = object : SyncCredentialsProvider {
        override val credentials: StateFlow<SyncCredentials?> =
            MutableStateFlow(SyncCredentials("me@x.com", "u1", "Admin", "org-1", "tok", "sess"))
    }
    private val tokens = object : AccessTokenProvider {
        override suspend fun accessToken(rejected: String?) = "token-1"
    }

    private fun repo(): TaggingRepositoryImpl {
        val service = Retrofit.Builder().baseUrl("http://127.0.0.1:${server.address.port}/vdms/api/").client(OkHttpClient().forAdc(tokens)).build()
            .create(TagService::class.java)
        return TaggingRepositoryImpl(TagApi(service, OnlineCall(FakeNetworkMonitor(), Dispatchers.Unconfined)), creds)
    }

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            method = ex.requestMethod
            path = ex.requestURI.rawPath
            query = ex.requestURI.rawQuery
            auth = ex.requestHeaders.getFirst("Authorization")
            contentType = ex.requestHeaders.getFirst("Content-Type")
            body = ex.requestBody.readBytes().toString(Charsets.ISO_8859_1)
            val bytes = answer.toByteArray()
            ex.sendResponseHeaders(status, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
            ex.close()
        }
        server.start()
    }

    @After
    fun stop() = server.stop(0)

    private val system = TagValue.qr("https://x/qr?id=sys 1")
    private val own = TagValue.qr("own/1")
    private val barcode = TagValue.barcode("5901234")
    private val nfc = TagValue.nfc("chip-9", isBlank = false, vdmsPrefix = "https://zzz")

    @Test
    fun `each kind lives on its own path with the id as one encoded segment`() {
        assertEquals("qrCode/sys%201/getAdcCheckByQrCodeId", TagPaths.check(system))
        assertEquals("clientQrCode/own%2F1/getAdcCheckByClientQrCodeId", TagPaths.check(own))
        assertEquals("clientBarCode/5901234/getAdcCheckByClientBarCodeId", TagPaths.check(barcode))
        assertEquals("clientNfc/chip-9/getAdcCheckByClientNfcId", TagPaths.check(nfc))
        assertEquals("clientQrCode/addClientQrCode", TagPaths.register(own))
        assertEquals("clientBarCode/addClientBarCode", TagPaths.register(barcode))
        assertEquals("clientNfc/addClientNfc", TagPaths.register(nfc))
        assertEquals("qrCode/sys%201/updateQrcodeById", TagPaths.update(system))
        assertEquals("clientQrCode/own%2F1/updateClientQrCodeById", TagPaths.update(own))
        assertEquals("clientBarCode/5901234/updateClientBarCodeById", TagPaths.update(barcode))
        assertEquals("clientNfc/chip-9/updateClientNfcById", TagPaths.update(nfc))
    }

    @Test
    fun `check asks with the asset and property and reads the answer`() = runBlocking {
        answer = """{"data":{"isTagged":1,"isAdc":true,"collectionId":"a9","isTaggedToProperty":0,"existingCollectionIdCount":1}}"""
        val check = repo().check(barcode, "a1", "p1")
        assertEquals("GET", method)
        assertEquals("/vdms/api/clientBarCode/5901234/getAdcCheckByClientBarCodeId", path)
        assertEquals("collectionId=a1&propertyId=p1", query)
        assertEquals("Bearer token-1", auth)
        assertEquals(TagUse.IN_USE, check.use)
        assertEquals("a9", check.heldBy)
        assertEquals(1, check.holderTagCount)
        assertTrue(check.confirmsMove)
    }

    @Test
    fun `an answer the rules can't use is an invalid response`() = runBlocking {
        answer = """{"data":{"isTagged":7}}"""
        try {
            repo().check(own, "a1", "p1")
            fail("expected a failure")
        } catch (e: TaggingException) {
            assertEquals(TaggingError.INVALID_RESPONSE, e.error)
        }
    }

    @Test
    fun `check answers map to free in use and unknown`() {
        assertEquals(TagUse.FREE, TagCheckDto(isTagged = 0, isAdc = true).toDomain()?.use)
        assertEquals(TagUse.UNKNOWN, TagCheckDto(isTagged = 2).toDomain()?.use)
        assertNull(TagCheckDto(isTagged = 1).toDomain())
        assertNull(TagCheckDto().toDomain())
        assertTrue(TagCheckDto(isTagged = 1, isAdc = true, isTaggedToProperty = 1).toDomain()!!.isOnOtherProperty)
        assertNull(TagCheckDto(isTagged = 0, isAdc = true, collectionId = "").toDomain()!!.heldBy)
    }

    @Test
    fun `register posts the kind's own keys with the user and organisation`() = runBlocking {
        repo().register(nfc, "a1", "p1")
        assertEquals("POST", method)
        assertEquals("/vdms/api/clientNfc/addClientNfc", path)
        assertTrue(contentType!!.startsWith("application/json"))
        listOf(""""nfc_id":"chip-9"""", """"createdBy":"me@x.com"""", """"orgId":"org-1"""", """"deviceId":"a1"""", """"propertyId":"p1"""").forEach {
            assertTrue("$it in $body", body.contains(it))
        }
        repo().register(own, "a1", "p1")
        assertTrue(body.contains(""""clientQrCodeId":"own/1"""") && body.contains(""""addedBy":"me@x.com""""))
    }

    @Test
    fun `a refused registration is a registration failure but being offline is not`() = runBlocking {
        status = 400
        try {
            repo().register(own, "a1", "p1")
            fail("expected a failure")
        } catch (e: TaggingException) {
            assertEquals(TaggingError.REGISTRATION_FAILED, e.error)
        }
        status = 401
        try {
            repo().register(own, "a1", "p1")
            fail("expected a failure")
        } catch (e: OnlineException) {
            assertEquals(OnlineError.SessionExpired, e.error)
        }
    }

    private fun write(tag: TagValue, photo: TagPhoto? = null, previous: String? = "old") = TagWrite(
        tag, "a1", "p1", previous, TagPlacement(QrMeasurement(90.0, 0.0, 0.0, 1.0, "placeholder"), isMovable = true), photo,
    )

    @Test
    fun `a QR write is a multipart PUT with direction movable and the photo`() = runBlocking {
        answer = """{"status":200,"data":{"newCollectionIdCount":1,"existingCollectionIdCount":2}}"""
        val update = repo().write(write(own, TagPhoto(byteArrayOf(1, 2, 3))))
        assertEquals("PUT", method)
        assertEquals("/vdms/api/clientQrCode/own%2F1/updateClientQrCodeById", path)
        assertTrue(contentType!!.startsWith("multipart/form-data"))
        listOf("updatedBy", "deviceId", "existingCollectionId", "movable", "direction", "propertyId", "qr.jpg", "image/jpeg").forEach {
            assertTrue("$it in body", body.contains(it))
        }
        assertTrue(body.contains("me@x.com") && body.contains("old") && body.contains("true"))
        assertTrue(body.contains(""""yaw":90.0""") && body.contains(""""method":"placeholder""""))
        assertEquals(1, update.newAssetCount)
        assertEquals(2, update.previousAssetCount)
    }

    @Test
    fun `a barcode write sends no direction movable or photo`() = runBlocking {
        repo().write(write(barcode, TagPhoto(byteArrayOf(1)), previous = null))
        assertTrue(!body.contains("qr.jpg") && !body.contains("yaw"))
        assertNotNull(path)
        assertEquals("/vdms/api/clientBarCode/5901234/updateClientBarCodeById", path)
    }

    @Test
    fun `status 826 in the body means someone else got the tag`() = runBlocking {
        answer = """{"status":826}"""
        try {
            repo().write(write(own))
            fail("expected a failure")
        } catch (e: TaggingException) {
            assertEquals(TaggingError.CLAIMED_ELSEWHERE, e.error)
        }
    }

    @Test
    fun `a body that doesn't fit still gives zero counts`() = runBlocking {
        answer = """{"data":"nope"}"""
        val update = repo().write(write(own))
        assertEquals(0, update.newAssetCount)
        assertEquals(0, update.previousAssetCount)
        answer = ""
        assertEquals(0, repo().write(write(own)).newAssetCount)
    }
}
