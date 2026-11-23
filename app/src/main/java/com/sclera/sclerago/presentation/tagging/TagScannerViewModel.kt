package com.sclera.sclerago.presentation.tagging

import androidx.lifecycle.ViewModel
import com.sclera.sclerago.services.tagging.QrDirectionMeter
import com.sclera.sclerago.services.tagging.QrDirectionMeterFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Hands the camera view a direction meter for each QR scan; the view releases it with the camera. */
@HiltViewModel
class TagScannerViewModel @Inject constructor(private val meters: QrDirectionMeterFactory) : ViewModel() {
    fun newMeter(): QrDirectionMeter = meters.create()
}
