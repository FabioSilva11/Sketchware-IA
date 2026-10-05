package pro.sketchware.chat.provider;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import pro.sketchware.chat.AiChatSettingsHelper;
import pro.sketchware.chat.BaseAppCompatActivity;
import pro.sketchware.R;
import pro.sketchware.chat.port.VoidPortSettings;
import pro.sketchware.chat.port.VoidPortRefreshModelService;

/**
 * Full-screen activity for managing a single AI provider.
 * Allows configuring API key, base URL, API path, and managing models.
 */
public final class ProviderDetailActivity extends BaseAppCompatActivity {

    private String providerId;
    private String providerName;
    private boolean isCustom;
    private SharedPreferences prefs;

    // Views
    private TextView tvProviderType;
    private TextView tvProviderGroup;
    private TextView labelApiKey;
    private TextView labelBaseUrl;
    private MaterialSwitch switchEnabled;
    private MaterialSwitch switchMultiKey;
    private MaterialSwitch switchVertexAi;
    private EditText etProviderName;
    private EditText etApiKey;
    private EditText etBaseUrl;
    private EditText etVertexLocation;
    private EditText etVertexProject;
    private EditText etVertexServiceAccount;
    private TextInputLayout tilApiKey;
    private TextInputLayout tilBaseUrl;
    private LinearLayout rowManageKeys;
    private View dividerManageKeys;
    private LinearLayout rowVertexAi;
    private View dividerVertexAi;
    private LinearLayout vertexFields;
    private String providerType = "openai";
    private String providerGroup = "other";
    private boolean loadingProvider;

    // Models tab
    private LinearLayout tabConfigContent;
    private LinearLayout tabModelsContent;
    private LinearLayout modelsEmptyState;
    private RecyclerView rvProviderModels;
    private LinearLayout modelsActionBar;
    private MaterialButton btnFetchModels;
    private MaterialButton btnAddModel;

    // Tab selectors
    private LinearLayout tabConfig;
    private LinearLayout tabModels;
    private ImageView iconTabConfig;
    private TextView labelTabConfig;
    private ImageView iconTabModels;
    private TextView labelTabModels;

    private ModelAdapter modelAdapter;
    private List<String> modelList = new ArrayList<>();
    private boolean isConfigTab = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_provider_detail);

        prefs = AiChatSettingsHelper.prefs(this);

        providerId = getIntent().getStringExtra("provider_id");
        providerName = getIntent().getStringExtra("provider_name");
        isCustom = getIntent().getBooleanExtra("provider_custom", false);

        if (providerId == null || providerName == null) {
            finish();
            return;
        }

        setupToolbar();
        setupViews();
        setupTabs();
        loadProviderData();
    }

    @Override
    public void onBackPressed() {
        saveAndFinish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_provider_detail, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_test_provider) {
            testProvider();
            return true;
        } else if (id == R.id.action_share_provider) {
            shareProvider();
            return true;
        } else if (id == R.id.action_delete_provider) {
            confirmDeleteProvider();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ────────────────────────────────────────
    // Setup
    // ────────────────────────────────────────

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.top_app_bar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(providerName);
            getSupportActionBar().setDisplayShowTitleEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> {
            saveAndFinish();
        });
    }

    private void setupViews() {
        tvProviderType = findViewById(R.id.tv_provider_type_value);
        tvProviderGroup = findViewById(R.id.tv_provider_group_value);
        labelApiKey = findViewById(R.id.label_api_key);
        labelBaseUrl = findViewById(R.id.label_api_base_url);
        switchEnabled = findViewById(R.id.switch_enabled);
        switchMultiKey = findViewById(R.id.switch_multi_key);
        switchVertexAi = findViewById(R.id.switch_vertex_ai);
        etProviderName = findViewById(R.id.et_provider_name);
        etApiKey = findViewById(R.id.et_api_key);
        etBaseUrl = findViewById(R.id.et_api_base_url);
        etVertexLocation = findViewById(R.id.et_vertex_location);
        etVertexProject = findViewById(R.id.et_vertex_project);
        etVertexServiceAccount = findViewById(R.id.et_vertex_service_account);
        tilApiKey = findViewById(R.id.til_api_key);
        tilBaseUrl = findViewById(R.id.til_api_base_url);
        rowManageKeys = findViewById(R.id.row_manage_keys);
        dividerManageKeys = findViewById(R.id.divider_manage_keys);
        rowVertexAi = findViewById(R.id.row_vertex_ai);
        dividerVertexAi = findViewById(R.id.divider_vertex_ai);
        vertexFields = findViewById(R.id.vertex_fields_container);

        tabConfigContent = findViewById(R.id.tab_config_content);
        tabModelsContent = findViewById(R.id.tab_models_content);
        modelsEmptyState = findViewById(R.id.models_empty_state);
        rvProviderModels = findViewById(R.id.rv_provider_models);
        modelsActionBar = findViewById(R.id.models_action_bar);
        btnFetchModels = findViewById(R.id.btn_fetch_models);
        btnAddModel = findViewById(R.id.btn_add_model);

        tabConfig = findViewById(R.id.tab_config);
        tabModels = findViewById(R.id.tab_models);
        iconTabConfig = findViewById(R.id.icon_tab_config);
        labelTabConfig = findViewById(R.id.label_tab_config);
        iconTabModels = findViewById(R.id.icon_tab_models);
        labelTabModels = findViewById(R.id.label_tab_models);

        modelAdapter = new ModelAdapter();
        rvProviderModels.setLayoutManager(new LinearLayoutManager(this));
        rvProviderModels.setAdapter(modelAdapter);

        btnFetchModels.setOnClickListener(v -> fetchModelsFromApi());
        btnAddModel.setOnClickListener(v -> showAddModelDialog());
        findViewById(R.id.row_provider_type).setOnClickListener(v -> showProviderTypePicker());
        findViewById(R.id.row_provider_group).setOnClickListener(v -> showProviderGroupPicker());
        rowManageKeys.setOnClickListener(v -> showMultiKeyManager());
        findViewById(R.id.row_network).setOnClickListener(v -> showNetworkSettings());
        findViewById(R.id.row_custom_request).setOnClickListener(v -> showCustomRequestSettings());

        switchEnabled.setOnCheckedChangeListener((button, checked) -> {
            if (loadingProvider) return;
            if (checked && !hasCurrentCredential()) {
                switchEnabled.setChecked(false);
                Toast.makeText(this, R.string.ia_api_key_required_to_enable, Toast.LENGTH_SHORT).show();
                return;
            }
            autoSave();
        });
        switchMultiKey.setOnCheckedChangeListener((button, checked) -> {
            updateConditionalFields();
            autoSave();
        });
        switchVertexAi.setOnCheckedChangeListener((button, checked) -> {
            updateConditionalFields();
            autoSave();
        });

        TextWatcher autoSaveWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { autoSave(); }
            @Override public void afterTextChanged(Editable s) {}
        };
        etProviderName.addTextChangedListener(autoSaveWatcher);
        etApiKey.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (loadingProvider) return;
                if (!hasCurrentCredential() && switchEnabled.isChecked()) {
                    switchEnabled.setChecked(false);
                } else {
                    autoSave();
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        etBaseUrl.addTextChangedListener(autoSaveWatcher);
        etVertexLocation.addTextChangedListener(autoSaveWatcher);
        etVertexProject.addTextChangedListener(autoSaveWatcher);
        etVertexServiceAccount.addTextChangedListener(autoSaveWatcher);
    }

    private void setupTabs() {
        tabConfig.setOnClickListener(v -> switchToConfigTab());
        tabModels.setOnClickListener(v -> switchToModelsTab());
    }

    private void switchToConfigTab() {
        isConfigTab = true;
        tabConfigContent.setVisibility(View.VISIBLE);
        tabModelsContent.setVisibility(View.GONE);
        modelsActionBar.setVisibility(View.GONE);
        iconTabConfig.setColorFilter(getResources().getColor(R.color.chat_accent, null));
        labelTabConfig.setTextColor(getResources().getColor(R.color.chat_accent, null));
        iconTabModels.setColorFilter(getResources().getColor(R.color.chat_text_secondary, null));
        labelTabModels.setTextColor(getResources().getColor(R.color.chat_text_secondary, null));
    }

    private void switchToModelsTab() {
        isConfigTab = false;
        tabConfigContent.setVisibility(View.GONE);
        tabModelsContent.setVisibility(View.VISIBLE);
        modelsActionBar.setVisibility(View.VISIBLE);
        iconTabConfig.setColorFilter(getResources().getColor(R.color.chat_text_secondary, null));
        labelTabConfig.setTextColor(getResources().getColor(R.color.chat_text_secondary, null));
        iconTabModels.setColorFilter(getResources().getColor(R.color.chat_accent, null));
        labelTabModels.setTextColor(getResources().getColor(R.color.chat_accent, null));
        updateModelsList();
    }

    // ────────────────────────────────────────
    // Load provider data
    // ────────────────────────────────────────

    private void loadProviderData() {
        loadingProvider = true;
        JSONObject config = VoidPortSettings.getOrCreateProviderConfig(prefs, providerId, providerName);
        providerType = VoidPortSettings.providerType(config);
        providerGroup = config.optString("group", "other");
        String configuredName = config.optString("name", providerName).trim();
        String configuredBaseUrl = config.optString("baseUrl", "").trim();

        etProviderName.setText(configuredName.isEmpty() ? providerName : configuredName);
        etApiKey.setText(config.optString("apiKey", ""));
        etApiKey.setSelection(etApiKey.getText().length());
        etBaseUrl.setText(configuredBaseUrl.isEmpty()
                ? VoidPortSettings.defaultBaseForProviderType(providerId)
                : configuredBaseUrl);
        switchEnabled.setChecked(config.optBoolean(
                "enabled", VoidPortSettings.defaultEnabledForProvider(providerId)));
        switchMultiKey.setChecked(config.optBoolean("multiKeyEnabled", false));
        switchVertexAi.setChecked(config.optBoolean("vertexAI", false));
        etVertexLocation.setText(config.optString("location", ""));
        etVertexProject.setText(config.optString("projectId", ""));
        etVertexServiceAccount.setText(config.optString("serviceAccountJson", ""));
        updateProviderTypeLabel();
        tvProviderGroup.setText("other".equals(providerGroup)
                ? getString(R.string.ia_group_other)
                : providerGroup);
        updateConditionalFields();
        loadingProvider = false;

        // Load models
        loadModels();
    }

    private VoidPortSettings.ProviderCardSpec findSpec() {
        List<VoidPortSettings.ProviderCardSpec> all = VoidPortSettings.getProviderCards(prefs);
        for (VoidPortSettings.ProviderCardSpec spec : all) {
            if (providerId.equals(spec.providerId)) {
                return spec;
            }
        }
        return null;
    }

    private String getProviderTypeDisplay() {
        String type = providerId;
        if (providerId.contains("openai")) return "OpenAI";
        if (providerId.contains("anthropic")) return "Anthropic";
        if (providerId.contains("gemini")) return "Gemini";
        if (providerId.contains("openrouter")) return "OpenRouter";
        if (providerId.contains("deepseek")) return "DeepSeek";
        if (providerId.contains("groq")) return "Groq";
        if (providerId.contains("mistral")) return "Mistral";
        if (providerId.contains("grok") || providerId.contains("xai")) return "Grok (xAI)";
        if (providerId.contains("minimax")) return "MiniMax";
        if (providerId.contains("ollama")) return "Ollama";
        if (providerId.contains("vllm")) return "vLLM";
        if (providerId.contains("lm_studio")) return "LM Studio";
        if (providerId.contains("litellm")) return "LiteLLM";
        if (providerId.contains("huggingface")) return "Hugging Face";
        return providerName;
    }

    // ────────────────────────────────────────
    // Save
    // ────────────────────────────────────────

    private void saveAndFinish() {
        saveProviderData();
        finish();
    }

    private void saveProviderData() {
        if (loadingProvider) return;
        JSONObject config = VoidPortSettings.getProviderConfigObject(prefs, providerId);
        if (config == null) config = VoidPortSettings.defaultProviderConfig(providerId, providerName);
        try {
            String editedName = textOf(etProviderName);
            config.put("id", providerId);
            config.put("name", editedName.isEmpty() ? providerName : editedName);
            config.put("providerType", providerType);
            boolean credentialReady = !VoidPortSettings.providerRequiresApiKey(providerId, config)
                    || hasCurrentCredential();
            config.put("enabled", switchEnabled.isChecked() && credentialReady);
            config.put("apiKey", textOf(etApiKey));
            config.put("baseUrl", textOf(etBaseUrl));
            config.put("chatPath", "openai".equals(providerType) ? "/chat/completions" : "");
            config.put("multiKeyEnabled", switchMultiKey.isChecked());
            config.put("vertexAI", "gemini".equals(providerType) && switchVertexAi.isChecked());
            config.put("location", textOf(etVertexLocation));
            config.put("projectId", textOf(etVertexProject));
            config.put("serviceAccountJson", textOf(etVertexServiceAccount));
            config.put("group", providerGroup);
            if (!config.has("models")) config.put("models", new JSONArray());
            VoidPortSettings.saveProviderConfig(prefs, config);
            providerName = config.optString("name", providerName);
            if (getSupportActionBar() != null) getSupportActionBar().setTitle(providerName);
        } catch (Exception ignored) {
        }
    }

    private void autoSave() {
        if (!loadingProvider) saveProviderData();
    }

    private static String textOf(EditText editText) {
        return editText == null || editText.getText() == null
                ? ""
                : editText.getText().toString().trim();
    }

    private boolean hasCurrentCredential() {
        if (!textOf(etApiKey).isEmpty()) return true;
        JSONObject config = VoidPortSettings.getProviderConfigObject(prefs, providerId);
        return VoidPortSettings.hasUsableApiKey(config);
    }

    private String providerTypeForId(String id) {
        return VoidPortSettings.providerTypeForId(id);
    }

    private void updateProviderTypeLabel() {
        tvProviderType.setText(switch (providerType) {
            case "gemini" -> "Gemini";
            case "anthropic" -> "Claude";
            default -> "OpenAI";
        });
    }

    private void updateConditionalFields() {
        if (rowManageKeys == null) return;
        boolean multiKey = switchMultiKey.isChecked();
        boolean google = "gemini".equals(providerType);
        boolean vertex = google && switchVertexAi.isChecked();
        rowManageKeys.setVisibility(multiKey ? View.VISIBLE : View.GONE);
        dividerManageKeys.setVisibility(multiKey ? View.VISIBLE : View.GONE);
        rowVertexAi.setVisibility(google ? View.VISIBLE : View.GONE);
        dividerVertexAi.setVisibility(google ? View.VISIBLE : View.GONE);
        vertexFields.setVisibility(vertex ? View.VISIBLE : View.GONE);
        int keyVisibility = (!multiKey && !vertex) ? View.VISIBLE : View.GONE;
        labelApiKey.setVisibility(keyVisibility);
        tilApiKey.setVisibility(keyVisibility);
        int baseVisibility = vertex ? View.GONE : View.VISIBLE;
        labelBaseUrl.setVisibility(baseVisibility);
        tilBaseUrl.setVisibility(baseVisibility);
    }

    private void showProviderTypePicker() {
        String[] labels = {"OpenAI", "Gemini", "Claude"};
        String[] values = {"openai", "gemini", "anthropic"};
        int checked = "gemini".equals(providerType) ? 1 : ("anthropic".equals(providerType) ? 2 : 0);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ia_provider_type_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    providerType = values[which];
                    updateProviderTypeLabel();
                    if (textOf(etBaseUrl).isEmpty()) {
                        etBaseUrl.setText(VoidPortSettings.defaultBaseForProviderType(providerType));
                    }
                    updateConditionalFields();
                    autoSave();
                    dialog.dismiss();
                })
                .show();
    }

    private void showProviderGroupPicker() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_text_input, null);
        TextInputEditText input = dialogView.findViewById(R.id.dialog_edit_text);
        input.setHint(R.string.ia_group_label);
        input.setText("other".equals(providerGroup) ? "" : providerGroup);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ia_group_label)
                .setView(dialogView)
                .setNegativeButton(R.string.common_word_cancel, null)
                .setNeutralButton(R.string.ia_group_other, (dialog, which) -> {
                    providerGroup = "other";
                    tvProviderGroup.setText(R.string.ia_group_other);
                    autoSave();
                })
                .setPositiveButton(R.string.common_word_ok, (dialog, which) -> {
                    String group = input.getText() == null ? "" : input.getText().toString().trim();
                    providerGroup = group.isEmpty() ? "other" : group;
                    tvProviderGroup.setText(group.isEmpty() ? getString(R.string.ia_group_other) : group);
                    autoSave();
                })
                .show();
    }

    private void showMultiKeyManager() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_text_input, null);
        TextInputEditText input = dialogView.findViewById(R.id.dialog_edit_text);
        input.setHint(R.string.ia_multi_key_dialog_hint);
        input.setSingleLine(false);
        input.setMinLines(5);
        JSONObject config = VoidPortSettings.getProviderConfigObject(prefs, providerId);
        JSONArray keys = config == null ? null : config.optJSONArray("apiKeys");
        StringBuilder existing = new StringBuilder();
        for (int i = 0; keys != null && i < keys.length(); i++) {
            JSONObject item = keys.optJSONObject(i);
            String key = item == null ? "" : item.optString("key", "").trim();
            if (key.isEmpty()) continue;
            if (existing.length() > 0) existing.append('\n');
            existing.append(key);
        }
        input.setText(existing.toString());
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ia_manage_keys_label)
                .setView(dialogView)
                .setNegativeButton(R.string.common_word_cancel, null)
                .setPositiveButton(R.string.common_word_ok, (dialog, which) -> {
                    JSONObject current = VoidPortSettings.getProviderConfigObject(prefs, providerId);
                    if (current == null) current = VoidPortSettings.defaultProviderConfig(providerId, providerName);
                    JSONArray next = new JSONArray();
                    String raw = input.getText() == null ? "" : input.getText().toString();
                    int position = 1;
                    for (String line : raw.split("\\r?\\n")) {
                        String key = line.trim();
                        if (key.isEmpty()) continue;
                        JSONObject item = new JSONObject();
                        try {
                            item.put("id", "key_" + position);
                            item.put("name", "Key " + position);
                            item.put("key", key);
                            item.put("enabled", true);
                            next.put(item);
                            position++;
                        } catch (Exception ignored) {
                        }
                    }
                    try {
                        current.put("apiKeys", next);
                        current.put("multiKeyEnabled", true);
                    } catch (Exception ignored) {
                    }
                    VoidPortSettings.saveProviderConfig(prefs, current);
                })
                .show();
    }

    private void showNetworkSettings() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_text_input, null);
        TextInputEditText input = dialogView.findViewById(R.id.dialog_edit_text);
        input.setHint(R.string.ia_network_dialog_hint);
        JSONObject config = VoidPortSettings.getProviderConfigObject(prefs, providerId);
        String host = config == null ? "" : config.optString("proxyHost", "");
        String port = config == null ? "8080" : config.optString("proxyPort", "8080");
        input.setText(host.isEmpty() ? "" : host + ":" + port);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ia_network_label)
                .setView(dialogView)
                .setNegativeButton(R.string.common_word_cancel, null)
                .setPositiveButton(R.string.common_word_ok, (dialog, which) -> {
                    String value = input.getText() == null ? "" : input.getText().toString().trim();
                    String proxyHost = value;
                    String proxyPort = "8080";
                    int colon = value.lastIndexOf(':');
                    if (colon > 0) {
                        proxyHost = value.substring(0, colon).trim();
                        proxyPort = value.substring(colon + 1).trim();
                    }
                    JSONObject current = VoidPortSettings.getProviderConfigObject(prefs, providerId);
                    if (current == null) current = VoidPortSettings.defaultProviderConfig(providerId, providerName);
                    try {
                        current.put("proxyEnabled", !proxyHost.isEmpty());
                        current.put("proxyType", "http");
                        current.put("proxyHost", proxyHost);
                        current.put("proxyPort", proxyPort.isEmpty() ? "8080" : proxyPort);
                    } catch (Exception ignored) {
                    }
                    VoidPortSettings.saveProviderConfig(prefs, current);
                })
                .show();
    }

    private void showCustomRequestSettings() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, 0, padding, 0);
        TextInputEditText headers = new TextInputEditText(this);
        headers.setHint(R.string.ia_custom_headers_hint);
        TextInputEditText body = new TextInputEditText(this);
        body.setHint(R.string.ia_custom_body_hint);
        body.setMinLines(3);
        JSONObject config = VoidPortSettings.getProviderConfigObject(prefs, providerId);
        headers.setText(config == null ? "{}" : config.optString("headers", "{}"));
        body.setText(config == null ? "{}" : config.optString("customBodyJson", "{}"));
        content.addView(headers, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ia_custom_request_label)
                .setView(content)
                .setNegativeButton(R.string.common_word_cancel, null)
                .setPositiveButton(R.string.common_word_ok, (dialog, which) -> {
                    try {
                        // Validate both values before replacing the stored request overrides.
                        new JSONObject(textOf(headers));
                        new JSONObject(textOf(body));
                        JSONObject current = VoidPortSettings.getProviderConfigObject(prefs, providerId);
                        if (current == null) current = VoidPortSettings.defaultProviderConfig(providerId, providerName);
                        current.put("headers", textOf(headers));
                        current.put("customBodyJson", textOf(body));
                        VoidPortSettings.saveProviderConfig(prefs, current);
                    } catch (Exception e) {
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    // ────────────────────────────────────────
    // Models management
    // ────────────────────────────────────────

    private void loadModels() {
        modelList.clear();
        modelList.addAll(VoidPortSettings.getProviderModels(prefs, providerId));
        updateModelsList();
    }

    private JSONArray readModelsArray() {
        String raw = prefs.getString(VoidPortSettings.PREF_CUSTOM_MODELS, "[]");
        try {
            return new JSONArray(raw);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private void saveModels() {
        // Models are saved individually via the add/remove operations
    }

    private void updateModelsList() {
        modelAdapter.notifyDataSetChanged();
        if (modelList.isEmpty()) {
            modelsEmptyState.setVisibility(View.VISIBLE);
            rvProviderModels.setVisibility(View.GONE);
        } else {
            modelsEmptyState.setVisibility(View.GONE);
            rvProviderModels.setVisibility(View.VISIBLE);
        }
    }

    private void showAddModelDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_text_input, null);
        TextInputEditText input = dialogView.findViewById(R.id.dialog_edit_text);
        if (input != null) {
            input.setHint(R.string.ia_model_id_label);
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ia_add_model_title)
                .setView(dialogView)
                .setNegativeButton(R.string.common_word_cancel, null)
                .setPositiveButton(R.string.common_word_ok, (dialog, which) -> {
                    if (input == null || input.getText() == null) return;
                    String modelId = input.getText().toString().trim();
                    if (modelId.isEmpty()) return;
                    addModel(modelId);
                })
                .show();
    }

    private void addModel(String modelId) {
        if (modelList.contains(modelId)) return;
        modelList.add(modelId);
        VoidPortSettings.setProviderModels(prefs, providerId, modelList);

        updateModelsList();
        Toast.makeText(this, getString(R.string.ia_models_fetched, 1), Toast.LENGTH_SHORT).show();
    }

    private void removeModel(String modelId) {
        modelList.remove(modelId);
        VoidPortSettings.setProviderModels(prefs, providerId, modelList);

        updateModelsList();
    }

    private void fetchModelsFromApi() {
        saveProviderData();
        Toast.makeText(this, R.string.ia_fetching_models, Toast.LENGTH_SHORT).show();
        VoidPortRefreshModelService.refreshProviderAsync(this, providerId, false, result -> {
            if (result.state == VoidPortRefreshModelService.RefreshState.FINISHED) {
                if (result.models.isEmpty()) {
                    Toast.makeText(this, getString(R.string.ia_models_fetched, 0), Toast.LENGTH_SHORT).show();
                    return;
                }
                ProviderModelFetchSheet.show(this, providerId, providerName,
                        result.models, modelList, selected -> {
                    modelList.clear();
                    modelList.addAll(selected);
                    VoidPortSettings.setProviderModels(prefs, providerId, modelList);
                    updateModelsList();
                });
            } else {
                Toast.makeText(this, getString(R.string.ia_fetch_models_failed, result.error), Toast.LENGTH_LONG).show();
            }
        });
    }

    // ────────────────────────────────────────
    // Test / Share / Delete
    // ────────────────────────────────────────

    private void testProvider() {
        saveProviderData();
        Toast.makeText(this, R.string.ia_testing_provider, Toast.LENGTH_SHORT).show();
        VoidPortRefreshModelService.refreshProviderAsync(this, providerId, false, result -> {
            if (result.state == VoidPortRefreshModelService.RefreshState.FINISHED) {
                Toast.makeText(this, getString(R.string.ia_provider_test_ok), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, getString(R.string.ia_fetch_models_failed, result.error), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void shareProvider() {
        try {
            JSONObject config = new JSONObject();
            config.put("id", providerId);
            config.put("name", etProviderName != null && etProviderName.getText() != null
                    ? etProviderName.getText().toString() : providerName);
            // Provider sharing intentionally excludes credentials.
            config.put("apiKey", "");
            if (etBaseUrl != null && etBaseUrl.getText() != null) {
                config.put("baseUrl", etBaseUrl.getText().toString().trim());
            }
            config.put("chatPath", VoidPortSettings.defaultChatPathForProviderType(providerTypeForId(providerId)));

            android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, config.toString(2));
            startActivity(android.content.Intent.createChooser(shareIntent, getString(R.string.ia_share_provider)));
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void confirmDeleteProvider() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ia_delete_provider)
                .setMessage(getString(R.string.ia_delete_provider_message, providerName))
                .setNegativeButton(R.string.common_word_cancel, null)
                .setPositiveButton(R.string.ia_delete_provider, (dialog, which) -> {
                    VoidPortSettings.removeProviderConfig(prefs, providerId);
                    Toast.makeText(this, providerName + " excluído", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .show();
    }

    // ────────────────────────────────────────
    // Model adapter
    // ────────────────────────────────────────

    private final class ModelAdapter extends RecyclerView.Adapter<ModelAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.item_provider_model_row, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            String modelId = modelList.get(position);
            holder.modelName.setText(modelId);
            holder.btnDelete.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(ProviderDetailActivity.this)
                        .setTitle(R.string.ia_delete_model)
                        .setNegativeButton(R.string.common_word_cancel, null)
                        .setPositiveButton(R.string.ia_delete_model, (d, w) -> removeModel(modelId))
                        .show();
            });
        }

        @Override
        public int getItemCount() {
            return modelList.size();
        }

        final class VH extends RecyclerView.ViewHolder {
            final TextView modelName;
            final ImageButton btnDelete;

            VH(@NonNull View itemView) {
                super(itemView);
                modelName = itemView.findViewById(R.id.model_name);
                btnDelete = itemView.findViewById(R.id.btn_delete_model);
            }
        }
    }
}
