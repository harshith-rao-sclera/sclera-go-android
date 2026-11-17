package com.sclera.sclerago.domain.usecase.location

import com.sclera.sclerago.domain.model.LocNode
import com.sclera.sclerago.domain.repository.LocationRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

// The building → floor → location tree of the downloaded property, for the location picker.
// Configuration → Building info reads and changes the places at the server (domain/usecase/config/PlacesConfigUseCases).

class ObserveLocationTreeUseCase @Inject constructor(private val repo: LocationRepository) {
    operator fun invoke(): StateFlow<List<LocNode>> = repo.tree
}

/** The location use cases, for injecting as one. */
class LocationUseCases @Inject constructor(
    val observeTree: ObserveLocationTreeUseCase,
)
