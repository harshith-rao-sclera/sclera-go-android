package com.sclera.sclerago.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/**
 * A form sheet's Delete, the same size and shape as its Save but quiet: a dark red tint with a thin red border, a trash
 * icon and red text. It takes Cancel's place in the footer (pass it as [FormSheet]'s `footerLeading`) and is read out as
 * [label] ("Delete Collectors"). Used by the group and property forms.
 */
@Composable
internal fun RowScope.FooterDeleteButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        Modifier.weight(1f).height(50.dp).clip(Capsule).background(C.DangerBg).border(1.dp, C.Red.copy(alpha = 0.45f), Capsule)
            .semantics { contentDescription = label }.clickable(role = Role.Button, onClick = onClick).then(modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        LineIcon(Icons.Trash, 17.dp, C.Red, 2.2f)
        Txt(stringResource(R.string.action_delete), 14, 700, C.Red)
    }
}
