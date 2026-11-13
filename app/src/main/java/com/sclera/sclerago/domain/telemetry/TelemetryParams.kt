package com.sclera.sclerago.domain.telemetry

/** Keeps telemetry parameters small, plain and free of anything that looks like personal data. */
object TelemetryParams {
    const val MAX_PARAMS = 10
    const val MAX_VALUE_LENGTH = 40
    private val KEY = Regex("[a-z][a-z0-9_]{0,39}")
    private val VALUE = Regex("[A-Za-z0-9_.-]{1,$MAX_VALUE_LENGTH}")

    /** Only snake_case keys and short values of letters, digits, '_', '.', '-' (so no '@', spaces, URLs or free text). */
    fun sanitise(params: Map<String, String>): Map<String, String> =
        params.filter { (k, v) -> KEY.matches(k) && VALUE.matches(v) }.entries.take(MAX_PARAMS).associate { it.key to it.value }

    /** A crash-report code: lower snake_case, at most 40 characters; anything else becomes "invalid_code". */
    fun code(raw: String): String = if (KEY.matches(raw)) raw else "invalid_code"
}
