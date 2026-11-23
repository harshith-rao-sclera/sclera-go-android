package com.sclera.sclerago.services.syncManager;

import android.content.ContentValues;
import android.database.Cursor;
import android.util.Log;

import com.sclera.sclerago.services.syncManager.enums.SyncMode;
import com.sclera.sclerago.services.syncManager.services.CloudSyncService;
import com.sclera.sclerago.services.syncManager.utils.SyncUtils;
import com.sclera.sclerago.utility.Utility;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.requery.android.database.sqlite.SQLiteDatabase;

public class SyncManagerService {
    private static final String TAG = "SyncManagerService";

    public static void deleteDatabaseIfWrongUser(String currentUser) {
        try {
            Cursor assetDataCursor = GlobalConfigDatabaseHelper.initializeInstance(Utility.context).globalDB.rawQuery("SELECT " + GlobalConfigDatabaseHelper.ASSET_CONFIG_TABLE_COLUMN_USER + " FROM " + GlobalConfigDatabaseHelper.TABLE_NAME_ASSET_CONFIG, null);

            if (assetDataCursor != null && assetDataCursor.moveToFirst()) {
                String existingUser = assetDataCursor.getString(0);

                if (!existingUser.equals(currentUser)) {
                    closeAllAssetDataSyncs();
                    SyncUtils.deleteAssetDataCollectionData(Utility.context, null);
                    GlobalConfigDatabaseHelper.initializeInstance(Utility.context).dropTablesForAssetDataCollection(existingUser);
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

            String token = assetCollectionData.getAccessToken();
            String orgId = assetCollectionData.getOrganisationId();
            String sessionId = assetCollectionData.getSessionId();
            String sessionUrl = assetCollectionData.getSessionUrl();
            String cloudUrl = assetCollectionData.getCloudUrl();
            String propertyId = assetCollectionData.getPropertyId();

            // The property id names the property's database file, so anything else is refused.
            if (!SyncManager.isValidPropertyId(propertyId)) {
                Log.w(TAG, "Asset data sync not started: invalid property id");
                return;
            }

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
            values.put(GlobalConfigDatabaseHelper.ASSET_CONFIG_TABLE_COLUMN_PROPERTY_ID, propertyId);

            GlobalConfigDatabaseHelper.initializeInstance(Utility.context).globalDB.insertWithOnConflict(GlobalConfigDatabaseHelper.TABLE_NAME_ASSET_CONFIG, null, values, SQLiteDatabase.CONFLICT_REPLACE);

            // One engine (and database) per property, like inspection's per vdms id; they all keep syncing.
            SyncManager currentInstance = SyncUtils.adcInstances.get(propertyId);
            boolean existing = currentInstance != null;
            if (existing) {
                currentInstance.setOrgId(orgId);
            } else {
                currentInstance = new SyncManager(user, url, token, sessionId, sessionUrl, orgId, Utility.context, propertyId);
                SyncUtils.adcInstances.put(propertyId, currentInstance);
            }
            // The app chooses which property it works on (as it chooses the vdms for inspection); the first one is
            // current until then.
            if (SyncUtils.dbInstance == null) {
                SyncUtils.dbInstance = currentInstance;
            }

            if (!existing || currentInstance.isSynced != 2) {
                if (currentInstance.progress == 0) {
                    ExecutorService executorService = Executors.newSingleThreadExecutor();
                    SyncManager finalCurrentInstance = currentInstance;
                    executorService.execute(() -> {
                        finalCurrentInstance.startConnection();
                        // Sends the QR, barcode and NFC tags saved without a connection (once per engine).
                        CloudSyncService.ensureRunning(finalCurrentInstance);

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

    /**
     * Stops the asset data sync of {@code propertyId}. Its database and sync status stay on the device, so opening
     * the property again reuses a finished download.
     */
    public static void closeAssetDataSync(String propertyId) {
        if (propertyId == null) {
            return;
        }
        SyncManager instance = SyncUtils.adcInstances.remove(propertyId);
        if (instance != null) {
            try {
                instance.closeConnection();
            } catch (Exception e) {
                Log.w(TAG, "Error closing asset data sync");
            }
        }
        if (SyncUtils.dbInstance != null && propertyId.equals(SyncUtils.dbInstance.propertyId)) {
            SyncUtils.dbInstance = null;
        }
    }

    /** Stops the asset data sync of every property (on sign-out or when another user signs in). */
    public static void closeAllAssetDataSyncs() {
        for (String propertyId : new ArrayList<>(SyncUtils.adcInstances.keySet())) {
            closeAssetDataSync(propertyId);
        }
        // An engine started before it was registered by property (or without one) is stopped too.
        SyncManager current = SyncUtils.dbInstance;
        if (current != null) {
            try {
                current.closeConnection();
            } catch (Exception e) {
                Log.w(TAG, "Error closing asset data sync");
            }
            SyncUtils.dbInstance = null;
        }
    }
}
