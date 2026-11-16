package com.sclera.sclerago.domain.model

/** Where a spec look-up is (the server's lookup job status). */
enum class LookupStatus {
    QUEUED, RUNNING, COMPLETED, FAILED;

    /** Still searching. */
    val waiting: Boolean get() = this == QUEUED || this == RUNNING

    companion object {
        fun of(text: String?): LookupStatus = when (text) {
            "queued" -> QUEUED
            "running" -> RUNNING
            "completed" -> COMPLETED
            else -> FAILED
        }
    }
}

/**
 * A search of the web for an asset model's published specs (the server's lookup job). Its suggestions, how sure each
 * is and where each came from are keyed by the app's field keys.
 */
data class SpecLookup(
    val id: String,
    val status: LookupStatus,
    /** Why it failed, as the server says it. */
    val statusDetail: String?,
    /** The product the specs were found for. */
    val product: String?,
    /** One sentence on how it went, as the server says it. */
    val message: String?,
    val values: Map<String, String>,
    val confidence: Map<String, Double>,
    val sources: Map<String, String>,
    /** What was typed to look the model up (manufacturer, model). */
    val typed: Map<String, String>,
    /** The suggestions have been shown to someone. */
    val seen: Boolean,
    val createdAt: Long?,
)

/** How sure a suggestion must be to start ticked (the web's 60%). */
const val SURE_ENOUGH = 0.6
