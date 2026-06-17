package pro.sketchware.activities.settings;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;
import pro.sketchware.activities.chat.port.VoidPortSettings;
import pro.sketchware.databinding.ActivityProviderDetailBinding;
import pro.sketchware.databinding.BottomSheetAddModelBinding;
import pro.sketchware.databinding.ItemProviderModelRowBinding;

/**
 * Kelivo-style provider detail screen.
 *
 * Shows a "Manage" card (provider type, group, enabled, multi-key, response API,
 * balance, network) followed by Name / API Key / Base URL / API Path fields, and
 * a bottom Config/Models tab switcher. All data persistence reuses the existing
 * {@link VoidPortSettings} keys so no migration is needed.
 */
public class ProviderDetailActivity extends AppCompatActivity {

    public static final String EXTRA_PROVIDER_TITLE = "provider_title";

    private ActivityProviderDetailBinding binding;
    private SharedPreferences prefs;
    private VoidPortSettings.ProviderCardSpec spec;

    private boolean showingModelsTab = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProviderDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        prefs = VoidPortSettings.prefs(this);

        String title = getIntent().getStringExtra(EXTRA_PROVIDER_TITLE);
        spec = findSpec(title);
        if (spec == null) {
            finish();
            return;
        }

        binding.topAppBar.setTitle(spec.title);
        binding.topAppBar.setNavigationOnClickListener(v -> finish());

        bindManageCard();
        bindCoreFields();
        bindExtraFields();
        bindTabs();
        refreshModelsTab();
    }

    @Nullable
    private VoidPortSettings.ProviderCardSpec findSpec(@Nullable String title) {
        if (title == null) return null;
        for (VoidPortSettings.ProviderCardSpec candidate : VoidPortSettings.getProviderCards()) {
            if (candidate.title.equals(title)) {
                return candidate;
            }
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────
    // "Manage" card
    // ─────────────────────────────────────────────────────────────────────

    private void bindManageCard() {
        binding.tvProviderTypeValue.setText(spec.title);
        binding.tvGroupValue.setText(isLocalGroupProvider() ? "Local" : "Other");

        VoidPortSettings.FieldSpec primaryField = primaryField();
        String enabledKey = primaryField != null ? primaryField.enabledKey : null;

        if (enabledKey != null) {
            binding.switchEnabled.setChecked(prefs.getBoolean(enabledKey, true));
            binding.switchEnabled.setOnCheckedChangeListener((b, checked) ->
                    prefs.edit().putBoolean(enabledKey, checked).apply());
        } else {
            // No dedicated enabled flag for this provider — treat "has a key/value" as enabled.
            binding.switchEnabled.setChecked(hasAnyFieldValue());
            binding.switchEnabled.setEnabled(false);
        }

        String multiKeyPrefKey = "multi_key_mode_" + slug(spec.title);
        binding.switchMultiKey.setChecked(prefs.getBoolean(multiKeyPrefKey, false));
        binding.switchMultiKey.setOnCheckedChangeListener((b, checked) ->
                prefs.edit().putBoolean(multiKeyPrefKey, checked).apply());

        String responseApiPrefKey = "response_api_mode_" + slug(spec.title);
        binding.switchResponseApi.setChecked(prefs.getBoolean(responseApiPrefKey, false));
        binding.switchResponseApi.setOnCheckedChangeListener((b, checked) ->
                prefs.edit().putBoolean(responseApiPrefKey, checked).apply());

        binding.rowProviderType.setOnClickListener(v ->
                Toast.makeText(this, spec.title, Toast.LENGTH_SHORT).show());

        binding.rowGroup.setOnClickListener(v -> showGroupPicker());

        binding.rowAccountBalance.setOnClickListener(v -> showAccountBalance());

        binding.rowNetwork.setOnClickListener(v -> showNetworkSettings());
    }

    private boolean isLocalGroupProvider() {
        String t = spec.title.toLowerCase();
        return t.contains("compatible") || t.contains("litellm") || t.contains("bedrock");
    }

    @Nullable
    private VoidPortSettings.FieldSpec primaryField() {
        return spec.fields.isEmpty() ? null : spec.fields.get(0);
    }

    private boolean hasAnyFieldValue() {
        for (VoidPortSettings.FieldSpec field : spec.fields) {
            String value = prefs.getString(field.prefKey, "");
            if (value != null && !value.trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void showGroupPicker() {
        String[] options = {"Other", "Local", "Custom"};
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.ia_group_label))
                .setItems(options, (dialog, which) -> binding.tvGroupValue.setText(options[which]))
                .show();
    }

    private void showAccountBalance() {
        Toast.makeText(this, "Balance lookup is not available for " + spec.title + " yet.",
                Toast.LENGTH_SHORT).show();
    }

    private void showNetworkSettings() {
        Toast.makeText(this, "Network proxy settings coming soon.", Toast.LENGTH_SHORT).show();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Core fields: Name / API Key / Base URL / API Path
    // ─────────────────────────────────────────────────────────────────────

    private void bindCoreFields() {
        binding.etProviderName.setText(spec.title);
        binding.etProviderName.setEnabled(false); // display name follows the spec title

        VoidPortSettings.FieldSpec apiKeyField = findFieldByLabel("API Key");
        if (apiKeyField != null) {
            binding.tilApiKey.setVisibility(View.VISIBLE);
            String currentKey = prefs.getString(apiKeyField.prefKey, "");
            binding.etApiKey.setText(currentKey);
            binding.etApiKey.addTextChangedListener(simpleWatcher(text ->
                    prefs.edit().putString(apiKeyField.prefKey, text).apply()));
        } else {
            binding.tilApiKey.setVisibility(View.GONE);
        }

        VoidPortSettings.FieldSpec baseUrlField = findFieldByLabel("Base URL");
        String baseUrlKey = baseUrlField != null ? baseUrlField.prefKey : ("base_url_override_" + slug(spec.title));
        String defaultBaseUrl = baseUrlField != null ? baseUrlField.defaultValue : defaultBaseUrlFor(spec.title);
        binding.etApiBaseUrl.setText(prefs.getString(baseUrlKey, defaultBaseUrl));
        binding.etApiBaseUrl.addTextChangedListener(simpleWatcher(text ->
                prefs.edit().putString(baseUrlKey, text).apply()));

        String apiPathKey = "api_path_override_" + slug(spec.title);
        binding.etApiPath.setText(prefs.getString(apiPathKey, ""));
        binding.etApiPath.addTextChangedListener(simpleWatcher(text ->
                prefs.edit().putString(apiPathKey, text).apply()));
    }

    @Nullable
    private VoidPortSettings.FieldSpec findFieldByLabel(String labelContains) {
        for (VoidPortSettings.FieldSpec field : spec.fields) {
            if (field.label.toLowerCase().contains(labelContains.toLowerCase())) {
                return field;
            }
        }
        return null;
    }

    private String defaultBaseUrlFor(String title) {
        switch (title) {
            case "OpenAI": return "https://api.openai.com/v1";
            case "Anthropic": return "https://api.anthropic.com/v1";
            case "DeepSeek": return "https://api.deepseek.com/v1";
            case "OpenRouter": return "https://openrouter.ai/api/v1";
            case "Gemini": return "https://generativelanguage.googleapis.com/v1beta/openai";
            case "Groq": return "https://api.groq.com/openai/v1";
            case "Grok (xAI)": return "https://api.x.ai/v1";
            case "Mistral": return "https://api.mistral.ai/v1";
            default: return "";
        }
    }

    /**
     * Any field not covered by the core 4 (Name/API Key/Base URL/API Path) is
     * rendered as an extra labelled input below — e.g. Vertex AI's Region/Project,
     * or Azure's Resource/API Version.
     */
    private void bindExtraFields() {
        binding.extraFieldsContainer.removeAllViews();
        for (VoidPortSettings.FieldSpec field : spec.fields) {
            boolean isCoreField = field.label.toLowerCase().contains("api key")
                    || field.label.toLowerCase().contains("base url");
            if (isCoreField) continue;

            TextView label = new TextView(this);
            label.setText(field.label);
            label.setTextColor(getColor(R.color.chat_text_secondary));
            label.setTextSize(13);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            labelParams.bottomMargin = dp(6);
            label.setLayoutParams(labelParams);
            binding.extraFieldsContainer.addView(label);

            TextInputLayout layout = new TextInputLayout(this);
            layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
            layout.setBoxCornerRadii(dp(14), dp(14), dp(14), dp(14));
            layout.setHintEnabled(false);
            if (field.password) {
                layout.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
            }
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            layoutParams.bottomMargin = dp(20);
            layout.setLayoutParams(layoutParams);

            TextInputEditText edit = new TextInputEditText(this);
            edit.setText(prefs.getString(field.prefKey, field.defaultValue));
            if (field.password) {
                edit.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            }
            edit.addTextChangedListener(simpleWatcher(text ->
                    prefs.edit().putString(field.prefKey, text).apply()));
            layout.addView(edit);
            binding.extraFieldsContainer.addView(layout);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Config / Models tab switcher
    // ─────────────────────────────────────────────────────────────────────

    private void bindTabs() {
        binding.tabConfig.setOnClickListener(v -> setModelsTabVisible(false));
        binding.tabModels.setOnClickListener(v -> setModelsTabVisible(true));
        binding.btnFetchModels.setOnClickListener(v -> fetchModels());
        binding.btnAddModel.setOnClickListener(v -> showAddModelSheet());
        setModelsTabVisible(false);
    }

    private void setModelsTabVisible(boolean modelsVisible) {
        showingModelsTab = modelsVisible;
        binding.tabConfigContent.setVisibility(modelsVisible ? View.GONE : View.VISIBLE);
        binding.tabModelsContent.setVisibility(modelsVisible ? View.VISIBLE : View.GONE);
        binding.modelsActionBar.setVisibility(modelsVisible ? View.VISIBLE : View.GONE);

        int accent = getColor(R.color.chat_accent);
        int secondary = getColor(R.color.chat_text_secondary);
        binding.iconTabConfig.setColorFilter(modelsVisible ? secondary : accent);
        binding.labelTabConfig.setTextColor(modelsVisible ? secondary : accent);
        binding.iconTabModels.setColorFilter(modelsVisible ? accent : secondary);
        binding.labelTabModels.setTextColor(modelsVisible ? accent : secondary);
    }

    private void fetchModels() {
        Toast.makeText(this, "Fetching models from " + spec.title + "…", Toast.LENGTH_SHORT).show();
        // Real model-list fetching would call the provider's /models endpoint here.
        // Left as a stub since it requires a live network call per-provider.
    }

    private void refreshModelsTab() {
        List<String> models = loadModelsForProvider();
        boolean empty = models.isEmpty();
        binding.modelsEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.rvProviderModels.setVisibility(empty ? View.GONE : View.VISIBLE);

        if (!empty) {
            binding.rvProviderModels.setLayoutManager(new LinearLayoutManager(this));
            binding.rvProviderModels.setAdapter(new ModelsAdapter(models));
        }
    }

    private List<String> loadModelsForProvider() {
        List<String> result = new ArrayList<>();
        JSONArray raw = readCustomModelsArray();
        for (int i = 0; i < raw.length(); i++) {
            JSONObject item = raw.optJSONObject(i);
            if (item == null) continue;
            if (spec.title.equals(item.optString("providerLabel"))) {
                String model = item.optString("model", "");
                if (!model.isEmpty()) result.add(model);
            }
        }
        return result;
    }

    private JSONArray readCustomModelsArray() {
        try {
            return new JSONArray(prefs.getString(VoidPortSettings.PREF_CUSTOM_MODELS, "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private void removeModel(String model) {
        JSONArray raw = readCustomModelsArray();
        JSONArray updated = new JSONArray();
        for (int i = 0; i < raw.length(); i++) {
            JSONObject item = raw.optJSONObject(i);
            if (item == null) continue;
            boolean matches = spec.title.equals(item.optString("providerLabel"))
                    && model.equals(item.optString("model"));
            if (!matches) updated.put(item);
        }
        prefs.edit().putString(VoidPortSettings.PREF_CUSTOM_MODELS, updated.toString()).apply();
        refreshModelsTab();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Add Model bottom sheet
    // ─────────────────────────────────────────────────────────────────────

    private void showAddModelSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        BottomSheetAddModelBinding sheet = BottomSheetAddModelBinding.inflate(LayoutInflater.from(this));
        dialog.setContentView(sheet.getRoot());

        setupSegmentedTabs(sheet);
        setupChipToggle(sheet.chipTypeChat, sheet.chipTypeEmbedding);
        setupChipToggle(sheet.chipInputText, sheet.chipInputImage);
        setupChipToggle(sheet.chipOutputText, sheet.chipOutputImage);

        sheet.btnCloseAddModel.setOnClickListener(v -> dialog.dismiss());

        sheet.btnConfirmAddModel.setOnClickListener(v -> {
            String modelId = textOf(sheet.etModelId);
            if (modelId.isEmpty()) {
                sheet.etModelId.setError(getString(R.string.ia_model_id_hint));
                return;
            }
            String modelName = textOf(sheet.etModelName);
            saveCustomModel(modelId, modelName.isEmpty() ? modelId : modelName);
            dialog.dismiss();
            refreshModelsTab();
        });

        BottomSheetDialog finalDialog = dialog;
        dialog.setOnShowListener(d -> {
            com.google.android.material.bottomsheet.BottomSheetBehavior<?> behavior =
                    finalDialog.getBehavior();
            behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
        });

        dialog.show();
    }

    private void setupSegmentedTabs(BottomSheetAddModelBinding sheet) {
        View[] tabs = {sheet.tabBasic, sheet.tabAdvanced, sheet.tabBuiltinTools};
        View[] panels = {sheet.panelBasic, sheet.panelAdvanced, sheet.panelBuiltinTools};

        for (int i = 0; i < tabs.length; i++) {
            int index = i;
            tabs[i].setOnClickListener(v -> {
                for (int j = 0; j < tabs.length; j++) {
                    boolean active = j == index;
                    panels[j].setVisibility(active ? View.VISIBLE : View.GONE);
                    TextView t = (TextView) tabs[j];
                    t.setBackground(active
                            ? androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_segmented_tab_selected)
                            : null);
                    t.setTextColor(active
                            ? getColor(R.color.chat_accent)
                            : getColor(R.color.chat_text_primary));
                    t.setTypeface(null, active ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
                }
            });
        }
    }

    private void setupChipToggle(MaterialButton chipA, MaterialButton chipB) {
        chipA.setOnClickListener(v -> selectChip(chipA, chipB));
        chipB.setOnClickListener(v -> selectChip(chipB, chipA));
    }

    private void selectChip(MaterialButton selected, MaterialButton other) {
        selected.setIconResource(R.drawable.ic_mtrl_check);
        selected.setIconTint(android.content.res.ColorStateList.valueOf(getColor(R.color.chat_accent)));
        selected.setTextColor(getColor(R.color.chat_accent));
        selected.setStrokeColorResource(R.color.chat_accent);

        other.setIcon(null);
        other.setTextColor(getColor(R.color.chat_text_secondary));
        other.setStrokeColorResource(R.color.chat_border);
    }

    private void saveCustomModel(String modelId, String modelName) {
        JSONArray models = readCustomModelsArray();
        try {
            JSONObject customModel = new JSONObject();
            customModel.put("providerId", slug(spec.title));
            customModel.put("providerLabel", spec.title);
            customModel.put("model", modelId);
            customModel.put("modelName", modelName);
            models.put(customModel);
            prefs.edit().putString(VoidPortSettings.PREF_CUSTOM_MODELS, models.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    private String textOf(TextInputEditText edit) {
        return edit.getText() == null ? "" : edit.getText().toString().trim();
    }

    private interface TextChanged {
        void onChanged(String text);
    }

    private TextWatcher simpleWatcher(TextChanged callback) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                callback.onChanged(s == null ? "" : s.toString());
            }
        };
    }

    private String slug(String text) {
        return text.toLowerCase().replaceAll("[^a-z0-9]+", "_");
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Models RecyclerView adapter
    // ─────────────────────────────────────────────────────────────────────

    private final class ModelsAdapter extends RecyclerView.Adapter<ModelsAdapter.Holder> {
        private final List<String> models;

        ModelsAdapter(List<String> models) {
            this.models = models;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemProviderModelRowBinding rowBinding = ItemProviderModelRowBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new Holder(rowBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            String model = models.get(position);
            holder.binding.modelName.setText(model);
            holder.binding.btnDeleteModel.setOnClickListener(v -> removeModel(model));
        }

        @Override
        public int getItemCount() {
            return models.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ItemProviderModelRowBinding binding;

            Holder(ItemProviderModelRowBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
