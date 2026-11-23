package com.sclera.sclerago.domain.usecase.config

import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.repository.PropertyDownloadRepository
import com.sclera.sclerago.domain.repository.PropertySettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// The property's AI switch (`aiValidation` in its settings; the web's config/aiSwitch.js, the server's services/ai_switch.py).
// Off, the server refuses everything a person starts AI with (403 "AI off. An admin can turn it on.") and background work
// skips AI; the app keeps its AI buttons where they are, disabled, with that sentence as their hint.

/** How long a read of the switch counts as fresh: someone else may flip it (the web's FRESH_MS). */
const val AI_SWITCH_FRESH_MS = 30_000L

/**
 * Whether AI is on in the current property, as it changes. On until the property's settings say otherwise (and when
 * they can't be read: the server still refuses, with the same words), so nothing flashes disabled. Each time it is
 * collected the settings are read again when they weren't read in the last [AI_SWITCH_FRESH_MS]; a change saved in
 * Configuration → Settings shows at once.
 */
class ObserveAiSwitchUseCase @Inject constructor(
    private val settings: PropertySettingsRepository,
    private val downloads: PropertyDownloadRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<Boolean> = downloads.downloads
        .map { it.currentId }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(true) else switchOf(id) }
        .distinctUntilChanged()

    private fun switchOf(propertyId: String): Flow<Boolean> = channelFlow {
        launch {
            try {
                settings.refreshAiSwitch(propertyId, AI_SWITCH_FRESH_MS)
            } catch (_: OnlineException) {
                // Offline or refused: the last known switch (on when none) stays.
            }
        }
        settings.aiSwitches.map { it[propertyId] != false }.collect { send(it) }
    }
}

/**
 * Reads the current property's AI switch again unless it was read in the last [maxAgeMs] (a screen that offers AI does
 * this when it opens, as the web asks on each mount); [ObserveAiSwitchUseCase] then shows the answer. Nothing happens
 * offline or when the settings can't be read: the last known switch stays.
 */
class RefreshAiSwitchUseCase @Inject constructor(
    private val settings: PropertySettingsRepository,
    private val downloads: PropertyDownloadRepository,
) {
    suspend operator fun invoke(maxAgeMs: Long = AI_SWITCH_FRESH_MS) {
        val propertyId = downloads.downloads.value.currentId ?: return
        try {
            settings.refreshAiSwitch(propertyId, maxAgeMs)
        } catch (_: OnlineException) {
            // The last known switch (on when none) stays.
        }
    }
}

/**
 * Whether [error], the answer to something that asks the AI, is the property's AI switch being off: a 403 after which the
 * property's settings, read again, say AI is off. Reading them again also turns the AI buttons off at once. Any other
 * error, or settings that can't be read, is not.
 */
class IsAiOffRefusalUseCase @Inject constructor(
    private val settings: PropertySettingsRepository,
    private val downloads: PropertyDownloadRepository,
) {
    suspend operator fun invoke(error: OnlineError): Boolean {
        if (error != OnlineError.Forbidden) return false
        val propertyId = downloads.downloads.value.currentId ?: return false
        return try {
            settings.refreshAiSwitch(propertyId, maxAgeMs = 0)
            settings.aiSwitches.value[propertyId] == false
        } catch (_: OnlineException) {
            false
        }
    }
}

/** The AI switch use cases, for injecting as one. */
class AiSwitchUseCases @Inject constructor(
    val observe: ObserveAiSwitchUseCase,
    val refresh: RefreshAiSwitchUseCase,
    val isAiOffRefusal: IsAiOffRefusalUseCase,
)
