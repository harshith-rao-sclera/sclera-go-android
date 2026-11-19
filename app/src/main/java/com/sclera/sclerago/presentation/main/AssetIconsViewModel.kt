package com.sclera.sclerago.presentation.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.domain.model.AssetIcons
import com.sclera.sclerago.domain.usecase.asset.ObserveAssetIconsUseCase
import com.sclera.sclerago.domain.usecase.asset.OpenAssetIconsUseCase
import com.sclera.sclerago.domain.usecase.asset.RefreshAssetIconsUseCase
import com.sclera.sclerago.domain.usecase.property.PropertyDownloadUseCases
import com.sclera.sclerago.presentation.icons.LocalAssetIcons
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The asset icons for the screens: when the app opens the icons are brought up to date (online); whenever the open
 * property changes, those kept on the device show at once and the
 * server's latest are downloaded (online), so every asset shows its type's icon as the server gives it.
 */
@HiltViewModel
class AssetIconsViewModel @Inject constructor(
    observe: ObserveAssetIconsUseCase,
    private val refresh: RefreshAssetIconsUseCase,
    private val open: OpenAssetIconsUseCase,
    downloads: PropertyDownloadUseCases,
) : ViewModel() {
    val icons: StateFlow<AssetIcons> = observe()

    init {
        // When the app opens: the icons themselves (the same for every property), whether or not a property is open.
        viewModelScope.launch { refresh() }
        viewModelScope.launch {
            downloads.observe().map { it.currentId.orEmpty() }.distinctUntilChanged().collectLatest { open(it) }
        }
    }
}

/** Gives the screens in [content] the asset icons ([LocalAssetIcons]). */
@Composable
fun AssetIconsHost(vm: AssetIconsViewModel = hiltViewModel(), content: @Composable () -> Unit) {
    val icons by vm.icons.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalAssetIcons provides icons, content = content)
}
