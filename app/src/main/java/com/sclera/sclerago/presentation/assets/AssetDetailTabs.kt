package com.sclera.sclerago.presentation.assets

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.ActivityKind
import com.sclera.sclerago.domain.model.AssetActivity
import com.sclera.sclerago.domain.model.AssetDocument
import com.sclera.sclerago.domain.model.DocumentFileStatus
import com.sclera.sclerago.domain.model.DocumentSource
import com.sclera.sclerago.domain.model.ManualKind
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.PhotoKind
import com.sclera.sclerago.domain.model.PhotoRule
import com.sclera.sclerago.domain.model.formatDuration
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.common.formatDate
import com.sclera.sclerago.presentation.common.formatDateTimeSeconds
import com.sclera.sclerago.presentation.common.is24Hour
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.presentation.components.IconMenu
import com.sclera.sclerago.presentation.components.initialsOf
import com.sclera.sclerago.presentation.components.LocalReadOnly
import com.sclera.sclerago.presentation.components.MenuAct
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.PlainField
import com.sclera.sclerago.presentation.components.RoundButton
import com.sclera.sclerago.presentation.components.bottomBorder
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Ico
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.ScleraFonts
import com.sclera.sclerago.presentation.theme.Txt

/** The tabs an asset's details can show, in the web app's order (Info, Validation, Documents, Media, Comments, History). */
enum class DetailTabKind(@StringRes val label: Int, val icon: Ico, val tag: String) {
    INFO(R.string.detail_tab_info, Icons.Info, DetailTags.TAB_INFO),
    VALIDATION(R.string.detail_tab_validation, Icons.ShieldCheck, DetailTags.TAB_VALIDATION),
    DOCUMENTS(R.string.detail_tab_documents, Icons.FileDoc, "detail-tab-documents"),
    MEDIA(R.string.detail_tab_media, Icons.Gallery, "detail-tab-media"),
    COMMENTS(R.string.detail_tab_comments, Icons.TextLines, "detail-tab-comments"),
    HISTORY(R.string.detail_tab_history, Icons.Clock, "detail-tab-history"),
}

/** One tab as shown: [fails] is the Validation tab's failed checks (0 = all passed). */
data class DetailTabItem(val kind: DetailTabKind, val fails: Int? = null)

/**
 * The tabs [state] shows, worked out from the asset as the web's asset panel does: Info, Validation,
 * Documents, Media, Comments and History (the web's tabs). A new asset isn't saved yet, so it has no validation,
 * comments or history: Info, Documents and Media (the documents, photos, videos and files picked for it).
 */
fun detailTabs(state: AssetDetailState): List<DetailTabItem> {
    if (state.asset == null) return emptyList()
    return buildList {
        add(DetailTabItem(DetailTabKind.INFO))
        // The red count badge is the steps needing attention (the Info banner's "N of M"); none while there are none.
        if (!state.isNew) add(DetailTabItem(DetailTabKind.VALIDATION, state.validation?.problems?.size?.takeIf { it > 0 }))
        // A new asset has Documents too: the files picked for it, added with it.
        add(DetailTabItem(DetailTabKind.DOCUMENTS))
        add(DetailTabItem(DetailTabKind.MEDIA))
        if (!state.isNew) {
            add(DetailTabItem(DetailTabKind.COMMENTS))
            add(DetailTabItem(DetailTabKind.HISTORY))
        }
    }
}

/** The open tab: [chosen] while it is one of [tabs], else the first (Info). */
fun shownTab(tabs: List<DetailTabItem>, chosen: DetailTabKind): DetailTabKind =
    chosen.takeIf { k -> tabs.any { it.kind == k } } ?: tabs.firstOrNull()?.kind ?: DetailTabKind.INFO

/** The strip of tabs, each with its icon above its name; they share the width on phones and tablets alike. */
@Composable
fun DetailTabBar(tabs: List<DetailTabItem>, selected: DetailTabKind, onSelect: (DetailTabKind) -> Unit) {
    // A few tabs (a new asset's Info, Documents and Media) have room for the icon beside the label, in a lower bar.
    val compact = tabs.size <= COMPACT_TABS
    Row(Modifier.fillMaxWidth().height(if (compact) 44.dp else 56.dp).background(C.Strip).bottomBorder(C.Border)) {
        tabs.forEach { t ->
            DetailTab(
                stringResource(t.kind.label), t.kind.icon, t.kind == selected, t.fails,
                Modifier.weight(1f).testTag(t.kind.tag), compact = compact,
            ) { onSelect(t.kind) }
        }
    }
}

/** Up to this many tabs, each shows its icon beside its label. */
private const val COMPACT_TABS = 3

/** A document's type as the short tag on its card: the file's extension ("XLSX"), else PDF, XLS, DOC or TXT by its kind. */
private fun AssetDocument.typeTag(): String =
    title.substringAfterLast('.', "").uppercase().takeIf { (source == DocumentSource.SOURCE_FILE || source == DocumentSource.ATTACHMENT) && it.length in 2..4 }
        ?: when (fileKind) {
            "pdf" -> "PDF"
            "spreadsheet" -> "XLS"
            "text" -> "TXT"
            else -> "DOC"
        }

/** The uploaded file's kind, as the tag under its name: Spreadsheet, PDF, Word document, Text, Video or Document. */
private fun fileKindRes(kind: String?) = when (kind) {
    "spreadsheet" -> R.string.doc_type_spreadsheet
    "pdf" -> R.string.doc_type_pdf
    "word" -> R.string.doc_type_word
    "text" -> R.string.doc_type_text
    "video" -> R.string.doc_type_video
    else -> R.string.doc_kind_other
}

/**
 * The Documents tab: a card per document — the file the asset was read from (or uploaded here), the web pages its applied
 * AI suggestions came from, and its model's manuals from the manufacturer's site. Each card shows its type, its name, a
 * tag (the file's kind, or the document's category) and its details: who uploaded the file, when, its size and type; or
 * what a page was used for (a manual's model), its site and whether the PDF is kept. The eye opens it; the bin deletes it
 * when the user may. Upload document is the floating + ([UploadDocumentFab]).
 */
@Composable
fun DocumentsTab(documents: List<AssetDocument>, locked: Boolean, onRemove: (AssetDocument) -> Unit) {
    if (documents.isEmpty()) {
        EmptyTab(stringResource(R.string.doc_none))
        return
    }
    documents.forEach { d -> DocumentCard(d, canRemove = d.removable && !locked, onRemove = { onRemove(d) }) }
}

/** Opens document [d]: its kept file in an app that reads it, else its page in the browser; null when it has neither. */
@Composable
internal fun documentOpener(d: AssetDocument): (() -> Unit)? {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val chooser = stringResource(R.string.doc_open_with)
    return when {
        d.fileUri != null -> ({ openFile(context, d.fileUri, d.contentType, chooser) })
        !d.url.isNullOrBlank() -> ({ runCatching { uriHandler.openUri(d.url) }; Unit })
        else -> null
    }
}

/**
 * One document as a card: its icon, its name, a tag (what it is), what it says (the fields a page was used for, a
 * manual's model, who added a file and when), then where it is from (a web document's site and whether its PDF is kept,
 * a file's size and type). Open sits at the top end (an arrow for a page only linked, an eye for a kept file), the bin
 * at the bottom end.
 */
@Composable
private fun DocumentCard(d: AssetDocument, canRemove: Boolean, onRemove: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val open = documentOpener(d)
    val web = d.source == DocumentSource.WEB_PAGE || d.source == DocumentSource.MANUAL
    val tag = when (d.source) {
        DocumentSource.SOURCE_FILE -> stringResource(fileKindRes(d.fileKind))
        DocumentSource.WEB_PAGE -> stringResource(if (d.official) R.string.doc_ai_source_official else R.string.doc_ai_source)
        DocumentSource.MANUAL -> stringResource(R.string.doc_manufacturer_kind, stringResource(d.manualKind.labelRes()))
        DocumentSource.ATTACHMENT -> stringResource(R.string.doc_attachment)
    }
    val about: String? = when (d.source) {
        DocumentSource.WEB_PAGE -> d.usedFor.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.scrt_doc_used_for_list, it.joinToString(", ")) }
        DocumentSource.MANUAL -> d.model?.let { stringResource(R.string.scrt_doc_model_line, it) }
        DocumentSource.SOURCE_FILE ->
            listOfNotNull(d.uploaderName ?: stringResource(R.string.doc_uploader_unknown), d.uploadedAt?.let { formatDate(it, locale) }).joinToString(" · ")
        DocumentSource.ATTACHMENT -> d.uploadedAt?.let { formatDate(it, locale) }
    }
    val type = d.contentType?.substringAfter('/')?.uppercase(locale)
    val fileLine = listOfNotNull(d.sizeBytes?.let { sizeText(it) }, type).joinToString(" · ").ifEmpty { null }
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).testTag("doc-${d.id}").clip(r(16)).background(C.Surface)
            .border(1.dp, C.Border, r(16)).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(48.dp).background(C.YTintBg, r(12)), contentAlignment = Alignment.Center) {
            if (d.source == DocumentSource.WEB_PAGE) {
                Box(Modifier.size(28.dp).background(C.Yellow, CircleShape), contentAlignment = Alignment.Center) {
                    LineIcon(Icons.Globe, 16.dp, C.OnYellow, 2f)
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LineIcon(if (d.fileKind == "spreadsheet") Icons.Table else Icons.FileDoc, 18.dp, C.Yellow, 2f)
                    Txt(d.typeTag(), 8, 800, C.Yellow, spacing = 0.3f)
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Txt(d.title, 15, 600, maxLines = 2)
            Row(
                Modifier.background(C.YTintBg, Capsule).padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (d.source == DocumentSource.MANUAL) LineIcon(Icons.FileDoc, 11.dp, C.Yellow, 2.2f)
                Txt(tag, 11, 700, C.Yellow, maxLines = 1)
            }
            about?.let { Txt(it, 12, 400, C.Muted, maxLines = Int.MAX_VALUE) }
            if (web) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    (d.publisher ?: d.url)?.let { Txt(it, 12, 700, C.Yellow, Modifier.weight(1f, fill = false), maxLines = 1) }
                    Txt(" · " + stringResource(d.fileStatus.labelRes()), 12, 500, C.Muted, maxLines = 1)
                }
            } else {
                fileLine?.let { Txt(it, 12, 500, C.Muted, maxLines = 1) }
            }
        }
        Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.CenterHorizontally) {
            if (open != null) {
                val label = stringResource(R.string.doc_open_cd, d.title)
                RoundButton(36.dp, C.Raised, open, Modifier.semantics { contentDescription = label }) {
                    LineIcon(if (d.fileUri != null) Icons.Eye else Icons.ArrowUpRight, 16.dp, C.Yellow, 2.2f)
                }
            } else {
                Spacer(Modifier.size(36.dp))
            }
            if (canRemove) {
                val label = stringResource(R.string.doc_remove_cd, d.title)
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onRemove).semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) { LineIcon(Icons.Trash, 18.dp, C.Red, 2.2f) }
            }
        }
    }
}

/** "Sorting photos…" with a small spinner, read out as one line. */
@Composable
private fun SortingNote(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().testTag("media-sorting").background(C.YTintBg, r(12)).padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(Modifier.size(16.dp), color = C.Yellow, strokeWidth = 2.dp)
        Txt(stringResource(R.string.media_sorting), 13, 600, C.Yellow, maxLines = Int.MAX_VALUE)
    }
}

/** Opens the kept file at [uri] in another app the user picks (a PDF or spreadsheet viewer), allowed to read it only. */
private fun openFile(context: Context, uri: String, type: String?, chooser: String) {
    val view = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), type ?: context.contentResolver.getType(Uri.parse(uri)))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(view, chooser)) }
}

internal fun ManualKind?.labelRes() = when (this) {
    ManualKind.MANUAL -> R.string.doc_kind_manual
    ManualKind.INSTALLATION -> R.string.doc_kind_installation
    ManualKind.DATASHEET -> R.string.doc_kind_datasheet
    ManualKind.PARTS -> R.string.doc_kind_parts
    ManualKind.OTHER, null -> R.string.doc_kind_other
}

private fun DocumentFileStatus.labelRes() = when (this) {
    DocumentFileStatus.PENDING -> R.string.doc_status_pending
    DocumentFileStatus.SAVED -> R.string.doc_status_saved
    DocumentFileStatus.LINK -> R.string.doc_status_link
}

/** A photo kind's name and what it shows, as the web words them. */
internal fun PhotoKind.nameRes() = when (this) {
    PhotoKind.NAMEPLATE -> R.string.media_kind_nameplate
    PhotoKind.ASSET -> R.string.media_kind_asset
    PhotoKind.LABEL_ID -> R.string.media_kind_label
}

private fun PhotoKind.hintRes() = when (this) {
    PhotoKind.NAMEPLATE -> R.string.media_hint_nameplate
    PhotoKind.ASSET -> R.string.media_hint_asset
    PhotoKind.LABEL_ID -> R.string.media_hint_label
}

/** A file's size as the web shows it: "2.4 MB", or "180 KB" (at least 1 KB). */
@Composable
private fun sizeText(bytes: Long): String =
    if (bytes >= 1024 * 1024) stringResource(R.string.scrt_size_mb, bytes / (1024f * 1024f))
    else stringResource(R.string.scrt_size_kb, (bytes / 1024).coerceAtLeast(1))

/**
 * The Media tab, edge to edge like Info: a card per kind of photo the property's photo rules count (how many it has against
 * what is needed or allowed, and its photos), one for the other photos, then "Videos", then "Voice memos and files". Tapping
 * a photo or file opens it. Adding is done with the floating buttons ([MediaFabs]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaTab(
    media: List<Media>,
    rules: List<PhotoRule>,
    onOpen: (Int) -> Unit,
    sorting: Boolean = false,
    /** The file the asset was read from: it shows in a section of its own under Videos. */
    sourceFile: AssetDocument? = null,
    canRemoveSource: Boolean = false,
    onRemoveSource: (AssetDocument) -> Unit = {},
    /** A new asset's photo moved to another kind's card (null: Other photos); null when photos can't be moved. */
    onMove: ((Media, PhotoKind?) -> Unit)? = null,
) {
    // Photos just added are read on the device and sorted into their kinds before they show.
    if (sorting) SortingNote(Modifier.padding(horizontal = 12.dp))
    val indexed = media.withIndex().toList()
    val photos = indexed.filter { (_, m) -> !m.video && !m.isDoc && !m.audio }
    val shown = rules.filter { r -> r.max > 0 || photos.any { it.value.category == r.kind } }
    // A new asset's photos can be dragged between the cards (tap and hold one); a kind at its most takes no more.
    val scope = rememberCoroutineScope()
    val drag = remember(onMove != null) { onMove?.let { PhotoDrag(scope) } }
    val full = { key: String -> shown.firstOrNull { it.kind.db == key }?.let { r -> photos.count { it.value.category == r.kind } >= r.max } == true }
    drag?.accept = { m, key ->
        if (full(key)) false else {
            onMove?.invoke(m, PhotoKind.of(key))
            true
        }
    }
    val lookOf = { key: String ->
        when {
            drag?.media == null || drag.from == key || drag.over != key -> DropLook.NONE
            full(key) -> DropLook.FULL
            else -> DropLook.TAKES
        }
    }
    // Every card a photo can go to, named, for moving one without dragging (TalkBack's actions).
    val cardNames = shown.map { it.kind.db to stringResource(it.kind.nameRes()) } + (OTHER_PHOTOS_KEY to stringResource(R.string.media_other))
    if (drag != null && photos.isNotEmpty()) {
        Txt(stringResource(R.string.media_drag_hint), 11, 500, C.Muted, Modifier.padding(horizontal = 12.dp), maxLines = 2)
    }
    shown.forEach { r ->
        val mine = photos.filter { it.value.category == r.kind }
        val count = mine.size
        val chip = if (count < r.min) stringResource(R.string.media_needed, count, r.min) else stringResource(R.string.media_allowed, count, r.max)
        PhotoCard(
            title = stringResource(r.kind.nameRes()), hint = stringResource(r.kind.hintRes()), chip = chip, chipWarn = count < r.min,
            items = mine, onOpen = onOpen, modifier = Modifier.testTag("media-card-${r.kind.db}"),
            drag = drag, key = r.kind.db, look = lookOf(r.kind.db), moveTo = cardNames.filter { it.first != r.kind.db && !full(it.first) },
        )
    }
    PhotoCard(
        title = stringResource(R.string.media_other), hint = stringResource(R.string.media_other_hint), chip = null, chipWarn = false,
        items = photos.filter { it.value.category == null && it.value.fieldId == null },
        onOpen = onOpen, modifier = Modifier.testTag("media-card-other"),
        drag = drag, key = OTHER_PHOTOS_KEY, look = lookOf(OTHER_PHOTOS_KEY), moveTo = cardNames.filter { it.first != OTHER_PHOTOS_KEY && !full(it.first) },
    )
    FileList(stringResource(R.string.media_videos), indexed.filter { (_, m) -> m.video }, onOpen)
    SourceDocumentSection(sourceFile, canRemove = sourceFile?.removable == true && canRemoveSource, onRemove = onRemoveSource)
    FileList(stringResource(R.string.media_memos_files), indexed.filter { (_, m) -> !m.video && (m.isDoc || m.audio) }, onOpen)
}

/**
 * A titled card of videos, voice memos or files, looking like the photo cards: each with its kind, name, size or
 * length; tapping one opens it.
 */
@Composable
private fun FileList(title: String, files: List<IndexedValue<Media>>, onOpen: (Int) -> Unit) {
    Column(
        // A card inset 12dp from the sides.
        Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(r(14)).background(C.Surface).border(1.dp, C.Border, r(14)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Txt(title, 16, 600, maxLines = 2)
        if (files.isEmpty()) Txt(stringResource(R.string.media_none), 12, 400, C.Muted, maxLines = Int.MAX_VALUE)
        else Column(Modifier.fillMaxWidth()) { FileRows(files, onOpen) }
    }
}

/**
 * The file the asset was read from, as a section like Videos and the other files: its type, name, size and when it was
 * added, opening on a tap; the bin takes it off (where allowed). "None yet." without one.
 */
@Composable
private fun SourceDocumentSection(doc: AssetDocument?, canRemove: Boolean, onRemove: (AssetDocument) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    Column(
        Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(r(14)).background(C.Surface).border(1.dp, C.Border, r(14)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Txt(stringResource(R.string.media_source_document), 16, 600, maxLines = 2)
        if (doc == null) {
            Txt(stringResource(R.string.media_none), 12, 400, C.Muted, maxLines = Int.MAX_VALUE)
            return@Column
        }
        val open = documentOpener(doc)
        Row(
            Modifier.fillMaxWidth().testTag("media-source-${doc.id}").then(if (open != null) Modifier.clickable(onClick = open) else Modifier)
                .heightIn(min = 56.dp).padding(horizontal = 2.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Txt(doc.typeTag(), 10, 700, C.Soft, Modifier.background(C.Btn, Capsule).padding(horizontal = 8.dp, vertical = 3.dp))
            Column(Modifier.weight(1f)) {
                Txt(doc.title, 13, 700, maxLines = 1)
                val meta = listOfNotNull(doc.sizeBytes?.let { sizeText(it) }, doc.uploadedAt?.let { formatDate(it, locale) }).joinToString(" · ")
                if (meta.isNotEmpty()) Txt(meta, 11, 400, C.Muted)
            }
            if (open != null) LineIcon(Icons.Eye, 15.dp, C.Yellow, 2.2f)
            if (canRemove) {
                val label = stringResource(R.string.doc_remove_cd, doc.title)
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button) { onRemove(doc) }.semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) { LineIcon(Icons.Trash, 16.dp, C.Red, 2.2f) }
            }
        }
    }
}

/** The rows of a [FileList], split by lines. */
@Composable
private fun FileRows(files: List<IndexedValue<Media>>, onOpen: (Int) -> Unit) {
        files.forEachIndexed { n, (i, m) ->
            Row(
                Modifier.fillMaxWidth().testTag(PhotoTags.tile(i)).then(if (n > 0) Modifier.topBorder(C.Border) else Modifier)
                    .clickable { onOpen(i) }.heightIn(min = 56.dp).padding(horizontal = 2.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val tag = when {
                    m.video -> R.string.media_tag_video
                    m.audio -> R.string.media_tag_audio
                    else -> R.string.media_tag_file
                }
                Txt(stringResource(tag), 10, 700, C.Soft, Modifier.background(C.Btn, Capsule).padding(horizontal = 8.dp, vertical = 3.dp))
                Column(Modifier.weight(1f)) {
                    Txt(m.fileName ?: stringResource(tag), 13, 700, maxLines = 1)
                    val length = lengthMs(m).takeIf { (m.video || m.audio) && it > 0 }?.let(::formatDuration)
                    val meta = listOfNotNull(m.sizeBytes?.let { sizeText(it) }, length).joinToString(" · ")
                    if (meta.isNotEmpty()) Txt(meta, 11, 400, C.Muted)
                }
                LineIcon(if (m.video || m.audio) Icons.Play else Icons.Download, 15.dp, C.Yellow, 2.2f)
            }
        }
}

/**
 * One photo card: its name with a count chip, what it shows, and its photos (or "None yet."). With [drag] (a new asset)
 * its photos can be picked up and dragged to another card ([moveTo]: the cards that can take one, by key and name), and it
 * lights up ([look]) while one is dragged over it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhotoCard(
    title: String,
    hint: String,
    chip: String?,
    chipWarn: Boolean,
    items: List<IndexedValue<Media>>,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier,
    drag: PhotoDrag? = null,
    key: String = "",
    look: DropLook = DropLook.NONE,
    moveTo: List<Pair<String, String>> = emptyList(),
) {
    val (bg, edge) = dropColors(look)
    val moveLabel = stringResource(R.string.media_move_to)
    Column(
        // Full width, like the Info sections; above the other cards while one of its photos is being dragged.
        modifier.zIndex(if (drag != null && drag.from == key) 1f else 0f)
            .then(if (drag != null) Modifier.photoDropTarget(drag, key) else Modifier)
            .padding(horizontal = 12.dp).fillMaxWidth().clip(r(14)).background(bg.value).border(1.dp, C.Border, r(14)).border(2.dp, edge.value, r(14))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Txt(title, 16, 600)
                    chip?.let {
                        Txt(
                            it, 12, 600, if (chipWarn) C.Orange else C.Muted,
                            Modifier.background(if (chipWarn) C.WarnRow else C.Raised, Capsule).padding(horizontal = 9.dp, vertical = 4.dp),
                        )
                    }
                }
                Txt(hint, 13, 400, C.Muted, maxLines = Int.MAX_VALUE)
            }
        }
        if (items.isEmpty()) Txt(stringResource(R.string.media_none), 12, 400, C.Muted, maxLines = Int.MAX_VALUE)
        else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { (i, m) ->
                val movable = if (drag != null) {
                    Modifier.draggablePhoto(drag, m, key).semantics {
                        customActions = moveTo.map { (to, name) -> CustomAccessibilityAction(moveLabel.format(name)) { drag.accept(m, to) } }
                    }
                } else Modifier
                MediaTile(m, { onOpen(i) }, Modifier.size(76.dp).then(movable).testTag(PhotoTags.tile(i)))
            }
        }
    }
}

/**
 * The Comments tab's list: one full-width row per comment, newest first, with its text, who wrote it and when
 * ("· edited" once changed), and a ⋮ menu in the top end corner with Edit (its author) and Delete (its author, or a
 * validator), as the web allows. Edit turns the row into a box with Save and Cancel; Delete asks first ([onDelete] shows
 * the question). [readOnly] (a signed-off asset, as the web's validated list) leaves the menu out.
 */
@Composable
fun CommentList(
    activity: List<AssetActivity>,
    readOnly: Boolean,
    onSave: (comment: AssetActivity, text: String, onSaved: () -> Unit) -> Unit,
    onDelete: (AssetActivity) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val comments = activity.filter { it.kind == ActivityKind.COMMENT }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var editText by rememberSaveable { mutableStateOf("") }
    val someone = stringResource(R.string.detail_comment_someone)
    // None yet: "No comments yet" in the middle of the tab, as the web's.
    if (comments.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(vertical = 120.dp).testTag(DetailTags.TAB_EMPTY), contentAlignment = Alignment.Center) {
            Txt(stringResource(R.string.detail_no_comments), 13, 700, C.Muted, maxLines = Int.MAX_VALUE, align = TextAlign.Center)
        }
        return
    }
    val editLabel = stringResource(R.string.detail_comment_edit)
    val deleteLabel = stringResource(R.string.detail_comment_delete)
    // One card per comment, 10dp apart, with the author's avatar under the text.
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        comments.forEach { e ->
            val menu = buildList {
                if (!readOnly && e.canEdit) add(MenuAct(Icons.Pencil, editLabel) { editText = e.text; editingId = e.id })
                if (!readOnly && e.canDelete) add(MenuAct(Icons.Trash, deleteLabel, danger = true) { onDelete(e) })
            }
            Row(
                Modifier.fillMaxWidth().testTag("detail-activity-${e.id}").clip(r(14)).background(C.Surface).border(1.dp, C.Border, r(14))
                    .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f).padding(top = 4.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (editingId == e.id) {
                        CompositionLocalProvider(LocalReadOnly provides false) {
                            PlainField(
                                editText, { editText = it }, stringResource(R.string.detail_comment_hint),
                                Modifier.fillMaxWidth().testTag("detail-comment-edit-${e.id}").clip(r(10)).background(C.Bg)
                                    .border(1.dp, C.Border2, r(10)).padding(10.dp),
                                size = 14, singleLine = false, minLines = 2,
                            )
                        }
                    } else {
                        Txt(e.text, 14, 400, maxLines = Int.MAX_VALUE)
                    }
                    val author = e.authorName ?: someone
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(30.dp).background(C.YTintBg, CircleShape), contentAlignment = Alignment.Center) { Txt(initialsOf(author).take(1), 12, 700, C.Yellow) }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Txt(author, 13, 700, maxLines = 1)
                            e.createdAt?.let {
                                    val time = formatDateTimeSeconds(it, locale, use24Hour = is24Hour())
                                    Txt(if (e.edited) stringResource(R.string.scrt_comment_time_edited, time) else time, 11, 400, C.Muted)
                            }
                        }
                    }
                }
                when {
                    // While editing: Save and Cancel where the ⋮ was.
                    editingId == e.id -> {
                        CommentIconButton(Icons.Check, stringResource(R.string.detail_comment_save), C.Yellow, "detail-comment-save-${e.id}") {
                            if (editText.isNotBlank()) onSave(e, editText) { editingId = null }
                        }
                        CommentIconButton(Icons.Close, stringResource(R.string.action_cancel), C.Muted, "detail-comment-cancel-${e.id}") { editingId = null }
                    }
                    // Edit and Delete sit in a ⋮ menu in the top end corner, only when the user may do one of them.
                    menu.isNotEmpty() -> Box {
                        var open by remember { mutableStateOf(false) }
                        CommentIconButton(Icons.Dots, stringResource(R.string.detail_comment_options), C.Muted, "detail-comment-menu-${e.id}") { open = true }
                        IconMenu(open, { open = false }, menu)
                    }
                }
            }
        }
    }
}

/** An icon-only button on a comment (48dp to touch, [description] read out). */
@Composable
private fun CommentIconButton(icon: Ico, description: String, tint: Color, tag: String, onClick: () -> Unit) {
    RoundButton(48.dp, Color.Transparent, onClick, Modifier.testTag(tag).semantics { contentDescription = description }) {
        LineIcon(icon, 18.dp, tint, 2.2f)
    }
}

/**
 * The History tab, as the web's timeline: a dot and a line down the start side, each entry's sentence ("Asset updated")
 * with the fields it changed (old → new), then who and when; "No history available" when there is none.
 */
@Composable
fun HistoryList(activity: List<AssetActivity>) {
    val entries = activity.filter { it.kind == ActivityKind.HISTORY }
    if (entries.isEmpty()) {
        EmptyTab(stringResource(R.string.detail_no_history))
        return
    }
    val locale = LocalConfiguration.current.locales[0]
    val empty = stringResource(R.string.detail_change_empty)
    Column(Modifier.fillMaxWidth().padding(start = 6.dp)) {
        entries.forEachIndexed { i, e ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).testTag("detail-activity-${e.id}")) {
                Box(Modifier.width(14.dp).fillMaxHeight()) {
                    if (i < entries.lastIndex) Box(Modifier.align(Alignment.TopCenter).padding(top = 6.dp).width(1.dp).fillMaxHeight().background(C.Border))
                    Box(Modifier.align(Alignment.TopCenter).padding(top = 5.dp).size(9.dp).background(C.Yellow, CircleShape).border(2.dp, C.YTintBg, CircleShape))
                }
                Column(Modifier.weight(1f).padding(start = 10.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // History lines are written by the server and the app; their sentence is kept as recorded.
                    Txt(e.text.ifEmpty { e.action.orEmpty() }, 16, 400, maxLines = Int.MAX_VALUE)
                    // As the web: "Building: empty → Harbour Point Tower", the old value struck through.
                    e.fieldChanges.forEach { c ->
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = C.Text)) { append(stringResource(R.string.scrt_history_field_label, c.field)) }
                                withStyle(SpanStyle(color = C.Muted, textDecoration = TextDecoration.LineThrough)) { append(c.from ?: empty) }
                                append(" → ")
                                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = C.Text)) { append(c.to ?: empty) }
                            },
                            color = C.Soft, fontSize = 12.sp, fontFamily = ScleraFonts.Inter, maxLines = Int.MAX_VALUE,
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Who made the change, by email.
                        Txt(e.authorName.orEmpty(), 13, 400, C.Muted, Modifier.weight(1f), maxLines = 1)
                        e.createdAt?.let { Txt(formatDateTimeSeconds(it, locale, use24Hour = is24Hour()), 13, 400, C.Muted) }
                    }
                }
            }
        }
    }
}

/**
 * The box in the Add comment sheet where a comment is written: the text, what is wrong with it ([error]), and Post (off while it is
 * blank or [posting]). It stays usable on a submitted asset: the server takes comments on any asset.
 */
@Composable
fun CommentBox(draft: String, error: UiText?, posting: Boolean, onChange: (String) -> Unit, onPost: () -> Unit, modifier: Modifier = Modifier) {
    val canPost = draft.isNotBlank() && !posting
    CompositionLocalProvider(LocalReadOnly provides false) {
        // As the web: the box, then a small dark Add on the end side under it (dimmed until something is written).
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PlainField(
                draft, onChange, stringResource(R.string.detail_comment_hint),
                Modifier.fillMaxWidth().testTag(DetailTags.COMMENT_FIELD).clip(r(10)).background(C.Bg)
                    .border(1.dp, if (error != null) C.Orange else C.Border2, r(10)).padding(10.dp),
                size = 14, singleLine = false, minLines = 3, readOnly = posting,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { error?.let { Txt(it.asString(), 12, 600, C.Orange, maxLines = Int.MAX_VALUE) } }
                Pill(
                    stringResource(R.string.action_add), { if (canPost) onPost() },
                    Modifier.testTag(DetailTags.COMMENT_POST).alpha(if (canPost) 1f else 0.45f),
                    bg = C.Btn, fg = C.Text, height = 36.dp, hPad = 16.dp, size = 12, weight = 700,
                )
            }
        }
    }
}

/** A tab with nothing to list yet. */
@Composable
private fun EmptyTab(text: String) {
    Box(
        Modifier.fillMaxWidth().testTag(DetailTags.TAB_EMPTY).clip(r(12)).background(C.Surface).border(1.dp, C.Border, r(12))
            .padding(horizontal = 12.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) { Txt(text, 13, 400, C.Muted, maxLines = Int.MAX_VALUE, align = TextAlign.Center) }
}

