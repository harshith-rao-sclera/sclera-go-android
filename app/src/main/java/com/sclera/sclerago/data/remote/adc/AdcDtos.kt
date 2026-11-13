package com.sclera.sclerago.data.remote.adc

import kotlinx.serialization.Serializable

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
)

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
data class DirectoryDto(val available: Boolean = false, val data: List<DirectoryPersonDto> = emptyList())

@Serializable
data class PermissionDto(val key: String, val name: String = "", val needsAllProperties: Boolean = false)

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
)
