package com.sclera.sclerago.services.syncManager;

import android.content.ContentValues;
import android.database.Cursor;

import com.sclera.sclerago.services.syncManager.enums.OfflineFeatures;
import com.sclera.sclerago.services.syncManager.enums.SyncMode;
import com.sclera.sclerago.services.syncManager.utils.SyncUtils;
import com.sclera.sclerago.utility.Utility;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.requery.android.database.sqlite.SQLiteDatabase;

public class SyncManagerService {

    public static void deleteDatabaseIfWrongUser(String currentUser) {
        try {
            Cursor assetDataCursor = GlobalConfigDatabaseHelper.initializeInstance(Utility.context).globalDB.rawQuery("SELECT " + GlobalConfigDatabaseHelper.ASSET_CONFIG_TABLE_COLUMN_USER + " FROM " + GlobalConfigDatabaseHelper.TABLE_NAME_ASSET_CONFIG, null);

            if (assetDataCursor != null && assetDataCursor.moveToFirst()) {
                String existingUser = assetDataCursor.getString(0);

                if (!existingUser.equals(currentUser)) {
                    if (SyncUtils.dbInstance != null) {
                        SyncUtils.dbInstance.closeConnection();
                    }
                    boolean deleted = Utility.context.deleteDatabase(OfflineFeatures.ASSET_DATA_COLLECTION + ".db") ||
                            Utility.context.deleteDatabase(OfflineFeatures.ASSET_DATA_COLLECTION + ".zip");
                    if (!deleted) {
                        System.out.println("Error deleting the database");
                    }
                    GlobalConfigDatabaseHelper.initializeInstance(Utility.context).dropTablesForAssetDataCollection(existingUser);
                    SyncUtils.dbInstance = null;
                }

                assetDataCursor.close();
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    public static void initAssetDataSync(JSONObject jsonObject) {
        try {
            AssetCollectionData assetCollectionData = SyncUtils.objectMapper.readValue(jsonObject.toString(), AssetCollectionData.class);
            String user = assetCollectionData.getEmail();
            String userId = assetCollectionData.getId();
            String role = assetCollectionData.getRole();
            String url = assetCollectionData.getUrl();
            url = url.replace("/api/asset-data-collection", "");
            String token = assetCollectionData.getAccessToken();
            String orgId = assetCollectionData.getOrganisationId();
            String sessionId = assetCollectionData.getSessionId();
            String sessionUrl = assetCollectionData.getSessionUrl();
            String cloudUrl = assetCollectionData.getCloudUrl();

            SyncManager.token = token;
            SyncManager.sessionId = sessionId;
            SyncManager.sessionUrl = sessionUrl;
            SyncManager.userId = userId;
            SyncManager.cloudUrl = cloudUrl;
            if (role != null) {
                SyncManager.role = role;
            }

            ContentValues values = new ContentValues();
            values.put(GlobalConfigDatabaseHelper.ASSET_CONFIG_TABLE_COLUMN_ID, userId);
            if (role != null) {
                values.put(GlobalConfigDatabaseHelper.ASSET_CONFIG_TABLE_COLUMN_ROLE, role);
            }
            values.put(GlobalConfigDatabaseHelper.ASSET_CONFIG_TABLE_COLUMN_TOKEN, token);
            values.put(GlobalConfigDatabaseHelper.ASSET_CONFIG_TABLE_COLUMN_URL, url);
            values.put(GlobalConfigDatabaseHelper.ASSET_CONFIG_TABLE_COLUMN_ORG_ID, orgId);
            values.put(GlobalConfigDatabaseHelper.ASSET_CONFIG_TABLE_COLUMN_SESSION_ID, sessionId);
            values.put(GlobalConfigDatabaseHelper.ASSET_CONFIG_TABLE_COLUMN_SESSION_URL, sessionUrl);
            values.put(GlobalConfigDatabaseHelper.ASSET_CONFIG_TABLE_COLUMN_USER, user);
            values.put(GlobalConfigDatabaseHelper.ASSET_CONFIG_TABLE_COLUMN_CLOUD_URL, cloudUrl);

            SyncManager currentInstance;
            GlobalConfigDatabaseHelper.initializeInstance(Utility.context).globalDB.insertWithOnConflict(GlobalConfigDatabaseHelper.TABLE_NAME_ASSET_CONFIG, null, values, SQLiteDatabase.CONFLICT_REPLACE);

            if (SyncUtils.dbInstance != null && SyncUtils.dbInstance.isSynced == 2) {
                currentInstance = SyncUtils.dbInstance;
                currentInstance.setOrgId(orgId);
                SyncUtils.dbInstance = currentInstance;
            } else {
                if (SyncUtils.dbInstance != null) {
                    currentInstance = SyncUtils.dbInstance;
                    currentInstance.setOrgId(orgId);
                    SyncUtils.dbInstance = currentInstance;
                } else {
                    currentInstance = new SyncManager(user, url, token, sessionId, sessionUrl, orgId, Utility.context);
                    SyncUtils.dbInstance = currentInstance;
                }

                if (currentInstance.progress == 0) {
                    ExecutorService executorService = Executors.newSingleThreadExecutor();
                    SyncManager finalCurrentInstance = currentInstance;
                    executorService.execute(() -> {
                        finalCurrentInstance.startConnection();

                        if (finalCurrentInstance.isSynced == 2) {
                            finalCurrentInstance.setCurrentDataSyncMode(SyncMode.POLLING);
                            finalCurrentInstance.setCurrentImageSyncMode(SyncMode.POLLING);

                            finalCurrentInstance.startDataSyncingService();
                            finalCurrentInstance.startImageSyncingService();
                            finalCurrentInstance.startLargeDataSyncingService();
                        }
                    });
                }
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}
