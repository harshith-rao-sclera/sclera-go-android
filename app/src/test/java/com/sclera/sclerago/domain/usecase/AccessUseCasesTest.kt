package com.sclera.sclerago.domain.usecase

import com.sclera.sclerago.domain.model.AccessState
import com.sclera.sclerago.domain.model.CurrentAccess
import com.sclera.sclerago.domain.model.Group
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.Person
import com.sclera.sclerago.domain.repository.CurrentAccessRepository
import com.sclera.sclerago.domain.usecase.access.CanDeleteGroupUseCase
import com.sclera.sclerago.domain.usecase.access.CountPeopleGivenAllPropertiesUseCase
import com.sclera.sclerago.domain.usecase.access.DeleteGroupUseCase
import com.sclera.sclerago.domain.usecase.access.GroupProblem
import com.sclera.sclerago.domain.usecase.access.ObserveGroupsUseCase
import com.sclera.sclerago.domain.usecase.access.ObservePeopleUseCase
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import com.sclera.sclerago.domain.usecase.access.PersonProblem
import com.sclera.sclerago.domain.usecase.access.RefreshAccessUseCase
import com.sclera.sclerago.domain.usecase.access.RemovePersonUseCase
import com.sclera.sclerago.domain.usecase.access.SaveGroupUseCase
import com.sclera.sclerago.domain.usecase.access.SavePersonUseCase
import com.sclera.sclerago.domain.usecase.access.ValidateGroupUseCase
import com.sclera.sclerago.domain.usecase.access.ValidatePersonUseCase
import com.sclera.sclerago.domain.usecase.access.groupProblem
import com.sclera.sclerago.domain.usecase.access.personProblem
import com.sclera.sclerago.testutil.InMemoryAccessRepository
import com.sclera.sclerago.testutil.allowAllGuard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AccessUseCasesTest {
    private val repo = InMemoryAccessRepository()
    private val guard = allowAllGuard()
    private val viewers = Group("g-v", "Viewers", "", emptySet())
    private val managers = Group("g-m", "Managers", "", setOf(Permission.MANAGE_ACCESS))
    private val admins = Group("g-admins", "Admins", "", setOf(Permission.COLLECT), locked = true)
    private fun person(email: String = "a@b.com", group: String = "g-v", all: Boolean = true, props: Set<String> = emptySet(), id: String = "") =
        Person(id, email, group, all, props)

    /** A guard for a signed-in user whose group can do only [permissions]. */
    private fun guardFor(vararg permissions: Permission) = PermissionGuard(
        object : CurrentAccessRepository {
            override val state: StateFlow<AccessState> =
                MutableStateFlow(
                    AccessState.Loaded(
                        CurrentAccess(
                            userId = "u-me", email = "me@x.com", name = null, role = null, organisationId = "org-1",
                            groupId = "g-me", groupName = "Mine", permissions = permissions.toSet(),
                            allProperties = true, canManageProperties = false,
                        ),
                    ),
                )
        },
    )

    private suspend fun assertForbidden(block: suspend () -> Unit) {
        try {
            block()
            fail("expected Forbidden")
        } catch (e: OnlineException) {
            assertEquals(OnlineError.Forbidden, e.error)
        }
    }

    @Test
    fun personProblem_coversEachRule() {
        val existing = listOf(person(id = "u1", email = "x@y.com"))
        assertEquals(PersonProblem.EMAIL_REQUIRED, personProblem(person(" "), existing, viewers))
        assertEquals(PersonProblem.EMAIL_INVALID, personProblem(person("nope"), existing, viewers))
        assertEquals(PersonProblem.EMAIL_TAKEN, personProblem(person("X@Y.com"), existing, viewers))
        assertEquals(PersonProblem.GROUP_REQUIRED, personProblem(person(), existing, null))
        assertEquals(PersonProblem.GROUP_NEEDS_ALL_PROPERTIES, personProblem(person(group = "g-m", all = false), existing, managers))
        assertEquals(PersonProblem.NO_PROPERTY, personProblem(person(all = false), existing, viewers))
        assertNull(personProblem(person(all = false, props = setOf("p1")), existing, viewers))
        assertNull(personProblem(person(id = "u1", email = "x@y.com"), existing, viewers))
    }

    @Test
    fun groupProblem_needsAUniqueName() {
        val groups = listOf(viewers)
        assertEquals(GroupProblem.NAME_REQUIRED, groupProblem(viewers.copy(name = " "), groups))
        assertEquals(GroupProblem.NAME_TAKEN, groupProblem(Group("", " viewers ", "", emptySet()), groups))
        assertNull(groupProblem(viewers.copy(name = "Viewers"), groups))
    }

    @Test
    fun observe_exposesTheRepositoryFlows() {
        assertEquals(repo.groups, ObserveGroupsUseCase(repo)())
        assertEquals(repo.people, ObservePeopleUseCase(repo)())
    }

    @Test
    fun refresh_asksTheRepository() = runTest {
        RefreshAccessUseCase(repo)()
        assertEquals(1, repo.refreshes)
    }

    @Test
    fun savePerson_addsUpdatesAndRejects() = runTest {
        repo.setGroups(listOf(viewers, managers))
        val save = SavePersonUseCase(repo, guard)
        assertEquals(PersonProblem.EMAIL_REQUIRED, save(person("")))
        assertNull(save(person(" a@b.com ", all = false, props = setOf("p1"))))
        val added = repo.people.value.single()
        assertEquals("a@b.com", added.email)
        assertEquals("u-1", added.id)
        // Access to every property drops the chosen ones.
        assertNull(save(added.copy(allProperties = true, propertyIds = setOf("p1"))))
        assertEquals(1, repo.people.value.size)
        assertTrue(repo.people.value.single().propertyIds.isEmpty())
    }

    @Test
    fun savePerson_withoutManageAccess_isRefused() = runTest {
        repo.setGroups(listOf(viewers))
        assertForbidden { SavePersonUseCase(repo, guardFor(Permission.COLLECT))(person()) }
        assertTrue(repo.people.value.isEmpty())
    }

    @Test
    fun removePerson_dropsThemFromTheList() = runTest {
        repo.setPeople(listOf(person(id = "u1"), person("c@d.com", id = "u2")))
        RemovePersonUseCase(repo, guard)(repo.people.value.first())
        assertEquals(listOf("u2"), repo.people.value.map { it.id })
    }

    @Test
    fun removePerson_withoutManageAccess_isRefused() = runTest {
        repo.setPeople(listOf(person(id = "u1")))
        assertForbidden { RemovePersonUseCase(repo, guardFor())(repo.people.value.first()) }
        assertEquals(1, repo.people.value.size)
    }

    @Test
    fun saveGroup_addsUpdatesAndGrantsAllPropertiesWhenNeeded() = runTest {
        repo.setGroups(listOf(viewers))
        repo.setPeople(listOf(person(id = "u1", all = false, props = setOf("p1"))))
        val save = SaveGroupUseCase(repo, guard)
        assertEquals(GroupProblem.NAME_REQUIRED, save(Group("", " ", "", emptySet())))
        assertNull(save(Group("", " New ", " d ", setOf(Permission.COLLECT))))
        val added = repo.groups.value.last()
        assertEquals("New", added.name)
        assertEquals("d", added.description)
        assertEquals("g-1", added.id)
        assertNull(save(viewers.copy(permissions = setOf(Permission.MANAGE_PROPERTIES))))
        assertTrue(repo.people.value.single().allProperties)
        assertTrue(repo.people.value.single().propertyIds.isEmpty())
    }

    @Test
    fun saveGroup_lockedGroupsKeepTheirNameAndAllPermissions() = runTest {
        repo.setGroups(listOf(admins))
        assertNull(SaveGroupUseCase(repo, guard)(admins.copy(name = "Renamed", permissions = emptySet())))
        val saved = repo.groups.value.single()
        assertEquals("Admins", saved.name)
        assertEquals(Permission.entries.toSet(), saved.permissions)
    }

    @Test
    fun saveGroup_withoutManageAccess_isRefused() = runTest {
        assertForbidden { SaveGroupUseCase(repo, guardFor(Permission.COLLECT))(Group("", "New", "", emptySet())) }
        assertTrue(repo.groups.value.isEmpty())
    }

    @Test
    fun deleteGroup_onlyWhenUnlockedAndEmpty() = runTest {
        repo.setGroups(listOf(admins, viewers, managers))
        repo.setPeople(listOf(person(group = "g-m", id = "u1")))
        val can = CanDeleteGroupUseCase(repo)
        assertFalse(can(admins))
        assertFalse(can(managers))
        assertTrue(can(viewers))
        val delete = DeleteGroupUseCase(repo, can, guard)
        assertFalse(delete(managers))
        assertTrue(delete(viewers))
        assertEquals(listOf("g-admins", "g-m"), repo.groups.value.map { it.id })
    }

    @Test
    fun deleteGroup_withoutManageAccess_isRefused() = runTest {
        repo.setGroups(listOf(viewers))
        assertForbidden { DeleteGroupUseCase(repo, CanDeleteGroupUseCase(repo), guardFor())(viewers) }
        assertEquals(1, repo.groups.value.size)
    }

    @Test
    fun groupAndPersonHelpers() {
        assertEquals(Permission.MANAGE_ACCESS, managers.topPermission)
        assertNull(viewers.topPermission)
        assertNotNull(admins.topPermission)
        assertEquals("ada", person("ada@x.com").name)
    }

    @Test
    fun countPeopleGivenAllProperties_onlyForNeedAllGroupsAndChosenAccess() {
        repo.setPeople(
            listOf(
                person(id = "u1", group = "g-m", all = false, props = setOf("p1")),
                person("c@d.com", id = "u2", group = "g-m"),
                person("e@f.com", id = "u3", all = false, props = setOf("p1")),
            ),
        )
        val count = CountPeopleGivenAllPropertiesUseCase(repo)
        assertEquals(1, count(managers))
        assertEquals(0, count(managers.copy(permissions = emptySet())))
        assertEquals(0, count(managers.copy(id = "")))
    }

    @Test
    fun validateUseCases_checkAgainstTheRepository() {
        repo.setGroups(listOf(viewers))
        repo.setPeople(listOf(person(id = "u1", email = "x@y.com")))
        assertEquals(PersonProblem.EMAIL_TAKEN, ValidatePersonUseCase(repo)(person("x@y.com")))
        assertNull(ValidatePersonUseCase(repo)(person("new@y.com")))
        assertEquals(GroupProblem.NAME_TAKEN, ValidateGroupUseCase(repo)(Group("", "VIEWERS", "", emptySet())))
    }
}
