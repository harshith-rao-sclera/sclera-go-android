package com.sclera.sclerago.presentation.fileimport

import com.sclera.sclerago.presentation.components.pressClickable
import com.sclera.sclerago.presentation.components.PRESSED_CARD
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.ImportedFile
import com.sclera.sclerago.domain.model.MAX_DOCUMENT_UPLOAD_MB
import com.sclera.sclerago.domain.model.UploadProblem
import com.sclera.sclerago.presentation.model.OnlineOnly
import com.sclera.sclerago.presentation.assets.ReviewScreen
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.components.CheckBox
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.main.ReviewItem
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import com.sclera.sclerago.services.media.formatSize

/** Test tags of the File tab's review. */
object FileImportTags {
    const val CREATE_FIELDS = "file-import-create-fields"
    const val SAMPLE = "file-import-sample"
}

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

/** The name the sample is offered under (the server's `Content-Disposition`). */
private const val SAMPLE_NAME = "asset-sample.xlsx"

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
) {
    val s = vm.state
    val banner = OnlineOnly.FILES.takeUnless { online }
    val explain = { why: OnlineOnly -> flash(UiText.Res(why.message)) }
    OutcomeEffect(s.outcome, onClose, flash, vm::outcomeShown)
    val sampleSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX_MIME)) { uri ->
        vm.saveSample(uri?.toString())
    }
    when (s.step) {
        FileStep.PICK -> {
            val send = s.toSend.size
            ReviewScreen(
                sub = pluralStringResource(R.plurals.file_import_files_sub, send, send),
                addLabel = stringResource(R.string.review_add_files), onAdd = onAddFiles,
                items = s.files.map { it.toItem(s) }, onToggle = vm::toggle,
                onDiscard = vm::discard, onGo = vm::read,
                goLabel = if (send > 0) pluralStringResource(R.plurals.file_import_read, send, send) else null,
                footer = { SampleLink { sampleSaver.launch(SAMPLE_NAME) } },
                offlineBanner = banner, offlineGo = OnlineOnly.READ_FILE.takeUnless { online }, onOffline = explain,
                thumb = { ExtBadge(it) },
            )
        }
        FileStep.READING -> {
            val sent = s.files.filter { it.id in s.jobs }
            ReviewScreen(
                sub = if (s.reading) pluralStringResource(R.plurals.file_import_files_sub, sent.size, sent.size)
                else stringResource(R.string.file_import_nothing_found),
                addLabel = stringResource(R.string.review_add_files), onAdd = if (s.reading) null else onAddFiles,
                items = sent.map { it.toItem(s) }, onToggle = vm::toggle,
                onDiscard = vm::discard, onGo = {},
                goLabel = stringResource(if (s.reading) R.string.file_import_reading_files else R.string.file_import_none_found),
                busy = true,
                offlineBanner = banner,
                thumb = { ExtBadge(it) },
            )
        }
        FileStep.REVIEW -> {
            val extras = s.extraNames.size
            ReviewScreen(
                sub = pluralStringResource(R.plurals.file_import_drafts_sub, s.drafts.size, s.drafts.size),
                addLabel = stringResource(R.string.review_add_files), onAdd = null,
                items = s.drafts.map { it.toItem(s.off) }, onToggle = vm::toggle,
                onDiscard = vm::discard, onGo = vm::accept,
                goLabel = if (s.busy) stringResource(R.string.file_import_adding) else null,
                busy = s.busy,
                footer = {
                    Txt(stringResource(R.string.file_import_tap_to_correct), 11, 400, C.Muted, maxLines = Int.MAX_VALUE)
                    if (extras > 0) CreateFieldsToggle(extras, s.createFields) { vm.setCreateFields(!s.createFields) }
                },
                onOpen = vm::openDraft,
                offlineBanner = banner, offlineGo = OnlineOnly.ADD_FILE_ASSETS.takeUnless { online }, onOffline = explain,
                thumb = { ExtBadge(it) },
            )
        }
    }
}

/** "Download sample": a blank spreadsheet with the property's fields as headings, to fill in and upload. */
@Composable
private fun SampleLink(onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).pressClickable(shape = r(10), pressed = PRESSED_CARD, onClick = onClick).testTag(FileImportTags.SAMPLE),
        verticalArrangement = Arrangement.Center,
    ) {
        Txt(stringResource(R.string.file_import_sample), 13, 700, C.Yellow)
        Txt(stringResource(R.string.file_import_sample_hint), 11, 400, C.Muted, maxLines = Int.MAX_VALUE)
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
    FileOutcome.SampleSaved -> stringResource(R.string.file_import_sample_saved)
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
        CheckBox(on)
        Txt(pluralStringResource(R.plurals.file_import_create_fields, count, count), 12, 500, C.Muted, maxLines = Int.MAX_VALUE)
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
        on = id !in s.off && id !in s.problems, ext = ext, extColor = extColor(), file = name, note = note, noteColor = color,
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
