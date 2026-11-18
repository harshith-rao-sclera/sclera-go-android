package com.sclera.sclerago.domain.model

/** The statuses the server locks: only an admin may change a submitted or validated (saved) asset. */
private val LOCKED_STATUSES = setOf(Status.SUBMITTED, Status.VALIDATED)

/** Whether the server would refuse a change to an asset in [status] by this user (an admin when [isAdmin]). */
fun isLockedFor(status: Status, isAdmin: Boolean): Boolean = !isAdmin && status in LOCKED_STATUSES

/** Why the server refuses a change to an asset (its check_editable); the web's lockedReason. */
enum class AssetLock {
    /** Someone who isn't an admin changes only the assets assigned to them. */
    NOT_ASSIGNED,

    /** Submitted or signed off: only an admin may change it. */
    SUBMITTED,
}

/**
 * As the server's check_editable (and the web's lockedForMe): an admin may change any asset; anyone else only one
 * assigned to them ([assignedToMe]) that isn't submitted or signed off. Null when the change is allowed.
 */
fun assetLock(status: Status, isAdmin: Boolean, assignedToMe: Boolean): AssetLock? = when {
    isAdmin -> null
    !assignedToMe -> AssetLock.NOT_ASSIGNED
    status in LOCKED_STATUSES -> AssetLock.SUBMITTED
    else -> null
}

/**
 * The mandatory fields of the form that are still empty: active, mandatory and blank in [values] (by field key). The
 * system, asset and sub type count as empty while they are still [UNCLASSIFIED], as the form shows them.
 */
fun missingRequiredFields(fields: List<FieldCfg>, values: Map<String, String>): List<FieldCfg> =
    fields.filter { f ->
        val v = values[f.key].orEmpty().trim()
        f.required && f.visible && (v.isEmpty() || (f.key in CLASSIFICATION_KEYS && v == UNCLASSIFIED))
    }

/** How many more photos of each kind the [rules] ask for, given how many the asset has ([counts]); kinds already met are left out. */
fun photosShort(rules: List<PhotoRule>, counts: Map<PhotoKind, Int>): Map<PhotoKind, Int> =
    rules.associate { it.kind to it.min - (counts[it.kind] ?: 0) }.filterValues { it > 0 }

/** How many photos of each kind [media] has (photos that answer a field, and photos of no kind, aren't counted). */
fun photoCounts(media: List<Media>): Map<PhotoKind, Int> =
    media.filter { it.fieldId == null }.mapNotNull { it.category }.groupingBy { it }.eachCount()

/** The form keys of the system, asset and sub type. */
private val CLASSIFICATION_KEYS = setOf("sys", "typ", "sub")
