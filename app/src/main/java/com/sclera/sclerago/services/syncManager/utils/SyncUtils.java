package com.sclera.sclerago.services.syncManager.utils;

import static com.sclera.sclerago.services.syncManager.constants.SyncConfigTables.*;
import static com.sclera.sclerago.utility.Utility.context;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

import com.sclera.sclerago.services.syncManager.GlobalConfigDatabaseHelper;
import com.sclera.sclerago.services.syncManager.SyncManager;
import com.sclera.sclerago.services.syncManager.enums.OfflineFeatures;
import com.sclera.sclerago.utility.Utility;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import io.requery.android.database.sqlite.SQLiteDatabase;
import okhttp3.Request;
import okhttp3.RequestBody;

@SuppressLint("Range")
public class SyncUtils {

    public static ConcurrentMap<String, SyncManager> dbInstances = new ConcurrentHashMap<>();
    public static ConcurrentMap<String, Integer> syncMap = new ConcurrentHashMap<>();
    public static ConcurrentMap<String, String> imageProgressMap = new ConcurrentHashMap<>();
    public static SyncManager dbInstance;
    public static String currentVDMS;
    public static String userData;
    public static double thresholdMB;
    // Initialised from App.onCreate — must not depend on MainActivity, since workers (e.g. AutoCheckoutWorker) can load this class first
    public static SharedPreferences sharedPrefs;
    public static final ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static SharedPreferences getSharedPrefs(Context context) {
        if (sharedPrefs == null) {
            sharedPrefs = context.getApplicationContext()
                    .getSharedPreferences("SYNC_MANAGER", Context.MODE_PRIVATE);
        }
        return sharedPrefs;
    }

    public static String assetDataImageProgress;

    /** Told (from a sync thread) which tables a batch of pulled changes touched, so the app can re-query them. */
    public interface TablesChangedListener {
        void onTablesChanged(Set<String> tables);
    }

    public static volatile TablesChangedListener tablesChangedListener;

    public static void notifyTablesChanged(Set<String> tables) {
        TablesChangedListener listener = tablesChangedListener;
        if (listener != null && tables != null && !tables.isEmpty()) {
            listener.onTablesChanged(tables);
        }
    }

    public static JSONObject response(JSONObject data, String message, String path, Integer status, Boolean success) {
        JSONObject result = new JSONObject();
        try {
            result.put("data", data);
            result.put("errorMessage", message);
            result.put("path", path);
            result.put("status", status);
            result.put("success", success);
            result.put("timestamp", BigInteger.valueOf(System.currentTimeMillis()));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return result;
    }

    public static <T> Set<T> getJSONArrayFromJSONStringForSet(String JSONString, Class<T> object_type) {
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try {
            CollectionType setType = mapper.getTypeFactory().constructCollectionType(HashSet.class, object_type);
            return mapper.readValue(JSONString, setType);
        } catch (JsonProcessingException e) {

            System.out.println(e);
            return null;
        }
    }

    // Helper function to convert JSONArray to SQL IN clause
    public static String convertJSONArrayToSQLInClause(JSONArray jsonArray) {
        StringBuilder inClause = new StringBuilder();
        if (jsonArray != null && jsonArray.length() > 0) {
            for (int i = 0; i < jsonArray.length(); i++) {
                inClause.append("'").append(jsonArray.optString(i)).append("'");
                if (i < jsonArray.length() - 1) {
                    inClause.append(", ");
                }
            }
        } else {
            inClause.append("''");
        }
        return inClause.toString();
    }

    /**
     * Puts a value into the ContentValues map with proper handling for null values.
     *
     * @param values       ContentValues object where the data will be inserted.
     * @param key          The key for the value in the ContentValues.
     * @param value        The value to be inserted. Can be Long or BigInteger.
     * @param defaultValue The default value to insert if the provided value is null.
     */
    public static void putValue(ContentValues values, String key, Object value, Object defaultValue) {
        if (value != null) {
            values.put(key, value.toString());
        } else {
            values.put(key, defaultValue.toString());
        }
    }

    //sync utils
    // Get current Unix timestamp in seconds
    public static long getCurrentTimestamp() {
        // Get current Unix timestamp in seconds
        return System.currentTimeMillis();
    }


    public static Request createGETRequest(String urlString, OfflineFeatures feature, SyncManager syncManager) {
        Request.Builder requestBuilder = new Request.Builder()
                .url(urlString)
                .get()
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer " + SyncManager.token)
                .addHeader("userEmail", SyncManager.user)
                .addHeader("role", SyncManager.role)
                .addHeader("version","v2");

        if (feature.equals(OfflineFeatures.ADMIN)) {
            requestBuilder.addHeader("Inspection-Version", syncManager.inspectionVersion);
            requestBuilder.addHeader("X-Tenant-ID", syncManager.getVdmsID());
        }

        if (feature.equals(OfflineFeatures.ASSET_DATA_COLLECTION)) {
            requestBuilder.addHeader("userid", SyncManager.userId)
                    .addHeader("scleraorgid", SyncUtils.dbInstance.getOrgId());
        }

        return requestBuilder.build();
    }

//    public static Request createGETRequestForFailedDBDownload(String urlString, String syncTimestamp, long existingDbFileSize) {
//        return new Request.Builder()
//                .url(urlString)
//                .get()
//                .addHeader("Accept", "application/json")
//                .addHeader("Authorization", "Bearer " + SyncManager.getToken())
//                .addHeader("filename", syncTimestamp)
//                .addHeader("byterange", String.valueOf(existingDbFileSize))
//                .asset-data-collection();
//    }

    public static Request createPOSTRequest(String urlString, RequestBody body, OfflineFeatures feature, SyncManager syncManager) {
        Request.Builder requestBuilder = new Request.Builder()
                .url(urlString)
                .post(body)
                .addHeader("Authorization", "Bearer " + SyncManager.token)
                .addHeader("userEmail", SyncManager.user)
                .addHeader("role", SyncManager.role)
                .addHeader("version","v2");

        if (feature.equals(OfflineFeatures.ADMIN)) {
            requestBuilder.addHeader("Inspection-Version", syncManager.inspectionVersion);
            requestBuilder.addHeader("X-Tenant-ID", syncManager.getVdmsID());
        }

        if (feature.equals(OfflineFeatures.ASSET_DATA_COLLECTION)) {
            requestBuilder.addHeader("userid", SyncManager.userId)
                    .addHeader("scleraorgid", SyncUtils.dbInstance.getOrgId());
        }

        return requestBuilder.build();
    }

    public static Request createPUTRequest(String urlString, RequestBody body) {
        return new Request.Builder()
                .url(urlString)
                .put(body)
                .addHeader("Authorization", "Bearer " + SyncManager.token)
                .build();
    }

    public static byte[] readBinaryData(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        return outputStream.toByteArray();
    }

    public static String getMultimediaIdFromURL(String urlString) {
        String multimediaId = "";
        try {
            // Decode the URL to handle any escape sequences
            String decodedUrl = URLDecoder.decode(urlString, "UTF-8");

            URL url = new URL(decodedUrl);
            String path = url.getPath();
            String[] pathParts = path.split("/");

            if (pathParts.length > 0) {
                multimediaId = pathParts[pathParts.length - 1];
            }
        } catch (MalformedURLException e) {
            System.out.println("Malformed URL: " + e.getMessage());
        } catch (UnsupportedEncodingException e) {
            System.out.println("Unsupported Encoding: " + e.getMessage());
        }
        return multimediaId;
    }

    public static boolean isValidJSONArray(String content) {
        try {
            new JSONArray(content);
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    public static boolean checkStorage(String moduleType) {
        StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
        long availableBytes = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
        double totalFileMB;
        if (moduleType.equals("asset-data-collection")) {
            totalFileMB = SyncUtils.dbInstance.totalFileSizeMB;
        } else {
            totalFileMB = SyncUtils.dbInstance.totalFileSizeMB;
        }
        thresholdMB = totalFileMB * 1.4;
        long thresholdBytes = (long) (thresholdMB * 1024 * 1024);
        return availableBytes < thresholdBytes;
    }

    @SuppressLint("DefaultLocale")
    public static double getProgress(int totalImages, String vdmsId) {
        SyncManager syncManager = null;
        if (!SyncUtils.dbInstances.isEmpty() && SyncUtils.dbInstances.containsKey(vdmsId)) {
            syncManager = SyncUtils.dbInstances.get(vdmsId);
        } else if (vdmsId == null) {
            syncManager = SyncUtils.dbInstance;
        }

        try {
            assert syncManager != null;
            try (Cursor cursor = syncManager.getDbConnection().rawQuery("SELECT COUNT(*) FROM " + multimediaTable + " WHERE media_changes IS NULL", null)) {
                if (cursor.moveToFirst()) {
                    int imagesDownloaded = cursor.getInt(0);
                    double progress = imagesDownloaded > 0 ? ((double) (imagesDownloaded * 100) / totalImages) : 0;
                    if (Utility.moduleType.equals("asset-data-collection")) {
                        SyncUtils.assetDataImageProgress = String.format("%d", (int) progress);
                    } else {
                        imageProgressMap.put(vdmsId, String.format("%d", (int) progress));
                    }
                    if (checkSyncStatus(vdmsId) == 2) return 100;
                    return progress;
                }
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return 100;
    }

    public static void unzipDatabaseFile(File zipFile, File outputDbFile) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                if (!zipEntry.isDirectory() && zipEntry.getName().endsWith(".db")) {
                    try (FileOutputStream fos = new FileOutputStream(outputDbFile)) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zipEntry = zis.getNextEntry();
            }
        }
    }

    public static int getTotalImagesInDB() {
        try (Cursor cursor = SyncUtils.dbInstance.getDbConnection().rawQuery("SELECT COUNT(*) FROM " +
                multimediaTable + " WHERE media_changes IS NULL", null)) {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return 0;
    }

    public static void updateToken(String token) {
        Cursor cursor = null;
        try {
            String tableName = GlobalConfigDatabaseHelper.TABLE_NAME_ASSET_CONFIG;

            String query = "SELECT * FROM " + tableName +
                    " WHERE " + GlobalConfigDatabaseHelper.ASSET_CONFIG_TABLE_COLUMN_TOKEN + " IS NOT NULL";
            cursor = GlobalConfigDatabaseHelper.initializeInstance(context).globalDB.rawQuery(query, null);

            if (cursor != null && cursor.moveToFirst()) {
                // Token exists, so update it
                ContentValues contentValues = new ContentValues();
                contentValues.put(GlobalConfigDatabaseHelper.ASSET_CONFIG_TABLE_COLUMN_TOKEN, token);

                // Adding a WHERE clause to update only the row with the existing token
                String whereClause = GlobalConfigDatabaseHelper.ASSET_CONFIG_TABLE_COLUMN_TOKEN + " IS NOT NULL";

                // Update the token where it exists
                int rowsAffected = GlobalConfigDatabaseHelper.initializeInstance(context).globalDB.update(
                        GlobalConfigDatabaseHelper.TABLE_NAME_ASSET_CONFIG,
                        contentValues,
                        whereClause,
                        null
                );

                if (rowsAffected > 0) {
                    Log.d("token-update", "Token updated successfully");
                    SyncManager.token = token;
                } else {
                    Log.d("token-update", "No rows affected by the update");
                }
            } else {
                Log.d("token-update", "No existing token found to update");
            }
        } catch (SQLiteException e) {
            Log.d("token-update", "Token update failed: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public static void updateSessionId(String sessionId) {
        Cursor cursor = null;
        try {
            String query = "SELECT * FROM " + GlobalConfigDatabaseHelper.TABLE_NAME_ASSET_CONFIG +
                    " WHERE " + GlobalConfigDatabaseHelper.ASSET_CONFIG_TABLE_COLUMN_SESSION_ID + " IS NOT NULL";
            cursor = GlobalConfigDatabaseHelper.initializeInstance(context).globalDB.rawQuery(query, null);

            if (cursor != null && cursor.moveToFirst()) {
                // Token exists, so update it
                ContentValues contentValues = new ContentValues();
                contentValues.put(GlobalConfigDatabaseHelper.ASSET_CONFIG_TABLE_COLUMN_SESSION_ID, sessionId);

                // Adding a WHERE clause to update only the row with the existing token
                String whereClause = GlobalConfigDatabaseHelper.ASSET_CONFIG_TABLE_COLUMN_SESSION_ID + " IS NOT NULL";

                // Update the token where it exists
                int rowsAffected = GlobalConfigDatabaseHelper.initializeInstance(context).globalDB.update(
                        GlobalConfigDatabaseHelper.TABLE_NAME_ASSET_CONFIG,
                        contentValues,
                        whereClause,
                        null
                );

                if (rowsAffected > 0) {
                    Log.d("session-id-update", "Session id updated successfully");
                    SyncManager.sessionId = sessionId;
                } else {
                    Log.d("session-id-update", "No rows affected by the update");
                }
            } else {
                Log.d("session-id-update", "No existing session id found to update");
            }
        } catch (SQLiteException e) {
            Log.d("session-id-update", "Session id update failed: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public static int checkSyncStatus(String vdmsId) {
        SyncManager syncManager = null;
        if (!SyncUtils.dbInstances.isEmpty() && SyncUtils.dbInstances.containsKey(vdmsId)) {
            syncManager = SyncUtils.dbInstances.get(vdmsId);
        } else if (vdmsId == null) {
            syncManager = SyncUtils.dbInstance;
        }


        try {
            if (syncManager != null) {
                try (Cursor cursor = syncManager.getDbConnection().rawQuery(
                        "SELECT " + globalTimestampSyncStatus +
                                " FROM " + timeStampTable +
                                " WHERE " + globalTimestampId + " = '1'", null)) {

                    if (cursor != null && cursor.moveToFirst()) {
                        return cursor.getInt(0);

                    }
                }
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return 0;
    }

    public static String hashBytes(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean isLong(String value) {
        try {
            Long.parseLong(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isFloat(String value) {
        try {
            Float.parseFloat(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isInt(String value) {
        if (value == null || value.isEmpty()) return false;
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isDouble(String value) {
        if (value == null || value.isEmpty()) return false;
        try {
            Double.parseDouble(value);
            // Ensure it is not a valid integer (optional)
            return value.contains(".") || value.toLowerCase().contains("e");
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isBoolean(String value) {
        if (value == null || value.isEmpty()) return false;
        return value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false");
    }


    public static boolean columnExists(String tableName, String columnName, SQLiteDatabase dbConnection) {
        Cursor cursor = dbConnection.rawQuery("PRAGMA table_info(" + tableName + ")", null);
        while (cursor.moveToNext()) {
            String existingColumn = cursor.getString(1); // Column name is in the second column (index 1)
            if (existingColumn.equals(columnName)) {
                cursor.close();
                return true;
            }
        }
        cursor.close();
        return false;
    }

    public static String replaceUrl(String syncURL) {
        return syncURL
                .replace("https://", "wss://")
                .replace("http://", "ws://")
                .replace("/adc-offline-sync", "");
    }

    public static boolean tableExists(String tableName, SQLiteDatabase dbConnection) {
        Cursor cursor = dbConnection.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",
                new String[]{tableName}
        );
        boolean exists = cursor.moveToFirst();
        cursor.close();
        return exists;
    }

}