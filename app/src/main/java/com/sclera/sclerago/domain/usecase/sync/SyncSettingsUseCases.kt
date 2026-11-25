package com.sclera.sclerago.domain.usecase.sync

import com.sclera.sclerago.domain.model.SyncSettings
import com.sclera.sclerago.domain.repository.SyncSettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** A property's sync state (waiting to upload, last sync, switches), kept up to date. */
class ObserveSyncSettingsUseCase @Inject constructor(private val repo: SyncSettingsRepository) {
    operator fun invoke(propertyId: String): Flow<SyncSettings> = repo.observe(propertyId)
}

/** Turns a property's Data sync on or off (changes from the server coming down; the device's always upload). */
class SetDataSyncUseCase @Inject constructor(private val repo: SyncSettingsRepository) {
    operator fun invoke(propertyId: String, on: Boolean) = repo.setDataSync(propertyId, on)
}

/** Turns a property's Photo and video sync on or off (the server's photos and videos coming down). */
class SetMediaSyncUseCase @Inject constructor(private val repo: SyncSettingsRepository) {
    operator fun invoke(propertyId: String, on: Boolean) = repo.setMediaSync(propertyId, on)
}

/** The Sync settings page's use cases, injected together. */
class SyncSettingsUseCases @Inject constructor(
    val observe: ObserveSyncSettingsUseCase,
    val setDataSync: SetDataSyncUseCase,
    val setMediaSync: SetMediaSyncUseCase,
)
