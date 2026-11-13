package com.sclera.sclerago.presentation.sync

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.sync.SyncFailure
import com.sclera.sclerago.domain.sync.SyncState
import com.sclera.sclerago.presentation.components.isTablet
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.ScleraTheme
import com.sclera.sclerago.presentation.theme.Txt

/** Test tags used by the sync screen UI tests. */
internal object SyncTags {
    const val PROGRESS = "sync_progress"
    const val AMOUNT = "sync_amount"
    const val RETRY = "sync_retry"
    const val FAILURE = "sync_failure"
}

private val ContentMaxWidth = 480.dp

/** Shows the app once the offline data is ready; until then (and when the first download fails) the download screen. */
@Composable
fun SyncGate(vm: SyncViewModel = hiltViewModel(), content: @Composable () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    // Inside the signed-in branch, so every sign-in starts (or resumes) the download; starting twice does nothing.
    LaunchedEffect(Unit) { vm.start() }
    if (state is SyncState.Ready) content() else SyncScreen(state, onRetry = vm::retry)
}

/** "Downloading your data": a progress bar while the first download runs, or why it failed with a retry button. */
@Composable
fun SyncScreen(state: SyncState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val tablet = isTablet()
    Box(modifier.fillMaxSize().background(C.Bg).safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (tablet) 20.dp else 16.dp),
        ) {
            val failed = state as? SyncState.Failed
            Box(
                Modifier.size(if (tablet) 88.dp else 72.dp).clip(CircleShape).background(C.Surface).border(1.dp, C.Border, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                LineIcon(if (failed == null) Icons.Download else Icons.Alert, 30.dp, if (failed == null) C.Yellow else C.Red, 1.8f)
            }
            if (failed == null) Downloading(state as? SyncState.Downloading, tablet) else Failure(failed.reason, onRetry, tablet)
        }
    }
}

@Composable
private fun Downloading(state: SyncState.Downloading?, tablet: Boolean) {
    val title = if (tablet) 28 else 22
    Txt(stringResource(R.string.sync_title), title, 700, align = TextAlign.Center, maxLines = 2)
    Txt(stringResource(R.string.sync_subtitle), if (tablet) 17 else 14, 400, C.Muted, align = TextAlign.Center, maxLines = 3)
    Spacer(Modifier.height(4.dp))
    val percent = state?.progressPercent
    val progressLabel = stringResource(R.string.sync_progress_description)
    val bar = Modifier.fillMaxWidth().height(6.dp).clip(r(3)).testTag(SyncTags.PROGRESS).semantics { contentDescription = progressLabel }
    if (percent != null) {
        LinearProgressIndicator(progress = { percent / 100f }, modifier = bar, color = C.Yellow, trackColor = C.Raised)
    } else {
        LinearProgressIndicator(modifier = bar, color = C.Yellow, trackColor = C.Raised)
    }
    val downloaded = state?.downloadedMb
    val total = state?.totalMb
    if (downloaded != null && total != null) {
        Txt(
            stringResource(R.string.sync_progress_mb, downloaded, total),
            if (tablet) 15 else 13, 500, C.Soft, modifier = Modifier.testTag(SyncTags.AMOUNT), align = TextAlign.Center,
        )
    }
}

@Composable
private fun Failure(reason: SyncFailure, onRetry: () -> Unit, tablet: Boolean) {
    Txt(stringResource(R.string.sync_failed_title), if (tablet) 28 else 22, 700, align = TextAlign.Center, maxLines = 2)
    Txt(
        stringResource(reason.message()), if (tablet) 17 else 14, 400, C.Muted,
        modifier = Modifier.testTag(SyncTags.FAILURE), align = TextAlign.Center, maxLines = 4,
    )
    Spacer(Modifier.height(4.dp))
    Row(
        Modifier.fillMaxWidth().height(if (tablet) 58.dp else 52.dp).testTag(SyncTags.RETRY)
            .background(C.Yellow, r(14)).clickable(role = Role.Button, onClick = onRetry),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        LineIcon(Icons.Refresh, 18.dp, C.OnYellow, 2.4f)
        Spacer(Modifier.size(8.dp))
        Txt(stringResource(R.string.sync_retry), if (tablet) 19 else 16, 600, C.OnYellow)
    }
}

@StringRes
private fun SyncFailure.message(): Int = when (this) {
    SyncFailure.NO_NETWORK -> R.string.sync_failed_no_network
    SyncFailure.SESSION_EXPIRED -> R.string.sync_failed_session
    SyncFailure.SERVER_ERROR -> R.string.sync_failed_server
    SyncFailure.STORAGE_FULL -> R.string.sync_failed_storage
}

private const val PREVIEW_PHONE = "spec:width=390dp,height=844dp"
private const val PREVIEW_TABLET_PORTRAIT = "spec:width=800dp,height=1280dp,dpi=240"
private const val PREVIEW_TABLET_LANDSCAPE = "spec:width=1280dp,height=800dp,dpi=240"

@Preview(name = "Phone", device = PREVIEW_PHONE)
@Preview(name = "Tablet portrait", device = PREVIEW_TABLET_PORTRAIT)
@Preview(name = "Tablet landscape", device = PREVIEW_TABLET_LANDSCAPE)
@Composable
private fun SyncDownloadingPreview() {
    ScleraTheme { SyncScreen(SyncState.Downloading(42, 31.5, 75.0), onRetry = {}) }
}

@Preview(name = "Phone unknown", device = PREVIEW_PHONE)
@Preview(name = "Tablet portrait unknown", device = PREVIEW_TABLET_PORTRAIT)
@Preview(name = "Tablet landscape unknown", device = PREVIEW_TABLET_LANDSCAPE)
@Composable
private fun SyncDownloadingUnknownPreview() {
    ScleraTheme { SyncScreen(SyncState.Downloading(), onRetry = {}) }
}

@Preview(name = "Phone failed", device = PREVIEW_PHONE)
@Preview(name = "Tablet portrait failed", device = PREVIEW_TABLET_PORTRAIT)
@Preview(name = "Tablet landscape failed", device = PREVIEW_TABLET_LANDSCAPE)
@Composable
private fun SyncFailedPreview() {
    ScleraTheme { SyncScreen(SyncState.Failed(SyncFailure.NO_NETWORK), onRetry = {}) }
}
