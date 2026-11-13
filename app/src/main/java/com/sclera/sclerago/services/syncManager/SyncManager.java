package com.sclera.sclerago.services.syncManager;

import static com.sclera.sclerago.services.syncManager.constants.SyncConfigTables.columnLastSynceddata;
import static com.sclera.sclerago.services.syncManager.constants.SyncConfigTables.columnTablename;
import static com.sclera.sclerago.services.syncManager.constants.SyncConfigTables.globalTimestampClientBarCodeLastSyncTimestamp;
import static com.sclera.sclerago.services.syncManager.constants.SyncConfigTables.globalTimestampClientQRCodeLastSyncTimestamp;
import static com.sclera.sclerago.services.syncManager.constants.SyncConfigTables.globalTimestampColumns;
import static com.sclera.sclerago.services.syncManager.constants.SyncConfigTables.globalTimestampId;
import static com.sclera.sclerago.services.syncManager.constants.SyncConfigTables.globalTimestampImageConfig;
import static com.sclera.sclerago.services.syncManager.constants.SyncConfigTables.globalTimestampMediaTimestamp;
import static com.sclera.sclerago.services.syncManager.constants.SyncConfigTables.globalTimestampQRCodeLastSyncTimestamp;
import static com.sclera.sclerago.services.syncManager.constants.SyncConfigTables.globalTimestampSeq;
import static com.sclera.sclerago.services.syncManager.constants.SyncConfigTables.globalTimestampSyncStatus;
import static com.sclera.sclerago.services.syncManager.constants.SyncConfigTables.globalTimestampTables;
import static com.sclera.sclerago.services.syncManager.constants.SyncConfigTables.multimediaContent;
import static com.sclera.sclerago.services.syncManager.constants.SyncConfigTables.multimediaId;
import static com.sclera.sclerago.services.syncManager.constants.SyncConfigTables.multimediaTable;
import static com.sclera.sclerago.services.syncManager.constants.SyncConfigTables.syncMetadataTable;
import static com.sclera.sclerago.services.syncManager.constants.SyncConfigTables.timeStampTable;
import static com.sclera.sclerago.services.syncManager.utils.SyncUtils.sharedPrefs;
import static com.sclera.sclerago.utility.Utility.context;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.sclera.sclerago.R;
import com.sclera.sclerago.services.syncManager.enums.OfflineFeatures;
import com.sclera.sclerago.services.syncManager.enums.SyncMode;
import com.sclera.sclerago.services.syncManager.services.DataSyncService;
import com.sclera.sclerago.services.syncManager.services.ImageSyncService;
import com.sclera.sclerago.services.syncManager.services.LargeDataSyncService;
import com.sclera.sclerago.services.syncManager.services.WebSocketClientService;
import com.sclera.sclerago.services.syncManager.utils.AuthInterceptor;
import com.sclera.sclerago.services.syncManager.utils.SyncUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.requery.android.database.sqlite.SQLiteDatabase;
import io.requery.android.database.sqlite.SQLiteStatement;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;

@SuppressLint("Range")
public class SyncManager {
    // Sync Config Data
    public static String userId;
    public static String role;
    private String vdmsID;
    private String syncURL;
    public static String user;
    private String orgId;
    public static String token;
    public static String sessionId;
    public static String sessionUrl;
    public static String cloudUrl;
    public String inspectionVersion;
    public String propertyId;
    public final Context appContext;
    public final OfflineFeatures feature;
    private SQLiteDatabase dbConnection;

    @SuppressLint("CommitPrefEdits")
    public SharedPreferences.Editor editor;

    public volatile boolean continueDataSync = true;
    public volatile boolean continueImageSync = true;
    public volatile boolean continueQRDataSync = true;
    public volatile boolean continueNFCDataSync = true;
    public volatile boolean continueBarCodeDataSync = true;
    public volatile boolean continueLargeFileSync = true;

    // DB downloading informations
    public int isSynced = 0;
    public int progress = 0;
    public double totalFileSizeMB;
    public double totalMBDownloaded;
    public long startTime;
    public long totalBytesRead;
    public String formattedRemainingTime;
    private int totalImages;

    private SyncMode currentDataSyncMode;
    private SyncMode currentImageSyncMode;
    private volatile boolean wsConnected = false;

    // cloud sync related information
    private boolean cloudSync;
    public int initialCloudSyncStatus = 0b00000;
    private static final String KEY_PULL_IMAGES = "_pull_images_enabled";
    private static final String KEY_DATA_SYNC     = "_data_sync_enabled";

    // connections
    private final OkHttpClient client = new OkHttpClient.Builder()
            .addInterceptor(new AuthInterceptor())
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build();
    private WebSocket webSocket = null;


    private static final String TAG = "SyncManager";

    // services
    DataSyncService dataSyncService;
    ImageSyncService imageSyncService;
    LargeDataSyncService largeDataSyncService;
    WebSocketClientService webSocketClientService;

    public String getVdmsID() {
        return vdmsID;
    }

    public void setVdmsID(String vdmsID) {
        this.vdmsID = vdmsID;
    }

    public String getSyncURL() {
        return syncURL;
    }

    public void setSyncURL(String syncURL) {
        this.syncURL = syncURL;
    }

    public SQLiteDatabase getDbConnection() {
        return dbConnection;
    }

    public OfflineFeatures getFeature() {
        return feature;
    }

    public static String getUserId() {
        return userId;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public boolean isWsConnected() {
        return wsConnected;
    }

    public void setWsConnected(boolean wsConnected) {
        this.wsConnected = wsConnected;
    }

    public OkHttpClient getClient() {
        return client;
    }

    public WebSocket getWebSocket() {
        return webSocket;
    }

    public void setWebSocket(WebSocket webSocket) {
        this.webSocket = webSocket;
    }

    public void setContinueDataSync(boolean continueDataSync) {
        this.continueDataSync = continueDataSync;
    }

    public void setContinueImageSync(boolean continueImageSync) {
        this.continueImageSync = continueImageSync;
    }

    public void setContinueQRDataSync(boolean continueQRDataSync) {
        this.continueQRDataSync = continueQRDataSync;
    }

    public void setContinueNFCDataSync(boolean continueNFCDataSync) {
        this.continueNFCDataSync = continueNFCDataSync;
    }

    public void setContinueBarCodeDataSync(boolean continueBarCodeDataSync) {
        this.continueBarCodeDataSync = continueBarCodeDataSync;
    }

    public void setContinueLargeFileSync(boolean continueLargeFileSync) {
        this.continueLargeFileSync = continueLargeFileSync;
    }

    public boolean isCloudSync() {
        return cloudSync;
    }

    public void setCloudSync(boolean cloudSync) {
        this.cloudSync = cloudSync;
    }

    public int getTotalImages() {
        return totalImages;
    }

    public SharedPreferences.Editor getEditor() {
        return editor;
    }

    public static void setUserId(String userId) {
        SyncManager.userId = userId;
    }

    public static String getRole() {
        return role;
    }

    public static void setRole(String role) {
        SyncManager.role = role;
    }

    public static String getUser() {
        return user;
    }

    public static void setUser(String user) {
        SyncManager.user = user;
    }

    public static String getToken() {
        return token;
    }

    public static void setToken(String token) {
        SyncManager.token = token;
    }

    public SyncMode getCurrentDataSyncMode() {
        return currentDataSyncMode;
    }

    public SyncMode getCurrentImageSyncMode() {
        return currentImageSyncMode;
    }

    public void setCurrentDataSyncMode(SyncMode currentDataSyncMode) {
        this.currentDataSyncMode = currentDataSyncMode;
    }

    public void setCurrentImageSyncMode(SyncMode currentImageSyncMode) {
        this.currentImageSyncMode = currentImageSyncMode;
    }

    public static void setPullImagesEnabled(Context context, boolean enabled, String vdmsId) {
        SharedPreferences prefs = SyncUtils.getSharedPrefs(context);
        prefs.edit().putBoolean(vdmsId + KEY_PULL_IMAGES, enabled).apply();
    }

    public static boolean isPullImagesEnabled(Context context, String vdmsId) {
        SharedPreferences prefs = SyncUtils.getSharedPrefs(context);
        return prefs.getBoolean(vdmsId + KEY_PULL_IMAGES, true);
    }

    public static boolean isDataSyncEnabled(Context context, String vdmsId) {
        return SyncUtils.getSharedPrefs(context).getBoolean(vdmsId + KEY_DATA_SYNC, true);
    }

    public static void setDataSyncEnabled(Context context, boolean enabled, String vdmsId) {
        SyncUtils.getSharedPrefs(context).edit().putBoolean(vdmsId + KEY_DATA_SYNC, enabled).apply();
    }


    public String getInspectionVersion() {
        return inspectionVersion;
    }

    public void setInspectionVersion(String inspectionVersion) {
        this.inspectionVersion = inspectionVersion;
    }

    public DataSyncService getDataSyncService() {
        return dataSyncService;
    }

    public ImageSyncService getImageSyncService() {
        return imageSyncService;
    }

    public LargeDataSyncService getLargeDataSyncService() {
        return largeDataSyncService;
    }

    // for asset data collection
    @JsonCreator
    public SyncManager(String user, String url, String token, String sessionId, String sessionUrl, String orgId, Context appContext, String propertyId) {
        SyncManager.user = user;
        this.syncURL = url + "/adc-offline-sync";
        SyncManager.token = token;
        SyncManager.sessionId = sessionId;
        SyncManager.sessionUrl = sessionUrl;
        this.orgId = orgId;
        this.appContext = appContext;
        this.feature = OfflineFeatures.ASSET_DATA_COLLECTION;
        this.dbConnection = null;
        editor = SyncUtils.getSharedPrefs(appContext).edit();
        this.isSynced = sharedPrefs.getInt("adc_sync_status", 0);
        this.totalImages = sharedPrefs.getInt("adc_total_images", 0);
        this.propertyId = propertyId;
    }

    public void startConnection() {
        if (progress != 0) {
            return;
        }

        try {
            setContinueDataSync(true);
            setContinueImageSync(true);
            setContinueLargeFileSync(true);
            setContinueNFCDataSync(true);
            setContinueQRDataSync(true);
            setContinueBarCodeDataSync(true);

            File dbFile;
            String syncTimestamp;

            Map<String, File> dbMap = pullInitialDatabase();
            if (dbMap == null || dbMap.isEmpty()) {
                Log.w(TAG, "Database map is empty or null.");
                return;
            }

            Map.Entry<String, File> firstEntry = dbMap.entrySet().iterator().next();
            syncTimestamp = firstEntry.getKey() == null ? "0" : firstEntry.getKey();
            dbFile = firstEntry.getValue();

            assert dbFile != null;
            if (dbConnection == null || !dbConnection.isOpen()) {
                String path = dbFile.getAbsolutePath();
                dbConnection = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.CREATE_IF_NECESSARY);
            }

            //Create global_timestamp table
            String lastPulledQuery = "CREATE TABLE IF NOT EXISTS " + timeStampTable + " ("
                    + globalTimestampId + " TEXT PRIMARY KEY DEFAULT '1', "
                    + globalTimestampSeq + " INTEGER DEFAULT 0, "
                    + globalTimestampMediaTimestamp + " INTEGER DEFAULT 0, "
                    + globalTimestampTables + " TEXT, "
                    + globalTimestampImageConfig + " TEXT, "
                    + globalTimestampSyncStatus + " INTEGER DEFAULT 1, "
                    + globalTimestampQRCodeLastSyncTimestamp + " INTEGER DEFAULT 0, "
                    + globalTimestampClientBarCodeLastSyncTimestamp + " INTEGER DEFAULT 0, "
                    + globalTimestampClientQRCodeLastSyncTimestamp + " INTEGER DEFAULT 0);";


            dbConnection.execSQL(lastPulledQuery);

            for (String[] column : globalTimestampColumns) {
                if (!SyncUtils.columnExists(timeStampTable, column[0], dbConnection)) {
                    String alterQuery = "ALTER TABLE " + timeStampTable + " ADD COLUMN " + column[0] + " " + column[1] + ";";
                    dbConnection.execSQL(alterQuery);
                }
            }

            ContentValues values = new ContentValues();
            values.put(globalTimestampId, 1);
            values.put(globalTimestampSeq, Long.parseLong(syncTimestamp));

            int rowsAffected = dbConnection.update(timeStampTable, values, globalTimestampId + " = ?", new String[]{"1"});

            if (rowsAffected == 0) {
                dbConnection.insertWithOnConflict(timeStampTable, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            }

            //Create multimedia table
            String imagesQuery = "CREATE TABLE IF NOT EXISTS " + multimediaTable + " ("
                    + multimediaId + " TEXT PRIMARY KEY, "
                    + multimediaContent + " BLOB "
                    + ");";

            dbConnection.execSQL(imagesQuery);
            generateTriggersForMultimediaTable();

            int retries = 0;
            int maxRetries = 5;
            while (retries < maxRetries) {
                if (pullDataConfiguration(Long.parseLong(syncTimestamp))) {
                    break;
                }

                retries++;

                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    break;
                }
            }

            pullImageConfiguration();
        } catch (Exception e) {
            Log.e(TAG, "Exception: " + e.getMessage());
        }
    }

    public void closeConnection() {
        isSynced = 0;

        if (dbConnection != null && dbConnection.isOpen()) {
            dbConnection.close();
            Log.d(TAG, "Database connection for " + vdmsID + " closed successfully.");
        } else {
            Log.d(TAG, "Database connection is already closed for " + vdmsID + " or was null.");
        }

        setContinueDataSync(false);
        setContinueImageSync(false);
        setContinueLargeFileSync(false);
        setContinueQRDataSync(false);
        setContinueNFCDataSync(false);
        setContinueBarCodeDataSync(false);

        if (dataSyncService != null) {
            if (dataSyncService.getDataSyncThread() != null && dataSyncService.getDataSyncThread().isAlive()) {
                dataSyncService.getDataSyncThread().interrupt();
            }

            if (dataSyncService.getDataSyncWSThread() != null && dataSyncService.getDataSyncWSThread().isAlive()) {
                dataSyncService.getDataSyncWSThread().interrupt();
            }
        }

        if (imageSyncService != null) {
            if (imageSyncService.getImageSyncThread() != null && imageSyncService.getImageSyncThread().isAlive()) {
                imageSyncService.getImageSyncThread().interrupt();
            }

            if (imageSyncService.getImageSyncWSThread() != null && imageSyncService.getImageSyncWSThread().isAlive()) {
                imageSyncService.getImageSyncWSThread().interrupt();
            }
        }

        if (largeDataSyncService != null) {
            if (largeDataSyncService.getLargeDataSyncThread() != null && largeDataSyncService.getLargeDataSyncThread().isAlive()) {
                largeDataSyncService.getLargeDataSyncThread().interrupt();
            }
        }

        if (feature.equals(OfflineFeatures.ADMIN)) {
            setContinueQRDataSync(false);
            setContinueNFCDataSync(false);
            setContinueBarCodeDataSync(false);

            editor.remove(getVdmsID() + "_sync_status");
            editor.remove(getVdmsID() + "_sync_timestamp");
            editor.remove(getVdmsID() + "_cloud_sync_status");
            editor.remove(getVdmsID() + KEY_PULL_IMAGES);
            editor.remove(getVdmsID() + KEY_DATA_SYNC);
            editor.apply();
        }

        if (feature.equals(OfflineFeatures.ASSET_DATA_COLLECTION)) {
            editor.remove("adc_total_images");
            editor.remove("adc_sync_status");
            editor.remove("adc_sync_timestamp");
            editor.apply();
        }

        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();

        if (client.cache() != null) {
            try {
                client.cache().close();
                Log.d(TAG, "Client cache closed successfully.");
            } catch (IOException e) {
                Log.d(TAG, "Error closing client cache: " + e.getMessage());
            }
        }

        if (webSocketClientService != null) {
            webSocketClientService.stopProcessor();
        }
    }

    public void startWebSocketClientService() {
        webSocketClientService = new WebSocketClientService(this);
    }

    public void startDataSyncingService() {
        dataSyncService = new DataSyncService(this, currentDataSyncMode);
        if (currentDataSyncMode.equals(SyncMode.WEBSOCKET)) {
            webSocketClientService.setDataSyncService(dataSyncService);
        }
    }

    public void startImageSyncingService() {
        imageSyncService = new ImageSyncService(this, currentImageSyncMode);
        if (currentDataSyncMode.equals(SyncMode.WEBSOCKET)) {
            webSocketClientService.setImageSyncService(imageSyncService);
        }
    }

    public void startLargeDataSyncingService() {
        largeDataSyncService = new LargeDataSyncService(this);
    }

    @SuppressLint("SetTextI18n")
    @Nullable
    private Map<String, File> pullInitialDatabase() throws InterruptedException {
        String dbName;
        String statusKey;
        String timestampKey;
        if (feature.equals(OfflineFeatures.ASSET_DATA_COLLECTION)) {
            dbName = OfflineFeatures.ASSET_DATA_COLLECTION.toString();
            statusKey = "adc_sync_status";
            timestampKey = "adc_sync_timestamp";
        } else {
            dbName = vdmsID;
            statusKey = vdmsID + "_sync_status";
            timestampKey = vdmsID + "_sync_timestamp";
        }

        try {
            Map<String, File> dbMap = new HashMap<>();
            File dbFile = appContext.getDatabasePath(dbName + ".db");
            File zipFile = appContext.getDatabasePath(dbName + ".zip");
            String urlString = syncURL + "/api/v1/download-sqlite-zipFile";

            long existingDbFileSize = 0;
            String syncTimestamp = "";

            Request request;

            if (vdmsID != null && !vdmsID.trim().isEmpty()) {
                urlString += "?vdms_id=" + vdmsID;
            }

            if (dbFile.exists() || (zipFile != null && Objects.requireNonNull(zipFile).exists())) {
                isSynced = sharedPrefs.getInt(statusKey, 0);

                if (feature.equals(OfflineFeatures.ADMIN)) {
                    SyncUtils.syncMap.put(vdmsID, isSynced);
                }

                if (isSynced == 2) {
                    dbConnection = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null, SQLiteDatabase.CREATE_IF_NECESSARY);
                    try (Cursor cursor = dbConnection.rawQuery("SELECT " + globalTimestampSeq + " FROM " + timeStampTable + " WHERE " + globalTimestampId + " = '1'", null)) {
                        if (cursor != null && cursor.moveToFirst()) {
                            long timestamp = cursor.getLong(0);
                            syncTimestamp = String.valueOf(timestamp);
                        }
                    }
                    dbMap.put(syncTimestamp, dbFile);
                    return dbMap;
                } else {
                    // Zip file is downloaded but error while unzipping the zip file
                    if (isSynced == 4) {
                        appContext.deleteDatabase(dbName + ".db");

                        if (!zipFile.exists()) {
                            isSynced = 3;
                            editor.putInt(statusKey, isSynced);
                            editor.apply();
                            return null;
                        }

                        SyncUtils.unzipDatabaseFile(zipFile, dbFile);
                        isSynced = 2;
                        appContext.deleteDatabase(dbName + ".zip");
                        editor.putInt(statusKey, isSynced);
                        syncTimestamp = sharedPrefs.getString(timestampKey, "0");
                        editor.apply();
                        createTimestampTable(dbFile, Long.valueOf(syncTimestamp));
                        dbMap.put(syncTimestamp, dbFile);
                        return dbMap;
                    }

                    closeConnection();
                    appContext.deleteDatabase(dbName + ".db");
                    appContext.deleteDatabase(dbName + ".zip");
                }
            }
            request = SyncUtils.createGETRequest(urlString, feature, this);

            setContinueDataSync(true);
            setContinueImageSync(true);
            setContinueLargeFileSync(true);

            if (feature.equals(OfflineFeatures.ADMIN)) {
                setContinueNFCDataSync(true);
                setContinueQRDataSync(true);
                setContinueBarCodeDataSync(true);
            }

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    isSynced = 1;

                    if (feature.equals(OfflineFeatures.ADMIN)) {
                        SyncUtils.syncMap.put(vdmsID, isSynced);
                    }

                    editor.putInt(statusKey, isSynced);
                    editor.apply();

                    // Get the total size of the file from the response headers
                    assert response.body() != null;
                    long fileSize;

                    fileSize = response.body().contentLength();
                    if (fileSize < 0) {
                        isSynced = 3;
                        if (feature.equals(OfflineFeatures.ADMIN)) {
                            SyncUtils.syncMap.put(vdmsID, isSynced);
                        }
                        editor.putInt(statusKey, isSynced);
                        editor.apply();
                        return null;
                    }
                    totalFileSizeMB = (double) fileSize / (1024 * 1024);
                    totalFileSizeMB = Math.round(totalFileSizeMB * 100.0) / 100.0;

                    String moduleType;
                    if (feature.equals(OfflineFeatures.ASSET_DATA_COLLECTION)) {
                        moduleType = "asset-data-collection";
                    } else {
                        moduleType = "vdms";
                    }

                    if (SyncUtils.checkStorage(moduleType)) {
                        progress = 0;
                        formattedRemainingTime = "";
                        isSynced = 3;

                        if (feature.equals(OfflineFeatures.ADMIN)) {
                            SyncUtils.syncMap.put(vdmsID, isSynced);
                        }

                        return null;
                    }

                    syncTimestamp = response.header("sync_timestamp");

                    editor.putString(timestampKey, syncTimestamp);
                    editor.apply();

                    startTime = System.currentTimeMillis();
                    long lastLoggedBytes = 0;

                    // Write the response body to the file
                    try (InputStream inputStream = response.body().byteStream();
                         FileOutputStream outputStream = new FileOutputStream(zipFile, true)) {

                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        totalBytesRead = existingDbFileSize;

                        Locale defaultLocale = Locale.getDefault();
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            totalBytesRead += bytesRead;
                            outputStream.write(buffer, 0, bytesRead);
                            totalMBDownloaded = Math.round(((double) totalBytesRead / (1024 * 1024)) * 100.0) / 100.0;

                            // Calculate remaining time and display
                            long currentTime = System.currentTimeMillis();
                            long elapsedTime = currentTime - startTime;

                            long remainingBytes = fileSize - totalBytesRead;

                            if (totalBytesRead - lastLoggedBytes >= 1024 * 1024) {
                                long remainingTimeInMillis = (remainingBytes * elapsedTime) / totalBytesRead;
                                long remainingSeconds = (remainingTimeInMillis / 1000) % 60;
                                long remainingMinutes = remainingTimeInMillis / (1000 * 60);

                                formattedRemainingTime = remainingMinutes > 0
                                        ? String.format(defaultLocale, "%.2fMB/%.2fMB | %02d " + context.getString(R.string.minutes_left), totalMBDownloaded, totalFileSizeMB, remainingMinutes)
                                        : String.format(defaultLocale, "%.2fMB/%.2fMB | %d " + context.getString(R.string.seconds_left), totalMBDownloaded, totalFileSizeMB, remainingSeconds);

                                lastLoggedBytes = totalBytesRead;
                            }

                            progress = (int) ((totalBytesRead * 100) / fileSize);
                        }

                        assert zipFile != null;
                        if (zipFile.exists()) {
                            isSynced = 4;
                            editor.putInt(statusKey, isSynced);
                            editor.apply();
                            SyncUtils.unzipDatabaseFile(zipFile, dbFile);
                            zipFile.delete();
                        }

                        Log.i(TAG, "Database downloaded successfully: " + dbFile.getAbsolutePath());
                        if (progress == 100) {
                            if (!continueDataSync && !continueImageSync && !continueNFCDataSync && !continueQRDataSync) {
                                return null;
                            }

                            isSynced = 2;
                            if (feature.equals(OfflineFeatures.ADMIN)) {
                                SyncUtils.syncMap.put(vdmsID, isSynced);
                            }

                            editor.putInt(statusKey, isSynced);
                            editor.apply();
                            progress = 0;

                            createTimestampTable(dbFile, Long.valueOf(syncTimestamp));
                        }
                        dbMap.put(syncTimestamp, dbFile);
                        return dbMap;
                    } catch (IOException e) {
                        Log.e(TAG, "Error while saving the database file", e);
                        progress = 0;
                        formattedRemainingTime = "";
                        isSynced = 3;
                        editor.putInt(statusKey, 1);
                        editor.apply();
                        if (feature.equals(OfflineFeatures.ADMIN)) {
                            SyncUtils.syncMap.put(vdmsID, isSynced);
                        }
                    }
                } else {
                    Log.e(TAG, "GET request failed. Response Code: " + response.code());
                    progress = 0;
                    formattedRemainingTime = "";
                    isSynced = 3;
                    editor.putInt(statusKey, 1);
                    editor.apply();
                    if (feature.equals(OfflineFeatures.ADMIN)) {
                        SyncUtils.syncMap.put(vdmsID, isSynced);
                    }
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Exception during HTTP GET request" + e);
            progress = 0;
            formattedRemainingTime = "";
            isSynced = 3;
            editor.putInt(statusKey, 1);
            editor.apply();
            if (feature.equals(OfflineFeatures.ADMIN)) {
                SyncUtils.syncMap.put(vdmsID, isSynced);
            }
        }
        return null;
    }

    private void createTimestampTable(File dbFile, Long syncTimestamp) {
        dbConnection = SQLiteDatabase.openDatabase(dbFile.getAbsolutePath(), null, SQLiteDatabase.CREATE_IF_NECESSARY);
        String createSyncMetadataTableQuery = "CREATE TABLE IF NOT EXISTS " + syncMetadataTable + " ("
                + columnTablename + " TEXT PRIMARY KEY, "
                + columnLastSynceddata + " INTEGER DEFAULT 0"
                + ");";

        dbConnection.execSQL(createSyncMetadataTableQuery);
        try (Cursor cursor = dbConnection.rawQuery(
                "SELECT name FROM sqlite_master " +
                        "WHERE type='table' " +
                        "AND name NOT LIKE 'sqlite_%' " +
                        "AND name NOT IN ('android_metadata', 'global_timestamp', 'multimedia', '" + syncMetadataTable + "');",
                null
        )) {
            while (cursor.moveToNext()) {
                String tableName = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                insertOrUpdateTableSyncMetadata(tableName, syncTimestamp);
            }
        }

    }

    private boolean pullDataConfiguration(Long syncTimestamp) {
        try {
            String urlString = syncURL + "/api/v1/getConfiguration";
            if (vdmsID != null && !vdmsID.trim().isEmpty()) {
                urlString += "?vdms_id=" + vdmsID;
            }

            Request request = SyncUtils.createGETRequest(urlString, feature, this);
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    assert response.body() != null;
                    JSONArray responseJson = new JSONArray(response.body().string());
                    Log.d(TAG, "Response JSON: " + responseJson);
                    processConfiguration(responseJson, syncTimestamp);
                    return true;
                } else {
                    Log.e(TAG, "GET request failed. Response Code: " + response.code());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception during HTTP GET request", e);
        }
        return false;
    }

    private void pullImageConfiguration() {
        try {
            String urlString = syncURL + "/api/v1/image/getImageConfig";
            if (vdmsID != null && !vdmsID.trim().isEmpty()) {
                urlString += "?vdms_id=" + vdmsID;
            }

            Request request = SyncUtils.createGETRequest(urlString, feature, this);
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    assert response.body() != null;
                    JSONObject responseJson = new JSONObject(response.body().string());
                    Log.d(TAG, "Response JSON: " + responseJson);
                    processImageConfiguration(responseJson);
                } else {
                    Log.e(TAG, "GET request failed. Response Code: " + response.code());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception during HTTP GET request", e);
        }
    }

    public void generateTriggersForMultimediaTable() {
        boolean hasMediaChangesColumn = false;
        boolean hasFlag = false;
        boolean hasContentType = false;
        boolean hasTableName = false;
        boolean hasColumnName = false;
        boolean hasRowId = false;
        boolean hasOrder = false;

        Cursor cursor = dbConnection.rawQuery("PRAGMA table_info(" + multimediaTable + ");", null);

        if (cursor != null) {
            while (cursor.moveToNext()) {
                String columnName = cursor.getString(cursor.getColumnIndex("name"));
                switch (columnName) {
                    case "media_changes":
                        hasMediaChangesColumn = true;
                        break;
                    case "flag":
                        hasFlag = true;
                        break;
                    case "content_type":
                        hasContentType = true;
                        break;
                    case "table_name":
                        hasTableName = true;
                        break;
                    case "column_name":
                        hasColumnName = true;
                        break;
                    case "row_id":
                        hasRowId = true;
                        break;
                    case "order":
                        hasOrder = true;
                        break;
                }
            }
            cursor.close();
        }

        if (!hasMediaChangesColumn) {
            dbConnection.execSQL("ALTER TABLE " + multimediaTable + " ADD COLUMN media_changes TEXT;");
        }
        if (!hasFlag) {
            dbConnection.execSQL("ALTER TABLE " + multimediaTable + " ADD COLUMN flag INTEGER DEFAULT 0;");
        }
        if (!hasContentType) {
            dbConnection.execSQL("ALTER TABLE " + multimediaTable + " ADD COLUMN content_type TEXT;");
        }
        if (!hasTableName) {
            dbConnection.execSQL("ALTER TABLE " + multimediaTable + " ADD COLUMN table_name TEXT;");
        }
        if (!hasColumnName) {
            dbConnection.execSQL("ALTER TABLE " + multimediaTable + " ADD COLUMN column_name TEXT;");
        }
        if (!hasRowId) {
            dbConnection.execSQL("ALTER TABLE " + multimediaTable + " ADD COLUMN row_id TEXT;");
        }
        if (!hasOrder) {
            dbConnection.execSQL("ALTER TABLE " + multimediaTable + " ADD COLUMN `order` INTEGER DEFAULT 0;");
        }

        String createIndexSQL = "CREATE INDEX IF NOT EXISTS idx_row_id ON " + multimediaTable + " (row_id);";
        try {
            dbConnection.execSQL(createIndexSQL);
        } catch (SQLException e) {
            Log.e(TAG, "Error generating triggers for multimedia table: " + e);
        }

        // Build the SQL statement for the UPDATE trigger
        String updateTriggerSQL = "CREATE TRIGGER IF NOT EXISTS update_images_trigger " +
                "AFTER UPDATE ON " + multimediaTable + " " +
                "FOR EACH ROW " +
                "WHEN (OLD.flag = NEW.flag AND OLD." + multimediaContent + " != NEW." + multimediaContent + ") " +
                "OR (OLD." + multimediaContent + " IS NOT NULL AND NEW." + multimediaContent + " IS NULL) " +
                "OR (OLD." + multimediaContent + " IS NULL AND NEW." + multimediaContent + " IS NOT NULL) " +
                "OR (OLD.`order` != NEW.`order`) " +
                "BEGIN " +
                "   UPDATE " + multimediaTable + " SET media_changes = '1' " +
                "   WHERE " + multimediaId + " = NEW." + multimediaId + "; " +
                "END;";


        // Build the SQL statement for the INSERT trigger
        String insertTriggerSQL = "CREATE TRIGGER IF NOT EXISTS insert_images_trigger " +
                "AFTER INSERT ON " + multimediaTable + " " +
                "FOR EACH ROW " +
                "WHEN NEW.flag = 0 " +
                "BEGIN " +
                "   UPDATE " + multimediaTable + " SET media_changes = '1' " +
                "   WHERE " + multimediaId + " = NEW." + multimediaId + "; " +
                "END;";

        // Execute the trigger SQL
        try {
            dbConnection.execSQL(updateTriggerSQL);
            dbConnection.execSQL(insertTriggerSQL);
        } catch (SQLException e) {
            Log.e(TAG, "Error generating triggers for multimedia table: " + e);
        }
    }

    private void createTriggersForTable(String tableName, @NonNull JSONArray columns, boolean tableAltered) throws JSONException {
        // Retrieve all primary key columns
        List<String> primaryKeyColumns = getPrimaryKeyColumns(tableName);

        int chunkSize = 50;
        int numChunks = (columns.length() + chunkSize - 1) / chunkSize;

        // Check if the 'changes, flag, sync_timestamp, is_removed' column exists
        boolean hasChangesColumn = false;
        boolean hasFlagColumn = false;
        boolean hasTimestampColumn = false;
        boolean hasDeleted = false;
        Cursor cursor = dbConnection.rawQuery("PRAGMA table_info(" + tableName + ");", null);

        List<String> jsonTypeColumns = new ArrayList<>();
        if (cursor != null) {
            while (cursor.moveToNext()) {
                String columnName = cursor.getString(cursor.getColumnIndex("name"));
                String columnType = cursor.getString(cursor.getColumnIndex("type"));

                if ("changes".equals(columnName)) {
                    hasChangesColumn = true;
                } else if ("flag".equals(columnName)) {
                    hasFlagColumn = true;
                } else if ("sync_timestamp".equals(columnName)) {
                    hasTimestampColumn = true;
                } else if ("is_removed".equals(columnName)) {
                    hasDeleted = true;
                }

                if ("JSON".equals(columnType)) {
                    jsonTypeColumns.add(columnName);
                }
            }
            cursor.close();
        }

        // Add 'changes' column if it doesn't exist
        if (!hasChangesColumn) {
            dbConnection.execSQL("ALTER TABLE " + tableName + " ADD COLUMN changes TEXT;");
        }

        // Add 'flag' column if it doesn't exist
        if (!hasFlagColumn) {
            dbConnection.execSQL("ALTER TABLE " + tableName + " ADD COLUMN flag INTEGER DEFAULT 0;");
        }

        // Add 'sync_timestamp' column if it doesn't exist
        if (!hasTimestampColumn) {
            dbConnection.execSQL("ALTER TABLE " + tableName + " ADD COLUMN sync_timestamp BIGINT;");
        }

        // Add 'is_removed' column if it doesn't exist
        if (!hasDeleted) {
            dbConnection.execSQL("ALTER TABLE " + tableName + " ADD COLUMN is_removed INTEGER DEFAULT 0;");
        }

        // If table is altered, remove old triggers
        if (tableAltered) {
            Cursor triggerCursor = dbConnection.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='trigger' AND name LIKE ? OR name LIKE ?",
                    new String[]{
                            "update_" + tableName + "_%",
                            "insert_" + tableName + "_%"
                    }
            );

            if (triggerCursor != null) {
                while (triggerCursor.moveToNext()) {
                    String triggerName = triggerCursor.getString(0);
                    dbConnection.execSQL("DROP TRIGGER IF EXISTS " + triggerName);
                }
                triggerCursor.close();
            }
        }

        StringBuilder whereClauseBuilder = new StringBuilder();
        for (String primaryKeyColumn : primaryKeyColumns) {
            whereClauseBuilder.append(primaryKeyColumn).append(" = NEW.")
                    .append(primaryKeyColumn).append(" AND ");
        }

        // Remove the trailing " AND "
        if (whereClauseBuilder.length() > 0) {
            whereClauseBuilder.setLength(whereClauseBuilder.length() - 5);
        }

        String whereClause = whereClauseBuilder.toString();

        // Create update triggers in chunks
        for (int i = 0; i < numChunks; i++) {
            StringBuilder fieldsBuilder = new StringBuilder();

            // Build JSON object entries for the current chunk
            for (int j = i * chunkSize; j < Math.min((i + 1) * chunkSize, columns.length()); j++) {
                String col = columns.getJSONObject(j).getString("name");

                if (jsonTypeColumns.contains(col)) {
                    // JSON-specific handling
                    fieldsBuilder.append("'").append(col).append("', CASE ")
                            .append("WHEN json_valid(NEW.").append(col).append(") AND json_valid(OLD.").append(col).append(") AND NEW.").append(col).append(" != OLD.").append(col).append(" THEN (")
                            .append("  SELECT json_group_object(")
                            .append("key, ")
                            .append("CASE ")
                            .append(" WHEN json_valid(value) THEN json(value) ")
                            .append(" ELSE value ")
                            .append("END")
                            .append(") ")
                            .append("  FROM (")
                            .append("    SELECT key, json_extract(NEW.").append(col).append(", '$.' || key) AS value ")
                            .append("    FROM json_each(NEW.").append(col).append(") ")
                            .append("    WHERE json_extract(OLD.").append(col).append(", '$.' || key) IS NULL ")
                            .append("       OR json_extract(NEW.").append(col).append(", '$.' || key) != json_extract(OLD.").append(col).append(", '$.' || key) ")
                            .append("    UNION ALL ")
                            .append("    SELECT key, '\"null\"' AS value ")
                            .append("    FROM json_each(OLD.").append(col).append(") ")
                            .append("    WHERE json_extract(NEW.").append(col).append(", '$.' || key) IS NULL ")
                            .append("  ) ")
                            .append(") ")
                            .append("WHEN NEW.").append(col).append(" IS NOT NULL AND OLD.").append(col).append(" IS NULL THEN NEW.").append(col).append(" ")
                            .append("WHEN NEW.").append(col).append(" IS NULL AND OLD.").append(col).append(" IS NOT NULL THEN 'null' ")
                            .append("ELSE json_extract(COALESCE(changes, '{}'), '$.").append(col).append("') END, ");
                } else {
                    // Standard handling for non-JSON columns
                    fieldsBuilder.append("'").append(col).append("', ")
                            .append("CASE ")
                            .append("WHEN NEW.").append(col).append(" IS NULL AND OLD.").append(col).append(" IS NOT NULL THEN 'null' ")
                            .append("WHEN NEW.").append(col).append(" != OLD.").append(col).append(" THEN NEW.").append(col).append(" ")
                            .append("WHEN NEW.").append(col).append(" IS NOT NULL AND OLD.").append(col).append(" IS NULL THEN NEW.").append(col).append(" ")
                            .append("ELSE json_extract(COALESCE(changes, '{}'), '$.").append(col).append("') END, ");
                }
            }

            // Add is_removed field at the end
            fieldsBuilder.append("'").append("is_removed").append("', ")
                    .append("CASE ")
                    .append("WHEN NEW.").append("is_removed").append(" IS NULL AND OLD.").append("is_removed").append(" IS NOT NULL THEN 'null' ") // Handle NULL assignment
                    .append("WHEN NEW.").append("is_removed").append(" != OLD.").append("is_removed").append(" THEN NEW.").append("is_removed").append(" ")
                    .append("WHEN NEW.").append("is_removed").append(" IS NOT NULL AND OLD.").append("is_removed").append(" IS NULL THEN NEW.").append("is_removed").append(" ")
                    .append("ELSE json_extract(COALESCE(changes, '{}'), '$.").append("is_removed").append("') END");

            // Remove trailing comma and space if necessary
            if (fieldsBuilder.length() > 0 && fieldsBuilder.charAt(fieldsBuilder.length() - 2) == ',') {
                fieldsBuilder.setLength(fieldsBuilder.length() - 2);
            }

            // Create the update trigger for the current chunk
            String updateTrigger = "CREATE TRIGGER IF NOT EXISTS update_" + tableName + "_" + (i + 1) + " " +
                    "AFTER UPDATE ON " + tableName + " " +
                    "FOR EACH ROW " +
                    "WHEN OLD.flag = NEW.flag " +
                    "BEGIN " +
                    "    UPDATE " + tableName + " " +
                    "    SET changes = json_patch(" +
                    "        COALESCE(changes, '{}'), " +
                    "        json_object(" + fieldsBuilder + ") " +
                    "    ), " +
                    "    sync_timestamp = strftime('%s', 'now') " +
                    "    WHERE " + whereClause + "; " +
                    "END;";

            dbConnection.execSQL(updateTrigger);
        }

        // Create insert triggers in chunks
        for (int i = 0; i < numChunks; i++) {
            StringBuilder insertFieldsBuilder = new StringBuilder();

            // Build JSON object entries for the current chunk
            for (int j = i * chunkSize; j < Math.min((i + 1) * chunkSize, columns.length()); j++) {
                String col = columns.getJSONObject(j).getString("name");
//                insertFieldsBuilder.append("'").append(col).append("', NEW.")
//                        .append(col).append(", ");
                if (jsonTypeColumns.contains(col)) {
                    insertFieldsBuilder.append("'").append(col).append("', CASE ")
                            .append("WHEN json_valid(NEW.").append(col).append(") THEN (")
                            .append("SELECT json_group_object(key, value) ")
                            .append("FROM (SELECT key, json_extract(NEW.").append(col).append(", '$.' || key) AS value ")
                            .append("FROM json_each(NEW.").append(col).append("))) ")
                            .append("ELSE NEW.").append(col).append(" END, ");
                } else {
                    insertFieldsBuilder.append("'").append(col).append("', NEW.").append(col).append(", ");
                }
            }

            // Remove trailing comma and space if necessary
            if (insertFieldsBuilder.length() > 0 && insertFieldsBuilder.charAt(insertFieldsBuilder.length() - 2) == ',') {
                insertFieldsBuilder.setLength(insertFieldsBuilder.length() - 2);
            }

            // Create the insert trigger for the current chunk
            String insertTrigger = "CREATE TRIGGER IF NOT EXISTS insert_" + tableName + "_" + (i + 1) + " " +
                    "AFTER INSERT ON " + tableName + " " +
                    "FOR EACH ROW " +
                    "WHEN NEW.flag = 0 " +
                    "BEGIN " +
                    "    UPDATE " + tableName + " " +
                    "    SET changes = json_object( " + insertFieldsBuilder + " )," +
                    "        sync_timestamp = strftime('%s', 'now') " +
                    "    WHERE " + whereClause + "; " +
                    "END;";
            dbConnection.execSQL(insertTrigger);
        }
    }

    public void processConfiguration(JSONArray responseJson, Long syncTimestamp) throws JSONException {
        JSONArray tables = new JSONArray();

        try {
            for (int i = 0; i < responseJson.length(); i++) {
                JSONObject table = responseJson.getJSONObject(i);
                String tableName = table.getString("table_name");
                JSONArray columns = table.getJSONArray("column_name");
                JSONArray constraints = table.getJSONArray("constraints");
                boolean tableAltered = false;

                boolean existedBefore = SyncUtils.tableExists(tableName, dbConnection);

                if (!existedBefore) {
                    StringBuilder createTableQuery = new StringBuilder("CREATE TABLE IF NOT EXISTS ");
                    createTableQuery.append(tableName).append(" (");
                    for (int j = 0; j < columns.length(); j++) {
                        JSONObject column = columns.getJSONObject(j);
                        createTableQuery.append(column.getString("name")).append(" ")
                                .append(column.getString("type"));
                        if (j < columns.length() - 1) {
                            createTableQuery.append(", ");
                        }
                    }
                    if (constraints.length() > 0) {
                        createTableQuery.append(", ");
                    }
                    for (int k = 0; k < constraints.length(); k++) {
                        createTableQuery.append(constraints.getString(k));
                        if (k < constraints.length() - 1) {
                            createTableQuery.append(", ");
                        }
                    }
                    createTableQuery.append(");");

                    dbConnection.execSQL(createTableQuery.toString());
                    insertOrUpdateTableSyncMetadata(tableName, 0);
                }

                // Get current column names from the table
                Set<String> existingColumns = new HashSet<>();
                Cursor cursor = dbConnection.rawQuery("PRAGMA table_info(" + tableName + ");", null);
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        String name = cursor.getString(cursor.getColumnIndex("name"));
                        existingColumns.add(name);
                    }
                    cursor.close();
                }
                for (int j = 0; j < columns.length(); j++) {
                    JSONObject column = columns.getJSONObject(j);
                    String columnName = column.getString("name");
                    String columnType = column.getString("type");

                    String colLower = columnName.toLowerCase();

                    if (!existingColumns.contains(colLower)) {
                        try {
                            String alterQuery = "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnType + ";";
                            dbConnection.execSQL(alterQuery);
                            tableAltered = true;
                            System.out.println("Altered table " + tableName);
                        } catch (Exception e) {
                            Log.e("DB", "Failed to alter table: " + e.getMessage());
                        }
                    }
                }
                createTriggersForTable(tableName, columns, tableAltered);
                tables.put(tableName);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing configuration of tables: " + e);
        } finally {
            //Adding the tables config to global_timestamp table
            JSONObject tableConfig = new JSONObject();
            for (int i = 0; i < responseJson.length(); i++) {
                JSONObject tableObj = responseJson.getJSONObject(i);
                String tableName = tableObj.getString("table_name");
                JSONArray columnNames = new JSONArray();
                JSONArray columnArray = tableObj.getJSONArray("column_name");
                for (int j = 0; j < columnArray.length(); j++) {
                    JSONObject columnObj = columnArray.getJSONObject(j);
                    String column = columnObj.getString("name");
                    columnNames.put(column);
                }
                tableConfig.put(tableName, columnNames);
            }

            ContentValues values = new ContentValues();
            values.put(globalTimestampTables, tableConfig.toString());

            dbConnection.update(timeStampTable, values, globalTimestampId + " = '1'", null);
        }
    }

    private void processImageConfiguration(JSONObject responseJson) {
        try {
            // Update the image_config column where id = 1 in global_timestamp table
            ContentValues values = new ContentValues();
            values.put(globalTimestampId, "1");
            values.put(globalTimestampImageConfig, responseJson.toString());

            dbConnection.update(timeStampTable, values, globalTimestampId + " = ?", new String[]{"1"});

            Iterator<String> keys = responseJson.keys();

            while (keys.hasNext()) {
                String tableName = keys.next();
                JSONArray columns = responseJson.getJSONArray(tableName);

                for (int i = 0; i < columns.length(); i++) {
                    String columnName = columns.getString(i);
                    try {
                        String alterTableQuery1 = "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " TEXT";
                        dbConnection.execSQL(alterTableQuery1);
                    } catch (SQLiteException ignored) {
                    }
                    try {
                        String alterTableQuery2 = "ALTER TABLE " + tableName + " ADD COLUMN media_sync_timestamp INTEGER DEFAULT 0";
                        dbConnection.execSQL(alterTableQuery2);
                    } catch (SQLiteException ignored) {
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception while processing image configuration", e);
        }
    }

    public void addImage(byte[] imageBytes, String tableName, String columnName, String primaryKey, String syncTimestamp,
                         String contentType, String order, Boolean fromController, String imageId) throws JSONException {
        if (imageSyncService != null) {
            imageSyncService.addImage(imageBytes, 0, 0, tableName, columnName, primaryKey, syncTimestamp, contentType, order, fromController, imageId);
        }
    }

    public byte[] retrieveImageData(String id) {
        if (imageSyncService != null) {
            return imageSyncService.retrieveImageData(id);
        }
        return new byte[]{};
    }

    @NonNull
    public List<String> getPrimaryKeyColumns(String tableName) {
        List<String> primaryKeyColumns = new ArrayList<>();
        Cursor pkCursor = dbConnection.rawQuery("PRAGMA table_info(" + tableName + ");", null);
        if (pkCursor != null) {
            while (pkCursor.moveToNext()) {
                int isPrimaryKey = pkCursor.getInt(pkCursor.getColumnIndex("pk"));
                if (isPrimaryKey > 0) {
                    primaryKeyColumns.add(pkCursor.getString(pkCursor.getColumnIndex("name")));
                }
            }
            pkCursor.close();
        }
        if (primaryKeyColumns.isEmpty()) {
            throw new IllegalArgumentException("Table " + tableName + " has no primary key.");
        }
        return primaryKeyColumns;
    }

    public void insertOrUpdateTableSyncMetadata(String tableName, long syncTimestamp) {
        SQLiteDatabase db = dbConnection;

        try {
            if (tableName != null) {
                // Insert or replace specific table
                String sql = "INSERT OR REPLACE INTO " + syncMetadataTable +
                        " (" + columnTablename + ", " + columnLastSynceddata + ") VALUES (?, ?)";
                SQLiteStatement statement = db.compileStatement(sql);
                statement.bindString(1, tableName);
                statement.bindLong(2, syncTimestamp);
                statement.execute();
            } else {
                // Update all rows with the same timestamp
                String sql = "UPDATE " + syncMetadataTable +
                        " SET " + columnLastSynceddata + " = ?";
                SQLiteStatement statement = db.compileStatement(sql);
                statement.bindLong(1, syncTimestamp);
                statement.executeUpdateDelete();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error while inserting/updating sync metadata: " + e);
        }
    }

}

