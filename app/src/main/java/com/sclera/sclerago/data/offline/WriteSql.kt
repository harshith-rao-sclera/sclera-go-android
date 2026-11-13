package com.sclera.sclerago.data.offline

// Every statement the offline writers run, with `?` for each value. `%LIVE%` stands for the "not removed" condition
// of the table (`is_removed = 0` once the sync engine has added that column). The engine's change triggers record
// each insert and update, so a row is inserted without the sync columns (`changes`, `flag`, `sync_timestamp`,
// `is_removed`: their defaults make it a local change) and an update never touches `flag`.

// ---- assets

/** The columns of an asset the writers compare and edit. */
internal const val ASSET_STATE = """SELECT status, validated, needs_review, ai_suggestion, name, manufacturer, model, serial_number,
    master_format_id, jacs_code_id, classification_source, building_id, floor_id, location_id
    FROM assets WHERE id = ? AND %LIVE%"""

internal const val ASSET_INSERT = """INSERT INTO assets (id, name, building_id, floor_id, location_id, status, source, source_ref, created_by,
    version, created_at, updated_at, manufacturer, model, serial_number, needs_review, values_changed_at, classification_source,
    values_revision, master_format_id, jacs_code_id, validated, ai_suggestion)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?)"""

/** New values for an asset's answers: bumps the revision validation's "new or changed" compares. */
internal const val ASSET_UPDATE_VALUES = """UPDATE assets SET name = ?, manufacturer = ?, model = ?, serial_number = ?, master_format_id = ?,
    jacs_code_id = ?, classification_source = ?, values_revision = values_revision + 1, values_changed_at = ?,
    version = version + 1, updated_at = ? WHERE id = ?"""

internal const val ASSET_UPDATE_STATUS = """UPDATE assets SET status = ?, validated = ?, ai_suggestion = COALESCE(?, ai_suggestion), needs_review = ?,
    version = version + 1, updated_at = ? WHERE id = ?"""

/** The place of an asset is one of its answers, so it bumps the revision too. */
internal const val ASSET_UPDATE_PLACE = """UPDATE assets SET building_id = ?, floor_id = ?, location_id = ?, values_revision = values_revision + 1,
    values_changed_at = ?, version = version + 1, updated_at = ? WHERE id = ?"""

internal const val ASSET_VALUES_ROW = "SELECT * FROM asset_values WHERE asset_id = ?"

internal const val ASSET_VALUES_INSERT = """INSERT INTO asset_values (asset_id, confidence, sources, "values") VALUES (?, ?, ?, ?)"""

internal const val ASSET_VALUES_UPDATE = """UPDATE asset_values SET "values" = ?, confidence = ?, sources = ? WHERE asset_id = ?"""

internal const val ACTIVITY_INSERT = """INSERT INTO asset_activity (id, asset_id, kind, action, text, changes, created_by, version, created_at, updated_at)
    VALUES (?, ?, 'history', ?, ?, ?, ?, 1, ?, ?)"""

internal const val FIELD_NAMES = """SELECT id, key, name FROM asset_fields WHERE (asset_id IS NULL OR asset_id = '' OR asset_id = ?) AND %LIVE%"""

internal const val PLACE_NAME_BUILDING = "SELECT name FROM buildings WHERE id = ?"
internal const val PLACE_NAME_FLOOR = "SELECT name FROM floors WHERE id = ?"
internal const val PLACE_NAME_LOCATION = "SELECT name FROM locations WHERE id = ?"

// ---- asset media

internal const val MEDIA_NEXT_POSITION = "SELECT COALESCE(MAX(position), -1) + 1 AS p FROM asset_media WHERE asset_id = ? AND %LIVE%"

internal const val MEDIA_INSERT = """INSERT INTO asset_media (id, asset_id, kind, filename, content_type, size_bytes, object_path, width, height,
    duration_seconds, position, created_by, version, created_at, updated_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)"""

internal const val MEDIA_OF_ASSET = "SELECT id, object_path FROM asset_media WHERE asset_id = ? AND %LIVE%"

/** A file the sync uploads: its row is flagged `media_changes` by the engine's trigger. The column is the one the server's image configuration names. */
internal const val MULTIMEDIA_INSERT = """INSERT OR REPLACE INTO multimedia (id, multimedia_content, table_name, column_name, row_id, content_type, "order")
    VALUES (?, ?, 'asset_media', ?, ?, ?, ?)"""

internal const val MULTIMEDIA_DELETE = "DELETE FROM multimedia WHERE table_name = 'asset_media' AND row_id = ?"

internal const val IMAGE_CONFIG = "SELECT image_config FROM global_timestamp WHERE id = '1'"

// ---- places

internal const val BUILDING_NAME_TAKEN = "SELECT 1 FROM buildings WHERE lower(name) = lower(?) AND id <> ? AND %LIVE%"
internal const val FLOOR_NAME_TAKEN = "SELECT 1 FROM floors WHERE building_id = ? AND lower(name) = lower(?) AND id <> ? AND %LIVE%"
internal const val LOCATION_NAME_TAKEN = "SELECT 1 FROM locations WHERE floor_id = ? AND lower(name) = lower(?) AND id <> ? AND %LIVE%"

internal const val BUILDING_NEXT_POSITION = "SELECT COALESCE(MAX(position), -1) + 1 AS p FROM buildings WHERE %LIVE%"
internal const val FLOOR_NEXT_POSITION = "SELECT COALESCE(MAX(position), -1) + 1 AS p FROM floors WHERE building_id = ? AND %LIVE%"
internal const val LOCATION_NEXT_POSITION = "SELECT COALESCE(MAX(position), -1) + 1 AS p FROM locations WHERE floor_id = ? AND %LIVE%"

internal const val BUILDING_INSERT = """INSERT INTO buildings (id, name, code, is_active, position, version, created_at, updated_at)
    VALUES (?, ?, NULL, 1, ?, 1, ?, ?)"""

internal const val FLOOR_INSERT = """INSERT INTO floors (id, building_id, name, code, type, is_active, position, version, created_at, updated_at)
    VALUES (?, ?, ?, ?, ?, 1, ?, 1, ?, ?)"""

internal const val LOCATION_INSERT = """INSERT INTO locations (id, floor_id, name, code, type, is_active, position, version, created_at, updated_at)
    VALUES (?, ?, ?, ?, ?, 1, ?, 1, ?, ?)"""

internal const val BUILDING_UPDATE = "UPDATE buildings SET name = ?, version = version + 1, updated_at = ? WHERE id = ?"

internal const val FLOOR_UPDATE = """UPDATE floors SET name = ?, type = ?, code = ?, position = ?, version = version + 1, updated_at = ?
    WHERE id = ?"""

internal const val LOCATION_UPDATE = """UPDATE locations SET name = ?, type = ?, code = ?, version = version + 1, updated_at = ?
    WHERE id = ?"""

internal const val BUILDING_ROW = "SELECT id, name FROM buildings WHERE id = ? AND %LIVE%"
internal const val FLOOR_ROW = "SELECT id, building_id, name, code, type, position FROM floors WHERE id = ? AND %LIVE%"
internal const val LOCATION_ROW = "SELECT id, floor_id, name, code, type FROM locations WHERE id = ? AND %LIVE%"

internal const val FLOORS_OF_BUILDING = "SELECT id, name, code, type, position FROM floors WHERE building_id = ? AND %LIVE%"
internal const val BUILDING_BY_NAME = "SELECT id FROM buildings WHERE lower(name) = lower(?) AND %LIVE%"
internal const val FLOOR_BY_NAME = "SELECT id FROM floors WHERE building_id = ? AND lower(name) = lower(?) AND %LIVE%"
internal const val LOCATION_BY_NAME = "SELECT id FROM locations WHERE floor_id = ? AND lower(name) = lower(?) AND %LIVE%"

internal const val FLOOR_IN_USE = "SELECT 1 FROM floors WHERE building_id = ? AND %LIVE% LIMIT 1"
internal const val LOCATION_IN_USE = "SELECT 1 FROM locations WHERE floor_id = ? AND %LIVE% LIMIT 1"
internal const val ASSET_IN_BUILDING = "SELECT 1 FROM assets WHERE building_id = ? AND %LIVE% LIMIT 1"
internal const val ASSET_ON_FLOOR = "SELECT 1 FROM assets WHERE floor_id = ? AND %LIVE% LIMIT 1"
internal const val ASSET_AT_LOCATION = "SELECT 1 FROM assets WHERE location_id = ? AND %LIVE% LIMIT 1"

// ---- asset fields

/** The standard fields (those of no one asset) with what the diff compares. */
internal const val STANDARD_FIELD_ROWS = """SELECT id, key, name, type, options, is_required, is_active, is_builtin, position, help_text, default_value, checks
    FROM asset_fields WHERE (asset_id IS NULL OR asset_id = '') AND %LIVE%"""

/** A field every asset has: standard (`is_standard`) but not built in, so it can be deleted again. */
internal const val FIELD_INSERT = """INSERT INTO asset_fields (id, key, name, type, options, is_required, is_active, position, help_text, default_value,
    checks, aliases, is_standard, is_builtin, version, created_at, updated_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '[]', 1, 0, 1, ?, ?)"""

internal const val FIELD_UPDATE = """UPDATE asset_fields SET name = ?, type = ?, options = ?, is_required = ?, is_active = ?, position = ?, help_text = ?,
    default_value = ?, checks = ?, version = version + 1, updated_at = ? WHERE id = ?"""

internal const val FIELD_KEY_TAKEN = "SELECT 1 FROM asset_fields WHERE key = ? AND %LIVE%"
internal const val FIELD_NAME_TAKEN = "SELECT 1 FROM asset_fields WHERE (asset_id IS NULL OR asset_id = '') AND lower(name) = lower(?) AND id <> ? AND %LIVE%"

// ---- validations

internal const val VALIDATION_NAME_TAKEN = "SELECT 1 FROM validations WHERE lower(name) = lower(?) AND id <> ? AND %LIVE%"

internal const val VALIDATION_INSERT = """INSERT INTO validations (id, name, description, rules, template_key, created_by, version, created_at, updated_at, settings)
    VALUES (?, ?, ?, ?, NULL, ?, 1, ?, ?, '{}')"""

internal const val VALIDATION_UPDATE = """UPDATE validations SET name = ?, description = ?, rules = ?, version = version + 1, updated_at = ?
    WHERE id = ?"""

internal const val VALIDATION_EXISTS = "SELECT 1 FROM validations WHERE id = ? AND %LIVE%"

/** A deleted validation's jobs stay, no longer pointing at it (the foreign key's SET NULL rule). */
internal const val JOBS_UNLINK = "UPDATE validation_jobs SET validation_id = NULL WHERE validation_id = ?"
