package com.sclera.sclerago.domain.validation

import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.testutil.asset
import com.sclera.sclerago.testutil.checkInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationRulesTest {
    private val good = mapOf(
        "name" to "Pump 1", "sys" to "HVAC", "typ" to "Pump", "jacs" to "A1",
        "make" to "Acme", "model" to "X1", "power" to "5 kW", "voltage" to "400",
    )

    private fun verdict(
        ruleId: String,
        values: Map<String, String> = good,
        others: List<Pair<Asset, CheckInput>> = emptyList(),
        photos: Int = 1,
        loc: Boolean = true,
        id: Int = 1,
        name: String = "Pump 1",
    ): RuleVerdict = RULES.getValue(ruleId).eval(RuleCtx(asset(id = id, name = name), checkInput(values, photos, loc), others))

    @Test
    fun everyTemplateAndSavedValidation_usesKnownRules() {
        TEMPLATES.forEach { t -> assertEquals(t.id, t.ruleIds.size, t.rules.size) }
        DEFAULT_SAVED.forEach { s -> assertEquals(s.ruleIds.size, s.rules.size) }
        RULES.keys.forEach { assertTrue("no description for $it", it in RULE_DESC) }
        assertTrue(ACROSS_RULES.all { it in RULES })
    }

    @Test
    fun basicRules_mapTheChecks() {
        assertEquals(RuleOutcome.PASS, verdict("mandatory").outcome)
        assertEquals(RuleOutcome.FAIL, verdict("mandatory", good - "name").outcome)
        assertEquals(RuleOutcome.NA, verdict("dates").outcome)
        assertEquals(RuleOutcome.LOOK, verdict("outliers", good + ("power" to "999999")).outcome)
    }

    @Test
    fun locationAndPhotoRules() {
        assertEquals(RuleOutcome.FAIL, verdict("location", loc = false).outcome)
        assertEquals(RuleOutcome.PASS, verdict("location").outcome)
        assertEquals(RuleOutcome.FAIL, verdict("photo", photos = 0).outcome)
        assertEquals(RuleOutcome.PASS, verdict("photo").outcome)
    }

    @Test
    fun duplicateRules_lookAtTheOtherAssets() {
        val other = asset(id = 2, name = "pump 1") to checkInput(good + ("serial" to "S1"))
        assertEquals(RuleOutcome.FAIL, verdict("dup_entered_twice", others = listOf(other)).outcome)
        assertEquals(RuleOutcome.PASS, verdict("dup_entered_twice").outcome)
        assertEquals(RuleOutcome.FAIL, verdict("dup_serial", good + ("serial" to "S1"), listOf(other)).outcome)
        assertEquals(RuleOutcome.PASS, verdict("dup_serial").outcome)
        val three = listOf(other, asset(id = 3, name = "Pump 1") to checkInput(good))
        assertEquals(RuleOutcome.LOOK, verdict("dup_names", others = three).outcome)
        assertEquals(RuleOutcome.PASS, verdict("dup_names").outcome)
    }

    @Test
    fun consistencyRules() {
        val twin = asset(id = 2, name = "Pump 2") to checkInput(good + ("make" to "ACME") + ("power" to "9 kW"))
        assertEquals(RuleOutcome.LOOK, verdict("make_consistent", others = listOf(twin)).outcome)
        assertEquals(RuleOutcome.PASS, verdict("make_consistent").outcome)
        assertEquals(RuleOutcome.LOOK, verdict("spec_twins", others = listOf(twin)).outcome)
        assertEquals(RuleOutcome.PASS, verdict("spec_twins").outcome)
    }

    @Test
    fun specGaps_countsEmptySpecFields() {
        assertEquals(RuleOutcome.PASS, verdict("spec_gaps").outcome)
        assertTrue(verdict("spec_gaps", good - "make").message!!.startsWith("1 spec field "))
        assertTrue(verdict("spec_gaps", good - "make" - "model").message!!.startsWith("2 spec fields "))
    }

    @Test
    fun simulatedAiRules_areStablePerAssetAndHandleMissingData() {
        assertEquals(RuleOutcome.LOOK, verdict("spec_published", good - "make").outcome)
        assertEquals(RuleOutcome.LOOK, verdict("ai_photos", photos = 0).outcome)
        (1..60).forEach { id ->
            listOf("spec_published", "ai_read", "ai_photos", "one_off").forEach { r ->
                assertEquals(verdict(r, id = id), verdict(r, id = id))
            }
        }
        val outcomes = (1..200).map { verdict("ai_read", id = it).outcome }.toSet()
        assertEquals(setOf(RuleOutcome.PASS, RuleOutcome.LOOK, RuleOutcome.FAIL), outcomes)
    }

    @Test
    fun runValidation_rollsUpTheWorstOutcome() {
        val a = asset(1) to checkInput(good)
        val b = asset(2, "Pump 2") to checkInput(good - "name", hasLocation = false)
        val results = runValidation(listOf(RULES.getValue("mandatory"), RULES.getValue("location")), listOf(a, b), listOf(a, b))
        assertEquals(RuleOutcome.PASS, results[0].outcome)
        assertTrue(results[0].findings.isEmpty())
        assertEquals(RuleOutcome.FAIL, results[1].outcome)
        assertEquals(2, results[1].findings.size)
        val look = runValidation(listOf(RULES.getValue("ai_photos")), listOf(asset(1) to checkInput(good, photoCount = 0)), emptyList())
        assertEquals(RuleOutcome.LOOK, look.single().outcome)
    }

    @Test
    fun valJob_countsItsResults() {
        val results = listOf(
            AssetResult("1", "a", RuleOutcome.PASS, emptyList()),
            AssetResult("2", "b", RuleOutcome.FAIL, emptyList()),
            AssetResult("3", "c", RuleOutcome.LOOK, emptyList()),
        )
        val job = ValJob("1", "t", "quick", 1, "All", listOf("1", "2", "3"), 0L, "p1", running = false, results = results)
        assertEquals(listOf(1, 1, 1), listOf(job.passed, job.failed, job.look))
    }
}
