package pro.sketchware.supabase;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import mod.hey.studios.project.ProjectSettings;
import pro.sketchware.R;

/**
 * A project's Supabase connection: the project URL and its anon (public) key, the two values the Supabase
 * dashboard shows under Project Settings → API. Like Firebase's setup in the library screen, it is set once per
 * project and used by every Supabase component.
 */
public final class SupabaseSettings {

    public static final String KEY_URL = "supabase_url";
    public static final String KEY_ANON_KEY = "supabase_anon_key";

    private SupabaseSettings() {
    }

    @NonNull
    public static String getUrl(@NonNull String scId) {
        return new ProjectSettings(scId).getValue(KEY_URL, "").trim();
    }

    @NonNull
    public static String getAnonKey(@NonNull String scId) {
        return new ProjectSettings(scId).getValue(KEY_ANON_KEY, "").trim();
    }

    public static boolean isConfigured(@NonNull String scId) {
        return urlProblem(getUrl(scId)) == 0 && !getAnonKey(scId).isEmpty();
    }

    public static void save(@NonNull String scId, @NonNull String url, @NonNull String anonKey) {
        ProjectSettings settings = new ProjectSettings(scId);
        settings.setValue(KEY_URL, normalizeUrl(url));
        settings.setValue(KEY_ANON_KEY, anonKey.trim());
    }

    /** The URL without spaces or trailing slashes, e.g. https://abcd.supabase.co */
    @NonNull
    public static String normalizeUrl(@NonNull String url) {
        String normalized = url.trim();
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }

    /**
     * Why the URL can't be used, as a string resource, or 0 when it can. http is accepted for a local Supabase
     * while developing.
     */
    @StringRes
    public static int urlProblem(@NonNull String url) {
        String trimmed = url.trim();
        if (trimmed.isEmpty()) return R.string.supabase_error_url_empty;
        if (!trimmed.startsWith("https://") && !trimmed.startsWith("http://")) {
            return R.string.supabase_error_url_scheme;
        }
        String host = normalizeUrl(trimmed.substring(trimmed.indexOf("://") + 3));
        if (host.isEmpty() || host.startsWith("/") || trimmed.contains(" ") || trimmed.contains("\"")) {
            return R.string.supabase_error_url_invalid;
        }
        return 0;
    }
}
