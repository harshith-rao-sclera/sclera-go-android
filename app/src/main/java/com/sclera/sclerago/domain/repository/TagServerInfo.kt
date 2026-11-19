package com.sclera.sclerago.domain.repository

/** Where the Sclera server that holds the physical tags is. */
interface TagServerInfo {
    /** The server's address; an NFC chip whose content starts with it was written by the main VDMS system. */
    val url: String
}
