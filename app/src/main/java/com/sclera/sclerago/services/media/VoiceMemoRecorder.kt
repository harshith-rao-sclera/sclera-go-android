package com.sclera.sclerago.services.media

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import com.sclera.sclerago.domain.model.Media
import java.io.File

/** Where recordings are kept until they are saved to an asset: app-private, inside the asset media wiped on sign-out. */
private const val MEMO_DIR = "asset-media/memos"

/** Shorter than this and the recorder has nothing usable to keep. */
private const val MIN_MEMO_MS = 700L

/**
 * Records voice memos for an asset (AAC in MPEG-4, `.m4a`) to an app-private file, one at a time. The caller asks for the
 * microphone first. A kept memo is a [Media] the app owns, so saving it to an asset moves it into the asset's files.
 */
class VoiceMemoRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    val recording: Boolean get() = recorder != null

    /** Starts recording; false when the microphone can't be used. */
    fun start(): Boolean {
        cancel()
        val target = File(File(context.filesDir, MEMO_DIR).apply { mkdirs() }, "memo-${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(BIT_RATE)
            r.setAudioSamplingRate(SAMPLE_RATE)
            r.setOutputFile(target.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            file = target
            startedAt = SystemClock.elapsedRealtime()
            true
        } catch (_: Exception) {
            r.release()
            target.delete()
            false
        }
    }

    /** How long the memo being recorded has run, in milliseconds. */
    fun elapsedMs(): Long = if (recording) SystemClock.elapsedRealtime() - startedAt else 0L

    /** Stops and returns the memo, or null when nothing usable was recorded (too short, or the recorder failed). */
    fun stop(): Media? {
        val r = recorder ?: return null
        val f = file
        val duration = elapsedMs()
        recorder = null
        file = null
        // stop() throws when no audio was captured (a tap straight after start).
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        if (!ok || f == null || duration < MIN_MEMO_MS || !f.isFile || f.length() == 0L) {
            f?.delete()
            return null
        }
        return Media(
            id = "memo-$startedAt", uri = Uri.fromFile(f).toString(), video = false, durationMs = duration,
            owned = true, audio = true, sizeBytes = f.length(),
        )
    }

    /** Stops without keeping anything. */
    fun cancel() {
        recorder?.let { r ->
            runCatching { r.stop() }
            r.release()
        }
        recorder = null
        file?.delete()
        file = null
    }

    private companion object {
        const val BIT_RATE = 64_000
        const val SAMPLE_RATE = 44_100
    }
}
