package com.sclera.sclerago.presentation.assets

import com.sclera.sclerago.presentation.components.pressClickable
import com.sclera.sclerago.presentation.components.PRESSED_CARD
import com.sclera.sclerago.presentation.common.is24Hour
import com.sclera.sclerago.presentation.common.timeFormatter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.presentation.components.LocalReadOnly
import com.sclera.sclerago.presentation.components.bottomBorder
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * A stored date or time as an instant. The web stores them as seconds since 1970 (as text); 13 digits are milliseconds.
 * Anything else (text from an import) is not a time the form can show as one.
 */
internal fun storedInstant(value: String): Instant? {
    val n = value.trim().toLongOrNull() ?: return null
    return if (value.trim().length >= 13) Instant.ofEpochMilli(n) else Instant.ofEpochSecond(n)
}

/** Whether a date or time row shows a day or a time of day. */
enum class WhenKind { DATE, TIME }

/**
 * A date or time field's row: its value in the phone's language ("15 Nov 2026", "10:42"), and a picker on tap that
 * stores the choice as the web does (seconds since 1970). A value the form can't read as a time is shown as it is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhenRow(key: String, label: String, value: String, kind: WhenKind, edited: Boolean, onChange: (String) -> Unit, rowBg: Color) {
    val readOnly = LocalReadOnly.current
    var open by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val locale = LocalConfiguration.current.locales[0]
    val clock24 = is24Hour()
    val instant = storedInstant(value)
    val shown = when {
        value.isBlank() -> null
        instant == null -> value
        kind == WhenKind.DATE -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(instant.atZone(zone))
        else -> timeFormatter(locale, clock24).format(instant.atZone(zone))
    }
    Row(
        Modifier.pressClickable(pressed = PRESSED_CARD, enabled = !readOnly) { open = true }.fillMaxWidth().testTag(DetailTags.field(key)).heightIn(min = 48.dp).topBorder(C.Border).background(rowBg)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FieldLabel(label, Modifier.width(92.dp))
        Txt(
            shown ?: stringResource(R.string.detail_value_none), 14, 600, if (shown != null) C.Text else C.Muted,
            Modifier.weight(1f).bottomBorder(if (edited) C.Yellow else Color.Transparent, 1.5.dp).padding(vertical = 6.dp),
        )
        if (!readOnly) LineIcon(if (kind == WhenKind.DATE) Icons.Calendar else Icons.Clock, 15.dp, C.Muted, 2.2f)
    }
    if (!open) return
    val ok = stringResource(R.string.action_ok)
    val cancel = stringResource(R.string.action_cancel)
    if (kind == WhenKind.DATE) {
        // The picker works in UTC days; the chosen day is kept as its start in the phone's time zone, as the web does.
        val initial = instant?.atZone(zone)?.toLocalDate()?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val picker = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton({
                    picker.selectedDateMillis?.let { ms ->
                        val day = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        onChange(day.atStartOfDay(zone).toEpochSecond().toString())
                    }
                    open = false
                }) { Txt(ok, 14, 700, C.Yellow) }
            },
            dismissButton = { TextButton({ open = false }) { Txt(cancel, 14, 600) } },
        ) { DatePicker(picker) }
    } else {
        val start = instant?.atZone(zone)?.toLocalTime() ?: LocalTime.now(zone)
        val picker = rememberTimePickerState(start.hour, start.minute)
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton({
                    val day = instant?.atZone(zone)?.toLocalDate() ?: LocalDate.now(zone)
                    onChange(day.atTime(picker.hour, picker.minute).atZone(zone).toEpochSecond().toString())
                    open = false
                }) { Txt(ok, 14, 700, C.Yellow) }
            },
            dismissButton = { TextButton({ open = false }) { Txt(cancel, 14, 600) } },
            text = { TimePicker(picker) },
            containerColor = C.Surface,
        )
    }
}

/**
 * A multiple-choice field's row, as the web's: a box per option, ticked for each chosen one. The answer is stored as
 * the web stores it, the chosen options joined with commas.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MultiCheckRow(key: String, label: String, value: String, options: List<String>, edited: Boolean, onChange: (String) -> Unit, rowBg: Color) {
    val readOnly = LocalReadOnly.current
    val chosen = value.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    Column(
        Modifier.fillMaxWidth().testTag(DetailTags.field(key)).topBorder(C.Border).background(rowBg).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FieldLabel(label, color = if (edited) C.Yellow else C.Muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { option ->
                val on = option in chosen
                Row(
                    Modifier.heightIn(min = 40.dp).pressClickable(shape = r(8), enabled = !readOnly) {
                        val next = if (on) chosen - option else chosen + option
                        onChange(options.filter { it in next }.joinToString(","))
                    }.padding(end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier.size(18.dp).background(if (on) C.Yellow else Color.Transparent, r(4)).border(1.5.dp, if (on) C.Yellow else C.Border2, r(4)),
                        contentAlignment = Alignment.Center,
                    ) { if (on) LineIcon(Icons.Check, 11.dp, C.OnYellow, 3.2f) }
                    Txt(option, 13, 500, maxLines = Int.MAX_VALUE)
                }
            }
        }
    }
}
