package com.sclera.sclerago.domain.usecase.validation

import com.sclera.sclerago.domain.system.ClockFormat
import java.util.Locale
import com.sclera.sclerago.domain.model.ANSWER_NO
import com.sclera.sclerago.domain.model.ANSWER_YES
import com.sclera.sclerago.domain.model.CHECKED
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FieldKind
import com.sclera.sclerago.domain.model.NO_INPUT_KINDS
import com.sclera.sclerago.domain.model.UNCLASSIFIED
import com.sclera.sclerago.domain.model.amountText
import com.sclera.sclerago.domain.repository.ValidationRunRepository
import com.sclera.sclerago.domain.validation.AssetInfoRow
import com.sclera.sclerago.domain.validation.ReviewAssetInfo
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

/**
 * The asset as the server has it, for the review's Asset info tab (the web's AssetDetails): each field its form has, in
 * order, with its value as a person reads it, then where it is and its status. Throws
 * [com.sclera.sclerago.domain.model.OnlineException] when it can't be read (offline, or deleted since the run).
 */
class GetReviewAssetInfoUseCase @Inject constructor(
    private val repo: ValidationRunRepository,
    private val clock: ClockFormat,
) {
    suspend operator fun invoke(propertyId: String, assetId: String, zone: ZoneId = ZoneId.systemDefault()): ReviewAssetInfo {
        val asset = repo.reviewAsset(propertyId, assetId)
        val shown = asset.fields.filter { it.visible && it.kind !in NO_INPUT_KINDS }
        return ReviewAssetInfo(
            rows = shown.map { f -> AssetInfoRow(f.label, readable(f, asset.values[f.id].orEmpty().trim(), zone)) },
            place = asset.place,
            status = asset.status,
        )
    }

    /** A stored value as the web shows it: dates and times (seconds since 1970) as such, checkboxes as Yes / No. */
    private fun readable(f: FieldCfg, v: String, zone: ZoneId): String? {
        if (v.isEmpty() || (f.key in CLASSIFICATION_KEYS && v == UNCLASSIFIED)) return null
        val at = v.toLongOrNull()?.let { Instant.ofEpochSecond(it).atZone(zone) }
        return when {
            f.kind == FieldKind.DATE && at != null -> at.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
            // On the phone's 12/24-hour clock.
            f.kind == FieldKind.TIME && at != null -> at.format(DateTimeFormatter.ofPattern(if (clock.is24Hour()) "HH:mm" else "h:mm a", Locale.getDefault()))
            f.kind == FieldKind.CHECKBOX -> if (v.equals(CHECKED, ignoreCase = true)) ANSWER_YES else ANSWER_NO
            // A currency field's amount with its currency ("1200 USD"), as the web reads it.
            else -> f.amountText(v)
        }
    }

    private companion object {
        /** System, asset and sub type count as empty while they are still unclassified, as the form shows them. */
        val CLASSIFICATION_KEYS = setOf("sys", "typ", "sub")
    }
}
