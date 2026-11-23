package com.sclera.sclerago.domain.model

/**
 * The standard answers the server keeps in columns of the asset itself rather than among its values (by field key). The
 * server's CORE_COLUMNS also has `description`, a column only once the offline database has it (the data layer adds it
 * to the columns it passes to [missingRequiredKeys] then).
 */
val CORE_FIELD_KEYS = listOf("name", "manufacturer", "model", "serial_number")

/**
 * The mandatory fields of an asset that have no answer: of [requiredKeys] (field keys of its active mandatory fields), the
 * ones whose answer is blank. A core field (one of [columns]: [CORE_FIELD_KEYS], and Description where it is a column) is
 * answered in [columns], every other one in [answers] (keyed by field key), as the server's `missingRequired`. This is the
 * one rule behind the "Input needed" status and the submit rule.
 */
fun missingRequiredKeys(requiredKeys: Collection<String>, columns: Map<String, String?>, answers: Map<String, String>): List<String> =
    requiredKeys.distinct().filter { key -> (if (key in CORE_FIELD_KEYS || key in columns) columns[key] else answers[key]).isNullOrBlank() }
