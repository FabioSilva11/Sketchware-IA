package pro.sketchware.activities.settings;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import pro.sketchware.R;
import pro.sketchware.activities.chat.port.VoidPortSettings;
import pro.sketchware.databinding.ActivityIaSettingsBinding;
import pro.sketchware.databinding.ItemProviderRowBinding;
import pro.sketchware.utility.TranslationFunction;

public class IaSettingsActivity extends BaseAppCompatActivity {

    private ActivityIaSettingsBinding binding;
    private SharedPreferences prefs;
    private ProvidersAdapter providersAdapter;

    private final List<ProviderItem> providers = List.of(
            new ProviderItem("openai", "OpenAI", "openai_api_key", "openai_enabled", R.drawable.kelivo_icon_openai),
            new ProviderItem("anthropic", "Anthropic", "anthropic_api_key", null, R.drawable.kelivo_icon_anthropic),
            new ProviderItem("gemini", "Gemini", "gemini_api_key", "gemini_enabled", R.drawable.kelivo_icon_gemini_color),
            new ProviderItem("openrouter", "OpenRouter", "openrouter_api_key", null, R.drawable.kelivo_icon_openrouter),
            new ProviderItem("deepseek", "DeepSeek", "deepseek_api_key", null, R.drawable.kelivo_icon_deepseek_color),
            new ProviderItem("groq", "Groq", "groq_api_key", "groq_enabled", R.drawable.ic_kelivo_globe),
            new ProviderItem("mistral", "Mistral", "mistral_api_key", null, R.drawable.kelivo_icon_mistral_color),
            new ProviderItem("openai_compatible", "OpenAI-Compatible", "openai_compatible_base_url", null, R.drawable.kelivo_icon_openai),
            new ProviderItem("litellm", "LiteLLM", "litellm_base_url", null, R.drawable.ic_kelivo_globe)
    );

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        binding = ActivityIaSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        prefs = VoidPortSettings.prefs(this);

        setupToolbar();
        setupInsets();
        setupProvidersList();
        setupSearch();
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
            if (id == R.id.action_import_provider || id == R.id.action_add_provider) {
                openProviderDetail(findProvider("OpenAI-Compatible"));
                return true;
            }
            return false;
        });
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
        providersAdapter = new ProvidersAdapter(new ArrayList<>(providers));
        binding.rvProviders.setLayoutManager(new LinearLayoutManager(this));
        binding.rvProviders.setAdapter(providersAdapter);
    }

    private void setupSearch() {
        binding.etSearchProviders.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                filterProviders(s == null ? "" : s.toString());
            }
        });
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
        if (providersAdapter != null) {
            providersAdapter.notifyDataSetChanged();
        }
        Toast.makeText(this, R.string.ia_providers_checked, Toast.LENGTH_SHORT).show();
    }

    @Nullable
    private ProviderItem findProvider(String name) {
        for (ProviderItem provider : providers) {
            if (provider.name.equals(name)) {
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
        startActivity(intent);
    }

    private boolean isProviderEnabled(ProviderItem provider) {
        if (provider.enabledKey != null) {
            return prefs.getBoolean(provider.enabledKey, true);
        }
        String value = prefs.getString(provider.statusPrefKey, "");
        return value != null && !value.trim().isEmpty();
    }

    private static final class ProviderItem {
        final String id;
        final String name;
        final String statusPrefKey;
        @Nullable final String enabledKey;
        @DrawableRes final int iconRes;

        ProviderItem(
                String id,
                String name,
                String statusPrefKey,
                @Nullable String enabledKey,
                @DrawableRes int iconRes
        ) {
            this.id = id;
            this.name = name;
            this.statusPrefKey = statusPrefKey;
            this.enabledKey = enabledKey;
            this.iconRes = iconRes;
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
