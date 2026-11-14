package pro.sketchware.activities.game.ui;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.DragEvent;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import pro.sketchware.R;
import pro.sketchware.activities.game.model.GameObjectBean;
import pro.sketchware.activities.game.model.GameProject;
import pro.sketchware.activities.game.model.GameProjectRepository;
import pro.sketchware.activities.game.model.GameScene;
import pro.sketchware.activities.game.model.GameScriptNode;

/**
 * Interactive 2D stage canvas for placing, moving, resizing, rotating, and inspecting
 * game objects and HUD elements inside the 2D Game Engine Editor.
 */
public class GameStageEditorView extends View {

    public interface StageListener {
        void onObjectSelected(GameObjectBean selected);

        void onSceneModified();

        void onOpenObjectEventBlocks(GameObjectBean target, String eventType, String eventParam);
    }

    public static final int LAYER_ALL = 0;
    public static final int LAYER_WORLD_ONLY = 1;
    public static final int LAYER_HUD_ONLY = 2;

    private static final float SNAP_GRID_PX = 16f;
    private static final float VISUAL_GRID_PX = 32f;
    private static final float MIN_OBJECT_SIZE = 16f;
    private static final float MIN_ZOOM = 0.15f;
    private static final float MAX_ZOOM = 4.0f;
    private static final int MAX_UNDO_HISTORY = 40;
    private static final long DOUBLE_TAP_TIMEOUT_MS = 280L;

    private static final int MODE_NONE = 0;
    private static final int MODE_PAN_OR_TAP = 1;
    private static final int MODE_PAN_CANVAS = 2;
    private static final int MODE_DRAG_OBJECT = 3;
    private static final int MODE_RESIZE_OBJECT = 4;
    private static final int MODE_ROTATE_OBJECT = 5;
    private static final int MODE_TWO_FINGER_PAN = 6;

    private final Gson gson = new Gson();
    private final Type objectListType = new TypeToken<ArrayList<GameObjectBean>>() {
    }.getType();

    private final HashMap<String, Bitmap> bitmapCache = new HashMap<>();
    private final HashSet<String> missingBitmaps = new HashSet<>();
    private final HashMap<String, ArrayList<String>> undoStacks = new HashMap<>();
    private final HashMap<String, ArrayList<String>> redoStacks = new HashMap<>();

    private String scId;
    private GameProject project;
    private GameScene scene;
    private StageListener listener;
    private GameObjectBean selectedObject;

    private boolean snapToGrid = true;
    private int layerFilter = LAYER_ALL;

    private float panX = 0f;
    private float panY = 0f;
    private float zoom = 1f;
    private boolean needsCameraReset = true;
    private int lastVirtualW = 0;
    private int lastVirtualH = 0;

    private float density = 1f;
    private float touchSlop = 8f;

    // Interaction state
    private int mode = MODE_NONE;
    private boolean isMultiTouch = false;
    private float downScreenX;
    private float downScreenY;
    private float lastScreenX;
    private float lastScreenY;
    private float lastMultiFocusX;
    private float lastMultiFocusY;

    private float dragStartStageX;
    private float dragStartStageY;
    private float origObjX;
    private float origObjY;
    private float origObjW;
    private float origObjH;
    private float origObjRot;
    private String pendingUndoSnapshot;
    private boolean isOverTrashZone = false;
    private boolean dragMovedBeyondSlop = false;

    private long lastTapTimeMs = 0L;
    private float lastTapX = 0f;
    private float lastTapY = 0f;
    private String lastTappedObjectId = null;

    // External Drag & Drop preview
    private String dropPreviewType = null;
    private float dropPreviewStageX = 0f;
    private float dropPreviewStageY = 0f;

    private ScaleGestureDetector scaleDetector;

    // Reusable drawing primitives
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dashedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF tempRect = new RectF();
    private final Rect srcRect = new Rect();
    private final RectF trashRect = new RectF();
    private final RectF eventChipRect = new RectF();
    private final Path tempPath = new Path();
    private boolean eventChipVisible = false;

    private int colorPrimary;
    private int colorOnPrimary;
    private int colorError;
    private int colorErrorContainer;
    private int colorOnErrorContainer;
    private int colorSurfaceContainerHighest;
    private int colorOnSurface;
    private int colorCanvasOuter;

    public GameStageEditorView(Context context) {
        this(context, null);
    }

    public GameStageEditorView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        density = getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        colorPrimary = MaterialColors.getColor(context, R.attr.colorPrimary, 0xFF3B82F6);
        colorOnPrimary = MaterialColors.getColor(context, R.attr.colorOnPrimary, 0xFFFFFFFF);
        colorError = MaterialColors.getColor(context, R.attr.colorError, 0xFFEF4444);
        colorErrorContainer = MaterialColors.getColor(context, R.attr.colorErrorContainer, 0xFF991B1B);
        colorOnErrorContainer = MaterialColors.getColor(context, R.attr.colorOnErrorContainer, 0xFFFEE2E2);
        colorSurfaceContainerHighest = MaterialColors.getColor(context, R.attr.colorSurfaceContainerHighest, 0xFF282E3A);
        colorOnSurface = MaterialColors.getColor(context, R.attr.colorOnSurface, 0xFFE5E7EB);
        colorCanvasOuter = MaterialColors.getColor(context, R.attr.colorSurfaceContainerLowest, 0xFF10141D);

        fillPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);

        dashedPaint.setStyle(Paint.Style.STROKE);
        dashedPaint.setPathEffect(new DashPathEffect(new float[]{10f, 8f}, 0f));

        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextSize(12f * density);

        badgePaint.setStyle(Paint.Style.FILL);
        handleFillPaint.setStyle(Paint.Style.FILL);
        handleStrokePaint.setStyle(Paint.Style.STROKE);
        handleStrokePaint.setStrokeWidth(2f * density);

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(@NonNull ScaleGestureDetector detector) {
                float factor = detector.getScaleFactor();
                float newZoom = clamp(zoom * factor, MIN_ZOOM, MAX_ZOOM);
                float ratio = newZoom / zoom;
                float fx = detector.getFocusX();
                float fy = detector.getFocusY();
                panX = fx - (fx - panX) * ratio;
                panY = fy - (fy - panY) * ratio;
                zoom = newZoom;
                invalidate();
                return true;
            }
        });

        setOnDragListener(this::handlePaletteDragEvent);
    }

    public void bind(String scId, GameProject project, GameScene scene, StageListener listener) {
        boolean projectChanged = this.project != project || (this.scId != null && !this.scId.equals(scId));
        this.scId = scId;
        this.project = project;
        this.listener = listener;
        if (projectChanged) {
            invalidateResourceCache();
            needsCameraReset = true;
        }
        setScene(scene);
    }

    public void setScene(GameScene scene) {
        this.scene = scene;
        if (scene != null && scene.objects == null) {
            scene.objects = new ArrayList<>();
        }
        if (scene != null && scene.scripts == null) {
            scene.scripts = new ArrayList<>();
        }
        selectedObject = null;
        int vw = getVirtualWidth();
        int vh = getVirtualHeight();
        if (vw != lastVirtualW || vh != lastVirtualH) {
            lastVirtualW = vw;
            lastVirtualH = vh;
            needsCameraReset = true;
        }
        if (needsCameraReset && getWidth() > 0 && getHeight() > 0) {
            resetCameraView();
        }
        if (listener != null) {
            listener.onObjectSelected(null);
        }
        invalidate();
    }

    public GameObjectBean getSelectedObject() {
        return selectedObject;
    }

    public void selectObject(@Nullable GameObjectBean obj) {
        if (obj != null && scene != null && scene.objects != null && !scene.objects.contains(obj)) {
            GameObjectBean match = obj.id != null ? scene.findObjectById(obj.id) : null;
            obj = match;
        }
        selectedObject = obj;
        if (listener != null) {
            listener.onObjectSelected(selectedObject);
        }
        invalidate();
    }

    public void setSnapToGrid(boolean snap) {
        this.snapToGrid = snap;
        invalidate();
    }

    public boolean isSnapToGrid() {
        return snapToGrid;
    }

    public void setLayerFilter(int filter) {
        if (filter < LAYER_ALL || filter > LAYER_HUD_ONLY) {
            filter = LAYER_ALL;
        }
        this.layerFilter = filter;
        if (selectedObject != null && !matchesLayerFilter(selectedObject)) {
            selectObject(null);
        } else {
            invalidate();
        }
    }

    public int getLayerFilter() {
        return layerFilter;
    }

    public void resetCameraView() {
        int viewW = getWidth();
        int viewH = getHeight();
        int virtualW = getVirtualWidth();
        int virtualH = getVirtualHeight();
        if (viewW <= 0 || viewH <= 0) {
            needsCameraReset = true;
            return;
        }
        float pad = 24f * density;
        float availW = Math.max(64f, viewW - pad * 2f);
        float availH = Math.max(64f, viewH - pad * 2f);
        zoom = clamp(Math.min(availW / virtualW, availH / virtualH), MIN_ZOOM, MAX_ZOOM);
        panX = (viewW - virtualW * zoom) * 0.5f;
        panY = (viewH - virtualH * zoom) * 0.5f;
        needsCameraReset = false;
        invalidate();
    }

    public void pushUndoState() {
        if (scene == null) {
            return;
        }
        String snapshot = captureObjectsSnapshot();
        pushUndoSnapshot(snapshot);
    }

    private void pushUndoSnapshot(String snapshot) {
        if (scene == null || snapshot == null) {
            return;
        }
        ArrayList<String> undo = getUndoStack();
        if (!undo.isEmpty() && undo.get(undo.size() - 1).equals(snapshot)) {
            return;
        }
        undo.add(snapshot);
        if (undo.size() > MAX_UNDO_HISTORY) {
            undo.remove(0);
        }
        getRedoStack().clear();
    }

    public boolean canUndo() {
        return scene != null && !getUndoStack().isEmpty();
    }

    public boolean canRedo() {
        return scene != null && !getRedoStack().isEmpty();
    }

    public void undo() {
        if (!canUndo()) {
            return;
        }
        ArrayList<String> undo = getUndoStack();
        ArrayList<String> redo = getRedoStack();
        String current = captureObjectsSnapshot();
        redo.add(current);
        if (redo.size() > MAX_UNDO_HISTORY) {
            redo.remove(0);
        }
        String previous = undo.remove(undo.size() - 1);
        restoreObjectsSnapshot(previous);
    }

    public void redo() {
        if (!canRedo()) {
            return;
        }
        ArrayList<String> undo = getUndoStack();
        ArrayList<String> redo = getRedoStack();
        String current = captureObjectsSnapshot();
        undo.add(current);
        if (undo.size() > MAX_UNDO_HISTORY) {
            undo.remove(0);
        }
        String next = redo.remove(redo.size() - 1);
        restoreObjectsSnapshot(next);
    }

    public void invalidateResourceCache() {
        for (Bitmap bmp : bitmapCache.values()) {
            if (bmp != null && !bmp.isRecycled()) {
                bmp.recycle();
            }
        }
        bitmapCache.clear();
        missingBitmaps.clear();
        invalidate();
    }

    public void addObjectFromPalette(String type) {
        if (scene == null || type == null) {
            return;
        }
        int worldW = getWorldWidth();
        int worldH = getWorldHeight();
        float centerX = getWidth() > 0 ? screenToStageX(getWidth() * 0.5f) : worldW * 0.5f;
        float centerY = getHeight() > 0 ? screenToStageY(getHeight() * 0.5f) : worldH * 0.5f;
        centerX = clamp(centerX, 32f, Math.max(32f, worldW - 32f));
        centerY = clamp(centerY, 32f, Math.max(32f, worldH - 32f));
        createAndInsertObject(type, centerX, centerY);
    }

    private void createAndInsertObject(String type, float stageCenterX, float stageCenterY) {
        if (scene == null) {
            return;
        }
        if (scene.objects == null) {
            scene.objects = new ArrayList<>();
        }
        pushUndoState();
        int nextIndex = computeNextIndex();
        GameObjectBean obj = GameObjectBean.createDefaultForType(type, stageCenterX, stageCenterY, nextIndex);
        if (obj.width <= 0f) {
            obj.width = 64f;
        }
        if (obj.height <= 0f) {
            obj.height = 64f;
        }
        if (GameObjectBean.TYPE_PARALLAX_BG.equals(type)) {
            obj.x = 0f;
            obj.y = 0f;
            obj.width = getWorldWidth();
            obj.height = getWorldHeight();
            obj.zIndex = -50;
        } else {
            obj.x = stageCenterX - obj.width * 0.5f;
            obj.y = stageCenterY - obj.height * 0.5f;
            if (snapToGrid) {
                obj.x = snap(obj.x);
                obj.y = snap(obj.y);
            }
            obj.zIndex = computeTopZIndex() + 1;
        }
        ensureUniqueIdentity(obj);
        if (obj.isHudType()) {
            obj.isHud = true;
            if (layerFilter == LAYER_WORLD_ONLY) {
                layerFilter = LAYER_ALL;
            }
        } else if (layerFilter == LAYER_HUD_ONLY) {
            layerFilter = LAYER_ALL;
        }
        scene.objects.add(obj);
        selectedObject = obj;
        if (listener != null) {
            listener.onObjectSelected(selectedObject);
            listener.onSceneModified();
        }
        invalidate();
    }

    private int computeNextIndex() {
        if (scene == null || scene.objects == null) {
            return 1;
        }
        return scene.objects.size() + 1;
    }

    private int computeTopZIndex() {
        int maxZ = 0;
        if (scene != null && scene.objects != null) {
            for (GameObjectBean obj : scene.objects) {
                if (obj.zIndex > maxZ) {
                    maxZ = obj.zIndex;
                }
            }
        }
        return maxZ;
    }

    private void ensureUniqueIdentity(GameObjectBean obj) {
        if (scene == null) {
            return;
        }
        String baseId = (obj.id == null || obj.id.trim().isEmpty()) ? "obj" : obj.id.trim();
        String candidateId = baseId;
        int i = 1;
        while (scene.findObjectById(candidateId) != null) {
            candidateId = baseId + "_" + i++;
        }
        obj.id = candidateId;

        String baseName = (obj.name == null || obj.name.trim().isEmpty()) ? baseId : obj.name.trim();
        String candidateName = baseName;
        int j = 2;
        while (scene.findObjectByName(candidateName) != null) {
            candidateName = baseName + "_" + j++;
        }
        obj.name = candidateName;
    }

    private String captureObjectsSnapshot() {
        if (scene == null || scene.objects == null) {
            return "[]";
        }
        return gson.toJson(scene.objects, objectListType);
    }

    private void restoreObjectsSnapshot(String json) {
        if (scene == null) {
            return;
        }
        ArrayList<GameObjectBean> restored = gson.fromJson(json, objectListType);
        if (restored == null) {
            restored = new ArrayList<>();
        }
        if (scene.objects == null) {
            scene.objects = new ArrayList<>();
        }
        String prevSelectedId = selectedObject != null ? selectedObject.id : null;
        scene.objects.clear();
        scene.objects.addAll(restored);
        selectedObject = prevSelectedId != null ? scene.findObjectById(prevSelectedId) : null;
        if (listener != null) {
            listener.onObjectSelected(selectedObject);
            listener.onSceneModified();
        }
        invalidate();
    }

    private ArrayList<String> getUndoStack() {
        String key = scene != null && scene.id != null ? scene.id : "__default__";
        return undoStacks.computeIfAbsent(key, k -> new ArrayList<>());
    }

    private ArrayList<String> getRedoStack() {
        String key = scene != null && scene.id != null ? scene.id : "__default__";
        return redoStacks.computeIfAbsent(key, k -> new ArrayList<>());
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (needsCameraReset && w > 0 && h > 0) {
            resetCameraView();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        invalidateResourceCache();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawColor(colorCanvasOuter);

        int virtualW = getVirtualWidth();
        int virtualH = getVirtualHeight();
        int worldW = getWorldWidth();
        int worldH = getWorldHeight();

        canvas.save();
        canvas.translate(panX, panY);
        canvas.scale(zoom, zoom);

        // Stage background spans the full scene world bounds
        tempRect.set(0f, 0f, worldW, worldH);
        int bgColor = (scene != null && scene.backgroundColor != 0) ? scene.backgroundColor : 0xFF1A2232;
        fillPaint.setColor(bgColor);
        canvas.drawRect(tempRect, fillPaint);

        if (scene != null && scene.backgroundSprite != null && !scene.backgroundSprite.isEmpty()) {
            Bitmap bgBmp = getBitmap(scene.backgroundSprite);
            if (bgBmp != null) {
                srcRect.set(0, 0, bgBmp.getWidth(), bgBmp.getHeight());
                bitmapPaint.setAlpha(255);
                canvas.drawBitmap(bgBmp, srcRect, tempRect, bitmapPaint);
            }
        }

        // Grid lines across full world
        drawStageGrid(canvas, worldW, worldH, bgColor);

        // Objects sorted by zIndex
        if (scene != null && scene.objects != null) {
            ArrayList<GameObjectBean> sorted = new ArrayList<>(scene.objects);
            Collections.sort(sorted, (a, b) -> Integer.compare(a.zIndex, b.zIndex));
            for (GameObjectBean obj : sorted) {
                if (matchesLayerFilter(obj)) {
                    drawGameObject(canvas, obj);
                }
            }
        }

        if (scene == null || scene.objects == null || scene.objects.isEmpty()) {
            drawCenteredLabel(canvas, "Empty Scene — Drag or tap items from the palette",
                    virtualW * 0.5f, virtualH * 0.5f, 0x99CBD5E1, Math.max(18f, virtualW * 0.022f));
        }

        // Initial camera viewport guide when world extends beyond 1 screen
        if (worldW > virtualW || worldH > virtualH) {
            dashedPaint.setColor(0xBB38BDF8);
            dashedPaint.setStrokeWidth(Math.max(1.2f, 2f / zoom));
            dashedPaint.setPathEffect(new DashPathEffect(new float[]{14f / zoom, 8f / zoom}, 0f));
            tempRect.set(0f, 0f, virtualW, virtualH);
            canvas.drawRect(tempRect, dashedPaint);
            drawBadgeLabel(canvas, 8f, 8f, "CAMERA " + virtualW + "x" + virtualH, 0xCC0284C7);
        }

        // World outer border
        strokePaint.setColor(0x9994A3B8);
        strokePaint.setStrokeWidth(Math.max(1f, 1.5f / zoom));
        tempRect.set(0f, 0f, worldW, worldH);
        canvas.drawRect(tempRect, strokePaint);

        // HUD safe frame indicator when HUD layer is visible
        if (layerFilter == LAYER_HUD_ONLY) {
            dashedPaint.setColor(0x9938BDF8);
            dashedPaint.setStrokeWidth(Math.max(1f, 1.5f / zoom));
            dashedPaint.setPathEffect(new DashPathEffect(new float[]{12f / zoom, 8f / zoom}, 0f));
            tempRect.set(16f, 16f, Math.max(16f, virtualW - 16f), Math.max(16f, virtualH - 16f));
            canvas.drawRect(tempRect, dashedPaint);
        }

        // Drop preview ghost from palette
        if (dropPreviewType != null) {
            drawDropGhost(canvas);
        }

        canvas.restore();

        // Screen-space selection overlay & handles
        eventChipVisible = false;
        if (selectedObject != null && matchesLayerFilter(selectedObject)) {
            drawSelectionOverlay(canvas, selectedObject);
        }

        // Top trash drop zone when dragging an existing object
        if (mode == MODE_DRAG_OBJECT) {
            drawTrashZone(canvas);
        }

        // Subtle zoom & filter status pill in bottom-left
        drawStatusOverlay(canvas, virtualW, virtualH);
    }

    private void drawStageGrid(Canvas canvas, int virtualW, int virtualH, int bgColor) {
        float step = snapToGrid ? VISUAL_GRID_PX : 64f;
        strokePaint.setStrokeWidth(Math.max(0.5f, 1f / zoom));
        double lum = (0.299 * Color.red(bgColor) + 0.587 * Color.green(bgColor) + 0.114 * Color.blue(bgColor)) / 255.0;
        if (lum > 0.6) {
            strokePaint.setColor(snapToGrid ? 0x260F172A : 0x120F172A);
        } else {
            strokePaint.setColor(snapToGrid ? 0x26FFFFFF : 0x14FFFFFF);
        }

        for (float x = step; x < virtualW; x += step) {
            canvas.drawLine(x, 0f, x, virtualH, strokePaint);
        }
        for (float y = step; y < virtualH; y += step) {
            canvas.drawLine(0f, y, virtualW, y, strokePaint);
        }
    }

    private void drawGameObject(Canvas canvas, GameObjectBean obj) {
        float w = Math.max(MIN_OBJECT_SIZE, obj.width);
        float h = Math.max(MIN_OBJECT_SIZE, obj.height);
        float cx = obj.x + w * 0.5f;
        float cy = obj.y + h * 0.5f;
        float sx = obj.scaleX == 0f ? 1f : obj.scaleX;
        float sy = obj.scaleY == 0f ? 1f : obj.scaleY;

        canvas.save();
        canvas.translate(cx, cy);
        canvas.rotate(obj.rotation);
        canvas.scale(sx, sy);

        RectF local = new RectF(-w * 0.5f, -h * 0.5f, w * 0.5f, h * 0.5f);
        float effectiveAlpha = obj.visible ? clamp(obj.alpha <= 0f ? 1f : obj.alpha, 0.08f, 1f) : 0.35f;
        int alpha255 = Math.round(effectiveAlpha * 255f);

        String type = obj.type != null ? obj.type : GameObjectBean.TYPE_SPRITE;
        Bitmap spriteBmp = (obj.spriteResName != null && !obj.spriteResName.isEmpty())
                ? getBitmap(obj.spriteResName) : null;

        if (spriteBmp != null) {
            srcRect.set(0, 0, spriteBmp.getWidth(), spriteBmp.getHeight());
            bitmapPaint.setAlpha(alpha255);
            canvas.drawBitmap(spriteBmp, srcRect, local, bitmapPaint);
            if (GameObjectBean.TYPE_ANIMATED_SPRITE.equals(type)) {
                drawBadgeLabel(canvas, local.left + 4f, local.top + 4f, "ANIM", 0xCC7C3AED);
            }
        } else {
            drawProceduralObject(canvas, obj, type, local, alpha255);
        }

        // Invisible indicator
        if (!obj.visible) {
            dashedPaint.setColor(0xCC94A3B8);
            dashedPaint.setStrokeWidth(Math.max(1f, 1.5f / zoom));
            dashedPaint.setPathEffect(new DashPathEffect(new float[]{8f / zoom, 6f / zoom}, 0f));
            canvas.drawRect(local, dashedPaint);
            drawBadgeLabel(canvas, local.left + 4f, local.bottom - 18f, "HIDDEN", 0xCC334155);
        }

        // HUD outline & tag
        if (isHudObject(obj)) {
            dashedPaint.setColor(0xDD38BDF8);
            dashedPaint.setStrokeWidth(Math.max(1f, 1.5f / zoom));
            dashedPaint.setPathEffect(new DashPathEffect(new float[]{8f / zoom, 5f / zoom}, 0f));
            canvas.drawRoundRect(local, 6f, 6f, dashedPaint);
            drawBadgeLabel(canvas, local.left + 4f, local.top + 4f, "HUD", 0xCC0284C7);
        }

        // Debug hitbox overlay
        if (project != null && project.showDebugHitboxes && (obj.physicsEnabled || obj.isSensor || GameObjectBean.TYPE_TRIGGER.equals(type))) {
            strokePaint.setColor(obj.isSensor ? 0xFFF59E0B : (obj.isStatic ? 0xFF38BDF8 : 0xFF22C55E));
            strokePaint.setStrokeWidth(Math.max(1f, 2f / zoom));
            canvas.drawRect(local, strokePaint);
        }

        // Lock indicator
        if (obj.locked) {
            drawBadgeLabel(canvas, Math.max(local.left + 4f, local.right - 46f), local.top + 4f, "LOCK", 0xCCDC2626);
        }

        canvas.restore();
    }

    private void drawProceduralObject(Canvas canvas, GameObjectBean obj, String type, RectF local, int alpha255) {
        int baseColor = resolveDisplayColor(obj, type);
        int fillColor = applyAlpha(baseColor, alpha255);
        float minDim = Math.min(local.width(), local.height());

        switch (type) {
            case GameObjectBean.TYPE_PARALLAX_BG -> {
                fillPaint.setColor(applyAlpha(baseColor, Math.min(110, alpha255)));
                canvas.drawRect(local, fillPaint);
                dashedPaint.setColor(applyAlpha(0xFF94A3B8, Math.min(200, alpha255)));
                dashedPaint.setStrokeWidth(Math.max(1f, 1.5f / zoom));
                dashedPaint.setPathEffect(new DashPathEffect(new float[]{12f, 8f}, 0f));
                canvas.drawRect(local, dashedPaint);
                drawCenteredLabel(canvas, "TILED / PARALLAX BG", 0f, 0f, applyAlpha(0xFFE2E8F0, alpha255), 14f);
            }
            case GameObjectBean.TYPE_WORLD_TEXT -> {
                dashedPaint.setColor(applyAlpha(0xFF38BDF8, Math.min(170, alpha255)));
                dashedPaint.setStrokeWidth(Math.max(1f, 1.2f / zoom));
                dashedPaint.setPathEffect(new DashPathEffect(new float[]{6f, 6f}, 0f));
                canvas.drawRect(local, dashedPaint);
                String label = (obj.text != null && !obj.text.isEmpty()) ? obj.text : (obj.name != null ? obj.name : "Text");
                int txtCol = obj.color != 0 ? applyAlpha(obj.color, alpha255) : applyAlpha(0xFFFFFFFF, alpha255);
                drawCenteredLabel(canvas, label, 0f, 0f, txtCol, Math.min(20f, Math.max(12f, local.height() * 0.42f)));
            }
            case GameObjectBean.TYPE_LIGHT_2D -> {
                float r = minDim * 0.48f;
                fillPaint.setColor(applyAlpha(baseColor, Math.min(55, alpha255)));
                canvas.drawCircle(0f, 0f, r, fillPaint);
                fillPaint.setColor(applyAlpha(baseColor, Math.min(130, alpha255)));
                canvas.drawCircle(0f, 0f, r * 0.45f, fillPaint);
                strokePaint.setColor(applyAlpha(baseColor, alpha255));
                strokePaint.setStrokeWidth(Math.max(1f, 1.5f / zoom));
                canvas.drawCircle(0f, 0f, r, strokePaint);
                drawCenteredLabel(canvas, "LIGHT", 0f, 0f, applyAlpha(0xFFFFFFFF, alpha255), 11f);
            }
            case GameObjectBean.TYPE_PLATFORM -> {
                fillPaint.setColor(fillColor);
                canvas.drawRoundRect(local, 6f, 6f, fillPaint);
                fillPaint.setColor(applyAlpha(0xFF4ADE80, alpha255));
                tempRect.set(local.left, local.top, local.right, Math.min(local.bottom, local.top + Math.min(10f, local.height() * 0.25f)));
                canvas.drawRoundRect(tempRect, 4f, 4f, fillPaint);
                strokePaint.setColor(applyAlpha(0xFF0F172A, alpha255));
                strokePaint.setStrokeWidth(Math.max(1f, 1.5f / zoom));
                canvas.drawRoundRect(local, 6f, 6f, strokePaint);
            }
            case GameObjectBean.TYPE_MOVING_PLATFORM -> {
                fillPaint.setColor(fillColor);
                canvas.drawRoundRect(local, 6f, 6f, fillPaint);
                strokePaint.setColor(applyAlpha(0xFFFFFFFF, Math.min(180, alpha255)));
                strokePaint.setStrokeWidth(Math.max(1f, 1.5f / zoom));
                canvas.drawRoundRect(local, 6f, 6f, strokePaint);
                dashedPaint.setColor(applyAlpha(0xFF38BDF8, 220));
                dashedPaint.setStrokeWidth(Math.max(1.5f, 2f / zoom));
                dashedPaint.setPathEffect(new DashPathEffect(new float[]{8f, 6f}, 0f));
                canvas.drawLine(0f, 0f, obj.moveX, obj.moveY, dashedPaint);
                fillPaint.setColor(applyAlpha(0xFF38BDF8, 220));
                canvas.drawCircle(obj.moveX, obj.moveY, 6f, fillPaint);
                drawCenteredLabel(canvas, "MOVING", 0f, 0f, applyAlpha(0xFFFFFFFF, alpha255), 10f);
            }
            case GameObjectBean.TYPE_ONEWAY_PLATFORM -> {
                fillPaint.setColor(applyAlpha(baseColor, Math.min(140, alpha255)));
                canvas.drawRoundRect(local, 4f, 4f, fillPaint);
                fillPaint.setColor(applyAlpha(0xFF2DD4BF, alpha255));
                tempRect.set(local.left, local.top, local.right, Math.min(local.bottom, local.top + Math.max(6f, local.height() * 0.35f)));
                canvas.drawRoundRect(tempRect, 4f, 4f, fillPaint);
                dashedPaint.setColor(applyAlpha(0xFFFFFFFF, Math.min(180, alpha255)));
                dashedPaint.setStrokeWidth(Math.max(1f, 1.4f / zoom));
                dashedPaint.setPathEffect(new DashPathEffect(new float[]{6f, 5f}, 0f));
                canvas.drawLine(local.left, local.bottom, local.right, local.bottom, dashedPaint);
            }
            case GameObjectBean.TYPE_PHYSICS_BOX -> {
                fillPaint.setColor(fillColor);
                canvas.drawRoundRect(local, 6f, 6f, fillPaint);
                strokePaint.setColor(applyAlpha(0xFFFFFBEB, Math.min(200, alpha255)));
                strokePaint.setStrokeWidth(Math.max(1f, 1.5f / zoom));
                canvas.drawRoundRect(local, 6f, 6f, strokePaint);
                canvas.drawLine(local.left + 4f, local.top + 4f, local.right - 4f, local.bottom - 4f, strokePaint);
                canvas.drawLine(local.right - 4f, local.top + 4f, local.left + 4f, local.bottom - 4f, strokePaint);
            }
            case GameObjectBean.TYPE_PHYSICS_CIRCLE -> {
                float r = minDim * 0.48f;
                fillPaint.setColor(fillColor);
                canvas.drawCircle(0f, 0f, r, fillPaint);
                strokePaint.setColor(applyAlpha(0xFFFFFBEB, Math.min(210, alpha255)));
                strokePaint.setStrokeWidth(Math.max(1f, 1.8f / zoom));
                canvas.drawCircle(0f, 0f, r, strokePaint);
                canvas.drawLine(-r * 0.6f, 0f, r * 0.6f, 0f, strokePaint);
                canvas.drawLine(0f, -r * 0.6f, 0f, r * 0.6f, strokePaint);
            }
            case GameObjectBean.TYPE_TRIGGER -> {
                fillPaint.setColor(applyAlpha(baseColor, Math.min(90, alpha255)));
                canvas.drawRect(local, fillPaint);
                dashedPaint.setColor(applyAlpha(baseColor, alpha255));
                dashedPaint.setStrokeWidth(Math.max(1f, 2f / zoom));
                dashedPaint.setPathEffect(new DashPathEffect(new float[]{10f, 6f}, 0f));
                canvas.drawRect(local, dashedPaint);
                drawCenteredLabel(canvas, "TRIGGER", 0f, 0f, applyAlpha(0xFFFFFFFF, alpha255), 11f);
            }
            case GameObjectBean.TYPE_GOAL -> {
                fillPaint.setColor(applyAlpha(baseColor, Math.min(100, alpha255)));
                canvas.drawRoundRect(local, 18f, 18f, fillPaint);
                strokePaint.setColor(applyAlpha(baseColor, alpha255));
                strokePaint.setStrokeWidth(Math.max(1.8f, 2.5f / zoom));
                canvas.drawRoundRect(local, 18f, 18f, strokePaint);
                drawCenteredLabel(canvas, "GOAL", 0f, 0f, applyAlpha(0xFFFFFFFF, alpha255), 12f);
            }
            case GameObjectBean.TYPE_CHECKPOINT -> {
                fillPaint.setColor(applyAlpha(baseColor, Math.min(85, alpha255)));
                canvas.drawRoundRect(local, 10f, 10f, fillPaint);
                strokePaint.setColor(applyAlpha(baseColor, alpha255));
                strokePaint.setStrokeWidth(Math.max(1.5f, 2f / zoom));
                canvas.drawRoundRect(local, 10f, 10f, strokePaint);
                drawCenteredLabel(canvas, "CP", 0f, 0f, applyAlpha(0xFFFFFFFF, alpha255), 12f);
            }
            case GameObjectBean.TYPE_DEATH_ZONE -> {
                fillPaint.setColor(applyAlpha(0xFFEF4444, Math.min(85, alpha255)));
                canvas.drawRect(local, fillPaint);
                dashedPaint.setColor(applyAlpha(0xFFEF4444, alpha255));
                dashedPaint.setStrokeWidth(Math.max(1.5f, 2f / zoom));
                dashedPaint.setPathEffect(new DashPathEffect(new float[]{12f, 6f}, 0f));
                canvas.drawRect(local, dashedPaint);
                drawCenteredLabel(canvas, "DEATH ZONE / VOID", 0f, 0f, applyAlpha(0xFFFCA5A5, alpha255), 12f);
            }
            case GameObjectBean.TYPE_HAZARD -> {
                fillPaint.setColor(fillColor);
                tempPath.reset();
                tempPath.moveTo(local.left, local.bottom);
                tempPath.lineTo(0f, local.top);
                tempPath.lineTo(local.right, local.bottom);
                tempPath.close();
                canvas.drawPath(tempPath, fillPaint);
                strokePaint.setColor(applyAlpha(0xFFFFFFFF, Math.min(200, alpha255)));
                strokePaint.setStrokeWidth(Math.max(1f, 1.5f / zoom));
                canvas.drawPath(tempPath, strokePaint);
            }
            case GameObjectBean.TYPE_COLLECTIBLE -> {
                float r = minDim * 0.45f;
                fillPaint.setColor(fillColor);
                canvas.drawCircle(0f, 0f, r, fillPaint);
                strokePaint.setColor(applyAlpha(0xFFFEF08A, alpha255));
                strokePaint.setStrokeWidth(Math.max(1.5f, 2f / zoom));
                canvas.drawCircle(0f, 0f, r * 0.72f, strokePaint);
            }
            case GameObjectBean.TYPE_BOUNCE_PAD -> {
                fillPaint.setColor(fillColor);
                canvas.drawRoundRect(local, 8f, 8f, fillPaint);
                strokePaint.setColor(applyAlpha(0xFFFFFFFF, Math.min(200, alpha255)));
                strokePaint.setStrokeWidth(Math.max(1.5f, 2f / zoom));
                canvas.drawLine(-local.width() * 0.25f, 4f, 0f, -6f, strokePaint);
                canvas.drawLine(0f, -6f, local.width() * 0.25f, 4f, strokePaint);
            }
            case GameObjectBean.TYPE_SPAWNER -> {
                fillPaint.setColor(applyAlpha(baseColor, Math.min(70, alpha255)));
                canvas.drawRoundRect(local, 10f, 10f, fillPaint);
                dashedPaint.setColor(applyAlpha(baseColor, alpha255));
                dashedPaint.setStrokeWidth(Math.max(1.5f, 2f / zoom));
                dashedPaint.setPathEffect(new DashPathEffect(new float[]{10f, 6f}, 0f));
                canvas.drawRoundRect(local, 10f, 10f, dashedPaint);
                String lbl = (obj.prefabName != null && !obj.prefabName.isEmpty())
                        ? "SPAWN: " + obj.prefabName : "SPAWNER";
                drawCenteredLabel(canvas, lbl, 0f, 0f, applyAlpha(0xFFE9D5FF, alpha255), 11f);
            }
            case GameObjectBean.TYPE_PROJECTILE -> {
                fillPaint.setColor(fillColor);
                canvas.drawRoundRect(local, Math.min(local.height() * 0.45f, 12f), Math.min(local.height() * 0.45f, 12f), fillPaint);
                strokePaint.setColor(applyAlpha(0xFFFFFFFF, alpha255));
                strokePaint.setStrokeWidth(Math.max(1f, 1.4f / zoom));
                canvas.drawRoundRect(local, Math.min(local.height() * 0.45f, 12f), Math.min(local.height() * 0.45f, 12f), strokePaint);
            }
            case GameObjectBean.TYPE_PARTICLES -> {
                fillPaint.setColor(applyAlpha(baseColor, Math.min(55, alpha255)));
                canvas.drawCircle(0f, 0f, minDim * 0.45f, fillPaint);
                fillPaint.setColor(fillColor);
                float r = Math.max(3f, minDim * 0.09f);
                canvas.drawCircle(0f, 0f, r * 1.3f, fillPaint);
                canvas.drawCircle(-minDim * 0.25f, -minDim * 0.18f, r, fillPaint);
                canvas.drawCircle(minDim * 0.24f, -minDim * 0.22f, r * 0.85f, fillPaint);
                canvas.drawCircle(minDim * 0.20f, minDim * 0.24f, r, fillPaint);
                canvas.drawCircle(-minDim * 0.22f, minDim * 0.20f, r * 0.8f, fillPaint);
            }
            case GameObjectBean.TYPE_CAMERA -> {
                fillPaint.setColor(applyAlpha(0xFF0F172A, Math.min(120, alpha255)));
                canvas.drawRoundRect(local, 8f, 8f, fillPaint);
                strokePaint.setColor(applyAlpha(0xFFFACC15, alpha255));
                strokePaint.setStrokeWidth(Math.max(1.5f, 2f / zoom));
                canvas.drawRoundRect(local, 8f, 8f, strokePaint);
                canvas.drawCircle(0f, 0f, minDim * 0.22f, strokePaint);
                drawCenteredLabel(canvas, "CAM", 0f, local.bottom - 10f, applyAlpha(0xFFFACC15, alpha255), 10f);
            }
            case GameObjectBean.TYPE_HUD_PANEL -> {
                fillPaint.setColor(fillColor);
                canvas.drawRoundRect(local, 14f, 14f, fillPaint);
                strokePaint.setColor(applyAlpha(0xFF94A3B8, Math.min(200, alpha255)));
                strokePaint.setStrokeWidth(Math.max(1.2f, 1.5f / zoom));
                canvas.drawRoundRect(local, 14f, 14f, strokePaint);
            }
            case GameObjectBean.TYPE_HUD_IMAGE -> {
                fillPaint.setColor(applyAlpha(baseColor, Math.min(120, alpha255)));
                canvas.drawRoundRect(local, 8f, 8f, fillPaint);
                strokePaint.setColor(applyAlpha(0xFFFFFFFF, Math.min(180, alpha255)));
                strokePaint.setStrokeWidth(Math.max(1f, 1.5f / zoom));
                canvas.drawRoundRect(local, 8f, 8f, strokePaint);
                drawCenteredLabel(canvas, "IMG", 0f, 0f, applyAlpha(0xFFFFFFFF, alpha255), 11f);
            }
            case GameObjectBean.TYPE_HUD_TEXT -> {
                dashedPaint.setColor(applyAlpha(0xFF94A3B8, Math.min(160, alpha255)));
                dashedPaint.setStrokeWidth(Math.max(1f, 1.2f / zoom));
                dashedPaint.setPathEffect(new DashPathEffect(new float[]{6f, 6f}, 0f));
                canvas.drawRect(local, dashedPaint);
                String label = (obj.text != null && !obj.text.isEmpty()) ? obj.text : (obj.name != null ? obj.name : "Text");
                int txtCol = obj.color != 0 ? applyAlpha(obj.color, alpha255) : applyAlpha(0xFFFFFFFF, alpha255);
                drawCenteredLabel(canvas, label, 0f, 0f, txtCol, Math.min(20f, Math.max(12f, local.height() * 0.42f)));
            }
            case GameObjectBean.TYPE_HUD_BUTTON -> {
                float radius = Math.min(16f, minDim * 0.35f);
                fillPaint.setColor(fillColor);
                canvas.drawRoundRect(local, radius, radius, fillPaint);
                strokePaint.setColor(applyAlpha(0xFFFFFFFF, Math.min(150, alpha255)));
                strokePaint.setStrokeWidth(Math.max(1f, 1.5f / zoom));
                canvas.drawRoundRect(local, radius, radius, strokePaint);
                String btnLabel = (obj.text != null && !obj.text.isEmpty()) ? obj.text : "BUTTON";
                drawCenteredLabel(canvas, btnLabel, 0f, 0f, applyAlpha(0xFFFFFFFF, alpha255), Math.min(16f, Math.max(11f, local.height() * 0.32f)));
            }
            case GameObjectBean.TYPE_HUD_DPAD -> {
                float arm = minDim * 0.17f;
                float half = minDim * 0.46f;
                fillPaint.setColor(applyAlpha(baseColor, Math.min(170, alpha255)));
                canvas.drawRoundRect(new RectF(-half, -arm, half, arm), 8f, 8f, fillPaint);
                canvas.drawRoundRect(new RectF(-arm, -half, arm, half), 8f, 8f, fillPaint);
                drawCenteredLabel(canvas, "D-PAD", 0f, 0f, applyAlpha(0xFFFFFFFF, alpha255), 10f);
            }
            case GameObjectBean.TYPE_HUD_JOYSTICK -> {
                float outerR = minDim * 0.46f;
                fillPaint.setColor(applyAlpha(baseColor, Math.min(75, alpha255)));
                canvas.drawCircle(0f, 0f, outerR, fillPaint);
                strokePaint.setColor(applyAlpha(baseColor, alpha255));
                strokePaint.setStrokeWidth(Math.max(1.5f, 2f / zoom));
                canvas.drawCircle(0f, 0f, outerR, strokePaint);
                fillPaint.setColor(applyAlpha(0xFFFFFFFF, Math.min(220, alpha255)));
                canvas.drawCircle(0f, 0f, outerR * 0.42f, fillPaint);
            }
            case GameObjectBean.TYPE_HUD_BAR -> {
                float r = Math.min(10f, local.height() * 0.4f);
                fillPaint.setColor(applyAlpha(0xFF1E293B, Math.min(210, alpha255)));
                canvas.drawRoundRect(local, r, r, fillPaint);
                tempRect.set(local.left + 2f, local.top + 2f, local.left + Math.max(4f, (local.width() - 4f) * 0.68f), local.bottom - 2f);
                fillPaint.setColor(fillColor);
                canvas.drawRoundRect(tempRect, r, r, fillPaint);
                strokePaint.setColor(applyAlpha(0xFFFFFFFF, Math.min(180, alpha255)));
                strokePaint.setStrokeWidth(Math.max(1f, 1.5f / zoom));
                canvas.drawRoundRect(local, r, r, strokePaint);
            }
            case GameObjectBean.TYPE_HUD_TOUCH_AREA -> {
                fillPaint.setColor(applyAlpha(baseColor, Math.min(50, alpha255)));
                canvas.drawRect(local, fillPaint);
                dashedPaint.setColor(applyAlpha(baseColor, alpha255));
                dashedPaint.setStrokeWidth(Math.max(1.2f, 1.8f / zoom));
                dashedPaint.setPathEffect(new DashPathEffect(new float[]{12f, 8f}, 0f));
                canvas.drawRect(local, dashedPaint);
                String tLabel = (obj.actionKey != null && !obj.actionKey.isEmpty()) ? "TOUCH: " + obj.actionKey : "TOUCH AREA";
                drawCenteredLabel(canvas, tLabel, 0f, 0f, applyAlpha(0xFFBAE6FD, alpha255), 13f);
            }
            case GameObjectBean.TYPE_HUD_DIALOG -> {
                fillPaint.setColor(fillColor);
                canvas.drawRoundRect(local, 16f, 16f, fillPaint);
                strokePaint.setColor(applyAlpha(0xFF60A5FA, alpha255));
                strokePaint.setStrokeWidth(Math.max(1.5f, 2f / zoom));
                canvas.drawRoundRect(local, 16f, 16f, strokePaint);
                drawCenteredLabel(canvas, nonEmptyStr(obj.text, "Dialog Box"), 0f, 0f, applyAlpha(0xFFFFFFFF, alpha255), 14f);
            }
            case GameObjectBean.TYPE_HUD_TOGGLE -> {
                float r = local.height() * 0.45f;
                fillPaint.setColor(applyAlpha(obj.toggled ? baseColor : 0xFF475569, alpha255));
                canvas.drawRoundRect(local, r, r, fillPaint);
                drawCenteredLabel(canvas, nonEmptyStr(obj.text, "TOGGLE") + (obj.toggled ? ": ON" : ": OFF"),
                        0f, 0f, applyAlpha(0xFFFFFFFF, alpha255), 12f);
            }
            default -> {
                // Sprite / Animated Sprite placeholder
                fillPaint.setColor(fillColor);
                canvas.drawRoundRect(local, 8f, 8f, fillPaint);
                strokePaint.setColor(applyAlpha(0xFFFFFFFF, Math.min(180, alpha255)));
                strokePaint.setStrokeWidth(Math.max(1f, 1.5f / zoom));
                canvas.drawRoundRect(local, 8f, 8f, strokePaint);
                String name = obj.name != null && !obj.name.isEmpty() ? obj.name : "Sprite";
                drawCenteredLabel(canvas, name, 0f, 0f, applyAlpha(0xFFFFFFFF, alpha255), 11f);
                if (GameObjectBean.TYPE_ANIMATED_SPRITE.equals(type)) {
                    drawBadgeLabel(canvas, local.left + 4f, local.bottom - 18f, "ANIM", 0xCC7C3AED);
                }
            }
        }
    }

    private void drawBadgeLabel(Canvas canvas, float x, float y, String text, int bgColor) {
        textPaint.setTextSize(9f);
        float w = textPaint.measureText(text) + 8f;
        float h = 14f;
        tempRect.set(x, y, x + w, y + h);
        badgePaint.setColor(bgColor);
        canvas.drawRoundRect(tempRect, 4f, 4f, badgePaint);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(text, x + 4f, y + 10.5f, textPaint);
    }

    private void drawCenteredLabel(Canvas canvas, String text, float cx, float cy, int color, float size) {
        textPaint.setColor(color);
        textPaint.setTextSize(size);
        textPaint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float baseline = cy - (fm.ascent + fm.descent) * 0.5f;
        canvas.drawText(text, cx, baseline, textPaint);
    }

    private void drawDropGhost(Canvas canvas) {
        GameObjectBean preview = GameObjectBean.createDefaultForType(dropPreviewType, dropPreviewStageX, dropPreviewStageY, 0);
        float w = Math.max(MIN_OBJECT_SIZE, preview.width);
        float h = Math.max(MIN_OBJECT_SIZE, preview.height);
        float x = dropPreviewStageX - w * 0.5f;
        float y = dropPreviewStageY - h * 0.5f;
        if (snapToGrid) {
            x = snap(x);
            y = snap(y);
        }
        tempRect.set(x, y, x + w, y + h);
        fillPaint.setColor(applyAlpha(colorPrimary, 80));
        canvas.drawRoundRect(tempRect, 8f, 8f, fillPaint);
        dashedPaint.setColor(colorPrimary);
        dashedPaint.setStrokeWidth(Math.max(1.5f, 2.5f / zoom));
        dashedPaint.setPathEffect(new DashPathEffect(new float[]{10f / zoom, 6f / zoom}, 0f));
        canvas.drawRoundRect(tempRect, 8f, 8f, dashedPaint);

        // Crosshair
        strokePaint.setColor(colorPrimary);
        strokePaint.setStrokeWidth(Math.max(1f, 1.5f / zoom));
        canvas.drawLine(x + w * 0.5f - 18f / zoom, y + h * 0.5f, x + w * 0.5f + 18f / zoom, y + h * 0.5f, strokePaint);
        canvas.drawLine(x + w * 0.5f, y + h * 0.5f - 18f / zoom, x + w * 0.5f, y + h * 0.5f + 18f / zoom, strokePaint);
    }

    private void drawSelectionOverlay(Canvas canvas, GameObjectBean obj) {
        PointF tl = objectLocalToScreen(obj, -Math.max(MIN_OBJECT_SIZE, obj.width) * 0.5f, -Math.max(MIN_OBJECT_SIZE, obj.height) * 0.5f);
        PointF tr = objectLocalToScreen(obj, Math.max(MIN_OBJECT_SIZE, obj.width) * 0.5f, -Math.max(MIN_OBJECT_SIZE, obj.height) * 0.5f);
        PointF br = objectLocalToScreen(obj, Math.max(MIN_OBJECT_SIZE, obj.width) * 0.5f, Math.max(MIN_OBJECT_SIZE, obj.height) * 0.5f);
        PointF bl = objectLocalToScreen(obj, -Math.max(MIN_OBJECT_SIZE, obj.width) * 0.5f, Math.max(MIN_OBJECT_SIZE, obj.height) * 0.5f);

        tempPath.reset();
        tempPath.moveTo(tl.x, tl.y);
        tempPath.lineTo(tr.x, tr.y);
        tempPath.lineTo(br.x, br.y);
        tempPath.lineTo(bl.x, bl.y);
        tempPath.close();

        strokePaint.setColor(obj.locked ? colorError : colorPrimary);
        strokePaint.setStrokeWidth(2.2f * density);
        canvas.drawPath(tempPath, strokePaint);

        if (!obj.locked) {
            // Rotate handle stem and knob above top-center
            PointF topCenter = new PointF((tl.x + tr.x) * 0.5f, (tl.y + tr.y) * 0.5f);
            PointF rotHandle = getRotateHandleScreenPos(obj);

            strokePaint.setColor(colorPrimary);
            strokePaint.setStrokeWidth(1.8f * density);
            canvas.drawLine(topCenter.x, topCenter.y, rotHandle.x, rotHandle.y, strokePaint);

            float rotR = 9f * density;
            handleFillPaint.setColor(0xFF0F172A);
            handleStrokePaint.setColor(colorPrimary);
            canvas.drawCircle(rotHandle.x, rotHandle.y, rotR, handleFillPaint);
            canvas.drawCircle(rotHandle.x, rotHandle.y, rotR, handleStrokePaint);

            // Resize handle at bottom-right
            float resizeR = 9f * density;
            handleFillPaint.setColor(colorPrimary);
            handleStrokePaint.setColor(Color.WHITE);
            canvas.drawCircle(br.x, br.y, resizeR, handleFillPaint);
            canvas.drawCircle(br.x, br.y, resizeR, handleStrokePaint);
        }

        // Info & quick events pill below/above object
        float minX = Math.min(Math.min(tl.x, tr.x), Math.min(bl.x, br.x));
        float maxY = Math.max(Math.max(tl.y, tr.y), Math.max(bl.y, br.y));
        String info = String.format(Locale.US, "%s • %d,%d (%dx%d)",
                obj.name != null ? obj.name : obj.id,
                Math.round(obj.x), Math.round(obj.y),
                Math.round(obj.width), Math.round(obj.height));
        textPaint.setTextSize(10.5f * density);
        textPaint.setTextAlign(Paint.Align.LEFT);
        float infoW = textPaint.measureText(info) + 16f * density;
        float pillH = 22f * density;
        float infoX = clamp(minX, 8f * density, Math.max(8f * density, getWidth() - infoW - 76f * density));
        float infoY = Math.min(getHeight() - pillH - 8f * density, maxY + 10f * density);

        tempRect.set(infoX, infoY, infoX + infoW, infoY + pillH);
        badgePaint.setColor(0xD90F172A);
        canvas.drawRoundRect(tempRect, 11f * density, 11f * density, badgePaint);
        textPaint.setColor(Color.WHITE);
        canvas.drawText(info, infoX + 8f * density, infoY + 15f * density, textPaint);

        // Quick event blocks pill next to info pill
        String evLabel = "Blocks";
        float evW = textPaint.measureText(evLabel) + 16f * density;
        float evX = tempRect.right + 6f * density;
        if (evX + evW <= getWidth() - 6f * density) {
            eventChipRect.set(evX, infoY, evX + evW, infoY + pillH);
            badgePaint.setColor(colorPrimary);
            canvas.drawRoundRect(eventChipRect, 11f * density, 11f * density, badgePaint);
            textPaint.setColor(colorOnPrimary);
            canvas.drawText(evLabel, evX + 8f * density, infoY + 15f * density, textPaint);
            eventChipVisible = true;
        }
    }

    private void drawTrashZone(Canvas canvas) {
        float w = Math.min(220f * density, getWidth() * 0.7f);
        float h = 42f * density;
        float left = (getWidth() - w) * 0.5f;
        float top = 12f * density;
        trashRect.set(left, top, left + w, top + h);

        badgePaint.setColor(isOverTrashZone ? colorErrorContainer : colorSurfaceContainerHighest);
        canvas.drawRoundRect(trashRect, 21f * density, 21f * density, badgePaint);

        strokePaint.setColor(isOverTrashZone ? colorError : 0x66FFFFFF);
        strokePaint.setStrokeWidth(1.5f * density);
        canvas.drawRoundRect(trashRect, 21f * density, 21f * density, strokePaint);

        textPaint.setTextSize(12.5f * density);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(isOverTrashZone ? colorOnErrorContainer : colorOnSurface);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float baseline = trashRect.centerY() - (fm.ascent + fm.descent) * 0.5f;
        canvas.drawText(isOverTrashZone ? "Drop to delete" : "Drag here to delete", trashRect.centerX(), baseline, textPaint);
    }

    private void drawStatusOverlay(Canvas canvas, int virtualW, int virtualH) {
        String filterTag = layerFilter == LAYER_WORLD_ONLY ? " • World"
                : (layerFilter == LAYER_HUD_ONLY ? " • HUD" : "");
        String snapTag = snapToGrid ? " • Grid" : "";
        int worldW = getWorldWidth();
        int worldH = getWorldHeight();
        String sizeTag = (worldW != virtualW || worldH != virtualH)
                ? String.format(Locale.US, "Cam %dx%d • World %dx%d", virtualW, virtualH, worldW, worldH)
                : String.format(Locale.US, "%dx%d", virtualW, virtualH);
        String status = String.format(Locale.US, "%d%% • %s%s%s",
                Math.round(zoom * 100f), sizeTag, filterTag, snapTag);

        textPaint.setTextSize(10.5f * density);
        textPaint.setTextAlign(Paint.Align.LEFT);
        float w = textPaint.measureText(status) + 16f * density;
        float h = 22f * density;
        float x = 10f * density;
        float y = getHeight() - h - 10f * density;
        if (y > 0f) {
            tempRect.set(x, y, x + w, y + h);
            badgePaint.setColor(0xB30F172A);
            canvas.drawRoundRect(tempRect, 11f * density, 11f * density, badgePaint);
            textPaint.setColor(0xFFE2E8F0);
            canvas.drawText(status, x + 8f * density, y + 14.5f * density, textPaint);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (scaleDetector != null) {
            scaleDetector.onTouchEvent(event);
        }

        int action = event.getActionMasked();
        int pointerCount = event.getPointerCount();

        if (pointerCount >= 2) {
            float fx = getAverageX(event);
            float fy = getAverageY(event);
            if (!isMultiTouch) {
                isMultiTouch = true;
                cancelPendingObjectTransforms();
                mode = MODE_TWO_FINGER_PAN;
                lastMultiFocusX = fx;
                lastMultiFocusY = fy;
            } else if (action == MotionEvent.ACTION_MOVE && !scaleDetector.isInProgress()) {
                panX += fx - lastMultiFocusX;
                panY += fy - lastMultiFocusY;
                lastMultiFocusX = fx;
                lastMultiFocusY = fy;
                invalidate();
            } else {
                lastMultiFocusX = fx;
                lastMultiFocusY = fy;
            }
            return true;
        }

        if (isMultiTouch) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                isMultiTouch = false;
                mode = MODE_NONE;
            }
            return true;
        }

        float vx = event.getX();
        float vy = event.getY();

        switch (action) {
            case MotionEvent.ACTION_DOWN -> {
                downScreenX = vx;
                downScreenY = vy;
                lastScreenX = vx;
                lastScreenY = vy;
                dragMovedBeyondSlop = false;
                isOverTrashZone = false;

                // Quick event blocks pill tap on selected object
                if (eventChipVisible && selectedObject != null && eventChipRect.contains(vx, vy)) {
                    mode = MODE_NONE;
                    openDefaultEventBlocks(selectedObject);
                    return true;
                }

                // Check selected object's rotate / resize handles first
                if (selectedObject != null && !selectedObject.locked && matchesLayerFilter(selectedObject)) {
                    PointF rotPos = getRotateHandleScreenPos(selectedObject);
                    if (distance(vx, vy, rotPos.x, rotPos.y) <= 22f * density) {
                        mode = MODE_ROTATE_OBJECT;
                        pendingUndoSnapshot = captureObjectsSnapshot();
                        origObjRot = selectedObject.rotation;
                        return true;
                    }

                    PointF brPos = objectLocalToScreen(selectedObject,
                            Math.max(MIN_OBJECT_SIZE, selectedObject.width) * 0.5f,
                            Math.max(MIN_OBJECT_SIZE, selectedObject.height) * 0.5f);
                    if (distance(vx, vy, brPos.x, brPos.y) <= 22f * density) {
                        mode = MODE_RESIZE_OBJECT;
                        pendingUndoSnapshot = captureObjectsSnapshot();
                        dragStartStageX = screenToStageX(vx);
                        dragStartStageY = screenToStageY(vy);
                        origObjX = selectedObject.x;
                        origObjY = selectedObject.y;
                        origObjW = selectedObject.width;
                        origObjH = selectedObject.height;
                        return true;
                    }
                }

                // Hit-test top-most unlocked object
                float stX = screenToStageX(vx);
                float stY = screenToStageY(vy);
                GameObjectBean hit = hitTestUnlockedObject(stX, stY);
                if (hit != null) {
                    if (selectedObject != hit) {
                        selectObject(hit);
                    }
                    mode = MODE_DRAG_OBJECT;
                    pendingUndoSnapshot = captureObjectsSnapshot();
                    dragStartStageX = stX;
                    dragStartStageY = stY;
                    origObjX = hit.x;
                    origObjY = hit.y;
                    return true;
                }

                // Empty space -> pan or tap to deselect / double-tap to reset camera
                mode = MODE_PAN_OR_TAP;
                return true;
            }

            case MotionEvent.ACTION_MOVE -> {
                if (mode == MODE_DRAG_OBJECT && selectedObject != null) {
                    if (!dragMovedBeyondSlop && distance(vx, vy, downScreenX, downScreenY) > touchSlop) {
                        dragMovedBeyondSlop = true;
                    }
                    if (dragMovedBeyondSlop) {
                        float stX = screenToStageX(vx);
                        float stY = screenToStageY(vy);
                        float newX = origObjX + (stX - dragStartStageX);
                        float newY = origObjY + (stY - dragStartStageY);
                        if (snapToGrid) {
                            newX = snap(newX);
                            newY = snap(newY);
                        }
                        selectedObject.x = newX;
                        selectedObject.y = newY;
                        boolean overTrash = trashRect.width() > 0f
                                && vx >= trashRect.left - 12f * density
                                && vx <= trashRect.right + 12f * density
                                && vy >= trashRect.top - 12f * density
                                && vy <= trashRect.bottom + 12f * density;
                        if (overTrash != isOverTrashZone) {
                            isOverTrashZone = overTrash;
                            if (overTrash) {
                                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                            }
                        }
                        invalidate();
                    }
                    return true;
                }

                if (mode == MODE_RESIZE_OBJECT && selectedObject != null) {
                    float stX = screenToStageX(vx);
                    float stY = screenToStageY(vy);
                    float dx = stX - dragStartStageX;
                    float dy = stY - dragStartStageY;
                    double rad = Math.toRadians(selectedObject.rotation);
                    float cos = (float) Math.cos(rad);
                    float sin = (float) Math.sin(rad);
                    float localDx = dx * cos + dy * sin;
                    float localDy = -dx * sin + dy * cos;
                    float sx = Math.abs(selectedObject.scaleX == 0f ? 1f : selectedObject.scaleX);
                    float sy = Math.abs(selectedObject.scaleY == 0f ? 1f : selectedObject.scaleY);

                    float newW = Math.max(MIN_OBJECT_SIZE, origObjW + localDx / sx);
                    float newH = Math.max(MIN_OBJECT_SIZE, origObjH + localDy / sy);
                    if (snapToGrid) {
                        newW = Math.max(MIN_OBJECT_SIZE, snap(newW));
                        newH = Math.max(MIN_OBJECT_SIZE, snap(newH));
                    }
                    selectedObject.width = newW;
                    selectedObject.height = newH;
                    invalidate();
                    return true;
                }

                if (mode == MODE_ROTATE_OBJECT && selectedObject != null) {
                    float w = Math.max(MIN_OBJECT_SIZE, selectedObject.width);
                    float h = Math.max(MIN_OBJECT_SIZE, selectedObject.height);
                    float centerScreenX = stageToScreenX(selectedObject.x + w * 0.5f);
                    float centerScreenY = stageToScreenY(selectedObject.y + h * 0.5f);
                    float angle = (float) Math.toDegrees(Math.atan2(vy - centerScreenY, vx - centerScreenX)) + 90f;
                    angle = ((angle % 360f) + 360f) % 360f;
                    if (snapToGrid) {
                        angle = Math.round(angle / 5f) * 5f;
                        if (angle >= 360f) {
                            angle = 0f;
                        }
                    }
                    selectedObject.rotation = angle;
                    invalidate();
                    return true;
                }

                if (mode == MODE_PAN_OR_TAP) {
                    if (distance(vx, vy, downScreenX, downScreenY) > touchSlop) {
                        mode = MODE_PAN_CANVAS;
                    }
                }
                if (mode == MODE_PAN_CANVAS) {
                    panX += vx - lastScreenX;
                    panY += vy - lastScreenY;
                    lastScreenX = vx;
                    lastScreenY = vy;
                    invalidate();
                }
                return true;
            }

            case MotionEvent.ACTION_UP -> {
                if (mode == MODE_DRAG_OBJECT && selectedObject != null) {
                    if (isOverTrashZone && scene != null && scene.objects != null) {
                        pushUndoSnapshot(pendingUndoSnapshot);
                        scene.objects.remove(selectedObject);
                        selectedObject = null;
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        if (listener != null) {
                            listener.onObjectSelected(null);
                            listener.onSceneModified();
                        }
                    } else if (dragMovedBeyondSlop && (selectedObject.x != origObjX || selectedObject.y != origObjY)) {
                        pushUndoSnapshot(pendingUndoSnapshot);
                        if (listener != null) {
                            listener.onObjectSelected(selectedObject);
                            listener.onSceneModified();
                        }
                    } else {
                        // Treat as tap on object -> check double-tap to open events
                        long now = System.currentTimeMillis();
                        if (selectedObject.id != null
                                && selectedObject.id.equals(lastTappedObjectId)
                                && (now - lastTapTimeMs) <= DOUBLE_TAP_TIMEOUT_MS
                                && distance(vx, vy, lastTapX, lastTapY) <= 36f * density) {
                            lastTapTimeMs = 0L;
                            lastTappedObjectId = null;
                            openDefaultEventBlocks(selectedObject);
                        } else {
                            lastTapTimeMs = now;
                            lastTapX = vx;
                            lastTapY = vy;
                            lastTappedObjectId = selectedObject.id;
                        }
                    }
                } else if (mode == MODE_RESIZE_OBJECT && selectedObject != null) {
                    if (selectedObject.width != origObjW || selectedObject.height != origObjH) {
                        pushUndoSnapshot(pendingUndoSnapshot);
                        if (listener != null) {
                            listener.onObjectSelected(selectedObject);
                            listener.onSceneModified();
                        }
                    }
                } else if (mode == MODE_ROTATE_OBJECT && selectedObject != null) {
                    if (selectedObject.rotation != origObjRot) {
                        pushUndoSnapshot(pendingUndoSnapshot);
                        if (listener != null) {
                            listener.onObjectSelected(selectedObject);
                            listener.onSceneModified();
                        }
                    }
                } else if (mode == MODE_PAN_OR_TAP) {
                    long now = System.currentTimeMillis();
                    if (lastTappedObjectId == null
                            && (now - lastTapTimeMs) <= DOUBLE_TAP_TIMEOUT_MS
                            && distance(vx, vy, lastTapX, lastTapY) <= 36f * density) {
                        lastTapTimeMs = 0L;
                        resetCameraView();
                    } else {
                        lastTapTimeMs = now;
                        lastTapX = vx;
                        lastTapY = vy;
                        lastTappedObjectId = null;
                        selectObject(null);
                    }
                }

                mode = MODE_NONE;
                isOverTrashZone = false;
                pendingUndoSnapshot = null;
                invalidate();
                return true;
            }

            case MotionEvent.ACTION_CANCEL -> {
                cancelPendingObjectTransforms();
                mode = MODE_NONE;
                isOverTrashZone = false;
                invalidate();
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    private void cancelPendingObjectTransforms() {
        if (selectedObject != null) {
            if (mode == MODE_DRAG_OBJECT) {
                selectedObject.x = origObjX;
                selectedObject.y = origObjY;
            } else if (mode == MODE_RESIZE_OBJECT) {
                selectedObject.width = origObjW;
                selectedObject.height = origObjH;
            } else if (mode == MODE_ROTATE_OBJECT) {
                selectedObject.rotation = origObjRot;
            }
        }
        pendingUndoSnapshot = null;
        isOverTrashZone = false;
    }

    private void openDefaultEventBlocks(GameObjectBean obj) {
        if (listener == null || obj == null) {
            return;
        }
        String eventType = GameObjectBean.TYPE_HUD_BUTTON.equals(obj.type)
                ? GameScriptNode.EVENT_BUTTON
                : GameScriptNode.EVENT_TOUCH;
        listener.onOpenObjectEventBlocks(obj, eventType, "");
    }

    private boolean handlePaletteDragEvent(View v, DragEvent event) {
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED -> {
                ClipDescription desc = event.getClipDescription();
                if (desc != null && (desc.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)
                        || "game_palette_type".contentEquals(desc.getLabel()))) {
                    Object local = event.getLocalState();
                    dropPreviewType = local instanceof String ? (String) local : GameObjectBean.TYPE_SPRITE;
                    return true;
                }
                return false;
            }
            case DragEvent.ACTION_DRAG_ENTERED, DragEvent.ACTION_DRAG_LOCATION -> {
                dropPreviewStageX = screenToStageX(event.getX());
                dropPreviewStageY = screenToStageY(event.getY());
                if (dropPreviewType == null && event.getLocalState() instanceof String s) {
                    dropPreviewType = s;
                }
                invalidate();
                return true;
            }
            case DragEvent.ACTION_DROP -> {
                String droppedType = null;
                ClipData clipData = event.getClipData();
                if (clipData != null && clipData.getItemCount() > 0 && clipData.getItemAt(0).getText() != null) {
                    droppedType = clipData.getItemAt(0).getText().toString();
                } else if (event.getLocalState() instanceof String s) {
                    droppedType = s;
                }
                float stX = screenToStageX(event.getX());
                float stY = screenToStageY(event.getY());
                dropPreviewType = null;
                if (droppedType != null && !droppedType.isEmpty()) {
                    createAndInsertObject(droppedType, stX, stY);
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                } else {
                    invalidate();
                }
                return true;
            }
            case DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> {
                dropPreviewType = null;
                invalidate();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Nullable
    private GameObjectBean hitTestUnlockedObject(float stageX, float stageY) {
        if (scene == null || scene.objects == null || scene.objects.isEmpty()) {
            return null;
        }
        ArrayList<GameObjectBean> sorted = new ArrayList<>(scene.objects);
        Collections.sort(sorted, (a, b) -> Integer.compare(a.zIndex, b.zIndex));

        float touchPadStage = (10f * density) / Math.max(0.25f, zoom);
        for (int i = sorted.size() - 1; i >= 0; i--) {
            GameObjectBean obj = sorted.get(i);
            if (obj.locked || !matchesLayerFilter(obj)) {
                continue;
            }
            if (hitTestObject(obj, stageX, stageY, touchPadStage)) {
                return obj;
            }
        }
        return null;
    }

    private boolean hitTestObject(GameObjectBean obj, float stageX, float stageY, float padStage) {
        float w = Math.max(MIN_OBJECT_SIZE, obj.width);
        float h = Math.max(MIN_OBJECT_SIZE, obj.height);
        float cx = obj.x + w * 0.5f;
        float cy = obj.y + h * 0.5f;
        float sx = obj.scaleX == 0f ? 1f : obj.scaleX;
        float sy = obj.scaleY == 0f ? 1f : obj.scaleY;

        float dx = stageX - cx;
        float dy = stageY - cy;
        double rad = Math.toRadians(obj.rotation);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);

        float localX = (dx * cos + dy * sin) / sx;
        float localY = (-dx * sin + dy * cos) / sy;

        return Math.abs(localX) <= (w * 0.5f + padStage)
                && Math.abs(localY) <= (h * 0.5f + padStage);
    }

    private PointF objectLocalToScreen(GameObjectBean obj, float localX, float localY) {
        float w = Math.max(MIN_OBJECT_SIZE, obj.width);
        float h = Math.max(MIN_OBJECT_SIZE, obj.height);
        float cx = obj.x + w * 0.5f;
        float cy = obj.y + h * 0.5f;
        float sx = obj.scaleX == 0f ? 1f : obj.scaleX;
        float sy = obj.scaleY == 0f ? 1f : obj.scaleY;

        float px = localX * sx;
        float py = localY * sy;
        double rad = Math.toRadians(obj.rotation);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        float stX = cx + (px * cos - py * sin);
        float stY = cy + (px * sin + py * cos);
        return new PointF(stageToScreenX(stX), stageToScreenY(stY));
    }

    private PointF getRotateHandleScreenPos(GameObjectBean obj) {
        float h = Math.max(MIN_OBJECT_SIZE, obj.height);
        PointF topCenter = objectLocalToScreen(obj, 0f, -h * 0.5f);
        double rad = Math.toRadians(obj.rotation);
        float ux = (float) Math.sin(rad);
        float uy = (float) -Math.cos(rad);
        float offset = 28f * density;
        return new PointF(topCenter.x + ux * offset, topCenter.y + uy * offset);
    }

    @Nullable
    private Bitmap getBitmap(String resName) {
        if (resName == null || resName.isEmpty() || scId == null || scId.isEmpty()) {
            return null;
        }
        if (bitmapCache.containsKey(resName)) {
            Bitmap cached = bitmapCache.get(resName);
            if (cached != null && !cached.isRecycled()) {
                return cached;
            }
            bitmapCache.remove(resName);
        }
        if (missingBitmaps.contains(resName)) {
            return null;
        }
        File file = GameProjectRepository.getImageFile(scId, resName);
        if (file == null || !file.exists() || !file.isFile()) {
            missingBitmaps.add(resName);
            return null;
        }
        Bitmap decoded = decodeBoundedBitmap(file.getAbsolutePath(), 1024);
        if (decoded != null) {
            bitmapCache.put(resName, decoded);
            return decoded;
        }
        missingBitmaps.add(resName);
        return null;
    }

    @Nullable
    private static Bitmap decodeBoundedBitmap(String path, int maxDim) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, opts);
            int sampleSize = 1;
            int w = opts.outWidth;
            int h = opts.outHeight;
            while (w / sampleSize > maxDim || h / sampleSize > maxDim) {
                sampleSize *= 2;
            }
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sampleSize;
            return BitmapFactory.decodeFile(path, opts);
        } catch (Throwable t) {
            return null;
        }
    }

    private boolean matchesLayerFilter(GameObjectBean obj) {
        if (obj == null) {
            return false;
        }
        boolean hud = isHudObject(obj);
        if (layerFilter == LAYER_WORLD_ONLY) {
            return !hud;
        }
        if (layerFilter == LAYER_HUD_ONLY) {
            return hud;
        }
        return true;
    }

    private static boolean isHudObject(GameObjectBean obj) {
        return obj.isHud || obj.isHudType();
    }

    private int getVirtualWidth() {
        return (project != null && project.virtualWidth > 0) ? project.virtualWidth : 1280;
    }

    private int getVirtualHeight() {
        return (project != null && project.virtualHeight > 0) ? project.virtualHeight : 720;
    }

    private int getWorldWidth() {
        int vw = getVirtualWidth();
        return (scene != null && scene.worldWidth > 0) ? Math.max(vw, scene.worldWidth) : vw;
    }

    private int getWorldHeight() {
        int vh = getVirtualHeight();
        return (scene != null && scene.worldHeight > 0) ? Math.max(vh, scene.worldHeight) : vh;
    }

    private static String nonEmptyStr(@Nullable String s, String fallback) {
        return (s != null && !s.trim().isEmpty()) ? s : fallback;
    }

    private float screenToStageX(float screenX) {
        return (screenX - panX) / zoom;
    }

    private float screenToStageY(float screenY) {
        return (screenY - panY) / zoom;
    }

    private float stageToScreenX(float stageX) {
        return panX + stageX * zoom;
    }

    private float stageToScreenY(float stageY) {
        return panY + stageY * zoom;
    }

    private static float snap(float value) {
        return Math.round(value / SNAP_GRID_PX) * SNAP_GRID_PX;
    }

    private static float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }

    private static float distance(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return (float) Math.hypot(dx, dy);
    }

    private static float getAverageX(MotionEvent event) {
        float sum = 0f;
        int count = event.getPointerCount();
        for (int i = 0; i < count; i++) {
            sum += event.getX(i);
        }
        return count > 0 ? sum / count : 0f;
    }

    private static float getAverageY(MotionEvent event) {
        float sum = 0f;
        int count = event.getPointerCount();
        for (int i = 0; i < count; i++) {
            sum += event.getY(i);
        }
        return count > 0 ? sum / count : 0f;
    }

    private static int resolveDisplayColor(GameObjectBean obj, String type) {
        if (obj.color != 0) {
            return obj.color;
        }
        return switch (type) {
            case GameObjectBean.TYPE_PARALLAX_BG -> 0xFF1E293B;
            case GameObjectBean.TYPE_WORLD_TEXT -> 0xFF38BDF8;
            case GameObjectBean.TYPE_LIGHT_2D -> 0xFFFACC15;
            case GameObjectBean.TYPE_PLATFORM -> 0xFF334155;
            case GameObjectBean.TYPE_MOVING_PLATFORM -> 0xFF0EA5E9;
            case GameObjectBean.TYPE_ONEWAY_PLATFORM -> 0xFF2DD4BF;
            case GameObjectBean.TYPE_PHYSICS_BOX -> 0xFFD97706;
            case GameObjectBean.TYPE_PHYSICS_CIRCLE -> 0xFFF97316;
            case GameObjectBean.TYPE_TRIGGER -> 0xFF10B981;
            case GameObjectBean.TYPE_GOAL -> 0xFF22C55E;
            case GameObjectBean.TYPE_CHECKPOINT -> 0xFF38BDF8;
            case GameObjectBean.TYPE_DEATH_ZONE -> 0xFFEF4444;
            case GameObjectBean.TYPE_HAZARD -> 0xFFF43F5E;
            case GameObjectBean.TYPE_COLLECTIBLE -> 0xFFFACC15;
            case GameObjectBean.TYPE_BOUNCE_PAD -> 0xFFA855F7;
            case GameObjectBean.TYPE_SPAWNER -> 0xFF8B5CF6;
            case GameObjectBean.TYPE_PROJECTILE -> 0xFF06B6D4;
            case GameObjectBean.TYPE_PARTICLES -> 0xFFF472B6;
            case GameObjectBean.TYPE_CAMERA -> 0xFFFACC15;
            case GameObjectBean.TYPE_HUD_PANEL -> 0xFF1E293B;
            case GameObjectBean.TYPE_HUD_IMAGE -> 0xFF60A5FA;
            case GameObjectBean.TYPE_HUD_BUTTON -> 0xFF2563EB;
            case GameObjectBean.TYPE_HUD_DPAD -> 0xFF64748B;
            case GameObjectBean.TYPE_HUD_JOYSTICK -> 0xFF38BDF8;
            case GameObjectBean.TYPE_HUD_BAR -> 0xFF22C55E;
            case GameObjectBean.TYPE_HUD_TOUCH_AREA -> 0xFF06B6D4;
            case GameObjectBean.TYPE_HUD_DIALOG -> 0xFF0F172A;
            case GameObjectBean.TYPE_HUD_TOGGLE -> 0xFF22C55E;
            case GameObjectBean.TYPE_ANIMATED_SPRITE -> 0xFF8B5CF6;
            default -> 0xFF3B82F6;
        };
    }

    private static int applyAlpha(int color, int alpha255) {
        int origA = Color.alpha(color);
        int combinedA = Math.round((origA / 255f) * alpha255);
        return Color.argb(
                Math.max(0, Math.min(255, combinedA)),
                Color.red(color),
                Color.green(color),
                Color.blue(color)
        );
    }
}
