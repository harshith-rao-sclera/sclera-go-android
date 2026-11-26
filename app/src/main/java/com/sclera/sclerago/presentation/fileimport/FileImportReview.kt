package com.sclera.sclerago.presentation.fileimport

import com.sclera.sclerago.presentation.components.pressClickable
import com.sclera.sclerago.presentation.components.PRESSED_CARD
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.ImportedFile
import com.sclera.sclerago.domain.model.MAX_DOCUMENT_UPLOAD_MB
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.UploadProblem
import com.sclera.sclerago.presentation.model.OnlineOnly
import com.sclera.sclerago.presentation.model.reason
import com.sclera.sclerago.presentation.assets.PhotoViewer
import com.sclera.sclerago.presentation.assets.ReviewScreen
import com.sclera.sclerago.presentation.assets.openFile
import com.sclera.sclerago.presentation.components.AccessGate
import com.sclera.sclerago.presentation.components.MediaImage
import com.sclera.sclerago.presentation.components.MenuAct
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.components.OnOffSwitch
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.main.ReviewItem
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import com.sclera.sclerago.services.media.formatSize

/** Test tags of the File tab's review. */
object FileImportTags {
    const val CREATE_FIELDS = "file-import-create-fields"
}

/**
 * The File tab's Review and onboard screen through the server's steps: the files picked (each checked as the server
 * would), their upload and reading, then the assets found in them to add. [onAddFiles] opens the file manager again;
 * [onClose] goes back to the tab's list once the review is done with; [flash] shows what happened.
 */
@Composable
fun FileImportReview(
    vm: FileImportViewModel,
    onAddFiles: () -> Unit,
    onClose: () -> Unit,
    flash: (UiText) -> Unit,
    /** The device is online. Offline, a banner says the files are read by the server, and Read / Add say why they wait. */
    online: Boolean = true,
    /** What the button that adds more says ("Add files"; the Camera's "Take more"). */
    addMore: String = stringResource(R.string.review_add_files),
) {
    val s = vm.state
    val context = LocalContext.current
    val openWith = stringResource(R.string.doc_open_with)
    // A photo or video opens in the app's viewer; any other file in an app that reads it.
    var preview by remember { mutableStateOf<ImportedFile?>(null) }
    val open: (ImportedFile) -> Unit = { f ->
        if (f.previewMedia() != null) {
            preview = f
        } else {
            openFile(context, f.uri, f.mime, openWith)
        }
    }
    val openPicked = { id: String -> s.files.firstOrNull { it.id == id }?.let(open); Unit }
    val openSource = { id: String -> s.drafts.firstOrNull { it.draft.id == id }?.file?.let(open); Unit }
    val banner = OnlineOnly.FILES.takeUnless { online }
    val explain = { why: OnlineOnly -> flash(UiText.Res(why.message)) }
    OutcomeEffect(s.outcome, onClose, flash, vm::outcomeShown)
    Box(Modifier.fillMaxSize()) {
    when (s.step) {
        FileStep.PICK -> {
            val send = s.toSend.size
            ReviewScreen(
                sub = pluralStringResource(R.plurals.file_import_files_sub, send, send),
                addLabel = addMore, onAdd = onAddFiles,
                items = s.files.map { it.toItem(s) }, onToggle = vm::toggle,
                onDiscard = vm::discard, onGo = vm::read,
                goLabel = if (send > 0) pluralStringResource(R.plurals.file_import_read, send, send) else null,
                onOpen = openPicked,
                offlineBanner = banner, offlineGo = OnlineOnly.READ_FILE.takeUnless { online }, onOffline = explain,
                goBlock = vm.collectBlock.reason(),
                // Holding a file: View, Include / Leave out (unless the server would refuse it), Remove.
                menu = { ri ->
                    buildList {
                        add(MenuAct(Icons.Eye, stringResource(R.string.media_view)) { openPicked(ri.id) })
                        if (ri.id !in s.problems) add(includeAct(ri.on) { vm.toggle(ri.id) })
                        add(MenuAct(Icons.Trash, stringResource(R.string.media_remove), danger = true) { vm.remove(ri.id) })
                    }
                },
                thumb = { ri, hold -> FileThumb(ri, { openPicked(ri.id) }, hold) },
            )
        }
        FileStep.READING -> {
            val sent = s.files.filter { it.id in s.jobs }
            ReviewScreen(
                sub = if (s.reading) pluralStringResource(R.plurals.file_import_files_sub, sent.size, sent.size)
                else stringResource(R.string.file_import_nothing_found),
                addLabel = addMore, onAdd = if (s.reading) null else onAddFiles,
                items = sent.map { it.toItem(s) }, onToggle = vm::toggle,
                onDiscard = vm::discard, onGo = {},
                goLabel = stringResource(if (s.reading) R.string.file_import_reading_files else R.string.file_import_none_found),
                busy = true,
                onOpen = openPicked,
                offlineBanner = banner,
                // While the files are read only viewing them is offered.
                menu = { ri -> listOf(MenuAct(Icons.Eye, stringResource(R.string.media_view)) { openPicked(ri.id) }) },
                thumb = { ri, hold -> FileThumb(ri, { openPicked(ri.id) }, hold) },
            )
        }
        FileStep.REVIEW -> {
            val extras = s.extraNames.size
            ReviewScreen(
                sub = pluralStringResource(R.plurals.file_import_drafts_sub, s.drafts.size, s.drafts.size),
                addLabel = addMore, onAdd = null,
                items = s.drafts.map { it.toItem(s.off) }, onToggle = vm::toggle,
                onDiscard = vm::discard, onGo = vm::accept,
                goBlock = vm.collectBlock.reason(),
                goLabel = if (s.busy) stringResource(R.string.file_import_adding) else null,
                busy = s.busy,
                footer = {
                    Txt(stringResource(R.string.file_import_tap_to_correct), 11, 400, C.Muted, maxLines = Int.MAX_VALUE)
                    if (extras > 0) {
                        // Greyed out, as Add, while the user can't collect; a tap says why.
                        AccessGate(vm.collectBlock, Modifier.fillMaxWidth()) {
                            CreateFieldsToggle(extras, s.createFields) { vm.setCreateFields(!s.createFields) }
                        }
                    }
                },
                onOpen = vm::openDraft,
                offlineBanner = banner, offlineGo = OnlineOnly.ADD_FILE_ASSETS.takeUnless { online }, onOffline = explain,
                // Holding an asset found: correct its details, view the file it was read from, include it or leave it out.
                menu = { ri ->
                    listOf(
                        MenuAct(Icons.Pencil, stringResource(R.string.file_import_edit_details)) { vm.openDraft(ri.id) },
                        MenuAct(Icons.Eye, stringResource(R.string.file_import_view_file)) { openSource(ri.id) },
                        includeAct(ri.on) { vm.toggle(ri.id) },
                    )
                },
                // The thumbnail shows the file the asset was read from; tapping it opens that file.
                thumb = { ri, hold -> FileThumb(ri, { openSource(ri.id) }, hold) },
            )
        }
    }
    preview?.let { f ->
        f.previewMedia()?.let { m ->
            BackHandler { preview = null }
            PhotoViewer(
                m, onClose = { preview = null }, onDownload = null,
                // Before it is read, a picked file can be taken off from its preview too.
                onRemove = if (s.step == FileStep.PICK) ({ preview = null; vm.remove(f.id) }) else null,
            )
        }
    }
    }
}

/** Include (ticks it) or Leave out (unticks it), for an item that is [on] or not. */
@Composable
private fun includeAct(on: Boolean, toggle: () -> Unit): MenuAct =
    if (on) MenuAct(Icons.Minus, stringResource(R.string.review_leave_out), go = toggle)
    else MenuAct(Icons.CheckSquare, stringResource(R.string.review_include), go = toggle)

/**
 * A file's picture: a photo, or a video's first frame, else its extension in its colour (a spreadsheet, a PDF…).
 * Tapping it opens the file; holding it lists what can be done with it ([onHold]).
 */
@Composable
private fun FileThumb(ri: ReviewItem, onOpen: () -> Unit, onHold: () -> Unit) {
    val label = stringResource(R.string.doc_open_cd, ri.file)
    val actions = stringResource(R.string.media_actions)
    Box(
        Modifier.size(52.dp).clip(r(10)).pressClickable(shape = r(10), onLongClick = onHold, onLongClickLabel = actions, onClick = onOpen)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        val media = ri.media
        if (media == null) ExtBadge(ri)
        else {
            MediaImage(media, Modifier.fillMaxSize(), maxPx = 160)
            if (media.video) LineIcon(Icons.Video, 16.dp, C.White, 2.2f, Modifier.align(Alignment.BottomStart).padding(5.dp))
        }
    }
}

/** A photo or video file as [Media], to show its picture and play it here; null for any other file. */
private fun ImportedFile.previewMedia(): Media? {
    val type = mime.orEmpty()
    return when {
        type.startsWith("image/") -> Media(id, uri, video = false)
        type.startsWith("video/") -> Media(id, uri, video = true)
        else -> null
    }
}

/** Tells the user once what adding or discarding did, and closes the review when it is done with. */
@Composable
private fun OutcomeEffect(outcome: FileOutcome?, onClose: () -> Unit, flash: (UiText) -> Unit, shown: () -> Unit) {
    val message = outcome?.message()
    LaunchedEffect(outcome) {
        if (outcome == null) return@LaunchedEffect
        shown()
        message?.let { flash(UiText.Raw(it)) }
        if (outcome.closes) onClose()
    }
}

/** The message for [this], as the web words it after adding: what was added, then what was made or left out. */
@Composable
private fun FileOutcome.message(): String? = when (this) {
    is FileOutcome.Added -> with(result) {
        listOfNotNull(
            pluralStringResource(R.plurals.file_import_added, added, added),
            placesCreated.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.file_import_places_created, it, it) },
            typesCreated.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.file_import_types_created, it, it) },
            fieldsCreated.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.file_import_fields_created, it, it) },
            unplaced.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.file_import_unplaced, it, it) },
            unclassified.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.file_import_unclassified, it, it) },
            leftOut.takeIf { it.isNotEmpty() }?.let { names ->
                stringResource(R.string.file_import_left_out, names.take(5).joinToString(", ") + if (names.size > 5) "…" else "")
            },
        ).joinToString(" ")
    }
    is FileOutcome.Discarded -> pluralStringResource(R.plurals.file_import_discarded, count, count)
    is FileOutcome.Failed -> message.asString()
    FileOutcome.Cleared -> null
}

/** "Save other details as fields": what the files said that no field holds becomes fields of each asset added. */
@Composable
private fun CreateFieldsToggle(count: Int, on: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).pressClickable(shape = r(10), pressed = PRESSED_CARD, onClick = onToggle).testTag(FileImportTags.CREATE_FIELDS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Txt(pluralStringResource(R.plurals.file_import_create_fields, count, count), 12, 400, C.Soft, Modifier.weight(1f), maxLines = Int.MAX_VALUE)
        OnOffSwitch(on, onChange = { onToggle() })
    }
}

/** A file's extension in its colour (the kind of file it is, or the file an asset was read from). */
@Composable
private fun ExtBadge(ri: ReviewItem) {
    Box(Modifier.size(52.dp).background(C.Raised, r(10)), contentAlignment = Alignment.Center) {
        Txt(ri.ext, 11, 800, ri.extColor)
    }
}

/** A picked file: whether the server would take it, then how its upload and reading go. */
private fun ImportedFile.toItem(s: FileImportState): ReviewItem {
    val problem = s.problems[id]
    val (note, color) = if (problem != null) problem.text() to C.Red else when (val job = s.jobs[id]) {
        null -> UiText.Res(R.string.file_import_ready) to C.Green
        is FileJob.Uploading -> UiText.Res(R.string.file_import_uploading, listOf(job.percent)) to C.Muted
        FileJob.Reading -> UiText.Res(R.string.file_import_reading) to C.Muted
        is FileJob.Found ->
            if (job.count > 0) UiText.Plural(R.plurals.file_import_found, job.count) to C.Green
            else UiText.Res(R.string.file_import_none_found) to C.Muted
        is FileJob.Failed -> (job.detail?.let { UiText.Res(R.string.file_import_failed, listOf(it)) } ?: UiText.Res(R.string.file_import_failed_plain)) to C.Red
        FileJob.TimedOut -> UiText.Res(R.string.file_import_timed_out) to C.Red
    }
    return ReviewItem(
        id = id, name = baseName, sub = UiText.Res(R.string.review_file_sub, listOf(name, formatSize(size))), ready = false,
        on = id !in s.off && id !in s.problems, ext = ext, extColor = extColor(), file = name, media = previewMedia(),
        note = note, noteColor = color,
    )
}

/** An asset read from a file: its name, where and what the file says it is, the file (and row) it came from. */
private fun FoundDraft.toItem(off: Set<String>): ReviewItem {
    val said = listOf(draft.place.joinToString(" › "), draft.type.joinToString(" › ")).filter { it.isNotEmpty() }.joinToString(" · ")
    val where = if (said.isEmpty()) UiText.Res(R.string.file_import_no_place) else UiText.Raw(said)
    val source = listOfNotNull(file.name, draft.sourceRef?.takeIf { it.isNotBlank() }).joinToString(" · ")
    val from = if (draft.photos > 0) {
        UiText.Res(R.string.file_import_from_photos, listOf(source, UiText.Plural(R.plurals.file_import_photos, draft.photos)))
    } else UiText.Res(R.string.file_import_from, listOf(source))
    val unsure = draft.unsure
    // As the web's review table: a type the file names that isn't one of the property's is said, as it goes in without one.
    val notes = listOfNotNull(
        UiText.Plural(R.plurals.file_import_check, unsure).takeIf { unsure > 0 },
        UiText.Res(R.string.file_import_no_matching_type).takeIf { draft.typeMatched == false },
    )
    return ReviewItem(
        id = draft.id, name = draft.name.ifBlank { source }, sub = UiText.Res(R.string.file_import_draft_sub, listOf(where, from)),
        ready = unsure == 0, on = draft.id !in off, ext = file.ext, extColor = file.extColor(), file = file.name,
        media = file.previewMedia(),
        note = when (notes.size) {
            0 -> null
            1 -> notes.single()
            else -> UiText.Res(R.string.file_import_draft_sub, notes)
        },
        noteColor = C.Orange,
    )
}

private fun UploadProblem.text(): UiText = when (this) {
    UploadProblem.UNREADABLE -> UiText.Res(R.string.file_import_unreadable)
    UploadProblem.NEEDS_AI -> UiText.Res(R.string.file_import_needs_ai)
    UploadProblem.EMPTY -> UiText.Res(R.string.file_import_empty)
    UploadProblem.TOO_LARGE -> UiText.Res(R.string.file_import_too_large, listOf(MAX_DOCUMENT_UPLOAD_MB))
}

/** Green for spreadsheets, red for PDFs, blue for photos, purple for videos. */
private fun ImportedFile.extColor(): Color {
    val mime = mime.orEmpty()
    return when {
        ext in setOf("XLS", "XLSX", "XLSM", "CSV", "TSV") || mime.contains("sheet") || mime.contains("excel") -> C.Green
        ext == "PDF" || mime == "application/pdf" -> C.Red
        mime.startsWith("image/") -> C.Blue
        mime.startsWith("video/") -> C.Purple
        else -> C.Soft
    }
}
