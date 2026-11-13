package com.sclera.sclerago.presentation.main

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sclera.sclerago.R
import com.sclera.sclerago.data.local.SEED_ASSETS
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.presentation.assets.AssetListTags
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

/** The dashboard: header, asset list, selection dock, search and bottom tabs, on phone and both tablet orientations. */
@RunWith(AndroidJUnit4::class)
class DashboardScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)
    private val assets = SEED_ASSETS.filter { it.propertyId == "p1" }
    private val pumps = assets.filter { "pump" in "${it.name} ${it.detail}".lowercase() }

    // ---- the list

    private fun checkList() {
        val opened = mutableListOf<String>()
        compose.setContent { ScleraTheme { DashboardSample(sampleAssetList(), onOpen = { opened += it }) } }
        compose.onNodeWithTag(DashTags.location(LocFilter.ALL)).assertIsDisplayed().assertTextContains("Test 861")
        compose.onNodeWithTag(DashTags.location(LocFilter.NONE)).assertTextContains(str(R.string.dash_unmapped))
        compose.onNodeWithTag(DashTags.chevron(LocFilter.ALL)).assertIsDisplayed()
        compose.onNodeWithContentDescription(str(R.string.dash_menu)).assertIsDisplayed()
        // One card per row: the first rows are there, each with its own tag.
        assets.take(3).forEach { compose.onNodeWithTag(AssetListTags.row(it.id)).assertIsDisplayed() }
        compose.onNodeWithTag(AssetListTags.chip(null)).assertTextContains(assets.size.toString(), substring = true)
        compose.onNodeWithTag(AssetListTags.chip(Status.DRAFT)).assertIsDisplayed()
        // + and search are there while nothing is selected, and so are all five tabs.
        compose.onNodeWithTag(DashTags.ADD).assertIsDisplayed()
        compose.onNodeWithContentDescription(str(R.string.action_search)).assertIsDisplayed()
        Tab.entries.forEach { compose.onNodeWithTag(DashTags.nav(it)).assertExists() }
        compose.onNodeWithContentDescription(str(R.string.label_assets)).assertExists()
        compose.onNodeWithTag(DashTags.DOCK).assertDoesNotExist()
        // Tapping a row opens that asset.
        compose.onNodeWithTag(AssetListTags.row(assets.first().id)).performClick()
        assertEquals(listOf(assets.first().id), opened)
    }

    @Test @Config(qualifiers = PHONE)
    fun phone_list() = checkList()

    @Test @Config(qualifiers = TABLET_PORTRAIT)
    fun tabletPortrait_list() = checkList()

    @Test @Config(qualifiers = TABLET_LANDSCAPE)
    fun tabletLandscape_list() = checkList()

    private fun checkHeaderTaps() {
        val picked = mutableListOf<LocFilter>()
        val chevrons = mutableListOf<LocFilter>()
        var menu = 0
        compose.setContent {
            ScleraTheme { DashboardHeader("Test 861", null, LocFilter.SPEC, { menu++ }, { picked += it }, { chevrons += it }) }
        }
        // No location chosen yet: the middle tab says so.
        compose.onNodeWithText(str(R.string.dash_choose_location)).assertIsDisplayed()
        compose.onNodeWithContentDescription(str(R.string.dash_menu)).performClick()
        compose.onNodeWithTag(DashTags.location(LocFilter.ALL)).performClick()
        compose.onNodeWithTag(DashTags.location(LocFilter.NONE)).performClick()
        compose.onNodeWithTag(DashTags.chevron(LocFilter.SPEC)).performClick()
        // Only the chosen tab has a chevron.
        compose.onNodeWithTag(DashTags.chevron(LocFilter.ALL)).assertDoesNotExist()
        assertEquals(1, menu)
        assertEquals(listOf(LocFilter.ALL, LocFilter.NONE), picked)
        assertEquals(listOf(LocFilter.SPEC), chevrons)
    }

    @Test @Config(qualifiers = PHONE)
    fun phone_headerTaps() = checkHeaderTaps()

    @Test @Config(qualifiers = TABLET_PORTRAIT)
    fun tabletPortrait_headerTaps() = checkHeaderTaps()

    @Test @Config(qualifiers = TABLET_LANDSCAPE)
    fun tabletLandscape_headerTaps() = checkHeaderTaps()

    // ---- selecting

    private fun checkSelecting() {
        compose.setContent { ScleraTheme { DashboardSample(sampleAssetList(picked = setOf(assets[0].id, assets[1].id))) } }
        compose.onNodeWithTag(DashTags.DOCK).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.dash_selected, 2)).assertIsDisplayed()
        compose.onNodeWithTag(DashTags.SELECT_ALL).assertTextContains(str(R.string.dash_select_all, assets.size))
        compose.onNodeWithText(str(R.string.action_validate)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.action_assign)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.action_submit)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.action_delete)).assertIsDisplayed()
        compose.onNodeWithContentDescription(str(R.string.dash_clear_selection)).assertIsDisplayed()
        // The + and search buttons are gone, so they can not overlap the dock.
        compose.onNodeWithTag(DashTags.ADD).assertDoesNotExist()
        compose.onNodeWithContentDescription(str(R.string.action_search)).assertDoesNotExist()
    }

    @Test @Config(qualifiers = PHONE)
    fun phone_selecting() = checkSelecting()

    @Test @Config(qualifiers = TABLET_PORTRAIT)
    fun tabletPortrait_selecting() = checkSelecting()

    @Test @Config(qualifiers = TABLET_LANDSCAPE)
    fun tabletLandscape_selecting() = checkSelecting()

    private fun checkDockActions() {
        val calls = mutableListOf<String>()
        compose.setContent {
            ScleraTheme {
                SelectionDock(
                    3, 24, allPicked = false, onClear = { calls += "clear" }, onSelectAll = { calls += "all" },
                    onValidate = { calls += "validate" }, onAssign = { calls += "assign" }, onSubmit = { calls += "submit" },
                    onDelete = { calls += "delete" },
                )
            }
        }
        compose.onNodeWithContentDescription(str(R.string.dash_clear_selection)).performClick()
        compose.onNodeWithTag(DashTags.SELECT_ALL).assertTextContains(str(R.string.dash_select_all, 24)).performClick()
        listOf(R.string.action_validate, R.string.action_assign, R.string.action_submit, R.string.action_delete)
            .forEach { compose.onNodeWithText(str(it)).performClick() }
        assertEquals(listOf("clear", "all", "validate", "assign", "submit", "delete"), calls)
    }

    @Test @Config(qualifiers = PHONE)
    fun phone_dockActions() = checkDockActions()

    @Test @Config(qualifiers = TABLET_LANDSCAPE)
    fun tabletLandscape_dockActions() = checkDockActions()

    @Test @Config(qualifiers = PHONE)
    fun dockOffersClearAllWhenEverythingIsPicked() {
        compose.setContent {
            ScleraTheme { SelectionDock(24, 24, true, {}, {}, {}, {}, {}, {}) }
        }
        compose.onNodeWithTag(DashTags.SELECT_ALL).assertTextContains(str(R.string.dash_clear_all))
    }

    // ---- search

    private fun checkSearch() {
        val query = mutableStateOf("pump")
        val open = mutableStateOf(true)
        compose.setContent {
            ScleraTheme {
                DashboardSample(
                    sampleAssetList(query.value), searchOpen = open.value,
                    onSearchOpenChange = { open.value = it }, onQuery = { query.value = it },
                )
            }
        }
        // Only the matching assets show, and the All chip counts just those.
        assertTrue(pumps.isNotEmpty() && pumps.size < assets.size)
        pumps.forEach { compose.onNodeWithTag(AssetListTags.row(it.id)).assertIsDisplayed() }
        compose.onNodeWithTag(AssetListTags.row(assets.first { it !in pumps }.id)).assertDoesNotExist()
        compose.onNodeWithTag(AssetListTags.chip(null)).assertTextContains(pumps.size.toString(), substring = true)
        compose.onNodeWithContentDescription(str(R.string.action_close_search)).assertIsDisplayed()
        // The + button stays above the capsule.
        compose.onNodeWithTag(DashTags.ADD).assertIsDisplayed()
        // Typing something nothing matches says so, naming the search.
        compose.onNodeWithText("pump").performTextInput("zz")
        compose.onNodeWithText(str(R.string.dash_empty_search, query.value)).assertIsDisplayed()
        compose.onNodeWithTag(AssetListTags.EMPTY).assertIsDisplayed()
        // Closing the capsule clears the text and brings the full list back.
        compose.onNodeWithContentDescription(str(R.string.action_close_search)).performClick()
        assertEquals("", query.value)
        compose.onNodeWithContentDescription(str(R.string.action_search)).assertIsDisplayed()
        compose.onNodeWithTag(AssetListTags.row(assets.first().id)).assertIsDisplayed()
    }

    @Test @Config(qualifiers = PHONE)
    fun phone_search() = checkSearch()

    @Test @Config(qualifiers = TABLET_PORTRAIT)
    fun tabletPortrait_search() = checkSearch()

    @Test @Config(qualifiers = TABLET_LANDSCAPE)
    fun tabletLandscape_search() = checkSearch()
}
