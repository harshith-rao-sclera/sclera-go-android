package com.sclera.sclerago.domain.usecase.property

import com.sclera.sclerago.domain.model.PropertyDownloads
import com.sclera.sclerago.domain.repository.NetworkMonitor
import com.sclera.sclerago.domain.repository.PropertyDownloadRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObservePropertyDownloadsUseCase @Inject constructor(private val repo: PropertyDownloadRepository) {
    operator fun invoke(): StateFlow<PropertyDownloads> = repo.downloads
}

/** Starts downloading a property for offline work; refused (false) without a connection. */
class StartPropertyDownloadUseCase @Inject constructor(
    private val repo: PropertyDownloadRepository,
    private val network: NetworkMonitor,
) {
    operator fun invoke(propertyId: String): Boolean {
        if (!network.isOnline()) return false
        repo.start(propertyId)
        return true
    }
}

/** Reopens the recently used property's offline data when the app starts signed in. */
class ResumePropertyDownloadUseCase @Inject constructor(private val repo: PropertyDownloadRepository) {
    operator fun invoke() = repo.resume()
}

/** Whether the device is online right now: properties not downloaded can't be opened offline. */
class IsDeviceOnlineUseCase @Inject constructor(private val network: NetworkMonitor) {
    operator fun invoke(): Boolean = network.isOnline()
}

/** The property download use cases, for injecting as one. */
class PropertyDownloadUseCases @Inject constructor(
    val observe: ObservePropertyDownloadsUseCase,
    val start: StartPropertyDownloadUseCase,
    val resume: ResumePropertyDownloadUseCase,
    val isOnline: IsDeviceOnlineUseCase,
)
