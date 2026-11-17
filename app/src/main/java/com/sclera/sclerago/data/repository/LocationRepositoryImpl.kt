package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.offline.PlacesOfflineSource
import com.sclera.sclerago.di.ApplicationScope
import com.sclera.sclerago.domain.model.LocNode
import com.sclera.sclerago.domain.repository.LocationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/** The building, floor and location tree of the downloaded property, from the offline database (read only). */
@Singleton
class LocationRepositoryImpl @Inject constructor(
    source: PlacesOfflineSource,
    @ApplicationScope scope: CoroutineScope,
) : LocationRepository {
    override val tree: StateFlow<List<LocNode>> = source.treeFlow().stateIn(scope, SharingStarted.Eagerly, emptyList())
}
