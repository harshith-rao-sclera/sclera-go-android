package com.sclera.sclerago

import com.sclera.sclerago.presentation.components.ToastHost
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sclera.sclerago.presentation.main.AssetIconsHost
import com.sclera.sclerago.domain.model.AuthState
import com.sclera.sclerago.presentation.launch.LaunchHost
import com.sclera.sclerago.presentation.launch.LaunchViewModel
import com.sclera.sclerago.presentation.login.LoginScreen
import com.sclera.sclerago.presentation.main.ScleraApp
import com.sclera.sclerago.presentation.premise.PremiseViewModel
import com.sclera.sclerago.presentation.session.SessionViewModel
import com.sclera.sclerago.presentation.theme.ScleraTheme
import com.sclera.sclerago.presentation.translation.AppLanguage
import com.sclera.sclerago.utility.Utility
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val session: SessionViewModel by viewModels()
    private val launch: LaunchViewModel by viewModels()
    private val premise: PremiseViewModel by viewModels()

    /** Whether the user was signed in when the auth state last changed (null: not known yet on this screen). */
    private var wasSignedIn: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // The system splash (dark canvas, brand symbol) hands over to the Compose launch animation,
        // which opens with the same canvas. The auth state is already known when the first frame is drawn.
        installSplashScreen()

        // Application context, not `this`: Utility.context is static, so holding the
        // Activity there wou   ld leak it across rotations / recreation.
        Utility.context = applicationContext

        // The app is dark-only, so keep light status/nav bar icons regardless of system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // Reopened after a sign-out: straight to the sign-in screen, without the launch animation.
        if (intent.getBooleanExtra(EXTRA_SIGNED_OUT, false)) launch.onIntroFinished()
        startFreshOnSignOut()
        setContent {
          AppLanguage {
            ScleraTheme {
                val playIntro = launch.playIntro.collectAsStateWithLifecycle().value
                LaunchHost(play = playIntro, onFinished = launch::onIntroFinished) {
                    // The app's toasts show over everything, at the top of the screen.
                    ToastHost {
                        // Any session-ending error (expired, blocked, signed in elsewhere) flips this to SignedOut.
                        when (val auth = session.authState.collectAsStateWithLifecycle().value) {
                            is AuthState.SignedOut -> LoginScreen(reason = auth.reason)
                            is AuthState.SignedIn -> AssetIconsHost { ScleraApp() }
                        }
                    }
                }
            }
          }
        }
    }

    override fun onStart() {
        super.onStart()
        session.onForeground()
        // The geofence is checked again: the phone may have moved on or off the property.
        premise.onForeground()
    }

    override fun onStop() {
        super.onStop()
        // Off screen: the access and property list stop being read every 30 seconds.
        session.onBackground()
    }

    /**
     * When the session ends (sign-out, expiry, blocked account), the app opens again from scratch, like a fresh install:
     * every screen's state (filters, chosen statuses, search, tabs, open sheets, AI results) goes with the old screen,
     * while [SessionViewModel] wipes everything stored about the user.
     */
    private fun startFreshOnSignOut() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                session.authState.collect { auth ->
                    val signedIn = auth is AuthState.SignedIn
                    if (wasSignedIn == true && !signedIn) restartFresh() else wasSignedIn = signedIn
                }
            }
        }
    }

    private fun restartFresh() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(EXTRA_SIGNED_OUT, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
        )
        finish()
    }

    private companion object {
        const val EXTRA_SIGNED_OUT = "signed_out"
    }
}
