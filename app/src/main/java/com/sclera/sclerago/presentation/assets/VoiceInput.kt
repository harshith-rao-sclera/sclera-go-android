package com.sclera.sclerago.presentation.assets

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.sclera.sclerago.R
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.services.voice.SpeechCapture
import com.sclera.sclerago.services.voice.SpeechFailure
import com.sclera.sclerago.services.voice.SpeechListener

/** The mic as the details screen shows it: listening or not, and the words heard so far. */
data class MicState(val listening: Boolean = false, val heard: String = "")

/** The mic of the details screen: its state, and [toggle] to start listening (asking for the microphone first) or stop. */
@Stable
class VoiceInput internal constructor() {
    var state by mutableStateOf(MicState())
        internal set
    internal var onToggle: () -> Unit = {}

    fun toggle() = onToggle()
}

/**
 * Speech input for filling fields by voice: listens with the device's recognizer, asking for the microphone at the moment
 * of use; [onHeard] gets what was said, [onFailed] why nothing was. Listening stops when the screen goes.
 */
@Composable
fun rememberVoiceInput(onHeard: (said: String, sure: Float?) -> Unit, onFailed: (UiText) -> Unit): VoiceInput {
    val context = LocalContext.current
    val input = remember { VoiceInput() }
    val heard by rememberUpdatedState(onHeard)
    val failed by rememberUpdatedState(onFailed)
    val capture = remember(context) {
        SpeechCapture(
            context,
            object : SpeechListener {
                override fun onPartial(text: String) {
                    input.state = input.state.copy(heard = text)
                }

                override fun onHeard(text: String, confidence: Float?) {
                    input.state = MicState()
                    heard(text, confidence)
                }

                override fun onFailed(reason: SpeechFailure) {
                    input.state = MicState()
                    failed(UiText.Res(reason.message()))
                }
            },
        )
    }
    DisposableEffect(capture) { onDispose { capture.release() } }

    fun listen() {
        input.state = MicState(listening = true)
        capture.start()
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) listen() else failed(UiText.Res(R.string.voice_no_permission))
    }
    input.onToggle = {
        when {
            input.state.listening -> capture.stop()
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> listen()
            else -> permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    return input
}

private fun SpeechFailure.message(): Int = when (this) {
    SpeechFailure.UNAVAILABLE -> R.string.voice_unavailable
    SpeechFailure.NOTHING_HEARD -> R.string.voice_nothing_heard
    SpeechFailure.NO_PERMISSION -> R.string.voice_no_permission
    SpeechFailure.OTHER -> R.string.voice_failed
}
