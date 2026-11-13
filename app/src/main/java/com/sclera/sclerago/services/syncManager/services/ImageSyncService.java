package com.sclera.sclerago.services.syncManager.services;


import static com.sclera.sclerago.services.syncManager.constants.SyncConfigTables.globalTimestampId;
import static com.sclera.sclerago.services.syncManager.constants.SyncConfigTables.globalTimestampImageConfig;
import static com.sclera.sclerago.services.syncManager.constants.SyncConfigTables.globalTimestampMediaTimestamp;
import static com.sclera.sclerago.services.syncManager.constants.SyncConfigTables.globalTimestampSyncStatus;
import static com.sclera.sclerago.services.syncManager.constants.SyncConfigTables.multimediaContent;
import static com.sclera.sclerago.services.syncManager.constants.SyncConfigTables.multimediaId;
import static com.sclera.sclerago.services.syncManager.constants.SyncConfigTables.multimediaTable;
import static com.sclera.sclerago.services.syncManager.constants.SyncConfigTables.timeStampTable;
import static com.sclera.sclerago.services.syncManager.utils.SyncUtils.imageProgressMap;
import static com.sclera.sclerago.services.syncManager.utils.SyncUtils.readBinaryData;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.util.Log;

import androidx.annotation.NonNull;

import com.sclera.sclerago.services.syncManager.SyncManager;
import com.sclera.sclerago.services.syncManager.enums.OfflineFeatures;
import com.sclera.sclerago.services.syncManager.enums.SyncMode;
import com.sclera.sclerago.services.syncManager.utils.SyncUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.requery.android.database.sqlite.SQLiteDatabase;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.MultipartReader;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSource;
import okio.ByteString;

@SuppressLint("Range")
public class ImageSyncService {
    private SyncMode currentImageSyncMode;
    private final SyncManager syncManager;
    private final SQLiteDatabase dbConnection;
    private Thread imageSyncWSThread;
    private Thread imageSyncThread;
    private final Map<String, TreeSet<Integer>> currentImageSet = new HashMap<>();
    final ByteArrayOutputStream multipartBuffer = new ByteArrayOutputStream();
    private final Queue<Map.Entry<String, ByteBuffer>> imageQueue = new LinkedList<>();
    final AtomicBoolean pullingImages = new AtomicBoolean(false);
    private final String TAG = "Image Sync Service";
    private static long SYNC_IMAGE_PERIOD = 0;
    private int totalImages;
    private String primaryKey = null;

    public static void setSyncImagePeriod(long syncImagePeriod) {
        SYNC_IMAGE_PERIOD = syncImagePeriod;
    }

    public void setCurrentImageSyncMode(SyncMode currentImageSyncMode) {
        this.currentImageSyncMode = currentImageSyncMode;
    }

    public Thread getImageSyncWSThread() {
        return imageSyncWSThread;
    }

    public Thread getImageSyncThread() {
        return imageSyncThread;
    }

    public Map<String, TreeSet<Integer>> getCurrentImageSet() {
        return currentImageSet;
    }

    public Queue<Map.Entry<String, ByteBuffer>> getImageQueue() {
        return imageQueue;
    }

    public void setPrimaryKey(String primaryKey) {
        this.primaryKey = primaryKey;
    }

    public ImageSyncService(SyncManager syncManager, SyncMode syncMode) {
        this.syncManager = syncManager;
        this.dbConnection = syncManager.getDbConnection();

        this.currentImageSyncMode = syncMode;
        if (syncMode.equals(SyncMode.POLLING)) {
            startImageSyncing();
        }
    }

    void startImageSyncingWS() {
        imageSyncWSThread = new Thread(() -> {
            while (syncManager.continueImageSync) {
                try {
                    if (syncManager.isWsConnected() && !pullingImages.get()) {
                        try {
                            Map<String, byte[]> mediaRecords = generatePayloadForPushImages();
                            if (!mediaRecords.isEmpty()) {
                                pushImages(mediaRecords);
                            } else {
                                if(syncManager.isPullImagesEnabled(syncManager.appContext,syncManager.getVdmsID())){
                                    requestImagePull();
                                }
                            }
                        } catch (Exception ex) {
                            Log.e(TAG, "Exception in image sync loop", ex);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error during polling", e);
                } finally {
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException e) {
                        Log.d(TAG, "WS image-sync thread interrupted; exiting loop");
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
        imageSyncWSThread.start();
    }

    void startImageSyncing() {
        imageSyncThread = new Thread(() -> {
            while (syncManager.continueImageSync) {
                try {
                    Map<String, byte[]> mediaRecords = generatePayloadForPushImages();
                    if (!mediaRecords.isEmpty()) {
                        pushImages(mediaRecords);
                    } else {
                        if (!syncManager.getFeature().equals(OfflineFeatures.ASSET_DATA_COLLECTION) && syncManager.isPullImagesEnabled(syncManager.appContext, syncManager.getVdmsID()) ) {
                            JSONObject payload = generatePayloadForPullImages();
                            if (payload != null) {
                                pullImages(payload);
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error during polling", e);
                } finally {
                    try {
                        Thread.sleep(SYNC_IMAGE_PERIOD);
                    } catch (InterruptedException e) {
                        Log.d(TAG, "Polling image-sync thread interrupted; exiting loop");
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
        imageSyncThread.start();
    }

    public void generatePayloadForWsPushImages() {
        try {
            Map<String, byte[]> mediaRecords = generatePayloadForPushImages();
            if (!mediaRecords.isEmpty()) {
                for (Map.Entry<String, byte[]> entry : mediaRecords.entrySet()) {
                    String imageInfo = entry.getKey();
                    String[] parts = imageInfo.split(" ");
                    List<String> partsList = new ArrayList<>(Arrays.asList(parts));
                    partsList.remove(parts.length - 2);
                    String newImageInfo = String.join(" ", partsList);

                    if (entry.getValue().length < 8 * 1024) {
                        mediaRecords.remove(imageInfo);
                    } else {
                        continue;
                    }

                    try {
                        ByteBuffer buffer = encodeImagePayload(newImageInfo, entry.getValue());
                        if (buffer != null) {
                            Iterator<Map.Entry<String, ByteBuffer>> iterator = imageQueue.iterator();
                            boolean shouldAdd = true;

                            byte[] newBufferBytes = new byte[buffer.remaining()];
                            buffer.duplicate().get(newBufferBytes);

                            while (iterator.hasNext()) {
                                Map.Entry<String, ByteBuffer> queuedEntry = iterator.next();

                                if (queuedEntry.getKey().equals(newImageInfo)) {
                                    ByteBuffer queuedBuffer = queuedEntry.getValue();
                                    byte[] queuedBytes = new byte[queuedBuffer.remaining()];
                                    queuedBuffer.duplicate().get(queuedBytes);

                                    if (Objects.equals(SyncUtils.hashBytes(queuedBytes), SyncUtils.hashBytes(newBufferBytes))) {
                                        shouldAdd = false;
                                    } else {
                                        iterator.remove();
                                    }
                                    break;
                                }
                            }

                            if (shouldAdd) {
                                imageQueue.offer(new AbstractMap.SimpleEntry<>(newImageInfo, buffer));
                            }
                        }
                    } catch (UnsupportedEncodingException e) {
                        Log.e(TAG, "Encoding error", e);
                    }
                }

                // If greater than 8kb
                if (!mediaRecords.isEmpty()) {
                    pushImages(mediaRecords);
                    Thread.sleep(10000);
                    generatePayloadForWsPushImages();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private JSONObject generatePayloadForPullImages() throws JSONException {
        String mediaTimestamp = "0";
        String imagesConfigString = "";

        // Retrieve media_timestamp and imagesConfig from global_timestamp table
        String query = "SELECT " + globalTimestampMediaTimestamp + ", " + globalTimestampImageConfig + " FROM " + timeStampTable + " WHERE " + globalTimestampId + " = '1'";
        Cursor cursor = dbConnection.rawQuery(query, null);

        if (cursor.moveToFirst()) {
            mediaTimestamp = String.valueOf(cursor.getLong(cursor.getColumnIndex(globalTimestampMediaTimestamp)));
            imagesConfigString = cursor.getString(cursor.getColumnIndex(globalTimestampImageConfig));
        }
        cursor.close(); // Close cursor to avoid resource leak

        // Parse the JSON object
        JSONObject payload = null;
        if (imagesConfigString != null && !imagesConfigString.isEmpty()) {
            try {
                payload = new JSONObject(imagesConfigString);
                payload.put("media_sync_timestamp", mediaTimestamp);
            } catch (JSONException e) {
                System.out.println(e.getMessage());
            }
        }

        return payload;
    }

    void pushNextImage() {
        try {
            if (!imageQueue.isEmpty()) {
                Map.Entry<String, ByteBuffer> currentImage = imageQueue.peek();
                assert currentImage != null;
                syncManager.getWebSocket().send(ByteString.of(currentImage.getValue()));
                Log.d(TAG, "Sent image with key: " + currentImage.getKey());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void requestImagePull() throws JSONException {
        JSONObject payload = generatePayloadForPullImages();
        payload.put("actionType", "ImagePull");
        if (payload != null) {
            syncManager.getWebSocket().send(payload.toString());
            Log.d(TAG, "Requested image pull: " + payload);
        }
    }

    @NonNull
    public Map<String, byte[]> generatePayloadForPushImages() {
        Cursor cursor = null;
        Map<String, byte[]> mediaRecords = new LinkedHashMap<>();

        try {
            String query =
                    "SELECT \"" + multimediaId + "\", \"table_name\", \"row_id\", \"column_name\", \"order\", \"content_type\" " +
                            "FROM \"" + multimediaTable + "\" " +
                            "WHERE \"media_changes\" IS NOT NULL " +
                            "AND \"content_type\" NOT LIKE '%video%' " +
                            "AND length(\"" + multimediaContent + "\") < " + (10 * 1024 * 1024) + " " +
                            "ORDER BY \"order\" ASC LIMIT 10;";
            cursor = dbConnection.rawQuery(query, null);

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String id = cursor.getString(cursor.getColumnIndex(multimediaId));
                    String tableName = cursor.getString(cursor.getColumnIndex("table_name"));
                    String recordId = cursor.getString(cursor.getColumnIndex("row_id"));
                    String columnName = cursor.getString(cursor.getColumnIndex("column_name"));
                    String contentType = cursor.getString(cursor.getColumnIndex("content_type"));
                    int order = cursor.getInt(cursor.getColumnIndex("order"));
                    byte[] imageBlob = retrieveImageData(id);

//                    String type = "";
//                    if (contentType.contains("/")) {
//                        type = contentType.split("/")[1];
//                    }

                    // Create a unique key using tableName and id
                    String key = tableName + " " + columnName + " " + recordId + " " + order + " " + id + " " + contentType;
                    mediaRecords.put(key, imageBlob);
                }
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return mediaRecords;
    }

    void pushImages(@NonNull Map<String, byte[]> mediaRecords) throws IOException, JSONException {
        MultipartBody.Builder multipartBuilder = new MultipartBody.Builder();
        multipartBuilder.setType(MultipartBody.FORM);

        Log.d("pushing image", mediaRecords.toString());
        for (Map.Entry<String, byte[]> entry : mediaRecords.entrySet()) {
            String imageInfo = entry.getKey();
            byte[] imageBlob = entry.getValue();

            String[] parts = imageInfo.split(" ");
            String contentType = parts[parts.length - 1];
            String imageId = parts[parts.length - 2];
            String newImageInfo = String.join(" ", Arrays.copyOf(parts, parts.length - 2));

            RequestBody requestBody;
            if (imageBlob != null) {
                requestBody = RequestBody.create(imageBlob, MediaType.parse(contentType));
            } else {
                requestBody = RequestBody.create(new byte[0], MediaType.parse(contentType));
            }
            String fileName;
            if (contentType.contains("/")) {
                fileName = imageId + "." + contentType.split("/")[1];
            } else {
                fileName = imageId + "." + contentType;
            }
            multipartBuilder.addFormDataPart(newImageInfo, fileName, requestBody);
        }

        RequestBody body = multipartBuilder.build();

        String urlString = syncManager.getSyncURL() + "/api/v1/upload-download-images/upload";
        if (syncManager.getVdmsID() != null && !syncManager.getVdmsID().trim().isEmpty()) {
            urlString += "?vdms_id=" + syncManager.getVdmsID();
        }

        Request request = SyncUtils.createPOSTRequest(urlString, body, syncManager.getFeature(), syncManager);

        // Execute the request
        Response response = syncManager.getClient().newCall(request).execute();

        if (response.isSuccessful()) {
            Log.d("PushImages", "Images pushed successfully");
            removeMediaChanges(mediaRecords);
            setSyncImagePeriod(10000);

            Map<String, byte[]> pendingImages = generatePayloadForPushImages();
            if (!pendingImages.isEmpty()) {
                pushImages(pendingImages);
            } else {
                if (!syncManager.getFeature().equals(OfflineFeatures.ASSET_DATA_COLLECTION) && currentImageSyncMode.equals(SyncMode.POLLING)) {
                    JSONObject payload = generatePayloadForPullImages();
                    if (payload != null) {
                        pullImages(payload);
                    }
                } else if (currentImageSyncMode.equals(SyncMode.WEBSOCKET)) {
                    requestImagePull();
                }
            }
        } else {
            Log.e("PushImages", "Failed to push images: " + response.message());
            response.close();
            setSyncImagePeriod(10000);
        }
    }

    private void removeMediaChanges(Map<String, byte[]> mediaRecords) {
        for (Map.Entry<String, byte[]> entry : mediaRecords.entrySet()) {
            String imageInfo = entry.getKey();
            byte[] imageBlob = entry.getValue();

            String[] parts = imageInfo.split(" ");

            String tableName = parts[0];
            String columnName = parts[1];
            String rowId = parts[2];
            String order = parts[3];
            String imageId = parts[parts.length - 2];

            byte[] existingBlob = retrieveImageData(imageId);


            String hashBlobInMemory = "";
            if (imageBlob != null) {
                hashBlobInMemory = SyncUtils.hashBytes(imageBlob);
            }

            String hashBlobInDatabase = "";
            if (existingBlob != null) {
                hashBlobInDatabase = SyncUtils.hashBytes(existingBlob);
            }


            if (Objects.equals(hashBlobInDatabase, hashBlobInMemory)) {
                String whereClause = "table_name = ? AND column_name = ? AND row_id = ? AND `order`= ?";
                String[] whereArgs = new String[]{tableName, columnName, rowId, order};

                ContentValues values = new ContentValues();
                values.putNull("media_changes");
                dbConnection.update(multimediaTable, values, whereClause, whereArgs);
            }
        }
    }

    private void pullImages(@NonNull JSONObject payload) {
        try {
            Log.d("Payload for pull images", payload.toString());
            String urlString = syncManager.getSyncURL() + "/api/v1/upload-download-images/downloads";
            if (syncManager.getVdmsID() != null && !syncManager.getVdmsID().trim().isEmpty()) {
                urlString += "?vdms_id=" + syncManager.getVdmsID();
            }

            RequestBody body = RequestBody.create(String.valueOf(payload), MediaType.get("application/json; charset=utf-8"));
            Request request = SyncUtils.createPOSTRequest(urlString, body, syncManager.getFeature(), syncManager);

            Response response = syncManager.getClient().newCall(request).execute();
            if (response.isSuccessful()) {
                if (payload.getString("media_sync_timestamp").equals("0")) {
                    totalImages = Integer.parseInt(response.header("total_images"));
                    if (totalImages == 0) {
                        setSyncStatus(0);

                        if (syncManager.getFeature().equals(OfflineFeatures.ADMIN)) {
                            imageProgressMap.put(syncManager.getVdmsID(), "100");
                        } else if (syncManager.getFeature().equals(OfflineFeatures.ASSET_DATA_COLLECTION)) {
                            SyncUtils.assetDataImageProgress = "100";
                        }
                    }

                    if (syncManager.getFeature().equals(OfflineFeatures.ADMIN)) {
                        syncManager.getEditor().putInt(syncManager.getVdmsID() + "_total_images", totalImages);
                    } else if (syncManager.getFeature().equals(OfflineFeatures.ASSET_DATA_COLLECTION)) {
                        syncManager.getEditor().putInt("adc_total_images", totalImages);
                    }

                    syncManager.getEditor().apply();
                }
                parseMultipartResponse(response);
            } else {
                Log.e("Pull Images", "Unexpected response code: " + response.code());
                response.close();
                setSyncImagePeriod(10000);
            }
        } catch (Exception e) {
            Log.e("Pull Images", "Exception during HTTP POST request" + e);
            setSyncImagePeriod(10000);
        }
    }

    void handleIncomingImages(byte[] originalData) {
        try {
            multipartBuffer.write(originalData);
            byte[] rawData = multipartBuffer.toByteArray();

            String boundary = getBoundaryFromData(rawData);
            if (boundary == null) {
                Log.e(TAG, "Could not extract boundary from data. Waiting for more data.");
                return;
            }
            byte[] boundaryBytes = boundary.getBytes(StandardCharsets.ISO_8859_1);

            int searchIndex = 0;
            String max_timestamp = "0";

            while (true) {
                // Find the start of the next part
                int partStartIndex = indexOf(rawData, boundaryBytes, searchIndex);
                if (partStartIndex == -1) {
                    // No more boundaries found. The rest of the data is an incomplete part.
                    break;
                }

                // Find the end of the current part (which is the start of the next boundary)
                int partEndIndex = indexOf(rawData, boundaryBytes, partStartIndex + boundaryBytes.length);
                if (partEndIndex == -1) {
                    // The end of this part is not yet in the buffer. We need more data.
                    break;
                }

                // --- A complete part is found between partStartIndex and partEndIndex ---
                int contentStartIndex = partStartIndex + boundaryBytes.length;

                int headersEndIndex = indexOf(rawData, new byte[]{'\r', '\n', '\r', '\n'}, contentStartIndex);
                if (headersEndIndex == -1) break;

                String headersStr = new String(rawData, contentStartIndex, headersEndIndex - contentStartIndex, StandardCharsets.UTF_8);
                String contentDisposition = null;
                String contentType = null;
                for (String line : headersStr.split("\r\n")) {
                    if (line.startsWith("Content-Disposition:")) {
                        contentDisposition = line.substring("Content-Disposition:".length()).trim();
                    } else if (line.startsWith("Content-Type:")) {
                        contentType = line.substring("Content-Type:".length()).trim();
                    }
                }


                // --- find image data offsets ---
                int imageDataStart = headersEndIndex + 4; // after \r\n\r\n
                int imageDataEnd = partEndIndex; // until next boundary

                // Remove leading/trailing CRLF if present
                if (imageDataStart + 1 < imageDataEnd && rawData[imageDataStart] == 0x0D && rawData[imageDataStart + 1] == 0x0A) {
                    imageDataStart += 2;
                }
                if (imageDataEnd - 2 >= imageDataStart && rawData[imageDataEnd - 2] == 0x0D && rawData[imageDataEnd - 1] == 0x0A) {
                    imageDataEnd -= 2;
                }

                int imageLength = imageDataEnd - imageDataStart;

                if (contentDisposition != null) {
                    Matcher matcher = Pattern.compile("name=\"([^\"]*)\"").matcher(contentDisposition);
                    if (matcher.find()) {
                        String name = matcher.group(1);

                        String[] parts = name.split(" ");
                        if (parts.length == 5) {
                            String tableName = parts[0];
                            String columnName = parts[1];
                            String currentPrimaryKey = parts[2];
                            String timestamp = parts[3];
                            String order = parts[4];

                            System.out.println("Current processing primary key is : " + currentPrimaryKey);

                            if (Long.parseLong(max_timestamp) <= Long.parseLong(timestamp)) {
                                max_timestamp = timestamp;
                            }

                            parseMultipartResponse(
                                    rawData, imageDataStart, imageDataEnd,
                                    tableName,
                                    columnName,
                                    currentPrimaryKey,
                                    timestamp,
                                    order,
                                    String.valueOf(imageLength),
                                    contentType
                            );

                            // Handle extra orders (only for old inspections)
                            if (!currentPrimaryKey.equals(primaryKey)) {
                                if (primaryKey != null) {
                                    removeExtraOrders(currentImageSet);
                                    currentImageSet.clear();
                                }
                                primaryKey = currentPrimaryKey;
                            }
                            currentImageSet.computeIfAbsent(tableName + " " + columnName + " " + primaryKey, k -> new TreeSet<>()).add(Integer.parseInt(order));
                        }
                    }
                }

                // Move search position to the end of the part we just processed
                searchIndex = partEndIndex;
            }

            //  Handle leftover data ---
            if (searchIndex > 0) {
                byte[] leftoverData = Arrays.copyOfRange(rawData, searchIndex, rawData.length);
                multipartBuffer.reset();
                multipartBuffer.write(leftoverData);
            }

            if (!max_timestamp.equals("0")) {
                updateGlobalMediaTimestamp(max_timestamp);
            }
        } catch (IOException e) {
            Log.e(TAG, "IOException while writing to buffer: " + e.getMessage());
        } catch (NumberFormatException e) {
            Log.e(TAG, "Failed to parse a number (e.g., timestamp, order, or content-length): " + e.getMessage());
        } catch (Exception e) {
            // A general catch for unexpected errors, but specific ones are better.
            Log.e(TAG, "An unexpected error occurred: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * The server omits the closing boundary for the last image of a batch, leaving that part
     * stranded in {@link #multipartBuffer}. On "Batch done" we append the boundary ourselves
     * and re-run the normal parser so the trailing part is completed instead of being lost.
     */
    void flushTrailingImagePart() {
        if (multipartBuffer.size() == 0) {
            return;
        }
        String boundary = getBoundaryFromData(multipartBuffer.toByteArray());
        if (boundary == null) {
            multipartBuffer.reset();
            return;
        }
        handleIncomingImages(boundary.getBytes(StandardCharsets.ISO_8859_1));
        multipartBuffer.reset();
    }

    void removeExtraOrders(Map<String, TreeSet<Integer>> orderMapFromResponse) throws JSONException {
        if (orderMapFromResponse.isEmpty()) {
            return;
        }

        // Since the map has only one entry
        Map.Entry<String, TreeSet<Integer>> responseEntry = orderMapFromResponse.entrySet().iterator().next();
        String key = responseEntry.getKey();
        TreeSet<Integer> ordersInResponse = responseEntry.getValue();

        // Extract identifiers
        String[] parts = key.split(" ");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid key format. Expected format: 'tableName columnName primaryKey'");
        }

        String tableName = parts[0];
        String columnName = parts[1];
        String primaryKey = parts[2];

        // Fetch orders from DB using the same key
        Map<String, TreeSet<Integer>> orderMapInDB = setOrderMapFromDB(primaryKey);
        TreeSet<Integer> ordersInDB = orderMapInDB.getOrDefault(key, new TreeSet<>());

        // Find orders that are in DB but not in response
        TreeSet<Integer> extraOrders = new TreeSet<>(ordersInDB);
        extraOrders.removeAll(ordersInResponse);

        for (Integer toBeDeletedOrder : extraOrders) {
            String orderStr = toBeDeletedOrder.toString();
            deleteContentFromMultimediaTable(tableName, columnName, primaryKey, orderStr);
            clearMultimediaLinksFromTable(tableName, columnName, primaryKey, orderStr);
        }
    }


    private ByteBuffer encodeImagePayload(String key, byte[] imageData) throws UnsupportedEncodingException {
        try {

            JSONObject metadata = new JSONObject();
            String[] parts = key.split(" ");

            String[] newParts = new String[parts.length - 1];
            System.arraycopy(parts, 0, newParts, 0, parts.length - 2);
            newParts[parts.length - 2] = parts[parts.length - 1];
            String newImageInfo = String.join(" ", newParts);

            metadata.put("key", key);

            byte[] metadataBytes = metadata.toString().getBytes(StandardCharsets.UTF_8);
            int metadataLength = metadataBytes.length;
            int imagedataLength = 0;
            if (imageData != null) {
                imagedataLength = imageData.length;
            }
            // Allocate buffer: 4 bytes for metadata length + metadata + image
            ByteBuffer buffer = ByteBuffer.allocate(5 + metadataLength + imagedataLength);
            buffer.putInt(metadataLength);      // 4 bytes: length prefix
            buffer.put(metadataBytes);          // JSON metadata (e.g., {"key":"..."})
            if (imagedataLength != 0) buffer.put(imageData);              // image content
            buffer.flip();

            return buffer;
        } catch (JSONException e) {
            Log.e(TAG, "Failed to construct metadata JSON", e);
            return null;
        }
    }

    private String getBoundaryFromData(byte[] rawData) {
        int end = indexOf(rawData, "\r\n".getBytes(StandardCharsets.ISO_8859_1), 0);
        return (end != -1) ? new String(rawData, 0, end, StandardCharsets.ISO_8859_1) : null;
    }

    private byte[] extractImageData(byte[] data, int start, int end) {
        byte[] crlf = new byte[]{0x0D, 0x0A};
        // Skip headers and find the start of the image data
        int headerEnd = indexOf(data, new byte[]{'\r', '\n', '\r', '\n'}, 0);
        if (headerEnd == -1) return new byte[0]; // No headers end found

        // 2. Extract headers
        String headersSection;
        try {
            headersSection = new String(data, 0, headerEnd - 0, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "Header parse error", e);
            return new byte[0];
        }

        // 3. Extract Content-Length from headers
        int expectedLength = -1;
        for (String line : headersSection.split("\r\n")) {
            if (line.toLowerCase().startsWith("content-length")) {
                try {
                    expectedLength = Integer.parseInt(line.split(":")[1].trim());
                    break;
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (expectedLength <= 0) {
            Log.e(TAG, "Invalid or missing Content-Length");
            return new byte[0];
        }

        // Start of image data
        int imageDataStartIndex = headerEnd + 4; // Move past the empty line

        // Extract all remaining data after imageDataStartIndex
        // int imageDataLength = data.length - imageDataStartIndex;
        int imageDataLength = Math.min(expectedLength, data.length - imageDataStartIndex);
        if (imageDataLength <= 0) return new byte[0]; // No data to extract

        // Extract image data
        byte[] imageData = new byte[imageDataLength];
        System.arraycopy(data, imageDataStartIndex, imageData, 0, imageData.length);

        //drop first and last crlf
        // Remove leading CRLF if present
        if (startsWith(imageData, crlf)) {
            byte[] temp = new byte[imageData.length - 2];
            System.arraycopy(imageData, 2, temp, 0, temp.length);
            imageData = temp;
        }

        // Remove trailing CRLF if present
        if (endsWith(imageData, crlf)) {
            byte[] temp = new byte[imageData.length - 2];
            System.arraycopy(imageData, 0, temp, 0, temp.length);
            imageData = temp;
        }

        // 6. Final safety check
//        if (imageData.length != expectedLength) {
//            Log.w(TAG, "Image size mismatch. Expected: " + expectedLength + ", Got: " + imageData.length);
//        }
        return imageData;
    }

    // Helper method to check if an array starts with a prefix
    private static boolean startsWith(byte[] array, byte[] prefix) {
        if (array.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (array[i] != prefix[i]) return false;
        }
        return true;
    }

    // Helper method to check if an array ends with a suffix
    private static boolean endsWith(byte[] array, byte[] suffix) {
        if (array.length < suffix.length) return false;
        for (int i = 0; i < suffix.length; i++) {
            if (array[array.length - suffix.length + i] != suffix[i]) return false;
        }
        return true;
    }

    public static String extractContentDisposition(byte[] rawData) {
        return extractHeaderValue(rawData, "Content-Disposition:");
    }

    public static String extractContentType(byte[] rawData) {
        return extractHeaderValue(rawData, "Content-Type:");
    }

    private static String extractHeaderValue(byte[] data, String headerName) {
        byte[] headerBytes = headerName.getBytes(StandardCharsets.US_ASCII); // headers are ASCII
        int start = indexOf(data, headerBytes, 0);
        if (start == -1) return null;

        // Move past the header name
        start += headerBytes.length;

        // Find end of line (\r\n)
        int end = start;
        while (end < data.length - 1) {
            if (data[end] == '\r' && data[end + 1] == '\n') {
                break;
            }
            end++;
        }

        // Convert only the header value to a string
        return new String(data, start, end - start, StandardCharsets.UTF_8).trim();
    }

    private static int indexOf(byte[] data, byte[] pattern, int from) {
        outer:
        for (int i = from; i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    public byte[] retrieveImageData(String id) {
        byte[] imageData = null;
        int blobSize = 0;
        String sizeQuery = "SELECT LENGTH(" + multimediaContent + ") FROM " + multimediaTable + " WHERE " + multimediaId + " = ?";
        String blobQuery = "SELECT " + multimediaContent + " FROM " + multimediaTable + " WHERE " + multimediaId + " = ?";
        String chunkQueryTemplate = "SELECT SUBSTR(" + multimediaContent + ", %d, %d) FROM " + multimediaTable + " WHERE " + multimediaId + " = ?";

        Cursor cursor;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try {
            // Get blob size
            cursor = dbConnection.rawQuery(sizeQuery, new String[]{id});
            if (cursor != null && cursor.moveToFirst()) {
                blobSize = cursor.getInt(0);
            }
            if (cursor != null) {
                cursor.close();
            }

            // Process blob data based on size
            if (blobSize > 2 * 1024 * 1024) {
                // Larger than 2 MB
                int chunkSize = 1024 * 1000; // 1 MB chunk size
                int offset = 1;
                while (offset <= blobSize) {
                    int length = Math.min(chunkSize, blobSize - offset + 1);
                    @SuppressLint("DefaultLocale") String chunkQuery = String.format(chunkQueryTemplate, offset, length);

                    cursor = dbConnection.rawQuery(chunkQuery, new String[]{id});
                    if (cursor != null && cursor.moveToFirst()) {
                        byte[] chunk = cursor.getBlob(0);
                        outputStream.write(chunk);
                    }
                    if (cursor != null) {
                        cursor.close();
                    }

                    offset += chunkSize;
                }

                imageData = outputStream.toByteArray();
            } else {
                // 2 MB or smaller
                cursor = dbConnection.rawQuery(blobQuery, new String[]{id});
                if (cursor != null && cursor.moveToFirst()) {
                    imageData = cursor.getBlob(0);
                }
                if (cursor != null) {
                    cursor.close();
                }
            }
        } catch (SQLException | IOException e) {
            System.out.println(e.getMessage());
        } finally {
            try {
                outputStream.close();
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }

        return imageData;
    }

    private void setSyncStatus(int flag) {
        ContentValues values = new ContentValues();
        values.put(globalTimestampSyncStatus, flag);

        try {
            dbConnection.update(timeStampTable, values, globalTimestampId + " = '1'", null);
        } catch (Exception e) {
            Log.e("SyncManager", "Error updating sync status", e);
        }
    }

    private void parseMultipartResponse(Response response) {
        boolean hasContent = false;
        Map<String, TreeSet<Integer>> orderMapFromResponse = new HashMap<>();
        Set<String> primaryKeysFromResponse = new HashSet<>();

        Pattern pattern = Pattern.compile("name=\"([^\"]*)\"");
        Matcher matcher;
        try {
            assert response.body() != null;
            MultipartReader multipartReader = new MultipartReader(response.body());
            String tableName = "";
            String columnName = "";
            String primaryKey = "";
            String timestamp = "";
            String order = "";

            String max_timestamp = "0";

            MultipartReader.Part part;
            while ((part = multipartReader.nextPart()) != null) {
                BufferedSource source = part.body();
                Headers headers = part.headers();

                String contentDisposition = headers.get("Content-Disposition");
                String contentType = headers.get("Content-Type");
                String contentLength = headers.get("Content-Length");

                System.out.println("Part Headers:");
                System.out.println("Content-Disposition: " + contentDisposition);
                System.out.println("Content-Type: " + contentType);
                System.out.println("Content-Length: " + contentLength);

                if (contentDisposition != null) {
                    matcher = pattern.matcher(contentDisposition);

                    if (matcher.find()) {
                        String name = matcher.group(1);

                        assert name != null;
                        String[] parts = name.split(" ");
                        if (parts.length == 5) {
                            tableName = parts[0];
                            columnName = parts[1];
                            primaryKey = parts[2];
                            timestamp = parts[3];
                            order = parts[4];

                            boolean rowExists = checkIfRowExistsInTable(tableName, primaryKey);
                            boolean check = checkIfMediaChangeIsEnabled(tableName, columnName, primaryKey);

                            if (Integer.parseInt(contentLength) > 0) {
                                hasContent = true;
                            }

                            if (syncManager.getFeature().equals(OfflineFeatures.ADMIN)) {
                                if (!rowExists || check) {
                                    break;
                                }
                            }

                            if (Long.parseLong(timestamp) > Long.parseLong(max_timestamp)) {
                                max_timestamp = timestamp;
                            }

                        } else {
                            Log.d(TAG, "Unexpected format for name: " + name);
                        }
                    } else {
                        Log.d(TAG, "Content-Disposition header does not contain name attribute.");
                    }
                }

                if (Integer.parseInt(contentLength) > 0) {
                    byte[] binaryData = readBinaryData(source.inputStream());
                    boolean exists = checkImageExists(tableName, columnName, primaryKey, order);

                    if (exists) {
                        updateContentInMultimediaTable(tableName, columnName, primaryKey, order, binaryData, 0, 0);
                    } else {
                        addImage(binaryData, 0, 0, tableName, columnName, primaryKey, timestamp, contentType, order, false, primaryKey);
                    }
                } else {
                    deleteContentFromMultimediaTable(tableName, columnName, primaryKey, order);
                    clearMultimediaLinksFromTable(tableName, columnName, primaryKey, order);
                }

                primaryKeysFromResponse.add(primaryKey);
                orderMapFromResponse.computeIfAbsent(tableName + " " + columnName + " " + primaryKey, k -> new TreeSet<>()).add(Integer.parseInt(order));

                hasContent = true;
                setSyncImagePeriod(0);
            }
            multipartReader.close();

            if (syncManager.getFeature().equals(OfflineFeatures.ADMIN) && (SyncUtils.checkSyncStatus(syncManager.getVdmsID()) == 1 || Integer.parseInt(imageProgressMap.getOrDefault(syncManager.getVdmsID(), "0")) <= 100)) {
                SyncUtils.getProgress(totalImages, syncManager.getVdmsID());
            }

            if (syncManager.getFeature().equals(OfflineFeatures.ADMIN) && !orderMapFromResponse.isEmpty()) {
                Map<String, TreeSet<Integer>> orderMapInDB = setOrderMapFromDB(primaryKeysFromResponse);

                for (Map.Entry<String, TreeSet<Integer>> entry : orderMapInDB.entrySet()) {
                    String entryKey = entry.getKey();

                    String[] parts = entryKey.split(" ");
                    if (parts.length == 3) {
                        tableName = parts[0];
                        columnName = parts[1];
                        primaryKey = parts[2];
                    }


                    String key = tableName + " " + columnName + " " + primaryKey;

                    TreeSet<Integer> ordersInDB = entry.getValue();
                    TreeSet<Integer> ordersInResponse = orderMapFromResponse.getOrDefault(key, new TreeSet<>());

                    TreeSet<Integer> extraOrders = new TreeSet<>(ordersInDB);
                    extraOrders.removeAll(ordersInResponse);

                    if (!extraOrders.isEmpty()) {
                        for (Integer toBeDeletedOrder : extraOrders) {
                            deleteContentFromMultimediaTable(tableName, columnName, primaryKey, toBeDeletedOrder.toString());
                            clearMultimediaLinksFromTable(tableName, columnName, primaryKey, toBeDeletedOrder.toString());
                        }
                    }
                }
            }


            if (!max_timestamp.equals("0")) {
                updateGlobalMediaTimestamp(max_timestamp);
            }
        } catch (Exception e) {
            setSyncImagePeriod(10000);
            System.out.println(e.getMessage());
        }

        if (!hasContent || SyncUtils.getTotalImagesInDB() == totalImages) {
            setSyncStatus(2);
            if (syncManager.getFeature().equals(OfflineFeatures.ADMIN) && SyncUtils.checkSyncStatus(syncManager.getVdmsID()) == 1) {
                imageProgressMap.put(syncManager.getVdmsID(), "100");
            } else if (syncManager.getFeature().equals(OfflineFeatures.ASSET_DATA_COLLECTION)) {
                SyncUtils.assetDataImageProgress = "100";
            }

            if (!hasContent) {
                setSyncImagePeriod(10000);
            }
        }
    }

    private long parseMultipartResponse(
            byte[] rawData,
            int imageDataStart,
            int imageDataEnd,
            String tableName,
            String columnName,
            String primaryKey,
            String timestamp,
            String order,
            String contentLength,
            String contentType
    ) {
        try {
            boolean rowExists = checkIfRowExistsInTable(tableName, primaryKey);
            boolean check = checkIfMediaChangeIsEnabled(tableName, columnName, primaryKey);

//            if (!rowExists || check) {
//                return -1;
//            }

            if (Integer.parseInt(contentLength) > 0) {
                boolean exists = checkImageExists(tableName, columnName, primaryKey, order);
                System.out.println("content length:" + contentLength);
                if (exists) {
                    updateContentInMultimediaTable(tableName, columnName, primaryKey, order, rawData, imageDataStart, imageDataEnd);
                } else {
                    addImage(rawData, imageDataStart, imageDataEnd, tableName, columnName, primaryKey, timestamp, contentType, order, false, primaryKey);
                }
            } else {
                deleteContentFromMultimediaTable(tableName, columnName, primaryKey, order);
                clearMultimediaLinksFromTable(tableName, columnName, primaryKey, order);
            }

            setSyncImagePeriod(0);

            if (syncManager.getFeature().equals(OfflineFeatures.ADMIN)) {
                if (SyncUtils.checkSyncStatus(syncManager.getVdmsID()) == 1 || Integer.parseInt(imageProgressMap.getOrDefault(syncManager.getVdmsID(), "0")) <= 100) {
                    SyncUtils.getProgress(totalImages, syncManager.getVdmsID());
                }
            }

            return 1;

        } catch (Exception e) {
            setSyncImagePeriod(10000);
            System.out.println(e.getMessage());
            return -1;
        }
    }

    private void updateGlobalMediaTimestamp(String syncTimestamp) {
        long media_sync_timestamp = Long.parseLong(syncTimestamp);

        ContentValues values = new ContentValues();
        values.put(globalTimestampMediaTimestamp, media_sync_timestamp);
        dbConnection.update(
                timeStampTable,
                values,
                globalTimestampId + " = 1",
                null
        );
    }

    public Map<String, TreeSet<Integer>> setOrderMapFromDB(Set<String> primaryKeys) {
        Map<String, TreeSet<Integer>> orderMap = new HashMap<>();
        String query = "SELECT table_name, column_name, `order` FROM " + multimediaTable + " WHERE row_id = ?";
        for (String primaryKey : primaryKeys) {
            TreeSet<Integer> orderSet = new TreeSet<>();
            String tableName = "";
            String columnName = "";

            try (Cursor cursor = dbConnection.rawQuery(query, new String[]{primaryKey})) {
                while (cursor.moveToNext()) {
                    tableName = cursor.getString(0);
                    columnName = cursor.getString(1);
                    int order = Integer.parseInt(cursor.getString(2));
                    orderSet.add(order);
                }
            }

            orderMap.put(tableName + " " + columnName + " " + primaryKey, orderSet);
        }
        return orderMap;
    }

    public Map<String, TreeSet<Integer>> setOrderMapFromDB(String primaryKey) {
        Map<String, TreeSet<Integer>> orderMap = new HashMap<>();
        String query = "SELECT table_name, column_name, `order` FROM " + multimediaTable + " WHERE row_id = ?";
        TreeSet<Integer> orderSet = new TreeSet<>();
        String tableName = "";
        String columnName = "";

        try (Cursor cursor = dbConnection.rawQuery(query, new String[]{primaryKey})) {
            while (cursor.moveToNext()) {
                tableName = cursor.getString(0);
                columnName = cursor.getString(1);
                int order = Integer.parseInt(cursor.getString(2));
                orderSet.add(order);
            }
        }

        orderMap.put(tableName + " " + columnName + " " + primaryKey, orderSet);
        return orderMap;
    }

    public boolean checkIfRowExistsInTable(String tableName, String primaryKeyValue) {
        List<String> primaryKeyColumns = syncManager.getPrimaryKeyColumns(tableName);
        try (Cursor cursor = dbConnection.query(
                tableName,
                new String[]{"1"},
                primaryKeyColumns.get(0) + " = ?",
                new String[]{primaryKeyValue},
                null,
                null,
                null,
                "1"
        )) {
            return cursor != null && cursor.moveToFirst();
        }
    }

    public boolean checkIfMediaChangeIsEnabled(String tableName, String columnName, String primaryKeyValue) {
        String query = "SELECT media_changes FROM " + multimediaTable + " WHERE table_name = ? AND column_name = ? AND row_id = ?";
        Cursor cursor = dbConnection.rawQuery(query, new String[]{tableName, columnName, primaryKeyValue});

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    String media_changes = cursor.getString(0);
                    if (media_changes != null) {
                        return true;
                    }
                }
            } finally {
                cursor.close();
            }
        }

        return false;
    }

    private boolean columnExists(String tableName, String columnName) {
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

    void updateMediaChanges(Map.Entry<String, ByteBuffer> currentImage, String rowId, int order) {

        String imageInfo = currentImage.getKey();
        ByteBuffer imageBlob = currentImage.getValue();
        imageBlob.flip();
        byte[] imageBytes = new byte[imageBlob.remaining()];
        imageBlob.get(imageBytes);
        String[] parts = imageInfo.split(" ");
        String imageId = parts[parts.length - 2];

        byte[] existingBlob = retrieveImageData(imageId);

        String hashBlobInMemory = "";
        if (imageBytes != null) {
            hashBlobInMemory = SyncUtils.hashBytes(imageBytes);
        }

        String hashBlobInDatabase = "";
        if (existingBlob != null) {
            hashBlobInDatabase = SyncUtils.hashBytes(existingBlob);
        }

        if (Objects.equals(hashBlobInDatabase, hashBlobInMemory)) {
            String query = "UPDATE " + multimediaTable + " SET media_changes = NULL WHERE row_id = ? AND `order` =  ?";
            try {
                dbConnection.execSQL(query, new Object[]{rowId, order});
                Log.d("DB", "media_changes set to Null for row_id = " + rowId + " and order = " + order);
            } catch (Exception e) {
                Log.e("DB", "Error updating media_changes: " + e.getMessage(), e);
            }
        }
    }

    public boolean checkImageExists(String tableName, String columnName, String rowId, String order) {
        boolean exists = false;
        try (Cursor cursor = dbConnection.rawQuery(
                "SELECT * " +
                        "FROM " + multimediaTable +
                        " WHERE table_name = ?" +
                        " AND column_name = ?" +
                        " AND row_id = ?" +
                        " AND `order` = ?",
                new String[]{tableName, columnName, rowId, order})) {

            if (cursor != null && cursor.moveToFirst()) {
                exists = true;
            }
        } catch (Exception e) {
            System.out.println("Error checking if image exists: " + e.getMessage());
        }
        return exists;
    }

    public void updateContentInMultimediaTable(String tableName, String columnName, String primaryKey, String order, byte[] binaryData, int start, int end) {
        String whereClause = "row_id = ? AND table_name = ? AND column_name = ? AND `order` = ?";
        String[] whereArgs = new String[]{primaryKey, tableName, columnName, order};

        try {
            ContentValues contentValues = new ContentValues();
            if (start != 0 && end != 0) {
                contentValues.put(multimediaContent, Arrays.copyOfRange(binaryData, start, end));
            } else {
                contentValues.put(multimediaContent, binaryData);
            }
            dbConnection.update(multimediaTable, contentValues, whereClause, whereArgs);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public void deleteContentFromMultimediaTable(String tableName, String columnName, String primaryKey, String order) {
        String whereClause = "table_name = ? AND column_name = ? AND row_id = ? AND `order` = ?";
        String[] whereArgs = new String[]{tableName, columnName, primaryKey, order};

        try {
            dbConnection.delete(multimediaTable, whereClause, whereArgs);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public void clearMultimediaLinksFromTable(String tableName, String columnName, String primaryKey, String order) throws JSONException {
        List<String> primaryKeyColumns = syncManager.getPrimaryKeyColumns(tableName);
        String existingMultimediaLinks = checkIfMultimediaLinksAlreadyExists(tableName, columnName, primaryKey, primaryKeyColumns.get(0));

        if (existingMultimediaLinks != null && syncManager.getFeature().equals(OfflineFeatures.ADMIN)) {
            ContentValues contentValues = new ContentValues();

            if (SyncUtils.isValidJSONArray(existingMultimediaLinks)) {
                JSONArray jsonArray = new JSONArray(existingMultimediaLinks);

                for (int i = jsonArray.length() - 1; i >= 0; i--) {
                    JSONObject imageObject = jsonArray.getJSONObject(i);
                    int currentOrder = imageObject.getInt("order");

                    if (currentOrder == Integer.parseInt(order)) {
                        jsonArray.remove(i);
                    }
                }
                contentValues.put(columnName, jsonArray.toString());
            } else {
                contentValues.putNull(columnName);
            }
            String whereClause = primaryKeyColumns.get(0) + " = ?";
            String[] whereArgs = new String[]{primaryKey};

            try {
                dbConnection.update(tableName, contentValues, whereClause, whereArgs);
            } catch (SQLException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    public String checkIfMultimediaLinksAlreadyExists(String tableName, String columnName, String recordID, String primaryKeyColumn) {
        String query = "SELECT " + columnName + " FROM " + tableName + " WHERE " + primaryKeyColumn + " = ?";
        Cursor cursor = dbConnection.rawQuery(query, new String[]{recordID});

        String existingImages = null;
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    int columnIndex = cursor.getColumnIndex(columnName);
                    if (columnIndex != -1) {
                        existingImages = cursor.getString(columnIndex);
                    }
                }
            } finally {
                cursor.close();
            }
        }

        return existingImages;
    }

    public void addImage(byte[] imageBytes, int start, int end, String tableName, String columnName, String primaryKey, String syncTimestamp,
                         String contentType, String order, Boolean fromController, String imageId) throws JSONException {
        long mediaSyncTimestamp = SyncUtils.getCurrentTimestamp();
        List<String> primaryKeyColumns = syncManager.getPrimaryKeyColumns(tableName);

        if (syncTimestamp != null) {
            mediaSyncTimestamp = Long.parseLong(syncTimestamp);
        }

        // Check if the record already exists in the database
        String selection = "table_name = ? AND column_name = ? AND row_id = ? AND `order` = ?";
        String[] selectionArgs = new String[]{tableName, columnName, primaryKey, String.valueOf(Integer.parseInt(order))};

        Cursor cursor = dbConnection.query(multimediaTable, new String[]{multimediaId}, selection, selectionArgs, null, null, null);

        ContentValues imageValues = new ContentValues();
        if (start != 0 && end != 0) {
            imageValues.put(multimediaContent, Arrays.copyOfRange(imageBytes, start, end));
        } else {
            imageValues.put(multimediaContent, imageBytes);
        }
        imageValues.put("table_name", tableName);
        imageValues.put("column_name", columnName);
        imageValues.put("row_id", primaryKey);
        imageValues.put("content_type", contentType);
        imageValues.put("\"order\"", Integer.parseInt(order));

        // From server
        if (!fromController) {
            imageValues.put("flag", SyncUtils.getCurrentTimestamp());
        }

        if (cursor != null && cursor.moveToFirst()) {
            String imageUUID = cursor.getString(cursor.getColumnIndex(multimediaId));
            dbConnection.update(multimediaTable, imageValues, multimediaId + " = ?", new String[]{imageUUID});
        } else {
            if (imageId == null || syncManager.inspectionVersion.equals("v1")) {
                UUID uuid = UUID.randomUUID();
                imageId = uuid.toString();
            }

            imageValues.put(multimediaId, imageId);

            dbConnection.insert(multimediaTable, null, imageValues);
        }

        if (cursor != null) {
            cursor.close();
        }
    }

    boolean matchesAck(String key, String status, String ackRecordId, int ackOrder) {
        String[] parts = key.split(" ");
        return Objects.equals(status, "200") && parts.length == 5 && parts[2].equals(ackRecordId) && Integer.parseInt(parts[3]) == ackOrder;
    }
}
