package com.sclera.sclerago.presentation.access

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.Group
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.Person
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.presentation.common.toUiText
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
    Person("u1", "dev.admin@sclera-adc.com", ADMINS_ID, true, lastSeenAt = SampleNow, fullName = "Dev Admin"),
    Person("u2", "ada.lovelace@sclera-adc.com", "g-collectors", false, setOf("p1"), lastSeenAt = SampleNow - 3_600_000),
)

internal val SamplePeopleList = AccessListState(
    tab = AccessTab.PEOPLE,
    people = SamplePeople.map { PersonRow(it, DEFAULT_GROUPS.first { g -> g.id == it.groupId }) },
    peopleTotal = SamplePeople.size,
    groupsTotal = DEFAULT_GROUPS.size,
    nowMillis = SampleNow,
    canAdd = true,
)

internal val SampleGroupsList = SamplePeopleList.copy(
    tab = AccessTab.GROUPS,
    groups = DEFAULT_GROUPS.map { GroupRow(it, if (it.id == ADMINS_ID) 1 else 0) },
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

/** First open with nothing saved: a loader (and no counts) until the server answers. */
@Preview(name = "Phone", device = PHONE)
@Preview(name = "Tablet portrait", device = TABLET_PORTRAIT)
@Preview(name = "Tablet landscape", device = TABLET_LANDSCAPE)
@Composable
private fun PeopleLoadingPreview() = Page(AccessListState(loading = true))

/** The server answered with no groups (the web shows an empty list too). */
@Preview(name = "Phone", device = PHONE)
@Preview(name = "Tablet portrait", device = TABLET_PORTRAIT)
@Preview(name = "Tablet landscape", device = TABLET_LANDSCAPE)
@Composable
private fun GroupsEmptyPreview() = Page(AccessListState(tab = AccessTab.GROUPS))

@Preview(name = "Phone", device = PHONE)
@Preview(name = "Tablet portrait", device = TABLET_PORTRAIT)
@Preview(name = "Tablet landscape", device = TABLET_LANDSCAPE)
@Composable
private fun AddPersonPreview() = ScleraTheme {
    PersonSheet(Person("", "", "g-collectors", false), DEFAULT_GROUPS, SampleProperties, { null }, { null }, {})
}

@Preview(name = "Phone", device = PHONE)
@Preview(name = "Tablet portrait", device = TABLET_PORTRAIT)
@Preview(name = "Tablet landscape", device = TABLET_LANDSCAPE)
@Composable
private fun EditPersonPreview() = ScleraTheme {
    PersonSheet(SamplePeople[1], DEFAULT_GROUPS, SampleProperties, { null }, { null }, {})
}

@Preview(name = "Phone", device = PHONE)
@Preview(name = "Tablet portrait", device = TABLET_PORTRAIT)
@Preview(name = "Tablet landscape", device = TABLET_LANDSCAPE)
@Composable
private fun NewGroupPreview() = ScleraTheme {
    GroupSheet(Group("", "", "", emptySet()), { null }, { null }, {})
}

/** An existing group that can be deleted: Delete shows at the end of the sheet. */
@Preview(name = "Phone", device = PHONE)
@Preview(name = "Tablet portrait", device = TABLET_PORTRAIT)
@Preview(name = "Tablet landscape", device = TABLET_LANDSCAPE)
@Composable
private fun EditGroupPreview() = ScleraTheme {
    GroupSheet(DEFAULT_GROUPS.first { it.id == "g-collectors" }, { null }, { null }, {}, canDelete = true)
}

@Preview(name = "Phone", device = PHONE)
@Preview(name = "Tablet portrait", device = TABLET_PORTRAIT)
@Preview(name = "Tablet landscape", device = TABLET_LANDSCAPE)
@Composable
private fun EditLockedGroupPreview() = ScleraTheme {
    GroupSheet(DEFAULT_GROUPS.first { it.locked }, { null }, { null }, {})
}

/** People and groups couldn't be read and nothing is saved: "Not available" and why, as the web shows it. */
@Preview(name = "Phone", device = PHONE)
@Preview(name = "Tablet portrait", device = TABLET_PORTRAIT)
@Preview(name = "Tablet landscape", device = TABLET_LANDSCAPE)
@Composable
private fun UnavailablePreview() = Page(AccessListState(unavailable = OnlineError.Offline.toUiText()))
