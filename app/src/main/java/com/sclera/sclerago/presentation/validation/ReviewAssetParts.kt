package com.sclera.sclerago.presentation.validation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.validation.AssetInfoRow
import com.sclera.sclerago.domain.validation.ReviewAssetInfo
import com.sclera.sclerago.presentation.components.Capsule
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

// The review's asset sheet: what the asset is on this phone (the web's AssetSummary and AssetDetails).

/** The asset's status as the web's review names it: drafts are being collected; signed off is validated. */
@Composable
private fun statusText(st: Status): String = stringResource(
    when (st) {
        Status.DRAFT, Status.INPUT, Status.AI -> R.string.rev_st_collecting
        Status.SUBMITTED -> R.string.rev_st_submitted
        Status.EXCEPTION -> R.string.rev_st_exception
        Status.VALIDATED -> R.string.rev_st_validated
    },
)

/** Its first letter, where it is ("Property 1 › No location yet"), its status and how many of its fields are filled. */
@Composable
internal fun ReviewAssetStrip(name: String, local: Asset?, propName: String, info: ReviewAssetInfo?) {
    Row(
        Modifier.fillMaxWidth().clip(r(12)).background(C.Bg).border(1.dp, C.Border, r(12)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(r(10)).background(C.Raised), contentAlignment = Alignment.Center) {
            Txt(name.trim().take(1).uppercase().ifEmpty { "?" }, 16, 800)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (local == null) {
                Txt(stringResource(R.string.rev_asset_not_here), 11, 400, C.Muted, maxLines = 3)
                return@Column
            }
            val where = local.where.ifBlank { stringResource(R.string.rev_no_location) }
            Txt(if (propName.isBlank()) where else stringResource(R.string.rev_place, propName, where), 13, 700, maxLines = 2)
            Txt(
                info?.let { stringResource(R.string.rev_fields_filled, statusText(local.st), it.filled, it.total) } ?: statusText(local.st),
                11, 500, C.Muted, maxLines = 2,
            )
            if (info != null && info.total > 0) {
                Box(Modifier.fillMaxWidth().height(4.dp).clip(Capsule).background(C.Raised)) {
                    Box(Modifier.fillMaxWidth(info.filled.toFloat() / info.total).fillMaxHeight().clip(Capsule).background(C.Green))
                }
            }
        }
    }
}

/** Asset info: each field and its value, then where it is and its status; and the way to the asset to fix them. */
@Composable
internal fun ReviewAssetInfoTab(local: Asset?, info: ReviewAssetInfo?, loading: Boolean, onOpenAsset: (String) -> Unit) {
    if (local == null) {
        Txt(stringResource(R.string.rev_asset_not_here), 12, 400, C.Muted, maxLines = 3)
        return
    }
    if (loading) {
        Txt(stringResource(R.string.val_loading), 12, 400, C.Muted)
        return
    }
    val rows = info?.rows.orEmpty() + listOf(
        AssetInfoRow(stringResource(R.string.rev_info_location), local.where.ifBlank { null }),
        AssetInfoRow(stringResource(R.string.rev_info_status), statusText(local.st)),
    )
    Column(Modifier.fillMaxWidth().clip(r(12)).background(C.Bg).border(1.dp, C.Border, r(12)).padding(horizontal = 12.dp)) {
        rows.forEachIndexed { i, row ->
            Row(
                Modifier.fillMaxWidth().then(if (i > 0) Modifier.topBorder(C.Border) else Modifier).padding(vertical = 10.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Txt(row.label, 12, 500, C.Muted, Modifier.weight(0.42f), maxLines = 2)
                Txt(row.value ?: "—", 13, 600, if (row.value == null) C.Muted else C.Text, Modifier.weight(0.58f), maxLines = 4)
            }
        }
    }
    Pill(
        stringResource(R.string.rev_open_asset), { onOpenAsset(local.id) }, Modifier.fillMaxWidth(),
        bg = C.Yellow, fg = C.OnYellow, height = 46.dp, size = 14, weight = 700,
    )
    Txt(stringResource(R.string.rev_info_hint), 11, 400, C.Muted, Modifier.fillMaxWidth(), maxLines = 3, align = TextAlign.Center)
}
