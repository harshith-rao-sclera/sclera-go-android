package com.sclera.sclerago.domain.usecase.asset

import com.sclera.sclerago.domain.model.SURE_ENOUGH
import com.sclera.sclerago.domain.model.SpecLookup
import com.sclera.sclerago.domain.model.UNCLASSIFIED
import javax.inject.Inject

/**
 * What filling the form from new photos puts in, as form values by key: [values] (the type, the JACS code and the
 * fields), the web page each looked-up field came from ([sources]), whether the type and the JACS code were set, and
 * how many fields were filled.
 */
data class PhotoFill(
    val values: Map<String, String>,
    val sources: Map<String, String>,
    val typeSet: Boolean,
    val jacsSet: Boolean,
    val fields: Int,
)

/**
 * The rules for filling the form from the photos just added (the phone's look-up of them), as the iOS app does: it
 * never overwrites what is there.
 * - **Type:** when none is picked and the AI is at least [SURE_ENOUGH] sure, its system, asset and sub type.
 * - **JACS code:** the type's likeliest code, when the JACS code is empty and the type was just set or is the asset's own.
 * - **Fields:** each empty field the AI is at least [SURE_ENOUGH] sure of, unless the photo contradicts what is there;
 *   the type's fields too when the type was set or is the asset's own.
 */
class PlanPhotoFillUseCase @Inject constructor() {
    operator fun invoke(lookup: SpecLookup, valueOf: (String) -> String, formatId: String?): PhotoFill {
        fun empty(key: String) = valueOf(key).let { it.isBlank() || (key in TYPE_KEYS && it == UNCLASSIFIED) }
        fun sure(key: String) = (lookup.confidence[key] ?: 1.0) >= SURE_ENOUGH && key !in lookup.differs

        val type = lookup.suggestedType
        val takeType = type != null && empty("sys") && type.confidence >= SURE_ENOUGH
        val ownType = type != null && formatId != null && type.masterFormatId == formatId
        val out = linkedMapOf<String, String>()
        if (takeType) {
            out["sys"] = type!!.systemType
            out["typ"] = type.assetType ?: UNCLASSIFIED
            out["sub"] = type.subtype ?: UNCLASSIFIED
        }
        val jacs = type?.jacsCode?.takeIf { (takeType || ownType) && empty(JACS) }
        jacs?.let { out[JACS] = it }

        val typeValues = if (takeType || ownType) type!!.values else emptyMap()
        val fields = (lookup.values + typeValues).filter { (key, value) ->
            value.isNotBlank() && key !in TYPE_KEYS && key != JACS && empty(key) && sure(key)
        }
        out += fields
        return PhotoFill(out, lookup.sources.filterKeys { it in fields }, takeType, jacs != null, fields.size)
    }

    private companion object {
        val TYPE_KEYS = setOf("sys", "typ", "sub")
        const val JACS = "jacs"
    }
}
