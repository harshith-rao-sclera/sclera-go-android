package com.sclera.sclerago.presentation.premise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.domain.model.PremiseNotice
import com.sclera.sclerago.domain.usecase.geofence.ObservePremiseNoticeUseCase
import com.sclera.sclerago.domain.usecase.geofence.RefreshPremiseUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The geofence's effect on a collector: why they are view only (for the banner) and when to explain and ask for the
 * location permission (the moment a collector opens a property with geofencing on). Holds no position.
 */
@HiltViewModel
class PremiseViewModel @Inject constructor(
    observeNotice: ObservePremiseNoticeUseCase,
    private val refreshPremise: RefreshPremiseUseCase,
) : ViewModel() {
    val notice: StateFlow<PremiseNotice> = observeNotice().stateIn(viewModelScope, SharingStarted.Eagerly, PremiseNotice.NONE)

    /** The explanation was answered (or put off) and shows again only when asked for from the banner. */
    private val answered = MutableStateFlow(false)

    /** Explain, then ask for, the location permission now. */
    val askLocation: StateFlow<Boolean> =
        combine(notice, answered) { n, done -> n == PremiseNotice.LOCATION_NEEDED && !done }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Back in the foreground: the geofence is checked again. */
    fun onForeground() = refreshPremise()

    /** The banner's Allow: explain again. */
    fun askAgain() {
        answered.value = false
    }

    /** "Not now" on the explanation. */
    fun onNotNow() {
        answered.value = true
    }

    /** Android's prompt was answered: a granted permission lets the geofence be checked at once. */
    fun onPermissionResult(granted: Boolean) {
        answered.value = true
        if (granted) refreshPremise()
    }
}
