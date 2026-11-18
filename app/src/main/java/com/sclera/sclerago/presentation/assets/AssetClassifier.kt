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
import kotlinx.coroutines.flow.first
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

    /** The asset asked about when the server has it as saved (its own answers and photos are used); null otherwise. */
    private var assetId: String? = null

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
    fun open(fields: List<FieldCfg>, value: (String) -> String, media: List<Media>, assetId: String? = null) {
        this.assetId = assetId
        val other = fields
            .filter { it.key !in NOT_OTHER && typeFieldName(it.key) == null }
            .mapNotNull { f -> value(f.key).trim().takeIf { it.isNotEmpty() }?.let { f.label to it } }
            .take(MAX_OTHER_DETAILS).toMap()
        details = ClassifyDetails(value("name"), value("make"), value("model"), value("serial"), other)
        sheetJob?.cancel()
        sheet.value = ClassifyState(ClassifyPhase.LOADING)
        sheetJob = scope.launch {
            sheet.value = try {
                // Nothing is picked yet: a pick goes straight into the form, so the user makes it.
                ClassifyState(ClassifyPhase.RESULTS, useCases.suggest(details, media, assetId = assetId))
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
            sheet.update { it?.copy(picked = masterFormatId, codes = r.jacsCodes, code = null, codesLoading = false) }
            return
        }
        sheet.update { it?.copy(picked = masterFormatId, codes = emptyList(), code = null, codesLoading = true) }
        sheetJob = scope.launch {
            val codes = try {
                useCases.suggest(details, emptyList(), masterFormatId, assetId).jacsCodes
            } catch (_: OnlineException) {
                // The type can still be applied; the JACS code is then left as it is.
                emptyList()
            }
            sheet.update { cur -> if (cur?.picked == masterFormatId) cur.copy(codes = codes, code = null, codesLoading = false) else cur }
        }
    }

    /**
     * The JACS code that goes with the picked type [masterFormatId]: its likeliest, once its codes are known (they come
     * with the first type, and are asked for another). It becomes the picked code; null when the type has none, or
     * another type was picked meanwhile.
     */
    suspend fun likeliestCode(masterFormatId: String): String? {
        val s = sheet.first { it == null || it.picked != masterFormatId || !it.codesLoading }
        if (s == null || s.picked != masterFormatId) return null
        val code = s.codes.firstOrNull()?.code ?: return null
        sheet.update { cur -> if (cur?.picked == masterFormatId) cur.copy(code = code) else cur }
        return code
    }

    fun close() {
        sheetJob?.cancel()
        sheet.value = null
    }

    /** The picked type's system, asset and sub type as form values; null while no type is picked. */
    fun typeValues(): Map<String, String>? {
        val type = sheet.value?.pickedType ?: return null
        return mapOf("sys" to type.systemType, "typ" to (type.assetType ?: UNCLASSIFIED), "sub" to (type.subtype ?: UNCLASSIFIED))
    }

    /** The JACS code picked; null when none is. */
    fun pickedCode(): String? = sheet.value?.code

    /** Picks [code], one of the picked type's JACS codes. */
    fun pickCode(code: String) = sheet.update { cur -> if (cur?.codes?.any { it.code == code } == true) cur.copy(code = code) else cur }

    /** [code], picked in the JACS Code row, becomes the picked type's code when it is one of the type's suggested codes. */
    fun noteCode(code: String) = sheet.update { cur -> if (cur?.codes?.any { it.code == code } == true) cur.copy(code = code) else cur }


    /** With no photo and nothing typed the server has nothing to go on (the asset need not be synced). */
    private fun OnlineError.classifyText(): UiText = if (this == OnlineError.Invalid) UiText.Res(R.string.classify_need_input) else toUiText()

    private companion object {
        /** Sent as themselves (or are what is being suggested), not among the other details. */
        val NOT_OTHER = setOf("name", "make", "model", "serial", "sys", "typ", "sub", "jacs")

        /** How many other filled fields a suggestion request carries as hints. */
        const val MAX_OTHER_DETAILS = 20
    }
}
