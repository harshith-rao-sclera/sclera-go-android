package com.sclera.sclerago.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.AssetListData
import com.sclera.sclerago.domain.model.AssetListQuery
import com.sclera.sclerago.domain.model.DataException
import com.sclera.sclerago.domain.model.LocationFilter
import com.sclera.sclerago.domain.model.Source
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.usecase.asset.AssetUseCases
import com.sclera.sclerago.domain.usecase.asset.ReloadAssetsUseCase
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.toUiText
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
import kotlinx.coroutines.launch
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

/** Which location the lists narrow the downloaded property's assets to. */
data class AssetListScope(val location: LocationFilter = LocationFilter.All)

/** What the asset lists show: one list per tab, the selection, the search and the pending bulk delete. */
data class AssetListState(
    val lists: Map<Tab, AssetListData> = emptyMap(),
    /** Only ids that are still in the current property count as selected. */
    val picked: Set<String> = emptySet(),
    val query: String = "",
    val searchOpen: Boolean = false,
    val confirmDelete: Boolean = false,
    /** The property's data is still being opened, so the assets aren't known yet: the lists show a loader. */
    val loading: Boolean = false,
    /** A pull to refresh is reading the property's assets again. */
    val refreshing: Boolean = false,
    /** The selected assets themselves (the ones in [picked]). */
    val pickedAssets: List<Asset> = emptyList(),
    /** The status chip each tab is filtered by (null: all). */
    val statusFilter: Map<Tab, Status?> = emptyMap(),
) {
    val selecting: Boolean get() = picked.isNotEmpty()
}

/**
 * The Assets lists (all tabs): search, status filter, multi-select and the bulk actions on the selection
 * (validate, submit, delete, assign a location). What the user should be told comes out of [notices].
 */
@HiltViewModel
class AssetListViewModel @Inject constructor(
    private val useCases: AssetUseCases,
    private val reloadAssets: ReloadAssetsUseCase,
) : ViewModel() {
    private val scope = MutableStateFlow(AssetListScope())
    private val ui = MutableStateFlow(Ui())
    private val message = MutableSharedFlow<UiText>(extraBufferCapacity = 8)

    /** Messages to flash after a bulk action. */
    val notices: SharedFlow<UiText> = message.asSharedFlow()

    private data class Ui(
        val picked: Set<String> = emptySet(),
        val query: String = "",
        val searchOpen: Boolean = false,
        val confirmDelete: Boolean = false,
        val statusFilter: Map<Tab, Status?> = emptyMap(),
        /** The tabs narrowed to assets sent back to be collected again (the Recollect chip). */
        val recollect: Set<Tab> = emptySet(),
        val refreshing: Boolean = false,
    )

    val state: StateFlow<AssetListState> = combine(useCases.observe(), scope, ui) { catalog, sc, u ->
        val assets = catalog.assets
        val lists = Tab.entries.associateWith { tab ->
            useCases.buildList(assets, AssetListQuery(tab.sources, sc.location, u.query, u.statusFilter[tab], tab in u.recollect))
        }
        val pickedAssets = assets.filter { it.id in u.picked }
        AssetListState(
            lists, pickedAssets.mapTo(HashSet()) { it.id }, u.query, u.searchOpen, u.confirmDelete,
            loading = !catalog.loaded, refreshing = u.refreshing, pickedAssets = pickedAssets, statusFilter = u.statusFilter,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AssetListState(loading = true))

    fun setScope(location: LocationFilter) {
        scope.value = AssetListScope(location)
    }

    /** A status chip; it turns the Recollect chip off (as on the web, Recollect goes with "All" only). */
    fun setStatusFilter(tab: Tab, status: Status?) =
        ui.update { it.copy(statusFilter = it.statusFilter + (tab to status), recollect = it.recollect - tab) }

    /** The Recollect chip: shows only the assets sent back to be collected again (with "All"), or turns that off. */
    fun toggleRecollect(tab: Tab) = ui.update {
        if (tab in it.recollect) it.copy(recollect = it.recollect - tab)
        else it.copy(statusFilter = it.statusFilter + (tab to null), recollect = it.recollect + tab)
    }

    /** Pull to refresh: reads the property's assets on this device again; the background sync keeps them up to date. */
    fun refresh() {
        if (ui.value.refreshing) return
        ui.update { it.copy(refreshing = true) }
        reloadAssets()
        ui.update { it.copy(refreshing = false) }
    }

    // ---- Search

    fun openSearch() = ui.update { it.copy(searchOpen = true) }
    fun closeSearch() = ui.update { it.copy(searchOpen = false, query = "") }
    fun setQuery(text: String) = ui.update { it.copy(query = text) }

    // ---- Selection

    fun toggle(id: String) = ui.update { it.copy(picked = useCases.toggleSelection(it.picked, id)) }
    fun clearSelection() = ui.update { if (it.picked.isEmpty()) it else it.copy(picked = emptySet()) }

    /** Picks everything [tab] shows, or clears the selection when all of it is already picked. */
    fun selectAll(tab: Tab) {
        val visible = state.value.lists[tab]?.items.orEmpty().map { it.id }
        ui.update { it.copy(picked = useCases.selectAll(it.picked, visible)) }
    }

    // ---- Bulk actions

    /** The signed-in user may sign assets off (the server's validate permission); the Validate button shows only then. */
    fun submitSelected() = changeStatus(Status.SUBMITTED, R.plurals.assets_submitted)

    private fun changeStatus(status: Status, plural: Int) {
        val ids = state.value.picked
        if (ids.isEmpty()) return
        ui.update { it.copy(picked = emptySet()) }
        write {
            useCases.setStatus(ids, status)
            message.emit(UiText.Plural(plural, ids.size))
        }
    }

    fun askDelete() = ui.update { it.copy(confirmDelete = true) }
    fun cancelDelete() = ui.update { it.copy(confirmDelete = false) }

    fun deleteSelected() {
        val ids = state.value.picked
        ui.update { it.copy(picked = emptySet(), confirmDelete = false) }
        if (ids.isEmpty()) return
        write {
            useCases.delete(ids)
            message.emit(UiText.Plural(R.plurals.assets_deleted, ids.size))
        }
    }

    /** Gives the selected assets [location], labelled [label] in the message. */
    /** Makes a change to the offline data in the background; when it is refused or fails the user is told why. */
    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: DataException) {
                message.emit(e.error.toUiText())
            }
        }
    }
}
