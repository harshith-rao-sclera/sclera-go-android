package com.sclera.sclerago.domain.usecase.integration

import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.PlacesDirection
import com.sclera.sclerago.domain.model.PlacesHistoryPage
import com.sclera.sclerago.domain.model.PlacesTransferDetail
import com.sclera.sclerago.domain.repository.PlacesHistoryRepository
import javax.inject.Inject

// The history of buildings, floors and locations sent to VDMS and imported from it (Integration page → History), as
// on the web. Anyone who can open the property can read it.

/** [limit] transfers from [offset], newest first; only those going [direction] when given. */
class GetPlacesHistoryUseCase @Inject constructor(private val repo: PlacesHistoryRepository) {
    suspend operator fun invoke(propertyId: String, direction: PlacesDirection?, limit: Int, offset: Int): PlacesHistoryPage =
        repo.page(propertyId, direction, limit, offset)
}

/** One transfer with the places it sent, added, updated and skipped. */
class GetPlacesTransferUseCase @Inject constructor(private val repo: PlacesHistoryRepository) {
    suspend operator fun invoke(propertyId: String, transferId: String): PlacesTransferDetail {
        if (transferId.isBlank()) throw OnlineException(OnlineError.Invalid)
        return repo.transfer(propertyId, transferId)
    }
}

/** The places history use cases, for injecting as one. */
class PlacesHistoryUseCases @Inject constructor(
    val page: GetPlacesHistoryUseCase,
    val transfer: GetPlacesTransferUseCase,
)
