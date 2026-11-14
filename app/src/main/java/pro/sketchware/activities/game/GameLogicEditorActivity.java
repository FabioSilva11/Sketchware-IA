package pro.sketchware.activities.game;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.editor.LogicEditorDrawer;
import com.besome.sketch.editor.logic.BlockPane;
import com.besome.sketch.editor.logic.LogicTopMenu;
import com.besome.sketch.editor.logic.PaletteBlock;
import com.besome.sketch.editor.logic.PaletteSelector;
import com.besome.sketch.editor.view.ViewDummy;
import com.besome.sketch.editor.view.ViewLogicEditor;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.besome.sketch.lib.ui.ColorPickerDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import pro.sketchware.R;
import pro.sketchware.activities.game.blocks.GameBlockBeanConverter;
import pro.sketchware.activities.game.blocks.GameBlockCatalog;
import pro.sketchware.activities.game.model.GameBlockItem;
import pro.sketchware.activities.game.model.GameObjectBean;
import pro.sketchware.activities.game.model.GameProject;
import pro.sketchware.activities.game.model.GameProjectRepository;
import pro.sketchware.activities.game.model.GameScene;
import pro.sketchware.activities.game.model.GameScriptNode;
import pro.sketchware.activities.game.runtime.GamePreviewActivity;
import pro.sketchware.editor.BlockCanvasView;
import pro.sketchware.editor.BlockMenuItem;
import pro.sketchware.editor.BlockView;

/**
 * Fullscreen single-event Scratch block coding screen for the 2D Game Engine.
 * Operates natively on {@code R.layout.logic_editor} ({@link ViewLogicEditor}, {@link BlockPane},
 * {@link PaletteBlock}, {@link PaletteSelector}, {@link ViewDummy}, {@link LogicTopMenu}) while
 * reading/writing {@link GameProjectRepository} and {@link GameScriptNode}.
 */
public class GameLogicEditorActivity extends BaseAppCompatActivity implements View.OnTouchListener {

    public static final String EXTRA_SC_ID = "sc_id";
    public static final String EXTRA_SCENE_ID = "scene_id";
    public static final String EXTRA_TARGET_OBJECT_ID = "target_object_id";
    public static final String EXTRA_EVENT_TYPE = "event_type";
    public static final String EXTRA_EVENT_PARAM = "event_param";
    public static final String EXTRA_EVENT_TITLE = "event_title";
    public static final String EXTRA_EVENT_SUBTITLE = "event_subtitle";

    private static final String CLIPBOARD_PREFIX = "SWGAME_BLOCKS:";
    private static final int MAX_UNDO_HISTORY = 60;

    private String scId = "";
    private String sceneId = "";
    private String targetObjectId = GameScriptNode.SCENE_TARGET_ID;
    private String eventType = GameScriptNode.EVENT_SCENE_START;
    private String eventParam = "";
    private String eventTitle = "";
    private String eventSubtitle = "";

    private GameProject gameProject;
    private GameScene currentScene;
    @Nullable
    private GameObjectBean targetObject;
    @Nullable
    private GameScriptNode currentScript;
    private boolean hasUnsavedChanges = false;

    // Native LogicEditorActivity views
    private View rootLayout;
    private Toolbar toolbar;
    private View syntaxCheckContainer;
    private ViewLogicEditor editor;
    private BlockPane blockPane;
    private LogicEditorDrawer rightDrawer;
    private LinearLayout layoutPalette;
    private LinearLayout areaPalette;
    private FloatingActionButton fabTogglePalette;
    private PaletteBlock paletteBlock;
    private PaletteSelector paletteSelector;
    private LogicTopMenu topMenu;
    private ViewDummy dummy;

    // Animations & palette state
    private ObjectAnimator paletteOpenAnim;
    private ObjectAnimator paletteCloseAnim;
    private ObjectAnimator topMenuOpenAnim;
    private ObjectAnimator topMenuCloseAnim;
    private boolean isPaletteOpen = false;
    private boolean isTopMenuOpen = false;
    private int selectedCategoryIndex = 0;

    // Touch / drag state
    private final Handler touchHandler = new Handler(Looper.getMainLooper());
    private Vibrator vibrator;
    private int touchSlop;
    private int longPressDelayMs;
    private BlockView touchedBlock;
    private boolean isDragging = false;
    private float initRawX;
    private float initRawY;
    private float lastRawX;
    private float lastRawY;
    private final int[] dummyPos = new int[2];
    @Nullable
    private PaneSnapshot preDragSnapshot;

    // Undo / Redo history
    private final Deque<PaneSnapshot> undoStack = new ArrayDeque<>();
    private final Deque<PaneSnapshot> redoStack = new ArrayDeque<>();

    private final Runnable startDragRunnable = this::startBlockDrag;

    public static Intent createIntent(
            Context context,
            String scId,
            @Nullable String sceneId,
            @Nullable GameObjectBean target,
            @Nullable String eventType,
            @Nullable String eventParam,
            @Nullable String eventTitle,
            @Nullable String eventSubtitle) {
        Intent intent = new Intent(context, GameLogicEditorActivity.class);
        intent.putExtra(EXTRA_SC_ID, scId != null ? scId : "");
        if (!TextUtils.isEmpty(sceneId)) {
            intent.putExtra(EXTRA_SCENE_ID, sceneId);
        }
        String targetId = (target != null && !TextUtils.isEmpty(target.id))
                ? target.id
                : GameScriptNode.SCENE_TARGET_ID;
        intent.putExtra(EXTRA_TARGET_OBJECT_ID, targetId);
        intent.putExtra(EXTRA_EVENT_TYPE,
                !TextUtils.isEmpty(eventType) ? eventType : GameScriptNode.EVENT_SCENE_START);
        if (eventParam != null) {
            intent.putExtra(EXTRA_EVENT_PARAM, eventParam);
        }
        if (eventTitle != null) {
            intent.putExtra(EXTRA_EVENT_TITLE, eventTitle);
        }
        if (eventSubtitle != null) {
            intent.putExtra(EXTRA_EVENT_SUBTITLE, eventSubtitle);
        }
        return intent;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        setTheme(R.style.Theme_SketchwarePro_SmallToolbar);
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        if (!isStoragePermissionGranted()) {
            finish();
            return;
        }

        readInputState(savedInstanceState);
        if (TextUtils.isEmpty(scId)) {
            Toast.makeText(this, "Invalid project.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        gameProject = GameProjectRepository.loadOrCreate(scId);
        gameProject.ensureInitialized(scId);

        currentScene = !TextUtils.isEmpty(sceneId) ? gameProject.getSceneById(sceneId) : null;
        if (currentScene == null) {
            currentScene = gameProject.getStartScene();
        }
        if (currentScene == null) {
            Toast.makeText(this, "Scene not found.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        sceneId = currentScene.id;

        targetObject = (TextUtils.isEmpty(targetObjectId)
                || GameScriptNode.SCENE_TARGET_ID.equals(targetObjectId))
                ? null
                : currentScene.findObjectById(targetObjectId);
        if (targetObject == null && !GameScriptNode.SCENE_TARGET_ID.equals(targetObjectId)) {
            targetObjectId = GameScriptNode.SCENE_TARGET_ID;
        }

        resolveCurrentScript();
        initNativeUi();
        setupBackNavigation();
    }

    private void readInputState(@Nullable Bundle savedInstanceState) {
        Bundle src = savedInstanceState != null ? savedInstanceState : getIntent().getExtras();
        if (src == null) {
            return;
        }
        scId = safeString(src.getString(EXTRA_SC_ID), "");
        sceneId = safeString(src.getString(EXTRA_SCENE_ID), "");
        targetObjectId = safeString(src.getString(EXTRA_TARGET_OBJECT_ID), GameScriptNode.SCENE_TARGET_ID);
        eventType = safeString(src.getString(EXTRA_EVENT_TYPE), GameScriptNode.EVENT_SCENE_START);
        eventParam = src.getString(EXTRA_EVENT_PARAM);
        eventTitle = safeString(src.getString(EXTRA_EVENT_TITLE), "");
        eventSubtitle = safeString(src.getString(EXTRA_EVENT_SUBTITLE), "");
        hasUnsavedChanges = savedInstanceState != null && savedInstanceState.getBoolean("has_unsaved", false);
    }

    private void resolveCurrentScript() {
        String resolvedTargetId = targetObject != null && !TextUtils.isEmpty(targetObject.id)
                ? targetObject.id
                : GameScriptNode.SCENE_TARGET_ID;
        targetObjectId = resolvedTargetId;
        eventType = safeString(eventType, GameScriptNode.EVENT_SCENE_START);

        if (eventParam == null && currentScene != null && currentScene.scripts != null) {
            String found = null;
            for (GameScriptNode node : currentScene.scripts) {
                if (node != null
                        && TextUtils.equals(node.targetObjectId, resolvedTargetId)
                        && TextUtils.equals(node.eventType, eventType)) {
                    found = node.eventParam;
                    break;
                }
            }
            eventParam = found != null ? found : defaultEventParam(eventType);
        } else if (eventParam == null) {
            eventParam = defaultEventParam(eventType);
        }

        currentScript = currentScene.getOrCreateScript(resolvedTargetId, eventType, eventParam);
        if (currentScript != null) {
            currentScript.ensureInitialized();
        }
    }

    private void initNativeUi() {
        setContentView(R.layout.logic_editor);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        ViewConfiguration vc = ViewConfiguration.get(this);
        touchSlop = vc.getScaledTouchSlop();
        longPressDelayMs = Math.max(120, ViewConfiguration.getLongPressTimeout() / 2);

        rootLayout = findViewById(R.id.layout);
        toolbar = findViewById(R.id.toolbar);
        syntaxCheckContainer = findViewById(R.id.syntax_check_container);
        editor = findViewById(R.id.editor);
        blockPane = editor.getBlockPane();
        blockPane.isGameMode = true;
        rightDrawer = findViewById(R.id.right_drawer);
        layoutPalette = findViewById(R.id.layout_palette);
        areaPalette = findViewById(R.id.area_palette);
        fabTogglePalette = findViewById(R.id.fab_toggle_palette);
        paletteBlock = findViewById(R.id.palette_block);
        paletteSelector = findViewById(R.id.palette_selector);
        topMenu = findViewById(R.id.top_menu);
        dummy = findViewById(R.id.dummy);

        if (syntaxCheckContainer != null) {
            syntaxCheckContainer.setVisibility(View.GONE);
        }
        if (rightDrawer != null) {
            rightDrawer.setVisibility(View.GONE);
        }

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
            Insets sysBars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(sysBars.left, sysBars.top, sysBars.right, sysBars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        String resolvedTitle = !TextUtils.isEmpty(eventTitle)
                ? eventTitle
                : (targetObject != null && !TextUtils.isEmpty(targetObject.name)
                        ? targetObject.name
                        : "Current Scene");
        String resolvedSubtitle = !TextUtils.isEmpty(eventSubtitle)
                ? eventSubtitle
                : buildDefaultSubtitle(eventType, eventParam);
        toolbar.setTitle(resolvedTitle);
        toolbar.setSubtitle(resolvedSubtitle);
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        // Top menu starts hidden above toolbar
        topMenu.setVisibility(View.GONE);
        topMenu.post(() -> {
            int h = topMenu.getHeight();
            if (h > 0) {
                topMenu.setTranslationY(-h);
            }
        });

        // Root Hat block + saved GameScriptNode blocks
        blockPane.a(buildHatTitle(), eventType);
        if (blockPane.getRoot() != null) {
            blockPane.getRoot().setOnTouchListener(this);
        }

        List<GameBlockItem> initialBlocks = currentScript != null ? currentScript.blocks : null;
        GameBlockBeanConverter.ConversionResult conv =
                GameBlockBeanConverter.toBlockBeans(this, initialBlocks);
        GameBlockBeanConverter.populatePane(this, blockPane, conv.beans, conv.rootNextId, this);
        undoStack.clear();
        redoStack.clear();

        // Setup Game categories in native PaletteSelector + PaletteBlock
        setupPalette();

        fabTogglePalette.setOnClickListener(v -> setPaletteOpen(!isPaletteOpen, true));
        View searchHeader = findViewById(R.id.search_header);
        if (searchHeader != null) {
            searchHeader.setOnClickListener(v -> paletteSelector.showSearchDialog());
        }

        configurePaletteOrientation(getResources().getConfiguration().orientation);
        setPaletteOpen(false, false);
        showCategory(0);
    }

    private void setupPalette() {
        List<GameBlockCatalog.CategoryDef> cats = GameBlockCatalog.getCategories();
        int n = cats.size();
        int[] ids = new int[n];
        String[] names = new String[n];
        int[] colors = new int[n];
        for (int i = 0; i < n; i++) {
            GameBlockCatalog.CategoryDef cat = cats.get(i);
            ids[i] = i;
            names[i] = cat.title;
            colors[i] = MaterialColors.harmonizeWithPrimary(this, cat.color);
        }
        paletteSelector.setOnBlockCategorySelectListener((idx, color) -> showCategory(idx));
        paletteSelector.setCustomPalettes(ids, names, colors);
    }

    private void showCategory(int categoryIdx) {
        List<GameBlockCatalog.CategoryDef> cats = GameBlockCatalog.getCategories();
        if (cats.isEmpty()) {
            return;
        }
        if (categoryIdx < 0 || categoryIdx >= cats.size()) {
            categoryIdx = 0;
        }
        selectedCategoryIndex = categoryIdx;
        paletteBlock.a();

        GameBlockCatalog.CategoryDef cat = cats.get(categoryIdx);
        int catColor = MaterialColors.harmonizeWithPrimary(this, cat.color);
        boolean isVariablesCat = "variables".equals(cat.id);

        if (isVariablesCat) {
            TextView addVarBtn = paletteBlock.a("Add Variable");
            addVarBtn.setOnClickListener(v -> showAddVariableDialog());

            TextView removeVarBtn = paletteBlock.a("Remove Variable");
            removeVarBtn.setOnClickListener(v -> showRemoveVariableDialog());

            List<String> varNames = getSortedVariableNames();
            for (String varName : varNames) {
                BlockCanvasView varBlock = paletteBlock.addGameBlock(varName, "d", "", "getVar", catColor);
                varBlock.setOnTouchListener(this);
            }
        }

        for (GameBlockCatalog.BlockDef def : cat.blocks) {
            if (isVariablesCat && "getVar".equals(def.opCode)) {
                continue;
            }
            int blockColor = MaterialColors.harmonizeWithPrimary(this, def.color);
            String blockType = GameBlockBeanConverter.normalizeBlockType(def.type, def);
            BlockCanvasView bv = paletteBlock.addGameBlock(def.spec, blockType, "", def.opCode, blockColor);
            bv.setOnTouchListener(this);
        }
    }

    private void configurePaletteOrientation(int orientation) {
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) layoutPalette.getLayoutParams();
        LinearLayout.LayoutParams fabLp = (LinearLayout.LayoutParams) fabTogglePalette.getLayoutParams();
        LinearLayout.LayoutParams areaLp = (LinearLayout.LayoutParams) areaPalette.getLayoutParams();
        int fabMargin = dp(16);

        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            layoutPalette.setOrientation(LinearLayout.HORIZONTAL);
            lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);

            fabLp.gravity = Gravity.BOTTOM | Gravity.END;
            fabLp.setMargins(fabMargin, fabMargin, fabMargin, fabMargin);

            areaLp.width = dp(320);
            areaLp.height = ViewGroup.LayoutParams.MATCH_PARENT;

            int closedDist = dp(320);
            paletteOpenAnim = ObjectAnimator.ofFloat(layoutPalette, View.TRANSLATION_X, 0f);
            paletteCloseAnim = ObjectAnimator.ofFloat(layoutPalette, View.TRANSLATION_X, closedDist);
            layoutPalette.setTranslationY(0f);
            layoutPalette.setTranslationX(isPaletteOpen ? 0f : closedDist);
        } else {
            layoutPalette.setOrientation(LinearLayout.VERTICAL);
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.removeRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);

            fabLp.gravity = Gravity.BOTTOM | Gravity.END;
            fabLp.setMargins(fabMargin, fabMargin, fabMargin, fabMargin);

            areaLp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            areaLp.height = dp(240);

            int closedDist = dp(240);
            paletteOpenAnim = ObjectAnimator.ofFloat(layoutPalette, View.TRANSLATION_Y, 0f);
            paletteCloseAnim = ObjectAnimator.ofFloat(layoutPalette, View.TRANSLATION_Y, closedDist);
            layoutPalette.setTranslationX(0f);
            layoutPalette.setTranslationY(isPaletteOpen ? 0f : closedDist);
        }

        layoutPalette.setLayoutParams(lp);
        fabTogglePalette.setLayoutParams(fabLp);
        areaPalette.setLayoutParams(areaLp);
        paletteOpenAnim.setDuration(250);
        paletteCloseAnim.setDuration(250);
    }

    private void setPaletteOpen(boolean open, boolean animate) {
        if (isPaletteOpen == open && animate) {
            return;
        }
        isPaletteOpen = open;
        if (!animate) {
            boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
            if (landscape) {
                layoutPalette.setTranslationY(0f);
                layoutPalette.setTranslationX(open ? 0f : dp(320));
            } else {
                layoutPalette.setTranslationX(0f);
                layoutPalette.setTranslationY(open ? 0f : dp(240));
            }
            return;
        }
        if (paletteOpenAnim != null && paletteOpenAnim.isRunning()) {
            paletteOpenAnim.cancel();
        }
        if (paletteCloseAnim != null && paletteCloseAnim.isRunning()) {
            paletteCloseAnim.cancel();
        }
        ObjectAnimator anim = open ? paletteOpenAnim : paletteCloseAnim;
        if (anim != null) {
            anim.start();
        }
    }

    private void setTopMenuOpen(boolean open) {
        if (isTopMenuOpen == open) {
            return;
        }
        isTopMenuOpen = open;
        if (topMenuOpenAnim != null && topMenuOpenAnim.isRunning()) {
            topMenuOpenAnim.cancel();
        }
        if (topMenuCloseAnim != null && topMenuCloseAnim.isRunning()) {
            topMenuCloseAnim.cancel();
        }
        int height = topMenu.getHeight();
        if (height <= 0) {
            height = dp(56);
        }
        if (open) {
            topMenu.setVisibility(View.VISIBLE);
            topMenu.setTranslationY(-height);
            topMenuOpenAnim = ObjectAnimator.ofFloat(topMenu, View.TRANSLATION_Y, 0f);
            topMenuOpenAnim.setDuration(220);
            topMenuOpenAnim.start();
        } else {
            topMenuCloseAnim = ObjectAnimator.ofFloat(topMenu, View.TRANSLATION_Y, -height);
            topMenuCloseAnim.setDuration(200);
            topMenuCloseAnim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!isTopMenuOpen) {
                        topMenu.setVisibility(View.GONE);
                    }
                }
            });
            topMenuCloseAnim.start();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        configurePaletteOrientation(newConfig.orientation);
    }

    // -------------------------------------------------------------------------
    // Native View.OnTouchListener drag & drop + click editing
    // -------------------------------------------------------------------------

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (!(v instanceof BlockView)) {
            return false;
        }
        BlockView bv = (BlockView) v;
        int action = event.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                touchHandler.removeCallbacks(startDragRunnable);
                touchedBlock = bv;
                isDragging = false;
                initRawX = event.getRawX();
                initRawY = event.getRawY();
                lastRawX = initRawX;
                lastRawY = initRawY;
                if (bv.getBlockType() != 0 || (!bv.ea && bv != blockPane.getRoot())) {
                    touchHandler.postDelayed(startDragRunnable, longPressDelayMs);
                }
                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                lastRawX = event.getRawX();
                lastRawY = event.getRawY();
                if (!isDragging) {
                    if (Math.hypot(lastRawX - initRawX, lastRawY - initRawY) > touchSlop) {
                        touchHandler.removeCallbacks(startDragRunnable);
                        return false;
                    }
                    return true;
                }

                updateDragPosition(lastRawX, lastRawY);
                return true;
            }

            case MotionEvent.ACTION_UP: {
                touchHandler.removeCallbacks(startDragRunnable);
                lastRawX = event.getRawX();
                lastRawY = event.getRawY();
                if (!isDragging) {
                    handleBlockTap(bv, event.getX(), event.getY());
                    touchedBlock = null;
                    return true;
                }
                finishBlockDrag(lastRawX, lastRawY);
                return true;
            }

            case MotionEvent.ACTION_CANCEL: {
                touchHandler.removeCallbacks(startDragRunnable);
                if (isDragging) {
                    cancelBlockDrag();
                    return true;
                }
                touchedBlock = null;
                return false;
            }

            default:
                return false;
        }
    }

    private void startBlockDrag() {
        if (touchedBlock == null || touchedBlock == blockPane.getRoot() || touchedBlock.ea) {
            return;
        }
        isDragging = true;
        preDragSnapshot = captureSnapshot();

        editor.setScrollEnabled(false);
        paletteBlock.setDragEnabled(false);
        ViewParent parent = touchedBlock.getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true);
        }

        if (vibrator != null) {
            try {
                vibrator.vibrate(100L);
            } catch (Throwable ignored) {
            }
        }

        dummy.a(touchedBlock);
        dummy.a(touchedBlock, lastRawX, lastRawY, initRawX, initRawY, 0f, 0f);
        dummy.setDummyVisibility(View.VISIBLE);

        if (touchedBlock.getBlockType() == 1) {
            setPaletteOpen(false, true);
        } else {
            topMenu.toggleLayoutVisibility(false);
            topMenu.setFavoriteActive(false);
            topMenu.setDetailActive(false);
            topMenu.setDeleteActive(false);
            topMenu.setCopyActive(false);
            setTopMenuOpen(true);
            blockPane.a(touchedBlock, View.GONE);
        }

        blockPane.a(touchedBlock);
        updateDragPosition(lastRawX, lastRawY);
    }

    private void updateDragPosition(float rawX, float rawY) {
        if (touchedBlock == null) {
            return;
        }
        dummy.a(touchedBlock, rawX, rawY, initRawX, initRawY, 0f, 0f);
        dummy.a(dummyPos);

        boolean isWorkspaceBlock = touchedBlock.getBlockType() == 0;
        boolean overDelete = isTopMenuOpen && topMenu.isInsideDeleteArea(rawX, rawY);
        boolean overCopy = isWorkspaceBlock && isTopMenuOpen && topMenu.isInsideCopyArea(rawX, rawY);

        topMenu.setDeleteActive(overDelete);
        topMenu.setCopyActive(overCopy);

        if (overDelete || overCopy) {
            blockPane.d();
            dummy.setAllow(true);
            return;
        }

        if (editor.hitTest(rawX, rawY)) {
            dummy.setAllow(true);
            blockPane.c(touchedBlock, dummyPos[0], dummyPos[1]);
        } else {
            blockPane.d();
            dummy.setAllow(false);
        }
    }

    private void finishBlockDrag(float rawX, float rawY) {
        BlockView drag = touchedBlock;
        touchedBlock = null;
        isDragging = false;

        editor.setScrollEnabled(true);
        paletteBlock.setDragEnabled(true);
        dummy.setDummyVisibility(View.GONE);
        boolean allowDrop = dummy.getAllow();
        dummy.setAllow(false);

        boolean isWorkspaceBlock = drag != null && drag.getBlockType() == 0;
        boolean overDelete = isTopMenuOpen && topMenu.isInsideDeleteArea(rawX, rawY);
        boolean overCopy = isWorkspaceBlock && isTopMenuOpen && topMenu.isInsideCopyArea(rawX, rawY);

        topMenu.setDeleteActive(false);
        topMenu.setCopyActive(false);
        setTopMenuOpen(false);
        blockPane.d();

        if (drag == null) {
            preDragSnapshot = null;
            return;
        }

        if (overDelete) {
            if (isWorkspaceBlock) {
                BlockView oldParent = drag.E;
                blockPane.b(drag);
                if (oldParent != null) {
                    oldParent.p().k();
                }
                blockPane.b();
                recordHistoryCommit();
            } else {
                preDragSnapshot = null;
            }
            return;
        }

        if (overCopy && isWorkspaceBlock) {
            blockPane.a(drag, View.VISIBLE);
            duplicateSubtree(drag);
            blockPane.b();
            recordHistoryCommit();
            return;
        }

        if (!allowDrop) {
            if (isWorkspaceBlock) {
                blockPane.a(drag, View.VISIBLE);
            }
            preDragSnapshot = null;
            return;
        }

        dummy.a(dummyPos);

        if (drag.getBlockType() == 1) {
            BlockView dropped = blockPane.a(drag, dummyPos[0], dummyPos[1], false);
            applyDefaultArgs(dropped);
            dropped.setOnTouchListener(this);
            dropped.o();
            dropped.p().k();
            blockPane.b();
            recordHistoryCommit();
        } else {
            blockPane.a(drag, View.VISIBLE);
            BlockView oldParent = drag.E;
            if (oldParent != null) {
                blockPane.c(drag);
                oldParent.p().k();
            }
            blockPane.a(drag, dummyPos[0], dummyPos[1], true);
            recordHistoryCommit();
        }
    }

    private void cancelBlockDrag() {
        BlockView drag = touchedBlock;
        touchedBlock = null;
        isDragging = false;
        preDragSnapshot = null;
        editor.setScrollEnabled(true);
        paletteBlock.setDragEnabled(true);
        dummy.setDummyVisibility(View.GONE);
        dummy.setAllow(false);
        topMenu.setDeleteActive(false);
        topMenu.setCopyActive(false);
        setTopMenuOpen(false);
        blockPane.d();
        if (drag != null && drag.getBlockType() == 0) {
            blockPane.a(drag, View.VISIBLE);
        }
    }

    private void applyDefaultArgs(@NonNull BlockView bv) {
        if ("getVar".equals(bv.U)) {
            return;
        }
        GameBlockCatalog.BlockDef def = GameBlockCatalog.findByOpCode(bv.U);
        if (def == null || def.defaultParams == null || bv.V == null) {
            return;
        }
        for (int i = 0; i < def.defaultParams.length && i < bv.V.size(); i++) {
            View slot = bv.V.get(i);
            if (slot instanceof BlockMenuItem) {
                BlockMenuItem mi = (BlockMenuItem) slot;
                String defVal = def.defaultParams[i];
                if (!TextUtils.isEmpty(defVal)) {
                    mi.setArgValue(defVal);
                }
            }
        }
    }

    private void duplicateSubtree(@NonNull BlockView source) {
        @SuppressWarnings("unchecked")
        ArrayList<BlockView> children = source.getAllChildren();
        if (children == null || children.isEmpty()) {
            return;
        }
        List<BlockBean> sourceBeans = new ArrayList<>();
        Map<Integer, Integer> idRemap = new HashMap<>();
        for (BlockView child : children) {
            BlockBean b = child.getBean().clone();
            try {
                int oldId = Integer.parseInt(b.id);
                int newId = blockPane.g++;
                idRemap.put(oldId, newId);
                b.id = String.valueOf(newId);
            } catch (NumberFormatException ignored) {
            }
            sourceBeans.add(b);
        }

        for (BlockBean b : sourceBeans) {
            if (b.nextBlock >= 0 && idRemap.containsKey(b.nextBlock)) {
                b.nextBlock = idRemap.get(b.nextBlock);
            } else {
                b.nextBlock = -1;
            }
            if (b.subStack1 >= 0 && idRemap.containsKey(b.subStack1)) {
                b.subStack1 = idRemap.get(b.subStack1);
            } else {
                b.subStack1 = -1;
            }
            if (b.subStack2 >= 0 && idRemap.containsKey(b.subStack2)) {
                b.subStack2 = idRemap.get(b.subStack2);
            } else {
                b.subStack2 = -1;
            }
            if (b.parameters != null) {
                for (int i = 0; i < b.parameters.size(); i++) {
                    String p = b.parameters.get(i);
                    if (p != null && p.startsWith("@")) {
                        try {
                            int refId = Integer.parseInt(p.substring(1));
                            if (idRemap.containsKey(refId)) {
                                b.parameters.set(i, "@" + idRemap.get(refId));
                            } else {
                                b.parameters.set(i, "");
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        }

        Map<Integer, BlockView> created = new HashMap<>();
        BlockView clonedRoot = null;
        for (BlockBean b : sourceBeans) {
            int newId = Integer.parseInt(b.id);
            BlockView bv = new BlockView(
                    this,
                    newId,
                    b.spec != null ? b.spec : "",
                    b.type != null ? b.type : " ",
                    b.typeName != null ? b.typeName : "",
                    b.opCode != null ? b.opCode : "",
                    b.color);
            bv.pa = blockPane;
            bv.setOnTouchListener(this);
            blockPane.addView(bv);
            created.put(newId, bv);
            if (clonedRoot == null) {
                clonedRoot = bv;
            }
        }

        for (BlockBean b : sourceBeans) {
            BlockView parent = created.get(Integer.parseInt(b.id));
            if (parent == null) {
                continue;
            }
            if (b.parameters != null && parent.V != null) {
                for (int p = 0; p < b.parameters.size() && p < parent.V.size(); p++) {
                    String val = b.parameters.get(p);
                    View slot = parent.V.get(p);
                    if (val != null && val.startsWith("@")) {
                        BlockView child = created.get(Integer.parseInt(val.substring(1)));
                        if (child != null && slot instanceof BlockCanvasView) {
                            parent.a((BlockCanvasView) slot, child);
                        }
                    } else if (slot instanceof BlockMenuItem) {
                        ((BlockMenuItem) slot).setArgValue(val != null ? val : "");
                    }
                }
            }
            if (b.subStack1 >= 0 && created.containsKey(b.subStack1)) {
                parent.e(created.get(b.subStack1));
            }
            if (b.subStack2 >= 0 && created.containsKey(b.subStack2)) {
                parent.f(created.get(b.subStack2));
            }
            if (b.nextBlock >= 0 && created.containsKey(b.nextBlock)) {
                parent.b(created.get(b.nextBlock));
            }
        }

        if (clonedRoot != null) {
            for (BlockView bv : created.values()) {
                if (bv.fa) {
                    bv.o();
                }
            }
            for (BlockView bv : created.values()) {
                if (!bv.fa) {
                    bv.o();
                }
            }
            clonedRoot.setX(source.getX() + dp(24));
            clonedRoot.setY(source.getY() + dp(24));
            clonedRoot.k();
        }
    }

    // -------------------------------------------------------------------------
    // Tap to edit BlockMenuItem arguments (or Hat event param)
    // -------------------------------------------------------------------------

    private void handleBlockTap(@NonNull BlockView bv, float localX, float localY) {
        if (bv.getBlockType() != 0) {
            return;
        }
        if (bv == blockPane.getRoot() || bv.ea) {
            if (eventUsesParam(eventType)) {
                showEditEventParamDialog();
            }
            return;
        }
        if (bv.V == null) {
            return;
        }
        for (int i = 0; i < bv.V.size(); i++) {
            View slot = bv.V.get(i);
            if (slot instanceof BlockMenuItem) {
                if (slot.getX() <= localX
                        && slot.getX() + slot.getWidth() >= localX
                        && slot.getY() <= localY
                        && slot.getY() + slot.getHeight() >= localY) {
                    openParameterEditor(bv, i, (BlockMenuItem) slot);
                    return;
                }
            }
        }
    }

    private void openParameterEditor(@NonNull BlockView hostBlock, int slotIndex, @NonNull BlockMenuItem menuItem) {
        String slotType = menuItem.b != null ? menuItem.b : "s";
        String menuName = menuItem.c != null ? menuItem.c : "";
        String currentVal = menuItem.getArgValue() != null ? menuItem.getArgValue().toString() : "";

        if ("m".equals(slotType)) {
            if ("color".equals(menuName)) {
                showColorPicker(hostBlock, menuItem, currentVal);
                return;
            }
            showDropdownSelectorDialog(hostBlock, menuName, menuItem, currentVal);
            return;
        }

        if ("b".equals(slotType)) {
            String[] opts = new String[]{"true", "false"};
            int checked = "false".equalsIgnoreCase(currentVal.trim()) ? 1 : 0;
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Boolean value")
                    .setSingleChoiceItems(opts, checked, (dialog, which) -> {
                        applyMenuItemValue(hostBlock, menuItem, opts[which]);
                        dialog.dismiss();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        boolean numericMode = "d".equals(slotType);
        showTextOrExpressionDialog(hostBlock, slotIndex, menuItem, numericMode, currentVal);
    }

    private void applyMenuItemValue(@NonNull BlockView hostBlock,
                                    @NonNull BlockMenuItem menuItem,
                                    @NonNull String newValue) {
        String oldVal = menuItem.getArgValue() != null ? menuItem.getArgValue().toString() : "";
        if (TextUtils.equals(oldVal, newValue)) {
            return;
        }
        preDragSnapshot = captureSnapshot();
        menuItem.setArgValue(newValue);
        hostBlock.o();
        hostBlock.p().k();
        blockPane.b();
        recordHistoryCommit();
    }

    private void showColorPicker(@NonNull BlockView hostBlock,
                                 @NonNull BlockMenuItem menuItem,
                                 @NonNull String currentVal) {
        String seed = TextUtils.isEmpty(currentVal) ? "#FF5252" : currentVal.replace("0xFF", "#");
        ColorPickerDialog dialog = new ColorPickerDialog(this, seed, false, false, scId);
        dialog.a(new ColorPickerDialog.b() {
            @Override
            public void a(int color) {
                String hex = String.format(Locale.US, "#%06X", (0xFFFFFF & color));
                applyMenuItemValue(hostBlock, menuItem, hex);
            }

            @Override
            public void a(String colorName, int color) {
                String hex = String.format(Locale.US, "#%06X", (0xFFFFFF & color));
                applyMenuItemValue(hostBlock, menuItem, hex);
            }
        });
        dialog.showAtLocation(menuItem, Gravity.CENTER, 0, 0);
    }

    private void showTextOrExpressionDialog(@NonNull BlockView hostBlock,
                                            int slotIndex,
                                            @NonNull BlockMenuItem menuItem,
                                            boolean numericMode,
                                            @NonNull String currentVal) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(20), dp(12), dp(20), dp(4));

        EditText et = new EditText(this);
        et.setText(currentVal);
        et.setSelection(currentVal.length());
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        et.setHint(numericMode ? "Number or expression (e.g. 10, score + 1)" : "Text or {var}");
        container.addView(et);

        GameBlockCatalog.BlockDef def = GameBlockCatalog.findByOpCode(hostBlock.U);
        String kind = (def != null && def.paramKinds != null && slotIndex < def.paramKinds.length)
                ? def.paramKinds[slotIndex]
                : (numericMode ? GameBlockCatalog.KIND_NUMBER : GameBlockCatalog.KIND_TEXT);

        List<String> suggestions = buildExpressionSuggestions(kind);
        if (!suggestions.isEmpty()) {
            TextView header = new TextView(this);
            header.setText("Quick insert");
            header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            header.setPadding(0, dp(12), 0, dp(6));
            container.addView(header);

            HorizontalScrollView hsv = new HorizontalScrollView(this);
            hsv.setHorizontalScrollBarEnabled(false);
            ChipGroup cg = new ChipGroup(this);
            cg.setSingleLine(true);
            for (String sug : suggestions) {
                Chip chip = new Chip(this);
                chip.setText(sug);
                chip.setEnsureMinTouchTargetSize(false);
                chip.setOnClickListener(v -> {
                    et.setText(sug);
                    et.setSelection(sug.length());
                });
                cg.addView(chip);
            }
            hsv.addView(cg);
            container.addView(hsv);
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(numericMode ? "Enter number / expression" : "Enter value")
                .setView(container)
                .setPositiveButton("Save", (d, w) -> applyMenuItemValue(hostBlock, menuItem, et.getText().toString()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDropdownSelectorDialog(@NonNull BlockView hostBlock,
                                            @NonNull String menuName,
                                            @NonNull BlockMenuItem menuItem,
                                            @NonNull String currentVal) {
        List<String> options = buildDropdownOptions(hostBlock.U, menuName);
        if (options.isEmpty() || allowsCustomEntry(menuName)) {
            if ("gameVar".equals(menuName) && options.isEmpty()) {
                Toast.makeText(this, "No variables yet. Create one first.", Toast.LENGTH_SHORT).show();
                showAddVariableDialog();
                return;
            }
        }

        if (allowsCustomEntry(menuName)) {
            List<String> listWithCustom = new ArrayList<>(options);
            listWithCustom.add("+ Custom value...");
            String[] items = listWithCustom.toArray(new String[0]);
            int checked = findSelectedIndex(options, currentVal);
            new MaterialAlertDialogBuilder(this)
                    .setTitle(getMenuDialogTitle(menuName))
                    .setSingleChoiceItems(items, checked, (dialog, which) -> {
                        if (which == listWithCustom.size() - 1) {
                            dialog.dismiss();
                            showCustomDropdownInputDialog(hostBlock, menuName, menuItem, currentVal);
                        } else {
                            applyMenuItemValue(hostBlock, menuItem, items[which]);
                            dialog.dismiss();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        if (options.isEmpty()) {
            showCustomDropdownInputDialog(hostBlock, menuName, menuItem, currentVal);
            return;
        }

        String[] items = options.toArray(new String[0]);
        int checked = findSelectedIndex(options, currentVal);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(getMenuDialogTitle(menuName))
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    applyMenuItemValue(hostBlock, menuItem, items[which]);
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null);
        if ("gameVar".equals(menuName)) {
            builder.setNeutralButton("+ New Variable", (dialog, which) -> showAddVariableDialog());
        }
        builder.show();
    }

    private void showCustomDropdownInputDialog(@NonNull BlockView hostBlock,
                                               @NonNull String menuName,
                                               @NonNull BlockMenuItem menuItem,
                                               @NonNull String currentVal) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(20), dp(12), dp(20), dp(4));

        EditText et = new EditText(this);
        et.setText(currentVal);
        et.setSelection(currentVal.length());
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        container.addView(et);

        new MaterialAlertDialogBuilder(this)
                .setTitle(getMenuDialogTitle(menuName))
                .setView(container)
                .setPositiveButton("Save", (d, w) -> {
                    String typed = et.getText().toString().trim();
                    if (!typed.isEmpty()) {
                        applyMenuItemValue(hostBlock, menuItem, typed);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private List<String> buildExpressionSuggestions(@NonNull String kind) {
        Set<String> out = new LinkedHashSet<>();
        if (GameBlockCatalog.KIND_TEXT.equals(kind)) {
            for (String var : getSortedVariableNames()) {
                out.add("{" + var + "}");
            }
            out.addAll(Arrays.asList("Score: {score}", "HP: {hp}", "Game Over!", "Victory!"));
            return new ArrayList<>(out);
        }
        out.addAll(getSortedVariableNames());
        out.addAll(Arrays.asList("selfX", "selfY", "vx", "vy", "rot", "size", "joyX", "joyY", "touchX", "touchY", "timer"));
        return new ArrayList<>(out);
    }

    private List<String> buildDropdownOptions(@Nullable String opCode, @NonNull String menuName) {
        Set<String> out = new LinkedHashSet<>();
        switch (menuName) {
            case "gameObject":
                if ("goToTarget".equals(opCode)) {
                    out.add("random");
                    out.add("touch");
                } else if ("createClone".equals(opCode)) {
                    out.add("myself");
                } else if (!"cameraFollow".equals(opCode) && !"propOf".equals(opCode)) {
                    out.add("touch");
                    out.add("edge");
                }
                if (currentScene != null && currentScene.objects != null) {
                    for (GameObjectBean obj : currentScene.objects) {
                        if (obj != null && !TextUtils.isEmpty(obj.name)) {
                            out.add(obj.name);
                        }
                    }
                }
                break;

            case "sprite":
            case "backdrop":
                try {
                    List<String> imgs = GameProjectRepository.getProjectImageNames(scId);
                    if (imgs != null) {
                        out.addAll(imgs);
                    }
                } catch (Throwable ignored) {
                }
                break;

            case "sound":
                try {
                    List<String> snds = GameProjectRepository.getProjectSoundNames(scId);
                    if (snds != null) {
                        out.addAll(snds);
                    }
                } catch (Throwable ignored) {
                }
                out.addAll(Arrays.asList("jump", "coin", "hit", "laser", "explosion", "powerup", "bgm_arcade"));
                break;

            case "scene":
                if (gameProject != null && gameProject.scenes != null) {
                    for (GameScene s : gameProject.scenes) {
                        if (s != null && !TextUtils.isEmpty(s.name)) {
                            out.add(s.name);
                        }
                    }
                }
                break;

            case "hud":
                if (currentScene != null && currentScene.objects != null) {
                    for (GameObjectBean obj : currentScene.objects) {
                        if (obj != null && obj.isHud && !TextUtils.isEmpty(obj.name)) {
                            out.add(obj.name);
                        }
                    }
                }
                break;

            case "prefab":
                if (gameProject != null && gameProject.prefabs != null) {
                    for (GameObjectBean p : gameProject.prefabs) {
                        if (p != null && !TextUtils.isEmpty(p.name)) {
                            out.add(p.name);
                        }
                    }
                }
                if (currentScene != null && currentScene.objects != null) {
                    for (GameObjectBean obj : currentScene.objects) {
                        if (obj != null && !obj.isHud && !TextUtils.isEmpty(obj.name)) {
                            out.add(obj.name);
                        }
                    }
                }
                break;

            case "tag":
                if (currentScene != null && currentScene.objects != null) {
                    for (GameObjectBean obj : currentScene.objects) {
                        if (obj != null && !TextUtils.isEmpty(obj.tag)) {
                            out.add(obj.tag);
                        }
                    }
                }
                if (gameProject != null && gameProject.prefabs != null) {
                    for (GameObjectBean p : gameProject.prefabs) {
                        if (p != null && !TextUtils.isEmpty(p.tag)) {
                            out.add(p.tag);
                        }
                    }
                }
                out.addAll(Arrays.asList("coin", "enemy", "ground", "player", "wall"));
                break;

            case "action":
                if (currentScene != null && currentScene.objects != null) {
                    for (GameObjectBean obj : currentScene.objects) {
                        if (obj != null && obj.isHud) {
                            if (!TextUtils.isEmpty(obj.actionKey)) {
                                out.add(obj.actionKey);
                            } else if (!TextUtils.isEmpty(obj.name)) {
                                out.add(obj.name);
                            }
                        }
                    }
                }
                out.addAll(Arrays.asList("JUMP", "ATTACK", "FIRE", "DASH", "LEFT", "RIGHT", "UP", "DOWN"));
                break;

            case "gameVar":
                out.addAll(getSortedVariableNames());
                break;

            case "layer":
                out.addAll(Arrays.asList("front", "back"));
                break;

            case "prop":
                out.addAll(Arrays.asList("x", "y", "vx", "vy", "rotation", "size", "alpha", "costume"));
                break;

            case "mathfn":
                out.addAll(Arrays.asList(
                        "abs", "floor", "ceiling", "sqrt",
                        "sin", "cos", "tan",
                        "asin", "acos", "atan",
                        "ln", "log", "e ^", "10 ^"));
                break;

            case "message":
                if (gameProject != null && gameProject.scenes != null) {
                    for (GameScene s : gameProject.scenes) {
                        if (s != null && s.scripts != null) {
                            for (GameScriptNode node : s.scripts) {
                                collectMessagesFromScriptNode(node, out);
                            }
                        }
                    }
                }
                out.addAll(Arrays.asList("gameover", "victory", "start", "level_clear"));
                break;

            case "bool":
                out.addAll(Arrays.asList("true", "false"));
                break;

            case "transition":
                out.addAll(Arrays.asList("fade", "flash", "instant"));
                break;

            default:
                break;
        }
        return new ArrayList<>(out);
    }

    private static boolean allowsCustomEntry(@NonNull String menuName) {
        return "tag".equals(menuName) || "message".equals(menuName) || "action".equals(menuName);
    }

    private static String getMenuDialogTitle(@NonNull String menuName) {
        switch (menuName) {
            case "gameObject": return "Select object";
            case "sprite": return "Select costume";
            case "backdrop": return "Select backdrop";
            case "sound": return "Select sound";
            case "scene": return "Select scene";
            case "hud": return "Select HUD element";
            case "prefab": return "Select prefab / object";
            case "tag": return "Select collision tag";
            case "action": return "Select button key";
            case "gameVar": return "Select variable";
            case "layer": return "Select layer";
            case "prop": return "Select property";
            case "mathfn": return "Select math function";
            case "message": return "Select broadcast message";
            case "bool": return "Select value";
            case "transition": return "Select transition";
            case "color": return "Select color";
            default: return "Select";
        }
    }

    private static int findSelectedIndex(@NonNull List<String> options, @NonNull String currentVal) {
        for (int i = 0; i < options.size(); i++) {
            if (TextUtils.equals(options.get(i), currentVal)) {
                return i;
            }
        }
        return -1;
    }

    private static void collectMessagesFromScriptNode(@Nullable GameScriptNode node, @NonNull Set<String> out) {
        if (node == null) {
            return;
        }
        if (GameScriptNode.EVENT_MESSAGE.equals(node.eventType) && !TextUtils.isEmpty(node.eventParam)) {
            out.add(node.eventParam);
        }
        collectMessagesFromBlockList(node.blocks, out);
    }

    private static void collectMessagesFromBlockList(@Nullable List<GameBlockItem> blocks, @NonNull Set<String> out) {
        if (blocks == null) {
            return;
        }
        for (GameBlockItem b : blocks) {
            if (b == null) {
                continue;
            }
            if (("sendMessage".equals(b.opCode) || "sendMessageWait".equals(b.opCode))
                    && b.parameters != null && !b.parameters.isEmpty()) {
                String msg = b.parameters.get(0);
                if (!TextUtils.isEmpty(msg)) {
                    out.add(msg);
                }
            }
            if (b.paramBlocks != null) {
                collectMessagesFromBlockList(b.paramBlocks, out);
            }
            collectMessagesFromBlockList(b.subStack1, out);
            collectMessagesFromBlockList(b.subStack2, out);
        }
    }

    // -------------------------------------------------------------------------
    // Variable Management
    // -------------------------------------------------------------------------

    private List<String> getSortedVariableNames() {
        List<String> names = new ArrayList<>();
        if (gameProject != null && gameProject.globalVariables != null) {
            names.addAll(gameProject.globalVariables.keySet());
        }
        Collections.sort(names);
        return names;
    }

    private void showAddVariableDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(20), dp(12), dp(20), dp(4));

        EditText nameEt = new EditText(this);
        nameEt.setHint("Variable name (e.g. score)");
        nameEt.setSingleLine(true);
        nameEt.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        container.addView(nameEt);

        EditText valueEt = new EditText(this);
        valueEt.setHint("Initial value (default 0)");
        valueEt.setText("0");
        valueEt.setSingleLine(true);
        container.addView(valueEt);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Add Variable")
                .setView(container)
                .setPositiveButton("Add", (d, w) -> {
                    String name = nameEt.getText().toString().trim();
                    if (TextUtils.isEmpty(name)) {
                        Toast.makeText(this, "Enter a variable name.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                        Toast.makeText(this, "Use letters, numbers, or underscore.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (gameProject.globalVariables == null) {
                        gameProject.globalVariables = new HashMap<>();
                    }
                    String initVal = valueEt.getText().toString().trim();
                    gameProject.globalVariables.put(name, TextUtils.isEmpty(initVal) ? "0" : initVal);
                    hasUnsavedChanges = true;
                    saveProjectSafely();
                    showCategory(selectedCategoryIndex);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showRemoveVariableDialog() {
        List<String> names = getSortedVariableNames();
        if (names.isEmpty()) {
            Toast.makeText(this, "No variables to remove.", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] items = names.toArray(new String[0]);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Remove Variable")
                .setItems(items, (d, which) -> {
                    if (gameProject != null && gameProject.globalVariables != null) {
                        gameProject.globalVariables.remove(items[which]);
                        hasUnsavedChanges = true;
                        saveProjectSafely();
                        showCategory(selectedCategoryIndex);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditEventParamDialog() {
        if (!eventUsesParam(eventType)) {
            return;
        }
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(20), dp(12), dp(20), dp(4));

        EditText et = new EditText(this);
        et.setText(eventParam != null ? eventParam : "");
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        container.addView(et);

        String title;
        if (GameScriptNode.EVENT_COLLISION.equals(eventType)) {
            title = "Colliding Tag";
        } else if (GameScriptNode.EVENT_BUTTON.equals(eventType)) {
            title = "HUD Button Key";
        } else {
            title = "Broadcast Message";
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setView(container)
                .setPositiveButton("Save", (d, w) -> {
                    String next = et.getText().toString().trim();
                    if (!TextUtils.isEmpty(next)) {
                        eventParam = next;
                        if (currentScript != null) {
                            currentScript.eventParam = next;
                        }
                        if (blockPane.getRoot() != null) {
                            blockPane.getRoot().setSpec(buildHatTitle());
                            blockPane.getRoot().k();
                        }
                        toolbar.setSubtitle(buildDefaultSubtitle(eventType, eventParam));
                        hasUnsavedChanges = true;
                        saveProjectSafely();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // -------------------------------------------------------------------------
    // Snapshot-based Undo / Redo for native BlockPane (supports floating stacks,
    // subStack1, subStack2, and nested @id reporters cleanly)
    // -------------------------------------------------------------------------

    private static final class FloatingStackSnapshot {
        int x;
        int y;
        int topId;
        final List<BlockBean> beans = new ArrayList<>();
    }

    private static final class PaneSnapshot {
        int rootNextId = -1;
        final List<BlockBean> rootBeans = new ArrayList<>();
        final List<FloatingStackSnapshot> floating = new ArrayList<>();
    }

    private PaneSnapshot captureSnapshot() {
        PaneSnapshot snap = new PaneSnapshot();
        BlockView root = blockPane.getRoot();
        snap.rootNextId = root != null ? root.ha : -1;

        @SuppressWarnings("unchecked")
        ArrayList<BlockBean> rootList = blockPane.getBlocks();
        if (rootList != null) {
            for (BlockBean b : rootList) {
                snap.rootBeans.add(b.clone());
            }
        }

        for (int i = 0; i < blockPane.getChildCount(); i++) {
            View child = blockPane.getChildAt(i);
            if (!(child instanceof BlockView)) {
                continue;
            }
            BlockView bv = (BlockView) child;
            if (bv == root || bv.ea || bv.E != null) {
                continue;
            }
            FloatingStackSnapshot f = new FloatingStackSnapshot();
            f.x = Math.round(bv.getX());
            f.y = Math.round(bv.getY());
            try {
                f.topId = Integer.parseInt(bv.getTag().toString());
            } catch (Exception e) {
                continue;
            }
            @SuppressWarnings("unchecked")
            ArrayList<BlockView> sub = bv.getAllChildren();
            if (sub != null) {
                for (BlockView sb : sub) {
                    f.beans.add(sb.getBean().clone());
                }
            }
            if (!f.beans.isEmpty()) {
                snap.floating.add(f);
            }
        }
        return snap;
    }

    private void recordHistoryCommit() {
        if (preDragSnapshot != null) {
            undoStack.push(preDragSnapshot);
            while (undoStack.size() > MAX_UNDO_HISTORY) {
                undoStack.removeLast();
            }
            redoStack.clear();
            preDragSnapshot = null;
        }
        syncCurrentScriptFromPane();
        hasUnsavedChanges = true;
        saveProjectSafely();
        invalidateOptionsMenu();
    }

    private void performUndo() {
        if (undoStack.isEmpty()) {
            return;
        }
        redoStack.push(captureSnapshot());
        PaneSnapshot target = undoStack.pop();
        restoreSnapshot(target);
        syncCurrentScriptFromPane();
        hasUnsavedChanges = true;
        saveProjectSafely();
        invalidateOptionsMenu();
    }

    private void performRedo() {
        if (redoStack.isEmpty()) {
            return;
        }
        undoStack.push(captureSnapshot());
        PaneSnapshot target = redoStack.pop();
        restoreSnapshot(target);
        syncCurrentScriptFromPane();
        hasUnsavedChanges = true;
        saveProjectSafely();
        invalidateOptionsMenu();
    }

    private void restoreSnapshot(@NonNull PaneSnapshot snap) {
        BlockView root = blockPane.getRoot();
        for (int i = blockPane.getChildCount() - 1; i >= 0; i--) {
            View child = blockPane.getChildAt(i);
            if (child instanceof BlockView && child != root) {
                blockPane.removeViewAt(i);
            }
        }
        if (root != null) {
            root.ha = -1;
        }

        // Restore root chain
        GameBlockBeanConverter.populatePane(this, blockPane, snap.rootBeans, snap.rootNextId, this);

        // Restore each floating stack
        for (FloatingStackSnapshot f : snap.floating) {
            restoreFloatingStack(f);
        }
        if (root != null) {
            root.k();
        }
        blockPane.b();
    }

    private void restoreFloatingStack(@NonNull FloatingStackSnapshot f) {
        Map<Integer, BlockView> created = new HashMap<>();
        int maxId = blockPane.g;
        for (BlockBean b : f.beans) {
            int id = Integer.parseInt(b.id);
            maxId = Math.max(maxId, id + 1);
            BlockView bv = new BlockView(
                    this,
                    id,
                    b.spec != null ? b.spec : "",
                    b.type != null ? b.type : " ",
                    b.typeName != null ? b.typeName : "",
                    b.opCode != null ? b.opCode : "",
                    b.color != 0 ? b.color : GameBlockBeanConverter.resolveHarmonizedColor(this, b.opCode));
            bv.pa = blockPane;
            bv.setOnTouchListener(this);
            blockPane.addView(bv);
            created.put(id, bv);
        }
        blockPane.g = maxId;

        for (BlockBean b : f.beans) {
            BlockView parent = created.get(Integer.parseInt(b.id));
            if (parent == null) {
                continue;
            }
            if (b.parameters != null && parent.V != null) {
                for (int p = 0; p < b.parameters.size() && p < parent.V.size(); p++) {
                    String val = b.parameters.get(p);
                    View slot = parent.V.get(p);
                    if (val != null && val.startsWith("@")) {
                        BlockView child = created.get(Integer.parseInt(val.substring(1)));
                        if (child != null && slot instanceof BlockCanvasView) {
                            parent.a((BlockCanvasView) slot, child);
                        }
                    } else if (slot instanceof BlockMenuItem) {
                        ((BlockMenuItem) slot).setArgValue(val != null ? val : "");
                    }
                }
            }
            if (b.subStack1 >= 0 && created.containsKey(b.subStack1)) {
                parent.e(created.get(b.subStack1));
            }
            if (b.subStack2 >= 0 && created.containsKey(b.subStack2)) {
                parent.f(created.get(b.subStack2));
            }
            if (b.nextBlock >= 0 && created.containsKey(b.nextBlock)) {
                parent.b(created.get(b.nextBlock));
            }
        }

        BlockView top = created.get(f.topId);
        if (top != null) {
            for (BlockView bv : created.values()) {
                if (bv.fa) {
                    bv.o();
                }
            }
            for (BlockView bv : created.values()) {
                if (!bv.fa) {
                    bv.o();
                }
            }
            top.setX(f.x);
            top.setY(f.y);
            top.k();
        }
    }

    private void syncCurrentScriptFromPane() {
        if (currentScript == null || blockPane == null) {
            return;
        }
        currentScript.blocks = GameBlockBeanConverter.fromBlockPane(blockPane);
    }

    // -------------------------------------------------------------------------
    // Menus, Preview, Helper Dialog & Persistence
    // -------------------------------------------------------------------------

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isPaletteOpen) {
                    setPaletteOpen(false, true);
                    return;
                }
                syncCurrentScriptFromPane();
                saveProjectSafely();
                setResult(RESULT_OK);
                finish();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.game_logic_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        applyMenuItemEnabled(menu.findItem(R.id.menu_logic_undo), !undoStack.isEmpty());
        applyMenuItemEnabled(menu.findItem(R.id.menu_logic_redo), !redoStack.isEmpty());
        return super.onPrepareOptionsMenu(menu);
    }

    private static void applyMenuItemEnabled(@Nullable MenuItem item, boolean enabled) {
        if (item == null) {
            return;
        }
        item.setEnabled(enabled);
        Drawable icon = item.getIcon();
        if (icon != null) {
            Drawable mutated = icon.mutate();
            mutated.setAlpha(enabled ? 255 : 90);
            item.setIcon(mutated);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_logic_preview) {
            launchPlayPreview();
            return true;
        } else if (id == R.id.menu_logic_undo) {
            performUndo();
            return true;
        } else if (id == R.id.menu_logic_redo) {
            performRedo();
            return true;
        } else if (id == R.id.menu_block_helper) {
            showBlockHelperDialog();
            return true;
        } else if (id == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void launchPlayPreview() {
        syncCurrentScriptFromPane();
        saveProjectSafely();
        Intent intent = new Intent(getApplicationContext(), GamePreviewActivity.class);
        intent.putExtra(GamePreviewActivity.EXTRA_SC_ID, scId);
        if (!TextUtils.isEmpty(sceneId)) {
            intent.putExtra(GamePreviewActivity.EXTRA_SCENE_ID, sceneId);
        }
        startActivity(intent);
    }

    private void showBlockHelperDialog() {
        syncCurrentScriptFromPane();
        List<GameBlockItem> blocks = currentScript != null ? currentScript.blocks : Collections.emptyList();
        StringBuilder sb = new StringBuilder();
        sb.append(buildHatTitle()).append("\n");
        if (blocks.isEmpty()) {
            sb.append("  (No connected blocks yet)");
        } else {
            appendReadableBlocks(sb, blocks, "  ");
        }
        String text = sb.toString();

        ScrollView scroll = new ScrollView(this);
        TextView tv = new TextView(this);
        tv.setPadding(dp(20), dp(14), dp(20), dp(14));
        tv.setTextIsSelectable(true);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setText(text);
        scroll.addView(tv);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Event Script Summary")
                .setView(scroll)
                .setNeutralButton("Copy", (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(ClipData.newPlainText("Game Script", text));
                        Toast.makeText(this, "Copied to clipboard.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setPositiveButton("Close", null)
                .show();
    }

    private static void appendReadableBlocks(@NonNull StringBuilder sb,
                                             @Nullable List<GameBlockItem> blocks,
                                             @NonNull String indent) {
        if (blocks == null) {
            return;
        }
        for (GameBlockItem b : blocks) {
            if (b == null) {
                continue;
            }
            sb.append(indent).append("• ").append(GameBlockCatalog.formatBlockDisplay(b)).append("\n");
            if (b.subStack1 != null && !b.subStack1.isEmpty()) {
                appendReadableBlocks(sb, b.subStack1, indent + "    ");
            }
            if (b.subStack2 != null && !b.subStack2.isEmpty()) {
                sb.append(indent).append("  else:\n");
                appendReadableBlocks(sb, b.subStack2, indent + "    ");
            }
        }
    }

    private void saveProjectSafely() {
        if (gameProject == null || TextUtils.isEmpty(scId)) {
            return;
        }
        if (currentScene != null && currentScene.scripts == null) {
            currentScene.scripts = new ArrayList<>();
        }
        GameProjectRepository.save(scId, gameProject);
        if (hasUnsavedChanges) {
            setResult(RESULT_OK);
        }
    }

    @Override
    public void onPause() {
        syncCurrentScriptFromPane();
        saveProjectSafely();
        super.onPause();
    }

    @Override
    public void finish() {
        syncCurrentScriptFromPane();
        saveProjectSafely();
        super.finish();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        syncCurrentScriptFromPane();
        outState.putString(EXTRA_SC_ID, scId);
        outState.putString(EXTRA_SCENE_ID, sceneId);
        outState.putString(EXTRA_TARGET_OBJECT_ID, targetObjectId);
        outState.putString(EXTRA_EVENT_TYPE, eventType);
        outState.putString(EXTRA_EVENT_PARAM, eventParam);
        outState.putString(EXTRA_EVENT_TITLE, eventTitle);
        outState.putString(EXTRA_EVENT_SUBTITLE, eventSubtitle);
        outState.putBoolean("has_unsaved", hasUnsavedChanges);
        saveProjectSafely();
    }

    private String buildHatTitle() {
        String targetName = targetObject != null && !TextUtils.isEmpty(targetObject.name)
                ? targetObject.name
                : "Current Scene";
        switch (safeString(eventType, GameScriptNode.EVENT_SCENE_START)) {
            case GameScriptNode.EVENT_SCENE_START:
                return GameScriptNode.SCENE_TARGET_ID.equals(targetObjectId)
                        ? "When Scene Starts"
                        : "When " + targetName + " Starts";
            case GameScriptNode.EVENT_UPDATE:
                return GameScriptNode.SCENE_TARGET_ID.equals(targetObjectId)
                        ? "Every Frame (Scene)"
                        : "Every Frame (" + targetName + ")";
            case GameScriptNode.EVENT_TOUCH:
                return "When " + targetName + " Touched";
            case GameScriptNode.EVENT_COLLISION:
                return TextUtils.isEmpty(eventParam)
                        ? "When " + targetName + " Collides"
                        : "When " + targetName + " Collides (" + eventParam + ")";
            case GameScriptNode.EVENT_BUTTON:
                return TextUtils.isEmpty(eventParam)
                        ? "When Button Pressed"
                        : "When Button (" + eventParam + ") Pressed";
            case GameScriptNode.EVENT_MESSAGE:
                return TextUtils.isEmpty(eventParam)
                        ? "When Message Received"
                        : "When Message (" + eventParam + ") Received";
            default:
                return "When " + eventType;
        }
    }

    private static boolean eventUsesParam(@Nullable String ev) {
        return GameScriptNode.EVENT_COLLISION.equals(ev)
                || GameScriptNode.EVENT_BUTTON.equals(ev)
                || GameScriptNode.EVENT_MESSAGE.equals(ev);
    }

    private static String defaultEventParam(@Nullable String ev) {
        if (GameScriptNode.EVENT_COLLISION.equals(ev)) {
            return "coin";
        }
        if (GameScriptNode.EVENT_BUTTON.equals(ev)) {
            return "JUMP";
        }
        if (GameScriptNode.EVENT_MESSAGE.equals(ev)) {
            return "gameover";
        }
        return "";
    }

    private static String buildDefaultSubtitle(String eventType, @Nullable String param) {
        String label;
        switch (safeString(eventType, GameScriptNode.EVENT_SCENE_START)) {
            case GameScriptNode.EVENT_SCENE_START:
                label = "On Start";
                break;
            case GameScriptNode.EVENT_UPDATE:
                label = "Every Frame (Update)";
                break;
            case GameScriptNode.EVENT_TOUCH:
                label = "On Touch";
                break;
            case GameScriptNode.EVENT_COLLISION:
                label = "On Collision";
                break;
            case GameScriptNode.EVENT_BUTTON:
                label = "HUD Button";
                break;
            case GameScriptNode.EVENT_MESSAGE:
                label = "Message";
                break;
            default:
                label = eventType;
                break;
        }
        if (!TextUtils.isEmpty(param)) {
            return label + " (" + param + ")";
        }
        return label;
    }

    private static String safeString(@Nullable String val, String fallback) {
        return !TextUtils.isEmpty(val) ? val : fallback;
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }
}
