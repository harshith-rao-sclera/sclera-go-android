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
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.presentation.components.LocalReadOnly
import com.sclera.sclerago.presentation.components.PlainField
import com.sclera.sclerago.presentation.components.bottomBorder
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.ScleraFonts
import com.sclera.sclerago.presentation.theme.Txt
import java.util.Locale

/** Test tags of the asset form. */
object DetailTags {
    fun section(key: String) = "detail-section-$key"
    fun field(key: String) = "detail-field-$key"
    fun badge(key: String) = "detail-badge-$key"
    const val TAB_INFO = "detail-tab-info"
    const val TAB_VALIDATION = "detail-tab-validation"
    const val TAB_EMPTY = "detail-tab-empty"
    const val LOCK_BANNER = "detail-lock-banner"
    const val MOVE_TO_DRAFT = "detail-move-to-draft"
    const val MARK_VALIDATED = "detail-mark-validated"
    const val COMMENT_FIELD = "detail-comment-field"
    const val COMMENT_POST = "detail-comment-post"
    const val SAVE = "detail-save"
    const val SUBMIT = "detail-submit"
    const val STATUS_LINE = "detail-status-line"
    const val LOCATION = "detail-location"
    const val SUGGEST_TYPE = "detail-suggest-type"
    const val CLONE = "detail-clone"
    const val MANAGED_NOTE = "detail-managed-note"
}

/** What a field value came from, shown at the end of its row. */
enum class FieldBadge { NONE, CHECK, OCR }

/**
 * The badge of the field [key]: read from the nameplate with doubt (CHECK), or read from it (OCR). A field changed in
 * the form shows no badge (its yellow underline marks it); its value is the user's now.
 */
fun AssetDetailState.badgeFor(key: String): FieldBadge = when {
    dirty(key) -> FieldBadge.NONE
    key == lowConfidenceKey -> FieldBadge.CHECK
    fromCamera && value(key).isNotEmpty() -> FieldBadge.OCR
    else -> FieldBadge.NONE
}

/** The asset's fields from the property's data (`asset_fields`): which show, which are mandatory, and their names. */
class FieldSpec(private val fields: List<FieldCfg>) {
    /** The fields by key (the first of a key wins), so each lookup is direct however many fields there are. */
    private val byKey: Map<String, FieldCfg> = buildMap { fields.forEach { putIfAbsent(it.key, it) } }

    fun cfg(key: String): FieldCfg? = byKey[key]
    fun shown(key: String) = cfg(key)?.visible ?: true
    fun required(key: String) = cfg(key)?.let { it.required && it.visible } ?: false

    /** The field's name, or [fallback] when the property has no such field; " *" marks a mandatory one. */
    fun label(key: String, fallback: String = key) = (cfg(key)?.label?.takeIf { it.isNotBlank() } ?: fallback) + if (required(key)) " *" else ""

    /** The fields that show, in their order, except [keys]. */
    fun visibleExcept(keys: Set<String>) = fields.filter { it.visible && it.key !in keys }
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
    /** The [note] is a warning (orange), e.g. a mandatory field still empty. */
    noteWarn: Boolean = false,
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
                Txt(title, 15, 700, modifier = Modifier.weight(1f))
                if (!open && summary != null) {
                    Txt(summary.text, 11, if (summary.warn) 700 else 500, if (summary.warn) C.Orange else C.Muted, mono = summary.mono)
                } else note?.let { Txt(it, if (noteWarn) 11 else 10, if (noteWarn) 700 else 500, if (noteWarn) C.Orange else C.Muted) }
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
    /** A text that may be long (notes, description): the value wraps over as many lines as it needs. */
    multiLine: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth().testTag(DetailTags.field(key)).then(if (requester != null) Modifier.bringIntoViewRequester(requester) else Modifier)
            .heightIn(min = 48.dp).topBorder(C.Border).background(rowBg).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FieldLabel(label, Modifier.width(92.dp))
        PlainField(
            value, onChange, stringResource(R.string.detail_field_hint, label.removeSuffix(" *").lowercase(Locale.getDefault())),
            Modifier.weight(1f).bottomBorder(if (edited) C.Yellow else Color.Transparent, 1.5.dp).padding(vertical = 6.dp),
            size = if (mono) 13 else 15, weight = 600, mono = mono, singleLine = !multiLine,
        )
        BadgeText(key, badge)
    }
}

/** A field's name; the " *" that marks a mandatory field is drawn in orange. */
@Composable
fun FieldLabel(label: String, modifier: Modifier = Modifier, color: Color = C.Muted) {
    val name = label.removeSuffix(REQUIRED_MARK)
    Text(
        buildAnnotatedString {
            append(name)
            if (name != label) withStyle(SpanStyle(color = C.Orange, fontWeight = FontWeight.Bold)) { append(REQUIRED_MARK) }
        },
        modifier, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = ScleraFonts.Inter,
        maxLines = 2, overflow = TextOverflow.Ellipsis,
    )
}

/** What [FieldSpec.label] puts after a mandatory field's name. */
private const val REQUIRED_MARK = " *"

/**
 * A row that shows a value (Location details): label and value, or "-" when there is none (as the web). With [onClick]
 * it is tappable, with a chevron like the other picker rows (the Location details rows open the location picker).
 */
@Composable
fun ValueRow(label: String, value: String?, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).topBorder(C.Border)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Txt(label, 13, 500, C.Muted, Modifier.width(92.dp), maxLines = 2)
        Txt(value ?: stringResource(R.string.detail_value_none), 15, 600, if (value != null) C.Text else C.Muted, Modifier.weight(1f), maxLines = Int.MAX_VALUE)
        if (onClick != null) LineIcon(Icons.ChevronDown, 16.dp, C.Muted, 2.4f)
    }
}

/** The OCR / CHECK mark at the end of a row. */
@Composable
private fun BadgeText(key: String, badge: FieldBadge) {
    val (text, color) = when (badge) {
        FieldBadge.NONE -> return
        FieldBadge.CHECK -> stringResource(R.string.detail_badge_check) to C.Orange
        FieldBadge.OCR -> stringResource(R.string.detail_badge_ocr) to C.Purple
    }
    Txt(text, 10, 700, color, Modifier.testTag(DetailTags.badge(key)), spacing = 0.3f)
}

/** A row whose value is chosen in a sheet: label, the value (or [empty] in grey), and a chevron; [onOpen] opens the sheet. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PickRow(
    key: String,
    label: String,
    value: String,
    empty: String,
    edited: Boolean,
    onOpen: () -> Unit,
    rowBg: Color = Color.Transparent,
    mono: Boolean = false,
    badge: FieldBadge = FieldBadge.NONE,
    requester: BringIntoViewRequester? = null,
) {
    val readOnly = LocalReadOnly.current
    Row(
        Modifier.fillMaxWidth().testTag(DetailTags.field(key)).then(if (requester != null) Modifier.bringIntoViewRequester(requester) else Modifier)
            .heightIn(min = 48.dp).topBorder(C.Border).background(rowBg).clickable(enabled = !readOnly, onClick = onOpen)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FieldLabel(label, Modifier.width(92.dp))
        Txt(
            value.ifEmpty { empty }, if (mono) 13 else 15, 600, if (value.isEmpty()) C.Muted else C.Text,
            Modifier.weight(1f).bottomBorder(if (edited) C.Yellow else Color.Transparent, 1.5.dp).padding(vertical = 6.dp), maxLines = Int.MAX_VALUE,
        )
        BadgeText(key, badge)
        if (!readOnly) LineIcon(Icons.ChevronDown, 16.dp, C.Muted, 2.4f)
    }
}

/** A dropdown row: label and the chosen option. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SelectRow(
    key: String,
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    edited: Boolean,
    onChange: (String) -> Unit,
    rowBg: Color = Color.Transparent,
    requester: BringIntoViewRequester? = null,
) {
    var open by remember { mutableStateOf(false) }
    val readOnly = LocalReadOnly.current
    val shown = options.firstOrNull { it.first == value }?.second ?: value
    Row(
        Modifier.fillMaxWidth().testTag(DetailTags.field(key)).then(if (requester != null) Modifier.bringIntoViewRequester(requester) else Modifier)
            .heightIn(min = 48.dp).topBorder(C.Border).background(rowBg).clickable(enabled = !readOnly) { open = true }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FieldLabel(label, Modifier.width(92.dp))
        // The menu hangs from the value, so it opens under it rather than over the label.
        Box(Modifier.weight(1f)) {
            Txt(
                shown, 15, 600, if (value.isEmpty()) C.Muted else C.Text,
                Modifier.fillMaxWidth().bottomBorder(if (edited) C.Yellow else Color.Transparent, 1.5.dp).padding(vertical = 6.dp),
                maxLines = Int.MAX_VALUE,
            )
            DropdownMenu(open, { open = false }, containerColor = C.Surface) {
                options.forEach { (k, text) ->
                    DropdownMenuItem(
                        text = { Txt(text, 14, if (k == value) 700 else 500, if (k == value) C.Yellow else C.Text, maxLines = Int.MAX_VALUE) },
                        onClick = { onChange(k); open = false },
                    )
                }
            }
        }
        if (!readOnly) LineIcon(Icons.ChevronDown, 16.dp, C.Muted, 2.4f)
    }
}
