package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.offline.ValidationOfflineSource
import com.sclera.sclerago.data.offline.ValidationWriter
import com.sclera.sclerago.di.ApplicationScope
import com.sclera.sclerago.di.MainDispatcher
import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.repository.UserDataCleaner
import com.sclera.sclerago.domain.repository.ValidationRepository
import com.sclera.sclerago.domain.system.IdGenerator
import com.sclera.sclerago.domain.system.TimeProvider
import com.sclera.sclerago.domain.system.next
import com.sclera.sclerago.domain.validation.CheckInput
import com.sclera.sclerago.domain.validation.RULES
import com.sclera.sclerago.domain.validation.Rule
import com.sclera.sclerago.domain.validation.SavedValidation
import com.sclera.sclerago.domain.validation.TEMPLATES
import com.sclera.sclerago.domain.validation.ValJob
import com.sclera.sclerago.domain.validation.runValidation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saved validations and jobs. Saved validations are read from and written to the offline database. The jobs listed
 * are the ones in the database (run by the server) and the ones run on this device, which stay in memory: a
 * job's delay stands in for the time a full server run takes, and the server's own run is not available offline.
 */
@Singleton
class ValidationRepositoryImpl @Inject constructor(
    source: ValidationOfflineSource,
    private val writer: ValidationWriter,
    private val idGen: IdGenerator,
    private val time: TimeProvider,
    @ApplicationScope private val appScope: CoroutineScope,
    @MainDispatcher private val main: CoroutineDispatcher,
) : ValidationRepository, UserDataCleaner {
    private val localJobs = MutableStateFlow(emptyList<ValJob>())

    override val jobs: StateFlow<List<ValJob>> = combine(source.recentJobsFlow(), localJobs) { stored, local ->
        (local + stored).sortedByDescending { it.startedAt }
    }.stateIn(appScope, SharingStarted.Eagerly, emptyList())

    override val saved: StateFlow<List<SavedValidation>> = source.savedFlow().stateIn(appScope, SharingStarted.Eagerly, emptyList())

    override fun sourceName(id: String): String =
        TEMPLATES.firstOrNull { it.id == id }?.name ?: saved.value.firstOrNull { it.id == id }?.name ?: "Validation"

    override fun sourceRules(id: String): List<Rule> =
        TEMPLATES.firstOrNull { it.id == id }?.rules ?: saved.value.firstOrNull { it.id == id }?.rules.orEmpty()

    override fun start(
        sourceId: String,
        ids: List<String>,
        label: String,
        prop: String,
        inputs: suspend () -> List<Pair<Asset, CheckInput>>,
        onDone: (ValJob) -> Unit,
    ) {
        val rules = sourceRules(sourceId)
        val id = idGen.next()
        localJobs.update { listOf(ValJob(id, sourceName(sourceId), sourceId, rules.size, label, ids, time.nowMillis(), prop)) + it }
        appScope.launch(main) {
            delay(RUN_BASE_MS + ids.size * RUN_PER_ASSET_MS)
            val all = runCatching { inputs() }.getOrNull()
            if (all == null) {
                localJobs.update { jobs -> jobs.filter { it.id != id } }
                return@launch
            }
            val results = runValidation(rules, all.filter { it.first.id in ids }, all)
            localJobs.update { jobs -> jobs.map { if (it.id == id) it.copy(running = false, results = results) else it } }
            localJobs.value.firstOrNull { it.id == id }?.let(onDone)
        }
    }

    override suspend fun save(id: String?, name: String, description: String, ruleIds: List<String>): String? {
        if (name.isBlank() || ruleIds.isEmpty()) return null
        return writer.save(id, name.trim(), description, ruleIds.distinct().filter { it in RULES })
    }

    override suspend fun delete(id: String) = writer.delete(id)

    override fun clear() {
        localJobs.value = emptyList()
    }

    private companion object {
        const val RUN_BASE_MS = 1600L
        const val RUN_PER_ASSET_MS = 120L
    }
}
