package com.sclera.sclerago.presentation.access

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.sclera.sclerago.domain.model.toggled
import com.sclera.sclerago.domain.usecase.access.GroupProblem
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.components.AccessGate
import com.sclera.sclerago.presentation.components.CheckBox
import com.sclera.sclerago.presentation.components.FooterDeleteButton
import com.sclera.sclerago.presentation.components.FormSheet
import com.sclera.sclerago.presentation.components.LocalToaster
import com.sclera.sclerago.presentation.components.ToastKind
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.theme.C


/** The longest name and description the web allows. */
private const val NameMax = 120
private const val DescriptionMax = 500

/**
 * New group / edit one, as the web's group form: name, description (optional) and what its people can do, each
 * permission with its description and "Needs all properties" where that applies. Admins always have every
 * permission (shown ticked, greyed out). An existing group has Delete and Save in the footer (Delete takes Cancel's
 * place; [onDelete] asks first).
 *
 * Every change control stays where it is and, when it can't be used, is greyed out and says why when tapped (the User
 * Access Guide): [blocked] for the fields and Save (no Manage access), [permissionsReason] for the permissions (also
 * the Admins group, which always keeps every one) and [deleteReason] for Delete (also Admins, and a group with people).
 */
@Composable
internal fun GroupSheet(
    initial: Group,
    problem: (Group) -> GroupProblem?,
    onSave: (Group) -> GroupProblem?,
    onClose: () -> Unit,
    canDelete: Boolean = false,
    onDelete: (Group) -> Unit = {},
    blocked: UiText? = null,
    permissionsReason: UiText? = blocked,
    deleteReason: UiText? = blocked,
) {
    val isNew = initial.id.isEmpty()
    val locked = initial.locked
    var name by remember { mutableStateOf(initial.name) }
    var description by remember { mutableStateOf(initial.description) }
    var perms by remember { mutableStateOf(initial.permissions) }
    var error by remember { mutableStateOf<UiText?>(null) }
    val toaster = LocalToaster.current
    val draft = initial.copy(name = name, description = description, permissions = perms)
    val deleteButton: (@Composable RowScope.() -> Unit)? =
        if (!isNew && canDelete) {
            {
                AccessGate(deleteReason, Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth()) {
                        FooterDeleteButton(stringResource(R.string.access_delete_group, initial.name), Modifier.testTag(AccessTags.DELETE_GROUP)) {
                            onDelete(initial)
                        }
                    }
                }
            }
        } else {
            null
        }

    Box(Modifier.fillMaxSize().testTag(AccessTags.GROUP_SHEET)) {
        FormSheet(
            if (isNew) stringResource(R.string.access_new_group) else stringResource(R.string.access_edit_group, initial.name),
            if (locked) stringResource(R.string.access_group_locked_sub, initial.name) else stringResource(R.string.access_group_sub_includes),
            stringResource(if (isNew) R.string.access_action_add_group else R.string.access_action_save),
            // Without Manage access Save is greyed out too, and a tap says why instead of saving.
            blocked == null && problem(draft) == null, error?.asString(), onClose,
            onPrimary = { if (blocked != null) toaster.show(blocked, ToastKind.INFO) else error = onSave(draft)?.toUiText() },
            bodyPadding = PaddingValues(16.dp), bodySpacing = 18.dp,
            primaryModifier = Modifier.testTag(AccessTags.SHEET_PRIMARY),
            dirty = name.trim() != initial.name.trim() || description.trim() != initial.description.trim() || perms != initial.permissions,
            footerLeading = deleteButton,
        ) {
            StackedField(stringResource(R.string.access_field_name)) {
                BoxedInput(
                    name, { name = it.take(NameMax); error = null }, stringResource(R.string.access_group_name_hint), Modifier.testTag(AccessTags.GROUP_NAME),
                    reason = blocked,
                )
            }
            StackedField(stringResource(R.string.access_field_description)) {
                BoxedInput(description, { description = it.take(DescriptionMax) }, stringResource(R.string.access_group_description_hint), reason = blocked)
            }
            StackedField(stringResource(R.string.access_what_they_can_do)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Ticking one ticks the ones it includes, and unticking one unticks every permission that includes
                    // it (the server's list says which), so what's ticked is what the group can do.
                    val infos = LocalPermissionInfo.current.values
                    orderedPermissions().forEach {
                        PermissionRow(it, on = locked || it in perms, permissionsReason) { perms = perms.toggled(it, infos) }
                    }
                }
            }
        }
    }
}

/**
 * A permission to tick: a card with its name ("Requires all properties" where that applies) over what it lets people do;
 * greyed out, saying [reason] when tapped, when it can't be changed.
 */
@Composable
private fun PermissionRow(p: Permission, on: Boolean, reason: UiText?, onToggle: () -> Unit) {
    ChoiceCard(
        on, Role.Checkbox, { CheckBox(on, shape = r(5), offColor = C.Soft, checkSize = 12.dp, size = 20.dp) },
        p.title(), p.description(),
        Modifier.testTag(AccessTags.permission(p)),
        chip = if (p.needsAll()) stringResource(R.string.access_needs_all) else null,
        reason = reason, onClick = onToggle,
    )
}
