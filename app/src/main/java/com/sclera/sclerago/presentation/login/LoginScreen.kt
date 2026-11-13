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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.repository.SsoConfig
import com.sclera.sclerago.domain.model.AuthError
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.common.toUiText
import com.sclera.sclerago.presentation.components.CloseButton
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.ScleraTheme
import com.sclera.sclerago.presentation.theme.Txt
import kotlinx.coroutines.delay
import org.json.JSONObject

private const val TAG = "ScleraAuth"
private const val SSO_FIRST_LOAD_TIMEOUT_MS = 30_000L

/** Test tags used by the login UI tests. */
internal object LoginTags {
    const val EMAIL = "login_email"
    const val CONTINUE = "login_continue"
    const val ERROR = "login_error"
    const val WEB = "login_web"
    const val TAGLINE = "login_tagline"
}

/**
 * Login flow: native email entry → `getRedirectUrlByEmail` in a WebView, which lands on the
 * Keycloak page (email prefilled via `login_hint`, user enters the password) → back to the
 * app domain → session id + access token → app. The flow's logic lives in [LoginViewModel].
 * [reason] is set when the user was sent here because their session ended (expired, blocked, …).
 */
@Composable
fun LoginScreen(reason: AuthError?, vm: LoginViewModel = hiltViewModel()) {
    var email by rememberSaveable { mutableStateOf("") }
    // Same lifetime as the screen's former `remember` state: reset whenever it enters composition.
    remember { vm.start(reason) }
    val ui by vm.state.collectAsStateWithLifecycle()
    val error = ui.error

    Box(Modifier.fillMaxSize().background(C.Bg)) {
        when (ui.stage) {
            LoginStage.Email -> EmailStep(
                email = email,
                error = error,
                onEmailChange = { email = it; vm.onEmailChanged() },
                onContinue = { vm.onContinue(email) },
            )
            LoginStage.Web -> Box(Modifier.systemBarsPadding().imePadding()) {
                SsoWebStep(
                    sso = vm.sso,
                    email = email.trim(),
                    onReturned = { vm.onReturned(email.trim()) },
                    onError = vm::fail,
                    onCancel = vm::onCancelWeb,
                )
            }
            LoginStage.Finishing -> Box(Modifier.fillMaxSize().systemBarsPadding(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = C.Yellow)
                    Spacer(Modifier.height(16.dp))
                    Txt(stringResource(R.string.login_signing_in), 15, 500, C.Muted)
                }
            }
        }
    }
}

private val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
private val FieldBorder = Color(0xFF3A3D44)
private val IconGrey = Color(0xFF8B8F97)

/** Red line under the email field (design: "Sign in" artboard). */
@Composable
private fun FieldError(message: String) {
    Row(
        Modifier.fillMaxWidth().testTag(LoginTags.ERROR),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LineIcon(Icons.Alert, 14.dp, C.Red, 2.2f)
        Txt(message, 13, 500, C.Red, Modifier.weight(1f), maxLines = 3)
    }
}

/** Shortest screen side from which the login uses the tablet layout (portrait and landscape). */
private val TabletMinSide = 600.dp

/**
 * Email step. Phones follow the "Sign in" artboard of the Sclera VDMS Mobile design: building photo
 * over the top two-thirds with the logo and tagline, and the sign-in sheet at the bottom. Tablets
 * (portrait and landscape) use option A of "Tablet Login Options": full-screen photo with the
 * sign-in form in a centered card.
 */
@Composable
private fun EmailStep(
    email: String,
    error: UiText?,
    onEmailChange: (String) -> Unit,
    onContinue: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(C.Bg)) {
        if (minOf(maxWidth, maxHeight) >= TabletMinSide) {
            TabletEmailStep(email, error, onEmailChange, onContinue)
        } else {
            PhoneEmailStep(email, error, onEmailChange, onContinue)
        }
    }
}

@Composable
private fun PhoneEmailStep(
    email: String,
    error: UiText?,
    onEmailChange: (String) -> Unit,
    onContinue: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(567f / 844f)) {
            Image(
                painterResource(R.drawable.login_bg), contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color(0x8C000000), 0.35f to Color(0x0D000000), 0.6f to Color(0x1A000000), 1f to Color(0xD9141518),
                    ),
                ),
            )
        }
        Brand(Modifier.statusBarsPadding().padding(start = 24.dp, top = 20.dp), logoHeight = 36.dp, textSize = 19, gap = 10.dp)

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding()) {
            Txt(
                stringResource(R.string.login_tagline), 30, 700, Color.White,
                Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp).testTag(LoginTags.TAGLINE),
                maxLines = 3, lineHeight = 36.sp,
            )
            SignInForm(
                email, error, onEmailChange, onContinue, PhoneForm,
                Modifier.fillMaxWidth().clip(SheetShape).background(C.Bg).navigationBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 26.dp),
            )
        }
    }
}

private val CardShape = RoundedCornerShape(28.dp)
private val CardBorder = Color(0x14FFFFFF)

/**
 * Tablet sign-in ("Sign in (iPad landscape / portrait)" artboards): photo fills the screen, tagline
 * bottom-left, and the logo with the form in a centered card.
 */
@Composable
private fun TabletEmailStep(
    email: String,
    error: UiText?,
    onEmailChange: (String) -> Unit,
    onContinue: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Image(
            painterResource(R.drawable.login_bg), contentDescription = null,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
        )
        Box(Modifier.fillMaxSize().background(Color(0x59000000)))
        Text(
            stringResource(R.string.login_tagline),
            Modifier.align(Alignment.BottomStart).navigationBarsPadding()
                .padding(start = 48.dp, end = 48.dp, bottom = 48.dp).testTag(LoginTags.TAGLINE),
            style = TextStyle(
                color = Color.White, fontSize = 46.sp, fontWeight = FontWeight.Bold, lineHeight = 53.sp,
                shadow = Shadow(Color(0x66000000), offset = Offset(0f, 4f), blurRadius = 20f),
            ),
            maxLines = 3,
        )

        // The card stays centered; with the keyboard up (landscape) it scrolls so the form stays reachable.
        BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            Column(
                Modifier.fillMaxWidth().heightIn(min = maxHeight).verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier.widthIn(max = 500.dp).fillMaxWidth()
                        .shadow(40.dp, CardShape, spotColor = Color(0x80000000), ambientColor = Color(0x80000000))
                        .clip(CardShape).background(C.Bg).border(1.dp, CardBorder, CardShape)
                        .padding(44.dp),
                ) {
                    Brand(logoHeight = 40.dp, textSize = 22, gap = 12.dp)
                    Spacer(Modifier.height(44.dp))
                    SignInForm(email, error, onEmailChange, onContinue, TabletForm)
                }
            }
        }
    }
}

/** Logo mark + "Sclera Go". */
@Composable
private fun Brand(modifier: Modifier = Modifier, logoHeight: Dp, textSize: Int, gap: Dp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) {
        Image(painterResource(R.drawable.login_logo), contentDescription = null, modifier = Modifier.height(logoHeight))
        Txt("Sclera Go", textSize, 700, Color.White) // Product name: not translated.
    }
}

/** Sizes of the sign-in form: the phone artboard, or the larger tablet card. */
private class FormSize(
    val gap: Dp,
    val title: Int,
    val subtitle: Int,
    val control: Dp,
    val controlText: Int,
    val note: Int,
    val noteIcon: Dp,
)

private val PhoneForm = FormSize(gap = 18.dp, title = 24, subtitle = 15, control = 52.dp, controlText = 17, note = 12, noteIcon = 12.dp)
private val TabletForm = FormSize(gap = 20.dp, title = 30, subtitle = 17, control = 58.dp, controlText = 19, note = 14, noteIcon = 14.dp)

@Preview(name = "Phone", device = "spec:width=390dp,height=844dp")
@Preview(name = "Tablet portrait", device = "spec:width=800dp,height=1280dp,dpi=240")
@Preview(name = "Tablet landscape", device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun EmailStepPreview() {
    ScleraTheme { EmailStep(email = "", error = null, onEmailChange = {}, onContinue = {}) }
}

/** Title, email field, Continue button and SSO note. */
@Composable
private fun SignInForm(
    email: String,
    error: UiText?,
    onEmailChange: (String) -> Unit,
    onContinue: () -> Unit,
    size: FormSize,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(size.gap)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Txt(stringResource(R.string.login_title), size.title, 700)
            Txt(stringResource(R.string.login_subtitle), size.subtitle, 400, C.Muted, maxLines = 2)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicTextField(
                value = email,
                onValueChange = onEmailChange,
                singleLine = true,
                cursorBrush = SolidColor(C.Yellow),
                textStyle = TextStyle(color = C.Text, fontSize = size.controlText.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onGo = { onContinue() }),
                modifier = Modifier.fillMaxWidth().testTag(LoginTags.EMAIL).onFocusChanged { focused = it.isFocused },
                decorationBox = { inner ->
                    Row(
                        Modifier.fillMaxWidth().height(size.control)
                            .background(C.Surface, r(14))
                            .border(
                                if (focused) 1.5.dp else 1.dp,
                                when { error != null -> C.Red; focused -> C.Yellow; else -> FieldBorder },
                                r(14),
                            )
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        LineIcon(Icons.Mail, 18.dp, IconGrey, 1.8f)
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (email.isEmpty()) Txt(stringResource(R.string.login_email_hint), size.controlText, 400, C.Dim)
                            inner()
                        }
                    }
                },
            )
            if (error != null) FieldError(error.asString())
        }
        Row(
            Modifier.fillMaxWidth().height(size.control).testTag(LoginTags.CONTINUE)
                .alpha(if (email.isBlank()) 0.55f else 1f)
                .background(C.Yellow, r(14)).clickable(role = Role.Button, onClick = onContinue),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Txt(stringResource(R.string.login_continue), size.controlText, 600, C.OnYellow)
            Spacer(Modifier.width(8.dp))
            LineIcon(Icons.ArrowRight, 16.dp, C.OnYellow, 2.6f)
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            LineIcon(Icons.Lock, size.noteIcon, IconGrey, 2.4f)
            Spacer(Modifier.width(6.dp))
            Txt(stringResource(R.string.login_sso_note), size.note, 400, IconGrey)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SsoWebStep(
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
