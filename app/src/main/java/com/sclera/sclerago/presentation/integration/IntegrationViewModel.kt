package com.sclera.sclerago.presentation.integration

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.AuthState
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.VdmsChoice
import com.sclera.sclerago.domain.model.VdmsLink
import com.sclera.sclerago.domain.usecase.auth.ObserveAuthStateUseCase
import com.sclera.sclerago.domain.usecase.integration.IntegrationUseCases
import com.sclera.sclerago.presentation.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The Add Integration sheet: the VDMS search, the ones found, the one picked, and the confirm before linking. */
data class AddLinkState(
    val query: String = "",
    val choices: List<VdmsChoice> = emptyList(),
    val searching: Boolean = true,
    val picked: VdmsChoice? = null,
    val confirm: Boolean = false,
)

data class IntegrationUiState(
    val propertyId: String = "",
    val loading: Boolean = true,
    val link: VdmsLink? = null,
    /** "+ Integration" can be used (not for the plain "user" role, as on the web). */
    val canAdd: Boolean = false,
    /** The organisation's name as the card and the sheets show it (the Sclera company name, else the organisation id). */
    val organisation: String = "",
    val viewing: Boolean = false,
    val adding: AddLinkState? = null,
    val message: UiText? = null,
)

/** The Integration page (side menu → Integration), as the web's: the property's link to a VDMS, and linking one. */
@HiltViewModel
class IntegrationViewModel @Inject constructor(
    private val useCases: IntegrationUseCases,
    private val auth: ObserveAuthStateUseCase,
) : ViewModel() {
    var state by mutableStateOf(IntegrationUiState())
        private set

    private var searchJob: Job? = null
    private var messageJob: Job? = null

    private fun set(f: IntegrationUiState.() -> IntegrationUiState) {
        state = state.f()
    }

    fun bind(propertyId: String) {
        state = IntegrationUiState(
            propertyId = propertyId,
            canAdd = useCases.canAdd(),
            organisation = (auth().value as? AuthState.SignedIn)?.session?.organisation.orEmpty(),
        )
        if (propertyId.isNotEmpty()) load()
    }

    fun load() {
        set { copy(loading = true) }
        viewModelScope.launch {
            try {
                val link = useCases.get(state.propertyId)
                set { copy(link = link, loading = false) }
            } catch (_: OnlineException) {
                set { copy(link = null, loading = false) }
                flash(UiText.Res(R.string.int_load_failed))
            }
        }
    }

    private fun flash(msg: UiText) {
        set { copy(message = msg) }
        messageJob?.cancel()
        messageJob = viewModelScope.launch {
            delay(3200)
            set { copy(message = null) }
        }
    }

    /** The card's name: the company's, else the organisation id the link carries. */
    fun nameOf(link: VdmsLink?): String = state.organisation.ifBlank { link?.organisationId.orEmpty() }

    fun view(on: Boolean) = set { copy(viewing = on) }

    // ---------------------------------------------------------------- Add

    fun startAdding() {
        if (state.link != null || !state.canAdd) return
        set { copy(adding = AddLinkState()) }
        search("", debounce = false)
    }

    fun closeAdding() {
        searchJob?.cancel()
        set { copy(adding = null) }
    }

    fun setQuery(q: String) {
        set { copy(adding = adding?.copy(query = q)) }
        search(q, debounce = true)
    }

    private fun search(q: String, debounce: Boolean) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (debounce) delay(SEARCH_MS)
            set { copy(adding = adding?.copy(searching = true)) }
            val found = try {
                useCases.search(state.propertyId, q)
            } catch (_: OnlineException) {
                emptyList() // As on the web the list just comes back empty.
            }
            set { copy(adding = adding?.copy(choices = found, searching = false)) }
        }
    }

    fun pick(c: VdmsChoice) = set { copy(adding = adding?.copy(picked = c)) }

    /** Done: asks to confirm (nothing picked: says so). */
    fun askConfirm() {
        val a = state.adding ?: return
        if (a.picked == null) {
            flash(UiText.Res(R.string.int_choose_vdms))
            return
        }
        set { copy(adding = a.copy(confirm = true)) }
    }

    fun cancelConfirm() = set { copy(adding = adding?.copy(confirm = false)) }

    /** Link: as on the web the sheet closes and the page shows its loader while linking, then reloads. */
    fun link() {
        val vdms = state.adding?.picked ?: return
        closeAdding()
        set { copy(loading = true) }
        viewModelScope.launch {
            try {
                useCases.link(state.propertyId, vdms.vdmsId)
                flash(UiText.Res(R.string.int_linked, listOf(vdms.vdmsId)))
                load()
            } catch (_: OnlineException) {
                // The server's own text can hold internal details, so the web's fallback sentence is shown.
                set { copy(loading = false) }
                flash(UiText.Res(R.string.int_link_failed))
            }
        }
    }

    private companion object {
        const val SEARCH_MS = 300L
    }
}
