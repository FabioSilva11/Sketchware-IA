package pro.sketchware.chat.provider;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import pro.sketchware.chat.AiChatSettingsHelper;
import pro.sketchware.chat.BaseAppCompatActivity;
import pro.sketchware.chat.KelivoModelIconResolver;
import pro.sketchware.R;
import pro.sketchware.chat.port.VoidPortSettings;

/**
 * Full-screen activity that lists all AI providers (built-in + custom) and
 * allows the user to manage their API keys, base URLs, and models manually.
 */
public final class IaSettingsActivity extends BaseAppCompatActivity {

    private RecyclerView recyclerView;
    private EditText searchInput;
    private ProviderAdapter adapter;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ia_settings);

        prefs = AiChatSettingsHelper.prefs(this);

        MaterialToolbar toolbar = findViewById(R.id.top_app_bar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(true);
            getSupportActionBar().setTitle(R.string.ia_settings_title);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        searchInput = findViewById(R.id.et_search_providers);
        recyclerView = findViewById(R.id.rv_providers);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProviderAdapter();
        recyclerView.setAdapter(adapter);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        loadProviders();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProviders();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_providers, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_check_providers) {
            showMultiSelectDialog();
            return true;
        } else if (id == R.id.action_import_provider) {
            showImportDialog();
            return true;
        } else if (id == R.id.action_add_provider) {
            showAddProviderDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showMultiSelectDialog() {
        List<VoidPortSettings.ProviderCardSpec> providers = VoidPortSettings.getProviderCards(prefs);
        String[] names = new String[providers.size()];
        boolean[] checked = new boolean[providers.size()];
        for (int i = 0; i < providers.size(); i++) {
            VoidPortSettings.ProviderCardSpec spec = providers.get(i);
            JSONObject config = VoidPortSettings.getProviderConfigObject(prefs, spec.providerId);
            names[i] = config == null ? spec.title : config.optString("name", spec.title);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ia_action_multi_select)
                .setMultiChoiceItems(names, checked, (d, which, selected) -> checked[which] = selected)
                .setNegativeButton(R.string.common_word_cancel, null)
                .setNeutralButton(R.string.ia_delete_custom_selected, null)
                .setPositiveButton(R.string.ia_export_selected, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            ViewGroup.LayoutParams listParams = dialog.getListView().getLayoutParams();
            listParams.height = Math.round(500 * getResources().getDisplayMetrics().density);
            dialog.getListView().setLayoutParams(listParams);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                Set<String> selectedIds = selectedProviderIds(providers, checked, false);
                if (selectedIds.isEmpty()) {
                    Toast.makeText(this, R.string.ia_no_provider_selected, Toast.LENGTH_SHORT).show();
                    return;
                }
                dialog.dismiss();
                shareSelectedProviders(selectedIds);
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                Set<String> selectedIds = selectedProviderIds(providers, checked, true);
                if (selectedIds.isEmpty()) {
                    Toast.makeText(this, R.string.ia_no_custom_provider_selected, Toast.LENGTH_SHORT).show();
                    return;
                }
                dialog.dismiss();
                confirmDeleteSelectedProviders(selectedIds);
            });
        });
        dialog.show();
    }

    private Set<String> selectedProviderIds(List<VoidPortSettings.ProviderCardSpec> providers,
                                            boolean[] checked,
                                            boolean customOnly) {
        Set<String> selected = new HashSet<>();
        for (int i = 0; i < providers.size() && i < checked.length; i++) {
            VoidPortSettings.ProviderCardSpec spec = providers.get(i);
            if (checked[i] && (!customOnly || spec.custom)) {
                selected.add(spec.providerId);
            }
        }
        return selected;
    }

    private void shareSelectedProviders(Set<String> selectedIds) {
        JSONArray exported = new JSONArray();
        for (String providerId : selectedIds) {
            JSONObject stored = VoidPortSettings.getProviderConfigObject(prefs, providerId);
            if (stored == null) continue;
            try {
                JSONObject safe = new JSONObject(stored.toString());
                safe.put("apiKey", "");
                safe.put("apiKeys", new JSONArray());
                safe.put("serviceAccountJson", "");
                exported.put(safe);
            } catch (Exception ignored) {
            }
        }
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/json");
        shareIntent.putExtra(Intent.EXTRA_TEXT, exported.toString());
        startActivity(Intent.createChooser(shareIntent, getString(R.string.ia_export_selected)));
    }

    private void confirmDeleteSelectedProviders(Set<String> selectedIds) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ia_delete_custom_selected)
                .setMessage(getString(R.string.ia_delete_selected_confirm, selectedIds.size()))
                .setNegativeButton(R.string.common_word_cancel, null)
                .setPositiveButton(R.string.ia_delete_provider, (dialog, which) -> {
                    for (String providerId : selectedIds) {
                        VoidPortSettings.removeProviderConfig(prefs, providerId);
                    }
                    loadProviders();
                })
                .show();
    }

    // ────────────────────────────────────────
    // Load providers
    // ────────────────────────────────────────

    private void loadProviders() {
        // KelivoIN was intentionally removed from Axion. Purge its old seeded
        // configuration so it cannot return as a dynamic/custom provider.
        VoidPortSettings.removeProviderConfig(prefs, "kelivoin");
        List<VoidPortSettings.ProviderCardSpec> allProviders = VoidPortSettings.getProviderCards(prefs);
        for (VoidPortSettings.ProviderCardSpec spec : allProviders) {
            if (!spec.custom) {
                JSONObject config = VoidPortSettings.getOrCreateProviderConfig(
                        prefs, spec.providerId, spec.title);
                if (config.optBoolean("enabled", false)
                        && VoidPortSettings.providerRequiresApiKey(spec.providerId, config)
                        && !VoidPortSettings.hasUsableApiKey(config)) {
                    try {
                        config.put("enabled", false);
                        VoidPortSettings.saveProviderConfig(prefs, config);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        VoidPortSettings.ensureValidCurrentSelection(prefs);
        adapter.setData(allProviders);
    }

    // ────────────────────────────────────────
    // Import dialog
    // ────────────────────────────────────────

    private void showImportDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_text_input, null);
        TextInputEditText input = dialogView.findViewById(R.id.dialog_edit_text);
        if (input != null) {
            input.setHint(R.string.ia_import_provider_hint);
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.common_word_import)
                .setView(dialogView)
                .setNegativeButton(R.string.common_word_cancel, null)
                .setPositiveButton(R.string.common_word_ok, (dialog, which) -> {
                    if (input == null || input.getText() == null) return;
                    String raw = input.getText().toString().trim();
                    if (raw.isEmpty()) return;
                    importProviderJson(raw);
                })
                .show();
    }

    private void importProviderJson(String raw) {
        try {
            JSONObject obj = new JSONObject(raw);
            VoidPortSettings.saveProviderConfig(prefs, obj);
            loadProviders();
            Toast.makeText(this,
                    getString(R.string.ia_imported_providers, 1),
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this,
                    getString(R.string.ia_import_failed, e.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }

    // ────────────────────────────────────────
    // Add provider dialog
    // ────────────────────────────────────────

    private void showAddProviderDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_text_input, null);
        TextInputEditText input = dialogView.findViewById(R.id.dialog_edit_text);
        if (input != null) {
            input.setHint(R.string.ia_name_label);
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ia_add_provider)
                .setView(dialogView)
                .setNegativeButton(R.string.common_word_cancel, null)
                .setPositiveButton(R.string.common_word_ok, (dialog, which) -> {
                    if (input == null || input.getText() == null) return;
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, R.string.ia_provider_name_required, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    createNewProvider(name);
                })
                .show();
    }

    private void createNewProvider(String name) {
        try {
            String id = VoidPortSettings.uniqueProviderId(prefs, name);
            JSONObject config = new JSONObject();
            config.put("id", id);
            config.put("name", name);
            config.put("enabled", true);
            config.put("apiKey", "");
            config.put("baseUrl", VoidPortSettings.defaultBaseForProviderType("openai"));
            config.put("chatPath", "/chat/completions");
            config.put("providerType", "openai");
            config.put("multiKeyEnabled", false);
            config.put("models", new JSONArray());
            VoidPortSettings.saveProviderConfig(prefs, config);

            loadProviders();

            // Open the detail screen for the new provider
            Intent intent = new Intent(this, ProviderDetailActivity.class);
            intent.putExtra("provider_id", id);
            intent.putExtra("provider_name", name);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ────────────────────────────────────────
    // RecyclerView adapter
    // ────────────────────────────────────────

    private final class ProviderAdapter extends RecyclerView.Adapter<ProviderAdapter.VH> {

        private final List<VoidPortSettings.ProviderCardSpec> allItems = new ArrayList<>();
        private final List<VoidPortSettings.ProviderCardSpec> displayed = new ArrayList<>();

        void setData(List<VoidPortSettings.ProviderCardSpec> items) {
            allItems.clear();
            allItems.addAll(items);
            filter(searchInput != null && searchInput.getText() != null
                    ? searchInput.getText().toString() : "");
        }

        void filter(String query) {
            displayed.clear();
            String q = query.trim().toLowerCase(Locale.getDefault());
            for (VoidPortSettings.ProviderCardSpec spec : allItems) {
                if (q.isEmpty()
                        || spec.title.toLowerCase(Locale.getDefault()).contains(q)
                        || spec.providerId.toLowerCase(Locale.getDefault()).contains(q)
                        || spec.description.toLowerCase(Locale.getDefault()).contains(q)) {
                    displayed.add(spec);
                }
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.item_provider_row, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            VoidPortSettings.ProviderCardSpec spec = displayed.get(position);
            JSONObject config = VoidPortSettings.getProviderConfigObject(prefs, spec.providerId);
            String displayName = config == null ? spec.title : config.optString("name", spec.title);
            holder.name.setText(displayName);

            // Resolve icon — show colored icons (no tint override)
            int iconRes = KelivoModelIconResolver.resolveProvider(spec.providerId, spec.title);
            holder.icon.clearColorFilter();
            if (iconRes != 0) {
                holder.icon.setImageResource(iconRes);
                holder.icon.setVisibility(View.VISIBLE);
                holder.initial.setVisibility(View.GONE);
            } else {
                holder.icon.setVisibility(View.GONE);
                holder.initial.setText(displayName.isEmpty() ? "?" : displayName.substring(0, 1));
                holder.initial.setVisibility(View.VISIBLE);
            }

            // Status badge
            boolean enabled = isProviderEnabled(spec);
            holder.badge.setText(enabled ? R.string.ia_status_on : R.string.ia_status_off);
            holder.badge.setTextColor(holder.itemView.getContext().getResources().getColor(
                    enabled ? R.color.provider_status_on_text : R.color.provider_status_off_text,
                    null));
            holder.badge.setBackgroundResource(enabled
                    ? R.drawable.bg_provider_status_badge_on
                    : R.drawable.bg_provider_status_badge);

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(IaSettingsActivity.this, ProviderDetailActivity.class);
                intent.putExtra("provider_id", spec.providerId);
                intent.putExtra("provider_name", spec.title);
                intent.putExtra("provider_description", spec.description);
                intent.putExtra("provider_custom", spec.custom);
                startActivity(intent);
            });
        }


        @Override
        public int getItemCount() {
            return displayed.size();
        }

        private boolean isProviderEnabled(VoidPortSettings.ProviderCardSpec spec) {
            return VoidPortSettings.isProviderConfigured(prefs, spec.providerId);
        }

        final class VH extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView initial;
            final TextView name;
            final TextView badge;

            VH(@NonNull View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.provider_icon);
                initial = itemView.findViewById(R.id.provider_initial);
                name = itemView.findViewById(R.id.provider_name);
                badge = itemView.findViewById(R.id.provider_status_badge);
            }
        }
    }
}
