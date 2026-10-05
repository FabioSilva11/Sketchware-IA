package pro.sketchware.chat;


import pro.sketchware.R;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import pro.sketchware.chat.port.VoidPortSettings;

/** Seletor de modelo: lista provedores locais -> modelos do provedor. */
public final class KelivoModelBottomSheet {

    public interface Callback {
        void onModelSelected(String providerId, String modelId);
    }

    private static final String PREF_PINNED = "pinned_models_v1";
    private static final String PREF_PINNED_LEGACY = "kelivo_pinned_models";

    private KelivoModelBottomSheet() {
    }

    public static void show(@NonNull ChatActivity activity, @NonNull Callback callback) {
        SharedPreferences prefs = AiChatSettingsHelper.prefs(activity);

        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        View content = LayoutInflater.from(activity).inflate(R.layout.bottom_sheet_kelivo_model, null);
        dialog.setContentView(content);

        ImageView back = content.findViewById(R.id.model_back);
        TextView title = content.findViewById(R.id.model_sheet_title);
        EditText search = content.findViewById(R.id.model_search);
        ImageView favoritesJump = content.findViewById(R.id.model_favorites_jump);
        RecyclerView list = content.findViewById(R.id.model_list);
        LinearLayout legacyChips = content.findViewById(R.id.provider_chips);
        if (legacyChips != null) legacyChips.setVisibility(View.GONE);

        KelivoModelSheetAdapter adapter = new KelivoModelSheetAdapter();
        list.setLayoutManager(new LinearLayoutManager(activity));
        list.setAdapter(adapter);

        java.util.concurrent.atomic.AtomicReference<String> openProviderId =
                new java.util.concurrent.atomic.AtomicReference<>(null);

        // Build local provider list from VoidPortSettings
        List<VoidPortSettings.ProviderCardSpec> allProviders = VoidPortSettings.getProviderCards(prefs);

        Runnable render = () -> {
            String query = search.getText() == null
                    ? ""
                    : search.getText().toString().trim().toLowerCase(Locale.getDefault());
            String providerId = openProviderId.get();
            if (providerId == null) {
                back.setVisibility(View.GONE);
                title.setText(R.string.axion_choose_provider);
                search.setHint(R.string.axion_search_providers);
                favoritesJump.setVisibility(View.GONE);
                adapter.submit(buildProviderRows(activity, allProviders, query, prefs));
                return;
            }

            VoidPortSettings.ProviderCardSpec provider = findSpec(allProviders, providerId);
            if (provider == null) {
                openProviderId.set(null);
                back.setVisibility(View.GONE);
                title.setText(R.string.axion_choose_provider);
                search.setHint(R.string.axion_search_providers);
                favoritesJump.setVisibility(View.GONE);
                adapter.submit(buildProviderRows(activity, allProviders, query, prefs));
                return;
            }
            back.setVisibility(View.VISIBLE);
            title.setText(provider.title);
            search.setHint(R.string.axion_search_models);
            favoritesJump.setVisibility(hasPinnedForProvider(activity, provider.providerId)
                    ? View.VISIBLE : View.GONE);
            adapter.submit(buildModelRows(activity, provider, query, prefs));
        };

        adapter.setListener(new KelivoModelSheetAdapter.Listener() {
            @Override
            public void onProviderSelected(String pid) {
                openProviderId.set(pid);
                search.setText("");
                list.scrollToPosition(0);
                render.run();
            }

            @Override
            public void onModelSelected(String pid, String modelId) {
                prefs.edit()
                        .putString(AiChatSettingsHelper.PREF_CURRENT_PROVIDER, pid)
                        .putString(AiChatSettingsHelper.PREF_CURRENT_MODEL, modelId)
                        .apply();
                callback.onModelSelected(pid, modelId);
                dialog.dismiss();
            }

            @Override
            public void onFavoriteToggle(String pid, String modelId) {
                togglePinned(activity, pid, modelId);
                render.run();
            }
        });

        back.setOnClickListener(v -> {
            openProviderId.set(null);
            search.setText("");
            list.scrollToPosition(0);
            render.run();
        });

        favoritesJump.setOnClickListener(v -> {
            String pid = openProviderId.get();
            if (pid == null) return;
            if (search.getText() != null && search.getText().length() > 0) search.setText("");
            VoidPortSettings.ProviderCardSpec provider = findSpec(allProviders, pid);
            if (provider != null) {
                adapter.submit(buildModelRows(activity, provider, "", prefs, true));
                list.scrollToPosition(0);
            }
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { render.run(); }
            @Override public void afterTextChanged(Editable s) { }
        });

        dialog.setOnShowListener(d -> {
            View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
                int targetHeight = (int) (metrics.heightPixels * 0.82f);
                sheet.getLayoutParams().height = targetHeight;
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
                behavior.setPeekHeight(targetHeight);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        render.run();
        dialog.show();
    }

    private static VoidPortSettings.ProviderCardSpec findSpec(
            List<VoidPortSettings.ProviderCardSpec> specs, String providerId) {
        if (specs == null || providerId == null) return null;
        for (VoidPortSettings.ProviderCardSpec spec : specs) {
            if (spec.providerId.equals(providerId)) return spec;
        }
        return null;
    }

    private static List<KelivoModelSheetAdapter.Row> buildProviderRows(
            Context context,
            List<VoidPortSettings.ProviderCardSpec> providers,
            String query,
            SharedPreferences prefs) {
        List<KelivoModelSheetAdapter.Row> rows = new ArrayList<>();
        String selected = prefs.getString(AiChatSettingsHelper.PREF_CURRENT_PROVIDER, "");
        for (VoidPortSettings.ProviderCardSpec spec : providers) {
            // Mostrar apenas provedores ativos/configurados
            boolean enabled = VoidPortSettings.isProviderConfigured(prefs, spec.providerId);
            if (!enabled) continue;
            if (!matches(spec.title, spec.providerId, query)) continue;
            rows.add(KelivoModelSheetAdapter.Row.provider(
                    spec.providerId,
                    spec.title,
                    "Ativo",
                    spec.providerId.equals(selected)));
        }
        if (rows.isEmpty()) {
            rows.add(KelivoModelSheetAdapter.Row.empty(
                    context.getString(R.string.axion_no_providers_available)));
        }
        return rows;
    }


    private static List<KelivoModelSheetAdapter.Row> buildModelRows(
            Context context,
            VoidPortSettings.ProviderCardSpec provider,
            String query,
            SharedPreferences prefs) {
        return buildModelRows(context, provider, query, prefs, false);
    }

    private static List<KelivoModelSheetAdapter.Row> buildModelRows(
            Context context,
            VoidPortSettings.ProviderCardSpec provider,
            String query,
            SharedPreferences prefs,
            boolean favoritesOnly) {
        List<KelivoModelSheetAdapter.Row> rows = new ArrayList<>();
        Set<String> pinned = getPinned(context);
        String currentProvider = prefs.getString(AiChatSettingsHelper.PREF_CURRENT_PROVIDER, "");
        String currentModel = prefs.getString(AiChatSettingsHelper.PREF_CURRENT_MODEL, "");

        for (VoidPortSettings.ModelOption option : VoidPortSettings.getVisibleModelOptions(prefs)) {
            if (!provider.providerId.equals(option.providerId)) continue;
            if (!matches(option.model, option.model, query)) continue;
            boolean modelPinned = pinned.contains(pinnedKey(option.providerId, option.model));
            if (favoritesOnly && !modelPinned) continue;
            boolean selected = option.providerId.equals(currentProvider)
                    && option.model.equals(currentModel);
            rows.add(new KelivoModelSheetAdapter.Row(
                    option.providerId, option.providerLabel, option.model, selected, modelPinned));
        }

        if (rows.isEmpty()) {
            rows.add(KelivoModelSheetAdapter.Row.empty(
                    context.getString(R.string.axion_no_models_in_provider)));
        }
        return rows;
    }

    private static boolean matches(String name, String id, String query) {
        if (query == null || query.isEmpty()) return true;
        String normalized = query.toLowerCase(Locale.getDefault());
        return (name != null && name.toLowerCase(Locale.getDefault()).contains(normalized))
                || (id != null && id.toLowerCase(Locale.getDefault()).contains(normalized));
    }

    private static boolean hasPinnedForProvider(Context context, String providerId) {
        String prefix = (providerId == null ? "" : providerId) + "::";
        for (String item : getPinned(context)) {
            if (item.startsWith(prefix)) return true;
        }
        return false;
    }

    private static Set<String> getPinned(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("chat_settings", Context.MODE_PRIVATE);
        Set<String> pinned = new HashSet<>(prefs.getStringSet(PREF_PINNED, new HashSet<>()));
        pinned.addAll(prefs.getStringSet(PREF_PINNED_LEGACY, new HashSet<>()));
        return pinned;
    }

    private static void togglePinned(Context context, String providerId, String modelId) {
        SharedPreferences prefs = context.getSharedPreferences("chat_settings", Context.MODE_PRIVATE);
        Set<String> pinned = new HashSet<>(prefs.getStringSet(PREF_PINNED, new HashSet<>()));
        Set<String> legacyPinned = new HashSet<>(prefs.getStringSet(PREF_PINNED_LEGACY, new HashSet<>()));
        pinned.addAll(legacyPinned);
        String key = pinnedKey(providerId, modelId);
        if (pinned.contains(key)) {
            pinned.remove(key);
            legacyPinned.remove(key);
        } else {
            pinned.add(key);
        }
        prefs.edit()
                .putStringSet(PREF_PINNED, pinned)
                .putStringSet(PREF_PINNED_LEGACY, legacyPinned)
                .apply();
    }

    private static String pinnedKey(String providerId, String modelId) {
        return (providerId == null ? "" : providerId) + "::" + (modelId == null ? "" : modelId);
    }
}
