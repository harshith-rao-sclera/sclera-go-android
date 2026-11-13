package com.example.sclerago.services.syncManager.constants;

public class SyncConfigTables {
    public static final String timeStampTable = "global_timestamp";
    public static final String globalTimestampId = "id";
    public static final String globalTimestampSeq = "seq";
    public static final String globalTimestampTables = "tables";
    public static final String globalTimestampImageConfig = "image_config";
    public static final String globalTimestampMediaTimestamp = "media_timestamp";
    public static final String globalTimestampQRCodeLastSyncTimestamp = "qr_code_last_sync_timestamp";
    public static final String globalTimestampClientQRCodeLastSyncTimestamp = "client_qr_code_last_sync_timestamp";
    public static final String globalTimestampClientBarCodeLastSyncTimestamp = "client_bar_code_last_sync_timestamp";
    public static final String globalTimestampSyncStatus = "sync_status";
    public static final String multimediaTable = "multimedia";
    public static final String multimediaId = "id";
    public static final String multimediaContent = "multimedia_content";
    public static final String syncMetadataTable = "table_sync_metadata";
    public static final String columnTablename = "table_name";
    public static final String columnLastSynceddata = "last_synced_data";
    public static String[][] globalTimestampColumns = {
            {globalTimestampId, "TEXT PRIMARY KEY DEFAULT '1'"},
            {globalTimestampSeq, "INTEGER DEFAULT 0"},
            {globalTimestampMediaTimestamp, "INTEGER DEFAULT 0"},
            {globalTimestampQRCodeLastSyncTimestamp, "INTEGER DEFAULT 0"},
            {globalTimestampClientQRCodeLastSyncTimestamp, "INTEGER DEFAULT 0"},
            {globalTimestampClientBarCodeLastSyncTimestamp, "INTEGER DEFAULT 0"},
            {globalTimestampTables, "TEXT"},
            {globalTimestampImageConfig, "TEXT"},
            {globalTimestampSyncStatus, "INTEGER DEFAULT 1"}
    };

}
