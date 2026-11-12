package com.sclera.sclerago.services.validation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sclera.sclerago.data.Asset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Runs validation jobs (a template's or a saved validation's rules over a set of assets) and keeps
 * the user's saved validations. The rules themselves are in Validation.kt / ValidationTemplates.kt.
 *
 * State is Compose snapshot state, so the Validation page recomposes as jobs start and finish.
 */
class ValidationService(private val scope: CoroutineScope) {

    /** Every job run so far, all properties, newest first. */
    var jobs by mutableStateOf(emptyList<ValJob>())
        private set

    /** The user's own rule sets (the built-in ones are [TEMPLATES]). */
    var saved by mutableStateOf(DEFAULT_SAVED)
        private set

    private var nextJobId = 1

    /** A template's or saved validation's name, by id. */
    fun sourceName(id: String): String =
        TEMPLATES.firstOrNull { it.id == id }?.name ?: saved.firstOrNull { it.id == id }?.name ?: "Validation"

    /** A template's or saved validation's rules, by id. */
    fun sourceRules(id: String): List<Rule> =
        TEMPLATES.firstOrNull { it.id == id }?.rules ?: saved.firstOrNull { it.id == id }?.rules.orEmpty()

    /** Jobs for one property, newest first. */
    fun jobsFor(prop: String): List<ValJob> = jobs.filter { it.prop == prop }

    /** When [sourceId] last ran on [prop], if ever. */
    fun lastRun(prop: String, sourceId: String): Long? =
        jobsFor(prop).filter { it.sourceId == sourceId }.maxOfOrNull { it.startedAt }

    /**
     * Starts a job running [sourceId]'s rules on the assets [ids]. It runs in the background (or at once
     * when [instant]); [inputs] is read when it finishes, so it checks the assets as they are then, and
     * [onDone] gets the finished job.
     */
    fun start(
        sourceId: String,
        ids: List<Int>,
        label: String,
        prop: String,
        inputs: () -> List<Pair<Asset, CheckInput>>,
        startedAt: Long = System.currentTimeMillis(),
        instant: Boolean = false,
        onDone: (ValJob) -> Unit = {},
    ) {
        val rules = sourceRules(sourceId)
        val id = nextJobId++
        jobs = listOf(ValJob(id, sourceName(sourceId), sourceId, rules.size, label, ids, startedAt, prop)) + jobs
        fun finish() {
            val all = inputs()
            val results = runValidation(rules, all.filter { it.first.id in ids }, all)
            jobs = jobs.map { if (it.id == id) it.copy(running = false, results = results) else it }
            jobs.firstOrNull { it.id == id }?.let(onDone)
        }
        if (instant) finish() else scope.launch {
            // Stand-in for the time a real server-side run takes.
            delay(1600L + ids.size * 120L)
            finish()
        }
    }

    /** Saves a new rule set ([id] null) or updates one. Returns false when there's nothing to save. */
    /** Saves a validation with its steps in the order given; returns its id, or null if it isn't complete. */
    fun save(id: String?, name: String, description: String, ruleIds: List<String>): String? {
        if (name.isBlank() || ruleIds.isEmpty()) return null
        val steps = ruleIds.distinct().filter { it in RULES }
        if (id == null) {
            val newId = "sv${System.currentTimeMillis()}"
            saved = saved + SavedValidation(newId, name.trim(), steps, description = description.trim())
            return newId
        }
        saved = saved.map { if (it.id == id) it.copy(name = name.trim(), ruleIds = steps, description = description.trim()) else it }
        return id
    }

    fun delete(id: String) {
        saved = saved.filter { it.id != id }
    }
}
