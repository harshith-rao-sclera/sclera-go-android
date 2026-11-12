package com.sclera.sclerago.presentation.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp

/** A 24x24 line icon made of SVG path data, drawn with a caller-chosen stroke width. */
class Ico(private vararg val parts: Part) {
    /** [clear] parts erase what was drawn before them (e.g. an arrow cut out of a solid badge). */
    class Part(val d: String, val stroke: Boolean = true, val fill: Boolean = false, val clear: Boolean = false)

    internal class Drawn(val path: Path, val stroke: Boolean, val fill: Boolean, val clear: Boolean)

    internal val paths: List<Drawn> by lazy {
        parts.map { Drawn(PathParser().parsePathString(it.d).toPath(), it.stroke, it.fill, it.clear) }
    }
    internal val hasClear: Boolean get() = parts.any { it.clear }
}

private fun s(d: String) = Ico.Part(d)
private fun f(d: String) = Ico.Part(d, stroke = false, fill = true)
private fun sf(d: String) = Ico.Part(d, stroke = true, fill = true)
private fun cutStroke(d: String) = Ico.Part(d, stroke = true, clear = true)
private fun cutFill(d: String) = Ico.Part(d, stroke = false, fill = true, clear = true)
private fun circle(cx: Float, cy: Float, r: Float) =
    "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0Z"

private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float = 0f): String {
    if (r == 0f) return "M$x,${y}h${w}v${h}h${-w}Z"
    return "M${x + r},${y}h${w - 2 * r}a$r,$r 0 0 1 $r,$r" +
        "v${h - 2 * r}a$r,$r 0 0 1 ${-r},$r" +
        "h${-(w - 2 * r)}a$r,$r 0 0 1 ${-r},${-r}" +
        "v${-(h - 2 * r)}a$r,$r 0 0 1 $r,${-r}Z"
}

@Composable
fun LineIcon(icon: Ico, size: Dp, color: Color, stroke: Float = 2f, modifier: Modifier = Modifier) {
    // Icons with cut-out parts draw into their own layer so "clear" only erases the icon itself.
    val layer = if (icon.hasClear) Modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen) else Modifier
    Canvas(modifier.size(size).then(layer)) {
        val k = this.size.minDimension / 24f
        scale(k, k, pivot = Offset.Zero) {
            icon.paths.forEach { p ->
                val blend = if (p.clear) BlendMode.Clear else BlendMode.SrcOver
                if (p.fill) drawPath(p.path, color, blendMode = blend)
                if (p.stroke) drawPath(
                    p.path, color, blendMode = blend,
                    style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}

object Icons {
    val Menu = Ico(s("M4 7h16M4 12h16M4 17h16"))
    /** The property (site): the same building as Configuration's header. */
    val Property = Ico(s(rect(5f, 3f, 14f, 18f, 1f)), s("M9 7h2M13 7h2M9 11h2M13 11h2M9 15h2M13 15h2"))
    val Pin = Ico(s("M12 21s-7-6.2-7-11.5A7 7 0 0 1 19 9.5C19 14.8 12 21 12 21z"), s(circle(12f, 9.5f, 2.5f)))
    val Unmapped = Ico(
        s("M9.3 4.2A7 7 0 0 1 19 9.5c0 1.6-.6 3.3-1.5 4.8M14.8 17.6C13.3 19.6 12 21 12 21s-7-6.2-7-11.5c0-.9.2-1.8.5-2.6"),
        s("M3 3l18 18"),
    )
    val ChevronDown = Ico(s("M6 9l6 6 6-6"))
    val ChevronLeft = Ico(s("M15 6l-6 6 6 6"))
    val ChevronRight = Ico(s("M9 6l6 6-6 6"))
    val Bluetooth = Ico(s("M7 7l10 10-5 5V2l5 5L7 17"))
    val Wifi = Ico(
        s("M2 9a15 15 0 0 1 20 0M5 12.5a10 10 0 0 1 14 0M8.5 16a5 5 0 0 1 7 0"),
        sf(circle(12f, 19.5f, 1.2f)),
    )
    val AccessPoint = Ico(
        s(rect(3f, 14f, 18f, 6f, 2f)), s("M7 17h.01M11 17h.01"),
        s("M8.5 10.5a5 5 0 0 1 7 0M5.5 7.5a9.5 9.5 0 0 1 13 0"),
    )
    val Box = Ico(s("M21 8L12 3 3 8v8l9 5 9-5zM3 8l9 5 9-5M12 13v8"))
    val Camera = Ico(
        s("M4 8h3l2-3h6l2 3h3a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1z"),
        s(circle(12f, 13.5f, 3.5f)),
    )
    val FileUpload = Ico(
        s("M14 3H6a1 1 0 0 0-1 1v16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8z"), s("M14 3v5h5"), s("M12 18v-6M9 15l3-3 3 3"),
    )
    /** Document with a sharp folded corner and a round upload badge at the bottom-left (File switch). */
    val FileUploadBadge = Ico(
        s("M7.2 12.6V4.6A1.6 1.6 0 0 1 8.8 3h5.4l5.8 5.8V19a1.6 1.6 0 0 1-1.6 1.6H12.2"), s("M14.2 3v5.8H20"),
        s("M3.5,17a4,4 0 1,0 8,0a4,4 0 1,0 -8,0Z"), f("M7.5 14.4L9.6 16.9H5.4Z"), s("M7.5 16.6v2.6"),
    )

    /** Same, with a solid badge and the arrow cut out of it (bottom bar File tab). */
    val FileUploadBadgeSolid = Ico(
        s("M7.2 12.6V4.6A1.6 1.6 0 0 1 8.8 3h5.4l5.8 5.8V19a1.6 1.6 0 0 1-1.6 1.6H12.2"), s("M14.2 3v5.8H20"),
        sf("M3.5,17a4,4 0 1,0 8,0a4,4 0 1,0 -8,0Z"), cutFill("M7.5 14.4L9.6 16.9H5.4Z"), cutStroke("M7.5 16.6v2.6"),
    )
    val FileDoc = Ico(
        s("M14 3H6a1 1 0 0 0-1 1v16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8z"), s("M14 3v5h5M9 13h6M9 17h6"),
    )
    val Keyboard = Ico(s(rect(2f, 6f, 20f, 12f, 2f)), s("M6 10h.01M10 10h.01M14 10h.01M18 10h.01M7 14h10"))
    val Play = Ico(f("M8 5v14l11-7z"))
    val PlusSmall = Ico(s("M12 6v12M6 12h12"))
    val Plus = Ico(s("M12 5v14M5 12h14"))
    val Check = Ico(s("M20 6L9 17l-5-5"))
    val CheckCircle = Ico(s(circle(12f, 12f, 9f)), s("M8 12l3 3 5-6"))
    val ShieldCheck = Ico(s("M12 3L5 6v6c0 4.5 3 7.5 7 9 4-1.5 7-4.5 7-9V6z"), s("M9 12l2 2 4-4"))
    val Send = Ico(s("M21 3L3 10.5l7 2.5 2.5 7z"), s("M21 3L10 13"))
    val Pencil = Ico(s("M4 20h4L19 9l-4-4L4 16z"), s("M13.5 6.5l4 4"))
    val Alert = Ico(s(circle(12f, 12f, 9f)), s("M12 7v6M12 16.5v.5"))
    val OctagonX = Ico(s("M8.2 3h7.6L21 8.2v7.6L15.8 21H8.2L3 15.8V8.2z"), s("M9 9l6 6M15 9l-6 6"))
    /** AI: three solid four-point sparkles (large, medium, small). */
    val Sparkles = Ico(
        f("M14.8,7Q15.71,13.69 22.4,14.6Q15.71,15.51 14.8,22.2Q13.89,15.51 7.2,14.6Q13.89,13.69 14.8,7Z"),
        f("M6.4,5.5Q6.84,8.76 10.1,9.2Q6.84,9.64 6.4,12.9Q5.96,9.64 2.7,9.2Q5.96,8.76 6.4,5.5Z"),
        f("M12.6,1.8Q12.86,3.74 14.8,4Q12.86,4.26 12.6,6.2Q12.34,4.26 10.4,4Q12.34,3.74 12.6,1.8Z"),
    )
    val Sparkle = Ico(s("M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9z"))
    val Close = Ico(s("M18 6L6 18M6 6l12 12"))
    val FlashOff = Ico(s("M13 3L6 13h5l-1 8 7-10h-5z"), s("M3 3l18 18"))
    val Flash = Ico(s("M13 3L6 13h5l-1 8 7-10h-5z"))
    val Video = Ico(f("M3 6h12v12H3zM15 10l6-3v10l-6-3z"))
    val Gallery = Ico(s(rect(3f, 4f, 18f, 16f, 2f)), s(circle(9f, 10f, 2f)), s("M21 16l-5-5-9 9"))
    val SwitchCamera = Ico(
        s("M4 9a8 8 0 0 1 14-3l2 2M20 15a8 8 0 0 1-14 3l-2-2"), s("M20 4v4h-4M4 20v-4h4"), s(circle(12f, 12f, 2.5f)),
    )
    val Expand = Ico(s("M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7"))
    val Search = Ico(s(circle(11f, 11f, 7f)), s("M20 20l-3.5-3.5"))
    val Save = Ico(s("M5 3h11l3 3v15H5z"), s("M8 3v5h7V3M8 21v-7h8v7"))
    val Crosshair = Ico(s(circle(12f, 12f, 3f)), s(circle(12f, 12f, 7.5f)), s("M12 2v3M12 19v3M2 12h3M19 12h3"))
    val Building = Ico(s(rect(5f, 3f, 14f, 18f, 1f)), s("M9 7h2M13 7h2M9 11h2M13 11h2M9 15h2M13 15h2"))
    /** Two buildings side by side: property list / current property in the side menu. */
    val Buildings = Ico(
        s("M3 21V6a1 1 0 011-1h7a1 1 0 011 1v15M2 21h20M12 21V10a1 1 0 011-1h6a1 1 0 011 1v11"),
        s("M6.5 9h2M6.5 12.5h2M6.5 16h2M15.5 13h1.5M15.5 16.5h1.5"),
    )
    val Floor = Ico(s("M12 3l9 5-9 5-9-5z"), s("M3 13l9 5 9-5"))
    val Info = Ico(s(circle(12f, 12f, 9f)), s("M12 11v6M12 7.5v.5"))
    val Users = Ico(
        s(circle(9f, 8f, 3.5f)), s("M2.5 20v-.5A5.5 5.5 0 0 1 8 14h2a5.5 5.5 0 0 1 5.5 5.5v.5"),
        s("M15.5 4.6a3.5 3.5 0 0 1 0 6.8M18 14.2a5.5 5.5 0 0 1 3.5 5.3v.5"),
    )
    val User = Ico(s("M12 12a4 4 0 100-8 4 4 0 000 8zM4 21c0-4 3.6-6 8-6s8 2 8 6"))
    val Mail = Ico(s(rect(3f, 5f, 18f, 14f, 2f)), s("M4 7l8 6 8-6"))
    val ArrowRight = Ico(s("M5 12h14M13 6l6 6-6 6"))
    val Download = Ico(s("M12 4v11M7 10l5 5 5-5"), s("M5 20h14"))
    val SignOut = Ico(s("M10 4H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h4"), s("M14 8l4 4-4 4M18 12H9"))
    val UserPlus = Ico(s(circle(9f, 8f, 4f)), s("M2 21v-1a6 6 0 0 1 6-6h2a6 6 0 0 1 6 6v1"), s("M19 8v6M16 11h6"))
    val Upload = Ico(s("M12 15V4M7 9l5-5 5 5"), s("M4 15v3a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-3"))
    val Trash = Ico(s("M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"))
    val Mic = Ico(s(rect(9f, 3f, 6f, 11f, 3f)), s("M5 11a7 7 0 0 0 14 0M12 18v3"))

    val Dots = Ico(f(circle(12f, 5f, 1.7f)), f(circle(12f, 12f, 1.7f)), f(circle(12f, 19f, 1.7f)))
    val Minus = Ico(s("M6 12h12"))

    // Validation templates
    val Bolt = Ico(s("M13 2.5L4.5 13.5H11l-1 8 8.5-11H12z"))
    val Copy = Ico(s(rect(8f, 8f, 12.5f, 12.5f, 2f)), s("M16 8V5.5A2 2 0 0 0 14 3.5H5.5a2 2 0 0 0-2 2V14a2 2 0 0 0 2 2H8"))
    val ScanAi = Ico(
        s("M3.5 8V5.5a2 2 0 0 1 2-2H8M16 3.5h2.5a2 2 0 0 1 2 2V8M20.5 16v2.5a2 2 0 0 1-2 2H16M8 20.5H5.5a2 2 0 0 1-2-2V16"),
        f("M12,7Q12.45,11.55 17,12Q12.45,12.45 12,17Q11.55,12.45 7,12Q11.55,11.55 12,7Z"),
    )

    // Side menu
    val Sliders = Ico(s("M4 6h9M17 6h3M4 12h3M11 12h9M4 18h11M19 18h1"), s(circle(15f, 6f, 2f)), s(circle(9f, 12f, 2f)), s(circle(17f, 18f, 2f)))
    val Clipboard = Ico(s(rect(5f, 4f, 14f, 17f, 2f)), s(rect(9f, 2.5f, 6f, 3f, 1f)), s("M9 11h6M9 15h4"))
    val Survey = Ico(s("M14 21H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h8l4 4v4"), s("M8 8h5M8 12h3"), s(circle(15.5f, 16.5f, 2.8f)), s("M17.6 18.6L20 21"))
    val Link = Ico(s("M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1"), s("M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1"))
    /** A tray with an arrow into it: import. */
    val Import = Ico(s("M4 14v5a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-5"), s("M12 3v12M7.5 10.5L12 15l4.5-4.5"))
    val Clock = Ico(s(circle(12f, 12f, 9f)), s("M12 7v5.5l-3.5 2"))
    val PlusCircle = Ico(s(circle(12f, 12f, 9f)), s("M12 8v8M8 12h8"))
    val CheckSquare = Ico(s(rect(4f, 4f, 16f, 16f, 3f)), s("M8.5 12.5l2.5 2.5 4.5-5"))
    /** Ticks beside lines: multiple check boxes. */
    val ListCheck = Ico(s("M3.5 7l1.5 1.5L8 5.5M3.5 15l1.5 1.5L8 13.5"), s("M11 7h10M11 15h10"))
    val Globe = Ico(s(circle(12f, 12f, 9f)), s("M3 12h18M12 3c2.5 2.6 3.7 5.6 3.7 9s-1.2 6.4-3.7 9c-2.5-2.6-3.7-5.6-3.7-9s1.2-6.4 3.7-9z"))
    /** A spreadsheet grid. */
    val Table = Ico(s(rect(3.5f, 4.5f, 17f, 15f, 2f)), s("M3.5 9.5h17M3.5 14.5h17M9.5 9.5v10"))
    val Gear = Ico(
        s(circle(12f, 12f, 3f)),
        s("M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"),
    )
    val Lock = Ico(s(rect(5f, 11f, 14f, 10f, 2f)), s("M8 11V8a4 4 0 0 1 8 0v3"))
    val Eye = Ico(s("M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z"), s(circle(12f, 12f, 3f)))
    val EyeOff = Ico(
        s("M10.6 5.1A10 10 0 0 1 12 5c6.4 0 10 7 10 7a17 17 0 0 1-2.4 3.3M6.6 6.6C3.7 8.4 2 12 2 12s3.6 7 10 7a9.6 9.6 0 0 0 5.4-1.6"),
        s("M9.9 9.9a3 3 0 0 0 4.2 4.2M3 3l18 18"),
    )
    /** Two switches: Configuration → Property Settings. */
    val Toggles = Ico(s(rect(3f, 4f, 18f, 7f, 3.5f)), f(circle(16.5f, 7.5f, 2f)), s(rect(3f, 13f, 18f, 7f, 3.5f)), f(circle(7.5f, 16.5f, 2f)))
    val Refresh = Ico(s("M20 12a8 8 0 1 1-2.3-5.7"), s("M20 4v5h-5"))
    val Hash = Ico(s("M5 9h15M4 15h15M10 3L8 21M16 3l-2 18"))
    val Calendar = Ico(s(rect(3.5f, 5f, 17f, 15.5f, 2f)), s("M3.5 10h17M8 3v4M16 3v4"))
    val TextLines = Ico(s("M4 6h16M4 10h16M4 14h16M4 18h10"))
    val TextAa = Ico(s("M3 18l4.5-12L12 18M4.7 14h5.6"), s("M17.5 18v-1.2m0 1.2a3 3 0 1 1 0-6 3 3 0 0 1 0 6zm0-6V9.5a2 2 0 0 0-3.5-1.2"))
    val ChevronCircle = Ico(s(circle(12f, 12f, 9f)), s("M8.5 10.5L12 14l3.5-3.5"))
    val ArrowUp = Ico(s("M12 19V5M6 11l6-6 6 6"))
    val ArrowDown = Ico(s("M12 5v14M6 13l6 6 6-6"))

    // Asset types
    val Motor = Ico(s(rect(2.5f, 7f, 13f, 10f, 2f)), s("M15.5 10.5H19v3h-3.5M19 12h2.5M6 7V5h6v2M5.5 10.5h7M5.5 13.5h7"))
    val Pump = Ico(s(circle(10f, 13f, 6f)), s(circle(10f, 13f, 2f)), s("M16 13h5.5M10 7V3.5h6"))
    val Chiller = Ico(s("M12 2.5v19M3.8 7.2l16.4 9.6M3.8 16.8l16.4-9.6"), s("M9.5 4l2.5 2 2.5-2M9.5 20l2.5-2 2.5 2"))
    val Fan = Ico(
        s(circle(12f, 12f, 1.8f)),
        s(
            "M12 10.2C11 6.5 12 3 15 3c2.2 0 2.8 2.4 1.4 4-1.2 1.4-2.9 2.3-4.4 3.2z" +
                "M13.8 12c3.7-1 7.2 0 7.2 3 0 2.2-2.4 2.8-4 1.4-1.4-1.2-2.3-2.9-3.2-4.4z" +
                "M12 13.8c1 3.7 0 7.2-3 7.2-2.2 0-2.8-2.4-1.4-4 1.2-1.4 2.9-2.3 4.4-3.2z" +
                "M10.2 12C6.5 13 3 12 3 9c0-2.2 2.4-2.8 4-1.4 1.4 1.2 2.3 2.9 3.2 4.4z"
        ),
    )
    val Controller = Ico(
        s(rect(6f, 6f, 12f, 12f, 2f)), s(rect(9.5f, 9.5f, 5f, 5f, 1f)),
        s("M9 2.5V6M15 2.5V6M9 18v3.5M15 18v3.5M2.5 9H6M2.5 15H6M18 9h3.5M18 15h3.5"),
    )
    val Cctv = Ico(s("M2.5 7L17 10.2l-1.5 5L2 12z"), s("M17 11.5l4.5-1v5l-5 .6M7 13.4V19H3"))
    val Panel = Ico(s(rect(5f, 2.5f, 14f, 19f, 1.5f)), s("M12.8 7L10 12h4l-2.8 5"))
    val Computer = Ico(s(rect(3f, 4f, 18f, 12f, 1.5f)), s("M8.5 20h7M12 16v4"))
    val Phone = Ico(s(rect(7f, 2.5f, 10f, 19f, 2.2f)), s("M11 18.5h2"))
    val Speaker = Ico(s(rect(5f, 2.5f, 14f, 19f, 2.5f)), s(circle(12f, 14.5f, 3.5f)), s(circle(12f, 7.2f, 1.2f)))
    val Sensor = Ico(s("M14 14.5V5a2 2 0 0 0-4 0v9.5a4 4 0 1 0 4 0z"), s("M12 9v7"))
}
