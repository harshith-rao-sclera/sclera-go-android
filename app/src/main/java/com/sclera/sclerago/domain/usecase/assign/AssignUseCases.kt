package com.sclera.sclerago.domain.usecase.assign

import com.sclera.sclerago.domain.model.AssignResult
import com.sclera.sclerago.domain.model.DirectoryPerson
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.repository.AccessRepository
import com.sclera.sclerago.domain.repository.AssetAssignmentRepository
import javax.inject.Inject

// Assign asset (asset list selection and asset details), as the web's: the assets go to one person.

/**
 * Who assets can be assigned to, matching [query]: the organisation's people as Sclera lists them (what Add user picks
 * from), [LIMIT] at a time; when Sclera can't be asked, the users already here. Throws [OnlineException].
 */
class FindAssigneesUseCase @Inject constructor(private val access: AccessRepository) {
    suspend operator fun invoke(query: String): List<DirectoryPerson> {
        val typed = query.trim()
        val found = access.directory(typed, LIMIT, 1)
        if (found.available) return found.people
        val q = typed.lowercase()
        return access.people.value
            .filter { q.isEmpty() || q in "${it.fullName.orEmpty()} ${it.email}".lowercase() }
            .map { DirectoryPerson(it.email, it.fullName, it.id) }
    }

    companion object {
        const val LIMIT = 50
    }
}

/**
 * Gives [assetIds] of [propertyId] to [email]; no email takes them off whoever has them. Someone not here yet is
 * added as a Collector on the property by the server. Throws [OnlineException].
 */
class AssignAssetsUseCase @Inject constructor(private val repo: AssetAssignmentRepository) {
    suspend operator fun invoke(propertyId: String, assetIds: Collection<String>, email: String?): AssignResult {
        val ids = assetIds.filter { it.isNotBlank() }
        if (ids.isEmpty()) return AssignResult(0, null, null, added = false, givenAccess = false)
        return repo.assign(propertyId, ids, email?.trim()?.takeIf { it.isNotEmpty() })
    }
}

/** The Assign asset use cases, for injecting as one. */
class AssignUseCases @Inject constructor(
    val findAssignees: FindAssigneesUseCase,
    val assign: AssignAssetsUseCase,
)
