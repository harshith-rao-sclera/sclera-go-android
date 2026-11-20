package com.sclera.sclerago.presentation.assets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.PhotoKind
import com.sclera.sclerago.domain.model.PhotoRule
import com.sclera.sclerago.presentation.components.CircleFab
import com.sclera.sclerago.presentation.components.FabAction
import com.sclera.sclerago.presentation.components.FabMenu
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.theme.C

/**
 * The Media tab's floating buttons: a yellow + that opens into Nameplate, Asset, Label ID (each only while the
 * property's photo rules allow more; its camera and gallery take no more than that), then Camera and File; under the + a mic that records a voice memo. The photo kinds
 * and Camera open the app's camera straight away (photo, video and the gallery are there); a photo taken for a kind goes
 * into its card, one taken with Camera into Other photos.
 */
@Composable
fun MediaFabs(
    media: List<Media>,
    rules: List<PhotoRule>,
    onAddPhoto: (kind: PhotoKind?) -> Unit,
    onAddFile: () -> Unit,
    onRecordMemo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val photos = media.filter { !it.video && !it.isDoc && !it.audio }
    val kinds = rules.filter { r -> r.max > 0 && photos.count { it.category == r.kind } < r.max }
    // The menu lists its actions top to bottom above the +: the photo kinds (each with how many it has of the most
    // the property allows), then Camera, then File.
    val actions = kinds.map { r ->
        val added = photos.count { it.category == r.kind }
        FabAction(
            stringResource(r.kind.nameRes()), Icons.Camera, "media-add-${r.kind.db}", { onAddPhoto(r.kind) }, null,
            note = stringResource(R.string.media_kind_count, added, r.max),
        )
    } +
        listOf(
            FabAction(stringResource(R.string.media_add_camera), Icons.Camera, "media-add-camera") { onAddPhoto(null) },
            FabAction(stringResource(R.string.media_add_file_fab), Icons.FileUpload, "media-add-file", onAddFile),
        )
    Column(
        modifier.padding(end = 16.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FabMenu(open, { open = it }, actions, stringResource(R.string.media_add_fab), stringResource(R.string.action_close), Modifier.testTag("media-add"))
        CircleFab(
            Icons.Mic, stringResource(R.string.media_record_memo), onRecordMemo, Modifier.testTag("media-record-memo"),
            bg = C.Btn, fg = C.Text, iconSize = 22.dp, stroke = 2.4f,
        )
    }
}

/** Upload document: the Documents tab's floating +. */
@Composable
fun UploadDocumentFab(onUpload: () -> Unit, modifier: Modifier = Modifier) {
    CircleFab(Icons.Plus, stringResource(R.string.doc_upload), onUpload, modifier.padding(end = 16.dp, bottom = 16.dp).testTag("doc-upload"))
}
