package com.sclera.sclerago.presentation.tagging

import com.sclera.sclerago.testutil.NoTagHistory
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.NfcFailure
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.TagKind
import com.sclera.sclerago.domain.model.TagScannerFailure
import com.sclera.sclerago.domain.model.TaggingError
import com.sclera.sclerago.domain.model.TaggingException
import com.sclera.sclerago.domain.usecase.tagging.AttachTagUseCase
import com.sclera.sclerago.domain.usecase.tagging.BuildScannedTagUseCase
import com.sclera.sclerago.domain.usecase.tagging.IsAssetSubmittedUseCase
import com.sclera.sclerago.domain.usecase.tagging.TagHolderNameUseCase
import com.sclera.sclerago.domain.usecase.tagging.TagPropertyUseCase
import com.sclera.sclerago.domain.usecase.tagging.TagScan
import com.sclera.sclerago.domain.usecase.tagging.TagUseCases
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.testutil.FakeDirectionSource
import com.sclera.sclerago.domain.model.PropertyDownloads
import com.sclera.sclerago.testutil.FakeNetworkMonitor
import com.sclera.sclerago.testutil.FakePropertyDownloads
import com.sclera.sclerago.testutil.FakeTagServer
import com.sclera.sclerago.testutil.FakeTaggingRepository
import com.sclera.sclerago.testutil.InMemoryAssetRepository
import com.sclera.sclerago.testutil.heldTag
import com.sclera.sclerago.testutil.unknownTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TagViewModelTest {
    private val repo = FakeTaggingRepository()
    private val network = FakeNetworkMonitor()
    private val assets = InMemoryAssetRepository()

    private fun vm() = TagViewModel(
        TagUseCases(AttachTagUseCase(repo, NoTagHistory), BuildScannedTagUseCase(FakeDirectionSource(), FakeTagServer()), IsAssetSubmittedUseCase(assets), TagHolderNameUseCase(assets), TagPropertyUseCase(FakePropertyDownloads(PropertyDownloads(currentId = "p1", restored = true)))),
        network,
    ).also { it.open("a2", "p1") }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TagViewModel.failure(): UiText = (state.value.notice as TagNotice.Refused).message
    private fun res(id: Int) = UiText.Res(id)

    @Test
    fun `starts idle with nothing tagged`() {
        val s = vm().state.value
        assertEquals(TagPhase.Idle, s.phase)
        assertTrue(s.tagged.isEmpty())
    }

    @Test
    fun `picking QR or barcode opens the scanner and NFC waits for a chip`() {
        val vm = vm()
        vm.start(TagKind.QR)
        assertEquals(TagPhase.Scanning(TagKind.QR), vm.state.value.phase)
        vm.cancel()
        vm.start(TagKind.BARCODE)
        assertEquals(TagPhase.Scanning(TagKind.BARCODE), vm.state.value.phase)
        vm.cancel()
        vm.start(TagKind.NFC)
        assertEquals(TagPhase.ReadingNfc, vm.state.value.phase)
    }

    @Test
    fun `with no connection a barcode or NFC tag says so but a QR code still scans`() {
        network.online = false
        val vm = vm()
        vm.start(TagKind.BARCODE)
        assertEquals(res(R.string.tag_error_offline), vm.failure())
        vm.start(TagKind.NFC)
        assertEquals(res(R.string.tag_error_offline), vm.failure())
        vm.start(TagKind.QR)
        assertEquals(TagPhase.Scanning(TagKind.QR), vm.state.value.phase)
    }

    @Test
    fun `a QR scan is tagged and the toast kind is set`() {
        val vm = vm()
        vm.start(TagKind.QR)
        vm.scanned(TagKind.QR, TagScan("sticker-1", byteArrayOf(1, 2)))
        val s = vm.state.value
        assertEquals(TagPhase.Idle, s.phase)
        assertEquals(setOf(TagKind.QR), s.tagged)
        assertEquals(TagNotice.Tagged(TagKind.QR), s.notice)
        val write = repo.written.single()
        assertEquals("a2", write.asset)
        assertEquals("p1", write.property)
        assertFalse(write.placement.isMovable)
        assertEquals(10.0, write.placement.direction?.yaw)
        assertEquals(2, write.photo?.data?.size)
        vm.toastShown()
        assertNull(vm.state.value.notice)
    }

    @Test
    fun `a barcode and an NFC tag are tagged without a direction or photo`() {
        val vm = vm()
        vm.scanned(TagKind.BARCODE, TagScan("5901234123457", null))
        assertNull(repo.written.last().placement.direction)
        assertNull(repo.written.last().photo)
        vm.nfcRead("chip-1", isBlank = false)
        assertEquals(setOf(TagKind.BARCODE, TagKind.NFC), vm.state.value.tagged)
    }

    @Test
    fun `a QR code already tagged asks before it is scanned again`() {
        val vm = vm()
        vm.scanned(TagKind.QR, TagScan("one", null))
        vm.start(TagKind.QR)
        assertEquals(TagPhase.ConfirmingRescan, vm.state.value.phase)
        vm.confirmRescan()
        assertEquals(TagPhase.Scanning(TagKind.QR), vm.state.value.phase)
    }

    @Test
    fun `tags done on one asset do not show on the next`() {
        val vm = vm()
        vm.scanned(TagKind.QR, TagScan("one", null))
        vm.open("a1", "p1")
        assertTrue(vm.state.value.tagged.isEmpty())
        vm.open("a2", "p1")
        assertEquals(setOf(TagKind.QR), vm.state.value.tagged)
    }

    @Test
    fun `a tag on another draft asks to move and the confirmation moves it`() {
        repo.check = heldTag(by = "a11")
        val vm = vm()
        vm.scanned(TagKind.QR, TagScan("held", null))
        assertEquals(TagPhase.ConfirmingMove("Meeting room speaker"), vm.state.value.phase)
        assertTrue(repo.written.isEmpty())
        vm.confirmMove()
        assertEquals(TagPhase.Idle, vm.state.value.phase)
        assertEquals("a11", repo.written.single().previousAsset)
    }

    @Test
    fun `a tag on a submitted asset is refused`() {
        repo.check = heldTag(by = "a1") // a1 is validated in the sample assets
        val vm = vm()
        vm.scanned(TagKind.QR, TagScan("held", null))
        assertEquals(UiText.Res(R.string.tag_error_submitted_named, listOf("Motor · ABB M3BP 160MLB 4")), vm.failure())
    }

    @Test
    fun `refusals and server failures become the matching text`() {
        val vm = vm()
        repo.check = unknownTag()
        vm.scanned(TagKind.QR, TagScan("https://x/qr?id=sys", null))
        assertEquals(res(R.string.tag_error_unknown_id), vm.failure())
        repo.check = com.sclera.sclerago.testutil.freeTag()
        repo.writeError = TaggingException(TaggingError.CLAIMED_ELSEWHERE)
        vm.scanned(TagKind.BARCODE, TagScan("1", null))
        assertEquals(res(R.string.tag_error_claimed_elsewhere), vm.failure())
        repo.writeError = OnlineException(OnlineError.Forbidden)
        vm.scanned(TagKind.BARCODE, TagScan("1", null))
        assertEquals(res(R.string.online_error_forbidden), vm.failure())
        repo.writeError = IllegalStateException("boom")
        vm.scanned(TagKind.BARCODE, TagScan("1", null))
        assertEquals(res(R.string.tag_error_generic), vm.failure())
    }

    @Test
    fun `text that holds nothing is refused`() {
        val vm = vm()
        vm.scanned(TagKind.QR, TagScan("   ", null))
        assertEquals(res(R.string.tag_error_empty), vm.failure())
        vm.nfcRead("", isBlank = true)
        assertEquals(res(R.string.tag_error_blank_nfc), vm.failure())
        assertTrue(repo.written.isEmpty())
    }

    @Test
    fun `scanner and NFC failures say why`() {
        val vm = vm()
        vm.scannerFailed(TagScannerFailure.CAMERA_DENIED)
        assertEquals(res(R.string.tag_error_camera_denied), vm.failure())
        vm.scannerFailed(TagScannerFailure.UNAVAILABLE)
        assertEquals(res(R.string.tag_error_camera_unavailable), vm.failure())
        mapOf(
            NfcFailure.UNAVAILABLE to R.string.tag_error_nfc_unavailable,
            NfcFailure.DISABLED to R.string.tag_error_nfc_disabled,
            NfcFailure.TIMED_OUT to R.string.tag_error_nfc_timed_out,
            NfcFailure.FAILED to R.string.tag_error_nfc_failed,
        ).forEach { (failure, text) ->
            vm.nfcFailed(failure)
            assertEquals(res(text), vm.failure())
        }
    }

    @Test
    fun `cancel closes everything`() {
        val vm = vm()
        vm.start(TagKind.BARCODE)
        vm.cancel()
        assertEquals(TagPhase.Idle, vm.state.value.phase)
    }

    @Test
    fun `nothing happens before an asset is open`() {
        val vm = TagViewModel(
            TagUseCases(AttachTagUseCase(repo, NoTagHistory), BuildScannedTagUseCase(FakeDirectionSource(), FakeTagServer()), IsAssetSubmittedUseCase(assets), TagHolderNameUseCase(assets), TagPropertyUseCase(FakePropertyDownloads(PropertyDownloads(currentId = "p1", restored = true)))),
            network,
        )
        vm.start(TagKind.QR)
        vm.scanned(TagKind.QR, TagScan("x", null))
        assertTrue(repo.written.isEmpty())
    }
}
