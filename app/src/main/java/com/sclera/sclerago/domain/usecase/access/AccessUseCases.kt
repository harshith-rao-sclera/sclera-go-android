package com.sclera.sclerago.domain.usecase.access

import com.sclera.sclerago.domain.model.DirectoryPage
import com.sclera.sclerago.domain.model.Group
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.PermissionInfo
import com.sclera.sclerago.domain.model.Person
import com.sclera.sclerago.domain.repository.AccessRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

// People and access, as the web client does it: the server checks everything else and explains any refusal.

/** Why a person can't be saved; the presentation layer turns each into a localized message. */
enum class PersonProblem { EMAIL_REQUIRED, GROUP_REQUIRED }

/** Why a group can't be saved. */
enum class GroupProblem { NAME_REQUIRED }

/**
 * Why [p] can't be saved, or null when it can. Adding someone needs their email and a group; an existing person
 * may be left in no group (they keep their sign-in but can open nothing).
 */
fun personProblem(p: Person): PersonProblem? {
    if (p.id.isNotEmpty()) return null
    return when {
        p.email.isBlank() -> PersonProblem.EMAIL_REQUIRED
        p.groupId.isBlank() -> PersonProblem.GROUP_REQUIRED
        else -> null
    }
}

/** Why [g] can't be saved, or null when it can: a group needs a name. */
fun groupProblem(g: Group): GroupProblem? = if (g.name.isBlank()) GroupProblem.NAME_REQUIRED else null

class ObserveGroupsUseCase @Inject constructor(private val repo: AccessRepository) {
    operator fun invoke(): StateFlow<List<Group>> = repo.groups
}

class ObservePeopleUseCase @Inject constructor(private val repo: AccessRepository) {
    operator fun invoke(): StateFlow<List<Person>> = repo.people
}

/** Every permission a group can be given, with the server's names and descriptions. */
class ObservePermissionsUseCase @Inject constructor(private val repo: AccessRepository) {
    operator fun invoke(): StateFlow<List<PermissionInfo>> = repo.permissions
}

/**
 * A [page] (from 1) of the people the organisation's directory lists for [query] (everyone when blank), as the web
 * asks for them: [SUGGESTIONS] at a time for the add-user email field, [PAGE] at a time for the Users list.
 * Throws [OnlineException].
 */
class BrowseDirectoryUseCase @Inject constructor(private val repo: AccessRepository) {
    suspend operator fun invoke(query: String, limit: Int, page: Int = 1): DirectoryPage = repo.directory(query.trim(), limit, page)

    companion object {
        const val SUGGESTIONS = 20
        const val PAGE = 50
    }
}

/** Loads groups, people and permissions from the server again (what is shown stays when it fails; throws [OnlineException]). */
class RefreshAccessUseCase @Inject constructor(private val repo: AccessRepository) {
    suspend operator fun invoke() = repo.refresh()
}

/** Whether [p] could be saved as drafted. */
class ValidatePersonUseCase @Inject constructor() {
    operator fun invoke(p: Person): PersonProblem? = personProblem(p)
}

/** Whether [g] could be saved as drafted. */
class ValidateGroupUseCase @Inject constructor() {
    operator fun invoke(g: Group): GroupProblem? = groupProblem(g)
}

/**
 * Adds ([Person.id] empty, an invitation) or updates a person's group and properties; returns why it can't
 * instead, or null when saved. Choosing every property clears the chosen ones. Throws [OnlineException].
 */
class SavePersonUseCase @Inject constructor(private val repo: AccessRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(p: Person): PersonProblem? {
        personProblem(p)?.let { return it }
        guard.requireOnline(Permission.MANAGE_ACCESS)
        repo.savePerson(p.copy(email = p.email.trim(), propertyIds = if (p.allProperties) emptySet() else p.propertyIds))
        return null
    }
}

/**
 * Adds ([Group.id] empty) or updates a group; returns why it can't instead, or null when saved. A locked group
 * (Admins) always has every permission, so its permissions aren't sent. Throws [OnlineException].
 */
class SaveGroupUseCase @Inject constructor(private val repo: AccessRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(g: Group): GroupProblem? {
        groupProblem(g)?.let { return it }
        guard.requireOnline(Permission.MANAGE_ACCESS)
        repo.saveGroup(g.copy(name = g.name.trim(), description = g.description.trim()))
        return null
    }
}

/**
 * Deletes a group; returns false for a locked group (Admins) or one that still has [members] (people in it), which
 * can't be deleted (the server refuses them; the web asks for its people to be moved first). Throws [OnlineException].
 */
class DeleteGroupUseCase @Inject constructor(private val repo: AccessRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(g: Group, members: Int = 0): Boolean {
        if (g.locked || members > 0) return false
        guard.requireOnline(Permission.MANAGE_ACCESS)
        repo.deleteGroup(g.id)
        return true
    }
}

/** The people-and-access use cases, for injecting as one. */
class AccessUseCases @Inject constructor(
    val observeGroups: ObserveGroupsUseCase,
    val observePeople: ObservePeopleUseCase,
    val observePermissions: ObservePermissionsUseCase,
    val browseDirectory: BrowseDirectoryUseCase,
    val refresh: RefreshAccessUseCase,
    val validatePerson: ValidatePersonUseCase,
    val validateGroup: ValidateGroupUseCase,
    val savePerson: SavePersonUseCase,
    val saveGroup: SaveGroupUseCase,
    val deleteGroup: DeleteGroupUseCase,
)

/**
 * Whether the user may see and manage people and access (the server's manage-access permission): only they get the Users
 * page, as the server lists the organisation's users to them alone.
 */
class CanManageAccessUseCase @Inject constructor(private val guard: PermissionGuard) {
    operator fun invoke(): Boolean = guard.allows(Permission.MANAGE_ACCESS)
}
