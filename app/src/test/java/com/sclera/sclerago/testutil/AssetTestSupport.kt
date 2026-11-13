package com.sclera.sclerago.testutil

import com.sclera.sclerago.data.repository.AssetConfigRepositoryImpl
import com.sclera.sclerago.data.repository.AssetRepositoryImpl
import com.sclera.sclerago.domain.model.AssetType
import com.sclera.sclerago.domain.repository.AssetConfigRepository
import com.sclera.sclerago.domain.repository.AssetRepository
import com.sclera.sclerago.domain.repository.AutofillRepository
import com.sclera.sclerago.domain.system.TimeProvider
import com.sclera.sclerago.domain.system.IdGenerator
import com.sclera.sclerago.domain.usecase.asset.AddAssetsUseCase
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
import com.sclera.sclerago.domain.usecase.asset.ObserveAssetsUseCase
import com.sclera.sclerago.domain.usecase.asset.SaveAssetDetailsUseCase
import com.sclera.sclerago.domain.usecase.asset.SelectAllUseCase
import com.sclera.sclerago.domain.usecase.asset.SetAssetStatusUseCase
import com.sclera.sclerago.domain.usecase.asset.ToggleSelectionUseCase

/** An [AutofillRepository] with no delay: it suggests what the test sets and counts its calls. */
class FakeAutofillRepository(var suggestions: Map<String, String> = emptyMap()) : AutofillRepository {
    var calls = 0
    override suspend fun suggest(type: AssetType, values: Map<String, String>): Map<String, String> {
        calls++
        return suggestions.filterKeys { values[it].isNullOrBlank() || values[it] == "Generic" }
    }
}

/** The asset use cases over the given repositories, wired as Hilt does. */
fun assetUseCases(
    repo: AssetRepository = AssetRepositoryImpl(),
    config: AssetConfigRepository = AssetConfigRepositoryImpl(),
    autofill: AutofillRepository = FakeAutofillRepository(),
    time: TimeProvider = FakeTimeProvider(),
    ids: IdGenerator = SequentialIdGenerator(),
) = AssetUseCases(
    ObserveAssetsUseCase(repo), GetAssetDetailsUseCase(repo), SetAssetStatusUseCase(repo), SaveAssetDetailsUseCase(repo),
    DeleteAssetsUseCase(repo), AssignLocationUseCase(repo), AttachMediaUseCase(repo), AddAssetsUseCase(repo, ids),
    CreateAssetUseCase(repo, ids), MatchDevicesUseCase(repo), BuildCheckInputUseCase(repo, config),
    BuildAssetListUseCase(), ToggleSelectionUseCase(), SelectAllUseCase(), EvaluateAssetChecksUseCase(),
    AutofillAssetUseCase(autofill), BuildFormValuesUseCase(time),
)

