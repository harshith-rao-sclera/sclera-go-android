package com.sclera.sclerago.domain.usecase.asset

import com.sclera.sclerago.domain.model.AssetDocument
import com.sclera.sclerago.domain.model.AuthState
import com.sclera.sclerago.domain.model.DocumentSource
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.OpenableFile
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.repository.AccessRepository
import com.sclera.sclerago.domain.repository.AssetDocumentsRepository
import com.sclera.sclerago.domain.repository.AssetRepository
import com.sclera.sclerago.domain.repository.AuthRepository
import com.sclera.sclerago.domain.usecase.access.PermissionGuard
import javax.inject.Inject

/*
 * An asset's Documents tab. Documents are not synced: every one (added to the asset, the file it was read from, web
 * pages, manuals) is the server's, so reading or changing them throws OnlineException (Offline without a connection), and
 * a change throws DataException when the asset may not be changed.
 */

/**
 * The asset's documents, from the server: the ones added to it, the file it was read from, the web pages its values came
 * from and its model's manuals. Each says who added it as far as this device knows (the signed-in
 * user's own name, else the people list) and whether the signed-in user may take it off: a document added to the asset
 * or a web page by anyone who can collect; never the file it was read from, nor a model's manual (every asset of that
 * model has it).
 * Throws OnlineException when they can't be read.
 */
class GetAssetDocumentsUseCase @Inject constructor(
    private val repo: AssetDocumentsRepository,
    private val access: AccessRepository,
    private val auth: AuthRepository,
    private val guard: PermissionGuard,
) {
    suspend operator fun invoke(assetId: String): List<AssetDocument> {
        val docs = repo.list(assetId)
        val collect = guard.allows(Permission.COLLECT)
        val me = guard.userId()
        val myName = (auth.state.value as? AuthState.SignedIn)?.session?.displayName?.takeIf { it.isNotBlank() }
        val names = access.people.value.associate { it.id to it.name }
        return docs.map { d ->
            val by = d.uploadedBy
            d.copy(
                uploaderName = by?.let { if (it == me) myName else null } ?: by?.let(names::get),
                removable = when (d.source) {
                    DocumentSource.ATTACHMENT, DocumentSource.WEB_PAGE -> collect
                    // The file the asset was read from is never removed here; nor is a model's manual (every asset of
                    // that model has it).
                    DocumentSource.SOURCE_FILE, DocumentSource.MANUAL -> false
                },
            )
        }
    }
}

/** A document's file, fetched from the server to be opened (a web page or manual only when its PDF is kept). */
class OpenAssetDocumentUseCase @Inject constructor(private val repo: AssetDocumentsRepository) {
    suspend operator fun invoke(assetId: String, document: AssetDocument): OpenableFile {
        if (!document.hasFile) throw OnlineException(OnlineError.NotFound)
        return repo.download(assetId, document)
    }
}

/**
 * Takes [document] off asset [assetId], as the server allows: a document added to it or a web page by anyone who can
 * collect and may change the asset; the uploaded file as [GetAssetDocumentsUseCase] says (the file isn't the asset's,
 * other assets may have been read from it, so whether the asset may be changed isn't asked). Nothing in Managed Assets
 * changes here.
 */
class RemoveAssetDocumentUseCase @Inject constructor(
    private val repo: AssetDocumentsRepository,
    private val assets: AssetRepository,
    private val guard: PermissionGuard,
) {
    suspend operator fun invoke(assetId: String, document: AssetDocument) {
        when (document.source) {
            DocumentSource.ATTACHMENT -> {
                guard.requireOnline(Permission.COLLECT)
                requireUnlocked(assets, guard, listOf(assetId))
                repo.removeAttachment(assetId, document.id)
            }
            DocumentSource.WEB_PAGE -> {
                guard.requireOnline(Permission.COLLECT)
                requireUnlocked(assets, guard, listOf(assetId))
                repo.removeWebPage(assetId, document.id)
            }
            // The file the asset was read from and a model's manual are never removed from the app.
            DocumentSource.SOURCE_FILE, DocumentSource.MANUAL -> throw OnlineException(OnlineError.Forbidden)
        }
    }
}

/** The Documents tab's use cases, bundled for injection. */
class AssetDocumentUseCases @Inject constructor(
    val list: GetAssetDocumentsUseCase,
    val open: OpenAssetDocumentUseCase,
    val remove: RemoveAssetDocumentUseCase,
)
