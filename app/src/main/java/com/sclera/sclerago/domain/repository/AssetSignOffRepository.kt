package com.sclera.sclerago.domain.repository

import com.sclera.sclerago.domain.model.SignOffOutcome

/**
 * An admin signs submitted assets off without a validation run (the server's `POST …/assets/bulk/validate`). Writes
 * throw [com.sclera.sclerago.domain.model.DataException] when they cannot be made.
 */
interface AssetSignOffRepository {
    /**
     * Marks the submitted ones of [ids] as validated, with a line in each one's history. Drafts and exceptions are left
     * as they are (named in [SignOffOutcome.notSubmitted]); assets already validated are left alone.
     */
    suspend fun markValidated(ids: Collection<String>): SignOffOutcome
}
