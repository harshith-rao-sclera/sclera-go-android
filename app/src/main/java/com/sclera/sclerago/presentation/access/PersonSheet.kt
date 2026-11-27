package com.sclera.sclerago.presentation.access

import com.sclera.sclerago.presentation.components.pressClickable
import com.sclera.sclerago.presentation.components.PRESSED_CARD
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.DirectoryPerson
import com.sclera.sclerago.domain.model.Group
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.Person
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.domain.usecase.access.PersonProblem
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.components.SkeletonLead
import com.sclera.sclerago.presentation.components.SkeletonList
import com.sclera.sclerago.presentation.components.AccessGate
import com.sclera.sclerago.presentation.components.CheckBox
import com.sclera.sclerago.presentation.components.SearchablePickerSheet
import com.sclera.sclerago.presentation.components.FormSheet
import com.sclera.sclerago.presentation.components.LocalToaster
import com.sclera.sclerago.presentation.components.ToastKind
import com.sclera.sclerago.presentation.components.RadioDot
import com.sclera.sclerago.presentation.components.SearchField
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** More properties than this and the list gets a search field (as on the web). */
private const val PropertySearchFrom = 8

/**
 * Add a user / change one's access, as the web's user form: the email (when adding; tapping it lists the
 * organisation's directory and typing narrows it: [onEmailTyped] asks, [suggestions] are who matches and
 * [suggestionHint] says why there are none), the group ("No access" too, for someone already here), then which
 * properties: all of them, or only the ones selected. Someone picked from the directory on the Users list comes
 * in with their email and name filled in ([Person.inviteName]), and their photo goes with the invitation. Stateless
 * towards the app: [problem] says what is still missing, [onSave] returns what stopped the save. Without Manage access
 * ([blocked]) every field and Save stay, greyed out, and a tap says why (the User Access Guide).
 */
@Composable
internal fun PersonSheet(
    initial: Person,
    groups: List<Group>,
    properties: List<Property>,
    problem: (Person) -> PersonProblem?,
    onSave: (Person) -> PersonProblem?,
    onClose: () -> Unit,
    suggestions: List<DirectoryPerson> = emptyList(),
    suggestionHint: UiText? = null,
    onEmailTyped: (String) -> Unit = {},
    onSuggestionPicked: () -> Unit = {},
    /** The directory is being asked: placeholder people instead of the last search's. */
    suggestionsLoading: Boolean = false,
    /** Why people can't be added or changed (no Manage access), or null when they can. */
    blocked: UiText? = null,
) {
    val isNew = initial.id.isEmpty()
    var email by remember { mutableStateOf(initial.email) }
    var picked by remember {
        mutableStateOf(
            initial.takeIf { isNew && (it.inviteName != null || it.imageUrl != null) }?.let { DirectoryPerson(it.email, it.inviteName, null, it.imageUrl) },
        )
    }
    var emailFocused by remember { mutableStateOf(false) }
    var groupId by remember { mutableStateOf(initial.groupId) }
    var all by remember { mutableStateOf(initial.allProperties) }
    var chosen by remember { mutableStateOf(initial.propertyIds) }
    var choosingGroup by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiText?>(null) }
    val group = groups.firstOrNull { it.id == groupId }
    // Manage properties and Manage access act on the whole organisation: a group with either has every property, never a
    // chosen few (the server grants them only to someone with all properties), as the web's.
    val organisationWide = group?.let { organisationWide(it) }.orEmpty()
    val everyProperty = all || organisationWide.isNotEmpty()
    // The name and photo go with the invitation only while the email is still the one picked (not retyped).
    val pickedNow = picked?.takeIf { isNew && it.email == email.trim() }
    val inviteName = pickedNow?.name?.takeIf { it.isNotBlank() }
    val draft = initial.copy(
        email = email, groupId = groupId, allProperties = everyProperty, propertyIds = if (everyProperty) emptySet() else chosen, inviteName = inviteName,
        imageUrl = if (isNew) pickedNow?.imageUrl else initial.imageUrl,
    )
    val noAccess = stringResource(R.string.access_no_access)
    val toaster = LocalToaster.current

    Box(Modifier.fillMaxSize().testTag(AccessTags.PERSON_SHEET)) {
        FormSheet(
            if (isNew) stringResource(R.string.access_person_add_heading) else initial.name,
            // The email goes under the name only when there is a name (as the web shows it).
            if (isNew) stringResource(R.string.access_person_add_sub_short) else initial.email.takeIf { it != initial.name },
            stringResource(if (isNew) R.string.access_action_add else R.string.access_action_save),
            // Without Manage access Save is greyed out too, and a tap says why instead of saving.
            blocked == null && problem(draft) == null, error?.asString(), onClose,
            onPrimary = { if (blocked != null) toaster.show(blocked, ToastKind.INFO) else error = onSave(draft)?.toUiText() },
            bodyPadding = PaddingValues(16.dp), bodySpacing = 18.dp,
            primaryModifier = Modifier.testTag(AccessTags.SHEET_PRIMARY),
            dirty = email.trim() != initial.email.trim() || groupId != initial.groupId ||
                everyProperty != initial.allProperties || (!everyProperty && chosen != initial.propertyIds),
        ) {
            if (isNew) {
                StackedField(stringResource(R.string.access_field_email)) {
                    BoxedInput(
                        email, { email = it; error = null; onEmailTyped(it) }, stringResource(R.string.access_email_or_name_hint),
                        Modifier.testTag(AccessTags.EMAIL).onFocusChanged {
                            // Tapping in lists the organisation's people straight away, so nobody has to remember an email.
                            if (it.isFocused && !emailFocused) onEmailTyped(email)
                            emailFocused = it.isFocused
                        },
                        keyboard = KeyboardType.Email, reason = blocked,
                    )
                    // The organisation's people are still being read: placeholders, then the hint or the matches.
                    if (emailFocused && suggestionsLoading && picked?.email != email.trim()) {
                        SkeletonList(stringResource(R.string.access_loading_users), count = 3, lead = SkeletonLead.CIRCLE, card = false, gap = 2.dp)
                    }
                    suggestionHint?.takeIf { emailFocused && !suggestionsLoading }?.let {
                        Txt(it.asString(), 12, 400, C.Muted, maxLines = Int.MAX_VALUE)
                    }
                    if (emailFocused && !suggestionsLoading && picked?.email != email.trim() && suggestions.isNotEmpty()) {
                        RowsCard {
                            suggestions.forEach { person ->
                                Suggestion(person, blocked) {
                                    email = person.email
                                    picked = person
                                    error = null
                                    onSuggestionPicked()
                                }
                            }
                        }
                    }
                }
            }
            StackedField(stringResource(R.string.access_field_group)) {
                BoxedSelect(group?.name ?: if (isNew) "" else noAccess, stringResource(R.string.access_choose_group), blocked) { choosingGroup = true }
                groupHint(group, isNew)?.let { Txt(it, 12, 400, C.Muted, maxLines = Int.MAX_VALUE) }
            }
            if (group != null) {
                StackedField(stringResource(R.string.label_properties)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ChoiceCard(
                            everyProperty, Role.RadioButton, { RadioDot(everyProperty) },
                            stringResource(R.string.access_all_properties), stringResource(R.string.access_scope_all_desc_short),
                            reason = blocked,
                        ) {
                            all = true
                            error = null
                        }
                        // A group that manages properties or access needs all of them: Chosen is greyed out then, and
                        // a tap says why.
                        val needsAll = organisationWideHint(group, organisationWide)
                        ChoiceCard(
                            !everyProperty, Role.RadioButton, { RadioDot(!everyProperty) },
                            stringResource(R.string.access_scope_chosen), stringResource(R.string.access_scope_chosen_desc_short),
                            chip = if (everyProperty) null else stringResource(R.string.access_n_chosen, chosen.size),
                            reason = blocked ?: needsAll,
                        ) {
                            all = false
                            error = null
                        }
                        needsAll?.let { Txt(it.asString(), 12, 400, C.Orange, maxLines = Int.MAX_VALUE) }
                    }
                }
                if (!everyProperty) PropertyPicker(properties, chosen, blocked) { id -> chosen = if (id in chosen) chosen - id else chosen + id }
            }
        }
        if (choosingGroup) {
            // Someone already here can be left in no group: they keep their sign-in but can open nothing.
            val options = (if (isNew) emptyList() else listOf("" to noAccess)) + groups.map { it.id to it.name }
            SearchablePickerSheet(
                title = stringResource(R.string.access_choose_group), options = options, selectedValue = groupId,
                pickerId = "access-group", descriptions = groups.associate { it.id to it.description },
                onPick = { id ->
                    groupId = id
                    choosingGroup = false
                    error = null
                },
                onClose = { choosingGroup = false },
            )
        }
    }
}

/** Under the group: what it is for, "Can look…" for a group that can do nothing, or what having no group means. */
@Composable
private fun groupHint(group: Group?, isNew: Boolean): String? = when {
    group == null -> if (isNew) null else stringResource(R.string.access_no_access_hint)
    group.description.isNotBlank() -> group.description
    group.effectivePermissions.isEmpty() -> stringResource(R.string.access_no_permissions_hint)
    else -> null
}

/** The [group]'s permissions that act on the whole organisation (Manage properties, Manage access), so need all properties. */
@Composable
private fun organisationWide(group: Group): List<Permission> = orderedPermissions().filter { group.can(it) && it.needsAll() }

/** "Admins can manage properties and manage access, so they get all properties." (null when nothing needs it). */
@Composable
private fun organisationWideHint(group: Group, permissions: List<Permission>): UiText? {
    val names = permissions.map { it.title().lowercase() }
    if (names.isEmpty()) return null
    return UiText.Res(R.string.access_needs_all_hint, listOf(group.name, names.joinToString(stringResource(R.string.access_and))))
}

/** The properties to tick, in a card, with a search field when there are many; greyed out, saying [reason], when they can't be. */
@Composable
private fun PropertyPicker(properties: List<Property>, chosen: Set<String>, reason: UiText?, onToggle: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    if (properties.size > PropertySearchFrom) SearchField(q, { q = it }, stringResource(R.string.access_search_properties))
    val typed = q.trim().lowercase()
    val listed = properties.filter { typed.isEmpty() || typed in it.name.lowercase() }
    if (listed.isEmpty()) {
        val empty = if (properties.isEmpty()) R.string.access_no_properties_yet else R.string.access_no_property_match
        Txt(stringResource(empty), 12, 400, C.Muted, maxLines = Int.MAX_VALUE)
        return
    }
    RowsCard {
        listed.forEachIndexed { i, p ->
            CardOptionRow(
                Modifier.then(if (i > 0) Modifier.topBorder(C.Border) else Modifier).semantics { selected = p.id in chosen },
                reason = reason,
                onClick = { onToggle(p.id) },
            ) {
                CheckBox(p.id in chosen, shape = r(5), offColor = C.Soft, checkSize = 12.dp, size = 20.dp)
                Txt(p.name, 14, 400, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Someone from the directory to add: their name (or email) over their email; greyed out, saying [reason], when they can't be. */
@Composable
private fun Suggestion(person: DirectoryPerson, reason: UiText?, onPick: () -> Unit) {
    val name = person.name?.takeIf { it.isNotBlank() }
    AccessGate(reason, Modifier.fillMaxWidth()) {
        Column(
            Modifier.pressClickable(pressed = PRESSED_CARD, role = Role.Button, onClick = onPick).fillMaxWidth().heightIn(min = 48.dp).topBorder(C.Border).testTag(AccessTags.suggestion(person.email))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Txt(name ?: person.email, 13, 600)
            if (name != null) Txt(person.email, 11, 400, C.Muted)
        }
    }
}
