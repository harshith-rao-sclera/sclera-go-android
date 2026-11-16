package com.sclera.sclerago.presentation.sync

import androidx.lifecycle.ViewModel
import com.sclera.sclerago.domain.sync.SyncState
import com.sclera.sclerago.domain.usecase.sync.ObserveSyncStateUseCase
import com.sclera.sclerago.domain.usecase.sync.RetryOfflineSyncUseCase
import com.sclera.sclerago.domain.usecase.sync.StartOfflineSyncUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Where the first-run download stands, and the actions the "Downloading your data" screen offers. */
@HiltViewModel
class SyncViewModel @Inject constructor(
    observeSyncState: ObserveSyncStateUseCase,
    private val startSync: StartOfflineSyncUseCase,
    private val retrySync: RetryOfflineSyncUseCase,
) : ViewModel() {
    val state: StateFlow<SyncState> = observeSyncState()

    fun start() = startSync()

    fun retry() = retrySync()
}
