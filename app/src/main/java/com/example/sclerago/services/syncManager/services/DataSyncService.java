package com.example.sclerago.services.syncManager.services;


import static com.example.sclerago.services.syncManager.constants.SyncConfigTables.globalTimestampId;
import static com.example.sclerago.services.syncManager.constants.SyncConfigTables.globalTimestampSeq;
import static com.example.sclerago.services.syncManager.constants.SyncConfigTables.timeStampTable;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.sclerago.services.syncManager.SyncManager;
import com.example.sclerago.services.syncManager.enums.OfflineFeatures;
import com.example.sclerago.services.syncManager.enums.SyncMode;
import com.example.sclerago.services.syncManager.utils.SyncUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import io.requery.android.database.sqlite.SQLiteDatabase;
import io.requery.android.database.sqlite.SQLiteStatement;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

@SuppressLint("Range")
public class DataSyncService {
    private SyncMode currentDataSyncMode;
    private SyncManager syncManager;
    private Thread dataSyncWSThread;
    private Thread dataSyncThread;
    ExecutorService processorExecutor;
    Future<?> currentProcessingTask = null;
    private final Map<String, JSONObject> dataPushPayload = new ConcurrentHashMap<>();
    final AtomicBoolean errorInPushingData = new AtomicBoolean(false);
    private static final long SYNC_PERIOD = 30000;
    private final String TAG = "Data Sync Service";
    private final SQLiteDatabase dbConnection;

    // Cloud-owned tables: pulled from the cloud but never pushed back from the device.
    private static final Set<String> NON_PUSHABLE_TABLES = new HashSet<>(Arrays.asList(
            "device", "location", "device_onboard_status"));

    public enum DataPushMode {
        WEBSOCKET, STREAMING
    }

    public Map<String, JSONObject> getDataPushPayload() {
        return dataPushPayload;
    }

    public void setCurrentDataSyncMode(SyncMode currentDataSyncMode) {
        this.currentDataSyncMode = currentDataSyncMode;
    }

    public Thread getDataSyncWSThread() {
        return dataSyncWSThread;
    }

    public Thread getDataSyncThread() {
        return dataSyncThread;
    }

    public void setProcessorExecutor(ExecutorService processorExecutor) {
        this.processorExecutor = processorExecutor;
    }

    public DataSyncService(SyncManager syncManager, SyncMode syncMode) {
        this.syncManager = syncManager;
        this.dbConnection = syncManager.getDbConnection();

        this.currentDataSyncMode = syncMode;
        if (syncMode.equals(SyncMode.POLLING)) {
            startDataSyncing();
        }
    }

    // Data syncing using websocket (push -> websocket, pull -> streaming)
    // Push payload > 8kb then push using streaming (websocket config in nginx issue)
    void startDataSyncingWS() {
        dataSyncWSThread = new Thread(() -> {
            while (syncManager.continueDataSync) {
                try {
                    if (syncManager.isWsConnected() && (currentProcessingTask == null || currentProcessingTask.isDone())) {
                        JSONArray localChanges = generatePayloadForPush(DataPushMode.WEBSOCKET);
                        Log.d(TAG, "Push payload: " + localChanges);
                        if (localChanges.length() > 0) {
                            pushChangesWS(localChanges);
                        } else {
                            if(syncManager.isDataSyncEnabled(syncManager.appContext,syncManager.getVdmsID())) {
                                pullChangesWS();
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error during polling", e);
                } finally {
                    try {
                        Thread.sleep(SYNC_PERIOD);
                    } catch (InterruptedException e) {
                        Log.d(TAG, "WS data-sync thread interrupted; exiting loop");
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
        dataSyncWSThread.start();
    }

    // Data syncing using streaming
    void startDataSyncing() {
        dataSyncThread = new Thread(() -> {
            while (syncManager.continueDataSync) {
                try {
                    JSONArray payload;
                    if (OfflineFeatures.ADMIN.equals(syncManager.getFeature())) {
                        payload = generatePayloadForPush(DataPushMode.STREAMING);
                    } else {
                        payload = generatePayloadForPush(); // push for ADC
                    }

                    if (payload.length() > 0) {
                        if (OfflineFeatures.ADMIN.equals(syncManager.getFeature())) {
                            pushChangesStreaming(payload);
                        } else {
                            pushChanges(payload); // push for ADC
                        }
                    } else {
                        if(syncManager.isDataSyncEnabled(syncManager.appContext,syncManager.getVdmsID())){
                            pullChanges();
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error during polling", e);
                } finally {
                    try {
                        Thread.sleep(SYNC_PERIOD);
                    } catch (InterruptedException e) {
                        Log.d(TAG, "Polling data-sync thread interrupted; exiting loop");
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
        dataSyncThread.start();
    }

    private void pushChangesWS(JSONArray localChanges) throws JSONException {
        errorInPushingData.set(false);
        for (int i = 0; i < localChanges.length(); i++) {
            if (errorInPushingData.get()) {
                break;
            }

            JSONObject pushJson = localChanges.getJSONObject(i);
            pushJson.put("actionType", "DataPush");
            syncManager.getWebSocket().send(pushJson.toString());
        }
    }

    private void pushChangesStreaming(JSONArray localChanges) {
        try {
            RequestBody streamingBody = getRequestBody(localChanges);

            Request request = new Request.Builder()
                    .url(syncManager.getSyncURL() + "/api/v1/upsert-data-stream")
                    .post(streamingBody)
                    .addHeader("Authorization", "Bearer " + SyncManager.token)
                    .addHeader("X-Tenant-ID", syncManager.getVdmsID())
                    .build();

            try (Response response = syncManager.getClient().newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Stream data failed: " + response);
                } else {
                    Log.i(TAG, "Stream data successful: " + response.code());
                    removeChangesStreaming(localChanges);

                    JSONArray payload = generatePayloadForPush(DataPushMode.STREAMING);
                    if (payload.length() > 0) {
                        pushChangesStreaming(payload);
                    } else {
                        pullChanges();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in pushing data changes by streaming: " + e);
        }
    }

    @NonNull
    private RequestBody getRequestBody(JSONArray localChanges) {

        // Create streaming request body
        return new RequestBody() {
            @Override
            public MediaType contentType() {
                return MediaType.parse("application/octet-stream");
            }

            @Override
            public long contentLength() {
                return -1;
            }

            @Override
            public void writeTo(@NonNull BufferedSink bufferedSink) {
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(bufferedSink.outputStream(), StandardCharsets.UTF_8)
                );

                try {
                    int size = localChanges.length();
                    for (int i = 0; i < size; i++) {
                        String jsonLine = localChanges.getJSONObject(i)
                                .toString()
                                .replace("\n", "")
                                .replace("\r", "")
                                .trim();
                        writer.write(jsonLine);
                        writer.newLine();
                    }
                    writer.flush();
                } catch (Exception e) {
                    Log.e(TAG, "Error in writing data to buffered sink", e);
                }
            }
        };
    }

    void pullChangesWS() {
        String pullPayload = getSyncMetadataAsJSONPayload();
        Log.d(TAG, "Pull payload: " + pullPayload);
        syncManager.getWebSocket().send(pullPayload);
    }

    public void pullChanges() {
        String syncTimestamp = getLastSyncTimestamp();
        String pullPayload = getSyncMetadataAsJSONPayload();
        RequestBody requestBody = RequestBody.create(pullPayload, MediaType.get("application/json; charset=utf-8"));

        // ADC still uses the old v1 endpoint while new inspection uses v2 endpoint for pull changes

        String version;
        String urlString;

        if (OfflineFeatures.ASSET_DATA_COLLECTION.equals(syncManager.getFeature())) {
            version = "v1";
        } else {
            version = "v2";
        }

        urlString = syncManager.getSyncURL() + "/api/" + version + "/getStreamChanges?sync_timestamp=" + syncTimestamp;

        if (syncManager.getVdmsID() != null && !syncManager.getVdmsID().trim().isEmpty()) {
            urlString += "&vdms_id=" + syncManager.getVdmsID();
        }

        Request request;
        if (OfflineFeatures.ASSET_DATA_COLLECTION.equals(syncManager.getFeature())) {
            request = SyncUtils.createGETRequest(urlString, syncManager.getFeature(), syncManager);
        } else {
            request = SyncUtils.createPOSTRequest(urlString, requestBody, syncManager.getFeature(), syncManager);
        }

        try (Response response = syncManager.getClient().newCall(request).execute()) {
            if (response.isSuccessful()) {
                String lastSyncTimestamp = response.header("sync_timestamp");
                System.out.println("Data sync timestamp from response: " + lastSyncTimestamp);
                if (response.body() != null) {
                    try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(response.body().byteStream()))) {
                        processChanges(bufferedReader, lastSyncTimestamp, SyncMode.POLLING);
                    } catch (Exception e) {
                        System.err.println("Exception: " + e);
                    }
                } else {
                    Log.e(TAG, "Response body is null");
                }
            } else {
                Log.e(TAG, "Unexpected response code: " + response.code());
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception during HTTP GET request: " + e);
        }
    }


    public JSONArray generatePayloadForPush(DataPushMode dataPushMode) throws JSONException {
        List<String> tablesToSync = getTablesToSync();

        JSONArray websocketPayload = new JSONArray();

        for (String tableName : tablesToSync) {
            // Cloud-owned tables are pulled only, never pushed back.
            if (NON_PUSHABLE_TABLES.contains(tableName)) {
                continue;
            }
            List<String> primaryKeyColumns = syncManager.getPrimaryKeyColumns(tableName);
            Cursor res = getChangedDocumentsForTable(tableName, primaryKeyColumns);
            if (res != null && res.moveToFirst()) {
                do {
                    JSONObject changesObj = new JSONObject();
                    changesObj.put("tablename", tableName);
                    if (dataPushMode.equals(DataPushMode.WEBSOCKET)) {
                        changesObj.put("primaryKey", new JSONArray(primaryKeyColumns));
                    }
                    StringBuilder mapKey = new StringBuilder(tableName);

                    // Iterate over all columns in the cursor
                    for (int i = 0; i < res.getColumnCount(); i++) {
                        String columnName = res.getColumnName(i);
                        String columnValue = res.getString(i);

                        if (Objects.equals(columnName, "changes")) {
                            // Parse and flatten the "changes" JSON column
                            JSONObject changesColumn = new JSONObject(columnValue);
                            Iterator<String> keys = changesColumn.keys();
                            while (keys.hasNext()) {
                                String key = keys.next();
                                changesObj.put(key, changesColumn.get(key));
                            }
                        } else {
                            changesObj.put(columnName, columnValue);
                        }

                        for (int j = 0; j < primaryKeyColumns.size(); j++) {
                            if (columnName.equals(primaryKeyColumns.get(j))) {
                                mapKey.append(" ").append(columnName).append(":").append(columnValue);
                            }
                        }
                    }

                    // greater than 8kb then perform streaming
                    if (dataPushMode.equals(DataPushMode.WEBSOCKET) && changesObj.toString().getBytes(StandardCharsets.UTF_8).length > 8 * 1024) {
                        dataPushPayload.clear();
                        JSONArray payload = generatePayloadForPush(DataPushMode.STREAMING);
                        pushChangesStreaming(payload);
                        return new JSONArray();
                    }

                    dataPushPayload.put(mapKey.toString(), changesObj);
                    websocketPayload.put(changesObj); // Add the object to the array
                } while (res.moveToNext());
                res.close();
            } else if (res != null) {
                res.close();
            }
        }

        return websocketPayload;
    }

    private void removeChangesStreaming(@NonNull JSONArray payload) throws JSONException {
        for (int i = 0; i < payload.length(); i++) {
            JSONObject tableObj = payload.getJSONObject(i);
            String tableName = (String) tableObj.remove("tablename");
            if (tableName == null) {
                Log.w(TAG, "Payload does not have tablename: " + tableObj);
                continue;
            }

            // Get the primary key columns
            List<String> primaryKeyColumns = syncManager.getPrimaryKeyColumns(tableName);
            StringBuilder whereClause = new StringBuilder();
            List<String> whereArgs = new ArrayList<>();

            // Build the WHERE clause and arguments
            for (String primaryKey : primaryKeyColumns) {
                if (tableObj.has(primaryKey)) {
                    if (whereClause.length() > 0) {
                        whereClause.append(" AND ");
                    }
                    whereClause.append(primaryKey).append(" = ?");
                    whereArgs.add(tableObj.getString(primaryKey));
                    tableObj.remove(primaryKey);
                }
            }

            try (Cursor cursor = dbConnection.query(
                    tableName,
                    new String[]{"changes"},
                    whereClause.toString(),
                    whereArgs.toArray(new String[0]),
                    null,
                    null,
                    null
            )) {
                if (cursor != null && cursor.moveToFirst()) {
                    String existingChanges = cursor.getString(cursor.getColumnIndex("changes"));
                    JSONObject existingChangesJSON = new JSONObject(existingChanges);

                    // Remove primary key columns from existing changes JSON
                    for (String primaryKey : primaryKeyColumns) {
                        if (existingChangesJSON.has(primaryKey)) {
                            existingChangesJSON.remove(primaryKey);
                        }
                    }

                    existingChanges = existingChangesJSON.toString();

                    // If the changes match, update the database
                    if (existingChanges.equals(tableObj.toString())) {
                        ContentValues contentValues = new ContentValues();
                        contentValues.putNull("changes");

                        dbConnection.update(tableName, contentValues, whereClause.toString(), whereArgs.toArray(new String[0]));
                    }
                }
            } catch (SQLiteException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    private String getSyncMetadataAsJSONPayload() {
        Cursor cursor = null;
        String jsonString = null;
        try {
            // Execute query
            cursor = dbConnection.rawQuery("SELECT table_name, last_synced_data FROM table_sync_metadata"
                    // Execute query
                    , null);

            // Prepare result map
            Map<String, Object> syncData = new HashMap<>();

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String tableName = cursor.getString(cursor.getColumnIndexOrThrow("table_name"));
                    long timestamp = cursor.isNull(cursor.getColumnIndexOrThrow("last_synced_data"))
                            ? 0 : cursor.getLong(cursor.getColumnIndexOrThrow("last_synced_data"));
                    syncData.put(tableName, timestamp);
                } while (cursor.moveToNext());

                JSONObject jsonObject = new JSONObject(syncData);
                jsonObject.put("actionType", "DataPull");
                jsonString = jsonObject.toString();
            }

        } catch (Exception e) {
            System.out.println("❌ Error in getSyncMetadataJson: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return jsonString;
    }

    @NonNull
    String getLastSyncTimestamp() {
        String syncTimestamp = "0";

        try {
            Cursor cursor = dbConnection.query(
                    timeStampTable,
                    new String[]{globalTimestampSeq},
                    globalTimestampId + " = ?",
                    new String[]{"1"},
                    null,
                    null,
                    null
            );

            if (cursor != null && cursor.moveToFirst()) {
                long syncTimestampLong = cursor.getLong(0);
                syncTimestamp = String.valueOf(syncTimestampLong);
                cursor.close();
            }

        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        return syncTimestamp;
    }

    @SuppressLint("CheckResult")
    public void processChanges(BufferedReader bufferedReader, String lastSyncTimestamp, SyncMode mode) {
        dbConnection.execSQL("PRAGMA foreign_keys=OFF;");
        try {
            String tablename = null;
            List<String> columns = new ArrayList<>();
            List<String> primaryKeyColumns = new ArrayList<>();
            String line;
            String syncTimestamp;

            List<String[]> records = new ArrayList<>();

            while ((line = bufferedReader.readLine()) != null) {
                line = line.trim();
                int startIdx = line.indexOf("|&|") + 3;
                int endIdx = line.indexOf("|&|", startIdx);

                if (endIdx == -1 || startIdx == 2) {
                    System.out.println("Skipping malformed line: " + line);
                    continue;
                }

                String trimmedLine = line.substring(startIdx, endIdx).trim();

                if (trimmedLine.equalsIgnoreCase("START")) {
                    System.out.println("STARTED");
                } else if (trimmedLine.startsWith("TABLENAME:")) {
                    tablename = trimmedLine.substring("TABLENAME:".length()).trim();
                    primaryKeyColumns = syncManager.getPrimaryKeyColumns(tablename);
                } else if (trimmedLine.startsWith("COLUMNS:")) {
                    columns = Arrays.asList(trimmedLine.substring("COLUMNS:".length()).trim().split("\\|\\$\\|"));
                } else if (trimmedLine.equalsIgnoreCase("NEXT")) {
                    if (!records.isEmpty()) {
                        long res = insertStreamingDataInBulk(records, columns, tablename);
                        if (res == -1) {
                            return;
                        }
                        records.clear();
                    }

                    tablename = null;
                    primaryKeyColumns = null;
                } else if (trimmedLine.startsWith("sync_timestamp") && mode.equals(SyncMode.WEBSOCKET)) {
                    syncTimestamp = trimmedLine.substring("sync_timestamp:".length()).trim();
                    ContentValues contentValues = new ContentValues();
                    contentValues.put(globalTimestampSeq, syncTimestamp);
                    dbConnection.update(timeStampTable, contentValues, globalTimestampId + " = ?", new String[]{"1"});
                } else if (trimmedLine.startsWith("LASTSYNCTIMESTAMP") && mode.equals(SyncMode.WEBSOCKET)) {
                    String[] parts = trimmedLine.split("\\|\\$\\|");
                    if (parts.length >= 3) {
                        String tableName = parts[1];
                        long lastAccessTimestamp = Long.parseLong(parts[2]);
                        syncManager.insertOrUpdateTableSyncMetadata(tableName, lastAccessTimestamp);
                    }

                } else if (trimmedLine.equalsIgnoreCase("END")) {
                    if (!records.isEmpty()) {
                        long res = insertStreamingDataInBulk(records, columns, tablename);
                        if (res == -1) {
                            return;
                        }
                        records.clear();
                    }

                    System.out.println("FINISHED");

                    if (mode.equals(SyncMode.POLLING)) {
                        ContentValues contentValues = new ContentValues();
                        contentValues.put(globalTimestampSeq, lastSyncTimestamp);
                        dbConnection.update(timeStampTable, contentValues, globalTimestampId + " = ?", new String[]{"1"});
                        syncManager.insertOrUpdateTableSyncMetadata(null, Long.parseLong(lastSyncTimestamp));
                    }

                    break;
                } else if (tablename != null && !columns.isEmpty()) {
                    String[] values = trimmedLine.split("\\|\\$\\|");
                    if (values.length == columns.size()) {
                        int isRemovedIndex = columns.indexOf("is_removed");

                        if (isRemovedIndex != -1 && "1".equals(values[isRemovedIndex])) {
                            long res = deleteRecordsWhileStreaming(values, columns, tablename, primaryKeyColumns);
                            if (res == -1) {
                                return;
                            }
                        } else {
                            records.add(values);

                            if (records.size() == 250) {
                                long res = insertStreamingDataInBulk(records, columns, tablename);
                                if (res == -1) {
                                    return;
                                }
                                records.clear();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception during processing changes: " + e);
            //isPullingChanges = false;
        } finally {
            dbConnection.execSQL("PRAGMA foreign_keys=ON;");
        }
    }

    public List<String> getTablesToSync() {
        List<String> tables = new ArrayList<>();
        try (Cursor cursor = dbConnection.rawQuery("SELECT tables FROM global_timestamp WHERE id = 1", null)) {

            if (cursor != null && cursor.moveToFirst()) {
                String tablesToSyncStr = cursor.getString(0);

                // Check if tablesToSyncStr is null or empty
                if (tablesToSyncStr != null && !tablesToSyncStr.isEmpty()) {
                    try {
                        JSONObject tablesConfig = new JSONObject(tablesToSyncStr);
                        Iterator<String> keys = tablesConfig.keys();
                        while (keys.hasNext()) {
                            String tableName = keys.next();
                            tables.add(tableName);
                        }
                    } catch (JSONException e) {
                        System.out.println("Failed to parse JSON: " + e);
                    }
                } else {
                    System.out.println("No tables to sync.");
                }
            }
        } catch (Exception e) {
            System.out.println("Exception in getTablesToSync: " + e);
        }

        return tables;
    }

    @Nullable
    private Cursor getChangedDocumentsForTable(String tableName, List<String> primaryKeyColumns) {
        Cursor cursor = null;
        try {
            List<String> columns = new ArrayList<>(primaryKeyColumns);
            columns.add("changes");

            String[] columnsArray = columns.toArray(new String[0]);
            cursor = dbConnection.query(
                    tableName,
                    columnsArray,
                    "changes IS NOT NULL AND LENGTH(changes) > 2",
                    null,
                    null,
                    null,
                    null
            );

            if (cursor != null && cursor.getCount() > 0) {
                return cursor;
            } else if (cursor != null) {
                cursor.close();
            }
        } catch (Exception e) {
            if (cursor != null) {
                cursor.close();
            }
            System.out.println(e.getMessage());
        }
        return null;
    }

    private long deleteRecordsWhileStreaming(String[] values, List<String> columns, String tablename, List<String> primaryKeyColumns) {
        try {
            // Build a WHERE clause to delete the record
            StringBuilder whereClause = new StringBuilder();
            List<String> whereArgs = new ArrayList<>();
            for (String pk : primaryKeyColumns) {
                int pkIndex = columns.indexOf(pk);
                if (pkIndex != -1) {
                    if (whereClause.length() > 0) {
                        whereClause.append(" AND ");
                    }
                    whereClause.append(pk).append(" = ?");
                    whereArgs.add(values[pkIndex]);
                }
            }

            // Execute delete query
            if (!whereClause.toString().isEmpty()) {
                return dbConnection.delete(tablename, whereClause.toString(), whereArgs.toArray(new String[0]));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
        return -1;
    }

    private long insertStreamingDataInBulk(List<String[]> records, List<String> columns, String tableName) {
        if (records == null || records.isEmpty() || columns == null || columns.isEmpty() || tableName == null || tableName.isEmpty()) {
            System.out.println(records);
            System.out.println(columns);
            System.out.println(tableName);
            Log.e(TAG, "Invalid input parameters");
            return -1;
        }
        List<String> primaryKeys = syncManager.getPrimaryKeyColumns(tableName);

        // Build SQL statement with placeholders
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(tableName).append(" (")
                .append(String.join(", ", columns)).append(") VALUES ");

        List<Object> bindArgs = new ArrayList<>();

        for (int i = 0; i < records.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append("(");

            for (int j = 0; j < columns.size(); j++) {
                if (j > 0) sql.append(", ");
                sql.append("?");

                bindArgs.add(parseValue(records.get(i)[j]));
            }
            sql.append(")");
        }

        sql.append(" ON CONFLICT(")
                .append(String.join(", ", primaryKeys))
                .append(") DO UPDATE SET ");

        boolean first = true;
        for (String col : columns) {
            if (primaryKeys.contains(col)) continue; // skip PK columns

            if (!first) sql.append(", ");
            sql.append(col).append(" = excluded.").append(col);
            first = false;
        }

        try (SQLiteStatement stmt = dbConnection.compileStatement(sql.toString())) {
            int index = 1;
            for (Object arg : bindArgs) {
                bind(stmt, index++, arg);
            }

            int rowsAffected = stmt.executeUpdateDelete();
//            System.out.println("Rows affected: " + rowsAffected);
            return rowsAffected;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    private Object parseValue(String value) {
        if (value == null || value.equals("NULL")) return null;
        if (SyncUtils.isInt(value)) return Integer.parseInt(value);
        if (SyncUtils.isLong(value)) return Long.parseLong(value);
        if (SyncUtils.isDouble(value)) return Double.parseDouble(value);
        if (SyncUtils.isFloat(value)) return Float.parseFloat(value);
        if (SyncUtils.isBoolean(value)) return Boolean.parseBoolean(value);
        return value;
    }

    private void bind(SQLiteStatement stmt, int index, Object arg) {
        if (arg == null) {
            stmt.bindNull(index);
        } else if (arg instanceof Integer) {
            stmt.bindLong(index, (Integer) arg);
        } else if (arg instanceof Long) {
            stmt.bindLong(index, (Long) arg);
        } else if (arg instanceof Double) {
            stmt.bindDouble(index, (Double) arg);
        } else if (arg instanceof Float) {
            stmt.bindDouble(index, (Float) arg);
        } else if (arg instanceof Boolean) {
            stmt.bindLong(index, (Boolean) arg ? 1 : 0);
        } else {
            stmt.bindString(index, arg.toString());
        }
    }

    public void clearLocalChangesWS(String[] messages) {
        try {
            String tableName = messages[1];
            List<String> primaryKeyColumns = new ArrayList<>();
            List<String> primaryKeyValues = new ArrayList<>();

            for (int i = 2; i < messages.length; i++) {
                String[] columns = messages[i].split(":");

                primaryKeyColumns.add(columns[0]);
                primaryKeyValues.add(columns[1]);
            }

            StringBuilder whereClause = new StringBuilder();
            for (int i = 0; i < primaryKeyColumns.size(); i++) {
                if (i > 0) whereClause.append(" AND ");
                whereClause.append(primaryKeyColumns.get(i)).append(" = ?");
            }

            try (Cursor cursor = dbConnection.query(
                    tableName,
                    new String[]{"changes"},
                    whereClause.toString(),
                    primaryKeyValues.toArray(new String[0]),
                    null,
                    null,
                    null
            )) {
                if (cursor != null && cursor.moveToFirst()) {
                    String localChangesStr = cursor.getString(cursor.getColumnIndex("changes"));
                    if (localChangesStr == null) return;

                    JSONObject localChanges = new JSONObject(localChangesStr);

                    // Remove PKs from localChanges
                    for (String pk : primaryKeyColumns) {
                        localChanges.remove(pk);
                    }

                    String mapKey = String.join(" ", Arrays.copyOfRange(messages, 1, messages.length));
                    JSONObject payload = dataPushPayload.get(mapKey);

                    if (payload == null) {
                        return;
                    }

                    payload.remove("tablename");
                    payload.remove("primaryKey");
                    payload.remove("actionType");
                    for (String pk : primaryKeyColumns) {
                        payload.remove(pk);
                    }

                    if (localChanges.toString().equals(payload.toString())) {
                        ContentValues contentValues = new ContentValues();
                        contentValues.putNull("changes");

                        int rowsUpdated = dbConnection.update(
                                tableName,
                                contentValues,
                                whereClause.toString(),
                                primaryKeyValues.toArray(new String[0])
                        );

                        if (rowsUpdated > 0) {
                            dataPushPayload.remove(mapKey);
                        }
                        Log.i(TAG, "Cleared local changes: " + rowsUpdated + " row(s) updated in " + tableName);
                    } else {
                        Log.i(TAG, "No matching payload found; local changes retained.");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error querying local changes", e);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error clearing local changes after successful sync", e);
        }
    }

    // Push Code used in ADC

    @NonNull
    public JSONArray generatePayloadForPush() throws JSONException {
        JSONArray payload = new JSONArray();
        List<String> tablesToSync = getTablesToSync();

        for (String tableName : tablesToSync) {
            // Cloud-owned tables are pulled only, never pushed back.
            if (NON_PUSHABLE_TABLES.contains(tableName)) {
                continue;
            }
            Cursor res = getChangedDocumentsForTable(tableName, syncManager.getPrimaryKeyColumns(tableName));

            if (res != null && res.moveToFirst()) {
                JSONObject tableObj = new JSONObject();
                tableObj.put("tablename", tableName);

                JSONArray changes = new JSONArray();
                do {
                    JSONObject changesObj = new JSONObject();

                    // Iterate over all columns in the cursor
                    for (int i = 0; i < res.getColumnCount(); i++) {
                        String columnName = res.getColumnName(i);
                        String columnValue = res.getString(i);
                        if (Objects.equals(columnName, "changes")) {
                            JSONObject changesColumn = new JSONObject(columnValue);

                            Iterator<String> keys = changesColumn.keys();
                            while (keys.hasNext()) {
                                String key = keys.next();
                                changesObj.put(key, changesColumn.get(key));
                            }
                        } else {
                            // For field of id
                            changesObj.put(columnName, columnValue);
                        }
                    }

                    changes.put(changesObj);
                } while (res.moveToNext());

                tableObj.put("changes", changes);
                payload.put(tableObj);
                res.close();
            } else if (res != null) {
                res.close();
            }
        }
        return payload;
    }

    private void pushChanges(@NonNull JSONArray payload) {
        Log.d(TAG, "Push changes payload: " + payload);
        String urlString = syncManager.getSyncURL() + "/api/v1/addChanges";
        if (syncManager.getVdmsID() != null && !syncManager.getVdmsID().trim().isEmpty()) {
            urlString += "?vdms_id=" + syncManager.getVdmsID();
        }

        RequestBody body = RequestBody.create(payload.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = SyncUtils.createPOSTRequest(urlString, body, syncManager.getFeature(), syncManager);

        try (Response response = syncManager.getClient().newCall(request).execute()) {
            if (response.isSuccessful()) {
                if (response.body() != null) {
                    String responseData = response.body().string();
                    Log.d("Push Changes", "Response after pushing changes: " + responseData);

                    try {
                        removeChanges(payload);
                        pullChanges();
                    } catch (JSONException e) {
                        Log.e("Push Changes", "JSONException while removing changes after pushing: " + e);
                    }
                } else {
                    Log.e("Push Changes", "Response body is null");
                }
            } else {
                Log.e("Push Changes", "Unexpected response code: " + response.code());
            }
        } catch (Exception e) {
            Log.e("Push Changes", "Exception during HTTP POST request: " + e);
        }
    }

    private void removeChanges(@NonNull JSONArray payload) throws JSONException {
        for (int i = 0; i < payload.length(); i++) {
            JSONObject tableObj = payload.getJSONObject(i);
            String tableName = tableObj.getString("tablename");
            JSONArray changes = tableObj.getJSONArray("changes");

            for (int j = 0; j < changes.length(); j++) {
                JSONObject change = changes.getJSONObject(j);

                // Get the primary key columns
                List<String> primaryKeyColumns = syncManager.getPrimaryKeyColumns(tableName);
                StringBuilder whereClause = new StringBuilder();
                List<String> whereArgs = new ArrayList<>();

                // Build the WHERE clause and arguments
                for (String primaryKey : primaryKeyColumns) {
                    if (change.has(primaryKey)) {
                        if (whereClause.length() > 0) {
                            whereClause.append(" AND ");
                        }
                        whereClause.append(primaryKey).append(" = ?");
                        whereArgs.add(change.getString(primaryKey));
                        change.remove(primaryKey);
                    }
                }

                try (Cursor cursor = dbConnection.query(
                        tableName,
                        new String[]{"changes"},
                        whereClause.toString(),
                        whereArgs.toArray(new String[0]),
                        null,
                        null,
                        null
                )) {
                    if (cursor != null && cursor.moveToFirst()) {
                        String existingChanges = cursor.getString(cursor.getColumnIndex("changes"));
                        JSONObject existingChangesJSON = new JSONObject(existingChanges);

                        // Remove primary key columns from existing changes JSON
                        for (String primaryKey : primaryKeyColumns) {
                            if (existingChangesJSON.has(primaryKey)) {
                                existingChangesJSON.remove(primaryKey);
                            }
                        }

                        existingChanges = existingChangesJSON.toString();

                        // If the changes match, update the database
                        if (existingChanges.equals(change.toString())) {
                            ContentValues contentValues = new ContentValues();
                            contentValues.putNull("changes");

                            dbConnection.update(tableName, contentValues, whereClause.toString(), whereArgs.toArray(new String[0]));
                        }
                    }
                } catch (SQLiteException e) {
                    System.out.println(e.getMessage());
                }
            }
        }
    }
}
