package pro.sketchware.ai.config;

import android.content.Context;
import android.content.SharedPreferences;

import pro.sketchware.activities.chat.port.VoidPortLlmMessage;
import pro.sketchware.activities.chat.port.VoidPortSettings;

/**
 * Single application gateway for AI settings.
 *
 * IaSettingsActivity and ProviderDetailActivity remain the editors of the
 * underlying VoidPortSettings preferences; every AI feature reads the same
 * provider/model selection through this repository.
 */
public final class AiSettingsRepository {

    public static final class Selection {
        public final String providerId;
        public final String modelName;
        public final VoidPortLlmMessage.ProviderConfig providerConfig;

        private Selection(String providerId, String modelName,
                          VoidPortLlmMessage.ProviderConfig providerConfig) {
            this.providerId = providerId;
            this.modelName = modelName;
            this.providerConfig = providerConfig;
        }
    }

    private final SharedPreferences preferences;

    public AiSettingsRepository(Context context) {
        preferences = VoidPortSettings.prefs(context.getApplicationContext());
    }

    public SharedPreferences preferences() {
        return preferences;
    }

    public Selection currentSelection() {
        VoidPortSettings.ensureValidCurrentSelection(preferences);
        String providerId = preferences.getString(VoidPortSettings.PREF_CURRENT_PROVIDER, "groq");
        String modelName = preferences.getString(
                VoidPortSettings.PREF_CURRENT_MODEL,
                "llama-3.1-8b-instant"
        );
        return selection(providerId, modelName);
    }

    public Selection selection(String providerId, String modelName) {
        String safeProvider = safe(providerId);
        return new Selection(
                safeProvider,
                safe(modelName),
                VoidPortLlmMessage.resolveProviderConfig(preferences, safeProvider)
        );
    }

    public boolean isConfigured(Selection selection) {
        return selection != null
                && !selection.providerId.isEmpty()
                && selection.providerConfig != null
                && VoidPortSettings.isProviderConfigured(preferences, selection.providerId);
    }

    public void select(String providerId, String modelName) {
        preferences.edit()
                .putString(VoidPortSettings.PREF_CURRENT_PROVIDER, safe(providerId))
                .putString(VoidPortSettings.PREF_CURRENT_MODEL, safe(modelName))
                .apply();
        VoidPortSettings.ensureValidCurrentSelection(preferences);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
