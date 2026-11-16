package com.sclera.sclerago.presentation.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertAny
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sclera.sclerago.R
import com.sclera.sclerago.presentation.access.AccessListState
import com.sclera.sclerago.presentation.access.AccessTab
import com.sclera.sclerago.presentation.access.AccessTags
import com.sclera.sclerago.presentation.access.PeopleContent
import com.sclera.sclerago.presentation.assets.AssetListTags
import com.sclera.sclerago.presentation.main.DashboardSample
import com.sclera.sclerago.presentation.main.sampleAssetList
import com.sclera.sclerago.presentation.property.PropertyListContent
import com.sclera.sclerago.presentation.property.PropertyListState
import com.sclera.sclerago.presentation.property.PropertyTags
import com.sclera.sclerago.presentation.theme.ScleraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The loader on the Property list, the Users page and the asset lists: shown instead of the rows while their data is
 * first read, and replaced by the rows or the empty message afterwards. Run on phone, tablet portrait and tablet
 * landscape by the subclasses below.
 */
abstract class ListLoadingScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun show(content: @Composable () -> Unit) = compose.setContent { ScleraTheme { content() } }

    private fun propertyList(state: PropertyListState) = show {
        PropertyListContent(state, "", onBack = {}, onQuery = {}, onOpen = {}, onAdd = {}, onImport = {})
    }

    private fun people(list: AccessListState) = show {
        PeopleContent(list, emptyList(), onBack = {}, onTab = {}, onQuery = {}, onAdd = {}, onEditPerson = {}, onEditGroup = {})
    }

    // ---- Property list

    @Test
    fun propertyList_loading_showsTheLoader_notTheEmptyText() {
        propertyList(PropertyListState(loading = true))
        compose.onNodeWithTag(PropertyTags.LOADING).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.property_loading)).assertIsDisplayed()
        compose.onNodeWithTag(PropertyTags.EMPTY).assertDoesNotExist()
        // No count yet, rather than a 0 that jumps.
        compose.onNodeWithTag(PropertyTags.COUNT).onChildren().assertAny(hasText("–"))
    }

    @Test
    fun propertyList_noProperties_saysSo() {
        propertyList(PropertyListState())
        compose.onNodeWithTag(PropertyTags.LOADING).assertDoesNotExist()
        compose.onNodeWithTag(PropertyTags.EMPTY).assertTextContains(str(R.string.property_none))
    }

    @Test
    fun propertyList_noMatch_namesTheSearch() {
        propertyList(PropertyListState(query = "zzz", propertyTotal = 3))
        compose.onNodeWithTag(PropertyTags.EMPTY).assertTextContains(str(R.string.property_none_match, "zzz"))
    }

    // ---- Users page

    @Test
    fun people_loading_showsTheLoader() {
        people(AccessListState(loading = true))
        compose.onNodeWithTag(AccessTags.LOADING).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.access_loading_people)).assertIsDisplayed()
        compose.onNodeWithTag(AccessTags.EMPTY).assertDoesNotExist()
    }

    @Test
    fun groups_loading_showsTheGroupsLoader() {
        people(AccessListState(tab = AccessTab.GROUPS, loading = true))
        compose.onNodeWithText(str(R.string.access_loading_groups)).assertIsDisplayed()
        compose.onNodeWithTag(AccessTags.EMPTY).assertDoesNotExist()
    }

    @Test
    fun people_none_saysNoOneHasAccess() {
        people(AccessListState())
        compose.onNodeWithTag(AccessTags.LOADING).assertDoesNotExist()
        compose.onNodeWithTag(AccessTags.EMPTY).assertTextContains(str(R.string.access_empty_people))
    }

    @Test
    fun groups_none_saysNoGroupsYet() {
        people(AccessListState(tab = AccessTab.GROUPS))
        compose.onNodeWithTag(AccessTags.EMPTY).assertTextContains(str(R.string.access_empty_groups))
    }

    // ---- Asset lists

    @Test
    fun assets_loading_showsTheLoader_notTheEmptyCard() {
        show { DashboardSample(sampleAssetList(loading = true)) }
        compose.onNodeWithTag(AssetListTags.LOADING).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.assets_loading)).assertIsDisplayed()
        compose.onNodeWithTag(AssetListTags.EMPTY).assertDoesNotExist()
    }

    @Test
    fun assets_loaded_showsTheRows_andNoLoader() {
        val list = sampleAssetList()
        show { DashboardSample(list) }
        compose.onNodeWithTag(AssetListTags.LOADING).assertDoesNotExist()
        compose.onNodeWithTag(AssetListTags.row(list.data.items.first().id)).assertIsDisplayed()
    }

    @Test
    fun sharedLoader_readsAsOneLine() {
        show { ListLoader("Loading things…") }
        compose.onNodeWithText("Loading things…").assertIsDisplayed()
    }
}

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class ListLoadingScreensPhoneTest : ListLoadingScreensTest()

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w800dp-h1280dp")
class ListLoadingScreensTabletPortraitTest : ListLoadingScreensTest()

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class ListLoadingScreensTabletLandscapeTest : ListLoadingScreensTest()
