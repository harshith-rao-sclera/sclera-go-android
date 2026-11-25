package com.sclera.sclerago.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.sclera.sclerago.R
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * A validation problem's message as the web's issueMessage shows it. The server leaves the date out of a date field's
 * problems ("In the future.", "In the past.", "Too early.") because it doesn't know the reader's time zone, so the field's
 * [value] is added here as a day in local time: "In the future: 3 Dec 2026.". Any other message is shown as it is.
 */
@Composable
fun issueMessage(message: String, fieldType: String?, value: String?): String {
    if (fieldType != "date" || message !in DATE_PROBLEMS) return message
    val at = value?.let(::dateMillis) ?: return message
    val day = formatDate(at, LocalConfiguration.current.locales[0])
    return stringResource(R.string.val_issue_date_message, message.removeSuffix("."), day)
}

/**
 * A value an issue shows (what it is now, a suggested fix, what it was set to): a date field's stored value as its day in
 * local time ("31 Dec 2026"), not the number it is kept as; any other value, or one that isn't a date, as it is.
 */
@Composable
fun issueValueText(value: String, fieldType: String?): String {
    if (fieldType != "date") return value
    val at = dateMillis(value) ?: return value
    return formatDate(at, LocalConfiguration.current.locales[0])
}

/** The date problems the server words without the date. */
private val DATE_PROBLEMS = setOf("In the future.", "In the past.", "Too early.")

/**
 * A date value as epoch millis, as the web's dateText reads it: a number is seconds since 1970 (how dates are stored),
 * or milliseconds when it has 13 digits or more (as the asset form reads them; some apps store those); otherwise an ISO
 * date or date-time. Null when it is neither.
 */
private fun dateMillis(value: String): Long? {
    val text = value.trim().ifEmpty { return null }
    text.toDoubleOrNull()?.let { n ->
        val digits = text.substringBefore('.').trimStart('-').length
        return if (digits >= 13) n.toLong() else (n * 1000).toLong()
    }
    return runCatching { Instant.parse(text).toEpochMilli() }
        .recoverCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }
        .recoverCatching { LocalDate.parse(text).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
        .getOrNull()
}
