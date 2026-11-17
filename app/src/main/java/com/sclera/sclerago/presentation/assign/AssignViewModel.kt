package com.sclera.sclerago.presentation.assign

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.AssignResult
import com.sclera.sclerago.domain.model.DirectoryPerson
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.usecase.assign.AssignUseCases
import com.sclera.sclerago.presentation.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The Assign asset sheet: the assets it assigns, the search, who matches and who is picked (the primary assignee). */
data class AssignSheetState(
    val ids: Set<String>,
    val query: String = "",
    val people: List<DirectoryPerson> = emptyList(),
    val searching: Boolean = true,
    val picked: String? = null,
)

/** Assign asset (asset list selection and asset details), as the web's: the assets go to one person, picked by email. */
@HiltViewModel
class AssignViewModel @Inject constructor(private val useCases: AssignUseCases) : ViewModel() {
    var sheet by mutableStateOf<AssignSheetState?>(null)
        private set

    private var propertyId = ""
    private var searchJob: Job? = null
    private val message = MutableSharedFlow<UiText>(extraBufferCapacity = 8)

    /** What happened, to flash on the dashboard. */
    val notices: SharedFlow<UiText> = message.asSharedFlow()

    /** The property whose assets are assigned; changing it closes the sheet. */
    fun bind(property: String) {
        if (property == propertyId) return
        propertyId = property
        close()
    }

    /** Opens the sheet for [ids], empty (as the web does: the new API doesn't say who has an asset). */
    fun open(ids: Set<String>) {
        if (ids.isEmpty() || propertyId.isEmpty()) return
        sheet = AssignSheetState(ids)
        search("", debounce = false)
    }

    fun close() {
        searchJob?.cancel()
        sheet = null
    }

    /** Picks [email] as the primary assignee. */
    fun pick(email: String) {
        sheet = sheet?.copy(picked = email)
    }

    /** Clear: nobody picked, so Add takes the assets off whoever has them. */
    fun clear() {
        sheet = sheet?.copy(picked = null)
    }

    fun setQuery(q: String) {
        sheet = sheet?.copy(query = q)
        search(q, debounce = true)
    }

    private fun search(q: String, debounce: Boolean) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (debounce) delay(SEARCH_MS)
            sheet = sheet?.copy(searching = true)
            val found = try {
                useCases.findAssignees(q)
            } catch (_: OnlineException) {
                emptyList() // As on the web, the list just comes back empty.
            }
            sheet = sheet?.copy(people = found, searching = false)
        }
    }

    /** Add: closes the sheet and assigns the assets, then says what happened. False when there was nothing to send. */
    fun submit(): Boolean {
        val s = sheet ?: return false
        close()
        val property = propertyId
        viewModelScope.launch {
            try {
                done(useCases.assign(property, s.ids, s.picked))?.let { message.emit(it) }
            } catch (_: OnlineException) {
                // The server's own text can hold internal details, so the app's words are shown instead.
                message.emit(UiText.Res(R.string.assign_failed))
            }
        }
        return true
    }

    /** "3 assets assigned to Ada (added as a Collector on this property)", as the web says it; nothing when they were taken off. */
    private fun done(r: AssignResult): UiText? {
        val who = r.name ?: r.email ?: return null
        val plural = when {
            r.added -> R.plurals.assign_done_added
            r.givenAccess -> R.plurals.assign_done_access
            else -> R.plurals.assign_done
        }
        return UiText.Plural(plural, r.assigned, listOf(r.assigned, who))
    }

    private companion object {
        const val SEARCH_MS = 300L
    }
}
