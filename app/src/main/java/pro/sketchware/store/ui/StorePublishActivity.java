package pro.sketchware.store.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.sketchware.R;
import pro.sketchware.databinding.ActivityP2pPublishBinding;
import pro.sketchware.store.ProjectPackager;
import pro.sketchware.store.StoreRuntime;
import pro.sketchware.store.core.Codec;
import pro.sketchware.store.core.Records;
import pro.sketchware.store.core.StoreEngine;

/** Publishes a local project, or a new version of one already published, to the P2P store. */
public class StorePublishActivity extends BaseAppCompatActivity {

    public static final String EXTRA_PROJECT_ID = "project_id";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private ActivityP2pPublishBinding binding;
    private StoreEngine engine;
    private final List<ProjectPackager.LocalProject> projects = new ArrayList<>();
    private ProjectPackager.LocalProject selected;
    private String projectId;
    private File icon;
    private final List<File> screenshots = new ArrayList<>();

    private final ActivityResultLauncher<String> pickIcon = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> importImages(uri == null ? null : java.util.Collections.singletonList(uri), true));
    private final ActivityResultLauncher<String> pickScreenshots = registerForActivityResult(
            new ActivityResultContracts.GetMultipleContents(), uris -> importImages(uris, false));

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityP2pPublishBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        projectId = getIntent().getStringExtra(EXTRA_PROJECT_ID);
        binding.screenshots.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        binding.iconFrame.setOnClickListener(v -> pickIcon.launch("image/*"));
        binding.pickScreenshots.setOnClickListener(v -> pickScreenshots.launch("image/*"));
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.publish) {
                publish();
                return true;
            }
            return false;
        });
        setPublishEnabled(false);
        io.execute(() -> {
            List<ProjectPackager.LocalProject> local = ProjectPackager.localProjects();
            runOnUiThread(() -> {
                projects.addAll(local);
                List<String> names = new ArrayList<>();
                for (ProjectPackager.LocalProject project : projects) {
                    names.add(project.title() + " · " + StoreUi.kindLabel(this, project.kind) + " · #" + project.scId);
                }
                binding.project.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names));
                binding.project.setOnItemClickListener((parent, view, position, id) -> select(projects.get(position)));
            });
        });
        StoreRuntime.get(this).start(ready -> {
            engine = ready;
            setPublishEnabled(true);
            prefillFromListing();
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        io.shutdown();
    }

    private void setPublishEnabled(boolean enabled) {
        binding.toolbar.getMenu().findItem(R.id.publish).setEnabled(enabled);
    }

    private void prefillFromListing() {
        if (projectId == null) {
            return;
        }
        JsonObject project = engine.myProject(projectId);
        if (project == null) {
            projectId = null;
            return;
        }
        binding.toolbar.setTitle(R.string.p2p_store_publish_new_version);
        binding.title.setText(Codec.string(project, "title", ""));
        binding.description.setText(Codec.string(project, "description", ""));
        binding.category.setText(Codec.string(project, "category", ""));
        binding.tags.setText(String.join(", ", Codec.strings(project, "tags")));
        File existingIcon = engine.myMedia(Codec.string(project, "icon", ""));
        if (existingIcon != null && existingIcon.isFile()) {
            icon = existingIcon;
            StoreUi.load(binding.icon, icon, 0);
        }
    }

    private void select(ProjectPackager.LocalProject project) {
        selected = project;
        if (binding.title.getText() == null || binding.title.getText().length() == 0) {
            binding.title.setText(project.title());
        }
        binding.versionName.setText(project.versionName);
        binding.versionCode.setText(String.valueOf(project.versionCode));
        if (icon == null && project.icon != null) {
            StoreUi.load(binding.icon, project.icon, 0);
        }
    }

    private void importImages(List<Uri> uris, boolean isIcon) {
        if (uris == null || uris.isEmpty()) {
            return;
        }
        io.execute(() -> {
            List<File> files = new ArrayList<>();
            for (Uri uri : uris) {
                try {
                    files.add(StoreUi.importImage(this, uri, isIcon ? 512 : 1280, isIcon));
                } catch (Exception ignored) {
                    // Not an image
                }
            }
            runOnUiThread(() -> {
                if (isIcon && !files.isEmpty()) {
                    icon = files.get(0);
                    StoreUi.load(binding.icon, icon, 0);
                } else if (!isIcon) {
                    for (File file : files) {
                        if (screenshots.size() < Records.MAX_SCREENSHOTS) {
                            screenshots.add(file);
                        }
                    }
                    showScreenshots();
                }
            });
        });
    }

    private void showScreenshots() {
        binding.screenshots.setVisibility(screenshots.isEmpty() ? View.GONE : View.VISIBLE);
        binding.screenshots.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_p2p_screenshot, parent, false);
                return new RecyclerView.ViewHolder(view) {
                };
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                StoreUi.load((ImageView) holder.itemView, screenshots.get(position), 0);
                holder.itemView.setOnLongClickListener(v -> {
                    screenshots.remove(holder.getBindingAdapterPosition());
                    showScreenshots();
                    return true;
                });
            }

            @Override
            public int getItemCount() {
                return screenshots.size();
            }
        });
    }

    private static String text(com.google.android.material.textfield.TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }

    private void publish() {
        if (selected == null) {
            binding.projectLayout.setError(getString(R.string.p2p_store_choose_project));
            return;
        }
        binding.projectLayout.setError(null);
        String title = text(binding.title);
        if (title.isEmpty()) {
            binding.titleLayout.setError(getString(R.string.p2p_store_title_required));
            return;
        }
        binding.titleLayout.setError(null);
        if (!engine.hasProfile()) {
            new MaterialAlertDialogBuilder(this)
                    .setMessage(R.string.p2p_store_create_profile_first)
                    .setPositiveButton(R.string.p2p_store_edit_profile,
                            (dialog, which) -> startActivity(new Intent(this, StoreEditProfileActivity.class)))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }
        StoreEngine.ProjectDetails details = new StoreEngine.ProjectDetails();
        details.title = title;
        details.description = text(binding.description);
        details.category = text(binding.category);
        details.kind = selected.kind;
        details.packageName = selected.packageName;
        for (String tag : text(binding.tags).split(",")) {
            if (!tag.trim().isEmpty()) {
                details.tags.add(tag.trim());
            }
        }
        details.icon = icon != null ? icon : selected.icon;
        details.screenshots.addAll(screenshots);
        String versionName = text(binding.versionName).isEmpty() ? selected.versionName : text(binding.versionName);
        long versionCode;
        try {
            versionCode = Long.parseLong(text(binding.versionCode));
        } catch (NumberFormatException e) {
            versionCode = selected.versionCode;
        }
        String notes = text(binding.notes);
        ProjectPackager.LocalProject project = selected;
        long code = versionCode;

        setPublishEnabled(false);
        binding.publishing.setVisibility(View.VISIBLE);
        Toast.makeText(this, R.string.p2p_store_publishing, Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            try {
                File staging = new File(getCacheDir(), "store_publish");
                File archive = ProjectPackager.pack(this, project, staging);
                engine.publishVersion(projectId, details, archive, versionName, code, notes);
                engine.syncSoon();
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.p2p_store_published, Toast.LENGTH_LONG).show();
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setPublishEnabled(true);
                    binding.publishing.setVisibility(View.GONE);
                    Toast.makeText(this, getString(R.string.p2p_store_publish_failed, e.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}
