package com.sclera.sclerago.presentation.assets

import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.ClassificationResult
import com.sclera.sclerago.domain.model.ClassifyDetails
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.JacsSuggestion
import com.sclera.sclerago.domain.model.MasterFormat
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.TypeSuggestion
import com.sclera.sclerago.domain.model.UNCLASSIFIED
import com.sclera.sclerago.domain.model.formatFor
import com.sclera.sclerago.domain.model.typeFieldName
import com.sclera.sclerago.domain.usecase.asset.ClassifyAssetUseCases
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.toUiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the "Suggest type" sheet is: asking the server, showing what it suggests, or failed. */
enum class ClassifyPhase { LOADING, RESULTS, ERROR }

/**
 * The "Suggest type" sheet: the server's answer, the type picked ([picked], a master format id), the JACS codes for that
 * type and the one picked, and whether what the photos said fills an empty manufacturer and model.
 */
data class ClassifyState(
    val phase: ClassifyPhase,
    val result: ClassificationResult? = null,
    val picked: String? = null,
    val codes: List<JacsSuggestion> = emptyList(),
    val code: String? = null,
    /** The picked type's JACS codes are being asked for. */
    val codesLoading: Boolean = false,
    val useReading: Boolean = true,
    val error: UiText? = null,
) {
    val pickedType: TypeSuggestion? get() = result?.suggestions?.firstOrNull { it.masterFormatId == picked }
}

/** What the classifier adds to the details screen: the sheet (null: closed) and the chosen type's fields the asset lacks. */
data class ClassifierUi(val sheet: ClassifyState? = null, val typeFields: List<FieldCfg> = emptyList())

/**
 * Classifying the open asset, for its view model (runs in its [scope]): the "Suggest type" sheet, which asks the server
 * what the asset is and keeps the picks, and the fields of the type chosen in the form that the asset doesn't have yet
 * (shown after its own until saving makes them).
 */
internal class AssetClassifier(private val useCases: ClassifyAssetUseCases, private val scope: CoroutineScope) {
    private val sheet = MutableStateFlow<ClassifyState?>(null)
    private val typeFields = MutableStateFlow<List<FieldCfg>>(emptyList())
    val ui: Flow<ClassifierUi> = combine(sheet, typeFields) { s, f -> ClassifierUi(s, f) }

    private var sheetJob: Job? = null
    private var fieldsJob: Job? = null
    private var refreshed = false
    private var details = ClassifyDetails()

    /** Forgets the sheet and the type fields (another asset opened, or the screen closed). */
    fun reset() {
        close()
        fieldsJob?.cancel()
        typeFields.value = emptyList()
    }

    /** Keeps every type's fields of the property on the device, once, so a type picked offline shows its fields. */
    fun refreshOnce() {
        if (refreshed) return
        refreshed = true
        scope.launch { useCases.refreshTypeFields() }
    }

    /**
     * Shows the fields of the type the form [values] choose (from [formats]) that are not among the asset's [fields];
     * none for no type, or a type the library lacks.
     */
    fun showTypeFields(formats: List<MasterFormat>, fields: List<FieldCfg>, values: Map<String, String>) {
        val format = formats.formatFor(values)?.id
        fieldsJob?.cancel()
        if (format == null) {
            typeFields.value = emptyList()
            return
        }
        val taken = fields.filter { typeFieldName(it.key) == null }.map { it.label }
        fieldsJob = scope.launch { typeFields.value = useCases.typeFields(format, taken) }
    }

    fun clearTypeFields() {
        fieldsJob?.cancel()
        typeFields.value = emptyList()
    }

    /**
     * Opens the sheet and asks what the asset is from its [media] and the form: name, manufacturer, model and serial
     * number, plus up to [MAX_OTHER_DETAILS] other filled [fields] as hints ([value] reads the form).
     */
    fun open(fields: List<FieldCfg>, value: (String) -> String, media: List<Media>) {
        val other = fields
            .filter { it.key !in NOT_OTHER && typeFieldName(it.key) == null }
            .mapNotNull { f -> value(f.key).trim().takeIf { it.isNotEmpty() }?.let { f.label to it } }
            .take(MAX_OTHER_DETAILS).toMap()
        details = ClassifyDetails(value("name"), value("make"), value("model"), value("serial"), other)
        sheetJob?.cancel()
        sheet.value = ClassifyState(ClassifyPhase.LOADING)
        sheetJob = scope.launch {
            sheet.value = try {
                val r = useCases.suggest(details, media)
                ClassifyState(ClassifyPhase.RESULTS, r, r.suggestions.firstOrNull()?.masterFormatId, r.jacsCodes, r.jacsCodes.firstOrNull()?.code)
            } catch (e: OnlineException) {
                ClassifyState(ClassifyPhase.ERROR, error = e.error.classifyText())
            }
        }
    }

    /** Picks the type [masterFormatId]; the codes the server gave are the first type's, so another type's are asked for. */
    fun pickType(masterFormatId: String) {
        val s = sheet.value ?: return
        val r = s.result ?: return
        if (masterFormatId == s.picked) return
        sheetJob?.cancel()
        if (masterFormatId == r.suggestions.firstOrNull()?.masterFormatId) {
            sheet.update { it?.copy(picked = masterFormatId, codes = r.jacsCodes, code = r.jacsCodes.firstOrNull()?.code, codesLoading = false) }
            return
        }
        sheet.update { it?.copy(picked = masterFormatId, codes = emptyList(), code = null, codesLoading = true) }
        sheetJob = scope.launch {
            val codes = try {
                useCases.suggest(details, emptyList(), masterFormatId).jacsCodes
            } catch (_: OnlineException) {
                // The type can still be applied; the JACS code is then left as it is.
                emptyList()
            }
            sheet.update { cur -> if (cur?.picked == masterFormatId) cur.copy(codes = codes, code = codes.firstOrNull()?.code, codesLoading = false) else cur }
        }
    }

    /** Picks the JACS code [code], or none when it is already picked. */
    fun pickCode(code: String) = sheet.update { cur -> cur?.copy(code = code.takeIf { it != cur.code }) }

    fun toggleReading() = sheet.update { it?.copy(useReading = !it.useReading) }

    fun close() {
        sheetJob?.cancel()
        sheet.value = null
    }

    /**
     * The form values the picks set, given the form's [value] by key: the type's system, asset and sub type, the JACS code
     * picked, and (when ticked) the manufacturer and model read off the photos where the form has none. Null without a type.
     */
    fun chosenValues(value: (String) -> String): Map<String, String>? {
        val s = sheet.value ?: return null
        val type = s.pickedType ?: return null
        return buildMap {
            put("sys", type.systemType)
            put("typ", type.assetType ?: UNCLASSIFIED)
            put("sub", type.subtype ?: UNCLASSIFIED)
            s.code?.let { put("jacs", it) }
            val read = s.result?.read
            if (s.useReading && read != null) {
                read.manufacturer?.takeIf { value("make").isBlank() }?.let { put("make", it) }
                read.model?.takeIf { value("model").isBlank() }?.let { put("model", it) }
            }
        }
    }

    /** With no photo and nothing typed the server has nothing to go on (the asset need not be synced). */
    private fun OnlineError.classifyText(): UiText = if (this == OnlineError.Invalid) UiText.Res(R.string.classify_need_input) else toUiText()

    private companion object {
        /** Sent as themselves (or are what is being suggested), not among the other details. */
        val NOT_OTHER = setOf("name", "make", "model", "serial", "sys", "typ", "sub", "jacs")

        /** How many other filled fields a suggestion request carries as hints. */
        const val MAX_OTHER_DETAILS = 20
    }
}
