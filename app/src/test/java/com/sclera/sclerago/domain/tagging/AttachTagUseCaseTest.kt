package com.sclera.sclerago.domain.tagging

import com.sclera.sclerago.testutil.NoTagHistory
import com.sclera.sclerago.domain.model.TagPlacement
import com.sclera.sclerago.domain.model.TagPhoto
import com.sclera.sclerago.domain.model.TagUpdate
import com.sclera.sclerago.domain.model.TagValue
import com.sclera.sclerago.domain.model.TaggingError
import com.sclera.sclerago.domain.model.TaggingException
import com.sclera.sclerago.domain.model.QrMeasurement
import com.sclera.sclerago.domain.usecase.tagging.AttachTagUseCase
import com.sclera.sclerago.domain.usecase.tagging.BuildScannedTagUseCase
import com.sclera.sclerago.domain.usecase.tagging.CanTagAssetUseCase
import com.sclera.sclerago.domain.usecase.tagging.TagScan
import com.sclera.sclerago.domain.model.TagKind
import com.sclera.sclerago.testutil.FakeDirectionSource
import com.sclera.sclerago.testutil.FakeTagServer
import com.sclera.sclerago.testutil.FakeTaggingRepository
import com.sclera.sclerago.testutil.InMemoryAssetRepository
import com.sclera.sclerago.testutil.allowAllGuard
import com.sclera.sclerago.testutil.freeTag
import com.sclera.sclerago.testutil.heldTag
import com.sclera.sclerago.testutil.unknownTag
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AttachTagUseCaseTest {
    private val repo = FakeTaggingRepository()
    private val attach = AttachTagUseCase(repo, NoTagHistory, CanTagAssetUseCase(InMemoryAssetRepository(emptyList()), allowAllGuard()))
    private val own = TagValue.qr("own-1")
    private val system = TagValue.qr("https://x/qr?id=sys-1")

    private suspend fun assertRefused(error: TaggingError, heldBy: String? = null, block: suspend () -> Unit) {
        try {
            block()
            fail("expected $error")
        } catch (e: TaggingException) {
            assertEquals(error, e.error)
            assertEquals(heldBy, e.heldBy)
        }
    }

    @Test
    fun `a free tag is written to the asset`() = runTest {
        val photo = TagPhoto(byteArrayOf(1))
        val placement = TagPlacement(QrMeasurement(1.0, 2.0, 3.0, 1.0, "m"), isMovable = true)
        val update = attach(own, "a1", "p1", placement, photo)
        assertEquals(TagUpdate(1, 0), update)
        val write = repo.written.single()
        assertEquals("a1", write.asset)
        assertEquals("p1", write.property)
        assertNull(write.previousAsset)
        assertEquals(placement, write.placement)
        assertEquals(photo, write.photo)
        assertTrue(repo.registered.isEmpty())
    }

    @Test
    fun `a customer's own unknown tag is registered first`() = runTest {
        repo.check = unknownTag()
        attach(own, "a1", "p1")
        assertEquals(listOf(own), repo.registered)
        assertEquals(1, repo.written.size)
    }

    @Test
    fun `an unknown Sclera code is refused and nothing is written`() = runTest {
        repo.check = unknownTag()
        assertRefused(TaggingError.UNKNOWN_ID) { attach(system, "a1", "p1") }
        assertTrue(repo.written.isEmpty())
        assertTrue(repo.registered.isEmpty())
    }

    @Test
    fun `a tag held by the main VDMS system is refused`() = runTest {
        repo.check = heldTag(isAdc = false)
        assertRefused(TaggingError.TAKEN_BY_VDMS) { attach(own, "a1", "p1") }
        assertTrue(repo.written.isEmpty())
    }

    @Test
    fun `a tag on another draft asks to be moved`() = runTest {
        repo.check = heldTag(by = "a2")
        assertRefused(TaggingError.NEEDS_MOVE, heldBy = "a2") { attach(own, "a1", "p1") }
        assertTrue(repo.written.isEmpty())
    }

    @Test
    fun `once the move is confirmed the tag is written with the asset it came from`() = runTest {
        repo.check = heldTag(by = "a2")
        attach(own, "a1", "p1", isMoveConfirmed = true)
        assertEquals("a2", repo.written.single().previousAsset)
    }

    @Test
    fun `a tag on a submitted asset can't be moved`() = runTest {
        repo.check = heldTag(by = "a2")
        assertRefused(TaggingError.TAKEN_BY_SUBMITTED, heldBy = "a2") { attach(own, "a1", "p1", isSubmitted = { it == "a2" }) }
    }

    @Test
    fun `a tag of another property can't be moved`() = runTest {
        repo.check = heldTag(by = "a2", otherProperty = true)
        assertRefused(TaggingError.TAKEN_BY_OTHER_PROPERTY) { attach(own, "a1", "p1") }
    }

    @Test
    fun `without a confirmation step the tag moves straight away`() = runTest {
        repo.check = heldTag(by = "a2", confirms = false)
        attach(own, "a1", "p1")
        assertEquals("a2", repo.written.single().previousAsset)
    }

    @Test
    fun `a holder with several tags is asked too`() = runTest {
        repo.check = heldTag(by = "a2", count = 2)
        assertRefused(TaggingError.NEEDS_MOVE, heldBy = "a2") { attach(own, "a1", "p1") }
    }

    @Test
    fun `a tag the asset already holds is written again without moving`() = runTest {
        repo.check = heldTag(by = "a1")
        attach(own, "a1", "p1")
        assertEquals("a1", repo.written.single().previousAsset)
    }

    @Test
    fun `a server failure while checking stops before writing`() = runTest {
        repo.checkError = IllegalStateException("down")
        try {
            attach(own, "a1", "p1")
            fail("expected a failure")
        } catch (_: IllegalStateException) {
        }
        assertTrue(repo.written.isEmpty())
    }

    @Test
    fun `building a QR scan gives its direction and the movable choice`() = runTest {
        val build = BuildScannedTagUseCase(FakeDirectionSource(), FakeTagServer())
        val (tag, placement) = build(TagKind.QR, TagScan("sticker", null), isMovable = true)
        assertEquals("sticker", tag.id)
        assertEquals(10.0, placement.direction?.yaw)
        assertTrue(placement.isMovable)
    }

    @Test
    fun `building a barcode scan has no direction`() = runTest {
        val build = BuildScannedTagUseCase(FakeDirectionSource(), FakeTagServer())
        val (tag, placement) = build(TagKind.BARCODE, TagScan(" 123 ", null), isMovable = true)
        assertEquals("123", tag.id)
        assertNull(placement.direction)
    }

    @Test
    fun `an NFC chip holding a link to the server belongs to the VDMS`() {
        val build = BuildScannedTagUseCase(FakeDirectionSource(), FakeTagServer("https://app.example.com"))
        assertEquals("chip-1", build.nfc("chip-1", isBlank = false).id)
        try {
            build.nfc("https://app.example.com/qr?id=1", isBlank = false)
            fail("expected a refusal")
        } catch (e: TaggingException) {
            assertEquals(TaggingError.TAKEN_BY_VDMS, e.error)
        }
    }

    @Test
    fun `an NFC kind can't go through the scan builder`() = runTest {
        val build = BuildScannedTagUseCase(FakeDirectionSource(), FakeTagServer())
        try {
            build(TagKind.NFC, TagScan("x", null), isMovable = false)
            fail("expected a failure")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun `an empty free check doesn't register`() = runTest {
        repo.check = freeTag()
        attach(own, "a1", "p1")
        assertTrue(repo.registered.isEmpty())
    }
}
