package com.sclera.sclerago.presentation.access

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.sclera.sclerago.data.local.DEFAULT_GROUPS
import com.sclera.sclerago.domain.model.ADMINS_ID
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.Person
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.presentation.theme.ScleraTheme

private const val PHONE = "spec:width=390dp,height=844dp"
private const val TABLET_PORTRAIT = "spec:width=800dp,height=1280dp,dpi=240"
private const val TABLET_LANDSCAPE = "spec:width=1280dp,height=800dp,dpi=240"

internal val SampleProperties = listOf(Property("p1", "Test 861", false), Property("p2", "Harbour Point Office Tower", false))

private const val SampleNow = 1_700_000_000_000L

private val SamplePeople = listOf(
    Person("u1", "dev.admin@sclera-adc.com", ADMINS_ID, true, lastSeenAt = SampleNow),
    Person("u2", "ada.lovelace@sclera-adc.com", "g-collectors", false, setOf("p1"), lastSeenAt = SampleNow - 3_600_000),
)

internal val SamplePeopleList = AccessListState(
    tab = AccessTab.PEOPLE,
    people = SamplePeople.map { PersonRow(it, DEFAULT_GROUPS.first { g -> g.id == it.groupId }, it.id == "u1", it.lastSeenAt) },
    peopleTotal = SamplePeople.size,
    groupsTotal = DEFAULT_GROUPS.size,
    nowMillis = SampleNow,
)

internal val SampleGroupsList = SamplePeopleList.copy(
    tab = AccessTab.GROUPS,
    groups = DEFAULT_GROUPS.map { GroupRow(it, if (it.id == ADMINS_ID) 1 else 0, canDelete = !it.locked) },
    permissionCounts = Permission.entries.associateWith { p -> DEFAULT_GROUPS.count { it.can(p) } },
)

@Composable
private fun Page(list: AccessListState) = ScleraTheme {
    PeopleContent(list, SampleProperties, {}, {}, {}, {}, {}, {})
}

@Preview(name = "Phone", device = PHONE)
@Preview(name = "Tablet portrait", device = TABLET_PORTRAIT)
@Preview(name = "Tablet landscape", device = TABLET_LANDSCAPE)
@Composable
private fun PeoplePreview() = Page(SamplePeopleList)

@Preview(name = "Phone", device = PHONE)
@Preview(name = "Tablet portrait", device = TABLET_PORTRAIT)
@Preview(name = "Tablet landscape", device = TABLET_LANDSCAPE)
@Composable
private fun GroupsPreview() = Page(SampleGroupsList)

/** Two groups ticked for deleting: the dock (× · 2 selected · Select all 3 · Delete) replaces the buttons. */
@Preview(name = "Phone", device = PHONE)
@Preview(name = "Tablet portrait", device = TABLET_PORTRAIT)
@Preview(name = "Tablet landscape", device = TABLET_LANDSCAPE)
@Composable
private fun GroupsTickedPreview() = Page(SampleGroupsList.copy(pickedGroups = setOf("g-collectors", "g-reviewers"), selectableGroups = 3))

@Preview(name = "Phone", device = PHONE)
@Preview(name = "Tablet portrait", device = TABLET_PORTRAIT)
@Preview(name = "Tablet landscape", device = TABLET_LANDSCAPE)
@Composable
private fun AddPersonPreview() = ScleraTheme {
    PersonSheet(Person("", "", "g-collectors", false), DEFAULT_GROUPS, SampleProperties, false, { null }, { null }, {}, {})
}

@Preview(name = "Phone", device = PHONE)
@Preview(name = "Tablet portrait", device = TABLET_PORTRAIT)
@Preview(name = "Tablet landscape", device = TABLET_LANDSCAPE)
@Composable
private fun EditPersonPreview() = ScleraTheme {
    PersonSheet(SamplePeople[0], DEFAULT_GROUPS, SampleProperties, false, { null }, { null }, {}, {})
}

@Preview(name = "Phone", device = PHONE)
@Preview(name = "Tablet portrait", device = TABLET_PORTRAIT)
@Preview(name = "Tablet landscape", device = TABLET_LANDSCAPE)
@Composable
private fun NewGroupPreview() = ScleraTheme {
    GroupSheet(com.sclera.sclerago.domain.model.Group("", "", "", emptySet()), { null }, { 0 }, { null }, {})
}

/** An existing group that can be deleted: Delete shows at the end of the sheet. */
@Preview(name = "Phone", device = PHONE)
@Preview(name = "Tablet portrait", device = TABLET_PORTRAIT)
@Preview(name = "Tablet landscape", device = TABLET_LANDSCAPE)
@Composable
private fun EditGroupPreview() = ScleraTheme {
    GroupSheet(DEFAULT_GROUPS.first { it.id == "g-collectors" }, { null }, { 0 }, { null }, {}, canDelete = true)
}

@Preview(name = "Phone", device = PHONE)
@Preview(name = "Tablet portrait", device = TABLET_PORTRAIT)
@Preview(name = "Tablet landscape", device = TABLET_LANDSCAPE)
@Composable
private fun EditLockedGroupPreview() = ScleraTheme {
    GroupSheet(DEFAULT_GROUPS.first { it.locked }, { null }, { 0 }, { null }, {})
}
