package com.sclera.sclerago.domain.model

/**
 * How an asset's status is stored in the database: the `status` text plus the flags that refine it.
 * [aiSuggestion] null leaves the stored flag as it is (it only matters while the asset is drafted).
 */
data class DbStatus(val status: String, val validated: Boolean, val aiSuggestion: Boolean?, val needsReview: Boolean = false)

/**
 * The app's [Status] from an asset row's `status` text and flags. First match wins: exception, then saved or
 * validated (VALIDATED), then submitted. The AI and needs-review flags only count on a drafted asset:
 * ai_suggestion gives AI, then needs_review gives INPUT, else DRAFT.
 */
fun statusFromDb(status: String?, needsReview: Boolean, validated: Boolean, aiSuggestion: Boolean): Status = when {
    status == "exception" -> Status.EXCEPTION
    status == "saved" || validated -> Status.VALIDATED
    status == "submitted" -> Status.SUBMITTED
    status == "drafted" && aiSuggestion -> Status.AI
    status == "drafted" && needsReview -> Status.INPUT
    else -> Status.DRAFT
}

/**
 * What to store for [status]: DRAFT, INPUT (needs_review) and AI (ai_suggestion) are drafted and set both flags to
 * match; SUBMITTED, EXCEPTION and VALIDATED (saved and validated) keep the stored AI flag as it is.
 */
fun statusToDb(status: Status): DbStatus = when (status) {
    Status.DRAFT -> DbStatus("drafted", validated = false, aiSuggestion = false)
    Status.INPUT -> DbStatus("drafted", validated = false, aiSuggestion = false, needsReview = true)
    Status.AI -> DbStatus("drafted", validated = false, aiSuggestion = true)
    Status.SUBMITTED -> DbStatus("submitted", validated = false, aiSuggestion = null)
    Status.EXCEPTION -> DbStatus("exception", validated = false, aiSuggestion = null)
    Status.VALIDATED -> DbStatus("saved", validated = true, aiSuggestion = null)
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
