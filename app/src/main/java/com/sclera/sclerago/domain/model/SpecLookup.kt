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
    /** Field keys where the photo read disagrees with the saved value (show both; never overwrite silently). */
    val differs: Set<String> = emptySet(),
    /** Field keys read off the photo (the rest were looked up online). */
    val fromPhoto: Set<String> = emptySet(),
    /** The type the AI thinks the asset is, when it isn't the one it has; null when none is suggested. */
    val suggestedType: LookupType? = null,
    /** What the photos say that none of the asset's fields takes (shown, never saved). */
    val extra: List<PhotoNote> = emptyList(),
    /** It started a search for the model's manuals on the manufacturer's site (they show with the asset's documents). */
    val manualsSearch: Boolean = false,
)

/** Something a photo says that no field takes: its [name] and [value]. */
data class PhotoNote(val name: String, val value: String)

/**
 * The type a look-up thinks the asset is (the server's `suggestedType`): its path, how sure (0..1) and why, the
 * likeliest JACS code, and suggested [values] for that type's fields by form key (the asset's own field of the same
 * name, else the type field's key). They go into the form only when the type is taken.
 */
data class LookupType(
    val masterFormatId: String,
    /** The type as one line, as the server writes it; null when it gives none. */
    val label: String? = null,
    val systemType: String,
    val assetType: String?,
    val subtype: String?,
    val confidence: Double,
    val reason: String,
    val jacsCode: String?,
    val values: Map<String, String>,
)

/** How sure a suggestion must be to start ticked (the web's 60%). */
const val SURE_ENOUGH = 0.6
