package com.sclera.sclerago.domain.usecase.assign

import com.sclera.sclerago.domain.model.AccessState
import com.sclera.sclerago.domain.model.AssignResult
import com.sclera.sclerago.domain.model.DirectoryPerson
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.repository.AccessRepository
import com.sclera.sclerago.domain.repository.AssetAssignmentRepository
import com.sclera.sclerago.domain.repository.CurrentAccessRepository
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
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
 * added as a Collector on the property by the server. The server lets only admins do it ("Only an admin can assign
 * assets to someone."), so it is offered to admins only. Throws [OnlineException].
 */
class AssignAssetsUseCase @Inject constructor(private val repo: AssetAssignmentRepository, private val guard: PermissionGuard) {
    /** Whether the signed-in user may assign assets (so the action shows): an admin who can collect, as the server checks. */
    fun allowed(): Boolean = guard.allows(Permission.COLLECT) && guard.isAdmin()

    /** Throws [OnlineException] Forbidden unless the user is an admin who can collect (the server's "Only an admin can assign"). */
    suspend operator fun invoke(propertyId: String, assetIds: Collection<String>, email: String?): AssignResult {
        guard.requireOnline(Permission.COLLECT)
        if (!guard.isAdmin()) throw OnlineException(OnlineError.Forbidden)
        val ids = assetIds.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) return AssignResult(0, null, null, added = false, givenAccess = false)
        return repo.assign(propertyId, ids, email?.trim()?.takeIf { it.isNotEmpty() })
    }
}

/** The signed-in user's email, for "Assign to me" (the web's logged-in user); null until their access is known. */
class MyEmailUseCase @Inject constructor(private val access: CurrentAccessRepository) {
    operator fun invoke(): String? = (access.state.value as? AccessState.Loaded)?.access?.email?.trim()?.takeIf { it.isNotEmpty() }
}

/**
 * The email of the person an asset of [propertyId] is assigned to ([userId], as the asset keeps it), for opening the sheet
 * on it as the web does with one asset ticked: from the property's assignees kept on the phone, or the signed-in user's own.
 * Null when it is nobody's or the person isn't known here.
 */
class CurrentAssigneeUseCase @Inject constructor(
    private val repo: AssetAssignmentRepository,
    private val guard: PermissionGuard,
    private val myEmail: MyEmailUseCase,
) {
    operator fun invoke(propertyId: String, userId: String?): String? {
        val id = userId?.takeIf { it.isNotBlank() } ?: return null
        repo.keptAssigneesOf(propertyId)[id]?.email?.takeIf { it.isNotBlank() }?.let { return it }
        return if (guard.userId() == id) myEmail() else null
    }
}

/** The Assign asset use cases, for injecting as one. */
class AssignUseCases @Inject constructor(
    val findAssignees: FindAssigneesUseCase,
    val assign: AssignAssetsUseCase,
    val myEmail: MyEmailUseCase,
    val currentAssignee: CurrentAssigneeUseCase,
)
