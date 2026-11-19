package com.sclera.sclerago.data.offline

// Every statement the offline writers run, with `?` for each value. `%LIVE%` stands for the "not removed" condition
// of the table (`is_removed = 0` once the sync engine has added that column). The engine's change triggers record
// each insert and update, so a row is inserted without the sync columns (`changes`, `flag`, `sync_timestamp`,
// `is_removed`: their defaults make it a local change) and an update never touches `flag`.

// ---- assets

/** The columns of an asset the writers compare and edit. */
internal const val ASSET_STATE = """SELECT status, validated, ai_suggestion, name, manufacturer, model, serial_number,
    master_format_id, jacs_code_id, classification_source, building_id, floor_id, location_id
    FROM assets WHERE id = ? AND %LIVE%"""

internal const val ASSET_INSERT = """INSERT INTO assets (id, name, building_id, floor_id, location_id, status, source, source_ref, created_by,
    version, created_at, updated_at, manufacturer, model, serial_number, needs_review, values_changed_at, classification_source,
    values_revision, master_format_id, jacs_code_id, validated, ai_suggestion, assigned_to)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, 0, ?, ?, 1, ?, ?, ?, ?, ?)"""

/** Whether AI suggestions could fill an empty field, worked out again after the answers or fields change (the server's refresh_ai_suggestion). */
internal const val ASSET_SET_AI_SUGGESTION = "UPDATE assets SET ai_suggestion = ? WHERE id = ?"

/** Deleted in a property linked to a VDMS: VDMS has to confirm it (the server's vdms_state DELETING). */
internal const val ASSET_VDMS_DELETING = "UPDATE assets SET vdms_state = 5 WHERE id = ?"

/** Someone asks an admin to reopen a submitted asset (the server's POST …/change-request): asking again replaces it. */
internal const val ASSET_REQUEST_CHANGE = """UPDATE assets SET change_requested_by = ?, change_requested_at = ?, change_request_note = ?,
    version = version + 1, updated_at = ? WHERE id = ?"""

/** A change request closed without reopening the asset: declined or withdrawn (the server's DELETE …/change-request). */
internal const val ASSET_DROP_CHANGE_REQUEST = """UPDATE assets SET change_requested_by = NULL, change_requested_at = NULL,
    change_request_note = NULL, version = version + 1, updated_at = ? WHERE id = ? AND change_requested_at IS NOT NULL"""

/** Back in draft is what a change request asked for, so it is over (the server's close_change_request). */
internal const val ASSET_CLOSE_CHANGE_REQUEST = """UPDATE assets SET change_requested_by = NULL, change_requested_at = NULL,
    change_request_note = NULL WHERE id = ? AND change_requested_at IS NOT NULL"""

/** Who an asset is assigned to. */
internal const val ASSET_ASSIGNEE = "SELECT assigned_to FROM assets WHERE id = ?"

/** The asset a web page (manual, datasheet) is attached to. */
internal const val WEB_DOCUMENT_ASSET = "SELECT asset_id FROM web_documents WHERE id = ?"

/** Who asked for a change to an asset (null when nobody has). */
internal const val ASSET_CHANGE_REQUESTER = "SELECT change_requested_by FROM assets WHERE id = ?"

/** An asset goes to someone (an unassigned asset claimed by whoever changes it, or an approved change request's asker). */
internal const val ASSET_SET_ASSIGNEE = "UPDATE assets SET assigned_to = ?, version = version + 1, updated_at = ? WHERE id = ?"

/** New values for an asset's answers: bumps the revision validation's "new or changed" compares. */
internal const val ASSET_UPDATE_VALUES = """UPDATE assets SET name = ?, manufacturer = ?, model = ?, serial_number = ?, master_format_id = ?,
    jacs_code_id = ?, classification_source = ?, values_revision = values_revision + 1, values_changed_at = ?,
    version = version + 1, updated_at = ? WHERE id = ?"""

internal const val ASSET_UPDATE_STATUS = """UPDATE assets SET status = ?, validated = ?, ai_suggestion = COALESCE(?, ai_suggestion),
    version = version + 1, updated_at = ? WHERE id = ?"""

/** The place of an asset is one of its answers, so it bumps the revision too. */
internal const val ASSET_UPDATE_PLACE = """UPDATE assets SET building_id = ?, floor_id = ?, location_id = ?, values_revision = values_revision + 1,
    values_changed_at = ?, version = version + 1, updated_at = ? WHERE id = ?"""

internal const val ASSET_VALUES_ROW = "SELECT * FROM asset_values WHERE asset_id = ?"

internal const val ASSET_VALUES_INSERT = """INSERT INTO asset_values (asset_id, confidence, sources, %ANSWERS%) VALUES (?, ?, ?, ?)"""

internal const val ASSET_VALUES_UPDATE = """UPDATE asset_values SET %ANSWERS% = ?, confidence = ?, sources = ? WHERE asset_id = ?"""

/**
 * A history line. %CHANGES% is the column of its field changes: `field_changes` (the server renamed it, so `changes` is
 * the sync engine's again), or `changes` in a database from before that.
 */
internal const val ACTIVITY_INSERT = """INSERT INTO asset_activity (id, asset_id, kind, action, text, %CHANGES%, created_by, version, created_at, updated_at)
    VALUES (?, ?, 'history', ?, ?, ?, ?, 1, ?, ?)"""

/** A comment, as the server's `POST /assets/{id}/comments` writes it: no action or changes, just the text and who wrote it. */
internal const val COMMENT_INSERT = """INSERT INTO asset_activity (id, asset_id, kind, action, text, created_by, version, created_at, updated_at)
    VALUES (?, ?, 'comment', NULL, ?, ?, 1, ?, ?)"""

/** A comment's new text, as the server's `PATCH …/comments/{id}`: version + 1, which marks it edited. */
internal const val COMMENT_UPDATE = """UPDATE asset_activity SET text = ?, version = version + 1, updated_at = ?
    WHERE id = ? AND asset_id = ? AND kind = 'comment' AND %LIVE%"""

/** One live comment of an asset. */
internal const val COMMENT_ROW = "SELECT text FROM asset_activity WHERE id = ? AND asset_id = ? AND kind = 'comment' AND %LIVE%"

/** The fields an edit's history line lists changes of, in form order: standard ones, then the asset's own (the server's field_order). */
internal const val HISTORY_FIELDS = """SELECT id, key, name, type FROM asset_fields WHERE (is_standard = 1 OR asset_id = ?)
    AND %LIVE% ORDER BY CASE WHEN is_standard = 1 THEN 0 ELSE 1 END, position, key"""

/** A new place for an asset, written with the rest of an edit (which bumps the revision once). */
internal const val ASSET_SET_PLACE = "UPDATE assets SET building_id = ?, floor_id = ?, location_id = ? WHERE id = ?"

internal const val FIELD_NAMES = """SELECT id, key, name FROM asset_fields WHERE (is_standard = 1 OR asset_id = ?) AND %LIVE%"""

internal const val PLACE_NAME_BUILDING = "SELECT name FROM buildings WHERE id = ?"
internal const val PLACE_NAME_FLOOR = "SELECT name FROM floors WHERE id = ?"
internal const val PLACE_NAME_LOCATION = "SELECT name FROM locations WHERE id = ?"

// ---- asset media

internal const val MEDIA_NEXT_POSITION = "SELECT COALESCE(MAX(position), -1) + 1 AS p FROM asset_media WHERE asset_id = ? AND %LIVE%"

internal const val MEDIA_OF_ASSET = "SELECT id, object_path FROM asset_media WHERE asset_id = ? AND %LIVE%"

/** One of an asset's media, with the field it answers (if any). */
internal const val MEDIA_ROW = "SELECT * FROM asset_media WHERE id = ? AND asset_id = ? AND %LIVE%"

/** The key an asset's answers are kept under for field [id]. */
internal const val FIELD_KEY = "SELECT key FROM asset_fields WHERE id = ?"

/** An answer kept in `asset_values` changed: bumps the revision validation's "new or changed" compares (the server's `touch`). */
internal const val ASSET_TOUCH_VALUES = """UPDATE assets SET values_revision = values_revision + 1, values_changed_at = ?,
    version = version + 1, updated_at = ? WHERE id = ?"""

/** How many photos of each kind an asset has (what the property's photo rules count, as the server's `photo_counts`). */
internal const val PHOTO_COUNTS = """SELECT category, COUNT(*) AS n FROM asset_media
    WHERE asset_id = ? AND category IS NOT NULL AND %LIVE% GROUP BY category"""

/**
 * A file the sync uploads: its row is flagged `media_changes` by the engine's trigger. The table is the row's
 * (`asset_media` or `documents`) and the column is the one the server's image configuration names for it.
 */
internal const val MULTIMEDIA_INSERT = """INSERT OR REPLACE INTO multimedia (id, multimedia_content, table_name, column_name, row_id, content_type, "order")
    VALUES (?, ?, ?, ?, ?, ?, ?)"""

// ---- documents

/**
 * A file uploaded for an asset, as the server's `documents` keeps it: already read (`ready`), with no drafts made from it.
 * The file itself is found by the row's id.
 */
internal const val DOCUMENT_INSERT = """INSERT INTO documents (id, filename, content_type, size_bytes, kind, status, draft_count, created_by,
    version, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 'ready', 0, ?, 1, ?, ?)"""

/** The file an asset was read from (the server's `assets.source_document_id`). */
internal const val ASSET_SET_SOURCE_DOCUMENT = """UPDATE assets SET source_document_id = ?, version = version + 1, updated_at = ? WHERE id = ?"""

/** Drops a removed media's file (the media removed, or its asset deleted) but keeps its `multimedia` row (the sync engine's record of it). */
internal const val MULTIMEDIA_CLEAR = "UPDATE multimedia SET multimedia_content = NULL WHERE table_name = 'asset_media' AND row_id = ?"

/** Drops a removed uploaded file's content, keeping its `multimedia` row as [MULTIMEDIA_CLEAR] does. */
internal const val MULTIMEDIA_CLEAR_DOCUMENT = "UPDATE multimedia SET multimedia_content = NULL WHERE table_name = 'documents' AND row_id = ?"

/** The uploaded file [id], if it is still there. */
internal const val DOCUMENT_ROW = "SELECT id FROM documents WHERE id = ? AND %LIVE%"

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
    FROM asset_fields WHERE is_standard = 1 AND %LIVE%"""

/** A field every asset has: standard (`is_standard`) but not built in, so it can be deleted again. */
internal const val FIELD_INSERT = """INSERT INTO asset_fields (id, key, name, type, options, is_required, is_active, position, help_text, default_value,
    checks, aliases, is_standard, is_builtin, version, created_at, updated_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '[]', 1, 0, 1, ?, ?)"""

internal const val FIELD_UPDATE = """UPDATE asset_fields SET name = ?, options = ?, is_required = ?, is_active = ?, position = ?, help_text = ?,
    default_value = ?, checks = ?, version = version + 1, updated_at = ? WHERE id = ?"""

internal const val FIELD_KEY_TAKEN = "SELECT 1 FROM asset_fields WHERE key = ? AND %LIVE%"

/** One asset's own fields (a type's fields among them). */
internal const val OWN_FIELDS = "SELECT * FROM asset_fields WHERE asset_id = ? AND %LIVE%"

/**
 * The names of the standard fields, which a type's field may not take. A row with no asset is not enough: a type's
 * own field rows have no asset either (server migration t0014), so only `is_standard` says "standard".
 */
internal const val STANDARD_FIELD_NAMES = "SELECT name FROM asset_fields WHERE is_standard = 1 AND %LIVE%"

/** A field another type brought that the new type has too: it is the new type's field now (the server's `apply_type`). */
internal const val FIELD_RETYPE = "UPDATE asset_fields SET master_format_id = ?, version = version + 1, updated_at = ? WHERE id = ?"
internal const val FIELD_NAME_TAKEN = "SELECT 1 FROM asset_fields WHERE is_standard = 1 AND lower(name) = lower(?) AND id <> ? AND %LIVE%"

/** The answers (`asset_values` answers of live assets; `%ANSWERS%` is the column) that may hold a field's key, to be checked in code; `%LIVE%` is for `assets a`. */
internal const val FIELD_ANSWERS = """SELECT av.%ANSWERS% AS v FROM asset_values av JOIN assets a ON a.id = av.asset_id
    WHERE av.%ANSWERS% LIKE ? ESCAPE '\' AND %LIVE%"""
