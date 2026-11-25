package com.sclera.sclerago.domain.model

/** Where one of an asset's media files is shown from: the offline database first, else the server. */
sealed interface MediaSource {
    /** The file is on the device (the offline database, or a file of the app's own): open [uri]. */
    data class Local(val uri: String) : MediaSource

    /** The database doesn't have the file yet; [uri] opens the copy fetched from the server. */
    data class Server(val uri: String) : MediaSource

    /** Not on the device, and being fetched from the server. */
    data object Loading : MediaSource

    /** Not on the device, and the server's couldn't be fetched (offline, or the server has none). */
    data object Missing : MediaSource
}
