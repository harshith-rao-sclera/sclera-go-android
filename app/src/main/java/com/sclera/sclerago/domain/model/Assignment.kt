package com.sclera.sclerago.domain.model

/**
 * What assigning assets to a person did, as the server says it: how many were [assigned], to whom ([email], [name];
 * no email when they were taken off whoever had them), and whether that person was [added] as a Collector on the
 * property or [givenAccess] to it first.
 */
data class AssignResult(
    val assigned: Int,
    val email: String?,
    val name: String?,
    val added: Boolean,
    val givenAccess: Boolean,
)
