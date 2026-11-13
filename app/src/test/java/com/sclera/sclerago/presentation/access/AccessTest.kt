package com.sclera.sclerago.presentation.access

import com.sclera.sclerago.data.PROPERTY_IMPORT_COLUMNS
import com.sclera.sclerago.data.local.DEFAULT_GROUPS
import com.sclera.sclerago.data.local.PROPERTIES
import com.sclera.sclerago.data.repository.AccessRepositoryImpl
import com.sclera.sclerago.data.writeSamplePropertyXlsx
import com.sclera.sclerago.domain.model.ADMINS_ID
import com.sclera.sclerago.domain.model.Group
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.Person
import com.sclera.sclerago.domain.usecase.access.AccessUseCases
import com.sclera.sclerago.domain.usecase.access.CanDeleteGroupUseCase
import com.sclera.sclerago.domain.usecase.access.DeleteGroupUseCase
import com.sclera.sclerago.domain.usecase.access.ObserveGroupsUseCase
import com.sclera.sclerago.domain.usecase.access.ObservePeopleUseCase
import com.sclera.sclerago.domain.usecase.access.RemovePersonUseCase
import com.sclera.sclerago.domain.usecase.access.SaveGroupUseCase
import com.sclera.sclerago.domain.usecase.access.SavePersonUseCase
import com.sclera.sclerago.domain.usecase.access.SeedSignedInUserUseCase
import com.sclera.sclerago.domain.usecase.auth.ObserveAuthStateUseCase
import com.sclera.sclerago.testutil.FakeAuthRepository
import com.sclera.sclerago.testutil.FakeTimeProvider
import com.sclera.sclerago.testutil.accessUseCases
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

/** People and access, groups and property rules (mock data). */
@RunWith(AndroidJUnit4::class)
class AccessTest {
    private val collectors = DEFAULT_GROUPS.first { it.name == "Collectors" }
    private val admins = DEFAULT_GROUPS.first { it.id == ADMINS_ID }

    /** What Hilt builds in the app, over a fresh in-memory repository. */
    private fun accessViewModel(): AccessViewModel {
        val repo = AccessRepositoryImpl()
        return AccessViewModel(
            accessUseCases(repo),
            ObserveAuthStateUseCase(FakeAuthRepository()),
            FakeTimeProvider(),
        )
    }

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
        val p = Person("", "a@b.com", ADMINS_ID, allProperties = false, propertyIds = setOf("p1"))
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
        assertEquals(Permission.entries.toSet(), vm.group(ADMINS_ID)!!.permissions)
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
    fun seeding_addsSignedInUserOnce() {
        val vm = accessViewModel()
        vm.ensureSeeded("dev.admin@sclera.com")
        vm.ensureSeeded("someone@else.com")
        assertEquals(listOf("dev.admin@sclera.com"), vm.people.map { it.email })
        assertEquals(ADMINS_ID, vm.people.single().groupId)
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
        assertNotNull(propertyProblem(" ", "Office", PROPERTIES))
        assertNotNull(propertyProblem("New tower", "", PROPERTIES))
        assertNotNull(propertyProblem("test 861", "Office", PROPERTIES))
        assertNull(propertyProblem("New tower", "Office", PROPERTIES))
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
