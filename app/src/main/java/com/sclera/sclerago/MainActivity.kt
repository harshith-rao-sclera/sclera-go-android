package com.sclera.sclerago

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sclera.sclerago.domain.model.AuthState
import com.sclera.sclerago.presentation.launch.LaunchHost
import com.sclera.sclerago.presentation.launch.LaunchViewModel
import com.sclera.sclerago.presentation.login.LoginScreen
import com.sclera.sclerago.presentation.main.ScleraApp
import com.sclera.sclerago.presentation.session.SessionViewModel
import com.sclera.sclerago.presentation.theme.ScleraTheme
import com.sclera.sclerago.utility.Utility
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val session: SessionViewModel by viewModels()
    private val launch: LaunchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // The system splash (dark canvas, brand symbol) hands over to the Compose launch animation,
        // which opens with the same canvas. The auth state is already known when the first frame is drawn.
        installSplashScreen()

        // Application context, not `this`: Utility.context is static, so holding the
        // Activity there would leak it across rotations / recreation.
        Utility.context = applicationContext

        // The app is dark-only, so keep light status/nav bar icons regardless of system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            ScleraTheme {
                val playIntro = launch.playIntro.collectAsStateWithLifecycle().value
                LaunchHost(play = playIntro, onFinished = launch::onIntroFinished) {
                    // Any session-ending error (expired, blocked, signed in elsewhere) flips this to SignedOut.
                    when (val auth = session.authState.collectAsStateWithLifecycle().value) {
                        is AuthState.SignedOut -> LoginScreen(reason = auth.reason)
                        is AuthState.SignedIn -> ScleraApp()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        session.onForeground()
    }
}
