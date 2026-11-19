package com.sclera.sclerago.domain.model

/** The types a field added to one asset can have, in the web's order ("Add field": Text, Number, Date, Yes / No, Link). */
val OWN_FIELD_KINDS = listOf(FieldKind.TEXT, FieldKind.NUMBER, FieldKind.DATE, FieldKind.YESNO, FieldKind.URL)

/** The longest name a field may have, as the server's (and the web's input) allow. */
const val OWN_FIELD_NAME_MAX = 120

/** What adding a field to one asset did. */
sealed interface AddFieldOutcome {
    /** Added; its answers go under [key] in the asset's form. */
    data class Added(val key: String) : AddFieldOutcome

    /** The asset's form already has a field of that name (a standard one or its own), as the server refuses it. */
    data object NameTaken : AddFieldOutcome

    /** No name was given. */
    data object NoName : AddFieldOutcome
}
