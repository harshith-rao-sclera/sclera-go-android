package com.example.sclerago

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sclerago.auth.AuthManager
import com.example.sclerago.auth.AuthState
import com.example.sclerago.ui.LoginScreen
import com.example.sclerago.ui.ScleraApp
import com.example.sclerago.ui.theme.ScleraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is dark-only, so keep light status/nav bar icons regardless of system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        AuthManager.init(this)
        setContent {
            ScleraTheme {
                // Any session-ending error (expired, blocked, signed in elsewhere) flips this to SignedOut.
                when (val auth = AuthManager.state.collectAsStateWithLifecycle().value) {
                    is AuthState.SignedOut -> LoginScreen(reason = auth.reason)
                    is AuthState.SignedIn -> ScleraApp()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AuthManager.onForeground()
    }
}
