package com.sclera.sclerago.presentation.config

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.domain.model.ClassCount
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.TypeField
import com.sclera.sclerago.domain.usecase.config.AssetTypeUseCases
import com.sclera.sclerago.domain.usecase.config.TypeRow
import com.sclera.sclerago.domain.usecase.config.expandablePaths
import com.sclera.sclerago.domain.usecase.config.nodeAt
import com.sclera.sclerago.domain.usecase.config.totalAssets
import com.sclera.sclerago.domain.usecase.config.visibleRows
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Configuration → Asset types: the property's type catalogue as a list (system types, asset types, sub types, with how many
 * assets each holds) and one type's detail (its sub types and fields). Read only: the app has no calls to change the library.
 */
@HiltViewModel
class AssetTypesViewModel @Inject constructor(private val useCases: AssetTypeUseCases) : ViewModel() {
    private var fieldsJob: Job? = null

    var tree by mutableStateOf<List<ClassCount>>(emptyList())
        private set

    var query by mutableStateOf("")
        private set

    private var expanded by mutableStateOf<Set<List<String>>>(emptySet())

    /** Names from the system type down of the type whose detail is open; null while it is closed. */
    var selected by mutableStateOf<List<String>?>(null)
        private set

    var fields by mutableStateOf<List<TypeField>>(emptyList())
        private set

    var fieldsLoad by mutableStateOf(LoadState.LOADING)
        private set

    init {
        viewModelScope.launch { useCases.observe().collect { tree = it } }
    }

    val rows: List<TypeRow> get() = tree.visibleRows(expanded, query)

    val totalAssets: Int get() = tree.totalAssets()

    /** The open type, or null when none is open or the catalogue no longer has it. */
    val detail: ClassCount? get() = selected?.let(tree::nodeAt)

    val allOpen: Boolean get() = tree.expandablePaths().let { it.isNotEmpty() && expanded.containsAll(it) }

    fun search(text: String) {
        query = text
    }

    fun toggle(path: List<String>) {
        expanded = if (path in expanded) expanded.minusElement(path) else expanded.plusElement(path)
    }

    fun toggleAll() {
        expanded = if (allOpen) emptySet() else tree.expandablePaths()
    }

    fun open(path: List<String>) {
        selected = path
        fields = emptyList()
        fieldsJob?.cancel()
        val id = tree.nodeAt(path)?.formatId
        if (id == null) {
            fieldsLoad = LoadState.READY
            return
        }
        fieldsLoad = LoadState.LOADING
        fieldsJob = viewModelScope.launch {
            try {
                fields = useCases.fields(id)
                fieldsLoad = LoadState.READY
            } catch (e: OnlineException) {
                fieldsLoad = LoadState.FAILED
            }
        }
    }

    fun close() {
        fieldsJob?.cancel()
        selected = null
    }
}
