package com.sclera.sclerago.presentation.assets

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sclera.sclerago.R
import com.sclera.sclerago.data.repository.AssetConfigRepositoryImpl
import com.sclera.sclerago.data.repository.AssetRepositoryImpl
import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.usecase.config.ObserveAssetConfigUseCase
import com.sclera.sclerago.domain.validation.AssetCheckId
import com.sclera.sclerago.domain.validation.CheckOutcome
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.testutil.FakeAutofillRepository
import com.sclera.sclerago.testutil.assetUseCases
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AssetDetailViewModelTest {
    private val repo = AssetRepositoryImpl()
    private val config = AssetConfigRepositoryImpl()
    private val autofill = FakeAutofillRepository()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = AssetDetailViewModel(assetUseCases(repo, config, autofill), ObserveAssetConfigUseCase(config))
    private fun seed(id: Int): Asset = repo.catalog.value.assetsIn("p1").first { it.id == id }
    private fun AssetDetailViewModel.openSeed(id: Int) = open(seed(id), isNew = false)
    private val photo = Media("m1", "content://m1", video = false, createdAt = 0L)

    @Test
    fun open_buildsTheFormFromWhatTheAssetCameWith() {
        val vm = vm()
        vm.openSeed(2)
        val s = vm.state.value
        assertEquals(2, s.asset?.id)
        assertEquals("SCL-000421", s.code)
        assertEquals("KSB", s.value("make"))
        assertEquals(0, s.dirtyCount)
        assertFalse(s.locked)
        assertNotNull(s.checks)
    }

    @Test
    fun nothingOpen_showsNothing_andOpeningTheSameAssetAgainKeepsTheEdits() {
        val vm = vm()
        assertNull(vm.state.value.asset)
        vm.openSeed(2)
        vm.edit("make", "Grundfos")
        vm.openSeed(2)
        assertEquals("Grundfos", vm.state.value.value("make"))
        vm.close()
        assertNull(vm.state.value.asset)
        vm.openSeed(2)
        assertEquals("KSB", vm.state.value.value("make"))
    }

    @Test
    fun edits_countAsChangesOnlyWhenTheyDifferFromTheSavedValue() {
        val vm = vm()
        vm.openSeed(2)
        vm.edit("make", "Grundfos")
        vm.edit("model", "Etanorm 065-040-160") // typed again, unchanged
        val s = vm.state.value
        assertTrue(s.dirty("make"))
        assertFalse(s.dirty("model"))
        assertEquals(1, s.dirtyCount)
        vm.discardEdits()
        assertEquals(0, vm.state.value.dirtyCount)
        assertEquals("KSB", vm.state.value.value("make"))
    }

    @Test
    fun save_keepsTheEditsAsADraft_andSaysSo() = runTest {
        val vm = vm()
        val notices = mutableListOf<UiText>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.notices.toList(notices) }
        vm.openSeed(2)
        vm.save() // nothing to save
        assertTrue(notices.isEmpty())
        vm.edit("make", "Grundfos")
        vm.save()
        assertEquals("Grundfos", repo.catalog.value.savedDetails[2]?.get("make"))
        assertEquals(Status.DRAFT, seed(2).st)
        assertEquals(0, vm.state.value.dirtyCount)
        assertTrue(vm.state.value.savedFlash)
        assertEquals(listOf<UiText>(UiText.Res(R.string.asset_saved_draft)), notices)
        vm.edit("make", "ABB")
        assertFalse(vm.state.value.savedFlash)
        job.cancel()
    }

    @Test
    fun submit_locksTheAsset_andItCanNoLongerBeEdited() {
        val vm = vm()
        vm.openSeed(2)
        vm.edit("serial", "S-1")
        vm.submit()
        assertEquals(Status.SUBMITTED, seed(2).st)
        assertEquals("S-1", repo.catalog.value.savedDetails[2]?.get("serial"))
        val s = vm.state.value
        assertTrue(s.locked)
        vm.edit("serial", "S-2")
        assertEquals("S-1", vm.state.value.value("serial"))
    }

    @Test
    fun validate_movesTheAssetToValidated() {
        val vm = vm()
        vm.openSeed(2)
        vm.validate()
        assertEquals(Status.VALIDATED, seed(2).st)
        assertEquals(Status.VALIDATED, vm.state.value.asset?.st)
    }

    @Test
    fun checks_followTheEditsAsTheyAreTyped() {
        val vm = vm()
        vm.openSeed(2)
        fun jacs() = vm.state.value.checks!!.items.first { it.id == AssetCheckId.JACS_CODE }
        vm.edit("jacs", "")
        assertEquals(CheckOutcome.FAIL, jacs().outcome)
        assertFalse(vm.state.value.checks!!.canValidate)
        vm.edit("jacs", "[JACS code 1]")
        assertEquals(CheckOutcome.PASS, jacs().outcome)
    }

    @Test
    fun lowConfidenceReading_isFlaggedUntilTheFieldIsChanged() {
        val vm = vm()
        vm.openSeed(2)
        val flagged = vm.state.value.lowConfidenceKey
        assertNotNull(flagged)
        assertEquals(FieldBadge.CHECK, vm.state.value.badgeFor(flagged!!))
        assertEquals(FieldBadge.OCR, vm.state.value.badgeFor("make"))
        vm.edit(flagged, "9999 RPM")
        assertNull(vm.state.value.lowConfidenceKey)
        assertEquals(FieldBadge.EDITED, vm.state.value.badgeFor(flagged))
        assertEquals(CheckOutcome.PASS, vm.state.value.checks!!.items.first { it.id == AssetCheckId.LOW_CONFIDENCE }.outcome)
    }

    @Test
    fun attach_toAnExistingAsset_savesTheMedia_andSaysHowMany() = runTest {
        val vm = vm()
        val notices = mutableListOf<UiText>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.notices.toList(notices) }
        vm.openSeed(2)
        val before = vm.state.value.media.size
        vm.attach(emptyList())
        vm.attach(listOf(photo))
        assertEquals(before + 1, vm.state.value.media.size)
        assertEquals(listOf(photo), repo.catalog.value.mediaAdded[2])
        assertEquals(listOf<UiText>(UiText.Plural(R.plurals.items_added_to_asset, 1)), notices)
        job.cancel()
    }

    @Test
    fun attach_toANewAsset_waitsInTheFormUntilItIsCreated() {
        val vm = vm()
        vm.open(seed(2).copy(id = -1), isNew = true)
        assertNull(vm.state.value.code)
        assertNull(vm.state.value.checks)
        vm.attach(listOf(photo))
        assertEquals(listOf(photo), vm.state.value.newPicks)
        assertEquals(listOf(photo), vm.state.value.media)
        assertTrue(repo.catalog.value.mediaAdded.isEmpty())
    }

    @Test
    fun autofill_fillsEmptyFields_marksThemAI_andClearsTheMessageLater() = runTest {
        autofill.suggestions = mapOf("jacs" to "[JACS code 2]", "make" to "ignored: already set")
        val vm = vm()
        vm.openSeed(2)
        vm.edit("jacs", "")
        vm.autofill()
        var s = vm.state.value
        assertEquals(1, autofill.calls)
        assertEquals("[JACS code 2]", s.value("jacs"))
        assertEquals("KSB", s.value("make"))
        assertEquals(setOf("jacs"), s.aiFilled)
        assertEquals(FieldBadge.AI, s.badgeFor("jacs"))
        assertEquals(UiText.Plural(R.plurals.ai_filled, 1), s.aiNotice)
        assertFalse(s.aiBusy)
        // Editing an AI-filled field takes its mark off.
        vm.edit("jacs", "[JACS code 1]")
        assertTrue(vm.state.value.aiFilled.isEmpty())
        advanceTimeBy(3_000)
        s = vm.state.value
        assertNull(s.aiNotice)
    }

    @Test
    fun autofill_withNothingToSuggest_saysSo_andNeverRunsOnALockedAsset() {
        val vm = vm()
        vm.openSeed(2)
        vm.autofill()
        assertEquals(UiText.Res(R.string.detail_ai_nothing), vm.state.value.aiNotice)
        vm.submit()
        val calls = autofill.calls
        vm.autofill()
        assertEquals(calls, autofill.calls)
    }

    @Test
    fun assetDeletedElsewhere_clearsTheState() {
        val vm = vm()
        vm.openSeed(2)
        repo.delete(listOf(2))
        assertNull(vm.state.value.asset)
    }
}
