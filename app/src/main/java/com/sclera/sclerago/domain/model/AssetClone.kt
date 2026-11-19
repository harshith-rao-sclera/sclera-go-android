package com.sclera.sclerago.domain.model

/** The most copies one Clone makes, as the web's ("Enter a number between 1 and 100"). */
const val MAX_CLONE_COPIES = 100

/** How much of an asset's place its copies keep: none of it, its building, its floor, or its location. */
enum class ClonePlace { NONE, BUILDING, FLOOR, LOCATION }

/**
 * What the copies of an asset take from it, as the web's Clone: how many copies, how much of its place, the form values
 * picked (app field keys; its name too when ticked, as the web copies it with the field values) and whether its type
 * stays. Photos are never copied: each copy starts without any.
 */
data class CloneRequest(
    val copies: Int,
    val place: ClonePlace,
    val values: Map<String, String>,
    /** Its system, asset and sub type are all copied, so the copies keep its type. */
    val keepType: Boolean,
)
