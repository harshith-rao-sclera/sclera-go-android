package com.sclera.sclerago.presentation.validation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
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
import com.sclera.sclerago.domain.validation.JOBS_BY_ME
import com.sclera.sclerago.domain.validation.JobFilterOptions
import com.sclera.sclerago.domain.validation.JobFilters
import com.sclera.sclerago.domain.validation.JobStatusFilter
import com.sclera.sclerago.presentation.components.BottomSheet
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

/** Test tags of the Filter jobs sheet. */
object JobFilterTags {
    const val SHEET = "job_filter_sheet"
    const val RESET = "job_filter_reset"
    const val SHOW = "job_filter_show"
    fun person(id: String?) = "job_filter_person_${id ?: "all"}"
    fun choice(key: String) = "job_filter_choice_$key"
}

/** The side rail's groups of Filter jobs. */
private enum class JobFilterGroup { STATUS, CHECK }

/** One choice of a group: its [key] (for its tag), what it says, how many runs it would show, and whether it is picked. */
private class JobChoice(val key: String, val label: String, val count: Int, val on: Boolean, val pick: () -> Unit)

/**
 * Filter jobs, as the asset list's filter: who started the run (a row of people: All, Me, then the others, each with
 * how many runs), then Status and Check in a side rail with the open one's choices beside it (one each, with how many
 * runs it would show). Choices take effect with "Show N jobs"; Reset clears them in the sheet; closing drops them.
 */
@Composable
internal fun JobFilterSheet(
    draft: JobFilters,
    options: JobFilterOptions,
    count: (JobFilters) -> Int,
    onChange: (JobFilters.() -> JobFilters) -> Unit,
    onReset: () -> Unit,
    onShow: () -> Unit,
    onClose: () -> Unit,
) {
    var group by rememberSaveable { mutableStateOf(JobFilterGroup.STATUS) }
    BottomSheet(onClose) {
        Column(Modifier.testTag(JobFilterTags.SHEET)) {
            Header(onClose)
            Txt(stringResource(R.string.valt_jobs_started_by), 12, 600, C.Muted, Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 6.dp))
            StartedByRow(draft, options, count, onChange)
        }
        Row(Modifier.weight(1f, fill = false).fillMaxWidth().topBorder(C.Border)) {
            Rail(group, draft, Modifier.width(if (isTablet()) 170.dp else 116.dp)) { group = it }
            Choices(group, draft, options, count, onChange, Modifier.weight(1f))
        }
        val matching = count(draft)
        Row(
            Modifier.fillMaxWidth().topBorder(C.Border).padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill(stringResource(R.string.filter_reset), onReset, Modifier.testTag(JobFilterTags.RESET), height = 44.dp, hPad = 18.dp, size = 13, weight = 700)
            Pill(
                pluralStringResource(R.plurals.valt_jobs_show, matching, matching), onShow,
                Modifier.weight(1f).testTag(JobFilterTags.SHOW), bg = C.Yellow, fg = C.OnYellow, height = 44.dp, size = 13, weight = 700,
            )
        }
    }
}

/** The close button (a yellow ×) at the start and "Filter jobs" in the middle. */
@Composable
private fun Header(onClose: () -> Unit) {
    val close = stringResource(R.string.action_close)
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
        RoundButton(36.dp, C.Btn, onClose, Modifier.align(Alignment.CenterStart).semantics { contentDescription = close }) {
            LineIcon(Icons.Close, 15.dp, C.Yellow, 2.6f)
        }
        Txt(stringResource(R.string.valt_jobs_filter_title), 15, 700)
    }
}

/** Who started the run: All (anyone), Me when one of the runs is the user's, then the others; each with how many runs. */
@Composable
private fun StartedByRow(draft: JobFilters, options: JobFilterOptions, count: (JobFilters) -> Int, onChange: (JobFilters.() -> JobFilters) -> Unit) {
    val someone = stringResource(R.string.valt_jobs_someone)
    val me = stringResource(R.string.valt_jobs_me)
    LazyRow(
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(key = "all") {
            Starter(stringResource(R.string.valt_jobs_all), stringResource(R.string.valt_jobs_anyone), C.Muted, count(draft.copy(by = null)), draft.by == null, null) {
                onChange { copy(by = null) }
            }
        }
        if (options.mine) item(key = JOBS_BY_ME) {
            Starter(me, me, C.Yellow, count(draft.copy(by = JOBS_BY_ME)), draft.by == JOBS_BY_ME, JOBS_BY_ME) { onChange { copy(by = JOBS_BY_ME) } }
        }
        items(options.people, key = { it.id }) { p ->
            val name = p.name ?: someone
            Starter(initialsOf(name), name, personTint(p.id), count(draft.copy(by = p.id)), draft.by == p.id, p.id) { onChange { copy(by = p.id) } }
        }
    }
}

/** One starter: a round avatar ([mark] in it), ringed with a tick when picked; their name and how many runs. */
@Composable
private fun Starter(mark: String, name: String, tint: Color, runs: Int, on: Boolean, id: String?, onPick: () -> Unit) {
    Column(
        Modifier.width(64.dp).clip(r(10)).testTag(JobFilterTags.person(id)).selectable(on, role = Role.RadioButton, onClick = onPick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box {
            PersonAvatar(mark, tint, size = 44.dp, textSize = 14, ring = if (on) C.Yellow else null)
            if (on) {
                Box(
                    Modifier.align(Alignment.BottomEnd).size(16.dp).clip(CircleShape).background(C.Bg),
                    contentAlignment = Alignment.Center,
                ) { LineIcon(Icons.Check, 11.dp, C.Yellow, 3f) }
            }
        }
        Txt(name, 11, if (on) 700 else 500, if (on) C.Text else C.Soft, maxLines = 2, align = TextAlign.Center)
        Txt(runs.toString(), 10, 500, C.Muted)
    }
}

/** The side rail: Status and Check; the open one is raised, and one with a choice made shows a dot of 1. */
@Composable
private fun Rail(open: JobFilterGroup, draft: JobFilters, modifier: Modifier, onOpen: (JobFilterGroup) -> Unit) {
    Column(modifier.fillMaxHeight().endBorder(C.Border).padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        JobFilterGroup.entries.forEach { g ->
            val on = g == open
            val chosen = when (g) {
                JobFilterGroup.STATUS -> draft.status != null
                JobFilterGroup.CHECK -> draft.check != null
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(r(8)).background(if (on) C.Raised else Color.Transparent)
                    .selectable(on, role = Role.Tab) { onOpen(g) }.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Txt(
                    stringResource(if (g == JobFilterGroup.STATUS) R.string.valt_jobs_status else R.string.valt_jobs_check), 13,
                    if (on) 700 else 500, if (on) C.Text else C.Muted, Modifier.weight(1f), maxLines = 2,
                )
                if (chosen) {
                    Box(Modifier.size(16.dp).clip(r(8)).background(C.Yellow), contentAlignment = Alignment.Center) { Txt("1", 10, 700, C.OnYellow) }
                }
            }
        }
    }
}

/** The open group's choices, "Any …" first: one can be picked, each with how many runs it would show. */
@Composable
private fun Choices(
    group: JobFilterGroup,
    draft: JobFilters,
    options: JobFilterOptions,
    count: (JobFilters) -> Int,
    onChange: (JobFilters.() -> JobFilters) -> Unit,
    modifier: Modifier,
) {
    val choices = when (group) {
        JobFilterGroup.STATUS -> {
            val any = JobChoice("status_any", stringResource(R.string.valt_jobs_any_status), count(draft.copy(status = null)), draft.status == null) {
                onChange { copy(status = null) }
            }
            listOf(any) + listOf(
                JobStatusFilter.READY to R.string.valt_jobs_ready,
                JobStatusFilter.RUNNING to R.string.valt_jobs_running,
                JobStatusFilter.COMPLETED to R.string.valt_jobs_completed,
                JobStatusFilter.FAILED to R.string.valt_jobs_failed,
            ).map { (status, label) ->
                JobChoice("status_${status.name.lowercase()}", stringResource(label), count(draft.copy(status = status)), draft.status == status) {
                    onChange { copy(status = status) }
                }
            }
        }
        JobFilterGroup.CHECK -> {
            val any = JobChoice("check_any", stringResource(R.string.valt_jobs_any_check), count(draft.copy(check = null)), draft.check == null) {
                onChange { copy(check = null) }
            }
            listOf(any) + options.checks.map { name ->
                JobChoice("check_$name", name, count(draft.copy(check = name)), draft.check == name) { onChange { copy(check = name) } }
            }
        }
    }
    LazyColumn(modifier, contentPadding = PaddingValues(horizontal = 12.dp)) {
        items(choices, key = { it.key }) { c -> ChoiceRow(c) }
    }
}

/** A choice: a round mark (yellow with a tick when picked), its name, and how many runs it would show. */
@Composable
private fun ChoiceRow(c: JobChoice) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).bottomBorder(C.Border).testTag(JobFilterTags.choice(c.key))
            .selectable(c.on, role = Role.RadioButton, onClick = c.pick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (c.on) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(C.Yellow), contentAlignment = Alignment.Center) {
                LineIcon(Icons.Check, 13.dp, C.OnYellow, 3f)
            }
        } else {
            Box(Modifier.size(22.dp).border(2.dp, C.RadioOff, CircleShape))
        }
        Txt(c.label, 14, if (c.on) 700 else 500, modifier = Modifier.weight(1f), maxLines = 2)
        Txt(c.count.toString(), 12, 500, C.Muted)
    }
}
