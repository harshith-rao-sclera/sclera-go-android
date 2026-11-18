package com.sclera.sclerago.presentation.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.domain.usecase.auth.ObserveOnlineUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Whether the device is online, for the screens that dim their online-only buttons ([com.sclera.sclerago.presentation.components.OfflineGate])
 * and show [com.sclera.sclerago.presentation.components.OfflineBanner] on online-only pages.
 */
@HiltViewModel
class ConnectivityViewModel @Inject constructor(observeOnline: ObserveOnlineUseCase) : ViewModel() {
    val online: StateFlow<Boolean> = observeOnline().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), observeOnline.now())

    private companion object {
        const val STOP_AFTER_MS = 5_000L
    }
}
