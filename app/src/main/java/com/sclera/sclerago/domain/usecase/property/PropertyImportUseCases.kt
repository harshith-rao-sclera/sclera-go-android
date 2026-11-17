package com.sclera.sclerago.domain.usecase.property

import com.sclera.sclerago.domain.model.ImportPlan
import com.sclera.sclerago.domain.model.ImportResult
import com.sclera.sclerago.domain.model.ImportTarget
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.repository.PropertyImportRepository
import javax.inject.Inject

// Import properties (property list → Import), as the web's Import on the new API: choose the sheet, check the column
// each detail is read from (the server proposes them), then import.

/** Saves the server's sample sheet to [destination] (Sample Format's download). Throws [OnlineException]. */
class SavePropertyTemplateUseCase @Inject constructor(private val repo: PropertyImportRepository) {
    suspend operator fun invoke(destination: String) = repo.saveTemplate(destination)
}

/**
 * Proceed: the server reads [file] and proposes a detail for each column; the mapping starts from those proposals
 * (detail key → column heading). Throws [OnlineException].
 */
class PreparePropertyImportUseCase @Inject constructor(private val repo: PropertyImportRepository) {
    suspend operator fun invoke(file: String): ImportPlan {
        val suggestion = repo.suggest(file)
        val targets = repo.targets()
        val known = targets.map { it.key }.toSet()
        val mapping = suggestion.columns
            .filter { it.target != null && it.target in known }
            .associate { it.target!! to it.heading }
        return ImportPlan(targets, suggestion, mapping)
    }
}

/**
 * Import: needs a column for every required detail (the property's name); then imports [file] with [mapping]
 * (detail key → column heading), sent as the web sends it (column heading → detail key). Throws [OnlineException].
 */
class ImportPropertiesUseCase @Inject constructor(private val repo: PropertyImportRepository) {
    sealed interface Result {
        data class Imported(val result: ImportResult) : Result

        /** A required detail has no column yet. */
        data object MissingRequired : Result
    }

    suspend operator fun invoke(file: String, targets: List<ImportTarget>, mapping: Map<String, String>): Result {
        val chosen = mapping.filterValues { it.isNotBlank() }
        val needed = targets.filter { it.required }.map { it.key }.ifEmpty { listOf(ImportTarget.REQUIRED_KEY) }
        if (needed.any { it !in chosen }) return Result.MissingRequired
        val byHeading = chosen.entries.associate { (target, heading) -> heading to target }
        return Result.Imported(repo.import(file, byHeading))
    }
}

/** The Import use cases, for injecting as one. */
class PropertyImportUseCases @Inject constructor(
    val saveTemplate: SavePropertyTemplateUseCase,
    val prepare: PreparePropertyImportUseCase,
    val import: ImportPropertiesUseCase,
)
