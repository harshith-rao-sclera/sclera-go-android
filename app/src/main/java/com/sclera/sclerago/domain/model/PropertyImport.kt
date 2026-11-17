package com.sclera.sclerago.domain.model

// Importing properties from a spreadsheet, as the web's Import does it on the new API.

/** How the server matched a column to a detail: its heading names it, AI judged it, or every value looks like it. */
enum class MatchHow { HEADING, AI, VALUES }

/** A property detail a column can be imported into: its [key] and the [header] the sample sheet uses. */
data class ImportTarget(val key: String, val header: String) {
    /** A sheet must have a column for this detail (the new API only needs a property's name). */
    val required: Boolean get() = key.equals(REQUIRED_KEY, ignoreCase = true)

    companion object {
        const val REQUIRED_KEY = "name"
    }
}

/** One of the sheet's columns: its [heading], its first few [samples], and the detail the server proposed for it. */
data class ImportColumn(val heading: String, val samples: List<String>, val target: String?, val how: MatchHow?)

/** What the server proposed for the sheet's columns, and whether AI helped ([aiNote] says how that went). */
data class ImportSuggestion(val columns: List<ImportColumn>, val aiUsed: Boolean, val aiNote: String?)

/**
 * A sheet ready to be mapped: the details a column can go into, what the server proposed, and the mapping that starts
 * from it ([mapping]: detail key → column heading).
 */
data class ImportPlan(val targets: List<ImportTarget>, val suggestion: ImportSuggestion, val mapping: Map<String, String>)

/** What an import did: how many properties were [created], and the names [skipped] because they are already in use. */
data class ImportResult(val created: Int, val skipped: List<String>)
