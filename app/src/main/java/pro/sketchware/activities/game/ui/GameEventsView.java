package pro.sketchware.activities.game.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
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
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.editor.event.CollapsibleEventLayout;
import com.besome.sketch.lib.base.CollapsibleViewHolder;
import com.besome.sketch.lib.ui.CollapsibleButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigationrail.NavigationRailView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import pro.sketchware.R;
import pro.sketchware.activities.game.model.GameObjectBean;
import pro.sketchware.activities.game.model.GameProject;
import pro.sketchware.activities.game.model.GameScene;
import pro.sketchware.activities.game.model.GameScriptNode;
import pro.sketchware.editor.EditorViewUtils;
import pro.sketchware.utility.SketchwareUtil;

/**
 * Native-parity Events tab backed by {@code R.layout.fr_logic_list} and
 * {@code R.layout.fr_logic_list_item}, organizing 2D scene & object scripts
 * across Sketchware's NavigationRail categories.
 */
public class GameEventsView extends FrameLayout {

    public interface EventActionListener {
        void onOpenEventLogic(GameScriptNode eventNode, @Nullable GameObjectBean target, String targetTitle, String eventName);

        void onEventsModified();
    }

    private static class EventEntry {
        final GameScriptNode node;
        @Nullable final GameObjectBean targetObj;
        final String targetTypeLabel;
        final String targetIdLabel;
        final String eventTypeBadge;
        final int eventTypeBgRes;
        final String eventName;
        final String description;
        @DrawableRes final int iconRes;
        @DrawableRes final int previewIconRes;
        final boolean showPreviewIcon;
        final boolean allowDelete;
        final boolean targetMissing;
        boolean collapsed = true;
        boolean confirmation = false;
        int buttonPressed = -1;

        EventEntry(GameScriptNode node,
                   @Nullable GameObjectBean targetObj,
                   String targetTypeLabel,
                   String targetIdLabel,
                   String eventTypeBadge,
                   @DrawableRes int eventTypeBgRes,
                   String eventName,
                   String description,
                   @DrawableRes int iconRes,
                   @DrawableRes int previewIconRes,
                   boolean showPreviewIcon,
                   boolean allowDelete,
                   boolean targetMissing) {
            this.node = node;
            this.targetObj = targetObj;
            this.targetTypeLabel = targetTypeLabel;
            this.targetIdLabel = targetIdLabel;
            this.eventTypeBadge = eventTypeBadge;
            this.eventTypeBgRes = eventTypeBgRes;
            this.eventName = eventName;
            this.description = description;
            this.iconRes = iconRes;
            this.previewIconRes = previewIconRes;
            this.showPreviewIcon = showPreviewIcon;
            this.allowDelete = allowDelete;
            this.targetMissing = targetMissing;
        }
    }

    private String scId;
    private GameProject project;
    private GameScene scene;
    private EventActionListener listener;

    private NavigationRailView paletteView;
    private TextView tvNoEvents;
    private RecyclerView eventList;
    private FloatingActionButton fab;
    private EventAdapter adapter;

    private int currentCategoryId = R.id.activity;
    private final List<EventEntry> visibleEntries = new ArrayList<>();

    private int colorOnSurfaceVariant;
    private int colorError;

    public GameEventsView(@NonNull Context context) {
        this(context, null);
    }

    public GameEventsView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.fr_logic_list, this, true);

        colorOnSurfaceVariant = MaterialColors.getColor(context, R.attr.colorOnSurfaceVariant, 0xFF94A3B8);
        colorError = MaterialColors.getColor(context, R.attr.colorError, 0xFFEF4444);

        paletteView = findViewById(R.id.palette);
        if (paletteView.getMenu() != null) {
            android.view.Menu menu = paletteView.getMenu();
            if (menu.findItem(R.id.activity) != null) menu.findItem(R.id.activity).setTitle("Scene");
            if (menu.findItem(R.id.view) != null) menu.findItem(R.id.view).setTitle("Objects");
            if (menu.findItem(R.id.component) != null) menu.findItem(R.id.component).setTitle("Collision");
            if (menu.findItem(R.id.drawer) != null) menu.findItem(R.id.drawer).setTitle("HUD");
            if (menu.findItem(R.id.moreblock) != null) menu.findItem(R.id.moreblock).setTitle("Messages");
        }
        View tvImport = findViewById(R.id.tv_import);
        if (tvImport != null) {
            tvImport.setVisibility(GONE);
        }
        tvNoEvents = findViewById(R.id.tv_no_events);
        eventList = findViewById(R.id.event_list);
        fab = findViewById(R.id.fab);

        eventList.setLayoutManager(new LinearLayoutManager(context, RecyclerView.VERTICAL, false));
        eventList.setHasFixedSize(true);
        adapter = new EventAdapter();
        eventList.setAdapter(adapter);

        eventList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 4 && fab.isShown()) {
                    fab.hide();
                } else if (dy < -4 && !fab.isShown()) {
                    fab.show();
                }
            }
        });

        paletteView.setOnItemSelectedListener(item -> {
            currentCategoryId = item.getItemId();
            fab.show();
            refreshEvents();
            return true;
        });
        paletteView.setSelectedItemId(R.id.activity);

        fab.setOnClickListener(v -> {
            if (!EditorViewUtils.a()) {
                showAddEventDialogForCurrentCategory();
            }
        });
    }

    public void bind(String scId, GameProject project, GameScene scene, EventActionListener listener) {
        this.scId = scId;
        this.project = project;
        this.scene = scene;
        this.listener = listener;
        refreshEvents();
    }

    public void refreshEvents() {
        visibleEntries.clear();

        if (scene == null) {
            updateEmptyState();
            adapter.notifyDataSetChanged();
            return;
        }

        scene.ensureInitialized();
        String sceneLabel = nonEmpty(scene.name, "Scene");

        if (currentCategoryId == R.id.activity) {
            GameScriptNode existingOnCreate = findScript(
                    GameScriptNode.SCENE_TARGET_ID, GameScriptNode.EVENT_SCENE_START, "");
            GameScriptNode onCreateNode = existingOnCreate != null ? existingOnCreate
                    : new GameScriptNode(GameScriptNode.SCENE_TARGET_ID, GameScriptNode.EVENT_SCENE_START, "");
            visibleEntries.add(new EventEntry(
                    onCreateNode,
                    null,
                    "Scene",
                    sceneLabel,
                    "SCENE",
                    R.drawable.bg_event_type_activity,
                    "onCreate",
                    formatBlockDescription("initializeLogic", onCreateNode),
                    R.drawable.ic_mtrl_code,
                    0,
                    false,
                    false,
                    false
            ));

            GameScriptNode existingOnUpdate = findScript(
                    GameScriptNode.SCENE_TARGET_ID, GameScriptNode.EVENT_UPDATE, "");
            GameScriptNode onUpdateNode = existingOnUpdate != null ? existingOnUpdate
                    : new GameScriptNode(GameScriptNode.SCENE_TARGET_ID, GameScriptNode.EVENT_UPDATE, "");
            visibleEntries.add(new EventEntry(
                    onUpdateNode,
                    null,
                    "Scene",
                    sceneLabel,
                    "SCENE",
                    R.drawable.bg_event_type_activity,
                    "onUpdate",
                    formatBlockDescription("onGameLoop (60 FPS)", onUpdateNode),
                    R.drawable.ic_mtrl_run,
                    0,
                    false,
                    existingOnUpdate != null,
                    false
            ));

            for (GameScriptNode node : scene.scripts) {
                if (node == null || node == existingOnCreate || node == existingOnUpdate) continue;
            }
        } else if (currentCategoryId == R.id.view) {
            for (GameScriptNode node : scene.scripts) {
                if (node == null) continue;
                boolean isTouch = GameScriptNode.EVENT_TOUCH.equals(node.eventType);
                boolean isButton = GameScriptNode.EVENT_BUTTON.equals(node.eventType);
                boolean isObjStart = !GameScriptNode.SCENE_TARGET_ID.equals(node.targetObjectId)
                        && GameScriptNode.EVENT_SCENE_START.equals(node.eventType);
                boolean isObjUpdate = !GameScriptNode.SCENE_TARGET_ID.equals(node.targetObjectId)
                        && GameScriptNode.EVENT_UPDATE.equals(node.eventType);
                if (isTouch || isButton) {
                    GameObjectBean obj = resolveObject(node.targetObjectId);
                    boolean isSceneTarget = GameScriptNode.SCENE_TARGET_ID.equals(node.targetObjectId);
                    boolean missing = !isSceneTarget && obj == null;
                    String targetType = isSceneTarget ? "Scene" : (missing ? "Removed object" : formatTypeName(obj.type));
                    String targetId = isSceneTarget ? sceneLabel : (missing ? node.targetObjectId : nonEmpty(obj.name, obj.id));
                    String eventTitle = isTouch ? "onTouchDown" : "onClick";
                    String baseDesc = isTouch ? "When object is touched"
                            : (nonEmpty(node.eventParam, "").isEmpty() ? "When button is clicked" : "Action: " + node.eventParam);
                    visibleEntries.add(new EventEntry(
                            node,
                            obj,
                            targetType,
                            targetId,
                            "OBJECT",
                            R.drawable.bg_event_type_view,
                            eventTitle,
                            formatBlockDescription(baseDesc, node),
                            obj != null ? getObjectIconRes(obj.type) : R.drawable.ic_mtrl_touch,
                            R.drawable.event_on_click_48dp,
                            true,
                            true,
                            missing
                    ));
                } else if (isObjStart || isObjUpdate) {
                    GameObjectBean obj = resolveObject(node.targetObjectId);
                    boolean missing = obj == null;
                    String targetType = missing ? "Removed object" : formatTypeName(obj.type);
                    String targetId = missing ? node.targetObjectId : nonEmpty(obj.name, obj.id);
                    visibleEntries.add(new EventEntry(
                            node,
                            obj,
                            targetType,
                            targetId,
                            "OBJECT",
                            R.drawable.bg_event_type_view,
                            isObjStart ? "onCreate" : "onUpdate",
                            formatBlockDescription(isObjStart ? "When object starts" : "onGameLoop (60 FPS)", node),
                            obj != null ? getObjectIconRes(obj.type)
                                    : (isObjStart ? R.drawable.ic_mtrl_code : R.drawable.ic_mtrl_run),
                            isObjStart ? R.drawable.ic_mtrl_code : R.drawable.ic_mtrl_run,
                            true,
                            true,
                            missing
                    ));
                }
            }
        } else if (currentCategoryId == R.id.component) {
            for (GameScriptNode node : scene.scripts) {
                if (node == null) continue;
                if (GameScriptNode.EVENT_COLLISION.equals(node.eventType)) {
                    GameObjectBean obj = resolveObject(node.targetObjectId);
                    boolean missing = obj == null && !GameScriptNode.SCENE_TARGET_ID.equals(node.targetObjectId);
                    String targetType = obj != null ? formatTypeName(obj.type)
                            : (missing ? "Removed object" : "Physics");
                    String targetId = obj != null ? nonEmpty(obj.name, obj.id)
                            : (missing ? node.targetObjectId : sceneLabel);
                    String tagDesc = nonEmpty(node.eventParam, "").isEmpty()
                            ? "Collision with any tag"
                            : "Collision with tag: " + node.eventParam;
                    visibleEntries.add(new EventEntry(
                            node,
                            obj,
                            targetType,
                            targetId,
                            "COLLISION",
                            R.drawable.bg_event_type_component,
                            "onCollision",
                            formatBlockDescription(tagDesc, node),
                            R.drawable.ic_mtrl_sensor,
                            R.drawable.ic_mtrl_sensor,
                            true,
                            true,
                            missing
                    ));
                }
            }
        } else if (currentCategoryId == R.id.drawer) {
            for (GameScriptNode node : scene.scripts) {
                if (node == null) continue;
                GameObjectBean obj = resolveObject(node.targetObjectId);
                if (obj != null && isHudOrCamera(obj)) {
                    String targetType = formatTypeName(obj.type);
                    String targetId = nonEmpty(obj.name, obj.id);
                    String eventTitle = mapEventTitle(node.eventType);
                    String baseDesc = "HUD / Camera (" + eventTitle + ")"
                            + (nonEmpty(node.eventParam, "").isEmpty() ? "" : ": " + node.eventParam);
                    visibleEntries.add(new EventEntry(
                            node,
                            obj,
                            targetType,
                            targetId,
                            "HUD",
                            R.drawable.bg_event_type_drawer_view,
                            eventTitle,
                            formatBlockDescription(baseDesc, node),
                            getObjectIconRes(obj.type),
                            R.drawable.ic_mtrl_camera,
                            true,
                            true,
                            false
                    ));
                }
            }
        } else if (currentCategoryId == R.id.moreblock) {
            for (GameScriptNode node : scene.scripts) {
                if (node == null) continue;
                if (GameScriptNode.EVENT_MESSAGE.equals(node.eventType)) {
                    GameObjectBean obj = resolveObject(node.targetObjectId);
                    boolean isSceneTarget = GameScriptNode.SCENE_TARGET_ID.equals(node.targetObjectId);
                    String targetId = isSceneTarget
                            ? nonEmpty(node.eventParam, "message")
                            : nonEmpty(obj != null ? obj.name : node.targetObjectId, "object")
                            + (nonEmpty(node.eventParam, "").isEmpty() ? "" : " : " + node.eventParam);
                    String baseDesc = "Global message: " + nonEmpty(node.eventParam, "(no key)");
                    visibleEntries.add(new EventEntry(
                            node,
                            obj,
                            "Message",
                            targetId,
                            "MESSAGE",
                            R.drawable.bg_event_type_moreblock,
                            "onMessage",
                            formatBlockDescription(baseDesc, node),
                            R.drawable.ic_mtrl_code,
                            R.drawable.ic_mtrl_chat,
                            false,
                            true,
                            !isSceneTarget && obj == null
                    ));
                }
            }
        }

        updateEmptyState();
        adapter.notifyDataSetChanged();
    }

    public void unselectAll() {
        for (EventEntry entry : visibleEntries) {
            entry.collapsed = true;
            entry.confirmation = false;
        }
        adapter.notifyDataSetChanged();
    }

    private void updateEmptyState() {
        if (visibleEntries.isEmpty()) {
            tvNoEvents.setVisibility(VISIBLE);
            tvNoEvents.setText(getEmptyMessageForCategory());
        } else {
            tvNoEvents.setVisibility(GONE);
        }
    }

    private String getEmptyMessageForCategory() {
        if (currentCategoryId == R.id.view) {
            return "No touch or button events.\nTap + to add one.";
        } else if (currentCategoryId == R.id.component) {
            return "No collision events.\nTap + to create a detector.";
        } else if (currentCategoryId == R.id.drawer) {
            return "No Camera or HUD events.\nTap + to bind a control.";
        } else if (currentCategoryId == R.id.moreblock) {
            return "No custom messages.\nTap + to create a channel.";
        }
        return "No events in this category.";
    }

    private void showAddEventDialogForCurrentCategory() {
        if (scene == null) {
            return;
        }
        scene.ensureInitialized();
        Context context = getContext();

        if (currentCategoryId == R.id.activity) {
            String[] items = new String[]{
                    "onCreate — On Scene Start",
                    "onUpdate — GameLoop (60 FPS)"
            };
            new MaterialAlertDialogBuilder(context)
                    .setTitle("Scene Events")
                    .setItems(items, (d, which) -> {
                        String evType = which == 0 ? GameScriptNode.EVENT_SCENE_START : GameScriptNode.EVENT_UPDATE;
                        String evName = which == 0 ? "onCreate" : "onUpdate";
                        createAndOpenEvent(null, evType, "", "Scene", evName);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        if (currentCategoryId == R.id.moreblock) {
            LinearLayout box = createDialogContainer(context);
            EditText etKey = new EditText(context);
            etKey.setHint("Message key (e.g., game_over)");
            etKey.setInputType(InputType.TYPE_CLASS_TEXT);
            etKey.setSingleLine(true);
            box.addView(etKey);

            new MaterialAlertDialogBuilder(context)
                    .setTitle("New Message Event")
                    .setView(box)
                    .setPositiveButton("Create & Open", (d, w) -> {
                        String key = etKey.getText().toString().trim();
                        if (key.isEmpty()) {
                            key = "message_1";
                        }
                        createAndOpenEvent(null, GameScriptNode.EVENT_MESSAGE, key, "Message", "onMessage: " + key);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        List<GameObjectBean> candidates = new ArrayList<>();
        if (scene.objects != null) {
            if (currentCategoryId == R.id.drawer) {
                for (GameObjectBean obj : scene.objects) {
                    if (obj != null && isHudOrCamera(obj)) {
                        candidates.add(obj);
                    }
                }
            } else {
                for (GameObjectBean obj : scene.objects) {
                    if (obj != null) {
                        candidates.add(obj);
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            String msg = currentCategoryId == R.id.drawer
                    ? "Add a Camera or HUD object to the scene first."
                    : "Add at least one 2D object to the scene first.";
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout box = createDialogContainer(context);
        TextView lblTarget = new TextView(context);
        lblTarget.setText("Target object:");
        lblTarget.setTextSize(12f);
        lblTarget.setTextColor(colorOnSurfaceVariant);
        box.addView(lblTarget);

        Spinner spnObject = new Spinner(context);
        List<String> objNames = new ArrayList<>();
        for (GameObjectBean obj : candidates) {
            objNames.add(nonEmpty(obj.name, obj.id) + " (" + formatTypeName(obj.type) + ")");
        }
        ArrayAdapter<String> objAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, objNames);
        spnObject.setAdapter(objAdapter);
        box.addView(spnObject);

        if (currentCategoryId == R.id.view || currentCategoryId == R.id.drawer) {
            TextView lblType = new TextView(context);
            lblType.setText("Event type:");
            lblType.setTextSize(12f);
            lblType.setTextColor(colorOnSurfaceVariant);
            lblType.setPadding(0, dp(10), 0, dp(4));
            box.addView(lblType);

            RadioGroup rg = new RadioGroup(context);
            RadioButton rbTouch = new RadioButton(context);
            rbTouch.setText("onTouchDown (When object is touched)");
            rbTouch.setId(View.generateViewId());
            RadioButton rbButton = new RadioButton(context);
            rbButton.setText("onClick (When button / action is clicked)");
            rbButton.setId(View.generateViewId());
            rg.addView(rbTouch);
            rg.addView(rbButton);
            rg.check(rbTouch.getId());
            box.addView(rg);

            new MaterialAlertDialogBuilder(context)
                    .setTitle(currentCategoryId == R.id.drawer ? "New HUD / Camera Event" : "New Object Event")
                    .setView(box)
                    .setPositiveButton("Create & Open", (d, w) -> {
                        int pos = spnObject.getSelectedItemPosition();
                        if (pos < 0 || pos >= candidates.size()) return;
                        GameObjectBean obj = candidates.get(pos);
                        boolean isButton = rg.getCheckedRadioButtonId() == rbButton.getId();
                        String evType = isButton ? GameScriptNode.EVENT_BUTTON : GameScriptNode.EVENT_TOUCH;
                        String evParam = isButton ? nonEmpty(obj.actionKey, "") : "";
                        String evName = isButton ? "onClick" : "onTouchDown";
                        createAndOpenEvent(obj, evType, evParam, nonEmpty(obj.name, obj.id), evName);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } else if (currentCategoryId == R.id.component) {
            TextView lblTag = new TextView(context);
            lblTag.setText("Other object's collision tag (blank = any):");
            lblTag.setTextSize(12f);
            lblTag.setTextColor(colorOnSurfaceVariant);
            lblTag.setPadding(0, dp(10), 0, dp(4));
            box.addView(lblTag);

            EditText etTag = new EditText(context);
            etTag.setHint("e.g., enemy, coin, ground");
            etTag.setSingleLine(true);
            box.addView(etTag);

            Set<String> tags = new LinkedHashSet<>();
            for (GameObjectBean o : scene.objects) {
                if (o != null && o.tag != null && !o.tag.trim().isEmpty()) {
                    tags.add(o.tag.trim());
                }
            }
            if (!tags.isEmpty()) {
                TextView lblChips = new TextView(context);
                lblChips.setText("Suggestions: " + String.join(", ", tags));
                lblChips.setTextSize(11f);
                lblChips.setTextColor(colorOnSurfaceVariant);
                lblChips.setPadding(0, dp(4), 0, 0);
                box.addView(lblChips);
            }

            new MaterialAlertDialogBuilder(context)
                    .setTitle("New Collision Event")
                    .setView(box)
                    .setPositiveButton("Create & Open", (d, w) -> {
                        int pos = spnObject.getSelectedItemPosition();
                        if (pos < 0 || pos >= candidates.size()) return;
                        GameObjectBean obj = candidates.get(pos);
                        String param = etTag.getText().toString().trim();
                        createAndOpenEvent(obj, GameScriptNode.EVENT_COLLISION, param, nonEmpty(obj.name, obj.id), "onCollision");
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    private void createAndOpenEvent(@Nullable GameObjectBean targetObj, String eventType, String eventParam,
                                    String targetTitle, String eventName) {
        if (scene == null) return;
        String targetId = targetObj != null && targetObj.id != null ? targetObj.id : GameScriptNode.SCENE_TARGET_ID;
        GameScriptNode node = scene.getOrCreateScript(targetId, eventType, eventParam != null ? eventParam : "");
        refreshEvents();
        if (listener != null) {
            listener.onEventsModified();
            listener.onOpenEventLogic(node, targetObj, targetTitle, eventName);
        }
    }

    private class EventAdapter extends RecyclerView.Adapter<EventAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View item = LayoutInflater.from(parent.getContext()).inflate(R.layout.fr_logic_list_item, parent, false);
            return new ViewHolder(item);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            EventEntry entry = visibleEntries.get(position);

            holder.icon.setImageResource(entry.iconRes);
            holder.targetId.setText(entry.targetIdLabel);

            if (entry.targetTypeLabel != null && !entry.targetTypeLabel.isEmpty()) {
                holder.targetType.setVisibility(VISIBLE);
                holder.targetType.setText(entry.targetTypeLabel);
                holder.targetType.setTextColor(entry.targetMissing ? colorError : colorOnSurfaceVariant);
            } else {
                holder.targetType.setVisibility(GONE);
            }

            holder.type.setText(entry.eventTypeBadge);
            if (entry.eventTypeBgRes != 0) {
                holder.type.setBackgroundResource(entry.eventTypeBgRes);
            }
            holder.name.setVisibility(VISIBLE);
            holder.name.setText(entry.eventName);
            holder.name.setTextSize(12.5f);
            holder.name.setTypeface(Typeface.DEFAULT_BOLD);
            holder.name.setTextColor(colorOnSurfaceVariant);

            holder.previewContainer.setVisibility(VISIBLE);
            if (entry.showPreviewIcon && entry.previewIconRes != 0) {
                holder.preview.setVisibility(VISIBLE);
                holder.preview.setImageResource(entry.previewIconRes);
            } else {
                holder.preview.setVisibility(GONE);
            }
            holder.description.setText(entry.description);

            if (entry.allowDelete) {
                holder.optionsLayout.showDelete();
            } else {
                holder.optionsLayout.hideDelete();
            }
            holder.optionsLayout.hideAddToCollection();

            if (entry.collapsed) {
                holder.optionContainer.setVisibility(GONE);
                holder.menu.setRotation(0f);
            } else {
                holder.optionContainer.setVisibility(VISIBLE);
                holder.menu.setRotation(-180f);
            }

            if (entry.confirmation) {
                if (holder.shouldAnimateNextTransformation()) {
                    holder.optionsLayout.showConfirmation();
                    holder.setAnimateNextTransformation(false);
                } else {
                    holder.optionsLayout.showConfirmationWithoutAnimation();
                }
            } else {
                if (holder.shouldAnimateNextTransformation()) {
                    holder.optionsLayout.hideConfirmation();
                    holder.setAnimateNextTransformation(false);
                } else {
                    holder.optionsLayout.hideConfirmationWithoutAnimation();
                }
            }

            ViewGroup.LayoutParams optLp = holder.optionContainer.getLayoutParams();
            if (optLp != null) {
                optLp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                holder.optionContainer.setLayoutParams(optLp);
            }
        }

        @Override
        public int getItemCount() {
            return visibleEntries.size();
        }

        private class ViewHolder extends CollapsibleViewHolder {
            final MaterialCardView root;
            final ImageView icon;
            final TextView targetType;
            final TextView targetId;
            final TextView type;
            final TextView name;
            final LinearLayout previewContainer;
            final ImageView preview;
            final TextView description;
            final ImageView menu;
            final LinearLayout optionContainer;
            final CollapsibleEventLayout optionsLayout;

            ViewHolder(View itemView) {
                super(itemView, 200);
                root = (MaterialCardView) itemView;
                icon = itemView.findViewById(R.id.img_icon);
                targetType = itemView.findViewById(R.id.tv_target_type);
                targetId = itemView.findViewById(R.id.tv_target_id);
                type = itemView.findViewById(R.id.tv_event_type);
                name = itemView.findViewById(R.id.tv_event_name);
                previewContainer = itemView.findViewById(R.id.ll_preview);
                preview = itemView.findViewById(R.id.img_preview);
                description = itemView.findViewById(R.id.tv_event_text);
                menu = itemView.findViewById(R.id.img_menu);
                optionContainer = itemView.findViewById(R.id.event_option_layout);
                optionsLayout = itemView.findViewById(R.id.event_option);

                optionsLayout.setButtonOnClickListener(v -> {
                    int pos = getLayoutPosition();
                    if (EditorViewUtils.a() || pos < 0 || pos >= visibleEntries.size()) {
                        return;
                    }
                    EventEntry entry = visibleEntries.get(pos);
                    if (v instanceof CollapsibleButton button) {
                        setAnimateNextTransformation(true);
                        entry.buttonPressed = button.getButtonId();
                        entry.confirmation = true;
                        notifyItemChanged(pos);
                    } else if (v.getId() == R.id.confirm_no) {
                        entry.confirmation = false;
                        setAnimateNextTransformation(true);
                        notifyItemChanged(pos);
                    } else if (v.getId() == R.id.confirm_yes) {
                        entry.confirmation = false;
                        if (entry.buttonPressed == 0) {
                            // Reset script blocks
                            if (entry.node != null && entry.node.blocks != null) {
                                entry.node.blocks.clear();
                            }
                            entry.collapsed = true;
                            setAnimateNextTransformation(true);
                            refreshEvents();
                            if (listener != null) {
                                listener.onEventsModified();
                            }
                        } else if (entry.buttonPressed == 1) {
                            // Delete script
                            if (scene != null && scene.scripts != null && entry.allowDelete) {
                                scene.scripts.remove(entry.node);
                            }
                            visibleEntries.remove(pos);
                            notifyItemRemoved(pos);
                            updateEmptyState();
                            if (listener != null) {
                                listener.onEventsModified();
                            }
                        }
                        fab.show();
                    }
                });

                onDoneInitializingViews();

                root.setOnClickListener(v -> {
                    int pos = getLayoutPosition();
                    if (EditorViewUtils.a() || pos < 0 || pos >= visibleEntries.size()) {
                        return;
                    }
                    EventEntry entry = visibleEntries.get(pos);
                    if (scene != null && entry.node != null) {
                        GameScriptNode ensured = scene.getOrCreateScript(
                                entry.node.targetObjectId, entry.node.eventType, entry.node.eventParam);
                        if (listener != null) {
                            listener.onOpenEventLogic(ensured, entry.targetObj, entry.targetIdLabel, entry.eventName);
                        }
                    } else if (listener != null) {
                        listener.onOpenEventLogic(entry.node, entry.targetObj, entry.targetIdLabel, entry.eventName);
                    }
                });
            }

            @Override
            protected boolean isCollapsed() {
                int pos = getLayoutPosition();
                return pos < 0 || pos >= visibleEntries.size() || visibleEntries.get(pos).collapsed;
            }

            @Override
            protected void setIsCollapsed(boolean isCollapsed) {
                int pos = getLayoutPosition();
                if (pos >= 0 && pos < visibleEntries.size()) {
                    visibleEntries.get(pos).collapsed = isCollapsed;
                }
            }

            @NonNull
            @Override
            protected ViewGroup getOptionsLayout() {
                return optionContainer;
            }

            @NonNull
            @Override
            protected Set<? extends View> getOnClickCollapseTriggerViews() {
                return Set.of(menu);
            }

            @NonNull
            @Override
            protected Set<? extends View> getOnLongClickCollapseTriggerViews() {
                return Set.of(root);
            }
        }
    }

    @Nullable
    private GameScriptNode findScript(String targetObjectId, String eventType, String eventParam) {
        if (scene == null || scene.scripts == null) {
            return null;
        }
        String param = eventParam == null ? "" : eventParam;
        for (GameScriptNode node : scene.scripts) {
            if (node == null) continue;
            String nodeParam = node.eventParam == null ? "" : node.eventParam;
            if (Objects.equals(targetObjectId, node.targetObjectId)
                    && Objects.equals(eventType, node.eventType)
                    && Objects.equals(param, nodeParam)) {
                return node;
            }
        }
        return null;
    }

    @Nullable
    private GameObjectBean resolveObject(@Nullable String objectId) {
        if (scene == null || objectId == null || GameScriptNode.SCENE_TARGET_ID.equals(objectId)) {
            return null;
        }
        return scene.findObjectById(objectId);
    }

    private LinearLayout createDialogContainer(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(12), dp(20), dp(8));
        return layout;
    }

    private static String formatBlockDescription(String base, GameScriptNode node) {
        int count = node != null && node.blocks != null ? node.blocks.size() : 0;
        if (count <= 0) {
            return base;
        }
        return base + " (" + count + (count == 1 ? " block)" : " blocks)");
    }

    private static boolean isHudOrCamera(@Nullable GameObjectBean obj) {
        return obj != null && (obj.isHudType() || GameObjectBean.TYPE_CAMERA.equals(obj.type));
    }

    private static String mapEventTitle(@Nullable String eventType) {
        if (eventType == null) return "event";
        return switch (eventType) {
            case GameScriptNode.EVENT_SCENE_START -> "onCreate";
            case GameScriptNode.EVENT_UPDATE -> "onUpdate";
            case GameScriptNode.EVENT_TOUCH -> "onTouchDown";
            case GameScriptNode.EVENT_COLLISION -> "onCollision";
            case GameScriptNode.EVENT_BUTTON -> "onClick";
            case GameScriptNode.EVENT_MESSAGE -> "onMessage";
            default -> eventType;
        };
    }

    @DrawableRes
    private static int getObjectIconRes(@Nullable String type) {
        return GamePaletteView.getTypeIconRes(type);
    }

    private static String formatTypeName(@Nullable String type) {
        return GameObjectBean.getTypeDisplayName(type);
    }

    private static String nonEmpty(@Nullable String s, String fallback) {
        return (s != null && !s.trim().isEmpty()) ? s : fallback;
    }

    private int dp(float val) {
        return SketchwareUtil.dpToPx(val);
    }
}
