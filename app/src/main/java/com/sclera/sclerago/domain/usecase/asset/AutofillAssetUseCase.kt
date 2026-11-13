package com.sclera.sclerago.domain.usecase.asset

import com.sclera.sclerago.domain.model.AssetType
import com.sclera.sclerago.domain.repository.AutofillRepository
import javax.inject.Inject

/** Asks for suggestions for the empty fields of an asset's details form. */
class AutofillAssetUseCase @Inject constructor(private val repo: AutofillRepository) {
    suspend operator fun invoke(type: AssetType, values: Map<String, String>): Map<String, String> = repo.suggest(type, values)
}
