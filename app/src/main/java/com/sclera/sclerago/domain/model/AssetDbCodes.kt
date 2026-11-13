package com.sclera.sclerago.domain.model

/**
 * How an asset's status is stored in the database: the `status` text plus the flags that refine it.
 * [aiSuggestion] null leaves the stored flag as it is (it only matters while the asset is drafted).
 * The server's `needs_review` flag (an asset read from a file, waiting for review) is never written by the app.
 */
data class DbStatus(val status: String, val validated: Boolean, val aiSuggestion: Boolean?)

/**
 * The app's [Status] from an asset row's `status` text and flags. First match wins: exception, then saved or
 * validated (VALIDATED), then submitted. On a drafted asset ai_suggestion gives AI, then a mandatory field still
 * empty ([missingRequired]) gives INPUT, else DRAFT. "Input needed" is derived, never stored.
 */
fun statusFromDb(status: String?, validated: Boolean, aiSuggestion: Boolean, missingRequired: Boolean): Status = when {
    status == "exception" -> Status.EXCEPTION
    status == "saved" || validated -> Status.VALIDATED
    status == "submitted" -> Status.SUBMITTED
    status == "drafted" && aiSuggestion -> Status.AI
    status == "drafted" && missingRequired -> Status.INPUT
    else -> Status.DRAFT
}

/**
 * What to store for [status]: DRAFT, INPUT and AI are drafted (INPUT is DRAFT: it is worked out from the missing
 * mandatory fields) and set the AI flag to match; SUBMITTED, EXCEPTION and VALIDATED (saved and validated) keep it.
 */
fun statusToDb(status: Status): DbStatus = when (status) {
    Status.DRAFT, Status.INPUT -> DbStatus("drafted", validated = false, aiSuggestion = false)
    Status.AI -> DbStatus("drafted", validated = false, aiSuggestion = true)
    Status.SUBMITTED -> DbStatus("submitted", validated = false, aiSuggestion = null)
    Status.EXCEPTION -> DbStatus("exception", validated = false, aiSuggestion = null)
    Status.VALIDATED -> DbStatus("saved", validated = true, aiSuggestion = null)
}

/** The app's [Source] for a stored `source` text: "document" is a file import; anything unknown is [Source.MANUAL]. */
fun sourceFromDb(source: String?): Source = when (source) {
    "bluetooth" -> Source.BT
    "wifi" -> Source.WIFI
    "document" -> Source.FILE
    else -> Source.MANUAL
}

/** The `source` text to store: the API accepts only manual, bluetooth and wifi, so the rest become manual. */
fun sourceToDb(source: Source): String = when (source) {
    Source.BT -> "bluetooth"
    Source.WIFI -> "wifi"
    Source.CAMERA, Source.AP, Source.FILE, Source.MANUAL -> "manual"
}
