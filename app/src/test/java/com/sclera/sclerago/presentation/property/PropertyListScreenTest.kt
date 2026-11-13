package com.sclera.sclerago.presentation.property

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sclera.sclerago.R
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.theme.ScleraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val PHONE = "w411dp-h891dp"
private const val TABLET_PORTRAIT = "w800dp-h1280dp"
private const val TABLET_LANDSCAPE = "w1280dp-h800dp"

/** The Property list page, the Import sheet and the Add sheet on phone and tablet sizes. */
@RunWith(AndroidJUnit4::class)
class PropertyListScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)

    private val opened = mutableListOf<String>()
    private var added = 0
    private var imported = 0
    private val queries = mutableListOf<String>()

    private fun showList(state: PropertyListState = SamplePropertyState) {
        compose.setContent {
            ScleraTheme {
                PropertyListContent(
                    state, "p1", "Test 861", onBack = {}, onQuery = { queries += it },
                    onOpen = { opened += it }, onAdd = { added++ }, onImport = { imported++ },
                )
            }
        }
    }

    private fun checkKeyStates() {
        showList()
        compose.onAllNodesWithText(str(R.string.property_list_title)).assertCountEquals(2) // app bar + heading
        compose.onNodeWithTag(PropertyTags.COUNT).assertTextContains("4 properties", substring = true)
        compose.onNodeWithText(str(R.string.label_properties)).assertIsDisplayed()
        compose.onNodeWithText("28").assertIsDisplayed()
        compose.onNodeWithTag(PropertyTags.IMPORT).assertIsDisplayed().performClick()
        compose.onNodeWithTag(PropertyTags.ADD).assertIsDisplayed().performClick()
        assertEquals(1, imported)
        assertEquals(1, added)
        compose.onNodeWithTag(PropertyTags.row("p1")).assertIsSelected()
        compose.onNodeWithTag(PropertyTags.row("p4")).performClick()
        assertEquals(listOf("p4"), opened)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun phone_showsHeadingTilesStripRowsAndButtons() = checkKeyStates()

    @Test
    @Config(qualifiers = TABLET_PORTRAIT)
    fun tabletPortrait_showsHeadingTilesStripRowsAndButtons() = checkKeyStates()

    @Test
    @Config(qualifiers = TABLET_LANDSCAPE)
    fun tabletLandscape_showsHeadingTilesStripRowsAndButtons() = checkKeyStates()

    @Test
    @Config(qualifiers = PHONE)
    fun assetCountsAreReadAsPlurals() {
        showList()
        compose.onNodeWithTag(PropertyTags.LIST).performScrollToNode(SemanticsMatcher.expectValue(SemanticsProperties.TestTag, PropertyTags.row("p4")))
        compose.onNodeWithText("3 assets").assertIsDisplayed()
        compose.onNodeWithText("25 assets").assertExists()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun noMatch_showsTheQueryInTheEmptyMessage() {
        showList(PropertyListState(query = " zzz ", propertyTotal = 4, assetTotal = 28))
        compose.onNodeWithTag(PropertyTags.LIST).performScrollToNode(SemanticsMatcher.expectValue(SemanticsProperties.TestTag, PropertyTags.EMPTY))
        compose.onNodeWithTag(PropertyTags.EMPTY).assertTextContains(str(R.string.property_none_match, "zzz"))
    }

    @Test
    @Config(qualifiers = PHONE)
    fun searchButton_opensTheCapsule_typingReportsTheQuery_closeClearsIt() {
        showList()
        compose.onNodeWithContentDescription(str(R.string.action_search)).performClick()
        compose.onNodeWithText(str(R.string.property_list_search_hint)).assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("har")
        assertEquals("har", queries.last())
        compose.onNodeWithContentDescription(str(R.string.action_close_search)).performClick()
        assertEquals("", queries.last())
        compose.onNodeWithContentDescription(str(R.string.action_search)).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun iconOnlyButtonsHaveDescriptions() {
        showList()
        compose.onNode(hasContentDescription(str(R.string.property_add_fab))).assertIsDisplayed()
        compose.onNode(hasContentDescription(str(R.string.action_search))).assertIsDisplayed()
    }

    // ---- Import sheet

    private fun showImport(file: String? = null, size: Long = -1, error: UiText? = null, onProceed: () -> Unit = { imported++ }) {
        compose.setContent {
            ScleraTheme { ImportSheetContent(file, size, error, onClose = {}, onChoose = { added++ }, onSample = {}, onProceed = onProceed) }
        }
    }

    private fun checkImportKeyStates() {
        showImport()
        compose.onNodeWithText(str(R.string.import_title)).assertIsDisplayed()
        compose.onNodeWithTag(ImportTags.DROP_ZONE).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.import_choose_file)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.import_formats)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.import_max_size)).assertIsDisplayed()
        compose.onNodeWithContentDescription(str(R.string.import_sample_download)).assertIsDisplayed()
        compose.onNodeWithTag(ImportTags.DROP_ZONE).performClick()
        assertEquals(1, added)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun import_phone_showsDropZoneLimitsAndSample() = checkImportKeyStates()

    @Test
    @Config(qualifiers = TABLET_PORTRAIT)
    fun import_tabletPortrait_showsDropZoneLimitsAndSample() = checkImportKeyStates()

    @Test
    @Config(qualifiers = TABLET_LANDSCAPE)
    fun import_tabletLandscape_showsDropZoneLimitsAndSample() = checkImportKeyStates()

    @Test
    @Config(qualifiers = PHONE)
    fun import_withAFile_showsItsNameAndSize() {
        showImport("sites.xlsx", 2048)
        compose.onNodeWithText("sites.xlsx").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun import_error_isShown() {
        showImport(error = UiText.Res(R.string.import_error_type))
        compose.onNodeWithTag(ImportTags.ERROR).assertTextContains(str(R.string.import_error_type))
    }

    @Test
    @Config(qualifiers = PHONE)
    fun import_proceed_reportsTheTap() {
        showImport("sites.xlsx", 10)
        compose.onNodeWithTag(ImportTags.PROCEED).performClick()
        assertEquals(1, imported)
    }

    // ---- Add sheet

    private fun checkAddSheet() {
        val saved = mutableStateOf<String?>(null)
        compose.setContent {
            ScleraTheme {
                AddPropertySheet(
                    existing = emptyList(), onClose = {},
                    onAdd = { saved.value = it.name; null },
                )
            }
        }
        compose.onNodeWithText(str(R.string.property_add_title)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.property_group_details)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.property_required_count, 2)).assertIsDisplayed()
        // Property details and Notes fold like the other groups: a chevron is drawn and tapping the title hides the fields.
        compose.onNodeWithTag(AddPropertyTags.NAME).assertIsDisplayed().performTextInput("Depot")
        compose.onNodeWithText(str(R.string.property_required_count, 1)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.property_group_details)).performClick()
        compose.onNodeWithTag(AddPropertyTags.NAME).assertDoesNotExist()
        compose.onNodeWithText(str(R.string.property_group_details)).performClick()
        compose.onNodeWithTag(AddPropertyTags.NAME).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun add_phone_countsRequiredFields_andDetailsGroupFolds() = checkAddSheet()

    @Test
    @Config(qualifiers = TABLET_PORTRAIT)
    fun add_tabletPortrait_countsRequiredFields_andDetailsGroupFolds() = checkAddSheet()

    @Test
    @Config(qualifiers = TABLET_LANDSCAPE)
    fun add_tabletLandscape_countsRequiredFields_andDetailsGroupFolds() = checkAddSheet()
}
