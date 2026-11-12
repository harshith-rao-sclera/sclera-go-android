package com.sclera.sclerago.domain.validation


import com.sclera.sclerago.domain.model.AssetType
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FieldKind
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.model.ValueRule
import com.sclera.sclerago.domain.model.typeOf
import kotlin.math.roundToInt

enum class CheckState { PASSED, FAILED, NA }

/** Where a problem is fixed: a section of the Info tab, or the location picker. */
enum class FixTarget(val section: String?) { IDENT("ident"), CLASS("cls"), TAGS("tags"), LOCATION(null), PHOTOS(null) }

data class CheckResult(
    val name: String,
    val state: CheckState,
    val messages: List<String> = emptyList(),
    val fixLabel: String? = null,
    val fix: FixTarget? = null,
)

/** What the checks look at: the asset's current field values plus a few facts about it. */
data class CheckInput(
    val values: Map<String, String>,
    val hasLocation: Boolean,
    val status: Status,
    val fromCamera: Boolean,
    val photoCount: Int,
    /** Mandatory fields as (key, label), from Configuration → Asset fields. */
    val required: List<Pair<String, String>> = DEFAULT_REQUIRED,
    /** Fewest photos an asset onboarded with the camera needs (sum of the photo types' minimums). */
    val minPhotos: Int = 1,
    /** The configured fields, for their own checks (flag when empty, value rule, limits). */
    val fieldChecks: List<FieldCfg> = emptyList(),
)

val DEFAULT_REQUIRED = listOf("name" to "Name", "sys" to "System type", "typ" to "Asset type", "jacs" to "JACS code")

val CHECK_NAMES = listOf("Mandatory", "Formats", "Placeholders", "Dates", "Ranges", "Consistency", "Lifecycle", "Location", "Photos")

private val FIELD_LABELS = mapOf(
    "name" to "Name", "make" to "Make", "model" to "Model", "serial" to "Serial no.", "power" to "Power",
    "voltage" to "Voltage", "speed" to "Speed", "sys" to "System type", "typ" to "Asset type", "sub" to "Sub type",
    "jacs" to "JACS code", "label" to "Label / QR", "ble" to "BLE tag", "ip" to "IP address", "note" to "Notes",
)

private val PLACEHOLDERS = setOf("tbd", "tba", "n/a", "na", "xxx", "xx", "-", "--", "?", "unknown", "test", "todo")

/** Runs one check (index 0..8 into [CHECK_NAMES]). */
fun runCheck(index: Int, input: CheckInput): CheckResult {
    val v = input.values
    fun value(k: String) = v[k].orEmpty().trim()
    val name = CHECK_NAMES[index]
    return when (index) {
        0 -> { // Mandatory, plus fields set to be flagged when they're left empty
            val flagged = input.fieldChecks.filter { it.flagEmpty && it.visible && !it.required }.map { it.key to it.label }
            val check = input.required + flagged.filter { f -> input.required.none { it.first == f.first } }
            val missing = check.filter { (k, _) ->
                val x = value(k)
                x.isEmpty() || (k in setOf("sys", "typ", "sub") && x == "Generic")
            }.map { it.second }
            if (missing.isEmpty()) CheckResult(name, CheckState.PASSED)
            else CheckResult(
                name, CheckState.FAILED,
                missing.map { m -> if (input.required.any { it.second == m }) "$m is required." else "$m is empty." },
                fixLabel = "Edit " + missing.first(),
                fix = sectionOf(check.first { it.second == missing.first() }.first),
            )
        }
        1 -> { // Formats
            val problems = mutableListOf<Pair<String, FixTarget>>()
            value("label").takeIf { it.isNotEmpty() && !Regex("^SCL-\\d{6}$").matches(it) }?.let {
                problems += "Label / QR (“$it”) doesn’t match the format SCL-000000." to FixTarget.TAGS
            }
            value("ble").takeIf { it.isNotEmpty() && !Regex("^SCL-\\d{4}$").matches(it) }?.let {
                problems += "BLE tag (“$it”) doesn’t match the format SCL-0000." to FixTarget.TAGS
            }
            value("ip").takeIf { it.isNotEmpty() && !isIpv4(it) }?.let {
                problems += "IP address (“$it”) isn’t a valid IPv4 address." to FixTarget.TAGS
            }
            value("serial").takeIf { it.isNotEmpty() && !Regex("^[A-Za-z0-9][A-Za-z0-9-]*$").matches(it) }?.let {
                problems += "Serial no. (“$it”) should only use letters, digits and dashes." to FixTarget.IDENT
            }
            // What each field is set to check in Configuration → Asset fields.
            input.fieldChecks.filter { it.visible && (it.hasFormat || it.kind in setOf(FieldKind.URL, FieldKind.TIME)) }.forEach { f ->
                val x = value(f.key)
                if (x.isNotEmpty()) fieldProblem(f, x)?.let { problems += "${f.label} (“$x”) $it." to sectionOf(f.key) }
            }
            if (problems.isEmpty()) CheckResult(name, CheckState.PASSED)
            else CheckResult(
                name, CheckState.FAILED, problems.map { it.first },
                fixLabel = "Edit " + problems.first().first.substringBefore(" (“"), fix = problems.first().second,
            )
        }
        2 -> { // Placeholders
            val bad = v.filter { (k, x) -> k != "note" && x.trim().lowercase() in PLACEHOLDERS }.keys
            if (bad.isEmpty()) CheckResult(name, CheckState.PASSED)
            else CheckResult(
                name, CheckState.FAILED,
                bad.map { "${FIELD_LABELS[it] ?: it} holds a placeholder (“${v[it]?.trim()}”)." },
                fixLabel = "Edit " + (FIELD_LABELS[bad.first()] ?: bad.first()),
                fix = sectionOf(bad.first()),
            )
        }
        3 -> CheckResult(name, CheckState.NA) // Dates: assets have no date fields yet
        4 -> { // Ranges
            val limits = listOf(
                Triple("power", "kW", 0.0..5000.0),
                Triple("voltage", "V", 0.0..33000.0),
                Triple("speed", "RPM", 0.0..36000.0),
            )
            val problems = limits.mapNotNull { (k, unit, range) ->
                val raw = value(k)
                if (raw.isEmpty()) return@mapNotNull null
                val num = Regex("^([0-9]+(?:\\.[0-9]+)?)\\s*([A-Za-z]*)$").find(raw)
                val label = FIELD_LABELS[k]
                when {
                    num == null -> "$label (“$raw”) isn’t a number with a unit."
                    num.groupValues[2].isNotEmpty() && !num.groupValues[2].equals(unit, ignoreCase = true) ->
                        "$label (“$raw”) should be in $unit."
                    num.groupValues[1].toDouble() !in range || num.groupValues[1].toDouble() == 0.0 ->
                        "$label (“$raw”) is outside the expected range."
                    else -> null
                }
            }
            if (problems.isEmpty()) CheckResult(name, CheckState.PASSED)
            else CheckResult(name, CheckState.FAILED, problems, "Edit " + problems.first().substringBefore(" (“"), FixTarget.IDENT)
        }
        5 -> { // Consistency
            val typ = value("typ")
            val fromName = typeOf(value("name"))
            if (typ.isEmpty() || typ == "Generic" || fromName == AssetType.GENERIC || fromName.label.equals(typ, ignoreCase = true)) {
                CheckResult(name, CheckState.PASSED)
            } else {
                val suggested = fromName.label
                CheckResult(
                    name, CheckState.FAILED,
                    listOf("The name suggests $suggested, but Asset type is $typ."),
                    "Edit Asset type", FixTarget.CLASS,
                )
            }
        }
        6 -> // Lifecycle
            if (input.status == Status.EXCEPTION) {
                CheckResult(name, CheckState.FAILED, listOf("The asset is in Exception: it isn’t responding or needs attention on site."))
            } else CheckResult(name, CheckState.PASSED)
        7 -> // Location
            if (input.hasLocation) CheckResult(name, CheckState.PASSED)
            else CheckResult(name, CheckState.FAILED, listOf("No location set."), "Set location", FixTarget.LOCATION)
        else -> // Photos
            when {
                !input.fromCamera -> CheckResult(name, CheckState.NA)
                input.photoCount >= input.minPhotos.coerceAtLeast(1) -> CheckResult(name, CheckState.PASSED)
                else -> CheckResult(
                    name, CheckState.FAILED,
                    listOf(
                        if (input.minPhotos <= 1) "Assets onboarded with the camera need at least one photo."
                        else "Assets onboarded with the camera need at least ${input.minPhotos} photos (${input.photoCount} so far).",
                    ),
                    "Add photo", FixTarget.PHOTOS,
                )
            }
    }
}

fun runAllChecks(input: CheckInput): List<CheckResult> = CHECK_NAMES.indices.map { runCheck(it, input) }

/** Share of applicable checks that pass, 0..100. */
fun dataQuality(results: List<CheckResult>): Int {
    val applicable = results.filter { it.state != CheckState.NA }
    if (applicable.isEmpty()) return 100
    return (100.0 * applicable.count { it.state == CheckState.PASSED } / applicable.size).roundToInt()
}

/** Why [x] breaks [f]'s checks ("should be …"), or null if it's fine. */
fun fieldProblem(f: FieldCfg, x: String): String? {
    val lo = f.min.trim().toDoubleOrNull()
    val hi = f.max.trim().toDoubleOrNull()
    return when (f.kind) {
        FieldKind.TEXT, FieldKind.TEXTAREA -> when {
            f.rule == ValueRule.ALNUM && !Regex("^[A-Za-z0-9 ]+$").matches(x) -> "should only use letters and numbers"
            f.rule == ValueRule.DIGITS && !x.all(Char::isDigit) -> "should only use digits"
            f.rule == ValueRule.CODE && !Regex("^[A-Z0-9-]+$").matches(x) -> "should only use capitals, digits and dashes"
            f.rule == ValueRule.NO_SPACES && x.any(Char::isWhitespace) -> "shouldn’t contain spaces"
            f.rule == ValueRule.EMAIL && !Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(x) -> "isn’t an email address"
            lo != null && x.length < lo -> "should be at least ${lo.toInt()} characters"
            hi != null && x.length > hi -> "should be at most ${hi.toInt()} characters"
            else -> null
        }
        FieldKind.NUMBER -> {
            val n = Regex("^-?[0-9]+(?:\\.[0-9]+)?").find(x)?.value?.toDoubleOrNull() ?: return "isn’t a number"
            when {
                f.rule == ValueRule.WHOLE && n % 1.0 != 0.0 -> "should be a whole number"
                f.rule == ValueRule.POSITIVE && n <= 0.0 -> "should be above zero"
                lo != null && n < lo -> "should be at least ${trimNum(lo)}"
                hi != null && n > hi -> "should be at most ${trimNum(hi)}"
                else -> null
            }
        }
        FieldKind.DATE -> {
            val d = parseDate(x) ?: return "isn’t a date"
            val today = java.time.LocalDate.now()
            when {
                f.rule == ValueRule.PAST && d.isAfter(today) -> "is in the future"
                f.rule == ValueRule.FUTURE && !d.isAfter(today) -> "should be in the future"
                lo != null && d.year < lo -> "is before ${lo.toInt()}"
                hi != null && d.year > hi -> "is after ${hi.toInt()}"
                else -> null
            }
        }
        FieldKind.URL -> if (Regex("^https?://\\S+$", RegexOption.IGNORE_CASE).matches(x)) null else "isn’t a web link (https://…)"
        FieldKind.TIME -> if (Regex("^([01]?\\d|2[0-3]):[0-5]\\d(\\s?[AaPp][Mm])?$").matches(x)) null else "isn’t a time"
        FieldKind.DROPDOWN, FieldKind.MULTI, FieldKind.CHECKBOX, FieldKind.YESNO -> null
    }
}

private fun trimNum(d: Double) = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()

/** Reads 2024-03-31, 31/03/2024, 31-03-2024 or 31.03.2024. */
private fun parseDate(s: String): java.time.LocalDate? {
    val t = s.trim()
    Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})").find(t)?.let { m ->
        return runCatching { java.time.LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull()
    }
    Regex("^(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4})").find(t)?.let { m ->
        return runCatching { java.time.LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull()
    }
    return null
}

private fun sectionOf(key: String): FixTarget = when (key) {
    "sys", "typ", "sub", "jacs" -> FixTarget.CLASS
    "label", "ble", "ip" -> FixTarget.TAGS
    else -> FixTarget.IDENT
}

private fun isIpv4(s: String): Boolean {
    val parts = s.split(".")
    return parts.size == 4 && parts.all { p -> p.isNotEmpty() && p.length <= 3 && p.all(Char::isDigit) && p.toInt() in 0..255 }
}
