package com.sclera.sclerago.domain.usecase.validation

import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.repository.AssetRepository
import com.sclera.sclerago.domain.repository.ValidationRepository
import com.sclera.sclerago.domain.validation.CheckInput
import com.sclera.sclerago.domain.validation.Rule
import com.sclera.sclerago.domain.validation.SavedValidation
import com.sclera.sclerago.domain.validation.ValJob
import com.sclera.sclerago.domain.validation.ValScope
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** [prop]'s jobs, newest first. */
fun List<ValJob>.forProperty(prop: String): List<ValJob> = filter { it.prop == prop }

/** When [sourceId] last ran on [prop], if ever. */
fun List<ValJob>.lastRun(prop: String, sourceId: String): Long? =
    forProperty(prop).filter { it.sourceId == sourceId }.maxOfOrNull { it.startedAt }

class ObserveValidationJobsUseCase @Inject constructor(private val repo: ValidationRepository) {
    operator fun invoke(): StateFlow<List<ValJob>> = repo.jobs
}

class ObserveSavedValidationsUseCase @Inject constructor(private val repo: ValidationRepository) {
    operator fun invoke(): StateFlow<List<SavedValidation>> = repo.saved
}

/** A template's or saved validation's name and rules, by id. */
class GetValidationSourceUseCase @Inject constructor(private val repo: ValidationRepository) {
    fun name(id: String): String = repo.sourceName(id)
    fun rules(id: String): List<Rule> = repo.sourceRules(id)
}

/** The assets a run would cover: all of them, the ones chosen, or the ones new or changed since its last run. */
class SelectValidationTargetsUseCase @Inject constructor(
    private val validation: ValidationRepository,
    private val assets: AssetRepository,
) {
    operator fun invoke(scope: ValScope, chosen: Set<Int>, sourceId: String, prop: String, all: List<Asset>): List<Asset> = when (scope) {
        ValScope.ALL -> all
        ValScope.CHOOSE -> all.filter { it.id in chosen }
        ValScope.CHANGED -> {
            val since = validation.jobs.value.lastRun(prop, sourceId)
            val touched = assets.catalog.value.touched
            if (since == null) all else all.filter { (touched[it.id] ?: 0L) > since }
        }
    }
}

/** Starts a validation job; it runs in the background (or at once when [instant]) and fills in its results when done. */
class StartValidationUseCase @Inject constructor(private val repo: ValidationRepository) {
    operator fun invoke(
        sourceId: String,
        ids: List<Int>,
        label: String,
        prop: String,
        inputs: () -> List<Pair<Asset, CheckInput>>,
        startedAt: Long = System.currentTimeMillis(),
        instant: Boolean = false,
        onDone: (ValJob) -> Unit = {},
    ) = repo.start(sourceId, ids, label, prop, inputs, startedAt, instant, onDone)
}

/** Saves a validation with its steps in the order given; returns its id, or null if it isn't complete. */
class SaveValidationUseCase @Inject constructor(private val repo: ValidationRepository) {
    operator fun invoke(id: String?, name: String, description: String, ruleIds: List<String>): String? =
        repo.save(id, name, description, ruleIds)
}

class DeleteValidationUseCase @Inject constructor(private val repo: ValidationRepository) {
    operator fun invoke(id: String) = repo.delete(id)
}

/** The validation use cases, for injecting as one. */
class ValidationUseCases @Inject constructor(
    val observeJobs: ObserveValidationJobsUseCase,
    val observeSaved: ObserveSavedValidationsUseCase,
    val source: GetValidationSourceUseCase,
    val selectTargets: SelectValidationTargetsUseCase,
    val start: StartValidationUseCase,
    val save: SaveValidationUseCase,
    val delete: DeleteValidationUseCase,
)
