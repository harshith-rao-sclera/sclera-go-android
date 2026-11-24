package com.sclera.sclerago.presentation.assets

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Media
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.components.MenuAct
import com.sclera.sclerago.presentation.icons.Icons

/**
 * What holding one of the asset's photos, videos, voice memos or files lists: View ([onOpen]), Download (saves a copy,
 * [onDownload]), Move to each other photo card ([moves]: its name and the move, a new asset's photos only) and Remove
 * ([onRemove], which asks first; null where nobody may remove it, e.g. a signed-off asset). [removeBlocked]: the signed-in
 * user may not change the asset, so Remove is greyed out and a tap says why. The same actions the viewer offers.
 */
@Composable
internal fun mediaMenu(
    m: Media,
    onOpen: () -> Unit,
    onDownload: ((Media) -> Unit)?,
    onRemove: ((Media) -> Unit)?,
    moves: List<Pair<String, () -> Unit>> = emptyList(),
    removeBlocked: UiText? = null,
): List<MenuAct> {
    val moveTo = stringResource(R.string.media_move_to)
    return buildList {
        add(MenuAct(Icons.Eye, stringResource(R.string.media_view), go = onOpen))
        onDownload?.let { download -> add(MenuAct(Icons.Download, stringResource(R.string.media_download)) { download(m) }) }
        moves.forEach { (name, move) -> add(MenuAct(Icons.ArrowUpRight, moveTo.format(name), go = move)) }
        onRemove?.let { remove -> add(MenuAct(Icons.Trash, stringResource(R.string.media_remove), danger = true, blocked = removeBlocked) { remove(m) }) }
    }
}
