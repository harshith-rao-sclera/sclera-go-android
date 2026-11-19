package com.sclera.sclerago.domain.usecase.validation

import com.sclera.sclerago.domain.model.ANSWER_NO
import com.sclera.sclerago.domain.model.ANSWER_YES
import com.sclera.sclerago.domain.model.CHECKED
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FieldKind
import com.sclera.sclerago.domain.model.NO_INPUT_KINDS
import com.sclera.sclerago.domain.model.UNCLASSIFIED
import com.sclera.sclerago.domain.usecase.asset.GetAssetDetailsUseCase
import com.sclera.sclerago.domain.usecase.asset.ObserveAssetFieldsUseCase
import com.sclera.sclerago.domain.validation.AssetInfoRow
import com.sclera.sclerago.domain.validation.ReviewAssetInfo
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

/**
 * The asset as it is on this phone, for the review's Asset info tab (the web's AssetDetails): each field the form shows,
 * in its order, with its value as a person reads it. Null when the asset isn't on this phone (not synced yet, or gone).
 */
class GetReviewAssetInfoUseCase @Inject constructor(
    private val details: GetAssetDetailsUseCase,
    private val fields: ObserveAssetFieldsUseCase,
) {
    suspend operator fun invoke(assetId: String, zone: ZoneId = ZoneId.systemDefault()): ReviewAssetInfo? {
        val values = details(assetId)?.fields ?: return null
        val shown = fields(assetId).first().filter { it.visible && it.kind !in NO_INPUT_KINDS }
        return ReviewAssetInfo(shown.map { f -> AssetInfoRow(f.label, readable(f, values[f.key].orEmpty().trim(), zone)) })
    }

    /** A stored value as the web shows it: dates and times (seconds since 1970) as such, checkboxes as Yes / No. */
    private fun readable(f: FieldCfg, v: String, zone: ZoneId): String? {
        if (v.isEmpty() || (f.key in CLASSIFICATION_KEYS && v == UNCLASSIFIED)) return null
        val at = v.toLongOrNull()?.let { Instant.ofEpochSecond(it).atZone(zone) }
        return when {
            f.kind == FieldKind.DATE && at != null -> at.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
            f.kind == FieldKind.TIME && at != null -> at.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
            f.kind == FieldKind.CHECKBOX -> if (v.equals(CHECKED, ignoreCase = true)) ANSWER_YES else ANSWER_NO
            else -> v
        }
    }

    private companion object {
        /** System, asset and sub type count as empty while they are still unclassified, as the form shows them. */
        val CLASSIFICATION_KEYS = setOf("sys", "typ", "sub")
    }
}
