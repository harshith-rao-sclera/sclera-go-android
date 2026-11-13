package com.sclera.sclerago.presentation.access

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sclera.sclerago.R
import com.sclera.sclerago.data.local.DEFAULT_GROUPS
import com.sclera.sclerago.domain.model.ADMINS_ID
import com.sclera.sclerago.domain.model.Group
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.Person
import com.sclera.sclerago.domain.usecase.access.GroupProblem
import com.sclera.sclerago.domain.usecase.access.PersonProblem
import com.sclera.sclerago.domain.usecase.access.groupProblem
import com.sclera.sclerago.domain.usecase.access.personProblem
import com.sclera.sclerago.presentation.theme.ScleraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val PHONE = "w411dp-h891dp"
private const val TABLET_PORTRAIT = "w800dp-h1280dp"
private const val TABLET_LANDSCAPE = "w1280dp-h800dp"

/** People list, group list, person sheets and group sheets on phone and both tablet orientations. */
@RunWith(AndroidJUnit4::class)
class PeopleScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)

    private val collectors = DEFAULT_GROUPS.first { it.id == "g-collectors" }
    private val admins = DEFAULT_GROUPS.first { it.id == ADMINS_ID }

    // ---- the page

    private fun checkPeopleAndGroupsTabs() {
        val tab = mutableStateOf(AccessTab.PEOPLE)
        val edited = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        var added = 0
        compose.setContent {
            ScleraTheme {
                val list = if (tab.value == AccessTab.PEOPLE) SamplePeopleList else SampleGroupsList
                PeopleContent(
                    list, SampleProperties, "Test 861", {}, { tab.value = it }, {}, { added++ },
                    { edited += it.id }, { edited += it.id }, { deleted += it.id },
                )
            }
        }
        compose.onNodeWithText(str(R.string.access_heading)).assertIsDisplayed()
        compose.onNodeWithTag(AccessTags.TAB_PEOPLE).assertIsSelected().assertTextContains(str(R.string.access_tab_people, 2))
        compose.onNodeWithTag(AccessTags.person("u1")).assertIsDisplayed()
        compose.onNodeWithContentDescription(str(R.string.access_last_seen_description, str(R.string.access_just_now)), substring = true).assertExists()
        compose.onNodeWithTag(AccessTags.person("u2")).performClick()
        compose.onNodeWithTag(AccessTags.FAB_ADD).performClick()
        assertEquals(listOf("u2"), edited)
        assertEquals(1, added)
        compose.onNodeWithContentDescription(str(R.string.access_add_person)).assertExists()

        // The Groups tile switches the tab, like the tab itself.
        compose.onNodeWithTag(AccessTags.TILE_GROUPS).performClick()
        compose.onNodeWithTag(AccessTags.TAB_GROUPS).assertIsSelected()
        compose.onNodeWithContentDescription(str(R.string.access_new_group)).assertExists()
        compose.onNodeWithTag(AccessTags.group(ADMINS_ID)).assertIsDisplayed()
        compose.onNodeWithContentDescription(str(R.string.access_edit_group, "Collectors")).performClick()
        compose.onNodeWithContentDescription(str(R.string.access_delete_group, "Collectors")).performClick()
        compose.onNodeWithContentDescription(str(R.string.access_delete_locked, "Admins")).assertIsNotEnabled()
        assertEquals(listOf("u2", "g-collectors"), edited)
        assertEquals(listOf("g-collectors"), deleted)
        compose.onNodeWithTag(AccessTags.TAB_PEOPLE).performClick()
        compose.onNodeWithTag(AccessTags.person("u1")).assertIsDisplayed()
    }

    @Test @Config(qualifiers = PHONE)
    fun phone_tabsTilesCardsAndButtons() = checkPeopleAndGroupsTabs()

    @Test @Config(qualifiers = TABLET_PORTRAIT)
    fun tabletPortrait_tabsTilesCardsAndButtons() = checkPeopleAndGroupsTabs()

    @Test @Config(qualifiers = TABLET_LANDSCAPE)
    fun tabletLandscape_tabsTilesCardsAndButtons() = checkPeopleAndGroupsTabs()

    private fun checkEmptyStates() {
        compose.setContent {
            ScleraTheme {
                PeopleContent(AccessListState(), emptyList(), "Test 861", {}, {}, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithTag(AccessTags.EMPTY).assertTextContains(str(R.string.access_empty_people))
    }

    @Test @Config(qualifiers = PHONE)
    fun phone_emptyPeople() = checkEmptyStates()

    @Test @Config(qualifiers = TABLET_PORTRAIT)
    fun tabletPortrait_emptyPeople() = checkEmptyStates()

    @Test @Config(qualifiers = TABLET_LANDSCAPE)
    fun tabletLandscape_emptyPeople() = checkEmptyStates()

    @Test @Config(qualifiers = PHONE)
    fun noMatchTextsNameTheSearch() {
        compose.setContent {
            ScleraTheme {
                PeopleContent(AccessListState(query = "zz"), emptyList(), "P", {}, {}, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithTag(AccessTags.EMPTY).assertTextContains(str(R.string.access_no_person_match, "zz"))
    }

    // ---- person sheet

    private fun showPerson(initial: Person, isMe: Boolean = false, saved: MutableList<Person> = mutableListOf(), removed: MutableList<Person> = mutableListOf()) {
        compose.setContent {
            ScleraTheme {
                PersonSheet(
                    initial, DEFAULT_GROUPS, SampleProperties, isMe,
                    problem = { personProblem(it, emptyList(), DEFAULT_GROUPS.firstOrNull { g -> g.id == it.groupId }) },
                    onSave = { saved += it; null }, onClose = {}, onRemove = { removed += it },
                )
            }
        }
    }

    private fun checkAddPerson() {
        val saved = mutableListOf<Person>()
        showPerson(Person("", "", "g-collectors", false), saved = saved)
        // Both the email and the property access are still missing.
        compose.onNodeWithTag(AccessTags.PERSON_PILL).assertTextContains(str(R.string.property_required_count, 2))
        compose.onNodeWithTag(AccessTags.EMAIL).performTextInput("new@sclera.com")
        compose.onNodeWithTag(AccessTags.PERSON_PILL).assertTextContains(str(R.string.property_required_count, 1))
        compose.onNodeWithText(str(R.string.property_required_count, 1), substring = true).assertExists()
        compose.onNodeWithText("Test 861").performScrollTo().performClick()
        compose.onNodeWithTag(AccessTags.PERSON_PILL).assertTextContains(str(R.string.property_ready))
        compose.onNodeWithTag(AccessTags.SHEET_PRIMARY).performClick()
        assertEquals(listOf("new@sclera.com"), saved.map { it.email })
        assertEquals(setOf("p1"), saved.single().propertyIds)
    }

    @Test @Config(qualifiers = PHONE)
    fun phone_addPersonCountsEmailAndAccess() = checkAddPerson()

    @Test @Config(qualifiers = TABLET_PORTRAIT)
    fun tabletPortrait_addPersonCountsEmailAndAccess() = checkAddPerson()

    @Test @Config(qualifiers = TABLET_LANDSCAPE)
    fun tabletLandscape_addPersonCountsEmailAndAccess() = checkAddPerson()

    private fun checkEditPerson() {
        val removed = mutableListOf<Person>()
        // Chosen access with nothing chosen: the pill must not say Ready.
        showPerson(Person("u2", "ada@sclera.com", "g-collectors", false), removed = removed)
        compose.onNodeWithTag(AccessTags.PERSON_PILL).assertTextContains(str(R.string.property_required_count, 1))
        compose.onNodeWithText("ada").assertIsDisplayed()
        compose.onNodeWithTag(AccessTags.REMOVE).performScrollTo().performClick()
        assertEquals(listOf("u2"), removed.map { it.id })
    }

    @Test @Config(qualifiers = PHONE)
    fun phone_editPersonPillReflectsAccess() = checkEditPerson()

    @Test @Config(qualifiers = TABLET_PORTRAIT)
    fun tabletPortrait_editPersonPillReflectsAccess() = checkEditPerson()

    @Test @Config(qualifiers = TABLET_LANDSCAPE)
    fun tabletLandscape_editPersonPillReflectsAccess() = checkEditPerson()

    @Test @Config(qualifiers = PHONE)
    fun editAdmin_isReadyWithForcedAllPropertiesText_andNoRemoveForMe() {
        showPerson(Person("u1", "dev.admin@sclera-adc.com", ADMINS_ID, true), isMe = true)
        compose.onNodeWithTag(AccessTags.PERSON_PILL).assertTextContains(str(R.string.property_ready))
        compose.onNodeWithText(str(R.string.access_forced_all, "Admins")).assertExists()
        compose.onNodeWithTag(AccessTags.REMOVE).assertDoesNotExist()
    }

    @Test @Config(qualifiers = PHONE)
    fun groupPicker_opensFromTheGroupRow() {
        showPerson(Person("", "", "g-collectors", false))
        compose.onNodeWithText("Collectors").performClick()
        compose.onNodeWithText(str(R.string.access_choose_group)).assertIsDisplayed()
        compose.onNodeWithText("Viewers").performClick()
        compose.onNodeWithText("Viewers").assertIsDisplayed()
    }

    // ---- group sheet

    private fun showGroup(initial: Group, givenAll: (Group) -> Int = { 0 }, saved: MutableList<Group> = mutableListOf()) {
        compose.setContent {
            ScleraTheme {
                GroupSheet(
                    initial, problem = { groupProblem(it, DEFAULT_GROUPS) }, givenAll = givenAll,
                    onSave = { saved += it; null }, onClose = {},
                )
            }
        }
    }

    private fun checkNewGroup() {
        val saved = mutableListOf<Group>()
        showGroup(Group("", "", "", emptySet()), saved = saved)
        compose.onNodeWithText(str(R.string.access_group_new_sub)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.property_required_count, 1)).assertIsDisplayed()
        compose.onNodeWithTag(AccessTags.GROUP_NAME).performTextInput("Auditors")
        compose.onNodeWithText(str(R.string.property_ready)).assertIsDisplayed()
        compose.onNodeWithTag(AccessTags.permission(Permission.VALIDATE)).performScrollTo().performClick()
        compose.onNodeWithTag(AccessTags.permission(Permission.VALIDATE)).assertIsSelected()
        compose.onNodeWithTag(AccessTags.SHEET_PRIMARY).performClick()
        assertEquals(listOf("Auditors"), saved.map { it.name })
        assertEquals(setOf(Permission.VALIDATE), saved.single().permissions)
    }

    @Test @Config(qualifiers = PHONE)
    fun phone_newGroup() = checkNewGroup()

    @Test @Config(qualifiers = TABLET_PORTRAIT)
    fun tabletPortrait_newGroup() = checkNewGroup()

    @Test @Config(qualifiers = TABLET_LANDSCAPE)
    fun tabletLandscape_newGroup() = checkNewGroup()

    private fun checkLockedGroup() {
        showGroup(admins)
        compose.onNodeWithText(str(R.string.access_group_locked_sub, "Admins")).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.access_locked_notice, "Admins")).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.access_locked)).assertExists()
        Permission.entries.forEach { compose.onNodeWithTag(AccessTags.permission(it)).performScrollTo().assertIsSelected() }
    }

    @Test @Config(qualifiers = PHONE)
    fun phone_lockedGroup() = checkLockedGroup()

    @Test @Config(qualifiers = TABLET_PORTRAIT)
    fun tabletPortrait_lockedGroup() = checkLockedGroup()

    @Test @Config(qualifiers = TABLET_LANDSCAPE)
    fun tabletLandscape_lockedGroup() = checkLockedGroup()

    @Test @Config(qualifiers = PHONE)
    fun tickingNeedAllPermission_showsWhoGetsAllProperties() {
        showGroup(collectors, givenAll = { g -> if (Permission.MANAGE_ACCESS in g.permissions) 2 else 0 })
        compose.onNodeWithTag(AccessTags.ELEVATE_NOTICE).assertDoesNotExist()
        compose.onNodeWithTag(AccessTags.permission(Permission.MANAGE_ACCESS)).performScrollTo().performClick()
        compose.onNodeWithTag(AccessTags.ELEVATE_NOTICE).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.access_elevate_notice, 2, 2)).assertExists()
    }

    @Test @Config(qualifiers = PHONE)
    fun problemsMapToMessages() {
        assertEquals(PersonProblem.entries.size, PersonProblem.entries.map { it.toUiText("a@b.com", "G") }.distinct().size)
        assertTrue(GroupProblem.entries.all { it.toUiText("n") != null })
    }
}
