package com.sclera.sclerago.domain.usecase

import com.sclera.sclerago.data.repository.AccessRepositoryImpl
import com.sclera.sclerago.domain.model.ADMINS_ID
import com.sclera.sclerago.domain.model.Group
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.Person
import com.sclera.sclerago.domain.usecase.access.CanDeleteGroupUseCase
import com.sclera.sclerago.domain.usecase.access.DeleteGroupUseCase
import com.sclera.sclerago.domain.usecase.access.ObserveGroupsUseCase
import com.sclera.sclerago.domain.usecase.access.ObservePeopleUseCase
import com.sclera.sclerago.domain.usecase.access.RemovePersonUseCase
import com.sclera.sclerago.domain.usecase.access.SaveGroupUseCase
import com.sclera.sclerago.domain.usecase.access.SavePersonUseCase
import com.sclera.sclerago.domain.usecase.access.SeedSignedInUserUseCase
import com.sclera.sclerago.domain.usecase.access.groupProblem
import com.sclera.sclerago.domain.usecase.access.personProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessUseCasesTest {
    private val repo = AccessRepositoryImpl()
    private val viewers = Group("g-v", "Viewers", "", emptySet())
    private val managers = Group("g-m", "Managers", "", setOf(Permission.MANAGE_ACCESS))
    private fun person(email: String = "a@b.com", group: String = "g-v", all: Boolean = true, props: Set<String> = emptySet(), id: String = "") =
        Person(id, email, group, all, props)

    @Test
    fun personProblem_coversEachRule() {
        val existing = listOf(person(id = "u1", email = "x@y.com"))
        assertEquals("Enter their email address.", personProblem(person(" "), existing, viewers))
        assertEquals("Enter a valid email address.", personProblem(person("nope"), existing, viewers))
        assertEquals("X@Y.com already has access.", personProblem(person("X@Y.com"), existing, viewers))
        assertEquals("Choose a group.", personProblem(person(), existing, null))
        assertEquals("Managers has permissions that need all properties.", personProblem(person(group = "g-m", all = false), existing, managers))
        assertEquals("Choose at least one property, or give them all properties.", personProblem(person(all = false), existing, viewers))
        assertNull(personProblem(person(all = false, props = setOf("p1")), existing, viewers))
        assertNull(personProblem(person(id = "u1", email = "x@y.com"), existing, viewers))
    }

    @Test
    fun groupProblem_needsAUniqueName() {
        val groups = listOf(viewers)
        assertEquals("Give the group a name.", groupProblem(viewers.copy(name = " "), groups))
        assertEquals("There is already a group called viewers.", groupProblem(Group("", " viewers ", "", emptySet()), groups))
        assertNull(groupProblem(viewers.copy(name = "Viewers"), groups))
    }

    @Test
    fun observe_exposesTheRepositoryFlows() {
        assertEquals(repo.groups, ObserveGroupsUseCase(repo)())
        assertEquals(repo.people, ObservePeopleUseCase(repo)())
    }

    @Test
    fun seedSignedInUser_onlyWhenEmptyAndSignedIn() {
        val seed = SeedSignedInUserUseCase(repo)
        seed(null)
        seed(" ")
        assertTrue(repo.people.value.isEmpty())
        seed("me@x.com")
        val me = repo.people.value.single()
        assertEquals(ADMINS_ID, me.groupId)
        assertTrue(me.allProperties)
        seed("other@x.com")
        assertEquals(1, repo.people.value.size)
    }

    @Test
    fun savePerson_addsUpdatesAndRejects() {
        repo.setGroups(listOf(viewers, managers))
        val save = SavePersonUseCase(repo)
        assertEquals("Enter their email address.", save(person("")))
        assertNull(save(person(" a@b.com ", all = false, props = setOf("p1"))))
        val added = repo.people.value.single()
        assertEquals("a@b.com", added.email)
        assertTrue(added.id.startsWith("u"))
        assertNull(save(added.copy(allProperties = true, propertyIds = setOf("p1"))))
        assertEquals(1, repo.people.value.size)
        assertTrue(repo.people.value.single().propertyIds.isEmpty())
    }

    @Test
    fun removePerson_dropsThemFromTheList() {
        repo.setPeople(listOf(person(id = "u1"), person("c@d.com", id = "u2")))
        RemovePersonUseCase(repo)(repo.people.value.first())
        assertEquals(listOf("u2"), repo.people.value.map { it.id })
    }

    @Test
    fun saveGroup_addsUpdatesAndGrantsAllPropertiesWhenNeeded() {
        repo.setGroups(listOf(viewers))
        repo.setPeople(listOf(person(id = "u1", all = false, props = setOf("p1"))))
        val save = SaveGroupUseCase(repo)
        assertEquals("Give the group a name.", save(Group("", " ", "", emptySet())))
        assertNull(save(Group("", " New ", " d ", setOf(Permission.COLLECT))))
        val added = repo.groups.value.last()
        assertEquals("New", added.name)
        assertEquals("d", added.description)
        assertTrue(added.id.startsWith("g"))
        assertNull(save(viewers.copy(permissions = setOf(Permission.MANAGE_PROPERTIES))))
        assertTrue(repo.people.value.single().allProperties)
        assertTrue(repo.people.value.single().propertyIds.isEmpty())
    }

    @Test
    fun saveGroup_lockedGroupsKeepTheirNameAndAllPermissions() {
        val admins = Group(ADMINS_ID, "Admins", "", setOf(Permission.COLLECT), locked = true)
        repo.setGroups(listOf(admins))
        assertNull(SaveGroupUseCase(repo)(admins.copy(name = "Renamed", permissions = emptySet())))
        val saved = repo.groups.value.single()
        assertEquals("Admins", saved.name)
        assertEquals(Permission.entries.toSet(), saved.permissions)
    }

    @Test
    fun deleteGroup_onlyWhenUnlockedAndEmpty() {
        val admins = Group(ADMINS_ID, "Admins", "", emptySet(), locked = true)
        repo.setGroups(listOf(admins, viewers, managers))
        repo.setPeople(listOf(person(group = "g-m", id = "u1")))
        val can = CanDeleteGroupUseCase(repo)
        assertFalse(can(admins))
        assertFalse(can(managers))
        assertTrue(can(viewers))
        val delete = DeleteGroupUseCase(repo, can)
        assertFalse(delete(managers))
        assertTrue(delete(viewers))
        assertEquals(listOf(ADMINS_ID, "g-m"), repo.groups.value.map { it.id })
    }

    @Test
    fun groupAndPersonHelpers() {
        assertEquals(Permission.MANAGE_ACCESS, managers.topPermission)
        assertNull(viewers.topPermission)
        assertNotNull(Group("a", "A", "", emptySet(), locked = true).topPermission)
        assertEquals("ada", person("ada@x.com").name)
        repo.setGroups(listOf(viewers))
        repo.clear()
        assertTrue(repo.people.value.isEmpty())
        assertTrue(repo.groups.value.isNotEmpty())
    }
}
