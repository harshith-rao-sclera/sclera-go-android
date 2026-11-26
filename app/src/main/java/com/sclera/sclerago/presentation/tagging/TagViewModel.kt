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

    /** The QR icon's popover: the user says whether the code goes on something movable or fixed in place, before it is scanned. */
    data object ChoosingPlacement : TagPhase

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

    /** The placement picked in the QR popover for the code being scanned: movable (stored as 1) or fixed (0). */
    private var isMovable = false

    /** What each asset's data last said it carries (the assets table's tag columns), by asset id. */
    private val savedByAsset = mutableMapOf<String, Set<TagKind>>()

    /**
     * Kinds tagged here that the asset's data doesn't show yet, by asset id: shown until the data next changes (the
     * tagged flag written after the tag, or a sync), which then decides alone.
     */
    private val taggedHere = mutableMapOf<String, Set<TagKind>>()

    private class Target(val asset: String, val property: String)
    private class Pending(val tag: TagValue, val placement: TagPlacement, val photo: TagPhoto?)

    /**
     * Points the flow at [asset]; a different asset starts fresh. [saved] are the kinds its data says it carries now (the
     * assets table's tag columns). It is called again whenever they change (a tag written here, one moved to or from
     * another asset, a sync), so a kind the data no longer has stops showing as tagged, even while the asset is open.
     */
    fun open(asset: String, property: String, saved: Set<TagKind> = emptySet()) {
        // New word from the data: it replaces what was tagged here and not yet written to it.
        if (savedByAsset[asset] != saved) taggedHere.remove(asset)
        savedByAsset[asset] = saved
        if (target?.asset == asset) {
            _state.update { it.copy(tagged = taggedOf(asset)) }
            return
        }
        job?.cancel()
        pending = null
        target = Target(asset, property)
        _state.update { TagUiState(tagged = taggedOf(asset)) }
    }

    /** The kinds the Tags bar shows for [asset]: what its data says, and what was tagged here that the data doesn't show yet. */
    private fun taggedOf(asset: String): Set<TagKind> = savedByAsset[asset].orEmpty() + taggedHere[asset].orEmpty()

    /**
     * The user picked [kind]. Only a QR code can be tagged offline; the others say so instead. A QR code first asks
     * where it goes (movable or fixed, [choosePlacement]). [nfcProblem]: why the phone can't read NFC now (none, or
     * switched off), checked before the NFC dialog opens so it never flashes up.
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
        isMovable = false
        _state.update {
            it.copy(
                phase = when (kind) {
                    TagKind.QR -> TagPhase.ChoosingPlacement
                    TagKind.NFC -> TagPhase.ReadingNfc
                    TagKind.BARCODE -> TagPhase.Scanning(kind)
                },
            )
        }
    }

    /**
     * The user picked the QR code's placement in the popover: [movable] for something that can be moved, else fixed. It is
     * written with the tag (the `movable` column: 1 or 0). An asset that already has a QR code then asks before it is replaced.
     */
    fun choosePlacement(movable: Boolean) {
        if (_state.value.phase != TagPhase.ChoosingPlacement) return
        isMovable = movable
        _state.update { it.copy(phase = if (TagKind.QR in it.tagged) TagPhase.ConfirmingRescan else TagPhase.Scanning(TagKind.QR)) }
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
                val (tag, placement) = tags.build(kind, scan, isMovable = kind == TagKind.QR && isMovable)
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
        taggedHere[asset] = taggedHere[asset].orEmpty() + kind
        _state.update { it.copy(phase = TagPhase.Idle, tagged = taggedOf(asset), notice = TagNotice.Tagged(kind)) }
    }

    private fun fail(message: UiText, nfcSettings: Boolean = false) {
        pending = null
        _state.update { it.copy(phase = TagPhase.Idle, notice = TagNotice.Refused(message, nfcSettings)) }
    }

    override fun onCleared() {
        job?.cancel()
    }
}
