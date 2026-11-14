package pro.sketchware.activities.game.ui;

import android.content.Context;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.editor.component.CollapsibleComponentLayout;
import com.besome.sketch.editor.component.ComponentEventButton;
import com.besome.sketch.lib.base.CollapsibleViewHolder;
import com.besome.sketch.lib.ui.CollapsibleButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import pro.sketchware.R;
import pro.sketchware.activities.game.model.GameObjectBean;
import pro.sketchware.activities.game.model.GameProject;
import pro.sketchware.activities.game.model.GameScene;
import pro.sketchware.activities.game.model.GameScriptNode;
import pro.sketchware.databinding.FrLogicListItemEventPreviewBinding;
import pro.sketchware.editor.EditorViewUtils;
import pro.sketchware.utility.SketchwareUtil;

/**
 * Native-parity Component/Objects tab backed by {@code R.layout.fr_component_list} and
 * {@code R.layout.fr_logic_list_item_component}, listing every 2D object/HUD element in the
 * active scene with expandable event chips ({@code Ao Tocar}, {@code Colisão}, {@code Update},
 * {@code Ao Iniciar}, and {@code Ao Clicar} for HUD buttons) plus delete confirmation.
 */
public class GameObjectsTabView extends FrameLayout {

    public interface ComponentActionListener {
        void onSelectAndInspectObject(GameObjectBean obj);

        void onOpenObjectEventLogic(GameObjectBean obj, String eventType, String eventParam, String eventName);

        void onObjectsModified();
    }

    private static class ObjectRowItem {
        final GameObjectBean obj;
        boolean isCollapsed = true;
        boolean isConfirmation = false;

        ObjectRowItem(GameObjectBean obj) {
            this.obj = obj;
        }
    }

    private static class QuickEventDef {
        final String eventType;
        final String label;
        final String logicTitle;
        @DrawableRes final int iconRes;

        QuickEventDef(String eventType, String label, String logicTitle, @DrawableRes int iconRes) {
            this.eventType = eventType;
            this.label = label;
            this.logicTitle = logicTitle;
            this.iconRes = iconRes;
        }
    }

    private String scId;
    private GameProject project;
    private GameScene scene;
    private ComponentActionListener listener;

    private TextView emptyMessage;
    private RecyclerView componentList;
    private FloatingActionButton fab;
    private ObjectAdapter adapter;
    private final RecyclerView.RecycledViewPool eventViewPool = new RecyclerView.RecycledViewPool();

    private final List<ObjectRowItem> items = new ArrayList<>();
    private int colorOnSurfaceVariant;

    public GameObjectsTabView(@NonNull Context context) {
        this(context, null);
    }

    public GameObjectsTabView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.fr_component_list, this, true);

        colorOnSurfaceVariant = MaterialColors.getColor(context, R.attr.colorOnSurfaceVariant, 0xFF94A3B8);

        emptyMessage = findViewById(R.id.empty_message);
        componentList = findViewById(R.id.component_list);
        fab = findViewById(R.id.fab);

        emptyMessage.setText("No 2D objects in this scene.\nTap + to add one.");
        componentList.setLayoutManager(new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
        componentList.setHasFixedSize(true);
        adapter = new ObjectAdapter();
        componentList.setAdapter(adapter);

        componentList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 4 && fab.isShown()) {
                    fab.hide();
                } else if (dy < -4 && !fab.isShown()) {
                    fab.show();
                }
            }
        });

        fab.setOnClickListener(v -> {
            if (!EditorViewUtils.a()) {
                showQuickAddObjectDialog();
            }
        });
    }

    public void bind(String scId, GameProject project, GameScene scene, ComponentActionListener listener) {
        this.scId = scId;
        this.project = project;
        this.scene = scene;
        this.listener = listener;
        refreshData();
    }

    public void refreshData() {
        items.clear();
        if (scene != null && scene.objects != null) {
            for (GameObjectBean obj : scene.objects) {
                if (obj != null) {
                    items.add(new ObjectRowItem(obj));
                }
            }
        }
        updateEmptyState();
        adapter.notifyDataSetChanged();
    }

    public void unselectAll() {
        for (ObjectRowItem row : items) {
            row.isCollapsed = true;
            row.isConfirmation = false;
        }
        adapter.notifyDataSetChanged();
    }

    private void updateEmptyState() {
        emptyMessage.setVisibility(items.isEmpty() ? VISIBLE : GONE);
    }

    private void showQuickAddObjectDialog() {
        if (scene == null) {
            return;
        }
        scene.ensureInitialized();
        Context context = getContext();

        final String[] types = new String[]{
                GameObjectBean.TYPE_SPRITE,
                GameObjectBean.TYPE_ANIMATED_SPRITE,
                GameObjectBean.TYPE_PLATFORM,
                GameObjectBean.TYPE_PHYSICS_BOX,
                GameObjectBean.TYPE_TRIGGER,
                GameObjectBean.TYPE_PARTICLES,
                GameObjectBean.TYPE_CAMERA,
                GameObjectBean.TYPE_HUD_TEXT,
                GameObjectBean.TYPE_HUD_BUTTON,
                GameObjectBean.TYPE_HUD_JOYSTICK,
                GameObjectBean.TYPE_HUD_BAR
        };
        final String[] labels = new String[]{
                "2D Sprite",
                "Animated Sprite",
                "Solid Platform",
                "Physics Box",
                "Trigger / Sensor",
                "Particles",
                "2D Camera",
                "HUD Text",
                "HUD Button",
                "HUD Joystick",
                "HUD Bar"
        };

        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(12), dp(20), dp(8));

        TextView lblType = new TextView(context);
        lblType.setText("Object / component type:");
        lblType.setTextSize(12f);
        lblType.setTextColor(colorOnSurfaceVariant);
        box.addView(lblType);

        Spinner spnType = new Spinner(context);
        spnType.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, labels));
        box.addView(spnType);

        TextView lblName = new TextView(context);
        lblName.setText("Name (optional):");
        lblName.setTextSize(12f);
        lblName.setTextColor(colorOnSurfaceVariant);
        lblName.setPadding(0, dp(10), 0, dp(4));
        box.addView(lblName);

        EditText etName = new EditText(context);
        etName.setHint("Leave blank for auto name");
        etName.setInputType(InputType.TYPE_CLASS_TEXT);
        etName.setSingleLine(true);
        box.addView(etName);

        new MaterialAlertDialogBuilder(context)
                .setTitle("Add 2D Object / Component")
                .setView(box)
                .setPositiveButton("Add", (d, w) -> {
                    int idx = spnType.getSelectedItemPosition();
                    if (idx < 0 || idx >= types.length) return;
                    String chosenType = types[idx];
                    int nextIndex = scene.objects.size() + 1;
                    float spawnX = project != null && project.virtualWidth > 0 ? project.virtualWidth * 0.4f : 320f;
                    float spawnY = project != null && project.virtualHeight > 0 ? project.virtualHeight * 0.4f : 220f;
                    GameObjectBean created = GameObjectBean.createDefaultForType(chosenType, spawnX, spawnY, nextIndex);
                    String customName = etName.getText().toString().trim();
                    if (!customName.isEmpty()) {
                        created.name = customName;
                    }
                    scene.objects.add(created);
                    refreshData();
                    if (listener != null) {
                        listener.onObjectsModified();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private List<QuickEventDef> getQuickEventsForObject(GameObjectBean obj) {
        List<QuickEventDef> defs = new ArrayList<>();
        defs.add(new QuickEventDef(GameScriptNode.EVENT_TOUCH, "On Touch", "onTouchDown", R.drawable.ic_mtrl_touch));
        defs.add(new QuickEventDef(GameScriptNode.EVENT_COLLISION, "Collision", "onCollision", R.drawable.ic_mtrl_sensor));
        defs.add(new QuickEventDef(GameScriptNode.EVENT_UPDATE, "Update", "onUpdate", R.drawable.ic_mtrl_run));
        defs.add(new QuickEventDef(GameScriptNode.EVENT_SCENE_START, "On Start", "onCreate", R.drawable.ic_mtrl_code));
        if (GameObjectBean.TYPE_HUD_BUTTON.equals(obj.type) || findFirstScript(obj.id, GameScriptNode.EVENT_BUTTON) != null) {
            defs.add(new QuickEventDef(GameScriptNode.EVENT_BUTTON, "On Click", "onClick", R.drawable.ic_mtrl_button_click));
        }
        return defs;
    }

    @Nullable
    private GameScriptNode findFirstScript(@Nullable String objectId, String eventType) {
        if (scene == null || scene.scripts == null || objectId == null) {
            return null;
        }
        for (GameScriptNode node : scene.scripts) {
            if (node != null && Objects.equals(objectId, node.targetObjectId) && Objects.equals(eventType, node.eventType)) {
                return node;
            }
        }
        return null;
    }

    private class ObjectAdapter extends RecyclerView.Adapter<ObjectAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View item = LayoutInflater.from(parent.getContext()).inflate(R.layout.fr_logic_list_item_component, parent, false);
            return new ViewHolder(item);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private class ViewHolder extends CollapsibleViewHolder {
            final ImageView imgIcon;
            final TextView tvComponentType;
            final TextView tvComponentId;
            final LinearLayout eventsPreview;
            final ImageView imgMenu;
            final LinearLayout componentOptionLayout;
            final RecyclerView componentEvents;
            final CollapsibleComponentLayout componentOption;

            ViewHolder(View itemView) {
                super(itemView, 200);
                imgIcon = itemView.findViewById(R.id.img_icon);
                tvComponentType = itemView.findViewById(R.id.tv_component_type);
                tvComponentId = itemView.findViewById(R.id.tv_component_id);
                eventsPreview = itemView.findViewById(R.id.events_preview);
                imgMenu = itemView.findViewById(R.id.img_menu);
                componentOptionLayout = itemView.findViewById(R.id.component_option_layout);
                componentEvents = itemView.findViewById(R.id.component_events);
                componentOption = itemView.findViewById(R.id.component_option);

                componentOption.getDeleteButton().getLabel().setText("Delete object");
                componentOption.setButtonOnClickListener(v -> {
                    int pos = getLayoutPosition();
                    if (EditorViewUtils.a() || pos < 0 || pos >= items.size()) {
                        return;
                    }
                    ObjectRowItem row = items.get(pos);
                    if (v instanceof CollapsibleButton) {
                        row.isConfirmation = true;
                        setAnimateNextTransformation(true);
                        notifyItemChanged(pos);
                    } else if (v.getId() == R.id.confirm_no) {
                        row.isConfirmation = false;
                        setAnimateNextTransformation(true);
                        notifyItemChanged(pos);
                    } else if (v.getId() == R.id.confirm_yes) {
                        row.isConfirmation = false;
                        if (scene != null && scene.objects != null) {
                            scene.objects.remove(row.obj);
                        }
                        items.remove(pos);
                        notifyItemRemoved(pos);
                        notifyItemRangeChanged(pos, items.size() - pos);
                        updateEmptyState();
                        fab.show();
                        if (listener != null) {
                            listener.onObjectsModified();
                        }
                    }
                });

                onDoneInitializingViews();
                setOnClickCollapseConfig(v -> v == imgMenu);

                componentEvents.setRecycledViewPool(eventViewPool);
                componentEvents.setItemAnimator(null);

                itemView.setOnClickListener(v -> {
                    int pos = getLayoutPosition();
                    if (EditorViewUtils.a() || pos < 0 || pos >= items.size()) {
                        return;
                    }
                    ObjectRowItem row = items.get(pos);
                    if (listener != null) {
                        listener.onSelectAndInspectObject(row.obj);
                    }
                });
            }

            void bind(ObjectRowItem row) {
                GameObjectBean obj = row.obj;
                imgIcon.setImageResource(getObjectIconRes(obj.type));
                tvComponentType.setText(formatTypeName(obj.type));

                String displayId = nonEmpty(obj.name, obj.id);
                if (obj.tag != null && !obj.tag.trim().isEmpty()) {
                    displayId = displayId + " : #" + obj.tag.trim();
                } else if (obj.actionKey != null && !obj.actionKey.trim().isEmpty()) {
                    displayId = displayId + " : " + obj.actionKey.trim();
                }
                tvComponentId.setText(displayId);

                List<QuickEventDef> eventDefs = getQuickEventsForObject(obj);

                eventsPreview.removeAllViews();
                Context context = itemView.getContext();
                ColorMatrix grayscaleMatrix = new ColorMatrix();
                grayscaleMatrix.setSaturation(0f);
                ColorMatrixColorFilter grayFilter = new ColorMatrixColorFilter(grayscaleMatrix);

                for (QuickEventDef def : eventDefs) {
                    GameScriptNode existing = findFirstScript(obj.id, def.eventType);
                    boolean added = existing != null;

                    FrLogicListItemEventPreviewBinding previewBinding =
                            FrLogicListItemEventPreviewBinding.inflate(LayoutInflater.from(context));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.setMargins(0, 0, dp(4), 0);
                    previewBinding.getRoot().setLayoutParams(lp);
                    previewBinding.icon.setImageResource(def.iconRes);
                    if (added) {
                        previewBinding.iconBg.setBackgroundResource(R.drawable.circle_bg_surface);
                        previewBinding.icon.clearColorFilter();
                        previewBinding.icon.setAlpha(1f);
                        previewBinding.getRoot().setScaleX(1f);
                        previewBinding.getRoot().setScaleY(1f);
                    } else {
                        previewBinding.iconBg.setBackground(null);
                        previewBinding.icon.setColorFilter(grayFilter);
                        previewBinding.icon.setAlpha(0.45f);
                        previewBinding.getRoot().setScaleX(0.8f);
                        previewBinding.getRoot().setScaleY(0.8f);
                    }
                    eventsPreview.addView(previewBinding.getRoot());
                }

                if (row.isCollapsed) {
                    componentOptionLayout.setVisibility(GONE);
                    imgMenu.setRotation(0f);
                    eventsPreview.setTranslationX(0f);
                    eventsPreview.setAlpha(1f);
                } else {
                    componentOptionLayout.setVisibility(VISIBLE);
                    imgMenu.setRotation(-180f);
                    eventsPreview.setTranslationX(eventsPreview.getWidth());
                    eventsPreview.setAlpha(0f);
                    componentEvents.setTranslationX(0f);
                    componentEvents.setAlpha(1f);
                    if (row.isConfirmation) {
                        if (shouldAnimateNextTransformation()) {
                            componentOption.showConfirmation();
                            setAnimateNextTransformation(false);
                        } else {
                            componentOption.showConfirmationWithoutAnimation();
                        }
                    } else {
                        if (shouldAnimateNextTransformation()) {
                            componentOption.hideConfirmation();
                            setAnimateNextTransformation(false);
                        } else {
                            componentOption.hideConfirmationWithoutAnimation();
                        }
                    }
                }

                ViewGroup.LayoutParams optLp = componentOptionLayout.getLayoutParams();
                if (optLp != null) {
                    optLp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    componentOptionLayout.setLayoutParams(optLp);
                }

                componentEvents.setVisibility(VISIBLE);
                componentEvents.setAdapter(new ObjectEventChipsAdapter(obj, eventDefs));
            }

            @Override
            protected boolean isCollapsed() {
                int pos = getLayoutPosition();
                return pos < 0 || pos >= items.size() || items.get(pos).isCollapsed;
            }

            @Override
            protected void setIsCollapsed(boolean isCollapsed) {
                int pos = getLayoutPosition();
                if (pos >= 0 && pos < items.size()) {
                    items.get(pos).isCollapsed = isCollapsed;
                }
            }

            @NonNull
            @Override
            protected ViewGroup getOptionsLayout() {
                return componentOptionLayout;
            }

            @NonNull
            @Override
            protected Set<? extends View> getOnClickCollapseTriggerViews() {
                return Set.of(imgMenu);
            }

            @NonNull
            @Override
            protected Set<? extends View> getOnLongClickCollapseTriggerViews() {
                return Set.of(itemView);
            }

            @Override
            public void collapse() {
                super.collapse();
                eventsPreview.animate().translationX(0f).alpha(1f).setStartDelay(120L).setDuration(150L).start();
                componentEvents.animate().translationX(-componentEvents.getWidth()).setDuration(150L).alpha(0f).start();
            }

            @Override
            public void expand() {
                super.expand();
                eventsPreview.animate().translationX(eventsPreview.getWidth()).alpha(0f).setDuration(150L).start();
                componentEvents.setTranslationX(-componentEvents.getWidth());
                componentEvents.setAlpha(0f);
                componentEvents.animate().translationX(0f).alpha(1f).setStartDelay(100L).setDuration(150L).start();
            }
        }
    }

    private class ObjectEventChipsAdapter extends RecyclerView.Adapter<ObjectEventChipsAdapter.EventButtonHolder> {
        private final GameObjectBean targetObj;
        private final List<QuickEventDef> defs;

        ObjectEventChipsAdapter(GameObjectBean targetObj, List<QuickEventDef> defs) {
            this.targetObj = targetObj;
            this.defs = defs;
        }

        @NonNull
        @Override
        public EventButtonHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ComponentEventButton button = new ComponentEventButton(parent.getContext());
            button.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(90)));
            return new EventButtonHolder(button);
        }

        @Override
        public void onBindViewHolder(@NonNull EventButtonHolder holder, int position) {
            QuickEventDef def = defs.get(position);
            GameScriptNode existing = findFirstScript(targetObj.id, def.eventType);
            boolean added = existing != null;

            holder.button.getIcon().setImageResource(def.iconRes);
            holder.button.getName().setText(def.label);
            if (added) {
                holder.button.onEventAdded();
            } else {
                holder.button.onEventAvailableToAdd();
            }

            holder.button.setClickListener(v -> {
                if (EditorViewUtils.a()) {
                    return;
                }
                String param = "";
                if (existing != null && existing.eventParam != null) {
                    param = existing.eventParam;
                } else if (GameScriptNode.EVENT_BUTTON.equals(def.eventType) && targetObj.actionKey != null) {
                    param = targetObj.actionKey;
                }
                if (listener != null) {
                    listener.onOpenObjectEventLogic(targetObj, def.eventType, param, def.logicTitle);
                }
            });
        }

        @Override
        public int getItemCount() {
            return defs.size();
        }

        class EventButtonHolder extends RecyclerView.ViewHolder {
            final ComponentEventButton button;

            EventButtonHolder(ComponentEventButton button) {
                super(button);
                this.button = button;
            }
        }
    }

    @DrawableRes
    private static int getObjectIconRes(@Nullable String type) {
        if (type == null) return R.drawable.ic_mtrl_image;
        return switch (type) {
            case GameObjectBean.TYPE_ANIMATED_SPRITE -> R.drawable.ic_mtrl_animation;
            case GameObjectBean.TYPE_PLATFORM -> R.drawable.ic_mtrl_view_horizontal;
            case GameObjectBean.TYPE_PHYSICS_BOX -> R.drawable.ic_mtrl_box;
            case GameObjectBean.TYPE_TRIGGER -> R.drawable.ic_mtrl_sensor;
            case GameObjectBean.TYPE_PARTICLES -> R.drawable.ic_mtrl_auto_awesome;
            case GameObjectBean.TYPE_CAMERA -> R.drawable.ic_mtrl_camera;
            case GameObjectBean.TYPE_HUD_TEXT -> R.drawable.ic_mtrl_formattext;
            case GameObjectBean.TYPE_HUD_BUTTON -> R.drawable.ic_mtrl_button_click;
            case GameObjectBean.TYPE_HUD_JOYSTICK -> R.drawable.ic_mtrl_target;
            case GameObjectBean.TYPE_HUD_BAR -> R.drawable.ic_mtrl_progress_bar;
            default -> R.drawable.ic_mtrl_image;
        };
    }

    private static String formatTypeName(@Nullable String type) {
        if (type == null) return "Object";
        return switch (type) {
            case GameObjectBean.TYPE_SPRITE -> "2D Sprite";
            case GameObjectBean.TYPE_ANIMATED_SPRITE -> "Animated Sprite";
            case GameObjectBean.TYPE_PLATFORM -> "Platform";
            case GameObjectBean.TYPE_PHYSICS_BOX -> "Physics Box";
            case GameObjectBean.TYPE_TRIGGER -> "Trigger / Sensor";
            case GameObjectBean.TYPE_PARTICLES -> "Particles";
            case GameObjectBean.TYPE_CAMERA -> "2D Camera";
            case GameObjectBean.TYPE_HUD_TEXT -> "HUD Text";
            case GameObjectBean.TYPE_HUD_BUTTON -> "HUD Button";
            case GameObjectBean.TYPE_HUD_JOYSTICK -> "HUD Joystick";
            case GameObjectBean.TYPE_HUD_BAR -> "HUD Bar";
            default -> type;
        };
    }

    private static String nonEmpty(@Nullable String s, String fallback) {
        return (s != null && !s.trim().isEmpty()) ? s : fallback;
    }

    private int dp(float val) {
        return SketchwareUtil.dpToPx(val);
    }
}
