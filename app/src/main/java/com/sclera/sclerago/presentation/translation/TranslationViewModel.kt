package com.sclera.sclerago.presentation.translation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.domain.model.ModelResult
import com.sclera.sclerago.domain.model.TranslationResult
import com.sclera.sclerago.domain.usecase.translation.TranslationUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/** What the language sheet's tapped row shows while its language is made ready for names and comments. */
sealed interface LanguageDownload {
    data object Idle : LanguageDownload
    data class Working(val code: String) : LanguageDownload
    data class Ready(val code: String) : LanguageDownload
    data class Failed(val code: String) : LanguageDownload
}

/** The chosen language, the sheet's download state, and the translating of server text (answers remembered while the app runs). */
@HiltViewModel
class TranslationViewModel @Inject constructor(private val useCases: TranslationUseCases) : ViewModel() {
    /** The language the app and its data are shown in; null: as the phone has it. */
    val target: StateFlow<String?> = useCases.observeTarget().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _download = MutableStateFlow<LanguageDownload>(LanguageDownload.Idle)
    val download: StateFlow<LanguageDownload> = _download

    private val _installed = MutableStateFlow<Set<String>>(emptySet())

    /** The languages that work offline (downloaded, and English). */
    val installed: StateFlow<Set<String>> = _installed

    init { refreshInstalled() }

    fun refreshInstalled() {
        viewModelScope.launch { _installed.value = useCases.installed() }
    }

    private val answers = ConcurrentHashMap<Pair<String, String>, String>()

    /**
     * Picks [code] (null: Don't translate). App text switches at once; the language is then made ready for names and
     * comments. [onDone] closes the sheet: straight away when nothing had to download, after a moment on Ready, never on Failed.
     */
    fun pick(code: String?, onDone: () -> Unit) {
        viewModelScope.launch {
            useCases.setTarget(code)
            if (code == null) {
                _download.value = LanguageDownload.Idle
                onDone()
            } else prepare(code, onDone)
        }
    }

    fun retry(code: String, onDone: () -> Unit) {
        viewModelScope.launch { prepare(code, onDone) }
    }

    private suspend fun prepare(code: String, onDone: () -> Unit) {
        _download.value = LanguageDownload.Working(code)
        when (useCases.prepare(code)) {
            ModelResult.ALREADY_INSTALLED -> { _download.value = LanguageDownload.Idle; onDone() }
            ModelResult.DOWNLOADED -> {
                refreshInstalled()
                _download.value = LanguageDownload.Ready(code)
                delay(READY_MS)
                _download.value = LanguageDownload.Idle
                onDone()
            }
            ModelResult.FAILED -> _download.value = LanguageDownload.Failed(code)
        }
    }

    /** What [text] was translated to earlier, or null when it hasn't been. */
    fun cached(text: String, target: String): String? = answers[target to text]

    suspend fun translate(text: String, target: String): TranslationResult {
        answers[target to text]?.let { return TranslationResult.Done(it) }
        return useCases.translate(text, target).also { if (it is TranslationResult.Done) answers[target to text] = it.text }
    }

    private companion object {
        const val READY_MS = 700L
    }
}
