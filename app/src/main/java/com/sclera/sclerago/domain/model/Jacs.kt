package com.sclera.sclerago.domain.model

/** A code of the JACS catalog: the [code] an asset stores, and its catalogue [title]. */
data class JacsCode(val code: String, val title: String?)

/** One page of a JACS search, best matches first; [next] is where the following page starts (null: the last page). */
data class JacsPage(val codes: List<JacsCode>, val next: String?)

/** The property's type for a JACS code, by its names, as the form sets the system, asset and sub type. */
data class JacsType(val masterFormatId: String, val systemType: String, val assetType: String?, val subtype: String?)

/** A type a catalogue code is filed under, by its names (the catalogue's `groups`). */
data class JacsGroup(val systemType: String, val assetType: String?, val subtype: String?)

/** A code of the whole JACS catalogue as the phone keeps it (`GET /jacs-codes/catalog`): its title and the types it's filed under. */
data class JacsCatalogCode(val code: String, val title: String?, val groups: List<JacsGroup> = emptyList())

/**
 * Whether two JACS codes sit under the same MasterFormat number: their first six digits ("238219-TRM" and "238219"), as
 * the web's sameJacsNumber. An asset keeps such a code when it's given a type whose own code is the other one.
 */
fun sameJacsNumber(a: String?, b: String?): Boolean {
    fun digits(code: String?) = code?.trim()?.take(6)?.takeIf { it.length == 6 && it.all(Char::isDigit) }
    val first = digits(a) ?: return false
    return first == digits(b)
}

/**
 * The JACS code an asset gets when it's given the type [format] (the backend's rule for the phone): the type's own code,
 * unless the asset's [current] code is that code or already under the same number, or the type has none. Null: the code
 * stays.
 */
fun jacsCodeForType(format: MasterFormat?, current: String?): String? {
    val code = format?.jacsCode?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val now = current?.trim().orEmpty()
    return code.takeUnless { it == now || sameJacsNumber(now, it) }
}
