package com.sclera.sclerago.presentation.property

import com.sclera.sclerago.presentation.components.pressClickable
import com.sclera.sclerago.presentation.components.PRESSED_CARD
import androidx.compose.foundation.layout.height
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.common.storedTimeText
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.sclera.sclerago.presentation.components.SheetHeader
import com.sclera.sclerago.presentation.components.BottomSheet
import com.sclera.sclerago.presentation.components.SkeletonBlock
import com.sclera.sclerago.presentation.components.SkeletonLine
import com.sclera.sclerago.presentation.components.ChoiceSheet
import com.sclera.sclerago.presentation.components.AccessGate
import com.sclera.sclerago.presentation.components.FooterDeleteButton
import com.sclera.sclerago.presentation.components.FormGroup
import com.sclera.sclerago.presentation.components.FormSheet
import com.sclera.sclerago.presentation.components.InlineField
import com.sclera.sclerago.presentation.components.InlineInput
import com.sclera.sclerago.presentation.components.InlineSelect
import com.sclera.sclerago.presentation.components.PillTone
import com.sclera.sclerago.presentation.components.PlainField
import com.sclera.sclerago.presentation.components.StatusPill
import com.sclera.sclerago.presentation.components.SecureWindow
import com.sclera.sclerago.presentation.components.SearchablePickerSheet
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

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

/**
 * Add New Property, as the web's form: Property details (name and type required; address lines, then City, State and
 * Country with the server's suggestions, and the region, which a picked country fills in), Primary contact (name,
 * email, phone number: digits only), Notes and Availability (start and end time, access code). Add checks the
 * required fields and the email and phone as the web does; [onAdd] returns what stopped it, [serverError] says the
 * server refused. [suggestions] are those for the address field being typed in ([onSuggest] asks for them).
 * With [initial] it edits that property instead (the info button on a property): the form starts from it, is titled
 * "Property Info", and its footer has Delete and Save (Delete takes Cancel's place; [onDelete] asks first). While
 * [readOnly] (offline: the copy saved on the device; or a group without Manage properties) it only shows the property:
 * nothing can be changed, Save is off, Delete is hidden and a line says why ([readOnlyReason]). With [accessBlock] (the
 * group can't manage properties) Save and Delete stay, greyed out, and a tap on either says why.
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
    readOnlyReason: Int = R.string.property_offline_edit,
    accessBlock: UiText? = null,
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
    var postcode by rememberSaveable { mutableStateOf(from.address.postcode) }
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
        TimePickerDialog(context, { _, hh, mm -> set("%02d:%02d".format(hh, mm)) }, h, m, android.text.format.DateFormat.is24HourFormat(context)).show()
    }

    /** What each address field holds, and what picking a suggestion there also changes (as the web's AddressFields). */
    fun valueOf(field: AddressField) = when (field) {
        AddressField.CITY -> city
        AddressField.STATE -> state
        AddressField.COUNTRY -> country
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
    fun AddressInput(field: AddressField, label: Int, hint: Int, needsCountry: Boolean = false) {
        // State and City wait for a country, as in the design.
        val waiting = needsCountry && country.isBlank()
        InlineField(stringResource(label), dim = waiting) {
            InlineSelect(valueOf(field), stringResource(if (waiting) R.string.property_choose_country_first else hint), enabled = editable && !waiting) { focused = field }
        }
    }

    val deleteName = from.name
    val deleteButton: (@Composable RowScope.() -> Unit)? = onDelete?.takeIf { editable || accessBlock != null }?.let { delete ->
        {
            AccessGate(accessBlock, Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth()) {
                    FooterDeleteButton(stringResource(R.string.property_delete_label, deleteName), Modifier.testTag(AddPropertyTags.DELETE), delete)
                }
            }
        }
    }
    val typeLabel = if (type.isBlank()) "" else labelOf(type, PROPERTY_TYPES)
    val regionLabel = if (region.isBlank()) "" else labelOf(region, PROPERTY_REGIONS)
    val draft = from.copy(
        name = name, type = type,
        address = from.address.copy(line1 = line1.trim(), line2 = line2.trim(), country = country.trim(), state = state.trim(), region = region, city = city.trim(), postcode = postcode.trim()),
        contact = Contact(contactName.trim(), contactEmail.trim(), contactPhone.trim()),
        notes = notes.trim(),
        availability = Availability(start, end, accessCode.trim()),
    )

    Box(Modifier.fillMaxSize()) {
        FormSheet(
            stringResource(if (initial != null) R.string.property_info_title else R.string.property_add_title),
            if (initial == null) stringResource(R.string.property_add_sub) else null,
            stringResource(if (initial != null) R.string.action_save else R.string.property_add_primary),
            // Add stays off until the required fields (name and type) are filled in, as in the design.
            editable && name.isNotBlank() && type.isNotBlank(), (error ?: serverError)?.asString(), onClose,
            onPrimary = { if (editable) error = onAdd(draft) },
            leadingIcon = Icons.Building,
            // How many of the two required fields (name, type) are filled in.
            progress = if (initial == null) ((if (name.isNotBlank()) 1 else 0) + (if (type.isNotBlank()) 1 else 0)) / 2f else null,
            footerLeading = deleteButton,
            primaryBlock = accessBlock,
            bodyBackground = C.Bg, bodyPadding = PaddingValues(vertical = 12.dp), bodySpacing = 12.dp,
            // Something was typed or changed: closing asks before discarding it.
            dirty = listOf(
                name to from.name, type to from.type, line1 to from.address.line1, line2 to from.address.line2,
                city to from.address.city, state to from.address.state, country to from.address.country, region to from.address.region, postcode to from.address.postcode,
                contactName to from.contact.name, contactEmail to from.contact.email, contactPhone to from.contact.phone,
                notes to from.notes, start to from.availability.start, end to from.availability.end,
                accessCode to from.availability.accessCode,
            ).any { (now, was) -> now != was },
        ) {
            if (readOnly) {
                Txt(
                    stringResource(readOnlyReason), 12, 400, C.Muted,
                    Modifier.padding(horizontal = 16.dp).testTag(AddPropertyTags.READ_ONLY), maxLines = Int.MAX_VALUE,
                )
            }
            FormGroup(
                stringResource(R.string.property_group_details), Icons.Building, collapsible = true,
                trailing = {
                    val missing = (if (name.isBlank()) 1 else 0) + (if (type.isBlank()) 1 else 0)
                    if (missing == 0) StatusPill(stringResource(R.string.property_pill_ready), PillTone.READY)
                    else StatusPill(stringResource(R.string.property_pill_required, missing), PillTone.NEEDED)
                },
            ) {
                InlineField(stringResource(R.string.field_name), required = true) {
                    InlineInput(name, { name = it; error = null }, stringResource(R.string.property_name_hint), Modifier.testTag(AddPropertyTags.NAME), enabled = editable)
                }
                InlineField(stringResource(R.string.property_label_type), required = true) {
                    InlineSelect(typeLabel, stringResource(R.string.property_select_type), enabled = editable) { picker = Picker.TYPE }
                }
            }
            FormGroup(stringResource(R.string.property_group_address), Icons.Pin, collapsible = true) {
                InlineField(stringResource(R.string.field_address_line1)) { InlineInput(line1, { line1 = it }, stringResource(R.string.field_address_line1_hint), enabled = editable) }
                InlineField(stringResource(R.string.field_address_line2)) { InlineInput(line2, { line2 = it }, stringResource(R.string.field_address_line2_hint), enabled = editable) }
                AddressInput(AddressField.COUNTRY, R.string.field_country, R.string.property_type_or_select_country)
                AddressInput(AddressField.STATE, R.string.field_state, R.string.property_type_or_select_state, needsCountry = true)
                InlineField(stringResource(R.string.field_region)) { InlineSelect(regionLabel, stringResource(R.string.property_select_region), enabled = editable) { picker = Picker.REGION } }
                AddressInput(AddressField.CITY, R.string.field_city, R.string.property_type_or_select_city, needsCountry = true)
                InlineField(stringResource(R.string.field_postal_code)) {
                    InlineInput(postcode, { postcode = it }, stringResource(R.string.field_postal_code_hint), enabled = editable)
                }
            }
            FormGroup(stringResource(R.string.property_group_contact), Icons.User, collapsible = true) {
                InlineField(stringResource(R.string.property_label_contact_name)) { InlineInput(contactName, { contactName = it }, stringResource(R.string.field_contact_name_hint), enabled = editable) }
                InlineField(stringResource(R.string.property_label_contact_email)) {
                    InlineInput(contactEmail, { contactEmail = it; error = null }, stringResource(R.string.field_email_hint), enabled = editable, keyboard = KeyboardType.Email)
                }
                InlineField(stringResource(R.string.property_label_contact_phone)) {
                    // Digits only, as the web's field takes them.
                    InlineInput(
                        contactPhone, { contactPhone = it.filter(Char::isDigit); error = null }, stringResource(R.string.field_phone_hint),
                        enabled = editable, keyboard = KeyboardType.Phone,
                    )
                }
            }
            FormGroup(
                stringResource(R.string.property_group_notes), Icons.FileDoc, collapsible = true,
                trailing = { if (notes.isBlank()) StatusPill(stringResource(R.string.property_pill_none_yet), PillTone.NEUTRAL) },
            ) {
                Box(Modifier.fillMaxWidth().topBorder(C.Border).padding(horizontal = 12.dp)) {
                    PlainField(
                        notes, { notes = it.take(NotesMax) }, stringResource(R.string.property_notes_hint),
                        Modifier.fillMaxWidth().heightIn(min = 84.dp).padding(vertical = 12.dp), size = 14, singleLine = false, minLines = 3, readOnly = readOnly,
                    )
                }
            }
            FormGroup(stringResource(R.string.property_group_availability), Icons.Clock, collapsible = true) {
                InlineField(stringResource(R.string.property_label_available_from)) { InlineSelect(storedTimeText(start), "", enabled = editable, chevron = false) { pickTime(start) { start = it } } }
                InlineField(stringResource(R.string.property_label_available_until)) { InlineSelect(storedTimeText(end), "", enabled = editable, chevron = false) { pickTime(end) { end = it } } }
                InlineField(stringResource(R.string.field_access_code)) { InlineInput(accessCode, { accessCode = it }, stringResource(R.string.field_access_code_hint), enabled = editable) }
            }
        }
        when (picker) {
            Picker.TYPE -> SearchablePickerSheet(
                title = stringResource(R.string.property_field_type), options = PROPERTY_TYPES.map { it.value to it.label }, selectedValue = type,
                pickerId = "property-type",
                onPick = { value -> type = value; picker = null; error = null }, onClose = { picker = null },
            )
            Picker.REGION -> ChoiceSheet(
                stringResource(R.string.field_region), PROPERTY_REGIONS.map { it.label to null }, regionLabel.ifEmpty { null },
                { label -> region = PROPERTY_REGIONS.first { it.label == label }.value; picker = null }, { picker = null },
            )
            null -> Unit
        }
        focused?.let { field ->
            val title = stringResource(when (field) { AddressField.COUNTRY -> R.string.field_country; AddressField.STATE -> R.string.field_state; AddressField.CITY -> R.string.field_city })
            AddressPickerSheet(field, title, country, valueOf(field), suggestions,
                onSearch = { onSuggest(field, country, state, it) },
                onPick = { pick(field, it); focused = null },
                onClose = { onClearSuggestions(); focused = null })
        }
    }
}


/** Placeholder suggestions under an address field while the server is asked, read out as "Loading suggestions…". */
@Composable
private fun SuggestionsSkeleton() {
    val label = stringResource(R.string.scrt_suggestions_loading)
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = label }) {
        repeat(SUGGESTION_PLACEHOLDERS) { i ->
            Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).topBorder(C.Border).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                SkeletonLine(if (i % 2 == 0) 0.45f else 0.3f, 10.dp)
            }
        }
    }
}

/** How many placeholder suggestions show while the server is asked. */
private const val SUGGESTION_PLACEHOLDERS = 3

/**
 * A property's details sheet while they are read from the server: its name in the title and the form's fields in
 * placeholders (a label over a value), read out as "Loading property…". × closes it (and stops the read).
 */
@Composable
internal fun PropertyFormSkeleton(name: String, onClose: () -> Unit) {
    val label = stringResource(R.string.scrt_property_loading)
    BottomSheet(onClose) {
        SheetHeader(stringResource(R.string.property_info_title), name, onClose)
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp).semantics(mergeDescendants = true) { contentDescription = label },
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            repeat(FORM_PLACEHOLDERS) { i ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonLine(0.25f, 9.dp)
                    SkeletonBlock(Modifier.fillMaxWidth(if (i % 3 == 2) 0.5f else 1f).height(44.dp), r(12))
                }
            }
        }
    }
}

/** How many placeholder fields the details sheet shows while it reads the property. */
private const val FORM_PLACEHOLDERS = 6

/** One suggestion under an address field: its name, and its state when that differs from the one chosen. */
@Composable
private fun Suggestion(s: AddressSuggestion, onPick: () -> Unit) {
    Column(
        Modifier.pressClickable(pressed = PRESSED_CARD, role = Role.Button, onClick = onPick).fillMaxWidth().heightIn(min = 48.dp).topBorder(C.Border).testTag(AddPropertyTags.suggestion(s.name))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Txt(s.name, 13, 600)
        s.detail?.let { Txt(it, 11, 400, C.Muted, maxLines = Int.MAX_VALUE) }
    }
}
