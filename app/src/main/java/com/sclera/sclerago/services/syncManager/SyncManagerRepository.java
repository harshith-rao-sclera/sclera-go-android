package com.sclera.sclerago.services.syncManager;

import android.annotation.SuppressLint;
import android.database.Cursor;

import com.sclera.sclerago.services.syncManager.enums.SyncMode;
import com.sclera.sclerago.services.syncManager.utils.SyncUtils;
import com.sclera.sclerago.utility.Utility;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressLint("Range")
public class SyncManagerRepository {
    public void initializeAssetDBInstance() {
        try {
            int numberOfThreads = Runtime.getRuntime().availableProcessors(); // You can adjust the number of threads
            ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);

            // Query the globalDB for all existing entries
            String[] columns = {
                    GlobalConfigDatabaseHelper.ASSET_CONFIG_TABLE_COLUMN_ID,
                    GlobalConfigDatabaseHelper.ASSET_CONFIG_TABLE_COLUMN_ROLE,
                    GlobalConfigDatabaseHelper.ASSET_CONFIG_TABLE_COLUMN_USER,
                    GlobalConfigDatabaseHelper.ASSET_CONFIG_TABLE_COLUMN_ORG_ID,
                    GlobalConfigDatabaseHelper.ASSET_CONFIG_TABLE_COLUMN_TOKEN,
                    GlobalConfigDatabaseHelper.ASSET_CONFIG_TABLE_COLUMN_SESSION_ID,
                    GlobalConfigDatabaseHelper.ASSET_CONFIG_TABLE_COLUMN_SESSION_URL,
                    GlobalConfigDatabaseHelper.ASSET_CONFIG_TABLE_COLUMN_URL,
                    GlobalConfigDatabaseHelper.ASSET_CONFIG_TABLE_COLUMN_CLOUD_URL
            };
            Cursor cursor = GlobalConfigDatabaseHelper.initializeInstance(Utility.context)
                    .globalDB.query(GlobalConfigDatabaseHelper.TABLE_NAME_ASSET_CONFIG, columns, null, null, null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String userId = cursor.getString(cursor.getColumnIndex(GlobalConfigDatabaseHelper.ASSET_CONFIG_TABLE_COLUMN_ID));
                    String url = cursor.getString(cursor.getColumnIndex(GlobalConfigDatabaseHelper.ASSET_CONFIG_TABLE_COLUMN_URL));
                    String user = cursor.getString(cursor.getColumnIndexOrThrow(GlobalConfigDatabaseHelper.ASSET_CONFIG_TABLE_COLUMN_USER));
                    String orgId = cursor.getString(cursor.getColumnIndexOrThrow(GlobalConfigDatabaseHelper.ASSET_CONFIG_TABLE_COLUMN_ORG_ID));
                    String token = cursor.getString(cursor.getColumnIndex(GlobalConfigDatabaseHelper.ASSET_CONFIG_TABLE_COLUMN_TOKEN));
                    String sessionId = cursor.getString(cursor.getColumnIndex(GlobalConfigDatabaseHelper.ASSET_CONFIG_TABLE_COLUMN_SESSION_ID));
                    String sessionUrl = cursor.getString(cursor.getColumnIndex(GlobalConfigDatabaseHelper.ASSET_CONFIG_TABLE_COLUMN_SESSION_URL));
                    String role = cursor.getString(cursor.getColumnIndex(GlobalConfigDatabaseHelper.ASSET_CONFIG_TABLE_COLUMN_ROLE));
                    String cloudUrl = cursor.getString(cursor.getColumnIndex(GlobalConfigDatabaseHelper.ASSET_CONFIG_TABLE_COLUMN_CLOUD_URL));

                    if (SyncUtils.dbInstance == null) {
                        SyncManager syncManager = new SyncManager(user, url, token, sessionId, sessionUrl, orgId, Utility.context);
                        SyncUtils.dbInstance = syncManager;
                        SyncManager.userId = userId;
                        SyncManager.cloudUrl = cloudUrl;
                        if (role != null) {
                            SyncManager.role = role;
                        }

                        // Create a task to run each SyncManager operation
                        Runnable syncTask = () -> {
                            try {
                                syncManager.startConnection();

                                if (syncManager.isSynced == 2) {
                                    syncManager.setCurrentDataSyncMode(SyncMode.POLLING);
                                    syncManager.setCurrentImageSyncMode(SyncMode.POLLING);

                                    syncManager.startDataSyncingService();
                                    syncManager.startImageSyncingService();
                                    syncManager.startLargeDataSyncingService();
                                }

                            } catch (Exception e) {
                                System.out.println(e.getMessage());
                            }
                        };

                        // Submit the task to the executor service
                        executorService.submit(syncTask);
                    }

                } while (cursor.moveToNext());

                cursor.close();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getLastSyncTimestamp() {
        Cursor cursor = null;
        String lastSyncedTimestamp = null;

        try {
            String query = "SELECT seq from global_timestamp";

            if (Utility.moduleType.equals("asset-data-collection")) {
                cursor = SyncUtils.dbInstance.getDbConnection().rawQuery(query, null);
            } else {
                cursor = SyncUtils.dbInstance.getDbConnection().rawQuery(query, null);
            }

            if (cursor != null && cursor.moveToFirst()) {
                lastSyncedTimestamp = cursor.getString(cursor.getColumnIndex("seq"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return lastSyncedTimestamp;
    }
}
