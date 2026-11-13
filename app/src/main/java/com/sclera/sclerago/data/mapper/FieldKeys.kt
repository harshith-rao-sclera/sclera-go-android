package com.sclera.sclerago.data.mapper

/** The app's field keys against the database's (`asset_fields.key`, `asset_values.values`). Other keys are the same on both sides. */
internal object FieldKeys {
    private val APP_TO_DB = mapOf(
        "make" to "manufacturer",
        "serial" to "serial_number",
        "power" to "rated_power_kw",
        "label" to "asset_tag",
        "install" to "install_date",
        "warranty" to "warranty_expiry",
        "lastService" to "last_service_date",
        "nextService" to "next_service_due",
        "note" to "notes",
    )
    private val DB_TO_APP = APP_TO_DB.entries.associate { (app, db) -> db to app }

    fun toApp(dbKey: String): String = DB_TO_APP[dbKey] ?: dbKey

    fun toDb(appKey: String): String = APP_TO_DB[appKey] ?: appKey
}
