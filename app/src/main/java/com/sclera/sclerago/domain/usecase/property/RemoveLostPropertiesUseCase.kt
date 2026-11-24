package com.sclera.sclerago.domain.usecase.property

import com.sclera.sclerago.domain.model.LostProperties
import com.sclera.sclerago.domain.repository.PropertyDownloadRepository
import com.sclera.sclerago.domain.repository.PropertyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Each time the server lists the user's properties (at start, on return to the app, on a re-check), a property
 * downloaded on this device that it no longer lists (access removed, or the property deleted) has its offline data
 * deleted and its sync stopped: the server refuses everything about it now, unsent changes included. Emits what was
 * removed, and whether it was the property being worked on, so the user can be told and taken to the property list.
 */
class RemoveLostPropertiesUseCase @Inject constructor(
    private val properties: PropertyRepository,
    private val downloads: PropertyDownloadRepository,
) {
    /** One removal at a time, so the same list seen twice (at start and by [invoke]) removes each property once. */
    private val removing = Mutex()

    operator fun invoke(): Flow<LostProperties> = properties.listed.mapNotNull { removeUnlisted(it) }

    /**
     * At app start, before the downloaded properties' sync starts again: waits up to [waitMs] for the server's first list
     * and removes what it no longer lists, so no sync starts on a property that is then closed under it. Null when no
     * list came in time (offline, say) or nothing was removed.
     */
    suspend fun beforeSyncStarts(waitMs: Long): LostProperties? =
        withTimeoutOrNull(waitMs) { properties.listed.first() }?.let { removeUnlisted(it) }

    private suspend fun removeUnlisted(listed: Set<String>): LostProperties? = removing.withLock {
        val onDevice = downloads.downloads.first { it.restored }
        val lost = (onDevice.downloadedIds + onDevice.active.keys) - listed
        if (lost.isEmpty()) return@withLock null
        val wasCurrent = onDevice.currentId in lost
        downloads.remove(lost)
        LostProperties(lost, wasCurrent)
    }
}
