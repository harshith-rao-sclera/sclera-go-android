package com.sclera.sclerago.presentation.access

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Group
import com.sclera.sclerago.domain.model.Person
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.domain.usecase.access.PersonProblem
import com.sclera.sclerago.domain.usecase.auth.ValidateEmailUseCase
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.components.CheckBox
import com.sclera.sclerago.presentation.components.ChoiceSheet
import com.sclera.sclerago.presentation.components.FormGroup
import com.sclera.sclerago.presentation.components.FormSheet
import com.sclera.sclerago.presentation.components.InlineField
import com.sclera.sclerago.presentation.components.InlineInput
import com.sclera.sclerago.presentation.components.InlineSelect
import com.sclera.sclerago.presentation.components.PillTone
import com.sclera.sclerago.presentation.components.StatusPill
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

private enum class Picker { GROUP, SCOPE }

/**
 * Add a person / edit one: a Person section (email, group) and a Property access section.
 * Stateless towards the app: [problem] says what is wrong with a draft, [onSave] returns what stopped the save.
 */
@Composable
internal fun PersonSheet(
    initial: Person,
    groups: List<Group>,
    properties: List<Property>,
    isMe: Boolean,
    problem: (Person) -> PersonProblem?,
    onSave: (Person) -> PersonProblem?,
    onClose: () -> Unit,
    onRemove: (Person) -> Unit,
) {
    val isNew = initial.id.isEmpty()
    var email by remember { mutableStateOf(initial.email) }
    var groupId by remember { mutableStateOf(initial.groupId) }
    var all by remember { mutableStateOf(initial.allProperties) }
    var chosen by remember { mutableStateOf(initial.propertyIds) }
    var picking by remember { mutableStateOf<Picker?>(null) }
    var error by remember { mutableStateOf<UiText?>(null) }
    val group = groups.firstOrNull { it.id == groupId }
    val forced = group?.needsAllProperties == true
    val isAll = all || forced
    val draft = initial.copy(email = email, groupId = groupId, allProperties = isAll, propertyIds = chosen)
    val emailOk = !isNew || ValidateEmailUseCase.EMAIL_ADDRESS.matches(email.trim())
    val scopeOk = isAll || chosen.isNotEmpty()
    // The Person pill counts what is still missing: the email (when adding) and the property access.
    val required = (if (emailOk) 0 else 1) + (if (scopeOk) 0 else 1)
    val total = if (isNew) 2 else 1
    val allProperties = stringResource(R.string.access_all_properties)
    val chosenProperties = stringResource(R.string.access_scope_chosen)
    val groupName = group?.name.orEmpty()

    Box(Modifier.fillMaxSize().testTag(AccessTags.PERSON_SHEET)) {
        FormSheet(
            if (isNew) stringResource(R.string.access_person_add_title) else initial.name,
            if (isNew) stringResource(R.string.access_person_add_sub) else initial.email,
            stringResource(if (isNew) R.string.access_action_add else R.string.access_action_save),
            problem(draft) == null, error?.asString(), onClose,
            onPrimary = { error = onSave(draft)?.toUiText(email, groupName) },
            leadingIcon = Icons.Users, progress = (total - required) / total.toFloat(),
            bodyBackground = C.Bg, bodyPadding = PaddingValues(vertical = 12.dp), bodySpacing = 12.dp,
            primaryModifier = Modifier.testTag(AccessTags.SHEET_PRIMARY),
            dirty = email.trim() != initial.email.trim() || groupId != initial.groupId ||
                all != initial.allProperties || chosen != initial.propertyIds,
        ) {
            FormGroup(stringResource(R.string.access_section_person), Icons.Users, trailing = {
                Box(Modifier.testTag(AccessTags.PERSON_PILL)) {
                    if (required == 0) StatusPill(stringResource(R.string.property_ready), PillTone.READY)
                    else StatusPill(stringResource(R.string.property_required_count, required), PillTone.NEEDED)
                }
            }) {
                if (isNew) {
                    InlineField(stringResource(R.string.access_field_email), required = true) {
                        InlineInput(
                            email, { email = it; error = null }, stringResource(R.string.login_email_hint),
                            Modifier.testTag(AccessTags.EMAIL), keyboard = KeyboardType.Email,
                        )
                    }
                }
                InlineField(stringResource(R.string.access_field_group), required = true) {
                    InlineSelect(groupName, stringResource(R.string.access_choose_group), upDown = true) { picking = Picker.GROUP }
                }
                group?.description?.takeIf { it.isNotBlank() }?.let {
                    Txt(it, 12, 400, C.Muted, Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp), maxLines = 3)
                }
            }
            FormGroup(stringResource(R.string.access_section_property_access), Icons.Building, trailing = {
                if (scopeOk) StatusPill(if (isAll) stringResource(R.string.access_all) else stringResource(R.string.access_n_chosen, chosen.size), PillTone.READY)
                else StatusPill(stringResource(R.string.property_required_count, 1), PillTone.NEEDED)
            }) {
                InlineField(stringResource(R.string.access_field_access), required = true) {
                    InlineSelect(if (isAll) allProperties else chosenProperties, "", upDown = true) { picking = Picker.SCOPE }
                }
                if (forced) {
                    Txt(
                        stringResource(R.string.access_forced_all, groupName), 12, 400, C.Muted,
                        Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp), maxLines = 3,
                    )
                }
                if (!isAll) {
                    properties.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).topBorder(C.Border)
                                .clickable { chosen = if (p.id in chosen) chosen - p.id else chosen + p.id; error = null }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CheckBox(p.id in chosen, shape = r(5), offColor = C.Soft, checkSize = 12.dp)
                            Txt(p.name, 14, 500, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            // Not in the design: removing a person needs an entry point (a confirmation follows).
            if (!isNew && !isMe) {
                Txt(
                    stringResource(R.string.access_remove_person, initial.name), 14, 600, C.Red,
                    Modifier.testTag(AccessTags.REMOVE).padding(horizontal = 16.dp).clip(r(8)).clickable { onRemove(initial) }
                        .heightIn(min = 48.dp).padding(vertical = 6.dp),
                )
            }
        }
        when (picking) {
            Picker.GROUP -> ChoiceSheet(
                stringResource(R.string.access_choose_group), groups.map { it.name to it.description.ifBlank { null } }, group?.name,
                onPick = { name -> groups.firstOrNull { it.name == name }?.let { groupId = it.id }; picking = null; error = null },
                onClose = { picking = null },
            )
            Picker.SCOPE -> ChoiceSheet(
                stringResource(R.string.access_section_property_access),
                listOf(
                    allProperties to stringResource(R.string.access_scope_all_desc),
                    chosenProperties to stringResource(R.string.access_scope_chosen_desc),
                ),
                if (isAll) allProperties else chosenProperties,
                onPick = { all = it == allProperties; picking = null; error = null },
                onClose = { picking = null },
                disabled = if (forced) setOf(chosenProperties) else emptySet(),
            )
            null -> Unit
        }
    }
}
