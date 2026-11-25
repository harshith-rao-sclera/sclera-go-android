package com.sclera.sclerago.presentation.assets

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.presentation.components.ConfirmDialog
import com.sclera.sclerago.presentation.components.DiscardChangesDialog
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.model.chipBg
import com.sclera.sclerago.presentation.theme.C

/**
 * The confirmation the asset details screen is asking ([modal]): submit (as an exception when incomplete), move to draft,
 * mark as validated, approve a change request, save with mandatory fields empty, delete, or discard; [onDone] closes it.
 */
@Composable
internal fun DetailModalDialog(modal: DetailModal, state: AssetDetailState, actions: AssetDetailActions, onDone: () -> Unit) {
    val isNew = state.isNew
    when (modal) {
        DetailModal.SUBMIT -> ConfirmDialog(
            icon = Icons.CheckCircle, iconBg = Status.SUBMITTED.chipBg, iconFg = C.Green,
            title = stringResource(R.string.detail_submit_title),
            body = stringResource(R.string.detail_submit_body),
            no = stringResource(R.string.action_keep_editing), yes = stringResource(R.string.action_submit), yesBg = C.Yellow,
            onNo = { onDone() },
            onYes = {
                onDone()
                if (isNew) actions.onCreate(true) else actions.onSubmit()
            },
        )
        // Incomplete: the server submits it as an exception, so say so (as the web's "missing mandatory fields" check).
        DetailModal.SUBMIT_EXCEPTION -> ConfirmDialog(
            icon = Icons.Alert, iconBg = C.WarnRow, iconFg = C.Orange,
            title = stringResource(R.string.detail_submit_exception_title),
            body = stringResource(R.string.detail_submit_exception_body),
            no = stringResource(R.string.action_keep_editing), yes = stringResource(R.string.action_submit), yesBg = C.Yellow,
            onNo = { onDone() },
            onYes = {
                onDone()
                if (isNew) actions.onCreate(true) else actions.onSubmit()
            },
        )
        // Back to draft (the web's dialog).
        DetailModal.MOVE_TO_DRAFT -> ConfirmDialog(
            icon = Icons.Refresh, iconBg = C.WarnRow, iconFg = C.Orange,
            title = stringResource(R.string.change_action_move_to_draft),
            body = stringResource(R.string.move_draft_body),
            no = stringResource(R.string.action_cancel), yes = stringResource(R.string.change_action_move_to_draft), yesBg = C.Yellow,
            onNo = { onDone() },
            onYes = {
                onDone()
                actions.onMoveToDraft()
            },
        )
        // A reviewer's sign-off without a validation run: say what happens first (the web's dialog).
        DetailModal.MARK_VALIDATED -> ConfirmDialog(
            icon = Icons.ShieldCheck, iconBg = Status.VALIDATED.chipBg, iconFg = C.Purple,
            title = stringResource(R.string.sign_off_action),
            body = pluralStringResource(R.plurals.sign_off_body, 1),
            no = stringResource(R.string.action_cancel), yes = stringResource(R.string.sign_off_action), yesBg = C.Yellow,
            onNo = { onDone() },
            onYes = {
                onDone()
                actions.onMarkValidated()
            },
        )
        // Approving a change request: back to Draft, with whoever asked (the web's dialog).
        DetailModal.APPROVE_REQUEST -> ConfirmDialog(
            icon = Icons.CheckCircle, iconBg = C.WarnRow, iconFg = C.Orange,
            title = stringResource(R.string.change_bulk_approve_title),
            body = pluralStringResource(
                R.plurals.change_bulk_approve_body, 1, 1, state.changeRequester ?: stringResource(R.string.change_whoever_asked),
            ),
            no = stringResource(R.string.action_cancel), yes = stringResource(R.string.change_request_reopen), yesBg = C.Yellow,
            onNo = { onDone() },
            onYes = {
                onDone()
                actions.changeRequest.onReopen()
            },
        )
        DetailModal.SAVE_MISSING -> ConfirmDialog(
            icon = Icons.Alert, iconBg = C.WarnRow, iconFg = C.Orange,
            title = stringResource(R.string.detail_save_missing_title),
            body = stringResource(R.string.detail_save_missing_body),
            no = stringResource(R.string.action_keep_editing), yes = stringResource(R.string.action_save), yesBg = C.Yellow,
            onNo = { onDone() },
            onYes = {
                onDone()
                actions.onSave()
            },
        )
        DetailModal.DELETE -> ConfirmDialog(
            icon = Icons.Trash, iconBg = C.DangerBg, iconFg = C.Red,
            title = stringResource(R.string.detail_delete_title),
            body = state.value("name").ifEmpty { null }?.let { stringResource(R.string.detail_delete_body, it) }
                ?: stringResource(R.string.detail_delete_body_unnamed),
            no = stringResource(R.string.action_cancel), yes = stringResource(R.string.action_delete),
            onNo = { onDone() },
            onYes = {
                onDone()
                actions.onDelete()
            },
        )
        DetailModal.DISCARD -> if (isNew) {
            ConfirmDialog(
                icon = Icons.Alert, iconBg = C.WarnRow, iconFg = C.Orange,
                title = stringResource(R.string.detail_discard_title),
                // With photos or videos taken for it, say they go too.
                body = stringResource(if (state.media.isNotEmpty()) R.string.detail_discard_photos_body else R.string.detail_discard_body),
                no = stringResource(R.string.action_keep_editing), yes = stringResource(R.string.action_discard),
                onNo = { onDone() },
                onYes = {
                    onDone()
                    actions.onBack()
                },
            )
        } else {
            DiscardChangesDialog(
                onKeep = { onDone() },
                onDiscard = {
                    onDone()
                    actions.onDiscardEdits()
                    actions.onBack()
                },
            )
        }
        DetailModal.NONE -> {}
    }
}
