package com.sclera.sclerago.presentation.property

import com.sclera.sclerago.presentation.components.SecureWindow
import android.app.TimePickerDialog
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sclera.sclerago.domain.model.Address
import com.sclera.sclerago.domain.model.Availability
import com.sclera.sclerago.domain.model.Contact
import com.sclera.sclerago.domain.model.PROPERTY_COUNTRIES
import com.sclera.sclerago.domain.model.PROPERTY_REGIONS
import com.sclera.sclerago.domain.model.PROPERTY_TYPES
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.domain.usecase.property.propertyProblem
import com.sclera.sclerago.presentation.common.plural
import com.sclera.sclerago.presentation.components.AdminFab
import com.sclera.sclerago.presentation.components.BottomSheet
import com.sclera.sclerago.presentation.components.ChoiceSheet
import com.sclera.sclerago.presentation.components.CloseButton
import com.sclera.sclerago.presentation.components.ColorChip
import com.sclera.sclerago.presentation.components.FormGroup
import com.sclera.sclerago.presentation.components.FormSheet
import com.sclera.sclerago.presentation.components.InlineField
import com.sclera.sclerago.presentation.components.InlineInput
import com.sclera.sclerago.presentation.components.InlineSelect
import com.sclera.sclerago.presentation.components.Pill
import com.sclera.sclerago.presentation.components.PlainField
import com.sclera.sclerago.presentation.components.RoundButton
import com.sclera.sclerago.presentation.components.SearchField
import com.sclera.sclerago.presentation.components.SubPageBar
import com.sclera.sclerago.presentation.components.TintChip
import com.sclera.sclerago.presentation.components.Toast
import com.sclera.sclerago.presentation.components.dashedBorder
import com.sclera.sclerago.presentation.components.r
import com.sclera.sclerago.presentation.components.topBorder
import com.sclera.sclerago.presentation.icons.Icons
import com.sclera.sclerago.presentation.icons.LineIcon
import com.sclera.sclerago.presentation.main.AppViewModel
import com.sclera.sclerago.presentation.theme.C
import com.sclera.sclerago.presentation.theme.Txt
import com.sclera.sclerago.services.media.formatSize
import java.io.OutputStream

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
private const val MAX_IMPORT_BYTES = 100L * 1024 * 1024
private val AssetBlue = Color(0xFF6FA8E6)

/** Property list (side menu): counts, import, search, open a property, add one. */
@Composable
fun PropertyListScreen(vm: AppViewModel) {
    val s = vm.state
    BackHandler(onBack = vm::closeAdmin)
    val list = vm.propertyList
    Box(Modifier.fillMaxSize().background(C.Bg)) {
        Column(Modifier.fillMaxSize()) {
            SubPageBar("Property list", vm.propName, vm::openMenu)
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    Column(Modifier.padding(bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val total = vm.properties.sumOf { vm.assetCount(it.id) }
                        Txt(
                            plural(vm.properties.size, "property", "properties") + " · " + plural(total, "asset", "assets"),
                            12, 400, C.Muted, Modifier.padding(horizontal = 2.dp),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SearchField(s.propListQ, vm::setPropListQ, "Search properties", Modifier.weight(1f), height = 40.dp)
                            ImportButton(vm::openImport)
                        }
                    }
                }
                items(list, key = { it.id }) { p ->
                    PropertyCard(p, vm.assetCount(p.id), current = p.id == s.prop) { vm.openPropertyFromList(p.id) }
                }
                if (list.isEmpty()) item {
                    Txt("No property matches “${s.propListQ.trim()}”.", 14, 400, C.Muted, maxLines = 2)
                }
            }
        }
        AdminFab("Property", vm::openAddProperty, Modifier.align(Alignment.BottomEnd))
        androidx.compose.animation.AnimatedVisibility(
            s.toastOn && !s.addPropOpen,
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 72.dp),
            enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut(),
        ) { Toast(s.toast) }
        if (s.importOpen) ImportSheet(vm::closeImport, vm::importProperties, vm::writePropertyTemplate)
        if (s.addPropOpen) AddPropertySheet(vm.properties, vm::closeAddProperty, vm::addProperty)
    }
}

/** Square button beside the search: import properties from a spreadsheet. */
@Composable
private fun ImportButton(onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(r(12)).background(C.YTintBg).border(1.dp, C.YTintBd, r(12))
            .semantics { contentDescription = "Import properties from a spreadsheet" }.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { LineIcon(Icons.Table, 18.dp, C.Yellow, 1.9f) }
}

/** A property: round building icon, name, asset count; the current property is tinted yellow. */
@Composable
private fun PropertyCard(p: Property, assets: Int, current: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(r(12)).background(if (current) C.YTintBg else C.Surface)
            .border(1.dp, if (current) C.YTintBd else C.Border, r(12))
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(if (current) C.Yellow.copy(alpha = 0.18f) else C.Raised),
            contentAlignment = Alignment.Center,
        ) { LineIcon(Icons.Building, 20.dp, if (current) C.Yellow else C.Muted, 1.9f) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Txt(p.name, 15, 600)
            ColorChip(plural(assets, "asset", "assets"), AssetBlue, Icons.Box, small = true)
        }
        LineIcon(Icons.ChevronRight, 16.dp, C.Dim, 2.4f)
    }
}

// ---------------------------------------------------------------- Import

private data class PickedFile(val uri: Uri, val name: String, val size: Long)

@Composable
private fun ImportSheet(onClose: () -> Unit, onProceed: (String) -> Unit, writeTemplate: (OutputStream) -> Unit) {
    val context = LocalContext.current
    var file by remember { mutableStateOf<PickedFile?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val picked = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) PickedFile(uri, c.getString(0) ?: "file", if (c.isNull(1)) -1 else c.getLong(1)) else null
        } ?: PickedFile(uri, uri.lastPathSegment ?: "file", -1)
        error = when {
            !picked.name.endsWith(".xlsx", ignoreCase = true) -> "Choose an .xlsx file."
            picked.size > MAX_IMPORT_BYTES -> "That file is larger than 100MB."
            else -> null
        }
        file = if (error == null) picked else null
    }
    val sampleSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX_MIME)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.openOutputStream(uri)?.use(writeTemplate) }
            .onFailure { error = "Couldn’t save the sample file." }
    }

    BottomSheet(onClose, maxHeightFraction = 0.56f) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Txt("Import", 20, 700, modifier = Modifier.weight(1f))
                CloseButton(onClose, size = 36.dp, iconSize = 14.dp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 150.dp).clip(r(16)).background(C.Bg)
                        .dashedBorder(if (file != null) C.Yellow else C.Border2, 1.5.dp, 16.dp)
                        .clickable { chooser.launch(arrayOf(XLSX_MIME, "application/octet-stream")) }.padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(C.YTintBg), contentAlignment = Alignment.Center) {
                        LineIcon(Icons.Upload, 22.dp, C.Yellow, 2f)
                    }
                    val f = file
                    if (f == null) Txt("Tap to choose a file", 14, 600)
                    else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Txt(f.name, 14, 600, modifier = Modifier.padding(horizontal = 16.dp))
                        Txt((if (f.size >= 0) formatSize(f.size) + " · " else "") + "Tap to choose a different file", 12, 400, C.Muted)
                    }
                }
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Txt("Supported formats: .xlsx", 12, 400, C.Muted)
                    Txt("Maximum size: 100MB", 12, 400, C.Muted)
                }
            }
            error?.let { Txt(it, 13, 500, C.Red) }
            Row(
                Modifier.fillMaxWidth().clip(r(14)).background(C.CardWash).border(1.dp, C.Border, r(14)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(38.dp).clip(r(10)).background(C.Raised), contentAlignment = Alignment.Center) {
                    LineIcon(Icons.FileDoc, 16.dp, C.Yellow, 1.9f)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Txt("Sample Format", 14, 600)
                    Txt("Download the example file for reference", 12, 400, C.Muted, maxLines = 2)
                }
                RoundButton(40.dp, C.Btn, { sampleSaver.launch("property-import-sample.xlsx") }) {
                    LineIcon(Icons.Download, 14.dp, C.Text, 2.2f)
                }
            }
            Pill(
                "Proceed", { file?.let { onProceed(it.name) } }, Modifier.fillMaxWidth(),
                bg = if (file != null) C.Yellow else C.Raised, fg = if (file != null) C.OnYellow else C.Muted,
                height = 50.dp, size = 15, weight = 700,
            )
        }
    }
}

// ---------------------------------------------------------------- Add New Property

@Composable
private fun AddPropertySheet(existing: List<Property>, onClose: () -> Unit, onAdd: (Property) -> String?) {
    val context = LocalContext.current
    SecureWindow() // the access code is typed here
    var name by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("") }
    var line1 by rememberSaveable { mutableStateOf("") }
    var line2 by rememberSaveable { mutableStateOf("") }
    var country by rememberSaveable { mutableStateOf("") }
    var state by rememberSaveable { mutableStateOf("") }
    var region by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    var contactName by rememberSaveable { mutableStateOf("") }
    var contactEmail by rememberSaveable { mutableStateOf("") }
    var contactPhone by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var start by rememberSaveable { mutableStateOf("00:01") }
    var end by rememberSaveable { mutableStateOf("23:59") }
    var accessCode by rememberSaveable { mutableStateOf("") }
    var picker by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val done = (if (name.isNotBlank()) 1 else 0) + (if (type.isNotEmpty()) 1 else 0)
    val valid = propertyProblem(name, type, existing) == null
    val states = PROPERTY_COUNTRIES[country].orEmpty()
    val stateHint = when {
        country.isEmpty() -> "Choose a country first"
        states.isEmpty() -> "No states for this country"
        else -> "Select state"
    }

    fun pickTime(current: String, set: (String) -> Unit) {
        val (h, m) = current.split(':').map { it.toIntOrNull() ?: 0 }
        TimePickerDialog(context, { _, hh, mm -> set("%02d:%02d".format(hh, mm)) }, h, m, true).show()
    }

    Box(Modifier.fillMaxSize()) {
        FormSheet(
            "Add New Property", "Name and type are required.", "Add", valid, error, onClose,
            onPrimary = {
                error = onAdd(
                    Property(
                        "", name, gps = false, type = type,
                        address = Address(line1.trim(), line2.trim(), country, state, region, city.trim()),
                        contact = Contact(contactName.trim(), contactEmail.trim(), contactPhone.trim()),
                        notes = notes.trim(),
                        availability = Availability(start, end, accessCode.trim()),
                    ),
                )
            },
            leadingIcon = Icons.Building, progress = done / 2f,
            bodyBackground = C.Bg, bodyPadding = PaddingValues(vertical = 12.dp), bodySpacing = 12.dp,
        ) {
            FormGroup("Property details", Icons.Building, trailing = {
                val ok = done == 2
                TintChip(if (ok) "Ready" else "${2 - done} required", bg = (if (ok) C.Green else C.Yellow).copy(alpha = 0.15f), fg = if (ok) C.Green else C.Yellow)
            }) {
                InlineField("Name", required = true) { InlineInput(name, { name = it; error = null }, "Enter property name") }
                InlineField("Type", required = true) { InlineSelect(type, "Select type") { picker = "type" } }
            }
            FormGroup("Address", Icons.Pin, collapsible = true) {
                InlineField("Address line 1") { InlineInput(line1, { line1 = it }, "Enter address line 1") }
                InlineField("Address line 2") { InlineInput(line2, { line2 = it }, "Enter address line 2") }
                InlineField("Country") { InlineSelect(country, "Select country") { picker = "country" } }
                InlineField("State", dim = states.isEmpty()) {
                    InlineSelect(state, stateHint, enabled = states.isNotEmpty()) { picker = "state" }
                }
                InlineField("Region") { InlineSelect(region, "Select region") { picker = "region" } }
                InlineField("City", dim = country.isEmpty()) {
                    InlineInput(city, { city = it }, if (country.isEmpty()) "Choose a country first" else "Enter city", enabled = country.isNotEmpty())
                }
            }
            FormGroup("Primary contact", Icons.User, collapsible = true) {
                InlineField("Name") { InlineInput(contactName, { contactName = it }, "Enter name") }
                InlineField("Email") { InlineInput(contactEmail, { contactEmail = it }, "Enter valid email", keyboard = KeyboardType.Email) }
                InlineField("Phone") { InlineInput(contactPhone, { contactPhone = it }, "Enter valid phone number", keyboard = KeyboardType.Phone) }
            }
            FormGroup("Notes", Icons.FileDoc, trailing = { TintChip(if (notes.isBlank()) "None yet" else "Added", bg = C.Raised, fg = C.Muted) }) {
                Box(Modifier.fillMaxWidth().topBorder(C.Border).padding(horizontal = 12.dp)) {
                    PlainField(notes, { notes = it }, "Enter notes", Modifier.fillMaxWidth().heightIn(min = 84.dp).padding(vertical = 12.dp), size = 15, singleLine = false, minLines = 3)
                }
            }
            FormGroup("Availability", Icons.Clock, collapsible = true) {
                InlineField("Start time") { InlineSelect(start, "", chevron = false) { pickTime(start) { start = it } } }
                InlineField("End time") { InlineSelect(end, "", chevron = false) { pickTime(end) { end = it } } }
                InlineField("Access code") { InlineInput(accessCode, { accessCode = it }, "Enter access code") }
            }
        }
        when (picker) {
            "type" -> ChoiceSheet("Type", PROPERTY_TYPES.map { it to null }, type, { type = it; picker = null; error = null }, { picker = null })
            "country" -> ChoiceSheet("Country", PROPERTY_COUNTRIES.keys.map { it to null }, country, {
                if (it != country) state = ""
                country = it; picker = null
            }, { picker = null })
            "state" -> ChoiceSheet("State", states.map { it to null }, state, { state = it; picker = null }, { picker = null })
            "region" -> ChoiceSheet("Region", PROPERTY_REGIONS.map { it to null }, region, { region = it; picker = null }, { picker = null })
        }
    }
}

