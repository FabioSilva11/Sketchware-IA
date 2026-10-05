package pro.sketchware.chat;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.chat.port.VoidPortSettings;

public final class AiChatSettingsHelper {

    public static final String PREFS_NAME = VoidPortSettings.PREFS_NAME;
    public static final String PREF_CURRENT_PROVIDER = VoidPortSettings.PREF_CURRENT_PROVIDER;
    public static final String PREF_CURRENT_MODEL = VoidPortSettings.PREF_CURRENT_MODEL;
    public static final String PREF_CUSTOM_MODELS = VoidPortSettings.PREF_CUSTOM_MODELS;
    public static final String PREF_CHAT_MODE = VoidPortSettings.PREF_CHAT_MODE;
    public static final String PREF_MULTI_AGENT_MODE = VoidPortSettings.PREF_MULTI_AGENT_MODE;

    private AiChatSettingsHelper() {
    }

    public static final class ModelOption {
        public final String providerId;
        public final String providerLabel;
        public final String model;

        public ModelOption(String providerId, String providerLabel, String model) {
            this.providerId = providerId;
            this.providerLabel = providerLabel;
            this.model = model;
        }

        public String getDisplayLabel() {
            return providerLabel + " - " + model;
        }
    }

    public static SharedPreferences prefs(Context context) {
        return VoidPortSettings.prefs(context);
    }

    public static String getChatMode(SharedPreferences prefs) {
        return VoidPortSettings.getChatMode(prefs);
    }

    public static void setChatMode(SharedPreferences prefs, String mode) {
        VoidPortSettings.setChatMode(prefs, mode);
    }

    public static String getMultiAgentMode(SharedPreferences prefs) {
        return VoidPortSettings.getMultiAgentMode(prefs);
    }

    public static void setMultiAgentMode(SharedPreferences prefs, String mode) {
        VoidPortSettings.setMultiAgentMode(prefs, mode);
    }

    public static String getPermissionMode(SharedPreferences prefs) {
        return VoidPortSettings.getPermissionMode(prefs);
    }

    public static void setPermissionMode(SharedPreferences prefs, String mode) {
        VoidPortSettings.setPermissionMode(prefs, mode);
    }

    public static List<ModelOption> getVisibleModelOptions(SharedPreferences prefs) {
        List<ModelOption> options = new ArrayList<>();
        for (VoidPortSettings.ModelOption option : VoidPortSettings.getVisibleModelOptions(prefs)) {
            options.add(new ModelOption(option.providerId, option.providerLabel, option.model));
        }
        return options;
    }

    public static void ensureValidCurrentSelection(SharedPreferences prefs) {
        VoidPortSettings.ensureValidCurrentSelection(prefs);
    }

    public static boolean isCurrentSelectionValid(SharedPreferences prefs, String providerId, String model) {
        return VoidPortSettings.isCurrentSelectionValid(prefs, providerId, model);
    }

    public static boolean isProviderSupportedInChat(String providerId) {
        return VoidPortSettings.isProviderSupportedInChat(providerId);
    }

    public static boolean isProviderConfigured(SharedPreferences prefs, String providerId) {
        return VoidPortSettings.isProviderConfigured(prefs, providerId);
    }
}
