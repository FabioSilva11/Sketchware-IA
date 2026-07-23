package com.besome.sketch.projects;

import static mod.hey.studios.util.ProjectFile.getDefaultColor;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;

import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.transition.AutoTransition;
import androidx.transition.TransitionManager;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.besome.sketch.lib.ui.ColorPickerDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipInputStream;

import a.a.a.GB;
import a.a.a.MA;
import a.a.a.VB;
import a.a.a.lC;
import a.a.a.mB;
import a.a.a.nB;
import a.a.a.oB;
import a.a.a.wB;
import a.a.a.wq;
import a.a.a.yB;
import mod.hey.studios.project.AndroidStudioProjectSettingsDialog;
import mod.hey.studios.project.ProjectSettings;
import mod.hey.studios.util.Helper;
import mod.hey.studios.util.ProjectMapUtils;
import mod.hey.studios.util.ProjectFile;
import mod.hilal.saif.activities.tools.ConfigActivity;
import pro.sketchware.R;
import pro.sketchware.activities.studio.AndroidStudioProjectActivity;
import pro.sketchware.control.VersionDialog;
import pro.sketchware.databinding.MyprojectSettingBinding;
import pro.sketchware.lib.validator.AppNameValidator;
import pro.sketchware.lib.validator.PackageNameValidator;
import pro.sketchware.utility.AdManager;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.TranslationFunction;

public class MyProjectSettingActivity extends BaseAppCompatActivity implements View.OnClickListener {

    public static final String EXTRA_PROJECT_KIND = "project_kind";
    private static final String TEMPLATE_ASSET_PATH = "template_studio/androidx.zip";
    private static final String PACKAGE_NAME_PLACEHOLDER = "$package_name$";
    private static final String PROJECT_NAME_PLACEHOLDER = "$project_name$";
    private static final int REQUEST_CODE_CREATE_ICON = 200212;
    private static final int REQUEST_CODE_PICK_ICON = 200213;
    private static final int REQUEST_CODE_PICK_CROPPED_ICON = 200214;
    private final String[] themeColorKeys = {"color_accent", "color_primary", "color_primary_dark", "color_control_highlight", "color_control_normal"};
    private final String[] themeColorLabels = {"colorAccent", "colorPrimary", "colorPrimaryDark", "colorControlHighlight", "colorControlNormal"};
    private final int[] projectThemeColors = new int[themeColorKeys.length];
    public MyprojectSettingBinding binding;
    private PackageNameValidator projectPackageNameValidator;
    private VB projectNameValidator;
    private AppNameValidator projectAppNameValidator;
    private boolean projectHasCustomIcon = false;
    private boolean updatingExistingProject = false;
    private int projectVersionCode = 1;
    private int projectVersionNameFirstPart;
    private int projectVersionNameSecondPart;
    private boolean shownPackageNameChangeWarning;
    private boolean isIconAdaptive;
    private Bitmap icon;
    private Uri pendingCropOutputUri;
    private String sc_id;
    private String projectKind = lC.PROJECT_KIND_SKETCHWARE;
    private String originalPackageName;
    private String saveError;

    private ThemePresetAdapter themePresetAdapter;

    public static void saveBitmapTo(Bitmap bitmap, String path) {
        File parent = new File(path).getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (FileOutputStream fileOutputStream = new FileOutputStream(path)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream);
            fileOutputStream.flush();
        } catch (IOException ignored) {
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = MyprojectSettingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(arg0 -> onBackPressed());

        if (!isStoragePermissionGranted()) finish();

        sc_id = getIntent().getStringExtra("sc_id");
        updatingExistingProject = getIntent().getBooleanExtra("is_update", false);
        projectKind = normalizeProjectKind(getIntent().getStringExtra(EXTRA_PROJECT_KIND));

        binding.verCode.setSelected(true);
        binding.verName.setSelected(true);


        binding.appIconLayout.setOnClickListener(this);
        binding.verCodeHolder.setOnClickListener(this);
        binding.verNameHolder.setOnClickListener(this);
        binding.imgThemeColorHelp.setOnClickListener(this);
        binding.okButton.setOnClickListener(this);
        binding.cancel.setOnClickListener(this);

        initializeThemePresets();

        binding.tilAppName.setHint(Helper.getResString(R.string.myprojects_settings_hint_enter_application_name));
        binding.tilPackageName.setHint(Helper.getResString(R.string.myprojects_settings_hint_enter_package_name));
        binding.tilProjectName.setHint(Helper.getResString(R.string.myprojects_settings_hint_enter_project_name));

        projectAppNameValidator = new AppNameValidator(getApplicationContext(), binding.tilAppName);
        projectPackageNameValidator = new PackageNameValidator(getApplicationContext(), binding.tilPackageName);
        projectNameValidator = new VB(getApplicationContext(), binding.tilProjectName);
        binding.tilPackageName.setOnFocusChangeListener((v, hasFocus) -> {
            if (isPackageNameLocked()) {
                return;
            }
            if (hasFocus) {
                if (!shownPackageNameChangeWarning && !Helper.getText((EditText) v).trim().contains("com.my.newproject")) {
                    showPackageNameChangeWarning();
                }
            }
        });

        projectThemeColors[0] = getDefaultColor(ProjectFile.COLOR_ACCENT);
        projectThemeColors[1] = getDefaultColor(ProjectFile.COLOR_PRIMARY);
        projectThemeColors[2] = getDefaultColor(ProjectFile.COLOR_PRIMARY_DARK);
        projectThemeColors[3] = getDefaultColor(ProjectFile.COLOR_CONTROL_HIGHLIGHT);
        projectThemeColors[4] = getDefaultColor(ProjectFile.COLOR_CONTROL_NORMAL);

        for (int i = 0; i < themeColorKeys.length; i++) {
            ThemeColorView colorView = new ThemeColorView(this, i);
            colorView.name.setText(themeColorLabels[i]);
            colorView.color.setBackgroundColor(Color.WHITE);
            binding.layoutThemeColors.addView(colorView);
            colorView.setOnClickListener(v -> {
                if (!mB.a()) {
                    pickColor(v, (Integer) v.getTag());
                }
            });
        }
        if (updatingExistingProject) {
            /* Set the dialog's title & save button label */
            binding.toolbar.setTitle("Project Settings");
            HashMap<String, Object> metadata = lC.b(sc_id);
            projectKind = normalizeProjectKind(yB.c(metadata, lC.PROJECT_KIND_KEY));
            originalPackageName = yB.c(metadata, "my_sc_pkg_name");
            binding.etPackageName.setText(yB.c(metadata, "my_sc_pkg_name"));
            binding.etProjectName.setText(yB.c(metadata, "my_ws_name"));
            binding.etAppName.setText(yB.c(metadata, "my_app_name"));
            binding.okButton.setText("Save changes");
            projectVersionCode = parseInt(yB.c(metadata, "sc_ver_code"), 1);
            parseVersion(yB.c(metadata, "sc_ver_name"));
            binding.verCode.setText(yB.c(metadata, "sc_ver_code"));
            binding.verName.setText(yB.c(metadata, "sc_ver_name"));
            projectHasCustomIcon = ProjectMapUtils.getBoolean(metadata, "custom_icon");
            if (projectHasCustomIcon) {
                binding.appIcon.setImageURI(FileProvider.getUriForFile(getApplicationContext(), getApplicationContext().getPackageName() + ".provider", getCustomIcon()));
            }

            for (int i = 0; i < themeColorKeys.length; i++) {
                projectThemeColors[i] = yB.a(metadata, themeColorKeys[i], projectThemeColors[i]);
            }
        } else {
            /* Set the dialog's title & create button label */
            String newProjectName = getIntent().getStringExtra("my_ws_name");
            String newProjectPackageName = getIntent().getStringExtra("my_sc_pkg_name");
            if (sc_id == null || sc_id.isEmpty()) {
                sc_id = lC.b();
                newProjectName = lC.c();
                newProjectPackageName = "com.my." + newProjectName.toLowerCase();
            }
            if (isAndroidStudioProject()) {
                binding.toolbar.setTitle("New Android Studio Project");
            }
            originalPackageName = newProjectPackageName;
            binding.etPackageName.setText(newProjectPackageName);
            binding.etProjectName.setText(newProjectName);
            binding.etAppName.setText(getIntent().getStringExtra("my_app_name"));

            String newProjectVersionCode = getIntent().getStringExtra("sc_ver_code");
            String newProjectVersionName = getIntent().getStringExtra("sc_ver_name");
            if (newProjectVersionCode == null || newProjectVersionCode.isEmpty()) {
                newProjectVersionCode = "1";
            }
            if (newProjectVersionName == null || newProjectVersionName.isEmpty()) {
                newProjectVersionName = "1.0";
            }
            projectVersionCode = parseInt(newProjectVersionCode, 1);
            parseVersion(newProjectVersionName);
            binding.verCode.setText(newProjectVersionCode);
            binding.verName.setText(newProjectVersionName);
            projectHasCustomIcon = getIntent().getBooleanExtra("custom_icon", false);
            if (projectHasCustomIcon) {
                binding.appIcon.setImageURI(FileProvider.getUriForFile(getApplicationContext(), getApplicationContext().getPackageName() + ".provider", getCustomIcon()));
            }
        }
        applyPackageNameLockIfNeeded();
        syncThemeColors();
        AdManager.loadBanner(this, binding.adContainer, "ca-app-pub-6598765502914364/2478153475");
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_CREATE_ICON && resultCode == RESULT_OK && data != null) {
            if (data.getParcelableExtra("appIco") != null) {
                icon = data.getParcelableExtra("appIco");

                isIconAdaptive = data.getBooleanExtra("isIconAdaptive", false);
                binding.appIcon.setImageBitmap(icon);
                projectHasCustomIcon = true;
            }
        } else if (requestCode == REQUEST_CODE_PICK_ICON && resultCode == RESULT_OK && data != null && data.getData() != null) {
            applySelectedIcon(data.getData());
        } else if (requestCode == REQUEST_CODE_PICK_CROPPED_ICON && resultCode == RESULT_OK) {
            applyCroppedIconResult(data);
        }

    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.app_icon_layout) {
            showAppIconOptions();
        } else if (id == R.id.ok_button) {
            mB.a(v);
            if (isInputValid()) {
                if (icon != null) saveBitmapTo(icon, getCustomIconPath());
                new SaveProjectAsyncTask(getApplicationContext()).execute();
            }
        } else if (id == R.id.cancel) {
            finish();
        } else if (id == R.id.img_theme_color_help) {
            animateLayoutChanges(binding.getRoot());
            if (binding.imgColorGuide.getVisibility() == View.VISIBLE) {
                binding.imgColorGuide.setVisibility(View.GONE);
            } else {
                binding.imgColorGuide.setVisibility(View.VISIBLE);
            }
        } else if (id == R.id.ver_code_holder || id == R.id.ver_name_holder) {
            if (ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_USE_NEW_VERSION_CONTROL)) {
                new VersionDialog(this).show();
            } else {
                showOldVersionControlDialog();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isStoragePermissionGranted()) {
            finish();
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        oB oBVar = new oB();
        oBVar.f(wq.e() + File.separator + sc_id);
        oBVar.f(wq.g() + File.separator + sc_id);
        oBVar.f(wq.t() + File.separator + sc_id);
        oBVar.f(wq.d() + File.separator + sc_id);
        File o = getCustomIcon();
        if (!o.exists()) {
            try {
                o.createNewFile();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void showOldVersionControlDialog() {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setIcon(R.drawable.numbers_48);
        dialog.setTitle(Helper.getResString(R.string.myprojects_settings_version_control_title));
        View view = wB.a(getApplicationContext(), R.layout.property_popup_version_control);
        ((TextView) view.findViewById(R.id.tv_code)).setText(Helper.getResString(R.string.myprojects_settings_version_control_title_code));
        ((TextView) view.findViewById(R.id.tv_name)).setText(Helper.getResString(R.string.myprojects_settings_version_control_title_name));

        NumberPicker versionCodePicker = view.findViewById(R.id.version_code);
        NumberPicker versionNameFirstPartPicker = view.findViewById(R.id.version_name1);
        NumberPicker versionNameSecondPartPicker = view.findViewById(R.id.version_name2);

        versionCodePicker.setWrapSelectorWheel(false);
        versionNameFirstPartPicker.setWrapSelectorWheel(false);
        versionNameSecondPartPicker.setWrapSelectorWheel(false);

        int versionCode = Integer.parseInt(Helper.getText(binding.verCode));
        int versionCodeMinimum = versionCode - 5;
        int versionNameFirstPartMinimum = 1;
        if (versionCodeMinimum <= 0) {
            versionCodeMinimum = 1;
        }
        versionCodePicker.setMinValue(versionCodeMinimum);
        versionCodePicker.setMaxValue(versionCode + 5);
        versionCodePicker.setValue(versionCode);

        String[] split = Helper.getText(binding.verName).split("\\.");
        AtomicInteger projectNewVersionNameFirstPart = new AtomicInteger(parseInt(split[0], 1));
        AtomicInteger projectNewVersionNameSecondPart = new AtomicInteger(parseInt(split[1], 0));
        if (projectNewVersionNameFirstPart.get() - 5 > 0) {
            versionNameFirstPartMinimum = projectNewVersionNameFirstPart.get() - 5;
        }
        versionNameFirstPartPicker.setMinValue(versionNameFirstPartMinimum);
        versionNameFirstPartPicker.setMaxValue(projectNewVersionNameFirstPart.get() + 5);
        versionNameFirstPartPicker.setValue(projectNewVersionNameFirstPart.get());

        versionNameSecondPartPicker.setMinValue(Math.max(projectNewVersionNameSecondPart.get() - 20, 0));
        versionNameSecondPartPicker.setMaxValue(projectNewVersionNameSecondPart.get() + 20);
        versionNameSecondPartPicker.setValue(projectNewVersionNameSecondPart.get());
        dialog.setView(view);

        versionCodePicker.setOnValueChangedListener((picker, oldVal, newVal) -> {
            if (oldVal > newVal && newVal < projectVersionCode) {
                picker.setValue(projectVersionCode);
            }
        });
        versionNameFirstPartPicker.setOnValueChangedListener((picker, oldVal, newVal) -> {
            projectNewVersionNameFirstPart.set(newVal);
            if (oldVal > newVal) {
                if (newVal < projectVersionNameFirstPart) {
                    versionCodePicker.setValue(projectVersionNameFirstPart);
                }
                if (projectNewVersionNameFirstPart.get() == projectVersionNameFirstPart || projectNewVersionNameSecondPart.get() <= projectVersionNameSecondPart) {
                    versionNameSecondPartPicker.setValue(projectVersionNameSecondPart);
                    projectNewVersionNameSecondPart.set(projectVersionNameSecondPart);
                }
            }
        });
        versionNameSecondPartPicker.setOnValueChangedListener((picker, oldVal, newVal) -> {
            projectNewVersionNameSecondPart.set(newVal);
            if (oldVal > newVal && newVal < projectVersionNameSecondPart && projectNewVersionNameFirstPart.get() < projectVersionNameFirstPart) {
                picker.setValue(projectVersionNameSecondPart);
            }
        });
        dialog.setPositiveButton(Helper.getResString(R.string.common_word_save), (v, which) -> {
            if (!mB.a()) {
                binding.verCode.setText(String.valueOf(versionCodePicker.getValue()));
                binding.verName.setText(projectNewVersionNameFirstPart + "." + projectNewVersionNameSecondPart);
                v.dismiss();
            }
        });
        dialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel), null);
        dialog.show();
    }

    private void syncThemeColors() {
        for (int i = 0; i < projectThemeColors.length; i++) {
            ((ThemeColorView) binding.layoutThemeColors.getChildAt(i)).color.setBackgroundColor(projectThemeColors[i]);
        }
    }

    private void parseVersion(String toParse) {
        try {
            String[] split = toParse.split("\\.");
            projectVersionNameFirstPart = parseInt(split[0], 1);
            projectVersionNameSecondPart = parseInt(split[1], 0);
        } catch (Exception ignored) {
        }
    }

    private void pickColor(View anchorView, int colorIndex) {
        ColorPickerDialog colorPickerDialog = new ColorPickerDialog(this, projectThemeColors[colorIndex], false, false);
        colorPickerDialog.a(new ColorPickerDialog.b() {
            @Override
            public void a(int var1) {
                projectThemeColors[colorIndex] = var1;
                syncThemeColors();
                themePresetAdapter.unselectThePreviousTheme(-1);
            }

            @Override
            public void a(String var1, int var2) {
                projectThemeColors[colorIndex] = var2;
                syncThemeColors();
                themePresetAdapter.unselectThePreviousTheme(-1);
            }
        });
        colorPickerDialog.showAtLocation(anchorView, Gravity.CENTER, 0, 0);
    }

    private void showResetIconConfirmation() {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setTitle(Helper.getResString(R.string.common_word_settings));
        dialog.setIcon(R.drawable.default_icon);
        dialog.setMessage(Helper.getResString(R.string.myprojects_settings_confirm_reset_icon));
        dialog.setPositiveButton(Helper.getResString(R.string.common_word_reset), (v, which) -> {
            binding.appIcon.setImageResource(R.drawable.default_icon);
            projectHasCustomIcon = false;
            v.dismiss();
        });
        dialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel), null);
        dialog.show();
    }

    private void showAppIconOptions() {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setTitle(Helper.getResString(R.string.myprojects_settings_context_menu_title_choose));
        dialog.setItems(new String[]{
                Helper.getResString(R.string.myprojects_settings_context_menu_title_choose_gallery),
                Helper.getResString(R.string.myprojects_settings_context_menu_title_choose_gallery_with_crop),
                Helper.getResString(R.string.myprojects_settings_context_menu_title_choose_gallery_default)
        }, (d, which) -> {
            switch (which) {
                case 0 -> pickCustomIcon(REQUEST_CODE_PICK_ICON);
                case 1 -> pickAndCropCustomIcon();
                case 2 -> showResetIconConfirmation();
            }
        });
        dialog.show();
    }

    private void pickCustomIcon(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, Helper.getResString(R.string.common_word_choose)), requestCode);
    }

    private void pickAndCropCustomIcon() {
        File cropOutputFile = getTempIconFile("cropped_icon.png");
        File parent = cropOutputFile.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        pendingCropOutputUri = FileProvider.getUriForFile(
                getApplicationContext(),
                getApplicationContext().getPackageName() + ".provider",
                cropOutputFile
        );

        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.putExtra("crop", "true");
        intent.putExtra("aspectX", 1);
        intent.putExtra("aspectY", 1);
        intent.putExtra("outputX", 512);
        intent.putExtra("outputY", 512);
        intent.putExtra("scale", true);
        intent.putExtra("scaleUpIfNeeded", true);
        intent.putExtra("output", pendingCropOutputUri);
        intent.putExtra("outputFormat", Bitmap.CompressFormat.PNG.toString());
        intent.putExtra("return-data", true);
        startActivityForResult(Intent.createChooser(intent, Helper.getResString(R.string.common_word_choose)), REQUEST_CODE_PICK_CROPPED_ICON);
    }

    private void applySelectedIcon(Uri uri) {
        try {
            applySelectedIcon(MediaStore.Images.Media.getBitmap(getContentResolver(), uri));
        } catch (Exception e) {
            e.printStackTrace();
            SketchwareUtil.toastError(Helper.getResString(R.string.common_error_an_error_occurred));
        }
    }

    private void applyCroppedIconResult(Intent data) {
        Bitmap bitmap = null;
        try {
            if (data != null && data.getExtras() != null) {
                bitmap = data.getExtras().getParcelable("data");
            }
            if (bitmap == null && pendingCropOutputUri != null && getTempIconFile("cropped_icon.png").exists()) {
                bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), pendingCropOutputUri);
            }
            if (bitmap == null && data != null && data.getData() != null) {
                bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), data.getData());
            }
            applySelectedIcon(bitmap);
        } catch (Exception e) {
            e.printStackTrace();
            SketchwareUtil.toastError(Helper.getResString(R.string.common_error_an_error_occurred));
        }
    }

    private void applySelectedIcon(Bitmap bitmap) {
        if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            SketchwareUtil.toastError(Helper.getResString(R.string.common_error_an_error_occurred));
            return;
        }
        icon = toSquareIconBitmap(bitmap);
        binding.appIcon.setImageBitmap(icon);
        projectHasCustomIcon = true;
        isIconAdaptive = false;
    }

    private Bitmap toSquareIconBitmap(Bitmap source) {
        int size = Math.min(source.getWidth(), source.getHeight());
        int left = Math.max(0, (source.getWidth() - size) / 2);
        int top = Math.max(0, (source.getHeight() - size) / 2);
        Bitmap square = Bitmap.createBitmap(source, left, top, size, size);
        return Bitmap.createScaledBitmap(square, 512, 512, true);
    }

    private File getCustomIcon() {
        return new File(getCustomIconPath());
    }

    private String getCustomIconPath() {
        return wq.e() + File.separator + sc_id + File.separator + "icon.png";
    }

    private String getTempIconsFolderPath(String foldername) {
        return wq.e() + File.separator + sc_id + File.separator + foldername;
    }

    private File getTempIconFile(String fileName) {
        return new File(wq.e() + File.separator + sc_id + File.separator + fileName);
    }

    private String getIconsFolderPath() {
        return wq.e() + File.separator + sc_id + File.separator + "mipmaps" + File.separator;
    }

    private boolean isInputValid() {
        return (isPackageNameLocked() || projectPackageNameValidator.b())
                && projectNameValidator.b()
                && projectAppNameValidator.b();
    }

    private void showPackageNameChangeWarning() {
        shownPackageNameChangeWarning = true;
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setTitle(Helper.getResString(R.string.common_word_warning));
        dialog.setIcon(R.drawable.break_warning_96_red);
        dialog.setMessage(Helper.getResString(R.string.myprojects_settings_message_package_rename));
        dialog.setPositiveButton(Helper.getResString(R.string.common_word_ok), null);
        dialog.show();
    }

    private int parseInt(String input, int fallback) {
        try {
            return Integer.parseInt(input);
        } catch (Exception unused) {
            return fallback;
        }
    }

    private void animateLayoutChanges(View view) {
        var autoTransition = new AutoTransition();
        autoTransition.setDuration((short) 200);
        TransitionManager.beginDelayedTransition((ViewGroup) view, autoTransition);
    }

    private void initializeThemePresets() {
        List<ThemeManager.ThemePreset> themePresets = Arrays.asList(ThemeManager.getThemePresets());

        themePresetAdapter = new ThemePresetAdapter(this, themePresets, (theme, position) -> applyTheme(theme));

        binding.btnGenerateRandomTheme.setOnClickListener(v -> {
            themePresetAdapter.unselectThePreviousTheme(-1);
            generateRandomTheme();
        });

        binding.btnReset.setOnClickListener(v -> {
            applyTheme(ThemeManager.getDefault());
        });

        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        binding.layoutThemePresets.setLayoutManager(layoutManager);

        binding.layoutThemePresets.setAdapter(themePresetAdapter);
    }

    private void generateRandomTheme() {
        ThemeManager.ThemePreset randomTheme = ThemeManager.generateRandomTheme();
        applyTheme(randomTheme);

        SketchwareUtil.toast(Helper.getResString(R.string.theme_random_generated));
    }

    private void applyTheme(ThemeManager.ThemePreset theme) {
        projectThemeColors[0] = theme.colorAccent;
        projectThemeColors[1] = theme.colorPrimary;
        projectThemeColors[2] = theme.colorPrimaryDark;
        projectThemeColors[3] = theme.colorControlHighlight;
        projectThemeColors[4] = theme.colorControlNormal;

        syncThemeColors();
    }

    private String normalizeProjectKind(String value) {
        if (lC.PROJECT_KIND_ANDROID_STUDIO.equals(value)) {
            return lC.PROJECT_KIND_ANDROID_STUDIO;
        }
        return lC.PROJECT_KIND_SKETCHWARE;
    }

    private boolean isAndroidStudioProject() {
        return lC.PROJECT_KIND_ANDROID_STUDIO.equals(projectKind);
    }

    private boolean isPackageNameLocked() {
        return updatingExistingProject && isAndroidStudioProject();
    }

    private void applyPackageNameLockIfNeeded() {
        if (!isPackageNameLocked()) {
            return;
        }
        binding.etPackageName.setText(originalPackageName);
        binding.etPackageName.setEnabled(false);
        binding.etPackageName.setFocusable(false);
        binding.etPackageName.setFocusableInTouchMode(false);
        binding.tilPackageName.setEnabled(false);
        binding.tilPackageName.setHelperText("Package locked after Android Studio project creation.");
    }

    private String getPackageNameForSave() {
        if (isPackageNameLocked() && originalPackageName != null && !originalPackageName.isEmpty()) {
            return originalPackageName;
        }
        return Helper.getText(binding.etPackageName);
    }

    private void createAndroidStudioProjectFromTemplate(HashMap<String, Object> data, boolean cleanProjectFolder) throws IOException {
        File projectRoot = new File(wq.getAndroidStudioProjectPath(sc_id));
        if (cleanProjectFolder && projectRoot.exists()) {
            FileUtil.deleteFile(projectRoot.getAbsolutePath());
        }
        if (!projectRoot.exists() && !projectRoot.mkdirs()) {
            throw new IOException("Nao foi possivel criar a pasta " + projectRoot.getAbsolutePath());
        }

        File appBuildFile = new File(projectRoot, "app" + File.separator + "build.gradle");
        if (cleanProjectFolder || !appBuildFile.exists()) {
            try (InputStream inputStream = getAssets().open(TEMPLATE_ASSET_PATH);
                 ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
                FileUtil.extractZipTo(zipInputStream, projectRoot.getAbsolutePath());
            }
        }

        applyAndroidStudioTemplateValues(projectRoot, data);
    }

    private void applyAndroidStudioTemplateValues(File projectRoot, HashMap<String, Object> data) throws IOException {
        String packageName = Objects.requireNonNull(data.get("my_sc_pkg_name")).toString();
        String projectName = Objects.requireNonNull(data.get("my_ws_name")).toString();
        String appName = Objects.requireNonNull(data.get("my_app_name")).toString();
        String versionCode = Objects.requireNonNull(data.get("sc_ver_code")).toString();
        String versionName = Objects.requireNonNull(data.get("sc_ver_name")).toString();

        moveTemplatePackageDirectory(projectRoot, packageName);

        HashMap<String, String> replacements = new HashMap<>();
        replacements.put(PACKAGE_NAME_PLACEHOLDER, packageName);
        replacements.put(PROJECT_NAME_PLACEHOLDER, escapeXml(appName));

        File manifestFile = new File(projectRoot, "app" + File.separator + "src" + File.separator + "main" + File.separator + "AndroidManifest.xml");
        File mainActivityFile = new File(projectRoot, "app" + File.separator + "src" + File.separator + "main" + File.separator + "java" + File.separator + packageName.replace('.', File.separatorChar) + File.separator + "MainActivity.java");
        File mainLayoutFile = new File(projectRoot, "app" + File.separator + "src" + File.separator + "main" + File.separator + "res" + File.separator + "layout" + File.separator + "activity_main.xml");
        File stringsFile = new File(projectRoot, "app" + File.separator + "src" + File.separator + "main" + File.separator + "res" + File.separator + "values" + File.separator + "strings.xml");

        replaceInTextFile(manifestFile, replacements);
        replaceInTextFile(mainActivityFile, replacements);
        replaceInTextFile(stringsFile, replacements);
        updateManifestPackage(manifestFile, packageName);
        updateManifestLauncherIcons(manifestFile);
        updateJavaPackage(mainActivityFile, packageName);
        updateStringResource(stringsFile, "app_name", escapeXml(appName));

        File appBuildFile = new File(projectRoot, "app" + File.separator + "build.gradle");
        if (appBuildFile.exists()) {
            String content = FileUtil.readFile(appBuildFile.getAbsolutePath());
            content = content.replace(PACKAGE_NAME_PLACEHOLDER, packageName);
            content = content.replaceAll("applicationId\\s+\"[^\"]*\"", java.util.regex.Matcher.quoteReplacement("applicationId \"" + escapeGradleString(packageName) + "\""));
            content = content.replaceAll("versionCode\\s+\\d+", "versionCode " + parseInt(versionCode, 1));
            content = content.replaceAll("versionName\\s+\"[^\"]*\"", java.util.regex.Matcher.quoteReplacement("versionName \"" + escapeGradleString(versionName) + "\""));
            writeTextFileIfChanged(appBuildFile, content);
            AndroidStudioProjectSettingsDialog.applyStoredSettingsToGradle(sc_id, projectRoot);
        }

        File settingsFile = new File(projectRoot, "settings.gradle");
        if (settingsFile.exists()) {
            String content = FileUtil.readFile(settingsFile.getAbsolutePath());
            if (!content.contains("rootProject.name")) {
                content = "rootProject.name = \"" + escapeGradleString(projectName) + "\"\n" + content;
            } else {
                content = content.replaceAll("rootProject\\.name\\s*=\\s*\"[^\"]*\"", java.util.regex.Matcher.quoteReplacement("rootProject.name = \"" + escapeGradleString(projectName) + "\""));
            }
            writeTextFileIfChanged(settingsFile, content);
        }

        writeAndroidStudioColors(projectRoot);
        writeAndroidStudioIcons(projectRoot);
    }

    private void moveTemplatePackageDirectory(File projectRoot, String packageName) throws IOException {
        File javaRoot = new File(projectRoot, "app" + File.separator + "src" + File.separator + "main" + File.separator + "java");
        File placeholderDirectory = new File(javaRoot, PACKAGE_NAME_PLACEHOLDER);
        File sourceDirectory = placeholderDirectory;
        if (!sourceDirectory.exists() && originalPackageName != null && !originalPackageName.isEmpty()) {
            sourceDirectory = new File(javaRoot, originalPackageName.replace('.', File.separatorChar));
        }
        if (!sourceDirectory.exists()) {
            return;
        }
        File packageDirectory = new File(javaRoot, packageName.replace('.', File.separatorChar));
        if (sourceDirectory.getCanonicalPath().equals(packageDirectory.getCanonicalPath())) {
            return;
        }
        File parent = packageDirectory.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        if (packageDirectory.exists()) {
            FileUtil.deleteFile(packageDirectory.getAbsolutePath());
        }
        FileUtil.copyDirectory(sourceDirectory, packageDirectory);
        FileUtil.deleteFile(sourceDirectory.getAbsolutePath());
    }

    private void replaceInTextFile(File file, HashMap<String, String> replacements) {
        if (!file.exists()) {
            return;
        }
        String content = FileUtil.readFile(file.getAbsolutePath());
        for (String key : replacements.keySet()) {
            content = content.replace(key, replacements.get(key));
        }
        writeTextFileIfChanged(file, content);
    }

    private void updateManifestPackage(File file, String packageName) {
        if (!file.exists()) {
            return;
        }
        String content = FileUtil.readFile(file.getAbsolutePath());
        content = content.replaceAll("package=\"[^\"]*\"", java.util.regex.Matcher.quoteReplacement("package=\"" + packageName + "\""));
        writeTextFileIfChanged(file, content);
    }

    private void updateManifestLauncherIcons(File file) {
        if (!file.exists()) {
            return;
        }
        String content = FileUtil.readFile(file.getAbsolutePath());
        int applicationStart = content.indexOf("<application");
        if (applicationStart == -1) {
            return;
        }
        int applicationEnd = content.indexOf(">", applicationStart);
        if (applicationEnd == -1) {
            return;
        }
        String applicationTag = content.substring(applicationStart, applicationEnd);
        String updatedTag = ensureXmlAttribute(applicationTag, "android:icon", "@mipmap/ic_launcher");
        updatedTag = ensureXmlAttribute(updatedTag, "android:roundIcon", "@mipmap/ic_launcher_round");
        if (!applicationTag.equals(updatedTag)) {
            content = content.substring(0, applicationStart) + updatedTag + content.substring(applicationEnd);
            writeTextFileIfChanged(file, content);
        }
    }

    private String ensureXmlAttribute(String tag, String attributeName, String value) {
        String attribute = attributeName + "=\"" + value + "\"";
        if (tag.matches("(?s).*\\s" + java.util.regex.Pattern.quote(attributeName) + "\\s*=\\s*\"[^\"]*\".*")) {
            return tag.replaceAll(
                    "\\s" + java.util.regex.Pattern.quote(attributeName) + "\\s*=\\s*\"[^\"]*\"",
                    java.util.regex.Matcher.quoteReplacement("\n        " + attribute)
            );
        }
        return tag + "\n        " + attribute;
    }

    private void updateJavaPackage(File file, String packageName) {
        if (!file.exists()) {
            return;
        }
        String content = FileUtil.readFile(file.getAbsolutePath());
        content = content.replaceAll("package\\s+[^;]+;", java.util.regex.Matcher.quoteReplacement("package " + packageName + ";"));
        writeTextFileIfChanged(file, content);
    }

    private void updateStringResource(File file, String name, String value) {
        if (!file.exists()) {
            return;
        }
        String content = FileUtil.readFile(file.getAbsolutePath());
        String replacement = "<string name=\"" + name + "\">" + value + "</string>";
        if (content.matches("(?s).*<string\\s+name=\"" + name + "\">.*?</string>.*")) {
            content = content.replaceAll("(?s)<string\\s+name=\"" + name + "\">.*?</string>", java.util.regex.Matcher.quoteReplacement(replacement));
        } else {
            content = content.replace("</resources>", "    " + replacement + "\n</resources>");
        }
        writeTextFileIfChanged(file, content);
    }

    private void writeTextFileIfChanged(File file, String content) {
        String existing = file.exists() ? FileUtil.readFile(file.getAbsolutePath()) : "";
        if (!Objects.equals(existing, content)) {
            FileUtil.writeFile(file.getAbsolutePath(), content);
        }
    }

    private void writeAndroidStudioColors(File projectRoot) {
        File colorsFile = new File(projectRoot, "app" + File.separator + "src" + File.separator + "main" + File.separator + "res" + File.separator + "values" + File.separator + "colors.xml");
        StringBuilder colors = new StringBuilder();
        colors.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n");
        for (int i = 0; i < themeColorKeys.length; i++) {
            colors.append("    <color name=\"")
                    .append(themeColorLabels[i])
                    .append("\">")
                    .append(toColorHex(projectThemeColors[i]))
                    .append("</color>\n");
        }
        colors.append("    <color name=\"textColorPrimary\">#1C1B1F</color>\n");
        colors.append("    <color name=\"textColorSecondary\">#5F5E62</color>\n");
        colors.append("</resources>\n");
        writeTextFileIfChanged(colorsFile, colors.toString());
    }

    private void writeAndroidStudioIcons(File projectRoot) throws IOException {
        Bitmap sourceIcon = icon;
        if (sourceIcon == null) {
            return;
        }

        String[] densityFolders = {"mipmap-mdpi", "mipmap-hdpi", "mipmap-xhdpi", "mipmap-xxhdpi", "mipmap-xxxhdpi"};
        int[] iconSizes = {48, 72, 96, 144, 192};
        File resDirectory = new File(projectRoot, "app" + File.separator + "src" + File.separator + "main" + File.separator + "res");
        for (int i = 0; i < densityFolders.length; i++) {
            File densityDirectory = new File(resDirectory, densityFolders[i]);
            writeScaledPng(sourceIcon, new File(densityDirectory, "ic_launcher.png"), iconSizes[i]);
            writeScaledPng(sourceIcon, new File(densityDirectory, "ic_launcher_round.png"), iconSizes[i]);
        }
        File adaptiveIconDirectory = new File(resDirectory, "mipmap-anydpi-v26");
        FileUtil.deleteFile(new File(adaptiveIconDirectory, "ic_launcher.xml").getAbsolutePath());
        FileUtil.deleteFile(new File(adaptiveIconDirectory, "ic_launcher_round.xml").getAbsolutePath());
    }

    private void writeScaledPng(Bitmap source, File target, int size) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        Bitmap scaled = Bitmap.createScaledBitmap(source, size, size, true);
        try (FileOutputStream outputStream = new FileOutputStream(target)) {
            scaled.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            outputStream.flush();
        } finally {
            if (scaled != source) {
                scaled.recycle();
            }
        }
    }

    private String toColorHex(int color) {
        return String.format("#%06X", (0xFFFFFF & color));
    }

    private String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String escapeGradleString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void openAndroidStudioProjectEditor() {
        Intent editorIntent = new Intent(this, AndroidStudioProjectActivity.class);
        editorIntent.putExtra(AndroidStudioProjectActivity.EXTRA_SC_ID, sc_id);
        startActivity(editorIntent);
        finish();
    }

    private static class ThemeColorView extends LinearLayout {

        private TextView color;
        private TextView name;

        public ThemeColorView(Context context, int tag) {
            super(context);
            initialize(context, tag);
        }

        private void initialize(Context context, int tag) {
            setTag(tag);
            wB.a(context, this, R.layout.myproject_color);
            color = findViewById(R.id.color);
            name = findViewById(R.id.name);
        }
    }

    private class SaveProjectAsyncTask extends MA {

        public SaveProjectAsyncTask(Context context) {
            super(context);
            addTask(this);
            k();
        }

        @Override
        public void a() {
            h();
            if (saveError != null) {
                new MaterialAlertDialogBuilder(MyProjectSettingActivity.this)
                        .setIcon(R.drawable.break_warning_96_red)
                        .setTitle(Helper.getResString(R.string.common_error_an_error_occurred))
                        .setMessage(saveError)
                        .setPositiveButton(Helper.getResString(R.string.common_word_ok), null)
                        .show();
                return;
            }
            Intent intent = getIntent();
            intent.putExtra("sc_id", sc_id);
            intent.putExtra("is_new", !updatingExistingProject);
            intent.putExtra("index", intent.getIntExtra("index", -1));
            intent.putExtra(EXTRA_PROJECT_KIND, projectKind);
            setResult(RESULT_OK, intent);
            if (isAndroidStudioProject()) {
                openAndroidStudioProjectEditor();
            } else {
                finish();
            }
        }

        @Override
        public void b() {
            saveError = null;
            HashMap<String, Object> data = new HashMap<>();
            data.put("sc_id", sc_id);
            data.put("my_sc_pkg_name", getPackageNameForSave());
            data.put("my_ws_name", Helper.getText(binding.etProjectName));
            data.put("my_app_name", Helper.getText(binding.etAppName));
            data.put(lC.PROJECT_KIND_KEY, projectKind);
            data.put("proj_type", isAndroidStudioProject() ? 2 : 1);
            if (updatingExistingProject) {
                data.put("custom_icon", projectHasCustomIcon);
                data.put("isIconAdaptive", isIconAdaptive);
                data.put("sc_ver_code", Helper.getText(binding.verCode));
                data.put("sc_ver_name", Helper.getText(binding.verName));
                data.put("sketchware_ver", GB.d(getApplicationContext()));
                for (int i = 0; i < themeColorKeys.length; i++) {
                    data.put(themeColorKeys[i], projectThemeColors[i]);
                }
                if (isAndroidStudioProject()) {
                    try {
                        createAndroidStudioProjectFromTemplate(data, false);
                        data.put("studio_path", wq.getAndroidStudioProjectPath(sc_id));
                        lC.b(sc_id, data);
                    } catch (Exception e) {
                        saveError = "Falha ao configurar o projeto Android Studio: " + e.getMessage();
                    }
                } else {
                    lC.b(sc_id, data);
                    updateProjectResourcesContents(data);
                }
            } else {
                data.put("my_sc_reg_dt", new nB().a("yyyyMMddHHmmss"));
                data.put("custom_icon", projectHasCustomIcon);
                data.put("isIconAdaptive", isIconAdaptive);
                data.put("sc_ver_code", Helper.getText(binding.verCode));
                data.put("sc_ver_name", Helper.getText(binding.verName));
                data.put("sketchware_ver", GB.d(getApplicationContext()));
                for (int i = 0; i < themeColorKeys.length; i++) {
                    data.put(themeColorKeys[i], projectThemeColors[i]);
                }
                if (isAndroidStudioProject()) {
                    try {
                        createAndroidStudioProjectFromTemplate(data, true);
                        data.put("studio_path", wq.getAndroidStudioProjectPath(sc_id));
                        lC.saveAndroidStudioProject(sc_id, data);
                    } catch (Exception e) {
                        saveError = "Falha ao criar o projeto Android Studio: " + e.getMessage();
                    }
                } else {
                    lC.a(sc_id, data);
                    updateProjectResourcesContents(data);
                    wq.a(getApplicationContext(), sc_id);
                    new oB().b(wq.b(sc_id));
                    ProjectSettings projectSettings = new ProjectSettings(sc_id);
                    projectSettings.setValue(ProjectSettings.SETTING_NEW_XML_COMMAND, ProjectSettings.SETTING_GENERIC_VALUE_TRUE);
                    projectSettings.setValue(ProjectSettings.SETTING_ENABLE_VIEWBINDING, ProjectSettings.SETTING_GENERIC_VALUE_TRUE);
                }

            }
            try {
                FileUtil.deleteFile(getTempIconsFolderPath("mipmaps" + File.separator));
                FileUtil.copyDirectory(new File(getTempIconsFolderPath("temp_icons" + File.separator)), new File(getIconsFolderPath()));
                FileUtil.deleteFile(getTempIconsFolderPath("temp_icons" + File.separator));
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

        private void updateProjectResourcesContents(HashMap<String, Object> data) {
            String baseDir = wq.b(sc_id) + "/files/resource/values/";
            String stringsFilePath = baseDir + "strings.xml";
            String colorsFilePath = baseDir + "colors.xml";
            String newAppName = Objects.requireNonNull(data.get("my_app_name")).toString();

            if (FileUtil.isExistFile(stringsFilePath)) {
                String xmlContent = FileUtil.readFile(stringsFilePath);
                xmlContent = xmlContent.replaceAll("(<string\\s+name=\"app_name\">)(.*?)(</string>)", "$1" + newAppName + "$3");
                FileUtil.writeFile(stringsFilePath, xmlContent);
            }

            if (FileUtil.isExistFile(colorsFilePath)) {
                String xmlContent = FileUtil.readFile(colorsFilePath);
                for (int i = 0; i < themeColorKeys.length; i++) {
                    String colorName = themeColorLabels[i];
                    String newColor = String.format("#%06X", (0xFFFFFF & projectThemeColors[i]));
                    xmlContent = xmlContent.replaceAll("(<color\\s+name=\"" + colorName + "\">)(.*?)(</color>)", "$1" + newColor + "$3");
                }
                FileUtil.writeFile(colorsFilePath, xmlContent);
            }

        }

        @Override
        public void a(String str) {
            h();
        }

    }
}
