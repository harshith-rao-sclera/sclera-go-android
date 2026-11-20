package com.sclera.sclerago.presentation.config

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.DEFAULT_PHOTO_RULES
import com.sclera.sclerago.domain.model.Fence
import com.sclera.sclerago.domain.model.Geofence
import com.sclera.sclerago.domain.model.MAX_PHOTOS_PER_ASSET
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.PhotoKind
import com.sclera.sclerago.domain.model.PhotoRule
import com.sclera.sclerago.domain.usecase.config.FenceProblem
import com.sclera.sclerago.domain.usecase.config.PhotoBound
import com.sclera.sclerago.domain.usecase.config.PhotoStepBlock
import com.sclera.sclerago.domain.usecase.config.PropertyConfigUseCases
import com.sclera.sclerago.domain.usecase.config.SaveGeofenceUseCase
import com.sclera.sclerago.domain.usecase.config.photoStepBlock
import com.sclera.sclerago.domain.usecase.config.stepPhotoRule
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.toUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Whether something read from the server is there yet. */
enum class LoadState { LOADING, READY, FAILED }

/** Asset fields → Photos: the property's photo rules as shown (changes show at once, before the server confirms them). */
data class PhotoRulesUi(val load: LoadState = LoadState.LOADING, val rules: List<PhotoRule> = DEFAULT_PHOTO_RULES) {
    /** Every kind's maximum together: the most photos an asset may have. */
    val totalMax: Int get() = rules.sumOf { it.max }
}

/**
 * Geofencing: the [saved] geofence and what is going on. [revision] changes whenever [saved] is (re)loaded or saved, so
 * the map shows it again. [confirmEnforce] is the enforce value waiting for the user to confirm, null when none is.
 */
data class GeofenceUi(
    val load: LoadState = LoadState.LOADING,
    val saved: Geofence = Geofence(enforced = false, fences = emptyList()),
    val saving: Boolean = false,
    val confirmEnforce: Boolean? = null,
    val revision: Int = 0,
)

/**
 * The Configuration settings the server keeps for the property: the photo rules (Asset fields), AI in validation
 * (Settings) and the geofence (Geofencing). Changes are shown at once and saved; when a save fails the last saved
 * values come back and the user is told. One-off messages come through [messages].
 */
@HiltViewModel
class ConfigViewModel @Inject constructor(private val useCases: PropertyConfigUseCases) : ViewModel() {
    private var propertyId = ""
    private var loadJob: Job? = null

    var photos by mutableStateOf(PhotoRulesUi())
        private set

    /** AI in validation; null until the settings are loaded (or when they could not be). */
    var aiValidation by mutableStateOf<Boolean?>(null)
        private set

    /** The property's type fields are in metric units; null until the settings are loaded (or when they could not be). */
    var metricUnits by mutableStateOf<Boolean?>(null)
        private set

    var geofence by mutableStateOf(GeofenceUi())
        private set

    private val _messages = Channel<UiText>(Channel.BUFFERED)

    /** Toasts for the screen to show once each. */
    val messages: Flow<UiText> = _messages.receiveAsFlow()

    private fun say(text: UiText) {
        _messages.trySend(text)
    }

    /** Loads the settings and geofence of [property] (again each time Configuration opens, or the property changes). */
    /** The map has fences drawn or changed that aren't saved yet: leaving Configuration asks first, as on the web. */
    var geoUnsaved by mutableStateOf(false)
        private set

    fun markGeoUnsaved(unsaved: Boolean) {
        geoUnsaved = unsaved
    }

    fun open(property: String) {
        propertyId = property
        geoUnsaved = false
        loadJob?.cancel()
        latestPhotoSave++
        photos = PhotoRulesUi()
        aiValidation = null
        metricUnits = null
        geofence = GeofenceUi(revision = geofence.revision + 1)
        if (property.isEmpty()) {
            photos = PhotoRulesUi(LoadState.FAILED)
            geofence = geofence.copy(load = LoadState.FAILED)
            return
        }
        loadJob = viewModelScope.launch {
            launch {
                try {
                    val s = useCases.loadSettings(property)
                    savedRules = s.photoRules
                    photos = PhotoRulesUi(LoadState.READY, s.photoRules)
                    aiValidation = s.aiValidation
                    metricUnits = s.metricUnits
                } catch (_: OnlineException) {
                    photos = PhotoRulesUi(LoadState.FAILED)
                }
            }
            launch {
                geofence = try {
                    GeofenceUi(LoadState.READY, useCases.loadGeofence(property), revision = geofence.revision + 1)
                } catch (_: OnlineException) {
                    geofence.copy(load = LoadState.FAILED)
                }
            }
        }
    }

    // ---------- Photo rules

    /** What the server last confirmed, to go back to when a save fails. */
    private var savedRules: List<PhotoRule> = DEFAULT_PHOTO_RULES

    /** Only the newest save's answer is shown. */
    private var latestPhotoSave = 0

    /** Why [kind]'s [bound] can't go [up] (or down), or null. */
    fun photoBlock(kind: PhotoKind, bound: PhotoBound, up: Boolean): PhotoStepBlock? =
        photoStepBlock(photos.rules, kind, bound, up)

    /** A stepper tap: shows the new number at once and saves every rule; a blocked tap says why. */
    fun stepPhoto(kind: PhotoKind, bound: PhotoBound, up: Boolean) {
        if (photos.load != LoadState.READY) return
        val next = stepPhotoRule(photos.rules, kind, bound, up)
        if (next == null) {
            photoBlock(kind, bound, up)?.let { say(it.message()) }
            return
        }
        photos = photos.copy(rules = next)
        val request = ++latestPhotoSave
        val property = propertyId
        viewModelScope.launch {
            try {
                val kept = useCases.savePhotoRules(property, next)
                savedRules = kept.photoRules
                if (request == latestPhotoSave) photos = photos.copy(rules = kept.photoRules)
            } catch (e: OnlineException) {
                if (request == latestPhotoSave) photos = photos.copy(rules = savedRules)
                say(e.error.toUiText())
            }
        }
    }

    // ---------- AI in validation

    /** The AI in validation switch is being saved: another tap waits for the server's answer. */
    var aiSaving by mutableStateOf(false)
        private set

    fun setAiValidation(on: Boolean) {
        val before = aiValidation ?: return
        if (aiSaving) return
        aiSaving = true
        aiValidation = on
        val property = propertyId
        viewModelScope.launch {
            try {
                aiValidation = useCases.setAiValidation(property, on).aiValidation
                say(UiText.Res(if (on) R.string.cfg_setting_on else R.string.cfg_setting_off, listOf(UiText.Res(R.string.cfg_ai_validation))))
            } catch (e: OnlineException) {
                aiValidation = before
                say(e.error.toUiText())
            } finally {
                aiSaving = false
            }
        }
    }

    // ---------- Units

    /** The Metric units switch is being saved: another tap waits for the server's answer. */
    var unitsSaving by mutableStateOf(false)
        private set

    /** The web's Units switch: on puts the property's type fields in metric units, off in US units. */
    fun setMetricUnits(metric: Boolean) {
        val before = metricUnits ?: return
        if (unitsSaving) return
        unitsSaving = true
        metricUnits = metric
        val property = propertyId
        viewModelScope.launch {
            try {
                metricUnits = useCases.setMetricUnits(property, metric).metricUnits
                say(UiText.Res(if (metric) R.string.cfg_units_metric_saved else R.string.cfg_units_us_saved))
            } catch (e: OnlineException) {
                metricUnits = before
                say(e.error.toUiText())
            } finally {
                unitsSaving = false
            }
        }
    }

    // ---------- Geofence

    /** The enforce checkbox: asks to confirm turning enforcement to [on] (only once a fence is saved). */
    fun askEnforce(on: Boolean) {
        val g = geofence
        if (g.load != LoadState.READY || g.saving || g.saved.fences.isEmpty()) return
        geofence = g.copy(confirmEnforce = on)
    }

    /** The enforce checkbox tapped before any fence is saved: says what to do first. */
    fun explainEnforce() = say(UiText.Res(R.string.cfg_geo_enforce_needs_fence))

    fun cancelEnforce() {
        geofence = geofence.copy(confirmEnforce = null)
    }

    /** Proceed in the enforce dialog: saves the switch with the saved fences. */
    fun confirmEnforce() {
        val on = geofence.confirmEnforce ?: return
        val fences = geofence.saved.fences
        geofence = geofence.copy(confirmEnforce = null, saving = true)
        val property = propertyId
        viewModelScope.launch {
            try {
                val kept = useCases.setGeofenceEnforced(property, fences, on)
                geofence = if (kept == null) {
                    geofence.copy(saving = false)
                } else {
                    say(UiText.Res(R.string.cfg_geo_enforce_done))
                    geofence.copy(saved = kept, saving = false)
                }
            } catch (_: OnlineException) {
                geofence = geofence.copy(saving = false)
                say(UiText.Res(R.string.cfg_geo_enforce_failed))
            }
        }
    }

    /** Save on the map: [fences] as drawn (null when the map's answer couldn't be read). */
    fun saveFences(fences: List<Fence>?) {
        val g = geofence
        if (g.load != LoadState.READY || g.saving) return
        if (fences == null) {
            say(UiText.Res(R.string.cfg_geo_save_failed))
            return
        }
        geofence = g.copy(saving = true)
        val property = propertyId
        viewModelScope.launch {
            try {
                when (val r = useCases.saveGeofence(property, fences, g.saved.enforced)) {
                    is SaveGeofenceUseCase.Result.Refused -> {
                        geofence = geofence.copy(saving = false)
                        say(UiText.Res(r.problem.messageRes()))
                    }
                    is SaveGeofenceUseCase.Result.Saved -> {
                        geofence = geofence.copy(saved = r.geofence, saving = false, revision = geofence.revision + 1)
                        say(UiText.Res(R.string.cfg_geo_saved))
                    }
                }
            } catch (_: OnlineException) {
                geofence = geofence.copy(saving = false)
                say(UiText.Res(R.string.cfg_geo_save_failed))
            }
        }
    }
}

/** Why a photo stepper is blocked, as shown when it is tapped. */
internal fun PhotoStepBlock.message(): UiText =
    if (this == PhotoStepBlock.TOTAL_LIMIT) UiText.Res(R.string.cfg_photo_total_limit, listOf(MAX_PHOTOS_PER_ASSET)) else UiText.Res(messageRes())

private fun PhotoStepBlock.messageRes(): Int = when (this) {
    PhotoStepBlock.NONE_REQUIRED -> R.string.cfg_photo_none_required
    PhotoStepBlock.RAISE_MAX_FIRST -> R.string.cfg_photo_raise_max
    PhotoStepBlock.NONE_ALLOWED -> R.string.cfg_photo_none_allowed
    PhotoStepBlock.BELOW_MIN -> R.string.cfg_photo_below_min
    PhotoStepBlock.TOTAL_LIMIT -> R.string.cfg_photo_total_limit
}

private fun FenceProblem.messageRes(): Int = when (this) {
    FenceProblem.NO_FENCES -> R.string.cfg_geo_need_fence
    FenceProblem.TOO_MANY_FENCES -> R.string.cfg_geo_too_many_fences
    FenceProblem.TOO_FEW_POINTS -> R.string.cfg_geo_too_few_points
    FenceProblem.TOO_MANY_POINTS -> R.string.cfg_geo_too_many_points
    FenceProblem.BAD_POINT -> R.string.cfg_geo_bad_point
}
