package com.sclera.sclerago.presentation.login

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.util.Log
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.repository.SsoConfig
import com.sclera.sclerago.domain.model.AuthError
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.toUiText
import com.sclera.sclerago.presentation.components.CloseButton
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import kotlinx.coroutines.delay
import org.json.JSONObject

private const val TAG = "ScleraAuth"
private const val SSO_FIRST_LOAD_TIMEOUT_MS = 30_000L

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun SsoWebStep(
    sso: SsoConfig,
    email: String,
    onReturned: () -> Unit,
    onError: (UiText) -> Unit,
    onCancel: () -> Unit,
) {
    var progress by remember { mutableIntStateOf(0) }
    var host by remember { mutableStateOf(sso.host) }
    var firstPageLoaded by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    // Back walks the Keycloak pages, then returns to the email step.
    BackHandler {
        val wv = webView
        if (wv != null && wv.canGoBack()) wv.goBack() else onCancel()
    }

    // The sign-in page itself never loaded (slow or blocked network).
    LaunchedEffect(Unit) {
        delay(SSO_FIRST_LOAD_TIMEOUT_MS)
        if (!firstPageLoaded) onError(UiText.Res(R.string.sso_error_timeout))
    }

    Column(Modifier.fillMaxSize().testTag(LoginTags.WEB)) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CloseButton(onClick = onCancel, size = 38.dp, iconSize = 16.dp)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Txt(stringResource(R.string.login_title), 15, 600)
                Txt(host, 12, 400, C.Muted)
            }
        }
        if (progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = C.Yellow,
                trackColor = C.Border,
            )
        } else {
            Spacer(Modifier.height(2.dp))
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    // JavaScript and DOM storage are needed by the Keycloak page; nothing else is switched on.
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.safeBrowsingEnabled = true
                    // Show the Keycloak page as designed: the app's dark theme would otherwise make
                    // WebView auto-darken it (background photo blacked out, Log in button dimmed).
                    disableAutoDarkening(settings)
                    // Only pages on the Sclera domain are loaded (see SsoClient), so cookies stay first-party.
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                            progress = newProgress
                        }
                    }
                    webViewClient = SsoClient(
                        sso = sso,
                        onReturned = onReturned,
                        onError = onError,
                        onPage = { url ->
                            firstPageLoaded = true
                            Uri.parse(url).host?.let { host = it }
                        },
                    )
                    loadUrl(sso.loginUrl(email))
                    webView = this
                }
            },
            onRelease = { it.stopLoading(); it.destroy() },
        )
    }
}

@Suppress("DEPRECATION")
private fun disableAutoDarkening(settings: WebSettings) {
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> settings.isAlgorithmicDarkeningAllowed = false
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> settings.forceDark = WebSettings.FORCE_DARK_OFF
    }
}

/**
 * Layout fix for the Sclera Keycloak theme in a WebView, so it looks like it does in Chrome
 * (card centred on screen, background photo above and below):
 *  - The theme centres the card in `.login-pf-page { height: 100% }`, but in the WebView the
 *    percentage/`vh` heights resolve against a stale, smaller viewport (e.g. while the keyboard
 *    from the email step is still closing) — down to 0px — so the card overflows above the top of
 *    the page and the background photo has almost no area to paint. Size the page from the real
 *    `innerHeight` instead, and keep it in sync when the WebView resizes (keyboard open/close).
 *  - The page autofocuses the password box, popping the keyboard over the form; drop that so both
 *    fields are visible — the user taps the password box to type.
 * Better fixed in the theme itself; this is harmless once it is.
 */
private const val FIX_KEYCLOAK_LAYOUT_JS = """
(function () {
  var root = document.documentElement;
  if (!document.getElementById('sclera-go-fix')) {
    var s = document.createElement('style');
    s.id = 'sclera-go-fix';
    s.textContent = 'html,body{height:auto!important;min-height:0!important}' +
      '.login-pf-page{height:auto!important;min-height:var(--sclera-vh,100vh)!important;box-sizing:border-box}';
    (document.head || root).appendChild(s);
    window.addEventListener('resize', function () { root.style.setProperty('--sclera-vh', window.innerHeight + 'px'); });
  }
  root.style.setProperty('--sclera-vh', window.innerHeight + 'px');
  function unfocus() {
    var a = document.activeElement;
    if (a && a.tagName === 'INPUT' && a.type === 'password' && !a.value) a.blur();
    window.scrollTo(0, 0);
  }
  unfocus();
  setTimeout(unfocus, 350);
})();
"""

/**
 * Maps the JSON body that an accounts API page rendered in the WebView (e.g. getRedirectUrlByEmail
 * rejecting the email) to an error message, or null when it isn't an error.
 */
internal fun accountsRejection(json: JSONObject): UiText? {
    val status = json.optInt("status", -1)
    AuthError.fromStatus(status)?.let { return it.toUiText() }
    if (status == -1 || status in 200..299) return null
    return json.optString("message").takeIf { it.isNotBlank() }?.let(UiText::Raw)
        ?: UiText.Res(R.string.sso_error_status, listOf(status))
}

private class SsoClient(
    private val sso: SsoConfig,
    private val onReturned: () -> Unit,
    private val onError: (UiText) -> Unit,
    private val onPage: (String) -> Unit,
) : WebViewClient() {
    private var done = false

    private fun finish(block: () -> Unit) {
        if (done) return
        done = true
        block()
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        if (sso.isReturnUrl(url)) {
            Log.d(TAG, "SSO returned to app domain")
            finish(onReturned)
            return true
        }
        // Never leave the Sclera domain or drop to plain http inside the sign-in page.
        if (!sso.isTrustedUrl(url)) {
            Log.w(TAG, "Blocked sign-in navigation to ${request.url.host}")
            view.stopLoading()
            finish { onError(UiText.Res(R.string.sso_error_untrusted)) }
            return true
        }
        return false
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        // Some redirects bypass shouldOverrideUrlLoading; catch the return here too.
        if (sso.isReturnUrl(url)) {
            view.stopLoading()
            finish(onReturned)
        }
    }

    override fun onPageFinished(view: WebView, url: String?) {
        if (url == null || done) return
        onPage(url)
        if (Uri.parse(url).host.equals(sso.host, ignoreCase = true)) {
            view.evaluateJavascript(FIX_KEYCLOAK_LAYOUT_JS, null)
        }
        // getRedirectUrlByEmail answers with JSON instead of redirecting when it rejects the email.
        if (sso.isAccountsApiUrl(url)) {
            view.evaluateJavascript("document.body ? document.body.innerText : ''") { raw ->
                val text = runCatching { JSONObject("{\"v\":$raw}").getString("v") }.getOrNull().orEmpty()
                val json = runCatching { JSONObject(text) }.getOrNull() ?: return@evaluateJavascript
                val message = accountsRejection(json) ?: return@evaluateJavascript
                Log.w(TAG, "Accounts rejected login (status ${json.optInt("status", -1)})")
                finish { onError(message) }
            }
        }
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (!request.isForMainFrame || done) return
        // Losing the return redirect itself is fine — we intercept it before it loads.
        if (sso.isReturnUrl(request.url.toString())) return
        Log.w(TAG, "SSO page error ${error.errorCode}: ${error.description}")
        val message = when (error.errorCode) {
            ERROR_HOST_LOOKUP, ERROR_CONNECT, ERROR_TIMEOUT, ERROR_IO -> AuthError.Network().toUiText()
            else -> UiText.Res(R.string.sso_error_load, listOf(error.description.toString()))
        }
        finish { onError(message) }
    }

    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
        if (!request.isForMainFrame || done) return
        val url = request.url.toString()
        // Accounts API errors carry a JSON body with a status code; onPageFinished reads it.
        if (sso.isAccountsApiUrl(url) || sso.isReturnUrl(url)) return
        if (response.statusCode >= 500) {
            Log.w(TAG, "SSO page HTTP ${response.statusCode}")
            finish { onError(UiText.Res(R.string.sso_error_unavailable, listOf(response.statusCode))) }
        }
    }

    @SuppressLint("WebViewClientOnReceivedSslError")
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
        Log.w(TAG, "SSL error: primary=${error.primaryError}")
        finish { onError(UiText.Res(R.string.sso_error_ssl)) }
    }
}
