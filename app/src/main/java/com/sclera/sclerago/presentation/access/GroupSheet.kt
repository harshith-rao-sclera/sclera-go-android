package com.sclera.sclerago.presentation.access

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Group
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.usecase.access.GroupProblem
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.components.CheckBox
import com.sclera.sclerago.presentation.components.FooterDeleteButton
import com.sclera.sclerago.presentation.components.FormSheet
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt


/** The longest name and description the web allows. */
private const val NameMax = 120
private const val DescriptionMax = 500

/**
 * New group / edit one, as the web's group form: name, description (optional) and what its people can do, each
 * permission with its description and "Needs all properties" where that applies. Admins always have every
 * permission (shown ticked, not editable). An existing group other than Admins has Delete and Save in the footer
 * (Delete takes Cancel's place; [onDelete] asks first).
 */
@Composable
internal fun GroupSheet(
    initial: Group,
    problem: (Group) -> GroupProblem?,
    onSave: (Group) -> GroupProblem?,
    onClose: () -> Unit,
    canDelete: Boolean = false,
    onDelete: (Group) -> Unit = {},
) {
    val isNew = initial.id.isEmpty()
    val locked = initial.locked
    var name by remember { mutableStateOf(initial.name) }
    var description by remember { mutableStateOf(initial.description) }
    var perms by remember { mutableStateOf(initial.permissions) }
    var error by remember { mutableStateOf<UiText?>(null) }
    val draft = initial.copy(name = name, description = description, permissions = perms)
    val deleteButton: (@Composable RowScope.() -> Unit)? =
        if (!isNew && canDelete) {
            { FooterDeleteButton(stringResource(R.string.access_delete_group, initial.name), Modifier.testTag(AccessTags.DELETE_GROUP)) { onDelete(initial) } }
        } else {
            null
        }

    Box(Modifier.fillMaxSize().testTag(AccessTags.GROUP_SHEET)) {
        FormSheet(
            if (isNew) stringResource(R.string.access_new_group) else stringResource(R.string.access_edit_group, initial.name),
            if (locked) stringResource(R.string.access_group_locked_sub, initial.name) else stringResource(R.string.access_group_sub_short),
            stringResource(if (isNew) R.string.access_action_add_group else R.string.access_action_save),
            problem(draft) == null, error?.asString(), onClose,
            onPrimary = { error = onSave(draft)?.toUiText() },
            bodyPadding = PaddingValues(16.dp), bodySpacing = 18.dp,
            primaryModifier = Modifier.testTag(AccessTags.SHEET_PRIMARY),
            dirty = name.trim() != initial.name.trim() || description.trim() != initial.description.trim() || perms != initial.permissions,
            footerLeading = deleteButton,
        ) {
            StackedField(stringResource(R.string.access_field_name)) {
                BoxedInput(name, { name = it.take(NameMax); error = null }, stringResource(R.string.access_group_name_hint), Modifier.testTag(AccessTags.GROUP_NAME))
            }
            StackedField(stringResource(R.string.access_field_description)) {
                BoxedInput(description, { description = it.take(DescriptionMax) }, stringResource(R.string.access_group_description_hint))
            }
            StackedField(stringResource(R.string.access_what_they_can_do)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    orderedPermissions().forEach {
                        PermissionRow(it, on = locked || it in perms, locked = locked) { perms = if (it in perms) perms - it else perms + it }
                    }
                }
            }
        }
    }
}

/** A permission to tick: a card with its name ("Requires all properties" where that applies) over what it lets people do. */
@Composable
private fun PermissionRow(p: Permission, on: Boolean, locked: Boolean, onToggle: () -> Unit) {
    ChoiceCard(
        on, Role.Checkbox, { CheckBox(on, shape = r(5), offColor = C.Soft, checkSize = 12.dp, size = 20.dp) },
        p.title(), p.description(),
        Modifier.testTag(AccessTags.permission(p)),
        chip = if (p.needsAll()) stringResource(R.string.access_needs_all) else null,
        enabled = !locked, faded = locked, onClick = onToggle,
    )
}
