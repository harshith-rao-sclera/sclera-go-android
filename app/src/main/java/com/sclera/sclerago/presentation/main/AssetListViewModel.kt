package com.sclera.sclerago.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.AssetListData
import com.sclera.sclerago.domain.model.AssetListQuery
import com.sclera.sclerago.domain.model.LocValue
import com.sclera.sclerago.domain.model.LocationFilter
import com.sclera.sclerago.domain.model.Source
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.usecase.asset.AssetUseCases
import com.sclera.sclerago.presentation.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** How an asset got onboarded, for each bottom tab's list; the Assets tab shows them all. */
internal val Tab.sources: Set<Source>?
    get() = when (this) {
        Tab.BT -> setOf(Source.BT)
        Tab.NET -> setOf(Source.WIFI, Source.AP)
        Tab.CAMERA -> setOf(Source.CAMERA)
        Tab.FILE -> setOf(Source.FILE)
        Tab.ASSETS -> null
    }

/** Which property's assets, narrowed to which location, the lists are showing now. */
data class AssetListScope(val property: String = "", val location: LocationFilter = LocationFilter.All)

/** What the asset lists show: one list per tab, the selection, the search and the pending bulk delete. */
data class AssetListState(
    val lists: Map<Tab, AssetListData> = emptyMap(),
    /** Only ids that are still in the current property count as selected. */
    val picked: Set<Int> = emptySet(),
    val query: String = "",
    val searchOpen: Boolean = false,
    val confirmDelete: Boolean = false,
) {
    val selecting: Boolean get() = picked.isNotEmpty()
}

/**
 * The Assets lists (all tabs): search, status filter, multi-select and the bulk actions on the selection
 * (validate, submit, delete, assign a location). What the user should be told comes out of [notices].
 */
@HiltViewModel
class AssetListViewModel @Inject constructor(private val useCases: AssetUseCases) : ViewModel() {
    private val scope = MutableStateFlow(AssetListScope())
    private val ui = MutableStateFlow(Ui())
    private val message = MutableSharedFlow<UiText>(extraBufferCapacity = 8)

    /** Messages to flash after a bulk action. */
    val notices: SharedFlow<UiText> = message.asSharedFlow()

    private data class Ui(
        val picked: Set<Int> = emptySet(),
        val query: String = "",
        val searchOpen: Boolean = false,
        val confirmDelete: Boolean = false,
        val statusFilter: Map<Tab, Status?> = emptyMap(),
    )

    val state: StateFlow<AssetListState> = combine(useCases.observe(), scope, ui) { catalog, sc, u ->
        val assets = catalog.assetsIn(sc.property)
        val lists = Tab.entries.associateWith { tab ->
            useCases.buildList(assets, AssetListQuery(tab.sources, sc.location, u.query, u.statusFilter[tab]))
        }
        AssetListState(lists, u.picked.filterTo(HashSet()) { id -> assets.any { it.id == id } }, u.query, u.searchOpen, u.confirmDelete)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AssetListState())

    fun setScope(property: String, location: LocationFilter) {
        scope.value = AssetListScope(property, location)
    }

    fun setStatusFilter(tab: Tab, status: Status?) = ui.update { it.copy(statusFilter = it.statusFilter + (tab to status)) }

    // ---- Search

    fun openSearch() = ui.update { it.copy(searchOpen = true) }
    fun closeSearch() = ui.update { it.copy(searchOpen = false, query = "") }
    fun setQuery(text: String) = ui.update { it.copy(query = text) }

    // ---- Selection

    fun toggle(id: Int) = ui.update { it.copy(picked = useCases.toggleSelection(it.picked, id)) }
    fun clearSelection() = ui.update { if (it.picked.isEmpty()) it else it.copy(picked = emptySet()) }

    /** Picks everything [tab] shows, or clears the selection when all of it is already picked. */
    fun selectAll(tab: Tab) {
        val visible = state.value.lists[tab]?.items.orEmpty().map { it.id }
        ui.update { it.copy(picked = useCases.selectAll(it.picked, visible)) }
    }

    // ---- Bulk actions

    fun validateSelected() = changeStatus(Status.VALIDATED, R.plurals.assets_validated)
    fun submitSelected() = changeStatus(Status.SUBMITTED, R.plurals.assets_submitted)

    private fun changeStatus(status: Status, plural: Int) {
        val ids = state.value.picked
        if (ids.isEmpty()) return
        useCases.setStatus(ids, status)
        ui.update { it.copy(picked = emptySet()) }
        message.tryEmit(UiText.Plural(plural, ids.size))
    }

    fun askDelete() = ui.update { it.copy(confirmDelete = true) }
    fun cancelDelete() = ui.update { it.copy(confirmDelete = false) }

    fun deleteSelected() {
        val ids = state.value.picked
        ui.update { it.copy(picked = emptySet(), confirmDelete = false) }
        if (ids.isEmpty()) return
        useCases.delete(ids)
        message.tryEmit(UiText.Plural(R.plurals.assets_deleted, ids.size))
    }

    /** Gives the selected assets [location], labelled [label] in the message. */
    fun assignSelected(location: LocValue, label: String) {
        val ids = state.value.picked
        ui.update { it.copy(picked = emptySet()) }
        if (ids.isEmpty()) return
        useCases.assignLocation(ids, location)
        message.tryEmit(UiText.Plural(R.plurals.assets_assigned, ids.size, listOf(ids.size, label)))
    }
}
