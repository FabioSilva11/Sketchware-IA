package pro.sketchware.activities.game.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.divider.MaterialDivider;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

import pro.sketchware.R;
import pro.sketchware.activities.game.model.GameObjectBean;
import pro.sketchware.activities.game.model.GameProject;
import pro.sketchware.activities.game.model.GameScene;
import pro.sketchware.utility.SketchwareUtil;

/**
 * Static Material 3 dialog helpers for Scene Management, Object Hierarchy & Prefabs,
 * and 2D Engine Settings.
 */
public class GameHierarchyDialog {

    public interface SceneDialogCallback {
        void onSceneSelected(GameScene scene);

        void onProjectUpdated();
    }

    public interface HierarchyDialogCallback {
        void onSelectObject(GameObjectBean obj);

        void onSceneUpdated();
    }

    private GameHierarchyDialog() {
    }

    public static void showSceneManagerDialog(
            @NonNull Context context,
            @NonNull GameProject project,
            @Nullable GameScene currentScene,
            @Nullable SceneDialogCallback callback) {
        if (project.scenes == null) {
            project.scenes = new ArrayList<>();
        }

        int colorSurfaceHigh = MaterialColors.getColor(context, R.attr.colorSurfaceContainerHigh, 0xFF283040);
        int colorPrimary = MaterialColors.getColor(context, R.attr.colorPrimary, 0xFF3B82F6);
        int colorPrimaryContainer = MaterialColors.getColor(context, R.attr.colorPrimaryContainer, 0xFF1D4ED8);
        int colorOnPrimaryContainer = MaterialColors.getColor(context, R.attr.colorOnPrimaryContainer, 0xFFDBEAFE);
        int colorOnSurface = MaterialColors.getColor(context, R.attr.colorOnSurface, 0xFFE5E7EB);
        int colorOnSurfaceVariant = MaterialColors.getColor(context, R.attr.colorOnSurfaceVariant, 0xFF94A3B8);
        int colorError = MaterialColors.getColor(context, R.attr.colorError, 0xFFEF4444);
        int colorOutlineVariant = MaterialColors.getColor(context, R.attr.colorOutlineVariant, 0x3394A3B8);

        ScrollView scrollView = new ScrollView(context);
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(8), dp(16), dp(8));
        scrollView.addView(list);

        final AlertDialog[] dialogRef = new AlertDialog[1];
        final GameScene[] activeRef = new GameScene[]{currentScene};

        Runnable[] rebuild = new Runnable[1];
        rebuild[0] = () -> {
            list.removeAllViews();
            if (project.scenes.isEmpty()) {
                TextView empty = new TextView(context);
                empty.setText("No scenes found.");
                empty.setTextColor(colorOnSurfaceVariant);
                empty.setPadding(dp(8), dp(12), dp(8), dp(12));
                list.addView(empty);
                return;
            }

            for (int i = 0; i < project.scenes.size(); i++) {
                GameScene scene = project.scenes.get(i);
                boolean isCurrent = activeRef[0] != null && scene.id != null && scene.id.equals(activeRef[0].id);
                boolean isStart = scene.id != null && scene.id.equals(project.startSceneId);

                MaterialCardView card = new MaterialCardView(context);
                card.setRadius(dp(12));
                card.setCardBackgroundColor(isCurrent ? colorPrimaryContainer : colorSurfaceHigh);
                card.setStrokeColor(isCurrent ? colorPrimary : colorOutlineVariant);
                card.setStrokeWidth(dp(1));
                card.setCardElevation(0f);

                LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                cardLp.bottomMargin = dp(8);
                card.setLayoutParams(cardLp);

                LinearLayout row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(12), dp(10), dp(8), dp(10));

                LinearLayout textCol = new LinearLayout(context);
                textCol.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

                LinearLayout titleRow = new LinearLayout(context);
                titleRow.setOrientation(LinearLayout.HORIZONTAL);
                titleRow.setGravity(Gravity.CENTER_VERTICAL);

                TextView tvName = new TextView(context);
                tvName.setText(nonEmpty(scene.name, scene.id));
                tvName.setTextColor(isCurrent ? colorOnPrimaryContainer : colorOnSurface);
                tvName.setTextSize(14f);
                tvName.setTypeface(null, Typeface.BOLD);
                tvName.setSingleLine(true);
                tvName.setEllipsize(TextUtils.TruncateAt.END);
                titleRow.addView(tvName);

                if (isStart) {
                    TextView startBadge = createPillBadge(context, "START", colorPrimary, 0xFFFFFFFF);
                    LinearLayout.LayoutParams bLp = (LinearLayout.LayoutParams) startBadge.getLayoutParams();
                    bLp.setMarginStart(dp(6));
                    titleRow.addView(startBadge);
                }
                if (isCurrent) {
                    TextView activeBadge = createPillBadge(context, "ACTIVE", 0xFF10B981, 0xFFFFFFFF);
                    LinearLayout.LayoutParams bLp = (LinearLayout.LayoutParams) activeBadge.getLayoutParams();
                    bLp.setMarginStart(dp(6));
                    titleRow.addView(activeBadge);
                }

                textCol.addView(titleRow);

                int objCount = scene.objects != null ? scene.objects.size() : 0;
                TextView tvSub = new TextView(context);
                tvSub.setText(objCount + (objCount == 1 ? " object" : " objects"));
                tvSub.setTextColor(isCurrent ? colorOnPrimaryContainer : colorOnSurfaceVariant);
                tvSub.setTextSize(11.5f);
                textCol.addView(tvSub);

                row.addView(textCol, textLp);

                // Action icons: Set Start, Rename, Duplicate, Delete
                row.addView(createIconAction(context, R.drawable.ic_mtrl_star,
                        isStart ? 0xFFFACC15 : colorOnSurfaceVariant, "Set as start scene", v -> {
                            project.startSceneId = scene.id;
                            if (callback != null) callback.onProjectUpdated();
                            rebuild[0].run();
                        }));

                row.addView(createIconAction(context, R.drawable.ic_mtrl_edit,
                        isCurrent ? colorOnPrimaryContainer : colorOnSurfaceVariant, "Rename", v ->
                                promptText(context, "Rename Scene", nonEmpty(scene.name, ""), "Scene name", newName -> {
                                    if (!newName.trim().isEmpty()) {
                                        scene.name = newName.trim();
                                        if (callback != null) callback.onProjectUpdated();
                                        rebuild[0].run();
                                    }
                                })));

                row.addView(createIconAction(context, R.drawable.ic_mtrl_content_copy,
                        isCurrent ? colorOnPrimaryContainer : colorOnSurfaceVariant, "Duplicate", v -> {
                            String newId = generateUniqueSceneId(project);
                            String newName = generateUniqueSceneName(project, nonEmpty(scene.name, "Scene") + " Copy");
                            GameScene copied = scene.copy(newId, newName);
                            project.scenes.add(copied);
                            if (callback != null) callback.onProjectUpdated();
                            rebuild[0].run();
                        }));

                boolean canDelete = project.scenes.size() > 1;
                ImageView btnDel = createIconAction(context, R.drawable.ic_mtrl_delete,
                        canDelete ? colorError : colorOutlineVariant, "Delete", v -> {
                            if (!canDelete) {
                                Toast.makeText(context, "The project must have at least 1 scene.", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            new MaterialAlertDialogBuilder(context)
                                    .setTitle("Delete Scene")
                                    .setMessage("Delete \"" + nonEmpty(scene.name, scene.id) + "\"?")
                                    .setPositiveButton("Delete", (d2, w2) -> {
                                        project.scenes.remove(scene);
                                        if (scene.id != null && scene.id.equals(project.startSceneId)) {
                                            project.startSceneId = project.scenes.get(0).id;
                                        }
                                        if (isCurrent) {
                                            activeRef[0] = project.scenes.get(0);
                                            if (callback != null) callback.onSceneSelected(activeRef[0]);
                                        }
                                        if (callback != null) callback.onProjectUpdated();
                                        rebuild[0].run();
                                    })
                                    .setNegativeButton("Cancel", null)
                                    .show();
                        });
                btnDel.setAlpha(canDelete ? 1f : 0.4f);
                row.addView(btnDel);

                card.addView(row);
                card.setOnClickListener(v -> {
                    activeRef[0] = scene;
                    if (callback != null) {
                        callback.onSceneSelected(scene);
                    }
                    if (dialogRef[0] != null) {
                        dialogRef[0].dismiss();
                    }
                });
                list.addView(card);
            }
        };

        rebuild[0].run();

        dialogRef[0] = new MaterialAlertDialogBuilder(context)
                .setTitle("Scene Manager")
                .setView(scrollView)
                .setPositiveButton("New Scene", null)
                .setNegativeButton("Close", null)
                .show();

        dialogRef[0].getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v ->
                promptText(context, "New Scene", "Level " + (project.scenes.size() + 1), "New scene name", name -> {
                    String trimmed = name.trim();
                    if (trimmed.isEmpty()) return;
                    GameScene newScene = new GameScene();
                    newScene.id = generateUniqueSceneId(project);
                    newScene.name = generateUniqueSceneName(project, trimmed);
                    newScene.backgroundColor = 0xFF1A2232;
                    newScene.gravityY = 980f;
                    newScene.objects = new ArrayList<>();
                    newScene.scripts = new ArrayList<>();
                    project.scenes.add(newScene);
                    if (project.startSceneId == null || project.startSceneId.isEmpty()) {
                        project.startSceneId = newScene.id;
                    }
                    if (callback != null) {
                        callback.onProjectUpdated();
                        callback.onSceneSelected(newScene);
                    }
                    dialogRef[0].dismiss();
                }));
    }

    public static void showHierarchyDialog(
            @NonNull Context context,
            @NonNull GameProject project,
            @NonNull GameScene currentScene,
            @Nullable HierarchyDialogCallback callback) {
        if (currentScene.objects == null) {
            currentScene.objects = new ArrayList<>();
        }
        if (project.prefabs == null) {
            project.prefabs = new ArrayList<>();
        }

        int colorSurfaceHigh = MaterialColors.getColor(context, R.attr.colorSurfaceContainerHigh, 0xFF283040);
        int colorPrimary = MaterialColors.getColor(context, R.attr.colorPrimary, 0xFF3B82F6);
        int colorOnSurface = MaterialColors.getColor(context, R.attr.colorOnSurface, 0xFFE5E7EB);
        int colorOnSurfaceVariant = MaterialColors.getColor(context, R.attr.colorOnSurfaceVariant, 0xFF94A3B8);
        int colorError = MaterialColors.getColor(context, R.attr.colorError, 0xFFEF4444);
        int colorOutlineVariant = MaterialColors.getColor(context, R.attr.colorOutlineVariant, 0x3394A3B8);

        ScrollView scrollView = new ScrollView(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(12));
        scrollView.addView(content);

        final AlertDialog[] dialogRef = new AlertDialog[1];

        Runnable[] rebuild = new Runnable[1];
        rebuild[0] = () -> {
            content.removeAllViews();

            // Section 1: Scene Objects (ordered top-most zIndex first)
            TextView objHeader = new TextView(context);
            objHeader.setText("Scene Objects (" + currentScene.objects.size() + ")");
            objHeader.setTextColor(colorPrimary);
            objHeader.setTextSize(12.5f);
            objHeader.setTypeface(null, Typeface.BOLD);
            objHeader.setPadding(0, dp(4), 0, dp(8));
            content.addView(objHeader);

            if (currentScene.objects.isEmpty()) {
                TextView empty = new TextView(context);
                empty.setText("Empty scene. Drag objects from the palette onto the stage.");
                empty.setTextColor(colorOnSurfaceVariant);
                empty.setTextSize(12f);
                empty.setPadding(dp(4), dp(4), dp(4), dp(10));
                content.addView(empty);
            } else {
                ArrayList<GameObjectBean> sorted = new ArrayList<>(currentScene.objects);
                Collections.sort(sorted, (a, b) -> Integer.compare(b.zIndex, a.zIndex));

                for (GameObjectBean obj : sorted) {
                    MaterialCardView card = new MaterialCardView(context);
                    card.setRadius(dp(10));
                    card.setCardBackgroundColor(colorSurfaceHigh);
                    card.setStrokeColor(colorOutlineVariant);
                    card.setStrokeWidth(dp(1));
                    card.setCardElevation(0f);

                    LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    cardLp.bottomMargin = dp(6);
                    card.setLayoutParams(cardLp);

                    LinearLayout row = new LinearLayout(context);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(dp(10), dp(8), dp(6), dp(8));

                    LinearLayout infoCol = new LinearLayout(context);
                    infoCol.setOrientation(LinearLayout.VERTICAL);

                    TextView tvName = new TextView(context);
                    tvName.setText(nonEmpty(obj.name, obj.id));
                    tvName.setTextColor(obj.visible ? colorOnSurface : colorOnSurfaceVariant);
                    tvName.setTextSize(13f);
                    tvName.setTypeface(null, Typeface.BOLD);
                    tvName.setSingleLine(true);
                    tvName.setEllipsize(TextUtils.TruncateAt.END);
                    infoCol.addView(tvName);

                    String layerTag = (obj.isHud || obj.isHudType()) ? "HUD" : "World";
                    TextView tvMeta = new TextView(context);
                    tvMeta.setText(String.format(Locale.US, "%s • %s • Z:%d",
                            nonEmpty(obj.type, "sprite"), layerTag, obj.zIndex));
                    tvMeta.setTextColor(colorOnSurfaceVariant);
                    tvMeta.setTextSize(11f);
                    tvMeta.setSingleLine(true);
                    tvMeta.setEllipsize(TextUtils.TruncateAt.END);
                    infoCol.addView(tvMeta);

                    row.addView(infoCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                    // Visibility toggle
                    ImageView btnVis = createIconAction(context, R.drawable.ic_mtrl_visibility,
                            obj.visible ? colorPrimary : colorOnSurfaceVariant, "Visibility", v -> {
                                obj.visible = !obj.visible;
                                if (callback != null) callback.onSceneUpdated();
                                rebuild[0].run();
                            });
                    btnVis.setAlpha(obj.visible ? 1f : 0.4f);
                    row.addView(btnVis);

                    // Lock toggle
                    ImageView btnLock = createIconAction(context, R.drawable.ic_mtrl_shield_lock,
                            obj.locked ? colorError : colorOnSurfaceVariant, "Lock", v -> {
                                obj.locked = !obj.locked;
                                if (callback != null) callback.onSceneUpdated();
                                rebuild[0].run();
                            });
                    btnLock.setAlpha(obj.locked ? 1f : 0.45f);
                    row.addView(btnLock);

                    // Z-Index +1 / -1
                    row.addView(createIconAction(context, R.drawable.ic_mtrl_arrow_up,
                            colorOnSurfaceVariant, "Bring forward", v -> {
                                obj.zIndex += 1;
                                if (callback != null) callback.onSceneUpdated();
                                rebuild[0].run();
                            }));
                    row.addView(createIconAction(context, R.drawable.ic_mtrl_arrow_down,
                            colorOnSurfaceVariant, "Send backward", v -> {
                                obj.zIndex -= 1;
                                if (callback != null) callback.onSceneUpdated();
                                rebuild[0].run();
                            }));

                    // Duplicate
                    row.addView(createIconAction(context, R.drawable.ic_mtrl_content_copy,
                            colorOnSurfaceVariant, "Duplicate", v -> {
                                GameObjectBean copy = cloneWithUniqueIdentity(currentScene, obj);
                                copy.x += 20f;
                                copy.y += 20f;
                                currentScene.objects.add(copy);
                                if (callback != null) callback.onSceneUpdated();
                                rebuild[0].run();
                            }));

                    // Delete
                    row.addView(createIconAction(context, R.drawable.ic_mtrl_delete,
                            colorError, "Delete", v -> {
                                currentScene.objects.remove(obj);
                                if (callback != null) callback.onSceneUpdated();
                                rebuild[0].run();
                            }));

                    card.addView(row);
                    card.setOnClickListener(v -> {
                        if (callback != null) {
                            callback.onSelectObject(obj);
                        }
                        if (dialogRef[0] != null) {
                            dialogRef[0].dismiss();
                        }
                    });
                    content.addView(card);
                }
            }

            // Divider & Section 2: Prefabs
            MaterialDivider divider = new MaterialDivider(context);
            LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            divLp.topMargin = dp(8);
            divLp.bottomMargin = dp(8);
            content.addView(divider, divLp);

            TextView prefabHeader = new TextView(context);
            prefabHeader.setText("Saved Prefabs (" + project.prefabs.size() + ")");
            prefabHeader.setTextColor(colorPrimary);
            prefabHeader.setTextSize(12.5f);
            prefabHeader.setTypeface(null, Typeface.BOLD);
            prefabHeader.setPadding(0, dp(2), 0, dp(8));
            content.addView(prefabHeader);

            if (project.prefabs.isEmpty()) {
                TextView emptyPrefabs = new TextView(context);
                emptyPrefabs.setText("No saved prefabs. Select an object and tap \"Save Prefab\".");
                emptyPrefabs.setTextColor(colorOnSurfaceVariant);
                emptyPrefabs.setTextSize(12f);
                emptyPrefabs.setPadding(dp(4), dp(2), dp(4), dp(6));
                content.addView(emptyPrefabs);
            } else {
                for (GameObjectBean prefab : new ArrayList<>(project.prefabs)) {
                    MaterialCardView card = new MaterialCardView(context);
                    card.setRadius(dp(10));
                    card.setCardBackgroundColor(colorSurfaceHigh);
                    card.setStrokeColor(colorOutlineVariant);
                    card.setStrokeWidth(dp(1));
                    card.setCardElevation(0f);

                    LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    cardLp.bottomMargin = dp(6);
                    card.setLayoutParams(cardLp);

                    LinearLayout row = new LinearLayout(context);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(dp(12), dp(8), dp(8), dp(8));

                    LinearLayout infoCol = new LinearLayout(context);
                    infoCol.setOrientation(LinearLayout.VERTICAL);

                    TextView tvName = new TextView(context);
                    tvName.setText(nonEmpty(prefab.name, "Prefab"));
                    tvName.setTextColor(colorOnSurface);
                    tvName.setTextSize(13f);
                    tvName.setTypeface(null, Typeface.BOLD);
                    infoCol.addView(tvName);

                    TextView tvType = new TextView(context);
                    tvType.setText(nonEmpty(prefab.type, GameObjectBean.TYPE_SPRITE));
                    tvType.setTextColor(colorOnSurfaceVariant);
                    tvType.setTextSize(11f);
                    infoCol.addView(tvType);

                    row.addView(infoCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                    MaterialButton btnSpawn = new MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                    btnSpawn.setText("Spawn");
                    btnSpawn.setTextSize(11.5f);
                    btnSpawn.setOnClickListener(v -> {
                        GameObjectBean instance = cloneWithUniqueIdentity(currentScene, prefab);
                        int vw = project.virtualWidth > 0 ? project.virtualWidth : 1280;
                        int vh = project.virtualHeight > 0 ? project.virtualHeight : 720;
                        instance.x = (vw - instance.width) * 0.5f;
                        instance.y = (vh - instance.height) * 0.5f;
                        currentScene.objects.add(instance);
                        if (callback != null) {
                            callback.onSceneUpdated();
                            callback.onSelectObject(instance);
                        }
                        if (dialogRef[0] != null) {
                            dialogRef[0].dismiss();
                        }
                    });
                    row.addView(btnSpawn);

                    row.addView(createIconAction(context, R.drawable.ic_mtrl_delete,
                            colorError, "Remove prefab", v -> {
                                project.prefabs.remove(prefab);
                                rebuild[0].run();
                            }));

                    card.addView(row);
                    content.addView(card);
                }
            }
        };

        rebuild[0].run();

        dialogRef[0] = new MaterialAlertDialogBuilder(context)
                .setTitle("Hierarchy & Prefabs")
                .setView(scrollView)
                .setNegativeButton("Close", null)
                .show();
    }

    public static void showEngineSettingsDialog(
            @NonNull Context context,
            @NonNull GameProject project,
            @Nullable Runnable onSaved) {
        int colorOnSurface = MaterialColors.getColor(context, R.attr.colorOnSurface, 0xFFE5E7EB);
        int colorOnSurfaceVariant = MaterialColors.getColor(context, R.attr.colorOnSurfaceVariant, 0xFF94A3B8);
        int colorPrimary = MaterialColors.getColor(context, R.attr.colorPrimary, 0xFF3B82F6);
        int colorPrimaryContainer = MaterialColors.getColor(context, R.attr.colorPrimaryContainer, 0xFF1D4ED8);
        int colorOnPrimaryContainer = MaterialColors.getColor(context, R.attr.colorOnPrimaryContainer, 0xFFDBEAFE);
        int colorSurfaceHigh = MaterialColors.getColor(context, R.attr.colorSurfaceContainerHigh, 0xFF283040);

        ScrollView scrollView = new ScrollView(context);
        LinearLayout form = new LinearLayout(context);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(12), dp(20), dp(8));
        scrollView.addView(form);

        TextView presetTitle = new TextView(context);
        presetTitle.setText("Resolution Presets");
        presetTitle.setTextSize(12f);
        presetTitle.setTypeface(null, Typeface.BOLD);
        presetTitle.setTextColor(colorOnSurfaceVariant);
        presetTitle.setPadding(0, 0, 0, dp(6));
        form.addView(presetTitle);

        EditText etWidth = new EditText(context);
        etWidth.setInputType(InputType.TYPE_CLASS_NUMBER);
        etWidth.setText(String.valueOf(project.virtualWidth > 0 ? project.virtualWidth : 1280));
        etWidth.setTextColor(colorOnSurface);

        EditText etHeight = new EditText(context);
        etHeight.setInputType(InputType.TYPE_CLASS_NUMBER);
        etHeight.setText(String.valueOf(project.virtualHeight > 0 ? project.virtualHeight : 720));
        etHeight.setTextColor(colorOnSurface);

        MaterialSwitch swLandscape = new MaterialSwitch(context);
        swLandscape.setText("Landscape Mode");
        swLandscape.setChecked(project.landscape);

        HorizontalScrollView hsv = new HorizontalScrollView(context);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout presetRow = new LinearLayout(context);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);

        String[] pNames = new String[]{"1280×720", "720×1280", "1920×1080", "1080×1920"};
        int[] pW = new int[]{1280, 720, 1920, 1080};
        int[] pH = new int[]{720, 1280, 1080, 1920};
        for (int i = 0; i < pNames.length; i++) {
            int w = pW[i];
            int h = pH[i];
            MaterialButton btn = new MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btn.setText(pNames[i]);
            btn.setTextSize(11.5f);
            btn.setOnClickListener(v -> {
                etWidth.setText(String.valueOf(w));
                etHeight.setText(String.valueOf(h));
                swLandscape.setChecked(w >= h);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
            lp.setMarginEnd(dp(6));
            presetRow.addView(btn, lp);
        }
        hsv.addView(presetRow);
        form.addView(hsv);

        form.addView(createLabeledInput(context, "Virtual Width (px)", etWidth, colorOnSurfaceVariant));
        form.addView(createLabeledInput(context, "Virtual Height (px)", etHeight, colorOnSurfaceVariant));

        swLandscape.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int curW = parseIntClamped(etWidth.getText().toString(), 1280, 240, 4096);
            int curH = parseIntClamped(etHeight.getText().toString(), 720, 240, 4096);
            if ((isChecked && curW < curH) || (!isChecked && curW > curH)) {
                etWidth.setText(String.valueOf(curH));
                etHeight.setText(String.valueOf(curW));
            }
        });
        LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        swLp.topMargin = dp(8);
        form.addView(swLandscape, swLp);

        // FPS Target toggle buttons (30 vs 60)
        TextView fpsTitle = new TextView(context);
        fpsTitle.setText("Frame Rate (Target FPS)");
        fpsTitle.setTextSize(12f);
        fpsTitle.setTextColor(colorOnSurfaceVariant);
        fpsTitle.setPadding(0, dp(10), 0, dp(6));
        form.addView(fpsTitle);

        final int[] chosenFps = new int[]{project.targetFps == 30 ? 30 : 60};
        LinearLayout fpsRow = new LinearLayout(context);
        fpsRow.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton btn30 = new MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        MaterialButton btn60 = new MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btn30.setText("30 FPS");
        btn60.setText("60 FPS");

        Runnable updateFpsButtons = () -> {
            boolean is30 = chosenFps[0] == 30;
            btn30.setBackgroundColor(is30 ? colorPrimaryContainer : colorSurfaceHigh);
            btn30.setTextColor(is30 ? colorOnPrimaryContainer : colorOnSurface);
            btn30.setStrokeColor(ColorStateList.valueOf(is30 ? colorPrimary : colorOnSurfaceVariant));

            btn60.setBackgroundColor(!is30 ? colorPrimaryContainer : colorSurfaceHigh);
            btn60.setTextColor(!is30 ? colorOnPrimaryContainer : colorOnSurface);
            btn60.setStrokeColor(ColorStateList.valueOf(!is30 ? colorPrimary : colorOnSurfaceVariant));
        };
        updateFpsButtons.run();

        btn30.setOnClickListener(v -> {
            chosenFps[0] = 30;
            updateFpsButtons.run();
        });
        btn60.setOnClickListener(v -> {
            chosenFps[0] = 60;
            updateFpsButtons.run();
        });

        LinearLayout.LayoutParams fpsLp1 = new LinearLayout.LayoutParams(0, dp(40), 1f);
        fpsLp1.setMarginEnd(dp(8));
        fpsRow.addView(btn30, fpsLp1);
        fpsRow.addView(btn60, new LinearLayout.LayoutParams(0, dp(40), 1f));
        form.addView(fpsRow);

        MaterialSwitch swHitboxes = new MaterialSwitch(context);
        swHitboxes.setText("Show Debug Hitboxes");
        swHitboxes.setChecked(project.showDebugHitboxes);
        LinearLayout.LayoutParams hbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hbLp.topMargin = dp(10);
        form.addView(swHitboxes, hbLp);

        new MaterialAlertDialogBuilder(context)
                .setTitle("2D Engine Settings")
                .setView(scrollView)
                .setPositiveButton("Save", (d, w) -> {
                    project.virtualWidth = parseIntClamped(etWidth.getText().toString(), 1280, 240, 4096);
                    project.virtualHeight = parseIntClamped(etHeight.getText().toString(), 720, 240, 4096);
                    project.landscape = swLandscape.isChecked();
                    project.targetFps = chosenFps[0];
                    project.showDebugHitboxes = swHitboxes.isChecked();
                    if (onSaved != null) {
                        onSaved.run();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // --- Helpers ---

    private interface TextConsumer {
        void accept(String value);
    }

    private static void promptText(Context context, String title, String initial, String hint, TextConsumer onConfirm) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(12), dp(20), dp(8));

        EditText et = new EditText(context);
        et.setText(initial);
        et.setHint(hint);
        et.setSingleLine(true);
        box.addView(et);

        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(box)
                .setPositiveButton("Save", (d, w) -> onConfirm.accept(et.getText().toString()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static View createLabeledInput(Context context, String label, EditText editText, int labelColor) {
        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        col.setLayoutParams(lp);

        TextView tv = new TextView(context);
        tv.setText(label);
        tv.setTextSize(11.5f);
        tv.setTextColor(labelColor);
        col.addView(tv);
        col.addView(editText);
        return col;
    }

    private static TextView createPillBadge(Context context, String text, int bgColor, int fgColor) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(9.5f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(fgColor);
        tv.setPadding(dp(6), dp(1), dp(6), dp(1));
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(dp(8));
        gd.setColor(bgColor);
        tv.setBackground(gd);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return tv;
    }

    private static ImageView createIconAction(Context context, @DrawableRes int iconRes, int tint, String desc, View.OnClickListener click) {
        ImageView iv = new ImageView(context);
        iv.setImageResource(iconRes);
        iv.setImageTintList(ColorStateList.valueOf(tint));
        iv.setPadding(dp(6), dp(6), dp(6), dp(6));
        iv.setContentDescription(desc);
        iv.setOnClickListener(click);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(32), dp(32)));
        return iv;
    }

    private static GameObjectBean cloneWithUniqueIdentity(GameScene scene, GameObjectBean source) {
        GameObjectBean copy = source.copy();
        int maxZ = 0;
        if (scene.objects != null) {
            for (GameObjectBean o : scene.objects) {
                if (o.zIndex > maxZ) maxZ = o.zIndex;
            }
        }
        copy.zIndex = maxZ + 1;

        String baseId = nonEmpty(source.id, "obj");
        String candId = baseId + "_1";
        int idx = 2;
        while (scene.findObjectById(candId) != null) {
            candId = baseId + "_" + idx++;
        }
        copy.id = candId;

        String baseName = nonEmpty(source.name, "obj");
        String candName = baseName;
        int nIdx = 2;
        while (scene.findObjectByName(candName) != null) {
            candName = baseName + "_" + nIdx++;
        }
        copy.name = candName;
        return copy;
    }

    private static String generateUniqueSceneId(GameProject project) {
        int idx = (project.scenes != null ? project.scenes.size() : 0) + 1;
        while (project.getSceneById("scene_" + idx) != null) {
            idx++;
        }
        return "scene_" + idx;
    }

    private static String generateUniqueSceneName(GameProject project, String desired) {
        if (project.scenes == null) return desired;
        String candidate = desired;
        int suffix = 2;
        while (sceneNameExists(project, candidate)) {
            candidate = desired + " " + suffix++;
        }
        return candidate;
    }

    private static boolean sceneNameExists(GameProject project, String name) {
        for (GameScene s : project.scenes) {
            if (s.name != null && s.name.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private static int parseIntClamped(String s, int fallback, int min, int max) {
        try {
            int val = Integer.parseInt(s.trim());
            return Math.max(min, Math.min(max, val));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String nonEmpty(@Nullable String s, String fallback) {
        return (s != null && !s.trim().isEmpty()) ? s : fallback;
    }

    private static int dp(float val) {
        return SketchwareUtil.dpToPx(val);
    }
}
