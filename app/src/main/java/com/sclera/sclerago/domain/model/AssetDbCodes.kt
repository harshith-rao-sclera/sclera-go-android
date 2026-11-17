package com.sclera.sclerago.domain.model

/**
 * How an asset's status is stored in the database: the `status` text plus the flags that refine it.
 * [aiSuggestion] null leaves the stored flag as it is (it only matters while the asset is drafted).
 * The server's `needs_review` flag (an asset read from a file, waiting for review) is never written by the app.
 */
data class DbStatus(val status: String, val validated: Boolean, val aiSuggestion: Boolean?)

/**
 * The app's [Status] from an asset row's `status` text and `validated` flag, as the web shows it: exception, then saved
 * or validated (VALIDATED), then submitted, else drafted (DRAFT). A drafted asset stays Drafted when its AI flag is set
 * or a mandatory field is empty; the AI flag is kept on the asset ([Asset.aiSuggestion]) for the AI suggestion chip.
 */
fun statusFromDb(status: String?, validated: Boolean): Status = when {
    status == "exception" -> Status.EXCEPTION
    status == "saved" || validated -> Status.VALIDATED
    status == "submitted" -> Status.SUBMITTED
    else -> Status.DRAFT
}

/**
 * What to store for [status]: DRAFT, INPUT and AI are drafted. Saving (AI) and submitting (SUBMITTED, or EXCEPTION
 * when incomplete) set the AI flag; DRAFT, INPUT and VALIDATED keep it as it is (a drafted asset shows as Drafted
 * either way, so moving one to draft must not clear it).
 */
fun statusToDb(status: Status): DbStatus = when (status) {
    Status.DRAFT, Status.INPUT -> DbStatus("drafted", validated = false, aiSuggestion = null)
    Status.AI -> DbStatus("drafted", validated = false, aiSuggestion = true)
    Status.SUBMITTED -> DbStatus("submitted", validated = false, aiSuggestion = true)
    Status.EXCEPTION -> DbStatus("exception", validated = false, aiSuggestion = true)
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
