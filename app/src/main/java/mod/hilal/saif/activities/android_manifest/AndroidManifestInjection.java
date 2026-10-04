package mod.hilal.saif.activities.android_manifest;

import static pro.sketchware.utility.GsonUtils.getGson;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import a.a.a.jC;
import a.a.a.wB;
import a.a.a.yq;
import mod.hey.studios.code.SrcCodeEditor;
import mod.hey.studios.util.Helper;
import mod.hilal.saif.android_manifest.AndroidManifestInjector;
import pro.sketchware.R;
import pro.sketchware.activities.editor.view.CodeViewerActivity;
import pro.sketchware.databinding.AndroidManifestInjectionBinding;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;

/**
 * AndroidManifest manager: chooses between the generated manifest (built from the project and the
 * options listed here) and a custom manifest written by the user.
 */
public class AndroidManifestInjection extends BaseAppCompatActivity {

    private static final Pattern ACTIVITY_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*");
    private static final String APPLICATION_ATTRS = "_application_attrs";
    private static final String ALL_ACTIVITIES_ATTRS = "_apply_for_all_activities";
    private static final String PERMISSIONS = "_application_permissions";

    private AndroidManifestInjectionBinding binding;
    private String sc_id;
    private String currentActivityName;
    /** Ignores toggle callbacks while the screen sets the mode itself. */
    private boolean updatingMode;
    private boolean generatingManifest;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = AndroidManifestInjectionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        sc_id = getIntent().getStringExtra("sc_id");
        String fileName = getIntent().getStringExtra("file_name");
        currentActivityName = fileName == null ? "" : fileName.replace(".java", "");
        if (sc_id == null) {
            finish();
            return;
        }

        binding.toolbar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));
        if (!currentActivityName.isEmpty()) {
            binding.toolbar.setSubtitle(getString(R.string.manifest_manager_subtitle, currentActivityName));
        }
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_view_source) {
                showManifestSource();
                return true;
            }
            return false;
        });

        ensureApplicationTheme();
        setupOptions();
        setupModeControls();
        binding.addActivity.setOnClickListener(v -> showAddActivityDialog());
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshMode();
        refreshActivities();
    }

    // ---------------------------------------------------------------------------------------------
    // Paths

    private String injectionDir() {
        return FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/Injection/androidmanifest/";
    }

    private String attributesPath() {
        return injectionDir() + "attributes.json";
    }

    private String modePath() {
        return AndroidManifestInjector.getPathAndroidManifestMode(sc_id).getAbsolutePath();
    }

    private String customManifestPath() {
        return AndroidManifestInjector.getPathCustomAndroidManifest(sc_id).getAbsolutePath();
    }

    // ---------------------------------------------------------------------------------------------
    // Generated or custom manifest

    private void setupModeControls() {
        binding.modeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || updatingMode) return;
            if (checkedId == R.id.mode_custom) {
                switchToCustom();
            } else {
                setCustomMode(false);
                refreshMode();
            }
        });
        binding.actionEditCustom.setOnClickListener(v -> openCustomEditor());
        binding.actionLoadGenerated.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.manifest_manager_action_load_generated)
                .setMessage(R.string.manifest_manager_load_generated_message)
                .setPositiveButton(R.string.common_word_continue, (d, w) -> generateIntoCustom(false))
                .setNegativeButton(R.string.common_word_cancel, null)
                .show());
        binding.actionValidate.setOnClickListener(v -> {
            String error = AndroidManifestInjector.validateManifest(readCustomManifest());
            if (error == null) {
                SketchwareUtil.toast(getString(R.string.manifest_manager_valid));
            } else {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.manifest_manager_invalid_title)
                        .setMessage(error)
                        .setPositiveButton(R.string.common_word_close, null)
                        .show();
            }
            refreshMode();
        });
        binding.actionDiscardCustom.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.manifest_manager_action_discard)
                .setMessage(R.string.manifest_manager_discard_message)
                .setPositiveButton(R.string.common_word_delete, (d, w) -> {
                    FileUtil.writeFile(customManifestPath(), "");
                    setCustomMode(false);
                    refreshMode();
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show());
    }

    private boolean isCustomMode() {
        return "custom".equals(FileUtil.readFileIfExist(modePath()).trim());
    }

    private void setCustomMode(boolean custom) {
        FileUtil.writeFile(modePath(), custom ? "custom" : "default");
    }

    private String readCustomManifest() {
        return FileUtil.readFileIfExist(customManifestPath());
    }

    private void switchToCustom() {
        if (readCustomManifest().trim().isEmpty()) {
            // Start from the manifest Sketchware generates; custom mode is only enabled once that worked.
            generateIntoCustom(true);
        } else {
            setCustomMode(true);
            refreshMode();
        }
    }

    private void generateIntoCustom(boolean enableCustomMode) {
        if (generatingManifest) return;
        generatingManifest = true;
        binding.statusProgress.setVisibility(View.VISIBLE);
        binding.statusText.setText(R.string.manifest_manager_generating);
        new Thread(() -> {
            String manifest = null;
            Exception failure = null;
            try {
                manifest = new yq(getApplicationContext(), sc_id).getDefaultManifestSrc(jC.b(sc_id), jC.a(sc_id), jC.c(sc_id));
            } catch (Exception e) {
                failure = e;
            }
            String result = manifest;
            Exception error = failure;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                generatingManifest = false;
                binding.statusProgress.setVisibility(View.GONE);
                if (result == null || result.trim().isEmpty()) {
                    SketchwareUtil.toastError(getString(R.string.manifest_manager_generate_failed,
                            error == null ? "" : String.valueOf(error.getMessage())));
                    if (enableCustomMode) setCustomMode(false);
                } else {
                    FileUtil.writeFile(customManifestPath(), result);
                    if (enableCustomMode) setCustomMode(true);
                    SketchwareUtil.toast(getString(R.string.manifest_manager_generated_loaded));
                }
                refreshMode();
            });
        }).start();
    }

    private void openCustomEditor() {
        if (!FileUtil.isExistFile(customManifestPath())) {
            FileUtil.writeFile(customManifestPath(), "");
        }
        Intent intent = new Intent(this, SrcCodeEditor.class);
        intent.putExtra("content", customManifestPath());
        intent.putExtra("xml", "");
        intent.putExtra("title", "AndroidManifest.xml");
        startActivity(intent);
    }

    private void refreshMode() {
        if (generatingManifest) return;
        boolean custom = isCustomMode();
        updatingMode = true;
        binding.modeGroup.check(custom ? R.id.mode_custom : R.id.mode_generated);
        updatingMode = false;

        binding.customSection.setVisibility(custom ? View.VISIBLE : View.GONE);
        binding.generatedIgnoredNote.setVisibility(custom ? View.VISIBLE : View.GONE);
        binding.optionsCard.setAlpha(custom ? 0.55f : 1f);

        if (!custom) {
            setStatus(R.drawable.ic_mtrl_info, R.color.chat_accent, getString(R.string.manifest_manager_status_generated));
            return;
        }
        String xml = readCustomManifest();
        binding.customPreview.setText(preview(xml));
        String error = AndroidManifestInjector.validateManifest(xml);
        if (error == null) {
            int lines = xml.isEmpty() ? 0 : xml.split("\n", -1).length;
            setStatus(R.drawable.ic_mtrl_shield_check, R.color.chat_accent, getString(R.string.manifest_manager_status_custom_valid, lines));
        } else {
            setStatus(R.drawable.ic_mtrl_info, R.color.chat_error, getString(R.string.manifest_manager_status_custom_invalid, error));
        }
    }

    private void setStatus(@DrawableRes int icon, int colorRes, CharSequence text) {
        binding.statusIcon.setImageResource(icon);
        binding.statusIcon.setColorFilter(ContextCompat.getColor(this, colorRes));
        binding.statusText.setText(text);
    }

    private static String preview(String xml) {
        String[] lines = xml.split("\n");
        StringBuilder out = new StringBuilder();
        int count = Math.min(lines.length, 14);
        for (int i = 0; i < count; i++) {
            if (i > 0) out.append('\n');
            out.append(lines[i]);
        }
        if (lines.length > count) out.append("\n…");
        return out.toString();
    }

    private void showManifestSource() {
        k();
        new Thread(() -> {
            String source = new yq(getApplicationContext(), sc_id).getFileSrc("AndroidManifest.xml", jC.b(sc_id), jC.a(sc_id), jC.c(sc_id));
            runOnUiThread(() -> {
                if (isFinishing()) return;
                h();
                Intent intent = new Intent(this, CodeViewerActivity.class);
                intent.putExtra("code", !source.isEmpty() ? source : getString(R.string.manifest_manager_source_failed));
                intent.putExtra("sc_id", sc_id);
                intent.putExtra("scheme", CodeViewerActivity.SCHEME_XML);
                startActivity(intent);
            });
        }).start();
    }

    // ---------------------------------------------------------------------------------------------
    // Options for the generated manifest

    private void setupOptions() {
        binding.options.removeAllViews();
        addOption(R.drawable.ic_mtrl_settings_applications, R.string.manifest_manager_option_application,
                R.string.manifest_manager_option_application_summary, v -> openDetails("application", currentActivityName));
        addOption(R.drawable.ic_mtrl_shield_check, R.string.manifest_manager_option_permissions,
                R.string.manifest_manager_option_permissions_summary, v -> openDetails("permission", currentActivityName));
        addOption(R.drawable.ic_mtrl_login, R.string.manifest_manager_option_launcher,
                R.string.manifest_manager_option_launcher_summary, v -> showLauncherActivityDialog());
        addOption(R.drawable.ic_mtrl_frame_source, R.string.manifest_manager_option_all_activities,
                R.string.manifest_manager_option_all_activities_summary, v -> openDetails("all", currentActivityName));
        addOption(R.drawable.ic_mtrl_component, R.string.manifest_manager_option_components,
                R.string.manifest_manager_option_components_summary, v -> openAppComponents());
    }

    private void addOption(@DrawableRes int icon, int title, int summary, View.OnClickListener listener) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_manifest_row, binding.options, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        ((TextView) row.findViewById(R.id.subtitle)).setText(summary);
        row.setOnClickListener(listener);
        binding.options.addView(row);
    }

    private void openDetails(String type, String activityName) {
        Intent intent = new Intent(getApplicationContext(), AndroidManifestInjectionDetails.class);
        intent.putExtra("sc_id", sc_id);
        intent.putExtra("file_name", activityName);
        intent.putExtra("type", type);
        startActivity(intent);
    }

    private void openAppComponents() {
        String path = AndroidManifestInjector.getPathAndroidManifestAppComponents(sc_id).getAbsolutePath();
        if (!FileUtil.isExistFile(path)) FileUtil.writeFile(path, "");
        Intent intent = new Intent(getApplicationContext(), SrcCodeEditor.class);
        intent.putExtra("content", path);
        intent.putExtra("xml", "");
        intent.putExtra("disableHeader", "");
        intent.putExtra("title", "app_components.xml");
        startActivity(intent);
    }

    private void showLauncherActivityDialog() {
        View view = wB.a(this, R.layout.dialog_add_custom_activity);
        TextInputLayout inputLayout = view.findViewById(R.id.activity_name_input_layout);
        TextInputEditText input = view.findViewById(R.id.activity_name_input);
        input.setText(AndroidManifestInjector.getLauncherActivity(sc_id));
        var dialog = new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_mtrl_lifecycle)
                .setTitle(Helper.getResString(R.string.change_launcher_activity_dialog_title))
                .setView(view)
                .setPositiveButton(Helper.getResString(R.string.common_word_save), null)
                .setNegativeButton(Helper.getResString(R.string.common_word_cancel), null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = Helper.getText(input).trim();
            if (!ACTIVITY_NAME.matcher(name).matches()) {
                inputLayout.setError(getString(R.string.manifest_manager_invalid_activity_name));
                return;
            }
            AndroidManifestInjector.setLauncherActivity(sc_id, name);
            SketchwareUtil.toast(getString(R.string.common_word_saved));
            dialog.dismiss();
        }));
        dialog.show();
    }

    /** Older projects got no application theme; the generated manifest needs one. */
    private void ensureApplicationTheme() {
        String path = attributesPath();
        if (!FileUtil.isExistFile(path)) return;
        ArrayList<HashMap<String, Object>> data = readAttributes();
        for (HashMap<String, Object> item : data) {
            if (APPLICATION_ATTRS.equals(item.get("name"))
                    && Objects.toString(item.get("value"), "").contains("android:theme")) {
                return;
            }
        }
        HashMap<String, Object> item = new HashMap<>();
        item.put("name", APPLICATION_ATTRS);
        item.put("value", "android:theme=\"@style/AppTheme\"");
        data.add(item);
        FileUtil.writeFile(path, getGson().toJson(data));
    }

    // ---------------------------------------------------------------------------------------------
    // Activities declared through the manifest manager

    private ArrayList<HashMap<String, Object>> readAttributes() {
        String path = attributesPath();
        if (!FileUtil.isExistFile(path)) return new ArrayList<>();
        try {
            ArrayList<HashMap<String, Object>> data = getGson().fromJson(FileUtil.readFile(path), Helper.TYPE_MAP_LIST);
            return data == null ? new ArrayList<>() : data;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private Set<String> declaredActivities() {
        Set<String> names = new LinkedHashSet<>();
        for (HashMap<String, Object> item : readAttributes()) {
            String name = Objects.toString(item.get("name"), "");
            if (!name.isEmpty() && !APPLICATION_ATTRS.equals(name) && !ALL_ACTIVITIES_ATTRS.equals(name) && !PERMISSIONS.equals(name)) {
                names.add(name);
            }
        }
        return names;
    }

    private void refreshActivities() {
        binding.activitiesList.removeAllViews();
        Set<String> activities = declaredActivities();
        for (String name : activities) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_manifest_row, binding.activitiesList, false);
            ((ImageView) row.findViewById(R.id.icon)).setImageResource(R.drawable.ic_mtrl_lifecycle);
            ((TextView) row.findViewById(R.id.title)).setText(name);
            ((TextView) row.findViewById(R.id.subtitle)).setText(R.string.manifest_manager_activity_summary);
            ImageButton delete = row.findViewById(R.id.action);
            delete.setVisibility(View.VISIBLE);
            delete.setImageResource(R.drawable.ic_p2p_delete);
            delete.setContentDescription(getString(R.string.common_word_delete));
            delete.setOnClickListener(v -> confirmDeleteActivity(name));
            row.findViewById(R.id.chevron).setVisibility(View.GONE);
            row.setOnClickListener(v -> openDetails("activity", name));
            binding.activitiesList.addView(row);
        }
        binding.activitiesEmpty.setVisibility(activities.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showAddActivityDialog() {
        View view = wB.a(this, R.layout.dialog_add_custom_activity);
        TextInputLayout inputLayout = view.findViewById(R.id.activity_name_input_layout);
        TextInputEditText input = view.findViewById(R.id.activity_name_input);
        input.setText(currentActivityName);
        var dialog = new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_mtrl_add)
                .setTitle(Helper.getResString(R.string.common_word_add_activtiy))
                .setView(view)
                .setPositiveButton(Helper.getResString(R.string.common_word_save), null)
                .setNegativeButton(Helper.getResString(R.string.common_word_cancel), null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = Helper.getText(input).trim();
            if (!ACTIVITY_NAME.matcher(name).matches()) {
                inputLayout.setError(getString(R.string.manifest_manager_invalid_activity_name));
                return;
            }
            if (declaredActivities().contains(name)) {
                inputLayout.setError(getString(R.string.manifest_manager_activity_exists));
                return;
            }
            addNewActivity(name);
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void addNewActivity(String componentName) {
        ArrayList<HashMap<String, Object>> data = readAttributes();
        for (String value : new String[]{
                "android:configChanges=\"orientation|screenSize|keyboardHidden|smallestScreenSize|screenLayout\"",
                "android:hardwareAccelerated=\"true\"",
                "android:supportsPictureInPicture=\"true\"",
                "android:screenOrientation=\"portrait\"",
                "android:theme=\"@style/AppTheme\"",
                "android:windowSoftInputMode=\"stateHidden\""}) {
            HashMap<String, Object> item = new HashMap<>();
            item.put("name", componentName);
            item.put("value", value);
            data.add(item);
        }
        FileUtil.writeFile(attributesPath(), getGson().toJson(data));
        refreshActivities();
        SketchwareUtil.toast(getString(R.string.manifest_manager_activity_added));
    }

    private void confirmDeleteActivity(String name) {
        new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.icon_delete)
                .setTitle(Helper.getResString(R.string.delete_custom_activity_dialog_title))
                .setMessage(Helper.getResString(R.string.delete_custom_activity_dialog_message).replace("%1$s", name))
                .setPositiveButton(Helper.getResString(R.string.common_word_delete), (d, which) -> deleteActivity(name))
                .setNegativeButton(Helper.getResString(R.string.common_word_cancel), null)
                .show();
    }

    private void deleteActivity(String activityName) {
        ArrayList<HashMap<String, Object>> data = readAttributes();
        data.removeIf(item -> activityName.equals(item.get("name")));
        FileUtil.writeFile(attributesPath(), getGson().toJson(data));
        removeComponents(activityName);
        refreshActivities();
        SketchwareUtil.toast(getString(R.string.manifest_manager_activity_removed));
    }

    private void removeComponents(String activityName) {
        String path = AndroidManifestInjector.getPathAndroidManifestActivitiesComponents(sc_id).getAbsolutePath();
        if (!FileUtil.isExistFile(path)) return;
        try {
            ArrayList<HashMap<String, Object>> data = getGson().fromJson(FileUtil.readFile(path), Helper.TYPE_MAP_LIST);
            if (data == null) return;
            data.removeIf(item -> activityName.equals(item.get("name")));
            FileUtil.writeFile(path, getGson().toJson(data));
        } catch (Exception ignored) {
        }
    }
}
