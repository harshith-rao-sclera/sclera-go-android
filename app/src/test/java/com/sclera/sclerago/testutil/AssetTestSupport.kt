package com.sclera.sclerago.testutil

import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.AssetActivity
import com.sclera.sclerago.domain.model.AssetCatalog
import com.sclera.sclerago.domain.model.AssetConfig
import com.sclera.sclerago.domain.model.AssetDetails
import com.sclera.sclerago.domain.model.LocValue
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.NewAsset
import com.sclera.sclerago.domain.model.DEFAULT_PHOTO_RULES
import com.sclera.sclerago.domain.model.PhotoRule
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.repository.AssetActivityRepository
import com.sclera.sclerago.domain.repository.AssetConfigRepository
import com.sclera.sclerago.domain.repository.AssetRepository
import com.sclera.sclerago.domain.repository.AutofillRepository
import com.sclera.sclerago.domain.repository.PhotoRulesRepository
import com.sclera.sclerago.domain.system.IdGenerator
import com.sclera.sclerago.domain.system.TimeProvider
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import com.sclera.sclerago.domain.usecase.asset.AddAssetsUseCase
import com.sclera.sclerago.domain.usecase.asset.AddCommentUseCase
import com.sclera.sclerago.domain.usecase.asset.AssetUseCases
import com.sclera.sclerago.domain.usecase.asset.AssignLocationUseCase
import com.sclera.sclerago.domain.usecase.asset.AttachMediaUseCase
import com.sclera.sclerago.domain.usecase.asset.AutofillAssetUseCase
import com.sclera.sclerago.domain.usecase.asset.BuildAssetListUseCase
import com.sclera.sclerago.domain.usecase.asset.BuildCheckInputUseCase
import com.sclera.sclerago.domain.usecase.asset.BuildFormValuesUseCase
import com.sclera.sclerago.domain.usecase.asset.CreateAssetUseCase
import com.sclera.sclerago.domain.usecase.asset.DeleteAssetsUseCase
import com.sclera.sclerago.domain.usecase.asset.EvaluateAssetChecksUseCase
import com.sclera.sclerago.domain.usecase.asset.GetAssetDetailsUseCase
import com.sclera.sclerago.domain.usecase.asset.MatchDevicesUseCase
import com.sclera.sclerago.domain.usecase.asset.ObserveAssetActivityUseCase
import com.sclera.sclerago.domain.usecase.asset.ObserveAssetsUseCase
import com.sclera.sclerago.domain.usecase.asset.SaveAssetDetailsUseCase
import com.sclera.sclerago.domain.usecase.asset.SelectAllUseCase
import com.sclera.sclerago.domain.usecase.asset.SetAssetStatusUseCase
import com.sclera.sclerago.domain.usecase.asset.ToggleSelectionUseCase
import com.sclera.sclerago.presentation.preview.SAMPLE_ASSETS
import com.sclera.sclerago.presentation.preview.SAMPLE_DETAILS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * The assets in memory, standing in for the offline database: writes change [catalog] at once, as the database's
 * change triggers would. [details] are the extra fields per asset id.
 */
class InMemoryAssetRepository(
    assets: List<Asset> = SAMPLE_ASSETS,
    details: Map<String, AssetDetails> = SAMPLE_DETAILS,
) : AssetRepository {
    override val catalog = MutableStateFlow(AssetCatalog(assets))
    val details = details.toMutableMap()

    private fun change(f: (List<Asset>) -> List<Asset>) {
        catalog.value = catalog.value.copy(assets = f(catalog.value.assets))
    }

    private fun Collection<String>.update(f: (Asset) -> Asset) = change { list -> list.map { if (it.id in this) f(it) else it } }

    override suspend fun add(assets: List<NewAsset>) {
        change { assets.map { it.asset } + it }
        assets.forEach { n ->
            if (n.values.isNotEmpty()) details[n.asset.id] = AssetDetails(n.asset.id, n.values)
            n.deviceId?.let { catalog.value = catalog.value.copy(deviceAsset = catalog.value.deviceAsset + (it to n.asset.id)) }
        }
    }

    override suspend fun setStatus(ids: Collection<String>, status: Status, photoRules: List<com.sclera.sclerago.domain.model.PhotoRule>): com.sclera.sclerago.domain.model.SubmitOutcome {
        ids.update { it.copy(st = status) }
        return com.sclera.sclerago.domain.model.SubmitOutcome()
    }

    override suspend fun saveDetails(id: String, values: Map<String, String>) {
        val before = details[id]
        details[id] = before?.copy(fields = before.fields + values) ?: AssetDetails(id, values)
        values["name"]?.let { name -> listOf(id).update { it.copy(name = name) } }
    }

    override suspend fun delete(ids: Collection<String>) = change { list -> list.filterNot { it.id in ids } }

    override suspend fun assignLocation(ids: Collection<String>, location: LocValue) =
        ids.update { it.copy(loc = location.loc, where = location.where, locationId = location.placeId) }

    override suspend fun attachMedia(id: String, media: List<Media>) = listOf(id).update { it.copy(media = it.media + media) }

    override suspend fun details(id: String): AssetDetails? = details[id]

    override suspend fun detailsFor(ids: Collection<String>): Map<String, AssetDetails> = details.filterKeys { it in ids }
}

/** An [AutofillRepository] with no delay: it suggests what the test sets (only for fields still empty) and counts its calls. */
class FakeAutofillRepository(var suggestions: Map<String, String> = emptyMap()) : AutofillRepository {
    var calls = 0
    override suspend fun suggest(asset: Asset, values: Map<String, String>): Map<String, String> {
        calls++
        return suggestions.filterKeys { values[it].isNullOrBlank() || values[it] == "Generic" }
    }
}

/** Comments and history per asset in memory; [addComment] adds a comment by "me" as the newest entry. */
class InMemoryActivityRepository(initial: Map<String, List<AssetActivity>> = emptyMap()) : AssetActivityRepository {
    private val flow = MutableStateFlow(initial)
    val comments = mutableListOf<Pair<String, String>>()

    override fun observe(assetId: String): Flow<List<AssetActivity>> = flow.map { it[assetId].orEmpty() }

    override suspend fun addComment(assetId: String, text: String) {
        comments += assetId to text
    }
}

/** The asset fields every asset has, in memory (the app reads them from the offline database). */
class InMemoryAssetConfig(initial: AssetConfig = AssetConfig(emptyList())) : AssetConfigRepository {
    override val config = MutableStateFlow(initial)
}

/** The property's photo rules in memory (the server's defaults unless the test sets others). */
class FakePhotoRulesRepository(rules: List<PhotoRule> = DEFAULT_PHOTO_RULES) : PhotoRulesRepository {
    val flow = MutableStateFlow(rules)
    override fun rules(): Flow<List<PhotoRule>> = flow
}

/** The asset use cases over the given repositories, wired as Hilt does ([guard] lets everything through by default). */
fun assetUseCases(
    repo: AssetRepository = InMemoryAssetRepository(),
    config: AssetConfigRepository = InMemoryAssetConfig(),
    photoRules: PhotoRulesRepository = FakePhotoRulesRepository(),
    autofill: AutofillRepository = FakeAutofillRepository(),
    activity: AssetActivityRepository = InMemoryActivityRepository(),
    time: TimeProvider = FakeTimeProvider(),
    ids: IdGenerator = SequentialIdGenerator(),
    guard: PermissionGuard = allowAllGuard(),
) = AssetUseCases(
    ObserveAssetsUseCase(repo), GetAssetDetailsUseCase(repo), SetAssetStatusUseCase(repo, guard), SaveAssetDetailsUseCase(repo, guard),
    DeleteAssetsUseCase(repo, guard), AssignLocationUseCase(repo, guard), AttachMediaUseCase(repo, guard), AddAssetsUseCase(repo, ids, guard),
    CreateAssetUseCase(repo, ids, guard), MatchDevicesUseCase(repo), BuildCheckInputUseCase(repo, config, photoRules),
    BuildAssetListUseCase(), ToggleSelectionUseCase(), SelectAllUseCase(), EvaluateAssetChecksUseCase(),
    AutofillAssetUseCase(autofill, guard), BuildFormValuesUseCase(time),
    ObserveAssetActivityUseCase(activity), AddCommentUseCase(activity, guard),
)
