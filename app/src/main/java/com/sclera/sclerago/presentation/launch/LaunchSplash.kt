package com.sclera.sclerago.presentation.launch

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.sclera.sclerago.R
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.ScleraFonts

const val LAUNCH_SPLASH_TAG = "launch_splash"

private val SymbolWidth = 242.dp
private val LockupGap = 20.dp

/**
 * The splash lockup (symbol over the "sclera-go" wordmark) at [timeMs] on the launch timeline:
 * the symbol zooms in from 5x, the wordmark's letters close up, then the whole layer fades out.
 */
@Composable
fun LaunchSplash(timeMs: Float, modifier: Modifier = Modifier) {
    val zoom = LaunchMotion.SymbolZoom.progress(timeMs)
    val word = LaunchMotion.WordmarkIn.progress(timeMs)
    val out = LaunchMotion.SplashOut.progress(timeMs)
    val description = stringResource(R.string.launch_logo_description)
    Box(
        modifier.fillMaxSize().alpha(1f - out).background(C.Splash).testTag(LAUNCH_SPLASH_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.semantics(mergeDescendants = true) { contentDescription = description },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LockupGap),
        ) {
            Image(
                painterResource(R.drawable.login_logo), contentDescription = null,
                modifier = Modifier.width(SymbolWidth).graphicsLayer {
                    val scale = LaunchMotion.SymbolStartScale * (1f - zoom) + zoom
                    scaleX = scale
                    scaleY = scale
                    alpha = zoom
                    blur(LaunchMotion.SymbolStartBlurPx * (1f - zoom))
                },
            )
            Wordmark(Modifier.graphicsLayer { alpha = word; blur(LaunchMotion.WordmarkStartBlurPx * (1f - word)) }, word)
        }
    }
}

@Composable
private fun Wordmark(modifier: Modifier, progress: Float) {
    val spacing = LaunchMotion.WordmarkStartSpacingEm +
        (LaunchMotion.WordmarkEndSpacingEm - LaunchMotion.WordmarkStartSpacingEm) * progress
    Text(
        // Product name: not translated.
        buildAnnotatedString {
            append("sclera")
            withStyle(SpanStyle(color = C.BrandYellow)) { append("-go") }
        },
        modifier,
        style = TextStyle(
            color = C.White, fontFamily = ScleraFonts.NunitoSans, fontWeight = FontWeight.ExtraBold,
            fontSize = 52.sp, letterSpacing = spacing.em, lineHeight = 52.sp,
        ),
        maxLines = 1, softWrap = false,
    )
}

/** Blur needs RenderEffect (Android 12+); older versions simply skip it. */
private fun GraphicsLayerScope.blur(radiusPx: Float) {
    renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && radiusPx > 0.01f) {
        BlurEffect(radiusPx, radiusPx, TileMode.Decal)
    } else {
        null
    }
}

@Preview(name = "Phone", device = "spec:width=390dp,height=844dp")
@Preview(name = "Tablet portrait", device = "spec:width=800dp,height=1280dp,dpi=240")
@Preview(name = "Tablet landscape", device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
private fun LaunchSplashPreview() {
    LaunchSplash(timeMs = 1600f)
}
