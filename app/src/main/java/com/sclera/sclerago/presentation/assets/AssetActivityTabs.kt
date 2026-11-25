package com.sclera.sclerago.presentation.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.ActivityKind
import com.sclera.sclerago.domain.model.AssetActivity
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.common.formatDateTimeSeconds
import com.sclera.sclerago.presentation.common.is24Hour
import com.sclera.sclerago.presentation.components.AccessGate
import com.sclera.sclerago.presentation.components.IconMenu
import com.sclera.sclerago.presentation.components.LocalReadOnly
import com.sclera.sclerago.presentation.components.MenuAct
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.PlainField
import com.sclera.sclerago.presentation.components.RoundButton
import com.sclera.sclerago.presentation.components.SkeletonLine
import com.sclera.sclerago.presentation.components.initialsOf
import com.sclera.sclerago.presentation.components.bottomBorder
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Ico
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.ScleraFonts
import com.sclera.sclerago.presentation.theme.Txt

// The asset details' Comments and History tabs.

/** A comment row in placeholders (who, what they wrote, when), read out as "Posting comment…". */
@Composable
private fun CommentSkeleton(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.scrt_comment_posting)
    Column(
        modifier.fillMaxWidth().background(C.Surface).bottomBorder(C.Border).padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)
            .semantics(mergeDescendants = true) { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SkeletonLine(0.35f, 10.dp)
        SkeletonLine(0.85f, 11.dp)
        SkeletonLine(0.22f, 8.dp)
    }
}

/**
 * The Comments tab's list: one full-width row per comment, newest first, with its text, who wrote it and when
 * ("· edited" once changed), and a ⋮ menu in the top end corner with Edit (its author) and Delete (its author, or a
 * validator), as the web allows. Edit turns the row into a box with Save and Cancel; Delete asks first ([onDelete] shows
 * the question). [readOnly] (a signed-off asset, as the web's validated list) leaves the menu out. Otherwise what the
 * signed-in user may not do stays in the menu, greyed out, saying why: Edit someone else's comment, or Delete it without
 * Validate ([deleteBlocked]).
 */
@Composable
fun CommentList(
    activity: List<AssetActivity>,
    readOnly: Boolean,
    onSave: (comment: AssetActivity, text: String, onSaved: () -> Unit) -> Unit,
    onDelete: (AssetActivity) -> Unit,
    /** A comment just sent is being kept: a placeholder row shows at the top, where it will appear (newest first). */
    posting: Boolean = false,
    /** Why the signed-in user can't remove a comment they may not delete (someone else's, without Validate). */
    deleteBlocked: UiText? = null,
) {
    val locale = LocalConfiguration.current.locales[0]
    val comments = activity.filter { it.kind == ActivityKind.COMMENT }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var editText by rememberSaveable { mutableStateOf("") }
    val someone = stringResource(R.string.detail_comment_someone)
    // None yet: "No comments yet" in the middle of the tab, as the web's (or the placeholder of the one being sent).
    if (comments.isEmpty() && posting) {
        CommentSkeleton(Modifier.topBorder(C.Border))
        return
    }
    if (comments.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(vertical = 120.dp).testTag(DetailTags.TAB_EMPTY), contentAlignment = Alignment.Center) {
            Txt(stringResource(R.string.detail_no_comments), 13, 700, C.Muted, maxLines = Int.MAX_VALUE, align = TextAlign.Center)
        }
        return
    }
    val editLabel = stringResource(R.string.detail_comment_edit)
    val deleteLabel = stringResource(R.string.detail_comment_delete)
    val notYours = UiText.Res(R.string.access_comment_edit_not_yours)
    val deleteNotYours = UiText.Res(R.string.access_comment_delete_not_yours)
    // Edge to edge: one row per comment, divided by a line, with no gap or side margin.
    Column(Modifier.fillMaxWidth().topBorder(C.Border)) {
        if (posting) CommentSkeleton()
        comments.forEach { e ->
            val menu = if (readOnly) emptyList() else buildList {
                add(MenuAct(Icons.Pencil, editLabel, blocked = notYours.takeUnless { e.canEdit }) { editText = e.text; editingId = e.id })
                // Delete always shows; one the user may not remove is greyed out, saying why.
                val deleteWhy = if (e.canDelete) null else deleteBlocked ?: deleteNotYours
                add(MenuAct(Icons.Trash, deleteLabel, danger = true, blocked = deleteWhy) { onDelete(e) })
            }
            Row(
                Modifier.fillMaxWidth().testTag("detail-activity-${e.id}").clip(r(14)).background(C.Surface).border(1.dp, C.Border, r(14))
                    .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f).padding(top = 4.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (editingId == e.id) {
                        CompositionLocalProvider(LocalReadOnly provides false) {
                            PlainField(
                                editText, { editText = it }, stringResource(R.string.detail_comment_hint),
                                Modifier.fillMaxWidth().testTag("detail-comment-edit-${e.id}").clip(r(10)).background(C.Bg)
                                    .border(1.dp, C.Border2, r(10)).padding(10.dp),
                                size = 14, singleLine = false, minLines = 2,
                            )
                        }
                    } else {
                        Txt(e.text, 14, 400, maxLines = Int.MAX_VALUE)
                    }
                    val author = e.authorName ?: someone
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(30.dp).background(C.YTintBg, CircleShape), contentAlignment = Alignment.Center) { Txt(initialsOf(author).take(1), 12, 700, C.Yellow) }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Txt(author, 13, 700, maxLines = 1)
                            e.createdAt?.let {
                                    val time = formatDateTimeSeconds(it, locale, use24Hour = is24Hour())
                                    Txt(if (e.edited) stringResource(R.string.scrt_comment_time_edited, time) else time, 11, 400, C.Muted)
                            }
                        }
                    }
                }
                when {
                    // While editing: Save and Cancel where the ⋮ was.
                    editingId == e.id -> {
                        CommentIconButton(Icons.Check, stringResource(R.string.detail_comment_save), C.Yellow, "detail-comment-save-${e.id}") {
                            if (editText.isNotBlank()) onSave(e, editText) { editingId = null }
                        }
                        CommentIconButton(Icons.Close, stringResource(R.string.action_cancel), C.Muted, "detail-comment-cancel-${e.id}") { editingId = null }
                    }
                    // Edit and Delete sit in a ⋮ menu in the top end corner, only when the user may do one of them.
                    menu.isNotEmpty() -> Box {
                        var open by remember { mutableStateOf(false) }
                        CommentIconButton(Icons.Dots, stringResource(R.string.detail_comment_options), C.Muted, "detail-comment-menu-${e.id}") { open = true }
                        IconMenu(open, { open = false }, menu)
                    }
                }
            }
        }
    }
}

/** An icon-only button on a comment (48dp to touch, [description] read out). */
@Composable
private fun CommentIconButton(icon: Ico, description: String, tint: Color, tag: String, onClick: () -> Unit) {
    RoundButton(48.dp, Color.Transparent, onClick, Modifier.testTag(tag).semantics { contentDescription = description }) {
        LineIcon(icon, 18.dp, tint, 2.2f)
    }
}

/**
 * The History tab, as the web's timeline: a dot and a line down the start side, each entry's sentence ("Asset updated")
 * with the fields it changed (old → new), then who and when; "No history available" when there is none.
 */
@Composable
fun HistoryList(activity: List<AssetActivity>) {
    val entries = activity.filter { it.kind == ActivityKind.HISTORY }
    if (entries.isEmpty()) {
        EmptyTab(stringResource(R.string.detail_no_history))
        return
    }
    val locale = LocalConfiguration.current.locales[0]
    val empty = stringResource(R.string.detail_change_empty)
    Column(Modifier.fillMaxWidth().padding(start = 6.dp)) {
        entries.forEachIndexed { i, e ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).testTag("detail-activity-${e.id}")) {
                Box(Modifier.width(14.dp).fillMaxHeight()) {
                    if (i < entries.lastIndex) Box(Modifier.align(Alignment.TopCenter).padding(top = 6.dp).width(1.dp).fillMaxHeight().background(C.Border))
                    Box(Modifier.align(Alignment.TopCenter).padding(top = 5.dp).size(9.dp).background(C.Yellow, CircleShape).border(2.dp, C.YTintBg, CircleShape))
                }
                Column(Modifier.weight(1f).padding(start = 10.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // History lines are written by the server and the app; their sentence is kept as recorded.
                    Txt(e.text.ifEmpty { e.action.orEmpty() }, 14, 400, maxLines = Int.MAX_VALUE)
                    // As the web: "Building: empty → Harbour Point Tower", the old value struck through.
                    e.fieldChanges.forEach { c ->
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = C.Text)) { append(stringResource(R.string.scrt_history_field_label, c.field)) }
                                withStyle(SpanStyle(color = C.Muted, textDecoration = TextDecoration.LineThrough)) { append(c.from ?: empty) }
                                append(" → ")
                                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = C.Text)) { append(c.to ?: empty) }
                            },
                            color = C.Soft, fontSize = 12.sp, fontFamily = ScleraFonts.Inter, maxLines = Int.MAX_VALUE,
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Who made the change, by email.
                        Txt(e.authorName.orEmpty(), 11, 400, C.Muted, Modifier.weight(1f), maxLines = 1)
                        e.createdAt?.let { Txt(formatDateTimeSeconds(it, locale, use24Hour = is24Hour()), 11, 400, C.Muted) }
                    }
                }
            }
        }
    }
}

/**
 * The box in the Add comment sheet where a comment is written: the text, what is wrong with it ([error]), and Post (off while it is
 * blank or [posting]). It stays usable on a submitted asset: the server takes comments on any asset. [blocked]: the
 * signed-in user may not comment, so the box and Add are greyed out and a tap says why.
 */
@Composable
fun CommentBox(
    draft: String,
    error: UiText?,
    posting: Boolean,
    onChange: (String) -> Unit,
    onPost: () -> Unit,
    modifier: Modifier = Modifier,
    blocked: UiText? = null,
) = AccessGate(blocked, modifier.fillMaxWidth()) {
    val canPost = draft.isNotBlank() && !posting
    CompositionLocalProvider(LocalReadOnly provides false) {
        // As the web: the box, then a small dark Add on the end side under it (dimmed until something is written).
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PlainField(
                draft, onChange, stringResource(R.string.detail_comment_hint),
                Modifier.fillMaxWidth().testTag(DetailTags.COMMENT_FIELD).clip(r(10)).background(C.Bg)
                    .border(1.dp, if (error != null) C.Orange else C.Border2, r(10)).padding(10.dp),
                size = 14, singleLine = false, minLines = 3, readOnly = posting,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { error?.let { Txt(it.asString(), 12, 600, C.Orange, maxLines = Int.MAX_VALUE) } }
                Pill(
                    stringResource(R.string.action_add), { if (canPost) onPost() },
                    Modifier.testTag(DetailTags.COMMENT_POST).alpha(if (canPost) 1f else 0.45f),
                    bg = C.Btn, fg = C.Text, height = 36.dp, hPad = 16.dp, size = 12, weight = 700,
                )
            }
        }
    }
}
