package com.sclera.sclerago.presentation.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.AssetDetails
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.Source
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.usecase.asset.AssetUseCases
import com.sclera.sclerago.domain.usecase.config.AssetConfigUseCases
import com.sclera.sclerago.domain.validation.AssetCheckInput
import com.sclera.sclerago.domain.validation.AssetChecks
import com.sclera.sclerago.presentation.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Everything the asset details screen shows; [asset] is null while nothing is open (or after it was deleted). */
data class AssetDetailState(
    val asset: Asset? = null,
    val isNew: Boolean = false,
    /** The asset code (null for a new one, which has none yet). */
    val code: String? = null,
    /** Values as last saved (what the form started from). */
    val saved: Map<String, String> = emptyMap(),
    /** Values typed since, by field key. */
    val edits: Map<String, String> = emptyMap(),
    val fields: List<FieldCfg> = emptyList(),
    val details: AssetDetails? = null,
    /** Photos, videos and documents: the asset own ones, plus the ones picked for a new asset. */
    val media: List<Media> = emptyList(),
    /** Picks made while the asset is new (kept until it is created). */
    val newPicks: List<Media> = emptyList(),
    /** The Validation tab checks (none for a new asset). */
    val checks: AssetChecks? = null,
    val savedFlash: Boolean = false,
    /** Fields AI filled in (cleared when the user edits one). */
    val aiFilled: Set<String> = emptySet(),
    val aiBusy: Boolean = false,
    val aiNotice: UiText? = null,
) {
    val values: Map<String, String> get() = saved + edits
    fun value(key: String): String = edits[key] ?: saved[key].orEmpty()
    fun dirty(key: String): Boolean = key in edits && edits[key] != saved[key].orEmpty()
    val dirtyCount: Int get() = edits.keys.count { dirty(it) }

    /** A submitted asset is read-only (until a recollect is requested). */
    val locked: Boolean get() = !isNew && asset?.st == Status.SUBMITTED
    val fromCamera: Boolean get() = asset?.src == Source.CAMERA

    /** The field the nameplate reading was unsure about, until its value is changed. */
    val lowConfidenceKey: String?
        get() {
            val key = details?.lowConfidence?.takeIf { fromCamera } ?: return null
            val original = details.fields[key].orEmpty()
            return key.takeIf { value(key).isNotEmpty() && value(key) == original }
        }
}

/**
 * One asset details screen: the form edits, Save / Submit / Validate, photos, the Validation tab checks
 * and the AI autofill. Creating a new asset and deleting one change what is on screen, so the host of
 * the screen does those (with the values from [AssetDetailState]).
 */
@HiltViewModel
class AssetDetailViewModel @Inject constructor(
    private val useCases: AssetUseCases,
    configUseCases: AssetConfigUseCases,
) : ViewModel() {
    private data class Target(val asset: Asset, val isNew: Boolean, val prefill: Map<String, String>)

    private data class Form(
        val edits: Map<String, String> = emptyMap(),
        val savedFlash: Boolean = false,
        val aiFilled: Set<String> = emptySet(),
        val aiBusy: Boolean = false,
        val aiNotice: UiText? = null,
        val newPicks: List<Media> = emptyList(),
    )

    private val target = MutableStateFlow<Target?>(null)
    private val form = MutableStateFlow(Form())
    private val message = MutableSharedFlow<UiText>(extraBufferCapacity = 8)

    /** Messages to flash after saving, submitting, validating or adding media. */
    val notices: SharedFlow<UiText> = message.asSharedFlow()

    val state: StateFlow<AssetDetailState> =
        combine(target, form, useCases.observe(), configUseCases.observe()) { t, f, catalog, config ->
            if (t == null) return@combine AssetDetailState()
            val asset = if (t.isNew) t.asset
            else catalog.assetsIn(t.asset.prop).firstOrNull { it.id == t.asset.id } ?: return@combine AssetDetailState()
            val details = if (t.isNew) null else useCases.details(asset.id)
            val saved = useCases.formValues(asset.name, t.isNew, details, config.fields, t.prefill) +
                catalog.savedDetails[asset.id].orEmpty()
            val base = AssetDetailState(
                asset = asset, isNew = t.isNew,
                code = if (t.isNew) null else details?.code ?: "SCL-000${400 + asset.id}",
                saved = saved, edits = f.edits, fields = config.fields, details = details,
                media = asset.media + f.newPicks, newPicks = f.newPicks, savedFlash = f.savedFlash,
                aiFilled = f.aiFilled, aiBusy = f.aiBusy, aiNotice = f.aiNotice,
            )
            if (t.isNew) base
            else base.copy(checks = useCases.evaluateChecks(checkInput(base, config.requiredFields.map { it.first }, config.minPhotos)))
        }.stateIn(viewModelScope, SharingStarted.Eagerly, AssetDetailState())

    private fun checkInput(s: AssetDetailState, requiredKeys: List<String>, minPhotos: Int): AssetCheckInput {
        val asset = checkNotNull(s.asset)
        val lowKey = s.lowConfidenceKey
        return AssetCheckInput(
            values = s.values, hasLocation = asset.loc != null, locationLabel = asset.where,
            photoCount = s.media.size, fromCamera = s.fromCamera, minPhotos = minPhotos,
            requiredKeys = requiredKeys.toSet(), lowConfidenceKey = lowKey,
            lowConfidenceLabel = s.fields.firstOrNull { it.key == lowKey }?.label ?: lowKey.orEmpty(),
        )
    }

    /** Shows [asset]; does nothing when that asset is already open, so edits survive recomposition. */
    fun open(asset: Asset, isNew: Boolean, prefill: Map<String, String> = emptyMap()) {
        val current = target.value
        if (current != null && current.asset.id == asset.id && current.isNew == isNew) return
        form.value = Form()
        target.value = Target(asset, isNew, prefill)
    }

    fun close() {
        target.value = null
        form.value = Form()
    }

    fun edit(key: String, value: String) {
        if (state.value.locked) return
        form.update { it.copy(edits = it.edits + (key to value), savedFlash = false, aiFilled = it.aiFilled - key) }
    }

    fun discardEdits() = form.update { it.copy(edits = emptyMap()) }

    /** Keeps the edits and (back) moves the asset to Draft. */
    fun save() {
        val s = state.value
        val id = s.asset?.id ?: return
        if (s.dirtyCount == 0) return
        useCases.saveDetails(id, s.values, Status.DRAFT)
        form.update { it.copy(edits = emptyMap(), savedFlash = true) }
        message.tryEmit(UiText.Res(R.string.asset_saved_draft))
    }

    /** Keeps the edits and locks the asset as Submitted. */
    fun submit() {
        val s = state.value
        val id = s.asset?.id ?: return
        useCases.saveDetails(id, s.values, Status.SUBMITTED)
        form.update { it.copy(edits = emptyMap()) }
        message.tryEmit(UiText.Res(R.string.asset_submitted_notice))
    }

    fun validate() {
        val id = state.value.asset?.id ?: return
        useCases.setStatus(listOf(id), Status.VALIDATED)
        message.tryEmit(UiText.Res(R.string.asset_validated_notice))
    }

    /** Photos, videos or documents added to the asset (kept in the form until a new asset is created). */
    fun attach(media: List<Media>) {
        val s = state.value
        val asset = s.asset ?: return
        if (media.isEmpty()) return
        if (s.isNew) form.update { it.copy(newPicks = it.newPicks + media) }
        else if (useCases.attachMedia(asset.id, media)) message.tryEmit(UiText.Plural(R.plurals.items_added_to_asset, media.size))
    }

    /** Fills the empty fields with suggestions; the result is shown for a moment. */
    fun autofill() {
        val s = state.value
        val asset = s.asset ?: return
        if (form.value.aiBusy || s.locked) return
        form.update { it.copy(aiBusy = true, aiNotice = null) }
        viewModelScope.launch {
            val fills = useCases.autofill(asset.type, s.values)
            form.update {
                it.copy(
                    edits = it.edits + fills, aiFilled = it.aiFilled + fills.keys, savedFlash = false, aiBusy = false,
                    aiNotice = if (fills.isEmpty()) UiText.Res(R.string.detail_ai_nothing) else UiText.Plural(R.plurals.ai_filled, fills.size),
                )
            }
            delay(NOTICE_MS)
            form.update { it.copy(aiNotice = null) }
        }
    }

    private companion object {
        const val NOTICE_MS = 2600L
    }
}
