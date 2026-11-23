package com.sclera.sclerago.domain.usecase.validation

import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.repository.FileRepository
import com.sclera.sclerago.domain.repository.ValidationRunRepository
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import com.sclera.sclerago.domain.validation.BulkFixResult
import com.sclera.sclerago.domain.validation.CONFIDENT
import com.sclera.sclerago.domain.validation.Decision
import com.sclera.sclerago.domain.validation.DecisionOutcome
import com.sclera.sclerago.domain.validation.IssueStatus
import com.sclera.sclerago.domain.validation.IssueValue
import com.sclera.sclerago.domain.validation.MergeAsset
import com.sclera.sclerago.domain.validation.ResultFilter
import com.sclera.sclerago.domain.validation.ResultPage
import com.sclera.sclerago.domain.validation.RunExport
import com.sclera.sclerago.domain.validation.RunInsights
import com.sclera.sclerago.domain.validation.RunIssue
import com.sclera.sclerago.domain.validation.RunStatus
import com.sclera.sclerago.domain.validation.ValRun
import kotlinx.coroutines.delay
import javax.inject.Inject

// Reviewing one run: its results and issues, fixing values, deciding on assets, merging duplicates, exporting.

class GetValidationRunUseCase @Inject constructor(private val repo: ValidationRunRepository) {
    suspend operator fun invoke(propertyId: String, id: String): ValRun = repo.run(propertyId, id)
}

class GetRunResultsUseCase @Inject constructor(private val repo: ValidationRunRepository) {
    suspend operator fun invoke(propertyId: String, jobId: String, filter: ResultFilter, cursor: String?): ResultPage =
        repo.results(propertyId, jobId, filter, cursor)
}

/** The run's issues: one asset's (every status) or the open ones of the whole run. */
class GetRunIssuesUseCase @Inject constructor(private val repo: ValidationRunRepository) {
    suspend fun ofAsset(propertyId: String, jobId: String, assetId: String): List<RunIssue> = repo.issues(propertyId, jobId, assetId, null)
    suspend fun open(propertyId: String, jobId: String): List<RunIssue> = repo.issues(propertyId, jobId, null, IssueStatus.OPEN)
}

class GetRunInsightsUseCase @Inject constructor(private val repo: ValidationRunRepository) {
    suspend operator fun invoke(propertyId: String, jobId: String): RunInsights = repo.insights(propertyId, jobId)
}

/** Fetches the run's results as CSV, and writes it where the user chose to save it. */
class ExportRunUseCase @Inject constructor(private val repo: ValidationRunRepository, private val files: FileRepository) {
    suspend operator fun invoke(propertyId: String, jobId: String): RunExport = repo.export(propertyId, jobId)

    suspend fun save(export: RunExport, uri: String): Boolean = files.writeFile(uri, export.bytes)
}

/** Applies a suggestion or a typed value to an issue, or keeps the asset as it is. Needs [Permission.CHECK]. */
class FixIssueUseCase @Inject constructor(private val repo: ValidationRunRepository, private val guard: PermissionGuard) {
    suspend fun apply(propertyId: String, issueId: String, value: IssueValue?): RunIssue {
        guard.requireOnline(Permission.CHECK)
        return repo.accept(propertyId, issueId, value)
    }

    /** A typed value; blank is refused. */
    suspend fun applyText(propertyId: String, issueId: String, text: String): RunIssue {
        guard.requireOnline(Permission.CHECK)
        if (text.isBlank()) throw OnlineException(OnlineError.Invalid)
        return repo.acceptText(propertyId, issueId, text)
    }

    suspend fun dismiss(propertyId: String, issueId: String): RunIssue {
        guard.requireOnline(Permission.CHECK)
        return repo.dismiss(propertyId, issueId)
    }
}

/** Undoes a fix (restores the old value) or reopens a kept issue. Reviewers only. */
class RevertFixUseCase @Inject constructor(private val repo: ValidationRunRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(propertyId: String, issueId: String): RunIssue {
        guard.requireOnline(Permission.VALIDATE)
        return repo.revert(propertyId, issueId)
    }
}

/** The run's open issues sure enough to fix in one go. */
fun List<RunIssue>.confidentFixes(): List<RunIssue> = filter { it.confident }

/** Applies the best suggestion of every confident fix. Needs [Permission.CHECK]. */
class ApplyConfidentFixesUseCase @Inject constructor(private val repo: ValidationRunRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(propertyId: String, issues: List<RunIssue>): BulkFixResult {
        guard.requireOnline(Permission.CHECK)
        val ids = issues.confidentFixes().map { it.id }
        if (ids.isEmpty()) return BulkFixResult(0, 0, 0)
        return repo.acceptAll(propertyId, ids, CONFIDENT)
    }
}

/**
 * Marks assets as validated, sends them back for recollection, excludes them, or undoes the decision. Reviewers only.
 * Only submitted assets are marked as validated: the server leaves drafts and exceptions as they are and names them.
 */
class DecideAssetsUseCase @Inject constructor(private val repo: ValidationRunRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(propertyId: String, jobId: String, assetIds: List<String>, decision: Decision, note: String?): DecisionOutcome {
        guard.requireOnline(Permission.VALIDATE)
        if (assetIds.isEmpty()) return DecisionOutcome(0)
        return repo.decide(propertyId, jobId, assetIds, decision, note?.takeIf { decision == Decision.RECOLLECT })
    }
}

/** Merges duplicate assets into the one kept, with the value chosen for each field that differs. Reviewers only. */
class MergeDuplicatesUseCase @Inject constructor(private val repo: ValidationRunRepository, private val guard: PermissionGuard) {
    suspend fun load(propertyId: String, assetIds: List<String>): List<MergeAsset> = assetIds.distinct().map { repo.mergeAsset(propertyId, it) }

    /** The value each field gets by default: the kept asset's when it has one, else the first other asset's. */
    fun defaults(keep: MergeAsset, all: List<MergeAsset>): Map<String, IssueValue> {
        val ordered = listOf(keep) + all.filter { it.id != keep.id }
        return differingFields(all).associateWith { f -> ordered.firstNotNullOfOrNull { a -> a.values[f]?.takeIf { it.text.isNotBlank() } } }
            .mapNotNull { (k, v) -> v?.let { k to it } }.toMap()
    }

    suspend operator fun invoke(propertyId: String, keepId: String, assetIds: List<String>, values: Map<String, IssueValue>) {
        guard.requireOnline(Permission.VALIDATE)
        val others = assetIds.filter { it != keepId }.distinct()
        if (others.isEmpty()) throw OnlineException(OnlineError.Invalid)
        repo.merge(propertyId, keepId, others, values)
    }

    companion object {
        /** The fields (ids) whose values differ between [assets]. */
        fun differingFields(assets: List<MergeAsset>): List<String> =
            assets.flatMap { it.values.keys }.distinct().filter { f -> assets.map { it.values[f]?.text.orEmpty().trim() }.distinct().size > 1 }
    }
}

/** How checking one asset of a run again ended. */
sealed interface RecheckOutcome {
    /** Its new result is in the run. */
    data object Done : RecheckOutcome

    /** The check failed; [detail] is the server's reason, when it gave one. */
    data class Failed(val detail: String?) : RecheckOutcome

    /** Still going after [RecheckAssetUseCase.GIVE_UP_MS]: its result comes into the run when it is done. */
    data object StillChecking : RecheckOutcome
}

/**
 * "Revalidate" in a run's review (the web's): checks one asset of the finished run again with the run's steps and
 * settings, inside that run (the rest of the run isn't checked again), and waits until its new result is in the run:
 * the server removes the run doing the check once it has folded it in, so asking for it then answers NotFound. Needs
 * [Permission.CHECK]. Throws [OnlineException] when it can't be started or asked about.
 */
class RecheckAssetUseCase @Inject constructor(private val repo: ValidationRunRepository, private val guard: PermissionGuard) {
    suspend operator fun invoke(propertyId: String, jobId: String, assetId: String): RecheckOutcome {
        guard.requireOnline(Permission.CHECK)
        val check = repo.recheck(propertyId, jobId, assetId)
        var waited = 0L
        while (true) {
            delay(POLL_MS)
            waited += POLL_MS
            val state = try {
                repo.run(propertyId, check.id)
            } catch (e: OnlineException) {
                if (e.error == OnlineError.NotFound) return RecheckOutcome.Done
                throw e
            }
            if (state.status == RunStatus.FAILED) return RecheckOutcome.Failed(state.statusDetail?.takeIf { it.isNotBlank() })
            if (waited > GIVE_UP_MS) return RecheckOutcome.StillChecking
        }
    }

    companion object {
        const val POLL_MS = 1_500L
        const val GIVE_UP_MS = 3 * 60 * 1_000L
    }
}

/** Reviewing a run, for injecting as one. */
class RunReviewUseCases @Inject constructor(
    val access: ValidationAccessUseCase,
    val run: GetValidationRunUseCase,
    val results: GetRunResultsUseCase,
    val issues: GetRunIssuesUseCase,
    val insights: GetRunInsightsUseCase,
    val export: ExportRunUseCase,
    val fix: FixIssueUseCase,
    val revert: RevertFixUseCase,
    val applyConfident: ApplyConfidentFixesUseCase,
    val decide: DecideAssetsUseCase,
    val merge: MergeDuplicatesUseCase,
    val assetInfo: GetReviewAssetInfoUseCase,
    val recheck: RecheckAssetUseCase,
)
