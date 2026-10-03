package pro.sketchware.store.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.recyclerview.widget.GridLayoutManager;

import com.besome.sketch.lib.base.BaseAppCompatActivity;

import java.util.List;

import pro.sketchware.R;
import pro.sketchware.databinding.ActivityP2pProfileBinding;
import pro.sketchware.store.StoreRuntime;
import pro.sketchware.store.core.Catalog;
import pro.sketchware.store.core.StoreEngine;

/** A user's page in the store: their profile and the projects they publish. */
public class StoreProfileActivity extends BaseAppCompatActivity {

    private static final String EXTRA_USER = "user";

    private ActivityP2pProfileBinding binding;
    private StoreRuntime runtime;
    private StoreEngine engine;
    private String user;
    private ListingAdapter adapter;
    private final Runnable onStoreChanged = this::bind;

    public static Intent intent(Context context, String user) {
        return new Intent(context, StoreProfileActivity.class).putExtra(EXTRA_USER, user);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityP2pProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        user = getIntent().getStringExtra(EXTRA_USER);
        adapter = new ListingAdapter(listing -> startActivity(StoreProjectActivity.intent(this, listing.author, listing.id)));
        binding.projects.setLayoutManager(new GridLayoutManager(this, 2));
        binding.projects.setAdapter(adapter);
        binding.editProfile.setOnClickListener(v -> startActivity(new Intent(this, StoreEditProfileActivity.class)));
        binding.publish.setOnClickListener(v -> startActivity(new Intent(this, StorePublishActivity.class)));
        runtime = StoreRuntime.get(this);
        runtime.start(ready -> {
            engine = ready;
            bind();
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        runtime.addListener(onStoreChanged);
        bind();
    }

    @Override
    protected void onStop() {
        super.onStop();
        runtime.removeListener(onStoreChanged);
    }

    private void bind() {
        if (engine == null || binding == null) {
            return;
        }
        boolean own = engine.identity().id().equals(user);
        binding.ownActions.setVisibility(own ? View.VISIBLE : View.GONE);
        binding.userId.setText(user);
        Catalog.Profile profile = engine.catalog().profile(user);
        if (profile == null && own) {
            // Not shared yet, or no profile: show what's saved locally
            binding.name.setText(engine.hasProfile()
                    ? pro.sketchware.store.core.Codec.string(engine.myProfile(), "name", "")
                    : getString(R.string.p2p_store_my_store));
            binding.bio.setText(engine.hasProfile() ? pro.sketchware.store.core.Codec.string(engine.myProfile(), "bio", "")
                    : getString(R.string.p2p_store_create_profile_first));
            loadOwnImages();
        } else if (profile != null) {
            binding.toolbar.setTitle(profile.name);
            binding.name.setText(profile.name);
            binding.bio.setText(profile.bio);
            binding.bio.setVisibility(profile.bio.isEmpty() ? View.GONE : View.VISIBLE);
            StoreUi.load(binding.banner, profile.banner, 0);
            StoreUi.load(binding.avatar, profile.avatar, R.drawable.ic_p2p_person);
            binding.logo.setVisibility(profile.logo != null && profile.logo.isFile() ? View.VISIBLE : View.GONE);
            StoreUi.load(binding.logo, profile.logo, 0);
        }
        List<Catalog.Listing> listings = engine.catalog().listings(null, null, Catalog.SORT_NEWEST, user);
        adapter.submit(listings);
        binding.projectsEmpty.setVisibility(listings.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void loadOwnImages() {
        if (!engine.hasProfile()) {
            return;
        }
        com.google.gson.JsonObject profile = engine.myProfile();
        StoreUi.load(binding.banner, engine.myMedia(pro.sketchware.store.core.Codec.string(profile, "banner", "")), 0);
        StoreUi.load(binding.avatar, engine.myMedia(pro.sketchware.store.core.Codec.string(profile, "avatar", "")),
                R.drawable.ic_p2p_person);
    }
}
