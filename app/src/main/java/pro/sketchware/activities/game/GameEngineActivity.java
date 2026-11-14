package pro.sketchware.activities.game;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.GravityCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.besome.sketch.design.DesignActivity;
import com.besome.sketch.editor.manage.font.ManageFontActivity;
import com.besome.sketch.editor.manage.image.ManageImageActivity;
import com.besome.sketch.editor.manage.lottie.ManageLottieActivity;
import com.besome.sketch.editor.manage.sound.ManageSoundActivity;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.besome.sketch.lib.ui.CustomViewPager;
import com.besome.sketch.tools.CompileLogActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;

import java.io.File;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.chrisbanes.insetter.Insetter;
import mod.agus.jcoderz.editor.manage.permission.ManagePermissionActivity;
import mod.hey.studios.activity.managers.assets.ManageAssetsActivity;
import mod.hey.studios.activity.managers.java.ManageJavaActivity;
import mod.hey.studios.project.ProjectTracker;
import mod.hey.studios.util.ProjectMapUtils;
import mod.jbk.build.BuildProgressReceiver;
import mod.jbk.build.BuiltInLibraries;
import mod.jbk.diagnostic.CompileErrorSaver;
import mod.jbk.diagnostic.MissingFileException;
import mod.khaled.logcat.LogReaderActivity;
import pro.sketchware.R;
import pro.sketchware.activities.game.compiler.GameApkSourceGenerator;
import pro.sketchware.activities.game.model.GameObjectBean;
import pro.sketchware.activities.game.model.GameProject;
import pro.sketchware.activities.game.model.GameProjectRepository;
import pro.sketchware.activities.game.model.GameScene;
import pro.sketchware.activities.game.model.GameScriptNode;
import pro.sketchware.activities.game.runtime.GamePreviewActivity;
import pro.sketchware.activities.game.ui.GameDrawerView;
import pro.sketchware.activities.game.ui.GameEventsView;
import pro.sketchware.activities.game.ui.GameHierarchyDialog;
import pro.sketchware.activities.game.ui.GameObjectsTabView;
import pro.sketchware.activities.game.ui.GamePaletteView;
import pro.sketchware.activities.game.ui.GamePropertyPanelView;
import pro.sketchware.activities.game.ui.GameStageEditorView;
import pro.sketchware.activities.resourceseditor.ResourcesEditorActivity;
import pro.sketchware.beans.ProjectDataManager;
import pro.sketchware.compiler.ProjectBuilder;
import pro.sketchware.core.MapValueUtils;
import pro.sketchware.filemanagers.ProjectResourceManager;
import pro.sketchware.filemanagers.ProjectResourceStore;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;
import pro.sketchware.workspace.ProjectDataManagerRegistry;
import pro.sketchware.workspace.ProjectFileManager;
import pro.sketchware.workspace.ProjectLibraryManager;
import pro.sketchware.workspace.ProjectWorkspaceManager;
import pro.sketchware.workspace.RecentProjectsRegistry;
import pro.sketchware.workspace.WorkspacePathManager;

/**
 * Main 2D Game Engine IDE activity matching {@link DesignActivity}'s Material 3 structure
 * (Toolbar + {@code @menu/design_menu}, 3-tab {@link CustomViewPager} for Design / Event / Component,
 * sliding {@link GamePropertyPanelView}, bottom Run/Scene bar, {@link GameDrawerView},
 * {@link GameLogicEditorActivity} launcher, {@link GamePreviewActivity}, and APK compilation).
 */
public class GameEngineActivity extends BaseAppCompatActivity {

    private static final String STATE_SC_ID = "sc_id";
    private static final String STATE_SCENE_ID = "current_scene_id";
    private static final String STATE_PALETTE_OPEN = "palette_open";
    private static final String STATE_INSPECTOR_OPEN = "inspector_open";
    private static final String STATE_ACTIVE_TAB = "active_tab";

    private static final int TAB_DESIGN = 0;
    private static final int TAB_EVENT = 1;
    private static final int TAB_COMPONENT = 2;

    private static final int PROPERTY_PANEL_HEIGHT_DP = 170;

    private DrawerLayout drawerLayout;
    private CoordinatorLayout coordinatorLayout;
    private Toolbar toolbar;
    private TabLayout tabLayout;
    private CustomViewPager viewPager;

    private ImageView imgOrientation;
    private TextView fileName;
    private MaterialButton btnCanvasTools;
    private MaterialButton btnRun;
    private MaterialButton btnOptions;

    private LinearLayout progressContainer;
    private TextView progressText;
    private LinearProgressIndicator progressBar;

    private PopupMenu bottomPopupMenu;
    private Menu bottomMenu;

    private LinearLayout designPageContainer;
    private GamePaletteView gamePalette;
    private GameStageEditorView gameStage;
    private GameEventsView gameEventsView;
    private GameObjectsTabView gameObjectsTabView;
    private GamePropertyPanelView gamePropertyPanel;
    private GameDrawerView gameDrawer;

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService buildExecutor = Executors.newSingleThreadExecutor();

    private String scId = "";
    private String pendingSceneId;
    private HashMap<String, Object> projectMetadata;
    private ProjectWorkspaceManager workspace;
    private GameProject gameProject;
    private GameScene currentScene;
    private BuildGameApkTask currentBuildTask;

    private boolean projectLoaded = false;
    private boolean paletteVisible = true;
    private boolean inspectorVisible = false;
    private int currentTab = TAB_DESIGN;

    private final ActivityResultLauncher<Intent> resourceManagerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result ->
                    reloadResourcesAfterManagerReturn());

    private final ActivityResultLauncher<Intent> logicEditorLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result ->
                    reloadGameProjectAfterLogicReturn());

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        if (!isStoragePermissionGranted()) {
            finish();
            return;
        }

        if (savedInstanceState != null) {
            scId = savedInstanceState.getString(STATE_SC_ID, "");
            pendingSceneId = savedInstanceState.getString(STATE_SCENE_ID, null);
            paletteVisible = savedInstanceState.getBoolean(STATE_PALETTE_OPEN, true);
            inspectorVisible = savedInstanceState.getBoolean(STATE_INSPECTOR_OPEN, false);
            currentTab = savedInstanceState.getInt(STATE_ACTIVE_TAB, TAB_DESIGN);
        } else if (getIntent() != null) {
            scId = getIntent().getStringExtra(STATE_SC_ID);
            pendingSceneId = getIntent().getStringExtra("scene_id");
        }

        if (TextUtils.isEmpty(scId)) {
            scId = ProjectTracker.SC_ID;
        }
        if (TextUtils.isEmpty(scId)) {
            SketchwareUtil.toastError("Invalid project.");
            finish();
            return;
        }

        ProjectTracker.setScId(scId);
        DesignActivity.sc_id = scId;

        setContentView(R.layout.activity_game_engine);
        bindShellViews();
        initToolbarAndDrawer();
        initWorkspacePages();
        initBottomBar();

        Insetter.builder()
                .margin(WindowInsetsCompat.Type.navigationBars())
                .applyToView(findViewById(R.id.container));

        loadProjectAsync(savedInstanceState != null);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isStoragePermissionGranted()) {
            finish();
            return;
        }
        if (!TextUtils.isEmpty(scId)) {
            ProjectTracker.setScId(scId);
            DesignActivity.sc_id = scId;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (projectLoaded && gameProject != null) {
            GameProjectRepository.save(scId, gameProject);
            saveSketchwareRegistriesAsync(false);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_SC_ID, scId);
        if (currentScene != null && !TextUtils.isEmpty(currentScene.id)) {
            outState.putString(STATE_SCENE_ID, currentScene.id);
        }
        outState.putBoolean(STATE_PALETTE_OPEN, paletteVisible);
        outState.putBoolean(STATE_INSPECTOR_OPEN, inspectorVisible);
        outState.putInt(STATE_ACTIVE_TAB, currentTab);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.game_design_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.design_actionbar_titleopen_drawer) {
            if (!drawerLayout.isDrawerOpen(GravityCompat.END)) {
                drawerLayout.openDrawer(GravityCompat.END);
            }
            return true;
        } else if (itemId == R.id.design_option_menu_title_save_project) {
            if (projectLoaded && gameProject != null) {
                GameProjectRepository.save(scId, gameProject);
                saveSketchwareRegistriesAsync(true);
            }
            return true;
        } else if (itemId == R.id.game_option_menu_preview) {
            launchPlayPreview();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.END)) {
            drawerLayout.closeDrawer(GravityCompat.END);
            return;
        }
        if (currentTab == TAB_DESIGN && inspectorVisible) {
            setPropertyPanelVisible(false, true, true);
            return;
        }
        if (currentTab > TAB_DESIGN) {
            viewPager.setCurrentItem(currentTab - 1, true);
            return;
        }
        if (projectLoaded && gameProject != null) {
            GameProjectRepository.save(scId, gameProject);
            saveAndCloseProject();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void finish() {
        ProjectDataManagerRegistry.a();
        super.finish();
    }

    @Override
    public void onDestroy() {
        if (currentBuildTask != null && !currentBuildTask.isBuildFinished) {
            currentBuildTask.canceled = true;
        }
        ioExecutor.shutdownNow();
        buildExecutor.shutdownNow();
        super.onDestroy();
    }

    private void bindShellViews() {
        drawerLayout = findViewById(R.id.drawer_layout);
        coordinatorLayout = findViewById(R.id.layout_coordinator);
        toolbar = findViewById(R.id.toolbar);
        tabLayout = findViewById(R.id.tab_layout);
        viewPager = findViewById(R.id.viewpager);

        imgOrientation = findViewById(R.id.img_orientation);
        fileName = findViewById(R.id.file_name);
        btnCanvasTools = findViewById(R.id.btn_canvas_tools);
        btnRun = findViewById(R.id.btn_run);
        btnOptions = findViewById(R.id.btn_options);

        progressContainer = findViewById(R.id.progress_container);
        progressText = findViewById(R.id.progress_text);
        progressBar = findViewById(R.id.progress);

        gamePropertyPanel = findViewById(R.id.game_property_panel);
        gameDrawer = findViewById(R.id.game_drawer);
    }

    private void initToolbarAndDrawer() {
        toolbar.setTitle(R.string.project_type_game_2d);
        toolbar.setSubtitle(scId);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

        gameDrawer.setDrawerActionListener(new GameDrawerView.DrawerActionListener() {
            @Override
            public void onOpenImageManager() {
                closeDrawerIfOpen();
                launchManagerActivity(ManageImageActivity.class, resourceManagerLauncher);
            }

            @Override
            public void onOpenSoundManager() {
                closeDrawerIfOpen();
                launchManagerActivity(ManageSoundActivity.class, resourceManagerLauncher);
            }

            @Override
            public void onOpenFontManager() {
                closeDrawerIfOpen();
                launchManagerActivity(ManageFontActivity.class, resourceManagerLauncher);
            }

            @Override
            public void onOpenLottieManager() {
                closeDrawerIfOpen();
                launchManagerActivity(ManageLottieActivity.class, resourceManagerLauncher);
            }

            @Override
            public void onOpenAssetsManager() {
                closeDrawerIfOpen();
                launchManagerActivity(ManageAssetsActivity.class, resourceManagerLauncher);
            }

            @Override
            public void onOpenResourcesEditor() {
                closeDrawerIfOpen();
                launchManagerActivity(ResourcesEditorActivity.class, resourceManagerLauncher);
            }

            @Override
            public void onOpenSceneManager() {
                closeDrawerIfOpen();
                openSceneManagerDialog();
            }

            @Override
            public void onOpenEngineSettings() {
                closeDrawerIfOpen();
                openEngineSettingsDialog();
            }

            @Override
            public void onOpenHierarchy() {
                closeDrawerIfOpen();
                openHierarchyDialog();
            }

            @Override
            public void onOpenPermissionManager() {
                closeDrawerIfOpen();
                launchManagerActivity(ManagePermissionActivity.class, null);
            }

            @Override
            public void onOpenJavaManager() {
                closeDrawerIfOpen();
                String pkg = workspace != null && workspace.packageName != null ? workspace.packageName : "";
                launchManagerActivity(ManageJavaActivity.class, null, new Pair<>("pkgName", pkg));
            }

            @Override
            public void onOpenLogcatReader() {
                closeDrawerIfOpen();
                launchManagerActivity(LogReaderActivity.class, null);
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initWorkspacePages() {
        designPageContainer = new LinearLayout(this);
        designPageContainer.setOrientation(LinearLayout.HORIZONTAL);
        designPageContainer.setBackgroundColor(MaterialColors.getColor(this, R.attr.colorSurface, 0xFF141A24));
        designPageContainer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        gamePalette = new GamePaletteView(this);
        gamePalette.setPaletteListener(type -> {
            if (!projectLoaded || currentScene == null) {
                return;
            }
            gameStage.addObjectFromPalette(type);
            refreshTabsAfterMutation();
            markGameModified();
        });
        designPageContainer.addView(gamePalette, new LinearLayout.LayoutParams(
                dp(120),
                LinearLayout.LayoutParams.MATCH_PARENT));

        gameStage = new GameStageEditorView(this);
        // Temporarily lock CustomViewPager horizontal swipe while interacting directly with the 2D stage
        gameStage.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                viewPager.disableTouchEvent();
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                viewPager.enableTouchEvent();
            }
            return false;
        });
        designPageContainer.addView(gameStage, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f));

        gameEventsView = new GameEventsView(this);
        gameObjectsTabView = new GameObjectsTabView(this);

        viewPager.setAdapter(new GamePagerAdapter());
        viewPager.setOffscreenPageLimit(3);
        tabLayout.setupWithViewPager(viewPager);

        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                handleTabSelected(position, true);
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
        });

        applyPaletteVisibility();
        setPropertyPanelVisible(inspectorVisible, false, false);
    }

    private void initBottomBar() {
        findViewById(R.id.file_name_container).setOnClickListener(v -> openSceneManagerDialog());

        btnCanvasTools.setCheckable(true);
        btnCanvasTools.setOnClickListener(this::showCanvasToolsPopup);

        btnRun.setOnClickListener(v -> {
            if (currentBuildTask != null && !currentBuildTask.canceled && !currentBuildTask.isBuildFinished) {
                currentBuildTask.cancelBuild();
                return;
            }
            startApkBuild();
        });

        bottomPopupMenu = new PopupMenu(this, btnOptions);
        bottomMenu = bottomPopupMenu.getMenu();
        bottomMenu.add(Menu.NONE, 1, 0, "Preview Game (2D)")
                .setIcon(R.drawable.ic_mtrl_circle_play)
                .setOnMenuItemClickListener(item -> {
                    launchPlayPreview();
                    return true;
                });
        bottomMenu.add(Menu.NONE, 2, 1, "Build and Install APK")
                .setIcon(R.drawable.ic_mtrl_apk_install)
                .setOnMenuItemClickListener(item -> {
                    startApkBuild();
                    return true;
                });
        bottomMenu.add(Menu.NONE, 3, 2, "Install Last APK")
                .setIcon(R.drawable.ic_mtrl_deployed_code)
                .setVisible(false)
                .setOnMenuItemClickListener(item -> {
                    if (workspace != null && FileUtil.isExistFile(workspace.finalToInstallApkPath)) {
                        installBuiltApk(workspace);
                    } else {
                        SketchwareUtil.toast("APK not built yet.");
                    }
                    return true;
                });
        bottomMenu.add(Menu.NONE, 4, 3, "Hierarchy & Prefabs")
                .setIcon(R.drawable.ic_kelivo_layers)
                .setOnMenuItemClickListener(item -> {
                    openHierarchyDialog();
                    return true;
                });
        bottomMenu.add(Menu.NONE, 5, 4, "2D Engine Settings")
                .setIcon(R.drawable.ic_mtrl_tune)
                .setOnMenuItemClickListener(item -> {
                    openEngineSettingsDialog();
                    return true;
                });
        bottomMenu.add(Menu.NONE, 7, 6, "Show Last Compile Error")
                .setOnMenuItemClickListener(item -> {
                    new CompileErrorSaver(scId).showLastErrors(this);
                    return true;
                });
        bottomMenu.add(Menu.NONE, 8, 7, "Clean Temporary Files")
                .setVisible(false)
                .setOnMenuItemClickListener(item -> {
                    if (workspace == null) {
                        return true;
                    }
                    final String path = workspace.projectMyscPath;
                    ioExecutor.execute(() -> {
                        FileUtil.deleteFile(path);
                        runOnUiThread(() -> {
                            updateBottomMenu();
                            SketchwareUtil.toast("Temporary files removed.");
                        });
                    });
                    return true;
                });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            bottomPopupMenu.setForceShowIcon(true);
        }
        bottomPopupMenu.setOnDismissListener(menu -> btnOptions.setChecked(false));
        btnOptions.setOnClickListener(v -> {
            updateBottomMenu();
            bottomPopupMenu.show();
        });
    }

    private void showCanvasToolsPopup(View anchor) {
        if (!projectLoaded || gameStage == null) {
            btnCanvasTools.setChecked(false);
            return;
        }
        PopupMenu popup = new PopupMenu(this, anchor);
        Menu menu = popup.getMenu();

        menu.add(1, 1, 0, R.string.game_engine_action_undo)
                .setIcon(R.drawable.ic_mtrl_undo)
                .setEnabled(gameStage.canUndo());
        menu.add(1, 2, 1, R.string.game_engine_action_redo)
                .setIcon(R.drawable.ic_mtrl_redo)
                .setEnabled(gameStage.canRedo());

        menu.add(2, 3, 2, R.string.game_engine_toggle_grid)
                .setIcon(R.drawable.ic_mtrl_grid)
                .setCheckable(true)
                .setChecked(gameStage.isSnapToGrid());

        menu.add(3, 4, 3, "Layer: " + getString(R.string.game_engine_layer_all))
                .setIcon(R.drawable.ic_kelivo_layers);
        menu.add(3, 5, 4, "Layer: " + getString(R.string.game_engine_layer_world))
                .setIcon(R.drawable.ic_kelivo_layers);
        menu.add(3, 6, 5, "Layer: " + getString(R.string.game_engine_layer_hud))
                .setIcon(R.drawable.ic_kelivo_layers);
        menu.setGroupCheckable(3, true, true);
        int filter = gameStage.getLayerFilter();
        menu.findItem(filter == GameStageEditorView.LAYER_WORLD_ONLY ? 5
                : (filter == GameStageEditorView.LAYER_HUD_ONLY ? 6 : 4)).setChecked(true);

        menu.add(4, 7, 6, R.string.game_engine_reset_camera)
                .setIcon(R.drawable.ic_mtrl_center);
        menu.add(4, 8, 7, R.string.game_engine_toggle_inspector)
                .setIcon(R.drawable.ic_mtrl_tune)
                .setCheckable(true)
                .setChecked(inspectorVisible);
        menu.add(4, 9, 8, R.string.game_engine_toggle_palette)
                .setIcon(R.drawable.ic_mtrl_palette)
                .setCheckable(true)
                .setChecked(paletteVisible);
        menu.add(4, 10, 9, R.string.game_engine_hierarchy_button)
                .setIcon(R.drawable.ic_mtrl_deployed_code);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            popup.setForceShowIcon(true);
        }
        popup.setOnDismissListener(m -> btnCanvasTools.setChecked(false));
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    gameStage.undo();
                    gamePropertyPanel.updateSelection(gameStage.getSelectedObject());
                    refreshTabsAfterMutation();
                    markGameModified();
                    return true;
                case 2:
                    gameStage.redo();
                    gamePropertyPanel.updateSelection(gameStage.getSelectedObject());
                    refreshTabsAfterMutation();
                    markGameModified();
                    return true;
                case 3:
                    gameStage.setSnapToGrid(!gameStage.isSnapToGrid());
                    return true;
                case 4:
                    gameStage.setLayerFilter(GameStageEditorView.LAYER_ALL);
                    return true;
                case 5:
                    gameStage.setLayerFilter(GameStageEditorView.LAYER_WORLD_ONLY);
                    return true;
                case 6:
                    gameStage.setLayerFilter(GameStageEditorView.LAYER_HUD_ONLY);
                    return true;
                case 7:
                    gameStage.resetCameraView();
                    return true;
                case 8:
                    setPropertyPanelVisible(!inspectorVisible, true, true);
                    return true;
                case 9:
                    paletteVisible = !paletteVisible;
                    applyPaletteVisibility();
                    return true;
                case 10:
                    openHierarchyDialog();
                    return true;
                default:
                    return false;
            }
        });
        popup.show();
    }

    private void handleTabSelected(int position, boolean animatePanel) {
        currentTab = position;
        if (position == TAB_DESIGN) {
            imgOrientation.setImageResource(R.drawable.ic_mtrl_screen);
            btnCanvasTools.setVisibility(View.VISIBLE);
            setPropertyPanelVisible(inspectorVisible, animatePanel, false);
        } else if (position == TAB_EVENT) {
            viewPager.enableTouchEvent();
            imgOrientation.setImageResource(R.drawable.ic_mtrl_code);
            btnCanvasTools.setVisibility(View.GONE);
            setPropertyPanelVisible(false, animatePanel, false);
            if (gameEventsView != null) {
                gameEventsView.refreshEvents();
            }
            if (gameObjectsTabView != null) {
                gameObjectsTabView.unselectAll();
            }
        } else if (position == TAB_COMPONENT) {
            viewPager.enableTouchEvent();
            imgOrientation.setImageResource(R.drawable.ic_mtrl_code);
            btnCanvasTools.setVisibility(View.GONE);
            setPropertyPanelVisible(false, animatePanel, false);
            if (gameObjectsTabView != null) {
                gameObjectsTabView.refreshData();
            }
            if (gameEventsView != null) {
                gameEventsView.unselectAll();
            }
        }
        invalidateOptionsMenu();
    }

    private void setPropertyPanelVisible(boolean visible, boolean animate, boolean updatePreference) {
        if (updatePreference) {
            inspectorVisible = visible;
        }
        if (gamePropertyPanel == null) {
            return;
        }
        gamePropertyPanel.animate().cancel();
        float targetY = visible ? 0f : (float) dp(PROPERTY_PANEL_HEIGHT_DP);
        if (animate) {
            gamePropertyPanel.animate()
                    .translationY(targetY)
                    .setDuration(200L)
                    .start();
        } else {
            gamePropertyPanel.setTranslationY(targetY);
        }
    }

    private void applyPaletteVisibility() {
        int vis = paletteVisible ? View.VISIBLE : View.GONE;
        if (gamePalette != null) {
            gamePalette.setVisibility(vis);
        }
    }

    private void loadProjectAsync(boolean hasSavedState) {
        if (!isFinishing()) {
            k();
        }
        final Context appContext = getApplicationContext();
        ioExecutor.execute(() -> {
            ProjectTracker.setScId(scId);
            DesignActivity.sc_id = scId;

            boolean reuseState = hasSavedState
                    && ProjectDataManagerRegistry.a != null
                    && ProjectDataManagerRegistry.b != null
                    && ProjectDataManagerRegistry.c != null
                    && ProjectDataManagerRegistry.d != null;

            ProjectDataManagerRegistry.a(scId, reuseState);
            ProjectFileManager fileMgr = ProjectDataManagerRegistry.b(scId, reuseState);
            if (fileMgr != null && fileMgr.b().isEmpty()) {
                fileMgr.a(0, "main");
            }
            ProjectResourceManager resMgr = ProjectDataManagerRegistry.d(scId, reuseState);
            ProjectDataManagerRegistry.c(scId, reuseState);
            if (!reuseState && resMgr != null) {
                resMgr.f();
                resMgr.g();
                resMgr.e();
            }

            HashMap<String, Object> metadata = RecentProjectsRegistry.b(scId);
            ProjectWorkspaceManager loadedWorkspace =
                    new ProjectWorkspaceManager(appContext, WorkspacePathManager.d(scId), metadata);
            GameProject loadedProject = GameProjectRepository.loadOrCreate(scId);
            loadedProject.ensureInitialized(scId);

            GameScene initialScene = !TextUtils.isEmpty(pendingSceneId)
                    ? loadedProject.getSceneById(pendingSceneId)
                    : null;
            if (initialScene == null) {
                initialScene = loadedProject.getStartScene();
            }
            final GameScene targetScene = initialScene;

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                h();

                projectMetadata = metadata;
                workspace = loadedWorkspace;
                gameProject = loadedProject;
                currentScene = targetScene;
                projectLoaded = true;

                String projectName = MapValueUtils.c(projectMetadata, "my_ws_name");
                toolbar.setTitle(TextUtils.isEmpty(projectName)
                        ? getString(R.string.project_type_game_2d)
                        : projectName);
                toolbar.setSubtitle(scId);

                bindAllViewsToCurrentScene(null);
                updateBottomMenu();

                if (currentTab != TAB_DESIGN) {
                    viewPager.setCurrentItem(currentTab, false);
                }
                handleTabSelected(currentTab, false);
            });
        });
    }

    private void bindAllViewsToCurrentScene(@Nullable String selectedObjectId) {
        if (!projectLoaded || gameProject == null) {
            return;
        }
        if (currentScene != null) {
            currentScene.ensureInitialized();
        }

        gameStage.bind(scId, gameProject, currentScene, new GameStageEditorView.StageListener() {
            @Override
            public void onObjectSelected(GameObjectBean selected) {
                if (selected != null && currentTab == TAB_DESIGN && !inspectorVisible) {
                    setPropertyPanelVisible(true, true, true);
                } else if (selected == null && currentTab == TAB_DESIGN && inspectorVisible) {
                    setPropertyPanelVisible(false, true, true);
                }
                gamePropertyPanel.updateSelection(selected);
            }

            @Override
            public void onSceneModified() {
                gamePropertyPanel.updateSelection(gameStage.getSelectedObject());
                refreshTabsAfterMutation();
                markGameModified();
            }

            @Override
            public void onOpenObjectEventBlocks(GameObjectBean target, String eventType, String eventParam) {
                openLogicEditor(
                        target,
                        eventType,
                        eventParam,
                        formatTargetTitle(target),
                        formatEventTitle(eventType, eventParam));
            }
        });

        GameObjectBean toSelect = (currentScene != null && !TextUtils.isEmpty(selectedObjectId))
                ? currentScene.findObjectById(selectedObjectId)
                : null;
        if (toSelect != null) {
            gameStage.selectObject(toSelect);
        }

        gamePropertyPanel.bind(scId, gameProject, currentScene, gameStage, createPropertyActionListener());
        gamePropertyPanel.updateSelection(gameStage.getSelectedObject());

        gameEventsView.bind(scId, gameProject, currentScene, new GameEventsView.EventActionListener() {
            @Override
            public void onOpenEventLogic(
                    GameScriptNode eventNode,
                    @Nullable GameObjectBean target,
                    String targetTitle,
                    String eventName
            ) {
                String eventType = eventNode != null ? eventNode.eventType : GameScriptNode.EVENT_SCENE_START;
                String eventParam = eventNode != null ? eventNode.eventParam : "";
                openLogicEditor(target, eventType, eventParam, targetTitle, eventName);
            }

            @Override
            public void onEventsModified() {
                refreshObjectsTabOnly();
                markGameModified();
            }
        });

        gameObjectsTabView.bind(scId, gameProject, currentScene, new GameObjectsTabView.ComponentActionListener() {
            @Override
            public void onSelectAndInspectObject(GameObjectBean obj) {
                gameStage.selectObject(obj);
                gamePropertyPanel.updateSelection(obj);
                if (!inspectorVisible) {
                    setPropertyPanelVisible(true, true, true);
                }
                if (viewPager.getCurrentItem() != TAB_DESIGN) {
                    viewPager.setCurrentItem(TAB_DESIGN, true);
                }
            }

            @Override
            public void onOpenObjectEventLogic(
                    GameObjectBean obj,
                    String eventType,
                    String eventParam,
                    String eventName
            ) {
                openLogicEditor(obj, eventType, eventParam, formatTargetTitle(obj), eventName);
            }

            @Override
            public void onObjectsModified() {
                gameStage.invalidate();
                gamePropertyPanel.updateSelection(gameStage.getSelectedObject());
                refreshEventsTabOnly();
                markGameModified();
            }
        });

        refreshSceneLabel();
    }

    private GamePropertyPanelView.PropertyActionListener createPropertyActionListener() {
        return new GamePropertyPanelView.PropertyActionListener() {
            @Override
            public void onPropertyChanged() {
                gameStage.invalidate();
                refreshTabsAfterMutation();
                markGameModified();
            }

            @Override
            public void onRequestOpenImageManager() {
                launchManagerActivity(ManageImageActivity.class, resourceManagerLauncher);
            }

            @Override
            public void onRequestOpenSoundManager() {
                launchManagerActivity(ManageSoundActivity.class, resourceManagerLauncher);
            }

            @Override
            public void onRequestOpenFontManager() {
                launchManagerActivity(ManageFontActivity.class, resourceManagerLauncher);
            }

            @Override
            public void onOpenEventBlocks(GameObjectBean target, String eventType, String eventParam) {
                openLogicEditor(
                        target,
                        eventType,
                        eventParam,
                        formatTargetTitle(target),
                        formatEventTitle(eventType, eventParam));
            }
        };
    }

    private void switchToScene(@Nullable GameScene scene) {
        if (!projectLoaded || scene == null) {
            return;
        }
        currentScene = scene;
        bindAllViewsToCurrentScene(null);
        GameProjectRepository.save(scId, gameProject);
    }

    private void openLogicEditor(
            @Nullable GameObjectBean target,
            @Nullable String eventType,
            @Nullable String eventParam,
            @Nullable String eventTitle,
            @Nullable String eventSubtitle
    ) {
        if (!projectLoaded || gameProject == null) {
            return;
        }
        GameProjectRepository.save(scId, gameProject);
        String sceneId = currentScene != null ? currentScene.id : gameProject.startSceneId;
        Intent intent = GameLogicEditorActivity.createIntent(
                this,
                scId,
                sceneId,
                target,
                eventType,
                eventParam,
                eventTitle,
                eventSubtitle);
        logicEditorLauncher.launch(intent);
    }

    private void reloadGameProjectAfterLogicReturn() {
        if (TextUtils.isEmpty(scId)) {
            return;
        }
        String prevSceneId = currentScene != null ? currentScene.id : null;
        GameObjectBean prevSelected = gameStage != null ? gameStage.getSelectedObject() : null;
        String prevObjId = prevSelected != null ? prevSelected.id : null;

        GameProject reloaded = GameProjectRepository.loadOrCreate(scId);
        reloaded.ensureInitialized(scId);
        gameProject = reloaded;

        GameScene nextScene = !TextUtils.isEmpty(prevSceneId) ? gameProject.getSceneById(prevSceneId) : null;
        if (nextScene == null) {
            nextScene = gameProject.getStartScene();
        }
        currentScene = nextScene;
        projectLoaded = true;

        bindAllViewsToCurrentScene(prevObjId);
    }

    private void reloadResourcesAfterManagerReturn() {
        if (TextUtils.isEmpty(scId)) {
            return;
        }
        ioExecutor.execute(() -> {
            try {
                ProjectResourceManager resMgr = ProjectDataManagerRegistry.d(scId);
                if (resMgr != null) {
                    resMgr.f();
                    resMgr.g();
                    resMgr.e();
                }
            } catch (Throwable ignored) {
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || !projectLoaded) {
                    return;
                }
                gameStage.invalidateResourceCache();
                gameStage.invalidate();
                gamePropertyPanel.updateSelection(gameStage.getSelectedObject());
                refreshTabsAfterMutation();
            });
        });
    }

    private void refreshTabsAfterMutation() {
        refreshEventsTabOnly();
        refreshObjectsTabOnly();
    }

    private void refreshEventsTabOnly() {
        if (gameEventsView != null) {
            gameEventsView.refreshEvents();
        }
    }

    private void refreshObjectsTabOnly() {
        if (gameObjectsTabView != null) {
            gameObjectsTabView.refreshData();
        }
    }

    private void refreshSceneLabel() {
        if (fileName == null) {
            return;
        }
        if (currentScene != null && !TextUtils.isEmpty(currentScene.name)) {
            fileName.setText(currentScene.name);
        } else {
            fileName.setText("Scene 1");
        }
    }

    private void markGameModified() {
        if (!projectLoaded || gameProject == null) {
            return;
        }
        GameProjectRepository.save(scId, gameProject);
        refreshSceneLabel();
    }

    private String formatTargetTitle(@Nullable GameObjectBean target) {
        if (target != null && !TextUtils.isEmpty(target.name)) {
            return target.name;
        }
        return currentScene != null && !TextUtils.isEmpty(currentScene.name) ? currentScene.name : "Scene";
    }

    private String formatEventTitle(@Nullable String eventType, @Nullable String eventParam) {
        if (GameScriptNode.EVENT_SCENE_START.equals(eventType)) {
            return "On Scene Start";
        } else if (GameScriptNode.EVENT_UPDATE.equals(eventType)) {
            return "Every Frame (Update)";
        } else if (GameScriptNode.EVENT_TOUCH.equals(eventType)) {
            return "On Touch";
        } else if (GameScriptNode.EVENT_COLLISION.equals(eventType)) {
            return TextUtils.isEmpty(eventParam) ? "On Collision" : "On Collision (" + eventParam + ")";
        } else if (GameScriptNode.EVENT_BUTTON.equals(eventType)) {
            return TextUtils.isEmpty(eventParam) ? "On Button Pressed" : "On Button Pressed (" + eventParam + ")";
        } else if (GameScriptNode.EVENT_MESSAGE.equals(eventType)) {
            return TextUtils.isEmpty(eventParam) ? "On Message Received" : "Message: " + eventParam;
        }
        return TextUtils.isEmpty(eventType) ? "Event" : eventType;
    }

    private void openSceneManagerDialog() {
        if (!projectLoaded || gameProject == null) {
            return;
        }
        GameHierarchyDialog.showSceneManagerDialog(this, gameProject, currentScene,
                new GameHierarchyDialog.SceneDialogCallback() {
                    @Override
                    public void onSceneSelected(GameScene scene) {
                        switchToScene(scene);
                    }

                    @Override
                    public void onProjectUpdated() {
                        if (gameProject.scenes != null && !gameProject.scenes.isEmpty()
                                && (currentScene == null || gameProject.getSceneById(currentScene.id) == null)) {
                            switchToScene(gameProject.getStartScene());
                        } else {
                            String selId = gameStage.getSelectedObject() != null
                                    ? gameStage.getSelectedObject().id : null;
                            bindAllViewsToCurrentScene(selId);
                        }
                        markGameModified();
                    }
                });
    }

    private void openHierarchyDialog() {
        if (!projectLoaded || gameProject == null || currentScene == null) {
            return;
        }
        GameHierarchyDialog.showHierarchyDialog(this, gameProject, currentScene,
                new GameHierarchyDialog.HierarchyDialogCallback() {
                    @Override
                    public void onSelectObject(GameObjectBean obj) {
                        gameStage.selectObject(obj);
                        gamePropertyPanel.updateSelection(obj);
                        if (currentTab != TAB_DESIGN) {
                            viewPager.setCurrentItem(TAB_DESIGN, true);
                        } else if (!inspectorVisible) {
                            setPropertyPanelVisible(true, true, true);
                        }
                    }

                    @Override
                    public void onSceneUpdated() {
                        String selId = gameStage.getSelectedObject() != null
                                ? gameStage.getSelectedObject().id : null;
                        bindAllViewsToCurrentScene(selId);
                        markGameModified();
                    }
                });
    }

    private void openEngineSettingsDialog() {
        if (!projectLoaded || gameProject == null) {
            return;
        }
        GameHierarchyDialog.showEngineSettingsDialog(this, gameProject, () -> {
            if (currentScene != null) {
                gameStage.setScene(currentScene);
                gameStage.resetCameraView();
            }
            gamePropertyPanel.updateSelection(gameStage.getSelectedObject());
            markGameModified();
        });
    }

    private void launchPlayPreview() {
        if (!projectLoaded || gameProject == null) {
            return;
        }
        GameProjectRepository.save(scId, gameProject);
        Intent intent = new Intent(getApplicationContext(), GamePreviewActivity.class);
        intent.putExtra(GamePreviewActivity.EXTRA_SC_ID, scId);
        if (currentScene != null && !TextUtils.isEmpty(currentScene.id)) {
            intent.putExtra(GamePreviewActivity.EXTRA_SCENE_ID, currentScene.id);
        }
        startActivity(intent);
    }

    private void updateBottomMenu() {
        if (bottomMenu == null) {
            return;
        }
        MenuItem installItem = bottomMenu.findItem(3);
        MenuItem cleanItem = bottomMenu.findItem(8);
        boolean hasApk = workspace != null && FileUtil.isExistFile(workspace.finalToInstallApkPath);
        boolean hasTemp = workspace != null && FileUtil.isExistFile(workspace.projectMyscPath);
        if (installItem != null) {
            installItem.setVisible(hasApk);
        }
        if (cleanItem != null) {
            cleanItem.setVisible(hasTemp);
        }
    }

    private void saveSketchwareRegistriesAsync(boolean showToast) {
        ioExecutor.execute(() -> {
            persistSketchwareManagers();
            if (showToast && !isFinishing() && !isDestroyed()) {
                runOnUiThread(() ->
                        SketchwareUtil.toast(getString(R.string.common_message_complete_save)));
            }
        });
    }

    private void saveAndCloseProject() {
        if (!isFinishing()) {
            k();
        }
        ioExecutor.execute(() -> {
            persistSketchwareManagers();
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    h();
                    finish();
                }
            });
        });
    }

    private void persistSketchwareManagers() {
        try {
            ProjectResourceManager resMgr = ProjectDataManagerRegistry.d(scId);
            if (resMgr != null) {
                resMgr.a();
                resMgr.x();
            }
            ProjectFileManager fileMgr = ProjectDataManagerRegistry.b(scId);
            if (fileMgr != null) {
                fileMgr.m();
            }
            ProjectDataManager dataMgr = ProjectDataManagerRegistry.a(scId);
            if (dataMgr != null) {
                dataMgr.j();
            }
            ProjectLibraryManager libMgr = ProjectDataManagerRegistry.c(scId);
            if (libMgr != null) {
                libMgr.l();
            }
        } catch (Throwable ignored) {
        }
    }

    private void closeDrawerIfOpen() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.END)) {
            drawerLayout.closeDrawer(GravityCompat.END);
        }
    }

    @SafeVarargs
    private final void launchManagerActivity(
            Class<? extends Activity> targetActivity,
            @Nullable ActivityResultLauncher<Intent> launcher,
            Pair<String, String>... extras
    ) {
        Intent intent = new Intent(getApplicationContext(), targetActivity);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(STATE_SC_ID, scId);
        if (extras != null) {
            for (Pair<String, String> extra : extras) {
                if (extra != null) {
                    intent.putExtra(extra.first, extra.second);
                }
            }
        }
        if (launcher != null) {
            launcher.launch(intent);
        } else {
            startActivity(intent);
        }
    }

    private void startApkBuild() {
        if (!projectLoaded || gameProject == null) {
            return;
        }
        if (currentBuildTask != null && !currentBuildTask.canceled && !currentBuildTask.isBuildFinished) {
            SketchwareUtil.toast("Build is already in progress.");
            return;
        }
        BuildGameApkTask task = new BuildGameApkTask();
        currentBuildTask = task;
        task.execute();
    }

    private void updateBuildUiState(boolean isRunning, @Nullable String initialText) {
        btnRun.setBackgroundTintList(ColorStateList.valueOf(ThemeUtils.getColor(
                this, isRunning ? R.attr.colorErrorContainer : R.attr.colorPrimary)));
        btnRun.setIcon(ContextCompat.getDrawable(
                this, isRunning ? R.drawable.ic_mtrl_stop : R.drawable.ic_mtrl_run));
        btnRun.setIconTint(ColorStateList.valueOf(ThemeUtils.getColor(
                this, isRunning ? R.attr.colorOnErrorContainer : R.attr.colorSurfaceContainerLowest)));
        btnRun.setTextColor(ColorStateList.valueOf(ThemeUtils.getColor(
                this, isRunning ? R.attr.colorOnErrorContainer : R.attr.colorSurfaceContainerLowest)));
        btnRun.setText(isRunning ? "Stop" : "Run");
        btnOptions.setEnabled(!isRunning);
        progressContainer.setVisibility(isRunning ? View.VISIBLE : View.GONE);
        if (isRunning) {
            progressBar.setIndeterminate(true);
            if (initialText != null) {
                progressText.setText(initialText);
            }
        }
    }

    private void installBuiltApk(@NonNull ProjectWorkspaceManager builtWorkspace) {
        File apkFile = new File(builtWorkspace.finalToInstallApkPath);
        if (!apkFile.exists()) {
            SketchwareUtil.toastError("Built APK not found.");
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri apkUri = FileProvider.getUriForFile(
                    getApplicationContext(),
                    getApplicationContext().getPackageName() + ".provider",
                    apkFile);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            startActivity(intent);
        } catch (Throwable t) {
            SketchwareUtil.toastError("Could not open installer: " + t.getMessage());
        }
    }

    private void indicateCompileErrorOccurred(String error) {
        String safeError = error == null ? "Unknown build error" : error;
        new CompileErrorSaver(scId).writeLogsToFile(safeError);
        Snackbar snackbar = Snackbar.make(
                coordinatorLayout != null ? coordinatorLayout : findViewById(android.R.id.content),
                "Show compile log",
                Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction(R.string.common_word_show, v -> {
            snackbar.dismiss();
            Intent intent = new Intent(getApplicationContext(), CompileLogActivity.class);
            intent.putExtra(STATE_SC_ID, scId);
            intent.putExtra("showingLastError", true);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });
        snackbar.show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private class GamePagerAdapter extends PagerAdapter {
        private final String[] labels = new String[]{
                getString(R.string.design_tab_title_view),
                getString(R.string.design_tab_title_event),
                getString(R.string.design_tab_title_component)
        };

        @Override
        public int getCount() {
            return 3;
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return labels[position];
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            View page;
            if (position == TAB_DESIGN) {
                page = designPageContainer;
            } else if (position == TAB_EVENT) {
                page = gameEventsView;
            } else {
                page = gameObjectsTabView;
            }
            if (page.getParent() instanceof ViewGroup parent) {
                parent.removeView(page);
            }
            container.addView(page, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            return page;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            if (object instanceof View view) {
                container.removeView(view);
            }
        }
    }

    private class BuildGameApkTask implements BuildProgressReceiver {

        volatile boolean canceled = false;
        volatile boolean isBuildFinished = false;

        void execute() {
            updateBuildUiState(true, "Preparing game build...");
            buildExecutor.execute(this::runBuild);
        }

        void cancelBuild() {
            canceled = true;
            onProgress("Canceling build...", -1);
        }

        @Override
        public void onProgress(String progress, int step) {
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (progress != null) {
                    progressText.setText(progress);
                }
                if (step > 0) {
                    if (progressBar.isIndeterminate()) {
                        progressBar.setIndeterminate(false);
                    }
                    progressBar.setMax(21);
                    progressBar.setProgress(Math.min(21, step), true);
                } else if (!progressBar.isIndeterminate()) {
                    progressBar.setIndeterminate(true);
                }
            });
        }

        private void runBuild() {
            final Context appContext = getApplicationContext();
            try {
                onProgress("Saving 2D project...", 1);
                GameProjectRepository.save(scId, gameProject);
                persistSketchwareManagers();
                if (canceled) {
                    finishCancelled();
                    return;
                }

                ProjectFileManager fileManager = ProjectDataManagerRegistry.b(scId);
                ProjectDataManager dataManager = ProjectDataManagerRegistry.a(scId);
                ProjectLibraryManager libraryManager = ProjectDataManagerRegistry.c(scId);
                ProjectResourceManager resourceManager = ProjectDataManagerRegistry.d(scId);

                if (fileManager != null && fileManager.b().isEmpty()) {
                    fileManager.a(0, "main");
                }

                HashMap<String, Object> metadata = RecentProjectsRegistry.b(scId);
                ProjectWorkspaceManager targetWorkspace =
                        new ProjectWorkspaceManager(appContext, WorkspacePathManager.d(scId), metadata);

                FileUtil.deleteFile(targetWorkspace.projectMyscPath);
                targetWorkspace.c(appContext);
                targetWorkspace.a();
                targetWorkspace.a(appContext, WorkspacePathManager.e("600"));

                if (ProjectMapUtils.getBoolean(metadata, "custom_icon")) {
                    targetWorkspace.aa(WorkspacePathManager.e() + File.separator + scId + File.separator + "mipmaps");
                    if (ProjectMapUtils.getBoolean(metadata, "isIconAdaptive", false)) {
                        targetWorkspace.createLauncherIconXml(
                                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                        + "<adaptive-icon xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                                        + "    <background android:drawable=\"@mipmap/ic_launcher_background\"/>\n"
                                        + "    <foreground android:drawable=\"@mipmap/ic_launcher_foreground\"/>\n"
                                        + "    <monochrome android:drawable=\"@mipmap/ic_launcher_monochrome\"/>\n"
                                        + "</adaptive-icon>");
                    } else {
                        targetWorkspace.a(WorkspacePathManager.e() + File.separator + scId + File.separator + "icon.png");
                    }
                }

                onProgress("Copying sprites, sounds, and fonts...", 2);
                if (resourceManager != null) {
                    resourceManager.b(targetWorkspace.resDirectoryPath + File.separator + "drawable-xhdpi");
                    resourceManager.c(targetWorkspace.resDirectoryPath + File.separator + "raw");
                    resourceManager.a(targetWorkspace.assetsPath + File.separator + "fonts");
                }
                if (canceled) {
                    finishCancelled();
                    return;
                }

                ProjectBuilder builder = new ProjectBuilder(this, appContext, targetWorkspace);
                targetWorkspace.a(libraryManager, fileManager, dataManager);
                builder.buildBuiltInLibraryInformation();
                targetWorkspace.b(fileManager, dataManager, libraryManager, builder.getBuiltInLibraryManager());
                targetWorkspace.f();
                targetWorkspace.e();

                onProgress("Injecting 2D runtime...", 4);
                GameApkSourceGenerator.injectGameRuntimeIntoWorkspace(scId, targetWorkspace, gameProject);
                if (canceled) {
                    finishCancelled();
                    return;
                }

                onProgress("Preparing AAPT2...", 6);
                builder.maybeExtractAapt2();
                if (canceled) {
                    finishCancelled();
                    return;
                }

                onProgress("Extracting libraries...", 8);
                BuiltInLibraries.extractCompileAssets(this);
                if (canceled) {
                    finishCancelled();
                    return;
                }

                onProgress("Compiling resources...", 11);
                builder.compileResources();
                if (canceled) {
                    finishCancelled();
                    return;
                }

                onProgress("Compiling Java code...", 14);
                builder.compileJavaCode();
                if (canceled) {
                    finishCancelled();
                    return;
                }

                onProgress("Generating DEX files...", 17);
                builder.createDexFilesFromClasses();
                builder.getDexFilesReady();
                if (canceled) {
                    finishCancelled();
                    return;
                }

                onProgress("Building APK...", 19);
                builder.buildApk();
                if (canceled) {
                    finishCancelled();
                    return;
                }

                onProgress("Signing APK...", 21);
                builder.signDebugApk();
                if (canceled) {
                    finishCancelled();
                    return;
                }

                isBuildFinished = true;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    workspace = targetWorkspace;
                    updateBuildUiState(false, null);
                    updateBottomMenu();
                    installBuiltApk(targetWorkspace);
                });
            } catch (MissingFileException e) {
                isBuildFinished = true;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    updateBuildUiState(false, null);
                    String path = e.getMissingFile() != null ? e.getMissingFile().getAbsolutePath() : "";
                    new MaterialAlertDialogBuilder(GameEngineActivity.this)
                            .setTitle("Missing file detected")
                            .setMessage("Missing required file/directory: " + path)
                            .setPositiveButton(R.string.common_word_ok, null)
                            .show();
                });
            } catch (ProjectResourceStore e) {
                isBuildFinished = true;
                String msg = e.getMessage();
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    updateBuildUiState(false, null);
                    indicateCompileErrorOccurred(msg);
                });
            } catch (Throwable t) {
                isBuildFinished = true;
                String trace = Log.getStackTraceString(t);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    updateBuildUiState(false, null);
                    indicateCompileErrorOccurred(trace);
                });
            }
        }

        private void finishCancelled() {
            isBuildFinished = true;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                updateBuildUiState(false, null);
                updateBottomMenu();
                SketchwareUtil.toast("Build canceled.");
            });
        }
    }
}
