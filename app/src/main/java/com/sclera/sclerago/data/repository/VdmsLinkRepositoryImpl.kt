package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.local.EncryptedCache
import com.sclera.sclerago.data.remote.adc.AdcIntegrationApi
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.repository.NetworkMonitor
import com.sclera.sclerago.domain.repository.VdmsLinkRepository
import javax.inject.Inject

/**
 * The property's VDMS link from the server's integration list, remembered (encrypted, wiped on sign-out with the rest of
 * the cache) so a delete made offline still knows whether VDMS has to confirm it.
 */
class VdmsLinkRepositoryImpl @Inject constructor(
    private val api: AdcIntegrationApi,
    private val cache: EncryptedCache,
    private val network: NetworkMonitor,
) : VdmsLinkRepository {
    override suspend fun isLinked(propertyId: String): Boolean {
        val key = KEY_PREFIX + propertyId
        if (network.isOnline()) {
            try {
                val linked = api.integration(propertyId).isNotEmpty()
                cache.write(key, if (linked) YES else NO)
                return linked
            } catch (_: OnlineException) {
                // The server couldn't say: fall back to what it said last.
            }
        }
        return cache.read(key) == YES
    }

    private companion object {
        const val KEY_PREFIX = "vdms_linked_"
        const val YES = "1"
        const val NO = "0"
    }
}
