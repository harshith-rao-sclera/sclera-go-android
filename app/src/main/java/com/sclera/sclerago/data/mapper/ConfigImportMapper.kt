package com.sclera.sclerago.data.mapper

import com.sclera.sclerago.data.remote.adc.FieldImportReadDto
import com.sclera.sclerago.data.remote.adc.FieldImportRowDto
import com.sclera.sclerago.data.remote.adc.PlaceImportResultDto
import com.sclera.sclerago.data.remote.adc.SheetSkipDto
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FieldKind
import com.sclera.sclerago.domain.model.FieldSection
import com.sclera.sclerago.domain.model.FieldsRead
import com.sclera.sclerago.domain.model.PlacesImported
import com.sclera.sclerago.domain.model.SheetSkip
import com.sclera.sclerago.domain.model.SkipReason
import com.sclera.sclerago.domain.model.configFieldKindOf
import com.sclera.sclerago.domain.model.currencyInName
import com.sclera.sclerago.domain.model.optionColorOrNull

// Configuration's spreadsheet imports: what the server read or made (`/places/import`, `/fields/import/read`) as the
// app's models. The server's reasons are mapped to a [SkipReason]; its text is never shown.

/** The server skips a photo, video or voice memo row with a reason that names Media; every other skip is "already there". */
private fun SheetSkipDto.toSkip() = SheetSkip(
    row = row,
    name = name.trim(),
    reason = if (reason.contains(MEDIA_WORD, ignoreCase = true)) SkipReason.MEDIA else SkipReason.ALREADY_THERE,
)

private const val MEDIA_WORD = "media"

private fun List<String>.cleanColumns() = map { it.trim() }.filter { it.isNotEmpty() }.distinct()

fun PlaceImportResultDto.toPlacesImported() = PlacesImported(
    buildings = buildingsCreated.coerceAtLeast(0),
    floors = floorsCreated.coerceAtLeast(0),
    locations = locationsCreated.coerceAtLeast(0),
    skipped = skipped.map { it.toSkip() },
    ignoredColumns = ignoredColumns.cleanColumns(),
)

/**
 * A field read from the sheet as a new custom field for the Add field sheet (keyed by its row, so each draft is told
 * apart), or null for a type the app doesn't know.
 */
private fun FieldImportRowDto.toDraft(): FieldCfg? {
    val kind = configFieldKindOf(type) ?: return null
    val label = name.trim().takeIf { it.isNotEmpty() } ?: return null
    return FieldCfg(
        key = "cf_import_$row",
        label = label,
        kind = kind,
        section = FieldSection.MORE,
        visible = isActive,
        required = isRequired,
        custom = true,
        options = options.orEmpty().map { it.label },
        optionColors = options.orEmpty().mapNotNull { o -> optionColorOrNull(o.color)?.let { o.label to it } }.toMap(),
        defaultValue = defaultValue.orEmpty(),
        tooltip = helpText.orEmpty(),
        // A currency row's name ends with its currency ("Purchase cost (USD)"), as the server requires.
        unit = if (kind == FieldKind.CURRENCY) currencyInName(label).orEmpty() else "",
    )
}

fun FieldImportReadDto.toFieldsRead() = FieldsRead(
    fields = fields.mapNotNull { it.toDraft() },
    skipped = skipped.map { it.toSkip() },
    ignoredColumns = ignoredColumns.cleanColumns(),
)
