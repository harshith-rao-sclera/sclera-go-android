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
import com.sclera.sclerago.domain.usecase.asset.CHANGE_NOTE_MAX
import com.sclera.sclerago.domain.usecase.asset.ChangeAction
import com.sclera.sclerago.domain.usecase.asset.ChangeActionBlock
import com.sclera.sclerago.domain.usecase.asset.ChangeActionOption
import com.sclera.sclerago.domain.usecase.asset.ChangeRequestUseCases
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
    /** Drafted assets the last Submit left in draft because they are incomplete: the user may submit them as exceptions. */
    val leftIncomplete: Set<String> = emptySet(),
    /** The change-request actions for the selection (none: the dock's Change action is hidden). */
    val changeOptions: List<ChangeActionOption> = emptyList(),
    /** The menu of [changeOptions] is open. */
    val changeMenu: Boolean = false,
    /** The change-request action being confirmed (null: none). */
    val changeAction: ChangeAction? = null,
    /** The note for a change request being asked for on the selection. */
    val changeNote: String = "",
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
    private val changeRequests: ChangeRequestUseCases,
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
        /** The tabs narrowed to assets someone asked an admin to reopen (the Change requests chip). */
        val changeRequests: Set<Tab> = emptySet(),
        val refreshing: Boolean = false,
        val leftIncomplete: Set<String> = emptySet(),
        val changeMenu: Boolean = false,
        val changeAction: ChangeAction? = null,
        val changeNote: String = "",
    )

    val state: StateFlow<AssetListState> = combine(useCases.observe(), scope, ui) { catalog, sc, u ->
        val assets = catalog.assets
        val admin = useCases.canWorkOn.isAdmin()
        val lists = Tab.entries.associateWith { tab ->
            useCases.buildList(
                assets,
                AssetListQuery(tab.sources, sc.location, u.query, u.statusFilter[tab], tab in u.recollect, tab in u.changeRequests, admin),
            )
        }
        val pickedAssets = assets.filter { it.id in u.picked }
        AssetListState(
            lists, pickedAssets.mapTo(HashSet()) { it.id }, u.query, u.searchOpen, u.confirmDelete,
            loading = !catalog.loaded, refreshing = u.refreshing, pickedAssets = pickedAssets, statusFilter = u.statusFilter,
            leftIncomplete = u.leftIncomplete,
            changeOptions = changeRequests.actions(pickedAssets),
            changeMenu = u.changeMenu, changeAction = u.changeAction, changeNote = u.changeNote,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AssetListState(loading = true))

    fun setScope(location: LocationFilter) {
        scope.value = AssetListScope(location)
    }

    /**
     * A status chip; it turns the Recollect and Change requests chips off (as on the web, Recollect goes with "All" only,
     * and Change requests is a tab of its own).
     */
    fun setStatusFilter(tab: Tab, status: Status?) = ui.update {
        it.copy(statusFilter = it.statusFilter + (tab to status), recollect = it.recollect - tab, changeRequests = it.changeRequests - tab)
    }

    /** The Recollect chip: shows only the assets sent back to be collected again (with "All"), or turns that off. */
    fun toggleRecollect(tab: Tab) = ui.update {
        if (tab in it.recollect) it.copy(recollect = it.recollect - tab)
        else it.copy(statusFilter = it.statusFilter + (tab to null), recollect = it.recollect + tab, changeRequests = it.changeRequests - tab)
    }

    /** The Change requests chip: shows only the assets someone asked an admin to reopen (the web's tab), or turns that off. */
    fun toggleChangeRequests(tab: Tab) = ui.update {
        if (tab in it.changeRequests) it.copy(changeRequests = it.changeRequests - tab)
        else it.copy(statusFilter = it.statusFilter + (tab to null), recollect = it.recollect - tab, changeRequests = it.changeRequests + tab)
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

    /** Picks or unpicks [id]; someone who isn't an admin can't pick an asset assigned to someone else, and is told why. */
    fun toggle(id: String) {
        val selectable = useCases.canWorkOn(id)
        if (!selectable && id !in ui.value.picked) message.tryEmit(UiText.Res(R.string.asset_lock_not_assigned))
        ui.update { it.copy(picked = useCases.toggleSelection(it.picked, id, selectable)) }
    }
    fun clearSelection() = ui.update { if (it.picked.isEmpty()) it else it.copy(picked = emptySet()) }

    /**
     * Picks everything [tab] shows that the user may work on, or clears the selection when all of it is already picked.
     * As the web, assets assigned to someone else are skipped for a user who isn't an admin, with a note saying so.
     */
    fun selectAll(tab: Tab) {
        val visible = state.value.lists[tab]?.items.orEmpty().map { it.id }
        val before = ui.value.picked
        val after = useCases.selectAll(before, visible, useCases.canWorkOn.checker())
        val skipped = if (after.isEmpty()) 0 else visible.count { it !in after }
        if (skipped > 0) message.tryEmit(UiText.Plural(R.plurals.assets_not_selectable, skipped))
        ui.update { it.copy(picked = after) }
    }

    // ---- Bulk actions

    /**
     * As the web's Submit: only the complete assets are submitted. Incomplete drafted ones stay in draft and the user is
     * asked whether to submit them as exceptions ([submitIncompleteAsExceptions]), as the web's exception list does.
     */
    fun submitSelected() {
        val ids = state.value.picked
        if (ids.isEmpty()) return
        ui.update { it.copy(picked = emptySet()) }
        write {
            val left = useCases.setStatus(ids, Status.SUBMITTED)
            val submitted = ids.size - left.size
            if (submitted > 0) message.emit(UiText.Plural(R.plurals.assets_submitted, submitted))
            if (left.isNotEmpty()) ui.update { it.copy(leftIncomplete = left) }
        }
    }

    /** Submits the assets the last Submit left in draft, as exceptions (the web's "submit-exception"). */
    fun submitIncompleteAsExceptions() {
        val ids = ui.value.leftIncomplete
        ui.update { it.copy(leftIncomplete = emptySet()) }
        if (ids.isEmpty()) return
        write {
            useCases.setStatus(ids, Status.SUBMITTED, allowExceptions = true)
            message.emit(UiText.Plural(R.plurals.assets_submitted_as_exceptions, ids.size))
        }
    }

    fun leaveIncompleteInDraft() = ui.update { it.copy(leftIncomplete = emptySet()) }

    // ---- Change requests on the selection (the web's selection bar)

    fun openChangeMenu() = ui.update { it.copy(changeMenu = true) }

    fun closeChange() = ui.update { it.copy(changeMenu = false, changeAction = null, changeNote = "") }

    /** Picks one of the selection's change-request actions: a blocked one says why, any other asks to confirm. */
    fun pickChange(option: ChangeActionOption) {
        option.blocked?.let { blocked ->
            message.tryEmit(
                UiText.Res(
                    when (blocked) {
                        ChangeActionBlock.NOT_ALL_SUBMITTED -> R.string.change_block_not_submitted
                        ChangeActionBlock.NOT_ALL_REQUESTED -> R.string.change_block_not_requested
                    },
                ),
            )
            return
        }
        // Updating a single asset's own request starts from what it said (as the web).
        val single = state.value.pickedAssets.singleOrNull()
        val note = if (option.action == ChangeAction.UPDATE_REQUEST) single?.changeRequest?.note.orEmpty() else ""
        ui.update { it.copy(changeMenu = false, changeAction = option.action, changeNote = note) }
    }

    fun setChangeNote(text: String) = ui.update { it.copy(changeNote = text.take(CHANGE_NOTE_MAX)) }

    /** Applies the confirmed action to every selected asset, then says how many changed (and how many couldn't). */
    fun applyChange() {
        val action = ui.value.changeAction ?: return
        val note = ui.value.changeNote
        val assets = state.value.pickedAssets
        ui.update { it.copy(changeAction = null, changeNote = "", picked = emptySet()) }
        if (assets.isEmpty()) return
        write {
            val result = changeRequests.apply(action, assets, note)
            if (result.done > 0) message.emit(UiText.Plural(action.donePlural, result.done))
            if (result.failed > 0) message.emit(UiText.Plural(R.plurals.change_bulk_failed, result.failed))
        }
    }

    private val ChangeAction.donePlural: Int
        get() = when (this) {
            ChangeAction.REQUEST, ChangeAction.UPDATE_REQUEST -> R.plurals.change_bulk_sent
            ChangeAction.MOVE_TO_DRAFT -> R.plurals.change_bulk_drafted
            ChangeAction.DECLINE -> R.plurals.change_bulk_declined
            ChangeAction.WITHDRAW -> R.plurals.change_bulk_withdrawn
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
