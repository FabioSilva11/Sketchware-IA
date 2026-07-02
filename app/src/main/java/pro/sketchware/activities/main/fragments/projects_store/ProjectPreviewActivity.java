package pro.sketchware.activities.main.fragments.projects_store;

import android.Manifest;
import android.app.DownloadManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.webkit.URLUtil;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.GridLayoutManager;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import pro.sketchware.R;
import pro.sketchware.activities.main.fragments.projects_store.adapters.ProjectScreenshotsAdapter;
import pro.sketchware.activities.main.fragments.projects_store.adapters.StoreProjectsAdapter;
import pro.sketchware.activities.main.fragments.projects_store.api.ProjectModel;
import pro.sketchware.activities.main.fragments.projects_store.api.SketchwareStoreApi;
import pro.sketchware.databinding.DialogStoreDownloadProgressBinding;
import pro.sketchware.databinding.FragmentStoreProjectPreviewBinding;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.UI;

public class ProjectPreviewActivity extends BaseAppCompatActivity {
    private static final long TITLE_CONTAINER_FADE_DURATION = 150L;
    private static final int REQUEST_STORAGE = 1001;
    private static final long PROGRESS_POLL_DELAY_MS = 450L;

    private FragmentStoreProjectPreviewBinding binding;
    private ProjectModel.Project project;
    private final SketchwareStoreApi storeApi = new SketchwareStoreApi();
    private final ArrayList<ProjectModel.Project> moreFromDeveloperProjects = new ArrayList<>();
    private final Handler downloadHandler = new Handler(Looper.getMainLooper());
    private StoreProjectsAdapter moreFromDeveloperAdapter;
    private boolean isTitleContainerShown;
    private String loadedDeveloperUsername;
    private long downloadId = -1L;
    private String pendingDownloadUrl;
    private CharSequence previousDownloadText;
    private AlertDialog downloadDialog;
    private DialogStoreDownloadProgressBinding downloadBinding;

    private final Runnable downloadProgressRunnable = new Runnable() {
        @Override
        public void run() {
            if (downloadId == -1L || downloadBinding == null) {
                return;
            }
            if (updateDownloadProgress()) {
                downloadHandler.postDelayed(this, PROGRESS_POLL_DELAY_MS);
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        binding = FragmentStoreProjectPreviewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setupMoreFromDeveloperGrid();

        loadProjectData(getIntent().getExtras());
    }

    @Override
    public void onDestroy() {
        downloadHandler.removeCallbacks(downloadProgressRunnable);
        dismissDownloadDialog();
        binding = null;
        super.onDestroy();
    }

    private void loadProjectData(Bundle bundle) {
        if (bundle == null) return;

        String json = bundle.getString("project_json");
        project = new Gson().fromJson(json, ProjectModel.Project.class);
        if (project == null) {
            finish();
            return;
        }
        bindProject(project);

        storeApi.getProjectDetails(project.getSlug(), detailedProject -> {
            if (detailedProject == null || binding == null || !isActivityAlive()) {
                return;
            }
            project = detailedProject;
            bindProject(project);
        });
    }

    private void bindProject(ProjectModel.Project project) {
        if (project == null || binding == null || !isActivityAlive()) {
            return;
        }

        binding.name.setText(project.getTitle());
        String developerName = isEmpty(project.getUserName()) ? "Unknown developer" : project.getUserName();
        binding.author.setText(developerName);
        binding.description.setText(project.getDescription());

        String whatIsNew = project.getWhatsnew();
        if (whatIsNew == null || whatIsNew.isEmpty()) {
            binding.cardWhatIsNew.setVisibility(View.GONE);
        } else {
            binding.cardWhatIsNew.setVisibility(View.VISIBLE);
            binding.whatIsNew.setText(whatIsNew);
        }

        binding.chipsContainer.removeAllViews();
        if ("1".equals(project.getIsEditorChoice())) {
            addChip("Featured");
        }

        if ("1".equals(project.getIsVerified())) {
            addChip("Open source");
        }

        if (!isEmpty(project.getCategory())) {
            addChip(project.getCategory());
        }

        if (!isEmpty(project.getCurrentVersion())) {
            addChip("v" + project.getCurrentVersion());
        }

        addChip("Rating " + project.getRating());
        addChip("Comments " + project.getReviews());
        if (!isEmpty(project.getLanguage())) {
            addChip(project.getLanguage());
        }
        if (!isEmpty(project.getLicense())) {
            addChip(project.getLicense());
        }
        if (!isEmpty(project.getSketchwareCompat())) {
            addChip("Sketchware " + project.getSketchwareCompat());
        }

        binding.detailDeveloper.setText("Developer: " + developerName);
        binding.detailVersion.setText("Version: " + (isEmpty(project.getCurrentVersion()) ? "Unknown" : project.getCurrentVersion()));
        binding.detailRating.setText("Rating: " + project.getRating());
        binding.detailRatingBar.setRating(project.getRatingValue());
        binding.detailComments.setText("Comments: " + project.getReviews());
        binding.downloads.setText("Downloads: " + project.getDownloads());
        String projectSize = project.getProjectSize();
        binding.filesize.setText("Size: " + (isEmpty(projectSize) ? "Unknown" : projectSize));
        String publishedDate = project.getPublishedDate();
        binding.timestamp.setText("Released: " + (isEmpty(publishedDate) ? "Unknown" : publishedDate));
        bindOptionalLink(binding.detailVideo, "Video: ", project.getVideoUrl());
        bindOptionalLink(binding.detailWebsite, "Website: ", project.getWebsite());
        bindOptionalLink(binding.detailGithub, "GitHub: ", project.getGithub());
        bindOptionalLink(binding.detailPrivacy, "Privacy: ", project.getPrivacyPolicy());
        loadMoreFromDeveloper(project);

        binding.btnComments.setVisibility(View.VISIBLE);
        binding.btnComments.setText("Comments (" + project.getReviews() + ")");
        binding.btnComments.setOnClickListener(v -> openCommentsSheet());
        binding.btnDownload.setOnClickListener(v -> openProjectInApp());
        binding.btnOpenIn.setOnClickListener(v -> openProject());
        binding.btnBack.setOnClickListener(v -> finish());

        binding.toolbarTitle.setSelected(true);
        binding.toolbarTitle.setText(project.getTitle());
        binding.toolbarSubtitle.setText(developerName);

        ArrayList<String> screenshots = project.getScreenshotUrls();
        binding.screenshots.setAdapter(new ProjectScreenshotsAdapter(screenshots));

        if (!isEmpty(project.getIcon())) {
            UI.loadImageFromUrl(binding.icon, project.getIcon());
        }
        binding.developerAvatar.setImageResource(R.drawable.ic_mtrl_profile);
        if (!isEmpty(project.getUserProfilePic())) {
            UI.loadImageFromUrl(binding.developerAvatar, project.getUserProfilePic());
        }
        UI.addSystemWindowInsetToPadding(binding.content, true, true, true, true);
        UI.addSystemWindowInsetToMargin(binding.buttonsContainer, true, false, true, true);
        UI.addSystemWindowInsetToPadding(binding.topScrim, false, true, false, false);
        UI.addSystemWindowInsetToPadding(binding.toolbar, true, true, true, false);

        binding.scrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, v1, v2, v3, v4) -> {
            int[] location = new int[2];
            binding.author.getLocationOnScreen(location);

            if (location[1] + binding.author.getHeight() + UI.getStatusBarHeight(this) < binding.toolbar.getHeight()) {
                if (isTitleContainerShown) return;
                isTitleContainerShown = true;

                binding.toolbarTitleContainer.setVisibility(View.VISIBLE);
                binding.toolbarTitleContainer.setTranslationY(24f);

                binding.topScrim.animate().alpha(1f).setDuration(TITLE_CONTAINER_FADE_DURATION).start();
                binding.toolbarTitleContainer.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setInterpolator(new LinearInterpolator())
                        .setDuration(TITLE_CONTAINER_FADE_DURATION)
                        .start();
            } else {
                if (!isTitleContainerShown) return;
                isTitleContainerShown = false;

                binding.topScrim.animate().alpha(0f).setDuration(TITLE_CONTAINER_FADE_DURATION).start();
                binding.toolbarTitleContainer.animate()
                        .translationY(24f)
                        .alpha(0f)
                        .setInterpolator(new LinearInterpolator())
                        .setDuration(TITLE_CONTAINER_FADE_DURATION)
                        .start();
            }
        });
    }

    private void setupMoreFromDeveloperGrid() {
        moreFromDeveloperAdapter = new StoreProjectsAdapter(moreFromDeveloperProjects, this);
        binding.moreFromDeveloperGrid.setLayoutManager(new GridLayoutManager(this, 3));
        binding.moreFromDeveloperGrid.setAdapter(moreFromDeveloperAdapter);
        binding.moreFromDeveloperGrid.setClipToPadding(false);
        binding.moreFromDeveloperGrid.setClipChildren(false);
    }

    private void loadMoreFromDeveloper(ProjectModel.Project currentProject) {
        String username = currentProject.getDeveloperUsername();
        if (isEmpty(username)) {
            showMoreFromDeveloper(false);
            return;
        }
        if (username.equals(loadedDeveloperUsername)) {
            return;
        }
        loadedDeveloperUsername = username;
        showMoreFromDeveloper(false);

        storeApi.getDeveloperPublications(username, projectModel -> {
            if (binding == null || !isActivityAlive() || !username.equals(loadedDeveloperUsername)) {
                return;
            }

            List<ProjectModel.Project> loaded = projectModel == null ? null : projectModel.getProjects();
            moreFromDeveloperProjects.clear();
            if (loaded != null) {
                for (ProjectModel.Project relatedProject : loaded) {
                    if (relatedProject == null || relatedProject.getSlug().equals(currentProject.getSlug())) {
                        continue;
                    }
                    moreFromDeveloperProjects.add(relatedProject);
                    if (moreFromDeveloperProjects.size() == 6) {
                        break;
                    }
                }
            }
            moreFromDeveloperAdapter.setProjects(moreFromDeveloperProjects);
            showMoreFromDeveloper(!moreFromDeveloperProjects.isEmpty());
        });
    }

    private void showMoreFromDeveloper(boolean show) {
        if (binding == null) {
            return;
        }
        binding.moreFromDeveloperTitle.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.moreFromDeveloperGrid.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void addChip(String name) {
        Chip chip = new Chip(binding.chipsContainer.getContext());
        chip.setText(name);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -1);
        params.setMarginEnd(SketchwareUtil.dpToPx(12f));
        binding.chipsContainer.addView(chip, params);
    }

    private void bindOptionalLink(TextView textView, String label, String url) {
        if (isEmpty(url)) {
            textView.setVisibility(View.GONE);
            textView.setOnClickListener(null);
            return;
        }
        textView.setVisibility(View.VISIBLE);
        textView.setText(label + url);
        textView.setOnClickListener(v -> openUrl(url));
    }

    private void openCommentsSheet() {
        CommentsBottomSheet sheet = CommentsBottomSheet.newInstance(project.getSlug());
        sheet.show(getSupportFragmentManager(), /* tag= */ CommentsBottomSheet.class.getSimpleName());
    }

    private void openProject() {
        String url = project.getWebsite();
        if (isEmpty(url)) {
            url = project.getGithub();
        }
        if (isEmpty(url)) {
            url = SketchwareStoreApi.SITE_URL;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        startActivity(intent);
    }

    private void openUrl(String url) {
        if (!isActivityAlive()) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }

    private void openProjectInApp() {
        if (binding == null || project == null) {
            return;
        }
        previousDownloadText = binding.btnDownload.getText();
        binding.btnDownload.setEnabled(false);
        binding.btnDownload.setText(R.string.store_download_preparing);
        showDownloadDialog();
        storeApi.getDownloadUrl(project.getSlug(), downloadUrl -> {
            if (binding == null || !isActivityAlive()) {
                return;
            }

            String url = isEmpty(downloadUrl) ? project.getFirstVersionFileUrl() : downloadUrl;
            if (isEmpty(url)) {
                updateDownloadDialog(R.string.store_download_unavailable, R.string.store_download_unavailable, false);
                resetDownloadButton();
                SketchwareUtil.toastError("Download unavailable");
                return;
            }

            startOrRequestPermission(url);
        });
    }

    private void showDownloadDialog() {
        downloadBinding = DialogStoreDownloadProgressBinding.inflate(getLayoutInflater());
        downloadBinding.downloadTitle.setText(R.string.store_download_preparing);
        downloadBinding.downloadStatus.setText(R.string.store_download_requesting);
        downloadBinding.downloadProgress.setIndeterminate(true);
        downloadDialog = new MaterialAlertDialogBuilder(this)
                .setView(downloadBinding.getRoot())
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        downloadDialog.setOnShowListener(dialog -> downloadDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener(v -> {
            cancelDownload();
            resetDownloadButton();
            dismissDownloadDialog();
        }));
        downloadDialog.setOnCancelListener(dialog -> {
            cancelDownload();
            resetDownloadButton();
        });
        downloadDialog.show();
        downloadDialog.setCanceledOnTouchOutside(false);
    }

    private void dismissDownloadDialog() {
        if (downloadDialog != null && downloadDialog.isShowing()) {
            downloadDialog.dismiss();
        }
        downloadDialog = null;
        downloadBinding = null;
    }

    private void updateDownloadDialog(int titleResId, int statusResId, boolean indeterminate) {
        if (downloadBinding == null) {
            return;
        }
        downloadBinding.downloadTitle.setText(titleResId);
        downloadBinding.downloadStatus.setText(statusResId);
        downloadBinding.downloadProgress.setIndeterminate(indeterminate);
        setDownloadDialogButtonText(indeterminate ? android.R.string.cancel : android.R.string.ok);
    }

    private void setDownloadDialogButtonText(int textResId) {
        if (downloadDialog != null && downloadDialog.isShowing()) {
            downloadDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setText(textResId);
        }
    }

    private void startOrRequestPermission(String url) {
        if (ensureStoragePermission()) {
            startDownload(url);
        } else {
            pendingDownloadUrl = url;
            updateDownloadDialog(R.string.store_download_waiting_permission, R.string.store_download_permission_required, true);
        }
    }

    private boolean ensureStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return true;
        }
        boolean write = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        boolean read = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        if (write && read) {
            return true;
        }
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE},
                REQUEST_STORAGE);
        return false;
    }

    private void startDownload(String url) {
        if (downloadBinding == null) {
            showDownloadDialog();
        }

        String fileName = URLUtil.guessFileName(url, null, null);
        downloadBinding.downloadFile.setText(fileName);
        downloadBinding.downloadTitle.setText(R.string.store_download_downloading);
        downloadBinding.downloadStatus.setText(R.string.store_download_starting);
        downloadBinding.downloadProgress.setIndeterminate(true);

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setTitle(fileName);
        request.setDescription("Sketchware Store download");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
        request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName);

        DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        if (downloadManager == null) {
            updateDownloadDialog(R.string.store_download_failed, R.string.store_download_failed, false);
            resetDownloadButton();
            return;
        }

        try {
            downloadId = downloadManager.enqueue(request);
        } catch (RuntimeException e) {
            updateDownloadDialog(R.string.store_download_failed, R.string.store_download_failed, false);
            resetDownloadButton();
            return;
        }
        downloadHandler.post(downloadProgressRunnable);
    }

    private boolean updateDownloadProgress() {
        DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        if (downloadManager == null || downloadBinding == null) {
            return false;
        }

        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        try (Cursor cursor = downloadManager.query(query)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return true;
            }

            int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            long downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            long total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));

            updateProgressText(downloaded, total);

            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                downloadBinding.downloadProgress.setIndeterminate(false);
                downloadBinding.downloadProgress.setProgress(100);
                downloadBinding.downloadTitle.setText(R.string.store_download_complete);
                downloadBinding.downloadStatus.setText(R.string.store_download_complete_message);
                setDownloadDialogButtonText(android.R.string.ok);
                downloadId = -1L;
                resetDownloadButton();
                return false;
            }

            if (status == DownloadManager.STATUS_FAILED) {
                updateDownloadDialog(R.string.store_download_failed, R.string.store_download_failed, false);
                downloadId = -1L;
                resetDownloadButton();
                return false;
            }

            if (status == DownloadManager.STATUS_PAUSED) {
                downloadBinding.downloadStatus.setText(R.string.store_download_paused);
            }
            return true;
        }
    }

    private void updateProgressText(long downloaded, long total) {
        if (downloadBinding == null) {
            return;
        }
        if (total > 0) {
            int progress = (int) Math.max(0, Math.min(100, downloaded * 100 / total));
            downloadBinding.downloadProgress.setIndeterminate(false);
            downloadBinding.downloadProgress.setProgress(progress);
            downloadBinding.downloadStatus.setText(getString(R.string.store_download_progress, progress, formatBytes(downloaded), formatBytes(total)));
        } else {
            downloadBinding.downloadProgress.setIndeterminate(true);
            downloadBinding.downloadStatus.setText(getString(R.string.store_download_progress_unknown, formatBytes(downloaded)));
        }
    }

    private void cancelDownload() {
        pendingDownloadUrl = null;
        if (downloadId == -1L) {
            return;
        }
        DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        if (downloadManager != null) {
            downloadManager.remove(downloadId);
        }
        downloadId = -1L;
        downloadHandler.removeCallbacks(downloadProgressRunnable);
    }

    private void resetDownloadButton() {
        if (binding == null) {
            return;
        }
        binding.btnDownload.setEnabled(true);
        binding.btnDownload.setText(previousDownloadText == null ? getString(R.string.store_preview_download) : previousDownloadText);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes / 1024d;
        if (value < 1024d) {
            return String.format(Locale.US, "%.1f KB", value);
        }
        value /= 1024d;
        if (value < 1024d) {
            return String.format(Locale.US, "%.1f MB", value);
        }
        return String.format(Locale.US, "%.1f GB", value / 1024d);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE) {
            boolean granted = grantResults.length > 0;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }
            if (granted && pendingDownloadUrl != null) {
                startDownload(pendingDownloadUrl);
                pendingDownloadUrl = null;
            } else {
                updateDownloadDialog(R.string.store_download_failed, R.string.store_download_permission_denied, false);
                resetDownloadButton();
            }
        }
    }

    private boolean isActivityAlive() {
        return !isFinishing() && (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !isDestroyed());
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
