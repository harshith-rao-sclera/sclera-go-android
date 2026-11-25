package com.sclera.sclerago.presentation.syncsettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.domain.model.SyncSettings
import com.sclera.sclerago.domain.usecase.sync.SyncSettingsUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The Sync settings page of the open property: what waits to upload, the last sync, and its two switches. */
@HiltViewModel
class SyncSettingsViewModel @Inject constructor(private val useCases: SyncSettingsUseCases) : ViewModel() {
    private val property = MutableStateFlow("")

    /** The property's sync; null until first read (the counts show placeholders). Read only while the page is on screen. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<SyncSettings?> = property
        .flatMapLatest { id -> if (id.isEmpty()) flowOf(null) else useCases.observe(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), null)

    /** Shows [propertyId]'s sync. */
    fun bind(propertyId: String) {
        property.value = propertyId
    }

    fun setDataSync(on: Boolean) = property.value.takeIf { it.isNotEmpty() }?.let { useCases.setDataSync(it, on) }

    fun setMediaSync(on: Boolean) = property.value.takeIf { it.isNotEmpty() }?.let { useCases.setMediaSync(it, on) }

    private companion object {
        /** Keeps reading through a short pause (rotation); stops soon after the page goes. */
        const val STOP_AFTER_MS = 5_000L
    }
}
