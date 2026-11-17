package com.sclera.sclerago.services.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Makes a video smaller before it is kept in the offline database (and uploaded from there): H.264 video with its
 * short side at most [MAX_SHORT_SIDE] pixels at about [VIDEO_BITRATE], and AAC audio, in an app-private `.mp4`, cut to
 * the length an asset keeps. Uses AndroidX Media3 Transformer, which encodes on the phone's hardware encoder.
 */
@Singleton
class VideoCompressor @Inject constructor(@ApplicationContext private val context: Context) {

    /** A compressed video: its app-private [file] and how long it now runs. */
    class Result(val file: File, val durationMs: Long)

    /** What a video is: its size, bitrate and length. */
    private class Info(val width: Int, val height: Int, val bitrate: Long, val durationMs: Long)

    /** How long [source] runs in milliseconds, or null when it can't be read. */
    fun durationMs(source: Uri): Long? = info(source)?.durationMs?.takeIf { it > 0 }

    /**
     * [source] re-encoded smaller and cut to its first [maxMs] milliseconds, as a new app-private file; null when it is
     * already small and short enough, or can't be compressed (then the original is kept as it is). The caller deletes
     * the file once it is in the database.
     */
    suspend fun compress(source: Uri, maxMs: Long): Result? {
        val info = info(source) ?: return null
        val tooLong = info.durationMs > maxMs
        val tooBig = minOf(info.width, info.height) > MAX_SHORT_SIDE || info.bitrate > VIDEO_BITRATE * 3 / 2
        if (!tooLong && !tooBig) return null
        val out = File(captureDir(context), "VID_${stamp()}_small.mp4")
        val done = withContext(Dispatchers.Main) { export(source, out, maxMs.takeIf { tooLong }) }
        if (!done || out.length() == 0L) {
            out.delete()
            return null
        }
        return Result(out, if (tooLong) maxMs else info.durationMs)
    }

    /** [source]'s size, bitrate and length, or null when it can't be read. */
    private fun info(source: Uri): Info? {
        // Released by hand: MediaMetadataRetriever is only AutoCloseable from Android 10 (the app runs from Android 8).
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, source)
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return null
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return null
            Info(
                w, h,
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: Long.MAX_VALUE,
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
            )
        } catch (_: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }

    /**
     * Runs the export on the main thread (Transformer needs a looper), keeping only the first [endMs] when given; true
     * when it finished. Cancelling stops it.
     */
    @OptIn(UnstableApi::class)
    private suspend fun export(source: Uri, out: File, endMs: Long?): Boolean = suspendCancellableCoroutine { cont ->
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(
                DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(VIDEO_BITRATE.toInt()).build())
                    .build(),
            )
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    if (cont.isActive) cont.resume(false)
                }
            })
            .build()
        val clip = MediaItem.Builder().setUri(source).apply {
            if (endMs != null) setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setEndPositionMs(endMs).build())
        }.build()
        val item = EditedMediaItem.Builder(clip)
            .setEffects(Effects(emptyList(), listOf(Presentation.createForShortSide(MAX_SHORT_SIDE))))
            .build()
        cont.invokeOnCancellation {
            transformer.cancel()
            out.delete()
        }
        runCatching { transformer.start(item, out.absolutePath) }.onFailure { if (cont.isActive) cont.resume(false) }
    }

    private companion object {
        /** 720p: enough to read a nameplate or see the equipment, a fraction of the size of the camera's 1080p / 4K. */
        const val MAX_SHORT_SIDE = 720

        /** About 2.5 Mbit/s: roughly 19MB a minute, against 100MB+ a minute straight from the camera. */
        const val VIDEO_BITRATE = 2_500_000L
    }
}
