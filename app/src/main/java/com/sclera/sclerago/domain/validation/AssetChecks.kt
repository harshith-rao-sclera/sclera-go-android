package com.sclera.sclerago.domain.validation

/** The checks on an asset's Validation tab, in the order they are listed. Text for each comes from string resources. */
enum class AssetCheckId { LOCATION, ASSET_TYPE, JACS_CODE, MAKE_MODEL, SERIAL, LOW_CONFIDENCE, PHOTO }

enum class CheckOutcome { PASS, FAIL, WARN }

/** What the line under a check's name says; [AssetCheck.args] / [AssetCheck.count] fill it in. */
enum class CheckNote {
    /** Nothing under the name. */
    NONE,
    /** The value that passed, e.g. "Mechanical · Pump" ([AssetCheck.args]). */
    VALUE,
    /** Missing, and the asset can't be validated without it. */
    REQUIRED_BEFORE_VALIDATE,
    /** Missing, but not required. */
    MISSING,
    /** A field was read with low confidence; args = its label. */
    LOW_CONFIDENCE_READING,
    /** Photos attached ([AssetCheck.count]). */
    PHOTO_COUNT,
    /** None attached, and the asset doesn't need any. */
    NO_PHOTO,
    /** Fewer photos than required: [AssetCheck.count] so far, [AssetCheck.needed] needed. */
    PHOTOS_NEEDED,
}

/**
 * One check's result. [required] is shown as a REQUIRED chip; [fix] and [fixField] say where the Fix button
 * goes (a section of the Info tab, or the location picker / camera) and which field to point at.
 */
data class AssetCheck(
    val id: AssetCheckId,
    val outcome: CheckOutcome,
    val required: Boolean = false,
    val note: CheckNote = CheckNote.NONE,
    val args: List<String> = emptyList(),
    val count: Int = 0,
    val needed: Int = 0,
    val fix: FixTarget? = null,
    val fixField: String? = null,
)

/** The checks of one asset. Warnings are worth a look but don't stop it being validated. */
data class AssetChecks(val items: List<AssetCheck>) {
    val failed: Int get() = items.count { it.outcome == CheckOutcome.FAIL }
    val canValidate: Boolean get() = failed == 0
}

/** What the checks look at. Field values are keyed as in the details form ("make", "model", "serial", "jacs", ...). */
data class AssetCheckInput(
    val values: Map<String, String>,
    val hasLocation: Boolean,
    val locationLabel: String,
    val photoCount: Int,
    val fromCamera: Boolean,
    val minPhotos: Int,
    /** Keys of the fields Configuration marks mandatory. */
    val requiredKeys: Set<String>,
    /** The field the nameplate reading was unsure about, and its label; null when none (or it has been reviewed). */
    val lowConfidenceKey: String? = null,
    val lowConfidenceLabel: String = "",
)
