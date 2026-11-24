package com.sclera.sclerago.presentation.fileimport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.UNSURE_BELOW
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.model.reason
import com.sclera.sclerago.presentation.components.FieldLabel
import com.sclera.sclerago.presentation.components.FormSheet
import com.sclera.sclerago.presentation.components.SheetInput
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** Test tags of the draft's correction sheet. */
object DraftEditTags {
    fun field(id: String) = "draft-edit-field-$id"
}

/**
 * Corrects an asset read from a file before it is added, as the web's review table does: Name and the fields the
 * files gave values for; the values the AI was unsure of are marked. Save sends only what changed.
 */
@Composable
fun DraftEditSheet(vm: FileImportViewModel) {
    val s = vm.state
    val edit = s.edit ?: return
    val found = s.editing ?: return
    val draft = found.draft
    val dirty = s.columns.any { f -> edit.values[f.id].orEmpty().trim() != draft.values[f.id].orEmpty() }
    FormSheet(
        title = draft.name.ifBlank { found.file.name },
        sub = stringResource(R.string.file_import_from, listOfNotNull(found.file.name, draft.sourceRef?.takeIf { it.isNotBlank() }).joinToString(" · ")),
        primary = stringResource(if (edit.saving) R.string.file_import_saving else R.string.action_save),
        primaryEnabled = dirty && !edit.saving,
        error = edit.error?.asString(),
        onClose = vm::closeDraft,
        onPrimary = vm::saveDraft,
        dirty = dirty,
        // Saving a correction needs Collect, as the server: greyed out without it, a tap says why.
        primaryBlock = vm.collectBlock.reason(),
    ) {
        s.columns.forEach { f ->
            val confidence = draft.confidence[f.id]
            val unsure = !draft.values[f.id].isNullOrBlank() && confidence != null && confidence < UNSURE_BELOW
            Column(Modifier.testTag(DraftEditTags.field(f.id)), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel(f.name)
                    if (unsure) Txt(stringResource(R.string.file_import_unsure), 11, 600, C.Orange)
                }
                // Values read from files can be long (descriptions, notes): they wrap so all of the text shows.
                SheetInput(edit.values[f.id].orEmpty(), { vm.editValue(f.id, it) }, "", enabled = !edit.saving, multiLine = true)
            }
        }
        // What the file says about its type, and its notes (the web's review table shows both).
        when {
            draft.typeMatched == false -> Txt(stringResource(R.string.file_import_type_unmatched), 12, 500, C.Orange, maxLines = Int.MAX_VALUE)
            draft.typeNamed != null && draft.typeNamed != draft.type.joinToString(" › ") ->
                Txt(stringResource(R.string.file_import_type_named, draft.typeNamed), 12, 400, C.Muted, maxLines = Int.MAX_VALUE)
        }
        draft.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FieldLabel(stringResource(R.string.file_import_notes))
                Txt(notes, 13, 400, C.Soft, maxLines = Int.MAX_VALUE)
            }
        }
    }
}
