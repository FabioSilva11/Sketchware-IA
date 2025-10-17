package pro.sketchware.network;

import static pro.sketchware.SketchApplication.getContext;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.Log;
import java.util.Locale;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.RequestBody;
import okhttp3.MediaType;
import pro.sketchware.R;

import org.json.JSONObject;
import java.io.IOException;

public class GroqClient {
    private static final String TAG = "GroqClient";
    private static final String BASE_URL = "https://api.groq.com/v1/chat/completions";
    private static GroqClient instance;
    private final Context context;
    private final OkHttpClient client;
    private String apiKey;

    private GroqClient() {
        this.context = pro.sketchware.SketchApplication.getContext();
        this.client = new OkHttpClient();
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
        Resources resources = context.getResources();
        Configuration configuration = resources.getConfiguration();
        return configuration.getLocales().get(0).getLanguage();
    }

    private void showApiKeyNotConfiguredDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);
        builder.setTitle(context.getString(R.string.groq_api_key_not_configured_title))
               .setMessage(context.getString(R.string.groq_api_key_not_configured_message))
               .setPositiveButton(context.getString(R.string.groq_api_key_configure), (dialog, which) -> {
                   android.content.Intent intent = new android.content.Intent(context, pro.sketchware.activities.settings.IaSettingsActivity.class);
                   intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                   context.startActivity(intent);
               })
               .setNegativeButton(context.getString(R.string.groq_api_key_cancel), null)
               .show();
    }

    public String sendMessage(String message) throws IOException {
        if (apiKey == null || apiKey.isEmpty()) {
            showApiKeyNotConfiguredDialog();
            throw new IllegalStateException(context.getString(R.string.groq_api_key_not_configured_title));
        }

        String deviceLanguage = getDeviceLanguage();
        String systemPrompt = String.format("You are a helpful assistant. Please always respond in language: %s", deviceLanguage);

        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("model", "gpt-oss");
            jsonBody.put("messages", new JSONObject[]
                {
                    new JSONObject().put("role", "system").put("content", systemPrompt),
                    new JSONObject().put("role", "user").put("content", message)
                }
            );
            jsonBody.put("temperature", 0.7);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao criar JSON da requisição", e);
            throw new IOException("Erro ao preparar requisição", e);
        }

        Request request = new Request.Builder()
                .url(BASE_URL)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody.toString(), MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Erro na requisição: " + response.code());
            }

            String responseBody = response.body().string();
            JSONObject jsonResponse = new JSONObject(responseBody);
            return jsonResponse.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");
        } catch (Exception e) {
            Log.e(TAG, "Erro ao processar resposta", e);
            throw new IOException("Erro ao processar resposta da API", e);
        }
    }
}