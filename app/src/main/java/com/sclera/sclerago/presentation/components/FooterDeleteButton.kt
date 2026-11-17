package com.sclera.sclerago.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C

/**
 * A form sheet's Delete: a round red trash button at the start of the footer, the same height as Cancel and Save
 * (pass it as [FormSheet]'s `footerLeading`), read out as [label] ("Delete Collectors"). Used by the group and
 * property forms.
 */
@Composable
internal fun FooterDeleteButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(50.dp).clip(CircleShape).background(C.DangerBg).border(1.dp, C.Red.copy(alpha = 0.45f), CircleShape)
            .semantics { contentDescription = label }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { LineIcon(Icons.Trash, 20.dp, C.Red, 2.2f) }
}
