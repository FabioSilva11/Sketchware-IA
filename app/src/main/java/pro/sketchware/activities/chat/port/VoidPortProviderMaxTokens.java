package pro.sketchware.activities.chat.port;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import pro.sketchware.SketchApplication;

public final class VoidPortProviderMaxTokens {
    public static final String MODE_DEFAULT = "default";
    public static final String MODE_CUSTOM = "custom";
    public static final int DEFAULT_MAX_TOKENS = 4000;

    private static final int MIN_MAX_TOKENS = 1;
    private static final int HARD_LIMIT_MAX_TOKENS = 200_000;

    private VoidPortProviderMaxTokens() {
    }

    public static String modeKey(String providerId) {
        return "provider_max_tokens_mode_" + slugify(providerId);
    }

    public static String customKey(String providerId) {
        return "provider_max_tokens_custom_" + slugify(providerId);
    }

    public static int resolve(String providerId, String modelName) {
        return resolve(providerId, modelName, DEFAULT_MAX_TOKENS);
    }

    public static int resolve(String providerId, String modelName, int requestedDefault) {
        Context context = SketchApplication.getContext();
        if (context == null) {
            return clamp(requestedDefault > 0 ? requestedDefault : DEFAULT_MAX_TOKENS);
        }
        SharedPreferences prefs = VoidPortSettings.prefs(context);
        return resolve(prefs, providerId, modelName, requestedDefault);
    }

    public static int resolve(SharedPreferences prefs, String providerId, String modelName, int requestedDefault) {
        int fallback = requestedDefault > 0 ? requestedDefault : DEFAULT_MAX_TOKENS;
        if (prefs == null) {
            return clamp(fallback);
        }

        JSONObject custom = VoidPortSettings.getProviderConfigObject(prefs, providerId);
        if (custom != null) {
            String mode = custom.optString("maxTokensMode", prefs.getString(modeKey(providerId), MODE_DEFAULT));
            if (MODE_CUSTOM.equals(mode)) {
                int customValue = custom.optInt("maxTokens", parsePositiveInt(prefs.getString(customKey(providerId), ""), -1));
                if (customValue > 0) {
                    return clamp(customValue);
                }
            }
        }

        String mode = prefs.getString(modeKey(providerId), MODE_DEFAULT);
        if (MODE_CUSTOM.equals(mode)) {
            int customValue = parsePositiveInt(prefs.getString(customKey(providerId), ""), -1);
            if (customValue > 0) {
                return clamp(customValue);
            }
        }
        return clamp(fallback);
    }

    private static int parsePositiveInt(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int clamp(int value) {
        return Math.max(MIN_MAX_TOKENS, Math.min(value, HARD_LIMIT_MAX_TOKENS));
    }

    private static String slugify(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(java.util.Locale.US).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }
}
