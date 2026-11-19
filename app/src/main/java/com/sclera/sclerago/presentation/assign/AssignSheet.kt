package com.sclera.sclerago.presentation.assign

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.presentation.components.BottomSheet
import com.sclera.sclerago.presentation.components.ChoiceList
import com.sclera.sclerago.presentation.components.GuardedClose
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.SearchField
import com.sclera.sclerago.presentation.components.SheetHeader
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** Test tags of the Assign asset sheet. */
object AssignTags {
    const val SHEET = "assign_sheet"
    const val CLEAR = "assign_clear"
    const val ADD = "assign_add"
}

/**
 * Assign asset, as the web's: the primary assignee (searched; each person by email, as the web lists them), Clear
 * while someone is picked, then Add. Adding with nobody picked takes the assets off whoever has them.
 */
@Composable
fun AssignSheet(s: AssignSheetState, vm: AssignViewModel, onSubmitted: () -> Unit) {
    GuardedClose(s.picked != null, vm::close) { close ->
        BottomSheet(close) {
            SheetHeader(stringResource(R.string.assign_title), null, close)
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).testTag(AssignTags.SHEET),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Txt(stringResource(R.string.assign_primary), 12, 600, C.Muted, Modifier.weight(1f))
                    if (s.picked != null) {
                        Txt(
                            stringResource(R.string.assign_clear), 12, 600, C.Yellow,
                            Modifier.testTag(AssignTags.CLEAR).clip(r(8)).clickable(role = Role.Button, onClick = vm::clear)
                                .heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 14.dp),
                        )
                    }
                }
                SearchField(s.query, vm::setQuery, stringResource(R.string.assign_select))
                // The one picked stays in view while the search lists others.
                val listed = s.people.map { it.email }.distinct()
                val shown = if (s.picked != null && s.picked !in listed) listOf(s.picked) + listed else listed
                when {
                    s.searching && shown.isEmpty() -> Txt(stringResource(R.string.assign_loading), 12, 400, C.Muted, Modifier.padding(8.dp))
                    shown.isEmpty() -> Txt(stringResource(R.string.assign_no_options), 12, 400, C.Muted, Modifier.padding(8.dp))
                }
                ChoiceList(shown, { it }, { it == s.picked }, vm::pick)
            }
            Column(Modifier.fillMaxWidth().topBorder(C.Border).padding(horizontal = 12.dp, vertical = 10.dp)) {
                Pill(
                    stringResource(R.string.assign_add), { if (vm.submit()) onSubmitted() },
                    Modifier.fillMaxWidth().testTag(AssignTags.ADD), bg = C.Yellow, fg = C.OnYellow, height = 44.dp, weight = 700,
                )
            }
        }
    }
}
