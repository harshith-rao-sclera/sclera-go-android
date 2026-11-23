package com.sclera.sclerago.presentation.access

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Group
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.usecase.access.GroupProblem
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.components.CheckBox
import com.sclera.sclerago.presentation.components.FooterDeleteButton
import com.sclera.sclerago.presentation.components.FormGroup
import com.sclera.sclerago.presentation.components.FormSheet
import com.sclera.sclerago.presentation.components.InlineField
import com.sclera.sclerago.presentation.components.InlineInput
import com.sclera.sclerago.presentation.components.TintChip
import com.sclera.sclerago.presentation.components.iconOf
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** Opacity of the rows of a locked group's permissions (all ticked, none editable). */
private const val LockedRowAlpha = 0.75f

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
            if (locked) stringResource(R.string.access_group_locked_sub, initial.name) else stringResource(R.string.access_group_new_sub),
            stringResource(if (isNew) R.string.access_action_add_group else R.string.access_action_save),
            problem(draft) == null, error?.asString(), onClose,
            onPrimary = { error = onSave(draft)?.toUiText() },
            leadingIcon = iconOf(Permission.MANAGE_ACCESS.iconPath),
            bodyBackground = C.Bg, bodyPadding = PaddingValues(vertical = 12.dp), bodySpacing = 12.dp,
            primaryModifier = Modifier.testTag(AccessTags.SHEET_PRIMARY),
            dirty = name.trim() != initial.name.trim() || description.trim() != initial.description.trim() || perms != initial.permissions,
            footerLeading = deleteButton,
        ) {
            FormGroup(stringResource(R.string.access_group_details), Icons.Users) {
                InlineField(stringResource(R.string.access_field_name)) {
                    InlineInput(name, { name = it.take(NameMax); error = null }, "", Modifier.testTag(AccessTags.GROUP_NAME))
                }
                InlineField(stringResource(R.string.access_field_description)) {
                    InlineInput(description, { description = it.take(DescriptionMax) }, "")
                }
            }
            FormGroup(stringResource(R.string.access_what_they_can_do), Icons.ShieldCheck) {
                orderedPermissions().forEach {
                    PermissionRow(it, on = locked || it in perms, locked = locked) { perms = if (it in perms) perms - it else perms + it }
                }
            }
        }
    }
}

/** A permission to tick: its name ("Needs all properties" where that applies) over what it lets people do. */
@Composable
private fun PermissionRow(p: Permission, on: Boolean, locked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).topBorder(C.Border).alpha(if (locked) LockedRowAlpha else 1f)
            .testTag(AccessTags.permission(p)).semantics { selected = on }
            .clickable(enabled = !locked, role = Role.Checkbox, onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.padding(top = 1.dp)) { CheckBox(on, shape = r(5), offColor = C.Soft, checkSize = 12.dp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Txt(p.title(), 14, 600)
                if (p.needsAll()) TintChip(stringResource(R.string.access_needs_all))
            }
            Txt(p.description(), 11, 400, C.Muted, maxLines = Int.MAX_VALUE, lineHeight = 15.sp)
        }
    }
}
