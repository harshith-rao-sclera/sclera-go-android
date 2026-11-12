package com.example.sclerago.services.syncManager.services;


import static com.example.sclerago.services.syncManager.utils.SyncUtils.replaceUrl;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.sclerago.services.syncManager.SyncManager;
import com.example.sclerago.services.syncManager.enums.SyncMode;
import com.example.sclerago.services.syncManager.utils.AuthInterceptor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import lombok.Data;
import lombok.EqualsAndHashCode;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

@EqualsAndHashCode(callSuper = true)
@Data
public class WebSocketClientService extends WebSocketListener {
    private OkHttpClient websocketClient = null;
    private SyncManager syncManager;
    private DataSyncService dataSyncService;
    private ImageSyncService imageSyncService;

    final String TAG = "Websocket";
    private BlockingQueue<String> queue;
    private QueueBufferedReader reader;
    private ScheduledExecutorService retryExecutor = Executors.newSingleThreadScheduledExecutor();
    private int retryAttempts = 0;
    private int fixedRetryCount = 0;
    private static final int FIXED_RETRY_LIMIT = 5; // First phase: 5 times every 10s

    public void setDataSyncService(DataSyncService dataSyncService) {
        this.dataSyncService = dataSyncService;
    }

    public void setImageSyncService(ImageSyncService imageSyncService) {
        this.imageSyncService = imageSyncService;
    }

    public WebSocketClientService(SyncManager syncManager) {
        this.syncManager = syncManager;
        connectToWS();
    }

    private void connectToWS() {
        websocketClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    Request requestWithHeaders = original.newBuilder()
                            .addHeader("Authorization", "Bearer " + SyncManager.token)
                            .addHeader("userid", SyncManager.userId)
                            .addHeader("scleraorgid", syncManager.getOrgId())
                            .addHeader("role", SyncManager.role)
                            .addHeader("userEmail", SyncManager.user)
                            .addHeader("Inspection-Version", syncManager.inspectionVersion)
                            .addHeader("X-Tenant-ID", syncManager.getVdmsID())
                            .method(original.method(), original.body())
                            .build();
                    return chain.proceed(requestWithHeaders);
                })
                .addInterceptor(new AuthInterceptor())
                .connectTimeout(10, TimeUnit.SECONDS)
                .build();

        String url = replaceUrl(syncManager.getSyncURL()) + "/websocket/sync";
        Request request = new Request.Builder()
                .url(url)
                .build();

        // Start the WebSocket connection
        websocketClient.newWebSocket(request, this);
    }

    @Override
    public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
        Log.d(TAG, "WebSocket connected for syncing");
        syncManager.setWsConnected(true);
        retryAttempts = 0;
        fixedRetryCount = 0;
        retryExecutor.shutdownNow();

        // Stop Polling threads before starting websocket
        if (dataSyncService != null) {
            if (dataSyncService.getDataSyncThread() != null && dataSyncService.getDataSyncThread().isAlive()) {
                dataSyncService.getDataSyncThread().interrupt();
            }
            // Stop any WS sync thread left over from a previous connection so we
            // don't end up with multiple data-sync threads on the same instance.
            if (dataSyncService.getDataSyncWSThread() != null && dataSyncService.getDataSyncWSThread().isAlive()) {
                dataSyncService.getDataSyncWSThread().interrupt();
            }
            dataSyncService.setCurrentDataSyncMode(SyncMode.WEBSOCKET);
            dataSyncService.setProcessorExecutor(Executors.newSingleThreadExecutor());
        }


        if (imageSyncService != null) {
            if (imageSyncService.getImageSyncThread() != null && imageSyncService.getImageSyncThread().isAlive()) {
                imageSyncService.getImageSyncThread().interrupt();
            }
            // Stop any WS image-sync thread left over from a previous connection.
            if (imageSyncService.getImageSyncWSThread() != null && imageSyncService.getImageSyncWSThread().isAlive()) {
                imageSyncService.getImageSyncWSThread().interrupt();
            }
            imageSyncService.setCurrentImageSyncMode(SyncMode.WEBSOCKET);
            imageSyncService.pullingImages.set(false);
        }

        syncManager.setWebSocket(webSocket);

        // Start fresh processor for this connection
        queue = new LinkedBlockingQueue<>();
        reader = new QueueBufferedReader(queue);

        dataSyncService.startDataSyncingWS();
        imageSyncService.startImageSyncingWS();
    }

    @Override
    public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
        try {
            if (text.startsWith("DataPush:")) {
                String payload = text.substring("DataPush: ".length()).trim();
                String[] messages = payload.split(" ");
                if ("200".equals(messages[0])) {
                    dataSyncService.clearLocalChangesWS(messages);
                } else {
                    dataSyncService.errorInPushingData.set(true);
                }

                if (dataSyncService.getDataPushPayload().isEmpty()) {
                    dataSyncService.pullChangesWS();
                }
            } else if (text.startsWith("DataPull:")) {
                String payload = text.substring("DataPull:".length()).trim();
                if (queue != null) {
                    queue.put(payload);

                    if (dataSyncService.currentProcessingTask == null || dataSyncService.currentProcessingTask.isDone()) {
                        dataSyncService.currentProcessingTask = dataSyncService.processorExecutor.submit(() -> {
                            try {
                                dataSyncService.processChanges(reader,
                                        dataSyncService.getLastSyncTimestamp(),
                                        SyncMode.WEBSOCKET);
                            } catch (Exception e) {
                                Log.e(TAG, "Error in processChanges", e);
                            }
                        });
                    }
                }
            } else if (text.startsWith("ImagePull: Batch done")) {
                Log.d(TAG, "ImagePull: Batch done");
                // The server omits the closing boundary for the last image of a batch,
                // so flush that stranded trailing part before finishing the batch.
                imageSyncService.flushTrailingImagePart();
                imageSyncService.pullingImages.set(false);

                // old inspection way of handling extra remaining order (not really used anymore)
                imageSyncService.removeExtraOrders(imageSyncService.getCurrentImageSet());
                imageSyncService.getCurrentImageSet().clear();
                imageSyncService.setPrimaryKey(null);
            } else if (text.startsWith("ImageStatus: ")) {

                // Not used since we are pushing images using https and not websocket due to the 8kb limit of websockets
                String[] parts = text.split(" ");
                String status = parts[0];
                String ackRecordId = parts[1];
                int ackOrder = Integer.parseInt(parts[2]);
                Map.Entry<String, ByteBuffer> currentImage = imageSyncService.getImageQueue().peek();
                if (currentImage != null && imageSyncService.matchesAck(currentImage.getKey(), status, ackRecordId, ackOrder)) {
                    Log.d(TAG, "Image acknowledged: " + ackRecordId + ", order: " + ackOrder);
                    imageSyncService.updateMediaChanges(currentImage, ackRecordId, ackOrder);
                    imageSyncService.getImageQueue().poll(); // Remove acknowledged image
                    imageSyncService.pushNextImage();
                } else {
                    Log.w(TAG, "Ack mismatch. Retrying...");
                    imageSyncService.pushNextImage();
                }
            } else {
                Log.w(TAG, "Received unhandled text message: " + text);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error processing incoming WebSocket message", e);
        }
    }

    @Override
    public void onMessage(@NonNull WebSocket webSocket, @NonNull ByteString bytes) {
        try {
            imageSyncService.pullingImages.set(true);
            imageSyncService.handleIncomingImages(bytes.toByteArray());
        } catch (Exception e) {
            Log.e(TAG, "Error processing binary WebSocket message", e);
        }
    }

    @Override
    public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, Response response) {
        Log.e(TAG, "WebSocket Failure: " + t.getMessage(), t);
        stopProcessor();
        syncManager.setWsConnected(false);
        websocketClient.dispatcher().executorService().shutdown();

        scheduleReconnect();
    }

    @Override
    public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
        Log.d(TAG, "WebSocket Closed: " + reason);
        stopProcessor();
        syncManager.setWsConnected(false);
        websocketClient.dispatcher().executorService().shutdown();

        scheduleReconnect();
    }

    public void stopProcessor() {
        if (queue != null) {
            queue.offer("__CLOSE__");
        }

        if (reader != null) {
            reader.close();
            reader = null;
        }

        if (dataSyncService != null) {
            if (dataSyncService.processorExecutor != null) {
                dataSyncService.processorExecutor.shutdown();
                try {
                    if (!dataSyncService.processorExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                        Log.w(TAG, "Data Processing did not finish in time; forcing shutdown.");
                        dataSyncService.processorExecutor.shutdownNow(); // force stop
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        if (imageSyncService != null) {
            imageSyncService.pullingImages.set(false);
        }

        queue = null;
    }

    private void scheduleReconnect() {
        if (retryExecutor.isShutdown() || retryExecutor.isTerminated()) {
            retryExecutor = Executors.newSingleThreadScheduledExecutor();
        }

        long delay;

        if (fixedRetryCount < FIXED_RETRY_LIMIT) {
            // Phase 1: Fixed 10s retry
            delay = 10;
            fixedRetryCount++;
            Log.d(TAG, "Fixed retry #" + fixedRetryCount + " in " + delay + " seconds.");
        } else {
            // start syncing using long polling
            if (dataSyncService != null
                    && (dataSyncService.getDataSyncThread() == null
                    || !dataSyncService.getDataSyncThread().isAlive())) {
                dataSyncService.setCurrentDataSyncMode(SyncMode.POLLING);
                dataSyncService.startDataSyncing();
            }

            if (imageSyncService != null
                    && (imageSyncService.getImageSyncThread() == null
                    || !imageSyncService.getImageSyncThread().isAlive())) {
                imageSyncService.setCurrentImageSyncMode(SyncMode.POLLING);
                imageSyncService.startImageSyncing();
            }


            // Phase 2: Exponential backoff (starting at 1s)
            delay = (long) Math.min(30, Math.pow(2, retryAttempts)); // Cap at 30s
            retryAttempts++;
            Log.d(TAG, "Exponential backoff retry #" + retryAttempts + " in " + delay + " seconds.");
        }

        retryExecutor.schedule(() -> {
            Log.d(TAG, "Attempting WebSocket reconnect...");
            connectToWS();
        }, delay, TimeUnit.SECONDS);
    }

    static class QueueBufferedReader extends BufferedReader {
        private final BlockingQueue<String> queue;
        private volatile boolean closed = false;

        public QueueBufferedReader(BlockingQueue<String> queue) {
            super(new StringReader("")); // never used
            this.queue = queue;
        }

        @Override
        public String readLine() throws IOException {
            while (!closed) {
                try {
                    String line = queue.take(); // blocks until data arrives
                    if ("__CLOSE__".equals(line)) return null; // EOF signal
                    return line;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while reading from queue", e);
                }
            }
            return null;
        }

        @Override
        public void close() {
            closed = true;
            queue.offer("__CLOSE__");
        }
    }

}
