package com.sclera.sclerago.services.syncManager.services;

import static com.sclera.sclerago.services.syncManager.constants.SyncConfigTables.globalTimestampClientBarCodeLastSyncTimestamp;
import static com.sclera.sclerago.services.syncManager.constants.SyncConfigTables.globalTimestampClientQRCodeLastSyncTimestamp;
import static com.sclera.sclerago.services.syncManager.constants.SyncConfigTables.globalTimestampId;
import static com.sclera.sclerago.services.syncManager.constants.SyncConfigTables.globalTimestampQRCodeLastSyncTimestamp;
import static com.sclera.sclerago.services.syncManager.constants.SyncConfigTables.timeStampTable;
import static com.sclera.sclerago.utility.Utility.context;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.sclera.sclerago.services.syncManager.GlobalConfigDatabaseHelper;
import com.sclera.sclerago.services.syncManager.SyncManager;
import com.sclera.sclerago.services.syncManager.dto.ClientBarCodeDTO;
import com.sclera.sclerago.services.syncManager.dto.ClientNfcDTO;
import com.sclera.sclerago.services.syncManager.dto.ClientQRCodeDTO;
import com.sclera.sclerago.services.syncManager.dto.NfcDTO;
import com.sclera.sclerago.services.syncManager.dto.NfcData;
import com.sclera.sclerago.services.syncManager.dto.ProxyProfileDTO;
import com.sclera.sclerago.services.syncManager.dto.QRCodeDTO;
import com.sclera.sclerago.services.syncManager.enums.OfflineFeatures;
import com.sclera.sclerago.services.syncManager.utils.SyncUtils;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.requery.android.database.sqlite.SQLiteDatabase;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CloudSyncService {
    private final SyncManager syncManager;
    private Thread qrDataSyncThread;
    private Thread nfcDataSyncThread;
    private Thread barCodeSyncThread;
    private static final long CLOUD_SYNC_PERIOD = 30000;
    private static final long pageSize = 10;
    private final String TAG = "Cloud Data Sync Service";
    private static final String LOG_TAG = "CloudSyncService";

    /** The old app's ApiEndpoints.CHECK_NFC_ID, relative to {@link #cloudBase()}. */
    private static final String CHECK_NFC_ID = "vdms/api/vdms/%s/checkNfcId";

    // The old app's proxy profile table (admin only). Sclera Go's GlobalConfigDatabaseHelper doesn't declare it.
    private static final String TABLE_PROXY_PROFILE = "proxy_profile";
    private static final String COLUMN_PROXY_PROFILE_ID = "id";
    private static final String COLUMN_PROXY_PROFILE_NAME = "name";
    private static final String COLUMN_PROXY_PROFILE_PUBLIC_IP = "public_ip";
    private static final String COLUMN_PROXY_PROFILE_TCP_PORT = "tcp_port";
    private static final String COLUMN_PROXY_PROFILE_UDP_PORT = "udp_port";
    private static final String COLUMN_PROXY_PROFILE_HOST_MACHINE = "host_machine";
    private static final String COLUMN_PROXY_PROFILE_CUSTOMER_ORG_ID = "customer_org_id";
    private static final String COLUMN_PROXY_PROFILE_SSL_ENABLED = "ssl_enabled";
    private static final String COLUMN_PROXY_PROFILE_IS_GLOBAL = "is_global";
    private static final String COLUMN_PROXY_PROFILE_IS_DELETED = "is_deleted";
    private static final String COLUMN_PROXY_PROFILE_LATITUDE = "latitude";
    private static final String COLUMN_PROXY_PROFILE_LONGITUDE = "longitude";

    /** One service per property engine, so each engine's pending QR codes are sent once. */
    private static final Map<String, CloudSyncService> RUNNING = new ConcurrentHashMap<>();

    /**
     * Starts the cloud sync of {@code syncManager}'s property unless it is already running for that engine. Called once the
     * engine's connection has started: by {@code SyncManagerService} (a property downloaded or opened) and by
     * {@code SyncManagerRepository} (every downloaded property on app start). An engine that is closed stops its loop on
     * its own ({@code continueQRDataSync} turns false).
     */
    public static synchronized void ensureRunning(SyncManager syncManager) {
        if (syncManager == null || syncManager.propertyId == null) {
            return;
        }
        CloudSyncService current = RUNNING.get(syncManager.propertyId);
        if (current != null && current.syncManager == syncManager
                && current.qrDataSyncThread != null && current.qrDataSyncThread.isAlive()) {
            return;
        }
        RUNNING.put(syncManager.propertyId, new CloudSyncService(syncManager));
    }

    /** The server's address with one trailing slash ({@code SyncManager.cloudUrl} is the sign-in host, without one). */
    private static String cloudBase() {
        String url = SyncManager.cloudUrl == null ? "" : SyncManager.cloudUrl;
        return url.endsWith("/") ? url : url + "/";
    }

    /** The property this engine syncs: the vdms id for admin, the property id for asset data collection. */
    private String scopeId() {
        return OfflineFeatures.ASSET_DATA_COLLECTION.equals(syncManager.getFeature()) ? syncManager.propertyId : syncManager.getVdmsID();
    }

    public Thread getQrDataSyncThread() {
        return qrDataSyncThread;
    }

    public Thread getNfcDataSyncThread() {
        return nfcDataSyncThread;
    }

    public Thread getBarCodeSyncThread() {
        return barCodeSyncThread;
    }

    public CloudSyncService(SyncManager syncManager) {
        this.syncManager = syncManager;

        getQRData();

        if (syncManager.feature.equals(OfflineFeatures.ADMIN)) {
            getNfcData();
            getBarCodeData();
            getProxyProfileData();
        }
    }

    // Syncing QR Data
    public void getQRData() {
        Log.d(TAG, "Task for fetching QR data from cloud is running");

        qrDataSyncThread = new Thread(() -> {
            while (syncManager.continueQRDataSync) {
                try {
                    boolean qrPushSuccess = pushChangedQRCodeRecords();
                    if (qrPushSuccess && syncManager.getFeature().equals(OfflineFeatures.ADMIN)) {
                        getQRCodeRecords();
                    }
                    boolean clientQrPushSuccess = pushChangedClientQRCodeRecords();
                    if (clientQrPushSuccess && syncManager.getFeature().equals(OfflineFeatures.ADMIN)) {
                        getClientQRCodeRecords();
                    }
                } catch (Throwable t) {
                    CrashlyticsLogger.INSTANCE.recordException(t);
                } finally {
                    try {
                        Thread.sleep(CLOUD_SYNC_PERIOD);
                    } catch (Throwable t) {
                        CrashlyticsLogger.INSTANCE.recordException(t);
                    }
                }
            }
        });

        qrDataSyncThread.start();
    }

    /**
     * Pushes locally changed QR code records (changes = "1") to the cloud.
     * Mirrors the iOS pushPendingQrCodeData flow: fetch pending rows, POST each one
     * as multipart/form-data, and on a successful response clear the pending flag.
     *
     * @return true if there were no pending records or all of them were pushed
     * successfully; false if any push failed (so the caller can skip the
     * pull this cycle and retry next time).
     */
    private boolean pushChangedQRCodeRecords() {
        SQLiteDatabase db = GlobalConfigDatabaseHelper.initializeInstance(context).globalDB;

        String query;
        String[] selectionArgs = null;

        if (syncManager.getFeature().equals(OfflineFeatures.ADMIN)) {
            query = "SELECT * FROM " + GlobalConfigDatabaseHelper.TABLE_NAME_QR_CODE +
                    " WHERE " + GlobalConfigDatabaseHelper.QR_CODE_COLUMN_CHANGES + " IS NOT NULL" +
                    " AND " + GlobalConfigDatabaseHelper.QR_CODE_COLUMN_CHANGES + " != '0'" +
                    " AND " + GlobalConfigDatabaseHelper.QR_CODE_COLUMN_VDMS_ID + " = ?";

            selectionArgs = new String[]{syncManager.getVdmsID()};

        } else {
            // Sclera Go's offline tagging (QrTagStore) marks a waiting code with sync_status = '1', not the old app's changes = '1'.
            query = "SELECT * FROM " + GlobalConfigDatabaseHelper.TABLE_NAME_QR_CODE +
                    " WHERE " + GlobalConfigDatabaseHelper.QR_CODE_COLUMN_SYNC_STATUS + " = '1'" +
                    " AND " + GlobalConfigDatabaseHelper.QR_CODE_COLUMN_ADC_CHECK + " = 1" +
                    // Sclera Go runs one engine per property: each sends only its own property's codes (vdms_id holds it).
                    " AND " + GlobalConfigDatabaseHelper.QR_CODE_COLUMN_VDMS_ID + " = ?";

            selectionArgs = new String[]{scopeId()};
        }

        boolean allSuccess = true;

        try (Cursor cursor = db.rawQuery(query, selectionArgs)) {
            if (cursor == null || !cursor.moveToFirst()) {
                // No pending records — nothing to push, safe to pull
                return true;
            }

            do {
                String id = getStringFromCursor(cursor, GlobalConfigDatabaseHelper.QR_CODE_COLUMN_ID);
                String deviceId = getStringFromCursor(cursor, GlobalConfigDatabaseHelper.QR_CODE_COLUMN_DEVICE_ID);
                String vdmsId = getStringFromCursor(cursor, GlobalConfigDatabaseHelper.QR_CODE_COLUMN_VDMS_ID);
                String locationId = getStringFromCursor(cursor, GlobalConfigDatabaseHelper.QR_CODE_COLUMN_LOCATION_ID);
                String direction = getStringFromCursor(cursor, GlobalConfigDatabaseHelper.QR_CODE_COLUMN_DIRECTION);
                String updatedBy = getStringFromCursor(cursor, GlobalConfigDatabaseHelper.QR_CODE_COLUMN_UPDATED_BY);
                String imageBase64 = getStringFromCursor(cursor, GlobalConfigDatabaseHelper.QR_CODE_COLUMN_IMAGE);
                String updatedTime = getStringFromCursor(cursor, GlobalConfigDatabaseHelper.QR_CODE_COLUMN_UPDATED_TIME);
                String changesJson = getStringFromCursor(cursor, GlobalConfigDatabaseHelper.QR_CODE_COLUMN_CHANGES);

                int movableIndex = cursor.getColumnIndex(GlobalConfigDatabaseHelper.QR_CODE_COLUMN_MOVABLE);
                Integer movable = (movableIndex != -1 && !cursor.isNull(movableIndex)) ? cursor.getInt(movableIndex) : null;

                boolean sent = sendQRCodeToServer(id, deviceId, vdmsId, locationId, movable, direction, updatedBy, imageBase64, updatedTime, changesJson);
                if (!sent) {
                    allSuccess = false;
                    CrashlyticsLogger.INSTANCE.recordException(id, "", new Throwable("Push failed for QR record  + " + id + "+  — will retry next cycle"));
                }
            } while (cursor.moveToNext());

        } catch (Throwable t) {
            CrashlyticsLogger.INSTANCE.recordException("Failed to fetch/push pending QR codes", t);
            return false;
        }

        return allSuccess;
    }

    private String getStringFromCursor(@NonNull Cursor cursor, String columnName) {
        int index = cursor.getColumnIndex(columnName);
        if (index == -1 || cursor.isNull(index)) {
            return null;
        }
        return cursor.getString(index);
    }

    /**
     * Adds a millisecond-epoch timestamp to the body, as a numeric value when the stored
     * value parses as a long and otherwise as the raw string. Null/blank values are skipped.
     */
    private static void putTimestamp(JSONObject json, String key, String value) throws JSONException {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        try {
            json.put(key, Long.parseLong(value.trim()));
        } catch (Throwable t) {
            CrashlyticsLogger.INSTANCE.recordException(t);
            json.put(key, value);
        }
    }

    /**
     * Sends a single QR code record to the cloud as multipart/form-data, mirroring
     * the iOS sendQrCodeToServer implementation. On a successful (2xx) response the
     * local row's changes flag is cleared to "0" so it is no longer treated as pending.
     */
    private boolean sendQRCodeToServer(String id, String deviceId, String vdmsId, String locationId,
                                       Integer movable, String direction, String updatedBy, String imageBase64,
                                       String updatedTime, String changesJson) {
        boolean isAdc = OfflineFeatures.ASSET_DATA_COLLECTION.equals(syncManager.getFeature());
        try {
            // Build the JSON "body" part
            JSONObject bodyJson = new JSONObject();
            if (isAdc) {
                // ADC DTO
                if (deviceId != null) bodyJson.put("deviceId", deviceId);
                if (updatedBy != null) bodyJson.put("updatedBy", updatedBy);
                if (movable != null) bodyJson.put("movable", movable);
                if (direction != null) bodyJson.put("direction", direction);
                if (vdmsId != null) bodyJson.put("propertyId", vdmsId);  // for adc we are putting the propertyId in vdmsId column
            } else {
                bodyJson.put("id", id);
                if (deviceId != null) bodyJson.put("deviceId", deviceId);
                if (vdmsId != null) bodyJson.put("vdmsId", vdmsId);
                if (locationId != null) bodyJson.put("locationId", locationId);
                if (movable != null) bodyJson.put("movable", movable);
                if (direction != null) bodyJson.put("direction", direction);
                putTimestamp(bodyJson, "updatedTime", updatedTime);
            }

            MultipartBody.Builder multipartBuilder = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM);

            // image field (if present) — stored locally as base64
            if (imageBase64 != null && !imageBase64.isEmpty()) {
                try {
                    byte[] imageBytes = Base64.decode(imageBase64, Base64.DEFAULT);
                    // Sclera Go's tag photo is a JPEG (the online write sends it as qr.jpg, image/jpeg).
                    multipartBuilder.addFormDataPart("image", "image.jpg",
                            RequestBody.create(imageBytes, MediaType.parse("image/jpeg")));
                } catch (Throwable t) {
                    CrashlyticsLogger.INSTANCE.recordException(id, "Invalid base64 image for QR record", t);
                }
            }

            // body field (JSON string)
            multipartBuilder.addFormDataPart("body", bodyJson.toString());

            RequestBody body = multipartBuilder.build();

            String vdmsParam = vdmsId != null ? "&vdms_id=" + Uri.encode(vdmsId) : "";
            Request baseRequest;
            if (isAdc) {
                // The code's id comes from a scan: encoded as one path segment. The query starts with vdms_id (no "?&").
                String urlString = cloudBase() + "vdms/api/qrCode/" + Uri.encode(id) + "/updateQrcodeById"
                        + (vdmsId != null ? "?vdms_id=" + Uri.encode(vdmsId) : "");
                baseRequest = SyncUtils.createPUTRequest(urlString, body);
            } else {
                String urlString = cloudBase() + "vdms/api/touchscreen/updateQrCode?loggedInUser="
                        + (updatedBy != null ? updatedBy : "") + vdmsParam;
                baseRequest = SyncUtils.createPOSTRequest(urlString, body, syncManager.getFeature(), syncManager);
            }

            Request request = baseRequest.newBuilder()
                    .addHeader("offline-sync", "true")
                    .build();

            try (Response response = syncManager.getClient().newCall(request).execute()) {
                // ADC reports a rejected QR as status 826 in the body (HTTP code may still be 2xx)
                JSONObject responseJson = isAdc && response.body() != null
                        ? parseJsonOrNull(response.body().string())
                        : null;
                boolean rejected = response.code() == 826
                        || (responseJson != null && responseJson.optInt("status") == 826);

                if (rejected && (isAdc || OfflineFeatures.ADMIN.equals(syncManager.getFeature()))) {
                    // Cloud rejected this QR (826): roll back the local changes so it is no longer
                    // pushed. Treated as handled so it doesn't block the pull cycle.
                    CrashlyticsLogger.INSTANCE.recordException(id, "Server rejected QR record with 826 — rolling back local changes", new Throwable());
                    if (isAdc) {
                        handleRejectedAdcQRCode(id, deviceId, changesJson, responseJson);
                    } else {
                        handleRejectedQRCode(id, deviceId, vdmsId, changesJson);
                    }
                    return true;
                }
                if (response.isSuccessful()) {
                    markQRCodeAsSynced(id);
                    if(isAdc) {
                        // A reply without a JSON body leaves the flag as it was (the old code crashed here).
                        updateTagStatus(responseJson != null ? responseJson.toString() : null, "qr_code_data", deviceId, false, null);
                    }
                    Log.d(TAG, "QR code synced successfully");
                    return true;
                }
                CrashlyticsLogger.INSTANCE.recordException(id, "Server returned " + response.code(), new Throwable());
                return false;
            }
        } catch (Throwable t) {
            CrashlyticsLogger.INSTANCE.recordException(id, "sending qr code to server failed", t);
            return false;
        }
    }

    private void markQRCodeAsSynced(String id) {
        try {
            ContentValues values = new ContentValues();
            // Synced: clear the pending flag AND drop the tag snapshot. Null matches the pull path's
            // representation of a synced row (see processQRCodeRecords).
            values.putNull(GlobalConfigDatabaseHelper.QR_CODE_COLUMN_CHANGES);
            // Sclera Go marks a waiting tag with sync_status = '1' (QrTagStore): cleared once sent. The photo is kept only until then.
            values.putNull(GlobalConfigDatabaseHelper.QR_CODE_COLUMN_SYNC_STATUS);
            values.putNull(GlobalConfigDatabaseHelper.QR_CODE_COLUMN_IMAGE);

            GlobalConfigDatabaseHelper.initializeInstance(context).globalDB.update(
                    GlobalConfigDatabaseHelper.TABLE_NAME_QR_CODE,
                    values,
                    GlobalConfigDatabaseHelper.QR_CODE_COLUMN_ID + " = ?",
                    new String[]{id}
            );
        } catch (Throwable t) {
            CrashlyticsLogger.INSTANCE.recordException(id, "Failed to clear changes flag for QR record ", t);
        }
    }

    /**
     * Handles a QR record the cloud rejected with HTTP 826: undo the onboard/tag status changes that
     * were applied locally when the QR was tagged, then revert the local row to its last-synced state.
     * {@code changesJson} is the full snapshot of the row as of the last successful sync.
     *
     * <p>When a snapshot is present (an edit of an existing QR), every column is restored from it and
     * the pending flag is cleared — the row must be kept because the cloud copy predates the sync
     * cursor and would not be restored by a subsequent pull. When there is no snapshot (a fresh
     * offline insert the cloud never had), the row is deleted instead.
     */
    private void handleRejectedQRCode(String id, String deviceId, String vdmsId, String changesJson) {
        try {
            QRCodeDTO qrCodeDTO = new QRCodeDTO();
            qrCodeDTO.setId(id);
            qrCodeDTO.setDevice_id(deviceId);
            qrCodeDTO.setVdms_id(vdmsId);
            undoDeviceOnboardStatusForQrSync(vdmsId, qrCodeDTO, extractPreviousDeviceId(changesJson));
        } catch (Throwable t) {
            CrashlyticsLogger.INSTANCE.recordException(id, "Failed to undo onboard status for rejected QR record ", t);
        }
        revertQRCodeRow(id, changesJson);
    }

    /**
     * Handles an ADC QR record the cloud rejected with status 826 ("Invalid QrCode"): revert the
     * local QR row to its last-synced state (or delete it if it was a fresh offline insert), and when
     * the response {@code data} is 0 — no QR left on the collection — mark the collection whose id is
     * the QR's {@code deviceId} as untagged.
     */
    private void handleRejectedAdcQRCode(String id, String deviceId, String changesJson, JSONObject responseJson) {
        revertQRCodeRow(id, changesJson);
        // The rejection may carry no JSON body (HTTP 826): the old code crashed on it.
        updateTagStatus(responseJson != null ? responseJson.toString() : null, "qr_code_data", deviceId, false, null);
        // As this method's description says: data = 0 means no QR is left on the asset, so it is untagged.
        untagAdcCollectionIfNoQrLeft(id, deviceId, responseJson);
    }

    /** {@link #handleRejectedAdcQRCode} for a client QR code, whose row is in client_qr_code_table. */
    private void handleRejectedAdcClientQRCode(String id, String deviceId, String changesJson, JSONObject responseJson) {
        revertClientQRCodeRow(id, changesJson);
        updateTagStatus(responseJson != null ? responseJson.toString() : null, "qr_code_data", deviceId, false, null);
        untagAdcCollectionIfNoQrLeft(id, deviceId, responseJson);
    }

    /**
     * When a rejected ADC QR's response {@code data} is 0 (no QR left on the collection), marks the
     * collection whose id is the QR's {@code deviceId} as untagged.
     */
    private void untagAdcCollectionIfNoQrLeft(String id, String deviceId, JSONObject responseJson) {
        if (deviceId == null || responseJson == null || responseJson.optInt("data", -1) != 0) {
            return;
        }
        try {
            updateCollectionTagStatus(deviceId, "qr_code_data", "untagged", SyncManager.getUser());
            Log.d(TAG, "Marked the asset untagged after its QR was rejected");
        } catch (Throwable t) {
            CrashlyticsLogger.INSTANCE.recordException(id, "Failed to untag collection " + deviceId + " for rejected QR", t);
        }
    }

    /**
     * The old app's {@code TaggingService.updateTagStatus}: sets the asset's tag flag from the counts the server answered
     * with ({@code data.newCollectionIdCount}, and {@code data.existingCollectionIdCount} for the asset the tag came from).
     */
    private void updateTagStatus(String apiResponse, String dbColumn, String collectionId,
                                 boolean retag, String existingCollectionId) {
        try {
            if (apiResponse == null) return;
            JsonNode dataNode = SyncUtils.objectMapper.readTree(apiResponse).path("data");
            if (!dataNode.isObject()) return;

            int newCount = dataNode.path("newCollectionIdCount").asInt(0);
            int existingCount = dataNode.path("existingCollectionIdCount").asInt(0);

            String tagStatus = newCount > 0 ? "tagged" : "untagged";
            updateCollectionTagStatus(collectionId, dbColumn, tagStatus, SyncManager.getUser());

            if (retag && existingCollectionId != null && !existingCollectionId.isEmpty()
                    && !existingCollectionId.equals(collectionId)) {
                String existingTagStatus = existingCount > 0 ? "tagged" : "untagged";
                updateCollectionTagStatus(existingCollectionId, dbColumn, existingTagStatus, SyncManager.getUser());
            }
        } catch (Exception e) {
            CrashlyticsLogger.INSTANCE.recordException("updateTagStatus", e);
        }
    }

    /**
     * The old app's {@code CollectionRepository.updateTagStatus}, on Sclera Go's data: the old {@code collection} table's
     * {@code qr_code_data} / {@code bar_code_data} / {@code nfc_data} are the {@code assets} table's {@code qr_code_tag} /
     * {@code barcode_tag} / {@code nfc_tag} here (as {@code AdcAssetTagFlagStore} writes them), in this engine's own property
     * database. Sclera Go keeps no {@code *_updated_by} column for them, so {@code user} is not stored.
     */
    private void updateCollectionTagStatus(String id, String dataColumn, String status, String user) {
        if (id == null || dataColumn == null) {
            return;
        }
        String column;
        switch (dataColumn) {
            case "qr_code_data":
                column = "qr_code_tag";
                break;
            case "bar_code_data":
                column = "barcode_tag";
                break;
            case "nfc_data":
                column = "nfc_tag";
                break;
            default:
                return;
        }
        try {
            SQLiteDatabase db = syncManager.getDbConnection();
            if (db == null || !db.isOpen()) {
                return;
            }
            ContentValues values = new ContentValues();
            values.put(column, status);
            int rows = db.update("assets", values, "id = ?", new String[]{id});
            if (rows > 0) {
                // The screens showing this property's assets reload.
                SyncUtils.notifyTablesChanged(syncManager.propertyId, Collections.singleton("assets"));
            }
        } catch (Exception e) {
            CrashlyticsLogger.INSTANCE.recordException("updateCollectionTagStatus", e);
        }
    }

    /**
     * The old app's {@code DeviceService.undoDeviceOnboardStatusForQrSync} (admin only) rolls back inspection's device
     * onboard status. Sclera Go has no inspection devices, so there is nothing to roll back.
     */
    private static void undoDeviceOnboardStatusForQrSync(String vdmsId, QRCodeDTO qrCodeDTO, String previousDeviceId) {
        Log.d(LOG_TAG, "No device onboard status to roll back");
    }

    /**
     * Stands in for the old app's {@code CrashlyticsLogger}. Sclera Go reports crashes only through
     * {@code domain/telemetry} with a short code, never a message, id or URL, so this logs the exception's type alone
     * (and R8 strips it from release builds).
     */
    private static final class CrashlyticsLogger {
        static final CrashlyticsLogger INSTANCE = new CrashlyticsLogger();

        void recordException(Throwable t) {
            log(t);
        }

        void recordException(String message, Throwable t) {
            log(t);
        }

        void recordException(String id, String message, Throwable t) {
            log(t);
        }

        private static void log(Throwable t) {
            Log.w(LOG_TAG, "cloud_sync_failed " + (t == null ? "" : t.getClass().getSimpleName()));
        }
    }

    /** Restores the row from its last-synced snapshot, or deletes it if it was a fresh offline insert. */
    private void revertQRCodeRow(String id, String changesJson) {
        ContentValues snapshot = snapshotToValues(changesJson);
        if (snapshot != null) {
            restoreQRCodeFromSnapshot(id, snapshot);
        } else {
            deleteQRCodeRecord(id);
        }
    }

    private JSONObject parseJsonOrNull(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return new JSONObject(json);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Restores a rejected QR row to its last-synced state from {@code snapshot} and clears the pending
     * flag, so the row matches the cloud copy and is no longer pushed.
     */
    private void restoreQRCodeFromSnapshot(String id, ContentValues snapshot) {
        try {
            snapshot.putNull(GlobalConfigDatabaseHelper.QR_CODE_COLUMN_CHANGES);
            GlobalConfigDatabaseHelper.initializeInstance(context).globalDB.update(
                    GlobalConfigDatabaseHelper.TABLE_NAME_QR_CODE,
                    snapshot,
                    GlobalConfigDatabaseHelper.QR_CODE_COLUMN_ID + " = ?",
                    new String[]{id}
            );
            Log.d(TAG, "Restored QR record " + id + " to last-synced state");
        } catch (Throwable t) {
            CrashlyticsLogger.INSTANCE.recordException(id, "Failed to restore QR record from snapshot", t);
        }
    }

    /**
     * Reads the previous {@code device_id} from the full-row snapshot stored in {@code changes}, so
     * the onboard rollback can re-tag the device the QR was on as of the last successful sync. Returns
     * null when there is no snapshot (fresh insert), the previous row was untagged, or the JSON is
     * malformed — in which case the rollback only untags the rejected device.
     */
    private String extractPreviousDeviceId(String changesJson) {
        if (changesJson == null || changesJson.isEmpty()) {
            return null;
        }
        try {
            String prev = new JSONObject(changesJson)
                    .optString(GlobalConfigDatabaseHelper.QR_CODE_COLUMN_DEVICE_ID, null);
            return (prev == null || prev.isEmpty()) ? null : prev;
        } catch (Throwable t) {
            CrashlyticsLogger.INSTANCE.recordException("Malformed QR changes snapshot: " + changesJson, t);
            return null;
        }
    }

    /**
     * Converts a full-row {@code changes} snapshot into {@link ContentValues} for restore: each
     * column key is written back, with JSON null mapped to a SQL NULL. Returns null when the snapshot
     * is blank, {@code "{}"} (a fresh insert with nothing to restore), or malformed. The {@code
     * changes} flag column is skipped here and set explicitly by the caller.
     */
    private ContentValues snapshotToValues(String changesJson) {
        if (changesJson == null || changesJson.isEmpty()) {
            return null;
        }
        try {
            JSONObject obj = new JSONObject(changesJson);
            ContentValues values = new ContentValues();
            java.util.Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (GlobalConfigDatabaseHelper.QR_CODE_COLUMN_CHANGES.equals(key)) {
                    continue;
                }
                if (obj.isNull(key)) {
                    values.putNull(key);
                } else {
                    values.put(key, obj.getString(key));
                }
            }
            return values.size() == 0 ? null : values;
        } catch (Throwable t) {
            CrashlyticsLogger.INSTANCE.recordException("Malformed changes snapshot, cannot restore: " + changesJson, t);
            return null;
        }
    }

    private void deleteQRCodeRecord(String id) {
        try {
            GlobalConfigDatabaseHelper.initializeInstance(context).globalDB.delete(
                    GlobalConfigDatabaseHelper.TABLE_NAME_QR_CODE,
                    GlobalConfigDatabaseHelper.QR_CODE_COLUMN_ID + " = ?",
                    new String[]{id}
            );
            Log.d(TAG, "Deleted rejected QR record " + id);
        } catch (Throwable t) {
            CrashlyticsLogger.INSTANCE.recordException(id, "Failed to delete QR record", t);
        }
    }

    private void getQRCodeRecords() {
        try {
            long lastSyncQRCodeTimestamp = getLastSyncQRCodeTimestamp();
            boolean hasMoreRecords = true;
            int page = 1;

            while (hasMoreRecords) {
                String urlString = cloudBase() + "vdms/api/org/" + syncManager.getOrgId() + "/email/" + SyncManager.user + "/vdms/" + syncManager.getVdmsID() + "/qrCodeRecords?loggedInUser=" + SyncManager.user + "&lastSyncTime=" + lastSyncQRCodeTimestamp + "&pageNo=" + page + "&pageSize=" + pageSize;
                Request request = SyncUtils.createGETRequest(urlString, syncManager.getFeature(), syncManager);
                try (Response response = syncManager.getClient().newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        assert response.body() != null;
                        JSONObject responseJson = new JSONObject(response.body().string());

                        JSONArray qrCodeRecords = responseJson.getJSONArray("data");
                        if (qrCodeRecords.length() == 0) {
                            hasMoreRecords = false;
                            syncManager.initialCloudSyncStatus |= (1 << 0);
                            if (syncManager.getDbConnection() != null) {
                                updateLastSyncQRCodeTimestamp();
                            }
                        } else {
                            processQRCodeRecords(qrCodeRecords);
                            page = page + 1;
                        }
                    } else {
                        Log.e(TAG, "GET request failed. Response Code: " + response.code());
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Exception during HTTP GET request" + t);
            CrashlyticsLogger.INSTANCE.recordException(t);
        }
    }

    private void updateLastSyncQRCodeTimestamp() {
        String maxUpdatedAt = null;
        BigInteger maxCreatedAt = null;
        String maxTimestamp = null;

        String queryUpdatedAt = "SELECT MAX(" + GlobalConfigDatabaseHelper.QR_CODE_COLUMN_UPDATED_TIME + ") FROM " + GlobalConfigDatabaseHelper.TABLE_NAME_QR_CODE;
        String queryCreatedAt = "SELECT MAX(" + GlobalConfigDatabaseHelper.QR_CODE_COLUMN_CREATION_TIME + ") FROM " + GlobalConfigDatabaseHelper.TABLE_NAME_QR_CODE;

        try {
            try (Cursor cursor = GlobalConfigDatabaseHelper.initializeInstance(context).globalDB.rawQuery(queryUpdatedAt, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    maxUpdatedAt = cursor.getString(0);
                }
            }

            try (Cursor cursor = GlobalConfigDatabaseHelper.initializeInstance(context).globalDB.rawQuery(queryCreatedAt, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    maxCreatedAt = BigInteger.valueOf(cursor.getLong(0));
                }
            }

            if (maxUpdatedAt != null && maxCreatedAt != null) {
                String maxCreatedAtStr = maxCreatedAt.toString();
                maxTimestamp = maxUpdatedAt.compareTo(maxCreatedAtStr) > 0 ? maxUpdatedAt : maxCreatedAtStr;
            } else if (maxUpdatedAt != null) {
                maxTimestamp = maxUpdatedAt;
            } else if (maxCreatedAt != null) {
                maxTimestamp = maxCreatedAt.toString();
            }

        } catch (Throwable t) {
            CrashlyticsLogger.INSTANCE.recordException("Error fetching max timestamp: ", t);
        }

        if (maxTimestamp != null) {
            long incrementedTimestamp = Long.parseLong(maxTimestamp) + 1;

            ContentValues values = new ContentValues();
            values.put(globalTimestampQRCodeLastSyncTimestamp, incrementedTimestamp);

            try {
                int rowsAffected = syncManager.getDbConnection().update(
                        timeStampTable,
                        values,
                        "id = ?",
                        new String[]{"1"}
                );
                if (rowsAffected == 0) {
                    System.out.println("No rows updated in the global_timestamp table.");
                }
            } catch (Throwable t) {
                CrashlyticsLogger.INSTANCE.recordException("Error updating last sync timestamp: ", t);
            }
        } else {
            System.out.println("No timestamp found to update.");
        }
    }

    private long getLastSyncQRCodeTimestamp() {
        long lastSyncQRCodeTimestamp = 0;

        if (syncManager.getDbConnection() != null) {
            String query = "SELECT " + globalTimestampQRCodeLastSyncTimestamp +
                    " FROM " + timeStampTable +
                    " WHERE " + globalTimestampId + " = '1'";

            try (Cursor cursor = syncManager.getDbConnection().rawQuery(query, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    lastSyncQRCodeTimestamp = cursor.getLong(0);
                }
            } catch (Throwable t) {
                CrashlyticsLogger.INSTANCE.recordException("Error retrieving last sync timestamp: ", t);
            }
        }

        return lastSyncQRCodeTimestamp;
    }

    private void processQRCodeRecords(@NonNull JSONArray qrCodeRecords) throws JsonProcessingException {
        Set<QRCodeDTO> qrCodeDTOS = SyncUtils.objectMapper.readValue(qrCodeRecords.toString(), new TypeReference<Set<QRCodeDTO>>() {
        });

        for (QRCodeDTO qrCodeDTO : qrCodeDTOS) {
            if (qrCodeDTO.getCreation_time() != null) {
                try {
                    BigInteger creationTime = new BigInteger(qrCodeDTO.getCreation_time().toString());
                    qrCodeDTO.setCreation_time(creationTime);
                } catch (Throwable t) {
                    CrashlyticsLogger.INSTANCE.recordException(t);
                    System.out.println(t.getMessage());
                }
            }

            ContentValues values = getContentValues(qrCodeDTO);
            GlobalConfigDatabaseHelper.initializeInstance(context).globalDB.insertWithOnConflict(GlobalConfigDatabaseHelper.TABLE_NAME_QR_CODE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    @NonNull
    private ContentValues getContentValues(@NonNull ClientBarCodeDTO clientBarCodeDTO) {
        ContentValues values = new ContentValues();
        if (clientBarCodeDTO.getId() != null) {
            values.put(GlobalConfigDatabaseHelper.CLIENT_BAR_CODE_RECORDS_COLUMN_ID, clientBarCodeDTO.getId());
        }
        if (clientBarCodeDTO.getClient_bar_code_id() != null) {
            values.put(GlobalConfigDatabaseHelper.CLIENT_BAR_CODE_RECORDS_COLUMN_CLIENT_BAR_CODE_ID, clientBarCodeDTO.getClient_bar_code_id());
        }
        if (clientBarCodeDTO.getDevice_id() != null) {
            values.put(GlobalConfigDatabaseHelper.CLIENT_BAR_CODE_RECORDS_COLUMN_DEVICE_ID, clientBarCodeDTO.getDevice_id());
        }
        if (clientBarCodeDTO.getVdms_id() != null) {
            values.put(GlobalConfigDatabaseHelper.CLIENT_BAR_CODE_RECORDS_COLUMN_VDMS_ID, clientBarCodeDTO.getVdms_id());
        }
        if (clientBarCodeDTO.getAdded_at() != null) {
            values.put(GlobalConfigDatabaseHelper.CLIENT_BAR_CODE_RECORDS_COLUMN_ADDED_AT, clientBarCodeDTO.getAdded_at());
        }
        if (clientBarCodeDTO.getAdded_by() != null) {
            values.put(GlobalConfigDatabaseHelper.CLIENT_BAR_CODE_RECORDS_COLUMN_ADDED_BY, clientBarCodeDTO.getAdded_by());
        }
        if (clientBarCodeDTO.getBatch_id() != null) {
            values.put(GlobalConfigDatabaseHelper.CLIENT_BAR_CODE_RECORDS_COLUMN_BATCH_ID, clientBarCodeDTO.getBatch_id());
        }
        if (clientBarCodeDTO.getUpdated_time() != null) {
            values.put(GlobalConfigDatabaseHelper.CLIENT_BAR_CODE_RECORDS_COLUMN_UPDATED_TIME, clientBarCodeDTO.getUpdated_time().toString());
        }
        if (clientBarCodeDTO.getUpdated_by() != null) {
            values.put(GlobalConfigDatabaseHelper.CLIENT_BAR_CODE_RECORDS_COLUMN_UPDATED_BY, clientBarCodeDTO.getUpdated_by());
        }
        if (clientBarCodeDTO.getLocation_id() != null) {
            values.put(GlobalConfigDatabaseHelper.CLIENT_BAR_CODE_RECORDS_COLUMN_LOCATION_ID, clientBarCodeDTO.getLocation_id());
        }
        return values;
    }

    private static @NonNull ContentValues getContentValues(@NonNull QRCodeDTO qrCodeDTO) {
        ContentValues values = new ContentValues();

        if (qrCodeDTO.getId() != null) {
            values.put(GlobalConfigDatabaseHelper.QR_CODE_COLUMN_ID, qrCodeDTO.getId());
        }

        if (qrCodeDTO.getDevice_id() != null) {
            values.put(GlobalConfigDatabaseHelper.QR_CODE_COLUMN_DEVICE_ID, qrCodeDTO.getDevice_id());
        }

        if (qrCodeDTO.getLocation_id() != null) {
            values.put(GlobalConfigDatabaseHelper.QR_CODE_COLUMN_LOCATION_ID, qrCodeDTO.getLocation_id());
        }

        if (qrCodeDTO.getVdms_id() != null) {
            values.put(GlobalConfigDatabaseHelper.QR_CODE_COLUMN_VDMS_ID, qrCodeDTO.getVdms_id());
        }

        if (qrCodeDTO.getQr_code_link() != null) {
            values.put(GlobalConfigDatabaseHelper.QR_CODE_COLUMN_QR_CODE_LINK, qrCodeDTO.getQr_code_link());
        }

        if (qrCodeDTO.getUpdated_time() != null) {
            values.put(GlobalConfigDatabaseHelper.QR_CODE_COLUMN_UPDATED_TIME, qrCodeDTO.getUpdated_time());
        }

        if (qrCodeDTO.getUpdated_by() != null) {
            values.put(GlobalConfigDatabaseHelper.QR_CODE_COLUMN_UPDATED_BY, qrCodeDTO.getUpdated_by());
        }

        if (qrCodeDTO.getCreation_time() != null) {
            values.put(GlobalConfigDatabaseHelper.QR_CODE_COLUMN_CREATION_TIME, qrCodeDTO.getCreation_time().toString());
        }

        if (qrCodeDTO.getMovable() != null) {
            values.put(GlobalConfigDatabaseHelper.QR_CODE_COLUMN_MOVABLE, qrCodeDTO.getMovable());
        }
        if (qrCodeDTO.getadcQrCodeCheck() != null) {
            values.put(GlobalConfigDatabaseHelper.QR_CODE_COLUMN_ADC_CHECK, qrCodeDTO.getadcQrCodeCheck());
        }


        if (qrCodeDTO.getDirection() != null) {
            values.put(GlobalConfigDatabaseHelper.QR_CODE_COLUMN_DIRECTION, qrCodeDTO.getDirection());
        }
//
//        if (qrCodeDTO.getCreation_time() != null) {
//            values.put(GlobalConfigDatabaseHelper.QR_CODE_COLUMN_DIRECTION, qrCodeDTO.getDirection());
//        }

        return values;
    }


    /**
     * Pushes locally changed client QR code records (changes = "1") to the cloud.
     * Mirrors {@link #pushChangedQRCodeRecords()}: fetch pending rows, POST each one
     * as multipart/form-data, and on a successful response clear the pending flag.
     *
     * @return true if there were no pending records or all of them were pushed
     * successfully; false if any push failed (so the caller can skip the
     * pull this cycle and retry next time).
     */
    private boolean pushChangedClientQRCodeRecords() {
        SQLiteDatabase db = GlobalConfigDatabaseHelper.initializeInstance(context).globalDB;

        String query;
        String[] selectionArgs = null;

        if (syncManager.getFeature().equals(OfflineFeatures.ADMIN)) {
            query = "SELECT * FROM " + GlobalConfigDatabaseHelper.TABLE_NAME_CLIENT_QR_CODE +
                    " WHERE " + GlobalConfigDatabaseHelper.CLIENT_QR_CODE_COLUMN_CHANGES + " IS NOT NULL" +
                    " AND " + GlobalConfigDatabaseHelper.CLIENT_QR_CODE_COLUMN_CHANGES + " != '0'" +
                    " AND " + GlobalConfigDatabaseHelper.CLIENT_QR_CODE_COLUMN_VDMS_ID + " = ?";

            selectionArgs = new String[]{syncManager.getVdmsID()};
        } else {
            // Sclera Go's offline tagging (QrTagStore) marks a waiting code with sync_status = '1', not the old app's changes = '1'.
            query = "SELECT * FROM " + GlobalConfigDatabaseHelper.TABLE_NAME_CLIENT_QR_CODE +
                    " WHERE " + GlobalConfigDatabaseHelper.CLIENT_QR_CODE_COLUMN_SYNC_STATUS + " = '1'" +
                    " AND " + GlobalConfigDatabaseHelper.CLIENT_QR_CODE_COLUMN_ADC_CHECK + " = 1" +
                    // Sclera Go runs one engine per property: each sends only its own property's codes (vdms_id holds it).
                    " AND " + GlobalConfigDatabaseHelper.CLIENT_QR_CODE_COLUMN_VDMS_ID + " = ?";

            selectionArgs = new String[]{scopeId()};
        }
        boolean allSuccess = true;

        try (Cursor cursor = db.rawQuery(query, selectionArgs)) {
            if (cursor == null || !cursor.moveToFirst()) {
                // No pending records — nothing to push, safe to pull
                return true;
            }

            do {
                String id = getStringFromCursor(cursor, GlobalConfigDatabaseHelper.CLIENT_QR_CODE_COLUMN_ID);
                String clientQrCodeId = getStringFromCursor(cursor, GlobalConfigDatabaseHelper.CLIENT_QR_CODE_COLUMN_CLIENT_QR_CODE_ID);
                String deviceId = getStringFromCursor(cursor, GlobalConfigDatabaseHelper.CLIENT_QR_CODE_COLUMN_DEVICE_ID);
                String vdmsId = getStringFromCursor(cursor, GlobalConfigDatabaseHelper.CLIENT_QR_CODE_COLUMN_VDMS_ID);
                String locationId = getStringFromCursor(cursor, GlobalConfigDatabaseHelper.CLIENT_QR_CODE_COLUMN_LOCATION_ID);
                String direction = getStringFromCursor(cursor, GlobalConfigDatabaseHelper.CLIENT_QR_CODE_COLUMN_DIRECTION);
                String updatedBy = getStringFromCursor(cursor, GlobalConfigDatabaseHelper.CLIENT_QR_CODE_COLUMN_UPDATED_BY);
                String imageBase64 = getStringFromCursor(cursor, GlobalConfigDatabaseHelper.CLIENT_QR_CODE_COLUMN_IMAGE);
                String updatedTime = getStringFromCursor(cursor, GlobalConfigDatabaseHelper.CLIENT_QR_CODE_COLUMN_UPDATED_AT);
                String changesJson = getStringFromCursor(cursor, GlobalConfigDatabaseHelper.CLIENT_QR_CODE_COLUMN_CHANGES);

                int movableIndex = cursor.getColumnIndex(GlobalConfigDatabaseHelper.CLIENT_QR_CODE_COLUMN_MOVABLE);
                Integer movable = (movableIndex != -1 && !cursor.isNull(movableIndex)) ? cursor.getInt(movableIndex) : null;

                boolean sent = sendClientQRCodeToServer(id, clientQrCodeId, deviceId, vdmsId, locationId, movable, direction, updatedBy, imageBase64, updatedTime, changesJson);
                if (!sent) {
                    allSuccess = false;
                    Log.e(TAG, "Push failed for a client QR record, will retry next cycle");
                }
            } while (cursor.moveToNext());

        } catch (Throwable t) {
            CrashlyticsLogger.INSTANCE.recordException("Failed to fetch/push pending client QR codes", t);
            return false;
        }

        return allSuccess;
    }

    /**
     * Sends a single client QR code record to the cloud as multipart/form-data,
     * mirroring {@link #sendQRCodeToServer}. On a successful (2xx) response the local
     * row's changes flag is cleared to "0" so it is no longer treated as pending.
     */
    private boolean sendClientQRCodeToServer(String id, String clientQrCodeId, String deviceId, String vdmsId,
                                             String locationId, Integer movable, String direction,
                                             String updatedBy, String imageBase64, String updatedTime,
                                             String changesJson) {
        boolean isAdc = OfflineFeatures.ASSET_DATA_COLLECTION.equals(syncManager.getFeature());
        try {
            // Build the JSON "body" part
            JSONObject bodyJson = new JSONObject();

            if (isAdc) {
                // ADC DTO
                if (deviceId != null) bodyJson.put("deviceId", deviceId);
                if (updatedBy != null) bodyJson.put("updatedBy", updatedBy);
                if (movable != null) bodyJson.put("movable", movable);
                if (direction != null) bodyJson.put("direction", direction);
                if (vdmsId != null) bodyJson.put("propertyId", vdmsId); // for adc we are putting the propertyId in vdmsId column
            } else {
                if (clientQrCodeId != null) bodyJson.put("clientQrCodeId", clientQrCodeId);
                if (deviceId != null) bodyJson.put("deviceId", deviceId);
                if (vdmsId != null) bodyJson.put("vdmsId", vdmsId);
                if (locationId != null) bodyJson.put("locationId", locationId);
                if (movable != null) bodyJson.put("movable", movable);
                if (direction != null) bodyJson.put("direction", direction);
                // The offline-sync handler (offline-sync: true header below) requires the camelCase key
                // `updatedTime` — snake_case updated_time is rejected with status 700. See sendQRCodeToServer.
                putTimestamp(bodyJson, "updatedTime", updatedTime);
            }
            MultipartBody.Builder multipartBuilder = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM);

            // image field (if present) — stored locally as base64
            if (imageBase64 != null && !imageBase64.isEmpty()) {
                try {
                    byte[] imageBytes = Base64.decode(imageBase64, Base64.DEFAULT);
                    multipartBuilder.addFormDataPart("image", "image.png",
                            RequestBody.create(imageBytes, MediaType.parse("image/png")));
                } catch (Throwable t) {
                    CrashlyticsLogger.INSTANCE.recordException(id, "Invalid base64 image for client QR record ", t);
                }
            }

            // body field (JSON string)
            multipartBuilder.addFormDataPart("body", bodyJson.toString());

            RequestBody body = multipartBuilder.build();

            Request baseRequest;
            if (isAdc) {
                // The code's id comes from a scan: encoded as one path segment.
                String urlString = cloudBase() + "vdms/api/clientQrCode/" + Uri.encode(clientQrCodeId) + "/updateClientQrCodeById";
                baseRequest = SyncUtils.createPUTRequest(urlString, body);
            } else {
                String urlString = cloudBase() + "vdms/api/org/" + syncManager.getOrgId() + "/email/"
                        + SyncManager.user + "/clientQrCode?loggedInUser=" + (updatedBy != null ? updatedBy : "");
                baseRequest = SyncUtils.createPOSTRequest(urlString, body, syncManager.getFeature(), syncManager);
            }

            Request request = baseRequest.newBuilder()
                    .addHeader("offline-sync", "true")
                    .build();

            try (Response response = syncManager.getClient().newCall(request).execute()) {
                // ADC reports a rejected QR as status 826 in the body (HTTP code may still be 2xx)
                JSONObject responseJson = isAdc && response.body() != null
                        ? parseJsonOrNull(response.body().string())
                        : null;
                boolean rejected = response.code() == 826
                        || (responseJson != null && responseJson.optInt("status") == 826);

                if (rejected && (isAdc || OfflineFeatures.ADMIN.equals(syncManager.getFeature()))) {
                    // Cloud rejected this client QR (826): roll back the local changes so it is no
                    // longer pushed. Treated as handled so it doesn't block the pull cycle.
                    Log.e(TAG, "Server rejected a client QR record with 826, rolling back local changes");
                    if (isAdc) {
                        // The client code's row is in client_qr_code_table (the old code reverted qr_code_table instead).
                        handleRejectedAdcClientQRCode(id, deviceId, changesJson, responseJson);
                    } else {
                        handleRejectedClientQRCode(id, clientQrCodeId, deviceId, vdmsId, changesJson);
                    }
                    return true;
                }
                if (response.isSuccessful()) {
                    markClientQRCodeAsSynced(id);
                    if(isAdc) {
                        // A reply without a JSON body leaves the flag as it was (the old code crashed here).
                        updateTagStatus(responseJson != null ? responseJson.toString() : null, "qr_code_data", deviceId, false, null);
                    }
                    Log.d(TAG, "Client QR code synced successfully");
                    return true;
                }
                Log.e(TAG, "Server returned " + response.code() + " for a client QR record");
                return false;
            }
        } catch (Throwable t) {
            CrashlyticsLogger.INSTANCE.recordException(id, "Network error sending client QR code ", t);
            return false;
        }
    }

    private void markClientQRCodeAsSynced(String id) {
        try {
            ContentValues values = new ContentValues();
            // Synced: clear the pending flag AND drop the tag snapshot. Null matches the pull path's
            // representation of a synced row (see processClientQRCodeRecords).
            values.putNull(GlobalConfigDatabaseHelper.CLIENT_QR_CODE_COLUMN_CHANGES);
            // Sclera Go marks a waiting tag with sync_status = '1' (QrTagStore): cleared once sent. The photo is kept only until then.
            values.putNull(GlobalConfigDatabaseHelper.CLIENT_QR_CODE_COLUMN_SYNC_STATUS);
            values.putNull(GlobalConfigDatabaseHelper.CLIENT_QR_CODE_COLUMN_IMAGE);

            GlobalConfigDatabaseHelper.initializeInstance(context).globalDB.update(
                    GlobalConfigDatabaseHelper.TABLE_NAME_CLIENT_QR_CODE,
                    values,
                    GlobalConfigDatabaseHelper.CLIENT_QR_CODE_COLUMN_ID + " = ?",
                    new String[]{id}
            );
        } catch (Throwable t) {
            CrashlyticsLogger.INSTANCE.recordException(id, "Failed to clear changes flag for client QR record ", t);
        }
    }

    /**
     * Handles a client QR record the cloud rejected with HTTP 826: undo the onboard/tag status
     * changes that were applied locally when the QR was tagged, then revert the local row to its
     * last-synced state. Mirrors {@link #handleRejectedQRCode}: when a snapshot is present, restore
     * every column and clear the pending flag (the row must be kept — its cloud copy predates the
     * sync cursor and would not be re-pulled). When there is no snapshot, delete the row.
     */
    private void handleRejectedClientQRCode(String id, String clientQrCodeId, String deviceId, String vdmsId, String changesJson) {
        try {
            QRCodeDTO qrCodeDTO = new QRCodeDTO();
            // The onboard sync keys client QR codes by clientQrCodeId (see QrConfigurationService).
            qrCodeDTO.setId(clientQrCodeId);
            qrCodeDTO.setDevice_id(deviceId);
            qrCodeDTO.setVdms_id(vdmsId);
            undoDeviceOnboardStatusForQrSync(vdmsId, qrCodeDTO, extractPreviousDeviceId(changesJson));
        } catch (Throwable t) {
            CrashlyticsLogger.INSTANCE.recordException(id, "Failed to undo onboard status for rejected client QR record ", t);
        }
        revertClientQRCodeRow(id, changesJson);
    }

    /** Restores the client QR row from its last-synced snapshot, or deletes it if it was a fresh offline insert. */
    private void revertClientQRCodeRow(String id, String changesJson) {
        ContentValues snapshot = snapshotToValues(changesJson);
        if (snapshot != null) {
            restoreClientQRCodeFromSnapshot(id, snapshot);
        } else {
            deleteClientQRCodeRecord(id);
        }
    }

    /**
     * Restores a rejected client QR row to its last-synced state from {@code snapshot} and clears the
     * pending flag, so the row matches the cloud copy and is no longer pushed.
     */
    private void restoreClientQRCodeFromSnapshot(String id, ContentValues snapshot) {
        try {
            snapshot.putNull(GlobalConfigDatabaseHelper.CLIENT_QR_CODE_COLUMN_CHANGES);
            GlobalConfigDatabaseHelper.initializeInstance(context).globalDB.update(
                    GlobalConfigDatabaseHelper.TABLE_NAME_CLIENT_QR_CODE,
                    snapshot,
                    GlobalConfigDatabaseHelper.CLIENT_QR_CODE_COLUMN_ID + " = ?",
                    new String[]{id}
            );
            Log.d(TAG, "Restored client QR record " + id + " to last-synced state");
        } catch (Throwable t) {
            CrashlyticsLogger.INSTANCE.recordException(id, "Failed to restore client QR record from snapshot", t);
        }
    }

    private void deleteClientQRCodeRecord(String id) {
        try {
            GlobalConfigDatabaseHelper.initializeInstance(context).globalDB.delete(
                    GlobalConfigDatabaseHelper.TABLE_NAME_CLIENT_QR_CODE,
                    GlobalConfigDatabaseHelper.CLIENT_QR_CODE_COLUMN_ID + " = ?",
                    new String[]{id}
            );
            Log.d(TAG, "Deleted rejected client QR record " + id);
        } catch (Throwable t) {
            CrashlyticsLogger.INSTANCE.recordException(id, "Failed to delete client QR record ", t);
        }
    }

    private void getClientQRCodeRecords() {
        try {
            long lastSyncClientQRCodeTimestamp = getLastSyncClientQRCodeTimestamp();
            boolean hasMoreRecords = true;
            int page = 1;

            while (hasMoreRecords) {
                String urlString = cloudBase() + "vdms/api/org/" + syncManager.getOrgId() + "/email/" + SyncManager.user + "/vdms/" + syncManager.getVdmsID() + "/clientQrCodeRecords?loggedInUser=" + SyncManager.user + "&lastSyncTime=" + lastSyncClientQRCodeTimestamp + "&pageNo=" + page + "&pageSize=" + pageSize;
                Request request = SyncUtils.createGETRequest(urlString, syncManager.getFeature(), syncManager);
                try (Response response = syncManager.getClient().newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        assert response.body() != null;
                        JSONObject responseJson = new JSONObject(response.body().string());

                        JSONArray clientQrCodeRecords = responseJson.getJSONArray("data");
                        if (clientQrCodeRecords.length() == 0) {
                            hasMoreRecords = false;
                            syncManager.initialCloudSyncStatus |= (1 << 1);
                            if (syncManager.getDbConnection() != null) {
                                updateLastSyncClientQRCodeTimestamp();
                            }
                        } else {
                            processClientQRCodeRecords(clientQrCodeRecords);
                            page = page + 1;
                        }
                    } else {
                        Log.e(TAG, "GET request failed. Response Code: " + response.code());
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            CrashlyticsLogger.INSTANCE.recordException("Exception during HTTP GET request", t);
        }
    }

    private void updateLastSyncClientQRCodeTimestamp() {
        BigInteger maxUpdatedAt = null;
        BigInteger maxCreatedAt = null;
        BigInteger maxTimestamp = null;

        String queryUpdatedAt = "SELECT MAX(" + GlobalConfigDatabaseHelper.CLIENT_QR_CODE_COLUMN_UPDATED_AT + ") FROM " + GlobalConfigDatabaseHelper.TABLE_NAME_CLIENT_QR_CODE;
        String queryCreatedAt = "SELECT MAX(" + GlobalConfigDatabaseHelper.CLIENT_QR_CODE_COLUMN_ADDED_AT + ") FROM " + GlobalConfigDatabaseHelper.TABLE_NAME_CLIENT_QR_CODE;

        try {
            try (Cursor cursor = GlobalConfigDatabaseHelper.initializeInstance(context).globalDB.rawQuery(queryUpdatedAt, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    maxUpdatedAt = BigInteger.valueOf(cursor.getLong(0));
                }
            }

            try (Cursor cursor = GlobalConfigDatabaseHelper.initializeInstance(context).globalDB.rawQuery(queryCreatedAt, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    maxCreatedAt = BigInteger.valueOf(cursor.getLong(0));
                }
            }

            if (maxUpdatedAt != null && maxCreatedAt != null) {
                maxTimestamp = maxUpdatedAt.max(maxCreatedAt);
            } else if (maxUpdatedAt != null) {
                maxTimestamp = maxUpdatedAt;
            } else if (maxCreatedAt != null) {
                maxTimestamp = maxCreatedAt;
            }

        } catch (Throwable t) {
            CrashlyticsLogger.INSTANCE.recordException("Error fetching max timestamp: ", t);
        }

        if (maxTimestamp != null) {
            long incrementedValue = maxTimestamp.longValue() + 1;

            ContentValues values = new ContentValues();
            values.put(globalTimestampClientQRCodeLastSyncTimestamp, incrementedValue);

            try {
                int rowsAffected = syncManager.getDbConnection().update(
                        timeStampTable,
                        values,
                        "id = ?",
                        new String[]{"1"}
                );
                if (rowsAffected == 0) {
                    System.out.println("No rows updated in the global_timestamp table.");
                }
            } catch (Throwable t) {
                CrashlyticsLogger.INSTANCE.recordException("Error updating last sync timestamp: ", t);
            }
        } else {
            System.out.println("No timestamp found to update.");
        }
    }

    private long getLastSyncClientQRCodeTimestamp() {
        long lastSyncClientQRCodeTimestamp = 0;

        if (syncManager.getDbConnection() != null) {
            String query = "SELECT " + globalTimestampClientQRCodeLastSyncTimestamp +
                    " FROM " + timeStampTable +
                    " WHERE " + globalTimestampId + " = '1'";

            try (Cursor cursor = syncManager.getDbConnection().rawQuery(query, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    lastSyncClientQRCodeTimestamp = cursor.getLong(0);
                }
            } catch (Throwable t) {
                CrashlyticsLogger.INSTANCE.recordException("Error retrieving last sync timestamp: ", t);
            }
        }

        return lastSyncClientQRCodeTimestamp;
    }

    public void processClientQRCodeRecords(@NonNull JSONArray clientQrCodeRecords) throws JsonProcessingException {
        Set<ClientQRCodeDTO> clientQRCodeDTOS = SyncUtils.objectMapper.readValue(clientQrCodeRecords.toString(), new TypeReference<Set<ClientQRCodeDTO>>() {
        });

        for (ClientQRCodeDTO clientQRCodeDTO : clientQRCodeDTOS) {

            if (clientQRCodeDTO.getAdded_at() != null) {
                try {
                    BigInteger addedAt = new BigInteger(clientQRCodeDTO.getAdded_at().toString());
                    clientQRCodeDTO.setAdded_at(addedAt);
                } catch (Throwable t) {
                    CrashlyticsLogger.INSTANCE.recordException(t);
                }
            }

            if (clientQRCodeDTO.getUpdated_at() != null) {
                try {
                    BigInteger updatedAt = new BigInteger(clientQRCodeDTO.getUpdated_at().toString());
                    clientQRCodeDTO.setUpdated_at(updatedAt);
                } catch (Throwable t) {
                    CrashlyticsLogger.INSTANCE.recordException(t);
                }
            }

            ContentValues values = getContentValues(clientQRCodeDTO);

            SQLiteDatabase db = GlobalConfigDatabaseHelper.initializeInstance(context).globalDB;
            String whereClause = GlobalConfigDatabaseHelper.CLIENT_QR_CODE_COLUMN_CLIENT_QR_CODE_ID + " = ?";
            String[] whereArgs = new String[]{clientQRCodeDTO.getClient_qr_code_id()};

            // Try update first
            int rowsAffected = db.update(
                    GlobalConfigDatabaseHelper.TABLE_NAME_CLIENT_QR_CODE,
                    values,
                    whereClause,
                    whereArgs
            );

            if (rowsAffected == 0) {
                // No row updated → insert new
                String uuid = UUID.randomUUID().toString();
                clientQRCodeDTO.setId(uuid);

                values = getContentValues(clientQRCodeDTO);

                db.insert(
                        GlobalConfigDatabaseHelper.TABLE_NAME_CLIENT_QR_CODE,
                        null,
                        values
                );
            }
        }
    }

    private static @NonNull ContentValues getContentValues(@NonNull ClientQRCodeDTO clientQRCodeDTO) {
        ContentValues values = new ContentValues();

        // Check and add each field conditionally if it is not null
        if (clientQRCodeDTO.getId() != null) {
            values.put(GlobalConfigDatabaseHelper.CLIENT_QR_CODE_COLUMN_ID, clientQRCodeDTO.getId());
        }

        if (clientQRCodeDTO.getClient_qr_code_id() != null) {
            values.put(GlobalConfigDatabaseHelper.CLIENT_QR_CODE_COLUMN_CLIENT_QR_CODE_ID, clientQRCodeDTO.getClient_qr_code_id());
        }

        if (clientQRCodeDTO.getVdms_id() != null) {
            values.put(GlobalConfigDatabaseHelper.CLIENT_QR_CODE_COLUMN_VDMS_ID, clientQRCodeDTO.getVdms_id());
        }

        if (clientQRCodeDTO.getAdded_by() != null) {
            values.put(GlobalConfigDatabaseHelper.CLIENT_QR_CODE_COLUMN_ADDED_BY, clientQRCodeDTO.getAdded_by());
        }

        if (clientQRCodeDTO.getAdded_at() != null) {
            values.put(GlobalConfigDatabaseHelper.CLIENT_QR_CODE_COLUMN_ADDED_AT, clientQRCodeDTO.getAdded_at().toString());
        }

        if (clientQRCodeDTO.getUpdated_at() != null) {
            values.put(GlobalConfigDatabaseHelper.CLIENT_QR_CODE_COLUMN_UPDATED_AT, clientQRCodeDTO.getUpdated_at().toString());
        }

        if (clientQRCodeDTO.getDevice_id() != null) {
            values.put(GlobalConfigDatabaseHelper.CLIENT_QR_CODE_COLUMN_DEVICE_ID, clientQRCodeDTO.getDevice_id());
        }

        if (clientQRCodeDTO.getLocation_id() != null) {
            values.put(GlobalConfigDatabaseHelper.CLIENT_QR_CODE_COLUMN_LOCATION_ID, clientQRCodeDTO.getLocation_id());
        }

        if (clientQRCodeDTO.getUpdated_by() != null) {
            values.put(GlobalConfigDatabaseHelper.CLIENT_QR_CODE_COLUMN_UPDATED_BY, clientQRCodeDTO.getUpdated_by());
        }
        if (clientQRCodeDTO.getMovable() != null) {
            values.put(GlobalConfigDatabaseHelper.QR_CODE_COLUMN_MOVABLE, clientQRCodeDTO.getMovable());
        }

        if (clientQRCodeDTO.getDirection() != null) {
            values.put(GlobalConfigDatabaseHelper.QR_CODE_COLUMN_DIRECTION, clientQRCodeDTO.getDirection());
        }

        return values;
    }

    // Syncing NFC Data
    public void getNfcData() {
        Log.d(TAG, "Task for fetching NFC data from cloud is running");

        nfcDataSyncThread = new Thread(() -> {
            while (syncManager.continueNFCDataSync) {
                try {
                    boolean nfcPushSuccess = pushChangedNfcRecords();
                    if (nfcPushSuccess) {
                        getNfcRecords();
                    }
                    getClientNfcRecords();
                } catch (Throwable t) {
                    CrashlyticsLogger.INSTANCE.recordException("Exception in scheduled task for fetching NFC data from cloud", t);
                } finally {
                    try {
                        Thread.sleep(CLOUD_SYNC_PERIOD);
                    } catch (Throwable t) {
                        CrashlyticsLogger.INSTANCE.recordException(t);
                    }
                }
            }
        });

        nfcDataSyncThread.start();
    }

    private boolean pushChangedNfcRecords() {
        SQLiteDatabase db = GlobalConfigDatabaseHelper.initializeInstance(context).globalDB;

        String query = "SELECT * FROM " + GlobalConfigDatabaseHelper.TABLE_NAME_NFC_RECORDS +
                " WHERE " + GlobalConfigDatabaseHelper.NFC_RECORDS_COLUMN_CHANGES + " IS NOT NULL" +
                " AND " + GlobalConfigDatabaseHelper.NFC_RECORDS_COLUMN_CHANGES + " != '0'";

        boolean allSuccess = true;

        try (Cursor cursor = db.rawQuery(query, null)) {
            if (cursor == null || !cursor.moveToFirst()) {
                // No pending records — nothing to push, safe to pull
                return true;
            }

            do {
                String id = getStringFromCursor(cursor, GlobalConfigDatabaseHelper.NFC_RECORDS_COLUMN_ID);
                String deviceId = getStringFromCursor(cursor, GlobalConfigDatabaseHelper.NFC_RECORDS_COLUMN_DEVICE_ID);
                String vdmsId = getStringFromCursor(cursor, GlobalConfigDatabaseHelper.NFC_RECORDS_COLUMN_VDMS_ID);
                String locationId = getStringFromCursor(cursor, GlobalConfigDatabaseHelper.NFC_RECORDS_COLUMN_LOCATION_ID);
                String uid = getStringFromCursor(cursor, GlobalConfigDatabaseHelper.NFC_RECORDS_COLUMN_UID);

                NfcData nfcCheckData = new NfcData(deviceId, vdmsId, locationId, uid);
                String nfcCheckJson = new ObjectMapper().writeValueAsString(nfcCheckData);
                String checkNfcApiUrl = cloudBase() + String.format(CHECK_NFC_ID, Uri.encode(vdmsId));

                RequestBody requestBody = RequestBody.create(
                        nfcCheckJson,
                        MediaType.parse("application/json")
                );

                Request request = SyncUtils.createPUTRequest(checkNfcApiUrl, requestBody)
                        .newBuilder()
                        .addHeader("offline-sync", "true")
                        .build();

                try (Response response = syncManager.getClient().newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        markNfcAsSynced(id);
                        Log.d(TAG, "Nfc " + id + " synced successfully");
                    } else {
                        allSuccess = false;
                    }
                } catch (Exception e) {
                    allSuccess = false;
                }
            } while (cursor.moveToNext());

        } catch (Exception e) {
            return false;
        }

        return allSuccess;
    }

    private void markNfcAsSynced(String id) {
        try {
            ContentValues values = new ContentValues();
            values.putNull(GlobalConfigDatabaseHelper.NFC_RECORDS_COLUMN_CHANGES);

            GlobalConfigDatabaseHelper.initializeInstance(context).globalDB.update(
                    GlobalConfigDatabaseHelper.TABLE_NAME_NFC_RECORDS,
                    values,
                    GlobalConfigDatabaseHelper.NFC_RECORDS_COLUMN_ID + " = ?",
                    new String[]{id}
            );
        } catch (Exception ignored) {
        }
    }


    private void getNfcRecords() {
        try {
            boolean hasMoreRecords = true;
            int page = 1;

            while (hasMoreRecords) {
                String urlString = cloudBase() + "vdms/api/org/" + syncManager.getOrgId() + "/email/" + SyncManager.user + "/vdms/" + syncManager.getVdmsID() + "/nfcRecords?loggedInUser=" + SyncManager.user + "&pageNo=" + page + "&pageSize=" + pageSize;
                Request request = SyncUtils.createGETRequest(urlString, syncManager.getFeature(), syncManager);
                try (Response response = syncManager.getClient().newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        assert response.body() != null;
                        JSONObject responseJson = new JSONObject(response.body().string());

                        JSONArray nfcRecords = responseJson.getJSONArray("data");
                        if (nfcRecords.length() == 0) {
                            hasMoreRecords = false;
                            syncManager.initialCloudSyncStatus |= (1 << 2);
                        } else {
                            processNfcRecords(nfcRecords);
                            page = page + 1;
                        }
                    } else {
                        Log.e(TAG, "GET request failed. Response Code: " + response.code());
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            CrashlyticsLogger.INSTANCE.recordException(t);
        }
    }


    private void processNfcRecords(@NonNull JSONArray nfcRecords) throws JsonProcessingException {
        Set<NfcDTO> nfcDTOS = SyncUtils.objectMapper.readValue(nfcRecords.toString(), new TypeReference<Set<NfcDTO>>() {
        });

        for (NfcDTO nfcDTO : nfcDTOS) {
            if (nfcDTO.getCreation_time() != null) {
                try {
                    BigInteger creationTime = new BigInteger(nfcDTO.getCreation_time().toString());
                    nfcDTO.setCreation_time(creationTime);
                } catch (NumberFormatException e) {
                    System.out.println(e.getMessage());
                }
            }

            ContentValues values = getContentValues(nfcDTO);
            GlobalConfigDatabaseHelper.initializeInstance(context).globalDB.insertWithOnConflict(GlobalConfigDatabaseHelper.TABLE_NAME_NFC_RECORDS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    private static @NonNull ContentValues getContentValues(@NonNull NfcDTO nfcDTO) {
        ContentValues values = new ContentValues();
        if (nfcDTO.getId() != null) {
            values.put(GlobalConfigDatabaseHelper.NFC_RECORDS_COLUMN_ID, nfcDTO.getId());
        }
        if (nfcDTO.getDevice_id() != null) {
            values.put(GlobalConfigDatabaseHelper.NFC_RECORDS_COLUMN_DEVICE_ID, nfcDTO.getDevice_id());
        }
        if (nfcDTO.getLocation_id() != null) {
            values.put(GlobalConfigDatabaseHelper.NFC_RECORDS_COLUMN_LOCATION_ID, nfcDTO.getLocation_id());
        }
        if (nfcDTO.getVdms_id() != null) {
            values.put(GlobalConfigDatabaseHelper.NFC_RECORDS_COLUMN_VDMS_ID, nfcDTO.getVdms_id());
        }
        if (nfcDTO.getUid() != null) {
            values.put(GlobalConfigDatabaseHelper.NFC_RECORDS_COLUMN_UID, nfcDTO.getUid());
        }
        if (nfcDTO.getCreation_time() != null) {
            values.put(GlobalConfigDatabaseHelper.NFC_RECORDS_COLUMN_CREATION_TIME, nfcDTO.getCreation_time().toString());
        }
        if (nfcDTO.getCreated_by() != null) {
            values.put(GlobalConfigDatabaseHelper.NFC_RECORDS_COLUMN_CREATED_BY, nfcDTO.getCreated_by());
        }
        return values;
    }

    private void getClientNfcRecords() {
        try {
            boolean hasMoreRecords = true;
            int page = 1;

            while (hasMoreRecords) {
                String urlString = cloudBase() + "vdms/api/org/" + syncManager.getOrgId() + "/email/" + SyncManager.user + "/vdms/" + syncManager.getVdmsID() + "/clientNfcRecords?loggedInUser=" + SyncManager.user + "&pageNo=" + page + "&pageSize=" + pageSize;
                Request request = SyncUtils.createGETRequest(urlString, syncManager.getFeature(), syncManager);
                try (Response response = syncManager.getClient().newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        assert response.body() != null;
                        JSONObject responseJson = new JSONObject(response.body().string());

                        JSONArray clientNfcRecords = responseJson.getJSONArray("data");
                        if (clientNfcRecords.length() == 0) {
                            hasMoreRecords = false;
                            syncManager.initialCloudSyncStatus |= (1 << 3);
                        } else {
                            processClientNfcRecords(clientNfcRecords);
                            page = page + 1;
                        }
                    } else {
                        Log.e(TAG, "GET request failed. Response Code: " + response.code());
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            CrashlyticsLogger.INSTANCE.recordException(t);
        }
    }

    private void processClientNfcRecords(@NonNull JSONArray clientNfcRecords) throws JsonProcessingException {
        Set<ClientNfcDTO> clientNfcDTOS = SyncUtils.objectMapper.readValue(clientNfcRecords.toString(), new TypeReference<Set<ClientNfcDTO>>() {
        });

        for (ClientNfcDTO clientNfcDTO : clientNfcDTOS) {
            if (clientNfcDTO.getCreationTime() != null) {
                try {
                    BigInteger creationTime = new BigInteger(clientNfcDTO.getCreationTime().toString());
                    clientNfcDTO.setCreationTime(creationTime);
                } catch (Throwable t) {
                    CrashlyticsLogger.INSTANCE.recordException(t);
                }
            }

            ContentValues values = getContentValues(clientNfcDTO);
            GlobalConfigDatabaseHelper.initializeInstance(context).globalDB.insertWithOnConflict(GlobalConfigDatabaseHelper.TABLE_NAME_CLIENT_NFC_RECORDS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    private static @NonNull ContentValues getContentValues(@NonNull ClientNfcDTO clientNfcDTO) {
        ContentValues values = new ContentValues();
        if (clientNfcDTO.getId() != null) {
            values.put(GlobalConfigDatabaseHelper.CLIENT_NFC_RECORDS_COLUMN_ID, clientNfcDTO.getId());
        }
        if (clientNfcDTO.getNfc_id() != null) {
            values.put(GlobalConfigDatabaseHelper.CLIENT_NFC_RECORDS_COLUMN_NFC_ID, clientNfcDTO.getNfc_id());
        }
        if (clientNfcDTO.getDeviceId() != null) {
            values.put(GlobalConfigDatabaseHelper.CLIENT_NFC_RECORDS_COLUMN_DEVICE_ID, clientNfcDTO.getDeviceId());
        }
        if (clientNfcDTO.getLocationId() != null) {
            values.put(GlobalConfigDatabaseHelper.CLIENT_NFC_RECORDS_COLUMN_LOCATION_ID, clientNfcDTO.getLocationId());
        }
        if (clientNfcDTO.getVdmsId() != null) {
            values.put(GlobalConfigDatabaseHelper.CLIENT_NFC_RECORDS_COLUMN_VDMS_ID, clientNfcDTO.getVdmsId());
        }
        if (clientNfcDTO.getUid() != null) {
            values.put(GlobalConfigDatabaseHelper.CLIENT_NFC_RECORDS_COLUMN_UID, clientNfcDTO.getUid());
        }
        if (clientNfcDTO.getCreationTime() != null) {
            values.put(GlobalConfigDatabaseHelper.CLIENT_NFC_RECORDS_COLUMN_CREATION_TIME, clientNfcDTO.getCreationTime().toString());
        }
        if (clientNfcDTO.getCreatedBy() != null) {
            values.put(GlobalConfigDatabaseHelper.CLIENT_NFC_RECORDS_COLUMN_CREATED_BY, clientNfcDTO.getCreatedBy());
        }
        return values;
    }

    // Sync Bar Code Data
    public void getBarCodeData() {
        Log.d(TAG, "Task for fetching Bar Code data from cloud is running");

        barCodeSyncThread = new Thread(() -> {
            while (syncManager.continueBarCodeDataSync) {
                try {
                    getClientBarCodeRecords();
                } catch (Exception e) {
                    Log.e(TAG, "Exception in scheduled task for fetching Bar Code data from cloud", e);
                } finally {
                    try {
                        Thread.sleep(CLOUD_SYNC_PERIOD);
                    } catch (Throwable t) {
                        CrashlyticsLogger.INSTANCE.recordException(t);
                    }
                }
            }
        });

        barCodeSyncThread.start();
    }

    private void getClientBarCodeRecords() {
        try {
            long lastSyncClientBarCodeTimestamp = getLastSyncClientBarCodeTimestamp();
            boolean hasMoreRecords = true;
            int page = 1;

            while (hasMoreRecords) {
                String urlString = cloudBase() + "vdms/api/org/" + syncManager.getOrgId() + "/email/" + SyncManager.user + "/vdms/" + syncManager.getVdmsID() + "/clientBarCode?loggedInUser=" + SyncManager.user + "&lastSyncTime=" + lastSyncClientBarCodeTimestamp + "&pageNo=" + page + "&pageSize=" + pageSize;
                Request request = SyncUtils.createGETRequest(urlString, syncManager.getFeature(), syncManager);
                try (Response response = syncManager.getClient().newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        assert response.body() != null;
                        JSONObject responseJson = new JSONObject(response.body().string());

                        JSONArray clientBarCodeRecords = responseJson.getJSONArray("data");
                        if (clientBarCodeRecords.length() == 0) {
                            hasMoreRecords = false;
                            syncManager.initialCloudSyncStatus |= (1 << 4);
                            if (syncManager.getDbConnection() != null) {
                                updateLastSyncClientBarCodeTimestamp();
                            }
                        } else {
                            processClientBarCodeRecords(clientBarCodeRecords);
                            page = page + 1;
                        }
                    } else {
                        Log.e(TAG, "GET request failed. Response Code: " + response.code());
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            CrashlyticsLogger.INSTANCE.recordException(t);
        }
    }

    private long getLastSyncClientBarCodeTimestamp() {
        long lastSyncClientBarCodeTimestamp = 0;

        if (syncManager.getDbConnection() != null) {
            String query = "SELECT " + globalTimestampClientBarCodeLastSyncTimestamp +
                    " FROM " + timeStampTable +
                    " WHERE " + globalTimestampId + " = '1'";

            try (Cursor cursor = syncManager.getDbConnection().rawQuery(query, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    lastSyncClientBarCodeTimestamp = cursor.getLong(0);
                }
            } catch (SQLiteException e) {
                System.out.println("Error retrieving last sync timestamp: " + e.getMessage());
            }
        }

        return lastSyncClientBarCodeTimestamp;
    }


    private void updateLastSyncClientBarCodeTimestamp() {
        BigInteger maxUpdatedAt = null;
        BigInteger maxCreatedAt = null;
        BigInteger maxTimestamp = null;

        String queryUpdatedAt = "SELECT MAX(" + GlobalConfigDatabaseHelper.CLIENT_BAR_CODE_RECORDS_COLUMN_UPDATED_TIME + ") FROM " + GlobalConfigDatabaseHelper.TABLE_NAME_CLIENT_BAR_CODE_RECORDS;
        String queryCreatedAt = "SELECT MAX(" + GlobalConfigDatabaseHelper.CLIENT_BAR_CODE_RECORDS_COLUMN_ADDED_AT + ") FROM " + GlobalConfigDatabaseHelper.TABLE_NAME_CLIENT_BAR_CODE_RECORDS;

        try {
            try (Cursor cursor = GlobalConfigDatabaseHelper.initializeInstance(context).globalDB.rawQuery(queryUpdatedAt, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    maxUpdatedAt = BigInteger.valueOf(cursor.getLong(0));
                }
            }

            try (Cursor cursor = GlobalConfigDatabaseHelper.initializeInstance(context).globalDB.rawQuery(queryCreatedAt, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    maxCreatedAt = BigInteger.valueOf(cursor.getLong(0));
                }
            }

            if (maxUpdatedAt != null && maxCreatedAt != null) {
                maxTimestamp = maxUpdatedAt.max(maxCreatedAt);
            } else if (maxUpdatedAt != null) {
                maxTimestamp = maxUpdatedAt;
            } else if (maxCreatedAt != null) {
                maxTimestamp = maxCreatedAt;
            }

        } catch (Throwable t) {
            CrashlyticsLogger.INSTANCE.recordException(t);
        }

        if (maxTimestamp != null) {
            long incrementedValue = maxTimestamp.longValue() + 1;

            ContentValues values = new ContentValues();
            values.put(globalTimestampClientBarCodeLastSyncTimestamp, incrementedValue);

            try {
                int rowsAffected = syncManager.getDbConnection().update(
                        timeStampTable,
                        values,
                        "id = ?",
                        new String[]{"1"}
                );
                if (rowsAffected == 0) {
                    System.out.println("No rows updated in the global_timestamp table.");
                }
            } catch (Throwable t) {
                CrashlyticsLogger.INSTANCE.recordException("Error updating last sync timestamp: ", t);
            }
        } else {
            System.out.println("No timestamp found to update.");
        }
    }

    private void processClientBarCodeRecords(@NonNull JSONArray clientBarCodeRecords) throws JsonProcessingException {
        Set<ClientBarCodeDTO> clientBarCodeDTOS = SyncUtils.objectMapper.readValue(clientBarCodeRecords.toString(), new TypeReference<Set<ClientBarCodeDTO>>() {
        });

        for (ClientBarCodeDTO clientBarCodeDTO : clientBarCodeDTOS) {
            if (clientBarCodeDTO.getUpdated_time() != null) {
                try {
                    BigInteger updatedTime = new BigInteger(clientBarCodeDTO.getUpdated_time().toString());
                    clientBarCodeDTO.setUpdated_time(updatedTime);
                } catch (NumberFormatException e) {
                    System.out.println(e.getMessage());
                }
            }

            ContentValues values = getContentValues(clientBarCodeDTO);
            GlobalConfigDatabaseHelper.initializeInstance(context).globalDB.insertWithOnConflict(GlobalConfigDatabaseHelper.TABLE_NAME_CLIENT_BAR_CODE_RECORDS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    private void getProxyProfileData() {
        try {
            String urlString = cloudBase() + "vdms/api/vdms/" + syncManager.getVdmsID() + "/proxyProfile?loggedInUser=" + SyncManager.user;
            Request request = SyncUtils.createGETRequest(urlString, syncManager.getFeature(), syncManager);
            try (Response response = syncManager.getClient().newCall(request).execute()) {
                if (response.isSuccessful()) {
                    assert response.body() != null;
                    JSONObject responseJson = new JSONObject(response.body().string());

                    if (responseJson.isNull("data")) {
                        Log.d(TAG, "No proxy profile found for vdms " + syncManager.getVdmsID());
                        return;
                    }

                    JSONObject proxyProfileRecord = responseJson.getJSONObject("data");
                    processProxyProfileRecord(proxyProfileRecord);
                } else {
                    Log.e(TAG, "GET request failed. Response Code: " + response.code());
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Exception during HTTP GET request" + t);
            CrashlyticsLogger.INSTANCE.recordException(t);
        }
    }

    private void processProxyProfileRecord(@NonNull JSONObject proxyProfileRecord) throws JsonProcessingException {
        ProxyProfileDTO proxyProfileDTO = SyncUtils.objectMapper.readValue(proxyProfileRecord.toString(), ProxyProfileDTO.class);

        ContentValues values = getContentValues(proxyProfileDTO);
        GlobalConfigDatabaseHelper.initializeInstance(context).globalDB.insertWithOnConflict(TABLE_PROXY_PROFILE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    @NonNull
    private ContentValues getContentValues(@NonNull ProxyProfileDTO proxyProfileDTO) {
        ContentValues values = new ContentValues();
        if (proxyProfileDTO.getId() != null) {
            values.put(COLUMN_PROXY_PROFILE_ID, proxyProfileDTO.getId());
        }
        if (proxyProfileDTO.getName() != null) {
            values.put(COLUMN_PROXY_PROFILE_NAME, proxyProfileDTO.getName());
        }
        if (proxyProfileDTO.getPublic_ip() != null) {
            values.put(COLUMN_PROXY_PROFILE_PUBLIC_IP, proxyProfileDTO.getPublic_ip());
        }
        if (proxyProfileDTO.getTcp_port() != null) {
            values.put(COLUMN_PROXY_PROFILE_TCP_PORT, proxyProfileDTO.getTcp_port());
        }
        if (proxyProfileDTO.getUdp_port() != null) {
            values.put(COLUMN_PROXY_PROFILE_UDP_PORT, proxyProfileDTO.getUdp_port());
        }
        if (proxyProfileDTO.getHost_machine() != null) {
            values.put(COLUMN_PROXY_PROFILE_HOST_MACHINE, proxyProfileDTO.getHost_machine());
        }
        if (proxyProfileDTO.getCustomer_org_id() != null) {
            values.put(COLUMN_PROXY_PROFILE_CUSTOMER_ORG_ID, proxyProfileDTO.getCustomer_org_id());
        }
        if (proxyProfileDTO.getIs_deleted() != null) {
            values.put(COLUMN_PROXY_PROFILE_IS_DELETED, proxyProfileDTO.getIs_deleted());
        }
        if (proxyProfileDTO.getIs_global() != null) {
            values.put(COLUMN_PROXY_PROFILE_IS_GLOBAL, proxyProfileDTO.getIs_global());
        }
        if (proxyProfileDTO.getSsl_enabled() != null) {
            values.put(COLUMN_PROXY_PROFILE_SSL_ENABLED, proxyProfileDTO.getSsl_enabled());
        }
        if (proxyProfileDTO.getLatitude() != null) {
            values.put(COLUMN_PROXY_PROFILE_LATITUDE, proxyProfileDTO.getLatitude());
        }
        if (proxyProfileDTO.getLongitude() != null) {
            values.put(COLUMN_PROXY_PROFILE_LONGITUDE, proxyProfileDTO.getLongitude());
        }
        return values;
    }


    public int checkCloudData() {
        try {
            if (syncManager.initialCloudSyncStatus == 0b11111) {
                syncManager.setCloudSync(true);
                syncManager.editor.putBoolean(syncManager.getVdmsID() + "_cloud_sync_status", true);
                syncManager.editor.apply();
                return 100;
            }

            int qrCodeCount = 0;
            int clientQrCodeCount = 0;
            int qrCodeCountDB = 0;
            int clientQrCodeCountDB = 0;

            int nfcCount = 0;
            int clientNfcCount = 0;
            int nfcCountDB = 0;
            int clientNfcCountDB = 0;

            int barCodeCount = 0;
            int barCodeCountDB = 0;

            String qrCodeUrlString = cloudBase() + "vdms/api/vdms/" + syncManager.getVdmsID() + "/getQrCodeCounts?lastSyncTime=0";
            Request qrCodeRequest = SyncUtils.createGETRequest(qrCodeUrlString, syncManager.getFeature(), syncManager);
            try (Response response = syncManager.getClient().newCall(qrCodeRequest).execute()) {
                if (response.isSuccessful()) {
                    assert response.body() != null;
                    JSONObject responseJson = new JSONObject(response.body().string());

                    JSONObject qrCountObj = responseJson.getJSONObject("data");
                    qrCodeCount = qrCountObj.getInt("qrCodeCount");
                    clientQrCodeCount = qrCountObj.getInt("clientQrCodeCount");
                    qrCodeCountDB = getCloudDataCountInDatabase(GlobalConfigDatabaseHelper.TABLE_NAME_QR_CODE);
                    clientQrCodeCountDB = getCloudDataCountInDatabase(GlobalConfigDatabaseHelper.TABLE_NAME_CLIENT_QR_CODE);

                } else {
                    Log.e(TAG, "GET request failed. Response Code: " + response.code());
                }
            }


            String nfcUrlString = cloudBase() + "vdms/api/vdms/" + syncManager.getVdmsID() + "/getNfcCounts?lastSyncTime=0";
            Request nfcRequest = SyncUtils.createGETRequest(nfcUrlString, syncManager.getFeature(), syncManager);
            try (Response response = syncManager.getClient().newCall(nfcRequest).execute()) {
                if (response.isSuccessful()) {
                    assert response.body() != null;
                    JSONObject responseJson = new JSONObject(response.body().string());

                    JSONObject nfcCountObj = responseJson.getJSONObject("data");
                    nfcCount = nfcCountObj.getInt("nfcCount");
                    clientNfcCount = nfcCountObj.getInt("clientNfcCount");
                    nfcCountDB = getCloudDataCountInDatabase(GlobalConfigDatabaseHelper.TABLE_NAME_NFC_RECORDS);
                    clientNfcCountDB = getCloudDataCountInDatabase(GlobalConfigDatabaseHelper.TABLE_NAME_CLIENT_NFC_RECORDS);
                } else {
                    Log.e(TAG, "GET request failed. Response Code: " + response.code());
                }
            }

            String barCodeUrlString = cloudBase() + "vdms/api/vdms/" + syncManager.getVdmsID() + "/getBarCodeCounts?lastSyncTime=0";
            Request barCodeRequest = SyncUtils.createGETRequest(barCodeUrlString, syncManager.getFeature(), syncManager);
            try (Response response = syncManager.getClient().newCall(barCodeRequest).execute()) {
                if (response.isSuccessful()) {
                    assert response.body() != null;
                    JSONObject responseJson = new JSONObject(response.body().string());

                    JSONObject barCodeCountObj = responseJson.getJSONObject("data");
                    barCodeCount = barCodeCountObj.getInt("BarCodeCount");

                    barCodeCountDB = getCloudDataCountInDatabase(GlobalConfigDatabaseHelper.TABLE_NAME_CLIENT_BAR_CODE_RECORDS);
                } else {
                    Log.e(TAG, "GET request failed. Response Code: " + response.code());
                }
            }


            int totalCloudRecords = qrCodeCount + clientQrCodeCount + nfcCount + clientNfcCount + barCodeCount;
            int totalCloudRecordsDB = qrCodeCountDB + clientQrCodeCountDB + nfcCountDB + clientNfcCountDB + barCodeCountDB;

            return (totalCloudRecordsDB / totalCloudRecords) * 100;
        } catch (Throwable t) {
            CrashlyticsLogger.INSTANCE.recordException(t);
        }

        return 0;
    }

    private int getCloudDataCountInDatabase(String tableName) {
        try {
            int count = 0;
            String query = "SELECT COUNT(*) FROM " + tableName
                    + " WHERE " + GlobalConfigDatabaseHelper.QR_CODE_COLUMN_VDMS_ID + " = ?";

            Cursor cursor = GlobalConfigDatabaseHelper.initializeInstance(context).globalDB.rawQuery(query, new String[]{syncManager.getVdmsID()});
            if (cursor != null && cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }

            return count;
        } catch (Throwable t) {
            CrashlyticsLogger.INSTANCE.recordException(tableName, "", t);
        }

        return -1;
    }
}
