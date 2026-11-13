package com.sclera.sclerago.domain.usecase.auth

import com.sclera.sclerago.domain.repository.UserDataCleaner
import javax.inject.Inject

/**
 * Wipes every trace of the user (assets, people, drafts, photos, web data) when they sign out or their
 * session ends, so the next person to sign in on this device starts clean.
 */
class ClearUserDataUseCase @Inject constructor(private val cleaners: Set<@JvmSuppressWildcards UserDataCleaner>) {
    operator fun invoke() = cleaners.forEach { cleaner -> runCatching { cleaner.clear() } }
}
