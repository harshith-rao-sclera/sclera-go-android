package com.sclera.sclerago.data.local

import com.sclera.sclerago.domain.repository.LookupsRunHereRepository
import com.sclera.sclerago.domain.repository.UserDataCleaner
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** The look-ups started on this phone, by asset id: in memory only, forgotten on sign-out. */
@Singleton
class LookupsRunHereStore @Inject constructor() : LookupsRunHereRepository, UserDataCleaner {
    private val byAsset = ConcurrentHashMap<String, String>()

    override fun lookupFor(assetId: String): String? = byAsset[assetId]

    override fun keep(assetId: String, lookupId: String) {
        byAsset[assetId] = lookupId
    }

    override fun forget(assetId: String) {
        byAsset.remove(assetId)
    }

    override fun clear() = byAsset.clear()
}
