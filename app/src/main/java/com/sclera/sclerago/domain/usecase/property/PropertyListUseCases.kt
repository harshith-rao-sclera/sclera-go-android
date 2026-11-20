package com.sclera.sclerago.domain.usecase.property

import com.sclera.sclerago.domain.limits.MAX_IMPORT_BYTES
import com.sclera.sclerago.domain.model.PropertySummary
import com.sclera.sclerago.domain.repository.AssetRepository
import com.sclera.sclerago.domain.repository.PropertyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/** Every property with its asset count, updated when either changes. Only the downloaded assets are counted, by the property they belong to; the others have none here. */
class ObservePropertySummariesUseCase @Inject constructor(
    private val properties: PropertyRepository,
    private val assets: AssetRepository,
) {
    operator fun invoke(): Flow<List<PropertySummary>> =
        combine(properties.properties, assets.catalog) { list, catalog ->
            val counts = catalog.assets.groupingBy { it.propertyId }.eachCount()
            list.map { PropertySummary(it, counts[it.id] ?: 0) }
        }
}

/**
 * The properties whose name contains [query] (case-insensitive, surrounding blanks ignored); all when it is blank.
 * They keep the server's order: downloaded ones aren't moved to the top.
 */
class FilterPropertySummariesUseCase @Inject constructor() {
    /** The properties whose name holds [query]: the downloaded ones ([downloaded]) first, each group A to Z by name. */
    operator fun invoke(all: List<PropertySummary>, query: String, downloaded: Set<String> = emptySet()): List<PropertySummary> {
        val q = query.trim().lowercase()
        return all.filter { q.isEmpty() || q in it.property.name.lowercase() }
            .sortedWith(compareBy<PropertySummary> { it.property.id !in downloaded }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.property.name.trim() })
    }
}

/** Why a spreadsheet can't be imported. */
enum class ImportFileProblem { NOT_XLSX, TOO_LARGE }

/** Checks a picked file's name and size (-1 when unknown) before the import accepts it. */
class ValidateImportFileUseCase @Inject constructor() {
    operator fun invoke(name: String, size: Long): ImportFileProblem? = when {
        !name.endsWith(".xlsx", ignoreCase = true) -> ImportFileProblem.NOT_XLSX
        size > MAX_IMPORT_BYTES -> ImportFileProblem.TOO_LARGE
        else -> null
    }
}
