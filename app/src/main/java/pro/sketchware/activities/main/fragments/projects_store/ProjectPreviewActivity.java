package pro.sketchware.activities.main.fragments.projects_store;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.LinearLayout;

import androidx.core.widget.NestedScrollView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.chip.Chip;
import com.google.gson.Gson;

import java.util.ArrayList;

import pro.sketchware.activities.main.fragments.projects_store.adapters.ProjectScreenshotsAdapter;
import pro.sketchware.activities.main.fragments.projects_store.api.ProjectModel;
import pro.sketchware.activities.main.fragments.projects_store.api.SketchwareStoreApi;
import pro.sketchware.databinding.FragmentStoreProjectPreviewBinding;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.UI;

public class ProjectPreviewActivity extends BaseAppCompatActivity {
    private static final long TITLE_CONTAINER_FADE_DURATION = 150L;

    private FragmentStoreProjectPreviewBinding binding;
    private ProjectModel.Project project;
    private final SketchwareStoreApi storeApi = new SketchwareStoreApi();
    private boolean isTitleContainerShown;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        binding = FragmentStoreProjectPreviewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        loadProjectData(getIntent().getExtras());
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
            if (detailedProject == null || binding == null) {
                return;
            }
            project = detailedProject;
            bindProject(project);
        });
    }

    private void bindProject(ProjectModel.Project project) {
        if (project == null || binding == null) {
            return;
        }

        binding.name.setText(project.getTitle());
        binding.author.setText(project.getUserName());
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

        binding.downloads.setText("Downloads: " + project.getDownloads());
        String projectSize = project.getProjectSize();
        binding.filesize.setText("Size: " + (isEmpty(projectSize) ? "Unknown" : projectSize));
        String publishedDate = project.getPublishedDate();
        binding.timestamp.setText("Released: " + (isEmpty(publishedDate) ? "Unknown" : publishedDate));

        binding.btnComments.setVisibility(project.hasComments() ? View.VISIBLE : View.GONE);
        binding.btnComments.setOnClickListener(v -> openCommentsSheet());
        binding.btnDownload.setOnClickListener(v -> openProjectInApp());
        binding.btnOpenIn.setOnClickListener(v -> openProject());
        binding.btnBack.setOnClickListener(v -> finish());

        binding.toolbarTitle.setSelected(true);
        binding.toolbarTitle.setText(project.getTitle());
        binding.toolbarSubtitle.setText(project.getUserName());

        ArrayList<String> screenshots = project.getScreenshotUrls();
        binding.screenshots.setAdapter(new ProjectScreenshotsAdapter(screenshots));

        if (!isEmpty(project.getIcon())) {
            UI.loadImageFromUrl(binding.icon, project.getIcon());
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

    private void addChip(String name) {
        Chip chip = new Chip(binding.chipsContainer.getContext());
        chip.setText(name);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -1);
        params.setMarginEnd(SketchwareUtil.dpToPx(12f));
        binding.chipsContainer.addView(chip, params);
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

    private void openProjectInApp() {
        binding.btnDownload.setEnabled(false);
        storeApi.getDownloadUrl(project.getSlug(), downloadUrl -> {
            if (binding == null) {
                return;
            }
            binding.btnDownload.setEnabled(true);

            String url = isEmpty(downloadUrl) ? project.getFirstVersionFileUrl() : downloadUrl;
            if (isEmpty(url)) {
                SketchwareUtil.toastError("Download unavailable");
                return;
            }

            Intent intent = new Intent(this, ProjectWebViewActivity.class);
            intent.putExtra("url", url);
            startActivity(intent);
        });
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
