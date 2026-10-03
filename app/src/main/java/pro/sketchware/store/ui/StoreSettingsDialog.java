package pro.sketchware.store.ui;

import android.app.Activity;
import android.view.LayoutInflater;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.concurrent.Executors;

import pro.sketchware.R;
import pro.sketchware.databinding.DialogP2pSettingsBinding;
import pro.sketchware.store.StoreRuntime;
import pro.sketchware.store.StoreSettings;
import pro.sketchware.store.core.StoreEngine;

/** Sharing, Wi-Fi only and cache settings of the P2P store. */
public final class StoreSettingsDialog {

    private StoreSettingsDialog() {
    }

    public static void show(Activity activity) {
        StoreRuntime runtime = StoreRuntime.get(activity);
        StoreSettings settings = runtime.settings();
        DialogP2pSettingsBinding binding = DialogP2pSettingsBinding.inflate(LayoutInflater.from(activity));
        binding.sharing.setChecked(settings.isSharing());
        binding.wifiOnly.setChecked(settings.isWifiOnly());
        binding.sharing.setOnCheckedChangeListener((button, checked) -> runtime.setSharing(checked));
        binding.wifiOnly.setOnCheckedChangeListener((button, checked) -> runtime.setWifiOnly(checked));

        int index = 0;
        for (int i = 0; i < StoreSettings.CACHE_LIMITS_MB.length; i++) {
            if (StoreSettings.CACHE_LIMITS_MB[i] == settings.getCacheLimitMb()) {
                index = i;
            }
        }
        binding.cacheLimit.setValue(index);
        binding.cacheLimit.setLabelFormatter(value ->
                StoreUi.size(activity, StoreSettings.CACHE_LIMITS_MB[(int) value] * 1024L * 1024L));
        Runnable showLimit = () -> binding.cacheTitle.setText(activity.getString(R.string.p2p_store_cache_limit,
                StoreUi.size(activity, StoreSettings.CACHE_LIMITS_MB[(int) binding.cacheLimit.getValue()] * 1024L * 1024L)));
        showLimit.run();
        binding.cacheLimit.addOnChangeListener((slider, value, fromUser) -> {
            runtime.setCacheLimitMb(StoreSettings.CACHE_LIMITS_MB[(int) value]);
            showLimit.run();
        });

        StoreEngine engine = runtime.engine();
        if (engine != null) {
            StoreEngine.Status status = engine.status();
            binding.cacheUsage.setText(activity.getString(R.string.p2p_store_cache_usage,
                    StoreUi.size(activity, status.cacheBytes)));
            binding.identity.setText(activity.getString(R.string.p2p_store_identity, engine.identity().id()));
            binding.clearCache.setOnClickListener(v -> Executors.newSingleThreadExecutor().execute(() -> {
                engine.clearCache();
                activity.runOnUiThread(() -> {
                    Toast.makeText(activity, R.string.p2p_store_cache_cleared, Toast.LENGTH_SHORT).show();
                    binding.cacheUsage.setText(activity.getString(R.string.p2p_store_cache_usage,
                            StoreUi.size(activity, engine.status().cacheBytes)));
                });
            }));
        } else {
            binding.clearCache.setEnabled(false);
        }

        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.p2p_store_settings)
                .setView(binding.getRoot())
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
