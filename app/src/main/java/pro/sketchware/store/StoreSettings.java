package pro.sketchware.store;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The user's choices for the P2P store.
 */
public final class StoreSettings {

    public static final int[] CACHE_LIMITS_MB = {256, 512, 1024, 2048, 4096, 8192};

    private static final String FILE = "p2p_store";
    private static final String SHARING = "sharing";
    private static final String WIFI_ONLY = "wifi_only";
    private static final String CACHE_LIMIT_MB = "cache_limit_mb";

    private final SharedPreferences preferences;

    public StoreSettings(Context context) {
        preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Whether this device shares (uploads) projects and profiles it has with other users. */
    public boolean isSharing() {
        return preferences.getBoolean(SHARING, true);
    }

    public void setSharing(boolean sharing) {
        preferences.edit().putBoolean(SHARING, sharing).apply();
    }

    /** Whether the network is only used on unmetered connections such as Wi-Fi. */
    public boolean isWifiOnly() {
        return preferences.getBoolean(WIFI_ONLY, true);
    }

    public void setWifiOnly(boolean wifiOnly) {
        preferences.edit().putBoolean(WIFI_ONLY, wifiOnly).apply();
    }

    public int getCacheLimitMb() {
        return preferences.getInt(CACHE_LIMIT_MB, 1024);
    }

    public void setCacheLimitMb(int megabytes) {
        preferences.edit().putInt(CACHE_LIMIT_MB, megabytes).apply();
    }

    public long getCacheLimitBytes() {
        return getCacheLimitMb() * 1024L * 1024L;
    }
}
