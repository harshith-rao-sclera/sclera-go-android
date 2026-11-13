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
//            String user = assetCollectionData.getEmail();
//            String userId = assetCollectionData.getId();
//            String role = assetCollectionData.getRole();
//            String url = assetCollectionData.getUrl();
//
//            String token = assetCollectionData.getAccessToken();
//            String orgId = assetCollectionData.getOrganisationId();
//            String sessionId = assetCollectionData.getSessionId();
//            String sessionUrl = assetCollectionData.getSessionUrl();
//            String cloudUrl = assetCollectionData.getCloudUrl();

            String user = "john.doe@example.com";
            String userId = "USR_123456";
            String role = "ADMIN";
            String url = "https://adc.vdms163.qa-gateway.scleravdms.com";
            String token = "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJyZ2oxUUc2MWFERHVPSXRFWURGbHBXajllQzNUZ2YwZ3gwSHBMSkdFQlhnIn0.eyJleHAiOjE3OTQ1NjEwNDEsImlhdCI6MTc5NDU1ODM0MSwiYXV0aF90aW1lIjoxNzk0NTUwNjA3LCJqdGkiOiIwZmM5ZmNmYy1iOTE5LTRmMDMtYWMyYi00MzI4YTRkZWU1MGQiLCJpc3MiOiJodHRwczovL3FhLWFjY291bnRzLnNjbGVyYS5jb20vYXV0aC9yZWFsbXMvU2NsZXJhIiwic3ViIjoiNTc5NGM3NDUtMTY5Mi00YzYzLThiMWUtZWJmYTg5OGEzMWIyIiwidHlwIjoiQmVhcmVyIiwiYXpwIjoic2NsZXJhIiwibm9uY2UiOiJXZEZneFlFOWFDcEdXdmpNclRtNF9DZFpRb1BJbTZEQk1jNFJSNXVUbWhVIiwic2Vzc2lvbl9zdGF0ZSI6IjQ5OGQxNGU1LTdhMTUtNGYxYy04YTliLTEyZjczMzlmNjMyYyIsInJlYWxtX2FjY2VzcyI6eyJyb2xlcyI6WyJvZmZsaW5lX2FjY2VzcyJdfSwic2NvcGUiOiJvcGVuaWQgcHJvZmlsZSBlbWFpbCBvZmZsaW5lX2FjY2VzcyIsInNpZCI6IjQ5OGQxNGU1LTdhMTUtNGYxYy04YTliLTEyZjczMzlmNjMyYyIsImVtYWlsX3ZlcmlmaWVkIjp0cnVlLCJuYW1lIjoibWFzdGVyIFVzZXIgQnJpamVzaCIsInByZWZlcnJlZF91c2VybmFtZSI6Im1hc3RlcnVzZXIuYnJpamVzaEBzY2xlcmEuY29tIiwibG9jYWxlIjoiRU4iLCJnaXZlbl9uYW1lIjoibWFzdGVyIFVzZXIgQnJpamVzaCIsImVtYWlsIjoibWFzdGVydXNlci5icmlqZXNoQHNjbGVyYS5jb20ifQ.ZkJExuMtlbbC-DcYcJGZNV5uDsAs201SN52b56siMuR1kiqQr15TIr2KXg7eJxqyvTiLHQ9GdmOzVDC15sD_WRcE_5I8k8OoMeV-TJvca6rtaU0vCVCDmv2CrTouDe0hj6XMOLNkKeRtGD0nn6CtjJZ6-KkhTmokddsDfeLgXVHifxFh0I6Hh6JKPiC9OEr3WAY1NbkoqIi_gSSdJYycTi-XSHOXykbOE8iLVK0dKZ5sRHmzP2NCjBEs2eOxmcO3a96LaNE-kdxhmIbR2rslO_wYLgffccsXPCh30zUgZOJIRkeCtZAiCtN0mmri_h1Y9KvA0lBpLjr9DTsuKYJ-cA";
            String orgId = "ORG_987654";
            String sessionId = "498d14e5-7a15-4f1c-8a9b-12f7339f632c";
            String sessionUrl = "https://app.example.com/session/SES_123456789";
            String propertyId = "01a1cec7-04a1-7979-95ee-44f8531ec489";
            String cloudUrl = "https://cloud.example.com";

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
                    currentInstance = new SyncManager(user, url, token, sessionId, sessionUrl, orgId, Utility.context, propertyId);
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
//                            finalCurrentInstance.startImageSyncingService();
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
