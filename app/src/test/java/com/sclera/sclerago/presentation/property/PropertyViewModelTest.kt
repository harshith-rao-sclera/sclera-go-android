package com.sclera.sclerago.presentation.property

import com.sclera.sclerago.R
import com.sclera.sclerago.data.repository.AssetRepositoryImpl
import com.sclera.sclerago.data.repository.OnboardingRepositoryImpl
import com.sclera.sclerago.data.repository.PropertyRepositoryImpl
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.domain.usecase.media.WritePropertyTemplateUseCase
import com.sclera.sclerago.domain.usecase.property.AddPropertyUseCase
import com.sclera.sclerago.domain.usecase.property.FilterPropertySummariesUseCase
import com.sclera.sclerago.domain.usecase.property.ObserveOnboardingUseCase
import com.sclera.sclerago.domain.usecase.property.ObservePropertiesUseCase
import com.sclera.sclerago.domain.usecase.property.ObservePropertySummariesUseCase
import com.sclera.sclerago.domain.usecase.property.PropertyUseCases
import com.sclera.sclerago.domain.usecase.property.SetOnboardingUseCase
import com.sclera.sclerago.domain.usecase.property.ValidateImportFileUseCase
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.testutil.FakeFileRepository
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
import java.io.ByteArrayOutputStream

/** ViewModel → use cases → the real in-memory repositories. */
@OptIn(ExperimentalCoroutinesApi::class)
class PropertyViewModelTest {
    private val properties = PropertyRepositoryImpl()
    private val assets = AssetRepositoryImpl()
    private val files = FakeFileRepository()
    private lateinit var vm: PropertyViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val onboarding = OnboardingRepositoryImpl()
        vm = PropertyViewModel(
            PropertyUseCases(
                ObservePropertiesUseCase(properties), AddPropertyUseCase(properties),
                ObserveOnboardingUseCase(onboarding), SetOnboardingUseCase(onboarding),
                ObservePropertySummariesUseCase(properties, assets), FilterPropertySummariesUseCase(), ValidateImportFileUseCase(),
            ),
            WritePropertyTemplateUseCase(files),
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun startsWithEveryPropertyAndTheTotals() {
        val s = vm.state.value
        assertEquals(properties.properties.value.size, s.rows.size)
        assertEquals(s.rows.size, s.propertyTotal)
        assertEquals(s.rows.sumOf { it.assets }, s.assetTotal)
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
    fun addProperty_validDraft_addsItClosesTheSheetAndSetsANotice() {
        vm.openAdd()
        assertNull(vm.addProperty(Property("", "Depot", false, type = "Warehouse")))
        val s = vm.state.value
        assertFalse(s.addOpen)
        assertTrue(s.rows.any { it.property.name == "Depot" && it.assets == 0 })
        assertEquals(UiText.Res(R.string.property_added, listOf("Depot")), s.notice)
        vm.consumeNotice()
        assertNull(vm.state.value.notice)
    }

    @Test
    fun addProperty_invalidDraft_returnsWhyAndChangesNothing() {
        vm.openAdd()
        val before = vm.state.value.propertyTotal
        assertEquals(UiText.Res(R.string.property_problem_name), vm.addProperty(Property("", " ", false, type = "Office")))
        assertEquals(UiText.Res(R.string.property_problem_type), vm.addProperty(Property("", "New", false)))
        assertEquals(
            UiText.Res(R.string.property_problem_duplicate, listOf("test 861")),
            vm.addProperty(Property("", " test 861 ", false, type = "Office")),
        )
        assertEquals(before, vm.state.value.propertyTotal)
        assertTrue(vm.state.value.addOpen)
        assertNull(vm.state.value.notice)
    }

    @Test
    fun importProblem_mapsTheRulesToMessages() {
        assertNull(vm.importProblem("sites.xlsx", 10))
        assertEquals(UiText.Res(R.string.import_error_type), vm.importProblem("sites.csv", 10))
        assertEquals(UiText.Res(R.string.import_error_size), vm.importProblem("sites.xlsx", 101L * 1024 * 1024))
    }

    @Test
    fun importFile_closesTheSheetAndSaysImportIsNotConnected() {
        vm.openImport()
        vm.importFile("sites.xlsx")
        assertFalse(vm.state.value.importOpen)
        assertEquals(UiText.Res(R.string.property_import_notice, listOf("sites.xlsx")), vm.state.value.notice)
    }

    @Test
    fun writeSample_asksTheFileRepositoryForTheTemplate() {
        vm.writeSample(ByteArrayOutputStream())
        assertTrue(files.templateWritten)
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
