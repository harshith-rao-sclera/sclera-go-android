package com.sclera.sclerago.data.local

import com.sclera.sclerago.domain.model.ADMINS_ID
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FieldKind
import com.sclera.sclerago.domain.model.FieldSection
import com.sclera.sclerago.domain.model.Group
import com.sclera.sclerago.domain.model.Permission
import com.sclera.sclerago.domain.model.PhotoReq
import com.sclera.sclerago.domain.model.ValueRule

// The defaults a new organisation starts with: asset fields, photo rules and access groups.

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
