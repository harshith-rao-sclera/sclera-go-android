package com.sclera.sclerago.presentation.property

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
import androidx.compose.foundation.shape.CircleShape
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
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.components.BottomSheet
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.RoundButton
import com.sclera.sclerago.presentation.components.dashedBorder
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import com.sclera.sclerago.services.media.formatSize
import java.io.OutputStream

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

/** Test tags of the Import sheet. */
object ImportTags {
    const val DROP_ZONE = "import_drop_zone"
    const val PROCEED = "import_proceed"
    const val ERROR = "import_error"
}

/** A file the user picked, before it is accepted. Name and size come from the provider, so they are checked, not trusted. */
private data class PickedFile(val uri: Uri, val name: String, val size: Long)

/** Import sheet: choose an .xlsx (checked by [problemOf]), download the sample, then proceed. */
@Composable
internal fun ImportSheet(
    onClose: () -> Unit,
    onProceed: (String) -> Unit,
    problemOf: (name: String, size: Long) -> UiText?,
    writeTemplate: (OutputStream) -> Unit,
) {
    val context = LocalContext.current
    var file by remember { mutableStateOf<PickedFile?>(null) }
    var error by remember { mutableStateOf<UiText?>(null) }

    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val picked = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) PickedFile(uri, c.getString(0) ?: "file", if (c.isNull(1)) -1 else c.getLong(1)) else null
        } ?: PickedFile(uri, uri.lastPathSegment ?: "file", -1)
        error = problemOf(picked.name, picked.size)
        file = if (error == null) picked else null
    }
    val sampleSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX_MIME)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.openOutputStream(uri)?.use(writeTemplate) }
            .onFailure { error = UiText.Res(R.string.import_error_sample) }
    }
    ImportSheetContent(
        file?.name, file?.size ?: -1, error, onClose,
        onChoose = { chooser.launch(arrayOf(XLSX_MIME, "application/octet-stream")) },
        onSample = { sampleSaver.launch("property-import-sample.xlsx") },
        onProceed = { file?.let { onProceed(it.name) } },
    )
}

/** The sheet's layout (design "Import"): drop zone, limits, sample card and Proceed. */
@Composable
internal fun ImportSheetContent(
    fileName: String?,
    fileSize: Long,
    error: UiText?,
    onClose: () -> Unit,
    onChoose: () -> Unit,
    onSample: () -> Unit,
    onProceed: () -> Unit,
) {
    val closeLabel = stringResource(R.string.action_close)
    val downloadLabel = stringResource(R.string.import_sample_download)
    BottomSheet(onClose, maxHeightFraction = 0.6f, background = C.SheetBg) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Txt(stringResource(R.string.import_title), 17, 700, modifier = Modifier.weight(1f))
                RoundButton(36.dp, C.Btn, onClose, Modifier.semantics { contentDescription = closeLabel }) {
                    LineIcon(Icons.Close, 14.dp, C.Text, 2.6f)
                }
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
                            11, 400, C.Muted,
                        )
                    }
                }
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Txt(stringResource(R.string.import_formats), 11, 400, C.Muted)
                    Txt(stringResource(R.string.import_max_size), 11, 400, C.Muted)
                }
            }
            error?.let { Txt(it.asString(), 12, 500, C.Red, Modifier.testTag(ImportTags.ERROR), maxLines = 3) }
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
                    Txt(stringResource(R.string.import_sample_sub), 11, 400, C.Muted, maxLines = 2)
                }
                RoundButton(40.dp, C.Btn, onSample, Modifier.semantics { contentDescription = downloadLabel }) {
                    LineIcon(Icons.Download, 14.dp, C.Text, 2.2f)
                }
            }
            Pill(
                stringResource(R.string.import_proceed), onProceed, Modifier.fillMaxWidth().testTag(ImportTags.PROCEED),
                bg = if (fileName != null) C.Yellow else C.Raised, fg = if (fileName != null) C.OnYellow else C.Muted,
                height = 46.dp, size = 14, weight = 700,
            )
        }
    }
}
