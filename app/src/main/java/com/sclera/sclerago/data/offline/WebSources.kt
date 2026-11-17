package com.sclera.sclerago.data.offline

import com.sclera.sclerago.data.mapper.DbJson
import com.sclera.sclerago.data.sync.AdcDatabase
import java.net.URI
import java.net.URLDecoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

// The web pages an asset's values came from, kept as the server keeps them when AI suggestions are applied (its
// services/web_documents.py keep_sources, with services/manuals.py for which sites are the manufacturer's).

private const val MAX_URL = 1000
private const val MAX_TITLE = 300
private const val MAX_PUBLISHER = 255

/** Sites never taken as the manufacturer's (forums, resellers, manual-sharing sites...): the server's NOT_OFFICIAL. */
private val NOT_OFFICIAL = listOf(
    "reddit.com", "facebook.com", "fb.com", "instagram.com", "twitter.com", "x.com", "tiktok.com",
    "linkedin.com", "pinterest.com", "youtube.com", "youtu.be", "vimeo.com", "quora.com", "medium.com",
    "stackexchange.com", "stackoverflow.com", "discord.com", "telegram.org", "t.me", "whatsapp.com",
    "tumblr.com", "blogspot.com", "wordpress.com", "wikipedia.org", "wikimedia.org", "fandom.com",
    "amazon.com", "ebay.com", "alibaba.com", "aliexpress.com", "walmart.com", "etsy.com", "made-in-china.com",
    "manualslib.com", "manualzz.com", "manualsdir.com", "manua.ls", "manualowl.com", "manualsonline.com",
    "scribd.com", "slideshare.net", "issuu.com", "docplayer.net", "pdfcoffee.com", "studylib.net", "yumpu.com",
    "academia.edu", "dokumen.tips", "vdocuments.mx", "pdfdrive.com", "archive.org", "github.com",
    "google.com", "bing.com", "duckduckgo.com",
)

/** Second-level names under a country's domain ("trane.co.uk"). */
private val SECOND_LEVEL = setOf("co", "com", "org", "net", "ac", "gov", "edu", "ltd", "plc")

private val NOT_ALNUM = Regex("[^a-z0-9]")

/** The page the asset already keeps at [url]. */
private const val KEPT_PAGE = "SELECT id, field_ids FROM web_documents WHERE asset_id = ? AND url = ? AND %LIVE%"

/** The sites a model's manuals were found on (screened as the manufacturer's by the server). */
private const val MANUAL_SITES = """SELECT publisher FROM web_documents
    WHERE (asset_id IS NULL OR asset_id = '') AND maker_key = ? AND model_key = ? AND %LIVE%"""

private const val PAGE_INSERT = """INSERT INTO web_documents (id, asset_id, url, title, publisher, official, field_ids, file_status,
    created_by, version, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, 'link', ?, 1, ?, ?)"""

private const val PAGE_FIELDS = "UPDATE web_documents SET field_ids = ?, version = version + 1, updated_at = ? WHERE id = ?"

/** [raw] as a web page worth keeping (http or https, with a host, not too long), without its fragment; else null. */
internal fun webLink(raw: String?): String? {
    val url = raw?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_URL } ?: return null
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
    return url.substringBefore('#')
}

/** How a manufacturer or model is matched: lowercase letters and digits only. */
private fun norm(text: String?): String = NOT_ALNUM.replace((text ?: "").lowercase(), "")

/** The host of [url], lowercase, without a leading "www.". */
private fun hostOf(url: String): String? = runCatching { URI(url.trim()).host }.getOrNull()?.lowercase()?.removePrefix("www.")

private fun within(host: String, domain: String) = host == domain || host.endsWith(".$domain")

private fun isBlocked(host: String) = NOT_OFFICIAL.any { within(host, it) }

/** The domain a site is registered as: "trane.com" for "us.trane.com", "trane.co.uk" for "www.trane.co.uk". */
private fun siteDomain(host: String): String {
    val labels = host.split(".")
    val keep = if (labels.size >= 3 && labels[labels.size - 2] in SECOND_LEVEL && labels.last().length == 2) 3 else 2
    return labels.takeLast(keep).joinToString(".")
}

/** Whether the site's own name is the manufacturer's whole name squashed, or its first word (3+ letters). */
private fun namedAfter(host: String, manufacturer: String?): Boolean {
    val words = (manufacturer ?: "").split(Regex("\\s+")).filter { it.isNotEmpty() }
    val whole = norm(manufacturer)
    val first = words.firstOrNull()?.let(::norm).orEmpty()
    val name = NOT_ALNUM.replace(siteDomain(host).substringBefore('.'), "")
    return name.isNotEmpty() && (name == whole || (first.length >= 3 && name == first))
}

/** Whether [host] is the manufacturer's: a site its manuals were found on, or one named after it; never a blocked one. */
private fun official(host: String, manufacturer: String?, manualSites: List<String>): Boolean {
    if (host.isEmpty() || isBlocked(host)) return false
    return manualSites.filter { it.isNotEmpty() && !isBlocked(it) }.any { within(host, it) } || namedAfter(host, manufacturer)
}

/** A name for the page: its file name ("RTAC-PRC012-EN.pdf"), else the site and path. */
private fun pageTitle(url: String): String {
    val uri = runCatching { URI(url) }.getOrNull()
    val path = uri?.rawPath.orEmpty()
    val name = runCatching { URLDecoder.decode(path.substringAfterLast('/'), "UTF-8") }.getOrDefault("").trim()
    if (name.isNotEmpty() && '.' in name) return name.take(MAX_TITLE)
    return "${hostOf(url) ?: uri?.authority.orEmpty()}${path.trimEnd('/')}".take(MAX_TITLE)
}

/**
 * Keeps the pages in [pages] (field key -> page) with asset [assetId], adding the fields to a page it already keeps,
 * as the server's keep_sources. The file isn't downloaded from the phone, so a new page is kept as a link.
 */
internal fun AdcDatabase.keepSources(
    assetId: String,
    pages: Map<String, String>,
    manufacturer: String?,
    model: String?,
    userId: String?,
    newId: () -> String,
    now: String,
) {
    if (pages.isEmpty() || !hasTable("web_documents")) return
    val live = notRemoved("web_documents")
    val fieldIdOf = query(FIELD_NAMES.replace("%LIVE%", notRemoved("asset_fields")), listOf(assetId))
        .associate { it.string("key").orEmpty() to it.string("id").orEmpty() }
    val byPage = pages.entries.groupBy({ it.value }, { fieldIdOf[it.key] }).mapValues { (_, ids) -> ids.filterNotNull() }
    val manualSites = if (norm(model).isEmpty()) emptyList()
    else query(MANUAL_SITES.replace("%LIVE%", live), listOf(norm(manufacturer), norm(model))).mapNotNull { it.string("publisher") }
    byPage.forEach { (url, fieldIds) ->
        val kept = queryOne(KEPT_PAGE.replace("%LIVE%", live), listOf(assetId, url))
        if (kept == null) {
            val host = hostOf(url).orEmpty()
            execute(
                PAGE_INSERT,
                listOf(
                    newId(), assetId, url, pageTitle(url), host.take(MAX_PUBLISHER), if (official(host, manufacturer, manualSites)) 1 else 0,
                    JsonArray(fieldIds.distinct().map(::JsonPrimitive)).toString(), userId, now, now,
                ),
                setOf("web_documents"),
            )
        } else {
            val old = DbJson.stringList(kept.string("field_ids"))
            val merged = (old + fieldIds).distinct()
            if (merged != old) {
                execute(PAGE_FIELDS, listOf(JsonArray(merged.map(::JsonPrimitive)).toString(), now, kept.string("id")), setOf("web_documents"))
            }
        }
    }
}
