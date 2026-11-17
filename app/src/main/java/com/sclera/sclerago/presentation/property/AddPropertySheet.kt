package com.sclera.sclerago.presentation.property

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.AddressSuggestion
import com.sclera.sclerago.domain.model.Availability
import com.sclera.sclerago.domain.model.Contact
import com.sclera.sclerago.domain.model.PROPERTY_REGIONS
import com.sclera.sclerago.domain.model.PROPERTY_TYPES
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.domain.model.labelOf
import com.sclera.sclerago.domain.model.regionOfCountry
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.components.ChoiceSheet
import com.sclera.sclerago.presentation.components.FooterDeleteButton
import com.sclera.sclerago.presentation.components.FormGroup
import com.sclera.sclerago.presentation.components.FormSheet
import com.sclera.sclerago.presentation.components.InlineField
import com.sclera.sclerago.presentation.components.InlineInput
import com.sclera.sclerago.presentation.components.InlineSelect
import com.sclera.sclerago.presentation.components.PlainField
import com.sclera.sclerago.presentation.components.SecureWindow
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Test tags of the Add property sheet. */
object AddPropertyTags {
    const val NAME = "add_property_name"
    const val DELETE = "add_property_delete"
    const val READ_ONLY = "add_property_read_only"
    fun suggestion(name: String) = "add_property_suggestion_$name"
}

/** Which picker sheet is open. */
private enum class Picker { TYPE, REGION }

/** The longest notes the web allows. */
private const val NotesMax = 256

/** How many suggestions show under an address field at once (typing narrows them). */
private const val SuggestionsShown = 8

/**
 * Add New Property, as the web's form: Property details (name and type required; address lines, then City, State and
 * Country with the server's suggestions, and the region, which a picked country fills in), Primary contact (name,
 * email, phone number: digits only), Notes and Availability (start and end time, access code). Add checks the
 * required fields and the email and phone as the web does; [onAdd] returns what stopped it, [serverError] says the
 * server refused. [suggestions] are those for the address field being typed in ([onSuggest] asks for them).
 * With [initial] it edits that property instead (the info button on a property): the form starts from it, is titled
 * "Property Info", saves with Save, and has a round Delete button before Cancel ([onDelete] asks first). While
 * [readOnly] (offline: the copy saved on the device) it only shows the property: nothing can be changed, Save is off,
 * Delete is hidden and a line says why.
 */
@Composable
internal fun AddPropertySheet(
    onClose: () -> Unit,
    onAdd: (Property) -> UiText?,
    serverError: UiText? = null,
    suggestions: AddressSuggestions? = null,
    onSuggest: (field: AddressField, country: String, state: String, text: String) -> Unit = { _, _, _, _ -> },
    onClearSuggestions: () -> Unit = {},
    initial: Property? = null,
    onDelete: (() -> Unit)? = null,
    readOnly: Boolean = false,
) {
    val editable = !readOnly
    val context = LocalContext.current
    SecureWindow() // the access code is typed here
    val from = initial ?: Property("", "", gps = false, type = "")
    var name by rememberSaveable { mutableStateOf(from.name) }
    var type by rememberSaveable { mutableStateOf(from.type) }
    var line1 by rememberSaveable { mutableStateOf(from.address.line1) }
    var line2 by rememberSaveable { mutableStateOf(from.address.line2) }
    var city by rememberSaveable { mutableStateOf(from.address.city) }
    var state by rememberSaveable { mutableStateOf(from.address.state) }
    var country by rememberSaveable { mutableStateOf(from.address.country) }
    var region by rememberSaveable { mutableStateOf(from.address.region) }
    var contactName by rememberSaveable { mutableStateOf(from.contact.name) }
    var contactEmail by rememberSaveable { mutableStateOf(from.contact.email) }
    var contactPhone by rememberSaveable { mutableStateOf(from.contact.phone) }
    var notes by rememberSaveable { mutableStateOf(from.notes) }
    var start by rememberSaveable { mutableStateOf(from.availability.start) }
    var end by rememberSaveable { mutableStateOf(from.availability.end) }
    var accessCode by rememberSaveable { mutableStateOf(from.availability.accessCode) }
    var picker by remember { mutableStateOf<Picker?>(null) }
    var focused by remember { mutableStateOf<AddressField?>(null) }
    var error by remember { mutableStateOf<UiText?>(null) }

    fun pickTime(current: String, set: (String) -> Unit) {
        val (h, m) = current.split(':').map { it.toIntOrNull() ?: 0 }
        TimePickerDialog(context, { _, hh, mm -> set("%02d:%02d".format(hh, mm)) }, h, m, false).show()
    }

    /** What each address field holds, and what picking a suggestion there also changes (as the web's AddressFields). */
    fun valueOf(field: AddressField) = when (field) {
        AddressField.CITY -> city
        AddressField.STATE -> state
        AddressField.COUNTRY -> country
    }

    fun typeInto(field: AddressField, text: String) {
        when (field) {
            AddressField.CITY -> city = text
            AddressField.STATE -> state = text
            AddressField.COUNTRY -> country = text
        }
        onSuggest(field, country, state, text)
    }

    fun pick(field: AddressField, s: AddressSuggestion) {
        when (field) {
            // A different country clears the state and city, which no longer belong to it; its code sets the region.
            AddressField.COUNTRY -> {
                if (!s.name.equals(country.trim(), ignoreCase = true)) {
                    state = ""
                    city = ""
                }
                country = s.name
                regionOfCountry(s.code)?.let { region = it }
            }
            // A different state clears the city.
            AddressField.STATE -> {
                if (!s.name.equals(state.trim(), ignoreCase = true)) city = ""
                state = s.name
            }
            // A town picked before its state fills the state.
            AddressField.CITY -> {
                city = s.name
                s.state?.let { state = it }
            }
        }
        onClearSuggestions()
    }

    @Composable
    fun AddressInput(field: AddressField, label: Int, hint: Int) {
        InlineField(stringResource(label)) {
            InlineInput(
                valueOf(field), { typeInto(field, it) }, stringResource(hint), enabled = editable,
                modifier = Modifier.onFocusChanged {
                    if (it.isFocused && focused != field) {
                        focused = field
                        onSuggest(field, country, state, valueOf(field)) // the lists open as soon as the field is tapped
                    } else if (!it.isFocused && focused == field) {
                        focused = null
                    }
                },
            )
        }
        val shown = suggestions?.takeIf { editable && it.field == field && focused == field }?.items.orEmpty()
        shown.take(SuggestionsShown).forEach { s -> Suggestion(s) { pick(field, s) } }
    }

    val deleteName = from.name
    val deleteButton: (@Composable RowScope.() -> Unit)? = onDelete?.takeIf { editable }?.let { delete ->
        { FooterDeleteButton(stringResource(R.string.property_delete_label, deleteName), Modifier.testTag(AddPropertyTags.DELETE), delete) }
    }
    val typeLabel = if (type.isBlank()) "" else labelOf(type, PROPERTY_TYPES)
    val regionLabel = if (region.isBlank()) "" else labelOf(region, PROPERTY_REGIONS)
    val draft = from.copy(
        name = name, type = type,
        address = from.address.copy(line1 = line1.trim(), line2 = line2.trim(), country = country.trim(), state = state.trim(), region = region, city = city.trim()),
        contact = Contact(contactName.trim(), contactEmail.trim(), contactPhone.trim()),
        notes = notes.trim(),
        availability = Availability(start, end, accessCode.trim()),
    )

    Box(Modifier.fillMaxSize()) {
        FormSheet(
            stringResource(if (initial != null) R.string.property_info_title else R.string.property_add_title), null,
            stringResource(if (initial != null) R.string.action_save else R.string.property_add_primary),
            editable, (error ?: serverError)?.asString(), onClose,
            onPrimary = { if (editable) error = onAdd(draft) },
            leadingIcon = Icons.Building,
            footerLeading = deleteButton,
            bodyBackground = C.Bg, bodyPadding = PaddingValues(vertical = 12.dp), bodySpacing = 12.dp,
            // Something was typed or changed: closing asks before discarding it.
            dirty = listOf(
                name to from.name, type to from.type, line1 to from.address.line1, line2 to from.address.line2,
                city to from.address.city, state to from.address.state, country to from.address.country, region to from.address.region,
                contactName to from.contact.name, contactEmail to from.contact.email, contactPhone to from.contact.phone,
                notes to from.notes, start to from.availability.start, end to from.availability.end,
                accessCode to from.availability.accessCode,
            ).any { (now, was) -> now != was },
        ) {
            if (readOnly) {
                Txt(
                    stringResource(R.string.property_offline_edit), 12, 400, C.Muted,
                    Modifier.padding(horizontal = 16.dp).testTag(AddPropertyTags.READ_ONLY), maxLines = 3,
                )
            }
            FormGroup(stringResource(R.string.property_group_details), Icons.Building) {
                InlineField(stringResource(R.string.property_field_name), required = true) {
                    InlineInput(name, { name = it; error = null }, stringResource(R.string.property_name_hint), Modifier.testTag(AddPropertyTags.NAME), enabled = editable)
                }
                InlineField(stringResource(R.string.property_field_type), required = true) {
                    InlineSelect(typeLabel, stringResource(R.string.property_select_type), enabled = editable) { picker = Picker.TYPE }
                }
                InlineField(stringResource(R.string.field_address_line1)) { InlineInput(line1, { line1 = it }, stringResource(R.string.field_address_line1_hint), enabled = editable) }
                InlineField(stringResource(R.string.field_address_line2)) { InlineInput(line2, { line2 = it }, stringResource(R.string.field_address_line2_hint), enabled = editable) }
                AddressInput(AddressField.CITY, R.string.field_city, R.string.property_enter_city)
                AddressInput(AddressField.STATE, R.string.field_state, R.string.property_enter_state)
                AddressInput(AddressField.COUNTRY, R.string.field_country, R.string.property_enter_country)
                InlineField(stringResource(R.string.field_region)) { InlineSelect(regionLabel, stringResource(R.string.property_select_region), enabled = editable) { picker = Picker.REGION } }
            }
            FormGroup(stringResource(R.string.property_group_contact), Icons.User) {
                InlineField(stringResource(R.string.field_name)) { InlineInput(contactName, { contactName = it }, stringResource(R.string.field_contact_name_hint), enabled = editable) }
                InlineField(stringResource(R.string.field_email)) {
                    InlineInput(contactEmail, { contactEmail = it; error = null }, stringResource(R.string.field_email_hint), enabled = editable, keyboard = KeyboardType.Email)
                }
                InlineField(stringResource(R.string.property_field_phone)) {
                    // Digits only, as the web's field takes them.
                    InlineInput(
                        contactPhone, { contactPhone = it.filter(Char::isDigit); error = null }, stringResource(R.string.field_phone_hint),
                        enabled = editable, keyboard = KeyboardType.Phone,
                    )
                }
            }
            FormGroup(stringResource(R.string.property_group_notes), Icons.FileDoc) {
                Box(Modifier.fillMaxWidth().topBorder(C.Border).padding(horizontal = 12.dp)) {
                    PlainField(
                        notes, { notes = it.take(NotesMax) }, stringResource(R.string.property_notes_hint),
                        Modifier.fillMaxWidth().heightIn(min = 84.dp).padding(vertical = 12.dp), size = 14, singleLine = false, minLines = 3, readOnly = readOnly,
                    )
                }
            }
            FormGroup(stringResource(R.string.property_group_availability), Icons.Clock) {
                InlineField(stringResource(R.string.field_start_time)) { InlineSelect(clockTime(start), "", enabled = editable, chevron = false) { pickTime(start) { start = it } } }
                InlineField(stringResource(R.string.field_end_time)) { InlineSelect(clockTime(end), "", enabled = editable, chevron = false) { pickTime(end) { end = it } } }
                InlineField(stringResource(R.string.field_access_code)) { InlineInput(accessCode, { accessCode = it }, stringResource(R.string.field_access_code_hint), enabled = editable) }
            }
        }
        when (picker) {
            Picker.TYPE -> ChoiceSheet(
                stringResource(R.string.property_field_type), PROPERTY_TYPES.map { it.label to null }, typeLabel.ifEmpty { null },
                { label -> type = PROPERTY_TYPES.first { it.label == label }.value; picker = null; error = null }, { picker = null },
            )
            Picker.REGION -> ChoiceSheet(
                stringResource(R.string.field_region), PROPERTY_REGIONS.map { it.label to null }, regionLabel.ifEmpty { null },
                { label -> region = PROPERTY_REGIONS.first { it.label == label }.value; picker = null }, { picker = null },
            )
            null -> Unit
        }
    }
}

/** "00:01" shown as the web shows times: "12:01 AM". */
private fun clockTime(hhmm: String): String = runCatching {
    LocalTime.parse(hhmm).format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
}.getOrDefault(hhmm)

/** One suggestion under an address field: its name, and its state when that differs from the one chosen. */
@Composable
private fun Suggestion(s: AddressSuggestion, onPick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).topBorder(C.Border).testTag(AddPropertyTags.suggestion(s.name))
            .clickable(role = Role.Button, onClick = onPick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Txt(s.name, 13, 600)
        s.detail?.let { Txt(it, 11, 400, C.Muted) }
    }
}
