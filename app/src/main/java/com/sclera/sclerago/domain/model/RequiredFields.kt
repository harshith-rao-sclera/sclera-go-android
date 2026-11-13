package com.sclera.sclerago.domain.model

/** The standard answers the server keeps in columns of the asset itself rather than among its values (by field key). */
val CORE_FIELD_KEYS = listOf("name", "manufacturer", "model", "serial_number")

/**
 * The mandatory fields of an asset that have no answer: of [requiredKeys] (field keys of its active mandatory fields), the
 * ones whose answer is blank. A core field is answered in [columns], every other one in [answers] (keyed by field key).
 * This is the one rule behind the "Input needed" status and the submit rule.
 */
fun missingRequiredKeys(requiredKeys: Collection<String>, columns: Map<String, String?>, answers: Map<String, String>): List<String> =
    requiredKeys.distinct().filter { key -> (if (key in CORE_FIELD_KEYS) columns[key] else answers[key]).isNullOrBlank() }
