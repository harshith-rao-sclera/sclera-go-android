package com.sclera.sclerago.domain.usecase.asset

import com.sclera.sclerago.domain.model.AssetDetails
import com.sclera.sclerago.domain.model.DEFAULT_DATE_PICKER
import com.sclera.sclerago.domain.model.DEFAULT_NOW
import com.sclera.sclerago.domain.model.DEFAULT_TIME_PICKER
import com.sclera.sclerago.domain.model.DEFAULT_TODAY
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.UNCLASSIFIED
import com.sclera.sclerago.domain.repository.AssetConfigRepository
import com.sclera.sclerago.domain.system.TimeProvider
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/**
 * The values an asset's details form starts from, before anything is typed: its name, "Generic" for the
 * classification fields, what it came with, and, for a new asset, each field's default (Configuration →
 * Asset fields) and what the scanned device it comes from already tells.
 */
class BuildFormValuesUseCase @Inject constructor(private val time: TimeProvider) {
    operator fun invoke(
        name: String,
        isNew: Boolean,
        details: AssetDetails?,
        fields: List<FieldCfg>,
        prefill: Map<String, String>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Map<String, String> {
        val values = mutableMapOf("name" to name, "sys" to UNCLASSIFIED, "typ" to UNCLASSIFIED, "sub" to UNCLASSIFIED)
        if (!isNew) details?.fields?.let { values.putAll(it) }
        if (isNew) {
            val now = Instant.ofEpochMilli(time.nowMillis()).atZone(zone)
            fields.filter { it.visible && it.defaultValue.isNotBlank() }.forEach { f ->
                // As the server's values.default_answer reads a default: "datepicker"/"timepicker" mean "picked on
                // the form" (no value), "today"/"now" (any case) mean the current date or time. Dates and times are
                // stored as the web's picker stores them: seconds since 1970 (UTC).
                when (f.defaultValue.trim().lowercase()) {
                    DEFAULT_DATE_PICKER, DEFAULT_TIME_PICKER -> Unit
                    DEFAULT_TODAY -> values[f.key] = now.toLocalDate().atStartOfDay(zone).toEpochSecond().toString()
                    DEFAULT_NOW -> values[f.key] = now.toEpochSecond().toString()
                    else -> values[f.key] = f.defaultValue
                }
            }
            values.putAll(prefill)
        }
        return values
    }
}

/**
 * The values of an asset added without its form (a scanned device's +, or Add on the ticked devices), as the new-asset
 * form would have started it: each field's default from the property's fields (Configuration → Asset fields), then what
 * the scan found ([prefill], which wins).
 */
class NewAssetValuesUseCase @Inject constructor(
    private val config: AssetConfigRepository,
    private val formValues: BuildFormValuesUseCase,
) {
    operator fun invoke(name: String, prefill: Map<String, String>): Map<String, String> =
        formValues(name, isNew = true, details = null, fields = config.config.value.fields, prefill = prefill)
}
