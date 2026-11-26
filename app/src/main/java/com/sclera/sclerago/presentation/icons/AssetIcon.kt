package com.sclera.sclerago.presentation.icons

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.AssetIcons
import com.sclera.sclerago.domain.model.GENERIC_ICON

/** The asset icons kept on the device, for the screens below [com.sclera.sclerago.presentation.main.AssetIconsHost]. */
val LocalAssetIcons = compositionLocalOf { AssetIcons() }

/**
 * The icon of [asset]'s type, as the server gives it and global_config.db keeps it: the type's icon (or the generic
 * one) drawn from its SVG. Only until the icons have been downloaded once does the app's own copy of the generic icon
 * ([Icons.Box]) show.
 */
@Composable
fun assetIco(asset: Asset): Ico = typeIco(asset.masterFormatId)

/** The icon of the catalogue type [masterFormatId] (null: the generic one), as [assetIco] draws an asset's. */
@Composable
fun typeIco(masterFormatId: String?): Ico = storedIco(LocalAssetIcons.current.nameFor(masterFormatId))

/**
 * The generic asset icon as global_config.db keeps it (the server's "generic"), for something that has no type yet: a
 * scanned device, or an asset that isn't on this phone. The app's own copy shows until the icons have downloaded.
 */
@Composable
fun genericAssetIco(): Ico = storedIco(GENERIC_ICON)

@Composable
private fun storedIco(name: String): Ico {
    val svg = LocalAssetIcons.current.svgOf(name) ?: return Icons.Box
    return remember(name, svg) { SvgIco.of(svg) }
}

/**
 * Turns an asset icon's SVG (24×24, Lucide style: strokes in currentColor) into an [Ico], so it is drawn as the app's
 * own line icons are (sized, tinted and stroked by [LineIcon]). Paths, circles, ellipses, rectangles (rounded too),
 * lines, polylines and polygons are read; anything else is left out.
 */
object SvgIco {
    private val ELEMENT = Regex("""<(path|circle|ellipse|rect|line|polyline|polygon)\b([^>]*)>""")
    // Names may hold digits (a line's x1, y1, x2, y2).
    private val ATTRIBUTE = Regex("""([a-zA-Z_:][\w:.-]*)\s*=\s*"([^"]*)"""")
    private val cache = HashMap<String, Ico>()

    /** The [Ico] of [svg] (read once per SVG). */
    fun of(svg: String): Ico = synchronized(cache) { cache.getOrPut(svg) { parse(svg) } }

    private fun parse(svg: String): Ico {
        val parts = ELEMENT.findAll(svg).mapNotNull { m ->
            val a = ATTRIBUTE.findAll(m.groupValues[2]).associate { it.groupValues[1] to it.groupValues[2] }
            fun n(key: String) = a[key]?.toFloatOrNull() ?: 0f
            when (m.groupValues[1]) {
                "path" -> a["d"]?.takeIf { it.isNotBlank() }
                "circle" -> ellipse(n("cx"), n("cy"), n("r"), n("r"))
                "ellipse" -> ellipse(n("cx"), n("cy"), n("rx"), n("ry"))
                "rect" -> rect(n("x"), n("y"), n("width"), n("height"), a["rx"]?.toFloatOrNull() ?: a["ry"]?.toFloatOrNull() ?: 0f, a["ry"]?.toFloatOrNull() ?: a["rx"]?.toFloatOrNull() ?: 0f)
                "line" -> "M${n("x1")} ${n("y1")}L${n("x2")} ${n("y2")}"
                "polyline" -> points(a["points"], close = false)
                "polygon" -> points(a["points"], close = true)
                else -> null
            }?.let { Ico.Part(it) }
        }.toList()
        return Ico(*parts.toTypedArray())
    }

    private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float): String? =
        if (rx <= 0f || ry <= 0f) null
        else "M${cx - rx} ${cy}A$rx $ry 0 1 0 ${cx + rx} ${cy}A$rx $ry 0 1 0 ${cx - rx} ${cy}Z"

    private fun rect(x: Float, y: Float, w: Float, h: Float, rxIn: Float, ryIn: Float): String? {
        if (w <= 0f || h <= 0f) return null
        val rx = rxIn.coerceIn(0f, w / 2)
        val ry = ryIn.coerceIn(0f, h / 2)
        if (rx == 0f || ry == 0f) return "M$x ${y}H${x + w}V${y + h}H${x}Z"
        return "M${x + rx} ${y}H${x + w - rx}A$rx $ry 0 0 1 ${x + w} ${y + ry}V${y + h - ry}" +
            "A$rx $ry 0 0 1 ${x + w - rx} ${y + h}H${x + rx}A$rx $ry 0 0 1 $x ${y + h - ry}V${y + ry}A$rx $ry 0 0 1 ${x + rx} ${y}Z"
    }

    private fun points(text: String?, close: Boolean): String? {
        val values = text.orEmpty().split(Regex("[\\s,]+")).mapNotNull { it.toFloatOrNull() }
        if (values.size < 4) return null
        val pairs = values.chunked(2).filter { it.size == 2 }
        return pairs.mapIndexed { i, (px, py) -> (if (i == 0) "M" else "L") + "$px $py" }.joinToString("") + if (close) "Z" else ""
    }
}
