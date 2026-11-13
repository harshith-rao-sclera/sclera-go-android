package com.sclera.sclerago.presentation.assets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FieldSection
import com.sclera.sclerago.presentation.components.LocalReadOnly
import com.sclera.sclerago.presentation.components.PlainField
import com.sclera.sclerago.presentation.components.bottomBorder
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import java.util.Locale

/** Test tags of the asset form. */
object DetailTags {
    fun section(key: String) = "detail-section-$key"
    fun field(key: String) = "detail-field-$key"
    fun badge(key: String) = "detail-badge-$key"
    const val TAB_INFO = "detail-tab-info"
    const val TAB_VALIDATION = "detail-tab-validation"
    const val SAVE = "detail-save"
    const val SUBMIT = "detail-submit"
    const val STATUS_LINE = "detail-status-line"
    const val LOCATION = "detail-location"
}

/** What a field value came from, shown at the end of its row. */
enum class FieldBadge { NONE, AI, EDITED, CHECK, OCR }

/** The badge of the identification field [key]: AI-filled, edited, read from the nameplate with doubt (CHECK), or read from it (OCR). */
fun AssetDetailState.badgeFor(key: String): FieldBadge = when {
    key in aiFilled -> FieldBadge.AI
    dirty(key) -> FieldBadge.EDITED
    key == lowConfidenceKey -> FieldBadge.CHECK
    fromCamera && value(key).isNotEmpty() -> FieldBadge.OCR
    else -> FieldBadge.NONE
}

/** The configured fields (Configuration → Asset fields): which show, which are mandatory, and their labels. */
class FieldSpec(private val fields: List<FieldCfg>) {
    private fun cfg(key: String) = fields.firstOrNull { it.key == key }
    fun shown(key: String) = cfg(key)?.visible ?: true
    fun required(key: String) = cfg(key)?.let { it.required && it.visible } ?: false
    fun label(key: String) = (cfg(key)?.label ?: key) + if (required(key)) " *" else ""
    fun visibleIn(section: FieldSection) = fields.filter { it.section == section && it.visible && it.key != "name" }
}

/** A collapsed section one-line summary. */
class Summary(val text: String, val warn: Boolean = false, val mono: Boolean = false)

/** A collapsible card of the form: title, a note or (when collapsed) a summary, and rows. */
@Composable
fun FormSection(
    key: String,
    title: String,
    note: String?,
    open: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    /** Shown on the right while the section is collapsed: what is in it, or what is missing. */
    summary: Summary? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val rot by animateFloatAsState(if (open) 0f else -90f, tween(200), label = "chev")
    Column(
        modifier.fillMaxWidth().testTag(DetailTags.section(key)).background(C.Surface).topBorder(C.Border).bottomBorder(C.Border)
            .padding(top = 4.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(if (trailing != null && open) 4.dp else 0.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.weight(1f).height(48.dp).clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LineIcon(Icons.ChevronDown, 18.dp, C.Muted, 2.4f, Modifier.rotate(rot))
                Txt(title, 14, 700, modifier = Modifier.weight(1f))
                if (!open && summary != null) {
                    Txt(summary.text, 11, if (summary.warn) 700 else 500, if (summary.warn) C.Orange else C.Muted, mono = summary.mono)
                } else note?.let { Txt(it, 10, 500, C.Muted) }
            }
            if (open) trailing?.invoke()
        }
        if (open) content()
    }
}

/** A text row: label, editable value, and an optional badge saying where the value came from. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FieldRow(
    key: String,
    label: String,
    value: String,
    onChange: (String) -> Unit,
    edited: Boolean,
    badge: FieldBadge = FieldBadge.NONE,
    rowBg: Color = Color.Transparent,
    mono: Boolean = false,
    requester: BringIntoViewRequester? = null,
) {
    Row(
        Modifier.fillMaxWidth().testTag(DetailTags.field(key)).then(if (requester != null) Modifier.bringIntoViewRequester(requester) else Modifier)
            .heightIn(min = 48.dp).topBorder(C.Border).background(rowBg).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Txt(label, 12, 500, C.Muted, Modifier.width(92.dp), maxLines = 2)
        PlainField(
            value, onChange, stringResource(R.string.detail_field_hint, label.lowercase(Locale.getDefault())),
            Modifier.weight(1f).bottomBorder(if (edited) C.Yellow else Color.Transparent, 1.5.dp).padding(vertical = 6.dp),
            size = if (mono) 13 else 14, weight = 600, mono = mono,
        )
        BadgeText(key, badge)
    }
}

/** The OCR / CHECK / AI / EDITED mark at the end of a row. */
@Composable
private fun BadgeText(key: String, badge: FieldBadge) {
    val (text, color) = when (badge) {
        FieldBadge.NONE -> return
        FieldBadge.AI -> stringResource(R.string.detail_badge_ai) to C.Cyan
        FieldBadge.EDITED -> stringResource(R.string.detail_badge_edited) to C.Yellow
        FieldBadge.CHECK -> stringResource(R.string.detail_badge_check) to C.Orange
        FieldBadge.OCR -> stringResource(R.string.detail_badge_ocr) to C.Purple
    }
    Txt(text, 10, 700, color, Modifier.testTag(DetailTags.badge(key)), spacing = 0.3f)
}

/** A dropdown row: label and the chosen option; [required] flags it as missing. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SelectRow(
    key: String,
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    edited: Boolean,
    onChange: (String) -> Unit,
    required: Boolean = false,
    rowBg: Color = Color.Transparent,
    requester: BringIntoViewRequester? = null,
) {
    var open by remember { mutableStateOf(false) }
    val readOnly = LocalReadOnly.current
    val shown = options.firstOrNull { it.first == value }?.second ?: value
    Box {
        Row(
            Modifier.fillMaxWidth().testTag(DetailTags.field(key)).then(if (requester != null) Modifier.bringIntoViewRequester(requester) else Modifier)
                .heightIn(min = 48.dp).topBorder(C.Border).background(rowBg).clickable(enabled = !readOnly) { open = true }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Txt(label, 12, 500, C.Muted, Modifier.width(92.dp), maxLines = 2)
            Txt(
                shown, 14, 600, if (value.isEmpty()) C.Muted else C.Text,
                Modifier.weight(1f).bottomBorder(if (edited) C.Yellow else Color.Transparent, 1.5.dp).padding(vertical = 6.dp),
            )
            if (required) Txt(stringResource(R.string.check_required), 10, 700, C.Orange)
            if (!readOnly) LineIcon(Icons.ChevronDown, 16.dp, C.Muted, 2.4f)
        }
        DropdownMenu(open, { open = false }, containerColor = C.Surface) {
            options.forEach { (k, text) ->
                DropdownMenuItem(
                    text = { Txt(text, 14, if (k == value) 700 else 500, if (k == value) C.Yellow else C.Text) },
                    onClick = { onChange(k); open = false },
                )
            }
        }
    }
}
