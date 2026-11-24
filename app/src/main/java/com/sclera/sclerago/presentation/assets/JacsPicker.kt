package com.sclera.sclerago.presentation.assets

import com.sclera.sclerago.presentation.components.pressClickable
import com.sclera.sclerago.presentation.components.PRESSED_CARD
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.JacsCode
import com.sclera.sclerago.domain.model.JacsSuggestion
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.model.SURE_ENOUGH
import com.sclera.sclerago.domain.usecase.asset.JacsUseCases
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.common.toUiText
import com.sclera.sclerago.presentation.components.SkeletonLead
import com.sclera.sclerago.presentation.components.SkeletonList
import com.sclera.sclerago.presentation.components.BottomSheet
import com.sclera.sclerago.presentation.components.OfflineGate
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.SearchField
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The JACS code picker while it is open: what is searched, the codes found so far, and where the next page starts; the
 * codes the AI suggested for the asset head the list (best first, with how sure).
 */
data class JacsPickerState(
    val query: String = "",
    val codes: List<JacsCode> = emptyList(),
    val next: String? = null,
    val loading: Boolean = false,
    val error: UiText? = null,
    val suggested: List<JacsSuggestion> = emptyList(),
)

/** What the JACS code picker's controls do. */
class JacsPickerActions(
    val onQuery: (String) -> Unit = {},
    val onMore: () -> Unit = {},
    val onPick: (code: String) -> Unit = {},
    val onClose: () -> Unit = {},
)

/** Test tags of the JACS code picker. */
object JacsTags {
    const val SHEET = "jacs-picker"
    const val MORE = "jacs-more"
    const val SUGGEST = "jacs-suggest"
    fun code(code: String) = "jacs-code-$code"
}

/**
 * Searching the JACS catalog for the asset's code, for its view model (runs in its [scope]), as the web's picker: a
 * search a moment after typing stops, the next page on request, a page already loading never asked twice and a code
 * never listed twice. The codes the AI suggested for the open asset are kept (until [reset]) to head the list.
 */
internal class JacsPicker(private val useCases: JacsUseCases, private val scope: CoroutineScope) {
    private val picker = MutableStateFlow<JacsPickerState?>(null)
    val state: StateFlow<JacsPickerState?> = picker.asStateFlow()
    private var job: Job? = null

    /** The codes the AI suggested, and the type they were suggested for (null: the type the AI thinks the asset is). */
    private var suggested: List<JacsSuggestion> = emptyList()
    private var suggestedFor: String? = null

    /**
     * Opens the picker; the AI's codes head it while the form's type ([typeId], a master format id) is still the one
     * they were suggested for (codes suggested without a type always do).
     */
    fun open(typeId: String?) {
        val shown = suggested.takeIf { suggestedFor == null || suggestedFor == typeId }.orEmpty()
        picker.value = JacsPickerState(suggested = shown)
        search("", wait = 0)
    }

    /** Keeps the AI's [codes], suggested for the type [typeId] (null: the AI's own idea of it), and opens the picker with them. */
    fun openSuggested(codes: List<JacsSuggestion>, typeId: String?) {
        suggested = codes
        suggestedFor = typeId
        open(typeId)
    }

    /**
     * [code] is one the AI suggested for the type the form has ([typeId]), as the web's `forType`: picking it keeps that
     * type, where a code picked otherwise sets the type from the code.
     */
    fun keepsType(code: String, typeId: String?): Boolean =
        suggestedFor != null && suggestedFor == typeId && suggested.any { it.code == code }

    fun close() {
        job?.cancel()
        picker.value = null
    }

    /** Closes the picker and forgets the AI's codes (another asset opened). */
    fun reset() {
        close()
        suggested = emptyList()
        suggestedFor = null
    }

    fun query(text: String) {
        picker.update { it?.copy(query = text, loading = false) }
        search(text, wait = SEARCH_DELAY_MS)
    }

    fun more() {
        val s = picker.value ?: return
        val next = s.next ?: return
        if (s.loading) return
        job = scope.launch { load(s.query, next) }
    }

    private fun search(text: String, wait: Long) {
        job?.cancel()
        job = scope.launch {
            delay(wait)
            load(text, cursor = null)
        }
    }

    private suspend fun load(text: String, cursor: String?) {
        picker.update { it?.copy(loading = true, error = null) }
        try {
            val page = useCases.search(text, cursor)
            picker.update { cur ->
                // An answer for an older search is dropped.
                if (cur == null || cur.query != text) cur
                else {
                    val codes = if (cursor == null) page.codes else cur.codes + page.codes
                    cur.copy(codes = codes.distinctBy { it.code }, next = page.next, loading = false)
                }
            }
        } catch (e: OnlineException) {
            picker.update { it?.copy(loading = false, error = e.error.toUiText()) }
        }
    }

    private companion object {
        const val SEARCH_DELAY_MS = 300L
    }
}

/**
 * The JACS code picker: search the catalog, each code with its title (the asset's [current] code marked); "Use …"
 * keeps a code typed by hand (also offline), and "No JACS code" clears it. Codes the AI suggested for the asset come
 * first, each with how sure it is.
 */
@Composable
fun JacsPickerSheet(s: JacsPickerState, current: String, actions: JacsPickerActions) {
    BottomSheet(onDismiss = actions.onClose) {
        Column(Modifier.weight(1f, fill = false).testTag(JacsTags.SHEET)) {
            SheetTitleRow(stringResource(R.string.jacs_title), stringResource(R.string.jacs_subtitle), actions.onClose)
            SearchField(
                s.query, actions.onQuery, stringResource(R.string.jacs_search_hint),
                Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp).fillMaxWidth(),
            )
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val typed = s.query.trim()
                Column(Modifier.fillMaxWidth().clip(r(12)).border(1.dp, C.Border, r(12))) {
                    var first = true
                    @Composable
                    fun row(code: String, title: String?, on: Boolean, tag: String) {
                        CodeRow(code, title, on, !first, tag) { actions.onPick(code) }
                        first = false
                    }
                    // The AI's codes first (those matching what is searched), each with how sure; the catalog doesn't list them again.
                    val suggested = s.suggested.filter { typed.isEmpty() || it.code.contains(typed, true) || it.title?.contains(typed, true) == true }
                    suggested.forEach { c ->
                        CodeRow(c.code, c.title, c.code == current, !first, JacsTags.code(c.code), sure = c.confidence) { actions.onPick(c.code) }
                        first = false
                    }
                    val listed = (suggested.map { it.code } + s.codes.map { it.code }).toSet()
                    if (typed.isNotEmpty() && listed.none { it.equals(typed, ignoreCase = true) }) {
                        row(typed, stringResource(R.string.jacs_use_typed), on = false, tag = JacsTags.code(typed))
                    }
                    s.codes.filter { c -> suggested.none { it.code == c.code } }
                        .forEach { c -> row(c.code, c.title, on = c.code == current, tag = JacsTags.code(c.code)) }
                    if (current.isNotBlank()) row("", stringResource(R.string.jacs_none), on = false, tag = JacsTags.code(""))
                }
                when {
                    s.error != null -> Txt(s.error.asString(), 12, 500, C.Orange, maxLines = Int.MAX_VALUE)
                    // The first page: placeholder rows; a further page (More): the small spinner under the list.
                    s.loading && s.codes.isEmpty() -> SkeletonList(
                        stringResource(R.string.classify_jacs_loading), count = 5, lead = SkeletonLead.NONE, lines = 2, card = false, gap = 2.dp,
                    )
                    s.loading -> Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = C.Yellow, strokeWidth = 2.dp)
                    }
                    s.codes.isEmpty() && typed.isNotEmpty() -> Txt(stringResource(R.string.jacs_no_match), 12, 500, C.Soft, maxLines = Int.MAX_VALUE)
                    s.next != null -> Pill(
                        stringResource(R.string.jacs_more), actions.onMore, Modifier.fillMaxWidth().testTag(JacsTags.MORE),
                        height = 44.dp, size = 13, weight = 700,
                    )
                }
            }
        }
    }
}

/**
 * One choice: the code (or what it does, for "No JACS code") and its title, marked when it is the asset's; one the AI
 * suggested says how sure it is ([sure], 0..1).
 */
@Composable
private fun CodeRow(code: String, title: String?, on: Boolean, divider: Boolean, tag: String, sure: Double? = null, onClick: () -> Unit) {
    Column(
        Modifier.pressClickable(pressed = PRESSED_CARD, onClick = onClick).fillMaxWidth().testTag(tag).then(if (divider) Modifier.topBorder(C.Border) else Modifier)
            .background(if (on) C.YTintBg else C.Surface).semantics { selected = on; role = Role.RadioButton }
            .heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (code.isNotEmpty()) Txt(code, 14, 700, if (on) C.Yellow else C.Text, maxLines = 1)
        title?.let { Txt(it, 12, if (code.isEmpty()) 600 else 400, if (code.isEmpty()) C.Text else C.Soft, maxLines = Int.MAX_VALUE) }
        sure?.let {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LineIcon(Icons.Sparkles, 11.dp, C.Cyan, 2.2f)
                Txt(stringResource(R.string.ai_sure, (it * 100).roundToInt()), 11, 700, if (it < SURE_ENOUGH) C.Orange else C.Green)
            }
        }
    }
}

/**
 * The AI button beside an empty JACS code, as the web's: asks the AI for codes for the asset's type, which then head the
 * picker. A spinner while it asks ([busy]); offline, or with the property's AI switch off ([aiOff]), it stays dimmed and
 * a tap says why ([onOffline]; [onSuggest] says AI is off).
 */
@Composable
internal fun JacsSuggestButton(busy: Boolean, aiOff: Boolean, offline: Boolean, onSuggest: () -> Unit, onOffline: () -> Unit) {
    val description = stringResource(if (busy) R.string.jacs_suggesting_cd else R.string.jacs_suggest_cd)
    OfflineGate(offline && !busy, onOffline) {
        AiOffGate(aiOff && !busy, onSuggest) {
            Box(
                Modifier.size(48.dp).testTag(JacsTags.SUGGEST).pressClickable(shape = CircleShape, enabled = !busy, role = Role.Button, onClick = onSuggest)
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(16.dp), color = C.Cyan, strokeWidth = 2.dp)
                else LineIcon(Icons.Sparkles, 18.dp, C.Cyan, 2.2f)
            }
        }
    }
}
