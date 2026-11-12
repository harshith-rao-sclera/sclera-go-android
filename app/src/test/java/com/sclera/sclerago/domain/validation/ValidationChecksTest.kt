package com.sclera.sclerago.domain.validation

import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FieldKind
import com.sclera.sclerago.domain.model.FieldSection
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.model.ValueRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ValidationChecksTest {
    private val good = mapOf("name" to "Pump 1", "sys" to "HVAC", "typ" to "Pump", "jacs" to "A1")

    private fun input(
        values: Map<String, String> = good,
        hasLocation: Boolean = true,
        status: Status = Status.DRAFT,
        fromCamera: Boolean = false,
        photoCount: Int = 0,
        minPhotos: Int = 1,
        fieldChecks: List<FieldCfg> = emptyList(),
    ) = CheckInput(values, hasLocation, status, fromCamera, photoCount, minPhotos = minPhotos, fieldChecks = fieldChecks)

    private fun cfg(kind: FieldKind, rule: ValueRule = ValueRule.NONE, min: String = "", max: String = "") =
        FieldCfg("f", "Field", kind, FieldSection.MORE, rule = rule, min = min, max = max)

    private fun run(index: Int, input: CheckInput) = runCheck(index, input)

    @Test
    fun mandatory_passesWhenAllRequiredAreFilled() = assertEquals(CheckState.PASSED, run(0, input()).state)

    @Test
    fun mandatory_failsForEmptyAndGenericValues() {
        val r = run(0, input(good + ("name" to " ") + ("typ" to "Generic")))
        assertEquals(CheckState.FAILED, r.state)
        assertEquals(2, r.messages.size)
        assertEquals("Edit Name", r.fixLabel)
        assertEquals(FixTarget.IDENT, r.fix)
    }

    @Test
    fun mandatory_flagsOptionalFieldsSetToFlagWhenEmpty() {
        val flagged = FieldCfg("make", "Make", FieldKind.TEXT, FieldSection.IDENT, flagEmpty = true)
        val r = run(0, input(fieldChecks = listOf(flagged)))
        assertEquals(CheckState.FAILED, r.state)
        assertEquals("Make is empty.", r.messages.single())
    }

    @Test
    fun formats_acceptsGoodValuesAndRejectsBadOnes() {
        assertEquals(CheckState.PASSED, run(1, input(good + mapOf("label" to "SCL-123456", "ble" to "SCL-1234", "ip" to "10.0.0.1", "serial" to "AB-12"))).state)
        val bad = run(1, input(good + mapOf("label" to "X", "ble" to "Y", "ip" to "300.1.1.1", "serial" to "a b")))
        assertEquals(CheckState.FAILED, bad.state)
        assertEquals(4, bad.messages.size)
        assertEquals(FixTarget.TAGS, bad.fix)
        assertEquals("Edit Label / QR", bad.fixLabel)
    }

    @Test
    fun formats_rejectsMalformedIpv4() {
        listOf("1.2.3", "a.b.c.d", "1.2.3.4.5", "1..2.3", "1.2.3.1234").forEach {
            assertEquals(it, CheckState.FAILED, run(1, input(good + ("ip" to it))).state)
        }
    }

    @Test
    fun formats_appliesConfiguredFieldRules() {
        val f = FieldCfg("url", "Link", FieldKind.URL, FieldSection.MORE)
        val r = run(1, input(good + ("url" to "ftp://x"), fieldChecks = listOf(f)))
        assertEquals(CheckState.FAILED, r.state)
        assertTrue(r.messages.single().startsWith("Link"))
    }

    @Test
    fun placeholders_areFlaggedExceptInNotes() {
        assertEquals(CheckState.PASSED, run(2, input(good + ("note" to "tbd"))).state)
        val r = run(2, input(good + ("make" to "TBD")))
        assertEquals(CheckState.FAILED, r.state)
        assertEquals("Edit Make", r.fixLabel)
    }

    @Test
    fun dates_isNotApplicable() = assertEquals(CheckState.NA, run(3, input()).state)

    @Test
    fun ranges_checkNumbersUnitsAndLimits() {
        assertEquals(CheckState.PASSED, run(4, input(good + mapOf("power" to "5 kW", "voltage" to "400", "speed" to "1450 rpm"))).state)
        val r = run(4, input(good + mapOf("power" to "abc", "voltage" to "400 kW", "speed" to "99999")))
        assertEquals(CheckState.FAILED, r.state)
        assertEquals(3, r.messages.size)
        assertEquals(CheckState.FAILED, run(4, input(good + ("power" to "0"))).state)
    }

    @Test
    fun consistency_comparesNameWithType() {
        assertEquals(CheckState.PASSED, run(5, input()).state)
        assertEquals(CheckState.PASSED, run(5, input(good + ("typ" to ""))).state)
        assertEquals(CheckState.PASSED, run(5, input(good + ("name" to "Thing"))).state)
        val r = run(5, input(good + ("typ" to "Motor")))
        assertEquals(CheckState.FAILED, r.state)
        assertEquals(FixTarget.CLASS, r.fix)
    }

    @Test
    fun lifecycle_failsOnlyForExceptions() {
        assertEquals(CheckState.PASSED, run(6, input()).state)
        assertEquals(CheckState.FAILED, run(6, input(status = Status.EXCEPTION)).state)
    }

    @Test
    fun location_needsALocation() {
        assertEquals(CheckState.PASSED, run(7, input()).state)
        assertEquals(FixTarget.LOCATION, run(7, input(hasLocation = false)).fix)
    }

    @Test
    fun photos_onlyApplyToCameraOnboarding() {
        assertEquals(CheckState.NA, run(8, input()).state)
        assertEquals(CheckState.PASSED, run(8, input(fromCamera = true, photoCount = 1)).state)
        val one = run(8, input(fromCamera = true, photoCount = 0))
        assertEquals(CheckState.FAILED, one.state)
        val many = run(8, input(fromCamera = true, photoCount = 1, minPhotos = 3))
        assertTrue(many.messages.single().contains("3"))
        assertEquals(FixTarget.PHOTOS, many.fix)
    }

    @Test
    fun runAllChecks_returnsOneResultPerCheck() = assertEquals(CHECK_NAMES.size, runAllChecks(input()).size)

    @Test
    fun dataQuality_isTheShareOfApplicableChecksThatPass() {
        assertEquals(100, dataQuality(emptyList()))
        assertEquals(100, dataQuality(listOf(CheckResult("a", CheckState.NA))))
        assertEquals(50, dataQuality(listOf(CheckResult("a", CheckState.PASSED), CheckResult("b", CheckState.FAILED), CheckResult("c", CheckState.NA))))
    }

    // ---- fieldProblem

    @Test
    fun fieldProblem_textRules() {
        assertNull(fieldProblem(cfg(FieldKind.TEXT, ValueRule.ALNUM), "abc 123"))
        assertNotNull(fieldProblem(cfg(FieldKind.TEXT, ValueRule.ALNUM), "a-b"))
        assertNotNull(fieldProblem(cfg(FieldKind.TEXT, ValueRule.DIGITS), "12a"))
        assertNotNull(fieldProblem(cfg(FieldKind.TEXT, ValueRule.CODE), "abc"))
        assertNull(fieldProblem(cfg(FieldKind.TEXT, ValueRule.CODE), "AB-12"))
        assertNotNull(fieldProblem(cfg(FieldKind.TEXT, ValueRule.NO_SPACES), "a b"))
        assertNotNull(fieldProblem(cfg(FieldKind.TEXT, ValueRule.EMAIL), "nope"))
        assertNull(fieldProblem(cfg(FieldKind.TEXT, ValueRule.EMAIL), "a@b.co"))
        assertNotNull(fieldProblem(cfg(FieldKind.TEXTAREA, min = "5"), "abc"))
        assertNotNull(fieldProblem(cfg(FieldKind.TEXT, max = "2"), "abc"))
        assertNull(fieldProblem(cfg(FieldKind.TEXT), "anything"))
    }

    @Test
    fun fieldProblem_numberRules() {
        assertNotNull(fieldProblem(cfg(FieldKind.NUMBER), "x"))
        assertNotNull(fieldProblem(cfg(FieldKind.NUMBER, ValueRule.WHOLE), "1.5"))
        assertNotNull(fieldProblem(cfg(FieldKind.NUMBER, ValueRule.POSITIVE), "0"))
        assertEquals("should be at least 5", fieldProblem(cfg(FieldKind.NUMBER, min = "5"), "2"))
        assertEquals("should be at most 2.5", fieldProblem(cfg(FieldKind.NUMBER, max = "2.5"), "3"))
        assertNull(fieldProblem(cfg(FieldKind.NUMBER, min = "1", max = "9"), "5 kW"))
    }

    @Test
    fun fieldProblem_dateRules() {
        val year = LocalDate.now().year
        assertNotNull(fieldProblem(cfg(FieldKind.DATE), "garbage"))
        assertNotNull(fieldProblem(cfg(FieldKind.DATE), "2024-13-45"))
        assertNull(fieldProblem(cfg(FieldKind.DATE), "31/03/2020"))
        assertNull(fieldProblem(cfg(FieldKind.DATE), "31.03.2020"))
        assertEquals("is in the future", fieldProblem(cfg(FieldKind.DATE, ValueRule.PAST), "${year + 5}-01-01"))
        assertEquals("should be in the future", fieldProblem(cfg(FieldKind.DATE, ValueRule.FUTURE), "2000-01-01"))
        assertEquals("is before 2010", fieldProblem(cfg(FieldKind.DATE, min = "2010"), "2000-01-01"))
        assertEquals("is after 2010", fieldProblem(cfg(FieldKind.DATE, max = "2010"), "2020-01-01"))
    }

    @Test
    fun fieldProblem_urlTimeAndChoiceKinds() {
        assertNull(fieldProblem(cfg(FieldKind.URL), "https://x.io/a"))
        assertNotNull(fieldProblem(cfg(FieldKind.URL), "x.io"))
        assertNull(fieldProblem(cfg(FieldKind.TIME), "23:59"))
        assertNull(fieldProblem(cfg(FieldKind.TIME), "9:30 pm"))
        assertNotNull(fieldProblem(cfg(FieldKind.TIME), "25:00"))
        listOf(FieldKind.DROPDOWN, FieldKind.MULTI, FieldKind.CHECKBOX, FieldKind.YESNO).forEach {
            assertNull(fieldProblem(cfg(it), "whatever"))
        }
    }
}
