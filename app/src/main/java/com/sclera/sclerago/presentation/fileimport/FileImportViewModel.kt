package com.sclera.sclerago.presentation.fileimport

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.domain.model.AssetDraft
import com.sclera.sclerago.domain.model.AuthState
import com.sclera.sclerago.domain.model.DocumentStatus
import com.sclera.sclerago.domain.model.DraftField
import com.sclera.sclerago.domain.model.NAME_FIELD_KEY
import com.sclera.sclerago.domain.model.DraftsAccepted
import com.sclera.sclerago.domain.model.ImportedFile
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.UploadProblem
import com.sclera.sclerago.domain.usecase.auth.ObserveAuthStateUseCase
import com.sclera.sclerago.domain.usecase.fileimport.FileImportUseCases
import com.sclera.sclerago.domain.usecase.media.FileUseCases
import com.sclera.sclerago.domain.usecase.property.PropertyDownloadUseCases
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.toUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where the File tab is: files picked, the server reading them, or the assets found waiting for review. */
enum class FileStep { PICK, READING, REVIEW }

/** How one picked file's upload and reading stands. */
sealed interface FileJob {
    data class Uploading(val percent: Int) : FileJob
    data object Reading : FileJob
    data class Found(val count: Int) : FileJob
    /** [detail] is the server's reason (the document's `statusDetail`) or why the call failed. */
    data class Failed(val detail: UiText?) : FileJob
    data object TimedOut : FileJob
}

/** An asset read from a file, with the name of the file it came from. */
data class FoundDraft(val draft: AssetDraft, val file: ImportedFile)

/** What the File tab tells the user once, then forgets. */
sealed interface FileOutcome {
    data class Added(val result: DraftsAccepted) : FileOutcome
    data class Discarded(val count: Int) : FileOutcome
    /** Nothing was waiting at the server (or the property changed): the review just closes. */
    data object Cleared : FileOutcome
    /** The sample spreadsheet was saved where the user chose. */
    data object SampleSaved : FileOutcome
    data class Failed(val message: UiText) : FileOutcome

    /** The review is done with, so the File tab goes back to its list. */
    val closes: Boolean get() = this is Added || this is Discarded || this is Cleared
}

/** A draft being corrected: the text of each field as typed, by field id. */
data class DraftEdit(
    val draftId: String,
    val values: Map<String, String>,
    val saving: Boolean = false,
    val error: UiText? = null,
)

data class FileImportState(
    val step: FileStep = FileStep.PICK,
    val files: List<ImportedFile> = emptyList(),
    /** Why each file that can't be sent would be refused, by file id. */
    val problems: Map<String, UploadProblem> = emptyMap(),
    val jobs: Map<String, FileJob> = emptyMap(),
    val drafts: List<FoundDraft> = emptyList(),
    /** Files (picking) or drafts (review) unticked. */
    val off: Set<String> = emptySet(),
    val createFields: Boolean = false,
    val busy: Boolean = false,
    val outcome: FileOutcome? = null,
    /** The property's fields (active standard ones, Name first), read when the drafts show. */
    val fields: List<DraftField> = emptyList(),
    val edit: DraftEdit? = null,
) {
    /** The fields a draft can be corrected in, as the web's columns: Name, then those with a value in some draft. */
    val columns: List<DraftField>
        get() = fields.filter { f -> f.key == NAME_FIELD_KEY || drafts.any { !it.draft.values[f.id].isNullOrBlank() } }

    val editing: FoundDraft? get() = edit?.let { e -> drafts.firstOrNull { it.draft.id == e.draftId } }

    val reading: Boolean get() = jobs.values.any { it is FileJob.Uploading || it is FileJob.Reading }

    /** Files that will be sent: ticked and not refused. */
    val toSend: List<ImportedFile> get() = files.filter { it.id !in off && it.id !in problems }

    val selectedDrafts: List<FoundDraft> get() = drafts.filter { it.draft.id !in off }

    /** The different things the selected drafts said that no field holds (they become fields with [createFields]). */
    val extraNames: Set<String> get() = selectedDrafts.flatMap { d -> d.draft.extraFields.map { it.name } }.toSet()
}

/**
 * The File tab, as the web's "Add assets from files": the files picked in the system file manager are uploaded to the
 * server (`POST /documents`), which reads them in the background; once read, the assets found in them (drafts) are
 * reviewed here and the ticked ones added (`asset-drafts/bulk/accept`) or all thrown away (`bulk/discard`). The assets
 * added reach the device's lists with the next sync.
 */
@HiltViewModel
class FileImportViewModel @Inject constructor(
    observeAuthState: ObserveAuthStateUseCase,
    downloads: PropertyDownloadUseCases,
    private val useCases: FileImportUseCases,
    private val fileUseCases: FileUseCases,
) : ViewModel() {
    var state by mutableStateOf(FileImportState())
        private set

    private fun set(f: FileImportState.() -> FileImportState) {
        state = state.f()
    }

    /** The property files go to: the current one. Drafts belong to the property they were read for. */
    private var property: String? = null
    private var readJob: Job? = null

    init {
        viewModelScope.launch {
            downloads.observe().map { it.currentId }.distinctUntilChanged().collect { id ->
                if (property != null && id != property) reset(FileOutcome.Cleared)
                property = id
            }
        }
        // A new session starts clean.
        viewModelScope.launch {
            observeAuthState().map { it is AuthState.SignedIn }.distinctUntilChanged().drop(1).collect { reset() }
        }
    }

    /**
     * Files picked in the system file manager, each checked as the server would. They join the files being picked, or
     * start again once a reading found nothing; while files are being read or their assets reviewed they are ignored.
     */
    fun add(uris: List<Uri>) {
        if (uris.isEmpty() || !acceptsFiles) return
        viewModelScope.launch {
            val picked = fileUseCases.describeFiles(uris.map { it.toString() })
            if (!acceptsFiles) return@launch
            set {
                val base = if (step == FileStep.PICK) this else FileImportState()
                val fresh = picked.filter { f -> base.files.none { it.uri == f.uri } }
                val refused = fresh.mapNotNull { f -> useCases.check(f)?.let { f.id to it } }.toMap()
                base.copy(files = base.files + fresh, problems = base.problems + refused, off = base.off + refused.keys)
            }
        }
    }

    /** Ticks or unticks a file (one the server would refuse can't be ticked) or a draft. */
    fun toggle(id: String) = set {
        when {
            busy || reading -> this
            step == FileStep.PICK && id in problems -> this
            id in off -> copy(off = off - id)
            else -> copy(off = off + id)
        }
    }

    fun setCreateFields(on: Boolean) = set { copy(createFields = on) }

    /** Uploads the ticked files together and waits while the server reads them; the drafts found are then shown. */
    fun read() {
        val files = state.toSend
        val prop = property
        if (state.step != FileStep.PICK || files.isEmpty() || prop == null) return
        set { copy(step = FileStep.READING, jobs = files.associate { it.id to FileJob.Uploading(0) }, off = emptySet()) }
        readJob = viewModelScope.launch {
            val found = coroutineScope { files.map { f -> async { readOne(prop, f) } }.awaitAll() }.flatten()
            if (found.isNotEmpty()) {
                val fields = useCases.fields()
                set { copy(step = FileStep.REVIEW, drafts = found, off = emptySet(), fields = fields) }
            }
        }
    }

    // ---- Correcting a draft before it is added (the web edits its review table's cells)

    /** Opens draft [id] to correct its values. */
    fun openDraft(id: String) = set {
        val found = drafts.firstOrNull { it.draft.id == id }
        if (found == null || busy) this
        else copy(edit = DraftEdit(id, columns.associate { f -> f.id to found.draft.values[f.id].orEmpty() }))
    }

    fun editValue(fieldId: String, text: String) = set {
        edit?.takeUnless { it.saving }?.let { copy(edit = it.copy(values = it.values + (fieldId to text), error = null)) } ?: this
    }

    fun closeDraft() = set { if (edit?.saving == true) this else copy(edit = null) }

    /** Sends what changed (`PATCH asset-drafts/{id}`); the draft comes back with the values now certain. */
    fun saveDraft() {
        val edit = state.edit ?: return
        val found = state.editing ?: return
        val prop = property ?: return
        if (edit.saving) return
        set { copy(edit = edit.copy(saving = true, error = null)) }
        viewModelScope.launch {
            try {
                val updated = useCases.updateDraft(prop, found.draft, edit.values)
                set { copy(edit = null, drafts = drafts.map { if (it.draft.id == updated.id) it.copy(draft = updated) else it }) }
            } catch (e: OnlineException) {
                set { copy(edit = this.edit?.copy(saving = false, error = e.error.toUiText())) }
            }
        }
    }

    // ---- Sample spreadsheet

    /** Saves the sample spreadsheet of the property's fields to [destination] (null: the user cancelled). */
    fun saveSample(destination: String?) {
        val prop = property
        if (destination == null || prop == null) return
        viewModelScope.launch {
            try {
                useCases.saveSample(prop, destination)
                set { copy(outcome = FileOutcome.SampleSaved) }
            } catch (e: OnlineException) {
                set { copy(outcome = FileOutcome.Failed(e.error.toUiText())) }
            }
        }
    }

    private suspend fun readOne(prop: String, file: ImportedFile): List<FoundDraft> {
        fun job(j: FileJob) = set { copy(jobs = jobs + (file.id to j)) }
        return try {
            val document = useCases.read(
                prop, file,
                onProgress = { p -> viewModelScope.launch { if (state.jobs[file.id] is FileJob.Uploading) job(FileJob.Uploading(p)) } },
                onUploaded = { job(FileJob.Reading) },
            )
            when (document.status) {
                DocumentStatus.PROCESSING -> { job(FileJob.TimedOut); emptyList() }
                DocumentStatus.FAILED -> { job(FileJob.Failed(document.statusDetail?.let { UiText.Raw(it) })); emptyList() }
                DocumentStatus.READY -> {
                    job(FileJob.Found(document.draftCount))
                    if (document.draftCount == 0) emptyList()
                    else useCases.drafts(prop, document.id).map { FoundDraft(it, file) }
                }
            }
        } catch (e: OnlineException) {
            job(FileJob.Failed(e.error.toUiText()))
            emptyList()
        }
    }

    /** Adds the ticked drafts as assets; the rest stay waiting at the server, as on the web. */
    fun accept() {
        val ids = state.selectedDrafts.map { it.draft.id }
        val prop = property
        if (state.step != FileStep.REVIEW || ids.isEmpty() || state.busy || prop == null) return
        val createFields = state.createFields && state.extraNames.isNotEmpty()
        set { copy(busy = true) }
        viewModelScope.launch {
            try {
                val result = useCases.accept(prop, ids, createFields)
                state = FileImportState(outcome = FileOutcome.Added(result))
            } catch (e: OnlineException) {
                set { copy(busy = false, outcome = FileOutcome.Failed(e.error.toUiText())) }
            }
        }
    }

    /** Throws away what was found (the drafts at the server too) and starts again. */
    fun discard() {
        if (state.busy) return
        readJob?.cancel()
        val ids = state.drafts.map { it.draft.id }
        val prop = property
        if (ids.isEmpty() || prop == null) {
            reset(FileOutcome.Cleared)
            return
        }
        set { copy(busy = true) }
        viewModelScope.launch {
            try {
                val count = useCases.discard(prop, ids)
                state = FileImportState(outcome = FileOutcome.Discarded(count))
            } catch (e: OnlineException) {
                set { copy(busy = false, outcome = FileOutcome.Failed(e.error.toUiText())) }
            }
        }
    }

    private val acceptsFiles: Boolean
        get() = state.step == FileStep.PICK || (state.step == FileStep.READING && !state.reading)

    /** Files picked or assets found are waiting, so the review has something to show. */
    val hasWork: Boolean get() = state.files.isNotEmpty() || state.drafts.isNotEmpty()

    fun outcomeShown() = set { copy(outcome = null) }

    private fun reset(outcome: FileOutcome? = null) {
        readJob?.cancel()
        state = FileImportState(outcome = outcome)
    }
}
