package com.sclera.sclerago.presentation.config

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.BuildingInfo
import com.sclera.sclerago.domain.model.LocNode
import com.sclera.sclerago.domain.model.NewLocation
import com.sclera.sclerago.domain.model.NodeKind
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.usecase.config.ImportBuildingsUseCase
import com.sclera.sclerago.domain.usecase.config.PlaceProblem
import com.sclera.sclerago.domain.usecase.config.PlaceRefused
import com.sclera.sclerago.domain.usecase.config.PlacesConfigUseCases
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.toUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Configuration → Building info, read and changed at the server (`/buildings`, `/floors`, `/locations`) as the web does.
 * The tree is read again after every change. Messages for the screen come through [messages].
 */
@HiltViewModel
class PlacesConfigViewModel @Inject constructor(private val useCases: PlacesConfigUseCases) : ViewModel() {
    private var propertyId = ""
    private var loadJob: Job? = null

    var load by mutableStateOf(LoadState.LOADING)
        private set

    /** Why the places could not be read (no connection, not allowed, …). */
    var failure by mutableStateOf<UiText?>(null)
        private set

    /** Buildings → floors → locations, hidden ones too. */
    var tree by mutableStateOf<List<LocNode>>(emptyList())
        private set

    private val _messages = Channel<UiText>(Channel.BUFFERED)
    val messages: Flow<UiText> = _messages.receiveAsFlow()

    private fun say(text: UiText) {
        _messages.trySend(text)
    }

    fun open(property: String) {
        propertyId = property
        reload(showLoading = true)
    }

    fun retry() = reload(showLoading = true)

    private fun reload(showLoading: Boolean) {
        loadJob?.cancel()
        if (showLoading) {
            load = LoadState.LOADING
            failure = null
        }
        val property = propertyId
        loadJob = viewModelScope.launch {
            try {
                tree = useCases.load(property)
                load = LoadState.READY
            } catch (e: OnlineException) {
                if (showLoading || load != LoadState.READY) {
                    failure = e.error.toUiText()
                    load = LoadState.FAILED
                } else {
                    say(e.error.toUiText())
                }
            }
        }
    }

    /** Runs a change at the server, then reads the tree again; a failure is told (never its details). */
    private fun send(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: OnlineException) {
                say(e.error.toUiText())
            }
            reload(showLoading = false)
        }
    }

    fun addBuilding(name: String, info: BuildingInfo?, onAdded: (String) -> Unit) = send {
        useCases.addBuilding(propertyId, tree, name, info)
            .onSuccess { onAdded(it); say(UiText.Res(R.string.cfg_building_added)) }
            .onFailure { refused(it, NodeKind.BUILDING) }
    }

    /** One floor, or several (bulk), each with its name, type and code; the first new one is shown. Names already there are skipped. */
    fun addFloors(building: LocNode, rows: List<NewLocation>, onAdded: (String) -> Unit) = send {
        val r = useCases.addFloors(propertyId, building, rows)
        r.firstId?.let(onAdded)
        when {
            r.added > 0 && r.skipped > 0 -> say(UiText.Res(R.string.cfg_locations_added_skipped, listOf(r.added, r.skipped)))
            r.added == 1 -> say(UiText.Res(R.string.cfg_floor_added))
            r.added > 1 -> say(UiText.Plural(R.plurals.cfg_floors_added, r.added))
            r.skipped > 0 -> say(UiText.Res(R.string.cfg_floor_exists))
        }
    }

    /** One location, or several (bulk), each with its own name, type and code; names already on the floor are skipped and counted. */
    fun addLocations(floor: LocNode, rows: List<NewLocation>) = send {
        val r = useCases.addLocations(propertyId, floor, rows)
        when {
            r.added > 0 && r.skipped > 0 -> say(UiText.Res(R.string.cfg_locations_added_skipped, listOf(r.added, r.skipped)))
            r.added > 0 -> say(UiText.Plural(R.plurals.cfg_locations_added, r.added))
            r.skipped > 0 -> say(UiText.Res(R.string.cfg_location_exists))
        }
    }

    fun saveBuilding(building: LocNode, name: String, info: BuildingInfo, visible: Boolean) = send {
        val problem = useCases.saveBuilding(propertyId, tree, building, name, info, visible)
        say(problem?.message(NodeKind.BUILDING) ?: UiText.Res(R.string.cfg_building_saved))
    }

    fun saveFloors(building: LocNode, floors: List<LocNode>) = send {
        val problem = useCases.saveFloors(propertyId, building, floors)
        say(problem?.message(NodeKind.FLOOR) ?: UiText.Res(R.string.cfg_floors_saved))
    }

    fun updateLocation(floor: LocNode, location: LocNode, name: String, type: String, code: String) = send {
        val problem = useCases.updateLocation(propertyId, floor, location, name, type, code)
        say(problem?.message(NodeKind.LOCATION) ?: UiText.Res(R.string.cfg_location_saved))
    }

    /** Deletes [places]; [onDeleted] runs when every one is gone. */
    fun delete(places: List<LocNode>, onDeleted: () -> Unit = {}) = send {
        val r = useCases.delete(propertyId, places)
        if (r.problem != null) {
            say(r.problem.message(places.firstOrNull()?.kind ?: NodeKind.LOCATION))
        } else if (r.deleted > 0) {
            say(UiText.Plural(R.plurals.cfg_places_deleted, r.deleted))
            onDeleted()
        }
    }

    /** Import buildings: rows of Building, Floor, Location, Type, Code from a CSV or Excel file, added at the server. */
    fun importBuildings(uri: String) = send {
        when (val r = useCases.importBuildings(propertyId, tree, uri)) {
            ImportBuildingsUseCase.Result.Unreadable -> say(UiText.Res(R.string.cfg_import_unreadable))
            ImportBuildingsUseCase.Result.NothingFound -> say(UiText.Res(R.string.cfg_import_buildings_nothing))
            is ImportBuildingsUseCase.Result.Imported ->
                say(if (r.added == 0) UiText.Res(R.string.cfg_import_buildings_all_there) else UiText.Plural(R.plurals.cfg_import_buildings_done, r.rows))
        }
    }

    private fun refused(e: Throwable, kind: NodeKind) {
        val problem = (e as? PlaceRefused)?.problem ?: PlaceProblem.DUPLICATE
        say(problem.message(kind))
    }
}

/** What a refused place change says; a duplicate names the kind of place. */
private fun PlaceProblem.message(kind: NodeKind): UiText = UiText.Res(
    when (this) {
        PlaceProblem.DUPLICATE -> when (kind) {
            NodeKind.BUILDING -> R.string.cfg_building_exists
            NodeKind.FLOOR -> R.string.cfg_floor_exists
            NodeKind.LOCATION -> R.string.cfg_location_exists
        }
        PlaceProblem.BUILDING_HAS_FLOORS -> R.string.cfg_building_has_floors
        PlaceProblem.FLOOR_HAS_LOCATIONS -> R.string.cfg_floor_has_locations
        PlaceProblem.IN_USE -> R.string.cfg_place_in_use
    },
)
