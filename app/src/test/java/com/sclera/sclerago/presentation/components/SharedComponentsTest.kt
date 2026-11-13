package com.sclera.sclerago.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.ScleraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The widgets the list pages share: circular FAB, search capsule, import strip, stat tiles, heading. */
@RunWith(AndroidJUnit4::class)
class SharedComponentsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun phoneAndTabletChecks(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { ScleraTheme { content() } }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun circleFab_isLabelledAndClickable_onPhone() {
        var taps = 0
        phoneAndTabletChecks { CircleFab(Icons.Plus, "Add thing", { taps++ }) }
        compose.onNodeWithContentDescription("Add thing").assertIsDisplayed().performClick()
        assertEquals(1, taps)
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp")
    fun searchFab_opensIntoACapsule_reportsTypingAndClearsOnClose_onTabletPortrait() {
        var query by mutableStateOf("")
        phoneAndTabletChecks { SearchFab(query, { query = it }, "Search things", "Open search", "Close search") }
        compose.onNodeWithContentDescription("Open search").performClick()
        compose.onNodeWithText("Search things").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("abc")
        assertEquals("abc", query)
        compose.onNodeWithContentDescription("Close search").performClick()
        assertEquals("", query)
        compose.onNodeWithContentDescription("Open search").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun importStrip_showsLabelAndFormat_andClicks_onTabletLandscape() {
        var taps = 0
        phoneAndTabletChecks { ImportStrip("Import it", ".xlsx", { taps++ }) }
        compose.onNodeWithText("Import it").assertIsDisplayed().performClick()
        compose.onNodeWithText(".xlsx").assertIsDisplayed()
        assertEquals(1, taps)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun statRow_showsTwoTilesWithValueAndLabel() {
        phoneAndTabletChecks {
            Column {
                StatRow {
                    tile("7", "Things")
                    tile("25", "Parts")
                }
            }
        }
        compose.onNodeWithText("Things").assertIsDisplayed()
        compose.onNodeWithText("25").assertIsDisplayed()
        compose.onNodeWithText("Parts").assertIsDisplayed()
    }

    @androidx.compose.runtime.Composable
    private fun RowScope.tile(value: String, label: String) = StatCard(Icons.Box, C.Blue, value, label, Modifier.weight(1f))
}
