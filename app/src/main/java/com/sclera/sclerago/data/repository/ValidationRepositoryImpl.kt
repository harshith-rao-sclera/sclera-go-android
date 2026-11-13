package com.sclera.sclerago.data.repository

import com.sclera.sclerago.di.MainDispatcher
import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.repository.UserDataCleaner
import com.sclera.sclerago.domain.system.IdGenerator
import com.sclera.sclerago.domain.system.next
import com.sclera.sclerago.domain.repository.ValidationRepository
import com.sclera.sclerago.domain.validation.CheckInput
import com.sclera.sclerago.domain.validation.DEFAULT_SAVED
import com.sclera.sclerago.domain.validation.RULES
import com.sclera.sclerago.domain.validation.Rule
import com.sclera.sclerago.domain.validation.SavedValidation
import com.sclera.sclerago.domain.validation.TEMPLATES
import com.sclera.sclerago.domain.validation.ValJob
import com.sclera.sclerago.domain.validation.runValidation
import dagger.hilt.android.ActivityRetainedLifecycle
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Runs validation jobs on the device and keeps the user's saved validations, in memory. A job's
 * delay stands in for the time a server-side run will take.
 */
@ActivityRetainedScoped
class ValidationRepositoryImpl @Inject constructor(
    lifecycle: ActivityRetainedLifecycle,
    @MainDispatcher dispatcher: CoroutineDispatcher,
    private val idGen: IdGenerator,
) : ValidationRepository, UserDataCleaner {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    init {
        lifecycle.addOnClearedListener { scope.cancel() }
    }

    private val jobState = MutableStateFlow(emptyList<ValJob>())
    override val jobs: StateFlow<List<ValJob>> = jobState.asStateFlow()

    private val savedState = MutableStateFlow(DEFAULT_SAVED)
    override val saved: StateFlow<List<SavedValidation>> = savedState.asStateFlow()

    override fun sourceName(id: String): String =
        TEMPLATES.firstOrNull { it.id == id }?.name ?: savedState.value.firstOrNull { it.id == id }?.name ?: "Validation"

    override fun sourceRules(id: String): List<Rule> =
        TEMPLATES.firstOrNull { it.id == id }?.rules ?: savedState.value.firstOrNull { it.id == id }?.rules.orEmpty()

    override fun start(
        sourceId: String,
        ids: List<String>,
        label: String,
        prop: String,
        inputs: () -> List<Pair<Asset, CheckInput>>,
        startedAt: Long,
        instant: Boolean,
        onDone: (ValJob) -> Unit,
    ) {
        val rules = sourceRules(sourceId)
        val id = idGen.next()
        jobState.value = listOf(ValJob(id, sourceName(sourceId), sourceId, rules.size, label, ids, startedAt, prop)) + jobState.value
        fun finish() {
            val all = inputs()
            val results = runValidation(rules, all.filter { it.first.id in ids }, all)
            jobState.value = jobState.value.map { if (it.id == id) it.copy(running = false, results = results) else it }
            jobState.value.firstOrNull { it.id == id }?.let(onDone)
        }
        if (instant) finish() else scope.launch {
            delay(1600L + ids.size * 120L)
            finish()
        }
    }

    override fun save(id: String?, name: String, description: String, ruleIds: List<String>): String? {
        if (name.isBlank() || ruleIds.isEmpty()) return null
        val steps = ruleIds.distinct().filter { it in RULES }
        if (id == null) {
            val newId = "sv${System.currentTimeMillis()}"
            savedState.value = savedState.value + SavedValidation(newId, name.trim(), steps, description = description.trim())
            return newId
        }
        savedState.value = savedState.value.map {
            if (it.id == id) it.copy(name = name.trim(), ruleIds = steps, description = description.trim()) else it
        }
        return id
    }

    override fun clear() {
        jobState.value = emptyList()
        savedState.value = DEFAULT_SAVED
    }

    override fun delete(id: String) {
        savedState.value = savedState.value.filter { it.id != id }
    }
}
