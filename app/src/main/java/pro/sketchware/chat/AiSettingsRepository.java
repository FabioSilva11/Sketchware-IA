package pro.sketchware.chat;

import android.content.Context;
import android.content.SharedPreferences;

import pro.sketchware.chat.port.VoidPortLlmMessage;
import pro.sketchware.chat.port.VoidPortSettings;

/**
 * Single application gateway for AI settings.
 *
 * Single gateway for AI settings. Every AI feature reads the
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
        String providerId = preferences.getString(
                VoidPortSettings.PREF_CURRENT_PROVIDER,
                "openai"
        );
        String modelName = preferences.getString(
                VoidPortSettings.PREF_CURRENT_MODEL,
                ""
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
