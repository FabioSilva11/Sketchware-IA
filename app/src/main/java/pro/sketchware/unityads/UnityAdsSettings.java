package pro.sketchware.unityads;

import androidx.annotation.NonNull;

import mod.hey.studios.project.ProjectSettings;

/**
 * A project's Unity Ads setup: the Android game ID from the Unity dashboard (Monetization → Ad units, or the
 * project's settings) and whether exported builds show test ads. Like AdMob's, it is set once per project in the
 * library screen and used by every Unity Ads component.
 */
public final class UnityAdsSettings {

    public static final String KEY_GAME_ID = "unity_ads_game_id";
    public static final String KEY_TEST_MODE = "unity_ads_test_mode";

    private UnityAdsSettings() {
    }

    @NonNull
    public static String getGameId(@NonNull String scId) {
        return new ProjectSettings(scId).getValue(KEY_GAME_ID, "").trim();
    }

    /** Whether exported (release) builds show test ads too. The builds Sketchware runs always do. */
    public static boolean isTestMode(@NonNull String scId) {
        return ProjectSettings.SETTING_GENERIC_VALUE_TRUE.equals(
                new ProjectSettings(scId).getValue(KEY_TEST_MODE, ProjectSettings.SETTING_GENERIC_VALUE_FALSE));
    }

    public static boolean isConfigured(@NonNull String scId) {
        return isValidGameId(getGameId(scId));
    }

    /** Game IDs are the digits the dashboard shows, e.g. 5512345. */
    public static boolean isValidGameId(@NonNull String gameId) {
        return gameId.trim().matches("[0-9]{1,12}");
    }

    public static void save(@NonNull String scId, @NonNull String gameId, boolean testMode) {
        ProjectSettings settings = new ProjectSettings(scId);
        settings.setValue(KEY_GAME_ID, gameId.trim());
        settings.setValue(KEY_TEST_MODE, testMode ? ProjectSettings.SETTING_GENERIC_VALUE_TRUE
                : ProjectSettings.SETTING_GENERIC_VALUE_FALSE);
    }
}
