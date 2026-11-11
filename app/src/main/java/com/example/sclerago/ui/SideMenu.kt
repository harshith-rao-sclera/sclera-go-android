package com.example.sclerago.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.sclerago.AppViewModel
import com.example.sclerago.MenuItem
import com.example.sclerago.data.plural
import com.example.sclerago.ui.icons.Ico
import com.example.sclerago.ui.icons.Icons
import com.example.sclerago.ui.icons.LineIcon
import com.example.sclerago.ui.theme.C
import com.example.sclerago.ui.theme.Txt

private val MENU = listOf(
    Triple(MenuItem.ASSETS, "Assets", Icons.Box),
    Triple(MenuItem.VALIDATION, "Validation", Icons.ShieldCheck),
    Triple(MenuItem.CONFIGURATION, "Configuration", Icons.Sliders),
    Triple(MenuItem.AUDIT, "Audit", Icons.Clipboard),
    Triple(MenuItem.SURVEY, "Survey", Icons.Survey),
    Triple(MenuItem.INTEGRATION, "Integration", Icons.Link),
)

private val PanelShape = RoundedCornerShape(topEnd = 22.dp, bottomEnd = 22.dp)

/** Side menu opened from the header's menu button: profile, current property and the app's sections. */
@Composable
fun SideMenu(vm: AppViewModel) {
    val open = vm.state.menuOpen
    BackHandler(enabled = open, onBack = vm::closeMenu)

    AnimatedVisibility(open, enter = fadeIn(tween(220)), exit = fadeOut(tween(200))) {
        Box(Modifier.fillMaxSize().background(Color(0x99000000)).noRippleClick(vm::closeMenu))
    }
    AnimatedVisibility(
        open,
        enter = slideInHorizontally(tween(260)) { -it },
        exit = slideOutHorizontally(tween(220)) { -it },
    ) {
        Column(
            Modifier.fillMaxHeight().fillMaxWidth(0.84f).clip(PanelShape).background(C.Surface)
                .border(1.dp, C.Border, PanelShape).noRippleClick { }
                .statusBarsPadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Profile
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(48.dp).background(C.Yellow, CircleShape), contentAlignment = Alignment.Center) {
                    Txt("JL", 16, 700, C.OnYellow)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Txt("Jordan Lee", 17, 700)
                    Txt("Field engineer · Sclera FM", 13, 400, C.Muted)
                }
                RoundButton(38.dp, C.Btn, vm::closeMenu) { LineIcon(Icons.Close, 18.dp, C.Text, 2.6f) }
            }

            // Current property
            Row(
                Modifier.fillMaxWidth().clip(r(16)).background(C.Raised).border(1.dp, C.Border2, r(16))
                    .padding(start = 12.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(40.dp).clip(r(10)).background(C.YTintBg), contentAlignment = Alignment.Center) {
                    LineIcon(Icons.Building, 20.dp, C.Yellow, 2.2f)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Txt("CURRENT PROPERTY", 10, 700, C.Muted, spacing = 0.8f)
                    Txt(vm.propName, 16, 700)
                    Txt(plural(vm.assets.size, "asset", "assets"), 12, 400, C.Muted)
                }
                Pill("Switch", vm::switchPropertyFromMenu, height = 34.dp, hPad = 14.dp, size = 13)
            }

            // Sections
            Column(Modifier.padding(top = 4.dp)) {
                MENU.forEach { (item, label, icon) -> MenuRow(label, icon) { vm.pickMenu(item) } }
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, icon: Ico, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(54.dp).clip(r(12)).clickable(onClick = onClick).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(36.dp).clip(r(10)).background(C.Raised), contentAlignment = Alignment.Center) {
            LineIcon(icon, 19.dp, C.Yellow, 2.2f)
        }
        Txt(label, 16, 600, modifier = Modifier.weight(1f))
        LineIcon(Icons.ChevronRight, 18.dp, C.Muted, 2.4f)
    }
}
