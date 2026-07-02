package pro.sketchware.activities.main.fragments.projects_store;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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
    private static final int GRID_SPAN_COUNT = 3;

    private FragmentProjectsStoreBinding binding;
    private SketchwareStoreApi storeApi;
    private StoreProjectsAdapter adapter;
    private final ArrayList<ProjectModel.Project> projects = new ArrayList<>();
    private final ArrayList<ProjectModel.Category> categories = new ArrayList<>();
    private final Handler searchHandler = new Handler(Looper.getMainLooper());

    private String selectedKind = "all";
    private String selectedSort = "newest";
    private String selectedCategory;
    private String searchQuery = "";
    private boolean selectedFree;
    private boolean selectedOpenSource;
    private boolean selectedFeatured;
    private int nextOffset;
    private int totalResults;
    private boolean loading;
    private int requestGeneration;
    private Runnable pendingSearch;
    private ObjectAnimator shimmerAnimator;

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
        setupFilterFab();
        setupPaging();
        loadStats();
        loadCategories();
        fetchPublications(true);

        UI.addSystemWindowInsetToMargin(binding.cardWarning, true, false, true, false);
        UI.addSystemWindowInsetToPadding(binding.storeContent, true, false, true, true);
        UI.addSystemWindowInsetToMargin(binding.filterFab, false, false, true, true);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (pendingSearch != null) {
            searchHandler.removeCallbacks(pendingSearch);
        }
        stopShimmer();
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

    private void setupFilterFab() {
        binding.filterFab.setOnClickListener(v -> showFiltersDialog());
    }

    private void setupPaging() {
        binding.loadMoreButton.setOnClickListener(v -> fetchPublications(false));
    }

    public void setSearchQuery(String query) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.equals(searchQuery)) {
            return;
        }
        searchQuery = normalizedQuery;
        if (binding != null) {
            debounceSearch();
        }
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
        storeApi.getCategories(categories -> {
            if (binding == null || categories == null) {
                return;
            }
            this.categories.clear();
            this.categories.addAll(categories);
            if (!loading) {
                binding.resultSummary.setText(resultSummaryText());
            }
        });
    }

    private void showFiltersDialog() {
        if (binding == null) {
            return;
        }

        ScrollView scrollView = new ScrollView(requireContext());
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        content.setPadding(padding, 0, padding, 0);
        scrollView.addView(content);

        addSectionTitle(content, R.string.store_filter_type);
        ChipGroup kindGroup = createSelectionGroup();
        addSelectableChip(kindGroup, "All", "all", "all".equals(selectedKind));
        addSelectableChip(kindGroup, "APK", "apk", "apk".equals(selectedKind));
        addSelectableChip(kindGroup, "SWB", "swb", "swb".equals(selectedKind));
        content.addView(kindGroup);

        addSectionTitle(content, R.string.store_filter_sort);
        ChipGroup sortGroup = createSelectionGroup();
        addSelectableChip(sortGroup, "Newest", "newest", "newest".equals(selectedSort));
        addSelectableChip(sortGroup, "Downloads", "downloads", "downloads".equals(selectedSort));
        addSelectableChip(sortGroup, "Rating", "rating", "rating".equals(selectedSort));
        addSelectableChip(sortGroup, "Updated", "updated", "updated".equals(selectedSort));
        content.addView(sortGroup);

        addSectionTitle(content, R.string.store_filter_flags);
        CheckBox freeCheck = createCheckBox(R.string.store_filter_free, selectedFree);
        CheckBox openSourceCheck = createCheckBox(R.string.store_filter_open_source, selectedOpenSource);
        CheckBox featuredCheck = createCheckBox(R.string.store_filter_featured, selectedFeatured);
        content.addView(freeCheck);
        content.addView(openSourceCheck);
        content.addView(featuredCheck);

        addSectionTitle(content, R.string.store_filter_category);
        ChipGroup categoryGroup = createSelectionGroup();
        addSelectableChip(categoryGroup, "All", null, selectedCategory == null);
        for (ProjectModel.Category category : categories) {
            addSelectableChip(categoryGroup, category.getName(), category.getSlug(), category.getSlug().equals(selectedCategory));
        }
        content.addView(categoryGroup);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.store_filters_title)
                .setView(scrollView)
                .setNeutralButton(R.string.store_filters_clear, (dialog, which) -> {
                    resetFilters();
                    fetchPublications(true);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.store_filters_apply, (dialog, which) -> {
                    selectedKind = checkedStringTag(kindGroup, "all");
                    selectedSort = checkedStringTag(sortGroup, "newest");
                    selectedCategory = checkedNullableStringTag(categoryGroup);
                    selectedFree = freeCheck.isChecked();
                    selectedOpenSource = openSourceCheck.isChecked();
                    selectedFeatured = featuredCheck.isChecked();
                    fetchPublications(true);
                })
                .show();
    }

    private ChipGroup createSelectionGroup() {
        ChipGroup group = new ChipGroup(requireContext());
        group.setSingleSelection(true);
        group.setSelectionRequired(true);
        group.setPadding(0, 4, 0, dp(8));
        return group;
    }

    private void addSectionTitle(LinearLayout content, int titleResId) {
        TextView title = new TextView(requireContext());
        title.setText(titleResId);
        title.setTextColor(requireContext().getColor(R.color.chat_text_primary));
        title.setTextSize(14f);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setPadding(0, dp(12), 0, 0);
        content.addView(title);
    }

    private CheckBox createCheckBox(int textResId, boolean checked) {
        CheckBox checkBox = new CheckBox(requireContext());
        checkBox.setText(textResId);
        checkBox.setChecked(checked);
        checkBox.setTextColor(requireContext().getColor(R.color.chat_text_primary));
        checkBox.setPadding(0, 0, 0, 0);
        return checkBox;
    }

    private void resetFilters() {
        selectedKind = "all";
        selectedSort = "newest";
        selectedCategory = null;
        selectedFree = false;
        selectedOpenSource = false;
        selectedFeatured = false;
    }

    private String checkedStringTag(ChipGroup group, String fallback) {
        Object tag = checkedTag(group);
        return tag instanceof String ? (String) tag : fallback;
    }

    private String checkedNullableStringTag(ChipGroup group) {
        Object tag = checkedTag(group);
        return tag instanceof String ? (String) tag : null;
    }

    private Object checkedTag(ChipGroup group) {
        Chip chip = group.findViewById(group.getCheckedChipId());
        return chip == null ? null : chip.getTag();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void fetchPublications(boolean reset) {
        if (binding == null || (loading && !reset)) {
            return;
        }

        if (reset) {
            nextOffset = 0;
            totalResults = 0;
            projects.clear();
            adapter.setProjects(projects);
            showShimmer(true);
        }

        loading = true;
        int generation = ++requestGeneration;
        binding.emptyState.setVisibility(View.GONE);
        binding.loadMoreButton.setVisibility(View.GONE);
        binding.resultSummary.setText(R.string.store_loading);

        storeApi.getPublications(
                selectedSort,
                selectedKind,
                selectedCategory,
                currentQuery(),
                selectedFree ? Boolean.TRUE : null,
                selectedOpenSource ? Boolean.TRUE : null,
                selectedFeatured ? Boolean.TRUE : null,
                SketchwareStoreApi.DEFAULT_PAGE_SIZE,
                nextOffset,
                projectModel -> {
                    if (binding == null || generation != requestGeneration) {
                        return;
                    }
                    loading = false;
                    handlePublications(projectModel, reset);
                }
        );
    }

    private void handlePublications(ProjectModel projectModel, boolean reset) {
        showShimmer(false);

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
        binding.publicationsGrid.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        binding.emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        binding.loadMoreButton.setVisibility(!isEmpty && nextOffset < totalResults ? View.VISIBLE : View.GONE);
        binding.resultSummary.setText(resultSummaryText());
    }

    private void showShimmer(boolean show) {
        if (binding == null) {
            return;
        }
        binding.shimmerContainer.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.publicationsGrid.setVisibility(show ? View.GONE : View.VISIBLE);
        if (show) {
            startShimmer();
        } else {
            stopShimmer();
        }
    }

    private void startShimmer() {
        if (binding == null || shimmerAnimator != null) {
            return;
        }
        shimmerAnimator = ObjectAnimator.ofFloat(binding.shimmerContainer, View.ALPHA, 0.45f, 1f);
        shimmerAnimator.setDuration(650L);
        shimmerAnimator.setRepeatCount(ValueAnimator.INFINITE);
        shimmerAnimator.setRepeatMode(ValueAnimator.REVERSE);
        shimmerAnimator.start();
    }

    private void stopShimmer() {
        if (shimmerAnimator != null) {
            shimmerAnimator.cancel();
            shimmerAnimator = null;
        }
        if (binding != null) {
            binding.shimmerContainer.setAlpha(1f);
        }
    }

    private String resultSummaryText() {
        if (projects.isEmpty() && totalResults == 0) {
            return getString(R.string.store_empty);
        }
        return String.format(Locale.US, "%d of %d publications - %s - %s",
                projects.size(),
                totalResults,
                selectedSort,
                filterSummary());
    }

    private String filterSummary() {
        StringBuilder builder = new StringBuilder("all".equals(selectedKind) ? "all types" : selectedKind.toUpperCase(Locale.US));
        if (selectedCategory != null) {
            builder.append(" - ").append(selectedCategoryName());
        }
        if (selectedFree) {
            builder.append(" - free");
        }
        if (selectedOpenSource) {
            builder.append(" - open");
        }
        if (selectedFeatured) {
            builder.append(" - featured");
        }
        return builder.toString();
    }

    private String selectedCategoryName() {
        for (ProjectModel.Category category : categories) {
            if (category.getSlug().equals(selectedCategory)) {
                return category.getName();
            }
        }
        return selectedCategory;
    }

    private void debounceSearch() {
        if (pendingSearch != null) {
            searchHandler.removeCallbacks(pendingSearch);
        }
        pendingSearch = () -> fetchPublications(true);
        searchHandler.postDelayed(pendingSearch, 450L);
    }

    private String currentQuery() {
        return searchQuery;
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
