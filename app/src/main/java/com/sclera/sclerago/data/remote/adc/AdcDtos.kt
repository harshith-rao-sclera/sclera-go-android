package com.sclera.sclerago.data.remote.adc

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// The API's JSON (camelCase). Unknown keys are ignored (see AdcJson); everything a screen does not use is left out.

@Serializable
data class PageDto<T>(val data: List<T> = emptyList(), val nextCursor: String? = null, val hasMore: Boolean = false)

@Serializable
data class PhotoRuleDto(val min: Int = 0, val max: Int = 5)

@Serializable
data class PhotoRulesDto(
    val nameplate: PhotoRuleDto = PhotoRuleDto(),
    val asset: PhotoRuleDto = PhotoRuleDto(),
    val labelId: PhotoRuleDto = PhotoRuleDto(),
)

@Serializable
data class SettingsDto(
    val bluetoothOnboarding: Boolean = false,
    val wifiOnboarding: Boolean = false,
    val photoRules: PhotoRulesDto = PhotoRulesDto(),
    /** AI in validation; on unless the property turned it off. */
    val aiValidation: Boolean = true,
    /** The units the property's type fields are in: [UNITS_US] (ft, psi, °F) or [UNITS_METRIC] (m, kPa, °C). */
    val unitSystem: String = UNITS_US,
)

/** [SettingsDto.unitSystem]'s two values. */
const val UNITS_US = "us"
const val UNITS_METRIC = "metric"

/** Someone the property's assets are assigned to (`GET /assets/assignees`), with how many they have. */
@Serializable
data class AssigneeDto(val userId: String, val name: String? = null, val email: String = "", val assets: Int = 0)

@Serializable
data class PropertyDto(
    val id: String,
    val name: String,
    val type: String? = null,
    val addressLine1: String? = null,
    val addressLine2: String? = null,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    val region: String? = null,
    val postalCode: String? = null,
    val contactName: String? = null,
    val contactEmail: String? = null,
    val contactPhone: String? = null,
    val notes: String? = null,
    val availableFrom: String? = null,
    val availableUntil: String? = null,
    val accessCode: String? = null,
    val status: String = "ready",
    val settings: SettingsDto = SettingsDto(),
)

@Serializable
data class GroupDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val permissions: List<String> = emptyList(),
    val isBuiltIn: Boolean = false,
    val userCount: Int = 0,
)

@Serializable
data class UserDto(
    val id: String,
    val email: String,
    val name: String? = null,
    val role: String? = null,
    val groupId: String? = null,
    val groupName: String? = null,
    /** False for someone invited who has not signed in yet. */
    val signedIn: Boolean = true,
    val allProperties: Boolean = false,
    val propertyIds: List<String> = emptyList(),
    val lastSeenAt: String? = null,
)

@Serializable
data class MemberDto(
    val userId: String,
    val email: String,
    val groupId: String? = null,
    val groupName: String? = null,
    val allProperties: Boolean = false,
)

@Serializable
data class DirectoryPersonDto(val email: String, val name: String? = null, val userId: String? = null, val groupId: String? = null)

@Serializable
data class DirectoryDto(
    val available: Boolean = false,
    val reason: String? = null,
    val hasMore: Boolean = false,
    val data: List<DirectoryPersonDto> = emptyList(),
)

/** A property detail a sheet's column can be imported into: its [key] and the [header] the sample uses. */
@Serializable
data class ImportTargetDto(val key: String = "", val header: String = "")

@Serializable
data class ImportTargetsDto(val data: List<ImportTargetDto> = emptyList())

/** One of the sheet's columns: its [heading], first few [samples], the detail it most likely holds and [how] that was decided. */
@Serializable
data class ImportColumnDto(
    val heading: String = "",
    val samples: List<JsonElement> = emptyList(),
    val target: String? = null,
    val how: String? = null,
)

/** What `…/properties/import/suggest` proposes for each column, and whether AI helped ([aiNote] says how it went). */
@Serializable
data class ImportSuggestionDto(val columns: List<ImportColumnDto> = emptyList(), val aiUsed: Boolean = false, val aiNote: String? = null)

@Serializable
data class ImportSkippedDto(val name: String = "")

/** What an import did: the properties [created], and those [skipped] because their name is already in use. */
@Serializable
data class ImportResultDto(val created: List<JsonElement> = emptyList(), val skipped: List<ImportSkippedDto> = emptyList())

/** A country or a state, as `GET /countries` and `GET /countries/{c}/states` list them. */
@Serializable
data class PlaceNameDto(val code: String? = null, val name: String = "")

/** A town, as `GET /countries/{c}/cities` lists them (largest first). */
@Serializable
data class CityDto(val name: String = "", val stateCode: String? = null, val state: String? = null)

/**
 * What `POST /properties/{p}/assets/bulk/assign` did: how many assets were [assigned], already theirs ([unchanged]) or
 * not on the server ([notFound]), to whom ([userId], [email], [name]; all null when they were taken off whoever had them),
 * and whether that person was [added] as a Collector on the property or [givenAccess] to it first.
 */
@Serializable
data class AssignResultDto(
    val assigned: Int = 0,
    val unchanged: Int = 0,
    val notFound: Int = 0,
    val userId: String? = null,
    val email: String? = null,
    val name: String? = null,
    val added: Boolean = false,
    val givenAccess: Boolean = false,
)

@Serializable
data class PermissionDto(val key: String, val name: String = "", val description: String = "", val needsAllProperties: Boolean = false)

@Serializable
data class MeDto(
    val userId: String,
    val email: String = "",
    val name: String? = null,
    /** The organisation every `/organisations/{org}` call must use (it may differ from what userInfo says). */
    val organisationId: String? = null,
    val role: String? = null,
    val groupId: String? = null,
    val groupName: String? = null,
    val permissions: List<String> = emptyList(),
    val allProperties: Boolean = false,
    val canManageProperties: Boolean = false,
    val isAdmin: Boolean = false,
)
