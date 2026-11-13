package com.sclera.sclerago.presentation.theme

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.sclera.sclerago.R

/** Design tokens lifted from the Sclera Go prototype. */
object C {
    val Bg = Color(0xFF141518)
    val Surface = Color(0xFF1E2024)
    val Border = Color(0xFF2C2F35)
    val Strip = Color(0xFF17181B)
    val Raised = Color(0xFF272A2F)
    val Text = Color(0xFFF4F4F5)
    val Muted = Color(0xFFA9ACB3)
    val Soft = Color(0xFFD2D4D8)
    val Yellow = Color(0xFFF5B800)
    val OnYellow = Color(0xFF1A1A1A)
    val Btn = Color(0xFF333333)
    val Border2 = Color(0xFF3A3D44)
    val RadioOff = Color(0xFF4A4D54)
    val YTintBg = Color(0xFF2E2914)
    val YTintBd = Color(0xFF4A4020)
    val Green = Color(0xFF5DBE8C)
    val Orange = Color(0xFFEE9A4D)
    val Red = Color(0xFFE46F6B)
    val Purple = Color(0xFFAE9CE6)
    val Blue = Color(0xFF6FA8E6)
    val Cyan = Color(0xFF4FC6D6)
    val Dim = Color(0xFF6B6F77)
    val ChipBd = Color(0xFF33363C)
    val PickedRow = Color(0xFF26231A)
    val WarnRow = Color(0xFF2A1F15)
    val Scrim = Color(0xB808090A)
    val White = Color.White
    val IconGrey = Color(0xFF8B8F97)
    val ImportStrip = Color(0xFF1A1B1E)
    val SheetBg = Color(0xFF1B1D21)
    val SheetHandle = Color(0xFF45464C)
    val TintText = Color(0xFFD8CFA8)
    val PrimaryOffBg = Color(0xFF5C4A10)
    val PrimaryOffFg = Color(0xFFC9B57A)
    val CardWash = Color(0x80272A2F)
    val DangerBg = Color(0xFF2A1919)
    val FieldRowWash = Color(0x99272A2F)

    /** Dim behind sheets and dialogs. */
    val Overlay = Color(0x99000000)

    /** Black at the given alpha (the photo shading of the sign-in screen). */
    fun shade(alpha: Float) = Color.Black.copy(alpha = alpha)

    /** White at the given alpha (hairlines on photos). */
    fun frost(alpha: Float) = Color.White.copy(alpha = alpha)
}

/** Bundled fonts (res/font, SIL OFL): Inter for the app, Nunito Sans for the logo lockup. */
object ScleraFonts {
    @OptIn(ExperimentalTextApi::class)
    private fun variable(@androidx.annotation.FontRes res: Int, vararg weights: Int) = FontFamily(
        weights.map { w -> Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w))) },
    )

    val Inter: FontFamily = variable(R.font.inter, 400, 500, 600, 700, 800)
    val NunitoSans: FontFamily = variable(R.font.nunito_sans, 700, 800)
}

@Composable
fun ScleraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Yellow, onPrimary = C.OnYellow,
            background = C.Bg, onBackground = C.Text,
            surface = C.Surface, onSurface = C.Text,
            surfaceContainer = C.Surface, surfaceVariant = C.Raised,
        )
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides TextStyle(color = C.Text, fontSize = 14.sp, fontFamily = ScleraFonts.Inter),
            LocalContentColor provides C.Text,
            LocalTextSelectionColors provides TextSelectionColors(C.Yellow, C.Yellow.copy(alpha = 0.35f)),
            content = content,
        )
    }
}

/** Single-line-by-default text with the prototype's px sizes mapped to sp. */
@Composable
fun Txt(
    text: String,
    size: Number,
    weight: Int = 400,
    color: Color = C.Text,
    modifier: Modifier = Modifier,
    mono: Boolean = false,
    italic: Boolean = false,
    spacing: Float = 0f,
    maxLines: Int = 1,
    align: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size.toFloat().sp,
        fontWeight = FontWeight(weight),
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        fontFamily = if (mono) FontFamily.Monospace else ScleraFonts.Inter,
        letterSpacing = spacing.sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = align,
        lineHeight = lineHeight,
    )
}
