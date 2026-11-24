package com.sclera.sclerago.domain.model

// Currency fields (the server's `currency` type): the answer is an amount, a number, and the field's unit is its
// currency ("USD"), one of the server's `GET /currencies` codes. Shown as the web's currencyField.js shows them.

/** A currency a currency field can be in, as the server lists them: [code] is what the field's unit is set to (USD). */
data class Currency(val code: String, val name: String)

/** A currency field's currency (its unit), or null for any other field or one the server sent without it. */
val FieldCfg.currency: String? get() = unit.trim().takeIf { kind == FieldKind.CURRENCY && it.isNotEmpty() }

/**
 * The field's name with its currency, as the web shows a currency field: "Purchase cost (USD)". A name that already
 * says it, and any other field's, stays as it is.
 */
val FieldCfg.labelWithCurrency: String
    get() {
        val code = currency ?: return label
        return if (label.uppercase().contains("($code)".uppercase())) label else "$label ($code)"
    }

private val CODE_AT_END = Regex("""\(\s*([A-Za-z]{3,4})\s*\)\s*$""")

/**
 * The currency code a field's name ends with, in brackets ("Purchase cost (USD)" -> "USD"), or null: how the server
 * finds the currency of a currency field read from a spreadsheet (it checks the code is one of its own).
 */
fun currencyInName(name: String): String? = CODE_AT_END.find(name.trim())?.groupValues?.get(1)?.uppercase()

/** An answer as it is read: a currency field's amount with its currency ("1200 USD"); blank answers and other fields' as they are. */
fun FieldCfg.amountText(value: String): String = currency?.takeIf { value.isNotBlank() }?.let { "$value $it" } ?: value
