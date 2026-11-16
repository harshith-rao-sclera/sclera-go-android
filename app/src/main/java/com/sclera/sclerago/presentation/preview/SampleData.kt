package com.sclera.sclerago.presentation.preview

import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.AssetDetails
import com.sclera.sclerago.domain.model.AssetType
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FieldKind
import com.sclera.sclerago.domain.model.FieldSection
import com.sclera.sclerago.domain.model.Source
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.model.ValueRule

// A few made-up assets, details and fields for the previews and UI tests. The app itself reads its data from the offline database.

/** The sample assets (all in the sample property "p1"). */
internal val SAMPLE_ASSETS = listOf(
    Asset("a1", "Motor · ABB M3BP 160MLB 4", "ABB M3BP 160MLB 4 · 15 kW · 1470 RPM", "location1", "location1 · floor 1", Source.CAMERA, Status.VALIDATED, AssetType.MOTOR, "p1"),
    Asset("a2", "Pump · KSB Etanorm 065-040-160", "KSB Etanorm · 7.5 kW · 2900 RPM", "location1", "location1 · floor 1", Source.CAMERA, Status.INPUT, AssetType.PUMP, "p1"),
    Asset("a11", "Meeting room speaker", "Jabra Speak2 75 · Bluetooth 5.2 · USB-C", "location2", "location2 · floor 1", Source.BT, Status.AI, AssetType.SPEAKER, "p1"),
    Asset("a3", "Chiller 2", "Carrier 30XA-502 · 497 kW cooling · R-134a", "location2", "location2 · floor 1", Source.BT, Status.VALIDATED, AssetType.CHILLER, "p1"),
    Asset("a4", "AHU-03", "Daikin D-AHU Professional · 12,000 m³/h", null, "", Source.BT, Status.SUBMITTED, AssetType.FAN, "p1"),
    Asset("a12", "Reception PC", "Dell OptiPlex 7010 SFF · Windows 11 Pro", "location1", "location1 · floor 1", Source.WIFI, Status.AI, AssetType.COMPUTER, "p1"),
    Asset("a13", "Desk phone · Reception", "Cisco IP Phone 7945G · SIP · PoE", null, "", Source.WIFI, Status.INPUT, AssetType.PHONE, "p1"),
    Asset("a5", "BACnet controller", "Siemens Desigo PXC5.E003 · BACnet/IP · 24 V", "location1", "location1 · floor 1", Source.WIFI, Status.VALIDATED, AssetType.CONTROLLER, "p1"),
    Asset("a6", "IP camera · Gate 2", "Axis P1465-LE · 1080p bullet · PoE", null, "", Source.WIFI, Status.EXCEPTION, AssetType.CCTV, "p1"),
    Asset("a7", "Distribution board DB-4", "Schneider Acti9 · 125 A TP&N · 24 way", "location2", "location2 · floor 1", Source.FILE, Status.DRAFT, AssetType.PANEL, "p1"),
    Asset("a8", "Fire pump FP-1", "Grundfos NKF 80-250 · 45 kW · 2950 RPM", "location1", "location1 · floor 1", Source.FILE, Status.SUBMITTED, AssetType.PUMP, "p1"),
    Asset("a9", "Exhaust fan EF-2", "Systemair DVS 450E4 · 0.75 kW · 1380 RPM", "location1", "location1 · floor 1", Source.CAMERA, Status.DRAFT, AssetType.FAN, "p1"),
    Asset("a10", "Access point · Floor 1 East", "Ruckus R650 · Wi-Fi 6 · PoE+", "location1", "location1 · floor 1", Source.AP, Status.VALIDATED, AssetType.AP, "p1"),
)

/** The details of the sample assets a2 and a4 (the Pump and the AHU). */
internal val SAMPLE_DETAILS = mapOf(
    "a2" to AssetDetails("SCL-000421", mapOf("make" to "KSB", "model" to "Etanorm 065-040-160", "serial" to "9971843210-0003", "power" to "7.5 kW", "voltage" to "415 V", "speed" to "2900 RPM", "sys" to "Mechanical", "typ" to "Pump", "sub" to "Centrifugal", "label" to "SCL-000421", "note" to "End-suction · 50 m³/h at 32 m head · cast iron casing · mechanical seal"), lowConfidence = "speed"),
    "a4" to AssetDetails("SCL-000422", mapOf("make" to "Daikin", "model" to "D-AHU Professional 080", "serial" to "DAHU-23-04417", "power" to "7.5 kW", "voltage" to "415 V", "speed" to "1450 RPM", "sys" to "HVAC", "typ" to "Fan / AHU", "sub" to "Plug fan", "jacs" to "[JACS code 1]", "label" to "SCL-000422", "ble" to "SCL-0422", "note" to "Supply 12,000 m³/h at 450 Pa · EC plug fan · G4 + F7 filters · plate heat recovery")),
)

/** The sample asset fields. */
internal val SAMPLE_FIELDS = listOf(
    FieldCfg("name", "Name", FieldKind.TEXT, FieldSection.IDENT, required = true, locked = true),
    FieldCfg("make", "Manufacturer", FieldKind.TEXT, FieldSection.IDENT),
    FieldCfg("model", "Model", FieldKind.TEXT, FieldSection.IDENT),
    FieldCfg("serial", "Serial number", FieldKind.TEXT, FieldSection.IDENT, flagEmpty = true),
    FieldCfg("power", "Rated power (kW)", FieldKind.NUMBER, FieldSection.IDENT, rule = ValueRule.POSITIVE),
    FieldCfg("voltage", "Voltage (V)", FieldKind.NUMBER, FieldSection.IDENT),
    FieldCfg("speed", "Speed (RPM)", FieldKind.NUMBER, FieldSection.IDENT),
    FieldCfg("sys", "System type", FieldKind.DROPDOWN, FieldSection.CLASS, required = true),
    FieldCfg("typ", "Asset type", FieldKind.DROPDOWN, FieldSection.CLASS, required = true),
    FieldCfg("sub", "Sub type", FieldKind.DROPDOWN, FieldSection.CLASS),
    FieldCfg("jacs", "JACS code", FieldKind.DROPDOWN, FieldSection.CLASS, required = true),
    FieldCfg("label", "Asset tag / QR", FieldKind.TEXT, FieldSection.TAGS, rule = ValueRule.CODE),
    FieldCfg("ble", "BLE tag", FieldKind.TEXT, FieldSection.TAGS),
    FieldCfg("ip", "IP address", FieldKind.TEXT, FieldSection.TAGS),
    FieldCfg("condition", "Condition", FieldKind.DROPDOWN, FieldSection.MORE, visible = false, options = listOf("Very good", "Good", "Fair", "Poor", "Very poor")),
    FieldCfg("criticality", "Criticality", FieldKind.DROPDOWN, FieldSection.MORE, visible = false, options = listOf("Low", "Medium", "High", "Critical")),
    FieldCfg("install", "Install date", FieldKind.DATE, FieldSection.MORE, visible = false, rule = ValueRule.PAST),
    FieldCfg("warranty", "Warranty expiry", FieldKind.DATE, FieldSection.MORE, visible = false),
    FieldCfg("lastService", "Last service date", FieldKind.DATE, FieldSection.MORE, visible = false, rule = ValueRule.PAST),
    FieldCfg("nextService", "Next service date", FieldKind.DATE, FieldSection.MORE, visible = false),
    FieldCfg("note", "Notes", FieldKind.TEXTAREA, FieldSection.NOTES),
)
