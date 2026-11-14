package pro.sketchware.activities.game.blocks;

import android.animation.ObjectAnimator;
import android.content.ClipData;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import com.besome.sketch.lib.ui.CustomHorizontalScrollView;
import com.besome.sketch.lib.ui.CustomScrollView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.besome.sketch.editor.logic.LogicTopMenu;
import com.besome.sketch.editor.view.ViewDummy;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import pro.sketchware.R;
import pro.sketchware.activities.game.blocks.GameBlockCatalog.BlockDef;
import pro.sketchware.activities.game.blocks.GameBlockCatalog.CategoryDef;
import pro.sketchware.activities.game.model.GameBlockItem;
import pro.sketchware.activities.game.model.GameObjectBean;
import pro.sketchware.activities.game.model.GameProject;
import pro.sketchware.activities.game.model.GameProjectRepository;
import pro.sketchware.activities.game.model.GameScene;
import pro.sketchware.activities.game.model.GameScriptNode;
import pro.sketchware.editor.BlockCanvasView;
import pro.sketchware.editor.BlockMenuItem;
import pro.sketchware.utility.ThemeUtils;

/**
 * Scratch-style drag-and-drop visual block coding editor for the 2D Game Engine.
 * Mirrors native Sketchware LogicEditorActivity + palette_block.xml + palette_selector_item.xml,
 * rendering blocks using {@link BlockCanvasView} + {@link BlockMenuItem}, dragging via {@link ViewDummy}
 * + {@link LogicTopMenu}, and editing {@link GameScriptNode} stacks in real time.
 */
public class GameBlocksEditorView extends FrameLayout {

    private static final int COLOR_HAT = 0xFFC88330;
    private static final int MAX_UNDO_HISTORY = 50;
    private static final int PALETTE_HEIGHT_DP = 240;
    private static final int PALETTE_WIDTH_LANDSCAPE_DP = 320;
    private static final int CATEGORY_RAIL_WIDTH_DP = 120;

    private static final Gson HISTORY_GSON = new Gson();
    private static final Type BLOCK_LIST_TYPE = new TypeToken<ArrayList<GameBlockItem>>() {}.getType();

    private static final class ScriptSnapshot {
        ArrayList<GameBlockItem> blocks = new ArrayList<>();
        ArrayList<ArrayList<GameBlockItem>> floatingStacks = new ArrayList<>();
    }

    private static final String[] GAME_COLOR_SWATCHES = {
            "#FFFFFF", "#1E293B", "#EF4444", "#F97316", "#F59E0B", "#EAB308",
            "#22C55E", "#10B981", "#06B6D4", "#3B82F6", "#6366F1", "#8B5CF6",
            "#EC4899", "#F43F5E", "#FFD54F", "#FF5252", "#94A3B8", "#475569"
    };

    private static final class ScriptHistory {
        final ArrayDeque<String> undoStack = new ArrayDeque<>();
        final ArrayDeque<String> redoStack = new ArrayDeque<>();
        String currentSnapshot = "[]";
    }

    private String scId = "";
    private GameProject project;
    private GameScene scene;
    private Runnable onScriptModified;
    private Runnable undoHistoryChangedListener;

    private String currentTargetId = GameScriptNode.SCENE_TARGET_ID;
    private String currentEventType = GameScriptNode.EVENT_SCENE_START;
    private String currentEventParam = "";
    private GameScriptNode currentScript;

    private final List<String> cachedImageNames = new ArrayList<>();
    private final List<String> cachedSoundNames = new ArrayList<>();
    private final List<String> cachedFontNames = new ArrayList<>();
    private final Map<String, ScriptHistory> historyByScriptKey = new HashMap<>();
    private boolean isRestoringHistory = false;

    private String selectedCategoryId = "motion";
    private boolean singleEventMode = false;
    private boolean paletteOpen = true;
    private boolean paletteTempHiddenForDrag = false;
    private int appliedOrientation = Configuration.ORIENTATION_UNDEFINED;

    // Theme-aware colors
    private boolean darkTheme;
    private int colorSurface;
    private int colorSurfaceContainer;
    private int colorSurfaceContainerHigh;
    private int colorSurfaceContainerHighest;
    private int colorSurfaceAlt;
    private int colorBorder;
    private int colorTextPrimary;
    private int colorTextSecondary;
    private int colorOnSurface;
    private int colorOnSurfaceInverse;
    private int colorAccent;

    // UI references
    private HorizontalScrollView toolbarScroll;
    private View topDivider;
    private TextView btnTogglePalette;
    private TextView chipTarget;
    private TextView chipEvent;
    private TextView chipEventParam;
    private TextView btnAddQuick;
    private TextView btnClearScript;

    private FrameLayout contentArea;
    private LinearLayout workspaceContainer;

    // Native LogicEditorActivity-style palette container (FAB + area_palette)
    private LinearLayout layoutPalette;
    private FloatingActionButton fabTogglePalette;
    private LinearLayout areaPalette;
    private LinearLayout actionsContainer;
    private ScrollView paletteBlocksVScroll;
    private HorizontalScrollView paletteBlocksHScroll;
    private LinearLayout paletteBlocksLayout;
    private TextView paletteSearchHeader;
    private LinearLayout categoryRailLayout;
    private ObjectAnimator paletteAnimator;

    private CustomScrollView workspaceVScroll;
    private CustomHorizontalScrollView workspaceHScroll;
    private RelativeLayout workspaceCanvas;
    private LinearLayout mainChainContainer;
    private GameBlockView hatBlockView;
    private BlockStackLayout rootStackLayout;
    private final List<BlockStackLayout> floatingStackLayouts = new ArrayList<>();
    private TextView emptyWorkspaceHint;

    // Native LogicTopMenu + ViewDummy drag state
    private LogicTopMenu logicTopMenu;
    private ViewDummy viewDummy;
    private Vibrator vibrator;
    private View activeDragSourceView;
    private DragPayload activeDragPayload;
    private float dragInitRawX;
    private float dragInitRawY;
    private ObjectAnimator topDeleteAnimator;

    public GameBlocksEditorView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public GameBlocksEditorView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public GameBlocksEditorView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        resolveThemeColors(context);
        buildLayout(context);
        applyPaletteOrientation(getResources().getConfiguration().orientation, false);
        renderCategories();
        renderPaletteBlocks();
        renderWorkspace();
    }

    private void resolveThemeColors(Context context) {
        darkTheme = ThemeUtils.isDarkThemeEnabled(context);
        colorAccent = MaterialColors.getColor(
                context, androidx.appcompat.R.attr.colorPrimary, 0xFF4C97FF);
        colorOnSurface = MaterialColors.getColor(
                context, R.attr.colorOnSurface, darkTheme ? 0xFFF1F5F9 : 0xFF0F172A);
        colorOnSurfaceInverse = MaterialColors.getColor(
                context, R.attr.colorOnSurfaceInverse, darkTheme ? 0xFF0F172A : 0xFFFFFFFF);
        colorSurface = MaterialColors.getColor(
                context, R.attr.colorSurface, darkTheme ? 0xFF1A1F2B : 0xFFFFFFFF);
        colorSurfaceContainer = MaterialColors.getColor(
                context, R.attr.colorSurfaceContainer, darkTheme ? 0xFF1E2433 : 0xFFF3F5FA);
        colorSurfaceContainerHigh = MaterialColors.getColor(
                context, R.attr.colorSurfaceContainerHigh, darkTheme ? 0xFF262D3E : 0xFFEAEEF5);
        colorSurfaceContainerHighest = MaterialColors.getColor(
                context, R.attr.colorSurfaceContainerHighest, darkTheme ? 0xFF2F374A : 0xFFE2E8F0);
        if (darkTheme) {
            colorSurfaceAlt = 0xFF131722;
            colorBorder = 0xFF2D3648;
            colorTextPrimary = 0xFFF1F5F9;
            colorTextSecondary = 0xFF94A3B8;
        } else {
            colorSurfaceAlt = 0xFFF1F5F9;
            colorBorder = 0xFFE2E8F0;
            colorTextPrimary = 0xFF0F172A;
            colorTextSecondary = 0xFF64748B;
        }
    }

    private int harmonizeColor(int color) {
        return MaterialColors.harmonizeWithPrimary(getContext(), color);
    }

    private void buildLayout(Context context) {
        LinearLayout rootColumn = new LinearLayout(context);
        rootColumn.setOrientation(LinearLayout.VERTICAL);
        rootColumn.setLayoutParams(new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(rootColumn);

        // --- Optional Top Chip Toolbar (hidden in singleEventMode for pure LogicEditor parity) ---
        toolbarScroll = new HorizontalScrollView(context);
        toolbarScroll.setHorizontalScrollBarEnabled(false);
        toolbarScroll.setFillViewport(true);
        toolbarScroll.setBackgroundColor(colorSurface);
        toolbarScroll.setLayoutParams(new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        rootColumn.addView(toolbarScroll);

        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(8), dp(6), dp(8), dp(6));
        toolbarScroll.addView(toolbar);

        btnTogglePalette = createToolbarChip(context, "Blocks", colorAccent, true);
        btnTogglePalette.setOnClickListener(v -> togglePalette());
        toolbar.addView(btnTogglePalette);

        addToolbarSpacer(toolbar, dp(6));

        chipTarget = createToolbarChip(context, "Target: Scene", colorSurfaceAlt, false);
        chipTarget.setOnClickListener(v -> showTargetSelectorDialog());
        toolbar.addView(chipTarget);

        addToolbarSpacer(toolbar, dp(6));

        chipEvent = createToolbarChip(context, "Event: On Start", colorSurfaceAlt, false);
        chipEvent.setOnClickListener(v -> showEventSelectorDialog());
        toolbar.addView(chipEvent);

        addToolbarSpacer(toolbar, dp(6));

        chipEventParam = createToolbarChip(context, "", 0x33E1A91A, false);
        chipEventParam.setVisibility(GONE);
        chipEventParam.setOnClickListener(v -> showEditEventParamDialog());
        toolbar.addView(chipEventParam);

        addToolbarSpacer(toolbar, dp(8));

        btnAddQuick = createToolbarChip(context, "+ Block", colorSurfaceAlt, false);
        btnAddQuick.setOnClickListener(v -> showQuickAddBlockDialog());
        toolbar.addView(btnAddQuick);

        addToolbarSpacer(toolbar, dp(6));

        btnClearScript = createToolbarChip(context, "Clear", colorSurfaceAlt, false);
        btnClearScript.setOnClickListener(v -> confirmClearCurrentScript());
        toolbar.addView(btnClearScript);

        topDivider = new View(context);
        topDivider.setBackgroundColor(colorBorder);
        topDivider.setLayoutParams(new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));
        rootColumn.addView(topDivider);

        // --- Content Area (Plain Surface Workspace + Bottom/Right FAB Palette + LogicTopMenu + ViewDummy) ---
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

        contentArea = new FrameLayout(context);
        contentArea.setClipChildren(false);
        contentArea.setLayoutParams(new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, 0, 1f));
        rootColumn.addView(contentArea);

        // Fullscreen workspace container (matches logic_editor.xml 4dp margin & plain surface)
        workspaceContainer = new LinearLayout(context);
        workspaceContainer.setOrientation(LinearLayout.VERTICAL);
        workspaceContainer.setBackgroundColor(colorSurface);
        if (Build.VERSION.SDK_INT >= 29) {
            workspaceContainer.setForceDarkAllowed(false);
        }
        workspaceContainer.setClipChildren(false);
        FrameLayout.LayoutParams wsLp = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        wsLp.setMargins(dp(4), dp(4), dp(4), dp(4));
        workspaceContainer.setLayoutParams(wsLp);
        contentArea.addView(workspaceContainer);

        FrameLayout workspaceFrame = new FrameLayout(context);
        workspaceFrame.setClipChildren(false);
        workspaceFrame.setLayoutParams(new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        workspaceContainer.addView(workspaceFrame);

        workspaceVScroll = new CustomScrollView(context);
        workspaceVScroll.setFillViewport(true);
        workspaceVScroll.setClipToPadding(false);
        workspaceVScroll.setLayoutParams(new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        workspaceFrame.addView(workspaceVScroll);

        workspaceHScroll = new CustomHorizontalScrollView(context);
        workspaceHScroll.setFillViewport(true);
        workspaceHScroll.setClipToPadding(false);
        workspaceVScroll.addView(workspaceHScroll);

        workspaceCanvas = new RelativeLayout(context);
        workspaceCanvas.setClipChildren(false);
        workspaceCanvas.setClipToPadding(false);
        workspaceCanvas.setMinimumWidth(dp(2000));
        workspaceCanvas.setMinimumHeight(dp(1600));
        workspaceCanvas.setPadding(dp(8), dp(8), dp(120), dp(160));
        workspaceHScroll.addView(workspaceCanvas);

        mainChainContainer = new LinearLayout(context);
        mainChainContainer.setOrientation(LinearLayout.VERTICAL);
        mainChainContainer.setClipChildren(false);
        mainChainContainer.setClipToPadding(false);
        RelativeLayout.LayoutParams mainLp = new RelativeLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        mainLp.leftMargin = dp(8);
        mainLp.topMargin = dp(8);
        workspaceCanvas.addView(mainChainContainer, mainLp);

        // Hat block at top (8dp, 8dp matching BlockPane.addRoot)
        hatBlockView = new GameBlockView(context, "h", harmonizeColor(COLOR_HAT));
        hatBlockView.setOnDragListener(new HatDropListener());
        mainChainContainer.addView(hatBlockView);

        // Root script stack directly interlocked beneath Hat block
        rootStackLayout = new BlockStackLayout(context, false);
        LinearLayout.LayoutParams rootStackLp = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        rootStackLp.topMargin = -hatBlockView.h;
        rootStackLayout.setLayoutParams(rootStackLp);
        rootStackLayout.setMinimumWidth(dp(220));
        rootStackLayout.setMinimumHeight(dp(96));
        rootStackLayout.setPadding(0, 0, dp(24), dp(48));
        mainChainContainer.addView(rootStackLayout);

        // Keep legacy field unused & permanently GONE so empty state matches native LogicEditorActivity
        emptyWorkspaceHint = new TextView(context);
        emptyWorkspaceHint.setVisibility(GONE);

        workspaceCanvas.setOnDragListener(new WorkspaceCanvasDragListener());

        // --- Native LogicEditorActivity-style Palette Drawer (FAB + area_palette mirroring palette_block.xml) ---
        layoutPalette = new LinearLayout(context);
        layoutPalette.setOrientation(LinearLayout.VERTICAL);
        layoutPalette.setClipChildren(false);
        layoutPalette.setClipToPadding(false);
        layoutPalette.setLayoutParams(new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        contentArea.addView(layoutPalette);

        fabTogglePalette = new FloatingActionButton(context);
        fabTogglePalette.setImageResource(R.drawable.block_flled_white);
        fabTogglePalette.setContentDescription("Blocks");
        fabTogglePalette.setOnClickListener(v -> togglePalette());
        layoutPalette.addView(fabTogglePalette);

        areaPalette = new LinearLayout(context);
        areaPalette.setOrientation(LinearLayout.HORIZONTAL);
        areaPalette.setBackgroundColor(colorSurfaceContainer);
        areaPalette.setElevation(dp(16));
        areaPalette.setClickable(true);
        areaPalette.setFocusable(true);
        areaPalette.setOnDragListener(new NonDropZoneDragListener());
        layoutPalette.addView(areaPalette);

        // Left side of palette_block.xml: ScrollView (weight 1) containing actions_container + paletteBlocksLayout
        paletteBlocksVScroll = new ScrollView(context);
        paletteBlocksVScroll.setVerticalScrollBarEnabled(true);
        paletteBlocksVScroll.setLayoutParams(new LinearLayout.LayoutParams(
                0, LayoutParams.MATCH_PARENT, 1f));
        areaPalette.addView(paletteBlocksVScroll);

        LinearLayout blocksInnerColumn = new LinearLayout(context);
        blocksInnerColumn.setOrientation(LinearLayout.VERTICAL);
        blocksInnerColumn.setLayoutParams(new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        paletteBlocksVScroll.addView(blocksInnerColumn);

        actionsContainer = new LinearLayout(context);
        actionsContainer.setOrientation(LinearLayout.VERTICAL);
        actionsContainer.setPadding(dp(8), dp(8), dp(8), 0);
        actionsContainer.setVisibility(GONE);
        blocksInnerColumn.addView(actionsContainer);

        paletteBlocksHScroll = new HorizontalScrollView(context);
        paletteBlocksHScroll.setHorizontalScrollBarEnabled(false);
        paletteBlocksHScroll.setFillViewport(false);
        paletteBlocksHScroll.setLayoutParams(new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        blocksInnerColumn.addView(paletteBlocksHScroll);

        paletteBlocksLayout = new LinearLayout(context);
        paletteBlocksLayout.setOrientation(LinearLayout.VERTICAL);
        paletteBlocksLayout.setPadding(dp(8), dp(8), dp(8), dp(12));
        paletteBlocksHScroll.addView(paletteBlocksLayout, new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START));

        // Right side of palette_block.xml: 120dp fixed width column with search_header + category selector
        LinearLayout rightSelectorColumn = new LinearLayout(context);
        rightSelectorColumn.setOrientation(LinearLayout.VERTICAL);
        rightSelectorColumn.setLayoutParams(new LinearLayout.LayoutParams(
                dp(CATEGORY_RAIL_WIDTH_DP), LayoutParams.MATCH_PARENT));
        areaPalette.addView(rightSelectorColumn);

        paletteSearchHeader = new TextView(context);
        paletteSearchHeader.setText(R.string.search_header);
        paletteSearchHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        paletteSearchHeader.setTextColor(colorOnSurface);
        paletteSearchHeader.setPadding(dp(10), dp(8), dp(10), dp(4));
        paletteSearchHeader.setSingleLine(true);
        TypedValue rippleOut = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, rippleOut, true)) {
            paletteSearchHeader.setBackgroundResource(rippleOut.resourceId);
        }
        paletteSearchHeader.setOnClickListener(v -> showSearchBlocksDialog());
        rightSelectorColumn.addView(paletteSearchHeader);

        ScrollView categoryScroll = new ScrollView(context);
        categoryScroll.setVerticalScrollBarEnabled(false);
        categoryScroll.setLayoutParams(new LinearLayout.LayoutParams(
                dp(CATEGORY_RAIL_WIDTH_DP), 0, 1f));
        rightSelectorColumn.addView(categoryScroll);

        categoryRailLayout = new LinearLayout(context);
        categoryRailLayout.setOrientation(LinearLayout.VERTICAL);
        categoryRailLayout.setPadding(dp(8), dp(6), 0, dp(8));
        categoryScroll.addView(categoryRailLayout);

        // --- Native LogicTopMenu (Delete + Duplicate cards) ---
        logicTopMenu = new LogicTopMenu(context);
        logicTopMenu.setTranslationY(-dp(48));
        logicTopMenu.setVisibility(GONE);
        logicTopMenu.setElevation(dp(8));
        logicTopMenu.toggleLayoutVisibility(true);
        View favoriteCard = logicTopMenu.findViewById(R.id.layout_favorite);
        if (favoriteCard != null) {
            favoriteCard.setVisibility(GONE);
        }
        logicTopMenu.setLayoutParams(new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, dp(48), Gravity.TOP));
        logicTopMenu.setOnDragListener(new TopMenuDragListener());
        contentArea.addView(logicTopMenu);

        // --- Native ViewDummy floating drag ghost ---
        viewDummy = new ViewDummy(context);
        viewDummy.setClickable(false);
        viewDummy.setFocusable(false);
        viewDummy.setDummyVisibility(GONE);
        viewDummy.setLayoutParams(new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        contentArea.addView(viewDummy);

        contentArea.setOnDragListener(new ContentFallbackDragListener());
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (newConfig != null) {
            applyPaletteOrientation(newConfig.orientation, true);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            int inferred = w > h ? Configuration.ORIENTATION_LANDSCAPE : Configuration.ORIENTATION_PORTRAIT;
            if (inferred != appliedOrientation) {
                post(() -> applyPaletteOrientation(inferred, false));
            }
        }
    }

    private boolean isLandscapeOrientation() {
        if (appliedOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            return true;
        }
        if (appliedOrientation == Configuration.ORIENTATION_PORTRAIT) {
            return false;
        }
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private void applyPaletteOrientation(int orientation, boolean force) {
        if (!force && orientation == appliedOrientation) {
            return;
        }
        appliedOrientation = orientation;
        boolean landscape = (orientation == Configuration.ORIENTATION_LANDSCAPE);
        int fabMargin = getResources().getDimensionPixelSize(R.dimen.action_button_margin);

        FrameLayout.LayoutParams paletteLp;
        LinearLayout.LayoutParams fabLp = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        fabLp.setMargins(fabMargin, fabMargin, fabMargin, fabMargin);

        LinearLayout.LayoutParams areaLp;
        if (landscape) {
            areaLp = new LinearLayout.LayoutParams(dp(PALETTE_WIDTH_LANDSCAPE_DP), LayoutParams.MATCH_PARENT);
            fabLp.gravity = Gravity.CENTER | Gravity.BOTTOM;
            layoutPalette.setOrientation(LinearLayout.HORIZONTAL);
            paletteLp = new FrameLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, Gravity.END | Gravity.TOP);
        } else {
            areaLp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(PALETTE_HEIGHT_DP));
            fabLp.gravity = Gravity.CENTER | Gravity.END;
            layoutPalette.setOrientation(LinearLayout.VERTICAL);
            paletteLp = new FrameLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.START | Gravity.BOTTOM);
        }

        areaPalette.setLayoutParams(areaLp);
        fabTogglePalette.setLayoutParams(fabLp);
        layoutPalette.setLayoutParams(paletteLp);

        if (paletteAnimator != null) {
            paletteAnimator.cancel();
            paletteAnimator = null;
        }

        boolean visiblePalette = paletteOpen && !paletteTempHiddenForDrag;
        if (landscape) {
            layoutPalette.setTranslationY(0f);
            layoutPalette.setTranslationX(visiblePalette ? 0f : dp(PALETTE_WIDTH_LANDSCAPE_DP));
        } else {
            layoutPalette.setTranslationX(0f);
            layoutPalette.setTranslationY(visiblePalette ? 0f : dp(PALETTE_HEIGHT_DP));
        }
        updateWorkspaceMarginsForPalette();
    }

    private void updateWorkspaceMarginsForPalette() {
        FrameLayout.LayoutParams wsLp = (FrameLayout.LayoutParams) workspaceContainer.getLayoutParams();
        boolean visiblePalette = paletteOpen && !paletteTempHiddenForDrag;
        boolean landscape = isLandscapeOrientation();
        int margin4 = dp(4);
        int nextBottom = (!landscape && visiblePalette) ? dp(PALETTE_HEIGHT_DP) : margin4;
        int nextRight = (landscape && visiblePalette) ? dp(PALETTE_WIDTH_LANDSCAPE_DP) : margin4;
        if (wsLp.bottomMargin != nextBottom || wsLp.rightMargin != nextRight
                || wsLp.leftMargin != margin4 || wsLp.topMargin != margin4) {
            wsLp.leftMargin = margin4;
            wsLp.topMargin = margin4;
            wsLp.bottomMargin = nextBottom;
            wsLp.rightMargin = nextRight;
            workspaceContainer.setLayoutParams(wsLp);
        }
        updateToolbarToggleAppearance();
    }

    private void togglePalette() {
        setPaletteOpen(!paletteOpen, true);
    }

    public void setPaletteOpen(boolean open, boolean animate) {
        paletteOpen = open;
        paletteTempHiddenForDrag = false;
        applyPaletteTranslation(open, animate);
        updateWorkspaceMarginsForPalette();
    }

    private void applyPaletteTranslation(boolean showArea, boolean animate) {
        if (paletteAnimator != null) {
            paletteAnimator.cancel();
            paletteAnimator = null;
        }
        boolean landscape = isLandscapeOrientation();
        float target = showArea
                ? 0f
                : (landscape ? dp(PALETTE_WIDTH_LANDSCAPE_DP) : dp(PALETTE_HEIGHT_DP));
        if (animate) {
            paletteAnimator = ObjectAnimator.ofFloat(
                    layoutPalette,
                    landscape ? View.TRANSLATION_X : View.TRANSLATION_Y,
                    target);
            paletteAnimator.setDuration(showArea ? 500L : 300L);
            paletteAnimator.setInterpolator(new DecelerateInterpolator());
            paletteAnimator.start();
        } else {
            if (landscape) {
                layoutPalette.setTranslationY(0f);
                layoutPalette.setTranslationX(target);
            } else {
                layoutPalette.setTranslationX(0f);
                layoutPalette.setTranslationY(target);
            }
        }
    }

    private void updateToolbarToggleAppearance() {
        if (btnTogglePalette == null) {
            return;
        }
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        if (paletteOpen) {
            bg.setColor(colorAccent);
            btnTogglePalette.setTextColor(Color.WHITE);
        } else {
            bg.setColor(colorSurfaceAlt);
            bg.setStroke(dp(1), colorBorder);
            btnTogglePalette.setTextColor(colorTextPrimary);
        }
        btnTogglePalette.setBackground(bg);
        btnTogglePalette.setText(paletteOpen ? "Blocks ▾" : "Blocks ▸");
    }

    /**
     * Enables single-event mode matching Sketchware's dedicated LogicEditorActivity screen:
     * hides the inline top chip bar so the canvas occupies 100% below the Activity Toolbar
     * and starts with the bottom/right palette drawer collapsed to its FAB.
     */
    public void setSingleEventMode(boolean singleEventMode) {
        this.singleEventMode = singleEventMode;
        if (toolbarScroll != null) {
            toolbarScroll.setVisibility(singleEventMode ? GONE : VISIBLE);
        }
        if (topDivider != null) {
            topDivider.setVisibility(singleEventMode ? GONE : VISIBLE);
        }
        if (singleEventMode) {
            setPaletteOpen(false, false);
        }
    }

    public boolean isPaletteOpen() {
        return paletteOpen;
    }

    @Nullable
    public GameScriptNode getCurrentScriptNode() {
        return currentScript;
    }

    public void setUndoHistoryChangedListener(@Nullable Runnable listener) {
        this.undoHistoryChangedListener = listener;
        notifyUndoHistoryChanged();
    }

    public boolean canUndo() {
        ScriptHistory hist = getCurrentScriptHistory(false);
        return hist != null && !hist.undoStack.isEmpty();
    }

    public boolean canRedo() {
        ScriptHistory hist = getCurrentScriptHistory(false);
        return hist != null && !hist.redoStack.isEmpty();
    }

    public void undo() {
        ScriptHistory hist = getCurrentScriptHistory(false);
        if (hist == null || hist.undoStack.isEmpty() || currentScript == null) {
            return;
        }
        hist.redoStack.push(hist.currentSnapshot);
        String prev = hist.undoStack.pop();
        restoreScriptSnapshot(prev);
        hist.currentSnapshot = prev;
        notifyUndoHistoryChanged();
        if (onScriptModified != null) {
            onScriptModified.run();
        }
    }

    public void redo() {
        ScriptHistory hist = getCurrentScriptHistory(false);
        if (hist == null || hist.redoStack.isEmpty() || currentScript == null) {
            return;
        }
        hist.undoStack.push(hist.currentSnapshot);
        String next = hist.redoStack.pop();
        restoreScriptSnapshot(next);
        hist.currentSnapshot = next;
        notifyUndoHistoryChanged();
        if (onScriptModified != null) {
            onScriptModified.run();
        }
    }

    private void restoreScriptSnapshot(String json) {
        if (currentScript == null) {
            return;
        }
        ScriptSnapshot snap = deserializeSnapshot(json);
        ensureTreeIntegrity(snap.blocks);
        for (ArrayList<GameBlockItem> floating : snap.floatingStacks) {
            ensureTreeIntegrity(floating);
        }
        currentScript.blocks = snap.blocks;
        currentScript.floatingStacks = snap.floatingStacks;
        isRestoringHistory = true;
        try {
            renderWorkspace();
        } finally {
            isRestoringHistory = false;
        }
    }

    private void ensureTreeIntegrity(@Nullable List<GameBlockItem> items) {
        if (items == null) {
            return;
        }
        for (GameBlockItem item : items) {
            if (item == null) {
                continue;
            }
            ensureItemIntegrity(item);
            if (item.paramBlocks != null) {
                ensureTreeIntegrity(item.paramBlocks);
            }
            ensureTreeIntegrity(item.subStack1);
            ensureTreeIntegrity(item.subStack2);
            GameBlockCatalog.syncNestedExpressions(item);
        }
    }

    @Nullable
    private ScriptHistory getCurrentScriptHistory(boolean createIfMissing) {
        String key = currentHistoryKey();
        ScriptHistory hist = historyByScriptKey.get(key);
        if (hist == null && createIfMissing) {
            hist = new ScriptHistory();
            hist.currentSnapshot = serializeScriptSnapshot(currentScript);
            historyByScriptKey.put(key, hist);
        }
        return hist;
    }

    private void initHistoryForCurrentScript() {
        getCurrentScriptHistory(true);
        notifyUndoHistoryChanged();
    }

    private void recordHistorySnapshotIfChanged() {
        if (isRestoringHistory) {
            return;
        }
        syncAllNestedExpressions();
        ScriptHistory hist = getCurrentScriptHistory(true);
        if (hist == null) {
            return;
        }
        String nextJson = serializeScriptSnapshot(currentScript);
        if (!TextUtils.equals(hist.currentSnapshot, nextJson)) {
            hist.undoStack.push(hist.currentSnapshot);
            while (hist.undoStack.size() > MAX_UNDO_HISTORY) {
                hist.undoStack.removeLast();
            }
            hist.redoStack.clear();
            hist.currentSnapshot = nextJson;
            notifyUndoHistoryChanged();
        }
    }

    private String currentHistoryKey() {
        return currentTargetId + "|" + currentEventType + "|" + (currentEventParam != null ? currentEventParam : "");
    }

    private static String serializeScriptSnapshot(@Nullable GameScriptNode script) {
        ScriptSnapshot snap = new ScriptSnapshot();
        if (script != null) {
            if (script.blocks != null) {
                snap.blocks.addAll(script.blocks);
            }
            if (script.floatingStacks != null) {
                snap.floatingStacks.addAll(script.floatingStacks);
            }
        }
        return HISTORY_GSON.toJson(snap);
    }

    private static ScriptSnapshot deserializeSnapshot(String json) {
        if (json != null && json.trim().startsWith("[")) {
            try {
                ScriptSnapshot legacy = new ScriptSnapshot();
                ArrayList<GameBlockItem> list = HISTORY_GSON.fromJson(json, BLOCK_LIST_TYPE);
                if (list != null) {
                    legacy.blocks = list;
                }
                return legacy;
            } catch (Throwable ignored) {
            }
        }
        try {
            ScriptSnapshot snap = HISTORY_GSON.fromJson(json, ScriptSnapshot.class);
            if (snap != null) {
                if (snap.blocks == null) {
                    snap.blocks = new ArrayList<>();
                }
                if (snap.floatingStacks == null) {
                    snap.floatingStacks = new ArrayList<>();
                }
                return snap;
            }
        } catch (Throwable ignored) {
        }
        return new ScriptSnapshot();
    }

    private static String serializeBlockList(@Nullable List<GameBlockItem> blocks) {
        return HISTORY_GSON.toJson(blocks != null ? blocks : new ArrayList<>());
    }

    private static ArrayList<GameBlockItem> deserializeBlockList(String json) {
        try {
            ArrayList<GameBlockItem> list = HISTORY_GSON.fromJson(json, BLOCK_LIST_TYPE);
            return list != null ? list : new ArrayList<>();
        } catch (Throwable ignored) {
            return new ArrayList<>();
        }
    }

    private void notifyUndoHistoryChanged() {
        if (undoHistoryChangedListener != null) {
            undoHistoryChangedListener.run();
        }
    }

    public String exportCurrentEventSource() {
        StringBuilder sb = new StringBuilder();
        String targetName = getTargetDisplayName(currentTargetId);
        String eventLabel = getShortEventLabel(currentEventType);

        sb.append("// ==========================================\n");
        sb.append("// Target: ").append(targetName).append("\n");
        sb.append("// Event:  ").append(eventLabel);
        if (eventUsesParam(currentEventType) && !TextUtils.isEmpty(currentEventParam)) {
            sb.append(" (\"").append(currentEventParam).append("\")");
        }
        sb.append("\n");
        if (scene != null && !TextUtils.isEmpty(scene.name)) {
            sb.append("// Scene:  ").append(scene.name).append("\n");
        }
        sb.append("// ==========================================\n\n");

        if (project != null && project.globalVariables != null && !project.globalVariables.isEmpty()) {
            sb.append("// Global Variables:\n");
            for (Map.Entry<String, String> entry : project.globalVariables.entrySet()) {
                sb.append("//   var ").append(entry.getKey())
                        .append(" = ").append(entry.getValue()).append(";\n");
            }
            sb.append("\n");
        }

        sb.append(buildEventMethodSignature()).append(" {\n");
        ArrayList<GameBlockItem> blocks = (currentScript != null && currentScript.blocks != null)
                ? currentScript.blocks
                : null;
        if (blocks == null || blocks.isEmpty()) {
            sb.append("    // (No blocks added to this event)\n");
        } else {
            appendBlockListSource(sb, blocks, "    ");
        }
        sb.append("}\n");
        return sb.toString();
    }

    private String buildEventMethodSignature() {
        switch (currentEventType) {
            case GameScriptNode.EVENT_SCENE_START:
                return "public void onSceneStart(RuntimeEntity self)";
            case GameScriptNode.EVENT_UPDATE:
                return "public void onUpdate(RuntimeEntity self, float dt)";
            case GameScriptNode.EVENT_TOUCH:
                return "public void onTouch(RuntimeEntity self, float worldX, float worldY)";
            case GameScriptNode.EVENT_COLLISION:
                return "public void onCollision(RuntimeEntity self, RuntimeEntity other)";
            case GameScriptNode.EVENT_BUTTON:
                return "public void onButtonPressed(String actionKey)";
            case GameScriptNode.EVENT_MESSAGE:
                return "public void onMessageReceived(String message)";
            default:
                return "public void onEvent(RuntimeEntity self)";
        }
    }

    private void appendBlockListSource(StringBuilder sb, List<GameBlockItem> blocks, String indent) {
        if (blocks == null) {
            return;
        }
        for (GameBlockItem block : blocks) {
            if (block == null || block.opCode == null) {
                continue;
            }
            appendSingleBlockSource(sb, block, indent);
        }
    }

    private void appendSingleBlockSource(StringBuilder sb, GameBlockItem block, String indent) {
        String op = block.opCode.trim();
        String p0 = blockParam(block, 0, "");
        String p1 = blockParam(block, 1, "");
        String p2 = blockParam(block, 2, "");

        switch (op) {
            case "if":
                sb.append(indent).append("if (").append(p0.isEmpty() ? "false" : p0).append(") {\n");
                appendBlockListSource(sb, block.subStack1, indent + "    ");
                sb.append(indent).append("}\n");
                break;

            case "ifElse":
                sb.append(indent).append("if (").append(p0.isEmpty() ? "false" : p0).append(") {\n");
                appendBlockListSource(sb, block.subStack1, indent + "    ");
                sb.append(indent).append("} else {\n");
                appendBlockListSource(sb, block.subStack2, indent + "    ");
                sb.append(indent).append("}\n");
                break;

            case "repeat":
                sb.append(indent).append("for (int _i = 0; _i < ").append(p0.isEmpty() ? "1" : p0).append("; _i++) {\n");
                appendBlockListSource(sb, block.subStack1, indent + "    ");
                sb.append(indent).append("}\n");
                break;

            case "ifButtonPressed":
            case "ifKeyPressed":
                sb.append(indent).append("if (runtime.isButtonPressed(").append(quoteSourceArg(p0)).append(")) {\n");
                appendBlockListSource(sb, block.subStack1, indent + "    ");
                sb.append(indent).append("}\n");
                break;

            case "ifCollidingTag":
                sb.append(indent).append("if (runtime.isCollidingTag(self, ").append(quoteSourceArg(p0)).append(")) {\n");
                appendBlockListSource(sb, block.subStack1, indent + "    ");
                sb.append(indent).append("}\n");
                break;

            case "ifOnGround":
                sb.append(indent).append("if (self != null && self.onGround) {\n");
                appendBlockListSource(sb, block.subStack1, indent + "    ");
                sb.append(indent).append("}\n");
                break;

            case "waitSeconds":
                sb.append(indent).append("yieldWaitSeconds(").append(p0.isEmpty() ? "0" : p0).append("f);\n");
                break;

            case "stopScript":
                sb.append(indent).append("return; // stopScript\n");
                break;

            case "setPos":
                sb.append(indent).append("self.setPosition(").append(p0).append(", ").append(p1).append(");\n");
                break;

            case "moveBy":
                sb.append(indent).append("self.moveBy(").append(p0).append(", ").append(p1).append(");\n");
                break;

            case "setVel":
                sb.append(indent).append("self.setVelocity(").append(p0).append(", ").append(p1).append(");\n");
                break;

            case "jumpImpulse":
                sb.append(indent).append("self.jumpImpulse(").append(p0).append(");\n");
                break;

            case "setRotation":
                sb.append(indent).append("self.rotation = ").append(p0).append("f;\n");
                break;

            case "rotateBy":
                sb.append(indent).append("self.rotation += ").append(p0).append("f;\n");
                break;

            case "pointToward":
                sb.append(indent).append("self.pointToward(").append(quoteSourceArg(p0)).append(");\n");
                break;

            case "cameraFollow":
                sb.append(indent).append("runtime.cameraFollow(").append(quoteSourceArg(p0));
                if (!p1.isEmpty()) {
                    sb.append(", ").append(p1);
                }
                sb.append(");\n");
                break;

            case "cameraShake":
                sb.append(indent).append("runtime.cameraShake(").append(p0).append(", ").append(p1).append(");\n");
                break;

            case "setSprite":
                sb.append(indent).append("self.setSprite(").append(quoteSourceArg(p0)).append(");\n");
                break;

            case "setVisible":
                sb.append(indent).append("self.visible = ").append(p0.isEmpty() ? "true" : p0).append(";\n");
                break;

            case "setAlpha":
                sb.append(indent).append("self.alpha = ").append(p0).append("f;\n");
                break;

            case "setScale":
                sb.append(indent).append("self.setScale(").append(p0).append(", ").append(p1).append(");\n");
                break;

            case "setColor":
                sb.append(indent).append("self.setColor(").append(quoteSourceArg(p0)).append(");\n");
                break;

            case "spawnParticles":
                sb.append(indent).append("runtime.spawnParticles(self, ").append(quoteSourceArg(p0))
                        .append(", ").append(p1).append(");\n");
                break;

            case "setPhysicsEnabled":
                sb.append(indent).append("self.physicsEnabled = ").append(p0.isEmpty() ? "true" : p0).append(";\n");
                break;

            case "setGravityScale":
                sb.append(indent).append("self.gravityScale = ").append(p0).append("f;\n");
                break;

            case "playSound":
                sb.append(indent).append("runtime.playSound(").append(quoteSourceArg(p0)).append(");\n");
                break;

            case "playBgm":
                sb.append(indent).append("runtime.playBgm(").append(quoteSourceArg(p0))
                        .append(", ").append(p1.isEmpty() ? "true" : p1).append(");\n");
                break;

            case "stopBgm":
                sb.append(indent).append("runtime.stopBgm();\n");
                break;

            case "setHudText":
                sb.append(indent).append("runtime.setHudText(").append(quoteSourceArg(p0))
                        .append(", ").append(p1.isEmpty() ? "\"\"" : p1).append(");\n");
                break;

            case "setHudBar":
                sb.append(indent).append("runtime.setHudBar(").append(quoteSourceArg(p0))
                        .append(", ").append(p1.isEmpty() ? "100" : p1).append(");\n");
                break;

            case "loadScene":
                sb.append(indent).append("runtime.loadScene(").append(quoteSourceArg(p0)).append(");\nreturn;\n");
                break;

            case "restartScene":
                sb.append(indent).append("runtime.restartScene();\nreturn;\n");
                break;

            case "spawnPrefab":
                sb.append(indent).append("runtime.spawnPrefab(").append(quoteSourceArg(p0))
                        .append(", ").append(p1).append(", ").append(p2).append(");\n");
                break;

            case "destroySelf":
                sb.append(indent).append("runtime.destroy(self);\nreturn;\n");
                break;

            case "destroyOther":
                sb.append(indent).append("runtime.destroy(other);\n");
                break;

            case "sendMessage":
                sb.append(indent).append("runtime.sendMessage(").append(quoteSourceArg(p0)).append(");\n");
                break;

            case "setVar":
                sb.append(indent).append("runtime.setVar(").append(quoteSourceArg(p0))
                        .append(", ").append(p1.isEmpty() ? "0" : p1).append(");\n");
                break;

            case "changeVar":
                sb.append(indent).append("runtime.changeVar(").append(quoteSourceArg(p0))
                        .append(", ").append(p1.isEmpty() ? "1" : p1).append(");\n");
                break;

            default:
                sb.append(indent).append("runtime.exec(").append(quoteSourceArg(op));
                if (block.parameters != null) {
                    for (String arg : block.parameters) {
                        sb.append(", ").append(quoteSourceArg(arg));
                    }
                }
                sb.append(");\n");
                break;
        }
    }

    private static String blockParam(GameBlockItem block, int idx, String fallback) {
        if (block != null && block.parameters != null && idx < block.parameters.size()
                && block.parameters.get(idx) != null) {
            return block.parameters.get(idx);
        }
        return fallback;
    }

    private static String quoteSourceArg(String raw) {
        if (raw == null) {
            return "\"\"";
        }
        String trimmed = raw.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed;
        }
        return "\"" + trimmed.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    public void showClearScriptDialog() {
        confirmClearCurrentScript();
    }

    public void showQuickAddDialog() {
        showQuickAddBlockDialog();
    }

    public String exportCurrentBlocksJson() {
        if (currentScript == null || currentScript.blocks == null || currentScript.blocks.isEmpty()) {
            return "";
        }
        return serializeBlockList(currentScript.blocks);
    }

    public int importBlocksFromJson(String json) {
        if (TextUtils.isEmpty(json)) {
            return 0;
        }
        String trimmed = json.trim();
        if (trimmed.startsWith("SWGAME_BLOCKS:")) {
            trimmed = trimmed.substring("SWGAME_BLOCKS:".length()).trim();
        }
        ArrayList<GameBlockItem> parsed = deserializeBlockList(trimmed);
        if (parsed.isEmpty()) {
            return 0;
        }
        ensureActiveScript();
        if (currentScript == null) {
            return 0;
        }
        ensureTreeIntegrity(parsed);
        for (GameBlockItem item : parsed) {
            if (item == null) {
                continue;
            }
            GameBlockItem clone = item.copy();
            assignFreshIds(clone);
            currentScript.blocks.add(clone);
        }
        renderWorkspace();
        notifyModified();
        return parsed.size();
    }

    public void bind(String scId, GameProject project, GameScene scene, Runnable onScriptModified) {
        this.scId = scId != null ? scId : "";
        this.project = project;
        this.scene = scene;
        this.onScriptModified = onScriptModified;

        refreshProjectResources();

        if (this.scene != null) {
            if (this.scene.objects == null) {
                this.scene.objects = new ArrayList<>();
            }
            if (this.scene.scripts == null) {
                this.scene.scripts = new ArrayList<>();
            }

            boolean currentTargetValid = GameScriptNode.SCENE_TARGET_ID.equals(currentTargetId)
                    || this.scene.findObjectById(currentTargetId) != null;

            if (!currentTargetValid || currentScript == null) {
                if (!this.scene.scripts.isEmpty()) {
                    GameScriptNode first = this.scene.scripts.get(0);
                    GameObjectBean obj = GameScriptNode.SCENE_TARGET_ID.equals(first.targetObjectId)
                            ? null
                            : this.scene.findObjectById(first.targetObjectId);
                    openEventScript(obj, first.eventType, first.eventParam);
                    return;
                }
                GameObjectBean firstObj = this.scene.objects.isEmpty() ? null : this.scene.objects.get(0);
                String initialEvent = firstObj != null
                        ? GameScriptNode.EVENT_UPDATE
                        : GameScriptNode.EVENT_SCENE_START;
                openEventScript(firstObj, initialEvent, "");
                return;
            }
        } else {
            currentScript = null;
        }

        initHistoryForCurrentScript();
        renderWorkspace();
    }

    public void discardFloatingStacks() {
        if (currentScript != null && currentScript.floatingStacks != null) {
            currentScript.floatingStacks.clear();
        }
        if (scene != null && scene.scripts != null) {
            for (GameScriptNode node : scene.scripts) {
                if (node != null && node.floatingStacks != null) {
                    node.floatingStacks.clear();
                }
            }
        }
        if (workspaceCanvas != null) {
            for (BlockStackLayout oldFloat : floatingStackLayouts) {
                if (oldFloat != null) {
                    workspaceCanvas.removeView(oldFloat);
                }
            }
        }
        floatingStackLayouts.clear();
    }

    public void openEventScript(@Nullable GameObjectBean targetOrNullForScene,
                                @Nullable String eventType,
                                @Nullable String eventParam) {
        if (currentScript != null && currentScript.floatingStacks != null) {
            currentScript.floatingStacks.clear();
        }
        currentTargetId = (targetOrNullForScene != null && !TextUtils.isEmpty(targetOrNullForScene.id))
                ? targetOrNullForScene.id
                : GameScriptNode.SCENE_TARGET_ID;
        currentEventType = !TextUtils.isEmpty(eventType)
                ? eventType
                : GameScriptNode.EVENT_SCENE_START;

        if (eventParam == null && scene != null && scene.scripts != null) {
            // Reuse existing param if a script for this target + event already exists
            String foundParam = null;
            for (GameScriptNode node : scene.scripts) {
                if (node != null
                        && TextUtils.equals(node.targetObjectId, currentTargetId)
                        && TextUtils.equals(node.eventType, currentEventType)) {
                    foundParam = node.eventParam;
                    break;
                }
            }
            currentEventParam = foundParam != null ? foundParam : defaultEventParam(currentEventType);
        } else {
            currentEventParam = eventParam != null ? eventParam : defaultEventParam(currentEventType);
        }

        if (scene != null) {
            currentScript = scene.getOrCreateScript(currentTargetId, currentEventType, currentEventParam);
            if (currentScript != null && currentScript.blocks == null) {
                currentScript.blocks = new ArrayList<>();
            }
        } else {
            currentScript = null;
        }

        initHistoryForCurrentScript();
        renderWorkspace();
        if (singleEventMode
                && (currentScript == null || currentScript.blocks == null || currentScript.blocks.isEmpty())) {
            post(() -> setPaletteOpen(true, true));
        }
    }

    public void refreshProjectResources() {
        cachedImageNames.clear();
        cachedSoundNames.clear();
        cachedFontNames.clear();
        if (TextUtils.isEmpty(scId)) {
            return;
        }
        try {
            List<String> imgs = GameProjectRepository.getProjectImageNames(scId);
            if (imgs != null) {
                cachedImageNames.addAll(imgs);
            }
        } catch (Throwable ignored) {
        }
        try {
            List<String> snds = GameProjectRepository.getProjectSoundNames(scId);
            if (snds != null) {
                cachedSoundNames.addAll(snds);
            }
        } catch (Throwable ignored) {
        }
        try {
            List<String> fonts = GameProjectRepository.getProjectFontNames(scId);
            if (fonts != null) {
                cachedFontNames.addAll(fonts);
            }
        } catch (Throwable ignored) {
        }
    }

    private void syncAllNestedExpressions() {
        if (currentScript == null) {
            return;
        }
        if (currentScript.blocks != null) {
            for (GameBlockItem b : currentScript.blocks) {
                GameBlockCatalog.syncNestedExpressions(b);
            }
        }
        if (currentScript.floatingStacks != null) {
            for (ArrayList<GameBlockItem> fs : currentScript.floatingStacks) {
                if (fs != null) {
                    for (GameBlockItem b : fs) {
                        GameBlockCatalog.syncNestedExpressions(b);
                    }
                }
            }
        }
    }

    private void notifyModified() {
        recordHistorySnapshotIfChanged();
        if (onScriptModified != null) {
            onScriptModified.run();
        }
    }

    // -------------------------------------------------------------------------
    // Palette Rendering (Native palette_block.xml + palette_selector_item.xml Parity)
    // -------------------------------------------------------------------------

    private void renderCategories() {
        categoryRailLayout.removeAllViews();
        Context ctx = getContext();
        for (CategoryDef cat : GameBlockCatalog.getCategories()) {
            boolean selected = cat.id.equals(selectedCategoryId);
            int harmonizedColor = MaterialColors.harmonizeWithPrimary(ctx, cat.color);

            RelativeLayout item = new RelativeLayout(ctx);
            item.setPadding(0, dp(2), 0, dp(2));
            item.setLayoutParams(new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, dp(24)));

            View bg = new View(ctx);
            bg.setBackgroundColor(harmonizedColor);
            RelativeLayout.LayoutParams bgLp = new RelativeLayout.LayoutParams(
                    selected ? LayoutParams.MATCH_PARENT : dp(4),
                    LayoutParams.MATCH_PARENT);
            item.addView(bg, bgLp);

            TextView tvCategory = new TextView(ctx);
            tvCategory.setText(shortCategoryTitle(cat.id, cat.title));
            tvCategory.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            tvCategory.setSingleLine(true);
            tvCategory.setEllipsize(TextUtils.TruncateAt.END);
            if (selected) {
                boolean lightBg = MaterialColors.isColorLight(harmonizedColor);
                tvCategory.setTextColor(lightBg ? colorOnSurface : colorOnSurfaceInverse);
            } else {
                tvCategory.setTextColor(colorOnSurface);
            }
            RelativeLayout.LayoutParams tvLp = new RelativeLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            tvLp.addRule(RelativeLayout.CENTER_VERTICAL);
            tvLp.leftMargin = dp(8);
            item.addView(tvCategory, tvLp);

            item.setOnClickListener(v -> {
                selectedCategoryId = cat.id;
                renderCategories();
                renderPaletteBlocks();
            });
            categoryRailLayout.addView(item);
        }
    }

    private String shortCategoryTitle(String id, String fullTitle) {
        switch (id) {
            case "motion":
                return "Motion";
            case "looks":
                return "Looks";
            case "physics":
                return "Physics";
            case "sound":
                return "Sound";
            case "control":
                return "Control";
            case "sensing":
                return "Sensing";
            case "operators":
                return "Operators";
            case "variables":
                return "Variables";
            case "hud":
                return "HUD";
            case "scenes":
                return "Scenes";
            default:
                return fullTitle;
        }
    }

    private void renderPaletteBlocks() {
        actionsContainer.removeAllViews();
        paletteBlocksLayout.removeAllViews();
        Context ctx = getContext();

        CategoryDef activeCat = null;
        for (CategoryDef cat : GameBlockCatalog.getCategories()) {
            if (cat.id.equals(selectedCategoryId)) {
                activeCat = cat;
                break;
            }
        }
        if (activeCat == null && !GameBlockCatalog.getCategories().isEmpty()) {
            activeCat = GameBlockCatalog.getCategories().get(0);
        }
        if (activeCat == null) {
            return;
        }

        boolean isVarCat = "variables".equals(activeCat.id);
        if (isVarCat) {
            actionsContainer.setVisibility(VISIBLE);
            addPaletteActionButton("Add Variable", this::showAddVariableDialog);
            addPaletteActionButton("Remove Variable", this::showRemoveVariableDialog);
        } else {
            actionsContainer.setVisibility(GONE);
        }

        if (isVarCat) {
            BlockDef getVarDef = GameBlockCatalog.findByOpCode("getVar");
            if (getVarDef != null) {
                List<String> varNames = collectVariableNamesForPalette();
                for (String varName : varNames) {
                    GameBlockItem varPreview = GameBlockCatalog.createBlockInstance(getVarDef);
                    if (varPreview != null) {
                        varPreview.parameters.clear();
                        varPreview.parameters.add(varName);
                    }
                    GameBlockView blockView = new GameBlockView(ctx, normalizeBlockType(getVarDef.type), harmonizeColor(getVarDef.color));
                    blockView.bindPalettePreview(varPreview, getVarDef);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                    lp.topMargin = dp(8);
                    blockView.setLayoutParams(lp);
                    blockView.setOnTouchListener(new PaletteBlockTouchListener(getVarDef, blockView, varName));
                    paletteBlocksLayout.addView(blockView);
                }
            }
        }

        for (BlockDef def : activeCat.blocks) {
            if (isVarCat && "getVar".equals(def.opCode)) {
                continue; // Rendered dynamically above per variable
            }
            GameBlockItem previewItem = GameBlockCatalog.createBlockInstance(def);
            if (isVarCat && previewItem != null && ("setVar".equals(def.opCode) || "changeVar".equals(def.opCode))) {
                List<String> varNames = collectVariableNamesForPalette();
                if (!varNames.isEmpty() && !previewItem.parameters.isEmpty()) {
                    previewItem.parameters.set(0, varNames.get(0));
                }
            }
            GameBlockView blockView = new GameBlockView(ctx, normalizeBlockType(def.type), harmonizeColor(def.color));
            blockView.bindPalettePreview(previewItem, def);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(8);
            lp.bottomMargin = 0;
            blockView.setLayoutParams(lp);
            String preboundVar = (previewItem != null && !previewItem.parameters.isEmpty() && isVarCat)
                    ? previewItem.parameters.get(0)
                    : null;
            blockView.setOnTouchListener(new PaletteBlockTouchListener(def, blockView, preboundVar));
            paletteBlocksLayout.addView(blockView);
        }
        if (paletteBlocksVScroll != null) {
            paletteBlocksVScroll.scrollTo(0, 0);
            paletteBlocksVScroll.post(() -> paletteBlocksVScroll.scrollTo(0, 0));
        }
        if (paletteBlocksHScroll != null) {
            paletteBlocksHScroll.scrollTo(0, 0);
        }
    }

    private List<String> collectVariableNamesForPalette() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (project != null && project.globalVariables != null) {
            names.addAll(project.globalVariables.keySet());
        }
        if (names.isEmpty()) {
            names.add("score");
        }
        return new ArrayList<>(names);
    }

    private void addPaletteActionButton(String title, Runnable action) {
        Context ctx = getContext();
        MaterialCardView cardView = new MaterialCardView(ctx);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, dp(30));
        cardLp.setMargins(0, 0, dp(4), dp(6));
        cardView.setLayoutParams(cardLp);
        cardView.setCardBackgroundColor(darkTheme ? colorSurfaceContainerHigh : colorSurfaceContainerHighest);
        cardView.setRadius(dp(6));
        cardView.setCardElevation(0f);

        TextView tv = new TextView(ctx);
        tv.setText(title.toUpperCase(Locale.ROOT));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(colorOnSurface);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(8), 0, dp(8), 0);
        cardView.addView(tv, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        cardView.setOnClickListener(v -> action.run());
        actionsContainer.addView(cardView);
    }

    private void showAddVariableDialog() {
        Context ctx = getContext();
        LinearLayout container = new LinearLayout(ctx);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(20), dp(12), dp(20), dp(4));

        final EditText nameEt = new EditText(ctx);
        nameEt.setHint("Variable name (e.g. coins, hp)");
        nameEt.setSingleLine(true);
        nameEt.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        container.addView(nameEt);

        final EditText initEt = new EditText(ctx);
        initEt.setHint("Initial value (e.g. 0)");
        initEt.setText("0");
        initEt.setSingleLine(true);
        initEt.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        LinearLayout.LayoutParams initLp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        initLp.topMargin = dp(8);
        initEt.setLayoutParams(initLp);
        container.addView(initEt);

        new MaterialAlertDialogBuilder(ctx)
                .setTitle("Add Variable")
                .setView(container)
                .setPositiveButton("Add", (d, w) -> {
                    String varName = nameEt.getText().toString().trim().replaceAll("\\s+", "_");
                    if (varName.isEmpty() || project == null) {
                        return;
                    }
                    if (project.globalVariables == null) {
                        project.globalVariables = new HashMap<>();
                    }
                    String initVal = initEt.getText().toString().trim();
                    project.globalVariables.put(varName, initVal.isEmpty() ? "0" : initVal);
                    renderPaletteBlocks();
                    if (onScriptModified != null) {
                        onScriptModified.run();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showRemoveVariableDialog() {
        Context ctx = getContext();
        if (project == null || project.globalVariables == null || project.globalVariables.isEmpty()) {
            Toast.makeText(ctx, "No variables created to remove.", Toast.LENGTH_SHORT).show();
            return;
        }
        final List<String> varNames = new ArrayList<>(project.globalVariables.keySet());
        new MaterialAlertDialogBuilder(ctx)
                .setTitle("Remove Variable")
                .setItems(varNames.toArray(new String[0]), (d, which) -> {
                    String victim = varNames.get(which);
                    project.globalVariables.remove(victim);
                    renderPaletteBlocks();
                    if (onScriptModified != null) {
                        onScriptModified.run();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showSearchBlocksDialog() {
        Context ctx = getContext();
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(10), dp(18), dp(4));

        final EditText searchEt = new EditText(ctx);
        searchEt.setHint("Search blocks by name or opCode...");
        searchEt.setSingleLine(true);
        root.addView(searchEt);

        ScrollView scroll = new ScrollView(ctx);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, dp(260));
        sLp.topMargin = dp(8);
        scroll.setLayoutParams(sLp);
        root.addView(scroll);

        final LinearLayout listCol = new LinearLayout(ctx);
        listCol.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listCol);

        final AlertDialog[] holder = new AlertDialog[1];

        Runnable populateResults = () -> {
            listCol.removeAllViews();
            String query = searchEt.getText().toString().trim().toLowerCase(Locale.ROOT);
            for (CategoryDef cat : GameBlockCatalog.getCategories()) {
                for (BlockDef def : cat.blocks) {
                    String display = GameBlockCatalog.formatBlockDisplay(GameBlockCatalog.createBlockInstance(def));
                    String haystack = (cat.title + " " + def.opCode + " " + display).toLowerCase(Locale.ROOT);
                    if (!query.isEmpty() && !haystack.contains(query)) {
                        continue;
                    }
                    TextView row = new TextView(ctx);
                    row.setText("[" + shortCategoryTitle(cat.id, cat.title) + "]  " + display);
                    row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
                    row.setTextColor(colorTextPrimary);
                    row.setPadding(dp(12), dp(10), dp(12), dp(10));
                    GradientDrawable rowBg = new GradientDrawable();
                    rowBg.setCornerRadius(dp(8));
                    rowBg.setColor(colorSurfaceAlt);
                    row.setBackground(rowBg);
                    LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
                    rLp.bottomMargin = dp(6);
                    row.setLayoutParams(rLp);
                    row.setOnClickListener(v -> {
                        selectedCategoryId = cat.id;
                        renderCategories();
                        renderPaletteBlocks();
                        appendPaletteBlockToRoot(def);
                        if (holder[0] != null) {
                            holder[0].dismiss();
                        }
                    });
                    listCol.addView(row);
                }
            }
        };

        searchEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                populateResults.run();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
        populateResults.run();

        holder[0] = new MaterialAlertDialogBuilder(ctx)
                .setTitle("Search Blocks")
                .setView(root)
                .setNegativeButton("Close", null)
                .show();
    }

    // -------------------------------------------------------------------------
    // Workspace Rendering
    // -------------------------------------------------------------------------

    private void renderWorkspace() {
        updateToolbarSummary();

        // Update Hat block header
        hatBlockView.bindHatHeader(buildHatTitle(), eventUsesParam(currentEventType), currentEventParam);

        ArrayList<GameBlockItem> blocks = currentScript != null && currentScript.blocks != null
                ? currentScript.blocks
                : new ArrayList<>();

        rootStackLayout.bindBlockList(blocks);
        rebuildStackView(rootStackLayout, blocks, false);

        // Rebuild floating stacks on the free 2D canvas
        for (BlockStackLayout oldFloat : floatingStackLayouts) {
            workspaceCanvas.removeView(oldFloat);
        }
        floatingStackLayouts.clear();

        if (currentScript != null && currentScript.floatingStacks != null) {
            pruneEmptyFloatingStacks();
            Context ctx = getContext();
            for (ArrayList<GameBlockItem> floatList : currentScript.floatingStacks) {
                if (floatList == null || floatList.isEmpty()) {
                    continue;
                }
                GameBlockItem head = floatList.get(0);
                BlockStackLayout floatStack = new BlockStackLayout(ctx, false);
                RelativeLayout.LayoutParams flLp = new RelativeLayout.LayoutParams(
                        LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                flLp.leftMargin = Math.max(dp(4), head.freeX);
                flLp.topMargin = Math.max(dp(4), head.freeY);
                floatStack.setLayoutParams(flLp);
                floatStack.setMinimumWidth(dp(64));
                floatStack.setMinimumHeight(dp(36));
                floatStack.setPadding(0, 0, dp(16), dp(24));
                workspaceCanvas.addView(floatStack);
                floatingStackLayouts.add(floatStack);
                rebuildStackView(floatStack, floatList, false);
            }
        }

        emptyWorkspaceHint.setVisibility(GONE);
        workspaceCanvas.requestLayout();
    }

    private void pruneEmptyFloatingStacks() {
        if (currentScript == null || currentScript.floatingStacks == null) {
            return;
        }
        for (int i = currentScript.floatingStacks.size() - 1; i >= 0; i--) {
            ArrayList<GameBlockItem> list = currentScript.floatingStacks.get(i);
            if (list == null || list.isEmpty()) {
                currentScript.floatingStacks.remove(i);
            }
        }
    }

    private boolean isFloatingStack(@Nullable ArrayList<GameBlockItem> list) {
        if (list == null || currentScript == null || currentScript.floatingStacks == null) {
            return false;
        }
        for (ArrayList<GameBlockItem> fs : currentScript.floatingStacks) {
            if (fs == list) {
                return true;
            }
        }
        return false;
    }

    private void rebuildStackView(BlockStackLayout container, ArrayList<GameBlockItem> list, boolean isSubstack) {
        container.removeAllViews();
        container.bindBlockList(list);
        Context ctx = getContext();

        if (list.isEmpty()) {
            return;
        }

        for (int i = 0; i < list.size(); i++) {
            GameBlockItem item = list.get(i);
            if (item == null) {
                continue;
            }
            ensureItemIntegrity(item);
            BlockDef def = GameBlockCatalog.findByOpCode(item.opCode);
            int rawColor = item.color != 0 ? item.color : (def != null ? def.color : GameBlockCatalog.COLOR_MOTION);
            int blockColor = harmonizeColor(rawColor);
            String blockType = normalizeBlockType(item.type != null ? item.type : (def != null ? def.type : " "));

            GameBlockView blockView = new GameBlockView(ctx, blockType, blockColor);
            blockView.bindWorkspaceItem(item, def, list, i);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                GameBlockItem prev = list.get(i - 1);
                String prevType = prev != null ? normalizeBlockType(prev.type) : " ";
                if (!"f".equals(prevType) && !"b".equals(blockType) && !"d".equals(blockType)) {
                    lp.topMargin = -blockView.h;
                }
            }
            blockView.setLayoutParams(lp);
            container.addView(blockView);

            if (blockView.b() && blockView.subStack1Layout != null) {
                rebuildStackView(blockView.subStack1Layout, item.subStack1, true);
            }
            if (blockView.c() && blockView.subStack2Layout != null) {
                rebuildStackView(blockView.subStack2Layout, item.subStack2, true);
            }
        }
    }

    private void ensureItemIntegrity(GameBlockItem item) {
        item.ensureInitialized();
        BlockDef def = GameBlockCatalog.findByOpCode(item.opCode);
        if (def != null) {
            int expected = !TextUtils.isEmpty(item.spec)
                    ? GameBlockCatalog.countPlaceholders(item.spec)
                    : def.defaultParams.length;
            int limit = Math.min(expected, def.defaultParams.length);
            while (item.parameters.size() < limit) {
                item.parameters.add(def.defaultParams[item.parameters.size()]);
            }
        }
        if (item.paramBlocks != null) {
            for (GameBlockItem child : item.paramBlocks) {
                if (child != null) {
                    ensureItemIntegrity(child);
                }
            }
        }
    }

    private String resolveParamKind(GameBlockItem item, @Nullable BlockDef def, int paramIdx) {
        if (("if".equals(item.opCode) || "ifElse".equals(item.opCode))
                && !TextUtils.isEmpty(item.spec)
                && GameBlockCatalog.countPlaceholders(item.spec) >= 3) {
            if (paramIdx == 0) {
                return GameBlockCatalog.KIND_EXPR;
            }
            if (paramIdx == 1) {
                return GameBlockCatalog.KIND_OP;
            }
            if (paramIdx == 2) {
                return GameBlockCatalog.KIND_NUMBER;
            }
        }
        if (def != null && paramIdx < def.paramKinds.length) {
            return def.paramKinds[paramIdx];
        }
        return GameBlockCatalog.KIND_TEXT;
    }

    private void updateToolbarSummary() {
        String targetName = getTargetDisplayName(currentTargetId);
        chipTarget.setText("Target: " + targetName + " ▾");

        String eventTitle = getShortEventLabel(currentEventType);
        int blockCount = (currentScript != null && currentScript.blocks != null)
                ? currentScript.blocks.size() : 0;
        chipEvent.setText(eventTitle + (blockCount > 0 ? " (" + blockCount + ")" : "") + " ▾");

        if (eventUsesParam(currentEventType)) {
            chipEventParam.setVisibility(VISIBLE);
            String prefix = getEventParamPrefix(currentEventType);
            String shown = TextUtils.isEmpty(currentEventParam) ? "set..." : currentEventParam;
            chipEventParam.setText(prefix + ": " + shown + " ✎");
        } else {
            chipEventParam.setVisibility(GONE);
        }

        btnClearScript.setEnabled(blockCount > 0);
        btnClearScript.setAlpha(blockCount > 0 ? 1f : 0.45f);
    }

    private String buildHatTitle() {
        String targetName = getTargetDisplayName(currentTargetId);
        switch (currentEventType) {
            case GameScriptNode.EVENT_SCENE_START:
                return GameScriptNode.SCENE_TARGET_ID.equals(currentTargetId)
                        ? "When Scene Starts"
                        : "When " + targetName + " Starts";
            case GameScriptNode.EVENT_UPDATE:
                return GameScriptNode.SCENE_TARGET_ID.equals(currentTargetId)
                        ? "Every Frame (Scene)"
                        : "Every Frame (" + targetName + ")";
            case GameScriptNode.EVENT_TOUCH:
                return "When " + targetName + " Touched";
            case GameScriptNode.EVENT_COLLISION:
                return "When " + targetName + " Collides";
            case GameScriptNode.EVENT_BUTTON:
                return "When Button Pressed";
            case GameScriptNode.EVENT_MESSAGE:
                return "When Message Received";
            default:
                return "When " + currentEventType;
        }
    }

    private String getTargetDisplayName(String targetId) {
        if (TextUtils.isEmpty(targetId) || GameScriptNode.SCENE_TARGET_ID.equals(targetId)) {
            return "Current Scene";
        }
        if (scene != null) {
            GameObjectBean obj = scene.findObjectById(targetId);
            if (obj != null && !TextUtils.isEmpty(obj.name)) {
                return obj.name;
            }
        }
        return targetId;
    }

    private static boolean eventUsesParam(String eventType) {
        return GameScriptNode.EVENT_COLLISION.equals(eventType)
                || GameScriptNode.EVENT_BUTTON.equals(eventType)
                || GameScriptNode.EVENT_MESSAGE.equals(eventType);
    }

    private static String defaultEventParam(String eventType) {
        if (GameScriptNode.EVENT_COLLISION.equals(eventType)) {
            return "coin";
        }
        if (GameScriptNode.EVENT_BUTTON.equals(eventType)) {
            return "jump";
        }
        if (GameScriptNode.EVENT_MESSAGE.equals(eventType)) {
            return "gameover";
        }
        return "";
    }

    private static String getShortEventLabel(String eventType) {
        switch (eventType) {
            case GameScriptNode.EVENT_SCENE_START:
                return "On Start";
            case GameScriptNode.EVENT_UPDATE:
                return "Every Frame";
            case GameScriptNode.EVENT_TOUCH:
                return "On Touch";
            case GameScriptNode.EVENT_COLLISION:
                return "Collision";
            case GameScriptNode.EVENT_BUTTON:
                return "HUD Button";
            case GameScriptNode.EVENT_MESSAGE:
                return "Message";
            default:
                return eventType;
        }
    }

    private static String getEventParamPrefix(String eventType) {
        switch (eventType) {
            case GameScriptNode.EVENT_COLLISION:
                return "tag";
            case GameScriptNode.EVENT_BUTTON:
                return "button";
            case GameScriptNode.EVENT_MESSAGE:
                return "msg";
            default:
                return "param";
        }
    }

    // -------------------------------------------------------------------------
    // Target, Event, Quick-Add, & Block Action Dialogs
    // -------------------------------------------------------------------------

    private void showTargetSelectorDialog() {
        Context ctx = getContext();
        List<String> labels = new ArrayList<>();
        final List<GameObjectBean> targets = new ArrayList<>();

        int sceneCount = countScriptsForTarget(GameScriptNode.SCENE_TARGET_ID);
        labels.add("Current Scene (Global)" + (sceneCount > 0 ? "  [" + sceneCount + " scripts]" : ""));
        targets.add(null);

        if (scene != null && scene.objects != null) {
            for (GameObjectBean obj : scene.objects) {
                if (obj == null) {
                    continue;
                }
                int cnt = countScriptsForTarget(obj.id);
                String kindTag = obj.isHud ? "HUD" : (!TextUtils.isEmpty(obj.type) ? obj.type : "Obj");
                String label = (!TextUtils.isEmpty(obj.name) ? obj.name : obj.id)
                        + "  (" + kindTag + ")"
                        + (cnt > 0 ? "  [" + cnt + "]" : "");
                labels.add(label);
                targets.add(obj);
            }
        }

        new MaterialAlertDialogBuilder(ctx)
                .setTitle("Select Script Target")
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    GameObjectBean chosen = targets.get(which);
                    String nextEvent = currentEventType;
                    if (chosen == null && GameScriptNode.EVENT_TOUCH.equals(nextEvent)) {
                        nextEvent = GameScriptNode.EVENT_SCENE_START;
                    }
                    openEventScript(chosen, nextEvent, null);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int countScriptsForTarget(String targetId) {
        if (scene == null || scene.scripts == null) {
            return 0;
        }
        int count = 0;
        for (GameScriptNode node : scene.scripts) {
            if (node != null
                    && TextUtils.equals(node.targetObjectId, targetId)
                    && node.blocks != null
                    && !node.blocks.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private void showEventSelectorDialog() {
        Context ctx = getContext();
        final List<String> labels = new ArrayList<>();
        final List<String> eventTypes = new ArrayList<>();
        final List<String> eventParams = new ArrayList<>();

        // Existing scripts for this target first
        if (scene != null && scene.scripts != null) {
            for (GameScriptNode node : scene.scripts) {
                if (node == null || !TextUtils.equals(node.targetObjectId, currentTargetId)) {
                    continue;
                }
                int size = node.blocks != null ? node.blocks.size() : 0;
                if (size == 0 && !TextUtils.equals(node.eventType, currentEventType)) {
                    continue;
                }
                String paramStr = eventUsesParam(node.eventType) && !TextUtils.isEmpty(node.eventParam)
                        ? " (" + node.eventParam + ")"
                        : "";
                labels.add("● " + getShortEventLabel(node.eventType) + paramStr + "  [" + size + " blocks]");
                eventTypes.add(node.eventType);
                eventParams.add(node.eventParam != null ? node.eventParam : "");
            }
        }

        // Standard events
        String[] allEvents = {
                GameScriptNode.EVENT_SCENE_START,
                GameScriptNode.EVENT_UPDATE,
                GameScriptNode.EVENT_TOUCH,
                GameScriptNode.EVENT_COLLISION,
                GameScriptNode.EVENT_BUTTON,
                GameScriptNode.EVENT_MESSAGE
        };
        String[] allTitles = {
                "When Scene / Object Starts",
                "Every Frame (Update)",
                "When Object Touched",
                "When Colliding with Tag...",
                "When HUD Button Pressed...",
                "When Message Received..."
        };

        for (int i = 0; i < allEvents.length; i++) {
            String ev = allEvents[i];
            labels.add("+ " + allTitles[i]);
            eventTypes.add(ev);
            eventParams.add(defaultEventParam(ev));
        }

        final GameObjectBean targetObj = GameScriptNode.SCENE_TARGET_ID.equals(currentTargetId) || scene == null
                ? null
                : scene.findObjectById(currentTargetId);

        new MaterialAlertDialogBuilder(ctx)
                .setTitle("Event for " + getTargetDisplayName(currentTargetId))
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    String ev = eventTypes.get(which);
                    String param = eventParams.get(which);
                    boolean isExistingEntry = labels.get(which).startsWith("● ");
                    openEventScript(targetObj, ev, param);
                    if (!isExistingEntry && eventUsesParam(ev)) {
                        showEditEventParamDialog();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditEventParamDialog() {
        if (!eventUsesParam(currentEventType)) {
            return;
        }
        String kind;
        String title;
        if (GameScriptNode.EVENT_COLLISION.equals(currentEventType)) {
            kind = GameBlockCatalog.KIND_TAG;
            title = "Collision Tag / Object";
        } else if (GameScriptNode.EVENT_BUTTON.equals(currentEventType)) {
            kind = GameBlockCatalog.KIND_ACTION;
            title = "HUD Button / Action";
        } else {
            kind = GameBlockCatalog.KIND_MESSAGE;
            title = "Message Name";
        }

        showSmartValueDialog(title, currentEventParam, kind, newVal -> {
            String cleaned = newVal != null ? newVal.trim() : "";
            if (cleaned.isEmpty()) {
                return;
            }
            if (scene != null && scene.scripts != null) {
                GameScriptNode existingOther = null;
                for (GameScriptNode node : scene.scripts) {
                    if (node != null
                            && node != currentScript
                            && TextUtils.equals(node.targetObjectId, currentTargetId)
                            && TextUtils.equals(node.eventType, currentEventType)
                            && TextUtils.equals(node.eventParam, cleaned)) {
                        existingOther = node;
                        break;
                    }
                }
                if (existingOther != null && existingOther.blocks != null && !existingOther.blocks.isEmpty()) {
                    currentEventParam = cleaned;
                    currentScript = existingOther;
                    renderWorkspace();
                    return;
                }
            }
            currentEventParam = cleaned;
            if (currentScript != null) {
                currentScript.eventParam = cleaned;
            }
            renderWorkspace();
            notifyModified();
        });
    }

    private void showQuickAddBlockDialog() {
        Context ctx = getContext();
        final List<BlockDef> allDefs = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        for (CategoryDef cat : GameBlockCatalog.getCategories()) {
            for (BlockDef def : cat.blocks) {
                allDefs.add(def);
                labels.add("[" + shortCategoryTitle(cat.id, cat.title) + "]  "
                        + GameBlockCatalog.formatBlockDisplay(GameBlockCatalog.createBlockInstance(def)));
            }
        }
        new MaterialAlertDialogBuilder(ctx)
                .setTitle("Add Block to Script")
                .setItems(labels.toArray(new String[0]), (dialog, which) -> appendPaletteBlockToRoot(allDefs.get(which)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmClearCurrentScript() {
        int mainCount = (currentScript != null && currentScript.blocks != null) ? currentScript.blocks.size() : 0;
        int floatCount = (currentScript != null && currentScript.floatingStacks != null) ? currentScript.floatingStacks.size() : 0;
        if (mainCount == 0 && floatCount == 0) {
            return;
        }
        new MaterialAlertDialogBuilder(getContext())
                .setTitle("Clear Script")
                .setMessage("Remove all blocks and floating stacks from this event?")
                .setPositiveButton("Clear", (d, w) -> {
                    currentScript.blocks.clear();
                    if (currentScript.floatingStacks != null) {
                        currentScript.floatingStacks.clear();
                    }
                    renderWorkspace();
                    notifyModified();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void appendPaletteBlockToRoot(BlockDef def) {
        appendPaletteBlockToRoot(def, null);
    }

    private void appendPaletteBlockToRoot(BlockDef def, @Nullable String preboundParam0) {
        if (def == null) {
            return;
        }
        ensureActiveScript();
        if (currentScript == null) {
            return;
        }
        GameBlockItem item = GameBlockCatalog.createBlockInstance(def);
        if (item == null) {
            return;
        }
        if (preboundParam0 != null && !preboundParam0.isEmpty()) {
            if (item.parameters == null) {
                item.parameters = new ArrayList<>();
            }
            if (item.parameters.isEmpty()) {
                item.parameters.add(preboundParam0);
            } else {
                item.parameters.set(0, preboundParam0);
            }
        }
        if (GameBlockCatalog.isReporterBlock(item)) {
            if (currentScript.floatingStacks == null) {
                currentScript.floatingStacks = new ArrayList<>();
            }
            int offset = currentScript.floatingStacks.size() * dp(18);
            item.freeX = dp(24) + (offset % dp(140));
            item.freeY = Math.max(dp(120), mainChainContainer.getHeight() + dp(12) + offset);
            ArrayList<GameBlockItem> single = new ArrayList<>();
            single.add(item);
            currentScript.floatingStacks.add(single);
            renderWorkspace();
            notifyModified();
            Toast.makeText(getContext(), "Block placed on canvas! Drag to a slot.", Toast.LENGTH_SHORT).show();
            return;
        }
        currentScript.blocks.add(item);
        renderWorkspace();
        notifyModified();
        workspaceVScroll.post(() -> workspaceVScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void ensureActiveScript() {
        if (currentScript == null && scene != null) {
            currentScript = scene.getOrCreateScript(currentTargetId, currentEventType, currentEventParam);
        }
        if (currentScript != null) {
            currentScript.ensureInitialized();
        }
    }

    private void showBlockQuickActions(View anchor, GameBlockItem item, ArrayList<GameBlockItem> parentList, int index) {
        PopupMenu popup = new PopupMenu(getContext(), anchor);
        popup.getMenu().add(0, 1, 0, "Duplicate");
        if (index > 0) {
            popup.getMenu().add(0, 2, 1, "Move up ↑");
        }
        if (index < parentList.size() - 1) {
            popup.getMenu().add(0, 3, 2, "Move down ↓");
        }
        if (!GameBlockCatalog.isReporterBlock(item) && isFloatingStack(parentList)) {
            popup.getMenu().add(0, 5, 3, "Snap to main script");
        }
        popup.getMenu().add(0, 4, 4, "Delete block");

        popup.setOnMenuItemClickListener(menuItem -> {
            switch (menuItem.getItemId()) {
                case 1: {
                    GameBlockItem dup = item.copy();
                    assignFreshIds(dup);
                    if (isFloatingStack(parentList) && index == 0) {
                        ArrayList<GameBlockItem> dupStack = new ArrayList<>();
                        dup.freeX = item.freeX + dp(18);
                        dup.freeY = item.freeY + dp(18);
                        dupStack.add(dup);
                        if (currentScript != null && currentScript.floatingStacks != null) {
                            currentScript.floatingStacks.add(dupStack);
                        }
                    } else {
                        parentList.add(Math.min(index + 1, parentList.size()), dup);
                    }
                    renderWorkspace();
                    notifyModified();
                    return true;
                }
                case 2:
                    if (index > 0) {
                        Collections.swap(parentList, index, index - 1);
                        renderWorkspace();
                        notifyModified();
                    }
                    return true;
                case 3:
                    if (index < parentList.size() - 1) {
                        Collections.swap(parentList, index, index + 1);
                        renderWorkspace();
                        notifyModified();
                    }
                    return true;
                case 4:
                    if (index >= 0 && index < parentList.size()) {
                        parentList.remove(index);
                        pruneEmptyFloatingStacks();
                        renderWorkspace();
                        notifyModified();
                    }
                    return true;
                case 5:
                    if (index >= 0 && index < parentList.size() && currentScript != null && currentScript.blocks != null) {
                        if (index == 0) {
                            currentScript.blocks.addAll(parentList);
                            parentList.clear();
                        } else {
                            GameBlockItem moved = parentList.remove(index);
                            currentScript.blocks.add(moved);
                        }
                        pruneEmptyFloatingStacks();
                        renderWorkspace();
                        notifyModified();
                    }
                    return true;
                default:
                    return false;
            }
        });
        popup.show();
    }

    private void showNestedBlockQuickActions(View anchor, GameBlockItem nested,
                                             GameBlockItem parentBlock, int paramIndex) {
        PopupMenu popup = new PopupMenu(getContext(), anchor);
        popup.getMenu().add(0, 1, 0, "Drop onto workspace");
        popup.getMenu().add(0, 2, 1, "Remove from slot");
        popup.setOnMenuItemClickListener(menuItem -> {
            if (menuItem.getItemId() == 1) {
                ensureActiveScript();
                parentBlock.setParamBlock(paramIndex, null);
                GameBlockCatalog.syncNestedExpressions(parentBlock);
                if (currentScript != null) {
                    if (currentScript.floatingStacks == null) {
                        currentScript.floatingStacks = new ArrayList<>();
                    }
                    nested.freeX = dp(32) + (currentScript.floatingStacks.size() * dp(16)) % dp(140);
                    nested.freeY = Math.max(dp(120), mainChainContainer.getHeight() + dp(12));
                    ArrayList<GameBlockItem> single = new ArrayList<>();
                    single.add(nested);
                    currentScript.floatingStacks.add(single);
                }
                renderWorkspace();
                notifyModified();
                return true;
            } else if (menuItem.getItemId() == 2) {
                parentBlock.setParamBlock(paramIndex, null);
                BlockDef def = GameBlockCatalog.findByOpCode(parentBlock.opCode);
                if (def != null && paramIndex < def.defaultParams.length && parentBlock.parameters != null) {
                    while (parentBlock.parameters.size() <= paramIndex) {
                        parentBlock.parameters.add("");
                    }
                    parentBlock.parameters.set(paramIndex, def.defaultParams[paramIndex]);
                }
                GameBlockCatalog.syncNestedExpressions(parentBlock);
                renderWorkspace();
                notifyModified();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private static void assignFreshIds(GameBlockItem item) {
        if (item == null) {
            return;
        }
        item.id = "blk_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        if (item.paramBlocks != null) {
            for (GameBlockItem child : item.paramBlocks) {
                assignFreshIds(child);
            }
        }
        if (item.subStack1 != null) {
            for (GameBlockItem child : item.subStack1) {
                assignFreshIds(child);
            }
        }
        if (item.subStack2 != null) {
            for (GameBlockItem child : item.subStack2) {
                assignFreshIds(child);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Smart Parameter Picker Dialogs
    // -------------------------------------------------------------------------

    private interface ValueCallback {
        void onValueChosen(String value);
    }

    private void showParameterEditor(GameBlockItem item, int paramIndex, @Nullable BlockDef def) {
        ensureItemIntegrity(item);
        String currentVal = paramIndex < item.parameters.size() ? item.parameters.get(paramIndex) : "";
        String kind = resolveParamKind(item, def, paramIndex);
        String title = getParamDialogTitle(kind);

        showSmartValueDialog(title, currentVal, kind, chosen -> {
            while (item.parameters.size() <= paramIndex) {
                item.parameters.add("");
            }
            item.parameters.set(paramIndex, chosen);
            syncAllNestedExpressions();
            renderWorkspace();
            notifyModified();
        });
    }

    private String getParamDialogTitle(String kind) {
        switch (kind) {
            case GameBlockCatalog.KIND_NUMBER:
                return "Number or Expression";
            case GameBlockCatalog.KIND_SPRITE:
                return "Select Sprite (Image)";
            case GameBlockCatalog.KIND_SOUND:
                return "Select Sound / Music";
            case GameBlockCatalog.KIND_SCENE:
                return "Select Scene";
            case GameBlockCatalog.KIND_OBJECT:
                return "Select Object";
            case GameBlockCatalog.KIND_HUD:
                return "Select HUD Element";
            case GameBlockCatalog.KIND_PREFAB:
                return "Select Prefab";
            case GameBlockCatalog.KIND_TAG:
                return "Tag / Collision Group";
            case GameBlockCatalog.KIND_ACTION:
                return "HUD Button / Action";
            case GameBlockCatalog.KIND_MESSAGE:
                return "Select Message";
            case GameBlockCatalog.KIND_BOOL:
                return "Select Boolean";
            case GameBlockCatalog.KIND_COND:
                return "Logic Condition (Boolean)";
            case GameBlockCatalog.KIND_OP:
                return "Comparison Operator";
            case GameBlockCatalog.KIND_VAR:
            case GameBlockCatalog.KIND_VAR_REF:
                return "Select or Create Variable";
            case GameBlockCatalog.KIND_MATH_FN:
                return "Math Function";
            case GameBlockCatalog.KIND_PROP:
                return "Object Property";
            case GameBlockCatalog.KIND_LAYER:
                return "Display Layer";
            case GameBlockCatalog.KIND_COLOR:
                return "Choose Color";
            case GameBlockCatalog.KIND_EXPR:
                return "Value or Expression";
            default:
                return "Edit Text";
        }
    }

    private static boolean isSelectorKind(String kind) {
        return GameBlockCatalog.KIND_SPRITE.equals(kind)
                || GameBlockCatalog.KIND_SOUND.equals(kind)
                || GameBlockCatalog.KIND_SCENE.equals(kind)
                || GameBlockCatalog.KIND_OBJECT.equals(kind)
                || GameBlockCatalog.KIND_HUD.equals(kind)
                || GameBlockCatalog.KIND_PREFAB.equals(kind)
                || GameBlockCatalog.KIND_ACTION.equals(kind)
                || GameBlockCatalog.KIND_TAG.equals(kind)
                || GameBlockCatalog.KIND_VAR.equals(kind)
                || GameBlockCatalog.KIND_VAR_REF.equals(kind)
                || GameBlockCatalog.KIND_MESSAGE.equals(kind);
    }

    private static boolean isExtensibleSelectorKind(String kind) {
        return GameBlockCatalog.KIND_TAG.equals(kind)
                || GameBlockCatalog.KIND_VAR.equals(kind)
                || GameBlockCatalog.KIND_VAR_REF.equals(kind)
                || GameBlockCatalog.KIND_MESSAGE.equals(kind);
    }

    private static String getEmptySelectorMessage(String kind) {
        if (GameBlockCatalog.KIND_SPRITE.equals(kind)) {
            return "No sprites or images imported yet. Import images in Project Resources first.";
        }
        if (GameBlockCatalog.KIND_SOUND.equals(kind)) {
            return "No sounds imported yet. Import sounds in Project Resources first.";
        }
        if (GameBlockCatalog.KIND_SCENE.equals(kind)) {
            return "No scenes found in this project.";
        }
        if (GameBlockCatalog.KIND_HUD.equals(kind)) {
            return "No HUD elements in this scene yet.";
        }
        if (GameBlockCatalog.KIND_PREFAB.equals(kind)) {
            return "No prefabs or objects in this scene yet.";
        }
        return "No items available.";
    }

    private void showQuickNewValueDialog(String title, String kind, ValueCallback callback) {
        Context ctx = getContext();
        LinearLayout container = new LinearLayout(ctx);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(20), dp(12), dp(20), dp(4));

        final EditText input = new EditText(ctx);
        input.setHint("Enter name...");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        container.addView(input);

        new MaterialAlertDialogBuilder(ctx)
                .setTitle(title)
                .setView(container)
                .setPositiveButton("Create", (d, w) -> {
                    String val = input.getText().toString().trim();
                    if (val.isEmpty()) {
                        return;
                    }
                    if ((GameBlockCatalog.KIND_VAR.equals(kind) || GameBlockCatalog.KIND_VAR_REF.equals(kind))
                            && project != null) {
                        val = val.replaceAll("\\s+", "_");
                        if (project.globalVariables == null) {
                            project.globalVariables = new HashMap<>();
                        }
                        if (!project.globalVariables.containsKey(val)) {
                            project.globalVariables.put(val, "0");
                        }
                        renderPaletteBlocks();
                    }
                    callback.onValueChosen(val);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showSmartValueDialog(String title, String currentValue, String kind, ValueCallback callback) {
        Context ctx = getContext();

        if (GameBlockCatalog.KIND_BOOL.equals(kind)) {
            final String[] vals = {"true", "false"};
            final String[] labels = {"true", "false"};
            new MaterialAlertDialogBuilder(ctx)
                    .setTitle(title)
                    .setItems(labels, (d, which) -> callback.onValueChosen(vals[which]))
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        if (GameBlockCatalog.KIND_OP.equals(kind)) {
            final String[] ops = {"==", "!=", ">", "<", ">=", "<="};
            final String[] labels = {
                    "==  (Equal)",
                    "!=  (Not equal)",
                    ">   (Greater than)",
                    "<   (Less than)",
                    ">=  (Greater or equal)",
                    "<=  (Less or equal)"
            };
            new MaterialAlertDialogBuilder(ctx)
                    .setTitle(title)
                    .setItems(labels, (d, which) -> callback.onValueChosen(ops[which]))
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        if (GameBlockCatalog.KIND_MATH_FN.equals(kind)) {
            final String[] fns = {"abs", "floor", "ceil", "sqrt", "sin", "cos", "tan",
                    "asin", "acos", "atan", "ln", "log10", "exp"};
            new MaterialAlertDialogBuilder(ctx)
                    .setTitle(title)
                    .setItems(fns, (d, which) -> callback.onValueChosen(fns[which]))
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        if (GameBlockCatalog.KIND_PROP.equals(kind)) {
            final String[] props = {"x", "y", "vx", "vy", "rotation", "size", "alpha", "spriteName", "visible"};
            new MaterialAlertDialogBuilder(ctx)
                    .setTitle(title)
                    .setItems(props, (d, which) -> callback.onValueChosen(props[which]))
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        if (GameBlockCatalog.KIND_LAYER.equals(kind)) {
            final String[] layers = {"front", "back"};
            final String[] labels = {"Front (front)", "Back (back)"};
            new MaterialAlertDialogBuilder(ctx)
                    .setTitle(title)
                    .setItems(labels, (d, which) -> callback.onValueChosen(layers[which]))
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        if (GameBlockCatalog.KIND_COLOR.equals(kind)) {
            showColorPickerDialog(title, currentValue, callback);
            return;
        }

        if (isSelectorKind(kind)) {
            List<String> suggestions = buildSuggestionsForKind(kind);
            boolean extensible = isExtensibleSelectorKind(kind);
            if (suggestions.isEmpty() && !extensible) {
                new MaterialAlertDialogBuilder(ctx)
                        .setTitle(title)
                        .setMessage(getEmptySelectorMessage(kind))
                        .setPositiveButton("Close", null)
                        .show();
                return;
            }
            final List<String> items = new ArrayList<>(suggestions);
            if (extensible) {
                items.add("+ New...");
            }
            new MaterialAlertDialogBuilder(ctx)
                    .setTitle(title)
                    .setItems(items.toArray(new String[0]), (d, which) -> {
                        if (extensible && which == items.size() - 1) {
                            showQuickNewValueDialog(title, kind, callback);
                            return;
                        }
                        String cleanValue = stripDisplayTag(items.get(which));
                        if ((GameBlockCatalog.KIND_VAR.equals(kind) || GameBlockCatalog.KIND_VAR_REF.equals(kind))
                                && project != null) {
                            if (project.globalVariables == null) {
                                project.globalVariables = new HashMap<>();
                            }
                            if (!project.globalVariables.containsKey(cleanValue)) {
                                project.globalVariables.put(cleanValue, "0");
                                renderPaletteBlocks();
                            }
                        }
                        callback.onValueChosen(cleanValue);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        // Combined editor for numbers, expressions, conditions, and free text
        LinearLayout container = new LinearLayout(ctx);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(20), dp(12), dp(20), dp(4));

        final EditText input = new EditText(ctx);
        input.setText(currentValue != null ? currentValue : "");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setSelection(input.getText().length());
        container.addView(input);

        if (GameBlockCatalog.KIND_COND.equals(kind)) {
            addConditionBuilderRow(ctx, container, input);
        }

        List<String> suggestions = buildSuggestionsForKind(kind);

        final AlertDialog[] dialogHolder = new AlertDialog[1];

        if (!suggestions.isEmpty()) {
            TextView sugHeader = new TextView(ctx);
            sugHeader.setText(GameBlockCatalog.KIND_NUMBER.equals(kind)
                    || GameBlockCatalog.KIND_EXPR.equals(kind)
                    || GameBlockCatalog.KIND_COND.equals(kind)
                    ? "Quick shortcuts:"
                    : "Available in project (tap to choose):");
            sugHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            sugHeader.setTextColor(colorTextSecondary);
            sugHeader.setPadding(0, dp(10), 0, dp(6));
            container.addView(sugHeader);

            boolean useChipMode = GameBlockCatalog.KIND_NUMBER.equals(kind)
                    || GameBlockCatalog.KIND_TEXT.equals(kind)
                    || GameBlockCatalog.KIND_EXPR.equals(kind)
                    || GameBlockCatalog.KIND_COND.equals(kind);

            if (useChipMode) {
                HorizontalScrollView chipScroll = new HorizontalScrollView(ctx);
                chipScroll.setHorizontalScrollBarEnabled(false);
                LinearLayout chipRow = new LinearLayout(ctx);
                chipRow.setOrientation(LinearLayout.HORIZONTAL);
                chipScroll.addView(chipRow);
                container.addView(chipScroll);

                for (String s : suggestions) {
                    TextView chip = new TextView(ctx);
                    chip.setText(s);
                    chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
                    chip.setTypeface(null, Typeface.BOLD);
                    chip.setTextColor(colorTextPrimary);
                    chip.setPadding(dp(12), dp(6), dp(12), dp(6));
                    GradientDrawable chipBg = new GradientDrawable();
                    chipBg.setCornerRadius(dp(14));
                    chipBg.setColor(colorSurfaceAlt);
                    chipBg.setStroke(dp(1), colorBorder);
                    chip.setBackground(chipBg);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                    lp.rightMargin = dp(6);
                    chip.setLayoutParams(lp);
                    chip.setOnClickListener(v -> {
                        if (GameBlockCatalog.KIND_TEXT.equals(kind) && s.startsWith("{") && s.endsWith("}")) {
                            int pos = Math.max(0, input.getSelectionStart());
                            input.getText().insert(pos, s);
                        } else {
                            input.setText(s);
                            input.setSelection(s.length());
                        }
                    });
                    chipRow.addView(chip);
                }
            } else {
                ScrollView listScroll = new ScrollView(ctx);
                LinearLayout.LayoutParams listScrollLp = new LinearLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
                listScrollLp.height = suggestions.size() > 6 ? dp(210) : LayoutParams.WRAP_CONTENT;
                listScroll.setLayoutParams(listScrollLp);
                container.addView(listScroll);

                LinearLayout listCol = new LinearLayout(ctx);
                listCol.setOrientation(LinearLayout.VERTICAL);
                listScroll.addView(listCol);

                for (String s : suggestions) {
                    TextView row = new TextView(ctx);
                    row.setText(s);
                    row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
                    row.setTextColor(colorTextPrimary);
                    row.setPadding(dp(12), dp(10), dp(12), dp(10));
                    GradientDrawable rowBg = new GradientDrawable();
                    rowBg.setCornerRadius(dp(8));
                    rowBg.setColor(colorSurfaceAlt);
                    row.setBackground(rowBg);
                    LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
                    rLp.bottomMargin = dp(6);
                    row.setLayoutParams(rLp);
                    row.setOnClickListener(v -> {
                        String cleanValue = stripDisplayTag(s);
                        if ((GameBlockCatalog.KIND_VAR.equals(kind) || GameBlockCatalog.KIND_VAR_REF.equals(kind))
                                && project != null) {
                            if (project.globalVariables == null) {
                                project.globalVariables = new HashMap<>();
                            }
                            if (!project.globalVariables.containsKey(cleanValue)) {
                                project.globalVariables.put(cleanValue, "0");
                            }
                        }
                        if (dialogHolder[0] != null) {
                            dialogHolder[0].dismiss();
                        }
                        callback.onValueChosen(cleanValue);
                    });
                    listCol.addView(row);
                }
            }
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(ctx)
                .setTitle(title)
                .setView(container)
                .setPositiveButton("Apply", (d, w) -> {
                    String val = input.getText().toString().trim();
                    if ((GameBlockCatalog.KIND_VAR.equals(kind) || GameBlockCatalog.KIND_VAR_REF.equals(kind))
                            && !val.isEmpty() && project != null) {
                        if (project.globalVariables == null) {
                            project.globalVariables = new HashMap<>();
                        }
                        if (!project.globalVariables.containsKey(val)) {
                            project.globalVariables.put(val, "0");
                        }
                        renderPaletteBlocks();
                    }
                    callback.onValueChosen(val);
                })
                .setNegativeButton("Cancel", null);

        dialogHolder[0] = builder.show();
    }

    private void addConditionBuilderRow(Context ctx, LinearLayout container, EditText targetInput) {
        TextView label = new TextView(ctx);
        label.setText("Quick comparison builder:");
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        label.setTextColor(colorTextSecondary);
        label.setPadding(0, dp(10), 0, dp(4));
        container.addView(label);

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(6), dp(8), dp(6));
        GradientDrawable rowBg = new GradientDrawable();
        rowBg.setCornerRadius(dp(10));
        rowBg.setColor(colorSurfaceAlt);
        rowBg.setStroke(dp(1), colorBorder);
        row.setBackground(rowBg);
        container.addView(row);

        final EditText leftEt = new EditText(ctx);
        leftEt.setHint("var");
        leftEt.setText("score");
        leftEt.setSingleLine(true);
        leftEt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        leftEt.setTextColor(colorTextPrimary);
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        leftEt.setLayoutParams(leftLp);
        row.addView(leftEt);

        final String[] ops = {"==", "!=", ">=", "<=", ">", "<"};
        final int[] opIdx = {2};
        final TextView opChip = new TextView(ctx);
        opChip.setText(ops[opIdx[0]]);
        opChip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        opChip.setTypeface(null, Typeface.BOLD);
        opChip.setTextColor(colorAccent);
        opChip.setPadding(dp(10), dp(6), dp(10), dp(6));
        GradientDrawable opBg = new GradientDrawable();
        opBg.setCornerRadius(dp(8));
        opBg.setColor(colorSurface);
        opBg.setStroke(dp(1), colorBorder);
        opChip.setBackground(opBg);
        LinearLayout.LayoutParams opLp = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        opLp.leftMargin = dp(4);
        opLp.rightMargin = dp(4);
        opChip.setLayoutParams(opLp);
        opChip.setOnClickListener(v -> {
            opIdx[0] = (opIdx[0] + 1) % ops.length;
            opChip.setText(ops[opIdx[0]]);
        });
        row.addView(opChip);

        final EditText rightEt = new EditText(ctx);
        rightEt.setHint("value");
        rightEt.setText("10");
        rightEt.setSingleLine(true);
        rightEt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        rightEt.setTextColor(colorTextPrimary);
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        rightEt.setLayoutParams(rightLp);
        row.addView(rightEt);

        TextView applyBtn = new TextView(ctx);
        applyBtn.setText("Use");
        applyBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        applyBtn.setTypeface(null, Typeface.BOLD);
        applyBtn.setTextColor(Color.WHITE);
        applyBtn.setPadding(dp(10), dp(6), dp(10), dp(6));
        GradientDrawable applyBg = new GradientDrawable();
        applyBg.setCornerRadius(dp(8));
        applyBg.setColor(colorAccent);
        applyBtn.setBackground(applyBg);
        LinearLayout.LayoutParams applyLp = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        applyLp.leftMargin = dp(6);
        applyBtn.setLayoutParams(applyLp);
        applyBtn.setOnClickListener(v -> {
            String l = leftEt.getText().toString().trim();
            String r = rightEt.getText().toString().trim();
            if (l.isEmpty()) l = "score";
            if (r.isEmpty()) r = "0";
            String built = l + " " + ops[opIdx[0]] + " " + r;
            targetInput.setText(built);
            targetInput.setSelection(built.length());
        });
        row.addView(applyBtn);
    }

    private void showColorPickerDialog(String title, String currentValue, ValueCallback callback) {
        Context ctx = getContext();
        LinearLayout container = new LinearLayout(ctx);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(20), dp(12), dp(20), dp(4));

        final View preview = new View(ctx);
        LinearLayout.LayoutParams prevLp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(36));
        prevLp.bottomMargin = dp(10);
        preview.setLayoutParams(prevLp);
        container.addView(preview);

        final EditText hexInput = new EditText(ctx);
        hexInput.setSingleLine(true);
        hexInput.setHint("#RRGGBB");
        hexInput.setText(!TextUtils.isEmpty(currentValue) ? currentValue : "#FF5252");
        hexInput.setSelection(hexInput.getText().length());
        container.addView(hexInput);

        Runnable refreshPreview = () -> {
            int parsed = parseColorSafe(hexInput.getText().toString(), 0xFFFF5252);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(8));
            bg.setColor(parsed);
            bg.setStroke(dp(1), colorBorder);
            preview.setBackground(bg);
        };
        refreshPreview.run();

        hexInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshPreview.run();
            }
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        LinearLayout grid = new LinearLayout(ctx);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        gridLp.topMargin = dp(10);
        grid.setLayoutParams(gridLp);
        container.addView(grid);

        int cols = 6;
        LinearLayout currentRow = null;
        for (int i = 0; i < GAME_COLOR_SWATCHES.length; i++) {
            if (i % cols == 0) {
                currentRow = new LinearLayout(ctx);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                currentRow.setGravity(Gravity.CENTER_HORIZONTAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
                rowLp.bottomMargin = dp(8);
                currentRow.setLayoutParams(rowLp);
                grid.addView(currentRow);
            }
            final String hex = GAME_COLOR_SWATCHES[i];
            View swatch = new View(ctx);
            GradientDrawable swBg = new GradientDrawable();
            swBg.setShape(GradientDrawable.OVAL);
            swBg.setColor(Color.parseColor(hex));
            swBg.setStroke(dp(1), colorBorder);
            swatch.setBackground(swBg);
            LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(dp(34), dp(34));
            swLp.setMargins(dp(4), 0, dp(4), 0);
            swatch.setLayoutParams(swLp);
            swatch.setOnClickListener(v -> {
                hexInput.setText(hex);
                hexInput.setSelection(hex.length());
            });
            currentRow.addView(swatch);
        }

        new MaterialAlertDialogBuilder(ctx)
                .setTitle(title)
                .setView(container)
                .setPositiveButton("Apply", (d, w) -> {
                    String text = hexInput.getText().toString().trim();
                    if (!text.startsWith("#") && (text.length() == 6 || text.length() == 8)) {
                        text = "#" + text;
                    }
                    callback.onValueChosen(text.isEmpty() ? "#FFFFFF" : text);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private List<String> buildSuggestionsForKind(String kind) {
        Set<String> out = new LinkedHashSet<>();
        switch (kind) {
            case GameBlockCatalog.KIND_SPRITE:
                refreshProjectResources();
                out.addAll(cachedImageNames);
                break;

            case GameBlockCatalog.KIND_SOUND:
                refreshProjectResources();
                out.addAll(cachedSoundNames);
                break;

            case GameBlockCatalog.KIND_SCENE:
                if (project != null && project.scenes != null) {
                    for (GameScene s : project.scenes) {
                        if (s != null && !TextUtils.isEmpty(s.name)) {
                            out.add(s.name);
                        }
                    }
                }
                break;

            case GameBlockCatalog.KIND_OBJECT:
                out.add("myself");
                out.add("touch");
                out.add("random");
                out.add("edge");
                if (scene != null && scene.objects != null) {
                    for (GameObjectBean obj : scene.objects) {
                        if (obj != null && !TextUtils.isEmpty(obj.name)) {
                            out.add(obj.name);
                        }
                    }
                }
                break;

            case GameBlockCatalog.KIND_HUD:
                if (scene != null && scene.objects != null) {
                    for (GameObjectBean obj : scene.objects) {
                        if (obj != null && obj.isHud && !TextUtils.isEmpty(obj.name)) {
                            out.add(obj.name);
                        }
                    }
                }
                break;

            case GameBlockCatalog.KIND_PREFAB:
                if (project != null && project.prefabs != null) {
                    for (GameObjectBean p : project.prefabs) {
                        if (p != null && !TextUtils.isEmpty(p.name)) {
                            out.add(p.name);
                        }
                    }
                }
                if (scene != null && scene.objects != null) {
                    for (GameObjectBean obj : scene.objects) {
                        if (obj != null && !obj.isHud && !TextUtils.isEmpty(obj.name)) {
                            out.add(obj.name);
                        }
                    }
                }
                break;

            case GameBlockCatalog.KIND_TAG:
                if (scene != null && scene.objects != null) {
                    for (GameObjectBean obj : scene.objects) {
                        if (obj != null && !TextUtils.isEmpty(obj.tag)) {
                            out.add(obj.tag);
                        }
                    }
                }
                if (project != null && project.prefabs != null) {
                    for (GameObjectBean p : project.prefabs) {
                        if (p != null && !TextUtils.isEmpty(p.tag)) {
                            out.add(p.tag);
                        }
                    }
                }
                out.addAll(Arrays.asList("coin", "enemy", "ground", "player"));
                break;

            case GameBlockCatalog.KIND_MESSAGE:
                if (scene != null && scene.scripts != null) {
                    for (GameScriptNode node : scene.scripts) {
                        collectMessagesFromScriptNode(node, out);
                    }
                }
                if (project != null && project.scenes != null) {
                    for (GameScene s : project.scenes) {
                        if (s != null && s.scripts != null) {
                            for (GameScriptNode node : s.scripts) {
                                collectMessagesFromScriptNode(node, out);
                            }
                        }
                    }
                }
                break;

            case GameBlockCatalog.KIND_ACTION:
                if (scene != null && scene.objects != null) {
                    for (GameObjectBean obj : scene.objects) {
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

            case GameBlockCatalog.KIND_VAR:
            case GameBlockCatalog.KIND_VAR_REF:
                if (project != null && project.globalVariables != null) {
                    out.addAll(project.globalVariables.keySet());
                }
                out.addAll(Arrays.asList("score", "hp", "coins", "level", "speed", "timer"));
                break;

            case GameBlockCatalog.KIND_COND:
                out.addAll(Arrays.asList(
                        "isOnGround",
                        "isTouching",
                        "abs(joyX) > 0.1 || abs(joyY) > 0.1",
                        "score >= 10",
                        "hp <= 0",
                        "isButtonPressed(\"JUMP\")",
                        "isCollidingTag(\"coin\")",
                        "selfX > 640",
                        "true",
                        "false"
                ));
                if (project != null && project.globalVariables != null) {
                    for (String k : project.globalVariables.keySet()) {
                        out.add(k + " >= 1");
                    }
                }
                break;

            case GameBlockCatalog.KIND_NUMBER:
                out.addAll(Arrays.asList("0", "1", "-1", "5", "10", "100", "0.5",
                        "score", "joyX", "joyY", "selfX", "selfY", "selfVX", "selfVY",
                        "touchX", "touchY", "random(1, 10)"));
                if (project != null && project.globalVariables != null) {
                    out.addAll(project.globalVariables.keySet());
                }
                break;

            case GameBlockCatalog.KIND_EXPR:
                if (project != null && project.globalVariables != null) {
                    out.addAll(project.globalVariables.keySet());
                }
                out.addAll(Arrays.asList("joyX * 6", "joyY * 6", "random(1, 10)",
                        "score", "hp", "joyX", "joyY", "selfX", "selfY", "selfVX", "selfVY", "1", "0"));
                break;

            case GameBlockCatalog.KIND_TEXT:
                out.add("Score: {score}");
                out.add("\"Score: \" + score");
                if (project != null && project.globalVariables != null) {
                    for (String k : project.globalVariables.keySet()) {
                        out.add("{" + k + "}");
                    }
                }
                out.addAll(Arrays.asList("HP: {hp}", "Game Over!", "Victory!"));
                break;

            default:
                break;
        }
        return new ArrayList<>(out);
    }

    private static void collectMessagesFromScriptNode(@Nullable GameScriptNode node, Set<String> out) {
        if (node == null) {
            return;
        }
        if (GameScriptNode.EVENT_MESSAGE.equals(node.eventType) && !TextUtils.isEmpty(node.eventParam)) {
            out.add(node.eventParam);
        }
        collectMessagesFromBlockList(node.blocks, out);
    }

    private static void collectMessagesFromBlockList(@Nullable List<GameBlockItem> blocks, Set<String> out) {
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

    private static String stripDisplayTag(String raw) {
        int tagIdx = raw.indexOf("  [");
        return tagIdx > 0 ? raw.substring(0, tagIdx).trim() : raw;
    }

    // -------------------------------------------------------------------------
    // Drag & Drop Listeners and Payload
    // -------------------------------------------------------------------------

    @Nullable
    private View highlightedSlotView;

    private static final class DragPayload {
        final boolean fromPalette;
        final BlockDef paletteDef;
        @Nullable
        final String preboundParam0;
        final GameBlockItem existingBlock;
        final ArrayList<GameBlockItem> sourceList;
        final int sourceIndex;
        @Nullable
        final GameBlockItem parentBlock;
        final int parentParamIndex;
        final View sourceView;
        final String blockType;

        DragPayload(BlockDef paletteDef, @Nullable String preboundParam0) {
            this.fromPalette = true;
            this.paletteDef = paletteDef;
            this.preboundParam0 = preboundParam0;
            this.existingBlock = null;
            this.sourceList = null;
            this.sourceIndex = -1;
            this.parentBlock = null;
            this.parentParamIndex = -1;
            this.sourceView = null;
            this.blockType = paletteDef != null ? normalizeBlockType(paletteDef.type) : " ";
        }

        DragPayload(GameBlockItem existingBlock, ArrayList<GameBlockItem> sourceList,
                    int sourceIndex, View sourceView) {
            this.fromPalette = false;
            this.paletteDef = null;
            this.preboundParam0 = null;
            this.existingBlock = existingBlock;
            this.sourceList = sourceList;
            this.sourceIndex = sourceIndex;
            this.parentBlock = null;
            this.parentParamIndex = -1;
            this.sourceView = sourceView;
            this.blockType = existingBlock != null ? normalizeBlockType(existingBlock.type) : " ";
        }

        DragPayload(GameBlockItem existingBlock, GameBlockItem parentBlock,
                    int parentParamIndex, View sourceView) {
            this.fromPalette = false;
            this.paletteDef = null;
            this.preboundParam0 = null;
            this.existingBlock = existingBlock;
            this.sourceList = null;
            this.sourceIndex = -1;
            this.parentBlock = parentBlock;
            this.parentParamIndex = parentParamIndex;
            this.sourceView = sourceView;
            this.blockType = existingBlock != null ? normalizeBlockType(existingBlock.type) : " ";
        }

        boolean isReporterOrBool() {
            return "d".equals(blockType) || "b".equals(blockType) || "n".equals(blockType);
        }

        @Nullable
        GameBlockItem buildPaletteItem() {
            GameBlockItem created = GameBlockCatalog.createBlockInstance(paletteDef);
            if (created != null && preboundParam0 != null && !preboundParam0.isEmpty()) {
                if (created.parameters == null) {
                    created.parameters = new ArrayList<>();
                }
                if (created.parameters.isEmpty()) {
                    created.parameters.add(preboundParam0);
                } else {
                    created.parameters.set(0, preboundParam0);
                }
            }
            return created;
        }
    }

    private static boolean isDescendantList(@Nullable GameBlockItem root,
                                            @Nullable ArrayList<GameBlockItem> candidate) {
        if (root == null || candidate == null) {
            return false;
        }
        if (root.subStack1 == candidate || root.subStack2 == candidate) {
            return true;
        }
        if (root.subStack1 != null) {
            for (GameBlockItem c : root.subStack1) {
                if (isDescendantList(c, candidate)) {
                    return true;
                }
            }
        }
        if (root.subStack2 != null) {
            for (GameBlockItem c : root.subStack2) {
                if (isDescendantList(c, candidate)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isBlockAncestorOrEqual(@Nullable GameBlockItem root,
                                                  @Nullable GameBlockItem target) {
        if (root == null || target == null) {
            return false;
        }
        if (root == target) {
            return true;
        }
        if (root.paramBlocks != null) {
            for (GameBlockItem c : root.paramBlocks) {
                if (isBlockAncestorOrEqual(c, target)) {
                    return true;
                }
            }
        }
        if (root.subStack1 != null) {
            for (GameBlockItem c : root.subStack1) {
                if (isBlockAncestorOrEqual(c, target)) {
                    return true;
                }
            }
        }
        if (root.subStack2 != null) {
            for (GameBlockItem c : root.subStack2) {
                if (isBlockAncestorOrEqual(c, target)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void triggerDragHaptic() {
        if (vibrator == null) {
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(100L, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(100L);
            }
        } catch (Throwable ignored) {
        }
    }

    private void showLogicTopMenu(boolean show) {
        if (logicTopMenu == null) {
            return;
        }
        if (topDeleteAnimator != null) {
            topDeleteAnimator.cancel();
            topDeleteAnimator = null;
        }
        if (show) {
            logicTopMenu.setDeleteActive(false);
            logicTopMenu.setCopyActive(false);
            logicTopMenu.setVisibility(VISIBLE);
            logicTopMenu.setTranslationY(-dp(48));
            topDeleteAnimator = ObjectAnimator.ofFloat(logicTopMenu, View.TRANSLATION_Y, 0f);
            topDeleteAnimator.setDuration(220L);
            topDeleteAnimator.setInterpolator(new DecelerateInterpolator());
            topDeleteAnimator.start();
        } else {
            logicTopMenu.setDeleteActive(false);
            logicTopMenu.setCopyActive(false);
            topDeleteAnimator = ObjectAnimator.ofFloat(logicTopMenu, View.TRANSLATION_Y, -dp(48));
            topDeleteAnimator.setDuration(150L);
            topDeleteAnimator.setInterpolator(new DecelerateInterpolator());
            topDeleteAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    if (logicTopMenu != null && logicTopMenu.getTranslationY() <= -dp(47)) {
                        logicTopMenu.setVisibility(GONE);
                    }
                }
            });
            topDeleteAnimator.start();
        }
    }

    private void clearDropIndicatorsRecursively(@Nullable BlockStackLayout stack) {
        if (stack == null) {
            return;
        }
        stack.setDropIndicator(false, -1);
        int count = stack.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = stack.getChildAt(i);
            if (child instanceof GameBlockView) {
                GameBlockView gv = (GameBlockView) child;
                clearDropIndicatorsRecursively(gv.subStack1Layout);
                clearDropIndicatorsRecursively(gv.subStack2Layout);
            }
        }
    }

    private void setSlotHighlight(@Nullable View v) {
        if (highlightedSlotView == v) {
            return;
        }
        if (highlightedSlotView != null) {
            highlightedSlotView.setScaleX(1f);
            highlightedSlotView.setScaleY(1f);
            highlightedSlotView.setAlpha(1f);
        }
        highlightedSlotView = v;
        if (v != null) {
            v.setScaleX(1.08f);
            v.setScaleY(1.08f);
            v.setAlpha(0.85f);
        }
    }

    private void clearAllDropIndicators() {
        clearDropIndicatorsRecursively(rootStackLayout);
        for (BlockStackLayout fs : floatingStackLayouts) {
            clearDropIndicatorsRecursively(fs);
        }
        setSlotHighlight(null);
    }

    private void updateDragVisuals(View targetView, float localX, float localY, boolean inValidZone) {
        if (viewDummy == null || activeDragSourceView == null) {
            return;
        }
        int[] targetLoc = new int[2];
        targetView.getLocationOnScreen(targetLoc);
        float rawX = targetLoc[0] + localX;
        float rawY = targetLoc[1] + localY;

        boolean isWorkspaceDrag = activeDragPayload != null && !activeDragPayload.fromPalette;
        boolean overDelete = false;
        boolean overCopy = false;
        if (isWorkspaceDrag && logicTopMenu != null && logicTopMenu.getVisibility() == VISIBLE) {
            overDelete = logicTopMenu.isInsideDeleteArea(rawX, rawY);
            overCopy = !overDelete && logicTopMenu.isInsideCopyArea(rawX, rawY);
            logicTopMenu.setDeleteActive(overDelete);
            logicTopMenu.setCopyActive(overCopy);
        } else if (logicTopMenu != null) {
            logicTopMenu.setDeleteActive(false);
            logicTopMenu.setCopyActive(false);
        }

        viewDummy.setAllow(inValidZone || overDelete || overCopy);
        viewDummy.a(activeDragSourceView, rawX, rawY, dragInitRawX, dragInitRawY, 0f, 0f);
    }

    private void startBlockDrag(View sourceView, DragPayload payload, String label,
                                float downRawX, float downRawY, float currentRawX, float currentRawY) {
        android.util.Log.d("GameBlocksDrag", "startBlockDrag: label=" + label
                + " fromPalette=" + (payload != null && payload.fromPalette)
                + " hasSourceList=" + (payload != null && payload.sourceList != null)
                + " hasParent=" + (payload != null && payload.parentBlock != null)
                + " down=(" + downRawX + "," + downRawY + ") cur=(" + currentRawX + "," + currentRawY + ")");
        triggerDragHaptic();
        activeDragSourceView = sourceView;
        activeDragPayload = payload;
        dragInitRawX = downRawX;
        dragInitRawY = downRawY;

        if (viewDummy != null) {
            viewDummy.b(sourceView);
            viewDummy.setAllow(false);
            viewDummy.a(sourceView, currentRawX, currentRawY, dragInitRawX, dragInitRawY, 0f, 0f);
            viewDummy.setDummyVisibility(VISIBLE);
        }

        ClipData clip = ClipData.newPlainText("game_block", label != null ? label : "block");
        View.DragShadowBuilder shadow = new View.DragShadowBuilder(sourceView) {
            @Override
            public void onProvideShadowMetrics(Point outShadowSize, Point outShadowTouchPoint) {
                outShadowSize.set(1, 1);
                outShadowTouchPoint.set(0, 0);
            }

            @Override
            public void onDrawShadow(Canvas canvas) {
                // Rendered by native ViewDummy overlay
            }
        };
        boolean started = sourceView.startDragAndDrop(clip, shadow, payload, 0);
        if (started) {
            if (workspaceVScroll != null) {
                workspaceVScroll.a();
            }
            if (workspaceHScroll != null) {
                workspaceHScroll.a();
            }
            if (!payload.fromPalette && payload.sourceView != null) {
                payload.sourceView.setAlpha(0.25f);
                showLogicTopMenu(true);
            }
            if (payload.fromPalette && paletteOpen) {
                paletteTempHiddenForDrag = true;
                post(() -> {
                    applyPaletteTranslation(false, true);
                    updateWorkspaceMarginsForPalette();
                });
            }
        } else {
            endBlockDragUi(payload);
        }
    }

    private void endBlockDragUi(@Nullable DragPayload payload) {
        if (activeDragPayload == null && activeDragSourceView == null
                && (viewDummy == null || viewDummy.getVisibility() != VISIBLE)) {
            return;
        }
        clearAllDropIndicators();
        if (workspaceVScroll != null) {
            workspaceVScroll.b();
        }
        if (workspaceHScroll != null) {
            workspaceHScroll.b();
        }
        activeDragSourceView = null;
        activeDragPayload = null;
        if (payload != null && payload.sourceView != null) {
            payload.sourceView.setAlpha(1f);
        }
        if (viewDummy != null) {
            viewDummy.setAllow(false);
            viewDummy.setDummyVisibility(GONE);
        }
        showLogicTopMenu(false);
        if (paletteTempHiddenForDrag) {
            paletteTempHiddenForDrag = false;
            applyPaletteTranslation(paletteOpen, true);
            updateWorkspaceMarginsForPalette();
        }
    }

    private void restoreDefaultParamValue(GameBlockItem parentBlock, int paramIndex) {
        if (parentBlock == null || paramIndex < 0) {
            return;
        }
        BlockDef def = GameBlockCatalog.findByOpCode(parentBlock.opCode);
        if (def != null && paramIndex < def.defaultParams.length) {
            if (parentBlock.parameters == null) {
                parentBlock.parameters = new ArrayList<>();
            }
            while (parentBlock.parameters.size() <= paramIndex) {
                parentBlock.parameters.add("");
            }
            parentBlock.parameters.set(paramIndex, def.defaultParams[paramIndex]);
        }
    }

    private boolean acceptDropIntoList(DragPayload payload, ArrayList<GameBlockItem> targetList, int insertIndex) {
        if (payload == null || targetList == null || payload.isReporterOrBool()) {
            return false;
        }
        boolean isRootTarget = (rootStackLayout != null && targetList == rootStackLayout.boundList);
        ensureActiveScript();
        if (isRootTarget && currentScript != null && currentScript.blocks != null) {
            targetList = currentScript.blocks;
        }
        if (payload.fromPalette) {
            GameBlockItem created = payload.buildPaletteItem();
            if (created == null) {
                return false;
            }
            int safeIdx = Math.max(0, Math.min(insertIndex, targetList.size()));
            targetList.add(safeIdx, created);
            syncAllNestedExpressions();
            renderWorkspace();
            notifyModified();
            return true;
        }

        if (payload.existingBlock == null || payload.sourceList == null) {
            return false;
        }
        if (isDescendantList(payload.existingBlock, targetList)) {
            return false;
        }

        int targetIdx = Math.max(0, Math.min(insertIndex, targetList.size()));
        int actualSourceIdx = indexOfIdentity(payload.sourceList, payload.existingBlock);
        if (actualSourceIdx < 0) {
            return false;
        }

        if (isFloatingStack(payload.sourceList) && actualSourceIdx == 0 && payload.sourceList != targetList) {
            ArrayList<GameBlockItem> moving = new ArrayList<>(payload.sourceList);
            payload.sourceList.clear();
            targetIdx = Math.max(0, Math.min(targetIdx, targetList.size()));
            targetList.addAll(targetIdx, moving);
            pruneEmptyFloatingStacks();
            syncAllNestedExpressions();
            renderWorkspace();
            notifyModified();
            return true;
        }

        if (payload.sourceList == targetList && (targetIdx == actualSourceIdx || targetIdx == actualSourceIdx + 1)) {
            return true; // Dropped back in exact same slot
        }

        payload.sourceList.remove(actualSourceIdx);
        if (payload.sourceList == targetList && actualSourceIdx < targetIdx) {
            targetIdx--;
        }
        targetIdx = Math.max(0, Math.min(targetIdx, targetList.size()));
        targetList.add(targetIdx, payload.existingBlock);
        pruneEmptyFloatingStacks();
        syncAllNestedExpressions();
        renderWorkspace();
        notifyModified();
        return true;
    }

    private boolean canAcceptSlotDrop(DragPayload payload, GameBlockItem targetBlock, int paramIndex) {
        if (payload == null || targetBlock == null) {
            return false;
        }
        BlockDef targetDef = GameBlockCatalog.findByOpCode(targetBlock.opCode);
        String targetKind = resolveParamKind(targetBlock, targetDef, paramIndex);
        GameBlockItem candidate = payload.fromPalette ? payload.buildPaletteItem() : payload.existingBlock;
        if (!GameBlockCatalog.canAcceptNestedBlock(targetKind, candidate)) {
            return false;
        }
        return payload.fromPalette || !isBlockAncestorOrEqual(payload.existingBlock, targetBlock);
    }

    private boolean acceptDropIntoSlot(DragPayload payload, GameBlockItem targetBlock, int paramIndex) {
        if (!canAcceptSlotDrop(payload, targetBlock, paramIndex)) {
            return false;
        }
        ensureActiveScript();
        GameBlockItem incoming;
        if (payload.fromPalette) {
            incoming = payload.buildPaletteItem();
            if (incoming == null) {
                return false;
            }
        } else {
            incoming = payload.existingBlock;
            if (payload.parentBlock == targetBlock && payload.parentParamIndex == paramIndex) {
                return true;
            }
            if (payload.parentBlock != null) {
                payload.parentBlock.setParamBlock(payload.parentParamIndex, null);
                restoreDefaultParamValue(payload.parentBlock, payload.parentParamIndex);
            } else if (payload.sourceList != null) {
                int idx = indexOfIdentity(payload.sourceList, incoming);
                if (idx >= 0) {
                    payload.sourceList.remove(idx);
                }
            }
        }

        GameBlockItem displaced = targetBlock.getParamBlock(paramIndex);
        if (displaced != null && displaced != incoming && currentScript != null) {
            if (currentScript.floatingStacks == null) {
                currentScript.floatingStacks = new ArrayList<>();
            }
            displaced.freeX = dp(32) + (currentScript.floatingStacks.size() * dp(18)) % dp(140);
            displaced.freeY = Math.max(dp(120), mainChainContainer.getHeight() + dp(16));
            ArrayList<GameBlockItem> ejected = new ArrayList<>();
            ejected.add(displaced);
            currentScript.floatingStacks.add(ejected);
        }

        targetBlock.setParamBlock(paramIndex, incoming);
        pruneEmptyFloatingStacks();
        syncAllNestedExpressions();
        renderWorkspace();
        notifyModified();
        return true;
    }

    private boolean acceptDropIntoCanvas(DragPayload payload, float dropX, float dropY) {
        android.util.Log.d("GameBlocksDrag", "acceptDropIntoCanvas: fromPalette="
                + (payload != null && payload.fromPalette)
                + " opCode=" + (payload != null && payload.existingBlock != null ? payload.existingBlock.opCode : "palette")
                + " drop=(" + dropX + "," + dropY + ")");
        if (payload == null) {
            return false;
        }
        ensureActiveScript();
        if (currentScript == null) {
            return false;
        }
        if (currentScript.floatingStacks == null) {
            currentScript.floatingStacks = new ArrayList<>();
        }

        int targetX = Math.max(dp(8), Math.round(dropX) - dp(28));
        int targetY = Math.max(dp(8), Math.round(dropY) - dp(16));

        if (payload.fromPalette) {
            GameBlockItem created = payload.buildPaletteItem();
            if (created == null) {
                return false;
            }
            created.freeX = targetX;
            created.freeY = targetY;
            ArrayList<GameBlockItem> single = new ArrayList<>();
            single.add(created);
            currentScript.floatingStacks.add(single);
            syncAllNestedExpressions();
            renderWorkspace();
            notifyModified();
            return true;
        }

        if (payload.existingBlock == null) {
            return false;
        }

        if (payload.parentBlock != null) {
            payload.parentBlock.setParamBlock(payload.parentParamIndex, null);
            restoreDefaultParamValue(payload.parentBlock, payload.parentParamIndex);
            payload.existingBlock.freeX = targetX;
            payload.existingBlock.freeY = targetY;
            ArrayList<GameBlockItem> single = new ArrayList<>();
            single.add(payload.existingBlock);
            currentScript.floatingStacks.add(single);
        } else if (payload.sourceList != null) {
            int idx = indexOfIdentity(payload.sourceList, payload.existingBlock);
            if (idx < 0) {
                return false;
            }
            if (isFloatingStack(payload.sourceList) && idx == 0) {
                payload.existingBlock.freeX = targetX;
                payload.existingBlock.freeY = targetY;
            } else {
                payload.sourceList.remove(idx);
                payload.existingBlock.freeX = targetX;
                payload.existingBlock.freeY = targetY;
                ArrayList<GameBlockItem> single = new ArrayList<>();
                single.add(payload.existingBlock);
                currentScript.floatingStacks.add(single);
            }
        } else {
            return false;
        }

        pruneEmptyFloatingStacks();
        syncAllNestedExpressions();
        renderWorkspace();
        notifyModified();
        return true;
    }

    private static int indexOfIdentity(List<GameBlockItem> list, GameBlockItem target) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == target) {
                return i;
            }
        }
        return -1;
    }

    private final class PaletteBlockTouchListener implements OnTouchListener {
        private final BlockDef def;
        private final GameBlockView view;
        @Nullable
        private final String preboundParam0;
        private float downX;
        private float downY;
        private float downRawX;
        private float downRawY;
        private boolean dragStarted;
        private final int touchSlop;
        private final int longPressDelayMs;
        private final Runnable longPressRunnable;

        PaletteBlockTouchListener(BlockDef def, GameBlockView view) {
            this(def, view, null);
        }

        PaletteBlockTouchListener(BlockDef def, GameBlockView view, @Nullable String preboundParam0) {
            this.def = def;
            this.view = view;
            this.preboundParam0 = preboundParam0;
            ViewConfiguration vc = ViewConfiguration.get(view.getContext());
            this.touchSlop = vc.getScaledTouchSlop();
            this.longPressDelayMs = Math.max(120, ViewConfiguration.getLongPressTimeout() / 2);
            this.longPressRunnable = () -> {
                if (!dragStarted) {
                    dragStarted = true;
                    if (view.getParent() != null) {
                        view.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    startBlockDrag(view, new DragPayload(def, preboundParam0), def.opCode,
                            downRawX, downRawY, downRawX, downRawY);
                }
            };
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getX();
                    downY = event.getY();
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    dragStarted = false;
                    v.postDelayed(longPressRunnable, longPressDelayMs);
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (dragStarted) {
                        return true;
                    }
                    float dx = event.getX() - downX;
                    float dy = event.getY() - downY;
                    if (Math.abs(dy) > touchSlop && Math.abs(dy) >= Math.abs(dx)) {
                        v.removeCallbacks(longPressRunnable);
                        return false;
                    }
                    if (isLandscapeOrientation() && dx < -touchSlop * 0.85f && Math.abs(dx) > Math.abs(dy) * 1.35f) {
                        v.removeCallbacks(longPressRunnable);
                        dragStarted = true;
                        if (v.getParent() != null) {
                            v.getParent().requestDisallowInterceptTouchEvent(true);
                        }
                        startBlockDrag(v, new DragPayload(def, preboundParam0), def.opCode,
                                downRawX, downRawY, event.getRawX(), event.getRawY());
                        return true;
                    }
                    if (Math.abs(dx) > touchSlop && !isLandscapeOrientation() && Math.abs(dx) >= Math.abs(dy)) {
                        v.removeCallbacks(longPressRunnable);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    v.removeCallbacks(longPressRunnable);
                    if (!dragStarted) {
                        float upDx = Math.abs(event.getX() - downX);
                        float upDy = Math.abs(event.getY() - downY);
                        if (upDx < touchSlop && upDy < touchSlop) {
                            appendPaletteBlockToRoot(def, preboundParam0);
                        }
                    }
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    v.removeCallbacks(longPressRunnable);
                    return true;

                default:
                    return false;
            }
        }
    }

    private final class WorkspaceBlockTouchListener implements OnTouchListener {
        private final GameBlockItem item;
        private final ArrayList<GameBlockItem> sourceList;
        private final int sourceIndex;
        private final GameBlockView view;
        private float downX;
        private float downY;
        private float downRawX;
        private float downRawY;
        private boolean dragStarted;
        private final int touchSlop;
        private final int longPressDelayMs;
        private final Runnable longPressRunnable;

        WorkspaceBlockTouchListener(GameBlockItem item, ArrayList<GameBlockItem> sourceList,
                                    int sourceIndex, GameBlockView view) {
            this.item = item;
            this.sourceList = sourceList;
            this.sourceIndex = sourceIndex;
            this.view = view;
            ViewConfiguration vc = ViewConfiguration.get(view.getContext());
            this.touchSlop = vc.getScaledTouchSlop();
            this.longPressDelayMs = Math.max(120, ViewConfiguration.getLongPressTimeout() / 2);
            this.longPressRunnable = () -> {
                if (!dragStarted) {
                    dragStarted = true;
                    if (view.getParent() != null) {
                        view.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    startBlockDrag(view, new DragPayload(item, sourceList, sourceIndex, view),
                            item.opCode, downRawX, downRawY, downRawX, downRawY);
                }
            };
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getX();
                    downY = event.getY();
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    dragStarted = false;
                    v.postDelayed(longPressRunnable, longPressDelayMs);
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (dragStarted) {
                        return true;
                    }
                    float dx = Math.abs(event.getX() - downX);
                    float dy = Math.abs(event.getY() - downY);
                    if (dx > touchSlop || dy > touchSlop) {
                        v.removeCallbacks(longPressRunnable);
                        return false;
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    v.removeCallbacks(longPressRunnable);
                    if (!dragStarted) {
                        float upDx = Math.abs(event.getX() - downX);
                        float upDy = Math.abs(event.getY() - downY);
                        if (upDx < touchSlop && upDy < touchSlop) {
                            showBlockQuickActions(v, item, sourceList, sourceIndex);
                        }
                    }
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    v.removeCallbacks(longPressRunnable);
                    return true;

                default:
                    return false;
            }
        }
    }

    private final class NestedBlockTouchListener implements OnTouchListener {
        private final GameBlockItem nested;
        private final GameBlockItem parentBlock;
        private final int paramIndex;
        private final GameBlockView view;
        private float downX;
        private float downY;
        private float downRawX;
        private float downRawY;
        private boolean dragStarted;
        private final int touchSlop;
        private final int longPressDelayMs;
        private final Runnable longPressRunnable;

        NestedBlockTouchListener(GameBlockItem nested, GameBlockItem parentBlock,
                                 int paramIndex, GameBlockView view) {
            this.nested = nested;
            this.parentBlock = parentBlock;
            this.paramIndex = paramIndex;
            this.view = view;
            ViewConfiguration vc = ViewConfiguration.get(view.getContext());
            this.touchSlop = vc.getScaledTouchSlop();
            this.longPressDelayMs = Math.max(120, ViewConfiguration.getLongPressTimeout() / 2);
            this.longPressRunnable = () -> {
                if (!dragStarted) {
                    dragStarted = true;
                    if (view.getParent() != null) {
                        view.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    startBlockDrag(view, new DragPayload(nested, parentBlock, paramIndex, view),
                            nested.opCode, downRawX, downRawY, downRawX, downRawY);
                }
            };
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getX();
                    downY = event.getY();
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    dragStarted = false;
                    if (v.getParent() != null) {
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    v.postDelayed(longPressRunnable, longPressDelayMs);
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (dragStarted) {
                        return true;
                    }
                    float dx = Math.abs(event.getX() - downX);
                    float dy = Math.abs(event.getY() - downY);
                    if (dx > touchSlop || dy > touchSlop) {
                        v.removeCallbacks(longPressRunnable);
                        return false;
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    v.removeCallbacks(longPressRunnable);
                    if (!dragStarted) {
                        float upDx = Math.abs(event.getX() - downX);
                        float upDy = Math.abs(event.getY() - downY);
                        if (upDx < touchSlop && upDy < touchSlop) {
                            showNestedBlockQuickActions(v, nested, parentBlock, paramIndex);
                        }
                    }
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    v.removeCallbacks(longPressRunnable);
                    return true;

                default:
                    return false;
            }
        }
    }

    private final class ParamSlotDragListener implements OnDragListener {
        private final GameBlockItem targetBlock;
        private final int paramIndex;

        ParamSlotDragListener(GameBlockItem targetBlock, int paramIndex) {
            this.targetBlock = targetBlock;
            this.paramIndex = paramIndex;
        }

        @Override
        public boolean onDrag(View v, DragEvent event) {
            Object local = event.getLocalState();
            if (!(local instanceof DragPayload)) {
                return false;
            }
            DragPayload payload = (DragPayload) local;

            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return canAcceptSlotDrop(payload, targetBlock, paramIndex);

                case DragEvent.ACTION_DRAG_ENTERED:
                case DragEvent.ACTION_DRAG_LOCATION:
                    clearAllDropIndicators();
                    setSlotHighlight(v);
                    updateDragVisuals(v, event.getX(), event.getY(), true);
                    return true;

                case DragEvent.ACTION_DRAG_EXITED:
                    if (highlightedSlotView == v) {
                        setSlotHighlight(null);
                    }
                    return true;

                case DragEvent.ACTION_DROP:
                    setSlotHighlight(null);
                    return acceptDropIntoSlot(payload, targetBlock, paramIndex);

                case DragEvent.ACTION_DRAG_ENDED:
                    setSlotHighlight(null);
                    endBlockDragUi(payload);
                    return true;

                default:
                    return false;
            }
        }
    }

    private final class StackDropTargetListener implements OnDragListener {
        private final BlockStackLayout targetStack;
        private final boolean forceAppendToEnd;

        StackDropTargetListener(BlockStackLayout targetStack, boolean forceAppendToEnd) {
            this.targetStack = targetStack;
            this.forceAppendToEnd = forceAppendToEnd;
        }

        @Override
        public boolean onDrag(View v, DragEvent event) {
            Object local = event.getLocalState();
            if (!(local instanceof DragPayload)) {
                return false;
            }
            DragPayload payload = (DragPayload) local;

            if (payload.isReporterOrBool()) {
                return false;
            }
            if (!payload.fromPalette && isDescendantList(payload.existingBlock, targetStack.boundList)) {
                return false;
            }

            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return true;

                case DragEvent.ACTION_DRAG_ENTERED:
                case DragEvent.ACTION_DRAG_LOCATION: {
                    setSlotHighlight(null);
                    int idx = forceAppendToEnd
                            ? targetStack.boundList.size()
                            : targetStack.computeInsertIndex(event.getY());
                    targetStack.setDropIndicator(true, idx);
                    updateDragVisuals(v, event.getX(), event.getY(), true);
                    return true;
                }

                case DragEvent.ACTION_DRAG_EXITED:
                    targetStack.setDropIndicator(false, -1);
                    return true;

                case DragEvent.ACTION_DROP:
                    int dropIdx = forceAppendToEnd
                            ? targetStack.boundList.size()
                            : targetStack.computeInsertIndex(event.getY());
                    targetStack.setDropIndicator(false, -1);
                    return acceptDropIntoList(payload, targetStack.boundList, dropIdx);

                case DragEvent.ACTION_DRAG_ENDED:
                    targetStack.setDropIndicator(false, -1);
                    endBlockDragUi(payload);
                    return true;

                default:
                    return false;
            }
        }
    }

    private final class HatDropListener implements OnDragListener {
        @Override
        public boolean onDrag(View v, DragEvent event) {
            Object local = event.getLocalState();
            if (!(local instanceof DragPayload)) {
                return false;
            }
            DragPayload payload = (DragPayload) local;
            if (payload.isReporterOrBool()) {
                return false;
            }
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return true;
                case DragEvent.ACTION_DRAG_ENTERED:
                case DragEvent.ACTION_DRAG_LOCATION:
                    setSlotHighlight(null);
                    rootStackLayout.setDropIndicator(true, 0);
                    updateDragVisuals(v, event.getX(), event.getY(), true);
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    rootStackLayout.setDropIndicator(false, -1);
                    return true;
                case DragEvent.ACTION_DROP:
                    rootStackLayout.setDropIndicator(false, -1);
                    return acceptDropIntoList(payload, rootStackLayout.boundList, 0);
                case DragEvent.ACTION_DRAG_ENDED:
                    rootStackLayout.setDropIndicator(false, -1);
                    return true;
                default:
                    return false;
            }
        }
    }

    private static final class NestedSlotMatch {
        final GameBlockItem block;
        final int paramIndex;
        final View slotView;
        final float distSq;

        NestedSlotMatch(GameBlockItem block, int paramIndex, View slotView, float distSq) {
            this.block = block;
            this.paramIndex = paramIndex;
            this.slotView = slotView;
            this.distSq = distSq;
        }
    }

    @Nullable
    private NestedSlotMatch findBestNestedSlotTarget(DragPayload payload, View targetView, float localX, float localY) {
        if (payload == null || !payload.isReporterOrBool() || targetView == null) {
            return null;
        }
        int[] targetLoc = new int[2];
        targetView.getLocationOnScreen(targetLoc);
        float fingerX = targetLoc[0] + localX;
        float fingerY = targetLoc[1] + localY;
        float ghostX = fingerX;
        float ghostY = fingerY;
        float slop = dp(18);

        NestedSlotMatch[] best = new NestedSlotMatch[1];
        if (mainChainContainer != null) {
            findBestSlotInContainer(mainChainContainer, payload, fingerX, fingerY, ghostX, ghostY, slop, best);
        }
        for (BlockStackLayout fs : floatingStackLayouts) {
            if (fs != null) {
                findBestSlotInContainer(fs, payload, fingerX, fingerY, ghostX, ghostY, slop, best);
            }
        }
        return best[0];
    }

    private void findBestSlotInContainer(ViewGroup container, DragPayload payload,
                                         float fingerX, float fingerY,
                                         float ghostX, float ghostY,
                                         float slop, NestedSlotMatch[] best) {
        if (container == null || container.getVisibility() != VISIBLE) {
            return;
        }
        int[] loc = new int[2];
        int count = container.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = container.getChildAt(i);
            if (child == null || child.getVisibility() != VISIBLE || child == payload.sourceView) {
                continue;
            }
            if (child instanceof GameBlockView) {
                GameBlockView gbv = (GameBlockView) child;
                if (gbv.nestedParentBlock != null && gbv.nestedParamIndex >= 0
                        && canAcceptSlotDrop(payload, gbv.nestedParentBlock, gbv.nestedParamIndex)) {
                    evaluateSlotCandidate(gbv, gbv.nestedParentBlock, gbv.nestedParamIndex,
                            fingerX, fingerY, ghostX, ghostY, slop, loc, best);
                }
                findBestSlotInContainer(gbv.headerRow, payload, fingerX, fingerY, ghostX, ghostY, slop, best);
                if (gbv.subStack1Layout != null) {
                    findBestSlotInContainer(gbv.subStack1Layout, payload, fingerX, fingerY, ghostX, ghostY, slop, best);
                }
                if (gbv.subStack2Layout != null) {
                    findBestSlotInContainer(gbv.subStack2Layout, payload, fingerX, fingerY, ghostX, ghostY, slop, best);
                }
            } else if (child instanceof GameBlockMenuItem) {
                GameBlockMenuItem slot = (GameBlockMenuItem) child;
                if (slot.ownerBlock != null && slot.ownerParamIndex >= 0
                        && canAcceptSlotDrop(payload, slot.ownerBlock, slot.ownerParamIndex)) {
                    evaluateSlotCandidate(slot, slot.ownerBlock, slot.ownerParamIndex,
                            fingerX, fingerY, ghostX, ghostY, slop, loc, best);
                }
            } else if (child instanceof ViewGroup) {
                findBestSlotInContainer((ViewGroup) child, payload, fingerX, fingerY, ghostX, ghostY, slop, best);
            }
        }
    }

    private static void evaluateSlotCandidate(View view, GameBlockItem ownerBlock, int paramIndex,
                                              float fingerX, float fingerY,
                                              float ghostX, float ghostY,
                                              float slop, int[] loc, NestedSlotMatch[] best) {
        if (view.getWidth() <= 0 || view.getHeight() <= 0) {
            return;
        }
        view.getLocationOnScreen(loc);
        float left = loc[0];
        float top = loc[1];
        float right = left + view.getWidth();
        float bottom = top + view.getHeight();

        boolean fingerHit = fingerX >= left - slop && fingerX <= right + slop
                && fingerY >= top - slop && fingerY <= bottom + slop;
        boolean ghostHit = ghostX >= left - slop && ghostX <= right + slop
                && ghostY >= top - slop && ghostY <= bottom + slop;
        if (!fingerHit && !ghostHit) {
            return;
        }

        float cx = (left + right) * 0.5f;
        float cy = (top + bottom) * 0.5f;
        float distSq = Float.MAX_VALUE;
        if (fingerHit) {
            float dx = fingerX - cx;
            float dy = fingerY - cy;
            distSq = Math.min(distSq, dx * dx + dy * dy);
        }
        if (ghostHit) {
            float dx = ghostX - cx;
            float dy = ghostY - cy;
            distSq = Math.min(distSq, dx * dx + dy * dy);
        }
        if (best[0] == null || distSq < best[0].distSq) {
            best[0] = new NestedSlotMatch(ownerBlock, paramIndex, view, distSq);
        }
    }

    private final class WorkspaceCanvasDragListener implements OnDragListener {
        @Override
        public boolean onDrag(View v, DragEvent event) {
            Object local = event.getLocalState();
            if (!(local instanceof DragPayload)) {
                return false;
            }
            DragPayload payload = (DragPayload) local;
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return true;
                case DragEvent.ACTION_DRAG_ENTERED:
                case DragEvent.ACTION_DRAG_LOCATION: {
                    clearAllDropIndicators();
                    NestedSlotMatch match = findBestNestedSlotTarget(payload, v, event.getX(), event.getY());
                    if (match != null) {
                        setSlotHighlight(match.slotView);
                    }
                    updateDragVisuals(v, event.getX(), event.getY(), true);
                    return true;
                }
                case DragEvent.ACTION_DROP: {
                    clearAllDropIndicators();
                    NestedSlotMatch match = findBestNestedSlotTarget(payload, v, event.getX(), event.getY());
                    if (match != null) {
                        return acceptDropIntoSlot(payload, match.block, match.paramIndex);
                    }
                    return acceptDropIntoCanvas(payload, event.getX(), event.getY());
                }
                case DragEvent.ACTION_DRAG_ENDED:
                    clearAllDropIndicators();
                    endBlockDragUi(payload);
                    return true;
                default:
                    return false;
            }
        }
    }

    private final class TopMenuDragListener implements OnDragListener {
        @Override
        public boolean onDrag(View v, DragEvent event) {
            Object local = event.getLocalState();
            if (!(local instanceof DragPayload)) {
                return false;
            }
            DragPayload payload = (DragPayload) local;

            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return true;
                case DragEvent.ACTION_DRAG_ENTERED:
                    clearAllDropIndicators();
                case DragEvent.ACTION_DRAG_LOCATION:
                    updateDragVisuals(v, event.getX(), event.getY(), false);
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    if (logicTopMenu != null) {
                        logicTopMenu.setDeleteActive(false);
                        logicTopMenu.setCopyActive(false);
                    }
                    return true;
                case DragEvent.ACTION_DROP:
                    if (payload.fromPalette || payload.existingBlock == null || logicTopMenu == null) {
                        return false;
                    }
                    int[] loc = new int[2];
                    v.getLocationOnScreen(loc);
                    float rawX = loc[0] + event.getX();
                    float rawY = loc[1] + event.getY();
                    boolean isDelete = logicTopMenu.isInsideDeleteArea(rawX, rawY);
                    boolean isCopy = !isDelete && logicTopMenu.isInsideCopyArea(rawX, rawY);
                    logicTopMenu.setDeleteActive(false);
                    logicTopMenu.setCopyActive(false);
                    if (isDelete) {
                        if (payload.parentBlock != null) {
                            payload.parentBlock.setParamBlock(payload.parentParamIndex, null);
                            restoreDefaultParamValue(payload.parentBlock, payload.parentParamIndex);
                            syncAllNestedExpressions();
                            renderWorkspace();
                            notifyModified();
                            return true;
                        } else if (payload.sourceList != null) {
                            int idx = indexOfIdentity(payload.sourceList, payload.existingBlock);
                            if (idx >= 0) {
                                payload.sourceList.remove(idx);
                                pruneEmptyFloatingStacks();
                                renderWorkspace();
                                notifyModified();
                                return true;
                            }
                        }
                    } else if (isCopy) {
                        GameBlockItem dup = payload.existingBlock.copy();
                        assignFreshIds(dup);
                        if (payload.parentBlock != null) {
                            ensureActiveScript();
                            if (currentScript != null) {
                                if (currentScript.floatingStacks == null) {
                                    currentScript.floatingStacks = new ArrayList<>();
                                }
                                dup.freeX = dp(32);
                                dup.freeY = Math.max(dp(120), mainChainContainer.getHeight() + dp(16));
                                ArrayList<GameBlockItem> single = new ArrayList<>();
                                single.add(dup);
                                currentScript.floatingStacks.add(single);
                            }
                        } else if (payload.sourceList != null) {
                            int idx = indexOfIdentity(payload.sourceList, payload.existingBlock);
                            if (idx >= 0 && isFloatingStack(payload.sourceList) && idx == 0) {
                                if (currentScript != null && currentScript.floatingStacks != null) {
                                    ArrayList<GameBlockItem> dupStack = new ArrayList<>();
                                    for (int j = 0; j < payload.sourceList.size(); j++) {
                                        GameBlockItem copyItem = payload.sourceList.get(j).copy();
                                        assignFreshIds(copyItem);
                                        if (j == 0) {
                                            copyItem.freeX = payload.existingBlock.freeX + dp(18);
                                            copyItem.freeY = payload.existingBlock.freeY + dp(18);
                                        }
                                        dupStack.add(copyItem);
                                    }
                                    currentScript.floatingStacks.add(dupStack);
                                }
                            } else if (idx >= 0) {
                                payload.sourceList.add(Math.min(idx + 1, payload.sourceList.size()), dup);
                            } else if (currentScript != null && currentScript.blocks != null) {
                                currentScript.blocks.add(dup);
                            }
                        }
                        syncAllNestedExpressions();
                        renderWorkspace();
                        notifyModified();
                        Toast.makeText(getContext(), "Bloco duplicado", Toast.LENGTH_SHORT).show();
                        return true;
                    }
                    return false;
                case DragEvent.ACTION_DRAG_ENDED:
                    endBlockDragUi(payload);
                    return true;
                default:
                    return false;
            }
        }
    }

    private final class NonDropZoneDragListener implements OnDragListener {
        @Override
        public boolean onDrag(View v, DragEvent event) {
            Object local = event.getLocalState();
            if (!(local instanceof DragPayload)) {
                return false;
            }
            DragPayload payload = (DragPayload) local;
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return true;
                case DragEvent.ACTION_DRAG_ENTERED:
                    clearAllDropIndicators();
                case DragEvent.ACTION_DRAG_LOCATION:
                    updateDragVisuals(v, event.getX(), event.getY(), false);
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    return true;
                case DragEvent.ACTION_DROP:
                    return false;
                case DragEvent.ACTION_DRAG_ENDED:
                    endBlockDragUi(payload);
                    return true;
                default:
                    return false;
            }
        }
    }

    private final class ContentFallbackDragListener implements OnDragListener {
        @Override
        public boolean onDrag(View v, DragEvent event) {
            Object local = event.getLocalState();
            if (!(local instanceof DragPayload)) {
                return false;
            }
            DragPayload payload = (DragPayload) local;
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return true;
                case DragEvent.ACTION_DRAG_ENTERED:
                    clearAllDropIndicators();
                case DragEvent.ACTION_DRAG_LOCATION:
                    updateDragVisuals(v, event.getX(), event.getY(), false);
                    return true;
                case DragEvent.ACTION_DROP:
                    return false;
                case DragEvent.ACTION_DRAG_ENDED:
                    endBlockDragUi(payload);
                    return true;
                default:
                    return false;
            }
        }
    }

    // -------------------------------------------------------------------------
    // BlockStackLayout (Vertical block stack with native black notched indicator)
    // -------------------------------------------------------------------------

    private final class BlockStackLayout extends LinearLayout {
        ArrayList<GameBlockItem> boundList = new ArrayList<>();
        final boolean isNestedSlot;
        private boolean dropActive = false;
        private int dropInsertIndex = -1;
        private final BlockCanvasView dropFeedbackCanvas;

        BlockStackLayout(Context context, boolean isNestedSlot) {
            super(context);
            this.isNestedSlot = isNestedSlot;
            setOrientation(VERTICAL);
            setClipChildren(false);
            setClipToPadding(false);

            this.dropFeedbackCanvas = new BlockCanvasView(context, " ", true);
            this.dropFeedbackCanvas.e = 0xFF000000;
            this.dropFeedbackCanvas.d = 4;

            setOnDragListener(new StackDropTargetListener(this, false));
        }

        void bindBlockList(ArrayList<GameBlockItem> list) {
            this.boundList = list != null ? list : new ArrayList<>();
        }

        void setDropIndicator(boolean active, int insertIndex) {
            if (this.dropActive != active || this.dropInsertIndex != insertIndex) {
                this.dropActive = active;
                this.dropInsertIndex = insertIndex;
                if (active && insertIndex >= 0) {
                    int feedbackW = activeDragSourceView != null && activeDragSourceView.getWidth() > 0
                            ? activeDragSourceView.getWidth()
                            : Math.max(dp(90), getWidth() - dp(6));
                    int barH = dp(6);
                    int notchH = dropFeedbackCanvas.h;
                    dropFeedbackCanvas.e = 0xFF000000;
                    dropFeedbackCanvas.d = 4;
                    dropFeedbackCanvas.z = feedbackW;
                    dropFeedbackCanvas.A = barH;
                    dropFeedbackCanvas.measure(
                            MeasureSpec.makeMeasureSpec(feedbackW, MeasureSpec.EXACTLY),
                            MeasureSpec.makeMeasureSpec(barH + notchH, MeasureSpec.EXACTLY));
                    dropFeedbackCanvas.layout(0, 0, feedbackW, barH + notchH);
                }
                invalidate();
            }
        }

        int computeInsertIndex(float y) {
            if (boundList.isEmpty()) {
                return 0;
            }
            int count = getChildCount();
            for (int i = 0; i < count; i++) {
                View child = getChildAt(i);
                float midY = (child.getTop() + child.getBottom()) * 0.5f;
                if (y < midY) {
                    return i;
                }
            }
            return count;
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (!dropActive || dropInsertIndex < 0) {
                return;
            }
            float y;
            int count = getChildCount();
            if (boundList.isEmpty() || count == 0 || dropInsertIndex <= 0) {
                y = count > 0 ? Math.max(0, getChildAt(0).getTop() - dp(2)) : 0;
            } else if (dropInsertIndex >= count) {
                View last = getChildAt(count - 1);
                y = Math.max(0, last.getBottom() - dropFeedbackCanvas.h - dp(3));
            } else {
                y = Math.max(0, getChildAt(dropInsertIndex).getTop() - dp(3));
            }
            canvas.save();
            canvas.translate(getPaddingLeft(), y);
            dropFeedbackCanvas.draw(canvas);
            canvas.restore();
        }
    }

    // -------------------------------------------------------------------------
    // GameBlockView & GameBlockMenuItem (Native BlockView + BlockMenuItem Parity)
    // -------------------------------------------------------------------------

    private final class GameBlockMenuItem extends BlockMenuItem {
        @Nullable
        private final TextView boolLabelView;
        private final String paramKind;
        @Nullable
        GameBlockItem ownerBlock;
        int ownerParamIndex = -1;

        GameBlockMenuItem(Context context, String slotType, String menuName, String paramKind) {
            super(context, slotType, menuName);
            this.paramKind = paramKind;
            if ("b".equals(slotType)) {
                TextView tv = new TextView(context);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f);
                tv.setTypeface(null, Typeface.BOLD);
                tv.setTextColor(0xFFFFFFFF);
                tv.setSingleLine(true);
                tv.setGravity(Gravity.CENTER);
                tv.setPadding(0, 0, 0, 0);
                RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                        LayoutParams.WRAP_CONTENT, super.G);
                lp.addRule(RelativeLayout.CENTER_IN_PARENT);
                tv.setLayoutParams(lp);
                addView(tv);
                this.boolLabelView = tv;
            } else {
                this.boolLabelView = null;
            }
        }

        @Override
        public void setArgValue(Object value) {
            String str = value != null ? value.toString() : "";
            super.setArgValue(str);
            if (boolLabelView != null) {
                boolLabelView.setText(str);
                Rect bounds = new Rect();
                boolLabelView.getPaint().getTextBounds(str, 0, str.length(), bounds);
                int minHexW = Math.max(super.aa, bounds.width() + dp(14));
                a((float) minHexW, (float) super.G, true);
            } else if (GameBlockCatalog.KIND_COLOR.equals(paramKind) && !str.isEmpty()) {
                int parsed = parseColorSafe(str, super.e);
                super.e = parsed;
                boolean light = isColorLight(parsed);
                if (super.V != null) {
                    super.V.setTextColor(light ? 0xFF0F172A : 0xFFFFFFFF);
                }
                if (super.W != null) {
                    super.W.setTextColor(light ? 0xFF0F172A : 0xFFFFFFFF);
                }
                invalidate();
            }
        }
    }

    private final class GameBlockView extends BlockCanvasView {
        private final LinearLayout headerRow;
        @Nullable
        private final BlockStackLayout subStack1Layout;
        @Nullable
        private final BlockStackLayout subStack2Layout;
        @Nullable
        private final TextView elseLabel;
        @Nullable
        GameBlockItem nestedParentBlock;
        int nestedParamIndex = -1;

        private final int headerPadLeft;
        private final int headerPadRight;
        private final int headerPadTop;
        private final int headerPadBottom;
        private final int minHeaderContentH;
        private final int minEmptySubstackH;

        GameBlockView(Context context, String blockType, int color) {
            super(context, normalizeBlockType(blockType), false);
            this.e = color;
            setClipChildren(false);
            setClipToPadding(false);

            this.headerPadLeft = this.w;
            this.headerPadRight = 0;
            this.headerPadTop = this.u;
            this.headerPadBottom = this.v;
            this.minHeaderContentH = this.G;
            this.minEmptySubstackH = this.i;

            headerRow = new LinearLayout(context);
            headerRow.setOrientation(LinearLayout.HORIZONTAL);
            headerRow.setGravity(Gravity.CENTER_VERTICAL);
            RelativeLayout.LayoutParams headerLp = new RelativeLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            headerLp.leftMargin = headerPadLeft;
            headerLp.topMargin = headerPadTop;
            headerRow.setLayoutParams(headerLp);
            addView(headerRow);

            if (b()) {
                subStack1Layout = new BlockStackLayout(context, true);
                subStack1Layout.setMinimumHeight(minEmptySubstackH);
                RelativeLayout.LayoutParams sub1Lp = new RelativeLayout.LayoutParams(
                        LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                sub1Lp.leftMargin = this.j;
                sub1Lp.topMargin = f();
                subStack1Layout.setLayoutParams(sub1Lp);
                addView(subStack1Layout);
            } else {
                subStack1Layout = null;
            }

            if (c()) {
                elseLabel = new TextView(context);
                elseLabel.setText("else");
                elseLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.0f);
                elseLabel.setTypeface(null, Typeface.BOLD);
                elseLabel.setTextColor(0xFFFFFFFF);
                elseLabel.setGravity(Gravity.CENTER_VERTICAL);
                elseLabel.setSingleLine(true);
                elseLabel.setPadding(0, 0, 0, 0);
                RelativeLayout.LayoutParams elseLp = new RelativeLayout.LayoutParams(
                        LayoutParams.WRAP_CONTENT, this.G);
                elseLp.leftMargin = headerPadLeft;
                elseLabel.setLayoutParams(elseLp);
                addView(elseLabel);

                subStack2Layout = new BlockStackLayout(context, true);
                subStack2Layout.setMinimumHeight(minEmptySubstackH);
                RelativeLayout.LayoutParams sub2Lp = new RelativeLayout.LayoutParams(
                        LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                sub2Lp.leftMargin = this.j;
                sub2Lp.topMargin = g();
                subStack2Layout.setLayoutParams(sub2Lp);
                addView(subStack2Layout);
            } else {
                elseLabel = null;
                subStack2Layout = null;
            }
        }

        void bindHatHeader(String title, boolean hasParam, String paramValue) {
            headerRow.removeAllViews();
            headerRow.addView(createLabelView(title));

            if (hasParam) {
                GameBlockMenuItem slot = createParamSlotView(
                        TextUtils.isEmpty(paramValue) ? "?" : paramValue,
                        GameBlockCatalog.KIND_TEXT,
                        this.e);
                slot.setOnClickListener(v -> showEditEventParamDialog());
                headerRow.addView(slot);
            }
            requestLayout();
        }

        void bindPalettePreview(GameBlockItem previewItem, BlockDef def) {
            populateSpecRow(previewItem, def, false);
            requestLayout();
        }

        void bindWorkspaceItem(GameBlockItem item, @Nullable BlockDef def,
                               ArrayList<GameBlockItem> parentList, int index) {
            populateSpecRow(item, def, true);
            setOnTouchListener(new WorkspaceBlockTouchListener(item, parentList, index, this));
            requestLayout();
        }

        void bindNestedItem(GameBlockItem nested, @Nullable BlockDef def,
                            GameBlockItem parentBlock, int paramIndex, String slotKind, boolean interactive) {
            this.nestedParentBlock = parentBlock;
            this.nestedParamIndex = paramIndex;
            populateSpecRow(nested, def, interactive);
            if (interactive) {
                setOnTouchListener(new NestedBlockTouchListener(nested, parentBlock, paramIndex, this));
                setOnDragListener(new ParamSlotDragListener(parentBlock, paramIndex));
            }
            requestLayout();
        }

        private void populateSpecRow(GameBlockItem item, @Nullable BlockDef def, boolean interactive) {
            headerRow.removeAllViews();
            String template = item.spec;
            if (TextUtils.isEmpty(template)) {
                template = def != null ? def.spec : item.opCode;
            }
            if (template == null) {
                template = "";
            }
            // For "e" blocks, "else" is drawn on the middle bar
            if ("e".equals(super.b)) {
                if (template.endsWith(" / else")) {
                    template = template.substring(0, template.length() - " / else".length());
                } else if (template.endsWith(" senão")) {
                    template = template.substring(0, template.length() - " senão".length());
                }
            }

            int cursor = 0;
            int paramIdx = 0;
            while (cursor < template.length()) {
                int placeholder = GameBlockCatalog.findNextPlaceholder(template, cursor);
                if (placeholder < 0) {
                    String tail = template.substring(cursor).trim();
                    if (!tail.isEmpty()) {
                        headerRow.addView(createLabelView(tail));
                    }
                    break;
                }
                String prefix = template.substring(cursor, placeholder).trim();
                if (!prefix.isEmpty()) {
                    headerRow.addView(createLabelView(prefix));
                }

                String val = "";
                if (item.parameters != null && paramIdx < item.parameters.size()
                        && item.parameters.get(paramIdx) != null) {
                    val = item.parameters.get(paramIdx);
                } else if (def != null && paramIdx < def.defaultParams.length) {
                    val = def.defaultParams[paramIdx];
                }
                String kind = resolveParamKind(item, def, paramIdx);
                GameBlockItem nestedChild = item.getParamBlock(paramIdx);

                if ("getVar".equals(item.opCode) && paramIdx == 0 && nestedChild == null) {
                    TextView varTv = createLabelView(TextUtils.isEmpty(val) ? "score" : val);
                    if (interactive) {
                        varTv.setOnClickListener(v -> showParameterEditor(item, 0, def));
                    }
                    headerRow.addView(varTv);
                } else if (nestedChild != null) {
                    BlockDef nestedDef = GameBlockCatalog.findByOpCode(nestedChild.opCode);
                    int nestedRawColor = nestedChild.color != 0
                            ? nestedChild.color
                            : (nestedDef != null ? nestedDef.color : GameBlockCatalog.COLOR_OPERATORS);
                    String nestedType = normalizeBlockType(nestedChild.type != null
                            ? nestedChild.type
                            : (nestedDef != null ? nestedDef.type : "d"));
                    GameBlockView nestedView = new GameBlockView(getContext(), nestedType, harmonizeColor(nestedRawColor));
                    nestedView.bindNestedItem(nestedChild, nestedDef, item, paramIdx, kind, interactive);
                    LinearLayout.LayoutParams nLp = new LinearLayout.LayoutParams(
                            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                    nLp.rightMargin = dp(4);
                    nestedView.setLayoutParams(nLp);
                    headerRow.addView(nestedView);
                } else {
                    GameBlockMenuItem slot = createParamSlotView(val, kind, this.e);
                    if (interactive) {
                        final int capturedIndex = paramIdx;
                        slot.ownerBlock = item;
                        slot.ownerParamIndex = capturedIndex;
                        slot.setOnClickListener(v -> showParameterEditor(item, capturedIndex, def));
                        slot.setOnDragListener(new ParamSlotDragListener(item, capturedIndex));
                    }
                    headerRow.addView(slot);
                }

                paramIdx++;
                cursor = placeholder + 2;
            }
        }

        private TextView createLabelView(String text) {
            TextView tv = new TextView(getContext());
            tv.setText(text);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.0f);
            tv.setTypeface(null, Typeface.BOLD);
            tv.setTextColor(0xFFFFFFFF);
            tv.setGravity(Gravity.CENTER_VERTICAL);
            tv.setSingleLine(true);
            tv.setPadding(0, 0, 0, 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, this.G);
            lp.rightMargin = dp(4);
            tv.setLayoutParams(lp);
            return tv;
        }

        private GameBlockMenuItem createParamSlotView(String value, String kind, int blockColor) {
            String slotType;
            String menuName = "";
            switch (kind) {
                case GameBlockCatalog.KIND_NUMBER:
                case GameBlockCatalog.KIND_EXPR:
                    slotType = "d";
                    break;
                case GameBlockCatalog.KIND_BOOL:
                case GameBlockCatalog.KIND_COND:
                    slotType = "b";
                    break;
                case GameBlockCatalog.KIND_SPRITE:
                    slotType = "m";
                    menuName = "sprite";
                    break;
                case GameBlockCatalog.KIND_SOUND:
                    slotType = "m";
                    menuName = "sound";
                    break;
                case GameBlockCatalog.KIND_SCENE:
                    slotType = "m";
                    menuName = "scene";
                    break;
                case GameBlockCatalog.KIND_OBJECT:
                    slotType = "m";
                    menuName = "obj";
                    break;
                case GameBlockCatalog.KIND_HUD:
                    slotType = "m";
                    menuName = "hud";
                    break;
                case GameBlockCatalog.KIND_PREFAB:
                    slotType = "m";
                    menuName = "prefab";
                    break;
                case GameBlockCatalog.KIND_TAG:
                    slotType = "m";
                    menuName = "tag";
                    break;
                case GameBlockCatalog.KIND_MESSAGE:
                    slotType = "m";
                    menuName = "msg";
                    break;
                case GameBlockCatalog.KIND_ACTION:
                    slotType = "m";
                    menuName = "action";
                    break;
                case GameBlockCatalog.KIND_VAR:
                case GameBlockCatalog.KIND_VAR_REF:
                    slotType = "m";
                    menuName = "var";
                    break;
                case GameBlockCatalog.KIND_MATH_FN:
                    slotType = "m";
                    menuName = "fn";
                    break;
                case GameBlockCatalog.KIND_PROP:
                    slotType = "m";
                    menuName = "prop";
                    break;
                case GameBlockCatalog.KIND_LAYER:
                    slotType = "m";
                    menuName = "layer";
                    break;
                case GameBlockCatalog.KIND_OP:
                    slotType = "m";
                    menuName = "op";
                    break;
                case GameBlockCatalog.KIND_COLOR:
                    slotType = "m";
                    menuName = "color";
                    break;
                case GameBlockCatalog.KIND_TEXT:
                default:
                    slotType = "s";
                    break;
            }

            GameBlockMenuItem slot = new GameBlockMenuItem(getContext(), slotType, menuName, kind);
            if ("m".equals(slotType) && !GameBlockCatalog.KIND_COLOR.equals(kind)) {
                slot.e = darkenColor(blockColor, 0.8f);
            }
            slot.setArgValue(value != null ? value : "");

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(4);
            slot.setLayoutParams(lp);
            return slot;
        }

        private boolean substackNeedsBottomMargin(BlockStackLayout stack) {
            int count = stack.getChildCount();
            if (count == 0) {
                return true;
            }
            View last = stack.getChildAt(count - 1);
            if (!(last instanceof GameBlockView)) {
                return true;
            }
            return "f".equals(((BlockCanvasView) last).b);
        }

        private int minBlockWidth() {
            if ("c".equals(super.b) || "e".equals(super.b) || "h".equals(super.b)) {
                return dp(90);
            }
            if ("b".equals(super.b) || "d".equals(super.b) || "s".equals(super.b) || "a".equals(super.b)) {
                return dp(30);
            }
            return dp(50);
        }

        @Override
        public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int unspec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            headerRow.measure(unspec, unspec);

            int headerH = Math.max(minHeaderContentH, headerRow.getMeasuredHeight());
            int topH = headerH + headerPadTop + headerPadBottom;

            int effLeft = headerPadLeft;
            int effRight = headerPadRight;
            if ("b".equals(super.b)) {
                int hexCap = Math.max(dp(10), topH / 2 + dp(2));
                effLeft = Math.max(headerPadLeft, hexCap);
                effRight = Math.max(dp(4), hexCap - dp(2));
            } else if ("d".equals(super.b)) {
                int roundCap = Math.max(dp(8), Math.round(topH * 0.34f));
                effLeft = Math.max(headerPadLeft, roundCap);
                effRight = Math.max(dp(4), roundCap - dp(2));
            }

            int headerW = headerRow.getMeasuredWidth() + effLeft + effRight;
            int blockW = Math.max(minBlockWidth(), headerW);

            if (b() && subStack1Layout != null) {
                subStack1Layout.measure(unspec, unspec);
                int sub1H = Math.max(minEmptySubstackH,
                        subStack1Layout.getMeasuredHeight() + (substackNeedsBottomMargin(subStack1Layout) ? this.h : 0));
                setSubstack1Height(sub1H);
                blockW = Math.max(blockW, this.j + subStack1Layout.getMeasuredWidth() + dp(4));
            }

            if (c() && subStack2Layout != null) {
                if (elseLabel != null) {
                    elseLabel.measure(unspec, unspec);
                    blockW = Math.max(blockW, effLeft + elseLabel.getMeasuredWidth() + dp(12));
                }
                subStack2Layout.measure(unspec, unspec);
                int sub2H = Math.max(minEmptySubstackH,
                        subStack2Layout.getMeasuredHeight() + (substackNeedsBottomMargin(subStack2Layout) ? this.h : 0));
                setSubstack2Height(sub2H);
                blockW = Math.max(blockW, this.j + subStack2Layout.getMeasuredWidth() + dp(4));
            }

            // Set BlockCanvasView z and A cleanly without calling requestLayout()
            a((float) blockW, (float) topH, false);

            RelativeLayout.LayoutParams headerLp = (RelativeLayout.LayoutParams) headerRow.getLayoutParams();
            headerLp.leftMargin = effLeft;
            headerLp.topMargin = Math.max(headerPadTop, (topH - headerRow.getMeasuredHeight()) / 2);

            if (b() && subStack1Layout != null) {
                RelativeLayout.LayoutParams sub1Lp = (RelativeLayout.LayoutParams) subStack1Layout.getLayoutParams();
                sub1Lp.leftMargin = this.j;
                sub1Lp.topMargin = f();
                sub1Lp.width = Math.max(dp(60), blockW - this.j);
            }

            if (c() && subStack2Layout != null) {
                if (elseLabel != null) {
                    RelativeLayout.LayoutParams elseLp = (RelativeLayout.LayoutParams) elseLabel.getLayoutParams();
                    elseLp.leftMargin = headerPadLeft;
                    elseLp.topMargin = g() - this.n + Math.max(0, (this.n - elseLabel.getMeasuredHeight()) / 2);
                }
                RelativeLayout.LayoutParams sub2Lp = (RelativeLayout.LayoutParams) subStack2Layout.getLayoutParams();
                sub2Lp.leftMargin = this.j;
                sub2Lp.topMargin = g();
                sub2Lp.width = Math.max(dp(60), blockW - this.j);
            }

            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    // -------------------------------------------------------------------------
    // UI Helpers
    // -------------------------------------------------------------------------

    private static int darkenColor(int color, float factor) {
        int a = Color.alpha(color);
        int r = Math.max(0, Math.min(255, Math.round(Color.red(color) * factor)));
        int g = Math.max(0, Math.min(255, Math.round(Color.green(color) * factor)));
        int b = Math.max(0, Math.min(255, Math.round(Color.blue(color) * factor)));
        return Color.argb(a, r, g, b);
    }

    private TextView createToolbarChip(Context ctx, String text, int bgColor, boolean boldWhite) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setSingleLine(true);
        tv.setPadding(dp(12), dp(6), dp(12), dp(6));
        tv.setTextColor(boldWhite ? Color.WHITE : colorTextPrimary);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(bgColor);
        if (!boldWhite) {
            bg.setStroke(dp(1), colorBorder);
        }
        tv.setBackground(bg);
        return tv;
    }

    private void addToolbarSpacer(LinearLayout parent, int widthPx) {
        View v = new View(parent.getContext());
        v.setLayoutParams(new LinearLayout.LayoutParams(widthPx, 1));
        parent.addView(v);
    }

    private static String normalizeBlockType(@Nullable String type) {
        if (type == null) {
            return " ";
        }
        switch (type) {
            case "h":
            case " ":
            case "c":
            case "e":
            case "f":
            case "b":
            case "d":
            case "s":
                return type;
            default:
                return " ";
        }
    }

    private static int withAlpha(int color, int alphaByte) {
        return (color & 0x00FFFFFF) | ((alphaByte & 0xFF) << 24);
    }

    private static int parseColorSafe(@Nullable String text, int fallback) {
        if (text == null) {
            return fallback;
        }
        String cleaned = text.trim();
        if (cleaned.isEmpty()) {
            return fallback;
        }
        if (!cleaned.startsWith("#")) {
            cleaned = "#" + cleaned;
        }
        try {
            return Color.parseColor(cleaned);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static boolean isColorLight(int color) {
        double luminance = (0.299 * Color.red(color)
                + 0.587 * Color.green(color)
                + 0.114 * Color.blue(color)) / 255.0;
        return luminance > 0.65;
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }
}
