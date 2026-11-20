package com.sclera.sclerago.presentation.property

import com.sclera.sclerago.presentation.model.OnlineOnly
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.AddressSuggestion
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.domain.model.PropertyDownload
import com.sclera.sclerago.domain.model.PropertyDownloads
import com.sclera.sclerago.domain.model.PropertySummary
import com.sclera.sclerago.domain.usecase.property.AddPropertyUseCase
import com.sclera.sclerago.domain.usecase.property.AddressUseCases
import com.sclera.sclerago.domain.usecase.property.ImportFileProblem
import com.sclera.sclerago.domain.usecase.property.PropertyDownloadUseCases
import com.sclera.sclerago.domain.usecase.property.PropertyProblem
import com.sclera.sclerago.domain.usecase.property.PropertyUseCases
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.toUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the Property list shows: the matching rows, the totals, which sheet is open, and a message to flash. */
data class PropertyListState(
    val query: String = "",
    val rows: List<PropertySummary> = emptyList(),
    /** Every property (not only the matching rows). */
    val properties: List<Property> = emptyList(),
    val propertyTotal: Int = 0,
    /** How many properties are synced to the device (their data downloaded). */
    val syncedTotal: Int = 0,
    val addOpen: Boolean = false,
    /** Why the server didn't create the property, shown in the Add sheet (which stays open). */
    val addError: UiText? = null,
    /** The property being edited (its info button), as the server has it. */
    val editing: Property? = null,
    /** Why the edit couldn't be saved, shown in the edit sheet (which stays open). */
    val editError: UiText? = null,
    /** The property waiting for Delete's confirmation. */
    val confirmDelete: Property? = null,
    val importOpen: Boolean = false,
    val notice: UiText? = null,
    /** The downloaded property and the downloads under way. */
    val downloads: PropertyDownloads = PropertyDownloads(),
    /** Offline, properties that aren't downloaded are faded and can't be opened or downloaded. */
    val online: Boolean = true,
    /** The properties are being read from the server and none are saved on the device yet: a loader shows instead of the list. */
    val loading: Boolean = false,
    /** A pull to refresh is reading the properties from the server again. */
    val refreshing: Boolean = false,
)

/** Property list screen: search, add, import and download. Opening a property is the caller's (navigation) job. */
@HiltViewModel
class PropertyViewModel @Inject constructor(
    private val useCases: PropertyUseCases,
    private val downloads: PropertyDownloadUseCases,
    private val address: AddressUseCases,
) : ViewModel() {
    /** Whether Add and Import are offered: only with Manage properties, as the web's "+ Property" and Import buttons. */
    val canManage: StateFlow<Boolean> = useCases.canManage().stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Suggestions under the Add form's City, State or Country field while it is being typed in. */
    private val _suggestions = MutableStateFlow<AddressSuggestions?>(null)
    val suggestions: StateFlow<AddressSuggestions?> = _suggestions
    private var suggestJob: Job? = null

    private val query = MutableStateFlow("")
    private val flags = MutableStateFlow(Flags())
    private val online = MutableStateFlow(downloads.isOnline())

    /** [refreshing]: a server read (when the list opened, or a pull) hasn't finished yet; [pulled]: it was a pull to refresh. */
    private data class Flags(
        val addOpen: Boolean = false,
        val addError: UiText? = null,
        val editing: Property? = null,
        val editError: UiText? = null,
        val confirmDelete: Property? = null,
        val importOpen: Boolean = false,
        val notice: UiText? = null,
        val refreshing: Boolean = true,
        val pulled: Boolean = false,
    )
    private var adding = false

    /** Failed downloads already told about, so each failure is announced once. */
    private val announcedFailures = mutableSetOf<String>()

    val state: StateFlow<PropertyListState> =
        combine(useCases.summaries(), query, flags, downloads.observe(), online) { all, q, f, d, on ->
            PropertyListState(
                query = q,
                // Downloaded properties first; each group A to Z.
                rows = useCases.filter(all, q, d.downloadedIds),
                properties = all.map { it.property },
                propertyTotal = all.size,
                syncedTotal = all.count { it.property.id in d.downloadedIds },
                addOpen = f.addOpen,
                addError = f.addError,
                editing = f.editing,
                editError = f.editError,
                confirmDelete = f.confirmDelete,
                importOpen = f.importOpen,
                notice = f.notice,
                downloads = d,
                online = on,
                // Saved properties show straight away; only an empty list waits for the server.
                loading = f.refreshing && all.isEmpty(),
                refreshing = f.pulled,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, PropertyListState())

    init {
        // Properties are read from the server whenever the list opens; offline the saved list stays.
        load(pulled = false)
        // Offline, properties that aren't downloaded fade out and the online-only buttons carry the offline badge.
        viewModelScope.launch { downloads.isOnline.observe().collect { online.value = it } }
        viewModelScope.launch {
            downloads.observe().collect { d ->
                val failed = d.active.filterValues { it is PropertyDownload.Failed }.keys
                announcedFailures.retainAll(failed)
                (failed - announcedFailures).firstOrNull()?.let { id ->
                    announcedFailures += id
                    val name = state.value.properties.firstOrNull { it.id == id }?.name ?: id
                    flags.update { it.copy(notice = UiText.Res(R.string.property_download_failed, listOf(name))) }
                }
            }
        }
    }

    /** Pull to refresh: reads the properties from the server again; offline the user is told, and the saved list stays. */
    fun refresh() {
        if (flags.value.refreshing) return
        flags.update { it.copy(refreshing = true, pulled = true) }
        load(pulled = true)
    }

    /** Reads the properties from the server; only a pull says so when the device is offline. */
    private fun load(pulled: Boolean) {
        viewModelScope.launch {
            try {
                useCases.refresh()
            } catch (e: OnlineException) {
                if (pulled || e.error != OnlineError.Offline) flags.update { it.copy(notice = e.error.toUiText()) }
            } finally {
                flags.update { it.copy(refreshing = false, pulled = false) }
            }
        }
    }

    fun setQuery(value: String) {
        query.value = value
    }

    /** Add: the property is created on the server, so offline the form doesn't open and the user is told why. */
    fun openAdd() {
        if (!downloads.isOnline()) {
            online.value = false
            explainOffline(OnlineOnly.NEW_PROPERTY)
            return
        }
        flags.update { it.copy(addOpen = true) }
    }
    fun closeAdd() {
        clearSuggestions()
        flags.update { it.copy(addOpen = false, addError = null) }
    }
    /** Import: properties are imported on the server, so offline the sheet doesn't open and the user is told why. */
    fun openImport() {
        if (!downloads.isOnline()) {
            online.value = false
            explainOffline(OnlineOnly.IMPORT_PROPERTIES)
            return
        }
        flags.update { it.copy(importOpen = true) }
    }
    fun closeImport() = flags.update { it.copy(importOpen = false) }
    /** Back to a fresh list (called when the screen is left). */
    fun reset() {
        query.value = ""
        // The server read runs once per screen model, so whether it has finished is kept.
        flags.update { Flags(refreshing = it.refreshing) }
    }

    fun consumeNotice() = flags.update { it.copy(notice = null) }

    /** Reads the connection again now (between the changes the watch started in init brings). */
    fun checkConnection() {
        online.value = downloads.isOnline()
    }

    /** An online-only button was tapped offline: says why it needs the server. */
    fun explainOffline(why: OnlineOnly) = flags.update { it.copy(notice = UiText.Res(why.message)) }

    /** Download button: fetches [propertyId]'s data for working offline. Offline it can't, and says so. */
    fun download(propertyId: String) {
        if (!downloads.start(propertyId)) {
            online.value = false
            explainOffline(OnlineOnly.DOWNLOAD_PROPERTY)
        }
    }

    /**
     * Adds [draft]; returns why it can't be added, or null when it is on its way. As on the web, the sheet just closes
     * (the list shows the new property) once the server has created it, or "Unable to create property" says it didn't.
     */
    fun addProperty(draft: Property): UiText? {
        useCases.validate(draft)?.let { return it.toUiText() }
        if (adding) return null
        // The connection dropped while the form was open: nothing is sent, and what was typed stays.
        if (!downloads.isOnline()) {
            online.value = false
            return UiText.Res(OnlineOnly.NEW_PROPERTY.message)
        }
        adding = true
        flags.update { it.copy(addError = null) }
        viewModelScope.launch {
            when (val r = useCases.add(draft)) {
                is AddPropertyUseCase.Result.Added -> {
                    clearSuggestions()
                    flags.update { it.copy(addOpen = false, addError = null) }
                }
                is AddPropertyUseCase.Result.Invalid -> flags.update { it.copy(addError = r.problem.toUiText()) }
                is AddPropertyUseCase.Result.Failed -> flags.update { it.copy(addError = UiText.Res(R.string.property_create_failed)) }
            }
            adding = false
        }
        return null
    }

    /**
     * Asks for suggestions for [field] matching [text] (a moment after typing stops), given the [country] and [state]
     * entered so far; the lists open as soon as the field is tapped, as on the web. Offline, nothing is suggested.
     */
    fun suggestAddress(field: AddressField, country: String, state: String, text: String) {
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            if (text.isNotBlank()) delay(SUGGEST_DELAY_MS)
            val found = try {
                when (field) {
                    AddressField.COUNTRY -> address.countries(text)
                    AddressField.STATE -> address.states(country, text)
                    AddressField.CITY -> address.cities(country, state, text)
                }
            } catch (_: OnlineException) {
                emptyList()
            }
            _suggestions.value = AddressSuggestions(field, found)
        }
    }

    fun clearSuggestions() {
        suggestJob?.cancel()
        _suggestions.value = null
    }

    // ---- The info button on a property: its details open straight in the edit form (Save), with Delete in the footer.

    /** Editing and deleting a property happen on the server, so offline they are blocked (and the user is told why). */
    private fun blockedOffline(): Boolean {
        if (downloads.isOnline()) return false
        online.value = false
        // With the form open the message shows in it (toasts are hidden behind it); otherwise as a toast.
        val offline = UiText.Res(OnlineOnly.EDIT_PROPERTY.message)
        flags.update { if (it.editing != null) it.copy(editError = offline) else it.copy(notice = offline) }
        return true
    }

    /**
     * Opens [p] in the form, as the server has it now (the list's copy when it can't be read, as the web). Offline it
     * opens with the copy saved on the device, to look at only: editing and deleting happen on the server.
     */
    fun openEdit(p: Property) {
        if (!downloads.isOnline()) {
            online.value = false
            flags.update { it.copy(editing = p, editError = null) }
            return
        }
        viewModelScope.launch {
            val fresh = try {
                useCases.get(p.id)
            } catch (_: OnlineException) {
                flags.update { it.copy(notice = UiText.Res(R.string.property_details_failed)) }
                p
            }
            flags.update { it.copy(editing = fresh, editError = null) }
        }
    }

    fun closeEdit() {
        clearSuggestions()
        flags.update { it.copy(editing = null, editError = null) }
    }

    /**
     * Save: checks the form as Add does, then saves it on the server. Once saved, the form closes and the list shows
     * the change (as the web refreshes); "Unable to update property" says when it wasn't.
     */
    fun saveEdit(draft: Property): UiText? {
        useCases.validate(draft)?.let { return it.toUiText() }
        if (adding) return null
        if (!downloads.isOnline()) {
            online.value = false
            return UiText.Res(OnlineOnly.EDIT_PROPERTY.message)
        }
        adding = true
        flags.update { it.copy(editError = null) }
        viewModelScope.launch {
            try {
                val problem = useCases.update(draft)
                if (problem != null) flags.update { it.copy(editError = problem.toUiText()) }
                else {
                    clearSuggestions()
                    flags.update { it.copy(editing = null, editError = null) }
                }
            } catch (_: OnlineException) {
                flags.update { it.copy(editError = UiText.Res(R.string.property_update_failed)) }
            }
            adding = false
        }
        return null
    }

    /** Delete: asks first ("Delete Property?"). */
    fun askDelete() {
        if (blockedOffline()) return
        flags.update { it.copy(confirmDelete = it.editing) }
    }
    fun cancelDelete() = flags.update { it.copy(confirmDelete = null) }

    /** Deletes the property after the confirmation; the form closes once it is gone, or "Unable to delete" says it isn't. */
    fun deleteProperty() {
        val p = flags.value.confirmDelete ?: return
        if (blockedOffline()) {
            flags.update { it.copy(confirmDelete = null) }
            return
        }
        viewModelScope.launch {
            try {
                useCases.delete(p.id)
                clearSuggestions()
                flags.update { it.copy(confirmDelete = null, editing = null, editError = null) }
            } catch (_: OnlineException) {
                flags.update { it.copy(confirmDelete = null, editError = UiText.Res(R.string.property_delete_failed)) }
            }
        }
    }
}

/** The web's words for each problem. */
internal fun PropertyProblem.toUiText(): UiText = when (this) {
    PropertyProblem.REQUIRED_FIELDS -> UiText.Res(R.string.property_fill_required)
    PropertyProblem.EMAIL_INVALID -> UiText.Res(R.string.property_email_invalid)
    PropertyProblem.PHONE_INVALID -> UiText.Res(R.string.property_phone_invalid)
}

/** The Add form's address fields that offer suggestions. */
enum class AddressField { CITY, STATE, COUNTRY }

/** The suggestions shown under [field]. */
data class AddressSuggestions(val field: AddressField, val items: List<AddressSuggestion>)

/** How long typing must pause before suggestions are asked for. */
private const val SUGGEST_DELAY_MS = 250L

internal fun ImportFileProblem.toUiText(): UiText = when (this) {
    ImportFileProblem.NOT_XLSX -> UiText.Res(R.string.import_error_type)
    ImportFileProblem.TOO_LARGE -> UiText.Res(R.string.import_error_size)
}
