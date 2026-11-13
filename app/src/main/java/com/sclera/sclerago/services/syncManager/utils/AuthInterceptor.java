package com.sclera.sclerago.services.syncManager.utils;

import android.util.Log;

import androidx.annotation.NonNull;

import com.sclera.sclerago.services.syncManager.SyncManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AuthInterceptor implements Interceptor {
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build();

    @NonNull
    @Override
    public Response intercept(Chain chain) throws IOException {
        Response response = chain.proceed(chain.request());

        if (response.code() == 401) {
            getLatestAccessToken();
        }

        return response;
    }

    private void getLatestAccessToken() {
        Request request = new Request.Builder()
                .url(SyncManager.sessionUrl)
                .post(RequestBody.create(null, new byte[0]))
                .addHeader("Cookie", "sessionId=" + SyncManager.sessionId)
                .build();


        String TAG = "AUTH INTERCEPTOR";
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                if (response.body() != null) {
                    String responseData = response.body().string();
                    try {
                        JSONObject responseJSON = new JSONObject(responseData);
                        if (responseJSON.getInt("status") == 200) {
                            JSONObject data = responseJSON.getJSONObject("data");
                            String accessToken = data.getString("accessToken");
                            SyncManager.token = accessToken;
                            SyncUtils.updateToken(accessToken);
                        } else {
                            Log.e(TAG, "Cloud response for token update is not 200");
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "JSONException: " + e);
                    }
                } else {
                    Log.e(TAG, "Response body is null");
                }
            } else {
                if (response.code() == 400) {
                    Log.e(TAG, "User session is invalid: " + response.code());
                } else {
                    Log.e(TAG, "Unexpected response code: " + response.code());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception during HTTP POST request: " + e);
        }
    }
}
