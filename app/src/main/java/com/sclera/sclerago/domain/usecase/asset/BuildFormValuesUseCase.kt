package com.sclera.sclerago.domain.usecase.asset

import com.sclera.sclerago.domain.model.AssetDetails
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FieldKind
import com.sclera.sclerago.domain.system.TimeProvider
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
        val values = mutableMapOf("name" to name, "sys" to GENERIC, "typ" to GENERIC, "sub" to GENERIC)
        if (!isNew) details?.fields?.let { values.putAll(it) }
        if (isNew) {
            val now = Instant.ofEpochMilli(time.nowMillis()).atZone(zone)
            fields.filter { it.visible && it.defaultValue.isNotBlank() }.forEach { f ->
                values[f.key] = when {
                    f.kind == FieldKind.DATE && f.defaultValue == TODAY -> now.format(DATE_FORMAT)
                    f.kind == FieldKind.TIME && f.defaultValue == NOW -> now.format(TIME_FORMAT)
                    else -> f.defaultValue
                }
            }
            values.putAll(prefill)
        }
        return values
    }

    private companion object {
        const val GENERIC = "Generic"
        const val TODAY = "Today"
        const val NOW = "Now"
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
