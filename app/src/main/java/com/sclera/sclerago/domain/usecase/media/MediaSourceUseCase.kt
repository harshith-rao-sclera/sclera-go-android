package com.sclera.sclerago.domain.usecase.media

import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.MediaSource
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.repository.MediaContentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile
import javax.inject.Inject

/**
 * Where [Media] of an asset is shown from, database first: its database copy when there is one; otherwise the server's,
 * fetched once (while it loads, [MediaSource.Loading]). It keeps looking at the database, and moves to the database copy
 * as soon as the sync brings it down (then it is done). A fetch that failed for want of a connection is tried again the
 * next time it looks; one the server refused isn't. A video's [preview] isn't fetched: the server has no smaller version
 * of a video, and a thumbnail isn't worth downloading the whole of one.
 */
class MediaSourceUseCase @Inject constructor(private val repository: MediaContentRepository) {
    operator fun invoke(assetId: String, media: Media, preview: Boolean): Flow<MediaSource> = flow {
        var server: MediaSource? = null
        var retry = !(preview && media.video)
        var first = true
        emitAll(
            repository.localChanges().transformWhile {
                if (repository.hasLocalCopy(media)) {
                    emit(MediaSource.Local(media.uri))
                    return@transformWhile false
                }
                if (server !is MediaSource.Server && retry) {
                    if (first) emit(MediaSource.Loading)
                    server = try {
                        MediaSource.Server(repository.serverCopy(assetId, media, preview))
                    } catch (e: OnlineException) {
                        if (e.error != OnlineError.Offline) retry = false
                        MediaSource.Missing
                    }
                }
                first = false
                emit(server ?: MediaSource.Missing)
                true
            },
        )
    }
        // Anything else that goes wrong (the database closing as the property changes) leaves the placeholder.
        .catch { emit(MediaSource.Missing) }
        .distinctUntilChanged()
}
