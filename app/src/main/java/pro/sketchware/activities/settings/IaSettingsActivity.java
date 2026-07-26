package pro.sketchware.activities.settings;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import pro.sketchware.R;
import pro.sketchware.ai.config.AiSettingsRepository;
import pro.sketchware.activities.chat.port.VoidPortProviderMaxTokens;
import pro.sketchware.activities.chat.port.VoidPortSettings;
import pro.sketchware.databinding.ActivityIaSettingsBinding;
import pro.sketchware.databinding.ItemProviderRowBinding;
import pro.sketchware.utility.TranslationFunction;

public class IaSettingsActivity extends BaseAppCompatActivity {

    private ActivityIaSettingsBinding binding;
    private SharedPreferences prefs;
    private ProvidersAdapter providersAdapter;
    private final List<ProviderItem> providers = new ArrayList<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        binding = ActivityIaSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        prefs = new AiSettingsRepository(this).preferences();

        setupToolbar();
        setupMaxTokensCard();
        setupInsets();
        setupProvidersList();
        setupSearch();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null) {
            updateMaxTokensLabel();
        }
        reloadProviders();
    }

    @Override
    public android.content.res.Resources getResources() {
        return TranslationFunction.wrapResources(this, super.getResources());
    }

    private void setupToolbar() {
        binding.topAppBar.setTitle(R.string.ia_settings_title);
        binding.topAppBar.setNavigationOnClickListener(v -> finish());
        binding.topAppBar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_check_providers) {
                refreshProviderStatuses();
                return true;
            }
            if (id == R.id.action_import_provider) {
                showImportProviderDialog();
                return true;
            }
            if (id == R.id.action_add_provider) {
                showAddProviderTypeDialog();
                return true;
            }
            return false;
        });
    }

    private void setupMaxTokensCard() {
        updateMaxTokensLabel();
        binding.btnEditMaxTokens.setOnClickListener(v -> showMaxTokensDialog());
    }

    private void updateMaxTokensLabel() {
        int current = parsePositiveInt(
                prefs.getString(VoidPortSettings.PREF_GLOBAL_MAX_TOKENS, ""),
                VoidPortProviderMaxTokens.DEFAULT_MAX_TOKENS
        );
        binding.tvMaxTokensValue.setText(getString(R.string.ia_max_tokens_current_value, current));
    }

    private void showMaxTokensDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(4), dp(4), dp(4), 0);

        TextView hint = new TextView(this);
        hint.setText(R.string.ia_max_tokens_hint);
        hint.setTextColor(getColor(R.color.chat_text_secondary));
        hint.setTextSize(13);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        hintParams.bottomMargin = dp(10);
        hint.setLayoutParams(hintParams);
        container.addView(hint);

        TextInputEditText input = addInput(container, R.string.ia_max_tokens_title, false, false);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(prefs.getString(VoidPortSettings.PREF_GLOBAL_MAX_TOKENS,
                String.valueOf(VoidPortProviderMaxTokens.DEFAULT_MAX_TOKENS)));

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ia_max_tokens_title)
                .setView(container)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.ia_save, (dialog, which) -> {
                    int value = parsePositiveInt(textOf(input), VoidPortProviderMaxTokens.DEFAULT_MAX_TOKENS);
                    prefs.edit().putString(VoidPortSettings.PREF_GLOBAL_MAX_TOKENS, String.valueOf(value)).apply();
                    updateMaxTokensLabel();
                    Toast.makeText(this, R.string.ia_max_tokens_saved, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void setupInsets() {
        int appBarLeft = binding.appBarLayout.getPaddingLeft();
        int appBarTop = binding.appBarLayout.getPaddingTop();
        int appBarRight = binding.appBarLayout.getPaddingRight();
        int appBarBottom = binding.appBarLayout.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(appBarLeft + sys.left, appBarTop + sys.top,
                    appBarRight + sys.right, appBarBottom);
            return insets;
        });

        int contentLeft = binding.providersContent.getPaddingLeft();
        int contentTop = binding.providersContent.getPaddingTop();
        int contentRight = binding.providersContent.getPaddingRight();
        int contentBottom = binding.providersContent.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(binding.providersContent, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(contentLeft + sys.left, contentTop,
                    contentRight + sys.right, contentBottom + sys.bottom);
            return insets;
        });
    }

    private void setupProvidersList() {
        providersAdapter = new ProvidersAdapter(new ArrayList<>());
        binding.rvProviders.setLayoutManager(new LinearLayoutManager(this));
        binding.rvProviders.setAdapter(providersAdapter);
        reloadProviders();
    }

    private void setupSearch() {
        binding.etSearchProviders.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                filterProviders(s == null ? "" : s.toString());
            }
        });
    }

    private void reloadProviders() {
        providers.clear();
        for (VoidPortSettings.ProviderCardSpec spec : VoidPortSettings.getProviderCards(prefs)) {
            providers.add(providerItemFromSpec(spec));
        }
        filterProviders(binding == null || binding.etSearchProviders.getText() == null
                ? ""
                : binding.etSearchProviders.getText().toString());
    }

    private ProviderItem providerItemFromSpec(VoidPortSettings.ProviderCardSpec spec) {
        VoidPortSettings.FieldSpec statusField = null;
        for (VoidPortSettings.FieldSpec field : spec.fields) {
            if (field.label.toLowerCase(Locale.ROOT).contains("api key")
                    || field.label.toLowerCase(Locale.ROOT).contains("base url")) {
                statusField = field;
                break;
            }
        }
        String statusPrefKey = statusField == null ? "" : statusField.prefKey;
        String enabledKey = statusField == null ? null : statusField.enabledKey;
        return new ProviderItem(
                spec.providerId,
                spec.title,
                statusPrefKey,
                enabledKey,
                iconForProvider(spec.providerId, spec.title),
                spec.custom
        );
    }

    private void filterProviders(String query) {
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        List<ProviderItem> filtered = new ArrayList<>();
        for (ProviderItem provider : providers) {
            if (normalized.isEmpty()
                    || provider.name.toLowerCase(Locale.ROOT).contains(normalized)
                    || provider.id.toLowerCase(Locale.ROOT).contains(normalized)) {
                filtered.add(provider);
            }
        }
        providersAdapter.updateData(filtered);
    }

    private void refreshProviderStatuses() {
        reloadProviders();
        Toast.makeText(this, R.string.ia_providers_checked, Toast.LENGTH_SHORT).show();
    }

    private void showAddProviderTypeDialog() {
        String[] labels = {"OpenAI", "Gemini", "Anthropic", "Ollama", "OpenAI-Compatible"};
        String[] types = {"openai", "gemini", "anthropic", "ollama", "openai_compatible"};
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ia_add_provider)
                .setItems(labels, (dialog, which) -> showProviderEditor(types[which], labels[which], null))
                .show();
    }

    private void showProviderEditor(String type, String defaultName, @Nullable JSONObject existing) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(4);
        container.setPadding(padding, padding, padding, 0);

        TextInputEditText name = addInput(container, R.string.ia_name_label, false, false);
        TextInputEditText apiKey = addInput(container, R.string.ia_api_key_label, true, true);
        TextInputEditText baseUrl = addInput(container, R.string.ia_api_base_url_label, false, false);
        TextInputEditText apiPath = addInput(container, R.string.ia_api_path_label, false, false);

        String normalizedType = existing == null ? type : VoidPortSettings.providerType(existing);
        name.setText(existing == null ? defaultName : existing.optString("name", defaultName));
        apiKey.setText(existing == null ? "" : existing.optString("apiKey", ""));
        baseUrl.setText(existing == null
                ? VoidPortSettings.defaultBaseForProviderType(normalizedType)
                : existing.optString("baseUrl", VoidPortSettings.defaultBaseForProviderType(normalizedType)));
        apiPath.setText(existing == null
                ? VoidPortSettings.defaultChatPathForProviderType(normalizedType)
                : existing.optString("chatPath", VoidPortSettings.defaultChatPathForProviderType(normalizedType)));

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ia_add_provider)
                .setView(container)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String displayName = textOf(name);
                    if (displayName.isEmpty()) {
                        Toast.makeText(this, R.string.ia_provider_name_required, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String providerId = existing == null
                            ? VoidPortSettings.uniqueProviderId(prefs, displayName)
                            : existing.optString("id", VoidPortSettings.uniqueProviderId(prefs, displayName));
                    JSONObject config = buildProviderConfig(providerId, normalizedType, displayName,
                            textOf(apiKey), textOf(baseUrl), textOf(apiPath));
                    VoidPortSettings.saveProviderConfig(prefs, config);
                    syncCustomProviderApis(config);
                    reloadProviders();
                    openProviderDetail(findProvider(providerId));
                })
                .show();
    }

    private TextInputEditText addInput(LinearLayout container, int labelRes, boolean password, boolean multiLine) {
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
        params.bottomMargin = dp(12);
        layout.setLayoutParams(params);

        TextInputEditText edit = new TextInputEditText(this);
        edit.setSingleLine(!multiLine);
        if (password) {
            edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        layout.addView(edit);
        container.addView(layout);
        return edit;
    }

    private void showImportProviderDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(4), dp(4), dp(4), 0);

        TextInputEditText input = addInput(container, R.string.ia_import_provider_hint, false, true);
        input.setMinLines(5);
        input.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.common_word_import)
                .setView(container)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.common_word_import, (dialog, which) -> {
                    try {
                        List<JSONObject> imported = parseImportedProviders(textOf(input));
                        for (JSONObject config : imported) {
                            VoidPortSettings.saveProviderConfig(prefs, config);
                            syncCustomProviderApis(config);
                        }
                        reloadProviders();
                        Toast.makeText(this,
                                getString(R.string.ia_imported_providers, imported.size()),
                                Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(this,
                                getString(R.string.ia_import_failed, e.getMessage()),
                                Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private List<JSONObject> parseImportedProviders(String raw) throws Exception {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            throw new JSONException("empty");
        }
        List<JSONObject> result = new ArrayList<>();
        if (text.startsWith("{")) {
            JSONObject object = new JSONObject(text);
            JSONObject providers = object.optJSONObject("providers");
            if (providers != null) {
                result.addAll(parseChatBoxProviders(providers));
            } else {
                result.add(configFromImportObject(object));
            }
            if (result.isEmpty()) {
                throw new JSONException("no providers found");
            }
            return result;
        }
        for (String line : text.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("ai-provider:v1:")) {
                result.add(decodeSharedProvider(trimmed));
            } else if (trimmed.startsWith("{")) {
                result.add(configFromImportObject(new JSONObject(trimmed)));
            }
        }
        if (result.isEmpty()) {
            throw new JSONException("unsupported format");
        }
        return result;
    }

    private List<JSONObject> parseChatBoxProviders(JSONObject providersObject) throws JSONException {
        List<JSONObject> result = new ArrayList<>();
        Iterator<String> keys = providersObject.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject provider = providersObject.optJSONObject(key);
            if (provider == null) {
                continue;
            }
            String apiKey = provider.optString("apiKey", "").trim();
            String baseUrl = provider.optString("baseUrl", "").trim();
            if (apiKey.isEmpty() && baseUrl.isEmpty()) {
                continue;
            }
            String type = typeFromImport(key);
            String name = provider.optString("name", defaultNameForType(type)).trim();
            result.add(buildProviderConfig(
                    VoidPortSettings.uniqueProviderId(prefs, name),
                    type,
                    name.isEmpty() ? defaultNameForType(type) : name,
                    apiKey,
                    baseUrl.isEmpty() ? VoidPortSettings.defaultBaseForProviderType(type) : baseUrl,
                    VoidPortSettings.defaultChatPathForProviderType(type)
            ));
        }
        return result;
    }

    private JSONObject decodeSharedProvider(String value) throws JSONException {
        String payload = value.substring("ai-provider:v1:".length()).trim();
        String json = new String(Base64.decode(payload, Base64.DEFAULT), StandardCharsets.UTF_8);
        return configFromImportObject(new JSONObject(json));
    }

    private JSONObject configFromImportObject(JSONObject object) throws JSONException {
        String type = typeFromImport(object.optString("type", object.optString("providerType", "openai")));
        String name = object.optString("name", defaultNameForType(type)).trim();
        String baseUrl = object.optString("baseUrl", VoidPortSettings.defaultBaseForProviderType(type)).trim();
        String apiPath = object.optString("chatPath", VoidPortSettings.defaultChatPathForProviderType(type)).trim();
        return buildProviderConfig(
                VoidPortSettings.uniqueProviderId(prefs, name),
                type,
                name.isEmpty() ? defaultNameForType(type) : name,
                object.optString("apiKey", ""),
                baseUrl.isEmpty() ? VoidPortSettings.defaultBaseForProviderType(type) : baseUrl,
                apiPath
        );
    }

    private JSONObject buildProviderConfig(String providerId, String type, String name,
                                           String apiKey, String baseUrl, String apiPath) {
        JSONObject config = new JSONObject();
        try {
            JSONArray models = new JSONArray();
            config.put("id", providerId);
            config.put("enabled", true);
            config.put("name", name);
            config.put("apiKey", apiKey == null ? "" : apiKey.trim());
            config.put("baseUrl", baseUrl == null ? "" : baseUrl.trim());
            config.put("providerType", type == null ? "openai" : type);
            config.put("chatPath", apiPath == null ? "" : apiPath.trim());
            config.put("useResponseApi", false);
            config.put("models", models);
            config.put("modelOverrides", new JSONObject());
            config.put("proxyEnabled", false);
            config.put("proxyType", "http");
            config.put("proxyHost", "");
            config.put("proxyPort", "8080");
            config.put("proxyUsername", "");
            config.put("proxyPassword", "");
            config.put("multiKeyEnabled", false);
            config.put("apiKeys", new JSONArray());
            config.put("headers", "{}");
            config.put("balanceEnabled", defaultBalanceEnabled(name, type));
            config.put("balanceApiPath", defaultBalanceApiPath(name));
            config.put("balanceResultPath", defaultBalanceResultPath(name));
        } catch (JSONException ignored) {
        }
        return config;
    }

    private void syncCustomProviderApis(JSONObject config) {
        AiProviderBalanceSyncService.syncProviderApis(
                prefs,
                config.optString("id", ""),
                config.optString("name", ""),
                VoidPortSettings.providerType(config),
                config.optString("apiKey", ""),
                config.optString("baseUrl", "")
        );
    }

    private String typeFromImport(String raw) {
        String type = raw == null ? "" : raw.trim().toLowerCase(Locale.US);
        if (type.contains("gemini") || type.contains("google")) {
            return "gemini";
        }
        if (type.contains("claude") || type.contains("anthropic")) {
            return "anthropic";
        }
        if (type.contains("minimax")) {
            return "minimax";
        }
        if (type.contains("compatible")) {
            return "openai_compatible";
        }
        return "openai";
    }

    private String defaultNameForType(String type) {
        return switch (type) {
            case "gemini" -> "Gemini";
            case "anthropic" -> "Anthropic";
            case "minimax" -> "MiniMax";
            case "openai_compatible" -> "OpenAI-Compatible";
            default -> "OpenAI";
        };
    }

    private boolean defaultBalanceEnabled(String name, String type) {
        String normalized = (name == null ? "" : name).toLowerCase(Locale.US);
        return "openai".equals(type)
                && (normalized.contains("openrouter")
                || normalized.contains("deepseek")
                || normalized.contains("aihubmix")
                || normalized.contains("silicon"));
    }

    private String defaultBalanceApiPath(String name) {
        String normalized = name == null ? "" : name.toLowerCase(Locale.US);
        if (normalized.contains("deepseek") || normalized.contains("aihubmix")) {
            return "/user/balance";
        }
        if (normalized.contains("silicon")) {
            return "/user/info";
        }
        return "/credits";
    }

    private String defaultBalanceResultPath(String name) {
        String normalized = name == null ? "" : name.toLowerCase(Locale.US);
        if (normalized.contains("deepseek") || normalized.contains("aihubmix")) {
            return "balance_infos[0].total_balance";
        }
        if (normalized.contains("silicon")) {
            return "data.totalBalance";
        }
        if (normalized.contains("openrouter")) {
            return "data.total_credits - data.total_usage";
        }
        return "data.total_usage";
    }

    @Nullable
    private ProviderItem findProvider(String idOrName) {
        for (ProviderItem provider : providers) {
            if (provider.id.equals(idOrName) || provider.name.equals(idOrName)) {
                return provider;
            }
        }
        return null;
    }

    private void openProviderDetail(@Nullable ProviderItem provider) {
        if (provider == null) {
            return;
        }
        Intent intent = new Intent(this, ProviderDetailActivity.class);
        intent.putExtra(ProviderDetailActivity.EXTRA_PROVIDER_TITLE, provider.name);
        intent.putExtra(ProviderDetailActivity.EXTRA_PROVIDER_ID, provider.id);
        startActivity(intent);
    }

    private void shareProvider(ProviderItem provider) {
        JSONObject export = exportProvider(provider);
        String type = export.optString("type", "openai");
        JSONObject payload = new JSONObject();
        try {
            payload.put("type", "gemini".equals(type) ? "google" : ("anthropic".equals(type) ? "claude" : "openai"));
            payload.put("name", export.optString("name", provider.name));
            payload.put("apiKey", export.optString("apiKey", ""));
            if (!"gemini".equals(type)) {
                payload.put("baseUrl", export.optString("baseUrl", ""));
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

    private JSONObject exportProvider(ProviderItem provider) {
        JSONObject custom = VoidPortSettings.getProviderConfigObject(prefs, provider.id);
        if (custom != null) {
            JSONObject copy = new JSONObject();
            try {
                Iterator<String> keys = custom.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    copy.put(key, custom.opt(key));
                }
                copy.put("apiKey", prefs.getString(VoidPortSettings.providerPrefKey(provider.id, "api_key"), custom.optString("apiKey", "")));
                copy.put("baseUrl", prefs.getString(VoidPortSettings.providerPrefKey(provider.id, "base_url"), custom.optString("baseUrl", "")));
                copy.put("type", VoidPortSettings.providerType(custom));
            } catch (JSONException ignored) {
            }
            return copy;
        }

        JSONObject object = new JSONObject();
        try {
            String type = typeFromImport(provider.name);
            String key = provider.statusPrefKey == null ? "" : prefs.getString(provider.statusPrefKey, "");
            String base = prefs.getString("base_url_override_" + provider.id, defaultBaseForProvider(provider.id));
            object.put("type", type);
            object.put("name", provider.name);
            object.put("apiKey", key);
            object.put("baseUrl", base);
        } catch (JSONException ignored) {
        }
        return object;
    }

    private String defaultBaseForProvider(String providerId) {
        return switch (providerId) {
            case "anthropic" -> "https://api.anthropic.com/v1";
            case "gemini" -> "https://generativelanguage.googleapis.com/v1beta";
            case "openrouter" -> "https://openrouter.ai/api/v1";
            case "deepseek" -> "https://api.deepseek.com";
            case "groq" -> "https://api.groq.com/openai/v1";
            case "mistral" -> "https://api.mistral.ai/v1";
            case "minimax" -> "https://api.minimax.io/v1";
            case "openai_compatible" -> prefs.getString("openai_compatible_base_url", "");
            case "litellm" -> prefs.getString("litellm_base_url", "");
            default -> "https://api.openai.com/v1";
        };
    }

    private boolean isProviderEnabled(ProviderItem provider) {
        return VoidPortSettings.isProviderConfigured(prefs, provider.id);
    }

    @DrawableRes
    private int iconForProvider(String id, String name) {
        String value = ((id == null ? "" : id) + " " + (name == null ? "" : name)).toLowerCase(Locale.US);
        if (value.contains("anthropic") || value.contains("claude")) {
            return R.drawable.kelivo_icon_anthropic;
        }
        if (value.contains("gemini") || value.contains("google")) {
            return R.drawable.kelivo_icon_gemini_color;
        }
        if (value.contains("ollama")) {
            return R.drawable.ic_kelivo_globe; // TODO: Use specific Ollama icon when available
        }
        if (value.contains("openrouter")) {
            return R.drawable.kelivo_icon_openrouter;
        }
        if (value.contains("deepseek")) {
            return R.drawable.kelivo_icon_deepseek_color;
        }
        if (value.contains("mistral")) {
            return R.drawable.kelivo_icon_mistral_color;
        }
        if (value.contains("minimax")) {
            return R.drawable.kelivo_icon_minimax_color;
        }
        if (value.contains("openai")) {
            return R.drawable.kelivo_icon_openai;
        }
        return R.drawable.ic_kelivo_globe;
    }

    private String textOf(TextInputEditText edit) {
        return edit.getText() == null ? "" : edit.getText().toString().trim();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private int parsePositiveInt(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static final class ProviderItem {
        final String id;
        final String name;
        final String statusPrefKey;
        @Nullable final String enabledKey;
        @DrawableRes final int iconRes;
        final boolean custom;

        ProviderItem(
                String id,
                String name,
                String statusPrefKey,
                @Nullable String enabledKey,
                @DrawableRes int iconRes,
                boolean custom
        ) {
            this.id = id;
            this.name = name;
            this.statusPrefKey = statusPrefKey;
            this.enabledKey = enabledKey;
            this.iconRes = iconRes;
            this.custom = custom;
        }
    }

    private final class ProvidersAdapter extends RecyclerView.Adapter<ProvidersAdapter.Holder> {
        private List<ProviderItem> data;

        ProvidersAdapter(List<ProviderItem> data) {
            this.data = data;
        }

        void updateData(List<ProviderItem> newData) {
            data = newData;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemProviderRowBinding rowBinding = ItemProviderRowBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new Holder(rowBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            ProviderItem provider = data.get(position);
            boolean enabled = isProviderEnabled(provider);

            holder.binding.providerName.setText(provider.name);
            holder.binding.providerIcon.setImageResource(provider.iconRes);
            holder.binding.providerStatusBadge.setSelected(enabled);
            holder.binding.providerStatusBadge.setText(enabled
                    ? getString(R.string.ia_status_on)
                    : getString(R.string.ia_status_off));
            holder.binding.providerStatusBadge.setTextColor(getColor(enabled
                    ? R.color.provider_status_on_text
                    : R.color.provider_status_off_text));
            holder.binding.rowDivider.setVisibility(position == data.size() - 1
                    ? View.GONE
                    : View.VISIBLE);
            holder.binding.getRoot().setOnClickListener(v -> openProviderDetail(provider));
            holder.binding.getRoot().setOnLongClickListener(v -> {
                shareProvider(provider);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ItemProviderRowBinding binding;

            Holder(ItemProviderRowBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
