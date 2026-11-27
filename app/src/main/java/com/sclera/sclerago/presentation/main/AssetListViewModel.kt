package com.sclera.sclerago.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.AssetLock
import com.sclera.sclerago.domain.model.AssetFilter
import com.sclera.sclerago.domain.model.AssetFilterOptions
import com.sclera.sclerago.domain.model.FilterCategory
import com.sclera.sclerago.domain.model.toggle
import com.sclera.sclerago.domain.model.togglePerson
import com.sclera.sclerago.domain.model.AssetListData
import com.sclera.sclerago.domain.model.AssetListQuery
import com.sclera.sclerago.domain.model.ChangeRequest
import com.sclera.sclerago.domain.model.DataException
import com.sclera.sclerago.domain.model.LocationFilter
import com.sclera.sclerago.domain.model.Source
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.model.SubmitOutcome
import com.sclera.sclerago.domain.model.ValidationMark
import com.sclera.sclerago.domain.model.isLockedFor
import com.sclera.sclerago.domain.usecase.access.AccessBlocks
import com.sclera.sclerago.domain.usecase.access.ObserveAccessBlocksUseCase
import com.sclera.sclerago.domain.usecase.access.ReviewerOnlyAction
import com.sclera.sclerago.domain.usecase.access.ReviewerOnlyActionsUseCase
import com.sclera.sclerago.domain.usecase.asset.AssetFilterUseCases
import com.sclera.sclerago.domain.usecase.asset.AssetUseCases
import com.sclera.sclerago.domain.usecase.asset.CHANGE_NOTE_MAX
import com.sclera.sclerago.domain.usecase.asset.ChangeAction
import com.sclera.sclerago.domain.usecase.asset.ChangeActionBlock
import com.sclera.sclerago.domain.usecase.asset.ChangeActionOption
import com.sclera.sclerago.domain.usecase.asset.ChangeRequestUseCases
import com.sclera.sclerago.domain.usecase.asset.ObserveSelectionAccessUseCase
import com.sclera.sclerago.domain.usecase.asset.ReloadAssetsUseCase
import com.sclera.sclerago.domain.usecase.asset.SelectionAccess
import com.sclera.sclerago.domain.usecase.asset.someAlreadySubmitted
import com.sclera.sclerago.domain.usecase.asset.withAiSwitch
import com.sclera.sclerago.domain.usecase.config.ObserveAiSwitchUseCase
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.toUiText
import com.sclera.sclerago.presentation.model.message
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * How an asset got onboarded, for each bottom tab's list; the Assets tab shows them all. The Network tab shows the
 * assets of its current mode only: [network] is "wifi" (Wi-Fi network) or "access_point" (Access points).
 */
internal fun Tab.sources(network: Source): Set<Source>? = when (this) {
    Tab.BT -> setOf(Source.BT)
    Tab.NET -> setOf(network)
    Tab.CAMERA -> setOf(Source.CAMERA)
    Tab.FILE -> setOf(Source.FILE)
    Tab.ASSETS -> null
}

/**
 * Which location the lists narrow the downloaded property's assets to, and the Network tab's mode as the source its list
 * shows ([Source.WIFI] or [Source.AP]).
 */
data class AssetListScope(val location: LocationFilter = LocationFilter.All, val network: Source = Source.WIFI)

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
    /**
     * The change-request actions for the selection, those the user can't use included (greyed out). With none, and no
     * [reviewerOnly] ones, nothing ticked has a change to offer (its status), so the dock has no Change action.
     */
    val changeOptions: List<ChangeActionOption> = emptyList(),
    /** The menu of [changeOptions] is open. */
    val changeMenu: Boolean = false,
    /** The change-request action being confirmed (null: none). */
    val changeAction: ChangeAction? = null,
    /** The note for a change request being asked for on the selection. */
    val changeNote: String = "",
    /** That note differs from what it started as (empty, or the one asset's own request): closing asks first. */
    val changeNoteChanged: Boolean = false,
    /** The filter sheet's choices in force (people and types), on every tab. */
    val filter: AssetFilter = AssetFilter(),
    /** The filter sheet, while it is open (null: closed). */
    val filterSheet: FilterSheetState? = null,
    /**
     * The signed-in user's access, kept up to date: what the selection offers is read from it, so it follows a change of
     * group (or the access arriving) at once.
     */
    val access: SelectionAccess = SelectionAccess(),
    /** The tabs whose chip leaves Submit out, as the web's tabs: Submitted, Exception, Validated and Change requests. */
    val noSubmit: Set<Tab> = emptySet(),
    /** Why each kind of button is greyed out for the signed-in user's group (null: it can be used); kept up to date. */
    val blocks: AccessBlocks = AccessBlocks(),
    /** The reviewer-only actions on the selection while the access is still being fetched: on the Change menu, greyed out. */
    val reviewerOnly: List<ReviewerOnlyAction> = emptyList(),
) {
    val selecting: Boolean get() = picked.isNotEmpty()

    /**
     * Submit is on the selection bar of [tab]: the tab's chip doesn't leave it out, and not every ticked asset is signed
     * off already (the web's). Without Collect it shows greyed out ([blocks]).
     */
    fun submitOffered(tab: Tab): Boolean =
        tab !in noSubmit && (pickedAssets.isEmpty() || pickedAssets.any { it.st != Status.VALIDATED })

    /** A ticked asset is signed off, so Move to Managed assets is on the bar (greyed out without Configure). */
    val anyValidated: Boolean get() = pickedAssets.any { it.st == Status.VALIDATED }

    /**
     * Delete is refused for these assets ([AssetLock.SUBMITTED]): one is submitted or signed off and the user isn't a
     * reviewer (lockedForMe; the web leaves Delete off then). Someone else's can't be ticked in the first place.
     */
    val deleteLock: AssetLock? get() = AssetLock.SUBMITTED.takeIf { pickedAssets.any { isLockedFor(it.st, access.reviewer) } }

    /**
     * Edit has nothing to change ([AssetLock.SUBMITTED]): every ticked asset is submitted or signed off and the user isn't
     * a reviewer. With some that can be changed, Edit goes ahead on those and leaves the rest as they are, saying how
     * many (BulkUpdateAssetsUseCase, as the server's check_editable refuses each locked one).
     */
    val editLock: AssetLock? get() =
        AssetLock.SUBMITTED.takeIf { pickedAssets.isNotEmpty() && pickedAssets.all { isLockedFor(it.st, access.reviewer) } }

    /** The Change button has something to offer: change-request actions, or reviewer-only ones shown greyed out. */
    val changeOffered: Boolean get() = changeOptions.isNotEmpty() || reviewerOnly.isNotEmpty()

    /**
     * Submit shows greyed out (a tap says why, the web's "Some are already submitted"): a ticked asset is submitted, an
     * exception or signed off.
     */
    val submitBlocked: Boolean get() = someAlreadySubmitted(pickedAssets)
}

/** The open filter sheet: the choices in force ([draft], each applied as it is made) and what it offers. */
data class FilterSheetState(
    val draft: AssetFilter,
    val options: AssetFilterOptions,
    /** The property's assignees are still being read (none kept on the phone yet): the people row shows placeholders. */
    val peopleLoading: Boolean = false,
)

/**
 * The Assets lists (all tabs): search, status filter, multi-select and the bulk actions on the selection
 * (validate, submit, delete, assign a location). What the user should be told comes out of [notices].
 */
@HiltViewModel
class AssetListViewModel @Inject constructor(
    private val useCases: AssetUseCases,
    private val reloadAssets: ReloadAssetsUseCase,
    private val changeRequests: ChangeRequestUseCases,
    private val filters: AssetFilterUseCases,
    selectionAccess: ObserveSelectionAccessUseCase,
    aiSwitch: ObserveAiSwitchUseCase,
    accessBlocks: ObserveAccessBlocksUseCase,
    private val reviewerOnly: ReviewerOnlyActionsUseCase,
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
        /** The tabs narrowed to assets someone asked an admin to reopen (the Change requests chip). */
        val changeRequests: Set<Tab> = emptySet(),
        /** The tabs narrowed to assets whose validation failed or needs a look (those chips). */
        val validation: Map<Tab, ValidationMark> = emptyMap(),
        /** The tabs narrowed to the assets in Managed Assets (the Managed assets chip, on the Assets tab only). */
        val managed: Set<Tab> = emptySet(),
        /** The tabs narrowed to the assets onboarded over Bluetooth or Wi-Fi (those chips, on the Assets tab only). */
        val onboarded: Map<Tab, Source> = emptyMap(),
        val refreshing: Boolean = false,
        val changeMenu: Boolean = false,
        val changeAction: ChangeAction? = null,
        val changeNote: String = "",
        /** What [changeNote] started as. */
        val changeNoteStart: String = "",
        /** The property the lists show (the filter starts empty on each one). */
        val propertyId: String? = null,
        val filter: AssetFilter = AssetFilter(),
        /** The choices being made in the open filter sheet; null while it is closed. */
        val draft: AssetFilter? = null,
        /** The tab the filter sheet was opened on: its "Show N assets" counts that tab's list. */
        val filterTab: Tab = Tab.ASSETS,
        /** People's names by user id, for the filter sheet. */
        val names: Map<String, String> = emptyMap(),
        /** The property's assignees as kept on the device: the filter sheet lists them all, with or without assets in the tab. */
        val everyone: Set<String> = emptySet(),
        /** People's profile photos by user id, for the filter sheet. */
        val photos: Map<String, String> = emptyMap(),
        /** The filter sheet is reading the property's assignees. */
        val namesLoading: Boolean = false,
    )

    /** The type catalogue by id, for the filter (kept for [toggleFilter], which drops choices under one taken off). */
    private val formats = filters.formats().stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** The signed-in user's access, again whenever it changes. */
    private val access = selectionAccess().stateIn(viewModelScope, SharingStarted.Eagerly, selectionAccess.now())

    /** Why each kind of button is greyed out, again whenever the access or the premise status changes. */
    private val blocks = accessBlocks().stateIn(viewModelScope, SharingStarted.Eagerly, accessBlocks.now())

    init {
        // A change of access (another group, or the access arriving) keeps ticked only the assets the user may still work on.
        viewModelScope.launch {
            access.drop(1).collect {
                val mayWork = useCases.canWorkOn.checker()
                ui.update { u -> if (u.picked.all(mayWork)) u else u.copy(picked = u.picked.filterTo(HashSet(), mayWork)) }
            }
        }
    }

    /** The property's AI switch: off, no asset is offered AI suggestions (the AI suggestion chip counts none). */
    private val aiOn = aiSwitch().stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val state: StateFlow<AssetListState> = combine(
        useCases.observe(), combine(formats, aiOn) { t, a -> t to a }, scope, ui, combine(access, blocks) { a, b -> a to b },
    ) { read, (types, ai), sc, u, (rights, why) ->
        val catalog = read.withAiSwitch(ai)
        val assets = catalog.assets
        fun query(tab: Tab, filter: AssetFilter) = AssetListQuery(
            tab.sources(sc.network), sc.location, u.query, u.statusFilter[tab], changeRequests = tab in u.changeRequests,
            validation = u.validation[tab], filter = filter, managed = tab == Tab.ASSETS && tab in u.managed,
            onboarded = u.onboarded[tab]?.takeIf { tab == Tab.ASSETS },
        )
        // The assets in Managed Assets have their chip on the Assets tab only; no other list ever shows them.
        fun managedFor(tab: Tab) = if (tab == Tab.ASSETS) catalog.managed else emptyList()
        val lists = Tab.entries.associateWith { tab -> useCases.buildList(assets, query(tab, u.filter), types, managedFor(tab)) }
        val sheet = u.draft?.let { draft ->
            val q = query(u.filterTab, draft)
            FilterSheetState(
                draft,
                filters.options(
                    useCases.buildList.scope(if (q.managed) managedFor(u.filterTab) else assets, q), types, draft, filters.me(), u.names, u.everyone,
                    u.photos,
                ),
                peopleLoading = u.namesLoading,
            )
        }
        val pickedAssets = assets.filter { it.id in u.picked }
        AssetListState(
            lists, pickedAssets.mapTo(HashSet()) { it.id }, u.query, u.searchOpen, u.confirmDelete,
            loading = !catalog.loaded, refreshing = u.refreshing, pickedAssets = pickedAssets, statusFilter = u.statusFilter,
            // The sign-off (reviewers and admins) comes first, then the change-request actions (the web's selection bar order).
            // As the asset bar: an action the group doesn't allow, and Request change for a reviewer (who changes the
            // assets instead), are left off the menu; the rest stay, greyed out with why where they can't be used.
            changeOptions = (changeRequests.signOff(pickedAssets) + changeRequests.actions(pickedAssets))
                .filterNot { it.access?.lacksPermission == true || it.blocked == ChangeActionBlock.REVIEWER_CHANGES_IT },
            changeMenu = u.changeMenu, changeAction = u.changeAction, changeNote = u.changeNote,
            changeNoteChanged = u.changeNote != u.changeNoteStart,
            filter = u.filter, filterSheet = sheet,
            access = rights,
            noSubmit = Tab.entries.filterTo(HashSet()) { tab -> tab in u.changeRequests || u.statusFilter[tab]?.let { it in NO_SUBMIT_CHIPS } == true },
            blocks = why,
            // Left off the Change menu when the group can't validate; greyed out only while the access is still loading.
            reviewerOnly = reviewerOnly(pickedAssets).filterNot { it.block.lacksPermission },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AssetListState(loading = true))

    fun setScope(location: LocationFilter) = scope.update { it.copy(location = location) }

    /**
     * The lists now show [propertyId]: nothing chosen on another property carries over. The filter, the status chips
     * (status, Change requests, validation, Managed, Bluetooth / Wi-Fi) go back to All, and the search and a selection
     * of the other property's assets are dropped.
     */
    fun bindProperty(propertyId: String?) {
        if (ui.value.propertyId == propertyId) return
        ui.update {
            it.copy(
                propertyId = propertyId, filter = AssetFilter(), draft = null, names = emptyMap(), everyone = emptySet(), photos = emptyMap(),
                statusFilter = emptyMap(), changeRequests = emptySet(), validation = emptyMap(), managed = emptySet(), onboarded = emptyMap(),
                picked = emptySet(), query = "", searchOpen = false,
                changeMenu = false, changeAction = null, changeNote = "", changeNoteStart = "", confirmDelete = false,
            )
        }
        // Its assignees (names and emails for "Assigned to"), read from the device and, online, from the server.
        propertyId?.takeIf { it.isNotBlank() }?.let { p -> viewModelScope.launch { filters.names.open(p) } }
    }

    // ---- Filter sheet

    /**
     * Opens the filter sheet on [tab] with the choices in force. The people are the property's assignees as kept on the
     * device (their names at once) and those with assets here; online, the assignees are fetched again.
     */
    fun openFilter(tab: Tab) {
        val property = ui.value.propertyId
        val stored = filters.names.storedFor(property)
        val photos = filters.names.photosFor(property)
        ui.update { it.copy(draft = it.filter, filterTab = tab, names = it.names + stored, everyone = stored.keys, photos = photos, namesLoading = true) }
        viewModelScope.launch {
            try {
                val names = filters.names(property)
                val fresh = filters.names.storedFor(property)
                val freshPhotos = filters.names.photosFor(property)
                ui.update { if (it.propertyId == property) it.copy(names = names + fresh, everyone = fresh.keys, photos = freshPhotos) else it }
            } finally {
                ui.update { it.copy(namesLoading = false) }
            }
        }
    }

    /** Closes the sheet; its choices stay in force (each took effect as it was made). */
    fun closeFilter() = ui.update { it.copy(draft = null) }

    fun toggleFilterPerson(userId: String) = changeFilter { it.togglePerson(userId) }

    fun toggleFilter(category: FilterCategory, key: String) = changeFilter { it.toggle(category, key, formats.value) }

    /** Reset: nothing chosen; the list shows every asset again at once. */
    fun resetFilter() = changeFilter { AssetFilter() }

    /** A choice in the open sheet: put in force at once, so the list behind it follows each tap. */
    private fun changeFilter(change: (AssetFilter) -> AssetFilter) = ui.update { u ->
        u.draft?.let { change(it) }?.let { f -> u.copy(draft = f, filter = f) } ?: u
    }

    /** The Network tab's mode changed: its list shows the assets of that scan ([Source.WIFI] or [Source.AP]). */
    fun setNetworkSource(source: Source) = scope.update { it.copy(network = source) }

    /**
     * A status chip; it turns the Change requests and validation chips off (as on the web, Change requests is a tab of
     * its own).
     */
    fun setStatusFilter(tab: Tab, status: Status?) = ui.update {
        it.copy(
            statusFilter = it.statusFilter + (tab to status), changeRequests = it.changeRequests - tab,
            validation = it.validation - tab, managed = it.managed - tab, onboarded = it.onboarded - tab,
        )
    }


    /**
     * The Change requests chip: shows only the assets someone asked an admin to reopen (the web's tab). Like a status
     * chip, tapping it again keeps it on; another chip turns it off.
     */
    fun selectChangeRequests(tab: Tab) = ui.update {
        it.copy(
            statusFilter = it.statusFilter + (tab to null), changeRequests = it.changeRequests + tab,
            validation = it.validation - tab, managed = it.managed - tab, onboarded = it.onboarded - tab,
        )
    }

    /**
     * The Failed validation / Needs a look chip: shows only the assets with that validation [mark] (with "All"). Like a
     * status chip, tapping it again keeps it on; another chip turns it off.
     */
    fun selectValidation(tab: Tab, mark: ValidationMark) = ui.update {
        it.copy(
            statusFilter = it.statusFilter + (tab to null), changeRequests = it.changeRequests - tab,
            validation = it.validation + (tab to mark), managed = it.managed - tab, onboarded = it.onboarded - tab,
        )
    }

    /**
     * The Managed assets chip (the web's Managed assets tab): shows only the assets in Managed Assets, read-only. Like a
     * status chip, tapping it again keeps it on; another chip turns it off. Nothing can be selected there, so a selection
     * made before is cleared.
     */
    fun selectManaged(tab: Tab) = ui.update {
        it.copy(
            statusFilter = it.statusFilter + (tab to null), changeRequests = it.changeRequests - tab,
            validation = it.validation - tab, managed = it.managed + tab, onboarded = it.onboarded - tab, picked = emptySet(),
        )
    }

    /**
     * The Bluetooth / Wi-Fi chip (the web's tabs of those names): shows only the assets onboarded over [source]. Like a
     * status chip, tapping it again keeps it on; another chip turns it off.
     */
    fun selectOnboarded(tab: Tab, source: Source) = ui.update {
        it.copy(
            statusFilter = it.statusFilter + (tab to null), changeRequests = it.changeRequests - tab,
            validation = it.validation - tab, managed = it.managed - tab, onboarded = it.onboarded + (tab to source),
        )
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
        // One in Managed Assets is read-only: nothing to select it for (the web's note).
        if (isManaged(id)) {
            message.tryEmit(UiText.Res(R.string.managed_not_selectable))
            return
        }
        val selectable = useCases.canWorkOn(id)
        if (!selectable && id !in ui.value.picked) message.tryEmit(UiText.Res(notSelectableReason()))
        ui.update { it.copy(picked = useCases.toggleSelection(it.picked, id, selectable)) }
    }

    /** Why an asset can't be picked: the group can only view, or it is assigned to someone else. */
    private fun notSelectableReason(): Int =
        if (useCases.canWorkOn.viewOnly()) R.string.view_only_not_selectable else R.string.asset_lock_not_assigned

    /** [id] is an asset in Managed Assets (read-only, never selected). */
    private fun isManaged(id: String): Boolean = useCases.observe().value.managed.any { it.id == id }

    fun clearSelection() = ui.update { if (it.picked.isEmpty()) it else it.copy(picked = emptySet()) }

    /**
     * Picks everything [tab] shows that the user may work on, or clears the selection when all of it is already picked.
     * As the web, assets assigned to someone else are skipped for a user who isn't an admin, with a note saying so.
     */
    fun selectAll(tab: Tab) {
        val visible = state.value.lists[tab]?.items.orEmpty().map { it.id }
        val before = ui.value.picked
        val mayWork = useCases.canWorkOn.checker()
        val managed = useCases.observe().value.managed.mapTo(HashSet()) { it.id }
        val after = useCases.selectAll(before, visible) { id -> id !in managed && mayWork(id) }
        val skipped = if (after.isEmpty()) 0 else visible.count { it !in after }
        when {
            // A view-only group selects nothing: one line says why, as the web's.
            useCases.canWorkOn.viewOnly() && visible.isNotEmpty() -> message.tryEmit(UiText.Res(R.string.view_only_not_selectable))
            skipped > 0 -> message.tryEmit(UiText.Plural(R.plurals.assets_not_selectable, skipped))
        }
        ui.update { it.copy(picked = after) }
    }

    // ---- Bulk actions

    /**
     * As the web's Submit: complete assets are submitted, incomplete ones (mandatory fields or photos missing) go in as
     * exceptions, and the user is told how many of each.
     */
    fun submitSelected() {
        val s = state.value
        val ids = s.picked
        if (ids.isEmpty() || !s.access.collect) return
        // Greyed out, as the web's: a tap says why and nothing is sent.
        if (s.submitBlocked) {
            message.tryEmit(UiText.Res(R.string.submit_block_some_submitted))
            return
        }
        ui.update { it.copy(picked = emptySet()) }
        write { message.emit(useCases.setStatus(ids, Status.SUBMITTED).toUiText()) }
    }

    /** What a submit did, as the web's submitOutcome: submitted, moved to Exception, already submitted. */
    private fun SubmitOutcome.toUiText(): UiText {
        val parts = listOfNotNull(
            UiText.Plural(R.plurals.assets_submitted, submitted).takeIf { submitted > 0 },
            UiText.Plural(R.plurals.assets_moved_to_exception, exceptions).takeIf { exceptions > 0 },
            UiText.Plural(R.plurals.assets_already_submitted, unchanged).takeIf { unchanged > 0 },
        )
        return when (parts.size) {
            0 -> UiText.Res(R.string.submit_nothing)
            1 -> parts.single()
            2 -> UiText.Res(R.string.submit_outcome_two, parts)
            else -> UiText.Res(R.string.submit_outcome_three, parts)
        }
    }

    // ---- Change requests on the selection (the web's selection bar)

    fun openChangeMenu() = ui.update { it.copy(changeMenu = true) }

    fun closeChange() = ui.update { it.copy(changeMenu = false, changeAction = null, changeNote = "", changeNoteStart = "") }

    /** Picks one of the selection's change-request actions: a blocked one says why, any other asks to confirm. */
    fun pickChange(option: ChangeActionOption) {
        option.access?.let { access ->
            message.tryEmit(UiText.Res(access.message))
            return
        }
        option.blocked?.let { blocked ->
            message.tryEmit(
                UiText.Res(
                    when (blocked) {
                        ChangeActionBlock.NOT_ALL_SUBMITTED -> R.string.change_block_not_submitted
                        ChangeActionBlock.NOT_ALL_REQUESTED -> R.string.change_block_not_requested
                        ChangeActionBlock.NOT_ALL_EXCEPTIONS -> R.string.change_block_not_exceptions
                        ChangeActionBlock.NOT_YOUR_EXCEPTION -> R.string.change_block_not_your_exception
                        ChangeActionBlock.SOME_ARE_DRAFTS -> R.string.change_block_some_drafts
                        ChangeActionBlock.SIGN_OFF_NOT_SUBMITTED -> R.string.sign_off_block_not_submitted
                        ChangeActionBlock.REVIEWER_CHANGES_IT -> R.string.access_request_change_reviewer
                        ChangeActionBlock.OWN_REQUEST -> R.string.access_decline_own_request
                        ChangeActionBlock.NOT_YOUR_REQUEST -> R.string.access_withdraw_not_yours
                    },
                ),
            )
            return
        }
        // Updating a single asset's own request starts from what it said (as the web).
        val single = state.value.pickedAssets.singleOrNull()
        val note = if (option.action == ChangeAction.UPDATE_REQUEST) single?.changeRequest?.note.orEmpty() else ""
        ui.update { it.copy(changeMenu = false, changeAction = option.action, changeNote = note, changeNoteStart = note) }
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
            if (result.failed > 0) {
                // A sign-off leaves what isn't submitted as it is; say so as the web does.
                val failed = if (action == ChangeAction.MARK_VALIDATED) R.plurals.sign_off_not_submitted else R.plurals.change_bulk_failed
                message.emit(UiText.Plural(failed, result.failed))
            }
        }
    }

    /** Who made [request] (name, else email), when known on this device. */
    fun requesterName(request: ChangeRequest): String? = changeRequests.requesterName(request)

    /** The signed-in user made [request]. */
    fun isMine(request: ChangeRequest): Boolean = changeRequests.isMine(request)

    private val ChangeAction.donePlural: Int
        get() = when (this) {
            ChangeAction.MARK_VALIDATED -> R.plurals.sign_off_done
            ChangeAction.REQUEST, ChangeAction.UPDATE_REQUEST -> R.plurals.change_bulk_sent
            ChangeAction.APPROVE -> R.plurals.change_bulk_approved
            ChangeAction.MOVE_TO_DRAFT -> R.plurals.change_bulk_drafted
            ChangeAction.DECLINE -> R.plurals.change_bulk_declined
            ChangeAction.WITHDRAW -> R.plurals.change_bulk_withdrawn
        }

    /** Delete on the bar: asks first, unless the user can't delete these (greyed out on the bar; refused here too). */
    fun askDelete() {
        val s = state.value
        if (s.blocks.collect != null || s.deleteLock != null) return
        ui.update { it.copy(confirmDelete = true) }
    }
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

/** The status chips whose tab leaves Submit off the selection bar (the web's Submitted, Exception and Validated tabs). */
private val NO_SUBMIT_CHIPS = setOf(Status.SUBMITTED, Status.EXCEPTION, Status.VALIDATED)
