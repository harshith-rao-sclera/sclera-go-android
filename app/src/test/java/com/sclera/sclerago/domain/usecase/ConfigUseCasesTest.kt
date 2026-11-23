package com.sclera.sclerago.domain.usecase

import com.sclera.sclerago.domain.model.AssetConfig
import com.sclera.sclerago.domain.model.DEFAULT_PHOTO_RULES
import com.sclera.sclerago.domain.model.FieldChecks
import com.sclera.sclerago.domain.model.FieldKind
import com.sclera.sclerago.domain.model.PhotoKind
import com.sclera.sclerago.domain.model.ValueRule
import com.sclera.sclerago.domain.usecase.config.FieldProblem
import com.sclera.sclerago.domain.usecase.config.ObserveAssetConfigUseCase
import com.sclera.sclerago.domain.usecase.config.PhotoBound
import com.sclera.sclerago.domain.usecase.config.PhotoStepBlock
import com.sclera.sclerago.domain.usecase.config.cleanChecks
import com.sclera.sclerago.domain.usecase.config.cleaned
import com.sclera.sclerago.domain.usecase.config.fieldSetupProblem
import com.sclera.sclerago.domain.usecase.config.nameTaken
import com.sclera.sclerago.domain.usecase.config.photoRulesValid
import com.sclera.sclerago.domain.usecase.config.photoStepBlock
import com.sclera.sclerago.domain.usecase.config.stepPhotoRule
import com.sclera.sclerago.presentation.preview.SAMPLE_FIELDS
import com.sclera.sclerago.testutil.InMemoryAssetConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigUseCasesTest {
    private val repo = InMemoryAssetConfig(AssetConfig(SAMPLE_FIELDS))
    private val fields get() = repo.config.value.fields
    private fun field(key: String) = fields.first { it.key == key }

    @Test
    fun observe_exposesTheRepositoryConfig() = assertEquals(repo.config, ObserveAssetConfigUseCase(repo)())

    @Test
    fun saveField_cleansAndEnforcesRules() {
        assertEquals(FieldProblem.NO_NAME, fieldSetupProblem(field("make").copy(label = " "), fields))
        val f = field("make").copy(label = " Maker ", tooltip = " t ", options = listOf(" a ", "a", "", "b"), required = true, visible = false).cleaned()
        assertEquals("Maker", f.label)
        assertEquals("t", f.tooltip)
        assertEquals(listOf("a", "b"), f.options)
        assertTrue(f.visible)
        val name = field("name").copy(required = false, visible = false).cleaned()
        assertTrue(name.required && name.visible)
    }

    @Test
    fun addFields_skipsExistingNames() {
        assertTrue(nameTaken(fields, "manufacturer", "a"))
        assertFalse(nameTaken(fields, " Colour ", "b"))
        assertFalse(nameTaken(fields, " ", "c"))
    }

    @Test
    fun setChecks_keepsWhatFitsTheField() {
        val c = cleanChecks(FieldKind.TEXT, FieldChecks(flagEmpty = true, rule = ValueRule.ALNUM, min = " 2 ", max = " 9 "), required = false)
        assertTrue(c.flagEmpty)
        assertEquals(ValueRule.ALNUM, c.rule)
        assertEquals("2", c.min)
        assertEquals("9", c.max)
    }

    @Test
    fun photoRules_stepWithinTheServerLimits() {
        val rules = DEFAULT_PHOTO_RULES
        assertEquals(PhotoStepBlock.NONE_REQUIRED, photoStepBlock(rules, PhotoKind.NAMEPLATE, PhotoBound.MIN, up = false))
        assertEquals(PhotoStepBlock.TOTAL_LIMIT, photoStepBlock(rules, PhotoKind.NAMEPLATE, PhotoBound.MAX, up = true))
        val raised = stepPhotoRule(rules, PhotoKind.NAMEPLATE, PhotoBound.MIN, up = true)
        assertNotNull(raised)
        assertEquals(1, raised!!.first { it.kind == PhotoKind.NAMEPLATE }.min)
        assertTrue(photoRulesValid(raised))
    }
}
