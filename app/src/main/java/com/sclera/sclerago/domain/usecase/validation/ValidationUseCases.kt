package com.sclera.sclerago.domain.usecase.validation

import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.repository.SavedValidationRepository
import com.sclera.sclerago.domain.repository.ValidationCatalogRepository
import com.sclera.sclerago.domain.repository.ValidationRunRepository
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import com.sclera.sclerago.domain.validation.FieldChoice
import com.sclera.sclerago.domain.validation.RunMode
import com.sclera.sclerago.domain.validation.RunRequest
import com.sclera.sclerago.domain.validation.StepOrder
import com.sclera.sclerago.domain.validation.ValRun
import com.sclera.sclerago.domain.validation.ValStep
import com.sclera.sclerago.domain.validation.ValTemplate
import com.sclera.sclerago.domain.validation.ValidationDef
import com.sclera.sclerago.domain.validation.ValidationDraft
import javax.inject.Inject

// The Validation page: the catalogue, saved validations and starting runs. Every call goes to the server
// (lists fall back to the phone's copy offline) and can throw OnlineException.

/** The most a saved validation's name and description may hold (the server's limits). */
const val VALIDATION_NAME_MAX = 120
const val VALIDATION_DESCRIPTION_MAX = 500

/** How many recent runs the page lists (as the web). */
const val RECENT_RUNS = 20

/** Whether the signed-in user may run validations and fix values ([Permission.CHECK]) and sign off, send back, exclude, undo fixes and keep saved validations ([Permission.VALIDATE]). */
class ValidationAccessUseCase @Inject constructor(private val guard: PermissionGuard) {
    fun canCheck(): Boolean = guard.allows(Permission.CHECK)
    fun canValidate(): Boolean = guard.allows(Permission.VALIDATE)
}

/** The steps (in the user's order) and the templates. */
class GetValidationCatalogUseCase @Inject constructor(private val repo: ValidationCatalogRepository) {
    suspend operator fun invoke(propertyId: String): Pair<List<ValStep>, List<ValTemplate>> =
        repo.steps(propertyId) to repo.templates(propertyId)
}

class GetStepOrderUseCase @Inject constructor(private val repo: ValidationCatalogRepository) {
    suspend operator fun invoke(propertyId: String): StepOrder = repo.order(propertyId)
}

/** Saves the user's own step order (templates and custom runs follow it); every step is kept, missing ones go last at the server. */
class SaveStepOrderUseCase @Inject constructor(private val repo: ValidationCatalogRepository) {
    suspend operator fun invoke(propertyId: String, steps: List<String>): StepOrder {
        if (steps.isEmpty()) throw OnlineException(OnlineError.Invalid)
        return repo.saveOrder(propertyId, steps.distinct())
    }
}

class ResetStepOrderUseCase @Inject constructor(private val repo: ValidationCatalogRepository) {
    suspend operator fun invoke(propertyId: String) = repo.resetOrder(propertyId)
}

/** The fields a step can be limited to. */
class GetValidationFieldsUseCase @Inject constructor(private val repo: ValidationCatalogRepository) {
    suspend operator fun invoke(propertyId: String): List<FieldChoice> = repo.fields(propertyId)
}

class GetSavedValidationsUseCase @Inject constructor(private val repo: SavedValidationRepository) {
    suspend operator fun invoke(propertyId: String): List<ValidationDef> = repo.validations(propertyId)
}

/** Creates ([id] null) or updates a saved validation; needs a name and at least one step. Reviewers only ([Permission.VALIDATE]). */
class SaveValidationUseCase @Inject constructor(private val repo: SavedValidationRepository, private val guard: PermissionGuard) {
    /** False when the draft is not complete. */
    fun isComplete(draft: ValidationDraft): Boolean = draft.name.isNotBlank() && draft.steps.isNotEmpty()

    suspend operator fun invoke(propertyId: String, id: String?, draft: ValidationDraft): ValidationDef {
        guard.requireOnline(Permission.VALIDATE)
        if (!isComplete(draft)) throw OnlineException(OnlineError.Invalid)
        val clean = draft.copy(
            name = draft.name.trim().take(VALIDATION_NAME_MAX),
            description = draft.description.trim().take(VALIDATION_DESCRIPTION_MAX),
            steps = draft.steps.distinct(),
        )
        return if (id == null) repo.create(propertyId, clean) else repo.update(propertyId, id, clean)
    }
}

/** Deletes a saved validation; its past runs stay. Reviewers only. */
class DeleteValidationUseCase @Inject constructor(private val repo: SavedValidationRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(propertyId: String, id: String) {
        guard.requireOnline(Permission.VALIDATE)
        repo.delete(propertyId, id)
    }
}

class GetValidationRunsUseCase @Inject constructor(private val repo: ValidationRunRepository) {
    suspend operator fun invoke(propertyId: String): List<ValRun> = repo.runs(propertyId, RECENT_RUNS)
}

/** Starts a run now, or saves it as ready. Choosing assets needs at least one. Needs [Permission.CHECK]. */
class StartValidationRunUseCase @Inject constructor(private val repo: ValidationRunRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(propertyId: String, request: RunRequest): ValRun {
        guard.requireOnline(Permission.CHECK)
        if (request.mode == RunMode.CHOSEN && request.assetIds.isEmpty()) throw OnlineException(OnlineError.Invalid)
        return repo.start(propertyId, request)
    }
}

/** Starts a run that was saved as ready. Needs [Permission.CHECK]. */
class StartReadyRunUseCase @Inject constructor(private val repo: ValidationRunRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(propertyId: String, id: String): ValRun {
        guard.requireOnline(Permission.CHECK)
        return repo.startReady(propertyId, id)
    }
}

/**
 * "Run again" on a finished run, as the server's rerun: a new run with its steps, settings and assets (assets deleted
 * since are left out; a new-or-changed run checks what changed since). Needs [Permission.CHECK].
 */
class RerunUseCase @Inject constructor(private val repo: ValidationRunRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(propertyId: String, id: String): ValRun {
        guard.requireOnline(Permission.CHECK)
        return repo.rerun(propertyId, id)
    }
}

/** Discards a run that hasn't started. Reviewers only. */
class DiscardRunUseCase @Inject constructor(private val repo: ValidationRunRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(propertyId: String, id: String) {
        guard.requireOnline(Permission.VALIDATE)
        repo.discard(propertyId, id)
    }
}


/** The Validation page's use cases, for injecting as one. */
class ValidationUseCases @Inject constructor(
    val access: ValidationAccessUseCase,
    val catalog: GetValidationCatalogUseCase,
    val order: GetStepOrderUseCase,
    val saveOrder: SaveStepOrderUseCase,
    val resetOrder: ResetStepOrderUseCase,
    val fields: GetValidationFieldsUseCase,
    val saved: GetSavedValidationsUseCase,
    val save: SaveValidationUseCase,
    val delete: DeleteValidationUseCase,
    val runs: GetValidationRunsUseCase,
    val start: StartValidationRunUseCase,
    val startReady: StartReadyRunUseCase,
    val rerun: RerunUseCase,
    val discard: DiscardRunUseCase,
)
