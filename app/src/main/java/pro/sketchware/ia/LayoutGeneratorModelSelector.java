package pro.sketchware.ia;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import pro.sketchware.activities.chat.port.VoidPortSettings;

/**
 * Selects a random configured provider and model for layout generation.
 * This allows the layout generator to use any configured AI provider dynamically
 * instead of being hardcoded to a specific provider/model.
 */
public final class LayoutGeneratorModelSelector {
    private static final String TAG = "LayoutModelSelector";

    private LayoutGeneratorModelSelector() {
    }

    public static final class SelectedModel {
        public final String providerId;
        public final String providerLabel;
        public final String modelName;

        public SelectedModel(String providerId, String providerLabel, String modelName) {
            this.providerId = providerId;
            this.providerLabel = providerLabel;
            this.modelName = modelName;
        }

        @Override
        public String toString() {
            return providerLabel + "/" + modelName;
        }
    }

    /**
     * Selects a random configured provider and model for layout generation.
     * Falls back to Groq/llama-3.1-8b-instant if no providers are configured.
     *
     * @param context Android context for accessing SharedPreferences
     * @return Selected provider and model, never null
     */
    public static SelectedModel selectRandomModel(Context context) {
        SharedPreferences prefs = VoidPortSettings.prefs(context);
        List<VoidPortSettings.ModelOption> availableOptions = VoidPortSettings.getVisibleModelOptions(prefs);

        // Filter to only include models suitable for layout generation
        List<VoidPortSettings.ModelOption> suitableOptions = new ArrayList<>();
        for (VoidPortSettings.ModelOption option : availableOptions) {
            if (isSuitableForLayoutGeneration(option)) {
                suitableOptions.add(option);
            }
        }

        if (suitableOptions.isEmpty()) {
            Log.w(TAG, "No suitable models found for layout generation, using fallback");
            return getFallbackModel();
        }

        // Randomly select one
        Random random = new Random();
        VoidPortSettings.ModelOption selected = suitableOptions.get(random.nextInt(suitableOptions.size()));

        Log.d(TAG, "Selected layout generation model: " + selected.providerId + "/" + selected.model);
        return new SelectedModel(selected.providerId, selected.providerLabel, selected.model);
    }

    /**
     * Gets the currently selected chat model for layout generation.
     * This can be used as an alternative to random selection.
     *
     * @param context Android context
     * @return Current chat model selection
     */
    public static SelectedModel getCurrentChatModel(Context context) {
        SharedPreferences prefs = VoidPortSettings.prefs(context);
        VoidPortSettings.ensureValidCurrentSelection(prefs);

        String providerId = prefs.getString(VoidPortSettings.PREF_CURRENT_PROVIDER, "groq");
        String modelName = prefs.getString(VoidPortSettings.PREF_CURRENT_MODEL, "llama-3.1-8b-instant");

        // Find the provider label
        String providerLabel = providerId;
        for (VoidPortSettings.ProviderGroup group : VoidPortSettings.getAllProviderGroups(prefs)) {
            if (group.providerId.equals(providerId)) {
                providerLabel = group.label;
                break;
            }
        }

        Log.d(TAG, "Using current chat model for layout generation: " + providerId + "/" + modelName);
        return new SelectedModel(providerId, providerLabel, modelName);
    }

    /**
     * Checks if a model is suitable for layout generation.
     * Filters out models that are too small, specialized for other tasks, or known to be problematic.
     */
    private static boolean isSuitableForLayoutGeneration(VoidPortSettings.ModelOption option) {
        String model = option.model.toLowerCase();
        String provider = option.providerId.toLowerCase();

        // Exclude very small models (nano, mini variants)
        if (model.contains("nano") || model.contains("1b") || model.contains("3b")) {
            return false;
        }

        // Exclude reasoning-only models
        if (model.contains("reasoner") || model.contains("-r1") || model.contains("o3") || model.contains("o4")) {
            return false;
        }

        // Exclude specialized code/math models that may not handle XML well
        if (model.contains("codestral") || model.contains("devstral")) {
            return false;
        }

        // Exclude vision-only models
        if (model.contains("vision") && !model.contains("versatile")) {
            return false;
        }

        // Prefer providers known to work well with XML generation
        // OpenAI, Anthropic, Gemini, DeepSeek, Groq, OpenRouter, Ollama are all good
        return true;
    }

    /**
     * Fallback model when no configured providers are available.
     */
    private static SelectedModel getFallbackModel() {
        return new SelectedModel("groq", "Groq", "llama-3.1-8b-instant");
    }

    /**
     * Applies the selected model to SharedPreferences temporarily for the layout generation request.
     * Returns the previous selection so it can be restored after generation.
     *
     * @param context Android context
     * @param selected Selected model to apply
     * @return Previous selection for restoration
     */
    public static SelectedModel applyModelSelection(Context context, SelectedModel selected) {
        SharedPreferences prefs = VoidPortSettings.prefs(context);

        // Save current selection for restoration
        String previousProvider = prefs.getString(VoidPortSettings.PREF_CURRENT_PROVIDER, "");
        String previousModel = prefs.getString(VoidPortSettings.PREF_CURRENT_MODEL, "");

        // Apply new selection
        prefs.edit()
                .putString(VoidPortSettings.PREF_CURRENT_PROVIDER, selected.providerId)
                .putString(VoidPortSettings.PREF_CURRENT_MODEL, selected.modelName)
                .apply();

        return new SelectedModel(previousProvider, "", previousModel);
    }

    /**
     * Restores the previous model selection.
     *
     * @param context Android context
     * @param previous Previous selection to restore
     */
    public static void restoreModelSelection(Context context, SelectedModel previous) {
        if (previous == null || previous.providerId.isEmpty()) {
            return;
        }

        SharedPreferences prefs = VoidPortSettings.prefs(context);
        prefs.edit()
                .putString(VoidPortSettings.PREF_CURRENT_PROVIDER, previous.providerId)
                .putString(VoidPortSettings.PREF_CURRENT_MODEL, previous.modelName)
                .apply();
    }
}
