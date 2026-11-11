package com.example.sclerago.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sclerago.R
import com.example.sclerago.auth.AuthError
import com.example.sclerago.ui.theme.ScleraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities

/** Native email step and the hand-off to the WebView (the Keycloak page itself needs a device). */
@RunWith(AndroidJUnit4::class)
class LoginScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun show(reason: AuthError? = null) = compose.setContent { ScleraTheme { LoginScreen(reason) } }

    @Test
    fun startsOnNativeEmailStep() {
        show()
        compose.onNodeWithText(context.getString(R.string.login_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.login_subtitle)).assertIsDisplayed()
        compose.onNodeWithTag(LoginTags.EMAIL).assertIsDisplayed()
        compose.onNodeWithTag(LoginTags.WEB).assertDoesNotExist()
        compose.onNodeWithTag(LoginTags.ERROR).assertDoesNotExist()
    }

    @Test
    fun noSessionDetailsAreShown() {
        show()
        compose.onNodeWithText("Session ID", substring = true, ignoreCase = true).assertDoesNotExist()
        compose.onNodeWithText("Access token", substring = true, ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun showsWhyTheSessionEnded() {
        show(AuthError.SessionExpired())
        compose.onNodeWithText(context.getString(R.string.auth_error_session_expired)).assertIsDisplayed()
    }

    @Test
    fun continueWithBlankEmail_showsError() {
        show()
        compose.onNodeWithTag(LoginTags.CONTINUE).performClick()
        compose.onNodeWithText(context.getString(R.string.login_error_email_blank)).assertIsDisplayed()
        compose.onNodeWithTag(LoginTags.WEB).assertDoesNotExist()
    }

    @Test
    fun continueWithInvalidEmail_showsError() {
        show()
        compose.onNodeWithTag(LoginTags.EMAIL).performTextInput("not-an-email")
        compose.onNodeWithTag(LoginTags.CONTINUE).performClick()
        compose.onNodeWithText(context.getString(R.string.login_error_email_invalid)).assertIsDisplayed()
    }

    private fun setOnline() {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = ShadowNetworkCapabilities.newInstance()
        shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(cm).setNetworkCapabilities(cm.activeNetwork, caps)
    }

    @Test
    fun continueWhileOffline_showsNetworkError() {
        show()
        compose.onNodeWithTag(LoginTags.EMAIL).performTextInput("masteruser.brijesh@sclera.com")
        compose.onNodeWithTag(LoginTags.CONTINUE).performClick()
        compose.onNodeWithText(context.getString(R.string.auth_error_network)).assertIsDisplayed()
        compose.onNodeWithTag(LoginTags.WEB).assertDoesNotExist()
    }

    @Test
    fun continueWithValidEmail_opensWebView() {
        setOnline()
        show()
        compose.onNodeWithTag(LoginTags.EMAIL).performTextInput("masteruser.brijesh@sclera.com")
        compose.onNodeWithTag(LoginTags.CONTINUE).performClick()
        compose.onNodeWithTag(LoginTags.WEB).assertExists()
        compose.onNodeWithTag(LoginTags.EMAIL).assertDoesNotExist()
    }

    @Test
    fun typingClearsError() {
        show(AuthError.UserBlocked())
        compose.onNodeWithTag(LoginTags.ERROR).assertIsDisplayed()
        compose.onNodeWithTag(LoginTags.EMAIL).performTextInput("a")
        compose.onNodeWithTag(LoginTags.ERROR).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "fr")
    fun rendersInDeviceLanguage() {
        show(AuthError.SessionExpired())
        compose.onNodeWithText("Se connecter").assertIsDisplayed()
        compose.onNodeWithText("Continuer").assertIsDisplayed()
        compose.onNodeWithText("Votre session a expiré. Veuillez vous reconnecter.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "ar")
    fun rendersRightToLeftLanguage() {
        show()
        compose.onNodeWithText("تسجيل الدخول").assertIsDisplayed()
    }
}
