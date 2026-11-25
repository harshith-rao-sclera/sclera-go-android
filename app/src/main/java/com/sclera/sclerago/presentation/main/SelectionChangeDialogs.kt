package com.sclera.sclerago.presentation.main

import com.sclera.sclerago.presentation.components.rememberAppear
import com.sclera.sclerago.presentation.components.scrim
import com.sclera.sclerago.presentation.components.popIn
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.sclera.sclerago.domain.usecase.access.ReviewerOnlyAction
import com.sclera.sclerago.domain.usecase.asset.ChangeAction
import com.sclera.sclerago.domain.usecase.asset.ChangeActionOption
import com.sclera.sclerago.presentation.assets.ChangeRequestDialog
import com.sclera.sclerago.presentation.assets.ChangeRequestLine
import com.sclera.sclerago.presentation.components.AccessGate
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
    if (state.changeMenu && state.changeOffered) {
        BackHandler(onBack = list::closeChange)
        ChangeMenu(state.changeOptions, state.reviewerOnly, count, onPick = list::pickChange, onCancel = list::closeChange)
    }
    when (val action = state.changeAction) {
        null -> Unit
        ChangeAction.REQUEST, ChangeAction.UPDATE_REQUEST -> {
            ChangeRequestDialog(
                note = state.changeNote, onNote = list::setChangeNote, onSend = list::applyChange, onCancel = list::closeChange,
                title = pluralStringResource(R.plurals.change_bulk_request_title, count, count),
                dirty = state.changeNoteChanged,
            )
        }
        ChangeAction.MARK_VALIDATED -> {
            BackHandler(onBack = list::closeChange)
            ChangeConfirm(
                title = stringResource(R.string.sign_off_action), body = pluralStringResource(R.plurals.sign_off_body, count),
                yes = changeActionLabel(action, count), requests = {}, onNo = list::closeChange, onYes = list::applyChange,
            )
        }
        ChangeAction.WITHDRAW -> {
            BackHandler(onBack = list::closeChange)
            ConfirmDialog(
                icon = Icons.Alert, iconBg = C.WarnRow, iconFg = C.Orange,
                title = pluralStringResource(R.plurals.change_bulk_withdraw_title, count,        count),
                body = stringResource(R.string.change_bulk_close_body),
                no = stringResource(R.string.action_cancel), yes = changeActionLabel(action, count), yesBg = C.Yellow,
                onNo = list::closeChange, onYes = list::applyChange,
            )
        }
        ChangeAction.APPROVE, ChangeAction.MOVE_TO_DRAFT, ChangeAction.DECLINE -> {
            BackHandler(onBack = list::closeChange)
            val assets = state.pickedAssets
            val (title, body) = when (action) {
                ChangeAction.APPROVE -> {
                    // Who the assets go to: the one person who asked, else "whoever asked" (as the web says it).
                    val askers = assets.mapNotNull { a -> a.changeRequest?.let { list.requesterName(it) } }.distinct()
                    val to = askers.singleOrNull() ?: stringResource(R.string.change_whoever_asked)
                    stringResource(R.string.change_bulk_approve_title) to pluralStringResource(R.plurals.change_bulk_approve_body, count, count, to)
                }
                ChangeAction.DECLINE ->
                    stringResource(R.string.change_bulk_decline_title) to pluralStringResource(R.plurals.change_bulk_decline_body, count)
                else ->
                    pluralStringResource(R.plurals.change_bulk_draft_title, count, count) to stringResource(R.string.change_bulk_draft_body)
            }
            // What was asked, so the admin reads it before acting: up to five requests, then how many more.
            val asked = assets.filter { it.changeRequest != null }
            ChangeConfirm(
                title = title, body = body, yes = changeActionLabel(action, count),
                requests = {
                    asked.take(MAX_REQUESTS_SHOWN).forEach { a ->
                        val request = checkNotNull(a.changeRequest)
                        if (count > 1) Txt(a.name, 12, 700, C.Text)
                        ChangeRequestLine(request, list.isMine(request), list.requesterName(request))
                    }
                    if (asked.size > MAX_REQUESTS_SHOWN) {
                        Txt(stringResource(R.string.change_requests_more, asked.size - MAX_REQUESTS_SHOWN), 12, 500, C.Muted, maxLines = Int.MAX_VALUE)
                    }
                },
                onNo = list::closeChange, onYes = list::applyChange,
            )
        }
    }
}

private const val MAX_REQUESTS_SHOWN = 5

/** A yes/no for a change-request action, with the requests it acts on listed under the explanation. */
@Composable
private fun ChangeConfirm(
    title: String,
    body: String,
    yes: String,
    requests: @Composable ColumnScope.() -> Unit,
    onNo: () -> Unit,
    onYes: () -> Unit,
) {
    val shown = rememberAppear()
    Box(Modifier.fillMaxSize().scrim(shown).noRippleClick(onNo).blockSwipe().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.popIn(shown).widthIn(max = 400.dp).fillMaxWidth().fabShadow(r(18), 16.dp).clip(r(18)).background(C.Surface)
                .border(1.dp, C.Border2, r(18)).noRippleClick { }.padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Txt(title, 16, 700)
            Txt(body, 13, 400, C.Muted, maxLines = Int.MAX_VALUE)
            requests()
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill(stringResource(R.string.action_cancel), onNo, Modifier.weight(1f), height = 48.dp, size = 14)
                Pill(yes, onYes, Modifier.weight(1f), bg = C.Yellow, fg = C.OnYellow, height = 48.dp, size = 14, weight = 700)
            }
        }
    }
}

/**
 * The actions offered for the selection, one per row; a blocked one is dimmed and says why when tapped. The reviewer-only
 * ones the user's group can't use ([reviewerOnly]) follow, greyed out by their access gate.
 */
@Composable
private fun ChangeMenu(
    options: List<ChangeActionOption>,
    reviewerOnly: List<ReviewerOnlyAction>,
    count: Int,
    onPick: (ChangeActionOption) -> Unit,
    onCancel: () -> Unit,
) {
    val shown = rememberAppear()
    Box(Modifier.fillMaxSize().scrim(shown).noRippleClick(onCancel).blockSwipe().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.popIn(shown).widthIn(max = 360.dp).fillMaxWidth().fabShadow(r(18), 16.dp).clip(r(18)).background(C.Surface)
                .border(1.dp, C.Border2, r(18)).noRippleClick { }.padding(20.dp).testTag(SelectionChangeTags.MENU),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // The dock's Change button: sign-off and change-request actions alike.
            Txt(stringResource(R.string.action_change), 16, 700)
            options.forEach { option ->
                // One the user's group can't use at all is greyed out by its access gate (a tap says why).
                AccessGate(option.access, Modifier.fillMaxWidth()) {
                    Pill(
                        changeActionLabel(option.action, count), { onPick(option) },
                        Modifier.fillMaxWidth().alpha(if (option.blocked != null && option.access == null) 0.4f else 1f)
                            .testTag(SelectionChangeTags.option(option.action)),
                        height = 48.dp, size = 14,
                    )
                }
            }
            reviewerOnly.forEach { locked ->
                AccessGate(locked.block, Modifier.fillMaxWidth()) {
                    Pill(
                        changeActionLabel(locked.action, count), {},
                        Modifier.fillMaxWidth().testTag(SelectionChangeTags.option(locked.action)), height = 48.dp, size = 14,
                    )
                }
            }
            Pill(stringResource(R.string.action_cancel), onCancel, Modifier.fillMaxWidth(), bg = C.Surface, height = 48.dp, size = 14)
        }
    }
}

/** The action's label as the web's: Approve / Decline say "requests" for several assets (also the asset bar's, for one). */
@Composable
internal fun changeActionLabel(action: ChangeAction, count: Int): String = when (action) {
    ChangeAction.MARK_VALIDATED -> stringResource(R.string.sign_off_action)
    ChangeAction.APPROVE -> pluralStringResource(R.plurals.change_action_approve, count)
    ChangeAction.DECLINE -> pluralStringResource(R.plurals.change_action_decline_all, count)
    ChangeAction.REQUEST -> stringResource(R.string.change_request_action)
    ChangeAction.UPDATE_REQUEST -> stringResource(R.string.change_action_update)
    ChangeAction.MOVE_TO_DRAFT -> stringResource(R.string.change_action_move_to_draft)
    ChangeAction.WITHDRAW -> stringResource(R.string.change_request_withdraw)
}
