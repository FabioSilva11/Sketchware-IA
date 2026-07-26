package pro.sketchware.activities.settings;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Base64;
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
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import pro.sketchware.R;
import pro.sketchware.ai.config.AiSettingsRepository;
import pro.sketchware.activities.chat.port.VoidPortSettings;
import pro.sketchware.databinding.ActivityProviderDetailBinding;
import pro.sketchware.databinding.BottomSheetAddModelBinding;
import pro.sketchware.databinding.ItemProviderModelRowBinding;

public class ProviderDetailActivity extends AppCompatActivity {

    public static final String EXTRA_PROVIDER_TITLE = "provider_title";
    public static final String EXTRA_PROVIDER_ID = "provider_id";

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();

    private ActivityProviderDetailBinding binding;
    private SharedPreferences prefs;
    private VoidPortSettings.ProviderCardSpec spec;
    private String providerId;
    private boolean showingModelsTab = false;
    private final Handler balanceSyncHandler = new Handler(Looper.getMainLooper());
    private final Runnable balanceSyncRunnable = this::syncProviderBalanceIfPositive;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProviderDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        prefs = new AiSettingsRepository(this).preferences();
        String title = getIntent().getStringExtra(EXTRA_PROVIDER_TITLE);
        String id = getIntent().getStringExtra(EXTRA_PROVIDER_ID);
        spec = findSpec(id, title);
        if (spec == null) {
            finish();
            return;
        }
        providerId = spec.providerId;

        setupToolbar();
        bindManageCard();
        bindCoreFields();
        bindExtraFields();
        bindTabs();
        refreshModelsTab();
    }

    @Override
    protected void onDestroy() {
        balanceSyncHandler.removeCallbacks(balanceSyncRunnable);
        super.onDestroy();
    }

    @Nullable
    private VoidPortSettings.ProviderCardSpec findSpec(@Nullable String id, @Nullable String title) {
        for (VoidPortSettings.ProviderCardSpec candidate : VoidPortSettings.getProviderCards(prefs)) {
            if ((id != null && candidate.providerId.equals(id))
                    || (title != null && candidate.title.equals(title))) {
                return candidate;
            }
        }
        return null;
    }

    private void setupToolbar() {
        binding.topAppBar.setTitle(spec.title);
        binding.topAppBar.setNavigationOnClickListener(v -> finish());
        binding.topAppBar.inflateMenu(R.menu.menu_provider_detail);
        binding.topAppBar.getMenu().findItem(R.id.action_delete_provider)
                .setVisible(spec.custom);
        binding.topAppBar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_test_provider) {
                testProviderConnection();
                return true;
            }
            if (id == R.id.action_share_provider) {
                shareCurrentProvider();
                return true;
            }
            if (id == R.id.action_delete_provider) {
                confirmDeleteProvider();
                return true;
            }
            return false;
        });
    }

    private void bindManageCard() {
        binding.tvProviderTypeValue.setText(providerFamilyLabel());
        binding.tvGroupValue.setText(prefs.getString(groupPrefKey(), defaultGroup()));

        VoidPortSettings.FieldSpec primaryField = primaryField();
        String enabledKey = primaryField != null ? primaryField.enabledKey : null;
        if (spec.custom) {
            enabledKey = VoidPortSettings.providerPrefKey(providerId, "enabled");
        }

        if (enabledKey != null) {
            binding.switchEnabled.setChecked(prefs.getBoolean(enabledKey, spec.custom
                    ? currentCustomConfig().optBoolean("enabled", true)
                    : true));
            String finalEnabledKey = enabledKey;
            binding.switchEnabled.setOnCheckedChangeListener((b, checked) -> {
                prefs.edit().putBoolean(finalEnabledKey, checked).apply();
                if (spec.custom) {
                    VoidPortSettings.updateProviderConfigValue(prefs, providerId, "enabled", checked);
                }
            });
        } else {
            binding.switchEnabled.setChecked(hasAnyFieldValue());
            binding.switchEnabled.setEnabled(false);
        }

        String multiKeyPrefKey = "multi_key_mode_" + providerId;
        boolean multiKeyEnabled = prefs.getBoolean(multiKeyPrefKey,
                currentCustomConfig().optBoolean("multiKeyEnabled", false));
        binding.switchMultiKey.setChecked(multiKeyEnabled);
        binding.switchMultiKey.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean(multiKeyPrefKey, checked).apply();
            if (spec.custom) {
                VoidPortSettings.updateProviderConfigValue(prefs, providerId, "multiKeyEnabled", checked);
            }
        });

        String responseApiPrefKey = "response_api_mode_" + providerId;
        binding.switchResponseApi.setChecked(prefs.getBoolean(responseApiPrefKey,
                currentCustomConfig().optBoolean("useResponseApi", false)));
        binding.switchResponseApi.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean(responseApiPrefKey, checked).apply();
            if (spec.custom) {
                VoidPortSettings.updateProviderConfigValue(prefs, providerId, "useResponseApi", checked);
            }
        });

        binding.rowProviderType.setOnClickListener(v ->
                Toast.makeText(this, providerFamilyLabel(), Toast.LENGTH_SHORT).show());
        binding.rowGroup.setOnClickListener(v -> showGroupPicker());
        binding.rowAccountBalance.setOnClickListener(v -> showAccountBalance());
        binding.rowNetwork.setOnClickListener(v -> showNetworkSettings());
    }

    private String providerFamilyLabel() {
        String family = providerFamily();
        return switch (family) {
            case "gemini" -> "Gemini";
            case "anthropic" -> "Anthropic";
            case "openai_compatible" -> "OpenAI-Compatible";
            default -> "OpenAI";
        };
    }

    private String providerFamily() {
        JSONObject config = currentCustomConfig();
        if (config.length() > 0) {
            return VoidPortSettings.providerType(config);
        }
        if ("gemini".equals(providerId)) {
            return "gemini";
        }
        if ("anthropic".equals(providerId)) {
            return "anthropic";
        }
        if ("openai_compatible".equals(providerId) || "litellm".equals(providerId)
                || "minimax".equals(providerId)) {
            return "openai_compatible";
        }
        return "openai";
    }

    private String defaultGroup() {
        return "openai_compatible".equals(providerFamily()) ? "Custom" : "Other";
    }

    private String groupPrefKey() {
        return "provider_group_" + providerId;
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
        String[] options = {"Other", "Custom", "Local"};
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.ia_group_label))
                .setItems(options, (dialog, which) -> {
                    binding.tvGroupValue.setText(options[which]);
                    prefs.edit().putString(groupPrefKey(), options[which]).apply();
                })
                .show();
    }

    private void showAccountBalance() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(4), dp(4), dp(4), 0);

        MaterialSwitch enabled = new MaterialSwitch(this);
        enabled.setText(R.string.ia_balance_enabled);
        enabled.setChecked(balanceEnabled());
        container.addView(enabled);

        TextInputEditText apiPath = addDialogInput(container, R.string.ia_balance_api_path, false);
        TextInputEditText resultPath = addDialogInput(container, R.string.ia_balance_result_path, false);
        apiPath.setText(balanceApiPath());
        resultPath.setText(balanceResultPath());

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ia_get_account_balance_label)
                .setView(container)
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.ia_save, (dialog, which) ->
                        saveBalanceSettings(enabled.isChecked(), textOf(apiPath), textOf(resultPath)))
                .setPositiveButton(R.string.ia_balance_query, (dialog, which) -> {
                    saveBalanceSettings(true, textOf(apiPath), textOf(resultPath));
                    queryBalance(textOf(apiPath), textOf(resultPath));
                })
                .show();
    }

    private void showNetworkSettings() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(4), dp(4), dp(4), 0);

        MaterialSwitch enabled = new MaterialSwitch(this);
        enabled.setText(R.string.ia_enable_proxy);
        enabled.setChecked(networkBool("proxyEnabled", false));
        container.addView(enabled);

        TextInputEditText type = addDialogInput(container, R.string.ia_proxy_type, false);
        TextInputEditText host = addDialogInput(container, R.string.ia_proxy_host, false);
        TextInputEditText port = addDialogInput(container, R.string.ia_proxy_port, false);
        TextInputEditText user = addDialogInput(container, R.string.ia_proxy_username, false);
        TextInputEditText password = addDialogInput(container, R.string.ia_proxy_password, true);

        type.setText(networkString("proxyType", "http"));
        host.setText(networkString("proxyHost", ""));
        port.setText(networkString("proxyPort", "8080"));
        user.setText(networkString("proxyUsername", ""));
        password.setText(networkString("proxyPassword", ""));

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ia_network_label)
                .setView(container)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.ia_save, (dialog, which) -> {
                    saveNetworkSettings(enabled.isChecked(), textOf(type), textOf(host),
                            textOf(port), textOf(user), textOf(password));
                    Toast.makeText(this, R.string.ia_network_saved, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private TextInputEditText addDialogInput(LinearLayout container, int labelRes, boolean password) {
        TextInputLayout layout = new TextInputLayout(this);
        layout.setHint(getString(labelRes));
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        layout.setBoxCornerRadii(dp(12), dp(12), dp(12), dp(12));
        if (password) {
            layout.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(12);
        layout.setLayoutParams(params);

        TextInputEditText edit = new TextInputEditText(this);
        if (password) {
            edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        layout.addView(edit);
        container.addView(layout);
        return edit;
    }

    private void bindCoreFields() {
        binding.etProviderName.setText(spec.title);
        binding.etProviderName.setEnabled(spec.custom);
        if (spec.custom) {
            binding.etProviderName.addTextChangedListener(simpleWatcher(text -> {
                if (!text.trim().isEmpty()) {
                    VoidPortSettings.updateProviderConfigValue(prefs, providerId, "name", text.trim());
                    binding.topAppBar.setTitle(text.trim());
                }
            }));
        }

        VoidPortSettings.FieldSpec apiKeyField = findFieldByLabel("API Key");
        if (apiKeyField != null) {
            binding.tilApiKey.setVisibility(View.VISIBLE);
            binding.etApiKey.setText(prefs.getString(apiKeyField.prefKey, apiKeyField.defaultValue));
            binding.etApiKey.addTextChangedListener(simpleWatcher(text -> {
                prefs.edit().putString(apiKeyField.prefKey, text).apply();
                if (spec.custom) {
                    VoidPortSettings.updateProviderConfigValue(prefs, providerId, "apiKey", text);
                }
                scheduleProviderBalanceSync();
            }));
        } else {
            binding.tilApiKey.setVisibility(View.GONE);
        }

        VoidPortSettings.FieldSpec baseUrlField = findFieldByLabel("Base URL");
        String baseUrlKey = baseUrlField != null ? baseUrlField.prefKey : ("base_url_override_" + providerId);
        String defaultBaseUrl = baseUrlField != null ? baseUrlField.defaultValue : defaultBaseUrlFor(providerId);
        binding.etApiBaseUrl.setText(prefs.getString(baseUrlKey, defaultBaseUrl));
        binding.etApiBaseUrl.addTextChangedListener(simpleWatcher(text -> {
            prefs.edit().putString(baseUrlKey, text).apply();
            if (spec.custom) {
                VoidPortSettings.updateProviderConfigValue(prefs, providerId, "baseUrl", text);
            }
            scheduleProviderBalanceSync();
        }));

        String apiPathKey = spec.custom
                ? VoidPortSettings.providerPrefKey(providerId, "api_path")
                : "api_path_override_" + providerId;
        String defaultPath = spec.custom
                ? currentCustomConfig().optString("chatPath", VoidPortSettings.defaultChatPathForProviderType(providerFamily()))
                : "";
        binding.etApiPath.setText(prefs.getString(apiPathKey, defaultPath));
        binding.etApiPath.addTextChangedListener(simpleWatcher(text -> {
            prefs.edit().putString(apiPathKey, text).apply();
            if (spec.custom) {
                VoidPortSettings.updateProviderConfigValue(prefs, providerId, "chatPath", text);
            }
        }));
    }

    @Nullable
    private VoidPortSettings.FieldSpec findFieldByLabel(String labelContains) {
        for (VoidPortSettings.FieldSpec field : spec.fields) {
            if (field.label.toLowerCase(Locale.US).contains(labelContains.toLowerCase(Locale.US))) {
                return field;
            }
        }
        return null;
    }

    private String defaultBaseUrlFor(String id) {
        return switch (id) {
            case "openai" -> "https://api.openai.com/v1";
            case "anthropic" -> "https://api.anthropic.com/v1";
            case "deepseek" -> "https://api.deepseek.com";
            case "openrouter" -> "https://openrouter.ai/api/v1";
            case "gemini" -> "https://generativelanguage.googleapis.com/v1beta";
            case "groq" -> "https://api.groq.com/openai/v1";
            case "mistral" -> "https://api.mistral.ai/v1";
            case "minimax" -> "https://api.minimax.io/v1";
            default -> VoidPortSettings.defaultBaseForProviderType(providerFamily());
        };
    }

    private void bindExtraFields() {
        binding.extraFieldsContainer.removeAllViews();
        for (VoidPortSettings.FieldSpec field : spec.fields) {
            boolean isCoreField = field.label.toLowerCase(Locale.US).contains("api key")
                    || field.label.toLowerCase(Locale.US).contains("base url");
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
                edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            }
            edit.addTextChangedListener(simpleWatcher(text -> {
                prefs.edit().putString(field.prefKey, text).apply();
                if (spec.custom && field.label.toLowerCase(Locale.US).contains("headers")) {
                    VoidPortSettings.updateProviderConfigValue(prefs, providerId, "headers", text);
                }
            }));
            layout.addView(edit);
            binding.extraFieldsContainer.addView(layout);
        }
    }

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
        binding.btnFetchModels.setEnabled(false);
        Toast.makeText(this, getString(R.string.ia_fetching_models, spec.title), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                List<String> models = fetchModelsForCurrentProvider();
                if (models.isEmpty()) {
                    throw new Exception(getString(R.string.ia_no_models_title));
                }
                saveFetchedModels(models);
                runOnUiThread(() -> {
                    binding.btnFetchModels.setEnabled(true);
                    refreshModelsTab();
                    Toast.makeText(this,
                            getString(R.string.ia_models_fetched, models.size()),
                            Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    binding.btnFetchModels.setEnabled(true);
                    Toast.makeText(this,
                            getString(R.string.ia_fetch_models_failed, e.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
            }
        }, "provider-fetch-models").start();
    }

    private List<String> fetchModelsForCurrentProvider() throws Exception {
        String family = providerFamily();
        if ("gemini".equals(family)) {
            return fetchGeminiModels();
        }
        if ("anthropic".equals(family)) {
            return fetchAnthropicModels();
        }
        return fetchOpenAiCompatibleModels();
    }

    private List<String> fetchOpenAiCompatibleModels() throws Exception {
        JSONObject json = fetchJson(modelListUrl(textOf(binding.etApiBaseUrl)), openAiHeaders());
        JSONArray data = json.optJSONArray("data");
        Set<String> names = new LinkedHashSet<>();
        for (int i = 0; data != null && i < data.length(); i++) {
            JSONObject model = data.optJSONObject(i);
            String id = model == null ? "" : model.optString("id", "");
            if (!id.isEmpty()) {
                names.add(id);
            }
        }
        return new ArrayList<>(names);
    }

    private List<String> fetchGeminiModels() throws Exception {
        String apiKey = textOf(binding.etApiKey);
        if (apiKey.isEmpty()) {
            throw new Exception(getString(R.string.ia_api_key_required));
        }
        // Send the key via header instead of query string so it never appears in URLs/logs.
        String url = trimTrailingSlash(textOf(binding.etApiBaseUrl)) + "/models";
        JSONObject json = fetchJson(url, new Headers.Builder().add("x-goog-api-key", apiKey).build());
        JSONArray models = json.optJSONArray("models");
        Set<String> names = new LinkedHashSet<>();
        for (int i = 0; models != null && i < models.length(); i++) {
            JSONObject model = models.optJSONObject(i);
            String name = model == null ? "" : model.optString("name", "");
            if (name.startsWith("models/")) {
                name = name.substring("models/".length());
            }
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return new ArrayList<>(names);
    }

    private List<String> fetchAnthropicModels() throws Exception {
        JSONObject json = fetchJson(trimTrailingSlash(textOf(binding.etApiBaseUrl)) + "/models", anthropicHeaders());
        JSONArray data = json.optJSONArray("data");
        Set<String> names = new LinkedHashSet<>();
        for (int i = 0; data != null && i < data.length(); i++) {
            JSONObject model = data.optJSONObject(i);
            String id = model == null ? "" : model.optString("id", "");
            if (!id.isEmpty()) {
                names.add(id);
            }
        }
        return new ArrayList<>(names);
    }

    private JSONObject fetchJson(String url, Headers headers) throws Exception {
        Request request = new Request.Builder().url(url).headers(headers).get().build();
        try (Response response = CLIENT.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new Exception("HTTP " + response.code() + ": " + compact(body));
            }
            return new JSONObject(body);
        }
    }

    private Headers openAiHeaders() {
        Headers.Builder headers = new Headers.Builder();
        String key = activeApiKey();
        if (!key.isEmpty()) {
            headers.add("Authorization", "Bearer " + key);
        }
        addExtraHeaders(headers);
        return headers.build();
    }

    private Headers anthropicHeaders() {
        Headers.Builder headers = new Headers.Builder();
        String key = activeApiKey();
        if (!key.isEmpty()) {
            headers.add("x-api-key", key);
        }
        headers.add("anthropic-version", "2023-06-01");
        addExtraHeaders(headers);
        return headers.build();
    }

    private void addExtraHeaders(Headers.Builder headers) {
        VoidPortSettings.FieldSpec headersField = findFieldByLabel("Headers");
        if (headersField == null) {
            return;
        }
        try {
            JSONObject json = new JSONObject(prefs.getString(headersField.prefKey, "{}"));
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

    private String activeApiKey() {
        String raw = textOf(binding.etApiKey);
        if (!binding.switchMultiKey.isChecked()) {
            return raw;
        }
        for (String part : raw.split("[\\n,;]")) {
            String candidate = part.trim();
            if (!candidate.isEmpty()) {
                return candidate;
            }
        }
        return raw;
    }

    private void saveFetchedModels(List<String> models) {
        JSONArray raw = readCustomModelsArray();
        JSONArray updated = new JSONArray();
        for (int i = 0; i < raw.length(); i++) {
            JSONObject item = raw.optJSONObject(i);
            if (item == null) continue;
            boolean sameProvider = providerId.equals(item.optString("providerId", ""));
            boolean autodetected = item.optBoolean("autodetected", false);
            if (!(sameProvider && autodetected)) {
                updated.put(item);
            }
        }
        for (String model : models) {
            putModel(updated, model, model, true);
        }
        prefs.edit()
                .putString(VoidPortSettings.PREF_CUSTOM_MODELS, updated.toString())
                .putString(VoidPortSettings.PREF_CURRENT_PROVIDER, providerId)
                .apply();
        if (spec.custom) {
            JSONObject config = currentCustomConfig();
            try {
                JSONArray configModels = new JSONArray();
                for (String model : models) {
                    configModels.put(model);
                }
                config.put("models", configModels);
                VoidPortSettings.saveProviderConfig(prefs, config);
            } catch (Exception ignored) {
            }
        }
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
        Set<String> result = new LinkedHashSet<>();
        JSONObject config = currentCustomConfig();
        JSONArray configModels = config.optJSONArray("models");
        for (int i = 0; configModels != null && i < configModels.length(); i++) {
            String model = configModels.optString(i, "");
            if (!model.isEmpty()) {
                result.add(model);
            }
        }
        JSONArray raw = readCustomModelsArray();
        for (int i = 0; i < raw.length(); i++) {
            JSONObject item = raw.optJSONObject(i);
            if (item == null) continue;
            boolean matches = providerId.equals(item.optString("providerId", ""))
                    || spec.title.equals(item.optString("providerLabel"));
            if (matches) {
                String model = item.optString("model", "");
                if (!model.isEmpty()) result.add(model);
            }
        }
        return new ArrayList<>(result);
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
            boolean matches = providerId.equals(item.optString("providerId", ""))
                    && model.equals(item.optString("model"));
            if (!matches) updated.put(item);
        }
        prefs.edit().putString(VoidPortSettings.PREF_CUSTOM_MODELS, updated.toString()).apply();
        if (spec.custom) {
            JSONObject config = currentCustomConfig();
            JSONArray models = new JSONArray();
            for (String existing : loadModelsForProvider()) {
                if (!model.equals(existing)) {
                    models.put(existing);
                }
            }
            try {
                config.put("models", models);
                VoidPortSettings.saveProviderConfig(prefs, config);
            } catch (JSONException ignored) {
            }
        }
        refreshModelsTab();
    }

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
        dialog.setOnShowListener(d -> finalDialog.getBehavior()
                .setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED));
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
        for (int i = 0; i < models.length(); i++) {
            JSONObject item = models.optJSONObject(i);
            if (item != null
                    && providerId.equals(item.optString("providerId", ""))
                    && modelId.equals(item.optString("model", ""))) {
                return;
            }
        }
        putModel(models, modelId, modelName, false);
        prefs.edit().putString(VoidPortSettings.PREF_CUSTOM_MODELS, models.toString()).apply();
        if (spec.custom) {
            JSONObject config = currentCustomConfig();
            JSONArray configModels = config.optJSONArray("models");
            if (configModels == null) {
                configModels = new JSONArray();
            }
            configModels.put(modelId);
            try {
                config.put("models", configModels);
                VoidPortSettings.saveProviderConfig(prefs, config);
            } catch (JSONException ignored) {
            }
        }
    }

    private void putModel(JSONArray target, String modelId, String modelName, boolean autodetected) {
        if (modelId == null || modelId.trim().isEmpty()) {
            return;
        }
        JSONObject customModel = new JSONObject();
        try {
            customModel.put("providerId", providerId);
            customModel.put("providerLabel", spec.title);
            customModel.put("model", modelId.trim());
            customModel.put("modelName", modelName == null || modelName.trim().isEmpty()
                    ? modelId.trim()
                    : modelName.trim());
            customModel.put("autodetected", autodetected);
            target.put(customModel);
        } catch (JSONException ignored) {
        }
    }

    private void testProviderConnection() {
        Toast.makeText(this, R.string.ia_testing_provider, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                List<String> models = fetchModelsForCurrentProvider();
                runOnUiThread(() -> Toast.makeText(this,
                        getString(R.string.ia_provider_test_ok, models.size()),
                        Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        getString(R.string.ia_provider_test_failed, e.getMessage()),
                        Toast.LENGTH_LONG).show());
            }
        }, "provider-test").start();
    }

    private void shareCurrentProvider() {
        JSONObject payload = new JSONObject();
        try {
            String type = providerFamily();
            payload.put("type", "gemini".equals(type) ? "google" : ("anthropic".equals(type) ? "claude" : "openai"));
            payload.put("name", textOf(binding.etProviderName).isEmpty() ? spec.title : textOf(binding.etProviderName));
            payload.put("apiKey", textOf(binding.etApiKey));
            if (!"gemini".equals(type)) {
                payload.put("baseUrl", textOf(binding.etApiBaseUrl));
            }
        } catch (JSONException ignored) {
        }
        String encoded = Base64.encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        String code = "ai-provider:v1:" + encoded;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Provider", code));
        }

        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, code);
        startActivity(Intent.createChooser(share, getString(R.string.ia_share_provider)));
    }

    private void confirmDeleteProvider() {
        if (!spec.custom) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ia_delete_provider)
                .setMessage(getString(R.string.ia_delete_provider_message, spec.title))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.ia_delete_provider, (dialog, which) -> {
                    VoidPortSettings.removeProviderConfig(prefs, providerId);
                    finish();
                })
                .show();
    }

    private void queryBalance(String apiPath, String resultPath) {
        if (!"openai".equals(providerFamily()) && !"openai_compatible".equals(providerFamily())) {
            Toast.makeText(this, R.string.ia_balance_openai_only, Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, R.string.ia_balance_querying, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                String url = balanceUrl(textOf(binding.etApiBaseUrl), apiPath);
                JSONObject json = fetchJson(url, openAiHeaders());
                String balance = formatBalance(json, resultPath);
                runOnUiThread(() -> Toast.makeText(this,
                        getString(R.string.ia_balance_result, balance),
                        Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        getString(R.string.ia_balance_error, e.getMessage()),
                        Toast.LENGTH_LONG).show());
            }
        }, "provider-balance").start();
    }

    private void saveBalanceSettings(boolean enabled, String apiPath, String resultPath) {
        prefs.edit()
                .putBoolean("provider_balance_enabled_" + providerId, enabled)
                .putString("provider_balance_api_path_" + providerId, apiPath)
                .putString("provider_balance_result_path_" + providerId, resultPath)
                .apply();
        if (spec.custom) {
            VoidPortSettings.updateProviderConfigValue(prefs, providerId, "balanceEnabled", enabled);
            VoidPortSettings.updateProviderConfigValue(prefs, providerId, "balanceApiPath", apiPath);
            VoidPortSettings.updateProviderConfigValue(prefs, providerId, "balanceResultPath", resultPath);
        }
        scheduleProviderBalanceSync();
    }

    private void scheduleProviderBalanceSync() {
        balanceSyncHandler.removeCallbacks(balanceSyncRunnable);
        balanceSyncHandler.postDelayed(balanceSyncRunnable, 1200);
    }

    private void syncProviderBalanceIfPositive() {
        AiProviderBalanceSyncService.syncIfPositive(
                prefs,
                providerId,
                textOf(binding.etProviderName).isEmpty() ? spec.title : textOf(binding.etProviderName),
                providerFamily(),
                activeApiKey(),
                textOf(binding.etApiBaseUrl)
        );
    }

    private boolean balanceEnabled() {
        return prefs.getBoolean("provider_balance_enabled_" + providerId,
                currentCustomConfig().optBoolean("balanceEnabled", defaultBalanceEnabled()));
    }

    private String balanceApiPath() {
        return prefs.getString("provider_balance_api_path_" + providerId,
                currentCustomConfig().optString("balanceApiPath", defaultBalanceApiPath()));
    }

    private String balanceResultPath() {
        return prefs.getString("provider_balance_result_path_" + providerId,
                currentCustomConfig().optString("balanceResultPath", defaultBalanceResultPath()));
    }

    private boolean defaultBalanceEnabled() {
        String name = spec.title.toLowerCase(Locale.US);
        return name.contains("openrouter") || name.contains("deepseek")
                || name.contains("aihubmix") || name.contains("silicon");
    }

    private String defaultBalanceApiPath() {
        String name = spec.title.toLowerCase(Locale.US);
        if (name.contains("deepseek") || name.contains("aihubmix")) return "/user/balance";
        if (name.contains("silicon")) return "/user/info";
        return "/credits";
    }

    private String defaultBalanceResultPath() {
        String name = spec.title.toLowerCase(Locale.US);
        if (name.contains("deepseek") || name.contains("aihubmix")) return "balance_infos[0].total_balance";
        if (name.contains("silicon")) return "data.totalBalance";
        if (name.contains("openrouter")) return "data.total_credits - data.total_usage";
        return "data.total_usage";
    }

    private String balanceUrl(String baseUrl, String apiPath) {
        String path = apiPath == null || apiPath.trim().isEmpty() ? "/credits" : apiPath.trim();
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        return trimTrailingSlash(baseUrl) + (path.startsWith("/") ? path : "/" + path);
    }

    private String formatBalance(Object json, String expression) throws Exception {
        String expr = expression == null ? "" : expression.trim();
        if (expr.isEmpty()) {
            throw new Exception(getString(R.string.ia_balance_result_path));
        }
        int minus = expr.indexOf(" - ");
        if (minus > 0) {
            double left = asDouble(readJsonPath(json, expr.substring(0, minus)));
            double right = asDouble(readJsonPath(json, expr.substring(minus + 3)));
            return String.format(Locale.US, "%.2f", left - right);
        }
        Object value = readJsonPath(json, expr);
        if (value instanceof Number) {
            return String.format(Locale.US, "%.2f", ((Number) value).doubleValue());
        }
        return String.valueOf(value);
    }

    private Object readJsonPath(Object current, String path) throws Exception {
        Object cursor = current;
        for (String rawPart : path.trim().split("\\.")) {
            String part = rawPart.trim();
            int bracket = part.indexOf('[');
            String key = bracket >= 0 ? part.substring(0, bracket) : part;
            if (!(cursor instanceof JSONObject object) || !object.has(key)) {
                throw new Exception("path not found: " + path);
            }
            cursor = object.get(key);
            while (bracket >= 0) {
                int end = part.indexOf(']', bracket);
                int index = Integer.parseInt(part.substring(bracket + 1, end));
                if (!(cursor instanceof JSONArray array) || index < 0 || index >= array.length()) {
                    throw new Exception("path not found: " + path);
                }
                cursor = array.get(index);
                bracket = part.indexOf('[', end);
            }
        }
        return cursor;
    }

    private double asDouble(Object value) throws Exception {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new Exception("not numeric: " + value);
        }
    }

    private void saveNetworkSettings(boolean enabled, String type, String host,
                                     String port, String user, String password) {
        prefs.edit()
                .putBoolean("provider_proxy_enabled_" + providerId, enabled)
                .putString("provider_proxy_type_" + providerId, type)
                .putString("provider_proxy_host_" + providerId, host)
                .putString("provider_proxy_port_" + providerId, port)
                .putString("provider_proxy_username_" + providerId, user)
                .putString("provider_proxy_password_" + providerId, password)
                .apply();
        if (spec.custom) {
            VoidPortSettings.updateProviderConfigValue(prefs, providerId, "proxyEnabled", enabled);
            VoidPortSettings.updateProviderConfigValue(prefs, providerId, "proxyType", type);
            VoidPortSettings.updateProviderConfigValue(prefs, providerId, "proxyHost", host);
            VoidPortSettings.updateProviderConfigValue(prefs, providerId, "proxyPort", port);
            VoidPortSettings.updateProviderConfigValue(prefs, providerId, "proxyUsername", user);
            VoidPortSettings.updateProviderConfigValue(prefs, providerId, "proxyPassword", password);
        }
    }

    private boolean networkBool(String key, boolean fallback) {
        return prefs.getBoolean(networkPrefKey(key),
                currentCustomConfig().optBoolean(key, fallback));
    }

    private String networkString(String key, String fallback) {
        return prefs.getString(networkPrefKey(key),
                currentCustomConfig().optString(key, fallback));
    }

    private String networkPrefKey(String key) {
        return switch (key) {
            case "proxyEnabled" -> "provider_proxy_enabled_" + providerId;
            case "proxyType" -> "provider_proxy_type_" + providerId;
            case "proxyHost" -> "provider_proxy_host_" + providerId;
            case "proxyPort" -> "provider_proxy_port_" + providerId;
            case "proxyUsername" -> "provider_proxy_username_" + providerId;
            case "proxyPassword" -> "provider_proxy_password_" + providerId;
            default -> "provider_" + key + "_" + providerId;
        };
    }

    private JSONObject currentCustomConfig() {
        JSONObject config = VoidPortSettings.getProviderConfigObject(prefs, providerId);
        return config == null ? new JSONObject() : config;
    }

    private String modelListUrl(String baseUrl) {
        String trimmed = trimTrailingSlash(baseUrl);
        if (trimmed.endsWith("/v1/chat/completions")) {
            return trimmed.substring(0, trimmed.length() - "/chat/completions".length()) + "/models";
        }
        if (trimmed.endsWith("/chat/completions")) {
            return trimmed.substring(0, trimmed.length() - "/chat/completions".length()) + "/models";
        }
        if (trimmed.endsWith("/v1")) {
            return trimmed + "/models";
        }
        return trimmed + "/v1/models";
    }

    private String trimTrailingSlash(String value) {
        String trimmed = value == null ? "" : value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String compact(String body) {
        if (body == null) return "";
        String compact = body.replace('\n', ' ').replace('\r', ' ').trim();
        return compact.length() > 180 ? compact.substring(0, 180) : compact;
    }

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

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

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
