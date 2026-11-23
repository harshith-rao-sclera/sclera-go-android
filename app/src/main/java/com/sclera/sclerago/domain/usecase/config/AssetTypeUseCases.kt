package com.sclera.sclerago.domain.usecase.config

import com.sclera.sclerago.domain.model.ClassCount
import com.sclera.sclerago.domain.model.TypeField
import com.sclera.sclerago.domain.repository.AssetTypeCatalogRepository
import com.sclera.sclerago.domain.repository.ClassificationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

// Configuration → Asset types: the property's type catalogue and the fields each type brings, read only.

/** One visible row of the type list: [path] is the names from the system type down, [depth] 0 for a system type. */
data class TypeRow(
    val path: List<String>,
    val depth: Int,
    val assets: Int,
    val formatId: String?,
    val hasKids: Boolean,
    val open: Boolean,
) {
    val name: String get() = path.last()
}

/** The assets filed under all the system types. */
fun List<ClassCount>.totalAssets(): Int = sumOf { it.assets }

/** The node [path] (names from the system type down) points at, or null when the catalogue has no such type. */
fun List<ClassCount>.nodeAt(path: List<String>): ClassCount? {
    var level = this
    var found: ClassCount? = null
    for (name in path) {
        found = level.firstOrNull { it.name == name } ?: return null
        level = found.kids
    }
    return found
}

/** Every type that has sub types, as paths: what "Expand all" opens. */
fun List<ClassCount>.expandablePaths(parent: List<String> = emptyList()): Set<List<String>> =
    flatMap { n -> (parent + n.name).let { p -> if (n.kids.isEmpty()) emptyList() else listOf(p) + n.kids.expandablePaths(p) } }.toSet()

/**
 * The rows to show: the whole tree with the types in [expanded] opened, or, for a non-blank [query], only the types
 * whose name has it (with everything under them) and the types above those, all opened.
 */
fun List<ClassCount>.visibleRows(expanded: Set<List<String>>, query: String): List<TypeRow> {
    val q = query.trim()
    fun keeps(n: ClassCount): Boolean = n.name.contains(q, ignoreCase = true) || n.kids.any(::keeps)
    fun rows(level: List<ClassCount>, parent: List<String>, underMatch: Boolean): List<TypeRow> = level.flatMap { n ->
        val matches = q.isEmpty() || underMatch || n.name.contains(q, ignoreCase = true)
        if (q.isNotEmpty() && !matches && !keeps(n)) return@flatMap emptyList()
        val path = parent + n.name
        val open = n.kids.isNotEmpty() && (q.isNotEmpty() || path in expanded)
        listOf(TypeRow(path, parent.size, n.assets, n.formatId, n.kids.isNotEmpty(), open)) +
            if (open) rows(n.kids, path, matches && q.isNotEmpty()) else emptyList()
    }
    return rows(this, emptyList(), false)
}

class ObserveAssetTypesUseCase @Inject constructor(private val repo: AssetTypeCatalogRepository) {
    operator fun invoke(): Flow<List<ClassCount>> = repo.tree()
}

/** The fields an asset of the type [formatId] gets, in the type's order (empty when the library doesn't know the type). */
class LoadTypeFieldsUseCase @Inject constructor(private val repo: ClassificationRepository) {
    suspend operator fun invoke(formatId: String): List<TypeField> = repo.typeFields(formatId).sortedBy { it.position }
}

class AssetTypeUseCases @Inject constructor(
    val observe: ObserveAssetTypesUseCase,
    val fields: LoadTypeFieldsUseCase,
)
