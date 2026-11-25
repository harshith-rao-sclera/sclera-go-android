package com.sclera.sclerago.presentation.assign

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.presentation.components.SkeletonLead
import com.sclera.sclerago.presentation.components.SkeletonList
import com.sclera.sclerago.presentation.components.BottomSheet
import com.sclera.sclerago.presentation.components.LoadFailedNote
import com.sclera.sclerago.presentation.components.ChoiceList
import com.sclera.sclerago.presentation.components.GuardedClose
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.SearchField
import com.sclera.sclerago.presentation.components.SheetHeader
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** Test tags of the Assign asset sheet. */
object AssignTags {
    const val SHEET = "assign_sheet"
    const val APPLY = "assign_apply"
}

/**
 * Assign asset, as the web's: the assignee is picked from "Nobody (unassigned)", "Assign to me (email)" and the people
 * searched (each by email, as the web lists them). Apply works once a choice is made. With one asset it opens on that
 * asset's assignee.
 */
@Composable
fun AssignSheet(s: AssignSheetState, vm: AssignViewModel, onSubmitted: () -> Unit) {
    GuardedClose(s.changed, vm::close) { close ->
        BottomSheet(close) {
            SheetHeader(stringResource(R.string.assign_title), null, close, titleSize = 20)
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).testTag(AssignTags.SHEET),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Txt(stringResource(R.string.assign_assignee), 12, 600, C.Muted)
                SearchField(s.query, vm::setQuery, stringResource(R.string.assign_select))
                val nobody = stringResource(R.string.assign_nobody)
                val meLabel = s.me?.let { stringResource(R.string.assign_to_me, it) }
                // While a search runs, the last search's people (which may not match any more) give way to placeholders.
                val listed = if (s.searching) emptyList() else s.people.map { it.email }.filter { !it.isSame(s.me) }.distinctBy { it.lowercase() }
                // The one picked stays in view while the search lists others.
                val pickedEmail = (s.picked as? AssignChoice.Person)?.email
                val kept = pickedEmail?.takeIf { p -> !p.isSame(s.me) && listed.none { it.isSame(p) } }
                val choices = buildList<AssignChoice> {
                    add(AssignChoice.Nobody)
                    s.me?.let { add(AssignChoice.Person(it)) }
                    kept?.let { add(AssignChoice.Person(it)) }
                    listed.forEach { add(AssignChoice.Person(it)) }
                }
                ChoiceList(
                    choices,
                    { c -> if (c is AssignChoice.Person) (if (c.email.isSame(s.me)) meLabel ?: c.email else c.email) else nobody },
                    { c -> c.isSame(s.picked) },
                    vm::pick,
                )
                when {
                    s.searching -> SkeletonList(stringResource(R.string.assign_loading), count = 4, lead = SkeletonLead.NONE, lines = 1, card = false, gap = 2.dp)
                    s.searchFailed -> LoadFailedNote(stringResource(R.string.assign_load_failed), vm::retrySearch)
                    listed.isEmpty() && s.query.isNotBlank() -> Txt(stringResource(R.string.assign_no_options), 12, 400, C.Muted, Modifier.padding(8.dp), maxLines = Int.MAX_VALUE)
                }
            }
            Column(Modifier.fillMaxWidth().topBorder(C.Border).padding(horizontal = 12.dp, vertical = 10.dp)) {
                val ok = s.canApply
                Pill(
                    stringResource(R.string.assign_apply), { if (vm.submit()) onSubmitted() },
                    Modifier.fillMaxWidth().alpha(if (ok) 1f else 0.4f).semantics { if (!ok) disabled() }.testTag(AssignTags.APPLY),
                    bg = C.Yellow, fg = C.OnYellow, height = 48.dp, weight = 700,
                )
            }
        }
    }
}

private fun String.isSame(other: String?): Boolean = other != null && equals(other, ignoreCase = true)

private fun AssignChoice.isSame(other: AssignChoice?): Boolean = when (this) {
    AssignChoice.Nobody -> other == AssignChoice.Nobody
    is AssignChoice.Person -> other is AssignChoice.Person && email.isSame(other.email)
}
