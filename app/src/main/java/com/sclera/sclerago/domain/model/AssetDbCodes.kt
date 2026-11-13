package com.sclera.sclerago.domain.model

/** How an asset's status is stored in the database: the `status` text plus the flags that refine it. */
data class DbStatus(val status: String, val validated: Boolean, val aiSuggestion: Boolean)

/**
 * The app's [Status] from an asset row's `status` text and flags. First match wins:
 * exception, then saved or validated (VALIDATED), then ai_suggestion on a drafted or submitted
 * asset (AI), then submitted, then drafted (INPUT when it needs review, else DRAFT).
 */
fun statusFromDb(status: String?, needsReview: Boolean, validated: Boolean, aiSuggestion: Boolean): Status = when {
    status == "exception" -> Status.EXCEPTION
    status == "saved" || validated -> Status.VALIDATED
    aiSuggestion && (status == "drafted" || status == "submitted") -> Status.AI
    status == "submitted" -> Status.SUBMITTED
    needsReview -> Status.INPUT
    else -> Status.DRAFT
}

/** What to store for [status]: DRAFT, INPUT and AI are drafted (AI sets ai_suggestion), VALIDATED is saved and validated. */
fun statusToDb(status: Status): DbStatus = when (status) {
    Status.DRAFT, Status.INPUT -> DbStatus("drafted", validated = false, aiSuggestion = false)
    Status.AI -> DbStatus("drafted", validated = false, aiSuggestion = true)
    Status.SUBMITTED -> DbStatus("submitted", validated = false, aiSuggestion = false)
    Status.EXCEPTION -> DbStatus("exception", validated = false, aiSuggestion = false)
    Status.VALIDATED -> DbStatus("saved", validated = true, aiSuggestion = false)
}

/** The app's [Source] for a stored `source` text; anything unknown (such as "document") is [Source.MANUAL]. */
fun sourceFromDb(source: String?): Source = when (source) {
    "bluetooth" -> Source.BT
    "wifi" -> Source.WIFI
    else -> Source.MANUAL
}

/** The `source` text to store: the API accepts only manual, bluetooth and wifi, so the rest become manual. */
fun sourceToDb(source: Source): String = when (source) {
    Source.BT -> "bluetooth"
    Source.WIFI -> "wifi"
    Source.CAMERA, Source.AP, Source.FILE, Source.MANUAL -> "manual"
}
