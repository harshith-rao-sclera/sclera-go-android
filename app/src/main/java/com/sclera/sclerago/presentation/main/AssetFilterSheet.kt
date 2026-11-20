package com.sclera.sclerago.presentation.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.AssetFilter
import com.sclera.sclerago.domain.model.AssetFilterOptions
import com.sclera.sclerago.domain.model.FilterCategory
import com.sclera.sclerago.domain.model.FilterChoice
import com.sclera.sclerago.domain.model.FilterPerson
import com.sclera.sclerago.presentation.components.BottomSheet
import com.sclera.sclerago.presentation.components.CheckBox
import com.sclera.sclerago.presentation.components.PersonAvatar
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.RoundButton
import com.sclera.sclerago.presentation.components.bottomBorder
import com.sclera.sclerago.presentation.components.endBorder
import com.sclera.sclerago.presentation.components.initialsOf
import com.sclera.sclerago.presentation.components.isTablet
import com.sclera.sclerago.presentation.components.personTint
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** Test tags of the filter sheet. */
object FilterTags {
    const val SHEET = "filter_sheet"
    const val RESET = "filter_reset"
    const val SHOW = "filter_show"
    fun person(userId: String) = "filter_person_$userId"
    fun category(c: FilterCategory) = "filter_category_${c.name.lowercase()}"
    fun choice(key: String) = "filter_choice_$key"
}

/** The key of the one choice under "Other": assets with no type. */
private const val UNTYPED_KEY = "untyped"

/**
 * The asset list's filter: who the assets are assigned to (a row of people, the signed-in user first as "You"), then the
 * type levels in a side rail (System type, Asset type, Sub type, Other) with the chosen level's choices beside it. Choices
 * only take effect with "Show N assets"; Reset clears them in the sheet; closing drops them.
 */
@Composable
fun AssetFilterSheet(
    s: FilterSheetState,
    onClose: () -> Unit,
    onPerson: (String) -> Unit,
    onToggle: (FilterCategory, String) -> Unit,
    onReset: () -> Unit,
    onShow: () -> Unit,
) {
    var category by rememberSaveable { mutableStateOf(FilterCategory.SYSTEM) }
    BottomSheet(onClose) {
        Column(Modifier.testTag(FilterTags.SHEET)) {
            FilterHeader(onClose)
            if (s.options.people.isNotEmpty()) PeopleRow(s.options.people, onPerson)
        }
        Row(Modifier.weight(1f, fill = false).fillMaxWidth().topBorder(C.Border)) {
            CategoryRail(category, s.draft, Modifier.width(if (isTablet()) 170.dp else 116.dp)) { category = it }
            ChoiceList(category, s.options, Modifier.weight(1f)) { onToggle(category, it) }
        }
        Row(
            Modifier.fillMaxWidth().topBorder(C.Border).padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill(stringResource(R.string.filter_reset), onReset, Modifier.testTag(FilterTags.RESET), height = 40.dp, hPad = 18.dp, size = 13, weight = 700)
            Pill(
                pluralStringResource(R.plurals.filter_show, s.matching, s.matching), onShow,
                Modifier.weight(1f).testTag(FilterTags.SHOW), bg = C.Yellow, fg = C.OnYellow, height = 40.dp, size = 13, weight = 700,
            )
        }
    }
}

/** The close button (a yellow ×) at the start and "Filter" in the middle. */
@Composable
private fun FilterHeader(onClose: () -> Unit) {
    val close = stringResource(R.string.action_close)
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
        RoundButton(36.dp, C.Btn, onClose, Modifier.align(Alignment.CenterStart).semantics { contentDescription = close }) {
            LineIcon(Icons.Close, 15.dp, C.Yellow, 2.6f)
        }
        Txt(stringResource(R.string.filter_title), 15, 700)
    }
}

/** "Assigned to" and a row of the people with assets here: initials, name and how many each has; a tap picks or un-picks. */
@Composable
private fun PeopleRow(people: List<FilterPerson>, onPerson: (String) -> Unit) {
    Txt(stringResource(R.string.filter_assigned_to), 12, 600, C.Muted, Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp))
    LazyRow(
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(people, key = { it.userId }) { p -> PersonChoice(p) { onPerson(p.userId) } }
    }
}

@Composable
private fun PersonChoice(p: FilterPerson, onClick: () -> Unit) {
    val name = when {
        p.me -> stringResource(R.string.filter_you)
        else -> p.name ?: stringResource(R.string.filter_someone)
    }
    val initials = if (p.me) stringResource(R.string.filter_me_initials) else p.name?.let(::initialsOf) ?: "?"
    val tint = if (p.me) C.Yellow else personTint(p.userId)
    Column(
        Modifier.width(60.dp).clip(r(10)).testTag(FilterTags.person(p.userId))
            .toggleable(p.selected, role = Role.Checkbox) { onClick() }.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        PersonAvatar(initials, tint, size = 40.dp, textSize = 13, ring = if (p.selected) C.Yellow else null)
        Txt(name, 11, if (p.selected) 700 else 500, if (p.selected) C.Text else C.Soft, maxLines = 1, align = TextAlign.Center)
        Txt(p.count.toString(), 10, 500, C.Muted)
    }
}

/** The side rail: one row per type level; the open one is raised, and a level with choices made shows how many. */
@Composable
private fun CategoryRail(open: FilterCategory, draft: AssetFilter, modifier: Modifier, onOpen: (FilterCategory) -> Unit) {
    Column(modifier.fillMaxHeight().endBorder(C.Border).padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        FilterCategory.entries.forEach { c ->
            val chosen = when (c) {
                FilterCategory.SYSTEM -> draft.systems.size
                FilterCategory.TYPE -> draft.types.size
                FilterCategory.SUBTYPE -> draft.subtypes.size
                FilterCategory.OTHER -> if (draft.untyped) 1 else 0
            }
            val on = c == open
            Row(
                Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(r(8)).background(if (on) C.Raised else Color.Transparent)
                    .testTag(FilterTags.category(c)).selectable(on, role = Role.Tab) { onOpen(c) }.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Txt(stringResource(c.label()), 13, if (on) 700 else 500, if (on) C.Text else C.Muted, Modifier.weight(1f), maxLines = 2)
                if (chosen > 0) {
                    Box(Modifier.size(16.dp).clip(r(8)).background(C.Yellow), contentAlignment = Alignment.Center) {
                        Txt(chosen.toString(), 10, 700, C.OnYellow)
                    }
                }
            }
        }
    }
}

private fun FilterCategory.label(): Int = when (this) {
    FilterCategory.SYSTEM -> R.string.filter_system_type
    FilterCategory.TYPE -> R.string.filter_asset_type
    FilterCategory.SUBTYPE -> R.string.filter_sub_type
    FilterCategory.OTHER -> R.string.filter_other
}

/** The open level's choices, each a checkbox row with how many assets it would show. */
@Composable
private fun ChoiceList(category: FilterCategory, options: AssetFilterOptions, modifier: Modifier, onToggle: (String) -> Unit) {
    val choices = when (category) {
        FilterCategory.SYSTEM -> options.systems
        FilterCategory.TYPE -> options.types
        FilterCategory.SUBTYPE -> options.subtypes
        FilterCategory.OTHER -> listOf(
            FilterChoice(UNTYPED_KEY, stringResource(R.string.filter_no_type), null, options.untypedCount, options.untypedSelected),
        ).filter { it.count > 0 || it.selected }
    }
    if (choices.isEmpty()) {
        Box(modifier.padding(16.dp)) { Txt(stringResource(R.string.filter_nothing), 12, 400, C.Muted, maxLines = 3) }
        return
    }
    LazyColumn(modifier, contentPadding = PaddingValues(horizontal = 10.dp)) {
        items(choices, key = { it.key }) { c -> ChoiceRow(c) { onToggle(c.key) } }
    }
}

@Composable
private fun ChoiceRow(c: FilterChoice, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).bottomBorder(C.Border).testTag(FilterTags.choice(c.key))
            .toggleable(c.selected, role = Role.Checkbox) { onClick() }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CheckBox(c.selected, r(4), offColor = C.RadioOff, checkSize = 10.dp, size = 16.dp, stroke = 1.5.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Txt(c.label, 13, 500, maxLines = 2)
            c.parent?.let { Txt(it, 11, 400, C.Muted, maxLines = 1) }
        }
        Txt(c.count.toString(), 12, 500, C.Muted)
    }
}
