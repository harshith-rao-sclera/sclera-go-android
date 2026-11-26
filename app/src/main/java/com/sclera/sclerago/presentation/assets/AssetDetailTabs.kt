package com.sclera.sclerago.presentation.assets

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.sclera.sclerago.presentation.components.pressClickable
import com.sclera.sclerago.presentation.components.PRESSED_CARD
import com.sclera.sclerago.presentation.components.SkeletonList
import androidx.compose.material3.CircularProgressIndicator
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.sclera.sclerago.presentation.components.SkeletonBlock
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.AssetDocument
import com.sclera.sclerago.domain.model.DocumentFileStatus
import com.sclera.sclerago.domain.model.DocumentSource
import com.sclera.sclerago.domain.model.ManualKind
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.PhotoKind
import com.sclera.sclerago.domain.model.FittedPhotos
import com.sclera.sclerago.domain.model.MAX_PHOTOS_PER_ASSET
import com.sclera.sclerago.domain.model.PhotoRefusal
import com.sclera.sclerago.domain.model.PhotoRule
import com.sclera.sclerago.domain.model.formatDuration
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.formatDate
import com.sclera.sclerago.presentation.components.AccessGate
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.presentation.components.IconMenu
import com.sclera.sclerago.presentation.components.bottomBorder
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Ico
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** The tabs an asset's details can show, in the web app's order (Info, Validation, Documents, Media, Comments, History). */
enum class DetailTabKind(@StringRes val label: Int, val icon: Ico, val tag: String) {
    INFO(R.string.detail_tab_info, Icons.Info, DetailTags.TAB_INFO),
    // The same icon as the side menu's Validation page.
    VALIDATION(R.string.detail_tab_validation, Icons.ValidationChecks, DetailTags.TAB_VALIDATION),
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
 * The Documents tab: a card per document — the ones added to the asset (kept on the device and synced like its photos),
 * then from the server the web pages its applied AI suggestions came from and its model's manuals from the manufacturer's site.
 * Each card shows its type, its name, a tag (the file's kind, or the document's category) and its details: when a file
 * was added, its size and type; or what a page was used for (a manual's model), its site and whether the PDF is kept. The
 * eye fetches the file and opens it ([onOpen]); the bin deletes it when the user may. Upload document is the floating +
 * ([UploadDocumentFab]). Documents are the server's only (not synced): offline none show, and none can be changed.
 */
@Composable
fun DocumentsTab(
    documents: List<AssetDocument>,
    ui: DocumentsUi,
    /** Nobody removes one now (a signed-off asset). */
    signedOff: Boolean,
    onOpen: (AssetDocument) -> Unit,
    onRemove: (AssetDocument) -> Unit,
    searching: Boolean = false,
    /** Why the signed-in user may not change the asset ([lockReason]) or remove its documents ([collectReason]); null: they may. */
    lockReason: UiText? = null,
    collectReason: UiText? = null,
) {
    val waiting = searching || ui.loading || ui.uploading
    if (documents.isEmpty() && !waiting) {
        EmptyTab(stringResource(if (ui.failed) R.string.doc_load_failed else R.string.doc_none))
        return
    }
    documents.forEach { d ->
        // The file the asset was read from and a model's manual (every asset of that model has it) have no bin; any
        // other the user may not remove keeps its bin, greyed out, saying why.
        val why = lockReason ?: collectReason.takeUnless { d.removable }
        val fixed = d.source == DocumentSource.MANUAL || d.source == DocumentSource.SOURCE_FILE
        val removable = !fixed && !signedOff && !ui.offline && (d.removable || why != null)
        DocumentCard(d, canRemove = removable, opening = ui.opening == d.id, onOpen = onOpen, onRemove = { onRemove(d) }, removeBlocked = why)
    }
    // Being fetched or added, or the AI lookup or the manual search is still finding pages and manuals: placeholder cards.
    if (waiting) SkeletonList(stringResource(if (searching) R.string.doc_searching else R.string.doc_loading), count = if (documents.isEmpty()) 2 else 1, lines = 3)
}

/**
 * Opens document [d]: its file fetched from the server ([onOpenFile]) when one is kept, else its page in the browser;
 * null when it has neither. Without [onOpenFile] only the page opens.
 */
@Composable
internal fun documentOpener(d: AssetDocument, onOpenFile: ((AssetDocument) -> Unit)? = null): (() -> Unit)? {
    val uriHandler = LocalUriHandler.current
    return when {
        onOpenFile != null && d.hasFile -> ({ onOpenFile(d) })
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
private fun DocumentCard(
    d: AssetDocument,
    canRemove: Boolean,
    opening: Boolean,
    onOpen: (AssetDocument) -> Unit,
    onRemove: () -> Unit,
    removeBlocked: UiText? = null,
) {
    val locale = LocalConfiguration.current.locales[0]
    val open = documentOpener(d, onOpen)
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
    // Tapping the card opens the document (a file in the app or another app, a web page in the browser); none while it is
    // being fetched. A document with nothing to open isn't tappable.
    val openLabel = stringResource(R.string.doc_open_cd, d.title)
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).testTag("doc-${d.id}").clip(r(16)).background(C.Surface)
            .border(1.dp, C.Border, r(16))
            .then(
                if (open != null) Modifier.pressClickable(shape = r(16), pressed = PRESSED_CARD, enabled = !opening, onClickLabel = openLabel, role = Role.Button, onClick = open)
                else Modifier,
            )
            .padding(12.dp),
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
            Txt(d.title, 14, 600, maxLines = 2)
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
            // While the file is fetched to be opened, a spinner where the remove button's partner was.
            if (opening) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = C.Yellow, strokeWidth = 2.dp)
                }
            } else {
                Spacer(Modifier.size(0.dp))
            }
            if (canRemove) {
                val label = stringResource(R.string.doc_remove_cd, d.title)
                AccessGate(removeBlocked) {
                    Box(
                        Modifier.size(40.dp).pressClickable(shape = CircleShape, role = Role.Button, onClick = onRemove).semantics { contentDescription = label },
                        contentAlignment = Alignment.Center,
                    ) { LineIcon(Icons.Trash, 18.dp, C.Red, 2.2f) }
                }
            }
        }
    }
}

/** "Sorting photos…" over placeholder tiles where the photos will show, read out as one line. */
@Composable
private fun SortingNote(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().testTag("media-sorting").semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Txt(stringResource(R.string.media_sorting), 12, 600, C.Yellow, maxLines = Int.MAX_VALUE)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(SORTING_TILES) { SkeletonBlock(Modifier.size(72.dp), r(10)) }
        }
    }
}

/** How many placeholder tiles show while photos are sorted. */
private const val SORTING_TILES = 3

/** Opens the file at [uri] in another app the user picks (a PDF or spreadsheet viewer), allowed to read it only. */
internal fun openFile(context: Context, uri: String, type: String?, chooser: String) {
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

/**
 * What to say about photos [fitted] turned away, a line per kind: how many weren't added, and that the kind already has
 * the most photos the property allows, or that the property doesn't collect that kind at all.
 */
internal fun photoLimitNotices(fitted: FittedPhotos): List<UiText> = fitted.refusedByKind.map { (kind, n) ->
    val max = fitted.limits[kind] ?: 0
    if (max == 0) UiText.Plural(R.plurals.photos_kind_not_collected, n, listOf(n, UiText.Res(kind.nameRes())))
    else UiText.Plural(R.plurals.photos_over_limit, n, listOf(n, UiText.Res(kind.nameRes()), max))
}

/** What the camera says when a shot is refused ([PhotoRefusal]): its kind is full or not collected, or the asset is full. */
internal fun PhotoRefusal.notice(): UiText = when (this) {
    PhotoRefusal.AssetFull -> UiText.Res(R.string.camera_asset_full, listOf(MAX_PHOTOS_PER_ASSET))
    is PhotoRefusal.KindFull ->
        if (max == 0) UiText.Res(R.string.camera_kind_not_collected, listOf(UiText.Res(kind.nameRes())))
        else UiText.Res(R.string.camera_kind_full, listOf(UiText.Res(kind.nameRes()), max))
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
    /**
     * The files the asset was read from (from the server), whatever their type (photos, PDFs, spreadsheets…): one Source
     * file section of their own, under Videos.
     */
    sourceFiles: List<AssetDocument> = emptyList(),
    /** The document being fetched to be opened, if any. */
    openingId: String? = null,
    /** Fetches a source file from the server and opens it ([openingId] while it is fetched). */
    onOpenSource: (AssetDocument) -> Unit = {},
    /** A photo moved to another kind's card (null: Other photos); null when photos can't be moved. */
    onMove: ((Media, PhotoKind?) -> Unit)? = null,
    /** What holding a photo or file offers besides opening it: saving a copy, and removing it (null: nobody may). */
    onDownload: ((Media) -> Unit)? = null,
    onRemove: ((Media) -> Unit)? = null,
    /** The signed-in user may not change the asset: Remove is greyed out and a tap says why. */
    removeBlocked: UiText? = null,
) {
    // Photos just added are read on the device and sorted into their kinds before they show.
    if (sorting) SortingNote(Modifier.padding(horizontal = 12.dp))
    val indexed = media.withIndex().toList()
    val photos = indexed.filter { (_, m) -> !m.video && !m.isDoc && !m.audio }
    val shown = rules.filter { r -> r.max > 0 || photos.any { it.value.category == r.kind } }
    // Photos can be dragged between the cards (tap and hold one); a kind at its most takes no more.
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
            onMove = onMove, onDownload = onDownload, onRemove = onRemove, removeBlocked = removeBlocked,
        )
    }
    PhotoCard(
        title = stringResource(R.string.media_other), hint = stringResource(R.string.media_other_hint), chip = null, chipWarn = false,
        items = photos.filter { it.value.category == null && it.value.fieldId == null },
        onOpen = onOpen, modifier = Modifier.testTag("media-card-other"),
        drag = drag, key = OTHER_PHOTOS_KEY, look = lookOf(OTHER_PHOTOS_KEY), moveTo = cardNames.filter { it.first != OTHER_PHOTOS_KEY && !full(it.first) },
        onMove = onMove, onDownload = onDownload, onRemove = onRemove, removeBlocked = removeBlocked,
    )
    FileList(stringResource(R.string.media_videos), indexed.filter { (_, m) -> m.video }, onOpen, onDownload, onRemove, removeBlocked)
    // The files the asset was read from, whatever their type: opened from here, never removed.
    SourceFileSection(sourceFiles, openingId, onOpenSource)
    FileList(stringResource(R.string.media_memos_files), indexed.filter { (_, m) -> !m.video && (m.isDoc || m.audio) }, onOpen, onDownload, onRemove, removeBlocked)
}

/** A source photo's preview (base64) as an image to show, or null when there is none or it can't be read. */
private fun previewBitmap(preview: String?): ImageBitmap? = preview?.let {
    runCatching {
        val bytes = Base64.decode(it, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}

/**
 * A titled card of videos, voice memos or files, looking like the photo cards: each with its kind, name, size or
 * length; tapping one opens it.
 */
@Composable
private fun FileList(
    title: String,
    files: List<IndexedValue<Media>>,
    onOpen: (Int) -> Unit,
    onDownload: ((Media) -> Unit)?,
    onRemove: ((Media) -> Unit)?,
    removeBlocked: UiText?,
) {
    Column(
        // A card inset 12dp from the sides.
        Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(r(14)).background(C.Surface).border(1.dp, C.Border, r(14)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Txt(title, 14, 600, maxLines = 2)
        if (files.isEmpty()) Txt(stringResource(R.string.media_none), 12, 400, C.Muted, maxLines = Int.MAX_VALUE)
        else Column(Modifier.fillMaxWidth()) { FileRows(files, onOpen, onDownload, onRemove, removeBlocked) }
    }
}

/**
 * Source file, as the web's: every file the asset was read from, whatever its type (a photo shows its thumbnail, from the
 * preview the server sends; anything else its type). Tapping one fetches it and opens it; none can be removed. "None"
 * when the asset wasn't read from a file.
 */
@Composable
private fun SourceFileSection(docs: List<AssetDocument>, openingId: String?, onOpen: (AssetDocument) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    Column(
        Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(r(14)).background(C.Surface).border(1.dp, C.Border, r(14)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Txt(stringResource(R.string.media_source_document), 14, 600, maxLines = 2)
        if (docs.isEmpty()) Txt(stringResource(R.string.media_none), 12, 400, C.Muted, maxLines = Int.MAX_VALUE)
        docs.forEach { doc ->
            val opening = openingId == doc.id
            val open = documentOpener(doc, onOpen)?.takeUnless { opening }
            Row(
                Modifier.fillMaxWidth().testTag("media-source-${doc.id}").then(if (open != null) Modifier.pressClickable(onClick = open) else Modifier)
                    .heightIn(min = 56.dp).padding(horizontal = 2.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val thumb = remember(doc.id, doc.preview) { previewBitmap(doc.preview) }
                if (thumb != null) {
                    Image(thumb, null, Modifier.size(44.dp).clip(r(8)), contentScale = ContentScale.Crop)
                } else {
                    Txt(doc.typeTag(), 10, 700, C.Soft, Modifier.background(C.Btn, Capsule).padding(horizontal = 8.dp, vertical = 3.dp))
                }
                Column(Modifier.weight(1f)) {
                    Txt(doc.title, 13, 700, maxLines = 1)
                    val meta = listOfNotNull(doc.sizeBytes?.let { sizeText(it) }, doc.uploadedAt?.let { formatDate(it, locale) }).joinToString(" · ")
                    if (meta.isNotEmpty()) Txt(meta, 11, 400, C.Muted)
                }
                if (opening) CircularProgressIndicator(Modifier.size(15.dp), color = C.Yellow, strokeWidth = 2.dp)
                else if (open != null) LineIcon(Icons.Eye, 15.dp, C.Yellow, 2.2f)
            }
        }
    }
}

/** The rows of a [FileList], split by lines; tapping one opens it, holding it lists what can be done with it. */
@Composable
private fun FileRows(
    files: List<IndexedValue<Media>>,
    onOpen: (Int) -> Unit,
    onDownload: ((Media) -> Unit)?,
    onRemove: ((Media) -> Unit)?,
    removeBlocked: UiText?,
) {
        val actionsLabel = stringResource(R.string.media_actions)
        files.forEachIndexed { n, (i, m) ->
            var menuOpen by remember(m.id) { mutableStateOf(false) }
            Row(
                Modifier.pressClickable(pressed = PRESSED_CARD, onLongClick = { menuOpen = true }, onLongClickLabel = actionsLabel) { onOpen(i) }
                    .fillMaxWidth().testTag(PhotoTags.tile(i)).then(if (n > 0) Modifier.topBorder(C.Border) else Modifier)
                    .heightIn(min = 56.dp).padding(horizontal = 2.dp, vertical = 8.dp),
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
                IconMenu(menuOpen, { menuOpen = false }, mediaMenu(m, { onOpen(i) }, onDownload, onRemove, removeBlocked = removeBlocked))
            }
        }
}

/**
 * One photo card: its name with a count chip, what it shows, and its photos (or "None yet."). With [drag]
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
    onMove: ((Media, PhotoKind?) -> Unit)? = null,
    onDownload: ((Media) -> Unit)? = null,
    onRemove: ((Media) -> Unit)? = null,
    removeBlocked: UiText? = null,
) {
    val (bg, edge) = dropColors(look)
    val moveLabel = stringResource(R.string.media_move_to)
    Column(
        // Full width, like the Info sections; above the other cards while one of its photos is being dragged.
        modifier.zIndex(if (drag != null && drag.from == key) 1f else 0f)
            .then(if (drag != null) Modifier.photoDropTarget(drag, key) else Modifier)
            // Rounded without clipping, so a photo dragged out of the card stays on top of the others, not cut off at its edge.
            .padding(horizontal = 12.dp).fillMaxWidth().background(bg.value, r(14)).border(1.dp, C.Border, r(14)).border(2.dp, edge.value, r(14))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Txt(title, 14, 600)
                    chip?.let {
                        Txt(
                            it, 12, 600, if (chipWarn) C.Orange else C.Muted,
                            Modifier.background(if (chipWarn) C.WarnRow else C.Raised, Capsule).padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                Txt(hint, 11, 400, C.Muted, maxLines = Int.MAX_VALUE)
            }
        }
        if (items.isEmpty()) Txt(stringResource(R.string.media_none), 12, 400, C.Muted, maxLines = Int.MAX_VALUE)
        else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { (i, m) ->
                val movable = if (drag != null) {
                    Modifier.semantics {
                        customActions = moveTo.map { (to, name) -> CustomAccessibilityAction(moveLabel.format(name)) { drag.accept(m, to) } }
                    }
                } else Modifier
                // Holding it lists View, Download, Move to (when photos can move: the other cards with room) and Remove.
                val moves = if (onMove != null) moveTo.map { (to, name) -> name to { onMove(m, PhotoKind.of(to)) } } else emptyList()
                MediaTile(
                    m, { onOpen(i) }, Modifier.size(76.dp).then(movable).testTag(PhotoTags.tile(i)),
                    menu = mediaMenu(m, { onOpen(i) }, onDownload, onRemove, moves, removeBlocked), drag = drag, card = key,
                )
            }
        }
    }
}

/** A tab with nothing to list yet. */
@Composable
internal fun EmptyTab(text: String) {
    Box(
        Modifier.fillMaxWidth().testTag(DetailTags.TAB_EMPTY).clip(r(12)).background(C.Surface).border(1.dp, C.Border, r(12))
            .padding(horizontal = 12.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) { Txt(text, 13, 400, C.Muted, maxLines = Int.MAX_VALUE, align = TextAlign.Center) }
}

