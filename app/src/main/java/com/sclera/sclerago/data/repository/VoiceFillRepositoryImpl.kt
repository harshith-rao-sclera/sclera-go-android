package com.sclera.sclerago.data.repository

import com.sclera.sclerago.data.remote.adc.AdcJobsApi
import com.sclera.sclerago.domain.model.ANSWER_NO
import com.sclera.sclerago.domain.model.ANSWER_YES
import com.sclera.sclerago.domain.model.CHECKED
import com.sclera.sclerago.domain.model.FieldKind
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.UNCHECKED
import com.sclera.sclerago.domain.model.Unheard
import com.sclera.sclerago.domain.model.VoiceField
import com.sclera.sclerago.domain.model.VoiceFillResult
import com.sclera.sclerago.domain.model.serverType
import com.sclera.sclerago.domain.repository.PropertyDownloadRepository
import com.sclera.sclerago.domain.repository.VoiceFillRepository
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.inject.Inject

/**
 * The server's voice fill for the current property. The form goes out with the app's own field keys, which the server
 * only echoes back, and each value in the shape its field stores at the server; the answer comes back as the text the
 * Info form keeps (yes / no answers as "Yes" / "No", checkboxes as "true" / "false", options joined with ",").
 */
class VoiceFillRepositoryImpl @Inject constructor(
    private val api: AdcJobsApi,
    private val downloads: PropertyDownloadRepository,
) : VoiceFillRepository {

    override suspend fun fill(
        transcript: String,
        fields: List<VoiceField>,
        language: String,
        timeZone: String,
        spokenAtMillis: Long,
    ): VoiceFillResult {
        val property = downloads.downloads.value.currentId ?: throw OnlineException(OnlineError.NotFound)
        val zone = runCatching { ZoneId.of(timeZone) }.getOrDefault(ZoneId.systemDefault())
        val body = JsonObject(
            mapOf(
                "transcript" to JsonPrimitive(transcript),
                "form" to JsonArray(fields.map { it.toJson(zone) }),
                "language" to JsonPrimitive(language),
                "timeZone" to JsonPrimitive(timeZone),
                "spokenAt" to JsonPrimitive(OffsetDateTime.ofInstant(Instant.ofEpochMilli(spokenAtMillis), zone).toString()),
            ),
        )
        val answer = api.voiceFill(property, body)
        val byKey = fields.associateBy { it.key }
        val values = answer.values.mapNotNull { (key, v) ->
            byKey[key]?.let { f -> formText(f.kind, v)?.let { text -> stored(f, text, zone, spokenAtMillis)?.let { key to it } } }
        }.toMap()
        return VoiceFillResult(
            values = values,
            confidence = answer.confidence.filterKeys { it in values },
            differs = answer.differs.filter { it in values }.toSet(),
            unmatched = answer.unmatched.mapNotNull { u ->
                u.text.trim().takeIf { it.isNotEmpty() }?.let { Unheard(it, u.reason.trim().takeIf { r -> r.isNotEmpty() }) }
            },
        )
    }

    private fun VoiceField.toJson(zone: ZoneId): JsonObject = JsonObject(
        buildMap {
            put("key", JsonPrimitive(key))
            put("label", JsonPrimitive(label))
            put("type", JsonPrimitive(kind.serverType))
            put("value", serverValue(kind, value, zone))
            if (options.isNotEmpty()) {
                put("options", JsonArray(options.map { JsonObject(mapOf("value" to JsonPrimitive(it), "label" to JsonPrimitive(it))) }))
            }
            // A currency field's currency (the web's voiceForm sends it; the server converts no money).
            if (unit.isNotBlank()) put("unit", JsonPrimitive(unit.take(MAX_UNIT)))
        },
    )

    /** What the form holds, in the shape the field has at the server (null when empty). */
    private fun serverValue(kind: FieldKind, value: String, zone: ZoneId): JsonElement {
        val v = value.trim()
        if (v.isEmpty()) return JsonNull
        return when (kind) {
            // Kept as seconds since 1970; the server reads a day as YYYY-MM-DD and a time as HH:MM.
            FieldKind.DATE -> JsonPrimitive(epoch(v)?.atZone(zone)?.toLocalDate()?.toString() ?: v)
            FieldKind.TIME -> JsonPrimitive(epoch(v)?.atZone(zone)?.toLocalTime()?.let { "%02d:%02d".format(it.hour, it.minute) } ?: v)
            FieldKind.MULTI -> JsonArray(v.split(",").map { it.trim() }.filter { it.isNotEmpty() }.map(::JsonPrimitive))
            FieldKind.CHECKBOX -> JsonPrimitive(v == CHECKED)
            FieldKind.YESNO -> JsonPrimitive(v.equals(ANSWER_YES, ignoreCase = true))
            FieldKind.NUMBER, FieldKind.CURRENCY -> v.toDoubleOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(v)
            else -> JsonPrimitive(v)
        }
    }

    /**
     * [text] for field [f] as the form stores it: a day (YYYY-MM-DD) or time (HH:MM) as seconds since 1970 in [zone],
     * a time on the day the field already has (else the day it was said); null for one that can't be read.
     */
    private fun stored(f: VoiceField, text: String, zone: ZoneId, spokenAtMillis: Long): String? = when (f.kind) {
        FieldKind.DATE -> runCatching { LocalDate.parse(text).atStartOfDay(zone).toEpochSecond().toString() }.getOrNull()
        FieldKind.TIME -> runCatching {
            val day = epoch(f.value)?.atZone(zone)?.toLocalDate() ?: Instant.ofEpochMilli(spokenAtMillis).atZone(zone).toLocalDate()
            day.atTime(LocalTime.parse(text)).atZone(zone).toEpochSecond().toString()
        }.getOrNull()
        else -> text
    }

    /** A stored date or time: seconds since 1970 as text (13 digits are milliseconds); null for anything else. */
    private fun epoch(value: String): Instant? {
        val v = value.trim()
        val n = v.toLongOrNull() ?: return null
        return if (v.length >= 13) Instant.ofEpochMilli(n) else Instant.ofEpochSecond(n)
    }

    /** A value the server sent, as the Info form keeps it; null for an empty or unusable one. */
    private fun formText(kind: FieldKind, v: JsonElement): String? = when (v) {
        is JsonNull -> null
        is JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }
            .joinToString(",").takeIf { it.isNotEmpty() }
        is JsonPrimitive -> {
            val flag = if (v.isString) null else v.booleanOrNull
            when {
                flag != null && kind == FieldKind.CHECKBOX -> if (flag) CHECKED else UNCHECKED
                flag != null && kind == FieldKind.YESNO -> if (flag) ANSWER_YES else ANSWER_NO
                else -> v.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            }
        }
        is JsonObject -> null
    }
}

/** The longest unit the server takes with a form field. */
private const val MAX_UNIT = 50
