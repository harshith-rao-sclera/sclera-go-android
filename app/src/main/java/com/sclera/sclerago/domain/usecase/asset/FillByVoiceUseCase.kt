package com.sclera.sclerago.domain.usecase.asset

import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FieldSection
import com.sclera.sclerago.domain.model.NO_INPUT_KINDS
import com.sclera.sclerago.domain.model.OPTION_KINDS
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.VoiceField
import com.sclera.sclerago.domain.model.VoiceFillResult
import com.sclera.sclerago.domain.model.currency
import com.sclera.sclerago.domain.repository.VoiceFillRepository
import com.sclera.sclerago.domain.system.TimeProvider
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

/** What was read from speech, and whether the server's AI read it ([byAi]) or the phone did (offline). */
data class VoiceReading(val result: VoiceFillResult, val byAi: Boolean)

/**
 * Fills the Info form from free speech: the person just talks about the asset ("it's a Daikin, serial 24A-118903,
 * 250 tons, installed March 2019") and the server's AI decides which fields the words are for (`/voice-fill`), several
 * at once and without the fields' names. The fields sent are the form's own (visible, editable, not the classification
 * picked from the type catalogue), with what they hold now. Where the AI can't be reached (offline, AI down) the phone
 * reads it itself, which needs each field's name said before its value ([ReadSpokenFieldsUseCase]).
 */
class FillByVoiceUseCase @Inject constructor(
    private val repo: VoiceFillRepository,
    private val guard: PermissionGuard,
    private val onPhone: ReadSpokenFieldsUseCase,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(said: String, fields: List<FieldCfg>, valueOf: (String) -> String): VoiceReading {
        val text = said.trim().take(MAX_TRANSCRIPT)
        val form = fields
            .filter { it.visible && !it.locked && it.section != FieldSection.CLASS && it.kind !in NO_INPUT_KINDS }
            .filter { it.kind !in OPTION_KINDS || it.options.isNotEmpty() }
            .take(MAX_FIELDS)
            .map { VoiceField(it.key, it.label.removeSuffix(" *").trim(), it.kind, valueOf(it.key), it.options, it.currency.orEmpty()) }
        if (text.isEmpty() || form.isEmpty()) return VoiceReading(VoiceFillResult(emptyMap()), byAi = false)
        guard.requireOnline(Permission.COLLECT)
        return try {
            VoiceReading(repo.fill(text, form, Locale.getDefault().toLanguageTag(), TimeZone.getDefault().id, time.nowMillis()), byAi = true)
        } catch (e: OnlineException) {
            when (e.error) {
                OnlineError.SessionExpired, OnlineError.Forbidden -> throw e
                // Offline, AI not set up, too slow or failing, nothing it could use, or a request it refused (both are
                // Invalid here): the phone reads it ("<field name> <value>").
                else -> VoiceReading(VoiceFillResult(onPhone(text, fields)), byAi = false)
            }
        }
    }

    private companion object {
        /** The server's limits: 4000 characters said, 200 fields. */
        const val MAX_TRANSCRIPT = 4000
        const val MAX_FIELDS = 200
    }
}
