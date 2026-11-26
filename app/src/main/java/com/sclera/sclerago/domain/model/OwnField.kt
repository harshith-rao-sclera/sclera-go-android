package com.sclera.sclerago.domain.model

/**
 * The types a field added to one asset can have: the ones Configuration's Add field offers, in its order. The server takes
 * any of them for one asset (`POST /assets/{id}/fields`); a dropdown or multiple check boxes need options, a currency
 * field its currency. (The web's own Add field offers only Text, Number, Date, Yes / No and Link.)
 */
val OWN_FIELD_KINDS = listOf(
    FieldKind.TEXT, FieldKind.CHECKBOX, FieldKind.MULTI, FieldKind.YESNO, FieldKind.DATE,
    FieldKind.TIME, FieldKind.NUMBER, FieldKind.CURRENCY, FieldKind.URL, FieldKind.DROPDOWN,
)

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

    /** A dropdown or multiple check boxes was given no options. */
    data object NoOptions : AddFieldOutcome

    /** A currency field was given no currency. */
    data object NoCurrency : AddFieldOutcome
}
