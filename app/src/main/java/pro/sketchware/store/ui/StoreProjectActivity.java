package pro.sketchware.store.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.design.DesignActivity;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import a.a.a.lC;
import mod.hey.studios.project.ProjectTracker;
import pro.sketchware.R;
import pro.sketchware.activities.studio.AndroidStudioProjectActivity;
import pro.sketchware.databinding.ActivityP2pProjectBinding;
import pro.sketchware.databinding.ItemP2pMetricBinding;
import pro.sketchware.databinding.ItemP2pVersionBinding;
import pro.sketchware.store.ProjectPackager;
import pro.sketchware.store.StoreRuntime;
import pro.sketchware.store.core.Catalog;
import pro.sketchware.store.core.P2PNode;
import pro.sketchware.store.core.Records;
import pro.sketchware.store.core.StoreEngine;

/** A project in the store: its listing, metrics, interactions and versions to download. */
public class StoreProjectActivity extends BaseAppCompatActivity {

    private static final String EXTRA_AUTHOR = "author";
    private static final String EXTRA_PROJECT = "project";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private ActivityP2pProjectBinding binding;
    private StoreRuntime runtime;
    private StoreEngine engine;
    private String author;
    private String projectId;
    private Catalog.Listing listing;
    private String downloadingInfohash;
    private Boolean likedOverride;
    private Boolean favoriteOverride;
    private boolean viewRecorded;
    private final Runnable onStoreChanged = this::bind;
    private final Runnable pollProgress = new Runnable() {
        @Override
        public void run() {
            if (engine != null && downloadingInfohash != null) {
                engine.node().pollTransfers();
                handler.postDelayed(this, 1000);
            }
        }
    };

    public static Intent intent(Context context, String author, String projectId) {
        return new Intent(context, StoreProjectActivity.class)
                .putExtra(EXTRA_AUTHOR, author)
                .putExtra(EXTRA_PROJECT, projectId);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityP2pProjectBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.toolbar.setOnMenuItemClickListener(this::onMenuItemClick);
        binding.toolbar.getMenu().findItem(R.id.project_new_version).setVisible(false);
        binding.toolbar.getMenu().findItem(R.id.project_unpublish).setVisible(false);
        author = getIntent().getStringExtra(EXTRA_AUTHOR);
        projectId = getIntent().getStringExtra(EXTRA_PROJECT);
        binding.screenshots.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        runtime = StoreRuntime.get(this);
        runtime.start(ready -> {
            engine = ready;
            bind();
            if (listing != null) {
                engine.refreshPeerCount(listing);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        runtime.addListener(onStoreChanged);
    }

    @Override
    protected void onStop() {
        super.onStop();
        runtime.removeListener(onStoreChanged);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        io.shutdown();
    }

    private boolean isOwn() {
        return engine != null && engine.identity().id().equals(author);
    }

    private void bind() {
        if (engine == null || binding == null) {
            return;
        }
        listing = engine.catalog().listing(author, projectId);
        if (listing == null) {
            binding.title.setText(R.string.p2p_store_empty_none);
            return;
        }
        if (!viewRecorded && !isOwn()) {
            viewRecorded = true;
            engine.recordEvent(Records.EVENT_VIEW, author, projectId, 0);
        }
        binding.toolbar.setTitle(listing.title);
        binding.title.setText(listing.title);
        binding.author.setText(getString(R.string.p2p_store_by,
                listing.authorName.isEmpty() ? StoreUi.shortId(listing.author) : listing.authorName));
        binding.author.setOnClickListener(v -> startActivity(StoreProfileActivity.intent(this, author)));
        binding.kind.setText(StoreUi.kindLabel(this, listing.kind));
        StoreUi.load(binding.icon, listing.icon, R.drawable.sketch_app_icon);
        binding.description.setText(listing.description);
        binding.details.setText(getString(R.string.p2p_store_details, listing.packageName, listing.category,
                String.join(", ", listing.tags), listing.author));
        bindMetrics();
        bindInteractions();
        bindScreenshots();
        bindVersions();

        Catalog.Version latest = listing.latest();
        binding.download.setEnabled(latest != null && downloadingInfohash == null);
        // One icon: download (and import) the latest version, or open it once imported
        boolean imported = importedProject(latest) != null;
        binding.download.setIconResource(imported ? R.drawable.ic_p2p_open : R.drawable.ic_mtrl_download);
        CharSequence downloadLabel = getString(imported ? R.string.p2p_store_open : R.string.p2p_store_download_open);
        binding.download.setContentDescription(downloadLabel);
        binding.download.setTooltipText(downloadLabel);
        binding.download.setOnClickListener(v -> {
            String existing = importedProject(listing.latest());
            if (existing != null) {
                openProject(existing);
            } else if (listing.latest() != null) {
                download(listing.latest());
            }
        });
        binding.toolbar.getMenu().findItem(R.id.project_new_version).setVisible(isOwn());
        binding.toolbar.getMenu().findItem(R.id.project_unpublish).setVisible(isOwn());
    }

    private boolean onMenuItemClick(MenuItem item) {
        if (listing == null) {
            return false;
        }
        if (item.getItemId() == R.id.project_new_version) {
            startActivity(new Intent(this, StorePublishActivity.class)
                    .putExtra(StorePublishActivity.EXTRA_PROJECT_ID, projectId));
            return true;
        }
        if (item.getItemId() == R.id.project_unpublish) {
            new MaterialAlertDialogBuilder(this)
                    .setMessage(getString(R.string.p2p_store_unpublish_confirm, listing.title))
                    .setPositiveButton(R.string.p2p_store_unpublish, (dialog, which) -> io.execute(() -> {
                        try {
                            engine.unpublish(projectId);
                            engine.syncSoon();
                            runOnUiThread(this::finish);
                        } catch (Exception e) {
                            runOnUiThread(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
                        }
                    }))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return true;
        }
        return false;
    }

    private void bindMetrics() {
        Catalog.Metrics m = listing.metrics;
        ViewGroup row = binding.metrics;
        row.removeAllViews();
        addMetric(row, StoreUi.count(m.likes), R.string.p2p_store_metric_likes);
        addMetric(row, StoreUi.count(m.downloads), R.string.p2p_store_metric_downloads);
        addMetric(row, StoreUi.count(m.views), R.string.p2p_store_metric_views);
        addMetric(row, StoreUi.count(m.favorites), R.string.p2p_store_metric_favorites);
        addMetric(row, StoreUi.count(m.versions), R.string.p2p_store_metric_versions);
        addMetric(row, StoreUi.count(m.peers), R.string.p2p_store_metric_peers);
        addMetric(row, String.valueOf(Math.round(m.score)), R.string.p2p_store_metric_popularity);
    }

    private void addMetric(ViewGroup row, String value, int label) {
        ItemP2pMetricBinding metric = ItemP2pMetricBinding.inflate(LayoutInflater.from(this), row, false);
        metric.value.setText(value);
        metric.label.setText(label);
        row.addView(metric.getRoot());
    }

    private void bindInteractions() {
        String me = engine.identity().id();
        boolean liked = likedOverride != null ? likedOverride : engine.catalog().hasLiked(me, author, projectId, false);
        boolean favorite = favoriteOverride != null ? favoriteOverride
                : engine.catalog().hasLiked(me, author, projectId, true);
        binding.like.setIconResource(liked ? R.drawable.ic_p2p_heart_filled : R.drawable.ic_p2p_heart);
        CharSequence likeLabel = getString(liked ? R.string.p2p_store_liked : R.string.p2p_store_like);
        binding.like.setContentDescription(likeLabel);
        binding.like.setTooltipText(likeLabel);
        binding.favorite.setIconResource(favorite ? R.drawable.ic_p2p_star : R.drawable.ic_p2p_star_outline);
        CharSequence favoriteLabel = getString(favorite ? R.string.p2p_store_favorited : R.string.p2p_store_favorite);
        binding.favorite.setContentDescription(favoriteLabel);
        binding.favorite.setTooltipText(favoriteLabel);
        binding.like.setEnabled(!isOwn());
        binding.favorite.setEnabled(!isOwn());
        binding.like.setOnClickListener(v -> {
            likedOverride = !liked;
            engine.recordEvent(liked ? Records.EVENT_UNLIKE : Records.EVENT_LIKE, author, projectId, 0);
            bindInteractions();
        });
        binding.favorite.setOnClickListener(v -> {
            favoriteOverride = !favorite;
            engine.recordEvent(favorite ? Records.EVENT_UNFAVORITE : Records.EVENT_FAVORITE, author, projectId, 0);
            bindInteractions();
        });
    }

    private void bindScreenshots() {
        List<File> shots = listing.screenshots;
        binding.screenshots.setVisibility(shots.isEmpty() ? View.GONE : View.VISIBLE);
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
                StoreUi.load((ImageView) holder.itemView, shots.get(position), 0);
            }

            @Override
            public int getItemCount() {
                return shots.size();
            }
        });
    }

    private void bindVersions() {
        binding.versions.removeAllViews();
        for (Catalog.Version version : listing.versions) {
            ItemP2pVersionBinding row = ItemP2pVersionBinding.inflate(getLayoutInflater(), binding.versions, false);
            row.name.setText(version.name);
            row.info.setText(getString(R.string.p2p_store_version_info, version.code,
                    StoreUi.size(this, version.size), StoreUi.date(this, version.publishedAt)));
            row.notes.setText(version.notes);
            row.notes.setVisibility(version.notes.isEmpty() ? View.GONE : View.VISIBLE);
            row.download.setEnabled(downloadingInfohash == null);
            row.download.setOnClickListener(v -> download(version));
            binding.versions.addView(row.getRoot());
        }
    }

    // Downloading and importing

    private void download(Catalog.Version version) {
        if (downloadingInfohash != null) {
            return;
        }
        Catalog.Listing current = listing;
        downloadingInfohash = version.infohash;
        binding.progressGroup.setVisibility(View.VISIBLE);
        binding.progress.setIndeterminate(true);
        binding.progressText.setText(R.string.p2p_store_progress_waiting);
        binding.download.setEnabled(false);
        binding.cancel.setOnClickListener(v -> {
            engine.node().cancel(version.infohash);
            finishDownload(null);
        });
        handler.post(pollProgress);
        engine.downloadVersion(current, version, new P2PNode.TransferListener() {
            @Override
            public void onProgress(P2PNode.Transfer transfer) {
                runOnUiThread(() -> showProgress(transfer));
            }

            @Override
            public void onFinished(P2PNode.Transfer transfer) {
            }

            @Override
            public void onFailed(P2PNode.Transfer transfer, String error) {
            }
        }, file -> runOnUiThread(() -> importDownloaded(current, version, file)),
                error -> runOnUiThread(() -> finishDownload(getString(R.string.p2p_store_download_failed, error))));
    }

    private void showProgress(P2PNode.Transfer transfer) {
        if (binding == null || downloadingInfohash == null) {
            return;
        }
        if (transfer.size > 0) {
            binding.progress.setIndeterminate(false);
            binding.progress.setProgressCompat(Math.round(transfer.progress * 100), true);
        }
        binding.progressText.setText(getString(R.string.p2p_store_progress, Math.round(transfer.progress * 100),
                transfer.peers, StoreUi.size(this, transfer.downloadRate)));
    }

    private void importDownloaded(Catalog.Listing current, Catalog.Version version, File file) {
        binding.progress.setIndeterminate(true);
        binding.progressText.setText(R.string.p2p_store_verifying);
        io.execute(() -> {
            try {
                String scId = ProjectPackager.importPackage(file, current.kind);
                rememberImport(version, scId);
                engine.recordEvent(Records.EVENT_DOWNLOAD, current.author, current.id, version.code);
                runOnUiThread(() -> {
                    finishDownload(getString(R.string.p2p_store_imported, scId));
                    openProject(scId);
                });
            } catch (Exception e) {
                runOnUiThread(() -> finishDownload(getString(R.string.p2p_store_download_failed, e.getMessage())));
            }
        });
    }

    private void finishDownload(String message) {
        downloadingInfohash = null;
        handler.removeCallbacks(pollProgress);
        if (binding != null) {
            binding.progressGroup.setVisibility(View.GONE);
            bind();
        }
        if (message != null) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }

    private SharedPreferences imports() {
        return getSharedPreferences("p2p_store_imports", MODE_PRIVATE);
    }

    private void rememberImport(Catalog.Version version, String scId) {
        imports().edit().putString(version.infohash, scId).apply();
    }

    /** The project a version was imported as, if it still exists. */
    private String importedProject(Catalog.Version version) {
        if (version == null) {
            return null;
        }
        String scId = imports().getString(version.infohash, null);
        return scId != null && lC.b(scId) != null ? scId : null;
    }

    private void openProject(String scId) {
        java.util.HashMap<String, Object> project = lC.b(scId);
        if (project == null) {
            return;
        }
        Intent intent;
        if (lC.isAndroidStudioProject(project)) {
            intent = new Intent(this, AndroidStudioProjectActivity.class)
                    .putExtra(AndroidStudioProjectActivity.EXTRA_SC_ID, scId);
        } else {
            ProjectTracker.setScId(scId);
            intent = new Intent(this, DesignActivity.class).putExtra("sc_id", scId);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }
}
