package com.sclera.sclerago.presentation.assets

import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.AssetDocument
import com.sclera.sclerago.domain.model.DataException
import com.sclera.sclerago.domain.model.DocumentFileStatus
import com.sclera.sclerago.domain.model.DocumentSource
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.OpenableFile
import com.sclera.sclerago.domain.usecase.asset.AssetDocumentUseCases
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.toUiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The Documents tab as last fetched from the server, and what it is doing. */
data class DocumentsUi(
    val documents: List<AssetDocument> = emptyList(),
    /** Fetching them (the first time, or again after a change). */
    val loading: Boolean = false,
    /**
     * Offline: the server's documents shown are the last list it sent (from the response cache); their files can't be
     * fetched to open, and none can be taken off.
     */
    val offline: Boolean = false,
    /** The last fetch failed for another reason. */
    val failed: Boolean = false,
    /** The document whose file is being fetched to be opened. */
    val opening: String? = null,
)

/**
 * An asset's documents, as the web's Documents tab shows them, read from the server every time (documents are not
 * synced): when the asset opens and when the device comes back online, again after each change, and every few seconds
 * while a web page's PDF is still downloading on the server (for a minute at most). Offline, or for an asset the server
 * doesn't have yet, none show. Opening one fetches its file, then [onOpen] hands it to the screen. What goes wrong is
 * said through [notify].
 */
internal class AssetDocumentsPanel(
    private val useCases: AssetDocumentUseCases,
    private val scope: CoroutineScope,
    private val online: Flow<Boolean>,
    private val notify: suspend (UiText) -> Unit,
    private val onOpen: suspend (OpenableFile) -> Unit,
) {
    private val ui = MutableStateFlow(DocumentsUi())
    val state: StateFlow<DocumentsUi> = ui.asStateFlow()

    private var assetId: String? = null

    /** The server has the asset, so it can have documents. */
    private var onServer = false
    private var loader: Job? = null

    /** Shows asset [id]'s documents ([onServer]: the server has it); null (a new asset, not saved yet) shows none. */
    fun show(id: String?, onServer: Boolean) {
        if (id == assetId && onServer == this.onServer) return
        assetId = id
        this.onServer = onServer
        loader?.cancel()
        ui.value = DocumentsUi(loading = id != null && onServer)
        if (id != null && onServer) loader = scope.launch { follow(id) }
    }

    /** Fetches them again (pull to refresh, or after a change). */
    fun reload() {
        val id = assetId ?: return
        if (!onServer) return
        loader?.cancel()
        loader = scope.launch { follow(id) }
    }

    /** Fetches [document]'s file from the server and opens it. */
    fun open(document: AssetDocument) = fetchFile(document, onOpen)

    /** Fetches [document]'s file from the server and hands it to [then] (the app's own viewer, say). */
    fun fetchFile(document: AssetDocument, then: suspend (OpenableFile) -> Unit) {
        val id = assetId ?: return
        if (ui.value.opening != null) return
        ui.update { it.copy(opening = document.id) }
        scope.launch {
            try {
                then(useCases.open(id, document))
            } catch (e: OnlineException) {
                notify(if (e.error == OnlineError.Offline) e.error.toUiText() else UiText.Res(R.string.doc_open_failed))
            } finally {
                ui.update { it.copy(opening = null) }
            }
        }
    }

    /** Takes [document] off the asset on the server. */
    fun remove(document: AssetDocument) {
        val id = assetId ?: return
        scope.launch {
            try {
                useCases.remove(id, document)
                ui.update { s -> s.copy(documents = s.documents.filterNot { it.id == document.id && it.source == document.source }) }
                notify(UiText.Res(R.string.doc_removed))
            } catch (e: OnlineException) {
                notify(e.error.toUiText())
            } catch (e: DataException) {
                notify(e.error.toUiText())
            }
            reload()
        }
    }

    /**
     * Shows [id]'s documents, again each time the device comes online. Offline the list is still asked for: the response
     * cache answers with the last list the server sent (none when it never sent one), and the tab says it is offline.
     */
    private suspend fun follow(id: String) {
        online.distinctUntilChanged().collectLatest { on ->
            var polls = 0
            do {
                ui.update { it.copy(loading = true, offline = !on) }
                val pending = fetch(id, on) && on
                if (pending) delay(PENDING_POLL_MS)
            } while (pending && ++polls < MAX_POLLS)
        }
    }

    /** One fetch ([on]: the device is online); true when a web page's PDF is still downloading on the server. */
    private suspend fun fetch(id: String, on: Boolean): Boolean = try {
        val docs = useCases.list(id)
        ui.update { it.copy(documents = docs, loading = false, failed = false, offline = !on) }
        docs.any { it.source == DocumentSource.WEB_PAGE && it.fileStatus == DocumentFileStatus.PENDING }
    } catch (e: OnlineException) {
        val offline = e.error == OnlineError.Offline
        ui.update { it.copy(documents = emptyList(), loading = false, offline = offline, failed = !offline) }
        false
    }

    private companion object {
        /** As the web: while a PDF downloads, look again every 3 seconds, at most 20 times. */
        const val PENDING_POLL_MS = 3_000L
        const val MAX_POLLS = 20
    }
}
