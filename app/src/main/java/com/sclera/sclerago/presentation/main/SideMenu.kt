package com.sclera.sclerago.presentation.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.sclera.sclerago.data.plural
import com.sclera.sclerago.domain.model.AuthState
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.RoundButton
import com.sclera.sclerago.presentation.components.endBorder
import com.sclera.sclerago.presentation.components.noRippleClick
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Ico
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.session.SessionViewModel
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

private val Wash = Color(0x80272A2F)

/** What a section row shows on its right edge. */
private sealed interface Trailing {
    data class Count(val n: Int) : Trailing
    data object Open : Trailing
    data object Soon : Trailing
}

/**
 * Side menu opened from the header's menu button: profile, Property / Users, the current property
 * with the app's sections under it, and Sign out pinned to the bottom.
 */
@Composable
fun SideMenu(vm: AppViewModel, sessionVm: SessionViewModel = hiltViewModel()) {
    val open = vm.state.menuOpen
    BackHandler(enabled = open, onBack = vm::closeMenu)
    val session = (sessionVm.authState.collectAsState().value as? AuthState.SignedIn)?.session
    val name = session?.displayName.orEmpty()
    var confirmSignOut by remember { mutableStateOf(false) }
    var sectionsOpen by rememberSaveable { mutableStateOf(true) }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            containerColor = C.Surface,
            title = { Txt("Sign out?", 19, 700) },
            text = {
                Txt(
                    "You'll need to sign in again with ${session?.email ?: "your email"} to use Sclera Go.",
                    14, 400, C.Muted, maxLines = 4,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    vm.closeMenu()
                    sessionVm.logout()
                }) { Txt("Sign out", 15, 700, C.Red) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Txt("Cancel", 15, 600, C.Text) }
            },
        )
    }

    AnimatedVisibility(open, enter = fadeIn(tween(220)), exit = fadeOut(tween(200))) {
        Box(Modifier.fillMaxSize().background(Color(0x99000000)).noRippleClick(vm::closeMenu))
    }
    AnimatedVisibility(
        open,
        enter = slideInHorizontally(tween(260)) { -it },
        exit = slideOutHorizontally(tween(220)) { -it },
    ) {
        Column(
            Modifier.fillMaxHeight().width(310.dp).background(C.Surface).endBorder(C.Border).noRippleClick { },
        ) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).statusBarsPadding()
                    .padding(start = 14.dp, end = 14.dp, top = 20.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Profile
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(46.dp).background(C.Yellow, CircleShape), contentAlignment = Alignment.Center) {
                        Txt(initials(name), 16, 800, C.OnYellow)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Txt(name, 16, 700)
                        Txt(listOfNotNull(session?.role, session?.email).joinToString(" · "), 12, 400, C.Muted)
                    }
                    RoundButton(38.dp, C.Btn, vm::closeMenu) { LineIcon(Icons.Close, 14.dp, C.Text, 2.6f) }
                }

                // Top-level destinations
                TopRow("Property list", Icons.Building) { vm.pickMenu(MenuItem.PROPERTY) }
                TopRow("Users", Icons.Users) { vm.pickMenu(MenuItem.USERS) }

                // Current property and its sections
                Column(Modifier.fillMaxWidth().clip(r(14)).background(Wash).border(1.dp, C.Border, r(14))) {
                    val chevron by animateFloatAsState(if (sectionsOpen) 0f else -90f, tween(200), label = "chevron")
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            Modifier.weight(1f).clip(r(10)).clickable { sectionsOpen = !sectionsOpen },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(Modifier.size(38.dp).clip(r(10)).background(C.YTintBg), contentAlignment = Alignment.Center) {
                                LineIcon(Icons.Building, 16.dp, C.Yellow, 1.9f)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Txt("CURRENT PROPERTY", 10, 600, C.Muted, spacing = 0.4f)
                                Txt(vm.propName, 15, 700)
                                Txt(plural(vm.assets.size, "asset", "assets"), 12, 400, C.Muted)
                            }
                        }
                        Pill("Switch", vm::switchPropertyFromMenu, height = 34.dp, hPad = 10.dp, size = 12)
                        Box(Modifier.width(22.dp).clickable { sectionsOpen = !sectionsOpen }, contentAlignment = Alignment.Center) {
                            LineIcon(Icons.ChevronDown, 14.dp, C.Muted, 2.6f, Modifier.rotate(chevron))
                        }
                    }
                    AnimatedVisibility(sectionsOpen, enter = expandVertically(), exit = shrinkVertically()) {
                        Column(Modifier.topBorder(C.Border).padding(start = 12.dp, end = 8.dp)) {
                            SectionRow("Assets", Icons.Box, Trailing.Count(vm.assets.size), first = true) { vm.pickMenu(MenuItem.ASSETS) }
                            SectionRow(
                                "Validation", Icons.ShieldCheck,
                                vm.propJobs.size.let { if (it > 0) Trailing.Count(it) else Trailing.Open },
                            ) { vm.pickMenu(MenuItem.VALIDATION) }
                            SectionRow("Configuration", Icons.Sliders, Trailing.Open) { vm.pickMenu(MenuItem.CONFIGURATION) }
                            SectionRow("Audit", Icons.Clipboard, Trailing.Soon) { vm.pickMenu(MenuItem.AUDIT) }
                            SectionRow("Survey", Icons.Survey, Trailing.Soon) { vm.pickMenu(MenuItem.SURVEY) }
                            SectionRow("Integration", Icons.Link, Trailing.Soon) { vm.pickMenu(MenuItem.INTEGRATION) }
                        }
                    }
                }
            }

            // Sign out, pinned to the bottom
            Box(Modifier.fillMaxWidth().topBorder(C.Border).navigationBarsPadding().padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 14.dp)) {
                Row(
                    Modifier.fillMaxWidth().height(48.dp).clip(r(14)).background(C.Red.copy(alpha = 0.12f))
                        .border(1.dp, C.Red.copy(alpha = 0.35f), r(14)).clickable { confirmSignOut = true },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    LineIcon(Icons.SignOut, 16.dp, C.Red, 2f)
                    Spacer(Modifier.width(8.dp))
                    Txt("Sign out", 15, 600, C.Red)
                }
            }
        }
    }
}

private fun initials(name: String): String {
    val parts = name.substringBefore('@').split(' ', '.', '_', '-').filter { it.isNotBlank() }
    return listOfNotNull(parts.firstOrNull(), parts.drop(1).lastOrNull())
        .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
}

@Composable
private fun IconBox(icon: Ico) {
    Box(Modifier.size(34.dp).clip(r(9)).background(C.Raised), contentAlignment = Alignment.Center) {
        LineIcon(icon, 16.dp, C.Yellow, 1.9f)
    }
}

/** Property list / Users: a card of its own. */
@Composable
private fun TopRow(label: String, icon: Ico, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(r(14)).background(Wash).border(1.dp, C.Border, r(14))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBox(icon)
        Txt(label, 15, 600, modifier = Modifier.weight(1f))
        LineIcon(Icons.ChevronRight, 14.dp, C.Dim, 2.6f)
    }
}

/** A section of the current property; rows after the first are separated by a line right of the icon. */
@Composable
private fun SectionRow(label: String, icon: Ico, trailing: Trailing, first: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBox(icon)
        Row(
            Modifier.weight(1f).heightIn(min = 48.dp).then(if (first) Modifier else Modifier.topBorder(C.Border)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Txt(label, 15, 600, modifier = Modifier.weight(1f))
            when (trailing) {
                is Trailing.Count -> Box(Modifier.clip(CircleShape).background(C.Raised).padding(horizontal = 9.dp, vertical = 3.dp)) {
                    Txt(trailing.n.toString(), 12, 700)
                }
                Trailing.Open -> LineIcon(Icons.ChevronRight, 14.dp, C.Dim, 2.6f)
                Trailing.Soon -> Box(Modifier.clip(CircleShape).background(C.Raised).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Txt("Soon", 10, 600, C.Muted)
                }
            }
        }
    }
}
