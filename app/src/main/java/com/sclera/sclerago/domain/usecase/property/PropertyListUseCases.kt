package com.sclera.sclerago.domain.usecase.property

import com.sclera.sclerago.domain.model.PropertySummary
import com.sclera.sclerago.domain.repository.AssetRepository
import com.sclera.sclerago.domain.repository.PropertyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/** Largest spreadsheet the property import accepts. */
const val MAX_IMPORT_BYTES = 100L * 1024 * 1024

/** Every property with its asset count, updated when either changes. */
class ObservePropertySummariesUseCase @Inject constructor(
    private val properties: PropertyRepository,
    private val assets: AssetRepository,
) {
    operator fun invoke(): Flow<List<PropertySummary>> =
        combine(properties.properties, assets.catalog) { list, catalog -> list.map { PropertySummary(it, catalog.countIn(it.id)) } }
}

/** The properties whose name contains [query] (case-insensitive, surrounding blanks ignored); all when it is blank. */
class FilterPropertySummariesUseCase @Inject constructor() {
    operator fun invoke(all: List<PropertySummary>, query: String): List<PropertySummary> {
        val q = query.trim().lowercase()
        return if (q.isEmpty()) all else all.filter { q in it.property.name.lowercase() }
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
