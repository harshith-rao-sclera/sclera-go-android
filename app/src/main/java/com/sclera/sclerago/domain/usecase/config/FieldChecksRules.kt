package com.sclera.sclerago.domain.usecase.config

import com.sclera.sclerago.domain.model.FieldChecks
import com.sclera.sclerago.domain.model.FieldKind
import com.sclera.sclerago.domain.model.MAX_CHECK_LENGTH
import com.sclera.sclerago.domain.model.MAX_PATTERN_LENGTH
import com.sclera.sclerago.domain.model.TEXT_KINDS
import com.sclera.sclerago.domain.model.ValueRule

// What the Checks sheet accepts, as the web's Checks dialog and the server (`asset_fields.checks`) do.

/** Why a field's checks can't be saved. */
enum class ChecksProblem {
    /** "Your own pattern" with no pattern. */
    PATTERN_MISSING,

    /** The pattern is longer than [MAX_PATTERN_LENGTH] or isn't a valid regular expression. */
    PATTERN_INVALID,

    /** A length limit isn't a whole number from 0 to [MAX_CHECK_LENGTH]. */
    LENGTHS_NOT_WHOLE,

    /** The longest length is 0. */
    MAX_LENGTH_BELOW_ONE,

    /** The shortest length is longer than the longest. */
    LENGTHS_REVERSED,

    /** A number limit isn't a number. */
    RANGE_NOT_NUMBER,

    /** The minimum is higher than the maximum. */
    RANGE_REVERSED,
}

private fun whole(s: String): Int? = s.trim().takeIf { Regex("\\d+").matches(it) }?.toIntOrNull()

/** Why [checks] can't be saved for a field of [kind], or null when they can. Only the parts that fit [kind] are looked at. */
fun checksProblem(kind: FieldKind, checks: FieldChecks): ChecksProblem? {
    val min = checks.min.trim()
    val max = checks.max.trim()
    return when (kind) {
        in TEXT_KINDS -> {
            val lo = whole(min)
            val hi = whole(max)
            when {
                checks.rule == ValueRule.PATTERN && checks.pattern.isBlank() -> ChecksProblem.PATTERN_MISSING
                checks.rule == ValueRule.PATTERN &&
                    (checks.pattern.length > MAX_PATTERN_LENGTH || runCatching { Regex(checks.pattern) }.isFailure) -> ChecksProblem.PATTERN_INVALID
                (min.isNotEmpty() && (lo == null || lo > MAX_CHECK_LENGTH)) ||
                    (max.isNotEmpty() && (hi == null || hi > MAX_CHECK_LENGTH)) -> ChecksProblem.LENGTHS_NOT_WHOLE
                hi != null && hi < 1 -> ChecksProblem.MAX_LENGTH_BELOW_ONE
                lo != null && hi != null && lo > hi -> ChecksProblem.LENGTHS_REVERSED
                else -> null
            }
        }
        FieldKind.NUMBER -> {
            val lo = min.toDoubleOrNull()
            val hi = max.toDoubleOrNull()
            when {
                (min.isNotEmpty() && lo == null) || (max.isNotEmpty() && hi == null) -> ChecksProblem.RANGE_NOT_NUMBER
                lo != null && hi != null && lo > hi -> ChecksProblem.RANGE_REVERSED
                else -> null
            }
        }
        else -> null
    }
}

/**
 * [checks] with only what a field of [kind] can have: text keeps its format (and pattern for "Your own pattern") and
 * lengths, numbers their range and whole numbers, dates past or future; other kinds only the highlight-empty flag.
 * A mandatory field is always flagged when empty, so it doesn't keep the flag.
 */
fun cleanChecks(kind: FieldKind, checks: FieldChecks, required: Boolean): FieldChecks {
    val flag = checks.flagEmpty && !required
    return when (kind) {
        in TEXT_KINDS -> {
            val rule = checks.rule.takeIf { kind in it.kinds } ?: ValueRule.NONE
            FieldChecks(
                flagEmpty = flag,
                rule = rule,
                pattern = if (rule == ValueRule.PATTERN) checks.pattern else "",
                min = checks.min.trim(),
                max = checks.max.trim(),
            )
        }
        FieldKind.NUMBER -> FieldChecks(flagEmpty = flag, min = checks.min.trim(), max = checks.max.trim(), wholeNumber = checks.wholeNumber)
        FieldKind.DATE -> FieldChecks(flagEmpty = flag, rule = checks.rule.takeIf { kind in it.kinds } ?: ValueRule.NONE)
        else -> FieldChecks(flagEmpty = flag)
    }
}
