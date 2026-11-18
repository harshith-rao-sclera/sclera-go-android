package com.sclera.sclerago.presentation.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.usecase.asset.ChangeAction
import com.sclera.sclerago.domain.usecase.asset.ChangeActionOption
import com.sclera.sclerago.presentation.assets.ChangeRequestDialog
import com.sclera.sclerago.presentation.components.ConfirmDialog
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.blockSwipe
import com.sclera.sclerago.presentation.components.fabShadow
import com.sclera.sclerago.presentation.components.noRippleClick
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** Test tags of the selection's change-request dialogs. */
object SelectionChangeTags {
    const val MENU = "selection-change-menu"
    fun option(action: ChangeAction) = "selection-change-${action.name}"
}

/**
 * The change-request actions on the selected assets (the web's selection bar): the menu of what can be done, then the
 * confirmation of the one picked: the note to send for a request, or a yes for moving to draft, declining or withdrawing.
 */
@Composable
internal fun SelectionChangeDialogs(state: AssetListState, list: AssetListViewModel) {
    val count = state.pickedAssets.size
    if (state.changeMenu && state.changeOptions.isNotEmpty()) {
        BackHandler(onBack = list::closeChange)
        ChangeMenu(state.changeOptions, onPick = list::pickChange, onCancel = list::closeChange)
    }
    when (val action = state.changeAction) {
        null -> Unit
        ChangeAction.REQUEST, ChangeAction.UPDATE_REQUEST -> {
            BackHandler(onBack = list::closeChange)
            ChangeRequestDialog(
                note = state.changeNote, onNote = list::setChangeNote, onSend = list::applyChange, onCancel = list::closeChange,
                title = pluralStringResource(R.plurals.change_bulk_request_title, count, count),
            )
        }
        ChangeAction.MOVE_TO_DRAFT, ChangeAction.DECLINE, ChangeAction.WITHDRAW -> {
            BackHandler(onBack = list::closeChange)
            val (title, body) = when (action) {
                ChangeAction.MOVE_TO_DRAFT ->
                    pluralStringResource(R.plurals.change_bulk_draft_title, count, count) to stringResource(R.string.change_bulk_draft_body)
                ChangeAction.DECLINE ->
                    pluralStringResource(R.plurals.change_bulk_decline_title, count, count) to stringResource(R.string.change_bulk_close_body)
                else ->
                    pluralStringResource(R.plurals.change_bulk_withdraw_title, count, count) to stringResource(R.string.change_bulk_close_body)
            }
            ConfirmDialog(
                icon = Icons.Alert, iconBg = C.WarnRow, iconFg = C.Orange,
                title = title, body = body,
                no = stringResource(R.string.action_cancel), yes = optionLabel(action), yesBg = C.Yellow,
                onNo = list::closeChange, onYes = list::applyChange,
            )
        }
    }
}

/** The actions offered for the selection, one per row; a blocked one is dimmed and says why when tapped. */
@Composable
private fun ChangeMenu(options: List<ChangeActionOption>, onPick: (ChangeActionOption) -> Unit, onCancel: () -> Unit) {
    Box(Modifier.fillMaxSize().background(C.Scrim).noRippleClick(onCancel).blockSwipe().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 360.dp).fillMaxWidth().fabShadow(r(18), 16.dp).clip(r(18)).background(C.Surface)
                .border(1.dp, C.Border2, r(18)).noRippleClick { }.padding(20.dp).testTag(SelectionChangeTags.MENU),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Txt(stringResource(R.string.dash_chip_change_requests), 16, 700)
            options.forEach { option ->
                Pill(
                    optionLabel(option.action), { onPick(option) },
                    Modifier.fillMaxWidth().alpha(if (option.blocked != null) 0.4f else 1f).testTag(SelectionChangeTags.option(option.action)),
                    height = 48.dp, size = 14,
                )
            }
            Pill(stringResource(R.string.action_cancel), onCancel, Modifier.fillMaxWidth(), bg = C.Surface, height = 48.dp, size = 14)
        }
    }
}

@Composable
private fun optionLabel(action: ChangeAction): String = stringResource(
    when (action) {
        ChangeAction.REQUEST -> R.string.change_request_action
        ChangeAction.UPDATE_REQUEST -> R.string.change_action_update
        ChangeAction.MOVE_TO_DRAFT -> R.string.change_action_move_to_draft
        ChangeAction.DECLINE -> R.string.change_action_decline
        ChangeAction.WITHDRAW -> R.string.change_request_withdraw
    },
)
