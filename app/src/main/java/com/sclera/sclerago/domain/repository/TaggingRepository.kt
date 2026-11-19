package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.TagCheck
import com.sclera.sclerago.domain.model.TagUpdate
import com.sclera.sclerago.domain.model.TagValue
import com.sclera.sclerago.domain.model.TagWrite

/** Tags live on the Sclera server. Every call can throw [com.sclera.sclerago.domain.model.OnlineException] or [com.sclera.sclerago.domain.model.TaggingException]. */
interface TaggingRepository {
    /** Asks the server whether [tag] exists, and who holds it. */
    suspend fun check(tag: TagValue, asset: String, property: String): TagCheck

    /** Adds a customer's own tag the server has never seen. */
    suspend fun register(tag: TagValue, asset: String, property: String)

    /** Writes the tag to the asset and answers with how many tags each asset now holds. */
    suspend fun write(write: TagWrite): TagUpdate
}
