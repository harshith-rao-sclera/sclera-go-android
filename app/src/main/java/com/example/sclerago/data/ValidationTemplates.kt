package com.example.sclerago.data

import kotlin.math.absoluteValue

/** How one asset fared on one rule. [LOOK] = worth a look, not a hard failure. */
enum class RuleOutcome { PASS, LOOK, FAIL, NA }

/** Everything a rule may look at for one asset: its values, plus the rest of the property for comparisons. */
class RuleCtx(val asset: Asset, val input: CheckInput, val others: List<Pair<Asset, CheckInput>>)

data class RuleVerdict(val outcome: RuleOutcome, val message: String? = null)

/** One validation rule. [ai] rules are simulated for now (no AI or spec lookup behind them yet). */
class Rule(val id: String, val name: String, val ai: Boolean = false, val eval: (RuleCtx) -> RuleVerdict)

/** A ready-made set of rules shown on the Validation page. */
class ValidationTemplate(
    val id: String,
    val name: String,
    val tagline: String,
    val description: String,
    val ai: Boolean,
    val ruleIds: List<String>,
) {
    val rules: List<Rule> get() = ruleIds.mapNotNull { RULES[it] }
}

/** A user's own rule set ("Saved validations"). */
data class SavedValidation(val id: String, val name: String, val ruleIds: List<String>, val lastRun: Long? = null) {
    val rules: List<Rule> get() = ruleIds.mapNotNull { RULES[it] }
}

/** Which assets a run covers. */
enum class ValScope(val title: String, val sub: String) {
    ALL("All assets", "Everything collected in this property."),
    CHOOSE("Choose assets", "Only the ones you tick."),
    CHANGED("New or changed", "Assets added or edited since this check last ran."),
}

data class Finding(val rule: String, val outcome: RuleOutcome, val message: String?)

data class AssetResult(val assetId: Int, val assetName: String, val outcome: RuleOutcome, val findings: List<Finding>)

/** One validation run ("Recent jobs"). Results are filled in when the background run finishes. */
data class ValJob(
    val id: Int,
    val title: String,
    val sourceId: String,
    val checks: Int,
    val scopeLabel: String,
    val assetIds: List<Int>,
    val startedAt: Long,
    val prop: String,
    val running: Boolean = true,
    val results: List<AssetResult> = emptyList(),
) {
    val failed get() = results.count { it.outcome == RuleOutcome.FAIL }
    val look get() = results.count { it.outcome == RuleOutcome.LOOK }
    val passed get() = results.count { it.outcome == RuleOutcome.PASS }
}

// ---------------------------------------------------------------- Rules

private fun fromCheck(index: Int, failAs: RuleOutcome = RuleOutcome.FAIL): (RuleCtx) -> RuleVerdict = { c ->
    val r = runCheck(index, c.input)
    when (r.state) {
        CheckState.PASSED -> RuleVerdict(RuleOutcome.PASS)
        CheckState.NA -> RuleVerdict(RuleOutcome.NA)
        CheckState.FAILED -> RuleVerdict(failAs, r.messages.firstOrNull())
    }
}

/** Stable pseudo-random 0..99 per asset and rule, so simulated AI results don't change between runs. */
private fun roll(c: RuleCtx, salt: String) = ((c.asset.id * 7919 + salt.hashCode()).absoluteValue % 100)

private fun v(c: RuleCtx, k: String) = c.input.values[k].orEmpty().trim()

val RULES: Map<String, Rule> = listOf(
    // The basics (same checks as an asset's Validation tab).
    Rule("mandatory", "Mandatory fields are filled", eval = fromCheck(0)),
    Rule("formats", "Values fit their field", eval = fromCheck(1)),
    Rule("placeholders", "No placeholder values", eval = fromCheck(2)),
    Rule("dates", "Dates make sense", eval = fromCheck(3)),
    Rule("ranges", "Numbers are in range", eval = fromCheck(4)),
    Rule("related", "Related fields agree", eval = fromCheck(5)),
    Rule("service", "Service and warranty are current", eval = fromCheck(6)),
    Rule("location", "Every asset has a location") { c ->
        if (c.input.hasLocation) RuleVerdict(RuleOutcome.PASS) else RuleVerdict(RuleOutcome.FAIL, "No location set.")
    },
    Rule("photo", "Every asset has a photo") { c ->
        if (c.input.photoCount > 0) RuleVerdict(RuleOutcome.PASS) else RuleVerdict(RuleOutcome.FAIL, "No photo attached.")
    },

    // Specs (AI, simulated).
    Rule("spec_published", "Matches the manufacturer's published specs", ai = true) { c ->
        val r = roll(c, "spec")
        when {
            v(c, "make").isEmpty() || v(c, "model").isEmpty() -> RuleVerdict(RuleOutcome.LOOK, "No make and model to look up.")
            r < 15 -> RuleVerdict(RuleOutcome.FAIL, "Power differs from the published datasheet for ${v(c, "model")}.")
            r < 30 -> RuleVerdict(RuleOutcome.LOOK, "Voltage couldn't be confirmed from the datasheet.")
            else -> RuleVerdict(RuleOutcome.PASS)
        }
    },
    Rule("spec_twins", "Agrees with identical twins", ai = true) { c ->
        val model = v(c, "model")
        val twin = c.others.firstOrNull { (a, i) -> a.id != c.asset.id && model.isNotEmpty() && i.values["model"]?.trim() == model }
        if (twin != null && twin.second.values["power"] != c.input.values["power"]) {
            RuleVerdict(RuleOutcome.LOOK, "Same model as ${twin.first.name}, but the power is different.")
        } else RuleVerdict(RuleOutcome.PASS)
    },
    Rule("spec_gaps", "Spec gaps can be filled", ai = true) { c ->
        val empty = listOf("make", "model", "power", "voltage").count { v(c, it).isEmpty() }
        if (empty == 0) RuleVerdict(RuleOutcome.PASS)
        else RuleVerdict(RuleOutcome.LOOK, "$empty spec ${if (empty == 1) "field" else "fields"} could be filled from the datasheet.")
    },

    // Duplicates and consistency.
    Rule("dup_entered_twice", "No asset entered twice") { c ->
        val name = c.asset.name.trim().lowercase()
        val dup = c.others.firstOrNull { (a, _) -> a.id != c.asset.id && a.name.trim().lowercase() == name }
        if (dup != null) RuleVerdict(RuleOutcome.FAIL, "Looks like the same asset as another entry with this name.") else RuleVerdict(RuleOutcome.PASS)
    },
    Rule("dup_serial", "Serial numbers are unique") { c ->
        val serial = v(c, "serial")
        val dup = serial.isNotEmpty() && c.others.any { (a, i) -> a.id != c.asset.id && i.values["serial"]?.trim() == serial }
        if (dup) RuleVerdict(RuleOutcome.FAIL, "Serial $serial is used by another asset.") else RuleVerdict(RuleOutcome.PASS)
    },
    Rule("dup_names", "Asset names are unique") { c ->
        val n = c.asset.name.trim().lowercase()
        val count = c.others.count { (a, _) -> a.name.trim().lowercase() == n }
        if (count > 1) RuleVerdict(RuleOutcome.LOOK, "$count assets share this name.") else RuleVerdict(RuleOutcome.PASS)
    },
    Rule("make_consistent", "Manufacturer names are consistent") { c ->
        val make = v(c, "make")
        val clash = c.others.firstOrNull { (_, i) ->
            val m = i.values["make"]?.trim().orEmpty()
            m.isNotEmpty() && m != make && m.equals(make, ignoreCase = true)
        }
        if (make.isNotEmpty() && clash != null) RuleVerdict(RuleOutcome.LOOK, "“$make” is also spelled “${clash.second.values["make"]}”.")
        else RuleVerdict(RuleOutcome.PASS)
    },
    Rule("one_off", "No one-off specs") { c ->
        if (v(c, "power").isNotEmpty() && roll(c, "oneoff") < 10) RuleVerdict(RuleOutcome.LOOK, "Power rating is unusual for this asset type.")
        else RuleVerdict(RuleOutcome.PASS)
    },
    Rule("outliers", "No outlier values", eval = fromCheck(4, failAs = RuleOutcome.LOOK)),

    // AI review (simulated).
    Rule("ai_read", "AI read of the asset", ai = true) { c ->
        val r = roll(c, "airead")
        when {
            r < 12 -> RuleVerdict(RuleOutcome.FAIL, "The notes describe a different asset type than the one recorded.")
            r < 32 -> RuleVerdict(RuleOutcome.LOOK, "Name and model look inconsistent; worth a second look.")
            else -> RuleVerdict(RuleOutcome.PASS)
        }
    },
    Rule("ai_photos", "Photos match the asset", ai = true) { c ->
        when {
            c.input.photoCount == 0 -> RuleVerdict(RuleOutcome.LOOK, "No photo to compare.")
            roll(c, "aiphoto") < 10 -> RuleVerdict(RuleOutcome.LOOK, "Photo may show a different unit.")
            else -> RuleVerdict(RuleOutcome.PASS)
        }
    },
).associateBy { it.id }

val TEMPLATES = listOf(
    ValidationTemplate(
        "quick", "Quick check", "The basics before handover",
        "The basics before handover: empty mandatory fields, wrong formats, placeholders, odd dates and numbers, overdue services, and missing locations and photos.",
        ai = false,
        ruleIds = listOf("mandatory", "formats", "placeholders", "dates", "ranges", "related", "service", "location", "photo"),
    ),
    ValidationTemplate(
        "specs", "Specs check", "Match published specs",
        "Compares every model with the manufacturer's published specs and with its identical twins, and fills the gaps.",
        ai = true, ruleIds = listOf("spec_published", "spec_twins", "spec_gaps"),
    ),
    ValidationTemplate(
        "dups", "Duplicates and consistency", "Find duplicates and mismatches",
        "Finds assets entered twice, shared serial numbers, inconsistent manufacturer and asset names, misclassified types, odd-one-out specs and outlier values.",
        ai = false,
        ruleIds = listOf("dup_entered_twice", "dup_serial", "dup_names", "make_consistent", "related", "one_off", "outliers"),
    ),
    ValidationTemplate(
        "deep", "AI deep review", "The most thorough AI review",
        "Published specs, identical twins, related fields and an AI read of every asset: the most thorough check.",
        ai = true, ruleIds = listOf("spec_published", "spec_twins", "spec_gaps", "related", "ai_read", "ai_photos"),
    ),
    ValidationTemplate(
        "full", "Full audit", "Every check, all at once",
        "Every check, including published specs looked up online.",
        ai = true,
        ruleIds = listOf(
            "mandatory", "formats", "placeholders", "dates", "ranges", "related", "service", "location", "photo",
            "spec_published", "spec_twins", "spec_gaps", "dup_entered_twice", "dup_serial", "dup_names", "make_consistent",
            "outliers", "ai_read",
        ),
    ),
)

val DEFAULT_SAVED = listOf(
    SavedValidation("sv1", "Field Validation", listOf("mandatory", "placeholders", "related", "location", "photo")),
)

/** Runs [rules] over [targets]; [all] is every asset in the property (for duplicate checks). */
fun runValidation(rules: List<Rule>, targets: List<Pair<Asset, CheckInput>>, all: List<Pair<Asset, CheckInput>>): List<AssetResult> =
    targets.map { (a, input) ->
        val ctx = RuleCtx(a, input, all)
        val findings = rules.map { r -> r.eval(ctx).let { Finding(r.name, it.outcome, it.message) } }
        val outcome = when {
            findings.any { it.outcome == RuleOutcome.FAIL } -> RuleOutcome.FAIL
            findings.any { it.outcome == RuleOutcome.LOOK } -> RuleOutcome.LOOK
            else -> RuleOutcome.PASS
        }
        AssetResult(a.id, a.name, outcome, findings.filter { it.outcome == RuleOutcome.FAIL || it.outcome == RuleOutcome.LOOK })
    }
