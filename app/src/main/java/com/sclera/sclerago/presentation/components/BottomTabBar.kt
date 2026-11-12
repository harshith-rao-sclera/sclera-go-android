package com.sclera.sclerago.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.sclera.sclerago.presentation.icons.Ico
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

// Bottom tab bar used by the main shell and Configuration.

/** The bar the bottom tabs sit in (main screen and Configuration). */
@Composable
internal fun BottomTabBar(content: @Composable RowScope.() -> Unit) {
    Box(Modifier.fillMaxWidth().background(C.Surface).topBorder(C.Border).navigationBarsPadding().height(58.dp)) {
        Row(Modifier.fillMaxSize(), content = content)
    }
}

/** [t] is how selected this tab is: 0 = not at all, 1 = fully (in between mid-swipe). */
@Composable
internal fun RowScope.NavItem(label: String, icon: Ico, iconSize: Dp, t: Float, first: Boolean, last: Boolean, onClick: () -> Unit) {
    val on = t > 0.5f
    val w = 1f + 0.6f * t
    val bg = lerp(Color.Transparent, C.Raised, t)
    val circ = lerp(Color.Transparent, C.Yellow, t)
    val ic = lerp(C.Muted, C.OnYellow, t)
    val size = lerp(30.dp, 36.dp, t)
    val arrowL = if (!first) 17.dp * t else 0.dp
    val arrowR = if (!last) 17.dp * t else 0.dp
    val labelH = 14.dp * t
    val labelA = t
    val gap = 3.dp * t
    Box(
        Modifier.weight(w).fillMaxHeight().background(bg).then(if (!last) Modifier.endBorder(C.Border) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Arrows sit beside the icon; the label is centred under the icon only (not under icon + arrows).
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.height(size), contentAlignment = Alignment.Center) {
                LineIcon(Icons.ChevronLeft, 17.dp, C.Yellow, 2.8f, Modifier.width(arrowL).alpha(arrowL / 17.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(gap)) {
                Box(
                    Modifier.size(size).then(if (on) Modifier.shadow(6.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black) else Modifier)
                        .background(circ, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { LineIcon(icon, iconSize, ic) }
                Box(Modifier.height(labelH).alpha(labelA), contentAlignment = Alignment.Center) {
                    Txt(label, 11, 700, C.Yellow, maxLines = 1)
                }
            }
            Box(Modifier.height(size), contentAlignment = Alignment.Center) {
                LineIcon(Icons.ChevronRight, 17.dp, C.Yellow, 2.8f, Modifier.width(arrowR).alpha(arrowR / 17.dp))
            }
        }
    }
}
