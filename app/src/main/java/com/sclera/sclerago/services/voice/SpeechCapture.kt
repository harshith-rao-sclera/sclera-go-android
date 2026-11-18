package com.sclera.sclerago.services.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Why nothing was heard. */
enum class SpeechFailure { UNAVAILABLE, NOTHING_HEARD, NO_PERMISSION, OTHER }

/** What [SpeechCapture] reports, on the main thread. */
interface SpeechListener {
    /** Everything heard so far. */
    fun onPartial(text: String)

    /** What was said, and how sure the recognizer is of it (0..1; null when it doesn't say). */
    fun onHeard(text: String, confidence: Float?)

    fun onFailed(reason: SpeechFailure)
}

/**
 * Speech to text with the device's own recognizer, listening until [stop] is called: the recognizer ends each session
 * at a pause, so a new one starts straight away and what is said is added to what was said before (as the web's voice
 * input keeps listening until Done). It reports everything heard as it comes, and all of it once stopped.
 *
 * It asks for the on-device model first, so the audio stays on the phone where the device has one, and uses the
 * recognizer's service only when it has none for the language. Use it on the main thread (with the microphone
 * permission granted) and [release] it when done.
 */
class SpeechCapture(private val context: Context, private val listener: SpeechListener) {
    private var recognizer: SpeechRecognizer? = null
    private var offline = true

    /** Still listening (not stopped or failed). */
    private var active = false

    /** Asked to stop: the session's last words are on their way. */
    private var stopping = false

    /** What earlier sessions heard, and the current session's words so far. */
    private val said = mutableListOf<String>()
    private var partial = ""
    private val sure = mutableListOf<Float>()

    /** Passing errors in a row since the last words were heard. */
    private var hiccups = 0

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start() {
        release()
        if (!available) return listener.onFailed(SpeechFailure.UNAVAILABLE)
        offline = true
        active = true
        stopping = false
        listen()
    }

    /** Stops listening and reports everything said. */
    fun stop() {
        if (!active || stopping) return
        stopping = true
        val r = recognizer
        if (r == null) finish() else r.stopListening()
    }

    fun release() {
        active = false
        stopping = false
        recognizer?.destroy()
        recognizer = null
        said.clear()
        sure.clear()
        partial = ""
        hiccups = 0
    }

    private fun listen() {
        partial = ""
        // The same recognizer listens again (a new one each time can find the last one still busy).
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(Listener())
            recognizer = it
        }
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, offline)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_MS)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_MS),
        )
    }

    /** Ends the session's words into what was said, then listens on, or (once asked to stop) reports it all. */
    private fun sessionEnded() {
        partial.takeIf { it.isNotBlank() }?.let { said += it.trim() }
        partial = ""
        if (stopping) finish() else listen()
    }

    private fun finish() {
        val text = said.joinToString(" ").trim()
        val confidence = sure.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        release()
        if (text.isBlank()) listener.onFailed(SpeechFailure.NOTHING_HEARD) else listener.onHeard(text, confidence)
    }

    private fun fail(reason: SpeechFailure) {
        if (said.isNotEmpty() || partial.isNotBlank()) {
            stopping = true
            return sessionEnded()
        }
        release()
        listener.onFailed(reason)
    }

    private fun heardSoFar() = (said + partial).filter { it.isNotBlank() }.joinToString(" ")

    private inner class Listener : RecognitionListener {
        override fun onPartialResults(results: Bundle?) {
            if (!active) return
            results?.texts()?.firstOrNull()?.takeIf { it.isNotBlank() }?.let {
                partial = it
                listener.onPartial(heardSoFar())
            }
        }

        override fun onResults(results: Bundle?) {
            if (!active) return
            hiccups = 0
            results?.texts()?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { partial = it }
            results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.firstOrNull()?.takeIf { it in 0f..1f }?.let { sure += it }
            sessionEnded()
        }

        override fun onError(error: Int) {
            if (!active) return
            when {
                // A pause with nothing new said: keep listening, unless asked to stop.
                error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> sessionEnded()
                stopping -> sessionEnded()
                // A hiccup between sessions (the recognizer still busy with the last one): keep recording until the
                // person taps stop, a few times in a row at most.
                error in PASSING && hiccups < MAX_HICCUPS -> {
                    hiccups++
                    sessionEnded()
                }
                // No on-device model for the language: ask the recognizer's service instead.
                offline && error in NO_LOCAL_MODEL -> {
                    offline = false
                    listen()
                }
                error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> fail(SpeechFailure.NO_PERMISSION)
                else -> fail(SpeechFailure.OTHER)
            }
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun Bundle.texts(): List<String>? = getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

    private companion object {
        /** How long a pause the recognizer is asked to wait through (many ignore it; listening restarts anyway). */
        const val SILENCE_MS = 10_000L

        /** Errors that pass: the recognizer still busy with the last session, or a client-side hiccup. */
        val PASSING = setOf(SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT)
        const val MAX_HICCUPS = 5

        /** The errors that say the device has no model of its own for the language. */
        val NO_LOCAL_MODEL = setOf(
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
            SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
        )
    }
}
