package com.sclera.sclerago.presentation.assets

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.limits.MAX_CLIP_MS
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.formatDuration
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.components.BottomSheet
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import com.sclera.sclerago.services.media.VoiceMemoRecorder
import kotlinx.coroutines.delay

/** Test tags of the voice memo recorder. */
object MemoTags {
    const val SHEET = "memo-sheet"
    const val TIME = "memo-time"
    const val SAVE = "memo-save"
}

private const val TICK_MS = 200L

/**
 * Records a voice memo for the asset: asks for the microphone at the moment of use, then records at once with a running
 * clock. Stop and save hands the memo to [onSaved]; Cancel or closing the sheet drops it. [onFailed] says why nothing
 * was recorded. The sheet closes itself through [onClose] either way.
 */
@Composable
fun VoiceMemoSheet(onSaved: (Media) -> Unit, onClose: () -> Unit, onFailed: (UiText) -> Unit) {
    val context = LocalContext.current
    val recorder = remember(context) { VoiceMemoRecorder(context) }
    val saved by rememberUpdatedState(onSaved)
    val failed by rememberUpdatedState(onFailed)
    val close by rememberUpdatedState(onClose)
    var recording by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }

    fun begin() {
        if (recorder.start()) recording = true
        else {
            failed(UiText.Res(R.string.memo_failed))
            close()
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) begin()
        else {
            failed(UiText.Res(R.string.memo_no_permission))
            close()
        }
    }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) begin() else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    LaunchedEffect(recording) {
        while (recording) {
            elapsed = recorder.elapsedMs()
            delay(TICK_MS)
        }
    }
    // Leaving the screen mid-recording keeps nothing.
    DisposableEffect(recorder) { onDispose { recorder.cancel() } }

    val cancel = {
        recorder.cancel()
        recording = false
        close()
    }
    val save = {
        val memo = recorder.stop()
        recording = false
        if (memo != null) saved(memo) else failed(UiText.Res(R.string.memo_too_short))
        close()
    }
    // A memo stops and is kept on its own at the 10-second limit (MAX_CLIP_MS).
    val atLimit = elapsed >= MAX_CLIP_MS
    LaunchedEffect(recording, atLimit) { if (recording && atLimit) save() }

    BottomSheet(onDismiss = cancel) {
        Column(Modifier.testTag(MemoTags.SHEET)) {
            SheetTitleRow(
                stringResource(R.string.media_tag_audio),
                stringResource(if (recording) R.string.memo_recording else R.string.memo_starting),
                cancel,
            )
            Column(
                Modifier.fillMaxWidth().padding(vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(76.dp).clip(CircleShape).background(if (recording) C.DangerBg else C.Raised),
                    contentAlignment = Alignment.Center,
                ) { LineIcon(Icons.Mic, 32.dp, if (recording) C.Red else C.Muted, 2.2f) }
                Txt(formatDuration(elapsed), 28, 800, modifier = Modifier.testTag(MemoTags.TIME))
                Txt(stringResource(R.string.memo_limit), 12, 500, C.Muted)
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Pill(stringResource(R.string.action_cancel), cancel, Modifier.weight(1f), height = 48.dp, size = 14, weight = 700)
                Pill(
                    stringResource(R.string.memo_stop_save), { if (recording) save() },
                    Modifier.weight(1f).testTag(MemoTags.SAVE).alpha(if (recording) 1f else 0.45f),
                    bg = C.Yellow, fg = C.OnYellow, height = 48.dp, size = 14, weight = 700,
                )
            }
        }
    }
}
