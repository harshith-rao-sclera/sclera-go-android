package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.PhotoRule
import kotlinx.coroutines.flow.Flow

/** The photo rules of the property being worked on (its settings at the server, kept from the last load). */
interface PhotoRulesRepository {
    /** One rule per kind of photo, in the server's order (nameplate, asset, label ID); the server's defaults when unknown. */
    fun rules(): Flow<List<PhotoRule>>
}
