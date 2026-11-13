package com.sclera.sclerago.presentation.main

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.UserSession
import com.sclera.sclerago.presentation.theme.ScleraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val PHONE = "w411dp-h891dp"
private const val TABLET_PORTRAIT = "w800dp-h1280dp"
private const val TABLET_LANDSCAPE = "w1280dp-h800dp"

@RunWith(AndroidJUnit4::class)
class SideMenuTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)

    private val picked = mutableListOf<MenuItem>()
    private var closed = 0
    private var switched = 0
    private var signedOut = 0

    private fun show(
        active: MenuItem = MenuItem.ASSETS,
        session: UserSession? = SampleSession,
        validationCount: Int = 4,
        open: Boolean = true,
    ) {
        compose.setContent {
            ScleraTheme {
                SideMenuPanel(
                    open, session, "Test 861", assetCount = 25, validationCount = validationCount, active = active,
                    onClose = { closed++ }, onPick = { picked += it }, onSwitch = { switched++ }, onSignOut = { signedOut++ },
                )
            }
        }
    }

    private fun checkKeyStates() {
        show()
        compose.onNodeWithText("Master User").assertIsDisplayed()
        compose.onNodeWithText(SampleSession.email).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.menu_current_property).uppercase()).assertIsDisplayed()
        compose.onNodeWithText("Test 861").assertIsDisplayed()
        compose.onNodeWithText("25 assets").assertIsDisplayed()
        compose.onNodeWithTag(MenuTags.item(MenuItem.PROPERTY)).performClick()
        compose.onNodeWithTag(MenuTags.item(MenuItem.VALIDATION)).performClick()
        compose.onNodeWithTag(MenuTags.SWITCH).performClick()
        assertEquals(listOf(MenuItem.PROPERTY, MenuItem.VALIDATION), picked)
        assertEquals(1, switched)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun phone_showsProfileCurrentPropertyAndSections() = checkKeyStates()

    @Test
    @Config(qualifiers = TABLET_PORTRAIT)
    fun tabletPortrait_showsProfileCurrentPropertyAndSections() = checkKeyStates()

    @Test
    @Config(qualifiers = TABLET_LANDSCAPE)
    fun tabletLandscape_showsProfileCurrentPropertyAndSections() = checkKeyStates()

    @Test
    @Config(qualifiers = PHONE)
    fun theActiveRowIsMarkedSelected_andOnlyThatRow() {
        show(active = MenuItem.PROPERTY)
        compose.onNodeWithTag(MenuTags.item(MenuItem.PROPERTY)).assertIsSelected()
        compose.onNodeWithTag(MenuTags.item(MenuItem.USERS)).assertIsNotSelected()
        compose.onNodeWithTag(MenuTags.item(MenuItem.ASSETS)).assertIsNotSelected()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun comingSoonSectionsAreListed() {
        show()
        compose.onNodeWithTag(MenuTags.item(MenuItem.AUDIT)).assertIsDisplayed()
        compose.onNodeWithTag(MenuTags.item(MenuItem.INTEGRATION)).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun signOut_asksFirst_andCancelKeepsTheSession() {
        show()
        compose.onNodeWithTag(MenuTags.SIGN_OUT).performClick()
        compose.onNodeWithText(str(R.string.menu_sign_out_title)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.menu_sign_out_message, SampleSession.email)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.action_cancel)).performClick()
        compose.onNodeWithText(str(R.string.menu_sign_out_title)).assertDoesNotExist()
        assertEquals(0, signedOut)
    }

    @Test
    @Config(qualifiers = TABLET_LANDSCAPE)
    fun signOut_confirmClosesTheMenuAndSignsOut() {
        show()
        compose.onNodeWithTag(MenuTags.SIGN_OUT).performClick()
        compose.onNodeWithTag(MenuTags.SIGN_OUT_CONFIRM).performClick()
        assertEquals(1, signedOut)
        assertEquals(1, closed)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun signOutQuestion_withoutASession_doesNotNameAnEmail() {
        show(session = null)
        compose.onNodeWithTag(MenuTags.SIGN_OUT).performClick()
        compose.onNodeWithText(str(R.string.menu_sign_out_message_generic)).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun closedMenu_showsNothing() {
        show(open = false)
        compose.onNodeWithTag(MenuTags.SIGN_OUT).assertDoesNotExist()
    }

    @Test
    fun activeMenuItem_followsThePageThatIsShowing() {
        assertEquals(MenuItem.PROPERTY, activeMenuItem(AdminPage.PROPERTIES, validationOpen = true, configurationOpen = false))
        assertEquals(MenuItem.USERS, activeMenuItem(AdminPage.PEOPLE, false, false))
        assertEquals(MenuItem.VALIDATION, activeMenuItem(null, true, false))
        assertEquals(MenuItem.CONFIGURATION, activeMenuItem(null, false, true))
        assertEquals(MenuItem.ASSETS, activeMenuItem(null, false, false))
    }

    @Test
    fun initials_useTheFirstAndLastWordOfTheName_orAQuestionMark() {
        assertEquals("MU", initials("Master User"))
        assertEquals("AL", initials("ada.byron.lovelace@x.com"))
        assertEquals("A", initials("ada"))
        assertEquals("?", initials(""))
    }
}
