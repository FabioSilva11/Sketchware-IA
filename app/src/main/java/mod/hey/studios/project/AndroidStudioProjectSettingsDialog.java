package mod.hey.studios.project;

import static com.besome.sketch.Config.VAR_DEFAULT_COMPILE_SDK_VERSION;
import static com.besome.sketch.Config.VAR_DEFAULT_MIN_SDK_VERSION;
import static com.besome.sketch.Config.VAR_DEFAULT_TARGET_SDK_VERSION;

import android.app.Activity;
import android.view.View;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import a.a.a.wq;
import mod.hey.studios.util.Helper;
import pro.sketchware.databinding.DialogProjectSettingsBinding;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;

public class AndroidStudioProjectSettingsDialog {

    private final Activity activity;
    private final String scId;
    private final ProjectSettings settings;

    public AndroidStudioProjectSettingsDialog(Activity activity, String scId) {
        this.activity = activity;
        this.scId = scId;
        settings = new ProjectSettings(scId);
    }

    public void show() {
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        DialogProjectSettingsBinding binding = DialogProjectSettingsBinding.inflate(activity.getLayoutInflater());
        File projectRoot = new File(wq.getAndroidStudioProjectPath(scId));
        File buildFile = findAppBuildFile(projectRoot);
        String buildGradle = readText(buildFile);

        dialog.setOnShowListener(bsd -> {
            BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) bsd;
            View parent = bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (parent != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(parent);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        });

        binding.etCompileSdkVersion.setText(settings.getValue(
                ProjectSettings.SETTING_COMPILE_SDK_VERSION,
                readCompileSdk(buildGradle, String.valueOf(VAR_DEFAULT_COMPILE_SDK_VERSION))));
        binding.etMinimumSdkVersion.setText(settings.getValue(
                ProjectSettings.SETTING_MINIMUM_SDK_VERSION,
                readMinSdk(buildGradle, String.valueOf(VAR_DEFAULT_MIN_SDK_VERSION))));
        binding.etTargetSdkVersion.setText(settings.getValue(
                ProjectSettings.SETTING_TARGET_SDK_VERSION,
                readTargetSdk(buildGradle, String.valueOf(VAR_DEFAULT_TARGET_SDK_VERSION))));

        binding.tilApplicationClassName.setVisibility(View.GONE);
        binding.enableViewbinding.setVisibility(View.GONE);
        binding.removeOldMethods.setVisibility(View.GONE);
        binding.useNewMaterialComponentsAppTheme.setVisibility(View.GONE);

        binding.etCompileSdkVersion.setTag(ProjectSettings.SETTING_COMPILE_SDK_VERSION);
        binding.etMinimumSdkVersion.setTag(ProjectSettings.SETTING_MINIMUM_SDK_VERSION);
        binding.etTargetSdkVersion.setTag(ProjectSettings.SETTING_TARGET_SDK_VERSION);

        dialog.setContentView(binding.getRoot());

        View[] preferences = {
                binding.etCompileSdkVersion,
                binding.etMinimumSdkVersion,
                binding.etTargetSdkVersion
        };

        binding.save.setOnClickListener(v -> {
            if (!validate(binding)) {
                return;
            }
            settings.setValues(preferences);
            try {
                applyStoredSettingsToGradle(scId, projectRoot);
                SketchwareUtil.toast("Project configuration saved");
                dialog.dismiss();
            } catch (IOException e) {
                SketchwareUtil.toastError("Couldn't update build.gradle: " + e.getMessage());
            }
        });
        binding.cancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    public static void applyStoredSettingsToGradle(String scId, File projectRoot) throws IOException {
        File buildFile = findAppBuildFile(projectRoot);
        if (buildFile == null || !buildFile.isFile()) {
            return;
        }

        ProjectSettings settings = new ProjectSettings(scId);
        String content = readText(buildFile);
        boolean kotlinDsl = buildFile.getName().endsWith(".kts");
        String compileSdk = settings.getValue(
                ProjectSettings.SETTING_COMPILE_SDK_VERSION,
                readCompileSdk(content, String.valueOf(VAR_DEFAULT_COMPILE_SDK_VERSION)));
        String minSdk = settings.getValue(
                ProjectSettings.SETTING_MINIMUM_SDK_VERSION,
                readMinSdk(content, String.valueOf(VAR_DEFAULT_MIN_SDK_VERSION)));
        String targetSdk = settings.getValue(
                ProjectSettings.SETTING_TARGET_SDK_VERSION,
                readTargetSdk(content, String.valueOf(VAR_DEFAULT_TARGET_SDK_VERSION)));

        String updated = updateCompileSdk(content, compileSdk, kotlinDsl);
        updated = updateDefaultConfigSdk(updated, "minSdk", "minSdkVersion", minSdk, kotlinDsl);
        updated = updateDefaultConfigSdk(updated, "targetSdk", "targetSdkVersion", targetSdk, kotlinDsl);

        if (!content.equals(updated)) {
            FileUtil.writeFile(buildFile.getAbsolutePath(), updated);
        }
    }

    private boolean validate(DialogProjectSettingsBinding binding) {
        clearError(binding.tilCompileSdkVersion);
        clearError(binding.tilMinimumSdkVersion);
        clearError(binding.tilTargetSdkVersion);

        int compileSdk = readPositiveInt(binding.tilCompileSdkVersion, Helper.getText(binding.etCompileSdkVersion));
        int minSdk = readPositiveInt(binding.tilMinimumSdkVersion, Helper.getText(binding.etMinimumSdkVersion));
        int targetSdk = readPositiveInt(binding.tilTargetSdkVersion, Helper.getText(binding.etTargetSdkVersion));
        if (compileSdk <= 0 || minSdk <= 0 || targetSdk <= 0) {
            return false;
        }
        if (minSdk > targetSdk) {
            binding.tilTargetSdkVersion.setError("Target SDK must be at least Minimum SDK");
            return false;
        }
        if (targetSdk > compileSdk) {
            binding.tilCompileSdkVersion.setError("Compile SDK must be at least Target SDK");
            return false;
        }
        return true;
    }

    private int readPositiveInt(TextInputLayout layout, String value) {
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
        }
        layout.setError("Enter a valid SDK version");
        return -1;
    }

    private static void clearError(TextInputLayout layout) {
        layout.setError(null);
        layout.setErrorEnabled(false);
    }

    private static File findAppBuildFile(File projectRoot) {
        if (projectRoot == null) {
            return null;
        }
        File groovy = new File(projectRoot, "app" + File.separator + "build.gradle");
        if (groovy.isFile()) {
            return groovy;
        }
        File kotlin = new File(projectRoot, "app" + File.separator + "build.gradle.kts");
        return kotlin.isFile() ? kotlin : null;
    }

    private static String updateCompileSdk(String content, String value, boolean kotlinDsl) {
        String updated = replaceFirstNumber(content, value,
                "\\bcompileSdkVersion\\s*\\(\\s*(\\d+)\\s*\\)",
                "\\bcompileSdkVersion\\s+(\\d+)",
                "\\bcompileSdk\\s*=\\s*(\\d+)",
                "\\bcompileSdk\\s+(\\d+)");
        if (updated != null) {
            return updated;
        }
        return insertIntoBlock(content, "android", sdkLine("compileSdk", "compileSdkVersion", value, kotlinDsl));
    }

    private static String updateDefaultConfigSdk(String content, String kotlinName, String groovyName, String value, boolean kotlinDsl) {
        String updated = replaceFirstNumber(content, value,
                "\\b" + Pattern.quote(groovyName) + "\\s*\\(\\s*(\\d+)\\s*\\)",
                "\\b" + Pattern.quote(groovyName) + "\\s+(\\d+)",
                "\\b" + Pattern.quote(kotlinName) + "\\s*=\\s*(\\d+)",
                "\\b" + Pattern.quote(kotlinName) + "\\s+(\\d+)");
        if (updated != null) {
            return updated;
        }
        return insertIntoBlock(content, "defaultConfig", sdkLine(kotlinName, groovyName, value, kotlinDsl));
    }

    private static String sdkLine(String kotlinName, String groovyName, String value, boolean kotlinDsl) {
        return kotlinDsl ? kotlinName + " = " + value : groovyName + " " + value;
    }

    private static String replaceFirstNumber(String content, String value, String... regexes) {
        for (String regex : regexes) {
            Matcher matcher = Pattern.compile(regex).matcher(content);
            if (matcher.find()) {
                return content.substring(0, matcher.start(1)) + value + content.substring(matcher.end(1));
            }
        }
        return null;
    }

    private static String insertIntoBlock(String content, String blockName, String line) {
        String updated = insertIntoBlockIfExists(content, blockName, line);
        if (updated != null) {
            return updated;
        }
        if ("defaultConfig".equals(blockName)) {
            return insertBlockIntoAndroid(content, "defaultConfig", line);
        }
        return content;
    }

    private static String insertIntoBlockIfExists(String content, String blockName, String line) {
        Matcher matcher = Pattern.compile("(?m)^(\\s*)" + Pattern.quote(blockName) + "\\s*\\{").matcher(content);
        if (!matcher.find()) {
            return null;
        }
        int insertPosition = findLineEnd(content, matcher.end());
        String indent = matcher.group(1) + "    ";
        return content.substring(0, insertPosition) + "\n" + indent + line + content.substring(insertPosition);
    }

    private static String insertBlockIntoAndroid(String content, String blockName, String line) {
        Matcher matcher = Pattern.compile("(?m)^(\\s*)android\\s*\\{").matcher(content);
        if (!matcher.find()) {
            return content;
        }
        int insertPosition = findLineEnd(content, matcher.end());
        String indent = matcher.group(1) + "    ";
        String childIndent = indent + "    ";
        String block = "\n" + indent + blockName + " {\n" + childIndent + line + "\n" + indent + "}";
        return content.substring(0, insertPosition) + block + content.substring(insertPosition);
    }

    private static int findLineEnd(String content, int start) {
        int lineEnd = content.indexOf('\n', start);
        return lineEnd == -1 ? content.length() : lineEnd;
    }

    private static String readCompileSdk(String content, String fallback) {
        return readFirstNumber(content, fallback,
                "\\bcompileSdkVersion\\s*\\(\\s*(\\d+)\\s*\\)",
                "\\bcompileSdkVersion\\s+(\\d+)",
                "\\bcompileSdk\\s*=\\s*(\\d+)",
                "\\bcompileSdk\\s+(\\d+)");
    }

    private static String readMinSdk(String content, String fallback) {
        return readFirstNumber(content, fallback,
                "\\bminSdkVersion\\s*\\(\\s*(\\d+)\\s*\\)",
                "\\bminSdkVersion\\s+(\\d+)",
                "\\bminSdk\\s*=\\s*(\\d+)",
                "\\bminSdk\\s+(\\d+)");
    }

    private static String readTargetSdk(String content, String fallback) {
        return readFirstNumber(content, fallback,
                "\\btargetSdkVersion\\s*\\(\\s*(\\d+)\\s*\\)",
                "\\btargetSdkVersion\\s+(\\d+)",
                "\\btargetSdk\\s*=\\s*(\\d+)",
                "\\btargetSdk\\s+(\\d+)");
    }

    private static String readFirstNumber(String content, String fallback, String... regexes) {
        for (String regex : regexes) {
            Matcher matcher = Pattern.compile(regex).matcher(content);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return fallback;
    }

    private static String readText(File file) {
        return file != null && file.isFile() ? FileUtil.readFile(file.getAbsolutePath()) : "";
    }
}
