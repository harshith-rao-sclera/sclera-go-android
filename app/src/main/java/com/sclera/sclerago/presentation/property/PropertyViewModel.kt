package com.sclera.sclerago.presentation.property

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.domain.model.PropertySummary
import com.sclera.sclerago.domain.usecase.media.WritePropertyTemplateUseCase
import com.sclera.sclerago.domain.usecase.property.AddPropertyUseCase
import com.sclera.sclerago.domain.usecase.property.ImportFileProblem
import com.sclera.sclerago.domain.usecase.property.PropertyProblem
import com.sclera.sclerago.domain.usecase.property.PropertyUseCases
import com.sclera.sclerago.presentation.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.io.OutputStream
import javax.inject.Inject

/** What the Property list shows: the matching rows, the totals, which sheet is open, and a message to flash. */
data class PropertyListState(
    val query: String = "",
    val rows: List<PropertySummary> = emptyList(),
    /** Every property (not only the matching rows), for the Add form's duplicate check. */
    val properties: List<Property> = emptyList(),
    val propertyTotal: Int = 0,
    val addOpen: Boolean = false,
    val importOpen: Boolean = false,
    val notice: UiText? = null,
)

/** Property list screen: search, add and import. Opening a property is the caller's (navigation) job. */
@HiltViewModel
class PropertyViewModel @Inject constructor(
    private val useCases: PropertyUseCases,
    private val writeTemplate: WritePropertyTemplateUseCase,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val flags = MutableStateFlow(Flags())

    private data class Flags(val addOpen: Boolean = false, val importOpen: Boolean = false, val notice: UiText? = null)

    val state: StateFlow<PropertyListState> =
        combine(useCases.summaries(), query, flags) { all, q, f ->
            PropertyListState(
                query = q,
                rows = useCases.filter(all, q),
                properties = all.map { it.property },
                propertyTotal = all.size,
                addOpen = f.addOpen,
                importOpen = f.importOpen,
                notice = f.notice,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, PropertyListState())

    fun setQuery(value: String) {
        query.value = value
    }

    fun openAdd() = flags.update { it.copy(addOpen = true) }
    fun closeAdd() = flags.update { it.copy(addOpen = false) }
    fun openImport() = flags.update { it.copy(importOpen = true) }
    fun closeImport() = flags.update { it.copy(importOpen = false) }
    /** Back to a fresh list (called when the screen is left). */
    fun reset() {
        query.value = ""
        flags.value = Flags()
    }

    fun consumeNotice() = flags.update { it.copy(notice = null) }

    /** Adds [draft]; returns why it can't be added, or null once it is (the sheet closes and a notice is set). */
    fun addProperty(draft: Property): UiText? = when (val r = useCases.add(draft)) {
        is AddPropertyUseCase.Result.Invalid -> r.problem.toUiText(r.name)
        is AddPropertyUseCase.Result.Added -> {
            flags.update { it.copy(addOpen = false, notice = UiText.Res(R.string.property_added, listOf(r.property.name))) }
            null
        }
    }

    /** Why the picked file can't be imported, or null when it can. */
    fun importProblem(name: String, size: Long): UiText? = useCases.validateImport(name, size)?.toUiText()

    /** The server import isn't connected yet: close the sheet and say so. */
    fun importFile(name: String) {
        flags.update { it.copy(importOpen = false, notice = UiText.Res(R.string.property_import_notice, listOf(name))) }
    }

    fun writeSample(out: OutputStream) = writeTemplate(out)
}

internal fun PropertyProblem.toUiText(name: String): UiText = when (this) {
    PropertyProblem.NAME_REQUIRED -> UiText.Res(R.string.property_problem_name)
    PropertyProblem.TYPE_REQUIRED -> UiText.Res(R.string.property_problem_type)
    PropertyProblem.DUPLICATE -> UiText.Res(R.string.property_problem_duplicate, listOf(name))
}

internal fun ImportFileProblem.toUiText(): UiText = when (this) {
    ImportFileProblem.NOT_XLSX -> UiText.Res(R.string.import_error_type)
    ImportFileProblem.TOO_LARGE -> UiText.Res(R.string.import_error_size)
}
