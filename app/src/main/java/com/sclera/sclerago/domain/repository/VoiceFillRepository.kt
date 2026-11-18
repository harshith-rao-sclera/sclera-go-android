package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.VoiceField
import com.sclera.sclerago.domain.model.VoiceFillResult

/**
 * The server's voice fill (`POST /properties/{id}/voice-fill`): what was said, matched by its AI to the fields of the
 * Info form, whether or not their names were said. Online only; nothing is saved. Throws
 * [com.sclera.sclerago.domain.model.OnlineException] (Invalid when nothing said matched a field).
 */
interface VoiceFillRepository {
    suspend fun fill(transcript: String, fields: List<VoiceField>, language: String, timeZone: String, spokenAtMillis: Long): VoiceFillResult
}
