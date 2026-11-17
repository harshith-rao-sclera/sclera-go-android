package com.sclera.sclerago.domain.model

/** The property's link to a VDMS (Managed Assets): one at most, and it can't be changed in the app. */
data class VdmsLink(val id: String, val vdmsId: String, val organisationId: String, val createdAt: Long?)

/** A VDMS of the organisation not linked to any property yet ([propertyName] is what it calls its site, if anything). */
data class VdmsChoice(val vdmsId: String, val propertyName: String?)

/** The Managed Assets Summary: assets moved to Managed Assets, and ones sent back to be recollected. */
data class ManagedCounts(val moved: Int = 0, val returned: Int = 0)

/** The places a count covers, by building, floor and location id (all empty: the whole property). */
data class PlaceScope(
    val buildingIds: List<String> = emptyList(),
    val floorIds: List<String> = emptyList(),
    val locationIds: List<String> = emptyList(),
)

/** Someone the linked VDMS can assign moved assets to; picked by [email]. */
data class VdmsAssignee(val email: String, val name: String?)
