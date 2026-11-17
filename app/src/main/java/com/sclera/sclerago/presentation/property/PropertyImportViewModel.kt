package com.sclera.sclerago.presentation.property

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.ImportPlan
import com.sclera.sclerago.domain.model.ImportResult
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.usecase.property.ImportPropertiesUseCase
import com.sclera.sclerago.domain.usecase.property.PropertyImportUseCases
import com.sclera.sclerago.domain.usecase.property.ValidateImportFileUseCase
import com.sclera.sclerago.presentation.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A spreadsheet the user picked: its content [uri], and the [name] and [size] its provider reported (checked, not trusted). */
data class PickedSheet(val uri: String, val name: String, val size: Long)

/**
 * The Import sheet: the file picked (first step), then once the server has read it the [plan] and the [mapping]
 * (detail key → column heading) being checked ("Mapping Data"). [incomplete] marks a required detail with no column.
 */
data class ImportUiState(
    val file: PickedSheet? = null,
    val error: UiText? = null,
    val loading: Boolean = false,
    val plan: ImportPlan? = null,
    val mapping: Map<String, String> = emptyMap(),
    val incomplete: Boolean = false,
)

/** Import properties from a spreadsheet (property list → Import), as the web's Import on the new API. */
@HiltViewModel
class PropertyImportViewModel @Inject constructor(
    private val useCases: PropertyImportUseCases,
    private val validateFile: ValidateImportFileUseCase,
) : ViewModel() {
    var state by mutableStateOf(ImportUiState())
        private set

    private val message = MutableSharedFlow<UiText>(extraBufferCapacity = 4)

    /** What happened, to flash on the page. */
    val notices: SharedFlow<UiText> = message.asSharedFlow()

    /** A fresh sheet (it opens empty each time, as the web's). */
    fun reset() {
        state = ImportUiState()
    }

    /** A file was picked: kept when it is an .xlsx of at most 100MB, otherwise the line under it says why. */
    fun pick(file: PickedSheet) {
        val problem = validateFile(file.name, file.size)
        state = state.copy(file = file.takeIf { problem == null }, error = problem?.toUiText(), plan = null, mapping = emptyMap())
    }

    /** Sample Format: saves the server's sample sheet where the user chose. */
    fun saveSample(destination: String) {
        viewModelScope.launch {
            try {
                useCases.saveTemplate(destination)
            } catch (e: OnlineException) {
                message.emit(if (e.error == OnlineError.Offline) UiText.Res(R.string.property_offline_import) else UiText.Res(R.string.import_error_sample))
            }
        }
    }

    /** Proceed: the server reads the file and proposes a column for each detail; then the mapping is checked. */
    fun proceed() {
        val file = state.file ?: return
        if (state.loading) return
        state = state.copy(loading = true)
        viewModelScope.launch {
            state = try {
                val plan = useCases.prepare(file.uri)
                state.copy(loading = false, plan = plan, mapping = plan.mapping, incomplete = false)
            } catch (e: OnlineException) {
                message.emit(if (e.error == OnlineError.Offline) UiText.Res(R.string.property_offline_import) else UiText.Res(R.string.import_read_failed))
                state.copy(loading = false)
            }
        }
    }

    /** Reads detail [target] from column [heading], or from no column when it is null. */
    fun map(target: String, heading: String?) {
        val mapping = if (heading == null) state.mapping - target else state.mapping + (target to heading)
        state = state.copy(mapping = mapping)
    }

    /** Import: needs a column for the property's name; then imports the rows, says what happened and calls [onDone]. */
    fun import(onDone: () -> Unit) {
        val file = state.file ?: return
        val plan = state.plan ?: return
        if (state.loading) return
        state = state.copy(loading = true)
        viewModelScope.launch {
            try {
                when (val r = useCases.import(file.uri, plan.targets, state.mapping)) {
                    ImportPropertiesUseCase.Result.MissingRequired -> state = state.copy(loading = false, incomplete = true)
                    is ImportPropertiesUseCase.Result.Imported -> {
                        state = ImportUiState()
                        message.emit(done(r.result))
                        onDone()
                    }
                }
            } catch (e: OnlineException) {
                state = state.copy(loading = false, incomplete = false)
                message.emit(if (e.error == OnlineError.Offline) UiText.Res(R.string.property_offline_import) else UiText.Res(R.string.import_failed))
            }
        }
    }

    /** "3 properties imported; 1 skipped because the name is already in use (Harbour Point).", as the web says it. */
    private fun done(r: ImportResult): UiText =
        if (r.skipped.isEmpty()) UiText.Plural(R.plurals.import_done, r.created, listOf(r.created))
        else UiText.Plural(R.plurals.import_done_skipped, r.created, listOf(r.created, r.skipped.size, r.skipped.joinToString(", ")))
}
