package com.sclera.sclerago.presentation.assets

import com.sclera.sclerago.domain.model.OnlineError
import com.sclera.sclerago.presentation.components.pressClickable
import com.sclera.sclerago.presentation.components.PRESSED_CARD
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.JacsCode
import com.sclera.sclerago.domain.model.OnlineException
import com.sclera.sclerago.domain.usecase.asset.JacsUseCases
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.common.toUiText
import com.sclera.sclerago.presentation.components.SkeletonLead
import com.sclera.sclerago.presentation.components.SkeletonList
import com.sclera.sclerago.presentation.components.BottomSheet
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.SearchField
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.topBorder
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

/** The JACS code picker while it is open: what is searched, the codes found so far, and where the next page starts. */
data class JacsPickerState(
    val query: String = "",
    val codes: List<JacsCode> = emptyList(),
    val next: String? = null,
    val loading: Boolean = false,
    val error: UiText? = null,
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
    fun code(code: String) = "jacs-code-$code"
}

/**
 * Searching the JACS catalog for the asset's code, for its view model (runs in its [scope]), as the web's picker: a
 * search a moment after typing stops, the next page on request, a page already loading never asked twice and a code
 * never listed twice.
 */
internal class JacsPicker(private val useCases: JacsUseCases, private val scope: CoroutineScope) {
    private val picker = MutableStateFlow<JacsPickerState?>(null)
    val state: StateFlow<JacsPickerState?> = picker.asStateFlow()
    private var job: Job? = null

    /** Opens the picker on the whole catalog. */
    fun open() {
        picker.value = JacsPickerState()
        search("", wait = 0)
    }

    fun close() {
        job?.cancel()
        picker.value = null
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
            // Offline with no catalogue on the phone yet (it comes with the first connection): nothing to pick from yet.
            val why = if (e.error == OnlineError.Offline) UiText.Res(R.string.jacs_catalog_missing) else e.error.toUiText()
            picker.update { it?.copy(loading = false, error = why) }
        }
    }

    private companion object {
        const val SEARCH_DELAY_MS = 300L
    }
}

/**
 * The JACS code picker: search the catalog, each code with its title (the asset's [current] code marked), and "No JACS
 * code" to clear it. Only a code in the catalog can be picked: what is typed searches, it is never used as a code.
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
                    s.codes.forEach { c -> row(c.code, c.title, on = c.code == current, tag = JacsTags.code(c.code)) }
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

/** One choice: the code (or what it does, for "No JACS code") and its title, marked when it is the asset's. */
@Composable
private fun CodeRow(code: String, title: String?, on: Boolean, divider: Boolean, tag: String, onClick: () -> Unit) {
    Column(
        Modifier.pressClickable(pressed = PRESSED_CARD, onClick = onClick).fillMaxWidth().testTag(tag).then(if (divider) Modifier.topBorder(C.Border) else Modifier)
            .background(if (on) C.YTintBg else C.Surface).semantics { selected = on; role = Role.RadioButton }
            .heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (code.isNotEmpty()) Txt(code, 14, 700, if (on) C.Yellow else C.Text, maxLines = 1)
        title?.let { Txt(it, 12, if (code.isEmpty()) 600 else 400, if (code.isEmpty()) C.Text else C.Soft, maxLines = Int.MAX_VALUE) }
    }
}

