package com.sclera.sclerago.presentation.main

import com.sclera.sclerago.presentation.model.OnlineOnly
import com.sclera.sclerago.presentation.components.OfflineGate
import com.sclera.sclerago.presentation.components.LocalToaster
import com.sclera.sclerago.presentation.common.UiText
import com.sclera.sclerago.presentation.common.ConnectivityViewModel
import com.sclera.sclerago.presentation.components.pressClickable
import com.sclera.sclerago.presentation.components.PRESSED_CARD
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.AuthState
import com.sclera.sclerago.domain.model.UserSession
import com.sclera.sclerago.presentation.components.AccessFailedBanner
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
import com.sclera.sclerago.services.media.MediaLoader
import java.util.Locale

/** What a section row shows on its right edge. */
private sealed interface Trailing {
    data class Count(val n: Int) : Trailing
    data object Open : Trailing
}

/** Test tags of the side menu. */
object MenuTags {
    const val SIGN_OUT = "menu_sign_out"
    const val SIGN_OUT_CONFIRM = "menu_sign_out_confirm"
    const val SIGN_OUT_WARNING = "menu_sign_out_warning"
    const val SWITCH_PROPERTY = "menu_switch_property"
    fun item(item: MenuItem) = "menu_item_${item.name.lowercase()}"
}

/** The menu row for the page that is showing: a side-menu page, Validation, Configuration, or else the assets. */
internal fun activeMenuItem(adminPage: AdminPage?, validationOpen: Boolean, configurationOpen: Boolean, integrationOpen: Boolean = false): MenuItem = when {
    adminPage == AdminPage.PROPERTIES -> MenuItem.PROPERTY
    adminPage == AdminPage.PEOPLE -> MenuItem.USERS
    validationOpen -> MenuItem.VALIDATION
    configurationOpen -> MenuItem.CONFIGURATION
    integrationOpen -> MenuItem.INTEGRATION
    else -> MenuItem.ASSETS
}

/**
 * Side menu opened from the header's menu button: profile, Property / Users, the current property
 * with the app's sections under it, and Sign out pinned to the bottom.
 */
@Composable
fun SideMenu(
    vm: AppViewModel,
    sessionVm: SessionViewModel = hiltViewModel(),
    connectivity: ConnectivityViewModel = hiltViewModel(),
) {
    val auth by sessionVm.authState.collectAsStateWithLifecycle()
    val online by connectivity.online.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val offlineNote = { toaster.show(UiText.Res(OnlineOnly.SIGN_OUT.message)) }
    val s = vm.state
    SideMenuPanel(
        open = s.menuOpen,
        session = (auth as? AuthState.SignedIn)?.session,
        propertyName = vm.propName.takeIf { vm.hasProperty },
        assetCount = vm.assets.size,
        showAssets = vm.seesAssets,
        showValidation = vm.canCheck,
        showConfiguration = vm.canConfigure,
        showIntegration = vm.canConfigure,
        showUsers = vm.canManageAccess,
        accessFailed = vm.accessFailed,
        onRetryAccess = vm::retryAccess,
        active = activeMenuItem(s.adminPage, s.valOpen, s.cfgOpen, s.intOpen),
        onClose = vm::closeMenu,
        onPick = vm::pickMenu,
        onSignOut = { if (!sessionVm.logout()) offlineNote() },
        signOutOffline = !online,
        onSignOutOffline = offlineNote,
    )
}

/** The menu itself, stateless apart from its folded sections and the sign-out question. */
@Composable
internal fun SideMenuPanel(
    open: Boolean,
    session: UserSession?,
    /** The downloaded property; null until one is downloaded, and then its card and sections are hidden. */
    propertyName: String?,
    assetCount: Int,
    active: MenuItem,
    onClose: () -> Unit,
    /** The Assets section shows except to a group that only configures (the web's seesDataCollection). */
    showAssets: Boolean = true,
    /** The Validation section shows only to someone who may run checks (the server's check permission). */
    showValidation: Boolean = true,
    /** The Configuration section shows only to someone who may configure the property (the server's configure permission), as on the web. */
    showConfiguration: Boolean = true,
    /** The Integration section shows only to someone who may configure the property (the server's configure permission). */
    showIntegration: Boolean = true,
    /** Users shows only to someone who may manage people and access (the server lists users only to them). */
    showUsers: Boolean = true,
    /** The user's access couldn't be loaded: a note under the profile says why sections are missing, with Retry. */
    accessFailed: Boolean = false,
    onRetryAccess: () -> Unit = {},
    onPick: (MenuItem) -> Unit,
    onSignOut: () -> Unit,
    /** Offline: Sign out is greyed out (the server has to end the session); a tap calls [onSignOutOffline] to say why. */
    signOutOffline: Boolean = false,
    onSignOutOffline: () -> Unit = {},
) {
    BackHandler(enabled = open, onBack = onClose)
    val name = session?.displayName.orEmpty()
    var confirmSignOut by remember { mutableStateOf(false) }
    var sectionsOpen by rememberSaveable { mutableStateOf(true) }

    if (confirmSignOut) {
        val email = session?.email
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            containerColor = C.Surface,
            title = { Txt(stringResource(R.string.menu_sign_out_title), 17, 700) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Txt(
                        if (email != null) stringResource(R.string.menu_sign_out_message, email) else stringResource(R.string.menu_sign_out_message_generic),
                        13, 400, C.Muted, maxLines = Int.MAX_VALUE,
                    )
                    // Signing out deletes this device's data, so work that hasn't reached the server would be lost.
                    Txt(
                        stringResource(R.string.menu_sign_out_sync_warning),
                        13, 600, C.Red, Modifier.testTag(MenuTags.SIGN_OUT_WARNING), maxLines = Int.MAX_VALUE,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmSignOut = false
                        onClose()
                        onSignOut()
                    },
                    Modifier.testTag(MenuTags.SIGN_OUT_CONFIRM),
                ) { Txt(stringResource(R.string.menu_sign_out), 14, 700, C.Red) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Txt(stringResource(R.string.action_cancel), 14, 600, C.Text) }
            },
        )
    }

    AnimatedVisibility(open, enter = fadeIn(tween(220)), exit = fadeOut(tween(200))) {
        Box(Modifier.fillMaxSize().background(C.Scrim).noRippleClick(onClose))
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
                    Avatar(name, session?.avatarUrl)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Txt(name, 15, 700)
                        listOfNotNull(session?.role, session?.organisation).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                            Txt(it, 11, 600, C.Yellow)
                        }
                        session?.email?.let { Txt(it, 11, 400, C.Muted) }
                    }
                    val closeLabel = stringResource(R.string.action_close)
                    RoundButton(38.dp, C.Btn, onClose, Modifier.semantics { contentDescription = closeLabel }) {
                        LineIcon(Icons.Close, 16.dp, C.Text, 3f)
                    }
                }

                if (accessFailed) AccessFailedBanner(onRetryAccess)

                // Top-level destinations
                TopRow(MenuItem.PROPERTY, stringResource(R.string.property_list_title), Icons.Buildings, active, onPick)
                if (showUsers) TopRow(MenuItem.USERS, stringResource(R.string.menu_users), Icons.Users, active, onPick)

                // Current property and its sections (none until a property is downloaded)
                if (propertyName != null) Column(Modifier.fillMaxWidth().clip(r(14)).background(C.CardWash).border(1.dp, C.Border, r(14))) {
                    val chevron by animateFloatAsState(if (sectionsOpen) 0f else -90f, tween(200), label = "chevron")
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            Modifier.weight(1f).pressClickable(shape = r(10)) { sectionsOpen = !sectionsOpen },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(Modifier.size(38.dp).clip(r(10)).background(C.YTintBg), contentAlignment = Alignment.Center) {
                                LineIcon(Icons.Buildings, 20.dp, C.Yellow, 1.8f)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Txt(stringResource(R.string.menu_current_property).uppercase(Locale.getDefault()), 10, 600, C.Muted, spacing = 0.4f)
                                Txt(propertyName, 14, 700)
                                Txt(pluralStringResource(R.plurals.asset_count, assetCount, assetCount), 11, 400, C.Muted)
                            }
                        }
                        // Switch property: opens the property list to pick another one.
                        val switchLabel = stringResource(R.string.menu_switch_property)
                        Box(
                            Modifier.size(40.dp).pressClickable(shape = r(10), role = Role.Button) { onPick(MenuItem.PROPERTY) }.background(C.Btn)
                                .semantics { contentDescription = switchLabel }.testTag(MenuTags.SWITCH_PROPERTY),
                            contentAlignment = Alignment.Center,
                        ) { LineIcon(Icons.Switch, 18.dp, C.Yellow, 2.2f) }
                        Box(Modifier.pressClickable { sectionsOpen = !sectionsOpen }.width(22.dp), contentAlignment = Alignment.Center) {
                            LineIcon(Icons.ChevronDown, 16.dp, C.Muted, 3f, Modifier.rotate(chevron))
                        }
                    }
                    AnimatedVisibility(sectionsOpen, enter = expandVertically(), exit = shrinkVertically()) {
                        Column(Modifier.topBorder(C.Border).padding(start = 12.dp, end = 8.dp)) {
                            if (showAssets) SectionRow(MenuItem.ASSETS, stringResource(R.string.label_assets), Icons.Box, Trailing.Count(assetCount), active, onPick, first = true)
                            if (showValidation) SectionRow(
                                MenuItem.VALIDATION, stringResource(R.string.menu_validation), Icons.ShieldCheck,
                                Trailing.Open, active, onPick,
                            )
                            if (showConfiguration) SectionRow(MenuItem.CONFIGURATION, stringResource(R.string.menu_configuration), Icons.Sliders, Trailing.Open, active, onPick)
                            if (showIntegration) SectionRow(MenuItem.INTEGRATION, stringResource(R.string.menu_integration), Icons.Link, Trailing.Open, active, onPick)
                        }
                    }
                }
            }

            // Sign out, pinned to the bottom
            Box(Modifier.fillMaxWidth().topBorder(C.Border).navigationBarsPadding().padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 14.dp)) {
                OfflineGate(signOutOffline, onSignOutOffline, Modifier.fillMaxWidth()) { Row(
                    Modifier.fillMaxWidth().height(48.dp).pressClickable(shape = r(14), pressed = PRESSED_CARD, role = Role.Button) { confirmSignOut = true }.background(C.Red.copy(alpha = 0.12f))
                        .border(1.dp, C.Red.copy(alpha = 0.35f), r(14)).testTag(MenuTags.SIGN_OUT)
                        ,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    LineIcon(Icons.SignOut, 19.dp, C.Red, 2.2f)
                    Spacer(Modifier.width(8.dp))
                    Txt(stringResource(R.string.menu_sign_out), 14, 600, C.Red)
                } }
            }
        }
    }
}

private const val AVATAR_PX = 138

/** Profile photo, or the user's initials on yellow when there is none (or while it loads). */
@Composable
private fun Avatar(name: String, url: String?) {
    val context = LocalContext.current
    val photo by produceState(url?.let { MediaLoader.cached(it, AVATAR_PX) }, url) {
        if (value == null && url != null) value = MediaLoader.load(context, url, video = false, maxPx = AVATAR_PX)
    }
    Box(Modifier.size(46.dp).clip(CircleShape).background(C.Yellow), contentAlignment = Alignment.Center) {
        val p = photo
        if (p != null) {
            Image(p, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Txt(initials(name), 15, 800, C.OnYellow)
        }
    }
}

internal fun initials(name: String): String {
    val parts = name.substringBefore('@').split(' ', '.', '_', '-').filter { it.isNotBlank() }
    return listOfNotNull(parts.firstOrNull(), parts.drop(1).lastOrNull())
        .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
}

@Composable
private fun IconBox(icon: Ico) {
    Box(Modifier.size(34.dp).clip(r(9)).background(C.Raised), contentAlignment = Alignment.Center) {
        LineIcon(icon, 20.dp, C.Yellow, 1.8f)
    }
}

/** Property list / Users: a card of its own. */
@Composable
private fun TopRow(item: MenuItem, label: String, icon: Ico, active: MenuItem, onPick: (MenuItem) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).pressClickable(shape = r(14), pressed = PRESSED_CARD, role = Role.Button) { onPick(item) }.background(C.CardWash).border(1.dp, C.Border, r(14))
            .semantics { selected = item == active }.testTag(MenuTags.item(item))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBox(icon)
        Txt(label, 14, 600, modifier = Modifier.weight(1f))
        LineIcon(Icons.ChevronRight, 14.dp, C.Dim, 2.6f)
    }
}

/** A section of the current property; rows after the first are separated by a line right of the icon. */
@Composable
private fun SectionRow(
    item: MenuItem,
    label: String,
    icon: Ico,
    trailing: Trailing,
    active: MenuItem,
    onPick: (MenuItem) -> Unit,
    first: Boolean = false,
) {
    Row(
        Modifier.pressClickable(pressed = PRESSED_CARD, role = Role.Button) { onPick(item) }.fillMaxWidth().heightIn(min = 48.dp).semantics { selected = item == active }.testTag(MenuTags.item(item))
            ,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBox(icon)
        Row(
            Modifier.weight(1f).heightIn(min = 48.dp).then(if (first) Modifier else Modifier.topBorder(C.Border)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Txt(label, 14, 600, modifier = Modifier.weight(1f))
            when (trailing) {
                is Trailing.Count -> Box(Modifier.clip(CircleShape).background(C.Raised).padding(horizontal = 9.dp, vertical = 3.dp)) {
                    Txt(trailing.n.toString(), 11, 700)
                }
                Trailing.Open -> LineIcon(Icons.ChevronRight, 14.dp, C.Dim, 2.6f)
            }
        }
    }
}
