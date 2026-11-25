package com.sclera.sclerago.presentation.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.DataException
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.LocValue
import com.sclera.sclerago.domain.model.NO_INPUT_KINDS
import com.sclera.sclerago.domain.usecase.asset.BulkUpdateAssetsUseCase
import com.sclera.sclerago.domain.usecase.config.ObserveAssetConfigUseCase
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.toUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The bulk Edit sheet: the assets it edits, the values typed (by field key) and the place chosen (with its label). */
data class BulkEditState(
    val ids: Set<String>,
    val values: Map<String, String> = emptyMap(),
    val place: LocValue? = null,
    val placeLabel: String? = null,
    val saving: Boolean = false,
) {
    /** Something to change: a field given a value, or a place. */
    val hasChanges: Boolean get() = place != null || values.values.any { it.isNotBlank() }
}

/** Editing the ticked assets at once (the web's bulk Edit): the place and any field values, written to the phone's data. */
@HiltViewModel
class BulkEditViewModel @Inject constructor(
    private val bulkUpdate: BulkUpdateAssetsUseCase,
    observeConfig: ObserveAssetConfigUseCase,
) : ViewModel() {
    var sheet by mutableStateOf<BulkEditState?>(null)
        private set

    /**
     * The fields the sheet offers: the property's visible fields with an input, but not the type (as the web). Null
     * until the property's fields have been read (the sheet shows placeholder rows meanwhile).
     */
    val fields: StateFlow<List<FieldCfg>?> = observeConfig()
        .map<_, List<FieldCfg>?> { c -> c.fields.filter { it.visible && it.kind !in NO_INPUT_KINDS && it.key !in NOT_BULK } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val message = MutableSharedFlow<UiText>(extraBufferCapacity = 8)

    /** What happened, to flash on the dashboard. */
    val notices: SharedFlow<UiText> = message.asSharedFlow()

    fun open(ids: Set<String>) {
        if (ids.isNotEmpty()) sheet = BulkEditState(ids)
    }

    fun close() {
        sheet = null
    }

    fun setValue(key: String, value: String) {
        sheet = sheet?.let { it.copy(values = it.values + (key to value)) }
    }

    /** The place picked in the location sheet: not written until Update. */
    fun setPlace(place: LocValue, label: String) {
        sheet = sheet?.copy(place = place, placeLabel = label)
    }

    /** Back to leaving each asset's place as it is. */
    fun clearPlace() {
        sheet = sheet?.copy(place = null, placeLabel = null)
    }

    /** Update: writes the changes to every ticked asset, closes the sheet ([onDone]) and says how it went. */
    fun update(onDone: () -> Unit) {
        val s = sheet ?: return
        if (s.saving) return
        if (!s.hasChanges) {
            message.tryEmit(UiText.Res(R.string.bulk_nothing))
            return
        }
        sheet = s.copy(saving = true)
        viewModelScope.launch {
            try {
                val r = bulkUpdate(s.ids, s.values, s.place)
                sheet = null
                onDone()
                // One toast for the outcome (a second would replace the first before it could be read).
                val updated = UiText.Plural(R.plurals.bulk_updated, r.updated)
                when {
                    r.skipped == 0 -> if (r.updated > 0) message.emit(updated)
                    r.updated == 0 -> message.emit(UiText.Plural(R.plurals.bulk_skipped, r.skipped))
                    else -> message.emit(UiText.Plural(R.plurals.bulk_updated_and_skipped, r.skipped, listOf(updated, r.skipped)))
                }
            } catch (e: DataException) {
                sheet = sheet?.copy(saving = false)
                message.emit(e.error.toUiText())
            }
        }
    }

    private companion object {
        /** The type (system, asset and sub type) and JACS code aren't bulk-edited (the web leaves them out). */
        val NOT_BULK = setOf("sys", "typ", "sub", "jacs")
    }
}
