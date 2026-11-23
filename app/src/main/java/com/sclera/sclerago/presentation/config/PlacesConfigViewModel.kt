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
import com.sclera.sclerago.domain.model.SheetRejectedException
import com.sclera.sclerago.domain.usecase.config.PlaceProblem
import com.sclera.sclerago.domain.usecase.config.PlaceRefused
import com.sclera.sclerago.domain.usecase.config.PlacesConfigUseCases
import com.sclera.sclerago.domain.usecase.property.ValidateImportFileUseCase
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.toUiText
import com.sclera.sclerago.presentation.property.PickedSheet
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
class PlacesConfigViewModel @Inject constructor(
    private val useCases: PlacesConfigUseCases,
    validateFile: ValidateImportFileUseCase,
) : ViewModel() {
    private var propertyId = ""
    private var loadJob: Job? = null
    private val sheetImport = SheetImportFlow(validateFile)

    /** The Import buildings sheet; null while it is closed. */
    val importing: SheetImportState? get() = sheetImport.state

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
        val deleted = useCases.delete(propertyId, places)
        if (deleted > 0) {
            say(UiText.Plural(R.plurals.cfg_places_deleted, deleted))
            onDeleted()
        }
    }

    // ---- Import buildings (as the web's Import: the server reads the sheet and makes everything in one go)

    fun openImport() = sheetImport.open()

    fun closeImport() = sheetImport.close()

    fun pickImport(file: PickedSheet) = sheetImport.pick(file)

    /** Sample Format: saves the server's sample sheet to [destination] (a document the user picked). */
    fun saveImportSample(destination: String) {
        val property = propertyId
        viewModelScope.launch { say(saveSampleMessage { useCases.saveImportSample(property, destination) }) }
    }

    /**
     * Import: the server matches the sheet's columns by heading, checks every row and makes the buildings, floors and
     * locations (all or nothing). Says what was added and skipped, and the columns it didn't read; a refused sheet is
     * told in the sheet, with the rows to fix.
     */
    fun runImport() {
        val file = sheetImport.begin() ?: return
        val property = propertyId
        viewModelScope.launch {
            try {
                val r = useCases.importBuildings(property, file.uri)
                sheetImport.close()
                say(placesImportedMessage(r))
                ignoredColumnsMessage(r.ignoredColumns)?.let(::say)
                reload(showLoading = false)
            } catch (e: SheetRejectedException) {
                sheetImport.refused(refusedMessage(e.rows, R.string.cfg_import_places_sheet_wrong))
            } catch (e: OnlineException) {
                sheetImport.failed()
                say(importFailure(e))
            }
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
    },
)
