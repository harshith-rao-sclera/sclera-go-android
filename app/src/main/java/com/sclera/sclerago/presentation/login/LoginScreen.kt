package com.sclera.sclerago.presentation.login

import android.webkit.WebView
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.AuthError
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.components.isTablet
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.launch.Entrance
import com.sclera.sclerago.presentation.launch.launchEntrance
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.ScleraTheme
import com.sclera.sclerago.presentation.theme.Txt


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
                    Txt(stringResource(R.string.login_signing_in), 14, 500, C.Muted)
                }
            }
        }
    }
}

private val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
/** Red line under the email field (design: "Sign in" artboard). */
@Composable
private fun FieldError(message: String) {
    Row(
        Modifier.fillMaxWidth().testTag(LoginTags.ERROR),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LineIcon(Icons.Alert, 14.dp, C.Red, 2.2f)
        Txt(message, 12, 500, C.Red, Modifier.weight(1f), maxLines = 3)
    }
}

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
    Box(Modifier.fillMaxSize().background(C.Bg)) {
        if (isTablet()) {
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
        Box(Modifier.fillMaxWidth().fillMaxHeight(567f / 844f).launchEntrance(Entrance.Hero)) {
            Image(
                painterResource(R.drawable.login_bg), contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to C.shade(0.55f), 0.35f to C.shade(0.05f), 0.6f to C.shade(0.1f), 1f to C.Bg.copy(alpha = 0.85f),
                    ),
                ),
            )
        }
        Brand(Modifier.launchEntrance(Entrance.Rise).statusBarsPadding().padding(start = 24.dp, top = 20.dp), logoHeight = 36.dp, textSize = 19, gap = 10.dp)

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding()) {
            Txt(
                stringResource(R.string.login_tagline), 30, 700, Color.White,
                Modifier.launchEntrance(Entrance.Rise).padding(start = 24.dp, end = 24.dp, bottom = 24.dp).testTag(LoginTags.TAGLINE),
                maxLines = 3, lineHeight = 36.sp,
            )
            SignInForm(
                email, error, onEmailChange, onContinue, PhoneForm,
                Modifier.fillMaxWidth().launchEntrance(Entrance.Sheet).clip(SheetShape).background(C.Bg).navigationBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 26.dp),
            )
        }
    }
}

private val CardShape = RoundedCornerShape(28.dp)

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
            modifier = Modifier.fillMaxSize().launchEntrance(Entrance.Hero), contentScale = ContentScale.Crop,
        )
        Box(Modifier.fillMaxSize().background(C.shade(0.35f)))
        Text(
            stringResource(R.string.login_tagline),
            Modifier.align(Alignment.BottomStart).launchEntrance(Entrance.Rise).navigationBarsPadding()
                .padding(start = 48.dp, end = 48.dp, bottom = 48.dp).testTag(LoginTags.TAGLINE),
            style = TextStyle(
                color = Color.White, fontSize = 46.sp, fontWeight = FontWeight.Bold, lineHeight = 53.sp,
                shadow = Shadow(C.shade(0.4f), offset = Offset(0f, 4f), blurRadius = 20f),
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
                    Modifier.widthIn(max = 500.dp).fillMaxWidth().launchEntrance(Entrance.Rise)
                        .shadow(40.dp, CardShape, spotColor = C.shade(0.5f), ambientColor = C.shade(0.5f))
                        .clip(CardShape).background(C.Bg).border(1.dp, C.frost(0.08f), CardShape)
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
                                when { error != null -> C.Red; focused -> C.Yellow; else -> C.Border2 },
                                r(14),
                            )
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        LineIcon(Icons.Mail, 18.dp, C.IconGrey, 1.8f)
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
            LineIcon(Icons.Lock, size.noteIcon, C.IconGrey, 2.4f)
            Spacer(Modifier.width(6.dp))
            Txt(stringResource(R.string.login_sso_note), size.note, 400, C.IconGrey)
        }
    }
}
