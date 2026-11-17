package com.sclera.sclerago.data.mapper

import com.sclera.sclerago.data.remote.adc.BuildingDto
import com.sclera.sclerago.data.remote.adc.CfgFieldDto
import com.sclera.sclerago.data.remote.adc.CfgOptionDto
import com.sclera.sclerago.data.remote.adc.FencesDto
import com.sclera.sclerago.data.remote.adc.FloorDto
import com.sclera.sclerago.data.remote.adc.LocationDto
import com.sclera.sclerago.data.remote.adc.GeofenceDto
import com.sclera.sclerago.data.remote.adc.PhotoRuleDto
import com.sclera.sclerago.data.remote.adc.PhotoRulesDto
import com.sclera.sclerago.data.remote.adc.SettingsDto
import com.sclera.sclerago.domain.model.Fence
import com.sclera.sclerago.domain.model.FieldCfg
import com.sclera.sclerago.domain.model.FieldSection
import com.sclera.sclerago.domain.model.LocNode
import com.sclera.sclerago.domain.model.NodeKind
import com.sclera.sclerago.domain.model.OPTION_KINDS
import com.sclera.sclerago.domain.model.PlaceChange
import com.sclera.sclerago.domain.model.configFieldKindOf
import com.sclera.sclerago.domain.model.DEFAULT_OPTION_COLOR
import com.sclera.sclerago.domain.model.optionColorOrNull
import com.sclera.sclerago.domain.model.serverType
import com.sclera.sclerago.domain.model.GeoPoint
import com.sclera.sclerago.domain.model.Geofence
import com.sclera.sclerago.domain.model.PhotoKind
import com.sclera.sclerago.domain.model.PhotoRule
import com.sclera.sclerago.domain.model.PropertySettings
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Property settings and geofence: the API's JSON (camelCase) ↔ the app's models.

// ---- Settings

fun PhotoRulesDto.toPhotoRules(): List<PhotoRule> = listOf(
    PhotoRule(PhotoKind.NAMEPLATE, nameplate.min, nameplate.max),
    PhotoRule(PhotoKind.ASSET, asset.min, asset.max),
    PhotoRule(PhotoKind.LABEL_ID, labelId.min, labelId.max),
)

fun List<PhotoRule>.toPhotoRulesDto(): PhotoRulesDto {
    fun of(kind: PhotoKind) = firstOrNull { it.kind == kind }?.let { PhotoRuleDto(it.min, it.max) } ?: PhotoRuleDto()
    return PhotoRulesDto(nameplate = of(PhotoKind.NAMEPLATE), asset = of(PhotoKind.ASSET), labelId = of(PhotoKind.LABEL_ID))
}

fun SettingsDto.toSettings() = PropertySettings(
    bluetoothOnboarding = bluetoothOnboarding,
    wifiOnboarding = wifiOnboarding,
    aiValidation = aiValidation,
    photoRules = photoRules.toPhotoRules(),
)

/** `PATCH /settings` body that sets every kind's photo rule: `{photoRules: {nameplate, asset, labelId: {min, max}}}`. */
fun photoRulesBody(rules: List<PhotoRule>): JsonObject {
    val dto = rules.toPhotoRulesDto()
    fun rule(r: PhotoRuleDto) = buildJsonObject { put("min", r.min); put("max", r.max) }
    return buildJsonObject {
        put("photoRules", buildJsonObject {
            put("nameplate", rule(dto.nameplate))
            put("asset", rule(dto.asset))
            put("labelId", rule(dto.labelId))
        })
    }
}

/** `PATCH /settings` body for AI in validation. */
fun aiValidationBody(on: Boolean): JsonObject = buildJsonObject { put("aiValidation", on) }

// ---- Geofence (GeoJSON positions are [longitude, latitude])

fun GeofenceDto.toGeofence() = Geofence(
    enforced = enforced,
    fences = fences?.coordinates.orEmpty().mapNotNull { polygon ->
        val rings = polygon.map { ring -> ring.openRing().mapNotNull { p -> if (p.size >= 2) GeoPoint(lat = p[1], lng = p[0]) else null } }
            .filter { it.isNotEmpty() }
        if (rings.isEmpty()) null else Fence(rings)
    },
)

/** A GeoJSON ring without its closing point (the app keeps rings open; the server closes them). */
private fun List<List<Double>>.openRing(): List<List<Double>> = if (size > 1 && first() == last()) dropLast(1) else this

/** `PUT /geofence` body: `{enforced, fences: MultiPolygon | null}`, every ring closed. */
fun geofenceBody(g: Geofence): JsonObject = buildJsonObject {
    put("enforced", g.enforced)
    if (g.fences.isEmpty()) {
        put("fences", JsonNull)
    } else {
        val polygons = g.fences.map { fence ->
            JsonArray(fence.rings.map { ring ->
                val closed = if (ring.first() == ring.last()) ring else ring + ring.first()
                JsonArray(closed.map { p -> JsonArray(listOf(JsonPrimitive(p.lng), JsonPrimitive(p.lat))) })
            })
        }
        put("fences", buildJsonObject {
            put("type", FencesDto().type)
            put("coordinates", JsonArray(polygons))
        })
    }
}

// ---- Asset fields (`/properties/{id}/fields`)

/** A standard field from the API as the app's [FieldCfg] (every kind, image and video included, as the web lists them), or null for an unknown type. */
fun CfgFieldDto.toFieldCfg(): FieldCfg? {
    val kind = configFieldKindOf(type) ?: return null
    val appKey = FieldKeys.toApp(key)
    return FieldCfg(
        key = appKey,
        label = name,
        kind = kind,
        section = SECTION_BY_KEY[appKey] ?: FieldSection.MORE,
        visible = isActive,
        required = isRequired,
        locked = appKey == "name",
        custom = !isBuiltIn,
        options = options.orEmpty().map { it.label },
        optionColors = options.orEmpty().mapNotNull { o -> optionColorOrNull(o.color)?.let { o.label to it } }.toMap(),
        defaultValue = defaultValue.orEmpty(),
        tooltip = helpText.orEmpty(),
        id = id,
    ).withChecks(readChecks(checks?.toString(), kind))
}

/**
 * [f]'s options as the API takes them, each with its colour: the one chosen for it, else the one it had ([old]), else
 * [DEFAULT_OPTION_COLOR] (as the web gives a new option).
 */
private fun optionsJson(f: FieldCfg, old: List<CfgOptionDto>?): JsonArray {
    val colours = old.orEmpty().associate { it.label to optionColorOrNull(it.color) }
    return JsonArray(f.options.map { label ->
        buildJsonObject {
            put("label", label)
            put("color", optionColorOrNull(f.optionColors[label]) ?: colours[label] ?: DEFAULT_OPTION_COLOR)
        }
    })
}

/** `POST /fields/bulk` item for a new field. */
fun fieldCreateJson(f: FieldCfg): JsonObject = buildJsonObject {
    put("name", f.label)
    put("type", f.kind.serverType)
    if (f.kind in OPTION_KINDS) put("options", optionsJson(f, null))
    put("isRequired", f.required)
    put("isActive", f.visible)
    f.tooltip.takeIf { it.isNotBlank() }?.let { put("helpText", it) }
    f.defaultValue.takeIf { it.isNotBlank() }?.let { put("defaultValue", it) }
    checksApiJson(f.kind, f.checks).takeIf { it.isNotEmpty() }?.let { put("checks", it) }
}

fun fieldsBulkJson(fields: List<FieldCfg>): JsonObject = buildJsonObject { put("fields", JsonArray(fields.map(::fieldCreateJson))) }

/** `PATCH /fields/{id}` with only what differs between [before] and [after]; [oldOptions] keep their colours. */
fun fieldPatchJson(before: FieldCfg, after: FieldCfg, oldOptions: List<CfgOptionDto>?): JsonObject = buildJsonObject {
    if (after.label != before.label) put("name", after.label)
    val optionsChanged = after.options != before.options || after.optionColors != before.optionColors
    if (after.kind in OPTION_KINDS && optionsChanged && after.options.isNotEmpty()) put("options", optionsJson(after, oldOptions))
    if (after.required != before.required) put("isRequired", after.required)
    if (after.visible != before.visible) put("isActive", after.visible)
    if (after.tooltip != before.tooltip) put("helpText", after.tooltip.takeIf { it.isNotBlank() }?.let { v -> JsonPrimitive(v) } ?: JsonNull)
    if (after.defaultValue != before.defaultValue) put("defaultValue", after.defaultValue.takeIf { it.isNotBlank() }?.let { v -> JsonPrimitive(v) } ?: JsonNull)
    if (after.checks != before.checks) put("checks", checksApiJson(after.kind, after.checks))
}

// ---- Buildings, floors, locations

/** The API's places as the building → floor → location tree, hidden ones too, siblings by position. */
fun placesTree(buildings: List<BuildingDto>, floors: List<FloorDto>, locations: List<LocationDto>): List<LocNode> {
    val locsByFloor = locations.sortedBy { it.position }.groupBy { it.floorId }
    val floorsByBuilding = floors.sortedBy { it.position }.groupBy { it.buildingId }
    return buildings.sortedBy { it.position }.map { b ->
        LocNode(
            id = b.id, name = b.name, kind = NodeKind.BUILDING, code = b.code.orEmpty(), active = b.isActive,
            kids = floorsByBuilding[b.id].orEmpty().map { f ->
                LocNode(
                    id = f.id, name = f.name, kind = NodeKind.FLOOR, type = f.type.orEmpty(), code = f.code.orEmpty(), active = f.isActive,
                    kids = locsByFloor[f.id].orEmpty().map { l ->
                        LocNode(id = l.id, name = l.name, kind = NodeKind.LOCATION, type = l.type.orEmpty(), code = l.code.orEmpty(), active = l.isActive)
                    },
                )
            },
        )
    }
}

/** A new building, floor or location: `{name, code?, type?, isActive: true}` (type only for floors and locations). */
fun placeCreateJson(name: String, code: String = "", type: String? = null): JsonObject = buildJsonObject {
    put("name", name)
    code.takeIf { it.isNotBlank() }?.let { put("code", it) }
    type?.takeIf { it.isNotBlank() }?.let { put("type", it) }
    put("isActive", true)
}

/** `PATCH` of a place: only what [c] sets; a blank code or type clears it. Buildings have no type. */
fun placePatchJson(kind: NodeKind, c: PlaceChange): JsonObject = buildJsonObject {
    c.name?.let { put("name", it) }
    c.code?.let { put("code", it.takeIf(String::isNotBlank)?.let { v -> JsonPrimitive(v) } ?: JsonNull) }
    if (kind != NodeKind.BUILDING) c.type?.let { put("type", it.takeIf(String::isNotBlank)?.let { v -> JsonPrimitive(v) } ?: JsonNull) }
    c.active?.let { put("isActive", it) }
    c.position?.let { put("position", it) }
}
