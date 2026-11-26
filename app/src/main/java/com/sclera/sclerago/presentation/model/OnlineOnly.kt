package com.sclera.sclerago.presentation.model

import androidx.annotation.StringRes
import com.sclera.sclerago.R

/**
 * What needs the server and so can't be done offline, each with the message that says so: "You're offline. [why it needs
 * the server] Try again when you're connected." A tap on an online-only button while offline shows it
 * ([com.sclera.sclerago.presentation.components.OfflineGate]), and an online-only page or section shows it in its banner
 * ([com.sclera.sclerago.presentation.components.OfflineBanner]).
 */
enum class OnlineOnly(@param:StringRes val message: Int) {
    // Asset page
    AI_SUGGESTIONS(R.string.offline_ai_suggestions),
    VOICE_FILL(R.string.offline_voice_fill),
    SUGGEST_TYPE(R.string.offline_suggest_type),
    REVALIDATE(R.string.offline_revalidate),
    DOCUMENTS(R.string.offline_documents),

    // Asset page and the asset list's selection bar
    VALIDATE(R.string.offline_validate),
    ASSIGN(R.string.offline_assign),
    MOVE_TO_MANAGED(R.string.offline_move_to_managed),

    // File tab
    FILES(R.string.offline_files),
    READ_FILE(R.string.offline_read_file),
    ADD_FILE_ASSETS(R.string.offline_add_file_assets),

    // Property list
    DOWNLOAD_PROPERTY(R.string.property_download_offline),
    NEW_PROPERTY(R.string.property_offline_add),
    IMPORT_PROPERTIES(R.string.property_offline_import),
    EDIT_PROPERTY(R.string.property_offline_edit),

    // Side menu pages
    USERS(R.string.offline_users),
    INTEGRATION(R.string.offline_integration),
    VALIDATION(R.string.offline_validation),

    SIGN_OUT(R.string.offline_sign_out),

    // Configuration tabs (Building info is read offline from the downloaded copy; adding and changing places is not)
    PLACES(R.string.offline_places),
    ASSET_FIELDS(R.string.offline_asset_fields),
    GEOFENCING(R.string.offline_geofencing),
    PROPERTY_SETTINGS(R.string.offline_property_settings),
}
