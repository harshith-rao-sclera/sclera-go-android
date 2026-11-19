package com.sclera.sclerago.data.repository

import com.sclera.sclerago.domain.model.TagCheck
import com.sclera.sclerago.domain.model.TagUpdate
import com.sclera.sclerago.domain.model.TagValue
import com.sclera.sclerago.domain.model.TagWrite
import com.sclera.sclerago.domain.repository.NetworkMonitor
import com.sclera.sclerago.domain.repository.TaggingRepository
import javax.inject.Inject

/** Tags through the server when the phone is online, and in its own tables (QR codes only) when it is not. */
class ConnectionAwareTaggingRepository @Inject constructor(
    private val network: NetworkMonitor,
    private val online: TaggingRepositoryImpl,
    private val offline: OfflineTaggingRepository,
) : TaggingRepository {
    private val current: TaggingRepository get() = if (network.isOnline()) online else offline

    override suspend fun check(tag: TagValue, asset: String, property: String): TagCheck = current.check(tag, asset, property)
    override suspend fun register(tag: TagValue, asset: String, property: String) = current.register(tag, asset, property)
    override suspend fun write(write: TagWrite): TagUpdate = current.write(write)
}
