package com.sclera.sclerago.presentation.access

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sclera.sclerago.domain.model.AuthState
import com.sclera.sclerago.domain.model.Group
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.Person
import com.sclera.sclerago.domain.usecase.access.GroupProblem
import com.sclera.sclerago.domain.usecase.access.PersonProblem
import com.sclera.sclerago.domain.usecase.auth.ObserveAuthStateUseCase
import com.sclera.sclerago.testutil.FakeAuthRepository
import com.sclera.sclerago.testutil.FakeTimeProvider
import com.sclera.sclerago.testutil.InMemoryAccessRepository
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

/** The groups an organisation starts with (as the server sends them; the same as the previews' sample groups). */
internal val ADMINS = Group("g-admins", "Admins", "Can do everything.", Permission.entries.toSet(), locked = true)
internal val COLLECTORS = Group("g-collectors", "Collectors", "Collect assets.", setOf(Permission.COLLECT))
internal val REVIEWERS = Group("g-reviewers", "Reviewers", "Collect assets and validate them.", setOf(Permission.COLLECT, Permission.VALIDATE))
internal val VIEWERS = Group("g-viewers", "Viewers", "Look, but not change anything.", emptySet())
internal val DEFAULT_GROUPS = listOf(ADMINS, COLLECTORS, REVIEWERS, VIEWERS)

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AccessViewModelTest {
    private val repo = InMemoryAccessRepository(DEFAULT_GROUPS)
    private val auth = FakeAuthRepository()
    private val time = FakeTimeProvider()
    private val me = Person("u-me", "me@x.com", ADMINS.id, allProperties = true)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = AccessViewModel(accessUseCases(repo), ObserveAuthStateUseCase(auth), time)

    @Test
    fun opensOnThePeopleTab_andReadsFromTheServer() {
        val vm = vm()
        assertEquals(AccessTab.PEOPLE, vm.state.tab)
        assertEquals(1, repo.refreshes)
    }

    @Test
    fun listState_marksMeAsSeenNowAndCountsMembers() {
        repo.setPeople(listOf(me))
        val vm = vm()
        vm.savePerson(Person("", "c@x.com", COLLECTORS.id, true))
        time.now += 5_000
        val list = vm.listState("ME@x.com")
        assertEquals(2, list.peopleTotal)
        assertEquals(DEFAULT_GROUPS.size, list.groupsTotal)
        assertTrue(list.people.single { it.person.email == "me@x.com" }.isMe)
        assertEquals(time.now, list.people.single { it.isMe }.lastSeenAt)
        assertNull(list.people.single { !it.isMe }.lastSeenAt)
        assertEquals(1, list.groups.single { it.group.id == COLLECTORS.id }.members)
        assertFalse(list.groups.single { it.group.id == COLLECTORS.id }.canDelete)
    }

    @Test
    fun search_matchesPeopleByGroupNameAndClearsOnTabChange() {
        repo.setPeople(listOf(me, Person("u2", "c@x.com", COLLECTORS.id, true)))
        val vm = vm()
        vm.setQuery("admins")
        assertEquals(listOf(me), vm.visiblePeople)
        vm.setTab(AccessTab.GROUPS)
        assertEquals("", vm.state.query)
    }

    @Test
    fun newPerson_startsInFirstUnlockedGroupWithChosenProperties() {
        val vm = vm()
        vm.newPerson()
        val form = vm.state.personForm!!
        assertEquals(COLLECTORS.id, form.groupId)
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
        assertEquals(listOf("a@b.com"), vm.people.map { it.email })
    }

    @Test
    fun savePerson_failing_keepsTheFormOpen_andSaysWhy() {
        val failing = object : com.sclera.sclerago.domain.repository.AccessRepository by repo {
            override suspend fun savePerson(person: Person): Person = throw OnlineException(OnlineError.SessionExpired)
        }
        val vm = AccessViewModel(accessUseCases(failing), ObserveAuthStateUseCase(auth), time)
        vm.newPerson()
        assertNull(vm.savePerson(vm.state.personForm!!.copy(email = "a@b.com", allProperties = true)))
        assertNotNull(vm.state.personForm)
        assertNotNull(vm.state.notice)
        vm.consumeNotice()
        assertNull(vm.state.notice)
    }

    @Test
    fun removePerson_confirmFlow() {
        repo.setPeople(listOf(me))
        val vm = vm()
        vm.editPerson(me)
        vm.askRemovePerson(me)
        assertEquals(me, vm.state.personToRemove)
        vm.removePerson(me)
        assertTrue(vm.people.isEmpty())
        assertNull(vm.state.personToRemove)
        assertNull(vm.state.personForm)
    }

    @Test
    fun saveGroup_needAllPermissionAsksBeforeChangingAnyonesAccess() {
        val vm = vm()
        vm.savePerson(Person("", "c@x.com", COLLECTORS.id, false, setOf("p1")))
        val draft = COLLECTORS.copy(permissions = COLLECTORS.permissions + Permission.MANAGE_ACCESS)
        assertEquals(1, vm.givenAllProperties(draft))
        vm.editGroup(COLLECTORS)
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
        vm.askDeleteGroup(VIEWERS)
        assertEquals(VIEWERS, vm.state.confirmDelete)
        vm.deleteGroup(VIEWERS)
        assertNull(vm.state.confirmDelete)
        assertNull(vm.group(VIEWERS.id))
    }

    @Test
    fun deleteGroup_fromItsSheet_closesTheSheet() {
        val vm = vm()
        vm.editGroup(VIEWERS)
        vm.askDeleteGroup(VIEWERS)
        vm.deleteGroup(VIEWERS)
        assertNull(vm.state.groupForm)
        assertNull(vm.state.confirmDelete)
    }

    @Test
    fun pickingGroups_onlyDeletableOnes_selectAllAndDeleteTogether() {
        val vm = vm()
        vm.setTab(AccessTab.GROUPS)
        val deletable = DEFAULT_GROUPS.filter { vm.canDelete(it) }
        assertEquals(listOf(COLLECTORS, REVIEWERS, VIEWERS), deletable)

        vm.toggleGroupPick(ADMINS) // locked: can't be ticked
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
        assertNotNull(vm.group(ADMINS.id))
    }

    @Test
    fun pickingGroups_isClearedByChangingTabOrClearing() {
        val vm = vm()
        vm.setTab(AccessTab.GROUPS)
        vm.toggleGroupPick(VIEWERS)
        vm.askDeletePicked()
        vm.clearGroupPick()
        assertTrue(vm.state.pickedGroups.isEmpty())
        assertFalse(vm.state.confirmDeletePicked)
        vm.toggleGroupPick(VIEWERS)
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
        assertEquals(DEFAULT_GROUPS.size, all.groups.size)
        // A locked group (Admins) can do everything, so it counts for every permission.
        Permission.entries.forEach { p -> assertEquals(DEFAULT_GROUPS.count { it.locked || p in it.permissions }, all.permissionCounts[p]) }

        vm.setGroupFilter(Permission.VALIDATE)
        val validators = vm.listState(null)
        assertEquals(Permission.VALIDATE, validators.groupFilter)
        assertEquals(setOf(ADMINS.id, REVIEWERS.id), validators.groups.map { it.group.id }.toSet())
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
