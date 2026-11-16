package com.sclera.sclerago.domain.usecase.asset

import com.sclera.sclerago.domain.model.AssetDetails
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FieldKind
import com.sclera.sclerago.domain.model.UNCLASSIFIED
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
                values[f.key] = when {
                    // Dates and times are stored as the web stores them: seconds since 1970 (UTC).
                    f.kind == FieldKind.DATE && f.defaultValue == TODAY -> now.toLocalDate().atStartOfDay(zone).toEpochSecond().toString()
                    f.kind == FieldKind.TIME && f.defaultValue == NOW -> now.toEpochSecond().toString()
                    else -> f.defaultValue
                }
            }
            values.putAll(prefill)
        }
        return values
    }

    private companion object {
        const val TODAY = "Today"
        const val NOW = "Now"
    }
}
