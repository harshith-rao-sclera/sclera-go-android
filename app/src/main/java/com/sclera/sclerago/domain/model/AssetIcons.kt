package com.sclera.sclerago.domain.model

/**
 * The asset icons as the server serves them (`GET /v1/asset-icons`): each icon's SVG by name, and for the open
 * property which icon each of its types uses ([byFormat]: master format id → icon name, from its classification). The
 * server works a type's icon out from its names, so the app never decides one itself.
 */
data class AssetIcons(
    val svg: Map<String, String> = emptyMap(),
    val byFormat: Map<String, String> = emptyMap(),
) {
    /** The icon name for the type [masterFormatId] (null: not classified, or not known yet); the generic box otherwise. */
    fun nameFor(masterFormatId: String?): String = masterFormatId?.let(byFormat::get) ?: GENERIC_ICON

    /** The SVG of [name], else of the generic box; null until the icons have been downloaded once. */
    fun svgOf(name: String): String? = svg[name] ?: svg[GENERIC_ICON]
}

/** The icon the server gives anything it can't name (its `fallback`). */
const val GENERIC_ICON = "generic"
