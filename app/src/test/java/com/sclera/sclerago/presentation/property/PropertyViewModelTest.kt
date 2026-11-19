package com.sclera.sclerago.presentation.property

import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.AssetCatalog
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.domain.model.PropertyDownload
import com.sclera.sclerago.domain.model.PropertyDownloads
import com.sclera.sclerago.domain.usecase.property.AddPropertyUseCase
import com.sclera.sclerago.domain.usecase.property.AddressUseCases
import com.sclera.sclerago.domain.usecase.property.DeletePropertyUseCase
import com.sclera.sclerago.domain.usecase.property.FilterPropertySummariesUseCase
import com.sclera.sclerago.domain.usecase.property.GetPropertyUseCase
import com.sclera.sclerago.domain.usecase.property.ObserveCanManagePropertiesUseCase
import com.sclera.sclerago.domain.usecase.property.ObserveOnboardingUseCase
import com.sclera.sclerago.domain.usecase.property.ObservePropertiesUseCase
import com.sclera.sclerago.domain.usecase.property.ObservePropertySummariesUseCase
import com.sclera.sclerago.domain.usecase.property.PropertyUseCases
import com.sclera.sclerago.domain.usecase.property.RefreshPropertiesUseCase
import com.sclera.sclerago.domain.usecase.property.SetOnboardingUseCase
import com.sclera.sclerago.domain.usecase.property.SuggestCitiesUseCase
import com.sclera.sclerago.domain.usecase.property.SuggestCountriesUseCase
import com.sclera.sclerago.domain.usecase.property.SuggestStatesUseCase
import com.sclera.sclerago.domain.usecase.property.UpdatePropertyUseCase
import com.sclera.sclerago.domain.usecase.property.ValidatePropertyUseCase
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.preview.SAMPLE_ASSETS
import com.sclera.sclerago.testutil.FakeCatalogRepository
import com.sclera.sclerago.testutil.FakeFileRepository
import com.sclera.sclerago.testutil.FakeNetworkMonitor
import com.sclera.sclerago.testutil.FakePropertyDownloads
import com.sclera.sclerago.testutil.FakePropertyRepository
import com.sclera.sclerago.testutil.NoAddresses
import com.sclera.sclerago.testutil.NoOnboarding
import com.sclera.sclerago.testutil.SAMPLE_PROPERTIES
import com.sclera.sclerago.testutil.allowAllGuard
import com.sclera.sclerago.testutil.propertyDownloadUseCases
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

/** ViewModel → use cases → the real in-memory repositories. */
@OptIn(ExperimentalCoroutinesApi::class)
class PropertyViewModelTest {
    private val properties = FakePropertyRepository(SAMPLE_PROPERTIES)
    private val assets = FakeCatalogRepository(AssetCatalog(SAMPLE_ASSETS))
    private val files = FakeFileRepository()
    private val downloads = FakePropertyDownloads(PropertyDownloads(currentId = "p1", downloadedIds = setOf("p1", "p4"), restored = true))
    private val network = FakeNetworkMonitor()
    private lateinit var vm: PropertyViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val onboarding = NoOnboarding()
        val guard = allowAllGuard()
        val addresses = NoAddresses()
        vm = PropertyViewModel(
            PropertyUseCases(
                ObservePropertiesUseCase(properties), AddPropertyUseCase(properties, guard), ValidatePropertyUseCase(),
                RefreshPropertiesUseCase(properties), ObserveOnboardingUseCase(onboarding), SetOnboardingUseCase(onboarding, guard),
                ObservePropertySummariesUseCase(properties, assets), FilterPropertySummariesUseCase(), ObserveCanManagePropertiesUseCase(guard),
                GetPropertyUseCase(properties), UpdatePropertyUseCase(properties), DeletePropertyUseCase(properties),
            ),
            propertyDownloadUseCases(downloads, network),
            AddressUseCases(SuggestCountriesUseCase(addresses), SuggestStatesUseCase(addresses), SuggestCitiesUseCase(addresses)),
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun startsWithEveryPropertyAndTheTotals() {
        val s = vm.state.value
        assertEquals(properties.properties.value.size, s.rows.size)
        assertEquals(s.rows.size, s.propertyTotal)
        assertEquals("", s.query)
        assertFalse(s.addOpen || s.importOpen)
        assertNull(s.notice)
    }

    @Test
    fun search_narrowsTheRowsButNotTheTotals_andClearingRestoresThem() {
        val total = vm.state.value.propertyTotal
        vm.setQuery("harbour")
        val s = vm.state.value
        assertEquals(listOf("Harbour Point Office Tower"), s.rows.map { it.property.name })
        assertEquals(total, s.propertyTotal)
        assertEquals(total, s.properties.size)
        vm.setQuery("")
        assertEquals(total, vm.state.value.rows.size)
    }

    @Test
    fun sheets_openAndClose() {
        vm.openAdd()
        assertTrue(vm.state.value.addOpen)
        vm.closeAdd()
        vm.openImport()
        assertTrue(vm.state.value.importOpen)
        assertFalse(vm.state.value.addOpen)
        vm.closeImport()
        assertFalse(vm.state.value.importOpen)
    }

    @Test
    fun addProperty_validDraft_addsItAndClosesTheSheet() {
        vm.openAdd()
        assertNull(vm.addProperty(Property("", "Depot", false, type = "Warehouse")))
        val s = vm.state.value
        assertFalse(s.addOpen)
        assertTrue(s.rows.any { it.property.name == "Depot" && it.assets == 0 })
    }

    @Test
    fun addProperty_invalidDraft_returnsWhyAndChangesNothing() {
        vm.openAdd()
        val before = vm.state.value.propertyTotal
        assertEquals(UiText.Res(R.string.property_fill_required), vm.addProperty(Property("", " ", false, type = "Office")))
        assertEquals(UiText.Res(R.string.property_fill_required), vm.addProperty(Property("", "New", false, type = "")))
        assertEquals(before, vm.state.value.propertyTotal)
        assertTrue(vm.state.value.addOpen)
        assertNull(vm.state.value.notice)
    }

    // ---- Downloads

    @Test
    fun opening_readsThePropertiesAndShowsWhatIsDownloaded() {
        assertEquals(1, properties.refreshes)
        assertEquals("p1", vm.state.value.downloads.currentId)
        assertEquals(setOf("p1", "p4"), vm.state.value.downloads.downloadedIds)
        assertTrue(vm.state.value.online)
    }

    @Test
    fun download_startsIt_andTheRowShowsItRunning() {
        vm.download("p5")
        assertEquals(listOf("p5"), downloads.started)
        assertTrue(vm.state.value.downloads.active["p5"] is PropertyDownload.Running)
        assertNull(vm.state.value.notice)
    }

    @Test
    fun download_offline_isRefusedAndSaysWhy() {
        network.online = false
        vm.download("p5")
        assertTrue(downloads.started.isEmpty())
        assertFalse(vm.state.value.online)
        assertEquals(UiText.Res(R.string.property_download_offline), vm.state.value.notice)
    }

    @Test
    fun checkConnection_followsTheNetwork() {
        network.online = false
        vm.checkConnection()
        assertFalse(vm.state.value.online)
        network.online = true
        vm.checkConnection()
        assertTrue(vm.state.value.online)
    }

    @Test
    fun aFailedDownload_isAnnouncedOnceByName() {
        downloads.flow.value = downloads.flow.value.copy(active = mapOf("p5" to PropertyDownload.Failed))
        assertEquals(UiText.Res(R.string.property_download_failed, listOf("Harbour Point Office Tower")), vm.state.value.notice)
        vm.consumeNotice()
        downloads.flow.value = downloads.flow.value.copy(active = mapOf("p5" to PropertyDownload.Failed, "p6" to PropertyDownload.Running(0.5f, 1.0, 2.0)))
        assertNull(vm.state.value.notice) // the same failure isn't announced twice
    }

    @Test
    fun reset_forgetsTheSearchAndClosesEverything() {
        vm.setQuery("x")
        vm.openAdd()
        vm.reset()
        val s = vm.state.value
        assertEquals("", s.query)
        assertFalse(s.addOpen || s.importOpen)
        assertEquals(s.propertyTotal, s.rows.size)
    }
}
