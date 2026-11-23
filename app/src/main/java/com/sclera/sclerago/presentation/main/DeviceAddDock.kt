package com.sclera.sclerago.presentation.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sclera.sclerago.R
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.RoundButton
import com.sclera.sclerago.presentation.components.fabShadow
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

/** Docked above the tabs while scanned devices are ticked: clear, how many, and Add them as draft assets. */
@Composable
internal fun DeviceAddDock(vm: AppViewModel) {
    val n = vm.pickedDevices.size
    val shape = r(14)
    Row(
        Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 8.dp).fillMaxWidth().height(60.dp)
            .fabShadow(shape, 10.dp).clip(shape).background(C.Surface).border(1.dp, C.Border2, shape)
            .padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RoundButton(40.dp, Color.Transparent, vm::clearDevicePick) { LineIcon(Icons.Close, 18.dp, C.Text, 2.4f) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(stringResource(R.string.dash_selected, n), 14, 700)
            Text(
                if (vm.allDevicesPicked) stringResource(R.string.dash_clear_all) else stringResource(R.string.dash_select_all, vm.addableCount),
                Modifier.clickable(onClick = vm::selectAllDevices),
                color = C.Soft, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                textDecoration = TextDecoration.Underline, maxLines = 1,
            )
        }
        Pill(
            pluralStringResource(R.plurals.scrt_add_n_as_assets, n, n), vm::addPickedDevices,
            bg = C.Yellow, fg = C.OnYellow, height = 42.dp, hPad = 16.dp, size = 14, weight = 700,
        )
    }
}
