package com.sclera.sclerago.presentation.tagging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.DataException
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.NfcFailure
import com.sclera.sclerago.domain.model.TagKind
import com.sclera.sclerago.domain.model.TagScannerFailure
import com.sclera.sclerago.domain.model.TagPhoto
import com.sclera.sclerago.domain.model.TagPlacement
import com.sclera.sclerago.domain.model.TagValue
import com.sclera.sclerago.domain.model.TaggingError
import com.sclera.sclerago.domain.model.TaggingException
import com.sclera.sclerago.domain.repository.NetworkMonitor
import com.sclera.sclerago.domain.usecase.tagging.TagScan
import com.sclera.sclerago.domain.usecase.tagging.TagUseCases
import com.sclera.sclerago.presentation.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where the tagging flow is, for the screen to show. */
sealed interface TagPhase {
    data object Idle : TagPhase

    /** The asset already has a QR tag: ask before it is replaced. */
    data object ConfirmingRescan : TagPhase

    /** The camera is open for a QR code or a barcode. */
    data class Scanning(val kind: TagKind) : TagPhase

    /** Waiting for an NFC tag to be held to the phone. */
    data object ReadingNfc : TagPhase
    data object Working : TagPhase

    /** The tag is on another draft ([holder] is its name when this device knows it); the user decides whether to retag it here. */
    data class ConfirmingMove(val holder: String?) : TagPhase
}

/** A short message over the details: the tag went on, or why it didn't (the old app's toasts). */
sealed interface TagNotice {
    data class Tagged(val kind: TagKind) : TagNotice
    /** [nfcSettings]: NFC is switched off, so the message offers to open the phone's NFC settings. */
    data class Refused(val message: UiText, val nfcSettings: Boolean = false) : TagNotice
}

data class TagUiState(
    val phase: TagPhase = TagPhase.Idle,
    /** The kinds the open asset was tagged with in this session. */
    val tagged: Set<TagKind> = emptySet(),
    /** The toast to show; cleared by [TagViewModel.toastShown]. */
    val notice: TagNotice? = null,
)

/**
 * Runs the tagging flow for one asset at a time: what was scanned, what the server and the rules said, and whether the user has
 * to confirm moving the tag. It knows nothing about the camera or NFC; the screen passes in what they read.
 */
@HiltViewModel
class TagViewModel @Inject constructor(
    private val tags: TagUseCases,
    private val network: NetworkMonitor,
) : ViewModel() {
    private val _state = MutableStateFlow(TagUiState())
    val state: StateFlow<TagUiState> = _state.asStateFlow()

    private var target: Target? = null
    private var pending: Pending? = null
    private var job: Job? = null

    /** Kinds tagged this session, by asset id. */
    private val taggedByAsset = mutableMapOf<String, Set<TagKind>>()

    private class Target(val asset: String, val property: String)
    private class Pending(val tag: TagValue, val placement: TagPlacement, val photo: TagPhoto?)

    /**
     * Points the flow at [asset]; a different asset starts fresh. [saved] are the kinds its synced data already says
     * it carries; they show as tagged along with the ones tagged in this session.
     */
    fun open(asset: String, property: String, saved: Set<TagKind> = emptySet()) {
        saved.takeIf { it.isNotEmpty() }?.let { taggedByAsset[asset] = taggedByAsset[asset].orEmpty() + it }
        if (target?.asset == asset) {
            _state.update { it.copy(tagged = taggedByAsset[asset].orEmpty()) }
            return
        }
        job?.cancel()
        pending = null
        target = Target(asset, property)
        _state.update { TagUiState(tagged = taggedByAsset[asset].orEmpty()) }
    }

    /**
     * The user picked [kind]. Only a QR code can be tagged offline; the others say so instead. [nfcProblem]: why the
     * phone can't read NFC now (none, or switched off), checked before the NFC dialog opens so it never flashes up.
     */
    fun start(kind: TagKind, nfcProblem: NfcFailure? = null) {
        val now = target ?: return
        // Only someone who may change the asset tags or retags it, any kind: an admin any asset, even a submitted or signed-off
        // one; anyone else one that is theirs or nobody's and not submitted; nobody one in Managed Assets (the server's rule).
        if (!tags.canTag(now.asset)) return
        // A QR code is tagged in the phone's own tables when offline; barcodes and NFC tags are checked on the server.
        if (kind != TagKind.QR && !network.isOnline()) return fail(UiText.Res(R.string.tag_error_offline))
        if (kind == TagKind.NFC && nfcProblem != null) return nfcFailed(nfcProblem)
        pending = null
        _state.update {
            it.copy(
                phase = when {
                    kind == TagKind.QR && TagKind.QR in it.tagged -> TagPhase.ConfirmingRescan
                    kind == TagKind.NFC -> TagPhase.ReadingNfc
                    else -> TagPhase.Scanning(kind)
                },
            )
        }
    }

    fun confirmRescan() = _state.update { it.copy(phase = TagPhase.Scanning(TagKind.QR)) }

    /** Closes whatever is open and forgets what was waiting. */
    fun cancel() {
        job?.cancel()
        pending = null
        _state.update { it.copy(phase = TagPhase.Idle) }
    }

    /** The scanner read a code. */
    fun scanned(kind: TagKind, scan: TagScan) {
        job?.cancel()
        _state.update { it.copy(phase = TagPhase.Working) }
        job = viewModelScope.launch {
            try {
                val (tag, placement) = tags.build(kind, scan, isMovable = false)
                attach(tag, placement, scan.photo?.let(::TagPhoto), isMoveConfirmed = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e.tagFailureText())
            }
        }
    }

    /** The NFC reader read a chip. */
    fun nfcRead(text: String, isBlank: Boolean) {
        job?.cancel()
        _state.update { it.copy(phase = TagPhase.Working) }
        job = viewModelScope.launch {
            try {
                attach(tags.build.nfc(text, isBlank), TagPlacement(), null, isMoveConfirmed = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e.tagFailureText())
            }
        }
    }

    fun nfcFailed(failure: NfcFailure) = fail(
        UiText.Res(
            when (failure) {
                NfcFailure.UNAVAILABLE -> R.string.tag_error_nfc_unavailable
                NfcFailure.DISABLED -> R.string.tag_error_nfc_disabled
                NfcFailure.TIMED_OUT -> R.string.tag_error_nfc_timed_out
                NfcFailure.FAILED -> R.string.tag_error_nfc_failed
            },
        ),
        nfcSettings = failure == NfcFailure.DISABLED,
    )

    /** The camera couldn't start. */
    fun scannerFailed(failure: TagScannerFailure) = fail(
        UiText.Res(if (failure == TagScannerFailure.CAMERA_DENIED) R.string.tag_error_camera_denied else R.string.tag_error_camera_unavailable),
    )

    /** The user agreed to take the tag from the other asset. */
    fun confirmMove() {
        val waiting = pending ?: return
        _state.update { it.copy(phase = TagPhase.Working) }
        job?.cancel()
        job = viewModelScope.launch { attach(waiting.tag, waiting.placement, waiting.photo, isMoveConfirmed = true) }
    }

    fun toastShown() = _state.update { it.copy(notice = null) }

    private suspend fun attach(tag: TagValue, placement: TagPlacement, photo: TagPhoto?, isMoveConfirmed: Boolean) {
        val now = target ?: return
        pending = Pending(tag, placement, photo)
        try {
            tags.attach(
                tag, now.asset, tags.property(now.property), placement, photo, isMoveConfirmed,
                isSubmitted = { id -> tags.isSubmitted(id) },
            )
            succeeded(now.asset, tag.kind)
        } catch (e: CancellationException) {
            throw e
        } catch (e: TaggingException) {
            if (e.error == TaggingError.NEEDS_MOVE) {
                _state.update { it.copy(phase = TagPhase.ConfirmingMove(e.heldBy?.let(tags.holderName::invoke))) }
            } else {
                fail(e.tagFailureText(tags.holderName::invoke))
            }
        } catch (e: OnlineException) {
            fail(e.tagFailureText())
        } catch (e: DataException) {
            // Refused before anything was written: the user may not change this asset (any more).
            fail(e.tagFailureText())
        } catch (e: Exception) {
            fail(UiText.Res(R.string.tag_error_generic))
        }
    }

    private fun succeeded(asset: String, kind: TagKind) {
        pending = null
        val kinds = taggedByAsset[asset].orEmpty() + kind
        taggedByAsset[asset] = kinds
        _state.update { it.copy(phase = TagPhase.Idle, tagged = kinds, notice = TagNotice.Tagged(kind)) }
    }

    private fun fail(message: UiText, nfcSettings: Boolean = false) {
        pending = null
        _state.update { it.copy(phase = TagPhase.Idle, notice = TagNotice.Refused(message, nfcSettings)) }
    }

    override fun onCleared() {
        job?.cancel()
    }
}
