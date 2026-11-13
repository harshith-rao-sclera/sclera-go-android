package com.sclera.sclerago.presentation.common

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sclera.sclerago.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneOffset
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class UiTextTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(text: UiText) {
        compose.setContent { Text(text.asString()) }
    }

    @Test
    fun resource_isFormattedWithItsArguments() {
        show(UiText.Res(R.string.property_showing, listOf("Test 861")))
        compose.onNodeWithText("Showing Test 861").assertExists()
    }

    @Test
    fun plural_picksTheFormByCount() {
        show(UiText.Plural(R.plurals.assets_deleted, 1))
        compose.onNodeWithText("1 asset deleted").assertExists()
    }

    @Test
    fun plural_withExtraArguments_formatsAllOfThem() {
        show(UiText.Plural(R.plurals.assets_assigned, 3, listOf(3, "Plant room")))
        compose.onNodeWithText("3 assets assigned to Plant room").assertExists()
    }

    @Test
    fun anArgumentThatIsItselfText_isResolvedFirst() {
        show(UiText.Res(R.string.menu_coming_soon, listOf(UiText.Res(R.string.menu_audit))))
        compose.onNodeWithText("Audit is coming soon").assertExists()
    }

    @Test
    fun raw_isShownAsIs() {
        show(UiText.Raw("Server said no"))
        compose.onNodeWithText("Server said no").assertExists()
    }

    @Test
    fun dayAndTime_usesTheLocalesOrderAndTheGivenZone() {
        val at = 1_793_875_320_000L // 5 Nov 2026, 10:42 UTC
        val uk = formatDayAndTime(at, Locale.UK, ZoneOffset.UTC)
        assertTrue(uk, uk.startsWith("5 Nov") && "10:42" in uk)
        val us = formatDayAndTime(at, Locale.US, ZoneOffset.UTC)
        assertTrue(us, us.startsWith("Nov 5") && "10:42" in us)
        // The same instant is later in the day further east.
        assertTrue("12:42" in formatDayAndTime(at, Locale.UK, ZoneOffset.ofHours(2)))
    }
}
