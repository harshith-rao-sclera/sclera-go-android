package com.sclera.sclerago.services.syncManager;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.requery.android.database.sqlite.SQLiteDatabase;

public class GlobalConfigDatabaseHelper {
    public SQLiteDatabase globalDB;
    private static final String DATABASE_NAME = "global_config.db";

    public static final String TABLE_NAME_ASSET_CONFIG = "asset_table_config";
    public static final String ASSET_CONFIG_TABLE_COLUMN_USER = "user";
    public static final String ASSET_CONFIG_TABLE_COLUMN_ID = "user_id";
    public static final String ASSET_CONFIG_TABLE_COLUMN_TOKEN = "token";
    public static final String ASSET_CONFIG_TABLE_COLUMN_URL = "url";
    public static final String ASSET_CONFIG_TABLE_COLUMN_ORG_ID = "org_id";
    public static final String ASSET_CONFIG_TABLE_COLUMN_SESSION_ID = "session_id";
    public static final String ASSET_CONFIG_TABLE_COLUMN_SESSION_URL = "session_url";
    public static final String ASSET_CONFIG_TABLE_COLUMN_ROLE = "role";
    public static final String ASSET_CONFIG_TABLE_COLUMN_CLOUD_URL = "cloud_url";
    public static final String ASSET_CONFIG_TABLE_COLUMN_PROPERTY_ID = "property_id";

    public static final String TABLE_NAME_QR_CODE = "qr_code_table";
    public static final String QR_CODE_COLUMN_ID = "id";
    public static final String QR_CODE_COLUMN_IMAGE_URL = "image_url";
    public static final String QR_CODE_COLUMN_LOCATION_ID = "location_id";
    public static final String QR_CODE_COLUMN_VDMS_ID = "vdms_id";
    public static final String QR_CODE_COLUMN_DEVICE_ID = "device_id";
    public static final String QR_CODE_COLUMN_CREATED_BY = "created_by";
    public static final String QR_CODE_COLUMN_CREATION_TIME = "creation_time";
    public static final String QR_CODE_COLUMN_BATCH_ID = "batch_id";
    public static final String QR_CODE_COLUMN_QR_CODE_LINK = "qr_code_link";
    public static final String QR_CODE_COLUMN_UPDATED_TIME = "updated_time";
    public static final String QR_CODE_COLUMN_UPDATED_BY = "updated_by";
    public static final String QR_CODE_COLUMN_CHANGES = "changes";
    public static final String QR_CODE_COLUMN_DIRECTION = "direction";
    public static final String QR_CODE_COLUMN_MOVABLE = "movable";
    public static final String QR_CODE_COLUMN_IMAGE = "image";
    public static final String QR_CODE_COLUMN_ADC_CHECK = "adc_qr_code_check";


    public static final String TABLE_NAME_CLIENT_QR_CODE = "client_qr_code_table";
    public static final String CLIENT_QR_CODE_COLUMN_ID = "id";
    public static final String CLIENT_QR_CODE_COLUMN_ADDED_AT = "added_at";
    public static final String CLIENT_QR_CODE_COLUMN_ADDED_BY = "added_by";
    public static final String CLIENT_QR_CODE_COLUMN_CLIENT_QR_CODE_ID = "client_qr_code_id";
    public static final String CLIENT_QR_CODE_COLUMN_DEVICE_ID = "device_id";
    public static final String CLIENT_QR_CODE_COLUMN_LOCATION_ID = "location_id";
    public static final String CLIENT_QR_CODE_COLUMN_UPDATED_AT = "updated_at";
    public static final String CLIENT_QR_CODE_COLUMN_UPDATED_BY = "updated_by";
    public static final String CLIENT_QR_CODE_COLUMN_VDMS_ID = "vdms_id";
    public static final String CLIENT_QR_CODE_COLUMN_BATCH_ID = "batch_id";
    public static final String CLIENT_QR_CODE_COLUMN_CHANGES = "changes";
    public static final String CLIENT_QR_CODE_COLUMN_IMAGE = "image";
    public static final String CLIENT_QR_CODE_COLUMN_DIRECTION = "direction";
    public static final String CLIENT_QR_CODE_COLUMN_MOVABLE = "movable";
    public static final String CLIENT_QR_CODE_COLUMN_ADC_CHECK = "adc_client_qr_code_check";


    public static final String TABLE_NAME_NFC_RECORDS = "nfc_records";
    public static final String NFC_RECORDS_COLUMN_ID = "id";
    public static final String NFC_RECORDS_COLUMN_CREATED_BY = "created_by";
    public static final String NFC_RECORDS_COLUMN_CREATION_TIME = "creation_time";
    public static final String NFC_RECORDS_COLUMN_DEVICE_ID = "device_id";
    public static final String NFC_RECORDS_COLUMN_LOCATION_ID = "location_id";
    public static final String NFC_RECORDS_COLUMN_UID = "uid";
    public static final String NFC_RECORDS_COLUMN_VDMS_ID = "vdms_id";
    public static final String NFC_RECORDS_COLUMN_CHANGES = "changes";


    public static final String TABLE_NAME_CLIENT_NFC_RECORDS = "client_nfc_records";
    public static final String CLIENT_NFC_RECORDS_COLUMN_ID = "id";
    public static final String CLIENT_NFC_RECORDS_COLUMN_BATCH_ID = "batch_id";
    public static final String CLIENT_NFC_RECORDS_COLUMN_CREATED_BY = "created_by";
    public static final String CLIENT_NFC_RECORDS_COLUMN_CREATION_TIME = "creation_time";
    public static final String CLIENT_NFC_RECORDS_COLUMN_DEVICE_ID = "device_id";
    public static final String CLIENT_NFC_RECORDS_COLUMN_LOCATION_ID = "location_id";
    public static final String CLIENT_NFC_RECORDS_COLUMN_NFC_ID = "nfc_id";
    public static final String CLIENT_NFC_RECORDS_COLUMN_UID = "uid";
    public static final String CLIENT_NFC_RECORDS_COLUMN_VDMS_ID = "vdms_id";
    public static final String CLIENT_NFC_RECORDS_COLUMN_CHANGES = "changes";


    public static final String TABLE_NAME_CLIENT_BAR_CODE_RECORDS = "client_bar_code_records";
    public static final String CLIENT_BAR_CODE_RECORDS_COLUMN_ID = "id";
    public static final String CLIENT_BAR_CODE_RECORDS_COLUMN_CLIENT_BAR_CODE_ID = "client_bar_code_id";
    public static final String CLIENT_BAR_CODE_RECORDS_COLUMN_DEVICE_ID = "device_id";
    public static final String CLIENT_BAR_CODE_RECORDS_COLUMN_VDMS_ID = "vdms_id";
    public static final String CLIENT_BAR_CODE_RECORDS_COLUMN_ADDED_AT = "added_at";
    public static final String CLIENT_BAR_CODE_RECORDS_COLUMN_ADDED_BY = "added_by";
    public static final String CLIENT_BAR_CODE_RECORDS_COLUMN_BATCH_ID = "batch_id";
    public static final String CLIENT_BAR_CODE_RECORDS_COLUMN_UPDATED_TIME = "updated_time";
    public static final String CLIENT_BAR_CODE_RECORDS_COLUMN_UPDATED_BY = "updated_by";
    public static final String CLIENT_BAR_CODE_RECORDS_COLUMN_LOCATION_ID = "location_id";
    public static final String CLIENT_BAR_CODE_RECORDS_COLUMN_CHANGES = "changes";


    public static final String USER_DATA = "user_data";
    public static final String COLUMN_USER_ID = "id";
    public static final String COLUMN_USER_DATA = "info";


    public static final String USER_INFO_CACHE = "user_info_cache";
    public static final String COLUMN_USER_INFO_CACHE_ID = "id";
    public static final String COLUMN_USER_INFO_CACHE_DATA = "user_info";
    public static final String COLUMN_USER_INFO_CACHE_TOKEN = "token_info";

    /**
     * The organisation's properties, as GET /api/v1/organisations/{orgId}/properties returns them.
     * Rows from the API have is_synced = 1; properties added on the device have is_synced = 0 until
     * they reach the server. Access codes and contacts are stored encrypted (see PropertyStore).
     */
    public static final String TABLE_NAME_PROPERTIES = "properties";
    public static final String PROPERTIES_COLUMN_ID = "id";
    public static final String PROPERTIES_COLUMN_ORGANISATION_ID = "organisation_id";
    public static final String PROPERTIES_COLUMN_NAME = "name";
    public static final String PROPERTIES_COLUMN_TYPE = "type";
    public static final String PROPERTIES_COLUMN_ADDRESS_LINE1 = "address_line1";
    public static final String PROPERTIES_COLUMN_ADDRESS_LINE2 = "address_line2";
    public static final String PROPERTIES_COLUMN_CITY = "city";
    public static final String PROPERTIES_COLUMN_STATE = "state";
    public static final String PROPERTIES_COLUMN_COUNTRY = "country";
    public static final String PROPERTIES_COLUMN_REGION = "region";
    public static final String PROPERTIES_COLUMN_POSTAL_CODE = "postal_code";
    public static final String PROPERTIES_COLUMN_CONTACT_NAME = "contact_name";
    public static final String PROPERTIES_COLUMN_CONTACT_EMAIL = "contact_email";
    public static final String PROPERTIES_COLUMN_CONTACT_PHONE = "contact_phone";
    public static final String PROPERTIES_COLUMN_NOTES = "notes";
    public static final String PROPERTIES_COLUMN_AVAILABLE_FROM = "available_from";
    public static final String PROPERTIES_COLUMN_AVAILABLE_UNTIL = "available_until";
    public static final String PROPERTIES_COLUMN_ACCESS_CODE = "access_code";
    public static final String PROPERTIES_COLUMN_STATUS = "status";
    public static final String PROPERTIES_COLUMN_STATUS_DETAIL = "status_detail";
    public static final String PROPERTIES_COLUMN_SETTINGS = "settings";
    public static final String PROPERTIES_COLUMN_GEOFENCE_ENFORCED = "geofence_enforced";
    public static final String PROPERTIES_COLUMN_CREATED_AT = "created_at";
    public static final String PROPERTIES_COLUMN_UPDATED_AT = "updated_at";
    public static final String PROPERTIES_COLUMN_IS_SYNCED = "is_synced";

    private static volatile GlobalConfigDatabaseHelper instance;

    private static final String TABLE_CREATE_PROPERTIES =
            "CREATE TABLE IF NOT EXISTS " + TABLE_NAME_PROPERTIES + " (" +
                    PROPERTIES_COLUMN_ID + " TEXT PRIMARY KEY, " +
                    PROPERTIES_COLUMN_ORGANISATION_ID + " TEXT, " +
                    PROPERTIES_COLUMN_NAME + " TEXT NOT NULL DEFAULT '', " +
                    PROPERTIES_COLUMN_TYPE + " TEXT, " +
                    PROPERTIES_COLUMN_ADDRESS_LINE1 + " TEXT, " +
                    PROPERTIES_COLUMN_ADDRESS_LINE2 + " TEXT, " +
                    PROPERTIES_COLUMN_CITY + " TEXT, " +
                    PROPERTIES_COLUMN_STATE + " TEXT, " +
                    PROPERTIES_COLUMN_COUNTRY + " TEXT, " +
                    PROPERTIES_COLUMN_REGION + " TEXT, " +
                    PROPERTIES_COLUMN_POSTAL_CODE + " TEXT, " +
                    PROPERTIES_COLUMN_CONTACT_NAME + " TEXT, " +
                    PROPERTIES_COLUMN_CONTACT_EMAIL + " TEXT, " +
                    PROPERTIES_COLUMN_CONTACT_PHONE + " TEXT, " +
                    PROPERTIES_COLUMN_NOTES + " TEXT, " +
                    PROPERTIES_COLUMN_AVAILABLE_FROM + " TEXT, " +
                    PROPERTIES_COLUMN_AVAILABLE_UNTIL + " TEXT, " +
                    PROPERTIES_COLUMN_ACCESS_CODE + " TEXT, " +
                    PROPERTIES_COLUMN_STATUS + " TEXT, " +
                    PROPERTIES_COLUMN_STATUS_DETAIL + " TEXT, " +
                    PROPERTIES_COLUMN_SETTINGS + " TEXT, " +
                    PROPERTIES_COLUMN_GEOFENCE_ENFORCED + " INTEGER DEFAULT 0, " +
                    PROPERTIES_COLUMN_CREATED_AT + " TEXT, " +
                    PROPERTIES_COLUMN_UPDATED_AT + " TEXT, " +
                    PROPERTIES_COLUMN_IS_SYNCED + " INTEGER NOT NULL DEFAULT 0 CHECK (" + PROPERTIES_COLUMN_IS_SYNCED + " IN (0, 1)));";

    private static final String TABLE_USER_DATA =
            "CREATE TABLE IF NOT EXISTS " + USER_DATA + " (" +
                    COLUMN_USER_ID + " TEXT PRIMARY KEY DEFAULT 1, " +
                    COLUMN_USER_DATA + " TEXT);";

    private static final String TABLE_USER_INFO_CACHE =
            "CREATE TABLE IF NOT EXISTS " + USER_INFO_CACHE + " (" +
                    COLUMN_USER_INFO_CACHE_ID + " TEXT PRIMARY KEY DEFAULT 1, " +
                    COLUMN_USER_INFO_CACHE_TOKEN + " TEXT, " +
                    COLUMN_USER_INFO_CACHE_DATA + " TEXT);";

    private static final String TABLE_CREATE_CONFIG_ASSET_TABLE =
            "CREATE TABLE IF NOT EXISTS " + TABLE_NAME_ASSET_CONFIG + " (" +
                    ASSET_CONFIG_TABLE_COLUMN_ID + " TEXT PRIMARY KEY, " +
                    ASSET_CONFIG_TABLE_COLUMN_USER + " TEXT, " +
                    ASSET_CONFIG_TABLE_COLUMN_TOKEN + " TEXT, " +
                    ASSET_CONFIG_TABLE_COLUMN_SESSION_ID + " TEXT, " +
                    ASSET_CONFIG_TABLE_COLUMN_SESSION_URL + " TEXT, " +
                    ASSET_CONFIG_TABLE_COLUMN_CLOUD_URL + " TEXT, " +
                    ASSET_CONFIG_TABLE_COLUMN_ORG_ID + " TEXT, " +
                    ASSET_CONFIG_TABLE_COLUMN_ROLE + " TEXT, " +
                    ASSET_CONFIG_TABLE_COLUMN_PROPERTY_ID + " TEXT, " +
                    ASSET_CONFIG_TABLE_COLUMN_URL + " TEXT);";

    private static final String TABLE_CREATE_QR_CODE =
            "CREATE TABLE IF NOT EXISTS " + TABLE_NAME_QR_CODE + " (" +
                    QR_CODE_COLUMN_ID + " TEXT PRIMARY KEY, " +
                    QR_CODE_COLUMN_IMAGE_URL + " TEXT, " +
                    QR_CODE_COLUMN_LOCATION_ID + " TEXT, " +
                    QR_CODE_COLUMN_VDMS_ID + " TEXT, " +
                    QR_CODE_COLUMN_DEVICE_ID + " TEXT, " +
                    QR_CODE_COLUMN_CREATED_BY + " TEXT, " +
                    QR_CODE_COLUMN_CREATION_TIME + " BIGINT DEFAULT 0, " +
                    QR_CODE_COLUMN_BATCH_ID + " TEXT, " +
                    QR_CODE_COLUMN_QR_CODE_LINK + " TEXT, " +
                    QR_CODE_COLUMN_UPDATED_TIME + " TEXT, " +
                    QR_CODE_COLUMN_UPDATED_BY + " TEXT, " +
                    QR_CODE_COLUMN_CHANGES + " TEXT, " +
                    QR_CODE_COLUMN_IMAGE + " TEXT, " +
                    QR_CODE_COLUMN_DIRECTION + " TEXT, " +
                    QR_CODE_COLUMN_MOVABLE + " INTEGER DEFAULT 1, " +
                    QR_CODE_COLUMN_ADC_CHECK + " INTEGER DEFAULT 0);";


    private static final String TABLE_CREATE_CLIENT_QR_CODE =
            "CREATE TABLE IF NOT EXISTS " + TABLE_NAME_CLIENT_QR_CODE + " (" +
                    CLIENT_QR_CODE_COLUMN_ID + " TEXT PRIMARY KEY, " +
                    CLIENT_QR_CODE_COLUMN_ADDED_AT + " BIGINT, " +
                    CLIENT_QR_CODE_COLUMN_ADDED_BY + " TEXT, " +
                    CLIENT_QR_CODE_COLUMN_CLIENT_QR_CODE_ID + " TEXT, " +
                    CLIENT_QR_CODE_COLUMN_DEVICE_ID + " TEXT, " +
                    CLIENT_QR_CODE_COLUMN_LOCATION_ID + " TEXT, " +
                    CLIENT_QR_CODE_COLUMN_UPDATED_AT + " BIGINT, " +
                    CLIENT_QR_CODE_COLUMN_UPDATED_BY + " TEXT, " +
                    CLIENT_QR_CODE_COLUMN_VDMS_ID + " TEXT, " +
                    CLIENT_QR_CODE_COLUMN_BATCH_ID + " TEXT, " +
                    CLIENT_QR_CODE_COLUMN_CHANGES + " TEXT, " +
                    CLIENT_QR_CODE_COLUMN_IMAGE + " TEXT, " +
                    CLIENT_QR_CODE_COLUMN_DIRECTION + " TEXT, " +
                    CLIENT_QR_CODE_COLUMN_ADC_CHECK + " INTEGER DEFAULT 0, " +
                    CLIENT_QR_CODE_COLUMN_MOVABLE + " INTEGER DEFAULT 1);";

    private static final String TABLE_CREATE_NFC_RECORDS =
            "CREATE TABLE IF NOT EXISTS " + TABLE_NAME_NFC_RECORDS + " (" +
                    NFC_RECORDS_COLUMN_ID + " TEXT PRIMARY KEY, " +
                    NFC_RECORDS_COLUMN_CREATED_BY + " TEXT, " +
                    NFC_RECORDS_COLUMN_CREATION_TIME + " BIGINT DEFAULT 0, " +
                    NFC_RECORDS_COLUMN_DEVICE_ID + " TEXT, " +
                    NFC_RECORDS_COLUMN_LOCATION_ID + " TEXT, " +
                    NFC_RECORDS_COLUMN_UID + " TEXT, " +
                    NFC_RECORDS_COLUMN_VDMS_ID + " TEXT, " +
                    NFC_RECORDS_COLUMN_CHANGES + " TEXT);";

    private static final String TABLE_CREATE_CLIENT_NFC_RECORDS =
            "CREATE TABLE IF NOT EXISTS " + TABLE_NAME_CLIENT_NFC_RECORDS + " (" +
                    CLIENT_NFC_RECORDS_COLUMN_ID + " TEXT PRIMARY KEY, " +
                    CLIENT_NFC_RECORDS_COLUMN_BATCH_ID + " TEXT, " +
                    CLIENT_NFC_RECORDS_COLUMN_CREATED_BY + " TEXT, " +
                    CLIENT_NFC_RECORDS_COLUMN_CREATION_TIME + " BIGINT DEFAULT 0, " +
                    CLIENT_NFC_RECORDS_COLUMN_DEVICE_ID + " TEXT, " +
                    CLIENT_NFC_RECORDS_COLUMN_LOCATION_ID + " TEXT, " +
                    CLIENT_NFC_RECORDS_COLUMN_NFC_ID + " TEXT, " +
                    CLIENT_NFC_RECORDS_COLUMN_UID + " TEXT, " +
                    CLIENT_NFC_RECORDS_COLUMN_VDMS_ID + " TEXT, " +
                    CLIENT_NFC_RECORDS_COLUMN_CHANGES + " TEXT);";

    private static final String TABLE_CREATE_CLIENT_BAR_CODE_RECORDS =
            "CREATE TABLE IF NOT EXISTS " + TABLE_NAME_CLIENT_BAR_CODE_RECORDS + " (" +
                    CLIENT_BAR_CODE_RECORDS_COLUMN_ID + " TEXT PRIMARY KEY, " +
                    CLIENT_BAR_CODE_RECORDS_COLUMN_CLIENT_BAR_CODE_ID + " TEXT, " +
                    CLIENT_BAR_CODE_RECORDS_COLUMN_DEVICE_ID + " TEXT, " +
                    CLIENT_BAR_CODE_RECORDS_COLUMN_VDMS_ID + " TEXT, " +
                    CLIENT_BAR_CODE_RECORDS_COLUMN_ADDED_AT + " TEXT, " +
                    CLIENT_BAR_CODE_RECORDS_COLUMN_ADDED_BY + " TEXT, " +
                    CLIENT_BAR_CODE_RECORDS_COLUMN_BATCH_ID + " TEXT, " +
                    CLIENT_BAR_CODE_RECORDS_COLUMN_UPDATED_TIME + " BIGINT DEFAULT 0, " +
                    CLIENT_BAR_CODE_RECORDS_COLUMN_UPDATED_BY + " TEXT, " +
                    CLIENT_BAR_CODE_RECORDS_COLUMN_LOCATION_ID + " TEXT, " +
                    CLIENT_BAR_CODE_RECORDS_COLUMN_CHANGES  + " TEXT);";

    public GlobalConfigDatabaseHelper(@NonNull Context context) {
        String path = context.getDatabasePath(DATABASE_NAME).getPath();
        globalDB = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.CREATE_IF_NECESSARY);
        globalDB.execSQL("PRAGMA foreign_keys=ON;");
        globalDB.execSQL(TABLE_CREATE_QR_CODE);
        globalDB.execSQL(TABLE_CREATE_CLIENT_QR_CODE);
        globalDB.execSQL(TABLE_USER_DATA);
        globalDB.execSQL(TABLE_CREATE_NFC_RECORDS);
        globalDB.execSQL(TABLE_CREATE_CLIENT_NFC_RECORDS);
        globalDB.execSQL(TABLE_CREATE_CONFIG_ASSET_TABLE);
        globalDB.execSQL(TABLE_CREATE_CLIENT_BAR_CODE_RECORDS);
        globalDB.execSQL(TABLE_USER_INFO_CACHE);
        globalDB.execSQL(TABLE_CREATE_PROPERTIES);
        syncSchema();
    }

    private void syncSchema() {
        globalDB.beginTransaction();
        try {
            syncTableSchema(TABLE_NAME_ASSET_CONFIG, TABLE_CREATE_CONFIG_ASSET_TABLE);
            syncTableSchema(TABLE_NAME_QR_CODE, TABLE_CREATE_QR_CODE);
            syncTableSchema(TABLE_NAME_CLIENT_QR_CODE, TABLE_CREATE_CLIENT_QR_CODE);
            syncTableSchema(TABLE_NAME_NFC_RECORDS, TABLE_CREATE_NFC_RECORDS);
            syncTableSchema(TABLE_NAME_CLIENT_NFC_RECORDS, TABLE_CREATE_CLIENT_NFC_RECORDS);
            syncTableSchema(TABLE_NAME_CLIENT_BAR_CODE_RECORDS, TABLE_CREATE_CLIENT_BAR_CODE_RECORDS);
            syncTableSchema(USER_DATA, TABLE_USER_DATA);
            syncTableSchema(USER_INFO_CACHE, TABLE_USER_INFO_CACHE);
            syncTableSchema(TABLE_NAME_PROPERTIES, TABLE_CREATE_PROPERTIES);

            globalDB.setTransactionSuccessful();
        } finally {
            globalDB.endTransaction();
        }
    }

    public static GlobalConfigDatabaseHelper initializeInstance(Context context) {
        if (instance == null) {
            synchronized (GlobalConfigDatabaseHelper.class) {
                if (instance == null) {
                    instance = new GlobalConfigDatabaseHelper(context);
                }
            }
        }
        return instance;
    }

    public void dropTablesForAssetDataCollection(String oldUser) {
        globalDB.execSQL("DELETE FROM " + GlobalConfigDatabaseHelper.TABLE_NAME_ASSET_CONFIG);
        globalDB.execSQL("DELETE FROM " + GlobalConfigDatabaseHelper.TABLE_NAME_QR_CODE);
        globalDB.execSQL("DELETE FROM " + GlobalConfigDatabaseHelper.TABLE_NAME_CLIENT_QR_CODE);
        globalDB.execSQL("DELETE FROM " + GlobalConfigDatabaseHelper.TABLE_NAME_NFC_RECORDS);
        globalDB.execSQL("DELETE FROM " + GlobalConfigDatabaseHelper.TABLE_NAME_CLIENT_NFC_RECORDS);
        globalDB.execSQL("DELETE FROM " + GlobalConfigDatabaseHelper.TABLE_NAME_CLIENT_BAR_CODE_RECORDS);
    }

    private static class ColumnDef {
        String name;
        String definition;

        ColumnDef(String name, String definition) {
            this.name = name;
            this.definition = definition;
        }
    }

    private String quoteIdentifier(String name) {
        return "`" + name.replace("`", "``") + "`";
    }

    private void syncTableSchema(String tableName, String createTableSql) {
        Set<String> existingColumns = getExistingColumns(tableName);
        List<ColumnDef> expectedColumns = extractColumnsFromCreateSql(createTableSql);

        for (ColumnDef column : expectedColumns) {
            if (!existingColumns.contains(column.name)) {
                globalDB.execSQL(
                        "ALTER TABLE " + quoteIdentifier(tableName) +
                                " ADD COLUMN " + quoteIdentifier(column.name) + " " + column.definition
                );

                existingColumns.add(column.name);
            }
        }
    }

    private Set<String> getExistingColumns(String tableName) {
        Set<String> columns = new HashSet<>();

        try (Cursor cursor = globalDB.rawQuery("PRAGMA table_info(" + tableName + ")", null)) {
            while (cursor.moveToNext()) {
                String columnName = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                columns.add(columnName);
            }
        }

        return columns;
    }

    private List<ColumnDef> extractColumnsFromCreateSql(String createTableSql) {
        List<ColumnDef> columns = new ArrayList<>();

        int start = createTableSql.indexOf("(");
        int end = createTableSql.lastIndexOf(")");

        if (start == -1 || end == -1 || end <= start) {
            return columns;
        }

        String body = createTableSql.substring(start + 1, end);
        List<String> columnLines = splitSqlColumns(body);

        for (String line : columnLines) {
            line = line.trim();

            if (line.isEmpty()) {
                continue;
            }

            String upperLine = line.toUpperCase();

            // Skip table-level constraints
            if (upperLine.startsWith("PRIMARY KEY") ||
                    upperLine.startsWith("FOREIGN KEY") ||
                    upperLine.startsWith("UNIQUE") ||
                    upperLine.startsWith("CHECK") ||
                    upperLine.startsWith("CONSTRAINT")) {
                continue;
            }

            String[] parts = line.split("\\s+", 2);

            if (parts.length < 2) {
                continue;
            }

            String columnName = cleanColumnName(parts[0]);
            String columnDefinition = parts[1].trim();

            // ALTER TABLE cannot add a new PRIMARY KEY column.
            // Existing primary key columns should already be present from old table creation.
            if (columnDefinition.toUpperCase().contains("PRIMARY KEY")) {
                continue;
            }

            columns.add(new ColumnDef(columnName, columnDefinition));
        }

        return columns;
    }

    private List<String> splitSqlColumns(String body) {
        List<String> result = new ArrayList<>();

        StringBuilder current = new StringBuilder();
        int parenthesesDepth = 0;

        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);

            if (c == '(') {
                parenthesesDepth++;
            } else if (c == ')') {
                parenthesesDepth--;
            }

            if (c == ',' && parenthesesDepth == 0) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0) {
            result.add(current.toString());
        }

        return result;
    }

    private String cleanColumnName(String columnName) {
        return columnName
                .replace("`", "")
                .replace("\"", "")
                .replace("[", "")
                .replace("]", "")
                .trim();
    }
}

