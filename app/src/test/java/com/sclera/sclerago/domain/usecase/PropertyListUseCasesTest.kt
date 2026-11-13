package com.sclera.sclerago.domain.usecase

import com.sclera.sclerago.data.repository.AssetRepositoryImpl
import com.sclera.sclerago.data.repository.PropertyRepositoryImpl
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.domain.model.PropertySummary
import com.sclera.sclerago.domain.usecase.property.FilterPropertySummariesUseCase
import com.sclera.sclerago.domain.usecase.property.ImportFileProblem
import com.sclera.sclerago.domain.limits.MAX_IMPORT_BYTES
import com.sclera.sclerago.domain.usecase.property.ObservePropertySummariesUseCase
import com.sclera.sclerago.domain.usecase.property.ValidateImportFileUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PropertyListUseCasesTest {
    private val properties = PropertyRepositoryImpl()
    private val assets = AssetRepositoryImpl()

    @Test
    fun summaries_pairEveryPropertyWithItsAssetCount() = runTest {
        val summaries = ObservePropertySummariesUseCase(properties, assets)().first()
        assertEquals(properties.properties.value, summaries.map { it.property })
        summaries.forEach { assertEquals(assets.catalog.value.countIn(it.property.id), it.assets) }
        assertTrue("the seeded current property has assets", summaries.first { it.property.id == "p1" }.assets > 0)
    }

    @Test
    fun summaries_includeAPropertyAddedLater_withNoAssets() = runTest {
        properties.add(Property("px", "Depot", false))
        val added = ObservePropertySummariesUseCase(properties, assets)().first().single { it.property.id == "px" }
        assertEquals(0, added.assets)
    }

    private val all = listOf("Tower", "Harbour Depot", "Riverside").map { PropertySummary(Property(it, it, false), 0) }

    @Test
    fun filter_matchesPartOfTheNameIgnoringCaseAndBlanks() {
        val filter = FilterPropertySummariesUseCase()
        assertEquals(listOf("Harbour Depot"), filter(all, "  HARB ").map { it.property.name })
        assertEquals(listOf("Tower", "Riverside"), filter(all, "r").map { it.property.name })
    }

    @Test
    fun filter_blankQueryKeepsEverything_andNoMatchGivesNothing() {
        val filter = FilterPropertySummariesUseCase()
        assertEquals(all, filter(all, "   "))
        assertTrue(filter(all, "zzz").isEmpty())
    }

    @Test
    fun validateImport_acceptsXlsxWithinTheLimit_orWhenTheSizeIsUnknown() {
        val validate = ValidateImportFileUseCase()
        assertNull(validate("sites.xlsx", 1024))
        assertNull(validate("SITES.XLSX", MAX_IMPORT_BYTES))
        assertNull(validate("sites.xlsx", -1))
    }

    @Test
    fun validateImport_refusesOtherTypesAndOversizeFiles() {
        val validate = ValidateImportFileUseCase()
        assertEquals(ImportFileProblem.NOT_XLSX, validate("sites.csv", 10))
        assertEquals(ImportFileProblem.NOT_XLSX, validate("sites.xlsx.exe", 10))
        assertEquals(ImportFileProblem.NOT_XLSX, validate("", 10))
        assertEquals(ImportFileProblem.TOO_LARGE, validate("sites.xlsx", MAX_IMPORT_BYTES + 1))
    }
}
