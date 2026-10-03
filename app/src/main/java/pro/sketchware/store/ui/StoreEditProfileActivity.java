package pro.sketchware.store.ui;

import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.gson.JsonObject;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import pro.sketchware.R;
import pro.sketchware.databinding.ActivityP2pEditProfileBinding;
import pro.sketchware.store.StoreRuntime;
import pro.sketchware.store.core.Codec;
import pro.sketchware.store.core.StoreEngine;

/** Edits the user's public store profile. */
public class StoreEditProfileActivity extends BaseAppCompatActivity {

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private ActivityP2pEditProfileBinding binding;
    private StoreEngine engine;
    private File avatar;
    private File banner;
    private File logo;
    private Consumer<File> pendingTarget;
    private int pendingMaxSize;
    private boolean pendingPng;

    private final ActivityResultLauncher<String> pickImage = registerForActivityResult(
            new ActivityResultContracts.GetContent(), this::onImagePicked);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityP2pEditProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.save.setEnabled(false);
        StoreRuntime.get(this).start(ready -> {
            engine = ready;
            JsonObject profile = engine.myProfile();
            if (profile != null) {
                binding.name.setText(Codec.string(profile, "name", ""));
                binding.bio.setText(Codec.string(profile, "bio", ""));
                avatar = engine.myMedia(Codec.string(profile, "avatar", ""));
                banner = engine.myMedia(Codec.string(profile, "banner", ""));
                logo = engine.myMedia(Codec.string(profile, "logo", ""));
                show(binding.avatar, avatar, R.drawable.ic_p2p_person);
                show(binding.banner, banner, 0);
                show(binding.logo, logo, 0);
            }
            binding.save.setEnabled(true);
        });
        binding.pickAvatar.setOnClickListener(v -> pick(512, true, file -> {
            avatar = file;
            show(binding.avatar, file, R.drawable.ic_p2p_person);
        }));
        binding.pickLogo.setOnClickListener(v -> pick(512, true, file -> {
            logo = file;
            show(binding.logo, file, 0);
        }));
        binding.pickBanner.setOnClickListener(v -> pick(1280, false, file -> {
            banner = file;
            show(binding.banner, file, 0);
        }));
        binding.save.setOnClickListener(v -> save());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        io.shutdown();
    }

    private void pick(int maxSize, boolean png, Consumer<File> target) {
        pendingTarget = target;
        pendingMaxSize = maxSize;
        pendingPng = png;
        pickImage.launch("image/*");
    }

    private void onImagePicked(Uri uri) {
        if (uri == null || pendingTarget == null) {
            return;
        }
        Consumer<File> target = pendingTarget;
        int maxSize = pendingMaxSize;
        boolean png = pendingPng;
        io.execute(() -> {
            try {
                File file = StoreUi.importImage(this, uri, maxSize, png);
                runOnUiThread(() -> target.accept(file));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private static void show(ImageView view, File file, int placeholder) {
        StoreUi.load(view, file, placeholder);
    }

    private void save() {
        String name = binding.name.getText() == null ? "" : binding.name.getText().toString().trim();
        String bio = binding.bio.getText() == null ? "" : binding.bio.getText().toString().trim();
        if (name.isEmpty()) {
            binding.nameLayout.setError(getString(R.string.p2p_store_name_required));
            return;
        }
        binding.nameLayout.setError(null);
        binding.save.setEnabled(false);
        binding.save.setText(R.string.p2p_store_saving);
        io.execute(() -> {
            try {
                engine.updateProfile(name, bio, avatar, banner, logo);
                engine.syncSoon();
                runOnUiThread(this::finish);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    binding.save.setEnabled(true);
                    binding.save.setText(R.string.p2p_store_save_profile);
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}
