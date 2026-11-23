package com.sclera.sclerago.presentation.assets

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import com.sclera.sclerago.services.media.VoiceMemoRecorder
import kotlinx.coroutines.delay
import java.io.File

/** Test tags of the voice memo recorder. */
object MemoTags {
    const val SHEET = "memo-sheet"
    const val TIME = "memo-time"
    const val SAVE = "memo-save"
    const val STOP = "memo-stop"
}

private const val TICK_MS = 200L

/**
 * Records a voice memo for the asset in two steps: asks for the microphone at the moment of use, records at once with a
 * running clock, Stop ends the recording, then Save memo hands it to [onSaved]. Closing the sheet drops the recording
 * and its file. [onFailed] says why nothing was recorded. The sheet closes itself through [onClose] either way.
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
    // The finished recording, waiting for Save memo.
    var memo by remember { mutableStateOf<Media?>(null) }
    val pending = rememberUpdatedState(memo)

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
    // Leaving the screen keeps nothing: a recording in progress stops and an unsaved one loses its file.
    DisposableEffect(recorder) {
        onDispose {
            recorder.cancel()
            pending.value?.let(::deleteMemoFile)
        }
    }

    val discard = { close() }
    val stop = {
        recording = false
        val done = recorder.stop()
        if (done != null) {
            memo = done
            elapsed = done.durationMs
        } else {
            failed(UiText.Res(R.string.memo_too_short))
            close()
        }
    }
    val save = {
        memo?.let { saved(it) }
        memo = null
        close()
    }
    // A memo stops on its own at the 10-second limit (MAX_CLIP_MS); Save memo then keeps it.
    val atLimit = elapsed >= MAX_CLIP_MS
    LaunchedEffect(recording, atLimit) { if (recording && atLimit) stop() }

    BottomSheet(onDismiss = discard) {
        Column(Modifier.testTag(MemoTags.SHEET)) {
            SheetTitleRow(stringResource(R.string.media_tag_audio), stringResource(R.string.memo_subtitle), discard)
            Column(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Txt(formatDuration(elapsed), 34, 600, if (recording) C.Red else C.Text, mono = true, modifier = Modifier.testTag(MemoTags.TIME))
                if (recording) {
                    Pill(
                        stringResource(R.string.memo_stop), stop, Modifier.testTag(MemoTags.STOP),
                        bg = C.Red, fg = C.OnYellow, height = 46.dp, hPad = 20.dp, size = 14, weight = 700,
                    )
                }
                Pill(
                    stringResource(R.string.memo_save), { if (memo != null) save() },
                    Modifier.fillMaxWidth().testTag(MemoTags.SAVE).alpha(if (memo != null) 1f else 0.45f),
                    bg = C.Yellow, fg = C.OnYellow, height = 46.dp, size = 14, weight = 700,
                )
            }
        }
    }
}

/** Removes a recording that was never saved to an asset. */
private fun deleteMemoFile(media: Media) {
    runCatching { Uri.parse(media.uri).path?.let { File(it).delete() } }
}
