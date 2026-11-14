// ImportIconActivity.java

package pro.sketchware.activities.importicon;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.Pair;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.besome.sketch.lib.ui.ColorPickerDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayoutMediator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import pro.sketchware.core.BackgroundTask;
import pro.sketchware.lib.validator.SimpleNameInputValidator;
import pro.sketchware.editor.EditorViewUtils;
import pro.sketchware.filemanagers.FileCryptoUtils;
import pro.sketchware.editor.ComponentNameRegistry;
import pro.sketchware.workspace.WorkspacePathManager;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.activities.importicon.adapters.IconAdapter;
import pro.sketchware.activities.resourceseditor.components.utils.ColorsEditorManager;
import pro.sketchware.databinding.DialogFilterIconsLayoutBinding;
import pro.sketchware.databinding.DialogSaveIconBinding;
import pro.sketchware.databinding.ImportIconBinding;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.PropertiesUtil;
import pro.sketchware.utility.SvgUtils;
import pro.sketchware.utility.TranslationFunction;

public class ImportIconActivity extends BaseAppCompatActivity implements IconAdapter.OnIconSelectedListener {

    private static final String ICON_TYPE_OUTLINE = "outline";
    private static final String ICON_TYPE_SHARP = "sharp";
    private static final String ICON_TYPE_TWO_TONE = "twotone";
    private static final String ICON_TYPE_ROUND = "round";
    private static final String ICON_TYPE_BASELINE = "baseline";
    private static final int PAGE_NEW_ICONS = 0;
    private static final int PAGE_OLD_ICONS = 1;
    private static final int ITEMS_PER_PAGE = 40;
    private String iconName;
    private SimpleNameInputValidator iconNameValidator;
    private MenuItem search;
    private SearchView searchView;
    private final OnBackPressedCallback searchViewCloser = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            setEnabled(false);
            if (search.isActionViewExpanded()) {
                search.collapseActionView();
                searchView.setQuery("", true);
            } else {
                getOnBackPressedDispatcher().onBackPressed();
            }
        }
    };

    private ImportIconBinding binding;

    private String sc_id;
    private ArrayList<String> alreadyAddedImageNames;
    private SvgUtils svgUtils;
    private String selected_icon_type = ICON_TYPE_ROUND;
    private int selected_color = Color.parseColor("#9E9E9E");
    private String selected_color_hex = "#9E9E9E";
    private int selectedIconPosition = -1;
    private Pair<String, String> selectedIcon;
    private final IconPageState newIconsPage = new IconPageState();
    private final IconPageState oldIconsPage = new IconPageState();
    private final IconPageState[] iconPages = new IconPageState[]{newIconsPage, oldIconsPage};
    private int currentPageIndex = PAGE_NEW_ICONS;
    private String currentQuery = "";

    private static class IconPageState {
        private final List<Pair<String, String>> allIconPaths = new ArrayList<>();
        private final List<Pair<String, String>> icons = new ArrayList<>();
        private IconAdapter adapter;
        private RecyclerView recyclerView;
        private int currentPage;
        private boolean isLoading;
        private boolean isLastPage;
    }

    private int getGridLayoutColumnCount() {
        int widthDp = (int) (getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density);
        return Math.max(2, widthDp / 96);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private boolean doExtractedIconsExist() {
        return new FileCryptoUtils().e(WorkspacePathManager.getExtractedIconPackStoreLocation());
    }

    private boolean doExtractedLucideIconsExist() {
        File iconsDirectory = new File(WorkspacePathManager.getExtractedLucideIconPackStoreLocation(), "icons");
        File[] svgFiles = iconsDirectory.listFiles((dir, name) -> name.endsWith(".svg"));
        return svgFiles != null && svgFiles.length > 0;
    }

    private void extractIcons() {
        extractAssetZip("icons" + File.separator + "icon_pack.zip", WorkspacePathManager.getExtractedIconPackStoreLocation());
    }

    private void extractLucideIcons() {
        FileUtil.deleteFile(WorkspacePathManager.getExtractedLucideIconPackStoreLocation());
        extractAssetZip("icons" + File.separator + "lucide_icons.zip", WorkspacePathManager.getExtractedLucideIconPackStoreLocation());
        if (!doExtractedLucideIconsExist()) {
            Log.e("icons", "Lucide icon pack extracted with no SVG files in icons/");
        }
    }

    private void extractAssetZip(String assetPath, String targetPath) {
        File targetDirectory = new File(targetPath);
        int fileCount = 0;
        int directoryCount = 0;
        byte[] buffer = new byte[8192];

        try (ZipInputStream input = new ZipInputStream(getAssets().open(assetPath))) {
            if (!targetDirectory.exists() && !targetDirectory.mkdirs()) {
                throw new IOException("Could not create target directory: " + targetDirectory.getAbsolutePath());
            }

            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                String entryName = entry.getName().replace('\\', '/');
                File output = new File(targetDirectory, entryName);
                if (!isInsideDirectory(targetDirectory, output)) {
                    throw new IOException("Blocked unsafe zip entry: " + entryName);
                }

                if (entry.isDirectory()) {
                    directoryCount++;
                    if (!output.exists() && !output.mkdirs()) {
                        throw new IOException("Could not create zip directory: " + output.getAbsolutePath());
                    }
                } else {
                    File parent = output.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        throw new IOException("Could not create parent directory: " + parent.getAbsolutePath());
                    }
                    try (FileOutputStream outputStream = new FileOutputStream(output, false)) {
                        int length;
                        while ((length = input.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, length);
                        }
                    }
                    fileCount++;
                }
                input.closeEntry();
            }
            Log.d("icons", "zip extract done asset=" + assetPath + " files=" + fileCount + " dirs=" + directoryCount);
        } catch (IOException e) {
            Log.e("icons", "Failed to extract " + assetPath + " to " + targetPath, e);
        }
    }

    private boolean isInsideDirectory(File directory, File file) throws IOException {
        String directoryPath = directory.getCanonicalPath();
        String filePath = file.getCanonicalPath();
        return filePath.equals(directoryPath) || filePath.startsWith(directoryPath + File.separator);
    }

    private void normalizeExtractedLucideIcons() {
        File iconsDirectory = new File(WorkspacePathManager.getExtractedLucideIconPackStoreLocation(), "icons");
        if (!iconsDirectory.isDirectory()) {
            Log.e("icons", "Lucide icons directory is missing: " + iconsDirectory.getAbsolutePath());
            return;
        }
        File[] svgFiles = iconsDirectory.listFiles((dir, name) -> name.endsWith(".svg"));
        if (svgFiles == null) {
            Log.e("icons", "Could not list Lucide SVG files: " + iconsDirectory.getAbsolutePath());
            return;
        }

        int changedCount = 0;
        for (File svgFile : svgFiles) {
            try {
                String content = new String(Files.readAllBytes(svgFile.toPath()), StandardCharsets.UTF_8);
                String normalized = content.replace("currentColor", "#000000");
                if (!content.equals(normalized)) {
                    Files.write(svgFile.toPath(), normalized.getBytes(StandardCharsets.UTF_8));
                    changedCount++;
                }
            } catch (IOException e) {
                Log.e("icons", "Failed to normalize Lucide SVG: " + svgFile.getAbsolutePath(), e);
            }
        }
        Log.d("icons", "lucide normalize done scanned=" + svgFiles.length + " changed=" + changedCount);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        for (IconPageState page : iconPages) {
            if (page.recyclerView != null && page.recyclerView.getLayoutManager() instanceof GridLayoutManager manager) {
                manager.setSpanCount(getGridLayoutColumnCount());
                page.recyclerView.requestLayout();
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ImportIconBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ColorPickerDialog colorpicker = new ColorPickerDialog(this, 0xFF9E9E9E, false, false);
        svgUtils = new SvgUtils(this);
        Toolbar toolbar = binding.toolbar.toolbar;
        binding.toolbar.layoutMainLogo.setVisibility(View.GONE);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setTitle(R.string.design_manager_icon_actionbar_title);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(true);
        toolbar.setNavigationOnClickListener(v -> {
            if (!EditorViewUtils.a()) {
                onBackPressed();
            }
        });

        sc_id = getIntent().getStringExtra("sc_id");
        alreadyAddedImageNames = getIntent().getStringArrayListExtra("imageNames");

        newIconsPage.adapter = new IconAdapter(this, selected_icon_type, selected_color, this);
        oldIconsPage.adapter = new IconAdapter(this, selected_icon_type, selected_color, this);
        binding.iconPackPager.setAdapter(new IconPagerAdapter());
        binding.iconPackPager.setOffscreenPageLimit(2);
        new TabLayoutMediator(binding.iconPackTabs, binding.iconPackPager, (tab, position) -> {
            tab.setText(position == PAGE_NEW_ICONS
                    ? R.string.import_icon_tab_new
                    : R.string.import_icon_tab_old);
        }).attach();
        binding.iconPackPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentPageIndex = position;
                filterIcons(currentQuery);
            }
        });
        k();

        binding.filterIconsButton.setOnClickListener(v -> showFilterDialog());

        new Handler().postDelayed(() -> new InitialIconLoader(this).execute(), 300L);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_import_icon, menu);
        search = menu.findItem(R.id.menu_find);
        searchView = (SearchView) search.getActionView();
        searchView.setQueryHint("Search");
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterIcons(newText);
                return true;
            }
        });
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.menu_find) {
            searchViewCloser.setEnabled(true);
            getOnBackPressedDispatcher().addCallback(this, searchViewCloser);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setIconName(Pair<String, String> icon) {
        iconName = "icon_" + sanitizeIconName(icon.first);
        if (!isDirectSvgIcon(icon)) {
            iconName += "_" + selected_icon_type;
        }
    }

    private void setIconColor() {
        new IconColorChangedIconLoader(this).execute();
    }

    private void listIcons() {
        newIconsPage.allIconPaths.clear();
        oldIconsPage.allIconPaths.clear();

        String iconPackStoreLocation = WorkspacePathManager.getExtractedIconPackStoreLocation() + File.separator + "svg/";
        try (Stream<Path> iconFiles = Files.list(Paths.get(iconPackStoreLocation))) {
            iconFiles.filter(Files::isDirectory)
                    .sorted()
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .forEach(folderName -> oldIconsPage.allIconPaths.add(new Pair<>(
                            folderName,
                            Paths.get(iconPackStoreLocation, folderName).toString()
                    )));
        } catch (IOException e) {
            Log.e("icons", "Failed to list old icons from " + iconPackStoreLocation, e);
        }
        String lucideIconPackStoreLocation = WorkspacePathManager.getExtractedLucideIconPackStoreLocation() + File.separator + "icons/";
        try (Stream<Path> iconFiles = Files.list(Paths.get(lucideIconPackStoreLocation))) {
            iconFiles.filter(path -> path.getFileName().toString().endsWith(".svg"))
                    .sorted()
                    .forEach(path -> newIconsPage.allIconPaths.add(new Pair<>(
                            "lucide_" + sanitizeIconName(stripExtension(path.getFileName().toString())),
                            path.toString()
                    )));
        } catch (IOException e) {
            Log.e("icons", "Failed to list new Lucide icons from " + lucideIconPackStoreLocation, e);
        }

        Log.d("icons", "new=" + newIconsPage.allIconPaths.size() + ", old=" + oldIconsPage.allIconPaths.size());
        runOnUiThread(() -> {
            currentQuery = "";
            if (searchView != null && searchView.getQuery().length() > 0) {
                searchView.setQuery("", false);
            }
            for (IconPageState page : iconPages) {
                resetPage(page);
            }
            loadMoreItems(newIconsPage);
            loadMoreItems(oldIconsPage);
        });
    }

    private String stripExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index > 0 ? fileName.substring(0, index) : fileName;
    }

    private String sanitizeIconName(String iconName) {
        return iconName.toLowerCase()
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }

    private void resetPage(IconPageState page) {
        page.icons.clear();
        page.currentPage = 0;
        page.isLoading = false;
        page.isLastPage = false;
        if (page.adapter != null) {
            page.adapter.submitList(new ArrayList<>());
        }
    }

    private IconPageState getCurrentPageState() {
        return iconPages[Math.max(0, Math.min(currentPageIndex, iconPages.length - 1))];
    }

    private void loadMoreItems(IconPageState page) {
        int start = page.currentPage * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, page.allIconPaths.size());

        if (start < end) {
            List<Pair<String, String>> newItems = page.allIconPaths.subList(start, end);
            page.icons.addAll(newItems);
            page.adapter.submitList(new ArrayList<>(page.icons));
            page.currentPage++;
        } else {
            page.isLastPage = true;
        }
        page.isLoading = false;
    }


    private void filterIcons(String query) {
        currentQuery = query == null ? "" : query;
        IconPageState page = getCurrentPageState();
        if (currentQuery.isEmpty()) {
            resetPage(page);
            loadMoreItems(page);
            return;
        }

        var filteredIcons = new ArrayList<Pair<String, String>>(page.allIconPaths.size());
        for (Pair<String, String> icon : page.allIconPaths) {
            if (icon.first.toLowerCase().contains(currentQuery.toLowerCase())) {
                filteredIcons.add(icon);
            }
        }
        page.icons.clear();
        page.icons.addAll(filteredIcons);
        page.isLastPage = true;
        page.adapter.submitList(new ArrayList<>(page.icons));
    }

    private class IconPagerAdapter extends RecyclerView.Adapter<IconPagerAdapter.PageViewHolder> {
        @NonNull
        @Override
        public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            RecyclerView recyclerView = new RecyclerView(parent.getContext());
            recyclerView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            recyclerView.setClipToPadding(false);
            recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
            recyclerView.setPadding(dp(6), dp(8), dp(6), dp(86));
            recyclerView.setVerticalScrollBarEnabled(false);
            recyclerView.setHorizontalScrollBarEnabled(false);
            recyclerView.setMotionEventSplittingEnabled(false);
            return new PageViewHolder(recyclerView);
        }

        @Override
        public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
            IconPageState page = iconPages[position];
            page.recyclerView = holder.recyclerView;
            holder.recyclerView.setLayoutManager(new GridLayoutManager(getBaseContext(), getGridLayoutColumnCount()));
            holder.recyclerView.setAdapter(page.adapter);
            holder.recyclerView.clearOnScrollListeners();
            holder.recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    if (!currentQuery.isEmpty() || page.isLoading || page.isLastPage) {
                        return;
                    }

                    GridLayoutManager layoutManager = (GridLayoutManager) recyclerView.getLayoutManager();
                    if (layoutManager == null) {
                        return;
                    }

                    int visibleItemCount = layoutManager.getChildCount();
                    int totalItemCount = layoutManager.getItemCount();
                    int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();
                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                            && firstVisibleItemPosition >= 0) {
                        page.isLoading = true;
                        loadMoreItems(page);
                    }
                }
            });
        }

        @Override
        public int getItemCount() {
            return iconPages.length;
        }

        private class PageViewHolder extends RecyclerView.ViewHolder {
            private final RecyclerView recyclerView;

            private PageViewHolder(@NonNull RecyclerView itemView) {
                super(itemView);
                recyclerView = itemView;
            }
        }
    }

    private void showFilterDialog() {
        DialogFilterIconsLayoutBinding dialogBinding = DialogFilterIconsLayoutBinding.inflate(getLayoutInflater());

        var dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogBinding.getRoot())
                .setTitle("Filter icons")
                .setNegativeButton("Cancel", (dialogInterface, i) -> dialogInterface.dismiss())
                .setPositiveButton("Apply", null)
                .create();
        dialog.setView(dialogBinding.getRoot());

        dialogBinding.selectColour.setText(selected_color_hex);
        dialogBinding.selectColour.setBackgroundColor(selected_color);

        if (Color.red(selected_color) * 0.299 + Color.green(selected_color) * 0.587 + Color.blue(selected_color) * 0.114 > 186) {
            dialogBinding.selectColour.setTextColor(Color.BLACK);
        } else {
            dialogBinding.selectColour.setTextColor(Color.WHITE);
        }
        dialogBinding.selectColour.setOnClickListener(view -> {
            ColorPickerDialog colorPicker = new ColorPickerDialog(this, selected_color_hex, false, false, sc_id);
            colorPicker.a(new ColorPickerDialog.b() {
                @Override
                public void a(int var1) {
                    selected_color = var1;
                    selected_color_hex = "#" + String.format("%06X", var1 & (0x00FFFFFF));
                    dialogBinding.selectColour.setText(selected_color_hex);
                    updateAdaptersColor();

                    dialogBinding.selectColour.setBackgroundColor(selected_color);

                    if (Color.red(selected_color) * 0.299 + Color.green(selected_color) * 0.587 + Color.blue(selected_color) * 0.114 > 186) {
                        dialogBinding.selectColour.setTextColor(Color.BLACK);
                    } else {
                        dialogBinding.selectColour.setTextColor(Color.WHITE);
                    }
                }

                @Override
                public void a(String var1, int var2) {
                    selected_color = var2;
                    selected_color_hex = "@color/" + var1;
                    dialogBinding.selectColour.setText(selected_color_hex);
                    updateAdaptersColor();

                    dialogBinding.selectColour.setBackgroundColor(selected_color);

                    if (Color.red(selected_color) * 0.299 + Color.green(selected_color) * 0.587 + Color.blue(selected_color) * 0.114 > 186) {
                        dialogBinding.selectColour.setTextColor(Color.BLACK);
                    } else {
                        dialogBinding.selectColour.setTextColor(Color.WHITE);
                    }
                }
            });
            colorPicker.materialColorAttr((attr, attrColor) -> {
                attr = "?attr/" + attr;
                selected_color = PropertiesUtil.parseColor(new ColorsEditorManager().getColorValue(getApplicationContext(), attr, 3));
                selected_color_hex = attr;
                dialogBinding.selectColour.setText(selected_color_hex);
                updateAdaptersColor();

                dialogBinding.selectColour.setBackgroundColor(selected_color);

                if (Color.red(selected_color) * 0.299 + Color.green(selected_color) * 0.587 + Color.blue(selected_color) * 0.114 > 186) {
                    dialogBinding.selectColour.setTextColor(Color.BLACK);
                } else {
                    dialogBinding.selectColour.setTextColor(Color.WHITE);
                }
            });
            colorPicker.showAtLocation(view, Gravity.CENTER, 0, 0);
        });

        switch (selected_icon_type) {
            case ICON_TYPE_OUTLINE -> dialogBinding.chipGroupStyle.check(R.id.chip_outline);
            case ICON_TYPE_BASELINE -> dialogBinding.chipGroupStyle.check(R.id.chip_baseline);
            case ICON_TYPE_SHARP -> dialogBinding.chipGroupStyle.check(R.id.chip_sharp);
            case ICON_TYPE_TWO_TONE -> dialogBinding.chipGroupStyle.check(R.id.chip_twotone);
            case ICON_TYPE_ROUND -> dialogBinding.chipGroupStyle.check(R.id.chip_round);
        }

        dialog.setOnShowListener(dialogInterface -> {

            Button positiveButton = ((AlertDialog) dialogInterface).getButton(DialogInterface.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(view -> {
                int checkedChipId = dialogBinding.chipGroupStyle.getCheckedChipId();
                if (checkedChipId == R.id.chip_outline && !selected_icon_type.equals(ICON_TYPE_OUTLINE)) {
                    updateIcons(ICON_TYPE_OUTLINE);
                }
                if (checkedChipId == R.id.chip_twotone && !selected_icon_type.equals(ICON_TYPE_TWO_TONE)) {
                    updateIcons(ICON_TYPE_TWO_TONE);
                }
                if (checkedChipId == R.id.chip_baseline && !selected_icon_type.equals(ICON_TYPE_BASELINE)) {
                    updateIcons(ICON_TYPE_BASELINE);
                }
                if (checkedChipId == R.id.chip_sharp && !selected_icon_type.equals(ICON_TYPE_SHARP)) {
                    updateIcons(ICON_TYPE_SHARP);
                }
                if (checkedChipId == R.id.chip_round && !selected_icon_type.equals(ICON_TYPE_ROUND)) {
                    updateIcons(ICON_TYPE_ROUND);
                }
                dialogInterface.dismiss();

            });
        });

        dialog.show();

    }

    private void updateIcons(String type) {
        selected_icon_type = type;
        for (IconPageState page : iconPages) {
            if (page.adapter != null) {
                page.adapter.setSelectedIconType(selected_icon_type);
                page.adapter.notifyDataSetChanged();
            }
        }
    }

    private void updateAdaptersColor() {
        for (IconPageState page : iconPages) {
            if (page.adapter != null) {
                page.adapter.setSelectedColor(selected_color);
                page.adapter.notifyDataSetChanged();
            }
        }
    }

    private void showSaveDialog(Pair<String, String> icon) {
        DialogSaveIconBinding dialogBinding = DialogSaveIconBinding.inflate(getLayoutInflater());

        var dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogBinding.getRoot())
                .setTitle("Save")
                .setNegativeButton("Cancel", (dialogInterface, i) -> dialogInterface.dismiss())
                .setPositiveButton("Save", null)
                .create();

        dialog.setOnShowListener(dialogInterface -> {
            Button positiveButton = ((AlertDialog) dialogInterface).getButton(DialogInterface.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(view -> {
                if (iconNameValidator.b() && selectedIcon != null) {
                    String resFullname = resolveIconFilePath(selectedIcon);
                    Intent intent = new Intent();
                    intent.putExtra("iconName", Helper.getText(dialogBinding.inputText));
                    intent.putExtra("iconPath", resFullname);

                    intent.putExtra("iconColor", selected_color);
                    intent.putExtra("iconColorHex", selected_color_hex);
                    setResult(Activity.RESULT_OK, intent);
                    finish();
                } else {
                    return;
                }
                dialogInterface.dismiss();
            });
        });

        svgUtils.loadImage(dialogBinding.icon, resolveIconFilePath(icon));
        dialogBinding.icon.setColorFilter(selected_color, PorterDuff.Mode.SRC_IN);
        iconNameValidator = new SimpleNameInputValidator(getApplicationContext(), dialogBinding.textInputLayout, ComponentNameRegistry.b, alreadyAddedImageNames);
        dialogBinding.licenceInfo.setOnClickListener(v -> {
            Uri webpage = Uri.parse("https://www.apache.org/licenses/LICENSE-2.0.txt");

            Intent intent = new Intent(Intent.ACTION_VIEW, webpage);
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            }
        });
        dialogBinding.inputText.setText(iconName);
        dialog.setView(dialogBinding.getRoot());
        dialog.show();
    }

    private String resolveIconFilePath(Pair<String, String> icon) {
        File path = new File(icon.second);
        if (path.isDirectory()) {
            return new File(path, selected_icon_type + ".svg").getAbsolutePath();
        }
        return path.getAbsolutePath();
    }

    private boolean isDirectSvgIcon(Pair<String, String> icon) {
        return new File(icon.second).isFile();
    }

    @Override
    public void onIconSelected(Pair<String, String> icon, int position) {
        if (!EditorViewUtils.a()) {
            selectedIconPosition = position;
            selectedIcon = icon;
            setIconName(icon);
            showSaveDialog(icon);
        }
    }

    private static class InitialIconLoader extends BackgroundTask {
        private final WeakReference<ImportIconActivity> activity;

        public InitialIconLoader(ImportIconActivity activity) {
            super(activity);
            this.activity = new WeakReference<>(activity);
            activity.addTask(this);
        }

        @Override
        public void a() {
            var activity = this.activity.get();
            activity.h();
            activity.setIconColor();
        }

        @Override
        public void b() {
            var activity = this.activity.get();
            if (!activity.doExtractedIconsExist()) {
                activity.extractIcons();
            }
            if (!activity.doExtractedLucideIconsExist()) {
                activity.extractLucideIcons();
            }
            activity.normalizeExtractedLucideIcons();
        }

        @Override
        public void a(String str) {
            activity.get().h();
        }

    }

    private static class IconColorChangedIconLoader extends BackgroundTask {
        private final WeakReference<ImportIconActivity> activity;

        public IconColorChangedIconLoader(ImportIconActivity activity) {
            super(activity);
            this.activity = new WeakReference<>(activity);
            activity.addTask(this);
            activity.k();
        }

        @Override
        public void a() {
            var activity = this.activity.get();
            activity.h();
            activity.selectedIconPosition = -1;
            activity.selectedIcon = null;
        }

        @Override
        public void b() {
            var activity = this.activity.get();
            activity.listIcons();
        }

        @Override
        public void a(String str) {
            activity.get().h();
        }
    }
}
