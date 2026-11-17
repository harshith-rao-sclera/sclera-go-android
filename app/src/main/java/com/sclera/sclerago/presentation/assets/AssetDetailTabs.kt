package com.sclera.sclerago.presentation.assets

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.presentation.theme.ScleraFonts
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.material3.Text
import com.sclera.sclerago.domain.model.ManualKind
import com.sclera.sclerago.domain.model.DocumentSource
import com.sclera.sclerago.domain.model.DocumentFileStatus
import com.sclera.sclerago.domain.model.AssetDocument
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalUriHandler
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.domain.model.PhotoRule
import com.sclera.sclerago.domain.model.PhotoKind
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import com.sclera.sclerago.domain.model.ActivityKind
import com.sclera.sclerago.domain.model.AssetActivity
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.common.formatDateTimeSeconds
import com.sclera.sclerago.presentation.components.LocalReadOnly
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.PlainField
import com.sclera.sclerago.presentation.components.RoundButton
import com.sclera.sclerago.presentation.components.bottomBorder
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.icons.Ico
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import com.sclera.sclerago.domain.model.formatDuration

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
 * Documents, Media, Comments and History (the web's tabs). A new asset isn't saved yet, so it has no validation, comments
 * or history: Info, Documents and Media (the files picked for it).
 */
fun detailTabs(state: AssetDetailState): List<DetailTabItem> {
    if (state.asset == null) return emptyList()
    return buildList {
        add(DetailTabItem(DetailTabKind.INFO))
        if (!state.isNew) add(DetailTabItem(DetailTabKind.VALIDATION))
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
    Row(Modifier.fillMaxWidth().height(if (compact) 44.dp else 60.dp).background(C.Strip).bottomBorder(C.Border)) {
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

/** A document's type as the short tag on its card: PDF, XLS, DOC or TXT. */
private fun AssetDocument.typeTag(): String = when (fileKind) {
    "pdf" -> "PDF"
    "spreadsheet" -> "XLS"
    "text" -> "TXT"
    else -> "DOC"
}

/**
 * The Documents tab: a card per document — the file the asset was read from, the web pages its applied AI suggestions
 * came from, and its model's manuals from the manufacturer's site — each with its category, what it was used for (or the
 * model), its link and whether the PDF is kept. Tapping one opens its page. Upload document is the floating + ([UploadDocumentFab]).
 */
@Composable
fun DocumentsTab(documents: List<AssetDocument>, locked: Boolean, onRemove: (AssetDocument) -> Unit) {
    if (documents.isEmpty()) {
        EmptyTab(stringResource(R.string.doc_none))
        return
    }
    val uri = LocalUriHandler.current
    documents.forEach { d ->
        val open = d.url?.takeIf { it.isNotBlank() }?.let { url -> { runCatching { uri.openUri(url) }; Unit } }
        val category = when (d.source) {
            DocumentSource.SOURCE_FILE -> stringResource(R.string.detail_value_none)
            DocumentSource.WEB_PAGE -> stringResource(if (d.official) R.string.doc_ai_source_official else R.string.doc_ai_source)
            DocumentSource.MANUAL -> stringResource(R.string.doc_manufacturer_kind, stringResource(d.manualKind.labelRes()))
        }
        Column(
            Modifier.fillMaxWidth().testTag("doc-${d.id}").clip(r(12)).background(C.Surface).border(1.dp, C.Border, r(12))
                .then(if (open != null) Modifier.clickable(onClick = open) else Modifier).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(C.YTintBg, r(10)), contentAlignment = Alignment.Center) {
                    Txt(d.typeTag(), 10, 800, C.Yellow)
                }
                Box(Modifier.weight(1f))
                if (d.removable && !locked) {
                    val label = stringResource(R.string.doc_remove_cd, d.title)
                    Box(
                        Modifier.size(44.dp).clip(r(10)).clickable { onRemove(d) }.semantics { contentDescription = label },
                        contentAlignment = Alignment.Center,
                    ) { LineIcon(Icons.Trash, 16.dp, C.Red, 2.2f) }
                }
            }
            Txt(d.title, 14, 700, maxLines = 2)
            DocLine(stringResource(R.string.doc_category), category)
            when (d.source) {
                DocumentSource.SOURCE_FILE -> DocLine(stringResource(R.string.doc_created_by), stringResource(R.string.detail_value_none))
                DocumentSource.WEB_PAGE -> if (d.usedFor.isNotEmpty()) DocLine(stringResource(R.string.doc_used_for), d.usedFor.joinToString(", "))
                DocumentSource.MANUAL -> d.model?.let { DocLine(stringResource(R.string.doc_model), it) }
            }
            if (d.url != null) DocLine(stringResource(R.string.doc_link), d.publisher ?: d.url, link = true)
            if (d.source != DocumentSource.SOURCE_FILE) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt(stringResource(d.fileStatus.labelRes()), 11, 500, C.Muted, Modifier.weight(1f))
                    if (open != null) LineIcon(Icons.Expand, 14.dp, C.Muted, 2.4f)
                }
            }
        }
    }
}

/** One "Label: value" line of a document card; a link is in the accent colour. */
@Composable
private fun DocLine(label: String, value: String, link: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Txt(label, 12, 500, C.Muted)
        Txt(value, 12, 600, if (link) C.Yellow else C.Soft, Modifier.weight(1f), maxLines = 2)
    }
}

private fun ManualKind?.labelRes() = when (this) {
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
private fun sizeText(bytes: Long): String =
    if (bytes >= 1024 * 1024) "%.1f MB".format(bytes / (1024f * 1024f)) else "${(bytes / 1024).coerceAtLeast(1)} KB"

/**
 * The Media tab: "Photos", with a card per kind of photo the property's photo rules count (how many it has against what
 * is needed or allowed, and its photos), one for the other photos, then "Videos", then "Voice memos and files". Tapping a
 * photo or file opens it. Adding is done with the floating buttons ([MediaFabs]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaTab(media: List<Media>, rules: List<PhotoRule>, onOpen: (Int) -> Unit) {
    val indexed = media.withIndex().toList()
    val photos = indexed.filter { (_, m) -> !m.video && !m.isDoc && !m.audio }
    val locale = LocalConfiguration.current.locales[0]
    // "Still needs 1 more nameplate photo, 2 more asset photos"
    val short = rules.mapNotNull { r ->
        val need = r.min - photos.count { it.value.category == r.kind }
        if (need > 0) pluralStringResource(R.plurals.media_need_more, need, need, stringResource(r.kind.nameRes()).lowercase(locale)) else null
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Txt(stringResource(R.string.media_photos), 15, 700)
        if (short.isNotEmpty()) {
            Txt(
                stringResource(R.string.media_still_needs, short.joinToString(", ")), 11, 700, C.Orange,
                Modifier.weight(1f, fill = false).background(C.WarnRow, Capsule).padding(horizontal = 8.dp, vertical = 3.dp), maxLines = 2,
            )
        }
    }
    rules.forEach { r ->
        val mine = photos.filter { it.value.category == r.kind }
        if (r.max == 0 && mine.isEmpty()) return@forEach
        val count = mine.size
        val chip = if (count < r.min) stringResource(R.string.media_needed, count, r.min) else stringResource(R.string.media_allowed, count, r.max)
        PhotoCard(
            title = stringResource(r.kind.nameRes()), hint = stringResource(r.kind.hintRes()), chip = chip, chipWarn = count < r.min,
            items = mine, onOpen = onOpen, modifier = Modifier.testTag("media-card-${r.kind.db}"),
        )
    }
    PhotoCard(
        title = stringResource(R.string.media_other), hint = stringResource(R.string.media_other_hint), chip = null, chipWarn = false,
        items = photos.filter { it.value.category == null && it.value.fieldId == null },
        onOpen = onOpen, modifier = Modifier.testTag("media-card-other"),
    )
    FileList(stringResource(R.string.media_videos), indexed.filter { (_, m) -> m.video }, onOpen)
    FileList(stringResource(R.string.media_memos_files), indexed.filter { (_, m) -> !m.video && (m.isDoc || m.audio) }, onOpen)
}

/** A titled list of videos, voice memos or files: each with its kind, name, size or length; tapping one opens it. */
@Composable
private fun FileList(title: String, files: List<IndexedValue<Media>>, onOpen: (Int) -> Unit) {
    Txt(title, 15, 700, modifier = Modifier.padding(top = 6.dp), maxLines = 2)
    if (files.isEmpty()) Txt(stringResource(R.string.media_none), 12, 400, C.Muted)
    else Column(Modifier.fillMaxWidth().clip(r(12)).border(1.dp, C.Border, r(12))) {
        files.forEachIndexed { n, (i, m) ->
            Row(
                Modifier.fillMaxWidth().testTag(PhotoTags.tile(i)).then(if (n > 0) Modifier.topBorder(C.Border) else Modifier)
                    .clickable { onOpen(i) }.heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 8.dp),
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
                    val meta = listOfNotNull(m.sizeBytes?.let(::sizeText), length).joinToString(" · ")
                    if (meta.isNotEmpty()) Txt(meta, 11, 400, C.Muted)
                }
                LineIcon(if (m.video || m.audio) Icons.Play else Icons.Download, 15.dp, C.Yellow, 2.2f)
            }
        }
    }
}

/** One photo card: its name with a count chip, what it shows, and its photos (or "None yet."). */
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
) {
    Column(
        modifier.fillMaxWidth().clip(r(12)).background(C.Surface).border(1.dp, C.Border, r(12)).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Txt(title, 14, 700)
                    chip?.let {
                        Txt(
                            it, 10, 700, if (chipWarn) C.Orange else C.Soft,
                            Modifier.background(if (chipWarn) C.WarnRow else C.Btn, Capsule).padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                Txt(hint, 11, 400, C.Muted, maxLines = 2)
            }
        }
        if (items.isEmpty()) Txt(stringResource(R.string.media_none), 12, 400, C.Muted)
        else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { (i, m) -> MediaTile(m, { onOpen(i) }, Modifier.size(96.dp).testTag(PhotoTags.tile(i))) }
        }
    }
}

/**
 * The Comments tab's list, as the web's: one card per comment, newest first, with its text, who wrote it and when
 * ("· edited" once changed), and Edit (its author) and Delete (its author, or a validator) on the end side. Edit turns
 * the card into a box with Save and Cancel; Delete asks first ([onDelete] shows the question). [readOnly] (a signed-off
 * asset, as the web's validated list) hides both.
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
    val edited = stringResource(R.string.detail_comment_edited)
    // None yet: "No comments yet" in the middle of the tab, as the web's.
    if (comments.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(vertical = 120.dp).testTag(DetailTags.TAB_EMPTY), contentAlignment = Alignment.Center) {
            Txt(stringResource(R.string.detail_no_comments), 13, 700, C.Muted, align = TextAlign.Center)
        }
        return
    }
    comments.forEach { e ->
        val canEdit = !readOnly && e.canEdit
        val canDelete = !readOnly && e.canDelete
        Row(
            Modifier.fillMaxWidth().testTag("detail-activity-${e.id}").clip(r(12)).background(C.Surface).border(1.dp, C.Border, r(12))
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
                    Txt(e.text, 14, 700, maxLines = Int.MAX_VALUE)
                }
                Txt(e.authorName ?: someone, 11, 500, C.Muted, maxLines = 1)
                e.createdAt?.let { Txt(formatDateTimeSeconds(it, locale) + if (e.edited) " · $edited" else "", 11, 400, C.Muted) }
            }
            if (editingId == e.id) {
                CommentIconButton(Icons.Check, stringResource(R.string.detail_comment_save), C.Yellow, "detail-comment-save-${e.id}") {
                    if (editText.isNotBlank()) onSave(e, editText) { editingId = null }
                }
                CommentIconButton(Icons.Close, stringResource(R.string.action_cancel), C.Muted, "detail-comment-cancel-${e.id}") { editingId = null }
            } else {
                if (canEdit) CommentIconButton(Icons.Pencil, stringResource(R.string.detail_comment_edit), C.Yellow, "detail-comment-edit-btn-${e.id}") {
                    editText = e.text
                    editingId = e.id
                }
                if (canDelete) CommentIconButton(Icons.Trash, stringResource(R.string.detail_comment_delete), C.Red, "detail-comment-delete-${e.id}") {
                    onDelete(e)
                }
            }
        }
    }
}

/** An icon-only button on a comment card (48dp to touch, [description] read out). */
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
                    Txt(e.text.ifEmpty { e.action.orEmpty() }, 14, 500, maxLines = 2)
                    // As the web: "Building: empty → Harbour Point Tower", the old value struck through.
                    e.fieldChanges.forEach { c ->
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = C.Text)) { append("${c.field}: ") }
                                withStyle(SpanStyle(color = C.Muted, textDecoration = TextDecoration.LineThrough)) { append(c.from ?: empty) }
                                append(" → ")
                                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = C.Text)) { append(c.to ?: empty) }
                            },
                            color = C.Soft, fontSize = 12.sp, fontFamily = ScleraFonts.Inter, maxLines = 3,
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Txt(if (e.byMe) stringResource(R.string.detail_by_you) else "", 11, 500, C.Muted, Modifier.weight(1f))
                        e.createdAt?.let { Txt(formatDateTimeSeconds(it, locale), 11, 400, C.Muted) }
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
fun CommentBox(draft: String, error: UiText?, posting: Boolean, onChange: (String) -> Unit, onPost: () -> Unit) {
    val canPost = draft.isNotBlank() && !posting
    CompositionLocalProvider(LocalReadOnly provides false) {
        // As the web: the box, then Add on the end side under it (dimmed until something is written).
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PlainField(
                draft, onChange, stringResource(R.string.detail_comment_hint),
                Modifier.fillMaxWidth().testTag(DetailTags.COMMENT_FIELD).clip(r(10)).background(C.Bg)
                    .border(1.dp, if (error != null) C.Orange else C.Border2, r(10)).padding(10.dp),
                size = 14, singleLine = false, minLines = 3, readOnly = posting,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { error?.let { Txt(it.asString(), 12, 600, C.Orange, maxLines = 3) } }
                Pill(
                    stringResource(R.string.action_add), { if (canPost) onPost() },
                    Modifier.testTag(DetailTags.COMMENT_POST).alpha(if (canPost) 1f else 0.45f),
                    bg = C.Yellow, fg = C.OnYellow, height = 48.dp, hPad = 20.dp, size = 13, weight = 700,
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
    ) { Txt(text, 13, 400, C.Muted, maxLines = 3, align = TextAlign.Center) }
}

