package com.sclera.sclerago.domain.usecase.access

import com.sclera.sclerago.domain.model.ADMINS_ID
import com.sclera.sclerago.domain.model.Group
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.Person
import com.sclera.sclerago.domain.repository.AccessRepository
import com.sclera.sclerago.domain.system.IdGenerator
import com.sclera.sclerago.domain.system.TimeProvider
import com.sclera.sclerago.domain.usecase.auth.ValidateEmailUseCase
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Why a person can't be saved; the presentation layer turns each into a localized message. */
enum class PersonProblem { EMAIL_REQUIRED, EMAIL_INVALID, EMAIL_TAKEN, GROUP_REQUIRED, GROUP_NEEDS_ALL_PROPERTIES, NO_PROPERTY }

/** Why a group can't be saved. */
enum class GroupProblem { NAME_REQUIRED, NAME_TAKEN }

/** Why [p] can't be saved, or null when it can. */
fun personProblem(p: Person, people: List<Person>, group: Group?): PersonProblem? {
    val email = p.email.trim()
    return when {
        email.isEmpty() -> PersonProblem.EMAIL_REQUIRED
        !ValidateEmailUseCase.EMAIL_ADDRESS.matches(email) -> PersonProblem.EMAIL_INVALID
        people.any { it.id != p.id && it.email.equals(email, ignoreCase = true) } -> PersonProblem.EMAIL_TAKEN
        group == null -> PersonProblem.GROUP_REQUIRED
        group.needsAllProperties && !p.allProperties -> PersonProblem.GROUP_NEEDS_ALL_PROPERTIES
        !p.allProperties && p.propertyIds.isEmpty() -> PersonProblem.NO_PROPERTY
        else -> null
    }
}

/** Why [g] can't be saved, or null when it can. */
fun groupProblem(g: Group, groups: List<Group>): GroupProblem? {
    val name = g.name.trim()
    return when {
        name.isEmpty() -> GroupProblem.NAME_REQUIRED
        groups.any { it.id != g.id && it.name.equals(name, ignoreCase = true) } -> GroupProblem.NAME_TAKEN
        else -> null
    }
}

class ObserveGroupsUseCase @Inject constructor(private val repo: AccessRepository) {
    operator fun invoke(): StateFlow<List<Group>> = repo.groups
}

class ObservePeopleUseCase @Inject constructor(private val repo: AccessRepository) {
    operator fun invoke(): StateFlow<List<Person>> = repo.people
}

/** Whether [p] could be saved as drafted, against the people and groups as they are now. */
class ValidatePersonUseCase @Inject constructor(private val repo: AccessRepository) {
    operator fun invoke(p: Person): PersonProblem? =
        personProblem(p, repo.people.value, repo.groups.value.firstOrNull { it.id == p.groupId })
}

/** Whether [g] could be saved as drafted. */
class ValidateGroupUseCase @Inject constructor(private val repo: AccessRepository) {
    operator fun invoke(g: Group): GroupProblem? = groupProblem(g, repo.groups.value)
}

/** Puts the signed-in user in as an admin the first time People and access opens. */
class SeedSignedInUserUseCase @Inject constructor(
    private val repo: AccessRepository,
    private val ids: IdGenerator,
    private val time: TimeProvider,
) {
    operator fun invoke(currentEmail: String?) {
        if (repo.people.value.isNotEmpty() || currentEmail.isNullOrBlank()) return
        repo.setPeople(listOf(Person(ids.next("u"), currentEmail, ADMINS_ID, allProperties = true, lastSeenAt = time.nowMillis())))
    }
}

/** Adds ([Person.id] empty) or updates a person; returns why it can't instead, or null when saved. */
class SavePersonUseCase @Inject constructor(private val repo: AccessRepository, private val ids: IdGenerator) {
    operator fun invoke(p: Person): PersonProblem? {
        val people = repo.people.value
        personProblem(p, people, repo.groups.value.firstOrNull { it.id == p.groupId })?.let { return it }
        val clean = p.copy(email = p.email.trim(), propertyIds = if (p.allProperties) emptySet() else p.propertyIds)
        repo.setPeople(
            if (clean.id.isEmpty()) people + clean.copy(id = ids.next("u"))
            else people.map { if (it.id == clean.id) clean else it },
        )
        return null
    }
}

class RemovePersonUseCase @Inject constructor(private val repo: AccessRepository) {
    operator fun invoke(p: Person) = repo.setPeople(repo.people.value.filterNot { it.id == p.id })
}

/**
 * How many people saving [g] would move from chosen properties to every property: those already in
 * the group when it (now) has a permission that needs all properties. The caller asks the user first.
 */
class CountPeopleGivenAllPropertiesUseCase @Inject constructor(private val repo: AccessRepository) {
    operator fun invoke(g: Group): Int =
        if (!g.needsAllProperties || g.id.isEmpty()) 0
        else repo.people.value.count { it.groupId == g.id && !it.allProperties }
}

/**
 * Adds ([Group.id] empty) or updates a group; returns why it can't instead, or null when saved.
 * Locked groups (Admins) keep their name and every permission; people in a group that now needs
 * every property get every property (see [CountPeopleGivenAllPropertiesUseCase]).
 */
class SaveGroupUseCase @Inject constructor(private val repo: AccessRepository, private val ids: IdGenerator) {
    operator fun invoke(g: Group): GroupProblem? {
        val groups = repo.groups.value
        groupProblem(g, groups)?.let { return it }
        val clean = g.copy(
            name = if (g.locked) groups.firstOrNull { it.id == g.id }?.name ?: g.name.trim() else g.name.trim(),
            description = g.description.trim(),
            permissions = if (g.locked) Permission.entries.toSet() else g.permissions,
        )
        repo.setGroups(
            if (clean.id.isEmpty()) groups + clean.copy(id = ids.next("g"))
            else groups.map { if (it.id == clean.id) clean else it },
        )
        if (clean.needsAllProperties) {
            repo.setPeople(repo.people.value.map { if (it.groupId == clean.id) it.copy(allProperties = true, propertyIds = emptySet()) else it })
        }
        return null
    }
}

/** A group can be deleted when it isn't locked and has nobody in it. */
class CanDeleteGroupUseCase @Inject constructor(private val repo: AccessRepository) {
    operator fun invoke(g: Group): Boolean = !g.locked && repo.people.value.none { it.groupId == g.id }
}

/** Deletes a group if it can be deleted; returns whether it was. */
class DeleteGroupUseCase @Inject constructor(private val repo: AccessRepository, private val canDelete: CanDeleteGroupUseCase) {
    operator fun invoke(g: Group): Boolean {
        if (!canDelete(g)) return false
        repo.setGroups(repo.groups.value.filterNot { it.id == g.id })
        return true
    }
}

/** The people-and-access use cases, for injecting as one. */
class AccessUseCases @Inject constructor(
    val observeGroups: ObserveGroupsUseCase,
    val observePeople: ObservePeopleUseCase,
    val validatePerson: ValidatePersonUseCase,
    val validateGroup: ValidateGroupUseCase,
    val seedSignedInUser: SeedSignedInUserUseCase,
    val savePerson: SavePersonUseCase,
    val removePerson: RemovePersonUseCase,
    val countGivenAllProperties: CountPeopleGivenAllPropertiesUseCase,
    val saveGroup: SaveGroupUseCase,
    val canDeleteGroup: CanDeleteGroupUseCase,
    val deleteGroup: DeleteGroupUseCase,
)
