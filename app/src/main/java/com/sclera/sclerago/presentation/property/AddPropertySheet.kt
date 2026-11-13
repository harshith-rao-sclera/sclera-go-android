package com.sclera.sclerago.presentation.property

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Address
import com.sclera.sclerago.domain.model.Availability
import com.sclera.sclerago.domain.model.Contact
import com.sclera.sclerago.domain.model.PROPERTY_COUNTRIES
import com.sclera.sclerago.domain.model.PROPERTY_REGIONS
import com.sclera.sclerago.domain.model.PROPERTY_TYPES
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.domain.usecase.property.propertyProblem
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.components.ChoiceSheet
import com.sclera.sclerago.presentation.components.FormGroup
import com.sclera.sclerago.presentation.components.FormSheet
import com.sclera.sclerago.presentation.components.InlineField
import com.sclera.sclerago.presentation.components.InlineInput
import com.sclera.sclerago.presentation.components.InlineSelect
import com.sclera.sclerago.presentation.components.PlainField
import com.sclera.sclerago.presentation.components.SecureWindow
import com.sclera.sclerago.presentation.components.TintChip
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.theme.C

/** Test tags of the Add property sheet. */
object AddPropertyTags {
    const val NAME = "add_property_name"
}

/** Which picker sheet is open. */
private enum class Picker { TYPE, COUNTRY, STATE, REGION }

/** Add New Property: grouped form; Add stays disabled until name and type are given. [onAdd] returns why it failed, or null. */
@Composable
internal fun AddPropertySheet(existing: List<Property>, onClose: () -> Unit, onAdd: (Property) -> UiText?) {
    val context = LocalContext.current
    SecureWindow() // the access code is typed here
    var name by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("") }
    var line1 by rememberSaveable { mutableStateOf("") }
    var line2 by rememberSaveable { mutableStateOf("") }
    var country by rememberSaveable { mutableStateOf("") }
    var state by rememberSaveable { mutableStateOf("") }
    var region by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    var contactName by rememberSaveable { mutableStateOf("") }
    var contactEmail by rememberSaveable { mutableStateOf("") }
    var contactPhone by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var start by rememberSaveable { mutableStateOf("00:01") }
    var end by rememberSaveable { mutableStateOf("23:59") }
    var accessCode by rememberSaveable { mutableStateOf("") }
    var picker by remember { mutableStateOf<Picker?>(null) }
    var error by remember { mutableStateOf<UiText?>(null) }

    val done = (if (name.isNotBlank()) 1 else 0) + (if (type.isNotEmpty()) 1 else 0)
    val valid = propertyProblem(name, type, existing) == null
    val states = PROPERTY_COUNTRIES[country].orEmpty()
    val stateHint = stringResource(
        when {
            country.isEmpty() -> R.string.property_choose_country_first
            states.isEmpty() -> R.string.property_no_states
            else -> R.string.property_select_state
        },
    )

    fun pickTime(current: String, set: (String) -> Unit) {
        val (h, m) = current.split(':').map { it.toIntOrNull() ?: 0 }
        TimePickerDialog(context, { _, hh, mm -> set("%02d:%02d".format(hh, mm)) }, h, m, true).show()
    }

    Box(Modifier.fillMaxSize()) {
        FormSheet(
            stringResource(R.string.property_add_title), stringResource(R.string.property_add_sub), stringResource(R.string.property_add_primary),
            valid, error?.asString(), onClose,
            onPrimary = {
                error = onAdd(
                    Property(
                        "", name, gps = false, type = type,
                        address = Address(line1.trim(), line2.trim(), country, state, region, city.trim()),
                        contact = Contact(contactName.trim(), contactEmail.trim(), contactPhone.trim()),
                        notes = notes.trim(),
                        availability = Availability(start, end, accessCode.trim()),
                    ),
                )
            },
            leadingIcon = Icons.Building, progress = done / 2f,
            bodyBackground = C.Bg, bodyPadding = PaddingValues(vertical = 12.dp), bodySpacing = 12.dp,
        ) {
            FormGroup(stringResource(R.string.property_group_details), Icons.Building, collapsible = true, trailing = {
                val ok = done == 2
                TintChip(
                    if (ok) stringResource(R.string.property_ready) else stringResource(R.string.property_required_count, 2 - done),
                    bg = (if (ok) C.Green else C.Yellow).copy(alpha = 0.15f), fg = if (ok) C.Green else C.Yellow,
                )
            }) {
                InlineField(stringResource(R.string.field_name), required = true) {
                    InlineInput(name, { name = it; error = null }, stringResource(R.string.property_name_hint), Modifier.testTag(AddPropertyTags.NAME))
                }
                InlineField(stringResource(R.string.field_type), required = true) {
                    InlineSelect(type, stringResource(R.string.property_select_type)) { picker = Picker.TYPE }
                }
            }
            FormGroup(stringResource(R.string.property_group_address), Icons.Pin, collapsible = true) {
                InlineField(stringResource(R.string.field_address_line1)) { InlineInput(line1, { line1 = it }, stringResource(R.string.field_address_line1_hint)) }
                InlineField(stringResource(R.string.field_address_line2)) { InlineInput(line2, { line2 = it }, stringResource(R.string.field_address_line2_hint)) }
                InlineField(stringResource(R.string.field_country)) { InlineSelect(country, stringResource(R.string.property_select_country)) { picker = Picker.COUNTRY } }
                InlineField(stringResource(R.string.field_state), dim = states.isEmpty()) {
                    InlineSelect(state, stateHint, enabled = states.isNotEmpty()) { picker = Picker.STATE }
                }
                InlineField(stringResource(R.string.field_region)) { InlineSelect(region, stringResource(R.string.property_select_region)) { picker = Picker.REGION } }
                InlineField(stringResource(R.string.field_city), dim = country.isEmpty()) {
                    InlineInput(
                        city, { city = it },
                        stringResource(if (country.isEmpty()) R.string.property_choose_country_first else R.string.property_enter_city),
                        enabled = country.isNotEmpty(),
                    )
                }
            }
            FormGroup(stringResource(R.string.property_group_contact), Icons.User, collapsible = true) {
                InlineField(stringResource(R.string.field_name)) { InlineInput(contactName, { contactName = it }, stringResource(R.string.field_contact_name_hint)) }
                InlineField(stringResource(R.string.field_email)) {
                    InlineInput(contactEmail, { contactEmail = it }, stringResource(R.string.field_email_hint), keyboard = KeyboardType.Email)
                }
                InlineField(stringResource(R.string.field_phone)) {
                    InlineInput(contactPhone, { contactPhone = it }, stringResource(R.string.field_phone_hint), keyboard = KeyboardType.Phone)
                }
            }
            FormGroup(stringResource(R.string.property_group_notes), Icons.FileDoc, collapsible = true, trailing = {
                TintChip(stringResource(if (notes.isBlank()) R.string.property_notes_none else R.string.property_notes_added), bg = C.Raised, fg = C.Muted)
            }) {
                Box(Modifier.fillMaxWidth().topBorder(C.Border).padding(horizontal = 12.dp)) {
                    PlainField(
                        notes, { notes = it }, stringResource(R.string.property_notes_hint),
                        Modifier.fillMaxWidth().heightIn(min = 84.dp).padding(vertical = 12.dp), size = 15, singleLine = false, minLines = 3,
                    )
                }
            }
            FormGroup(stringResource(R.string.property_group_availability), Icons.Clock, collapsible = true) {
                InlineField(stringResource(R.string.field_start_time)) { InlineSelect(start, "", chevron = false) { pickTime(start) { start = it } } }
                InlineField(stringResource(R.string.field_end_time)) { InlineSelect(end, "", chevron = false) { pickTime(end) { end = it } } }
                InlineField(stringResource(R.string.field_access_code)) { InlineInput(accessCode, { accessCode = it }, stringResource(R.string.field_access_code_hint)) }
            }
        }
        when (picker) {
            Picker.TYPE -> ChoiceSheet(
                stringResource(R.string.field_type), PROPERTY_TYPES.map { it to null }, type,
                { type = it; picker = null; error = null }, { picker = null },
            )
            Picker.COUNTRY -> ChoiceSheet(
                stringResource(R.string.field_country), PROPERTY_COUNTRIES.keys.map { it to null }, country,
                {
                    if (it != country) state = ""
                    country = it
                    picker = null
                },
                { picker = null },
            )
            Picker.STATE -> ChoiceSheet(stringResource(R.string.field_state), states.map { it to null }, state, { state = it; picker = null }, { picker = null })
            Picker.REGION -> ChoiceSheet(stringResource(R.string.field_region), PROPERTY_REGIONS.map { it to null }, region, { region = it; picker = null }, { picker = null })
            null -> Unit
        }
    }
}
