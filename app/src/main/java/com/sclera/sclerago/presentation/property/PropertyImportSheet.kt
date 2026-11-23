package com.sclera.sclerago.presentation.property

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.ImportPlan
import com.sclera.sclerago.domain.model.ImportTarget
import com.sclera.sclerago.domain.model.MatchHow
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.components.BottomSheet
import com.sclera.sclerago.presentation.components.ChoiceSheet
import com.sclera.sclerago.presentation.components.GuardedClose
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.RoundButton
import com.sclera.sclerago.presentation.components.TintChip
import com.sclera.sclerago.presentation.components.dashedBorder
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import com.sclera.sclerago.services.media.formatSize

internal const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

/** Test tags of the Import sheet. */
object ImportTags {
    const val DROP_ZONE = "import_drop_zone"
    const val PROCEED = "import_proceed"
    const val ERROR = "import_error"
    const val IMPORT = "import_import"
    const val LOADING = "import_loading"
    fun target(key: String) = "import_target_$key"
}

/**
 * Import, as the web's on the new API: choose an .xlsx (at most 100MB; Sample Format downloads the server's sample),
 * Proceed (the server reads it and proposes the column each detail is read from), then "Mapping Data": check or change
 * each detail's column (the name needs one) and Import. [onImported] closes it once the rows are in.
 */
@Composable
internal fun ImportSheet(vm: PropertyImportViewModel, onClose: () -> Unit, onImported: () -> Unit) {
    val context = LocalContext.current
    val s = vm.state
    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pick(pickedSheet(context, uri))
    }
    val sampleSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX_MIME)) { uri ->
        if (uri != null) vm.saveSample(uri.toString())
    }
    val plan = s.plan
    GuardedClose(s.file != null, onClose) { close ->
        if (plan == null) {
            ImportSheetContent(
                s.file?.name, s.file?.size ?: -1, s.error, close,
                onChoose = { chooser.launch(IMPORT_PICK_TYPES) },
                onSample = { sampleSaver.launch(SAMPLE_NAME) },
                onProceed = vm::proceed,
                loading = s.loading,
            )
        } else {
            MappingSheet(plan, s.mapping, s.incomplete, s.loading, close, vm::map) { vm.import(onImported) }
        }
    }
}

/** The name the sample is saved under, as the web's download. */
internal const val SAMPLE_NAME = "Sample Format.xlsx"

/** The spreadsheet types the file picker offers for an import (.xlsx; some providers only say octet-stream). */
internal val IMPORT_PICK_TYPES = arrayOf(XLSX_MIME, "application/octet-stream")

/** The file picked at [uri], with the name and size its provider reports (checked by the caller, not trusted). */
internal fun pickedSheet(context: Context, uri: Uri): PickedSheet = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst()) PickedSheet(uri.toString(), c.getString(0) ?: "file", if (c.isNull(1)) -1 else c.getLong(1)) else null
    }
}.getOrNull() ?: PickedSheet(uri.toString(), uri.lastPathSegment ?: "file", -1)

/**
 * The first step (design "Import"): drop zone, limits, sample card and the action ([actionLabel]: Proceed, or Import
 * when there is no mapping step); a loader while the server reads the file.
 */
@Composable
internal fun ImportSheetContent(
    fileName: String?,
    fileSize: Long,
    error: UiText?,
    onClose: () -> Unit,
    onChoose: () -> Unit,
    onSample: () -> Unit,
    onProceed: () -> Unit,
    loading: Boolean = false,
    actionLabel: String = stringResource(R.string.import_proceed),
) {
    val downloadLabel = stringResource(R.string.import_sample_download)
    BottomSheet(onClose, maxHeightFraction = 0.6f, background = C.SheetBg) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SheetTitle(stringResource(R.string.import_title), onClose)
            if (loading) {
                Loader()
                return@Column
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 110.dp).clip(r(16)).background(C.Bg)
                        .dashedBorder(if (fileName != null) C.Yellow else C.Border2, 1.5.dp, 16.dp)
                        .testTag(ImportTags.DROP_ZONE).clickable(role = Role.Button, onClick = onChoose).padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(C.YTintBg), contentAlignment = Alignment.Center) {
                        LineIcon(Icons.Upload, 22.dp, C.Yellow, 2f)
                    }
                    if (fileName == null) Txt(stringResource(R.string.import_choose_file), 13, 600)
                    else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Txt(fileName, 13, 600, modifier = Modifier.padding(horizontal = 16.dp))
                        Txt(
                            if (fileSize >= 0) stringResource(R.string.import_file_size_change, formatSize(fileSize))
                            else stringResource(R.string.import_choose_different),
                            11, 400, C.Muted, maxLines = Int.MAX_VALUE,
                        )
                    }
                }
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Txt(stringResource(R.string.import_formats), 11, 400, C.Muted, maxLines = Int.MAX_VALUE)
                    Txt(stringResource(R.string.import_max_size), 11, 400, C.Muted, maxLines = Int.MAX_VALUE)
                }
            }
            error?.let { Txt(it.asString(), 12, 500, C.Red, Modifier.testTag(ImportTags.ERROR), maxLines = Int.MAX_VALUE) }
            Row(
                Modifier.fillMaxWidth().clip(r(12)).background(C.CardWash).border(1.dp, C.Border, r(12)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(38.dp).clip(r(10)).background(C.Raised), contentAlignment = Alignment.Center) {
                    LineIcon(Icons.FileDoc, 16.dp, C.Yellow, 1.9f)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Txt(stringResource(R.string.import_sample_title), 13, 600)
                    Txt(stringResource(R.string.import_sample_sub), 11, 400, C.Muted, maxLines = Int.MAX_VALUE)
                }
                RoundButton(40.dp, C.Btn, onSample, Modifier.semantics { contentDescription = downloadLabel }) {
                    LineIcon(Icons.Download, 14.dp, C.Text, 2.2f)
                }
            }
            Pill(
                actionLabel, { if (fileName != null) onProceed() }, Modifier.fillMaxWidth().testTag(ImportTags.PROCEED),
                bg = if (fileName != null) C.Yellow else C.Raised, fg = if (fileName != null) C.OnYellow else C.Muted,
                height = 46.dp, size = 14, weight = 700,
            )
        }
    }
}

/**
 * The second step, "Mapping Data", as the web's: how many columns were matched (and whether AI helped, what it said,
 * and which columns won't be imported), then for each detail the column it is read from ("Heading", "AI" or "Values"
 * while it is still the server's proposal). A required detail with no column is marked once Import is tried.
 */
@Composable
private fun MappingSheet(
    plan: ImportPlan,
    mapping: Map<String, String>,
    incomplete: Boolean,
    loading: Boolean,
    onClose: () -> Unit,
    onMap: (target: String, heading: String?) -> Unit,
    onImport: () -> Unit,
) {
    var choosingFor by remember { mutableStateOf<ImportTarget?>(null) }
    BottomSheet(onClose, maxHeightFraction = 0.92f, background = C.SheetBg) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SheetTitle(stringResource(R.string.import_mapping_title), onClose)
        }
        if (loading) {
            Box(Modifier.padding(12.dp)) { Loader() }
            return@BottomSheet
        }
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PickerNote(plan, mapping)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Txt(stringResource(R.string.import_source_fields), 11, 600, C.Muted, Modifier.weight(1f))
                Txt(stringResource(R.string.import_target_fields), 11, 600, C.Muted, Modifier.weight(1f))
            }
            plan.targets.forEach { target ->
                val heading = mapping[target.key]
                val suggested = plan.suggestion.columns.firstOrNull { it.target == target.key }
                MappingRow(
                    target, heading, missing = incomplete && target.required && heading == null,
                    badge = suggested?.how?.takeIf { suggested.heading == heading },
                ) { choosingFor = target }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
            val ready = mapping.isNotEmpty()
            Pill(
                stringResource(R.string.import_action), { if (ready) onImport() }, Modifier.fillMaxWidth().testTag(ImportTags.IMPORT),
                bg = if (ready) C.Yellow else C.Raised, fg = if (ready) C.OnYellow else C.Muted, height = 46.dp, size = 14, weight = 700,
            )
        }
    }
    choosingFor?.let { target ->
        // A column already used for another detail isn't offered again (as the web's picker); "None" clears it.
        val usedElsewhere = mapping.filterKeys { it != target.key }.values.toSet()
        val none = stringResource(R.string.import_none)
        val options = listOf(none to null) + plan.suggestion.columns
            .filter { it.heading !in usedElsewhere }
            .map { c -> c.heading to c.samples.take(SAMPLES_SHOWN).joinToString(" · ").ifEmpty { null } }
        ChoiceSheet(
            target.header, options, mapping[target.key] ?: none,
            onPick = { picked ->
                onMap(target.key, picked.takeIf { it != none })
                choosingFor = null
            },
            onClose = { choosingFor = null },
        )
    }
}

/** How many of a column's values show under it in the picker. */
private const val SAMPLES_SHOWN = 3

/** "4 of 6 columns matched, with help from AI. Check them before importing.", the AI's note, and the columns left out. */
@Composable
private fun PickerNote(plan: ImportPlan, mapping: Map<String, String>) {
    val matched = mapping.values.toSet()
    val total = plan.suggestion.columns.size
    val leftOut = plan.suggestion.columns.map { it.heading }.filter { it !in matched }
    Column(
        Modifier.fillMaxWidth().clip(r(12)).background(C.CardWash).border(1.dp, C.Border, r(12)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Txt(
            stringResource(if (plan.suggestion.aiUsed) R.string.import_matched_ai else R.string.import_matched, matched.size, total),
            12, 500, maxLines = Int.MAX_VALUE,
        )
        plan.suggestion.aiNote?.let { Txt(it, 11, 400, C.Muted, maxLines = Int.MAX_VALUE) }
        if (leftOut.isNotEmpty()) Txt(stringResource(R.string.import_not_imported, leftOut.joinToString(", ")), 11, 400, C.Muted, maxLines = Int.MAX_VALUE)
    }
}

/** One detail: the column it is read from (tap to change), an arrow, and the detail's name with how it was matched. */
@Composable
private fun MappingRow(target: ImportTarget, heading: String?, missing: Boolean, badge: MatchHow?, onChoose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().testTag(ImportTags.target(target.key)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.weight(1f).heightIn(min = 48.dp).clip(r(10)).background(C.Bg)
                .border(1.dp, if (missing) C.Red else C.Border, r(10))
                .clickable(role = Role.Button, onClick = onChoose).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Txt(heading ?: stringResource(R.string.import_select_field), 13, if (heading != null) 600 else 400, if (heading != null) C.Text else C.Muted, Modifier.weight(1f), maxLines = 2)
            LineIcon(Icons.ChevronDown, 12.dp, C.Muted, 2.4f)
        }
        LineIcon(Icons.ChevronRight, 12.dp, C.Dim, 2.4f)
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Txt(target.header, 13, 600, if (missing) C.Red else C.Text, maxLines = 2)
            if (target.required) Txt("*", 13, 600, C.Yellow)
            badge?.let { TintChip(stringResource(it.labelRes())) }
        }
    }
}

private fun MatchHow.labelRes(): Int = when (this) {
    MatchHow.HEADING -> R.string.import_how_heading
    MatchHow.AI -> R.string.import_how_ai
    MatchHow.VALUES -> R.string.import_how_values
}

@Composable
private fun SheetTitle(title: String, onClose: () -> Unit) {
    val closeLabel = stringResource(R.string.action_close)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Txt(title, 17, 700, modifier = Modifier.weight(1f))
        RoundButton(36.dp, C.Btn, onClose, Modifier.semantics { contentDescription = closeLabel }) {
            LineIcon(Icons.Close, 14.dp, C.Text, 2.6f)
        }
    }
}

@Composable
private fun Loader() {
    Box(Modifier.fillMaxWidth().heightIn(min = 140.dp).testTag(ImportTags.LOADING), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(28.dp), color = C.Yellow, strokeWidth = 2.5.dp)
    }
}
