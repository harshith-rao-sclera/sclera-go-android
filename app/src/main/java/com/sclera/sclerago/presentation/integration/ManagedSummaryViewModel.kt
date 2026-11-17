package com.sclera.sclerago.presentation.integration

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.domain.model.ManagedCounts
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.PlaceScope
import com.sclera.sclerago.domain.usecase.integration.GetManagedCountsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The asset list's info button: the Managed Assets Summary (moved / returned counts) for the places the list is narrowed to. */
@HiltViewModel
class ManagedSummaryViewModel @Inject constructor(private val counts: GetManagedCountsUseCase) : ViewModel() {
    /** The counts on show, or null while the summary is closed. */
    var shown by mutableStateOf<ManagedCounts?>(null)
        private set

    private var job: Job? = null

    /** Opens the summary at 0 and fills it in, as the web does; when it can't be read the counts stay 0. */
    fun open(propertyId: String, scope: PlaceScope) {
        shown = ManagedCounts()
        if (propertyId.isEmpty()) return
        job?.cancel()
        job = viewModelScope.launch {
            try {
                val c = counts(propertyId, scope)
                if (shown != null) shown = c
            } catch (_: OnlineException) {
                // As on the web: nothing is said, the counts stay 0.
            }
        }
    }

    fun close() {
        job?.cancel()
        shown = null
    }
}
