package com.sclera.sclerago.presentation.integration

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.AccessBlock
import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.MovePreview
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.VdmsAssignee
import com.sclera.sclerago.domain.usecase.integration.ManagedAssetsUseCases
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.model.reason
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The Move to managed assets sheet: the assets it moves, the network (required) and the assignee (optional). */
data class MoveSheetState(
    val ids: Set<String>,
    val network: String? = null,
    val query: String = "",
    val assignees: List<VdmsAssignee> = emptyList(),
    val searching: Boolean = true,
    val assignee: String? = null,
    /** What the move would take (null until known, or when it couldn't be asked): the places VDMS lacks go there first. */
    val preview: MovePreview? = null,
)

/** Why "Move to Managed assets" can't be used for the property, as the web says it on the button. */
enum class MoveBlock { NOT_LINKED, API_MISSING, CHECK_FAILED }

data class MoveUiState(
    val propertyId: String = "",
    /**
     * Why the signed-in user can't move assets to Managed Assets (it needs Configure), null when they can. The action still
     * shows on validated assets, greyed out, and says why ([MoveToManagedViewModel.blockedReason]). Nothing is allowed
     * until the access is known.
     */
    val access: AccessBlock? = AccessBlock.CHECKING,
    /** Why the property's assets can't be moved (null: linked to a VDMS). Not linked until the check says otherwise, as on the web. */
    val block: MoveBlock? = MoveBlock.NOT_LINKED,
    /** The linked VDMS's network names, fetched once it is known to be linked (as the web does). */
    val networks: List<String> = emptyList(),
    /** The networks are being fetched (the property is linked; its network names aren't in yet). */
    val networksLoading: Boolean = false,
    val sheet: MoveSheetState? = null,
)

/** "Move to Managed assets" on the asset list selection, as the web's asset list: validated assets go to the linked VDMS. */
@HiltViewModel
class MoveToManagedViewModel @Inject constructor(
    private val useCases: ManagedAssetsUseCases,
) : ViewModel() {
    var state by mutableStateOf(MoveUiState())
        private set

    private val message = MutableSharedFlow<UiText>(extraBufferCapacity = 8)

    /** Messages to flash on the dashboard. */
    val notices: SharedFlow<UiText> = message.asSharedFlow()

    private var bindJob: Job? = null
    private var searchJob: Job? = null
    private var previewJob: Job? = null

    init {
        viewModelScope.launch { useCases.configureBlock().collect { block -> set { copy(access = block) } } }
    }

    private fun set(f: MoveUiState.() -> MoveUiState) {
        state = state.f()
    }

    /** Finds out whether [propertyId] is linked to a VDMS (and if so its networks); when it can't, the reason shows on the button. */
    fun bind(propertyId: String) {
        bindJob?.cancel()
        searchJob?.cancel()
        state = MoveUiState(propertyId = propertyId, access = state.access)
        if (propertyId.isEmpty()) return
        bindJob = viewModelScope.launch {
            val block = try {
                if (useCases.integration(propertyId) != null) null else MoveBlock.NOT_LINKED
            } catch (e: OnlineException) {
                if (e.error != OnlineError.Offline) message.emit(UiText.Res(R.string.int_load_failed))
                // A 404: this API has no Managed Assets (VDMS) endpoints (an older build).
                if (e.error == OnlineError.NotFound) MoveBlock.API_MISSING else MoveBlock.CHECK_FAILED
            }
            set { copy(block = block, networksLoading = block == null) }
            if (block != null) return@launch
            try {
                val names = useCases.networks(propertyId)
                set { copy(networks = names) }
            } catch (_: OnlineException) {
                message.emit(UiText.Res(R.string.mv_networks_failed))
            } finally {
                set { copy(networksLoading = false) }
            }
        }
    }

    /**
     * Whether the action shows for the selected [assets]: one of them is validated. It shows for everyone, greyed out with
     * the reason for someone without Configure (see [blockedReason]).
     */
    fun offered(assets: Collection<Asset>): Boolean = useCases.offered(assets)

    /**
     * Why the action can't be used right now, or null when it can: the access first (Configure, which linking a VDMS won't
     * give), then the link, then the Validated filter (the web's Validated tab).
     */
    fun blockedReason(onValidatedFilter: Boolean): UiText? = state.access.reason() ?: when (state.block) {
        MoveBlock.API_MISSING -> UiText.Res(R.string.mv_api_missing)
        MoveBlock.CHECK_FAILED -> UiText.Res(R.string.mv_check_failed)
        MoveBlock.NOT_LINKED -> UiText.Res(R.string.mv_link_first)
        null -> if (onValidatedFilter) null else UiText.Res(R.string.mv_open_validated)
    }

    /** Move tapped: opens the sheet for [ids], or says why it can't be used. */
    fun tap(ids: Set<String>, onValidatedFilter: Boolean) {
        val reason = blockedReason(onValidatedFilter)
        if (reason != null) message.tryEmit(reason) else open(ids)
    }

    private fun open(ids: Set<String>) {
        if (state.access != null || state.block != null || ids.isEmpty()) return
        set { copy(sheet = MoveSheetState(ids)) }
        search("", debounce = false)
        preview(ids)
    }

    /** Asks what the move would take, as the web does when its dialog opens; a preview that fails just leaves the note out. */
    private fun preview(ids: Set<String>) {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            val found = try {
                useCases.preview(state.propertyId, ids)
            } catch (_: OnlineException) {
                return@launch
            }
            set { copy(sheet = sheet?.takeIf { it.ids == ids }?.copy(preview = found) ?: sheet) }
        }
    }

    fun close() {
        searchJob?.cancel()
        previewJob?.cancel()
        set { copy(sheet = null) }
    }

    fun pickNetwork(name: String) = set { copy(sheet = sheet?.copy(network = name)) }

    /** Picks [email] as the assignee, or clears it when it is already the one picked (the assignee is optional). */
    fun pickAssignee(email: String) = set { copy(sheet = sheet?.let { sh -> sh.copy(assignee = email.takeIf { it != sh.assignee }) }) }

    fun setQuery(q: String) {
        set { copy(sheet = sheet?.copy(query = q)) }
        search(q, debounce = true)
    }

    private fun search(q: String, debounce: Boolean) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (debounce) delay(SEARCH_MS)
            set { copy(sheet = sheet?.copy(searching = true)) }
            val found = try {
                useCases.assignees(state.propertyId, q)
            } catch (_: OnlineException) {
                emptyList() // As on the web the list just comes back empty.
            }
            set { copy(sheet = sheet?.copy(assignees = found, searching = false)) }
        }
    }

    /** Submit: needs a network; closes the sheet and moves the assets, then says what happened. False when nothing was sent. */
    fun submit(): Boolean {
        val s = state.sheet ?: return false
        val network = s.network
        if (network == null) {
            message.tryEmit(UiText.Res(R.string.mv_choose_network))
            return false
        }
        close()
        val propertyId = state.propertyId
        viewModelScope.launch {
            val text = try {
                val outcome = useCases.move(propertyId, s.ids, network, s.assignee)
                // The buildings, floors and locations VDMS lacked were sent there first, as the web says.
                val places = placeCountsText(outcome.placesSent)
                when (val moved = outcome.moved) {
                    0 -> UiText.Res(R.string.mv_nothing_moved)
                    null -> places?.let { UiText.Res(R.string.mv_collections_moving_places, listOf(it)) } ?: UiText.Res(R.string.mv_collections_moving)
                    else -> places?.let { UiText.Plural(R.plurals.mv_assets_moving_places, moved, listOf(moved, it)) }
                        ?: UiText.Plural(R.plurals.mv_assets_moving, moved)
                }
            } catch (e: OnlineException) {
                // The server's own text can hold internal details, so the app's words are shown instead.
                if (e.error is OnlineError.Conflict) UiText.Res(R.string.mv_places_busy) else UiText.Res(R.string.mv_failed)
            }
            message.emit(text)
        }
        return true
    }

    private companion object {
        const val SEARCH_MS = 300L
    }
}
