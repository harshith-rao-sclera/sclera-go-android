package com.sclera.sclerago.data.tagging

import com.sclera.sclerago.testutil.NoTagHistory
import com.sclera.sclerago.data.offline.QrTagRow
import com.sclera.sclerago.data.offline.QrTagStore
import com.sclera.sclerago.data.repository.ConnectionAwareTaggingRepository
import com.sclera.sclerago.data.repository.OfflineTaggingRepository
import com.sclera.sclerago.data.repository.TaggingRepositoryImpl
import com.sclera.sclerago.data.sync.SyncCredentials
import com.sclera.sclerago.data.sync.SyncCredentialsProvider
import com.sclera.sclerago.domain.model.QrMeasurement
import com.sclera.sclerago.domain.model.TagPhoto
import com.sclera.sclerago.domain.model.TagPlacement
import com.sclera.sclerago.domain.model.TagUse
import com.sclera.sclerago.domain.model.TagValue
import com.sclera.sclerago.domain.model.TagWrite
import com.sclera.sclerago.domain.model.TaggingError
import com.sclera.sclerago.domain.model.TaggingException
import com.sclera.sclerago.domain.usecase.tagging.AttachTagUseCase
import com.sclera.sclerago.domain.usecase.tagging.CanTagAssetUseCase
import com.sclera.sclerago.testutil.InMemoryAssetRepository
import com.sclera.sclerago.testutil.allowAllGuard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private class FakeQrTags : QrTagStore {
    val rows = mutableMapOf<String, QrTagRow>()
    val saved = mutableListOf<Triple<TagWrite, String, String>>()
    var photoBytes: ByteArray? = null
    var counts = mapOf<String, Int>()
    var refuse: TaggingException? = null

    override fun row(tag: TagValue) = rows[tag.id]
    override fun save(write: TagWrite, photo: ByteArray, direction: String, user: String) {
        refuse?.let { throw it }
        photoBytes = photo
        saved += Triple(write, direction, user)
        rows[write.tag.id] = QrTagRow(write.asset, write.property, true)
    }

    override fun tagCount(asset: String) = counts[asset] ?: 0
}

class OfflineTaggingRepositoryTest {
    private val tags = FakeQrTags()
    private val creds = object : SyncCredentialsProvider {
        override val credentials: StateFlow<SyncCredentials?> = MutableStateFlow(SyncCredentials("me@x.com", "u", "Admin", "o", "t", "s"))
    }
    private val repo = OfflineTaggingRepository(tags, creds)
    private val qr = TagValue.qr("own-1")
    private val photo = TagPhoto(byteArrayOf(9, 8))
    private val placement = TagPlacement(QrMeasurement(90.0, 0.0, 0.0, 1.0, "placeholder"), isMovable = true)

    private suspend fun assertRefused(error: TaggingError, block: suspend () -> Unit) {
        try {
            block()
            fail("expected $error")
        } catch (e: TaggingException) {
            assertEquals(error, e.error)
        }
    }

    @Test
    fun `a code the phone has never seen is free and needs no registration`() = runTest {
        val check = repo.check(qr, "a1", "p1")
        assertEquals(TagUse.FREE, check.use)
        assertTrue(check.isAdc)
        assertFalse(check.confirmsMove)
        repo.register(qr, "a1", "p1")
    }

    @Test
    fun `a known code reports its holder and property`() = runTest {
        tags.rows["own-1"] = QrTagRow("a9", "p2", isAdc = true)
        val check = repo.check(qr, "a1", "p1")
        assertEquals(TagUse.IN_USE, check.use)
        assertEquals("a9", check.heldBy)
        assertTrue(check.isOnOtherProperty)
        assertEquals(1, check.holderTagCount)
    }

    @Test
    fun `a known code nobody holds is free, and one the VDMS holds is not ADC`() = runTest {
        tags.rows["own-1"] = QrTagRow("", "p1", isAdc = true)
        assertEquals(TagUse.FREE, repo.check(qr, "a1", "p1").use)
        tags.rows["own-1"] = QrTagRow(null, null, isAdc = false)
        val check = repo.check(qr, "a1", "p1")
        assertEquals(TagUse.IN_USE, check.use)
        assertFalse(check.isAdc)
    }

    @Test
    fun `barcodes and NFC tags need a connection`() = runTest {
        assertRefused(TaggingError.NEEDS_CONNECTION) { repo.check(TagValue.barcode("1"), "a1", "p1") }
        assertRefused(TaggingError.NEEDS_CONNECTION) {
            repo.write(TagWrite(TagValue.barcode("1"), "a1", "p1", null, TagPlacement(), null))
        }
    }

    @Test
    fun `writing needs the photo`() = runTest {
        assertRefused(TaggingError.PHOTO_REQUIRED) { repo.write(TagWrite(qr, "a1", "p1", null, placement, null)) }
        assertTrue(tags.saved.isEmpty())
    }

    @Test
    fun `a write saves the photo direction and who made it, and counts what the old asset keeps`() = runTest {
        tags.counts = mapOf("a2" to 3)
        val update = repo.write(TagWrite(qr, "a1", "p1", "a2", placement, photo))
        val (write, direction, user) = tags.saved.single()
        assertEquals("a1", write.asset)
        assertEquals("me@x.com", user)
        assertTrue(direction.contains(""""yaw":90.0"""))
        assertEquals(listOf<Byte>(9, 8), tags.photoBytes!!.toList())
        assertEquals(1, update.newAssetCount)
        assertEquals(3, update.previousAssetCount)
        assertEquals(0, repo.write(TagWrite(qr, "a1", "p1", "a1", placement, photo)).previousAssetCount)
    }

    @Test
    fun `a refusal from the store reaches the caller`() = runTest {
        tags.refuse = TaggingException(TaggingError.TAKEN_BY_OTHER_PROPERTY)
        assertRefused(TaggingError.TAKEN_BY_OTHER_PROPERTY) { repo.write(TagWrite(qr, "a1", "p1", null, placement, photo)) }
    }

    @Test
    fun `offline attach moves a tag from another draft without asking, as the old app did`() = runTest {
        tags.rows["own-1"] = QrTagRow("a9", "p1", isAdc = true)
        AttachTagUseCase(repo, NoTagHistory, CanTagAssetUseCase(InMemoryAssetRepository(emptyList()), allowAllGuard()))(qr, "a1", "p1", placement, photo)
        assertEquals("a9", tags.saved.single().first.previousAsset)
    }

    @Test
    fun `tags are written offline when the phone is offline and online when it is not`() = runTest {
        val online = TaggingRepositoryImpl(
            com.sclera.sclerago.data.remote.tagging.TagApi(
                object : com.sclera.sclerago.data.remote.tagging.TagService {
                    override suspend fun check(path: String, asset: String, property: String) = throw java.io.IOException("unused")
                    override suspend fun register(path: String, body: okhttp3.RequestBody) = throw java.io.IOException("unused")
                    override suspend fun writeWithPhoto(path: String, parts: List<okhttp3.MultipartBody.Part>, timeoutSeconds: String) = throw java.io.IOException("unused")
                    override suspend fun write(path: String, body: okhttp3.RequestBody) = throw java.io.IOException("unused")
                },
                com.sclera.sclerago.data.remote.adc.OnlineCall(com.sclera.sclerago.testutil.FakeNetworkMonitor(), kotlinx.coroutines.Dispatchers.Unconfined),
            ),
            creds,
        )
        val network = com.sclera.sclerago.testutil.FakeNetworkMonitor(online = false)
        val routed = ConnectionAwareTaggingRepository(network, online, repo, com.sclera.sclerago.data.offline.AssetTagFlagStore { _, _, _ -> }, NoTagHistory)
        assertEquals(TagUse.FREE, routed.check(qr, "a1", "p1").use)
        routed.write(TagWrite(qr, "a1", "p1", null, placement, photo))
        assertEquals(1, tags.saved.size)
        network.online = true
        try {
            routed.check(qr, "a1", "p1")
            fail("expected the online call to be tried")
        } catch (_: Exception) {
        }
        assertEquals(1, tags.saved.size)
    }
}
