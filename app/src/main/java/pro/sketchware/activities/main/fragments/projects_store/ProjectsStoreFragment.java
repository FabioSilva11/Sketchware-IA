package pro.sketchware.activities.main.fragments.projects_store;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;

import com.google.android.material.chip.Chip;
import com.google.android.material.transition.MaterialFadeThrough;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import pro.sketchware.R;
import pro.sketchware.activities.main.fragments.projects_store.adapters.StoreProjectsAdapter;
import pro.sketchware.activities.main.fragments.projects_store.api.ProjectModel;
import pro.sketchware.activities.main.fragments.projects_store.api.SketchwareStoreApi;
import pro.sketchware.databinding.FragmentProjectsStoreBinding;
import pro.sketchware.utility.UI;

public class ProjectsStoreFragment extends Fragment {
    private static final int GRID_SPAN_COUNT = 4;

    private FragmentProjectsStoreBinding binding;
    private SketchwareStoreApi storeApi;
    private StoreProjectsAdapter adapter;
    private final ArrayList<ProjectModel.Project> projects = new ArrayList<>();
    private final Handler searchHandler = new Handler(Looper.getMainLooper());

    private String selectedKind = "all";
    private String selectedSort = "newest";
    private String selectedCategory;
    private int nextOffset;
    private int totalResults;
    private boolean loading;
    private Runnable pendingSearch;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setEnterTransition(new MaterialFadeThrough());
        setReturnTransition(new MaterialFadeThrough());
        setExitTransition(new MaterialFadeThrough());
        setReenterTransition(new MaterialFadeThrough());
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentProjectsStoreBinding.inflate(inflater, container, false);
        storeApi = new SketchwareStoreApi();
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupOfficialStoreLink();
        setupGrid();
        setupStaticChips();
        setupSearch();
        setupPaging();
        loadStats();
        loadCategories();
        fetchPublications(true);

        UI.addSystemWindowInsetToMargin(binding.cardWarning, true, false, true, false);
        UI.addSystemWindowInsetToPadding(binding.storeContent, true, false, true, true);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (pendingSearch != null) {
            searchHandler.removeCallbacks(pendingSearch);
        }
        binding = null;
    }

    private void setupOfficialStoreLink() {
        binding.cardWarning.setOnClickListener(v -> openOfficialStoreWebsite());
        binding.storeSideNote.setOnClickListener(v -> openOfficialStoreWebsite());
        binding.openStoreButton.setOnClickListener(v -> openOfficialStoreWebsite());
    }

    private void setupGrid() {
        adapter = new StoreProjectsAdapter(projects, requireActivity());
        binding.publicationsGrid.setLayoutManager(new GridLayoutManager(getContext(), GRID_SPAN_COUNT));
        binding.publicationsGrid.setAdapter(adapter);
        binding.publicationsGrid.setClipToPadding(false);
        binding.publicationsGrid.setClipChildren(false);
    }

    private void setupStaticChips() {
        addSelectableChip(binding.kindChips, "All", "all", true);
        addSelectableChip(binding.kindChips, "APK", "apk", false);
        addSelectableChip(binding.kindChips, "SWB", "swb", false);
        binding.kindChips.setOnCheckedChangeListener((group, checkedId) -> {
            Chip chip = group.findViewById(checkedId);
            selectedKind = chip == null ? "all" : String.valueOf(chip.getTag());
            fetchPublications(true);
        });

        addSelectableChip(binding.sortChips, "Newest", "newest", true);
        addSelectableChip(binding.sortChips, "Downloads", "downloads", false);
        addSelectableChip(binding.sortChips, "Rating", "rating", false);
        addSelectableChip(binding.sortChips, "Updated", "updated", false);
        binding.sortChips.setOnCheckedChangeListener((group, checkedId) -> {
            Chip chip = group.findViewById(checkedId);
            selectedSort = chip == null ? "newest" : String.valueOf(chip.getTag());
            fetchPublications(true);
        });

        binding.freeChip.setOnCheckedChangeListener((buttonView, isChecked) -> fetchPublications(true));
        binding.openSourceChip.setOnCheckedChangeListener((buttonView, isChecked) -> fetchPublications(true));
        binding.featuredChip.setOnCheckedChangeListener((buttonView, isChecked) -> fetchPublications(true));
    }

    private void setupSearch() {
        binding.searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                fetchPublications(true);
                return true;
            }
            return false;
        });

        binding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                debounceSearch();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void setupPaging() {
        binding.loadMoreButton.setOnClickListener(v -> fetchPublications(false));
    }

    private void loadStats() {
        storeApi.getStoreStats(stats -> {
            if (binding == null || stats == null) {
                return;
            }
            binding.statApks.setText(String.format(Locale.US, "%d\nAPKs", stats.getApks()));
            binding.statSwbs.setText(String.format(Locale.US, "%d\nSWBs", stats.getSwbs()));
            binding.statUsers.setText(String.format(Locale.US, "%d\nUsers", stats.getUsers()));
            binding.statDownloads.setText(String.format(Locale.US, "%d\nDownloads", stats.getDownloads()));
        });
    }

    private void loadCategories() {
        binding.categoryChips.removeAllViews();
        addCategoryChip("All", null, true);
        storeApi.getCategories(categories -> {
            if (binding == null || categories == null) {
                return;
            }
            for (ProjectModel.Category category : categories) {
                addCategoryChip(category.getName(), category.getSlug(), false);
            }
            binding.categoryChips.setOnCheckedChangeListener((group, checkedId) -> {
                Chip chip = group.findViewById(checkedId);
                selectedCategory = chip == null ? null : (String) chip.getTag();
                fetchPublications(true);
            });
        });
    }

    private void fetchPublications(boolean reset) {
        if (binding == null || loading) {
            return;
        }

        if (reset) {
            nextOffset = 0;
            totalResults = 0;
            projects.clear();
            adapter.setProjects(projects);
        }

        loading = true;
        binding.emptyState.setVisibility(View.GONE);
        binding.loadMoreButton.setVisibility(View.GONE);
        binding.resultSummary.setText(R.string.store_loading);

        storeApi.getPublications(
                selectedSort,
                selectedKind,
                selectedCategory,
                currentQuery(),
                binding.freeChip.isChecked() ? Boolean.TRUE : null,
                binding.openSourceChip.isChecked() ? Boolean.TRUE : null,
                binding.featuredChip.isChecked() ? Boolean.TRUE : null,
                SketchwareStoreApi.DEFAULT_PAGE_SIZE,
                nextOffset,
                projectModel -> {
                    if (binding == null) {
                        return;
                    }
                    loading = false;
                    handlePublications(projectModel, reset);
                }
        );
    }

    private void handlePublications(ProjectModel projectModel, boolean reset) {
        List<ProjectModel.Project> loaded = projectModel == null ? null : projectModel.getProjects();
        totalResults = projectModel == null ? 0 : projectModel.getTotal();

        if (loaded != null && !loaded.isEmpty()) {
            if (reset) {
                projects.clear();
                projects.addAll(loaded);
                adapter.setProjects(projects);
            } else {
                projects.addAll(loaded);
                adapter.addProjects(loaded);
            }
            nextOffset = projects.size();
        }

        boolean isEmpty = projects.isEmpty();
        binding.emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        binding.loadMoreButton.setVisibility(!isEmpty && nextOffset < totalResults ? View.VISIBLE : View.GONE);
        binding.resultSummary.setText(resultSummaryText());
    }

    private String resultSummaryText() {
        if (projects.isEmpty() && totalResults == 0) {
            return getString(R.string.store_empty);
        }
        return String.format(Locale.US, "%d of %d publications - %s - %s",
                projects.size(),
                totalResults,
                selectedSort,
                "all".equals(selectedKind) ? "all types" : selectedKind.toUpperCase(Locale.US));
    }

    private void debounceSearch() {
        if (pendingSearch != null) {
            searchHandler.removeCallbacks(pendingSearch);
        }
        pendingSearch = () -> fetchPublications(true);
        searchHandler.postDelayed(pendingSearch, 450L);
    }

    private String currentQuery() {
        return binding.searchInput.getText() == null ? "" : binding.searchInput.getText().toString().trim();
    }

    private void addCategoryChip(String text, String slug, boolean checked) {
        Chip chip = addSelectableChip(binding.categoryChips, text, slug, checked);
        chip.setMaxLines(1);
    }

    private Chip addSelectableChip(ViewGroup group, String text, String tag, boolean checked) {
        Chip chip = new Chip(group.getContext());
        chip.setId(View.generateViewId());
        chip.setText(text);
        chip.setTag(tag);
        chip.setCheckable(true);
        chip.setChecked(checked);
        group.addView(chip);
        return chip;
    }

    private void openOfficialStoreWebsite() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(SketchwareStoreApi.SITE_URL));
        startActivity(intent);
    }
}
