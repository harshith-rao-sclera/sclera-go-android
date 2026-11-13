package com.sclera.sclerago.data.repository

import com.sclera.sclerago.domain.model.AssetType
import com.sclera.sclerago.domain.repository.AutofillRepository
import com.sclera.sclerago.services.ai.aiAutofill
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * Suggestions from typical values per asset type, worked out on the device (nothing is sent anywhere),
 * after the short pause a real service would take. A server-side service would replace this class.
 */
class LocalAutofillRepository @Inject constructor() : AutofillRepository {
    override suspend fun suggest(type: AssetType, values: Map<String, String>): Map<String, String> {
        delay(THINKING_MS)
        return aiAutofill(type, values)
    }

    private companion object {
        const val THINKING_MS = 1100L
    }
}
