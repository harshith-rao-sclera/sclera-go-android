package com.sclera.sclerago.presentation.config

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.R
import com.sclera.sclerago.domain.model.ClassCount
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FieldKind
import com.sclera.sclerago.domain.model.TypeField
import com.sclera.sclerago.domain.model.configFieldKindOf
import com.sclera.sclerago.presentation.components.BottomSheet
import com.sclera.sclerago.presentation.components.CfgCard
import com.sclera.sclerago.presentation.components.CloseButton
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt

internal class AssetTypeDetailState(
    /** Names from the system type down. */
    val path: List<String>,
    val type: ClassCount,
    val fields: List<TypeField>,
    val fieldsLoad: LoadState,
    /** The property's standard fields the type's form also has. */
    val standardFields: List<FieldCfg>,
)

/** One type (system type, asset type or sub type) as a sheet over the list: counts, its sub types and the fields it brings. */
@Composable
internal fun AssetTypeDetailSheet(state: AssetTypeDetailState, onOpenSub: (String) -> Unit, onClose: () -> Unit) {
    BottomSheet(onClose, maxHeightFraction = 0.94f) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Breadcrumb(state.path, Modifier.weight(1f))
            CloseButton(onClose, size = 36.dp, iconSize = 16.dp)
        }
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 30.dp).testTag(AssetTypesTags.DETAIL),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TypeSummary(state)
            SubTypesCard(state.type.kids, onOpenSub)
            if (state.type.formatId != null) FieldsCard(state)
            if (state.standardFields.isNotEmpty()) StandardFieldsCard(state.standardFields)
        }
    }
}

@Composable
private fun Breadcrumb(path: List<String>, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        path.forEachIndexed { i, name ->
            val last = i == path.lastIndex
            if (i > 0) LineIcon(Icons.ChevronRight, 9.dp, C.Dim, 3.4f)
            Txt(name, 12, if (last) 600 else 400, if (last) C.Text else C.Muted, Modifier.weight(1f, fill = false))
        }
    }
}

@Composable
private fun TypeSummary(state: AssetTypeDetailState) {
    val kindLabel = when (state.path.size) {
        1 -> R.string.cfgt_types_system_type
        2 -> R.string.cfgt_types_asset_type
        else -> R.string.cfgt_types_sub_type
    }
    CfgCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Txt(stringResource(kindLabel), 12, 400, C.Muted)
            Txt(state.type.name, 22, 700, maxLines = 3)
            Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Txt(stringResource(R.string.cfgt_types_assets_count, state.type.assets), 12, 600)
                Txt(stringResource(R.string.cfgt_types_subtypes_count, state.type.kids.size), 12, 600)
            }
        }
    }
}

@Composable
private fun SubTypesCard(kids: List<ClassCount>, onOpen: (String) -> Unit) {
    CfgCard {
        Txt(stringResource(R.string.cfgt_types_subtypes_title), 15, 700, modifier = Modifier.padding(14.dp))
        if (kids.isEmpty()) {
            Txt(stringResource(R.string.cfgt_types_no_subtypes), 13, 400, C.Muted, Modifier.fillMaxWidth().topBorder(C.Border).padding(14.dp), maxLines = Int.MAX_VALUE)
        }
        kids.forEach { kid ->
            Row(
                Modifier.fillMaxWidth().topBorder(C.Border).heightIn(min = 44.dp).clickable { onOpen(kid.name) }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Txt(kid.name, 14, 400, modifier = Modifier.weight(1f), maxLines = 2)
                Txt(kid.assets.toString(), 12, 600, C.Muted)
                LineIcon(Icons.ChevronRight, 10.dp, C.Dim, 3f)
            }
        }
    }
}

@Composable
private fun FieldsCard(state: AssetTypeDetailState) {
    CfgCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(stringResource(R.string.cfgt_types_fields_of, state.type.name), 15, 700, maxLines = 2)
            Txt(stringResource(R.string.cfgt_types_fields_hint), 12, 400, C.Muted, maxLines = Int.MAX_VALUE)
        }
        val note = when {
            state.fieldsLoad == LoadState.LOADING -> R.string.cfgt_types_fields_loading
            state.fieldsLoad == LoadState.FAILED -> R.string.cfgt_types_fields_failed
            state.fields.isEmpty() -> R.string.cfgt_types_fields_empty
            else -> null
        }
        if (note != null) Txt(stringResource(note), 13, 400, C.Muted, Modifier.fillMaxWidth().topBorder(C.Border).padding(14.dp), maxLines = Int.MAX_VALUE)
        state.fields.forEachIndexed { i, f -> TypeFieldRow(i + 1, f) }
    }
}

@Composable
private fun TypeFieldRow(number: Int, f: TypeField) {
    val kind = configFieldKindOf(f.type)
    Row(
        Modifier.fillMaxWidth().topBorder(C.Border).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Txt(number.toString(), 12, 600, C.Muted, Modifier.width(18.dp))
        Column(Modifier.weight(1f).padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Txt(f.name, 14, 600, maxLines = 2)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    Modifier.heightIn(min = 20.dp).clip(r(6)).background(C.Raised).padding(horizontal = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    LineIcon((kind ?: FieldKind.TEXT).icon(), 10.dp, C.Soft, 2.2f)
                    Txt(kind?.shown ?: f.type, 11, 600, C.Soft)
                }
                if (f.options.isNotEmpty()) {
                    Txt(pluralStringResource(R.plurals.cfg_field_options, f.options.size, f.options.size), 11, 400, C.Muted)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StandardFieldsCard(fields: List<FieldCfg>) {
    CfgCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Txt(stringResource(R.string.cfgt_types_also_on_form, fields.size), 13, 600, maxLines = 2)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                fields.forEach { f ->
                    Row(
                        Modifier.heightIn(min = 26.dp).clip(r(13)).background(C.Raised).padding(horizontal = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Txt(f.label, 12, 500, C.Soft)
                        if (f.required) Txt("*", 12, 400, C.Red)
                    }
                }
            }
        }
    }
}
