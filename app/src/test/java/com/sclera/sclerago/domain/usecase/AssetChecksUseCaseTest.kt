package com.sclera.sclerago.domain.usecase

import com.sclera.sclerago.data.local.DEFAULT_FIELDS
import com.sclera.sclerago.domain.model.AssetDetails
import com.sclera.sclerago.domain.model.FieldKind
import com.sclera.sclerago.domain.model.FieldSection
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.usecase.asset.BuildFormValuesUseCase
import com.sclera.sclerago.domain.usecase.asset.EvaluateAssetChecksUseCase
import com.sclera.sclerago.domain.validation.AssetCheck
import com.sclera.sclerago.domain.validation.AssetCheckId
import com.sclera.sclerago.domain.validation.AssetCheckInput
import com.sclera.sclerago.domain.validation.CheckNote
import com.sclera.sclerago.domain.validation.CheckOutcome
import com.sclera.sclerago.domain.validation.FixTarget
import com.sclera.sclerago.testutil.FakeTimeProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class AssetChecksUseCaseTest {
    private val evaluate = EvaluateAssetChecksUseCase()
    private val complete = mapOf(
        "sys" to "Mechanical", "typ" to "Pump", "jacs" to "[JACS code 1]", "make" to "KSB", "model" to "Etanorm",
        "serial" to "9971402215",
    )

    private fun input(
        values: Map<String, String> = complete,
        hasLocation: Boolean = true,
        photos: Int = 2,
        fromCamera: Boolean = true,
        minPhotos: Int = 1,
        required: Set<String> = setOf("name", "sys", "typ", "jacs"),
        lowKey: String? = null,
    ) = AssetCheckInput(
        values, hasLocation, "Level 2 · Plant room", photos, fromCamera, minPhotos, required,
        lowConfidenceKey = lowKey, lowConfidenceLabel = "Speed",
    )

    private fun check(id: AssetCheckId, i: AssetCheckInput = input()): AssetCheck = evaluate(i).items.first { it.id == id }

    @Test
    fun everythingInPlace_passesAll_andCanValidate() {
        val result = evaluate(input())
        assertEquals(AssetCheckId.entries, result.items.map { it.id })
        assertTrue(result.items.all { it.outcome == CheckOutcome.PASS })
        assertEquals(0, result.failed)
        assertTrue(result.canValidate)
    }

    @Test
    fun passingChecks_carryTheirValueForTheSubtitle() {
        assertEquals(listOf("Level 2 · Plant room"), check(AssetCheckId.LOCATION).args)
        assertEquals(listOf("Mechanical · Pump"), check(AssetCheckId.ASSET_TYPE).args)
        assertEquals(listOf("KSB Etanorm"), check(AssetCheckId.MAKE_MODEL).args)
        assertEquals(listOf("9971402215"), check(AssetCheckId.SERIAL).args)
        assertEquals(CheckNote.VALUE, check(AssetCheckId.SERIAL).note)
        assertEquals(2, check(AssetCheckId.PHOTO).count)
        assertEquals(CheckNote.PHOTO_COUNT, check(AssetCheckId.PHOTO).note)
    }

    @Test
    fun missingJacsCode_failsAsRequired_withAFixToTheClassificationField() {
        val jacs = check(AssetCheckId.JACS_CODE, input(complete + ("jacs" to " ")))
        assertEquals(CheckOutcome.FAIL, jacs.outcome)
        assertTrue(jacs.required)
        assertEquals(CheckNote.REQUIRED_BEFORE_VALIDATE, jacs.note)
        assertEquals(FixTarget.CLASS, jacs.fix)
        assertEquals("jacs", jacs.fixField)
        val all = evaluate(input(complete - "jacs"))
        assertEquals(1, all.failed)
        assertFalse(all.canValidate)
    }

    @Test
    fun missingOptionalField_onlyWarns_andDoesNotBlockValidating() {
        val serial = check(AssetCheckId.SERIAL, input(complete - "serial"))
        assertEquals(CheckOutcome.WARN, serial.outcome)
        assertFalse(serial.required)
        assertEquals("serial", serial.fixField)
        assertTrue(evaluate(input(complete - "serial")).canValidate)
        // Once Configuration makes the serial mandatory, the same gap fails.
        val mandatory = check(AssetCheckId.SERIAL, input(complete - "serial", required = setOf("serial")))
        assertEquals(CheckOutcome.FAIL, mandatory.outcome)
        assertTrue(mandatory.required)
    }

    @Test
    fun makeAndModel_bothNeeded_andFixPointsAtTheMissingOne() {
        assertEquals("make", check(AssetCheckId.MAKE_MODEL, input(complete - "make")).fixField)
        assertEquals("model", check(AssetCheckId.MAKE_MODEL, input(complete - "model")).fixField)
        assertEquals(FixTarget.IDENT, check(AssetCheckId.MAKE_MODEL, input(complete - "make")).fix)
    }

    @Test
    fun genericAssetType_countsAsNotClassified() {
        val type = check(AssetCheckId.ASSET_TYPE, input(complete + ("typ" to "Generic")))
        assertEquals(CheckOutcome.FAIL, type.outcome)
        assertEquals("typ", type.fixField)
        assertEquals(CheckOutcome.WARN, check(AssetCheckId.ASSET_TYPE, input(complete + ("typ" to ""), required = emptySet())).outcome)
    }

    @Test
    fun noLocation_fails_withAFixThatOpensThePicker() {
        val location = check(AssetCheckId.LOCATION, input(hasLocation = false))
        assertEquals(CheckOutcome.FAIL, location.outcome)
        assertEquals(FixTarget.LOCATION, location.fix)
        assertNull(location.fixField)
    }

    @Test
    fun lowConfidenceReading_warnsUntilReviewed() {
        val warn = check(AssetCheckId.LOW_CONFIDENCE, input(lowKey = "speed"))
        assertEquals(CheckOutcome.WARN, warn.outcome)
        assertEquals(listOf("Speed"), warn.args)
        assertEquals("speed", warn.fixField)
        assertEquals(CheckOutcome.PASS, check(AssetCheckId.LOW_CONFIDENCE, input(lowKey = null)).outcome)
        // A warning alone never blocks validating.
        assertTrue(evaluate(input(lowKey = "speed")).canValidate)
    }

    @Test
    fun photos_cameraAssetsNeedTheConfiguredMinimum_othersOnlyWarn() {
        val few = check(AssetCheckId.PHOTO, input(photos = 1, minPhotos = 3))
        assertEquals(CheckOutcome.FAIL, few.outcome)
        assertEquals(1, few.count)
        assertEquals(3, few.needed)
        assertEquals(FixTarget.PHOTOS, few.fix)
        assertEquals(CheckOutcome.FAIL, check(AssetCheckId.PHOTO, input(photos = 0, minPhotos = 0)).outcome)
        val manual = check(AssetCheckId.PHOTO, input(photos = 0, fromCamera = false))
        assertEquals(CheckOutcome.WARN, manual.outcome)
        assertEquals(CheckNote.NO_PHOTO, manual.note)
        assertEquals(CheckOutcome.PASS, check(AssetCheckId.PHOTO, input(photos = 1, fromCamera = false)).outcome)
    }

    // ---- The form's starting values

    private val zone = ZoneOffset.UTC
    private val fields = DEFAULT_FIELDS + listOf(
        FieldCfg("d1", "Installed", FieldKind.DATE, FieldSection.MORE, defaultValue = "Today"),
        FieldCfg("t1", "At", FieldKind.TIME, FieldSection.MORE, defaultValue = "Now"),
        FieldCfg("k1", "Kind", FieldKind.TEXT, FieldSection.MORE, defaultValue = "Plant"),
        FieldCfg("hidden", "Hidden", FieldKind.TEXT, FieldSection.MORE, visible = false, defaultValue = "x"),
    )
    private val formValues = BuildFormValuesUseCase(FakeTimeProvider(1_793_875_320_000L)) // 5 Nov 2026, 10:42 UTC

    @Test
    fun formValues_existingAsset_startsFromWhatItCameWith() {
        val details = AssetDetails("SCL-1", mapOf("make" to "KSB", "typ" to "Pump"))
        val v = formValues("Pump 1", false, details, fields, mapOf("note" to "ignored"), zone)
        assertEquals("Pump 1", v["name"])
        assertEquals("KSB", v["make"])
        assertEquals("Pump", v["typ"])
        assertEquals("Generic", v["sys"])
        assertNull(v["d1"])
        assertNull(v["note"])
    }

    @Test
    fun formValues_newAsset_getsVisibleDefaults_theClock_andThePrefill() {
        val v = formValues("", true, null, fields, mapOf("note" to "from scan", "k1" to "Scanned"), zone)
        assertEquals("05/11/2026", v["d1"])
        assertEquals("10:42", v["t1"])
        assertEquals("Scanned", v["k1"])
        assertEquals("from scan", v["note"])
        assertNull(v["hidden"])
    }
}
