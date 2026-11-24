package com.sclera.sclerago.presentation.config

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.PlacesImported
import com.sclera.sclerago.domain.model.SheetSkip
import com.sclera.sclerago.domain.model.SkipReason
import com.sclera.sclerago.domain.usecase.property.ValidateImportFileUseCase
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.toUiText
import com.sclera.sclerago.presentation.components.GuardedClose
import com.sclera.sclerago.presentation.model.reason
import com.sclera.sclerago.presentation.property.IMPORT_PICK_TYPES
import com.sclera.sclerago.presentation.property.ImportSheetContent
import com.sclera.sclerago.presentation.property.PickedSheet
import com.sclera.sclerago.presentation.property.SAMPLE_NAME
import com.sclera.sclerago.presentation.property.XLSX_MIME
import com.sclera.sclerago.presentation.property.pickedSheet
import com.sclera.sclerago.presentation.property.toUiText

// Configuration's spreadsheet imports (Building info → Import buildings, Asset fields → Import from Excel), as the web's
// Import dialog on the new API: choose an .xlsx (Sample Format downloads the server's sample), then Import; the server
// reads the sheet, matching its columns by heading.

/** The Import sheet: the file picked, why it can't be used (or why the server refused it), and whether it is being read. */
data class SheetImportState(val file: PickedSheet? = null, val error: UiText? = null, val loading: Boolean = false)

/**
 * The Import sheet's steps that are the same for both imports, kept by a ViewModel: [state] is null while the sheet is
 * closed. A picked file is kept when it is an .xlsx of at most 100MB ([validate]); otherwise the sheet says why.
 */
internal class SheetImportFlow(private val validate: ValidateImportFileUseCase) {
    var state by mutableStateOf<SheetImportState?>(null)
        private set

    fun open() {
        state = SheetImportState()
    }

    fun close() {
        state = null
    }

    fun pick(file: PickedSheet) {
        val problem = validate(file.name, file.size)
        state = state?.copy(file = file.takeIf { problem == null }, error = problem?.toUiText())
    }

    /** The file to send, now marked as being read; null when there is none or it is already being read. */
    fun begin(): PickedSheet? {
        val s = state ?: return null
        if (s.loading || s.file == null) return null
        state = s.copy(loading = true, error = null)
        return s.file
    }

    /** The server refused the sheet: the sheet stays open and says why, so a corrected file can be picked. */
    fun refused(why: UiText) {
        state = state?.copy(loading = false, error = why)
    }

    /** The call failed (the message is flashed): the sheet stays open to try again. */
    fun failed() {
        state = state?.copy(loading = false)
    }
}

/** Saves a sample sheet with [save] (false: it couldn't be written) and says how it went. */
internal suspend fun saveSampleMessage(save: suspend () -> Boolean): UiText = try {
    if (save()) UiText.Res(R.string.cfg_import_sample_saved) else UiText.Res(R.string.import_error_sample)
} catch (e: OnlineException) {
    if (e.error == OnlineError.Offline) e.error.toUiText() else UiText.Res(R.string.import_error_sample)
}

/** Why an import call failed: a file that couldn't be read or sent is told as such, other failures as usual. */
internal fun importFailure(e: OnlineException): UiText =
    if (e.error == OnlineError.Invalid) UiText.Res(R.string.cfg_import_unreadable) else e.error.toUiText()

/** How many rows a refusal lists before "…". */
private const val ROWS_SHOWN = 10

/** "Nothing was imported. Fix rows 3, 5 and 8 …", or [wholeSheet] when the server named no row (a column is missing, …). */
internal fun refusedMessage(rows: List<Int>, wholeSheet: Int): UiText {
    if (rows.isEmpty()) return UiText.Res(wholeSheet)
    val shown = rows.take(ROWS_SHOWN).joinToString(", ") + if (rows.size > ROWS_SHOWN) ", …" else ""
    return UiText.Plural(R.plurals.cfg_import_rows_to_fix, rows.size, listOf(shown))
}

/** "Imported: 2 buildings, 3 floors and 6 locations added; 1 location already there, skipped.", as the web says it. */
internal fun placesImportedMessage(r: PlacesImported): UiText {
    val buildings = UiText.Plural(R.plurals.cfg_import_n_buildings, r.buildings)
    val floors = UiText.Plural(R.plurals.cfg_import_n_floors, r.floors)
    val locations = UiText.Plural(R.plurals.cfg_import_n_locations, r.locations)
    return if (r.skipped.isEmpty()) {
        UiText.Res(R.string.cfg_import_places_done, listOf(buildings, floors, locations))
    } else {
        UiText.Plural(R.plurals.cfg_import_places_done_skipped, r.skipped.size, listOf(r.skipped.size, buildings, floors, locations))
    }
}

/** "Columns not imported: Notes, Owner" (headings the server doesn't read), or null when every column was read. */
internal fun ignoredColumnsMessage(columns: List<String>): UiText? =
    columns.takeIf { it.isNotEmpty() }?.let { UiText.Res(R.string.cfg_import_columns_ignored, listOf(it.joinToString(", "))) }

/** Why rows were left out of a field import: "2 already exist; 1 photo, video or voice memo: add it in Media", or null. */
internal fun skippedReasons(skipped: List<SheetSkip>): UiText? {
    val media = skipped.count { it.reason == SkipReason.MEDIA }
    val existing = skipped.size - media
    val parts = listOfNotNull(
        existing.takeIf { it > 0 }?.let { UiText.Plural(R.plurals.cfg_import_skip_existing, it) },
        media.takeIf { it > 0 }?.let { UiText.Plural(R.plurals.cfg_import_skip_media, it) },
    )
    return when (parts.size) {
        0 -> null
        1 -> parts[0]
        else -> UiText.Res(R.string.cfg_import_skip_both, parts)
    }
}

/**
 * The Import sheet for Import buildings and Import from Excel: choose an .xlsx (Sample Format saves the server's sample
 * where the user picks), then Import. A server refusal shows under the file, so a corrected one can be chosen.
 */
@Composable
internal fun ConfigImportSheet(
    state: SheetImportState,
    onPick: (PickedSheet) -> Unit,
    onSample: (destination: String) -> Unit,
    onImport: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onPick(pickedSheet(context, uri))
    }
    val sampleSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX_MIME)) { uri ->
        if (uri != null) onSample(uri.toString())
    }
    // The sheet only opens from a button greyed out without Configure; should the access change while it is open, Import
    // is greyed out with the reason.
    val block = LocalConfigBlock.current
    GuardedClose(state.file != null && !state.loading, onClose) { close ->
        ImportSheetContent(
            state.file?.name, state.file?.size ?: -1, state.error, close,
            onChoose = { chooser.launch(IMPORT_PICK_TYPES) },
            onSample = { sampleSaver.launch(SAMPLE_NAME) },
            onProceed = { if (block == null) onImport() },
            loading = state.loading,
            actionLabel = stringResource(R.string.import_action),
            importReason = block.reason(),
        )
    }
}
