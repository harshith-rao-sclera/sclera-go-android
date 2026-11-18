package com.sclera.sclerago.domain.model

/** A code of the JACS catalog: the [code] an asset stores, and its catalogue [title]. */
data class JacsCode(val code: String, val title: String?)

/** One page of a JACS search, best matches first; [next] is where the following page starts (null: the last page). */
data class JacsPage(val codes: List<JacsCode>, val next: String?)

/** The property's type for a JACS code, by its names, as the form sets the system, asset and sub type. */
data class JacsType(val masterFormatId: String, val systemType: String, val assetType: String?, val subtype: String?)
