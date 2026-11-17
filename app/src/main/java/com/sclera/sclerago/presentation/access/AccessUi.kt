package com.sclera.sclerago.presentation.access

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Group
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.PermissionInfo
import com.sclera.sclerago.domain.model.VIEW_ONLY_COLOR
import com.sclera.sclerago.domain.usecase.access.GroupProblem
import com.sclera.sclerago.domain.usecase.access.PersonProblem
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.components.asColor

// How the domain's people-and-access types look and read: colours, icons and string resources.

/** Test tags of People and access. */
object AccessTags {
    const val LIST = "access_list"
    const val FAB_ADD = "access_add"
    const val EMPTY = "access_empty"
    const val LOADING = "access_loading"
    const val TAB_PEOPLE = "access_tab_people"
    const val TAB_GROUPS = "access_tab_groups"
    const val PERSON_SHEET = "access_person_sheet"
    const val GROUP_SHEET = "access_group_sheet"
    const val SHEET_PRIMARY = "access_sheet_primary"
    const val EMAIL = "access_email_field"
    const val GROUP_NAME = "access_group_name"
    const val UNAVAILABLE = "access_unavailable"
    const val LOADING_USERS = "access_loading_users"
    const val DELETE_GROUP = "access_delete_group"
    fun person(id: String) = "access_person_$id"
    fun group(id: String) = "access_group_$id"
    fun suggestion(email: String) = "access_suggestion_$email"
    fun directory(email: String) = "access_directory_$email"
    fun permission(p: Permission) = "access_permission_${p.name}"
}

internal fun Group.tint(): Color = topPermission?.color?.asColor() ?: VIEW_ONLY_COLOR.asColor()

/** The server's permissions by key, for the names and descriptions below (empty until they are loaded). */
internal val LocalPermissionInfo = staticCompositionLocalOf<Map<String, PermissionInfo>> { emptyMap() }

/** The permission's name as the server words it; the app's own until the server's are loaded. */
@Composable
internal fun Permission.title(): String =
    LocalPermissionInfo.current[key]?.name?.takeIf { it.isNotBlank() } ?: stringResource(titleRes())

/** What the permission lets people do, as the server words it; the app's own until the server's are loaded. */
@Composable
internal fun Permission.description(): String =
    LocalPermissionInfo.current[key]?.description?.takeIf { it.isNotBlank() } ?: stringResource(descriptionRes())

/** Whether the permission only works with every property, as the server says (the app's own until loaded). */
@Composable
internal fun Permission.needsAll(): Boolean = LocalPermissionInfo.current[key]?.needsAllProperties ?: needsAllProperties

/** The permissions a group can be given, in the server's order (the app's own order until the server's are loaded). */
@Composable
internal fun orderedPermissions(): List<Permission> {
    val keys = LocalPermissionInfo.current.keys.toList()
    return if (keys.isEmpty()) Permission.entries else Permission.entries.sortedBy { p -> keys.indexOf(p.key).let { if (it < 0) Int.MAX_VALUE else it } }
}

@StringRes
private fun Permission.titleRes(): Int = when (this) {
    Permission.COLLECT -> R.string.access_perm_collect
    Permission.CHECK -> R.string.access_perm_check
    Permission.VALIDATE -> R.string.access_perm_validate
    Permission.CONFIGURE -> R.string.access_perm_configure
    Permission.MANAGE_PROPERTIES -> R.string.access_perm_manage_properties
    Permission.MANAGE_ACCESS -> R.string.access_perm_manage_access
}

@StringRes
private fun Permission.descriptionRes(): Int = when (this) {
    Permission.COLLECT -> R.string.access_perm_collect_desc
    Permission.CHECK -> R.string.access_perm_check_desc
    Permission.VALIDATE -> R.string.access_perm_validate_desc
    Permission.CONFIGURE -> R.string.access_perm_configure_desc
    Permission.MANAGE_PROPERTIES -> R.string.access_perm_manage_properties_desc
    Permission.MANAGE_ACCESS -> R.string.access_perm_manage_access_desc
}

internal fun PersonProblem.toUiText(): UiText = when (this) {
    PersonProblem.EMAIL_REQUIRED -> UiText.Res(R.string.access_problem_email_required)
    PersonProblem.GROUP_REQUIRED -> UiText.Res(R.string.access_problem_group_required)
}

internal fun GroupProblem.toUiText(): UiText = when (this) {
    GroupProblem.NAME_REQUIRED -> UiText.Res(R.string.access_problem_group_name)
}

/** "DA" for dev.admin (first and last part of the name), "?" when there is nothing to show. */
internal fun initialsOf(name: String): String {
    val parts = name.split(' ', '.', '_', '-').filter { it.isNotBlank() }
    return listOfNotNull(parts.firstOrNull(), parts.drop(1).lastOrNull()).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
}
