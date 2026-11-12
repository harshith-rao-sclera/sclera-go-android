package com.sclera.sclerago.domain.usecase.access

import com.sclera.sclerago.domain.model.ADMINS_ID
import com.sclera.sclerago.domain.model.Group
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.Person
import com.sclera.sclerago.domain.repository.AccessRepository
import com.sclera.sclerago.domain.usecase.auth.ValidateEmailUseCase
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Why [p] can't be saved, or null when it can. */
fun personProblem(p: Person, people: List<Person>, group: Group?): String? {
    val email = p.email.trim()
    return when {
        email.isEmpty() -> "Enter their email address."
        !ValidateEmailUseCase.EMAIL_ADDRESS.matches(email) -> "Enter a valid email address."
        people.any { it.id != p.id && it.email.equals(email, ignoreCase = true) } -> "$email already has access."
        group == null -> "Choose a group."
        group.needsAllProperties && !p.allProperties -> "${group.name} has permissions that need all properties."
        !p.allProperties && p.propertyIds.isEmpty() -> "Choose at least one property, or give them all properties."
        else -> null
    }
}

/** Why [g] can't be saved, or null when it can. */
fun groupProblem(g: Group, groups: List<Group>): String? {
    val name = g.name.trim()
    return when {
        name.isEmpty() -> "Give the group a name."
        groups.any { it.id != g.id && it.name.equals(name, ignoreCase = true) } -> "There is already a group called $name."
        else -> null
    }
}

class ObserveGroupsUseCase @Inject constructor(private val repo: AccessRepository) {
    operator fun invoke(): StateFlow<List<Group>> = repo.groups
}

class ObservePeopleUseCase @Inject constructor(private val repo: AccessRepository) {
    operator fun invoke(): StateFlow<List<Person>> = repo.people
}

/** Puts the signed-in user in as an admin the first time People and access opens. */
class SeedSignedInUserUseCase @Inject constructor(private val repo: AccessRepository) {
    operator fun invoke(currentEmail: String?) {
        if (repo.people.value.isNotEmpty() || currentEmail.isNullOrBlank()) return
        repo.setPeople(listOf(Person("u-me", currentEmail, ADMINS_ID, allProperties = true, lastSeen = "just now")))
    }
}

/** Adds ([Person.id] empty) or updates a person; returns why it can't instead, or null when saved. */
class SavePersonUseCase @Inject constructor(private val repo: AccessRepository) {
    operator fun invoke(p: Person): String? {
        val people = repo.people.value
        personProblem(p, people, repo.groups.value.firstOrNull { it.id == p.groupId })?.let { return it }
        val clean = p.copy(email = p.email.trim(), propertyIds = if (p.allProperties) emptySet() else p.propertyIds)
        repo.setPeople(
            if (clean.id.isEmpty()) people + clean.copy(id = "u${System.currentTimeMillis()}")
            else people.map { if (it.id == clean.id) clean else it },
        )
        return null
    }
}

class RemovePersonUseCase @Inject constructor(private val repo: AccessRepository) {
    operator fun invoke(p: Person) = repo.setPeople(repo.people.value.filterNot { it.id == p.id })
}

/**
 * Adds ([Group.id] empty) or updates a group; returns why it can't instead, or null when saved.
 * Locked groups (Admins) keep their name and every permission; people in a group that now needs
 * every property get every property.
 */
class SaveGroupUseCase @Inject constructor(private val repo: AccessRepository) {
    operator fun invoke(g: Group): String? {
        val groups = repo.groups.value
        groupProblem(g, groups)?.let { return it }
        val clean = g.copy(
            name = if (g.locked) groups.firstOrNull { it.id == g.id }?.name ?: g.name.trim() else g.name.trim(),
            description = g.description.trim(),
            permissions = if (g.locked) Permission.entries.toSet() else g.permissions,
        )
        repo.setGroups(
            if (clean.id.isEmpty()) groups + clean.copy(id = "g${System.currentTimeMillis()}")
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
    val seedSignedInUser: SeedSignedInUserUseCase,
    val savePerson: SavePersonUseCase,
    val removePerson: RemovePersonUseCase,
    val saveGroup: SaveGroupUseCase,
    val canDeleteGroup: CanDeleteGroupUseCase,
    val deleteGroup: DeleteGroupUseCase,
)
