package com.sclera.sclerago.presentation.tagging

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.TagScannerFailure
import com.sclera.sclerago.domain.model.TagKind
import com.sclera.sclerago.domain.usecase.tagging.TagScan
import com.sclera.sclerago.presentation.components.RoundButton
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

private val ButtonBg = Color(0xCC202226)
private val Scrim = Color(0x94000000)
private const val QR_FRACTION_TOP = 0.27f
private const val BARCODE_FRACTION_TOP = 0.32f

/**
 * The camera screen for scanning a QR code or a barcode: a viewfinder with yellow corners and a sweeping line, a close button and
 * the flashlight, and a hint underneath. The code is read by [TagScannerView]; this only draws around it.
 */
@Composable
fun TagScanScreen(
    kind: TagKind,
    onScan: (TagScan) -> Unit,
    onFailure: (TagScannerFailure) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var torchOn by remember { mutableStateOf(false) }
    val isQr = kind == TagKind.QR
    Box(modifier.fillMaxSize().background(Color.Black).testTag(TagTags.SCREEN)) {
        TagScannerView(kind, torchOn, onScan, onFailure, Modifier.fillMaxSize())
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val width = if (isQr) minOf(264.dp, maxHeight * 0.42f, maxWidth - 48.dp) else minOf(320.dp, maxWidth - 48.dp)
            val height = if (isQr) width else width * 150f / 320f
            val top = maxHeight * (if (isQr) QR_FRACTION_TOP else BARCODE_FRACTION_TOP)
            Viewfinder(width, height, top)
            Column(
                Modifier.align(Alignment.TopCenter).offset(y = top + height + 36.dp).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Txt(
                    stringResource(if (isQr) R.string.tag_scan_qr_hint else R.string.tag_scan_barcode_hint),
                    16, 600, align = TextAlign.Center, maxLines = 3,
                )
                Txt(
                    stringResource(if (isQr) R.string.tag_scan_qr_sub else R.string.tag_scan_barcode_sub),
                    13, 400, C.Muted, Modifier.padding(top = 6.dp), align = TextAlign.Center, maxLines = 2,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).height(60.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val close = stringResource(R.string.action_close)
            RoundButton(44.dp, ButtonBg, onClose, Modifier.border(1.dp, C.Border2, CircleShape).semantics { contentDescription = close }.testTag(TagTags.CLOSE)) {
                LineIcon(Icons.Close, 18.dp, C.Text, 3f)
            }
            Txt(
                stringResource(if (isQr) R.string.tag_scan_qr_title else R.string.tag_scan_barcode_title),
                17, 700, modifier = Modifier.weight(1f), align = TextAlign.Center,
            )
            val torch = stringResource(R.string.tag_torch)
            RoundButton(44.dp, ButtonBg, { torchOn = !torchOn }, Modifier.border(1.dp, C.Border2, CircleShape).semantics { contentDescription = torch }.testTag(TagTags.TORCH)) {
                // The camera's flash icons, so it reads as the flash (on: yellow).
                LineIcon(if (torchOn) Icons.Flash else Icons.FlashOff, 20.dp, if (torchOn) C.Yellow else C.Text)
            }
        }
    }
}

/** The dimmed surround with a clear window, the four yellow corners and the sweeping line. */
@Composable
private fun Viewfinder(width: Dp, height: Dp, top: Dp) {
    val sweep by rememberInfiniteTransition(label = "sweep").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse), label = "line",
    )
    Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
        val w = width.toPx()
        val h = height.toPx()
        val left = (size.width - w) / 2
        val t = top.toPx()
        val radius = 22.dp.toPx()
        drawRect(Scrim)
        drawRoundRect(Color.Black, Offset(left, t), Size(w, h), CornerRadius(radius), blendMode = BlendMode.Clear)
        val stroke = 4.dp.toPx()
        val arm = 40.dp.toPx()
        listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f).forEach { (fx, fy) ->
            val x = left + fx * w
            val y = t + fy * h
            val sx = if (fx == 0f) 1f else -1f
            val sy = if (fy == 0f) 1f else -1f
            val path = Path().apply {
                moveTo(x, y + sy * arm)
                lineTo(x, y + sy * radius)
                quadraticTo(x, y, x + sx * radius, y)
                lineTo(x + sx * arm, y)
            }
            drawPath(path, C.Yellow, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        val lineY = t + 14.dp.toPx() + sweep * (h - 30.dp.toPx())
        drawRect(C.Yellow, Offset(left + 14.dp.toPx(), lineY), Size(w - 28.dp.toPx(), 2.dp.toPx()))
    }
}
