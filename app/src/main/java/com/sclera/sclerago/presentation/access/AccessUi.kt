package com.sclera.sclerago.presentation.access

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Group
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.VIEW_ONLY_COLOR
import com.sclera.sclerago.domain.model.VIEW_ONLY_ICON
import com.sclera.sclerago.domain.usecase.access.GroupProblem
import com.sclera.sclerago.domain.usecase.access.PersonProblem
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.components.asColor
import com.sclera.sclerago.presentation.components.iconOf
import com.sclera.sclerago.presentation.icons.Ico

// How the domain's people-and-access types look and read: colours, icons and string resources.

/** Test tags of People and access. */
object AccessTags {
    const val LIST = "access_list"
    const val FAB_ADD = "access_add"
    const val EMPTY = "access_empty"
    const val TILE_PEOPLE = "access_tile_people"
    const val TILE_GROUPS = "access_tile_groups"
    const val TAB_PEOPLE = "access_tab_people"
    const val TAB_GROUPS = "access_tab_groups"
    const val PERSON_SHEET = "access_person_sheet"
    const val GROUP_SHEET = "access_group_sheet"
    const val SHEET_PRIMARY = "access_sheet_primary"
    const val EMAIL = "access_email_field"
    const val GROUP_NAME = "access_group_name"
    const val PERSON_PILL = "access_person_pill"
    const val REMOVE = "access_remove_person"
    const val ELEVATE_NOTICE = "access_elevate_notice"
    fun person(id: String) = "access_person_$id"
    fun group(id: String) = "access_group_$id"
    fun editGroup(id: String) = "access_group_edit_$id"
    fun deleteGroup(id: String) = "access_group_delete_$id"
    fun permission(p: Permission) = "access_permission_${p.name}"
}

internal fun Group.tint(): Color = topPermission?.color?.asColor() ?: VIEW_ONLY_COLOR.asColor()
internal fun Group.icon(): Ico = iconOf(topPermission?.iconPath ?: VIEW_ONLY_ICON)

@StringRes
internal fun Permission.titleRes(): Int = when (this) {
    Permission.COLLECT -> R.string.access_perm_collect
    Permission.VALIDATE -> R.string.access_perm_validate
    Permission.CONFIGURE -> R.string.access_perm_configure
    Permission.MANAGE_PROPERTIES -> R.string.access_perm_manage_properties
    Permission.MANAGE_ACCESS -> R.string.access_perm_manage_access
}

@StringRes
internal fun Permission.descriptionRes(): Int = when (this) {
    Permission.COLLECT -> R.string.access_perm_collect_desc
    Permission.VALIDATE -> R.string.access_perm_validate_desc
    Permission.CONFIGURE -> R.string.access_perm_configure_desc
    Permission.MANAGE_PROPERTIES -> R.string.access_perm_manage_properties_desc
    Permission.MANAGE_ACCESS -> R.string.access_perm_manage_access_desc
}

/** The message for a person problem; [email] and [groupName] fill the ones that name them. */
internal fun PersonProblem.toUiText(email: String, groupName: String): UiText = when (this) {
    PersonProblem.EMAIL_REQUIRED -> UiText.Res(R.string.access_problem_email_required)
    PersonProblem.EMAIL_INVALID -> UiText.Res(R.string.access_problem_email_invalid)
    PersonProblem.EMAIL_TAKEN -> UiText.Res(R.string.access_problem_email_taken, listOf(email.trim()))
    PersonProblem.GROUP_REQUIRED -> UiText.Res(R.string.access_problem_group_required)
    PersonProblem.GROUP_NEEDS_ALL_PROPERTIES -> UiText.Res(R.string.access_forced_all, listOf(groupName))
    PersonProblem.NO_PROPERTY -> UiText.Res(R.string.access_problem_no_property)
}

internal fun GroupProblem.toUiText(name: String): UiText = when (this) {
    GroupProblem.NAME_REQUIRED -> UiText.Res(R.string.access_problem_group_name)
    GroupProblem.NAME_TAKEN -> UiText.Res(R.string.access_problem_group_taken, listOf(name.trim()))
}

/** "DA" for dev.admin (first and last part of the name), "?" when there is nothing to show. */
internal fun initialsOf(name: String): String {
    val parts = name.split(' ', '.', '_', '-').filter { it.isNotBlank() }
    return listOfNotNull(parts.firstOrNull(), parts.drop(1).lastOrNull()).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
}
