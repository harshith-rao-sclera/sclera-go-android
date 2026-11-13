package com.sclera.sclerago.presentation.access

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.sclera.sclerago.domain.model.Group
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.Person
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.presentation.theme.ScleraTheme

private const val PHONE = "spec:width=390dp,height=844dp"
private const val TABLET_PORTRAIT = "spec:width=800dp,height=1280dp,dpi=240"
private const val TABLET_LANDSCAPE = "spec:width=1280dp,height=800dp,dpi=240"

internal val SampleProperties = listOf(Property("p1", "Test 861", false), Property("p2", "Harbour Point Office Tower", false))

private const val ADMINS_ID = "g-admins"

// Sample groups for the previews only.
private val DEFAULT_GROUPS = listOf(
    Group(ADMINS_ID, "Admins", "Can do everything.", Permission.entries.toSet(), locked = true),
    Group("g-collectors", "Collectors", "Collect assets.", setOf(Permission.COLLECT)),
    Group("g-reviewers", "Reviewers", "Collect assets and validate them.", setOf(Permission.COLLECT, Permission.VALIDATE)),
    Group("g-viewers", "Viewers", "Look, but not change anything.", emptySet()),
)

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
)

@Composable
private fun Page(list: AccessListState) = ScleraTheme {
    PeopleContent(list, SampleProperties, "Test 861", {}, {}, {}, {}, {}, {}, {})
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

@Preview(name = "Phone", device = PHONE)
@Preview(name = "Tablet portrait", device = TABLET_PORTRAIT)
@Preview(name = "Tablet landscape", device = TABLET_LANDSCAPE)
@Composable
private fun EditLockedGroupPreview() = ScleraTheme {
    GroupSheet(DEFAULT_GROUPS.first { it.locked }, { null }, { 0 }, { null }, {})
}
