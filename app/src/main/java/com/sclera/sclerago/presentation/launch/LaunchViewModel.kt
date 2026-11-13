package com.sclera.sclerago.presentation.launch

import androidx.lifecycle.ViewModel
import com.sclera.sclerago.domain.usecase.launch.ShouldPlayIntroUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** Whether the launch animation still has to play (once per cold start, unless animations are off). */
@HiltViewModel
class LaunchViewModel @Inject constructor(private val shouldPlayIntro: ShouldPlayIntroUseCase) : ViewModel() {
    private val _playIntro = MutableStateFlow(shouldPlayIntro(alreadyPlayed = false))
    val playIntro: StateFlow<Boolean> = _playIntro.asStateFlow()

    fun onIntroFinished() {
        _playIntro.value = shouldPlayIntro(alreadyPlayed = true)
    }
}
