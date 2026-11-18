package com.sclera.sclerago.domain.usecase.fileimport

import com.sclera.sclerago.domain.model.AssetDraft
import com.sclera.sclerago.domain.model.DocumentStatus
import com.sclera.sclerago.domain.model.DraftField
import com.sclera.sclerago.domain.model.NAME_FIELD_KEY
import com.sclera.sclerago.domain.model.DraftsAccepted
import com.sclera.sclerago.domain.model.ImportedFile
import com.sclera.sclerago.domain.model.MAX_DOCUMENT_UPLOAD_MB
import com.sclera.sclerago.domain.model.UploadProblem
import com.sclera.sclerago.domain.model.UploadedDocument
import com.sclera.sclerago.domain.model.documentKind
import com.sclera.sclerago.domain.repository.FileImportRepository
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * Why the server would refuse [file] (`POST /documents`): a kind it can't read, an empty file or one over the limit;
 * null when it can go. A size the file manager didn't give (-1) is left to the server.
 */
class CheckUploadUseCase @Inject constructor() {
    operator fun invoke(file: ImportedFile): UploadProblem? = when {
        documentKind(file.name, file.mime) == null -> UploadProblem.UNREADABLE
        file.size == 0L -> UploadProblem.EMPTY
        file.size > MAX_DOCUMENT_UPLOAD_MB * 1024L * 1024L -> UploadProblem.TOO_LARGE
        else -> null
    }
}

/**
 * Uploads [ImportedFile] and waits while the server reads it, as the web does: the document is asked for every
 * [POLL_EVERY_MS] until it is `ready` or `failed`, for up to ten minutes. A document still `processing` after that
 * timed out.
 */
class ReadFileUseCase @Inject constructor(private val files: FileImportRepository) {
    suspend operator fun invoke(
        property: String,
        file: ImportedFile,
        onProgress: (Int) -> Unit,
        onUploaded: () -> Unit,
    ): UploadedDocument {
        var document = files.upload(property, file, onProgress)
        onUploaded()
        repeat(MAX_POLLS) {
            if (document.status != DocumentStatus.PROCESSING) return document
            delay(POLL_EVERY_MS)
            document = files.document(property, document.id)
        }
        return document
    }

    companion object {
        const val POLL_EVERY_MS = 1_500L
        const val MAX_POLLS = 400 // ten minutes
    }
}

/** The assets read from an uploaded file that wait for review. */
class ListDraftsUseCase @Inject constructor(private val files: FileImportRepository) {
    suspend operator fun invoke(property: String, documentId: String): List<AssetDraft> = files.drafts(property, documentId)
}

/** The property's fields a file's values go in, Name first (as the web's review columns). */
class ListDraftFieldsUseCase @Inject constructor(private val files: FileImportRepository) {
    suspend operator fun invoke(): List<DraftField> = files.fields().sortedBy { it.key != NAME_FIELD_KEY }
}

/**
 * Corrects a draft's values as the web does: only the fields whose text changed are sent, trimmed, and an emptied one
 * as null (cleared). Returns the draft unchanged (no call) when nothing changed.
 */
class UpdateDraftUseCase @Inject constructor(private val files: FileImportRepository) {
    suspend operator fun invoke(property: String, draft: AssetDraft, edited: Map<String, String>): AssetDraft {
        val changes = edited.mapValues { (_, text) -> text.trim() }
            .filter { (field, text) -> text != draft.values[field].orEmpty() }
            .mapValues { (_, text) -> text.ifEmpty { null } }
        return if (changes.isEmpty()) draft else files.updateDraft(property, draft.id, changes)
    }
}

/** Saves the sample spreadsheet (a column per field) to [destination]; filled in, it uploads as any file does. */
class SaveSampleUseCase @Inject constructor(private val files: FileImportRepository) {
    suspend operator fun invoke(property: String, destination: String) = files.saveSample(property, destination)
}

/** Adds the drafts [ids] as assets (they reach the device with the next sync). */
class AcceptDraftsUseCase @Inject constructor(private val files: FileImportRepository) {
    suspend operator fun invoke(property: String, ids: List<String>, createFields: Boolean): DraftsAccepted =
        files.accept(property, ids, createFields)
}

/** Throws the drafts [ids] away at the server. */
class DiscardDraftsUseCase @Inject constructor(private val files: FileImportRepository) {
    suspend operator fun invoke(property: String, ids: List<String>): Int = if (ids.isEmpty()) 0 else files.discard(property, ids)
}

/** The File tab's use cases, for injecting as one. */
class FileImportUseCases @Inject constructor(
    val check: CheckUploadUseCase,
    val read: ReadFileUseCase,
    val drafts: ListDraftsUseCase,
    val fields: ListDraftFieldsUseCase,
    val updateDraft: UpdateDraftUseCase,
    val saveSample: SaveSampleUseCase,
    val accept: AcceptDraftsUseCase,
    val discard: DiscardDraftsUseCase,
)
