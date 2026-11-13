package com.sclera.sclerago.data.online

import com.sclera.sclerago.data.mapper.toCurrentAccess
import com.sclera.sclerago.data.remote.adc.AdcApi
import com.sclera.sclerago.di.ApplicationScope
import com.sclera.sclerago.domain.model.CurrentAccess
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.repository.CurrentAccessRepository
import com.sclera.sclerago.domain.repository.UserDataCleaner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The caller's group and permissions (`GET /me`), fetched once after each sign-in without blocking anything;
 * a failure leaves [access] null. It is for showing and gating UI only: the server checks every call.
 * Held in memory only (nothing is persisted).
 */
@Singleton
class CurrentAccessRepositoryImpl @Inject constructor(
    private val api: AdcApi,
    session: OnlineSession,
    @ApplicationScope scope: CoroutineScope,
) : CurrentAccessRepository, UserDataCleaner {
    private val state = MutableStateFlow<CurrentAccess?>(null)
    override val access: StateFlow<CurrentAccess?> = state

    init {
        scope.launch {
            session.user.collectLatest { user ->
                state.value = null
                if (user != null) {
                    try {
                        state.value = api.me().toCurrentAccess()
                    } catch (_: OnlineException) {
                        // ignored: the access stays unknown
                    }
                }
            }
        }
    }

    override fun clear() {
        state.value = null
    }
}
