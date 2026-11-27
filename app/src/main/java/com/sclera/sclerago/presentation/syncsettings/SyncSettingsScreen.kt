package com.sclera.sclerago.presentation.syncsettings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.SyncSettings
import com.sclera.sclerago.presentation.common.formatDateAndTime
import com.sclera.sclerago.presentation.common.is24Hour
import com.sclera.sclerago.presentation.components.OnOffSwitch
import com.sclera.sclerago.presentation.components.SkeletonBlock
import com.sclera.sclerago.presentation.components.StatCard
import com.sclera.sclerago.presentation.components.StatRow
import com.sclera.sclerago.presentation.components.SubPageBar
import com.sclera.sclerago.presentation.components.agoText
import com.sclera.sclerago.presentation.components.pageGutter
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Ico
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import kotlinx.coroutines.delay

/** Test tags of the Sync settings page. */
object SyncSettingsTags {
    const val PAGE = "sync_settings_page"
    const val ASSETS = "sync_assets_waiting"
    const val MEDIA = "sync_media_waiting"
    const val LAST = "sync_last_synced"
    const val DATA = "sync_data_switch"
    const val PHOTOS = "sync_media_switch"
}

/**
 * Sync settings of the open property (side menu, under it): how many assets and photos or videos wait to upload, when
 * it last synced, and the Data sync and Photo and video sync switches (they stop only what comes down; the device's own
 * changes always upload).
 */
@Composable
fun SyncSettingsScreen(
    propertyId: String,
    propName: String,
    onClose: () -> Unit,
    /** The header's menu button: opens the side menu. */
    onMenu: () -> Unit = {},
    vm: SyncSettingsViewModel = hiltViewModel(),
) {
    LaunchedEffect(propertyId) { vm.bind(propertyId) }
    val s by vm.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(C.Bg).testTag(SyncSettingsTags.PAGE)) {
        Column(Modifier.fillMaxSize()) {
            SubPageBar(stringResource(R.string.sync_title), propName, onMenu)
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = pageGutter())
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Waiting(s)
                LastSynced(s)
                Switches(s, vm::setDataSync, vm::setMediaSync)
            }
        }
    }
}

/**
 * Assets waiting and Media waiting, side by side (placeholders until first read): the same tiles as the Property list's
 * and Users and access' counts; a label too long for a phone's half width shrinks a little rather than being cut off.
 */
@Composable
private fun Waiting(s: SyncSettings?) {
    StatRow {
        StatCard(
            Icons.Box, C.Yellow, s?.assetsWaiting?.toString().orEmpty(), stringResource(R.string.sync_assets_waiting),
            Modifier.weight(1f).testTag(SyncSettingsTags.ASSETS), valueSize = 24, labelSize = 12, prominent = true, loading = s == null, labelMinSize = 10, labelLines = 2,
        )
        StatCard(
            Icons.Gallery, C.Blue, s?.mediaWaiting?.toString().orEmpty(), stringResource(R.string.sync_media_waiting),
            Modifier.weight(1f).testTag(SyncSettingsTags.MEDIA), valueSize = 24, labelSize = 12, prominent = true, loading = s == null, labelMinSize = 10, labelLines = 2,
        )
    }
}

/** When it last synced: how long ago (kept current while the page is open) and the date and time. */
@Composable
private fun LastSynced(s: SyncSettings?) {
    val at = s?.lastSyncedAt
    // "just now" turns into "1 min ago" without anything else changing.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(AGO_TICK_MS)
            value = System.currentTimeMillis()
        }
    }
    Row(
        Modifier.fillMaxWidth().clip16().padding(horizontal = 16.dp, vertical = 12.dp).testTag(SyncSettingsTags.LAST),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LineIcon(Icons.History, 22.dp, C.Soft, 2f)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(stringResource(R.string.sync_last_synced), 13, 500, C.Muted)
            when {
                s == null -> SkeletonBlock(Modifier.padding(vertical = 4.dp).width(120.dp).height(14.dp))
                at == null -> Txt(stringResource(R.string.sync_never), 16, 700)
                else -> {
                    Txt(agoText(at, now), 16, 700)
                    val locale = LocalConfiguration.current.locales[0]
                    Txt(formatDateAndTime(at, locale, use24Hour = is24Hour()), 12, 400, C.Muted)
                }
            }
        }
    }
}

/** Data sync and Photo and video sync, in one card. */
@Composable
private fun Switches(s: SyncSettings?, onData: (Boolean) -> Unit, onMedia: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth().clip16()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Txt(stringResource(R.string.sync_section), 18, 700)
            Txt(stringResource(R.string.sync_shared), 12, 400, C.Muted, maxLines = Int.MAX_VALUE)
        }
        SwitchRow(
            Icons.Database, stringResource(R.string.sync_data), stringResource(R.string.sync_data_sub),
            on = s?.dataSync ?: true, enabled = s != null, tag = SyncSettingsTags.DATA, divider = true, onChange = onData,
        )
        SwitchRow(
            Icons.Photos, stringResource(R.string.sync_media), stringResource(R.string.sync_media_sub),
            on = s?.mediaSync ?: true, enabled = s != null, tag = SyncSettingsTags.PHOTOS, divider = true, onChange = onMedia,
        )
    }
}

@Composable
private fun SwitchRow(
    icon: Ico,
    title: String,
    sub: String,
    on: Boolean,
    enabled: Boolean,
    tag: String,
    divider: Boolean = false,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().then(if (divider) Modifier.topBorder(C.Border) else Modifier).heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LineIcon(icon, 22.dp, C.Yellow, 2f)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(title, 15, 700)
            Txt(sub, 12, 400, C.Muted, maxLines = Int.MAX_VALUE)
        }
        OnOffSwitch(on, Modifier.testTag(tag)) { if (enabled) onChange(it) }
    }
}

/** The page's card look: the surface colour, a thin border and rounded corners. */
private fun Modifier.clip16(): Modifier =
    clip(r(16)).background(C.Surface).border(1.dp, C.Border, r(16))

/** How often "how long ago" is worked out again. */
private const val AGO_TICK_MS = 15_000L
