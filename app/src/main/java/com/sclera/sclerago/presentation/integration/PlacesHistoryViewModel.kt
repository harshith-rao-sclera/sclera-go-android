package com.sclera.sclerago.presentation.integration

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.PlacesDirection
import com.sclera.sclerago.domain.model.PlacesTransfer
import com.sclera.sclerago.domain.model.PlacesTransferDetail
import com.sclera.sclerago.domain.usecase.integration.PlacesHistoryUseCases
import com.sclera.sclerago.presentation.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One transfer's places, while they are fetched, once they are, or when they couldn't be. */
sealed interface TransferDetailState {
    data object Loading : TransferDetailState
    data object Failed : TransferDetailState
    data class Loaded(val detail: PlacesTransferDetail) : TransferDetailState
}

data class PlacesHistoryUiState(
    val propertyId: String = "",
    /** Which way the list is narrowed to (null: both). */
    val filter: PlacesDirection? = null,
    val items: List<PlacesTransfer> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    /** The list couldn't be read (shown with Try again while nothing is listed). */
    val failed: Boolean = false,
    /** The transfer whose places show. */
    val open: String? = null,
    val details: Map<String, TransferDetailState> = emptyMap(),
    val message: UiText? = null,
)

/**
 * The places history (Integration page → History), as the web's: every send of buildings, floors and locations to VDMS
 * and import from it, newest first, by direction, a page at a time; a transfer opens to show its places. While VDMS is
 * still working on one, the list is read again every few seconds. Online only.
 */
@HiltViewModel
class PlacesHistoryViewModel @Inject constructor(private val useCases: PlacesHistoryUseCases) : ViewModel() {
    var state by mutableStateOf(PlacesHistoryUiState())
        private set

    private var loadJob: Job? = null
    private var pollJob: Job? = null
    private var messageJob: Job? = null

    private fun set(f: PlacesHistoryUiState.() -> PlacesHistoryUiState) {
        state = state.f()
    }

    /** The sheet opened for [propertyId]: the first page of every transfer. */
    fun start(propertyId: String) {
        stop()
        state = PlacesHistoryUiState(propertyId = propertyId)
        load()
    }

    /** The sheet closed: nothing is read any more. */
    fun stop() {
        loadJob?.cancel()
        pollJob?.cancel()
    }

    fun setFilter(direction: PlacesDirection?) {
        if (direction == state.filter && !state.failed) return
        set { copy(filter = direction, open = null, items = emptyList(), total = 0) }
        load()
    }

    /** The first page again; [quiet] keeps what is shown while it reads, and a failure says nothing. */
    fun load(quiet: Boolean = false) {
        loadJob?.cancel()
        if (!quiet) set { copy(loading = true) }
        val filter = state.filter
        loadJob = viewModelScope.launch {
            try {
                val page = useCases.page(state.propertyId, filter, PAGE, 0)
                set { copy(items = page.items, total = page.total, failed = false, loading = false) }
            } catch (_: OnlineException) {
                set { if (quiet) this else copy(failed = true, loading = false) }
            }
            watch()
        }
    }

    /** Show more: the next page, after those listed (one already listed isn't listed twice). */
    fun loadMore() {
        if (state.loadingMore || state.items.size >= state.total) return
        set { copy(loadingMore = true) }
        val filter = state.filter
        viewModelScope.launch {
            try {
                val page = useCases.page(state.propertyId, filter, PAGE, state.items.size)
                set {
                    val seen = items.mapTo(HashSet()) { it.id }
                    copy(items = items + page.items.filter { it.id !in seen }, total = page.total, loadingMore = false)
                }
            } catch (_: OnlineException) {
                set { copy(loadingMore = false) }
                flash(UiText.Res(R.string.ph_more_failed))
            }
        }
    }

    /** Opens [id]'s places (fetched the first time), or closes them. */
    fun toggle(id: String) {
        val open = if (state.open == id) null else id
        set { copy(open = open) }
        if (open != null && state.details[open] == null) fetchDetail(open)
    }

    fun retryDetail(id: String) = fetchDetail(id)

    private fun fetchDetail(id: String) {
        set { copy(details = details + (id to TransferDetailState.Loading)) }
        viewModelScope.launch {
            val next = try {
                TransferDetailState.Loaded(useCases.transfer(state.propertyId, id))
            } catch (_: OnlineException) {
                TransferDetailState.Failed
            }
            set { copy(details = details + (id to next)) }
        }
    }

    /**
     * A transfer VDMS hasn't finished: the list is read again every few seconds, and its places are fetched again when
     * they show (they change as VDMS reports).
     */
    private fun watch() {
        pollJob?.cancel()
        if (state.items.none { it.waiting }) return
        pollJob = viewModelScope.launch {
            delay(POLL_MS)
            val waiting = state.items.filter { it.waiting }.mapTo(HashSet()) { it.id }
            set { copy(details = details - waiting) }
            state.open?.takeIf { it in waiting }?.let(::fetchDetail)
            load(quiet = true)
        }
    }

    private fun flash(msg: UiText) {
        set { copy(message = msg) }
        messageJob?.cancel()
        messageJob = viewModelScope.launch {
            delay(MESSAGE_MS)
            set { copy(message = null) }
        }
    }

    private companion object {
        const val PAGE = 25
        const val POLL_MS = 4_000L
        const val MESSAGE_MS = 3_200L
    }
}
