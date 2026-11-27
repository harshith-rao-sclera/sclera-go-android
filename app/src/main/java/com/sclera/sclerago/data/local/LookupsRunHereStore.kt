package com.sclera.sclerago.data.local

import com.sclera.sclerago.di.ApplicationScope
import com.sclera.sclerago.domain.model.RunningLookup
import com.sclera.sclerago.domain.model.SpecLookup
import com.sclera.sclerago.domain.repository.LookupsRunHereRepository
import com.sclera.sclerago.domain.repository.UserDataCleaner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The look-ups started on this phone, by asset id: in memory only, forgotten on sign-out. A running one runs in the
 * app's scope, so leaving the asset's page doesn't cancel it.
 */
@Singleton
class LookupsRunHereStore @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
) : LookupsRunHereRepository, UserDataCleaner {
    private val byAsset = ConcurrentHashMap<String, SpecLookup>()
    private val running = ConcurrentHashMap<String, Running>()

    /** A look-up under way and how it started. */
    private class Running(val info: RunningLookup, val job: Deferred<SpecLookup>)

    override suspend fun run(assetId: String, info: RunningLookup, lookUp: suspend () -> SpecLookup): SpecLookup {
        val job = scope.async { lookUp().also { byAsset[assetId] = it } }
        val entry = Running(info, job)
        running.put(assetId, entry)?.takeIf { it !== entry }?.job?.cancel()
        job.invokeOnCompletion { running.remove(assetId, entry) }
        return job.await()
    }

    override fun running(assetId: String): RunningLookup? = running[assetId]?.takeIf { it.job.isActive }?.info

    override suspend fun awaitRunning(assetId: String): SpecLookup? = running[assetId]?.job?.await()

    override fun lookupFor(assetId: String): SpecLookup? = byAsset[assetId]

    override fun keep(assetId: String, lookup: SpecLookup) {
        byAsset[assetId] = lookup
    }

    override fun forget(assetId: String) {
        byAsset.remove(assetId)
    }

    override fun clear() {
        running.values.forEach { it.job.cancel() }
        running.clear()
        byAsset.clear()
    }
}
