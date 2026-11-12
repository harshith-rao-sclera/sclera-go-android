package com.sclera.sclerago.data.local

import com.sclera.sclerago.domain.model.ADMINS_ID
import com.sclera.sclerago.domain.model.Address
import com.sclera.sclerago.domain.model.Asset
import com.sclera.sclerago.domain.model.AssetDetails
import com.sclera.sclerago.domain.model.AssetType
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FieldKind
import com.sclera.sclerago.domain.model.FieldSection
import com.sclera.sclerago.domain.model.Group
import com.sclera.sclerago.domain.model.LocNode
import com.sclera.sclerago.domain.model.NodeKind
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.PhotoReq
import com.sclera.sclerago.domain.model.Property
import com.sclera.sclerago.domain.model.Source
import com.sclera.sclerago.domain.model.Status
import com.sclera.sclerago.domain.model.ValueRule

// Mock data the in-memory repositories start from, until these screens are wired to the Sclera API.

val SEED_ASSETS = listOf(
    Asset(1, "Motor · ABB M3BP 160MLB 4", "ABB M3BP 160MLB 4 · 15 kW · 1470 RPM", "location1", "location1 · floor 1", Source.CAMERA, Status.VALIDATED, AssetType.MOTOR),
    Asset(2, "Pump · KSB Etanorm 065-040-160", "KSB Etanorm · 7.5 kW · 2900 RPM", "location1", "location1 · floor 1", Source.CAMERA, Status.INPUT, AssetType.PUMP),
    Asset(11, "Meeting room speaker", "Jabra Speak2 75 · Bluetooth 5.2 · USB-C", "location2", "location2 · floor 1", Source.BT, Status.AI, AssetType.SPEAKER),
    Asset(3, "Chiller 2", "Carrier 30XA-502 · 497 kW cooling · R-134a", "location2", "location2 · floor 1", Source.BT, Status.VALIDATED, AssetType.CHILLER),
    Asset(4, "AHU-03", "Daikin D-AHU Professional · 12,000 m³/h", null, "", Source.BT, Status.SUBMITTED, AssetType.FAN),
    Asset(12, "Reception PC", "Dell OptiPlex 7010 SFF · Windows 11 Pro", "location1", "location1 · floor 1", Source.WIFI, Status.AI, AssetType.COMPUTER),
    Asset(13, "Desk phone · Reception", "Cisco IP Phone 7945G · SIP · PoE", null, "", Source.WIFI, Status.INPUT, AssetType.PHONE),
    Asset(5, "BACnet controller", "Siemens Desigo PXC5.E003 · BACnet/IP · 24 V", "location1", "location1 · floor 1", Source.WIFI, Status.VALIDATED, AssetType.CONTROLLER),
    Asset(6, "IP camera · Gate 2", "Axis P1465-LE · 1080p bullet · PoE", null, "", Source.WIFI, Status.EXCEPTION, AssetType.CCTV),
    Asset(7, "Distribution board DB-4", "Schneider Acti9 · 125 A TP&N · 24 way", "location2", "location2 · floor 1", Source.FILE, Status.DRAFT, AssetType.PANEL),
    Asset(8, "Fire pump FP-1", "Grundfos NKF 80-250 · 45 kW · 2950 RPM", "location1", "location1 · floor 1", Source.FILE, Status.SUBMITTED, AssetType.PUMP),
    Asset(9, "Exhaust fan EF-2", "Systemair DVS 450E4 · 0.75 kW · 1380 RPM", "location1", "location1 · floor 1", Source.CAMERA, Status.DRAFT, AssetType.FAN),
    Asset(10, "Access point · Floor 1 East", "Ruckus R650 · Wi-Fi 6 · PoE+", "location1", "location1 · floor 1", Source.AP, Status.VALIDATED, AssetType.AP),
).map { it.copy(media = SEED_PHOTOS[it.id].orEmpty()) }

val PROPERTIES = listOf(
    Property("p1", "Test 861", true),
    Property("p2", "Dhanush 16/10 2.1.0", false),
    Property("p3", "Post production 12/10", false),
    Property("p4", "Sample Property 5", false),
    Property("p5", "Harbour Point Office Tower", false, "Office", Address("1 Harbour Point", country = "Singapore")),
    Property("p6", "Northgate Logistics Hub", false, "Warehouse", Address("Northgate Industrial Estate", country = "United Kingdom", state = "England")),
    Property("p7", "Riverside Medical Centre", false, "Hospital", Address("12 Riverside Road", country = "Australia", state = "Victoria")),
)

private fun loc(id: String, name: String, count: Int = 0, type: String = "", code: String = "") =
    LocNode(id, name, NodeKind.LOCATION, null, count, type, code)
private fun floor(id: String, name: String, vararg kids: LocNode) = LocNode(id, name, NodeKind.FLOOR, kids.toList())
private fun bld(id: String, name: String, vararg kids: LocNode) = LocNode(id, name, NodeKind.BUILDING, kids.toList())

fun locationTree(propId: String): List<LocNode> = when (propId) {
    "p1" -> listOf(
        bld("b1", "building 1",
            floor("f1", "floor 1", loc("l1", "location1", 7, "Office", "B1-F1-01"), loc("l2", "location2", 3, "Plant room", "B1-F1-02")),
            floor("f3", "floor 2", loc("l4", "Server room", type = "IT room", code = "B1-F2-01"), loc("l5", "Store room", type = "Storage", code = "B1-F2-02")),
        ),
        bld("b2", "Admin Building", floor("f4", "Ground floor", loc("l6", "Reception", type = "Lobby", code = "AB-G-01"), loc("l7", "Electrical room", type = "Plant room", code = "AB-G-02"))),
        bld("b3", "Office Tower", floor("f5", "Level 3", loc("l8", "AHU room", type = "Plant room", code = "OT-L3-01"))),
        bld("b4", "Pump House", floor("f2", "Ground floor", loc("l3", "Pump room", type = "Plant room", code = "PH-G-01"))),
        bld("b5", "Main Plant"),
    )
    else -> emptyList()
}

val DETAILS = mapOf(
    1 to AssetDetails("SCL-000418", mapOf("make" to "ABB", "model" to "M3BP 160MLB 4", "serial" to "3GBP161420-ADG-4524118", "power" to "15 kW", "voltage" to "415 V", "speed" to "1470 RPM", "sys" to "Mechanical", "typ" to "Motor", "sub" to "Induction", "jacs" to "[JACS code 1]", "label" to "SCL-000418", "note" to "IE3 · IP55 · insulation class F · 27.5 A at 415 V · frame 160L · B3 foot mount"), "speed"),
    2 to AssetDetails("SCL-000421", mapOf("make" to "KSB", "model" to "Etanorm 065-040-160", "serial" to "9971843210-0003", "power" to "7.5 kW", "voltage" to "415 V", "speed" to "2900 RPM", "sys" to "Mechanical", "typ" to "Pump", "sub" to "Centrifugal", "label" to "SCL-000421", "note" to "End-suction · 50 m³/h at 32 m head · cast iron casing · mechanical seal")),
    11 to AssetDetails("SCL-000455", mapOf("make" to "Jabra", "model" to "Speak2 75", "serial" to "2775-209-SN43218", "voltage" to "5 V", "sys" to "Audio-visual", "typ" to "Speakerphone", "sub" to "Bluetooth speakerphone", "jacs" to "[JACS code 2]", "label" to "SCL-000455", "ble" to "SCL-0447", "note" to "Bluetooth 5.2 · USB-C · Teams certified · rooms up to 8 people")),
    3 to AssetDetails("SCL-000402", mapOf("make" to "Carrier", "model" to "AquaForce 30XA-502", "serial" to "4219Q76102", "power" to "162 kW", "voltage" to "415 V", "sys" to "HVAC", "typ" to "Chiller", "sub" to "Air-cooled screw", "jacs" to "[JACS code 1]", "label" to "SCL-000402", "ble" to "SCL-0418", "note" to "Cooling 497 kW · R-134a · 2 screw compressors · EER 3.1 · chilled water 7/12 °C")),
    4 to AssetDetails("SCL-000422", mapOf("make" to "Daikin", "model" to "D-AHU Professional 080", "serial" to "DAHU-23-04417", "power" to "7.5 kW", "voltage" to "415 V", "speed" to "1450 RPM", "sys" to "HVAC", "typ" to "Fan / AHU", "sub" to "Plug fan", "jacs" to "[JACS code 1]", "label" to "SCL-000422", "ble" to "SCL-0422", "note" to "Supply 12,000 m³/h at 450 Pa · EC plug fan · G4 + F7 filters · plate heat recovery")),
    12 to AssetDetails("SCL-000464", mapOf("make" to "Dell", "model" to "OptiPlex 7010 SFF", "serial" to "7GQ4XK3", "power" to "0.18 kW", "voltage" to "230 V", "sys" to "IT & network", "typ" to "Computer", "sub" to "Desktop", "jacs" to "[JACS code 2]", "label" to "SCL-000464", "ip" to "192.168.10.64", "note" to "Intel Core i5-13500 · 16 GB RAM · 512 GB SSD · Windows 11 Pro")),
    13 to AssetDetails("SCL-000471", mapOf("make" to "Cisco", "model" to "CP-7945G", "serial" to "FCH1523A0KZ", "voltage" to "48 V", "sys" to "IT & network", "typ" to "Phone", "sub" to "VoIP desk phone", "label" to "SCL-000471", "ip" to "192.168.10.71", "note" to "SIP · PoE 802.3af class 2 · 2 lines · colour display · extension 2001")),
    5 to AssetDetails("SCL-000377", mapOf("make" to "Siemens", "model" to "Desigo PXC5.E003", "serial" to "S55372-C100-E1823", "voltage" to "24 V", "sys" to "HVAC", "typ" to "Controller", "sub" to "BACnet/IP controller", "jacs" to "[JACS code 2]", "label" to "SCL-000377", "ip" to "192.168.10.21", "note" to "BACnet/IP port 47808 · B-BC profile · DIN rail · 24 V AC/DC supply")),
    6 to AssetDetails("SCL-000457", mapOf("make" to "Axis", "model" to "P1465-LE", "serial" to "ACCC8E9F21A4", "voltage" to "48 V", "sys" to "Security", "typ" to "CCTV camera", "sub" to "Outdoor bullet", "jacs" to "[JACS code 2]", "label" to "SCL-000457", "ip" to "192.168.10.57", "note" to "1080p · IP66 / IK10 · IR 40 m · PoE class 3 · RTSP · not responding since last scan")),
    7 to AssetDetails("SCL-000484", mapOf("make" to "Schneider Electric", "model" to "Acti9 Isobar P TPN 24-way", "serial" to "DB4-2311-0784", "voltage" to "415 V", "sys" to "Electrical", "typ" to "Distribution board", "sub" to "TP&N final distribution", "label" to "SCL-000484", "note" to "125 A incomer · 24 ways per phase · Icu 10 kA · IP43 · fed from MSB-1")),
    8 to AssetDetails("SCL-000430", mapOf("make" to "Grundfos", "model" to "NKF 80-250/260", "serial" to "98371240-P12115", "power" to "45 kW", "voltage" to "415 V", "speed" to "2950 RPM", "sys" to "Fire safety", "typ" to "Pump", "sub" to "Centrifugal", "jacs" to "[JACS code 1]", "label" to "SCL-000430", "note" to "EN 12845 · 150 m³/h at 70 m head · electric duty pump · jockey and diesel standby")),
    9 to AssetDetails("SCL-000439", mapOf("make" to "Systemair", "model" to "DVS 450E4", "serial" to "41842-2209", "power" to "0.75 kW", "voltage" to "415 V", "speed" to "1380 RPM", "sys" to "HVAC", "typ" to "Fan / AHU", "sub" to "Centrifugal", "jacs" to "[JACS code 1]", "label" to "SCL-000439", "note" to "Roof exhaust · 5,400 m³/h at 200 Pa · toilet and kitchen extract · speed controlled")),
    10 to AssetDetails("SCL-000410", mapOf("make" to "Ruckus", "model" to "R650", "serial" to "432104001877", "voltage" to "48 V", "sys" to "IT & network", "typ" to "Access point", "sub" to "Wi-Fi 6 indoor AP", "jacs" to "[JACS code 2]", "label" to "SCL-000410", "ip" to "192.168.10.5", "note" to "SSID SCL-PLANT · MAC A4:2B:B0:1C:3F:10 · 2.4 / 5 GHz 4x4 · PoE 802.3at · ceiling mount")),
)

val DEFAULT_FIELDS = listOf(
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

val DEFAULT_PHOTO_REQS = listOf(
    PhotoReq("plate", "Nameplate", "Manufacturer's plate with model and serial number", 1, 2),
    PhotoReq("asset", "Asset", "The equipment itself, in place", 1, 3),
    PhotoReq("label", "Label / tag", "The asset tag or QR label", 0, 1),
    PhotoReq("site", "Surroundings", "Where the asset sits, to find it again", 0, 2),
)

val DEFAULT_GROUPS = listOf(
    Group(ADMINS_ID, "Admins", "Can do everything.", Permission.entries.toSet(), locked = true),
    Group("g-collectors", "Collectors", "Collect assets.", setOf(Permission.COLLECT)),
    Group("g-reviewers", "Reviewers", "Collect assets and validate them.", setOf(Permission.COLLECT, Permission.VALIDATE)),
    Group("g-viewers", "Viewers", "Look, but not change anything.", emptySet()),
)
