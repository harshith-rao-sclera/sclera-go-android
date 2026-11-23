package com.sclera.sclerago.presentation.config

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FieldChecks
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.SheetRejectedException
import com.sclera.sclerago.domain.usecase.config.ChecksProblem
import com.sclera.sclerago.domain.usecase.config.DeleteFieldUseCase
import com.sclera.sclerago.domain.usecase.config.FieldProblem
import com.sclera.sclerago.domain.usecase.config.FieldsConfigUseCases
import com.sclera.sclerago.domain.usecase.config.SaveFieldUseCase
import com.sclera.sclerago.domain.usecase.config.SetFieldChecksUseCase
import com.sclera.sclerago.domain.usecase.property.ValidateImportFileUseCase
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.toUiText
import com.sclera.sclerago.presentation.property.PickedSheet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Configuration → Asset fields, read and changed at the server (`/properties/{id}/fields`) as the web does. Each change
 * is sent at once; what the server returns replaces the field shown. Messages for the screen come through [messages].
 */
@HiltViewModel
class FieldsConfigViewModel @Inject constructor(
    private val useCases: FieldsConfigUseCases,
    validateFile: ValidateImportFileUseCase,
) : ViewModel() {
    private var propertyId = ""
    private var loadJob: Job? = null
    private val sheetImport = SheetImportFlow(validateFile)

    /** The Import from Excel sheet; null while it is closed. */
    val importing: SheetImportState? get() = sheetImport.state

    var load by mutableStateOf(LoadState.LOADING)
        private set

    /** Why the fields could not be read (no connection, not allowed, …). */
    var failure by mutableStateOf<UiText?>(null)
        private set

    var fields by mutableStateOf<List<FieldCfg>>(emptyList())
        private set

    private val _messages = Channel<UiText>(Channel.BUFFERED)
    val messages: Flow<UiText> = _messages.receiveAsFlow()

    private fun say(text: UiText) {
        _messages.trySend(text)
    }

    fun open(property: String) {
        propertyId = property
        reload()
    }

    fun retry() = reload()

    private fun reload() {
        loadJob?.cancel()
        load = LoadState.LOADING
        failure = null
        val property = propertyId
        loadJob = viewModelScope.launch {
            try {
                fields = useCases.load(property)
                load = LoadState.READY
            } catch (e: OnlineException) {
                failure = e.error.toUiText()
                load = LoadState.FAILED
            }
        }
    }

    /** Runs a change at the server; a failure is told (never its details). */
    private fun send(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: OnlineException) {
                say(e.error.toUiText())
            }
        }
    }

    private fun replace(f: FieldCfg) {
        fields = fields.map { if (it.id == f.id) f else it }
    }


    fun toggleRequired(f: FieldCfg) = send { replace(useCases.toggleRequired(propertyId, f)) }

    /** Field sheet: [onDone] runs once the field is saved. */
    fun save(before: FieldCfg, after: FieldCfg, onDone: () -> Unit) = send {
        when (val r = useCases.save(propertyId, before, after, fields)) {
            is SaveFieldUseCase.Result.Refused -> say(UiText.Res(r.problem.messageRes()))
            is SaveFieldUseCase.Result.Saved -> {
                replace(r.field)
                say(UiText.Res(R.string.cfg_field_saved))
                onDone()
            }
        }
    }

    /** Add field sheet: [onDone] runs once the fields are added. */
    fun add(drafts: List<FieldCfg>, onDone: () -> Unit) = send {
        val made = useCases.add(propertyId, fields, drafts)
        if (made.isEmpty()) {
            say(UiText.Res(R.string.cfg_field_exists))
        } else {
            fields = fields + made
            say(UiText.Plural(R.plurals.cfg_fields_added, made.size))
            onDone()
        }
    }

    fun delete(f: FieldCfg, onDone: () -> Unit) = send {
        when (useCases.delete(propertyId, f)) {
            DeleteFieldUseCase.Result.DELETED -> {
                fields = fields.filter { it.id != f.id }
                say(UiText.Res(R.string.cfg_field_deleted, listOf(f.label)))
                onDone()
            }
            DeleteFieldUseCase.Result.BUILT_IN -> say(UiText.Res(R.string.cfg_field_built_in, listOf(f.label)))
            DeleteFieldUseCase.Result.IN_USE -> say(UiText.Res(R.string.cfg_field_in_use, listOf(f.label)))
        }
    }

    /** Checks sheet: [onDone] runs once the checks are saved. */
    fun setChecks(f: FieldCfg, checks: FieldChecks, onDone: () -> Unit) = send {
        when (val r = useCases.setChecks(propertyId, f, checks)) {
            is SetFieldChecksUseCase.Result.Refused -> say(UiText.Res(r.problem.messageRes()))
            is SetFieldChecksUseCase.Result.Saved -> {
                replace(r.field)
                say(UiText.Res(R.string.cfg_checks_saved, listOf(f.label)))
                onDone()
            }
        }
    }

    fun removeChecks(f: FieldCfg, onDone: () -> Unit) = send {
        val r = useCases.setChecks(propertyId, f, FieldChecks())
        if (r is SetFieldChecksUseCase.Result.Saved) {
            replace(r.field)
            say(UiText.Res(R.string.cfg_checks_removed, listOf(f.label)))
            onDone()
        }
    }

    // ---- Import from Excel (as the web's Import: the server reads one field per row; the fields are reviewed before adding)

    fun openImport() = sheetImport.open()

    fun closeImport() = sheetImport.close()

    fun pickImport(file: PickedSheet) = sheetImport.pick(file)

    /** Sample Format: saves the server's sample sheet to [destination] (a document the user picked). */
    fun saveImportSample(destination: String) {
        val property = propertyId
        viewModelScope.launch { say(saveSampleMessage { useCases.saveImportSample(property, destination) }) }
    }

    /**
     * Import: the server reads the sheet (nothing is added yet); [onReady] gets the new fields to review in the Add field
     * sheet. Rows left out (names already there, photos, videos and voice memos) and columns not read are told; a
     * refused sheet is told in the sheet, with the rows to fix.
     */
    fun runImport(onReady: (List<FieldCfg>) -> Unit) {
        val file = sheetImport.begin() ?: return
        val property = propertyId
        viewModelScope.launch {
            try {
                val r = useCases.importFields(property, file.uri)
                sheetImport.close()
                val skipped = skippedReasons(r.skipped)
                when {
                    r.fields.isEmpty() && skipped != null -> say(UiText.Res(R.string.cfg_import_fields_nothing, listOf(skipped)))
                    r.fields.isEmpty() -> say(UiText.Res(R.string.cfg_import_fields_none))
                    else -> {
                        skipped?.let { say(UiText.Res(R.string.cfg_import_fields_skipped, listOf(it))) }
                        onReady(r.fields)
                    }
                }
                ignoredColumnsMessage(r.ignoredColumns)?.let(::say)
            } catch (e: SheetRejectedException) {
                sheetImport.refused(refusedMessage(e.rows, R.string.cfg_import_fields_sheet_wrong))
            } catch (e: OnlineException) {
                sheetImport.failed()
                say(importFailure(e))
            }
        }
    }
}

private fun FieldProblem.messageRes(): Int = when (this) {
    FieldProblem.NO_NAME -> R.string.cfg_field_name_required
    FieldProblem.NO_OPTIONS -> R.string.cfg_field_needs_option
    FieldProblem.DUPLICATE -> R.string.cfg_field_exists
}

/** Why a field's checks can't be saved, as the Checks sheet and its toasts say it. */
internal fun ChecksProblem.messageRes(): Int = when (this) {
    ChecksProblem.PATTERN_MISSING -> R.string.cfg_checks_pattern_missing
    ChecksProblem.PATTERN_INVALID -> R.string.cfg_checks_pattern_invalid
    ChecksProblem.LENGTHS_NOT_WHOLE -> R.string.cfg_checks_lengths_whole
    ChecksProblem.MAX_LENGTH_BELOW_ONE -> R.string.cfg_checks_max_length_one
    ChecksProblem.LENGTHS_REVERSED -> R.string.cfg_checks_lengths_reversed
    ChecksProblem.RANGE_NOT_NUMBER -> R.string.cfg_checks_range_number
    ChecksProblem.RANGE_REVERSED -> R.string.cfg_checks_range_reversed
}
