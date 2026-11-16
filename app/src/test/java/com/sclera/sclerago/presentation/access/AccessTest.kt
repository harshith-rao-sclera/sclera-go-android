package com.sclera.sclerago.presentation.access

import com.sclera.sclerago.data.PROPERTY_IMPORT_COLUMNS
import com.sclera.sclerago.data.writeSamplePropertyXlsx
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.domain.model.Group
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.Person
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
import org.junit.Before
import com.sclera.sclerago.domain.usecase.access.GroupProblem
import com.sclera.sclerago.domain.usecase.access.PersonProblem
import com.sclera.sclerago.domain.usecase.access.groupProblem
import com.sclera.sclerago.domain.usecase.access.personProblem
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sclera.sclerago.domain.usecase.property.propertyProblem
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** People and access, groups and property rules, over an in-memory stand-in for the server. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AccessTest {
    private val collectors = COLLECTORS
    private val admins = ADMINS

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** What Hilt builds in the app, over a fresh repository holding the default groups. */
    private fun accessViewModel(): AccessViewModel = AccessViewModel(
        accessUseCases(InMemoryAccessRepository(DEFAULT_GROUPS)),
        ObserveAuthStateUseCase(FakeAuthRepository()),
        FakeTimeProvider(),
    )

    // ---- personProblem

    @Test
    fun person_needsValidUniqueEmail() {
        val ok = Person("", "new@sclera.com", collectors.id, allProperties = true)
        assertNull(personProblem(ok, emptyList(), collectors))
        assertNotNull(personProblem(ok.copy(email = ""), emptyList(), collectors))
        assertNotNull(personProblem(ok.copy(email = "not-an-email"), emptyList(), collectors))
        val existing = listOf(Person("u1", "NEW@sclera.com", collectors.id, true))
        assertEquals(PersonProblem.EMAIL_TAKEN, personProblem(ok, existing, collectors))
        // Editing the same person keeps their own email.
        assertNull(personProblem(ok.copy(id = "u1"), existing, collectors))
    }

    @Test
    fun person_chosenPropertiesNeedAtLeastOne() {
        val p = Person("", "a@b.com", collectors.id, allProperties = false)
        assertNotNull(personProblem(p, emptyList(), collectors))
        assertNull(personProblem(p.copy(propertyIds = setOf("p1")), emptyList(), collectors))
    }

    @Test
    fun person_groupsThatManageNeedAllProperties() {
        val p = Person("", "a@b.com", admins.id, allProperties = false, propertyIds = setOf("p1"))
        assertEquals(PersonProblem.GROUP_NEEDS_ALL_PROPERTIES, personProblem(p, emptyList(), admins))
        assertNull(personProblem(p.copy(allProperties = true), emptyList(), admins))
    }

    // ---- groups

    @Test
    fun group_needsUniqueName() {
        assertNotNull(groupProblem(Group("", " ", "", emptySet()), DEFAULT_GROUPS))
        assertEquals(GroupProblem.NAME_TAKEN, groupProblem(Group("", "collectors", "", emptySet()), DEFAULT_GROUPS))
        assertNull(groupProblem(collectors.copy(description = "changed"), DEFAULT_GROUPS))
        assertNull(groupProblem(Group("", "Auditors", "", setOf(Permission.VALIDATE)), DEFAULT_GROUPS))
    }

    @Test
    fun admins_cantBeDeleted_andKeepEveryPermission() {
        val vm = accessViewModel()
        assertFalse(vm.canDelete(admins))
        assertNull(vm.saveGroup(admins.copy(permissions = setOf(Permission.COLLECT))))
        assertEquals(Permission.entries.toSet(), vm.group(admins.id)!!.permissions)
    }

    @Test
    fun groupsWithPeople_cantBeDeleted() {
        val vm = accessViewModel()
        assertTrue(vm.canDelete(collectors))
        assertNull(vm.savePerson(Person("", "c@sclera.com", collectors.id, allProperties = true)))
        assertFalse(vm.canDelete(collectors))
        vm.deleteGroup(collectors)
        assertNotNull(vm.group(collectors.id))
    }

    @Test
    fun deletingEmptyGroup_removesIt() {
        val vm = accessViewModel()
        vm.deleteGroup(collectors)
        assertNull(vm.group(collectors.id))
    }

    @Test
    fun givingGroupAManagePermission_givesItsPeopleAllProperties() {
        val vm = accessViewModel()
        vm.savePerson(Person("", "c@sclera.com", collectors.id, allProperties = false, propertyIds = setOf("p1")))
        vm.saveGroup(collectors.copy(permissions = collectors.permissions + Permission.MANAGE_ACCESS))
        val person = vm.people.single()
        assertTrue(person.allProperties)
        assertTrue(person.propertyIds.isEmpty())
    }

    @Test
    fun search_filtersPeopleAndGroups() {
        val vm = accessViewModel()
        vm.savePerson(Person("", "alpha@sclera.com", collectors.id, true))
        vm.savePerson(Person("", "beta@sclera.com", collectors.id, true))
        vm.setTab(AccessTab.PEOPLE)
        vm.setQuery("ALPHA")
        assertEquals(listOf("alpha@sclera.com"), vm.visiblePeople.map { it.email })
        vm.setTab(AccessTab.GROUPS)
        vm.setQuery("validate")
        assertEquals(listOf("Reviewers"), vm.visibleGroups.map { it.name })
    }

    // ---- properties

    @Test
    fun property_needsNameTypeAndUniqueName() {
        val existing = listOf(Property("p1", "Test 861", false), Property("p2", "Harbour Point", false))
        assertNotNull(propertyProblem(" ", "Office", existing))
        assertNotNull(propertyProblem("New tower", "", existing))
        assertNotNull(propertyProblem("test 861", "Office", existing))
        assertNull(propertyProblem("New tower", "Office", existing))
    }

    @Test
    fun sampleXlsx_isAZipWithTheImportColumns() {
        val bytes = ByteArrayOutputStream().also(::writeSamplePropertyXlsx).toByteArray()
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                entries[e.name] = zip.readBytes().decodeToString()
            }
        }
        assertTrue("[Content_Types].xml" in entries)
        assertTrue("xl/workbook.xml" in entries)
        val sheet = entries.getValue("xl/worksheets/sheet1.xml")
        PROPERTY_IMPORT_COLUMNS.forEach { assertTrue(it, it in sheet) }
    }
}
