package com.sclera.sclerago.data.translation

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.sclera.sclerago.domain.model.ModelResult
import com.sclera.sclerago.domain.model.TranslationLanguages
import com.sclera.sclerago.domain.model.TranslationResult
import com.sclera.sclerago.domain.repository.TextTranslator
import com.sclera.sclerago.domain.repository.TranslationModels
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Translates on the device with Google ML Kit. Each text's language is guessed first (0.75 confidence; an unknown or
 * failed guess counts as English). Translating never downloads: a pair whose models aren't on the phone is left as
 * written, and [ensure] downloads a language (40 s limit) when the person picks it, keeping only the four used most
 * recently.
 */
class MlKitTextTranslator internal constructor(private val usage: ModelUsage) : TextTranslator, TranslationModels {
    private val identifier = LanguageIdentification.getClient(
        LanguageIdentificationOptions.Builder().setConfidenceThreshold(CONFIDENCE).build(),
    )
    private val manager = RemoteModelManager.getInstance()
    private val modelLock = Mutex()

    override suspend fun translate(texts: List<String>, target: String): List<TranslationResult> {
        val to = LanguageCodes.toMlKit(target)
        return texts.map { text ->
            try { translateOne(text, to) } catch (e: CancellationException) { throw e } catch (_: Exception) { TranslationResult.Failed }
        }
    }

    override suspend fun ensure(target: String): ModelResult {
        val code = LanguageCodes.toMlKit(target)
        return try {
            modelLock.withLock {
                if (installed(code)) return ModelResult.ALREADY_INSTALLED
                val translator = Translation.getClient(
                    TranslatorOptions.Builder().setSourceLanguage(TranslateLanguage.ENGLISH).setTargetLanguage(code).build(),
                )
                try {
                    withTimeout(DOWNLOAD_TIMEOUT_MS) { translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await() }
                } finally {
                    translator.close()
                }
                evictOld(code)
                ModelResult.DOWNLOADED
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ModelResult.FAILED
        }
    }

    override suspend fun installed(): Set<String> {
        val downloaded = try {
            manager.getDownloadedModels(TranslateRemoteModel::class.java).await().map { it.language }.toSet()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptySet()
        }
        return TranslationLanguages.supported.filter { LanguageCodes.toMlKit(it) == TranslateLanguage.ENGLISH || LanguageCodes.toMlKit(it) in downloaded }.toSet()
    }

    private suspend fun translateOne(text: String, to: String): TranslationResult {
        val from = sourceOf(text)
        if (from == to || !installed(from) || !installed(to)) return TranslationResult.Unavailable
        val translator = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(from).setTargetLanguage(to).build())
        try {
            usage.touch(to)
            return TranslationResult.Done(translator.translate(text).await())
        } finally {
            translator.close()
        }
    }

    private suspend fun sourceOf(text: String): String {
        val found = try { identifier.identifyLanguage(text).await() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        return found?.takeIf { it != UNDETERMINED }?.let { TranslateLanguage.fromLanguageTag(it) } ?: TranslationLanguages.ENGLISH
    }

    /** English ships with ML Kit; any other model must have been downloaded. */
    private suspend fun installed(code: String): Boolean =
        code == TranslateLanguage.ENGLISH || manager.isModelDownloaded(TranslateRemoteModel.Builder(code).build()).await()

    /** Marks [code] used and deletes the models that fell out of the last four. */
    private suspend fun evictOld(code: String) {
        usage.touch(code).forEach { old ->
            try { manager.deleteDownloadedModel(TranslateRemoteModel.Builder(old).build()).await() } catch (e: CancellationException) { throw e } catch (_: Exception) { }
            usage.forget(old)
        }
    }

    private companion object {
        const val CONFIDENCE = 0.75f
        const val DOWNLOAD_TIMEOUT_MS = 40_000L
        const val UNDETERMINED = "und"
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
