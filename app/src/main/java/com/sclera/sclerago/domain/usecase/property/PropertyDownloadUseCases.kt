package com.sclera.sclerago.domain.usecase.property

import com.sclera.sclerago.domain.model.PropertyDownloads
import com.sclera.sclerago.domain.repository.NetworkMonitor
import com.sclera.sclerago.domain.repository.PropertyDownloadRepository
import kotlinx.coroutines.flow.Flow
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

/** Switches to a downloaded property; it is already on the device, so this works offline too. */
class OpenDownloadedPropertyUseCase @Inject constructor(private val repo: PropertyDownloadRepository) {
    operator fun invoke(propertyId: String) = repo.open(propertyId)
}

/** Restarts the sync of every downloaded property (and reopens the current one) when the app starts signed in. */
class ResumePropertyDownloadUseCase @Inject constructor(private val repo: PropertyDownloadRepository) {
    operator fun invoke() = repo.resume()
}

/** Whether the device is online right now: properties not downloaded can't be opened offline. */
class IsDeviceOnlineUseCase @Inject constructor(private val network: NetworkMonitor) {
    operator fun invoke(): Boolean = network.isOnline()

    /** Whether the device is online, now and whenever that changes. */
    fun observe(): Flow<Boolean> = network.observeOnline()
}

/** The property download use cases, for injecting as one. */
class PropertyDownloadUseCases @Inject constructor(
    val observe: ObservePropertyDownloadsUseCase,
    val start: StartPropertyDownloadUseCase,
    val open: OpenDownloadedPropertyUseCase,
    val resume: ResumePropertyDownloadUseCase,
    val isOnline: IsDeviceOnlineUseCase,
)
