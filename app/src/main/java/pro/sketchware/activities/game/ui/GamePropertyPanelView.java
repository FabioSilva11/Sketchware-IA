package pro.sketchware.activities.game.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.ImageViewCompat;

import com.besome.sketch.lib.ui.CustomHorizontalScrollView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import pro.sketchware.R;
import pro.sketchware.activities.game.model.GameObjectBean;
import pro.sketchware.activities.game.model.GameProject;
import pro.sketchware.activities.game.model.GameProjectRepository;
import pro.sketchware.activities.game.model.GameScene;
import pro.sketchware.activities.game.model.GameScriptNode;
import pro.sketchware.utility.SketchwareUtil;

/**
 * Bottom inspector panel with 1:1 parity to Sketchware's native ViewProperty layout:
 * 40dp top selector bar (spn_widget, img_delete, img_save), rounded selector tabs (Basic, Física, Event),
 * and 80x80dp MaterialCardView items inside hcv_property / property_contents.
 */
public class GamePropertyPanelView extends LinearLayout {

    public interface PropertyActionListener {
        void onPropertyChanged();

        void onRequestOpenImageManager();

        void onRequestOpenSoundManager();

        void onRequestOpenFontManager();

        void onOpenEventBlocks(GameObjectBean target, String eventType, String eventParam);
    }

    private static final int TAB_BASIC = 0;
    private static final int TAB_PHYSICS = 1;
    private static final int TAB_EVENT = 2;

    private static final int[] PRESET_COLORS = new int[]{
            0xFF0F172A, 0xFF1E293B, 0xFF334155, 0xFF64748B, 0xFFE2E8F0, 0xFFFFFFFF,
            0xFFEF4444, 0xFFF97316, 0xFFF59E0B, 0xFFEAB308, 0xFF84CC16, 0xFF22C55E,
            0xFF10B981, 0xFF14B8A6, 0xFF06B6D4, 0xFF0EA5E9, 0xFF3B82F6, 0xFF6366F1,
            0xFF8B5CF6, 0xFFA855F7, 0xFFD946EF, 0xFFEC4899, 0xFFF43F5E, 0xFF78350F
    };

    private static class SpinnerEntry {
        @Nullable final GameObjectBean object;
        final String label;

        SpinnerEntry(@Nullable GameObjectBean object, String label) {
            this.object = object;
            this.label = label;
        }
    }

    private String scId;
    private GameProject project;
    private GameScene scene;
    private GameStageEditorView stageView;
    private PropertyActionListener listener;
    private GameObjectBean selected;

    private int activeTab = TAB_BASIC;
    private boolean suppressSpinnerCallback = false;

    private Spinner spnWidget;
    private ImageButton imgDelete;
    private ImageButton imgSave;
    private LinearLayout layoutPropertyGroup;
    private CustomHorizontalScrollView hcvProperty;
    private LinearLayout cardsStrip;
    private final List<View> groupTabViews = new ArrayList<>();
    private final List<SpinnerEntry> spinnerEntries = new ArrayList<>();
    private WidgetSpinnerAdapter spinnerAdapter;

    private int colorSurfaceContainer;
    private int colorSurfaceContainerHigh;
    private int colorSurfaceContainerHighest;
    private int colorPrimary;
    private int colorOnPrimary;
    private int colorPrimaryContainer;
    private int colorOnPrimaryContainer;
    private int colorError;
    private int colorErrorContainer;
    private int colorOnErrorContainer;
    private int colorOnSurface;
    private int colorOnSurfaceVariant;
    private int colorOutlineVariant;

    public GamePropertyPanelView(Context context) {
        this(context, null);
    }

    public GamePropertyPanelView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setOrientation(VERTICAL);

        colorSurfaceContainer = MaterialColors.getColor(context, R.attr.colorSurfaceContainer, 0xFF1E2430);
        colorSurfaceContainerHigh = MaterialColors.getColor(context, R.attr.colorSurfaceContainerHigh, 0xFF283040);
        colorSurfaceContainerHighest = MaterialColors.getColor(context, R.attr.colorSurfaceContainerHighest, 0xFF323B4E);
        colorPrimary = MaterialColors.getColor(context, R.attr.colorPrimary, 0xFF3B82F6);
        colorOnPrimary = MaterialColors.getColor(context, R.attr.colorOnPrimary, 0xFFFFFFFF);
        colorPrimaryContainer = MaterialColors.getColor(context, R.attr.colorPrimaryContainer, 0xFF1D4ED8);
        colorOnPrimaryContainer = MaterialColors.getColor(context, R.attr.colorOnPrimaryContainer, 0xFFDBEAFE);
        colorError = MaterialColors.getColor(context, R.attr.colorError, 0xFFEF4444);
        colorErrorContainer = MaterialColors.getColor(context, R.attr.colorErrorContainer, 0xFF7F1D1D);
        colorOnErrorContainer = MaterialColors.getColor(context, R.attr.colorOnErrorContainer, 0xFFFEE2E2);
        colorOnSurface = MaterialColors.getColor(context, R.attr.colorOnSurface, 0xFFE5E7EB);
        colorOnSurfaceVariant = MaterialColors.getColor(context, R.attr.colorOnSurfaceVariant, 0xFF94A3B8);
        colorOutlineVariant = MaterialColors.getColor(context, R.attr.colorOutlineVariant, 0x3394A3B8);

        setBackgroundColor(colorSurfaceContainer);

        LayoutInflater.from(context).inflate(R.layout.view_property, this, true);

        spnWidget = findViewById(R.id.spn_widget);
        imgDelete = findViewById(R.id.img_delete);
        imgSave = findViewById(R.id.img_save);
        layoutPropertyGroup = findViewById(R.id.layout_property_group);
        hcvProperty = findViewById(R.id.hcv_property);
        cardsStrip = findViewById(R.id.property_contents);

        View legacyViewEvent = findViewById(R.id.view_event);
        if (legacyViewEvent != null) {
            legacyViewEvent.setVisibility(GONE);
        }
        View legacySeeAll = findViewById(R.id.layout_property_see_all);
        if (legacySeeAll != null) {
            legacySeeAll.setVisibility(GONE);
        }

        hcvProperty.setHorizontalScrollBarEnabled(false);
        cardsStrip.setGravity(Gravity.CENTER_VERTICAL);

        imgDelete.setImageResource(R.drawable.ic_mtrl_delete);
        imgDelete.setOnClickListener(v -> deleteSelectedObject());

        imgSave.setImageResource(R.drawable.ic_mtrl_save);
        imgSave.setOnClickListener(v -> promptSavePrefab());

        spinnerAdapter = new WidgetSpinnerAdapter(context);
        spnWidget.setAdapter(spinnerAdapter);
        spnWidget.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (suppressSpinnerCallback || position < 0 || position >= spinnerEntries.size()) {
                    return;
                }
                GameObjectBean target = spinnerEntries.get(position).object;
                if (isSameObject(target, selected)) {
                    spinnerAdapter.setSelectedPosition(position);
                    return;
                }
                spinnerAdapter.setSelectedPosition(position);
                selected = target;
                updateActionButtonsVisibility();
                if (stageView != null) {
                    stageView.selectObject(target);
                } else {
                    rebuildCardsOnly();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        initializeGroupTabs(context);
        updateTabStyles();
        rebuildContent();
    }

    private void initializeGroupTabs(Context context) {
        layoutPropertyGroup.removeAllViews();
        groupTabViews.clear();
        addGroupTab(context, TAB_BASIC, "Basic");
        addGroupTab(context, TAB_PHYSICS, "Physics");
        addGroupTab(context, TAB_EVENT, "Event");
    }

    private void addGroupTab(Context context, int tabIndex, String label) {
        View tabView = LayoutInflater.from(context).inflate(R.layout.property_group_item, layoutPropertyGroup, false);
        TextView tvTitle = tabView.findViewById(R.id.tv_title);
        tvTitle.setText(label);
        tabView.setTag(tabIndex);
        tabView.setOnClickListener(v -> {
            if (activeTab != tabIndex) {
                activeTab = tabIndex;
                updateTabStyles();
                rebuildCardsOnly();
            }
        });
        layoutPropertyGroup.addView(tabView);
        groupTabViews.add(tabView);
    }

    private void updateTabStyles() {
        for (View item : groupTabViews) {
            int idx = item.getTag() instanceof Integer ? (Integer) item.getTag() : 0;
            boolean active = idx == activeTab;
            item.setSelected(active);
            item.animate()
                    .scaleX(active ? 0.9f : 0.8f)
                    .scaleY(active ? 0.9f : 0.8f)
                    .setDuration(120L)
                    .start();
        }
    }

    public void bind(String scId, GameProject project, GameScene scene, GameStageEditorView stageView, PropertyActionListener listener) {
        this.scId = scId;
        this.project = project;
        this.scene = scene;
        this.stageView = stageView;
        this.listener = listener;
        rebuildContent();
    }

    public void updateSelection(@Nullable GameObjectBean selected) {
        this.selected = selected;
        rebuildContent();
    }

    private void updateActionButtonsVisibility() {
        int vis = selected != null ? VISIBLE : GONE;
        imgDelete.setVisibility(vis);
        imgSave.setVisibility(vis);
    }

    private void syncSpinnerEntries() {
        spinnerEntries.clear();
        if (scene != null) {
            String sceneName = nonEmpty(scene.name, "Scene");
            spinnerEntries.add(new SpinnerEntry(null, "Scene: " + sceneName));
            if (scene.objects != null) {
                for (GameObjectBean obj : scene.objects) {
                    if (obj == null) continue;
                    String objName = nonEmpty(obj.name, obj.id);
                    spinnerEntries.add(new SpinnerEntry(obj, objName + " (" + formatTypeName(obj.type) + ")"));
                }
            }
        } else {
            spinnerEntries.add(new SpinnerEntry(null, "No scene"));
        }

        int targetPos = 0;
        if (selected != null) {
            for (int i = 1; i < spinnerEntries.size(); i++) {
                if (isSameObject(spinnerEntries.get(i).object, selected)) {
                    targetPos = i;
                    break;
                }
            }
        }

        suppressSpinnerCallback = true;
        spinnerAdapter.setSelectedPosition(targetPos);
        spinnerAdapter.notifyDataSetChanged();
        if (spnWidget.getSelectedItemPosition() != targetPos) {
            spnWidget.setSelection(targetPos, false);
        }
        spnWidget.post(() -> suppressSpinnerCallback = false);
    }

    private void rebuildContent() {
        updateActionButtonsVisibility();
        syncSpinnerEntries();
        rebuildCardsOnly();
    }

    private void rebuildCardsOnly() {
        cardsStrip.removeAllViews();

        if (scene == null) {
            return;
        }

        if (selected == null) {
            switch (activeTab) {
                case TAB_BASIC -> buildSceneBasicCards();
                case TAB_PHYSICS -> buildScenePhysicsCards();
                case TAB_EVENT -> buildSceneEventCards();
            }
        } else {
            switch (activeTab) {
                case TAB_BASIC -> buildObjectBasicCards();
                case TAB_PHYSICS -> buildObjectPhysicsCards();
                case TAB_EVENT -> buildObjectEventCards();
            }
        }
    }

    private void buildSceneBasicCards() {
        addPropertyCard(R.drawable.ic_mtrl_edit, "Scene Name", nonEmpty(scene.name, "Scene"), 0,
                v -> showTextInputDialog("Scene Name", nonEmpty(scene.name, ""), "e.g., Level 1", value -> {
                    if (!value.trim().isEmpty()) {
                        scene.name = value.trim();
                        notifySceneChanged(false);
                    }
                }));

        int bgColor = scene.backgroundColor != 0 ? scene.backgroundColor : 0xFF1A2232;
        addPropertyCard(R.drawable.ic_mtrl_palette, "Bg Color", String.format(Locale.US, "#%06X", bgColor & 0xFFFFFF), bgColor,
                v -> showColorPickerDialog("Scene Background Color", bgColor, false, chosen -> {
                    scene.backgroundColor = chosen;
                    notifySceneChanged(false);
                }));

        addPropertyCard(R.drawable.ic_mtrl_image, "Bg Image", nonEmpty(scene.backgroundSprite, "None"), 0,
                v -> showImagePickerDialog("Scene Background Image", scene.backgroundSprite, chosen -> {
                    scene.backgroundSprite = chosen;
                    notifySceneChanged(true);
                }));

        addPropertyCard(R.drawable.ic_mtrl_music, "BGM Sound", nonEmpty(scene.bgmSound, "None"), 0,
                v -> showSoundPickerDialog("Background Music (BGM)", scene.bgmSound, chosen -> {
                    scene.bgmSound = chosen;
                    notifySceneChanged(false);
                }));

        int worldW = Math.round(scene.getEffectiveWorldWidth(project));
        int worldH = Math.round(scene.getEffectiveWorldHeight(project));
        addPropertyCard(R.drawable.ic_mtrl_devices, "World Size", worldW + "x" + worldH, 0,
                v -> showSceneWorldSizeDialog());

        int darkPct = Math.round(Math.max(0f, Math.min(1f, scene.ambientDarkness)) * 100f);
        addPropertyCard(R.drawable.ic_mtrl_bulb, "Ambient Dark", darkPct + "%", 0,
                v -> showSceneAmbientDarknessDialog());
    }

    private void buildScenePhysicsCards() {
        addPropertyCard(R.drawable.ic_mtrl_tune, "Gravity Y", formatFloat(scene.gravityY), 0,
                v -> showSceneGravityDialog());

        addPropertyCard(R.drawable.ic_mtrl_sensor, "Fall / Bounds", formatBoundsMode(scene.worldBoundsMode), 0,
                v -> showSceneBoundsDialog());

        if (project != null) {
            addToggleCard(R.drawable.ic_mtrl_grid, "Hitboxes", project.showDebugHitboxes ? "Visible" : "Hidden", project.showDebugHitboxes,
                    v -> {
                        project.showDebugHitboxes = !project.showDebugHitboxes;
                        notifySceneChanged(false);
                    });
        }
    }

    private void buildSceneEventCards() {
        addEventCard(GameScriptNode.SCENE_TARGET_ID, GameScriptNode.EVENT_SCENE_START, "",
                R.drawable.ic_mtrl_code, "onCreate",
                v -> openBlocks(null, GameScriptNode.EVENT_SCENE_START, ""));

        addEventCard(GameScriptNode.SCENE_TARGET_ID, GameScriptNode.EVENT_UPDATE, "",
                R.drawable.ic_mtrl_run, "onUpdate",
                v -> openBlocks(null, GameScriptNode.EVENT_UPDATE, ""));

        addEventCard(GameScriptNode.SCENE_TARGET_ID, GameScriptNode.EVENT_MESSAGE, null,
                R.drawable.ic_mtrl_chat, "Message", "New key",
                v -> promptMessageEvent(null));

        for (ScriptInfo info : findParameterizedScripts(GameScriptNode.SCENE_TARGET_ID, GameScriptNode.EVENT_MESSAGE)) {
            if (!info.param.isEmpty()) {
                addEventCard(GameScriptNode.SCENE_TARGET_ID, GameScriptNode.EVENT_MESSAGE, info.param,
                        R.drawable.ic_mtrl_chat, "Msg: " + info.param,
                        v -> openBlocks(null, GameScriptNode.EVENT_MESSAGE, info.param));
            }
        }
    }

    private void buildObjectBasicCards() {
        addPropertyCard(R.drawable.ic_mtrl_edit, "Name", nonEmpty(selected.name, selected.id), 0,
                v -> showTextInputDialog("Object Name", nonEmpty(selected.name, ""), "e.g., player", value -> {
                    String trimmed = value.trim();
                    if (!trimmed.isEmpty()) {
                        pushUndo();
                        selected.name = trimmed;
                        notifySceneChanged(false);
                    }
                }));

        addPropertyCard(R.drawable.ic_mtrl_label, "Tag", nonEmpty(selected.tag, "No tag"), 0,
                v -> showTagDialog());

        String posSummary = String.format(Locale.US, "%d,%d • %dx%d",
                Math.round(selected.x), Math.round(selected.y),
                Math.round(selected.width), Math.round(selected.height));
        addPropertyCard(R.drawable.ic_mtrl_devices, "Pos & Size", posSummary, 0,
                v -> showBoundsDialog());

        addPropertyCard(R.drawable.ic_mtrl_sort, "Z-Index", String.valueOf(selected.zIndex), 0,
                v -> showZIndexDialog());

        int objColor = selected.color;
        addPropertyCard(R.drawable.ic_mtrl_palette, "Color",
                objColor == 0 ? "Default" : String.format(Locale.US, "#%06X", objColor & 0xFFFFFF),
                objColor != 0 ? objColor : 0xFF3B82F6,
                v -> showColorPickerDialog("Object Color", selected.color, true, chosen -> {
                    pushUndo();
                    selected.color = chosen;
                    notifySceneChanged(false);
                }));

        addPropertyCard(R.drawable.ic_mtrl_image, "Sprite", nonEmpty(selected.spriteResName, "None"), 0,
                v -> showImagePickerDialog("Object Sprite", selected.spriteResName, chosen -> {
                    pushUndo();
                    selected.spriteResName = chosen;
                    notifySceneChanged(true);
                }));

        addPropertyCard(R.drawable.ic_mtrl_music, "Sound", nonEmpty(selected.soundResName, "None"), 0,
                v -> showSoundPickerDialog("Object Sound", selected.soundResName, chosen -> {
                    pushUndo();
                    selected.soundResName = chosen;
                    notifySceneChanged(false);
                }));

        addPropertyCard(R.drawable.ic_mtrl_font, "Font", nonEmpty(selected.fontResName, "Default"), 0,
                v -> showFontPickerDialog("Object Font", selected.fontResName, chosen -> {
                    pushUndo();
                    selected.fontResName = chosen;
                    notifySceneChanged(false);
                }));

        buildTypeSpecificCards();

        String textPreview = nonEmpty(selected.text, nonEmpty(selected.actionKey, "Empty"));
        addPropertyCard(R.drawable.ic_mtrl_formattext, "Text / Action", textPreview, 0,
                v -> showTextAndActionDialog());

        addToggleCard(R.drawable.ic_mtrl_visibility, "Visible", selected.visible ? "Visible" : "Hidden", selected.visible,
                v -> {
                    pushUndo();
                    selected.visible = !selected.visible;
                    notifySceneChanged(false);
                });

        addToggleCard(R.drawable.ic_mtrl_shield_lock, "Locked", selected.locked ? "Locked" : "Unlocked", selected.locked,
                v -> {
                    pushUndo();
                    selected.locked = !selected.locked;
                    notifySceneChanged(false);
                });

        addActionCard(R.drawable.ic_mtrl_content_copy, "Duplicate", false, v -> duplicateSelectedObject());
    }

    private void buildObjectPhysicsCards() {
        String physicsSummary = selected.physicsEnabled
                ? (selected.isSensor ? "Sensor" : (selected.oneWay ? "One-Way" : (selected.isStatic ? "Static" : (selected.circleCollider ? "Circle" : "Dynamic"))))
                : "Disabled";
        addPropertyCard(R.drawable.ic_mtrl_tune, "Physics", physicsSummary, 0,
                v -> showPhysicsDialog());

        addToggleCard(R.drawable.ic_mtrl_view_horizontal, "Static", selected.isStatic ? "Yes" : "No", selected.isStatic,
                v -> {
                    pushUndo();
                    selected.physicsEnabled = true;
                    selected.isStatic = !selected.isStatic;
                    notifySceneChanged(false);
                });

        addToggleCard(R.drawable.ic_mtrl_sensor, "Sensor", selected.isSensor ? "Active" : "No", selected.isSensor,
                v -> {
                    pushUndo();
                    selected.physicsEnabled = true;
                    selected.isSensor = !selected.isSensor;
                    notifySceneChanged(false);
                });

        addPropertyCard(R.drawable.ic_mtrl_rotate, "Rotation", String.format(Locale.US, "%.0f°", selected.rotation), 0,
                v -> showRotationAndScaleDialog());

        String gravityLabel = "g=" + formatFloat(selected.gravityScale) + " • q=" + formatFloat(selected.bounce);
        addPropertyCard(R.drawable.ic_mtrl_box, "Body / Friction", gravityLabel, 0,
                v -> showPhysicsDialog());
    }

    private void buildObjectEventCards() {
        String targetId = selected.id != null ? selected.id : "";

        addEventCard(targetId, GameScriptNode.EVENT_SCENE_START, "",
                R.drawable.ic_mtrl_code, "On Start",
                v -> openBlocks(selected, GameScriptNode.EVENT_SCENE_START, ""));

        addEventCard(targetId, GameScriptNode.EVENT_UPDATE, "",
                R.drawable.ic_mtrl_run, "Update",
                v -> openBlocks(selected, GameScriptNode.EVENT_UPDATE, ""));

        addEventCard(targetId, GameScriptNode.EVENT_TOUCH, "",
                R.drawable.ic_mtrl_touch, "On Touch",
                v -> openBlocks(selected, GameScriptNode.EVENT_TOUCH, ""));

        addEventCard(targetId, GameScriptNode.EVENT_COLLISION, null,
                R.drawable.ic_mtrl_sensor, "Collision", "Target tag",
                v -> promptCollisionTag(selected));

        for (ScriptInfo info : findParameterizedScripts(targetId, GameScriptNode.EVENT_COLLISION)) {
            String label = info.param.isEmpty() ? "Collision: Any" : "Collide: " + info.param;
            addEventCard(targetId, GameScriptNode.EVENT_COLLISION, info.param,
                    R.drawable.ic_mtrl_sensor, label,
                    v -> openBlocks(selected, GameScriptNode.EVENT_COLLISION, info.param));
        }

        if (GameObjectBean.TYPE_HUD_BUTTON.equals(selected.type)) {
            addEventCard(targetId, GameScriptNode.EVENT_BUTTON, "",
                    R.drawable.ic_mtrl_button_click, "On Click",
                    v -> openBlocks(selected, GameScriptNode.EVENT_BUTTON, ""));
        }
        for (ScriptInfo info : findParameterizedScripts(targetId, GameScriptNode.EVENT_BUTTON)) {
            if (!info.param.isEmpty()) {
                addEventCard(targetId, GameScriptNode.EVENT_BUTTON, info.param,
                        R.drawable.ic_mtrl_button_click, "Button: " + info.param,
                        v -> openBlocks(selected, GameScriptNode.EVENT_BUTTON, info.param));
            }
        }

        addEventCard(targetId, GameScriptNode.EVENT_MESSAGE, null,
                R.drawable.ic_mtrl_chat, "Message", "By key",
                v -> promptMessageEvent(selected));

        for (ScriptInfo info : findParameterizedScripts(targetId, GameScriptNode.EVENT_MESSAGE)) {
            if (!info.param.isEmpty()) {
                addEventCard(targetId, GameScriptNode.EVENT_MESSAGE, info.param,
                        R.drawable.ic_mtrl_chat, "Msg: " + info.param,
                        v -> openBlocks(selected, GameScriptNode.EVENT_MESSAGE, info.param));
            }
        }
    }

    private MaterialCardView inflateGridCard(Context context, OnClickListener click) {
        View view = LayoutInflater.from(context).inflate(R.layout.property_grid_item, cardsStrip, false);
        MaterialCardView card = view instanceof MaterialCardView ? (MaterialCardView) view : view.findViewById(R.id.property_menu_item);
        card.setCardBackgroundColor(colorSurfaceContainerHighest);
        card.setStrokeWidth(0);
        card.setOnClickListener(click);
        cardsStrip.addView(view);
        return card;
    }

    private void addPropertyCard(@DrawableRes int iconRes, String title, String value, int swatchColor, OnClickListener click) {
        Context context = getContext();
        MaterialCardView card = inflateGridCard(context, click);
        ImageView icon = card.findViewById(R.id.img_icon);
        TextView tvTitle = card.findViewById(R.id.tv_title);
        TextView tvSub = card.findViewById(R.id.tv_sub_title);

        icon.setImageResource(iconRes);
        int iconTint = swatchColor != 0 ? swatchColor : colorOnSurfaceVariant;
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(iconTint));
        if (icon.getLayoutParams() instanceof MarginLayoutParams mlp) {
            mlp.bottomMargin = dp(4);
            icon.setLayoutParams(mlp);
        }

        tvTitle.setText(title);
        tvTitle.setTextSize(11f);

        tvSub.setVisibility(VISIBLE);
        tvSub.setText(value);
        tvSub.setTextSize(10f);
    }

    private void addToggleCard(@DrawableRes int iconRes, String title, String value, boolean active, OnClickListener click) {
        Context context = getContext();
        MaterialCardView card = inflateGridCard(context, click);
        ImageView icon = card.findViewById(R.id.img_icon);
        TextView tvTitle = card.findViewById(R.id.tv_title);
        TextView tvSub = card.findViewById(R.id.tv_sub_title);

        if (active) {
            card.setStrokeColor(colorPrimary);
            card.setStrokeWidth(dp(1));
        }

        icon.setImageResource(iconRes);
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(active ? colorPrimary : colorOnSurfaceVariant));
        if (icon.getLayoutParams() instanceof MarginLayoutParams mlp) {
            mlp.bottomMargin = dp(4);
            icon.setLayoutParams(mlp);
        }

        tvTitle.setText(title);
        tvTitle.setTextSize(11f);
        tvTitle.setTextColor(active ? colorPrimary : colorOnSurface);

        tvSub.setVisibility(VISIBLE);
        tvSub.setText(value);
        tvSub.setTextSize(10f);
        tvSub.setTextColor(active ? colorPrimary : colorOnSurfaceVariant);
    }

    private void addActionCard(@DrawableRes int iconRes, String title, boolean danger, OnClickListener click) {
        Context context = getContext();
        MaterialCardView card = inflateGridCard(context, click);
        ImageView icon = card.findViewById(R.id.img_icon);
        TextView tvTitle = card.findViewById(R.id.tv_title);
        TextView tvSub = card.findViewById(R.id.tv_sub_title);

        int fg = danger ? colorError : colorOnSurfaceVariant;
        icon.setImageResource(iconRes);
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(fg));
        if (icon.getLayoutParams() instanceof MarginLayoutParams mlp) {
            mlp.bottomMargin = dp(4);
            icon.setLayoutParams(mlp);
        }

        tvTitle.setText(title);
        tvTitle.setTextSize(11f);
        tvTitle.setTextColor(danger ? colorError : colorOnSurface);

        tvSub.setVisibility(VISIBLE);
        tvSub.setText("Action");
        tvSub.setTextSize(10f);
    }

    private void addEventCard(String targetId, String eventType, @Nullable String eventParam,
                              @DrawableRes int iconRes, String title, OnClickListener click) {
        int blockCount = countScriptBlocks(targetId, eventType, eventParam);
        String sub = blockCount == 1 ? "1 block" : blockCount + " blocks";
        addEventCard(targetId, eventType, eventParam, iconRes, title, sub, click);
    }

    private void addEventCard(String targetId, String eventType, @Nullable String eventParam,
                              @DrawableRes int iconRes, String title, String fallbackSub, OnClickListener click) {
        Context context = getContext();
        int blockCount = countScriptBlocks(targetId, eventType, eventParam);
        boolean hasBlocks = blockCount > 0;

        MaterialCardView card = inflateGridCard(context, click);
        ImageView icon = card.findViewById(R.id.img_icon);
        TextView tvTitle = card.findViewById(R.id.tv_title);
        TextView tvSub = card.findViewById(R.id.tv_sub_title);

        if (hasBlocks) {
            card.setStrokeColor(colorPrimary);
            card.setStrokeWidth(dp(1));
        }

        icon.setImageResource(iconRes);
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(hasBlocks ? colorPrimary : colorOnSurfaceVariant));
        if (icon.getLayoutParams() instanceof MarginLayoutParams mlp) {
            mlp.bottomMargin = dp(4);
            icon.setLayoutParams(mlp);
        }

        tvTitle.setText(title);
        tvTitle.setTextSize(11f);
        tvTitle.setTextColor(hasBlocks ? colorPrimary : colorOnSurface);

        tvSub.setVisibility(VISIBLE);
        tvSub.setText(hasBlocks ? (blockCount == 1 ? "1 block" : blockCount + " blocks") : fallbackSub);
        tvSub.setTextSize(10f);
        tvSub.setTextColor(hasBlocks ? colorPrimary : colorOnSurfaceVariant);
    }

    private static boolean isSameObject(@Nullable GameObjectBean a, @Nullable GameObjectBean b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return Objects.equals(a.id, b.id);
    }

    private class WidgetSpinnerAdapter extends BaseAdapter {
        private final LayoutInflater inflater;
        private int selectedPosition = 0;

        WidgetSpinnerAdapter(Context context) {
            this.inflater = LayoutInflater.from(context);
        }

        void setSelectedPosition(int selectedPosition) {
            this.selectedPosition = selectedPosition;
        }

        @Override
        public int getCount() {
            return spinnerEntries.size();
        }

        @Override
        public SpinnerEntry getItem(int position) {
            return spinnerEntries.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView tv;
            if (convertView instanceof TextView) {
                tv = (TextView) convertView;
            } else {
                tv = (TextView) inflater.inflate(android.R.layout.simple_spinner_item, parent, false);
                tv.setSingleLine(true);
                tv.setEllipsize(TextUtils.TruncateAt.END);
                tv.setTextSize(12.5f);
                tv.setTextColor(colorOnSurface);
                tv.setPadding(dp(4), dp(2), dp(4), dp(2));
            }
            tv.setText(getItem(position).label);
            return tv;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            TextView tv;
            if (convertView instanceof TextView) {
                tv = (TextView) convertView;
            } else {
                tv = (TextView) inflater.inflate(android.R.layout.simple_spinner_dropdown_item, parent, false);
                tv.setSingleLine(true);
                tv.setEllipsize(TextUtils.TruncateAt.END);
                tv.setTextSize(13f);
                tv.setPadding(dp(16), dp(12), dp(16), dp(12));
                tv.setBackgroundColor(colorSurfaceContainerHigh);
            }
            tv.setText(getItem(position).label);
            tv.setTextColor(position == selectedPosition ? colorPrimary : colorOnSurface);
            tv.setTypeface(null, position == selectedPosition ? Typeface.BOLD : Typeface.NORMAL);
            return tv;
        }
    }

    // --- Inspector Dialogs ---

    private interface StringConsumer {
        void accept(String value);
    }

    private interface IntConsumer {
        void accept(int value);
    }

    private void showTextInputDialog(String title, String initial, String hint, StringConsumer onConfirm) {
        Context context = getContext();
        LinearLayout box = createDialogContainer(context);
        EditText et = createStyledEditText(context, initial, hint, InputType.TYPE_CLASS_TEXT);
        box.addView(et);

        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(box)
                .setPositiveButton("Apply", (d, w) -> onConfirm.accept(et.getText().toString()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showTagDialog() {
        Context context = getContext();
        LinearLayout box = createDialogContainer(context);
        EditText et = createStyledEditText(context, nonEmpty(selected.tag, ""), "e.g., player, enemy, ground", InputType.TYPE_CLASS_TEXT);
        box.addView(et);

        TextView quickLabel = new TextView(context);
        quickLabel.setText("Quick suggestions:");
        quickLabel.setTextSize(12f);
        quickLabel.setTextColor(colorOnSurfaceVariant);
        quickLabel.setPadding(0, dp(10), 0, dp(6));
        box.addView(quickLabel);

        HorizontalScrollView hsv = new HorizontalScrollView(context);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setClipToPadding(false);
        hsv.setPadding(0, dp(2), 0, dp(2));
        LinearLayout chips = new LinearLayout(context);
        chips.setOrientation(HORIZONTAL);
        String[] suggestions = new String[]{"player", "enemy", "ground", "wall", "coin", "goal", "bullet"};
        for (String tag : suggestions) {
            MaterialButton chip = createCompactOutlinedButton(context, tag, 11.5f);
            chip.setOnClickListener(v -> et.setText(tag));
            LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(6));
            chips.addView(chip, lp);
        }
        hsv.addView(chips);
        box.addView(hsv);

        new MaterialAlertDialogBuilder(context)
                .setTitle("Collision Tag / Group")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    pushUndo();
                    selected.tag = et.getText().toString().trim();
                    notifySceneChanged(false);
                })
                .setNeutralButton("Clear", (d, w) -> {
                    pushUndo();
                    selected.tag = "";
                    notifySceneChanged(false);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showBoundsDialog() {
        Context context = getContext();
        LinearLayout box = createDialogContainer(context);

        EditText etX = createStyledEditText(context, formatFloat(selected.x), "Position X", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        EditText etY = createStyledEditText(context, formatFloat(selected.y), "Position Y", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        EditText etW = createStyledEditText(context, formatFloat(selected.width), "Width", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText etH = createStyledEditText(context, formatFloat(selected.height), "Height", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        box.addView(labeledField(context, "Position X", etX));
        box.addView(labeledField(context, "Position Y", etY));
        box.addView(labeledField(context, "Width", etW));
        box.addView(labeledField(context, "Height", etH));

        new MaterialAlertDialogBuilder(context)
                .setTitle("Position & Size")
                .setView(box)
                .setPositiveButton("Apply", (d, w) -> {
                    pushUndo();
                    selected.x = parseFloatOr(etX.getText().toString(), selected.x);
                    selected.y = parseFloatOr(etY.getText().toString(), selected.y);
                    selected.width = Math.max(16f, parseFloatOr(etW.getText().toString(), selected.width));
                    selected.height = Math.max(16f, parseFloatOr(etH.getText().toString(), selected.height));
                    notifySceneChanged(false);
                })
                .setNeutralButton("Center", (d, w) -> {
                    pushUndo();
                    int vw = project != null && project.virtualWidth > 0 ? project.virtualWidth : 1280;
                    int vh = project != null && project.virtualHeight > 0 ? project.virtualHeight : 720;
                    selected.x = (vw - selected.width) * 0.5f;
                    selected.y = (vh - selected.height) * 0.5f;
                    notifySceneChanged(false);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showRotationAndScaleDialog() {
        Context context = getContext();
        LinearLayout box = createDialogContainer(context);

        TextView tvAngle = new TextView(context);
        tvAngle.setText(String.format(Locale.US, "Rotation: %.0f°", selected.rotation));
        tvAngle.setTextColor(colorOnSurface);
        tvAngle.setTypeface(null, Typeface.BOLD);
        box.addView(tvAngle);

        SeekBar seekBar = new SeekBar(context);
        seekBar.setMax(360);
        int initAngle = Math.round(((selected.rotation % 360f) + 360f) % 360f);
        seekBar.setProgress(initAngle);
        box.addView(seekBar);

        EditText etRot = createStyledEditText(context, formatFloat(selected.rotation), "Angle (degrees)",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        EditText etScaleX = createStyledEditText(context, formatFloat(selected.scaleX == 0f ? 1f : selected.scaleX), "Scale X",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        EditText etScaleY = createStyledEditText(context, formatFloat(selected.scaleY == 0f ? 1f : selected.scaleY), "Scale Y",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        EditText etAlpha = createStyledEditText(context, formatFloat(selected.alpha <= 0f ? 1f : selected.alpha), "Opacity (0.1 - 1.0)",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                tvAngle.setText("Rotation: " + progress + "°");
                if (fromUser) {
                    etRot.setText(String.valueOf(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
            }
        });

        box.addView(labeledField(context, "Angle (°)", etRot));
        box.addView(labeledField(context, "Scale X", etScaleX));
        box.addView(labeledField(context, "Scale Y", etScaleY));
        box.addView(labeledField(context, "Opacity (0..1)", etAlpha));

        new MaterialAlertDialogBuilder(context)
                .setTitle("Rotation, Scale & Opacity")
                .setView(box)
                .setPositiveButton("Apply", (d, w) -> {
                    pushUndo();
                    selected.rotation = parseFloatOr(etRot.getText().toString(), selected.rotation);
                    selected.scaleX = parseFloatOr(etScaleX.getText().toString(), 1f);
                    selected.scaleY = parseFloatOr(etScaleY.getText().toString(), 1f);
                    selected.alpha = Math.max(0.05f, Math.min(1f, parseFloatOr(etAlpha.getText().toString(), 1f)));
                    notifySceneChanged(false);
                })
                .setNeutralButton("Reset (0°)", (d, w) -> {
                    pushUndo();
                    selected.rotation = 0f;
                    selected.scaleX = 1f;
                    selected.scaleY = 1f;
                    selected.alpha = 1f;
                    notifySceneChanged(false);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showZIndexDialog() {
        Context context = getContext();
        LinearLayout box = createDialogContainer(context);
        EditText etZ = createStyledEditText(context, String.valueOf(selected.zIndex), "Z-Index Layer",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        box.addView(etZ);

        new MaterialAlertDialogBuilder(context)
                .setTitle("Layer Order (Z-Index)")
                .setView(box)
                .setPositiveButton("Apply", (d, w) -> {
                    pushUndo();
                    try {
                        selected.zIndex = Integer.parseInt(etZ.getText().toString().trim());
                    } catch (NumberFormatException ignored) {
                    }
                    notifySceneChanged(false);
                })
                .setNeutralButton("Bring to Front", (d, w) -> {
                    pushUndo();
                    int maxZ = selected.zIndex;
                    if (scene.objects != null) {
                        for (GameObjectBean o : scene.objects) {
                            if (o.zIndex > maxZ) {
                                maxZ = o.zIndex;
                            }
                        }
                    }
                    selected.zIndex = maxZ + 1;
                    notifySceneChanged(false);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showTextAndActionDialog() {
        Context context = getContext();
        LinearLayout box = createDialogContainer(context);
        EditText etText = createStyledEditText(context, nonEmpty(selected.text, ""), "Visible text (HUD/Button)", InputType.TYPE_CLASS_TEXT);
        EditText etAction = createStyledEditText(context, nonEmpty(selected.actionKey, ""), "ActionKey (e.g., jump, fire)", InputType.TYPE_CLASS_TEXT);
        box.addView(labeledField(context, "Text", etText));
        box.addView(labeledField(context, "Action Key", etAction));

        new MaterialAlertDialogBuilder(context)
                .setTitle("Text & Action")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    pushUndo();
                    selected.text = etText.getText().toString();
                    selected.actionKey = etAction.getText().toString().trim();
                    notifySceneChanged(false);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showPhysicsDialog() {
        Context context = getContext();
        ScrollView sv = new ScrollView(context);
        LinearLayout box = createDialogContainer(context);
        sv.addView(box);

        MaterialSwitch swEnabled = new MaterialSwitch(context);
        swEnabled.setText("Physics Enabled");
        swEnabled.setChecked(selected.physicsEnabled);
        box.addView(swEnabled);

        MaterialSwitch swStatic = new MaterialSwitch(context);
        swStatic.setText("Static Body (does not fall)");
        swStatic.setChecked(selected.isStatic);
        box.addView(swStatic);

        MaterialSwitch swSensor = new MaterialSwitch(context);
        swSensor.setText("Sensor (detects collision only)");
        swSensor.setChecked(selected.isSensor);
        box.addView(swSensor);

        MaterialSwitch swCircle = new MaterialSwitch(context);
        swCircle.setText("Circle Collider");
        swCircle.setChecked(selected.circleCollider);
        box.addView(swCircle);

        MaterialSwitch swOneWay = new MaterialSwitch(context);
        swOneWay.setText("One-Way Platform (jump through bottom)");
        swOneWay.setChecked(selected.oneWay);
        box.addView(swOneWay);

        MaterialSwitch swDestroyOff = new MaterialSwitch(context);
        swDestroyOff.setText("Destroy Offscreen");
        swDestroyOff.setChecked(selected.destroyOffscreen);
        box.addView(swDestroyOff);

        MaterialSwitch swNearCam = new MaterialSwitch(context);
        swNearCam.setText("Active Only Near Camera");
        swNearCam.setChecked(selected.activeOnlyNearCamera);
        box.addView(swNearCam);

        EditText etGrav = createStyledEditText(context, formatFloat(selected.gravityScale), "1.0",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        EditText etBounce = createStyledEditText(context, formatFloat(selected.bounce), "0.0 - 1.0",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText etFriction = createStyledEditText(context, formatFloat(selected.friction), "0.0 - 1.0",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        box.addView(labeledField(context, "Gravity Scale", etGrav));
        box.addView(labeledField(context, "Bounce", etBounce));
        box.addView(labeledField(context, "Friction", etFriction));

        new MaterialAlertDialogBuilder(context)
                .setTitle("Object 2D Physics")
                .setView(sv)
                .setPositiveButton("Apply", (d, w) -> {
                    pushUndo();
                    selected.physicsEnabled = swEnabled.isChecked();
                    selected.isStatic = swStatic.isChecked();
                    selected.isSensor = swSensor.isChecked();
                    selected.circleCollider = swCircle.isChecked();
                    selected.oneWay = swOneWay.isChecked();
                    selected.destroyOffscreen = swDestroyOff.isChecked();
                    selected.activeOnlyNearCamera = swNearCam.isChecked();
                    selected.gravityScale = parseFloatOr(etGrav.getText().toString(), selected.gravityScale);
                    selected.bounce = Math.max(0f, parseFloatOr(etBounce.getText().toString(), selected.bounce));
                    selected.friction = Math.max(0f, parseFloatOr(etFriction.getText().toString(), selected.friction));
                    notifySceneChanged(false);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void buildTypeSpecificCards() {
        if (selected == null || selected.type == null) {
            return;
        }
        switch (selected.type) {
            case GameObjectBean.TYPE_GOAL -> {
                String targetLabel = resolveSceneTargetLabel(selected.targetScene);
                addPropertyCard(R.drawable.ic_mtrl_next, "Target Scene", targetLabel, 0,
                        v -> showGoalTargetDialog());
            }
            case GameObjectBean.TYPE_SPAWNER -> {
                String spLabel = nonEmpty(selected.prefabName, "Choose prefab") + " (" + formatFloat(selected.cooldownSec) + "s)";
                addPropertyCard(R.drawable.ic_mtrl_add_circle, "Spawner", spLabel, 0,
                        v -> showSpawnerConfigDialog());
            }
            case GameObjectBean.TYPE_PARALLAX_BG -> {
                String pLabel = formatFloat(selected.parallaxX) + "x, scroll " + formatFloat(selected.moveX);
                addPropertyCard(R.drawable.ic_mtrl_pattern, "Parallax / Scroll", pLabel, 0,
                        v -> showMotionConfigDialog(true));
            }
            case GameObjectBean.TYPE_MOVING_PLATFORM, GameObjectBean.TYPE_PROJECTILE, GameObjectBean.TYPE_BOUNCE_PAD -> {
                String mLabel = "spd=" + formatFloat(selected.speed) + " (" + formatFloat(selected.moveX) + "," + formatFloat(selected.moveY) + ")";
                addPropertyCard(R.drawable.ic_mtrl_swipe_horizontal, "Motion / Speed", mLabel, 0,
                        v -> showMotionConfigDialog(false));
            }
            case GameObjectBean.TYPE_ANIMATED_SPRITE -> {
                String fLabel = nonEmpty(selected.spriteFrames, "No frames") + " @ " + formatFloat(selected.animFps) + "fps";
                addPropertyCard(R.drawable.ic_mtrl_animation, "Anim Frames", fLabel, 0,
                        v -> showAnimFramesDialog());
            }
            case GameObjectBean.TYPE_HAZARD, GameObjectBean.TYPE_COLLECTIBLE, GameObjectBean.TYPE_DEATH_ZONE -> {
                String lbl = GameObjectBean.TYPE_COLLECTIBLE.equals(selected.type) ? "Points" : "Damage";
                addPropertyCard(R.drawable.ic_mtrl_star, lbl, formatFloat(selected.damage), 0,
                        v -> showDamageOrValueDialog(lbl));
            }
            case GameObjectBean.TYPE_HUD_TOGGLE -> {
                addToggleCard(R.drawable.ic_mtrl_switch, "State", selected.toggled ? "ON" : "OFF", selected.toggled,
                        v -> {
                            pushUndo();
                            selected.toggled = !selected.toggled;
                            notifySceneChanged(false);
                        });
            }
            default -> {
            }
        }
    }

    private String resolveSceneTargetLabel(@Nullable String targetScene) {
        if (targetScene == null || targetScene.trim().isEmpty() || "__next__".equals(targetScene)) {
            return "Next Scene";
        }
        if ("__prev__".equals(targetScene)) {
            return "Previous Scene";
        }
        if (project != null && project.scenes != null) {
            for (GameScene s : project.scenes) {
                if (s != null && (targetScene.equals(s.id) || targetScene.equalsIgnoreCase(s.name))) {
                    return nonEmpty(s.name, s.id);
                }
            }
        }
        return targetScene;
    }

    private static String formatBoundsMode(@Nullable String mode) {
        if (mode == null) return "Free";
        return switch (mode) {
            case GameScene.WORLD_BOUNDS_SOLID -> "Solid Edges";
            case GameScene.WORLD_BOUNDS_RESPAWN_BOTTOM -> "Respawn Fall";
            case GameScene.WORLD_BOUNDS_RESTART_BOTTOM -> "Restart Fall";
            default -> "Free";
        };
    }

    private void showSceneWorldSizeDialog() {
        if (scene == null) return;
        Context context = getContext();
        ScrollView sv = new ScrollView(context);
        LinearLayout box = createDialogContainer(context);
        sv.addView(box);

        EditText etW = createStyledEditText(context, formatFloat(scene.getEffectiveWorldWidth(project)), "1280",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText etH = createStyledEditText(context, formatFloat(scene.getEffectiveWorldHeight(project)), "720",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        box.addView(labeledField(context, "World Width (0 = Match Camera)", etW));
        box.addView(labeledField(context, "World Height (0 = Match Camera)", etH));

        LinearLayout presets = new LinearLayout(context);
        presets.setOrientation(HORIZONTAL);
        presets.setPadding(0, dp(8), 0, 0);
        String[] pLabels = new String[]{"Camera", "Wide 3840", "Tall 2560", "Big 2560x1920"};
        float[] pWidths = new float[]{0f, 3840f, 720f, 2560f};
        float[] pHeights = new float[]{0f, 720f, 2560f, 1920f};
        presets.setPadding(0, dp(8), 0, dp(2));
        for (int i = 0; i < pLabels.length; i++) {
            float pw = pWidths[i];
            float ph = pHeights[i];
            MaterialButton btn = createCompactOutlinedButton(context, pLabels[i], 11.5f);
            btn.setOnClickListener(v -> {
                etW.setText(formatFloat(pw));
                etH.setText(formatFloat(ph));
            });
            LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(6));
            presets.addView(btn, lp);
        }
        HorizontalScrollView hsv = new HorizontalScrollView(context);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setClipToPadding(false);
        hsv.addView(presets);
        box.addView(hsv);

        new MaterialAlertDialogBuilder(context)
                .setTitle("Scene World Size")
                .setView(sv)
                .setPositiveButton("Apply", (d, w) -> {
                    float wVal = parseFloatOr(etW.getText().toString(), 0f);
                    float hVal = parseFloatOr(etH.getText().toString(), 0f);
                    scene.worldWidth = wVal <= 0f ? 0 : Math.round(Math.max(240f, Math.min(20000f, wVal)));
                    scene.worldHeight = hVal <= 0f ? 0 : Math.round(Math.max(240f, Math.min(20000f, hVal)));
                    notifySceneChanged(false);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showSceneAmbientDarknessDialog() {
        if (scene == null) return;
        Context context = getContext();
        LinearLayout box = createDialogContainer(context);
        int currentPct = Math.round(Math.max(0f, Math.min(1f, scene.ambientDarkness)) * 100f);
        EditText etPct = createStyledEditText(context, String.valueOf(currentPct), "0 - 100",
                InputType.TYPE_CLASS_NUMBER);
        box.addView(labeledField(context, "Ambient Darkness (%)", etPct));

        HorizontalScrollView hsv = new HorizontalScrollView(context);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setClipToPadding(false);
        LinearLayout presets = new LinearLayout(context);
        presets.setOrientation(HORIZONTAL);
        presets.setPadding(0, dp(8), 0, dp(2));
        int[] pcts = new int[]{0, 45, 85};
        String[] labels = new String[]{"0% Day", "45% Dusk", "85% Night"};
        for (int i = 0; i < pcts.length; i++) {
            int val = pcts[i];
            MaterialButton btn = createCompactOutlinedButton(context, labels[i], 11.5f);
            btn.setOnClickListener(v -> etPct.setText(String.valueOf(val)));
            LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(6));
            presets.addView(btn, lp);
        }
        hsv.addView(presets);
        box.addView(hsv);

        new MaterialAlertDialogBuilder(context)
                .setTitle("Ambient Darkness (2D Lights)")
                .setView(box)
                .setPositiveButton("Apply", (d, w) -> {
                    float pct = Math.max(0f, Math.min(100f, parseFloatOr(etPct.getText().toString(), currentPct)));
                    scene.ambientDarkness = pct / 100f;
                    notifySceneChanged(false);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showSceneBoundsDialog() {
        if (scene == null) return;
        final String[] modes = new String[]{
                GameScene.WORLD_BOUNDS_FREE,
                GameScene.WORLD_BOUNDS_SOLID,
                GameScene.WORLD_BOUNDS_RESPAWN_BOTTOM,
                GameScene.WORLD_BOUNDS_RESTART_BOTTOM
        };
        final String[] labels = new String[]{
                "Free (No World Borders)",
                "Solid World Edges (Keep Inside)",
                "Respawn at Checkpoint on Fall",
                "Restart Scene on Bottom Fall"
        };
        int checked = 0;
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].equals(scene.worldBoundsMode)) {
                checked = i;
                break;
            }
        }
        final int[] chosen = new int[]{checked};
        new MaterialAlertDialogBuilder(getContext())
                .setTitle("World Bounds & Fall Behavior")
                .setSingleChoiceItems(labels, checked, (d, which) -> chosen[0] = which)
                .setPositiveButton("Apply", (d, w) -> {
                    scene.worldBoundsMode = modes[chosen[0]];
                    notifySceneChanged(false);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showGoalTargetDialog() {
        if (selected == null) return;
        List<String> keys = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        keys.add("__next__");
        labels.add("Next Scene (Auto Order)");
        keys.add("__prev__");
        labels.add("Previous Scene (Auto Order)");
        if (project != null && project.scenes != null) {
            for (GameScene s : project.scenes) {
                if (s != null) {
                    keys.add(s.id);
                    labels.add(nonEmpty(s.name, s.id) + " (" + s.id + ")");
                }
            }
        }
        int currentIdx = 0;
        for (int i = 0; i < keys.size(); i++) {
            if (keys.get(i).equals(selected.targetScene)) {
                currentIdx = i;
                break;
            }
        }
        final int[] chosen = new int[]{currentIdx};
        new MaterialAlertDialogBuilder(getContext())
                .setTitle("Goal / Portal Destination")
                .setSingleChoiceItems(labels.toArray(new String[0]), currentIdx, (d, which) -> chosen[0] = which)
                .setPositiveButton("Apply", (d, w) -> {
                    pushUndo();
                    selected.targetScene = keys.get(chosen[0]);
                    notifySceneChanged(false);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showSpawnerConfigDialog() {
        if (selected == null) return;
        Context context = getContext();
        ScrollView sv = new ScrollView(context);
        LinearLayout box = createDialogContainer(context);
        sv.addView(box);

        List<String> candidateValues = new ArrayList<>();
        List<String> candidateLabels = new ArrayList<>();
        candidateValues.add("");
        candidateLabels.add("(None)");
        if (project != null && project.prefabs != null) {
            for (GameObjectBean pf : project.prefabs) {
                if (pf != null && pf.name != null && !pf.name.trim().isEmpty()) {
                    candidateValues.add(pf.name);
                    candidateLabels.add("Prefab: " + pf.name);
                }
            }
        }
        if (scene != null && scene.objects != null) {
            for (GameObjectBean obj : scene.objects) {
                if (obj != null && !obj.id.equals(selected.id) && obj.name != null && !obj.name.trim().isEmpty()) {
                    if (!candidateValues.contains(obj.name)) {
                        candidateValues.add(obj.name);
                        candidateLabels.add("Scene: " + obj.name);
                    }
                }
            }
        }

        Spinner spPrefab = new Spinner(context);
        spPrefab.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, candidateLabels));
        int selIdx = Math.max(0, candidateValues.indexOf(nonEmpty(selected.prefabName, "")));
        spPrefab.setSelection(selIdx);
        box.addView(labeledField(context, "Prefab / Object to Spawn", spPrefab));

        EditText etInterval = createStyledEditText(context, formatFloat(selected.cooldownSec), "1.5",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText etMoveX = createStyledEditText(context, formatFloat(selected.moveX), "0",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        EditText etMoveY = createStyledEditText(context, formatFloat(selected.moveY), "180",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);

        box.addView(labeledField(context, "Spawn Interval (seconds, 0 = manual)", etInterval));
        box.addView(labeledField(context, "Initial Velocity X", etMoveX));
        box.addView(labeledField(context, "Initial Velocity Y", etMoveY));

        new MaterialAlertDialogBuilder(context)
                .setTitle("Spawner Emitter Config")
                .setView(sv)
                .setPositiveButton("Apply", (d, w) -> {
                    pushUndo();
                    int pos = spPrefab.getSelectedItemPosition();
                    selected.prefabName = (pos >= 0 && pos < candidateValues.size()) ? candidateValues.get(pos) : "";
                    selected.cooldownSec = Math.max(0f, parseFloatOr(etInterval.getText().toString(), selected.cooldownSec));
                    selected.moveX = parseFloatOr(etMoveX.getText().toString(), selected.moveX);
                    selected.moveY = parseFloatOr(etMoveY.getText().toString(), selected.moveY);
                    notifySceneChanged(false);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showMotionConfigDialog(boolean isParallax) {
        if (selected == null) return;
        Context context = getContext();
        ScrollView sv = new ScrollView(context);
        LinearLayout box = createDialogContainer(context);
        sv.addView(box);

        EditText etSpeed = createStyledEditText(context, formatFloat(selected.speed), "120",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        EditText etMoveX = createStyledEditText(context, formatFloat(selected.moveX), "0",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        EditText etMoveY = createStyledEditText(context, formatFloat(selected.moveY), "0",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        EditText etParX = createStyledEditText(context, formatFloat(selected.parallaxX), "0.4",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText etParY = createStyledEditText(context, formatFloat(selected.parallaxY), "0.4",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        if (isParallax) {
            box.addView(labeledField(context, "Parallax Factor X (0 = fixed, 1 = world)", etParX));
            box.addView(labeledField(context, "Parallax Factor Y (0 = fixed, 1 = world)", etParY));
            box.addView(labeledField(context, "Auto Scroll Speed X (px/sec)", etMoveX));
            box.addView(labeledField(context, "Auto Scroll Speed Y (px/sec)", etMoveY));
        } else {
            box.addView(labeledField(context, GameObjectBean.TYPE_BOUNCE_PAD.equals(selected.type) ? "Bounce Speed (%)" : "Speed", etSpeed));
            box.addView(labeledField(context, "Move / Velocity X", etMoveX));
            box.addView(labeledField(context, "Move / Velocity Y", etMoveY));
        }

        new MaterialAlertDialogBuilder(context)
                .setTitle(isParallax ? "Parallax & Auto-Scroll" : "Motion & Speed")
                .setView(sv)
                .setPositiveButton("Apply", (d, w) -> {
                    pushUndo();
                    selected.speed = parseFloatOr(etSpeed.getText().toString(), selected.speed);
                    selected.moveX = parseFloatOr(etMoveX.getText().toString(), selected.moveX);
                    selected.moveY = parseFloatOr(etMoveY.getText().toString(), selected.moveY);
                    if (isParallax) {
                        selected.parallaxX = clamp(parseFloatOr(etParX.getText().toString(), selected.parallaxX), 0f, 3f);
                        selected.parallaxY = clamp(parseFloatOr(etParY.getText().toString(), selected.parallaxY), 0f, 3f);
                    }
                    notifySceneChanged(false);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAnimFramesDialog() {
        if (selected == null) return;
        Context context = getContext();
        ScrollView sv = new ScrollView(context);
        LinearLayout box = createDialogContainer(context);
        sv.addView(box);

        EditText etFrames = createStyledEditText(context, nonEmpty(selected.spriteFrames, ""), "walk_1,walk_2,walk_3",
                InputType.TYPE_CLASS_TEXT);
        EditText etFps = createStyledEditText(context, formatFloat(selected.animFps), "8",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        box.addView(labeledField(context, "Sprite Frames (comma-separated image names)", etFrames));
        box.addView(labeledField(context, "Animation FPS", etFps));

        MaterialButton btnAddFrame = createCompactOutlinedButton(context, "Append Image Frame...", 12f);
        btnAddFrame.setOnClickListener(v -> showImagePickerDialog("Add Animation Frame", "", img -> {
            if (img != null && !img.isEmpty()) {
                String cur = etFrames.getText().toString().trim();
                etFrames.setText(cur.isEmpty() ? img : cur + "," + img);
            }
        }));
        LayoutParams addFrameLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        addFrameLp.topMargin = dp(4);
        box.addView(btnAddFrame, addFrameLp);

        new MaterialAlertDialogBuilder(context)
                .setTitle("Sprite Animation")
                .setView(sv)
                .setPositiveButton("Apply", (d, w) -> {
                    pushUndo();
                    selected.spriteFrames = etFrames.getText().toString().trim();
                    selected.animFps = Math.max(0.5f, Math.min(60f, parseFloatOr(etFps.getText().toString(), selected.animFps)));
                    notifySceneChanged(true);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDamageOrValueDialog(String label) {
        if (selected == null) return;
        Context context = getContext();
        LinearLayout box = createDialogContainer(context);
        EditText etVal = createStyledEditText(context, formatFloat(selected.damage), "1",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        box.addView(labeledField(context, label + " Value", etVal));
        new MaterialAlertDialogBuilder(context)
                .setTitle(label)
                .setView(box)
                .setPositiveButton("Apply", (d, w) -> {
                    pushUndo();
                    selected.damage = Math.max(0f, parseFloatOr(etVal.getText().toString(), selected.damage));
                    notifySceneChanged(false);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }

    private void showSceneGravityDialog() {
        Context context = getContext();
        LinearLayout box = createDialogContainer(context);
        EditText etGrav = createStyledEditText(context, formatFloat(scene.gravityY), "e.g., 980",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        box.addView(etGrav);

        HorizontalScrollView hsv = new HorizontalScrollView(context);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setClipToPadding(false);
        LinearLayout presets = new LinearLayout(context);
        presets.setOrientation(HORIZONTAL);
        presets.setPadding(0, dp(8), 0, dp(2));
        String[] labels = new String[]{"0 (Top-Down)", "600 (Moon)", "980 (Normal)"};
        float[] values = new float[]{0f, 600f, 980f};
        for (int i = 0; i < labels.length; i++) {
            float val = values[i];
            MaterialButton btn = createCompactOutlinedButton(context, labels[i], 11.5f);
            btn.setOnClickListener(v -> etGrav.setText(formatFloat(val)));
            LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            if (i < labels.length - 1) {
                lp.setMarginEnd(dp(6));
            }
            presets.addView(btn, lp);
        }
        hsv.addView(presets);
        box.addView(hsv);

        new MaterialAlertDialogBuilder(context)
                .setTitle("Scene Gravity Y")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    scene.gravityY = parseFloatOr(etGrav.getText().toString(), scene.gravityY);
                    notifySceneChanged(false);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showColorPickerDialog(String title, int initialColor, boolean allowReset, IntConsumer onPicked) {
        Context context = getContext();
        ScrollView sv = new ScrollView(context);
        LinearLayout box = createDialogContainer(context);
        sv.addView(box);

        final int[] chosen = new int[]{initialColor != 0 ? initialColor : 0xFF3B82F6};

        LinearLayout previewRow = new LinearLayout(context);
        previewRow.setOrientation(HORIZONTAL);
        previewRow.setGravity(Gravity.CENTER_VERTICAL);
        previewRow.setPadding(0, 0, 0, dp(10));

        View previewSwatch = new View(context);
        GradientDrawable previewBg = new GradientDrawable();
        previewBg.setCornerRadius(dp(10));
        previewBg.setStroke(dp(1), colorOutlineVariant);
        previewBg.setColor(chosen[0]);
        previewSwatch.setBackground(previewBg);
        previewRow.addView(previewSwatch, new LayoutParams(dp(44), dp(44)));

        EditText etHex = createStyledEditText(context, String.format(Locale.US, "#%08X", chosen[0]), "#AARRGGBB", InputType.TYPE_CLASS_TEXT);
        LayoutParams hexLp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        hexLp.setMarginStart(dp(10));
        previewRow.addView(etHex, hexLp);
        box.addView(previewRow);

        etHex.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {
                    String txt = s.toString().trim();
                    if (!txt.startsWith("#")) {
                        txt = "#" + txt;
                    }
                    if (txt.length() == 7 || txt.length() == 9) {
                        int parsed = Color.parseColor(txt);
                        chosen[0] = parsed;
                        previewBg.setColor(parsed);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // Grid of preset colors (6 columns x 4 rows)
        int cols = 6;
        for (int row = 0; row < PRESET_COLORS.length; row += cols) {
            LinearLayout rowLayout = new LinearLayout(context);
            rowLayout.setOrientation(HORIZONTAL);
            rowLayout.setPadding(0, dp(4), 0, dp(4));
            for (int c = 0; c < cols && (row + c) < PRESET_COLORS.length; c++) {
                int color = PRESET_COLORS[row + c];
                View item = new View(context);
                GradientDrawable gd = new GradientDrawable();
                gd.setShape(GradientDrawable.OVAL);
                gd.setColor(color);
                gd.setStroke(dp(1), colorOutlineVariant);
                item.setBackground(gd);
                LayoutParams itemLp = new LayoutParams(0, dp(34), 1f);
                itemLp.setMarginEnd(c < cols - 1 ? dp(6) : 0);
                item.setOnClickListener(v -> {
                    chosen[0] = color;
                    previewBg.setColor(color);
                    etHex.setText(String.format(Locale.US, "#%08X", color));
                });
                rowLayout.addView(item, itemLp);
            }
            box.addView(rowLayout);
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(sv)
                .setPositiveButton("Apply", (d, w) -> onPicked.accept(chosen[0]))
                .setNegativeButton("Cancel", null);
        if (allowReset) {
            builder.setNeutralButton("Default", (d, w) -> onPicked.accept(0));
        }
        builder.show();
    }

    private void showImagePickerDialog(String title, @Nullable String currentValue, StringConsumer onPicked) {
        Context context = getContext();
        List<String> images = (scId != null && !scId.isEmpty())
                ? GameProjectRepository.getProjectImageNames(scId) : new ArrayList<>();

        ScrollView sv = new ScrollView(context);
        LinearLayout list = createDialogContainer(context);
        sv.addView(list);

        final androidx.appcompat.app.AlertDialog[] dialogRef = new androidx.appcompat.app.AlertDialog[1];

        list.addView(createResourceRow(context, "None (use color)", null, TextUtils.isEmpty(currentValue), v -> {
            onPicked.accept("");
            if (dialogRef[0] != null) dialogRef[0].dismiss();
        }));

        if (images != null) {
            for (String imgName : images) {
                File imgFile = GameProjectRepository.getImageFile(scId, imgName);
                Bitmap thumb = (imgFile != null && imgFile.exists()) ? decodeThumb(imgFile.getAbsolutePath()) : null;
                boolean isSelected = imgName.equals(currentValue);
                list.addView(createResourceRow(context, imgName, thumb, isSelected, v -> {
                    onPicked.accept(imgName);
                    if (dialogRef[0] != null) dialogRef[0].dismiss();
                }));
            }
        }

        if (images == null || images.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText("No images imported in this project yet.");
            empty.setTextColor(colorOnSurfaceVariant);
            empty.setPadding(dp(8), dp(12), dp(8), dp(4));
            list.addView(empty);
        }

        dialogRef[0] = new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(sv)
                .setNeutralButton("Manage Images", (d, w) -> {
                    if (listener != null) {
                        listener.onRequestOpenImageManager();
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showSoundPickerDialog(String title, @Nullable String currentValue, StringConsumer onPicked) {
        Context context = getContext();
        List<String> sounds = (scId != null && !scId.isEmpty())
                ? GameProjectRepository.getProjectSoundNames(scId) : new ArrayList<>();

        ScrollView sv = new ScrollView(context);
        LinearLayout list = createDialogContainer(context);
        sv.addView(list);

        final androidx.appcompat.app.AlertDialog[] dialogRef = new androidx.appcompat.app.AlertDialog[1];

        list.addView(createSimpleResourceRow(context, R.drawable.ic_mtrl_music, "None", TextUtils.isEmpty(currentValue), v -> {
            onPicked.accept("");
            if (dialogRef[0] != null) dialogRef[0].dismiss();
        }));

        if (sounds != null) {
            for (String sound : sounds) {
                list.addView(createSimpleResourceRow(context, R.drawable.ic_mtrl_music, sound, sound.equals(currentValue), v -> {
                    onPicked.accept(sound);
                    if (dialogRef[0] != null) dialogRef[0].dismiss();
                }));
            }
        }

        if (sounds == null || sounds.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText("No sounds imported in this project yet.");
            empty.setTextColor(colorOnSurfaceVariant);
            empty.setPadding(dp(8), dp(12), dp(8), dp(4));
            list.addView(empty);
        }

        dialogRef[0] = new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(sv)
                .setNeutralButton("Manage Sounds", (d, w) -> {
                    if (listener != null) {
                        listener.onRequestOpenSoundManager();
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showFontPickerDialog(String title, @Nullable String currentValue, StringConsumer onPicked) {
        Context context = getContext();
        List<String> fonts = (scId != null && !scId.isEmpty())
                ? GameProjectRepository.getProjectFontNames(scId) : new ArrayList<>();

        ScrollView sv = new ScrollView(context);
        LinearLayout list = createDialogContainer(context);
        sv.addView(list);

        final androidx.appcompat.app.AlertDialog[] dialogRef = new androidx.appcompat.app.AlertDialog[1];

        list.addView(createSimpleResourceRow(context, R.drawable.ic_mtrl_font, "Default Font", TextUtils.isEmpty(currentValue), v -> {
            onPicked.accept("");
            if (dialogRef[0] != null) dialogRef[0].dismiss();
        }));

        if (fonts != null) {
            for (String font : fonts) {
                list.addView(createSimpleResourceRow(context, R.drawable.ic_mtrl_font, font, font.equals(currentValue), v -> {
                    onPicked.accept(font);
                    if (dialogRef[0] != null) dialogRef[0].dismiss();
                }));
            }
        }

        if (fonts == null || fonts.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText("No fonts imported in this project yet.");
            empty.setTextColor(colorOnSurfaceVariant);
            empty.setPadding(dp(8), dp(12), dp(8), dp(4));
            list.addView(empty);
        }

        dialogRef[0] = new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(sv)
                .setNeutralButton("Manage Fonts", (d, w) -> {
                    if (listener != null) {
                        listener.onRequestOpenFontManager();
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private View createResourceRow(Context context, String name, @Nullable Bitmap thumb, boolean isSelected, OnClickListener click) {
        MaterialCardView card = new MaterialCardView(context);
        card.setRadius(dp(10));
        card.setCardBackgroundColor(isSelected ? colorPrimaryContainer : colorSurfaceContainerHigh);
        card.setStrokeColor(isSelected ? colorPrimary : colorOutlineVariant);
        card.setStrokeWidth(dp(1));
        card.setCardElevation(0f);
        card.setOnClickListener(click);

        LayoutParams cardLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(6);
        card.setLayoutParams(cardLp);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));

        ImageView iv = new ImageView(context);
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        if (thumb != null) {
            iv.setImageBitmap(thumb);
        } else {
            iv.setImageResource(R.drawable.ic_mtrl_image);
            iv.setImageTintList(ColorStateList.valueOf(colorOnSurfaceVariant));
        }
        row.addView(iv, new LayoutParams(dp(40), dp(40)));

        TextView tv = new TextView(context);
        tv.setText(name);
        tv.setTextColor(isSelected ? colorOnPrimaryContainer : colorOnSurface);
        tv.setTextSize(13f);
        tv.setTypeface(null, isSelected ? Typeface.BOLD : Typeface.NORMAL);
        LayoutParams tvLp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        tvLp.setMarginStart(dp(12));
        row.addView(tv, tvLp);

        if (isSelected) {
            ImageView check = new ImageView(context);
            check.setImageResource(R.drawable.ic_mtrl_check);
            check.setImageTintList(ColorStateList.valueOf(colorOnPrimaryContainer));
            row.addView(check, new LayoutParams(dp(20), dp(20)));
        }

        card.addView(row);
        return card;
    }

    private View createSimpleResourceRow(Context context, @DrawableRes int iconRes, String name, boolean isSelected, OnClickListener click) {
        MaterialCardView card = new MaterialCardView(context);
        card.setRadius(dp(10));
        card.setCardBackgroundColor(isSelected ? colorPrimaryContainer : colorSurfaceContainerHigh);
        card.setStrokeColor(isSelected ? colorPrimary : colorOutlineVariant);
        card.setStrokeWidth(dp(1));
        card.setCardElevation(0f);
        card.setOnClickListener(click);

        LayoutParams cardLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(6);
        card.setLayoutParams(cardLp);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));

        ImageView iv = new ImageView(context);
        iv.setImageResource(iconRes);
        iv.setImageTintList(ColorStateList.valueOf(isSelected ? colorOnPrimaryContainer : colorPrimary));
        row.addView(iv, new LayoutParams(dp(22), dp(22)));

        TextView tv = new TextView(context);
        tv.setText(name);
        tv.setTextColor(isSelected ? colorOnPrimaryContainer : colorOnSurface);
        tv.setTextSize(13f);
        tv.setTypeface(null, isSelected ? Typeface.BOLD : Typeface.NORMAL);
        LayoutParams tvLp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        tvLp.setMarginStart(dp(12));
        row.addView(tv, tvLp);

        if (isSelected) {
            ImageView check = new ImageView(context);
            check.setImageResource(R.drawable.ic_mtrl_check);
            check.setImageTintList(ColorStateList.valueOf(colorOnPrimaryContainer));
            row.addView(check, new LayoutParams(dp(20), dp(20)));
        }

        card.addView(row);
        return card;
    }

    private void duplicateSelectedObject() {
        if (selected == null || scene == null) {
            return;
        }
        if (scene.objects == null) {
            scene.objects = new ArrayList<>();
        }
        pushUndo();
        GameObjectBean copy = selected.copy();
        copy.x += 24f;
        copy.y += 24f;
        int maxZ = selected.zIndex;
        for (GameObjectBean o : scene.objects) {
            if (o.zIndex > maxZ) {
                maxZ = o.zIndex;
            }
        }
        copy.zIndex = maxZ + 1;

        String baseId = nonEmpty(selected.id, "obj");
        String candId = baseId + "_copy";
        int idx = 2;
        while (scene.findObjectById(candId) != null) {
            candId = baseId + "_copy" + idx++;
        }
        copy.id = candId;

        String baseName = nonEmpty(selected.name, baseId);
        String candName = baseName + "_copy";
        int nIdx = 2;
        while (scene.findObjectByName(candName) != null) {
            candName = baseName + "_copy" + nIdx++;
        }
        copy.name = candName;

        scene.objects.add(copy);
        selected = copy;
        if (stageView != null) {
            stageView.selectObject(copy);
        }
        notifySceneChanged(false);
    }

    private void promptSavePrefab() {
        if (selected == null || project == null) {
            return;
        }
        String defaultName = nonEmpty(selected.name, selected.id);
        showTextInputDialog("Save as Prefab", defaultName, "Prefab name", name -> {
            String trimmed = name.trim();
            if (trimmed.isEmpty()) {
                return;
            }
            if (project.prefabs == null) {
                project.prefabs = new ArrayList<>();
            }
            GameObjectBean prefab = selected.copy();
            prefab.name = trimmed;
            prefab.id = "prefab_" + System.currentTimeMillis();
            project.prefabs.add(prefab);
            if (scene != null && scene.scripts != null && selected.id != null) {
                if (project.prefabScripts == null) {
                    project.prefabScripts = new ArrayList<>();
                }
                com.google.gson.Gson gson = new com.google.gson.Gson();
                int sIdx = 1;
                for (GameScriptNode script : scene.scripts) {
                    if (script != null && selected.id.equals(script.targetObjectId)) {
                        GameScriptNode copiedScript = gson.fromJson(gson.toJson(script), GameScriptNode.class);
                        if (copiedScript != null) {
                            copiedScript.id = "pfscript_" + System.currentTimeMillis() + "_" + (sIdx++);
                            copiedScript.targetObjectId = prefab.id;
                            project.prefabScripts.add(copiedScript);
                        }
                    }
                }
            }
            Toast.makeText(getContext(), "Prefab \"" + trimmed + "\" saved!", Toast.LENGTH_SHORT).show();
            if (listener != null) {
                listener.onPropertyChanged();
            }
        });
    }

    private void deleteSelectedObject() {
        if (selected == null || scene == null || scene.objects == null) {
            return;
        }
        String name = nonEmpty(selected.name, selected.id);
        new MaterialAlertDialogBuilder(getContext())
                .setTitle("Delete Object")
                .setMessage("Delete \"" + name + "\" from scene?")
                .setPositiveButton("Delete", (d, w) -> {
                    pushUndo();
                    scene.objects.remove(selected);
                    selected = null;
                    if (stageView != null) {
                        stageView.selectObject(null);
                    }
                    notifySceneChanged(false);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void promptCollisionTag(GameObjectBean target) {
        Context context = getContext();
        LinearLayout box = createDialogContainer(context);
        EditText etTag = createStyledEditText(context, "", "Other object's tag (blank = any)", InputType.TYPE_CLASS_TEXT);
        box.addView(etTag);

        // Collect existing tags from scene objects for quick picking
        Set<String> tags = new LinkedHashSet<>();
        if (scene != null && scene.objects != null) {
            for (GameObjectBean obj : scene.objects) {
                if (obj.tag != null && !obj.tag.trim().isEmpty()) {
                    tags.add(obj.tag.trim());
                }
            }
        }
        if (!tags.isEmpty()) {
            TextView lbl = new TextView(context);
            lbl.setText("Tags in scene:");
            lbl.setTextSize(12f);
            lbl.setTextColor(colorOnSurfaceVariant);
            lbl.setPadding(0, dp(8), 0, dp(4));
            box.addView(lbl);

            HorizontalScrollView hsv = new HorizontalScrollView(context);
            hsv.setHorizontalScrollBarEnabled(false);
            hsv.setClipToPadding(false);
            hsv.setPadding(0, dp(2), 0, dp(2));
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(HORIZONTAL);
            for (String tag : tags) {
                MaterialButton btn = createCompactOutlinedButton(context, tag, 11.5f);
                btn.setOnClickListener(v -> etTag.setText(tag));
                LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                lp.setMarginEnd(dp(6));
                row.addView(btn, lp);
            }
            hsv.addView(row);
            box.addView(hsv);
        }

        new MaterialAlertDialogBuilder(context)
                .setTitle("Event: On Collision")
                .setView(box)
                .setPositiveButton("Open Blocks", (d, w) ->
                        openBlocks(target, GameScriptNode.EVENT_COLLISION, etTag.getText().toString().trim()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void promptMessageEvent(@Nullable GameObjectBean target) {
        Context context = getContext();
        LinearLayout box = createDialogContainer(context);
        EditText etMsg = createStyledEditText(context, "", "Message key (e.g., game_over)", InputType.TYPE_CLASS_TEXT);
        box.addView(etMsg);

        new MaterialAlertDialogBuilder(context)
                .setTitle("Event: On Message")
                .setView(box)
                .setPositiveButton("Open Blocks", (d, w) ->
                        openBlocks(target, GameScriptNode.EVENT_MESSAGE, etMsg.getText().toString().trim()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openBlocks(@Nullable GameObjectBean target, String eventType, String eventParam) {
        if (listener != null) {
            listener.onOpenEventBlocks(target, eventType, eventParam != null ? eventParam : "");
        }
    }

    private void pushUndo() {
        if (stageView != null) {
            stageView.pushUndoState();
        }
    }

    private void notifySceneChanged(boolean refreshImages) {
        if (stageView != null) {
            if (refreshImages) {
                stageView.invalidateResourceCache();
            } else {
                stageView.invalidate();
            }
        }
        rebuildContent();
        if (listener != null) {
            listener.onPropertyChanged();
        }
    }

    // --- Script Inspection ---

    private static class ScriptInfo {
        final String param;

        ScriptInfo(String param) {
            this.param = param;
        }
    }

    private int countScriptBlocks(String targetId, String eventType, @Nullable String eventParam) {
        if (scene == null || scene.scripts == null || scene.scripts.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (GameScriptNode node : scene.scripts) {
            if (node == null) continue;
            String nodeParam = node.eventParam == null ? "" : node.eventParam;
            if (Objects.equals(targetId, node.targetObjectId) && Objects.equals(eventType, node.eventType)) {
                if (eventParam == null || Objects.equals(eventParam, nodeParam)) {
                    total += node.blocks != null ? node.blocks.size() : 0;
                }
            }
        }
        return total;
    }

    private List<ScriptInfo> findParameterizedScripts(String targetId, String eventType) {
        List<ScriptInfo> out = new ArrayList<>();
        if (scene == null || scene.scripts == null || scene.scripts.isEmpty()) {
            return out;
        }
        for (GameScriptNode node : scene.scripts) {
            if (node == null) continue;
            if (Objects.equals(targetId, node.targetObjectId) && Objects.equals(eventType, node.eventType)) {
                out.add(new ScriptInfo(node.eventParam == null ? "" : node.eventParam));
            }
        }
        return out;
    }

    // --- UI Helpers ---

    private LinearLayout createDialogContainer(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(VERTICAL);
        layout.setPadding(dp(20), dp(12), dp(20), dp(8));
        return layout;
    }

    private EditText createStyledEditText(Context context, String text, String hint, int inputType) {
        EditText et = new EditText(context);
        et.setText(text);
        et.setHint(hint);
        et.setInputType(inputType);
        et.setSingleLine(true);
        et.setTextColor(colorOnSurface);
        et.setHintTextColor(colorOnSurfaceVariant);
        return et;
    }

    private View labeledField(Context context, String label, View fieldView) {
        LinearLayout col = new LinearLayout(context);
        col.setOrientation(VERTICAL);
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        col.setLayoutParams(lp);

        TextView tv = new TextView(context);
        tv.setText(label);
        tv.setTextSize(11.5f);
        tv.setTextColor(colorOnSurfaceVariant);
        col.addView(tv);
        col.addView(fieldView);
        return col;
    }

    @Nullable
    private static Bitmap decodeThumb(String path) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, opts);
            int sample = 1;
            while (opts.outWidth / sample > 128 || opts.outHeight / sample > 128) {
                sample *= 2;
            }
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sample;
            return BitmapFactory.decodeFile(path, opts);
        } catch (Throwable t) {
            return null;
        }
    }

    private static float parseFloatOr(String s, float fallback) {
        try {
            return Float.parseFloat(s.trim().replace(',', '.'));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String formatFloat(float f) {
        if (Math.abs(f - Math.round(f)) < 0.001f) {
            return String.valueOf(Math.round(f));
        }
        return String.format(Locale.US, "%.2f", f);
    }

    private static String nonEmpty(@Nullable String s, String fallback) {
        return (s != null && !s.trim().isEmpty()) ? s : fallback;
    }

    private static String formatTypeName(@Nullable String type) {
        return GameObjectBean.getTypeDisplayName(type);
    }

    static MaterialButton createCompactOutlinedButton(Context context, String text, float textSizeSp) {
        MaterialButton btn = new MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btn.setText(text);
        btn.setTextSize(textSizeSp);
        btn.setSingleLine(true);
        btn.setInsetTop(0);
        btn.setInsetBottom(0);
        int minH = SketchwareUtil.dpToPx(38);
        int hPad = SketchwareUtil.dpToPx(12);
        int vPad = SketchwareUtil.dpToPx(6);
        btn.setMinHeight(minH);
        btn.setMinimumHeight(minH);
        btn.setMinWidth(0);
        btn.setMinimumWidth(0);
        btn.setPaddingRelative(hPad, vPad, hPad, vPad);
        return btn;
    }

    private int dp(float val) {
        return SketchwareUtil.dpToPx(val);
    }
}
