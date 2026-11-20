package com.sclera.sclerago.presentation.tagging

import com.sclera.sclerago.presentation.components.LocalToaster
import com.sclera.sclerago.presentation.components.ToastAction
import com.sclera.sclerago.presentation.components.ToastKind
import com.sclera.sclerago.presentation.common.UiText
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.TagScannerFailure
import com.sclera.sclerago.domain.model.NfcFailure
import com.sclera.sclerago.domain.model.TagKind
import com.sclera.sclerago.domain.usecase.tagging.TagScan
import com.sclera.sclerago.presentation.common.asString
import com.sclera.sclerago.presentation.components.BottomSheet
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.blockSwipe
import com.sclera.sclerago.presentation.components.noRippleClick
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.icons.Ico
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import com.sclera.sclerago.services.tagging.NfcTagReader
import kotlinx.coroutines.delay

/** What the tagging flow's screens, sheets and dialogs do. */
class TagFlowActions(
    val onCancel: () -> Unit,
    val onScan: (TagKind, TagScan) -> Unit,
    val onScannerFailure: (TagScannerFailure) -> Unit,
    val onNfcRead: (text: String, isBlank: Boolean) -> Unit,
    val onNfcFailure: (NfcFailure) -> Unit,
    val onConfirmRescan: () -> Unit,
    val onConfirmMove: () -> Unit,
    val onToastShown: () -> Unit,
)

private val NfcCard = Color(0xFF1B1D21)
private const val NFC_TIMEOUT_MS = 20_000L

/** Whatever the tagging flow shows over the asset's details: the scanner, the NFC dialog, the confirmations and failures, and the "tagged" toast. */
@Composable
fun TagFlowHost(state: TagUiState, actions: TagFlowActions) {
    when (val phase = state.phase) {
        TagPhase.Idle -> Unit
        TagPhase.ConfirmingRescan -> MessageSheet(
            icon = Icons.Qr, tint = C.Green, tintBg = Color(0x245DBE8C), tintBd = Color(0x805DBE8C),
            title = stringResource(R.string.tag_rescan_title), message = stringResource(R.string.tag_rescan_message),
            primary = stringResource(R.string.tag_rescan_confirm), onPrimary = actions.onConfirmRescan,
            secondary = stringResource(R.string.tag_cancel), onSecondary = actions.onCancel,
        )
        // A dialog window of its own, so the camera fills the whole screen, behind the status and navigation bars (the details screen
        // keeps its content inside them); the controls stay inside the safe area.
        is TagPhase.Scanning -> Dialog(
            onDismissRequest = actions.onCancel,
            properties = DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            TagScanScreen(
                kind = phase.kind, onScan = { actions.onScan(phase.kind, it) }, onFailure = actions.onScannerFailure, onClose = actions.onCancel,
            )
        }
        TagPhase.ReadingNfc -> NfcDialog(actions)
        TagPhase.Working -> WorkingOverlay()
        is TagPhase.ConfirmingMove -> MessageSheet(
            icon = Icons.Switch, tint = C.Yellow, tintBg = C.YTintBg, tintBd = C.YTintBd,
            title = stringResource(R.string.tag_move_title),
            message = phase.holder?.let { stringResource(R.string.tag_move_message_named, it) } ?: stringResource(R.string.tag_move_message),
            primary = stringResource(R.string.tag_move_confirm), onPrimary = actions.onConfirmMove,
            secondary = stringResource(R.string.tag_cancel), onSecondary = actions.onCancel,
        )
    }
    // "QR code tagged", or why the tag was refused, as the app's toast at the top (NFC off: with Open settings).
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val shown by rememberUpdatedState(actions.onToastShown)
    val notice = state.notice
    LaunchedEffect(notice) {
        when (notice) {
            null -> return@LaunchedEffect
            is TagNotice.Tagged -> toaster.show(UiText.Res(taggedText(notice.kind)), ToastKind.SUCCESS)
            is TagNotice.Refused -> toaster.show(
                notice.message, ToastKind.PROBLEM,
                if (notice.nfcSettings) ToastAction(UiText.Res(R.string.tag_nfc_open_settings)) { openNfcSettings(context) } else null,
            )
        }
        shown()
    }
}

/** A bottom sheet (centred on tablets): a round icon, a title and message, and two buttons. */
@Composable
private fun MessageSheet(
    icon: Ico, tint: Color, tintBg: Color, tintBd: Color,
    title: String, message: String,
    primary: String, onPrimary: () -> Unit,
    secondary: String, onSecondary: () -> Unit,
) {
    BottomSheet(onDismiss = onSecondary, maxHeightFraction = 0.7f, background = NfcCard) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 18.dp, bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(tintBg).border(1.dp, tintBd, CircleShape), contentAlignment = Alignment.Center) {
                LineIcon(icon, 24.dp, tint, 1.7f)
            }
            Txt(title, 20, 700, modifier = Modifier.padding(top = 14.dp), align = TextAlign.Center, maxLines = 3)
            Txt(message, 14, 400, C.Muted, Modifier.padding(top = 10.dp).widthIn(max = 320.dp), align = TextAlign.Center, maxLines = 6)
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill(secondary, onSecondary, Modifier.weight(1f).testTag(TagTags.SHEET_SECONDARY), height = 50.dp, size = 15, weight = 700)
            Pill(primary, onPrimary, Modifier.weight(1f).testTag(TagTags.SHEET_PRIMARY), bg = C.Yellow, fg = C.OnYellow, height = 50.dp, size = 15, weight = 700)
        }
    }
}

/** "Ready to scan": the pulsing NFC dialog while the reader waits for a tag. */
@Composable
private fun NfcDialog(actions: TagFlowActions) {
    BackHandler(onBack = actions.onCancel)
    val activity = LocalActivity.current
    val onRead by rememberUpdatedState(actions.onNfcRead)
    val onFailure by rememberUpdatedState(actions.onNfcFailure)
    DisposableEffect(activity) {
        if (activity != null) NfcTagReader.start(activity, { onRead(it.text, it.isBlank) }, { onFailure(it) }) else onFailure(NfcFailure.UNAVAILABLE)
        onDispose { activity?.let(NfcTagReader::stop) }
    }
    LaunchedEffect(Unit) {
        delay(NFC_TIMEOUT_MS)
        onFailure(NfcFailure.TIMED_OUT)
    }
    Box(Modifier.fillMaxSize().background(Color(0x99000000)).noRippleClick(actions.onCancel).blockSwipe().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 400.dp).fillMaxWidth().clip(r(26)).background(NfcCard).border(1.dp, C.Border, r(26)).noRippleClick { }
                .testTag(TagTags.NFC_DIALOG).padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NfcPulse()
            Txt(stringResource(R.string.tag_nfc_ready), 20, 700, align = TextAlign.Center)
            Txt(stringResource(R.string.tag_nfc_hold_message), 14, 400, C.Muted, align = TextAlign.Center, maxLines = 3)
            Pill(stringResource(R.string.tag_cancel), actions.onCancel, Modifier.padding(top = 8.dp).fillMaxWidth().testTag(TagTags.NFC_CANCEL), height = 50.dp, size = 15, weight = 700)
        }
    }
}

@Composable
private fun NfcPulse() {
    val transition = rememberInfiniteTransition(label = "nfc")
    Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
        listOf(0, 800, 1600).forEach { offset ->
            val phase by transition.animateFloat(
                0f, 1f, infiniteRepeatable(tween(2400, delayMillis = 0), RepeatMode.Restart, StartOffset(offset)), label = "ring",
            )
            Box(Modifier.size(72.dp).scale(0.7f + 1.3f * phase).alpha(0.8f * (1f - phase)).border(2.dp, C.Yellow, CircleShape))
        }
        Box(Modifier.size(72.dp).clip(CircleShape).background(C.YTintBg).border(1.dp, C.YTintBd, CircleShape), contentAlignment = Alignment.Center) {
            LineIcon(Icons.Nfc, 34.dp, C.Yellow, 1.8f)
        }
    }
}

@Composable
private fun WorkingOverlay() {
    BackHandler { }
    Box(Modifier.fillMaxSize().background(Color(0x99000000)).noRippleClick { }.blockSwipe().testTag(TagTags.WORKING), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator(color = C.Yellow, modifier = Modifier.size(40.dp))
            Txt(stringResource(R.string.tag_working), 16, 600)
        }
    }
}

/** Opens the phone's NFC settings, or its connection settings on a phone without that page. */
private fun openNfcSettings(context: Context) {
    val nfc = Intent(Settings.ACTION_NFC_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(nfc)
    } catch (_: ActivityNotFoundException) {
        runCatching { context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
