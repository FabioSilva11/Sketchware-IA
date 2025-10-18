package pro.sketchware.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import pro.sketchware.R;

public class GroqClient {
    private static final String TAG = "GroqClient";
    private static final String BASE_URL = "https://api.groq.com/openai/v1/chat/completions";

    private static GroqClient instance;

    private final Context context;
    private final OkHttpClient client;
    private String apiKey;

    private GroqClient() {
        this.context = pro.sketchware.SketchApplication.getContext();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    public static synchronized GroqClient getInstance() {
        if (instance == null) {
            instance = new GroqClient();
        }
        return instance;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    private String getDeviceLanguage() {
        try {
            Resources resources = context.getResources();
            Configuration configuration = resources.getConfiguration();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                return configuration.getLocales().get(0).getLanguage();
            } else {
                //noinspection deprecation
                return configuration.locale.getLanguage();
            }
        } catch (Throwable t) {
            return Locale.getDefault().getLanguage();
        }
    }

    private void navigateToSettings() {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> {
            try {
                Toast.makeText(context, context.getString(R.string.groq_api_key_not_configured_message), Toast.LENGTH_SHORT).show();
                android.content.Intent intent = new android.content.Intent(context, pro.sketchware.activities.settings.IaSettingsActivity.class);
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "Failed to open AI settings", e);
            }
        });
    }

    public String sendMessage(String message) throws IOException {
        SharedPreferences prefs = context.getSharedPreferences("ia_settings", Context.MODE_PRIVATE);
        boolean groqEnabled = prefs.getBoolean("groq_enabled", false);
        if (!groqEnabled) {
            navigateToSettings();
            throw new IOException(context.getString(R.string.groq_api_key_not_configured_title));
        }

        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = prefs.getString("groq_api_key", "");
        }
        if (apiKey == null || apiKey.isEmpty()) {
            navigateToSettings();
            throw new IOException(context.getString(R.string.groq_api_key_not_configured_title));
        }

        String deviceLanguage = getDeviceLanguage();
        String systemPrompt = String.format("You are a helpful assistant. Please always respond in language: %s", deviceLanguage);

        JSONObject jsonBody = new JSONObject();
        try {
            // Choose a widely available Groq model
            jsonBody.put("model", "llama-3.1-8b-instant");
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content", systemPrompt));
            messages.put(new JSONObject().put("role", "user").put("content", message));
            jsonBody.put("messages", messages);
            jsonBody.put("temperature", 0.7);
            jsonBody.put("max_tokens", 4000);
        } catch (Exception e) {
            Log.e(TAG, "Error creating JSON for request", e);
            throw new IOException("Error preparing request", e);
        }

        Request request = new Request.Builder()
                .url(BASE_URL)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody.toString(), MediaType.parse("application/json")))
                .build();

        int maxRetries = 3;
        long backoffMs = 1000L;

        IOException lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    int code = response.code();
                    if (attempt < maxRetries && (code == 429 || (code >= 500 && code < 600))) {
                        try {
                            Thread.sleep(backoffMs);
                        } catch (InterruptedException ignored) { }
                        backoffMs *= 2;
                        continue;
                    }
                    throw new IOException("Request error: " + code + " - " + (response.body() != null ? response.body().string() : ""));
                }

                String responseBody = response.body() != null ? response.body().string() : "";
                JSONObject jsonResponse = new JSONObject(responseBody);
                JSONArray choices = jsonResponse.optJSONArray("choices");
                if (choices == null || choices.length() == 0) {
                    Log.e(TAG, "Response missing choices: " + responseBody);
                    throw new IOException("Invalid response from Groq API");
                }
                JSONObject firstChoice = choices.getJSONObject(0);
                String content = null;
                if (firstChoice.has("message")) {
                    content = firstChoice.getJSONObject("message").optString("content", null);
                }
                if (content == null) {
                    content = firstChoice.optString("text", null);
                }
                if (content == null) {
                    Log.e(TAG, "Could not extract content: " + responseBody);
                    throw new IOException("AI response content is empty");
                }
                return content;
            } catch (IOException e) {
                lastException = e;
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ignored) { }
                    backoffMs *= 2;
                    continue;
                }
                Log.e(TAG, "Error processing response", e);
                throw e;
            } catch (Exception e) {
                Log.e(TAG, "Error processing response", e);
                throw new IOException("Error processing API response", e);
            }
        }

        if (lastException != null) {
            throw lastException;
        } else {
            throw new IOException("Unknown error contacting Groq API");
        }
    }
}