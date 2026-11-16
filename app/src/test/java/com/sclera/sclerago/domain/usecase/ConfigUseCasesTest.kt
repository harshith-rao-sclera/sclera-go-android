package com.sclera.sclerago.domain.usecase

import com.sclera.sclerago.data.local.DEFAULT_PHOTO_REQS
import com.sclera.sclerago.domain.model.AssetConfig
import com.sclera.sclerago.presentation.preview.SAMPLE_FIELDS
import com.sclera.sclerago.testutil.InMemoryAssetConfig
import com.sclera.sclerago.testutil.allowAllGuard
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FieldKind
import com.sclera.sclerago.domain.model.FieldSection
import com.sclera.sclerago.domain.model.ValueRule
import com.sclera.sclerago.domain.usecase.config.AddFieldUseCase
import com.sclera.sclerago.domain.usecase.config.AddFieldsUseCase
import com.sclera.sclerago.domain.usecase.config.AddPhotoReqUseCase
import com.sclera.sclerago.domain.usecase.config.ImportFieldsUseCase
import com.sclera.sclerago.domain.usecase.config.ObserveAssetConfigUseCase
import com.sclera.sclerago.domain.usecase.config.RemoveFieldUseCase
import com.sclera.sclerago.domain.usecase.config.RemovePhotoReqUseCase
import com.sclera.sclerago.domain.usecase.config.SaveFieldUseCase
import com.sclera.sclerago.domain.usecase.config.SetFieldChecksUseCase
import com.sclera.sclerago.domain.usecase.config.SetFieldOptionsUseCase
import com.sclera.sclerago.domain.usecase.config.SetGeoEnforceUseCase
import com.sclera.sclerago.domain.usecase.config.SetPhotoLimitsUseCase
import com.sclera.sclerago.domain.usecase.config.ToggleFieldRequiredUseCase
import com.sclera.sclerago.domain.usecase.config.ToggleFieldVisibleUseCase
import com.sclera.sclerago.testutil.FakeFileRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigUseCasesTest {
    private val repo = InMemoryAssetConfig(AssetConfig(SAMPLE_FIELDS, DEFAULT_PHOTO_REQS))
    private val guard = allowAllGuard()
    private val fields get() = repo.config.value.fields
    private fun field(key: String) = fields.first { it.key == key }

    @Test
    fun observe_exposesTheRepositoryConfig() = assertEquals(repo.config, ObserveAssetConfigUseCase(repo)())

    @Test
    fun toggleVisible_respectsLockedAndMandatoryFields() = runTest {
        val toggle = ToggleFieldVisibleUseCase(repo, guard)
        assertEquals(ToggleFieldVisibleUseCase.Result.UNCHANGED, toggle("nope"))
        assertEquals(ToggleFieldVisibleUseCase.Result.UNCHANGED, toggle("name"))
        assertEquals(ToggleFieldVisibleUseCase.Result.MANDATORY, toggle("sys"))
        assertEquals(ToggleFieldVisibleUseCase.Result.CHANGED, toggle("make"))
        assertFalse(field("make").visible)
        toggle("make")
        assertTrue(field("make").visible)
    }

    @Test
    fun toggleRequired_makesFieldsVisibleAndKeepsLockedOnes() = runTest {
        val toggle = ToggleFieldRequiredUseCase(repo, guard)
        ToggleFieldVisibleUseCase(repo, guard)("make")
        toggle("make")
        assertTrue(field("make").required)
        assertTrue(field("make").visible)
        toggle("name")
        assertTrue(field("name").required)
        toggle("make")
        assertFalse(field("make").required)
    }

    @Test
    fun addAndRemoveField_onlyCustomFieldsGoAway() = runTest {
        assertFalse(AddFieldUseCase(repo, guard)("  ", FieldKind.TEXT, false))
        assertTrue(AddFieldUseCase(repo, guard)(" Colour ", FieldKind.TEXT, true))
        val custom = fields.single { it.custom }
        assertEquals("Colour", custom.label)
        assertTrue(custom.required)
        RemoveFieldUseCase(repo, guard)("make")
        assertNotNull(field("make"))
        RemoveFieldUseCase(repo, guard)(custom.key)
        assertTrue(fields.none { it.custom })
    }

    @Test
    fun saveField_cleansAndEnforcesRules() = runTest {
        val save = SaveFieldUseCase(repo, guard)
        assertFalse(save(field("make").copy(label = " ")))
        assertTrue(save(field("make").copy(label = " Maker ", tooltip = " t ", options = listOf(" a ", "a", "", "b"), required = true, visible = false)))
        val f = field("make")
        assertEquals("Maker", f.label)
        assertEquals("t", f.tooltip)
        assertEquals(listOf("a", "b"), f.options)
        assertTrue(f.visible)
        assertTrue(save(field("name").copy(required = false, visible = false)))
        assertTrue(field("name").required && field("name").visible)
    }

    @Test
    fun addFields_skipsExistingNames() = runTest {
        val add = AddFieldsUseCase(repo, guard)
        val n = add(listOf(
            FieldCfg("a", "manufacturer", FieldKind.TEXT, FieldSection.IDENT),
            FieldCfg("b", " Colour ", FieldKind.TEXT, FieldSection.IDENT, required = true, visible = false),
            FieldCfg("c", " ", FieldKind.TEXT, FieldSection.IDENT),
        ))
        assertEquals(1, n)
        val added = fields.last()
        assertEquals("Colour", added.label)
        assertEquals(FieldSection.MORE, added.section)
        assertTrue(added.custom && added.visible)
        assertEquals(0, add(emptyList()))
    }

    @Test
    fun importFields_infersKindsFromTheColumnValues() = runTest {
        val files = FakeFileRepository()
        val import = ImportFieldsUseCase(repo, files)
        files.sheet = null
        assertEquals(ImportFieldsUseCase.Result.Unreadable, import("u"))
        files.sheet = listOf(
            listOf("Weight", "Bought", "Active", "Site", "Kind", "Remarks", "Empty"),
            listOf("1.5", "2020-01-02", "yes", "https://a", "A", "free text one", ""),
            listOf("2", "03/04/2021", "No", "http://b", "B", "free text two", ""),
            listOf("3", "5.6.2022", "y", "https://c", "A", "free text three", ""),
            listOf("4", "7-8-2023", "n", "https://d", "B", "free text four", ""),
        )
        val result = import("u") as ImportFieldsUseCase.Result.Fields
        val kinds = result.fields.associate { it.label to it.kind }
        assertEquals(FieldKind.NUMBER, kinds["Weight"])
        assertEquals(FieldKind.DATE, kinds["Bought"])
        assertEquals(FieldKind.YESNO, kinds["Active"])
        assertEquals(FieldKind.URL, kinds["Site"])
        assertEquals(FieldKind.DROPDOWN, kinds["Kind"])
        assertEquals(FieldKind.TEXT, kinds["Remarks"])
        assertEquals(FieldKind.TEXT, kinds["Empty"])
        assertEquals(listOf("A", "B"), result.fields.first { it.label == "Kind" }.options)
        assertTrue(fields.none { it.custom })
    }

    @Test
    fun importFields_reportsWhenNothingIsNew() = runTest {
        val files = FakeFileRepository(listOf(listOf("Name", "model", " ")))
        assertEquals(ImportFieldsUseCase.Result.NothingNew, ImportFieldsUseCase(repo, files)("u"))
    }

    @Test
    fun setChecksAndOptions_updateOneField() = runTest {
        SetFieldChecksUseCase(repo, guard)("make", true, ValueRule.ALNUM, " 2 ", " 9 ")
        val f = field("make")
        assertTrue(f.flagEmpty)
        assertEquals(ValueRule.ALNUM, f.rule)
        assertEquals("2", f.min)
        assertEquals("9", f.max)
        SetFieldOptionsUseCase(repo, guard)("make", listOf(" x ", "x", "", "y"))
        assertEquals(listOf("x", "y"), field("make").options)
    }

    @Test
    fun photoRequirements_areClampedAddedAndRemoved() = runTest {
        val id = repo.config.value.photoReqs.first().id
        SetPhotoLimitsUseCase(repo, guard)(id, 12, 0)
        assertEquals(9 to 9, repo.config.value.photoReqs.first().let { it.min to it.max })
        SetPhotoLimitsUseCase(repo, guard)(id, -3, 0)
        assertEquals(0 to 1, repo.config.value.photoReqs.first().let { it.min to it.max })
        val before = repo.config.value.photoReqs.size
        assertFalse(AddPhotoReqUseCase(repo, guard)(" ", "h"))
        assertTrue(AddPhotoReqUseCase(repo, guard)(" Plate ", " close up "))
        assertEquals(before + 1, repo.config.value.photoReqs.size)
        RemovePhotoReqUseCase(repo, guard)(repo.config.value.photoReqs.last().id)
        assertEquals(before, repo.config.value.photoReqs.size)
    }

    @Test
    fun geoEnforce_canBeSwitched() = runTest {
        SetGeoEnforceUseCase(repo, guard)(true)
        assertTrue(repo.config.value.geoEnforce)
    }
}
