package com.sclera.sclerago.presentation.assets

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sclera.sclerago.R
import com.sclera.sclerago.presentation.theme.ScleraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val PHONE = "w411dp-h891dp"
private const val TABLET_PORTRAIT = "w800dp-h1280dp"
private const val TABLET_LANDSCAPE = "w1280dp-h800dp"

/** The search box of the System, Asset and Sub type dropdowns ([SelectRow] with `searchable`). */
@RunWith(AndroidJUnit4::class)
class SelectRowSearchTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val types = listOf("-", "Chiller", "Air Handling Unit", "Boiler", "Chilled Water Pump").map { it to it }

    @Test
    fun `filtering keeps options containing the text in any case and trims it`() {
        assertEquals(listOf("Chiller", "Chilled Water Pump"), filterOptions(types, "  CHILL ").map { it.first })
        assertEquals(types, filterOptions(types, " "))
        assertEquals(emptyList<Pair<String, String>>(), filterOptions(types, "xyz"))
    }

    private fun checkSearch() {
        val picked = mutableListOf<String>()
        compose.setContent { ScleraTheme { SelectRow("typ", "Asset type", "", types, edited = false, onChange = { picked += it }, searchable = true) } }
        compose.onNodeWithTag(DetailTags.field("typ")).performClick()
        compose.onNodeWithTag(DetailTags.search("typ")).assertIsDisplayed()
        compose.onNodeWithText("Boiler").assertIsDisplayed()
        compose.onNodeWithTag(DetailTags.search("typ")).performTextInput("pump")
        compose.onNodeWithText("Boiler").assertDoesNotExist()
        compose.onNodeWithText("Chilled Water Pump").assertIsDisplayed().performClick()
        assertEquals(listOf("Chilled Water Pump"), picked)
    }

    @Test @Config(qualifiers = PHONE)
    fun searchOnPhone() = checkSearch()

    @Test @Config(qualifiers = TABLET_PORTRAIT)
    fun searchOnTabletPortrait() = checkSearch()

    @Test @Config(qualifiers = TABLET_LANDSCAPE)
    fun searchOnTabletLandscape() = checkSearch()

    @Test @Config(qualifiers = PHONE)
    fun nothingMatchingSaysSo() {
        compose.setContent { ScleraTheme { SelectRow("sys", "System type", "", types, edited = false, onChange = {}, searchable = true) } }
        compose.onNodeWithTag(DetailTags.field("sys")).performClick()
        compose.onNodeWithTag(DetailTags.search("sys")).performTextInput("xyz")
        compose.onNodeWithText(context.getString(R.string.select_no_match)).assertIsDisplayed()
    }

    @Test @Config(qualifiers = PHONE)
    fun aPlainDropdownHasNoSearchBox() {
        compose.setContent { ScleraTheme { SelectRow("f", "Field", "", types, edited = false, onChange = {}) } }
        compose.onNodeWithTag(DetailTags.field("f")).performClick()
        compose.onNodeWithTag(DetailTags.search("f")).assertDoesNotExist()
    }
}
