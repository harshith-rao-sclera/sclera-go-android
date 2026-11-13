package com.sclera.sclerago.domain.usecase.asset

import com.sclera.sclerago.domain.validation.AssetCheck
import com.sclera.sclerago.domain.validation.AssetCheckId
import com.sclera.sclerago.domain.validation.AssetCheckInput
import com.sclera.sclerago.domain.validation.AssetChecks
import com.sclera.sclerago.domain.validation.CheckNote
import com.sclera.sclerago.domain.validation.CheckOutcome
import com.sclera.sclerago.domain.validation.FixTarget
import javax.inject.Inject

/**
 * Runs the Validation tab's checks on an asset. A missing value fails the check when its field is mandatory
 * (or it is the location) and only warns otherwise.
 */
class EvaluateAssetChecksUseCase @Inject constructor() {
    operator fun invoke(input: AssetCheckInput): AssetChecks = AssetChecks(
        listOf(location(input), assetType(input), jacs(input), makeModel(input), serial(input), lowConfidence(input), photos(input)),
    )

    private fun value(input: AssetCheckInput, key: String) = input.values[key].orEmpty().trim()

    /** "Generic" is the placeholder for an unclassified type. */
    private fun classified(input: AssetCheckInput, key: String) = value(input, key).takeIf { it.isNotEmpty() && it != GENERIC }

    private fun missing(id: AssetCheckId, required: Boolean, fix: FixTarget, field: String, note: CheckNote) = AssetCheck(
        id, if (required) CheckOutcome.FAIL else CheckOutcome.WARN, required, note, fix = fix, fixField = field,
    )

    private fun passed(id: AssetCheckId, required: Boolean, shown: String) =
        AssetCheck(id, CheckOutcome.PASS, required, CheckNote.VALUE, listOf(shown))

    private fun location(i: AssetCheckInput) =
        if (i.hasLocation) passed(AssetCheckId.LOCATION, false, i.locationLabel)
        else AssetCheck(AssetCheckId.LOCATION, CheckOutcome.FAIL, note = CheckNote.MISSING, fix = FixTarget.LOCATION)

    private fun assetType(i: AssetCheckInput): AssetCheck {
        val required = "typ" in i.requiredKeys || "sys" in i.requiredKeys
        val shown = listOfNotNull(classified(i, "sys"), classified(i, "typ"))
        return if (classified(i, "typ") != null) passed(AssetCheckId.ASSET_TYPE, required, shown.joinToString(SEPARATOR))
        else missing(AssetCheckId.ASSET_TYPE, required, FixTarget.CLASS, "typ", CheckNote.MISSING)
    }

    private fun jacs(i: AssetCheckInput): AssetCheck {
        val required = "jacs" in i.requiredKeys
        val code = value(i, "jacs")
        return if (code.isNotEmpty()) passed(AssetCheckId.JACS_CODE, required, code)
        else missing(
            AssetCheckId.JACS_CODE, required, FixTarget.CLASS, "jacs",
            if (required) CheckNote.REQUIRED_BEFORE_VALIDATE else CheckNote.MISSING,
        )
    }

    private fun makeModel(i: AssetCheckInput): AssetCheck {
        val required = "make" in i.requiredKeys || "model" in i.requiredKeys
        val make = value(i, "make")
        val model = value(i, "model")
        return if (make.isNotEmpty() && model.isNotEmpty()) passed(AssetCheckId.MAKE_MODEL, required, "$make $model")
        else missing(AssetCheckId.MAKE_MODEL, required, FixTarget.IDENT, if (make.isEmpty()) "make" else "model", CheckNote.MISSING)
    }

    private fun serial(i: AssetCheckInput): AssetCheck {
        val required = "serial" in i.requiredKeys
        val serial = value(i, "serial")
        return if (serial.isNotEmpty()) passed(AssetCheckId.SERIAL, required, serial)
        else missing(AssetCheckId.SERIAL, required, FixTarget.IDENT, "serial", CheckNote.MISSING)
    }

    private fun lowConfidence(i: AssetCheckInput): AssetCheck {
        val key = i.lowConfidenceKey ?: return AssetCheck(AssetCheckId.LOW_CONFIDENCE, CheckOutcome.PASS)
        return AssetCheck(
            AssetCheckId.LOW_CONFIDENCE, CheckOutcome.WARN, note = CheckNote.LOW_CONFIDENCE_READING,
            args = listOf(i.lowConfidenceLabel), fix = FixTarget.IDENT, fixField = key,
        )
    }

    private fun photos(i: AssetCheckInput): AssetCheck {
        val needed = if (i.fromCamera) i.minPhotos.coerceAtLeast(1) else 0
        return when {
            i.photoCount >= needed && i.photoCount > 0 -> AssetCheck(AssetCheckId.PHOTO, CheckOutcome.PASS, note = CheckNote.PHOTO_COUNT, count = i.photoCount)
            i.photoCount < needed -> AssetCheck(
                AssetCheckId.PHOTO, CheckOutcome.FAIL, note = CheckNote.PHOTOS_NEEDED, count = i.photoCount, needed = needed, fix = FixTarget.PHOTOS,
            )
            else -> AssetCheck(AssetCheckId.PHOTO, CheckOutcome.WARN, note = CheckNote.NO_PHOTO, fix = FixTarget.PHOTOS)
        }
    }

    private companion object {
        const val GENERIC = "Generic"
        const val SEPARATOR = " · "
    }
}
