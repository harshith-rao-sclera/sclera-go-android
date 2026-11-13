package com.sclera.sclerago.presentation.access

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sclera.sclerago.data.repository.AccessRepositoryImpl
import com.sclera.sclerago.domain.model.AuthState
import com.sclera.sclerago.domain.model.Group
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.Person
import com.sclera.sclerago.domain.usecase.access.GroupProblem
import com.sclera.sclerago.domain.usecase.access.PersonProblem
import com.sclera.sclerago.domain.usecase.auth.ObserveAuthStateUseCase
import com.sclera.sclerago.testutil.FakeAuthRepository
import com.sclera.sclerago.testutil.FakeTimeProvider
import com.sclera.sclerago.testutil.accessUseCases
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AccessViewModelTest {
    private val repo = AccessRepositoryImpl()
    private val auth = FakeAuthRepository()
    private val time = FakeTimeProvider()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = AccessViewModel(accessUseCases(repo, time = time), ObserveAuthStateUseCase(auth), time)
    private val collectors get() = repo.groups.value.first { it.id == "g-collectors" }

    @Test
    fun opensOnThePeopleTab() = assertEquals(AccessTab.PEOPLE, vm().state.tab)

    @Test
    fun listState_marksMeAsSeenNowAndCountsMembers() {
        val vm = vm()
        vm.ensureSeeded("me@x.com")
        vm.savePerson(Person("", "c@x.com", collectors.id, true))
        time.now += 5_000
        val list = vm.listState("ME@x.com")
        assertEquals(2, list.peopleTotal)
        assertEquals(repo.groups.value.size, list.groupsTotal)
        assertTrue(list.people.single { it.person.email == "me@x.com" }.isMe)
        assertEquals(time.now, list.people.single { it.isMe }.lastSeenAt)
        assertNull(list.people.single { !it.isMe }.lastSeenAt)
        assertEquals(1, list.groups.single { it.group.id == collectors.id }.members)
        assertFalse(list.groups.single { it.group.id == collectors.id }.canDelete)
    }

    @Test
    fun search_matchesPeopleByGroupNameAndClearsOnTabChange() {
        val vm = vm()
        vm.ensureSeeded("me@x.com")
        vm.setQuery("admins")
        assertEquals(1, vm.visiblePeople.size)
        vm.setTab(AccessTab.GROUPS)
        assertEquals("", vm.state.query)
    }

    @Test
    fun newPerson_startsInFirstUnlockedGroupWithChosenProperties() {
        val vm = vm()
        vm.newPerson()
        val form = vm.state.personForm!!
        assertEquals(collectors.id, form.groupId)
        assertFalse(form.allProperties)
        assertEquals(PersonProblem.EMAIL_REQUIRED, vm.personProblem(form))
        vm.closePersonForm()
        assertNull(vm.state.personForm)
    }

    @Test
    fun savePerson_closesFormOnSuccessAndReportsProblems() {
        val vm = vm()
        vm.newPerson()
        assertEquals(PersonProblem.EMAIL_INVALID, vm.savePerson(vm.state.personForm!!.copy(email = "nope")))
        assertNotNull(vm.state.personForm)
        assertNull(vm.savePerson(vm.state.personForm!!.copy(email = "a@b.com", allProperties = true)))
        assertNull(vm.state.personForm)
        assertEquals(1, vm.people.size)
    }

    @Test
    fun removePerson_confirmFlow() {
        val vm = vm()
        vm.ensureSeeded("me@x.com")
        val p = vm.people.single()
        vm.editPerson(p)
        vm.askRemovePerson(p)
        assertEquals(p, vm.state.personToRemove)
        vm.removePerson(p)
        assertTrue(vm.people.isEmpty())
        assertNull(vm.state.personToRemove)
        assertNull(vm.state.personForm)
    }

    @Test
    fun saveGroup_needAllPermissionAsksBeforeChangingAnyonesAccess() {
        val vm = vm()
        vm.savePerson(Person("", "c@x.com", collectors.id, false, setOf("p1")))
        val draft = collectors.copy(permissions = collectors.permissions + Permission.MANAGE_ACCESS)
        assertEquals(1, vm.givenAllProperties(draft))
        vm.editGroup(collectors)
        assertNull(vm.saveGroup(draft))
        // Nothing changed yet: waiting for the confirmation.
        assertEquals(draft, vm.state.groupToConfirm)
        assertFalse(vm.people.single().allProperties)
        vm.cancelGroupConfirm()
        assertNull(vm.state.groupToConfirm)
        assertNotNull(vm.state.groupForm)
        vm.saveGroup(draft)
        vm.confirmGroupSave()
        assertTrue(vm.people.single().allProperties)
        assertNull(vm.state.groupForm)
        assertNull(vm.state.groupToConfirm)
    }

    @Test
    fun saveGroup_withoutSideEffectSavesAtOnceAndReportsProblems() {
        val vm = vm()
        vm.newGroup()
        assertEquals(GroupProblem.NAME_REQUIRED, vm.saveGroup(Group("", " ", "", emptySet())))
        assertNull(vm.saveGroup(Group("", "Auditors", "", setOf(Permission.VALIDATE))))
        assertNull(vm.state.groupForm)
        assertTrue(vm.groups.any { it.name == "Auditors" })
        vm.confirmGroupSave() // nothing pending: no effect
    }

    @Test
    fun deleteGroup_confirmFlow() {
        val vm = vm()
        vm.askDeleteGroup(collectors)
        assertEquals(collectors, vm.state.confirmDelete)
        vm.deleteGroup(collectors)
        assertNull(vm.state.confirmDelete)
        assertNull(vm.group(collectors.id))
        vm.closeGroupForm()
    }

    @Test
    fun deleteGroup_fromItsSheet_closesTheSheet() {
        val vm = vm()
        vm.editGroup(collectors)
        vm.askDeleteGroup(collectors)
        vm.deleteGroup(collectors)
        assertNull(vm.state.groupForm)
        assertNull(vm.state.confirmDelete)
    }

    @Test
    fun pickingGroups_onlyDeletableOnes_selectAllAndDeleteTogether() {
        val vm = vm()
        vm.setTab(AccessTab.GROUPS)
        val admins = repo.groups.value.first { it.locked }
        val deletable = repo.groups.value.filter { vm.canDelete(it) }
        assertTrue(deletable.size >= 2)

        vm.toggleGroupPick(admins) // locked: can't be ticked
        assertTrue(vm.state.pickedGroups.isEmpty())
        vm.toggleGroupPick(deletable[0])
        vm.toggleGroupPick(deletable[1])
        assertEquals(setOf(deletable[0].id, deletable[1].id), vm.listState(null).pickedGroups)
        assertEquals(deletable.size, vm.listState(null).selectableGroups)
        vm.toggleGroupPick(deletable[1])
        assertEquals(setOf(deletable[0].id), vm.state.pickedGroups)

        vm.selectAllGroups()
        assertEquals(deletable.map { it.id }.toSet(), vm.state.pickedGroups)
        vm.selectAllGroups() // everything ticked: select all clears
        assertTrue(vm.state.pickedGroups.isEmpty())

        vm.toggleGroupPick(deletable[0])
        vm.askDeletePicked()
        assertTrue(vm.state.confirmDeletePicked)
        vm.cancelDeletePicked()
        assertFalse(vm.state.confirmDeletePicked)
        vm.askDeletePicked()
        vm.deletePicked()
        assertNull(vm.group(deletable[0].id))
        assertTrue(vm.state.pickedGroups.isEmpty())
        assertFalse(vm.state.confirmDeletePicked)
        assertNotNull(vm.group(admins.id))
    }

    @Test
    fun pickingGroups_isClearedByChangingTabOrClearing() {
        val vm = vm()
        vm.setTab(AccessTab.GROUPS)
        val g = repo.groups.value.first { vm.canDelete(it) }
        vm.toggleGroupPick(g)
        vm.askDeletePicked()
        vm.clearGroupPick()
        assertTrue(vm.state.pickedGroups.isEmpty())
        assertFalse(vm.state.confirmDeletePicked)
        vm.toggleGroupPick(g)
        vm.setTab(AccessTab.PEOPLE)
        assertTrue(vm.state.pickedGroups.isEmpty())
        vm.askDeletePicked() // nothing ticked: nothing to confirm
        assertFalse(vm.state.confirmDeletePicked)
    }

    @Test
    fun groupFilter_showsOnlyGroupsThatCanDoIt_andCountsEachPermission() {
        val vm = vm()
        vm.setTab(AccessTab.GROUPS)
        val all = vm.listState(null)
        assertNull(all.groupFilter)
        assertEquals(repo.groups.value.size, all.groups.size)
        // A locked group (Admins) can do everything, so it counts for every permission.
        Permission.entries.forEach { p -> assertEquals(repo.groups.value.count { it.locked || p in it.permissions }, all.permissionCounts[p]) }

        vm.setGroupFilter(Permission.VALIDATE)
        val validators = vm.listState(null)
        assertEquals(Permission.VALIDATE, validators.groupFilter)
        assertTrue(validators.groups.isNotEmpty())
        assertTrue(validators.groups.all { it.group.can(Permission.VALIDATE) })
        assertEquals(all.permissionCounts, validators.permissionCounts) // counts don't depend on the filter

        // Search and filter combine; changing tab clears the filter.
        vm.setQuery("zz-no-such-group")
        assertTrue(vm.listState(null).groups.isEmpty())
        vm.setTab(AccessTab.PEOPLE)
        assertNull(vm.state.groupFilter)
    }

    @Test
    fun signOut_closesFormsAndClearsSearch() {
        val vm = vm()
        vm.setQuery("secret")
        vm.newPerson()
        auth.set(AuthState.SignedOut())
        assertEquals(AccessState(), vm.state)
    }
}
