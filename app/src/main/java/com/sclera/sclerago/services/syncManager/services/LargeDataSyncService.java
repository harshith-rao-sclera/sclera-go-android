package com.sclera.sclerago.services.syncManager.services;

import static com.sclera.sclerago.services.syncManager.constants.SyncConfigTables.*;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.database.Cursor;
import android.util.Log;

import androidx.annotation.NonNull;

import com.sclera.sclerago.services.syncManager.SyncManager;

import java.io.IOException;

import io.requery.android.database.sqlite.SQLiteDatabase;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

@SuppressLint("Range")
public class LargeDataSyncService {
    private final SyncManager syncManager;
    private Thread largeDataSyncThread;
    private final SQLiteDatabase dbConnection;
    private static final long LARGE_DATA_SYNC_PERIOD = 30000;
    private final String TAG = "Large file Sync Service";

    public Thread getLargeDataSyncThread() {
        return largeDataSyncThread;
    }

    public boolean hasPendingLargeFiles() {
        String query = "SELECT 1 FROM \"" + multimediaTable + "\" " +
                "WHERE \"media_changes\" IS NOT NULL " +
                "AND (\"content_type\" LIKE '%video%' " +
                "OR length(\"" + multimediaContent + "\") >= " + (10 * 1024 * 1024) + ") LIMIT 1;";
        try (Cursor cursor = dbConnection.rawQuery(query, null)) {
            return cursor != null && cursor.moveToFirst();
        } catch (Exception e) {
            Log.e(TAG, "Error checking pending large files", e);
            return false;
        }
    }

    public LargeDataSyncService(SyncManager syncManager) {
        this.syncManager = syncManager;
        this.dbConnection = syncManager.getDbConnection();

        startLargeDataSyncThread();
    }

    void startLargeDataSyncThread() {
        largeDataSyncThread = new Thread(() -> {
            while (syncManager.continueLargeFileSync) {
                try {
                    Cursor cursor = null;

                    try {
                        String query = "SELECT \"" + multimediaId + "\", \"table_name\", \"row_id\", \"column_name\", \"order\", \"content_type\" " +
                                "FROM \"" + multimediaTable + "\" " +
                                "WHERE \"media_changes\" IS NOT NULL " +
                                "AND (\"content_type\" LIKE '%video%' " +
                                "OR length(\"" + multimediaContent + "\") >= " + (10 * 1024 * 1024) + ") " +
                                "ORDER BY \"order\" ASC;";

                        cursor = dbConnection.rawQuery(query, null);

                        if (cursor != null) {
                            while (cursor.moveToNext()) {
                                String id = cursor.getString(cursor.getColumnIndex(multimediaId));
                                String tableName = cursor.getString(cursor.getColumnIndex("table_name"));
                                String recordId = cursor.getString(cursor.getColumnIndex("row_id"));
                                String columnName = cursor.getString(cursor.getColumnIndex("column_name"));
                                String contentType = cursor.getString(cursor.getColumnIndex("content_type"));

                                // Prepare SQL queries
                                String sizeQuery = "SELECT LENGTH(" + multimediaContent + ") FROM " + multimediaTable +
                                        " WHERE " + multimediaId + " = ?";
                                String chunkQueryTemplate = "SELECT SUBSTR(" + multimediaContent + ", %d, %d) FROM " +
                                        multimediaTable + " WHERE " + multimediaId + " = ?";

                                String key = tableName + " " + columnName + " " + recordId + " " + contentType;
                                if (pushLargeData(id, sizeQuery, chunkQueryTemplate, key)) {
                                    String sql = "SELECT 1 FROM " + multimediaTable +
                                            " WHERE table_name = ? AND column_name = ? AND row_id = ? AND multimedia_content IS NULL";

                                    Cursor isNullCursor = dbConnection.rawQuery(sql, new String[]{tableName, columnName, recordId});
                                    boolean isNull = (isNullCursor != null && isNullCursor.moveToFirst());
                                    if (isNullCursor != null) {
                                        isNullCursor.close();
                                    }

                                    if (!isNull) {
                                        String whereClause = "table_name = ? AND column_name = ? AND row_id = ?";
                                        String[] whereArgs = new String[]{tableName, columnName, recordId};

                                        ContentValues values = new ContentValues();
                                        values.putNull("media_changes");
                                        dbConnection.update(multimediaTable, values, whereClause, whereArgs);
                                    } else {
                                        String whereClause = "table_name = ? AND column_name = ? AND row_id = ? AND multimedia_content IS NULL";
                                        String[] whereArgs = new String[]{tableName, columnName, recordId};

                                        dbConnection.delete(multimediaTable, whereClause, whereArgs);
                                    }
                                } else {
                                    break;
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.out.println(e.getMessage());
                    } finally {
                        if (cursor != null) {
                            cursor.close();
                        }
                    }

                    Thread.sleep(LARGE_DATA_SYNC_PERIOD);
                } catch (InterruptedException e) {
                    Log.d(TAG, "Polling thread interrupted");
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "Error during polling", e);
                }
            }
        });

        largeDataSyncThread.start();
    }

    private boolean pushLargeData(String id, String sizeQuery, String chunkQueryTemplate, String key) {
        // NOTE:- id in method parameter is multimediaId
        try {
            // Get blob size
            int blobSize = 0;
            Cursor cursor = dbConnection.rawQuery(sizeQuery, new String[]{id});
            if (cursor != null && cursor.moveToFirst()) {
                blobSize = cursor.getInt(0);
            }
            if (cursor != null) cursor.close();

            if (blobSize == 0) {
                Log.e(TAG, "No data found for id: " + id);
            }

            RequestBody streamingBody = getRequestBody(id, blobSize, chunkQueryTemplate);

            // Build the single POST request
            Request request = new Request.Builder()
                    .url(syncManager.getSyncURL() + "/api/v1/video-stream")
                    .post(streamingBody)
                    .addHeader("key", key) // send header once
                    .addHeader("Video-Length", String.valueOf(blobSize))
                    .addHeader("Authorization", "Bearer " + SyncManager.token)
                    .addHeader("X-Tenant-ID", syncManager.getVdmsID())
                    .build();

            // Execute the request
            try (Response response = syncManager.getClient().newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Upload failed: " + response);
                    return false;
                } else {
                    Log.i(TAG, "Upload success for id:" + id + ", response code: " + response.code());
                    return true;
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error while pushing large files", e);
        }
        return false;
    }

    @NonNull
    private RequestBody getRequestBody(String id, int blobSize, String chunkQueryTemplate) {
        int chunkSize = 1024 * 1000; // 1 MB chunks
        final int totalSize = blobSize;

        // Create streaming request body
        RequestBody streamingBody = new RequestBody() {
            @Override
            public MediaType contentType() {
                return MediaType.parse("application/octet-stream");
            }

            @Override
            public long contentLength() {
                return totalSize; // If server supports chunked transfer, you can return -1
            }

            @Override
            public void writeTo(@NonNull BufferedSink bufferedSink) throws IOException {
                int offset = 1;
                while (offset <= totalSize) {
                    int length = Math.min(chunkSize, totalSize - offset + 1);
                    String chunkQuery = String.format(chunkQueryTemplate, offset, length);

                    Cursor chunkCursor = dbConnection.rawQuery(chunkQuery, new String[]{id});
                    if (chunkCursor != null && chunkCursor.moveToFirst()) {
                        byte[] chunk = chunkCursor.getBlob(0);
                        bufferedSink.write(chunk); // stream directly to network
                    }
                    if (chunkCursor != null) chunkCursor.close();

                    offset += chunkSize;
                }
                bufferedSink.flush();
            }
        };
        return streamingBody;
    }
}
