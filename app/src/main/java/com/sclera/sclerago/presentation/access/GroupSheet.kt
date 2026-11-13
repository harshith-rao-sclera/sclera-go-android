package com.sclera.sclerago.presentation.access

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Group
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.usecase.access.GroupProblem
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.components.CheckBox
import com.sclera.sclerago.presentation.components.FormGroup
import com.sclera.sclerago.presentation.components.FormSheet
import com.sclera.sclerago.presentation.components.InlineField
import com.sclera.sclerago.presentation.components.InlineInput
import com.sclera.sclerago.presentation.components.PillTone
import com.sclera.sclerago.presentation.components.StatusPill
import com.sclera.sclerago.presentation.components.TintChip
import com.sclera.sclerago.presentation.components.asColor
import com.sclera.sclerago.presentation.components.iconOf
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** Opacity of the rows of a locked group's permissions (all ticked, none editable). */
private const val LockedRowAlpha = 0.75f

/**
 * New group / edit one: Group details (name, description) and What they can do (permissions).
 * [givenAll] says how many people the draft would move to every property (shown as a notice;
 * the caller asks for confirmation before saving). A group that [canDelete] offers Delete at the end
 * ([onDelete] asks for confirmation); the list's group cards have no buttons of their own.
 */
@Composable
internal fun GroupSheet(
    initial: Group,
    problem: (Group) -> GroupProblem?,
    givenAll: (Group) -> Int,
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
    val named = name.isNotBlank()
    val affected = givenAll(draft)

    Box(Modifier.fillMaxSize().testTag(AccessTags.GROUP_SHEET)) {
        FormSheet(
            if (isNew) stringResource(R.string.access_new_group) else stringResource(R.string.access_edit_group, initial.name),
            when {
                locked -> stringResource(R.string.access_group_locked_sub, initial.name)
                isNew -> stringResource(R.string.access_group_new_sub)
                else -> stringResource(R.string.access_group_edit_sub)
            },
            stringResource(if (isNew) R.string.access_action_add_group else R.string.access_action_save),
            problem(draft) == null, error?.asString(), onClose,
            onPrimary = { error = onSave(draft)?.toUiText(name) },
            leadingIcon = iconOf(Permission.MANAGE_ACCESS.iconPath), progress = if (named) 1f else 0f,
            bodyBackground = C.Bg, bodyPadding = PaddingValues(vertical = 12.dp), bodySpacing = 12.dp,
            primaryModifier = Modifier.testTag(AccessTags.SHEET_PRIMARY),
            dirty = name.trim() != initial.name.trim() || description.trim() != initial.description.trim() || perms != initial.permissions,
        ) {
            FormGroup(stringResource(R.string.access_group_details), Icons.Users, trailing = {
                if (named) StatusPill(stringResource(R.string.property_ready), PillTone.READY)
                else StatusPill(stringResource(R.string.property_required_count, 1), PillTone.NEEDED)
            }) {
                InlineField(stringResource(R.string.access_field_name), required = true, dim = locked) {
                    InlineInput(
                        name, { name = it; error = null }, stringResource(R.string.access_group_name_hint),
                        Modifier.testTag(AccessTags.GROUP_NAME), enabled = !locked,
                    )
                }
                InlineField(stringResource(R.string.access_field_description)) {
                    InlineInput(description, { description = it }, stringResource(R.string.access_group_desc_hint))
                }
            }
            FormGroup(stringResource(R.string.access_what_they_can_do), Icons.ShieldCheck, trailing = {
                if (locked) StatusPill(stringResource(R.string.access_locked), PillTone.NEUTRAL)
                else StatusPill(stringResource(R.string.access_n_chosen, perms.size), PillTone.READY)
            }) {
                if (locked) {
                    Row(
                        Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LineIcon(Icons.Lock, 13.dp, C.TintText, 2.2f)
                        Txt(stringResource(R.string.access_locked_notice, initial.name), 11, 400, C.TintText, maxLines = 2)
                    }
                }
                Permission.entries.forEach { PermissionRow(it, on = locked || it in perms, locked = locked) { perms = if (it in perms) perms - it else perms + it } }
            }
            if (affected > 0) {
                Row(
                    Modifier.fillMaxWidth().testTag(AccessTags.ELEVATE_NOTICE).background(C.YTintBg).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LineIcon(Icons.Alert, 14.dp, C.TintText, 2.2f)
                    Txt(pluralStringResource(R.plurals.access_elevate_notice, affected, affected), 11, 500, C.TintText, maxLines = 3)
                }
            }
            if (!isNew && canDelete) {
                Txt(
                    stringResource(R.string.access_delete_group, initial.name), 13, 600, C.Red,
                    Modifier.testTag(AccessTags.DELETE_GROUP).padding(horizontal = 16.dp).clip(r(8))
                        .clickable(role = Role.Button) { onDelete(initial) }.heightIn(min = 48.dp).padding(vertical = 6.dp),
                )
            }
        }
    }
}

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
                LineIcon(iconOf(p.iconPath), 13.dp, p.color.asColor(), 1.9f)
                Txt(stringResource(p.titleRes()), 14, 600)
                if (p.needsAllProperties) TintChip(stringResource(R.string.access_needs_all))
            }
            Txt(stringResource(p.descriptionRes()), 11, 400, C.Muted, maxLines = 3, lineHeight = 15.sp)
        }
    }
}
