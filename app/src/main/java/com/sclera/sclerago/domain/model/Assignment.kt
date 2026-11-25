package com.sclera.sclerago.domain.model

/**
 * What assigning assets to a person did, as the server says it: how many were [assigned], how many already were theirs
 * ([unchanged]) and how many the server doesn't have ([notFound]: made on this phone and not synced yet), to whom ([email],
 * [name]; no email when they were taken off whoever had them), and whether that person was [added] as a Collector on the
 * property or [givenAccess] to it first.
 */
data class AssignResult(
    val assigned: Int,
    val email: String?,
    val name: String?,
    val added: Boolean,
    val givenAccess: Boolean,
    val unchanged: Int = 0,
    val notFound: Int = 0,
)

/** Why "Assign to me" can't take the assets (the web's takeReason): only an admin may take them. */
enum class TakeRefusal {
    /** One already has an assignee: only an admin reassigns it. */
    ALREADY_ASSIGNED,

    /** Assigned to the signed-in user already (none to anyone else): there is nothing to take. */
    ALREADY_YOURS,

    /** One is submitted or signed off: only an admin assigns it. */
    SUBMITTED,
}
