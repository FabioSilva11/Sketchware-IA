package a.a.a;

import static android.text.TextUtils.isEmpty;
import static com.besome.sketch.Config.VAR_DEFAULT_TARGET_SDK_VERSION;

import android.Manifest;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Intent;

import com.besome.sketch.beans.ProjectFileBean;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import mod.hey.studios.build.BuildSettings;
import mod.hey.studios.project.ProjectSettings;
import mod.hey.studios.util.Helper;
import mod.hilal.saif.android_manifest.AndroidManifestInjector;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileResConfig;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.xml.XmlBuilder;

public class Ix {
    public XmlBuilder a = new XmlBuilder("manifest");
    public ArrayList<ProjectFileBean> b;
    public BuildSettings buildSettings;
    public jq c;
    public FilePathUtil fpu = new FilePathUtil();
    public FileResConfig frc;
    public ProjectSettings settings;
    private boolean targetsSdkVersion31OrHigher = false;
    private String packageName;
    private final Set<String> addedPermissions = new HashSet<>();

    public Ix(jq jq, ArrayList<ProjectFileBean> projectFileBeans) {
        c = jq;
        b = projectFileBeans;
        buildSettings = new BuildSettings(jq.sc_id);
        frc = new FileResConfig(c.sc_id);
        a.addAttribute("xmlns", "android", "http://schemas.android.com/apk/res/android");
    }

    /**
     * Adds FileProvider metadata to AndroidManifest.
     *
     * @param applicationTag AndroidManifest {@link XmlBuilder} object
     */
    private void writeFileProvider(XmlBuilder applicationTag) {
        XmlBuilder providerTag = new XmlBuilder("provider");
        providerTag.addAttribute("android", "authorities", c.packageName + ".provider");
        providerTag.addAttribute("android", "name", "androidx.core.content.FileProvider");
        providerTag.addAttribute("android", "exported", "false");
        providerTag.addAttribute("android", "grantUriPermissions", "true");
        XmlBuilder metadataTag = new XmlBuilder("meta-data");
        metadataTag.addAttribute("android", "name", "android.support.FILE_PROVIDER_PATHS");
        metadataTag.addAttribute("android", "resource", "@xml/provider_paths");
        providerTag.addChildNode(metadataTag);
        applicationTag.addChildNode(providerTag);
    }

    /**
     * Adds a permission to AndroidManifest.
     *
     * @param manifestTag    AndroidManifest {@link XmlBuilder} object
     * @param permissionName The {@code uses-permission} {@link XmlBuilder} tag
     */
    private void writePermission(XmlBuilder manifestTag, String permissionName) {
        if (addedPermissions.contains(permissionName)) {
            return;
        }
        XmlBuilder usesPermissionTag = new XmlBuilder("uses-permission");
        usesPermissionTag.addAttribute("android", "name", permissionName);
        manifestTag.addChildNode(usesPermissionTag);
        addedPermissions.add(permissionName);
    }


    /**
     * Adds the Google Maps SDK API key metadata to AndroidManifest.
     *
     * @param applicationTag AndroidManifest {@link XmlBuilder} object
     */
    private void writeGoogleMapMetaData(XmlBuilder applicationTag) {
        XmlBuilder metadataTag = new XmlBuilder("meta-data");
        metadataTag.addAttribute("android", "name", "com.google.android.geo.API_KEY");
        metadataTag.addAttribute("android", "value", "@string/google_maps_key");
        applicationTag.addChildNode(metadataTag);
    }

    /**
     * Specifies in AndroidManifest that the app uses Apache HTTP legacy library.
     *
     * @param applicationTag AndroidManifest {@link XmlBuilder} object
     */
    private void writeLegacyLibrary(XmlBuilder applicationTag) {
        XmlBuilder usesLibraryTag = new XmlBuilder("uses-library");
        usesLibraryTag.addAttribute("android", "name", "org.apache.http.legacy");
        usesLibraryTag.addAttribute("android", "required", "false");
        applicationTag.addChildNode(usesLibraryTag);
    }


    /**
     * Registers a {@link BroadcastReceiver} in AndroidManifest.
     *
     * @param applicationTag AndroidManifest {@link XmlBuilder} object
     * @param receiverName   The component name of the broadcast
     * @see ComponentName
     */
    private void writeBroadcast(XmlBuilder applicationTag, String receiverName) {
        XmlBuilder receiverTag = new XmlBuilder("receiver");
        receiverTag.addAttribute("android", "name", receiverName);
        XmlBuilder intentFilterTag = new XmlBuilder("intent-filter");
        XmlBuilder actionTag = new XmlBuilder("action");
        actionTag.addAttribute("android", "name", receiverName);
        intentFilterTag.addChildNode(actionTag);
        if (targetsSdkVersion31OrHigher) {
            receiverTag.addAttribute("android", "exported", "true");
        }
        receiverTag.addChildNode(intentFilterTag);
        applicationTag.addChildNode(receiverTag);
    }

    private void writeAdmobAppId(XmlBuilder applicationTag) {
        XmlBuilder metadataTag = new XmlBuilder("meta-data");
        metadataTag.addAttribute("android", "name", "com.google.android.gms.ads.APPLICATION_ID");
        metadataTag.addAttribute("android", "value", c.appId);
        applicationTag.addChildNode(metadataTag);
    }

    /**
     * Registers a {@link Service} in AndroidManifest.
     *
     * @param applicationTag AndroidManifest {@link XmlBuilder} object
     * @param serviceName    The component name of the service
     */
    private void writeService(XmlBuilder applicationTag, String serviceName) {
        XmlBuilder serviceTag = new XmlBuilder("service");
        serviceTag.addAttribute("android", "name", serviceName);
        serviceTag.addAttribute("android", "enabled", "true");
        applicationTag.addChildNode(serviceTag);
    }




    public void setYq(yq yqVar) {
        settings = new ProjectSettings(yqVar.sc_id);
        targetsSdkVersion31OrHigher = Integer.parseInt(settings.getValue(ProjectSettings.SETTING_TARGET_SDK_VERSION, String.valueOf(VAR_DEFAULT_TARGET_SDK_VERSION))) >= 31;
        packageName = yqVar.packageName;
    }

    /**
     * Builds an AndroidManifest.
     *
     * @return The AndroidManifest as {@link String}
     */
    public String a() {
        return a(true);
    }

    /**
     * Builds an AndroidManifest.
     *
     * @param applyCustomManifest whether the saved full custom manifest should replace the generated manifest
     * @return The AndroidManifest as {@link String}
     */
    public String a(boolean applyCustomManifest) {
        int targetSdkVersion;
        try {
            targetSdkVersion = Integer.parseInt(settings.getValue(ProjectSettings.SETTING_TARGET_SDK_VERSION, String.valueOf(VAR_DEFAULT_TARGET_SDK_VERSION)));
        } catch (NumberFormatException ignored) {
            targetSdkVersion = VAR_DEFAULT_TARGET_SDK_VERSION;
        }
        boolean addRequestLegacyExternalStorage = targetSdkVersion >= 28;

        a.addAttribute("", "package", c.packageName);

        if (!c.hasPermissions()) {
            if (c.hasPermission(jq.PERMISSION_CALL_PHONE)) {
                writePermission(a, Manifest.permission.CALL_PHONE);
            }
            if (c.hasPermission(jq.PERMISSION_INTERNET)) {
                writePermission(a, Manifest.permission.INTERNET);
            }
            if (c.hasPermission(jq.PERMISSION_VIBRATE)) {
                writePermission(a, Manifest.permission.VIBRATE);
            }
            if (c.hasPermission(jq.PERMISSION_ACCESS_NETWORK_STATE)) {
                writePermission(a, Manifest.permission.ACCESS_NETWORK_STATE);
            }
            if (c.hasPermission(jq.PERMISSION_CAMERA)) {
                writePermission(a, Manifest.permission.CAMERA);
            }
            if (c.hasPermission(jq.PERMISSION_READ_EXTERNAL_STORAGE)) {
                writePermission(a, Manifest.permission.READ_EXTERNAL_STORAGE);
            }
            if (c.hasPermission(jq.PERMISSION_WRITE_EXTERNAL_STORAGE)) {
                writePermission(a, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            if (c.hasPermission(jq.PERMISSION_RECORD_AUDIO)) {
                writePermission(a, Manifest.permission.RECORD_AUDIO);
            }
            if (c.hasPermission(jq.PERMISSION_BLUETOOTH)) {
                writePermission(a, Manifest.permission.BLUETOOTH);
            }
            if (c.hasPermission(jq.PERMISSION_BLUETOOTH_ADMIN)) {
                writePermission(a, Manifest.permission.BLUETOOTH_ADMIN);
            }
            if (c.hasPermission(jq.PERMISSION_ACCESS_FINE_LOCATION)) {
                writePermission(a, Manifest.permission.ACCESS_FINE_LOCATION);
            }
            if (c.hasPermission(jq.PERMISSION_ACTIVITY_RECOGNITION)) {
                writePermission(a, Manifest.permission.ACTIVITY_RECOGNITION);
            }
        }
        if (FileUtil.isExistFile(fpu.getPathPermission(c.sc_id))) {
            for (String s : frc.getPermissionList()) {
                writePermission(a, s);
            }
        }
        if (c.isAlarmManagerUsed) {
            writePermission(a, "android.permission.SCHEDULE_EXACT_ALARM");
        }
        if (c.isBiometricManagerUsed) {
            writePermission(a, "android.permission.USE_BIOMETRIC");
        }
        if (c.isFusedLocationManagerUsed) {
            writePermission(a, Manifest.permission.ACCESS_FINE_LOCATION);
            writePermission(a, Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (c.isWorkManagerUsed) {
            // Declared by the app itself so that no library's tools:node="remove" drops them.
            writePermission(a, "android.permission.WAKE_LOCK");
            writePermission(a, "android.permission.ACCESS_NETWORK_STATE");
            writePermission(a, "android.permission.RECEIVE_BOOT_COMPLETED");
            writePermission(a, "android.permission.FOREGROUND_SERVICE");
        }
        AndroidManifestInjector.getP(a, c.sc_id);

        if (c.isTextToSpeechUsed || c.isSpeechToTextUsed) {
            XmlBuilder queries = new XmlBuilder("queries");
            if (c.isTextToSpeechUsed && targetSdkVersion >= 30) {
                XmlBuilder intent = new XmlBuilder("intent");
                XmlBuilder action = new XmlBuilder("action");
                action.addAttribute("android", "name", "android.intent.action.TTS_SERVICE");
                intent.addChildNode(action);
                queries.addChildNode(intent);
            }
            if (c.isSpeechToTextUsed && targetSdkVersion >= 30) {
                XmlBuilder intent = new XmlBuilder("intent");
                XmlBuilder action = new XmlBuilder("action");
                action.addAttribute("android", "name", "android.speech.RecognitionService");
                intent.addChildNode(action);
                queries.addChildNode(intent);
            }
            a.addChildNode(queries);
        }

        XmlBuilder applicationTag = new XmlBuilder("application");
        applicationTag.addAttribute("android", "allowBackup", "true");
        applicationTag.addAttribute("android", "icon", "@mipmap/ic_launcher");
        applicationTag.addAttribute("android", "label", "@string/app_name");

        String applicationClassName = settings.getValue(ProjectSettings.SETTING_APPLICATION_CLASS, ".SketchApplication");
        applicationTag.addAttribute("android", "name", applicationClassName);
        if (addRequestLegacyExternalStorage) {
            applicationTag.addAttribute("android", "requestLegacyExternalStorage", "true");
        }
        if (!buildSettings.getValue(BuildSettings.SETTING_NO_HTTP_LEGACY, BuildSettings.SETTING_GENERIC_VALUE_FALSE)
                .equals(BuildSettings.SETTING_GENERIC_VALUE_TRUE)) {
            applicationTag.addAttribute("android", "usesCleartextTraffic", "true");
        }
        AndroidManifestInjector.getAppAttrs(applicationTag, c.sc_id);
        if (targetSdkVersion >= 33 && !AndroidManifestInjector.isApplicationAttributeInjected(c.sc_id, "android:enableOnBackInvokedCallback")) {
            // Apps targeting API 36 get predictive back, which no longer calls onBackPressed(),
            // the method Sketchware's onBackPressed event generates.
            applicationTag.addAttribute("android", "enableOnBackInvokedCallback", "false");
        }

        boolean hasDebugActivity = false;
        for (ProjectFileBean projectFileBean : b) {
            if (!projectFileBean.fileName.contains("_fragment")) {
                XmlBuilder activityTag = new XmlBuilder("activity");

                String javaName = projectFileBean.getJavaName();
                activityTag.addAttribute("android", "name", "." + javaName.substring(0, javaName.indexOf(".java")));

                if (!AndroidManifestInjector.getActivityAttrs(activityTag, c.sc_id, projectFileBean.getJavaName())) {
                    activityTag.addAttribute("android", "configChanges", "orientation|screenSize|keyboardHidden|smallestScreenSize|screenLayout");
                    activityTag.addAttribute("android", "hardwareAccelerated", "true");
                    activityTag.addAttribute("android", "supportsPictureInPicture", "true");
                }
                if (!AndroidManifestInjector.isActivityThemeUsed(activityTag, c.sc_id, projectFileBean.getJavaName())) {
                    if (c.g) {
                        if (projectFileBean.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_FULLSCREEN)) {
                            activityTag.addAttribute("android", "theme", "@style/AppTheme.FullScreen");
                        }
                    } else if (projectFileBean.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_FULLSCREEN)) {
                        if (projectFileBean.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_TOOLBAR)) {
                            activityTag.addAttribute("android", "theme", "@style/NoStatusBar");
                        } else {
                            activityTag.addAttribute("android", "theme", "@style/FullScreen");
                        }
                    } else if (!projectFileBean.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_TOOLBAR)) {
                        activityTag.addAttribute("android", "theme", "@style/NoActionBar");
                    }
                }
                if (!AndroidManifestInjector.isActivityOrientationUsed(activityTag, c.sc_id, projectFileBean.getJavaName())) {
                    int orientation = projectFileBean.orientation;
                    if (orientation == ProjectFileBean.ORIENTATION_PORTRAIT) {
                        activityTag.addAttribute("android", "screenOrientation", "portrait");
                    } else if (orientation == ProjectFileBean.ORIENTATION_LANDSCAPE) {
                        activityTag.addAttribute("android", "screenOrientation", "landscape");
                    }
                }
                if (!AndroidManifestInjector.isActivityKeyboardUsed(activityTag, c.sc_id, projectFileBean.getJavaName())) {
                    String keyboardSetting = vq.a(projectFileBean.keyboardSetting);
                    if (!keyboardSetting.isEmpty()) {
                        activityTag.addAttribute("android", "windowSoftInputMode", keyboardSetting);
                    }
                }
                if (projectFileBean.fileName.equals(AndroidManifestInjector.getLauncherActivity(c.sc_id))) {
                    XmlBuilder intentFilterTag = new XmlBuilder("intent-filter");
                    XmlBuilder actionTag = new XmlBuilder("action");
                    actionTag.addAttribute("android", "name", Intent.ACTION_MAIN);
                    intentFilterTag.addChildNode(actionTag);
                    XmlBuilder categoryTag = new XmlBuilder("category");
                    categoryTag.addAttribute("android", "name", Intent.CATEGORY_LAUNCHER);
                    intentFilterTag.addChildNode(categoryTag);
                    if (targetsSdkVersion31OrHigher && !AndroidManifestInjector.isActivityExportedUsed(c.sc_id, javaName)) {
                        activityTag.addAttribute("android", "exported", "true");
                    }
                    activityTag.addChildNode(intentFilterTag);
                }
                applicationTag.addChildNode(activityTag);
            }
            if (projectFileBean.fileName.equals("debug")) {
                hasDebugActivity = true;
            }
        }

        if (!hasDebugActivity) {
            XmlBuilder activityTag = new XmlBuilder("activity");
            activityTag.addAttribute("android", "name", ".DebugActivity");
            activityTag.addAttribute("android", "screenOrientation", "portrait");
            activityTag.addAttribute("android", "theme", "@style/AppTheme.DebugActivity");
            applicationTag.addChildNode(activityTag);
        }
        // Components, permissions and <queries> that libraries need (Firebase registrars, AdMob,
        // WorkManager, androidx.startup, ...) are merged in from their own manifests at build time.
        if (c.u) {
            writeFileProvider(applicationTag);
        }
        if (c.isAdMobEnabled && !isEmpty(c.appId)) {
            writeAdmobAppId(applicationTag);
        }
        if (c.isMapUsed) {
            writeGoogleMapMetaData(applicationTag);
        }
        if (FileUtil.isExistFile(fpu.getManifestJava(c.sc_id))) {
            ArrayList<HashMap<String, Object>> activityAttrs = getActivityAttrs();
            for (String activityName : frc.getJavaManifestList()) {
                writeJava(applicationTag, activityName, activityAttrs);
            }
        }
        if (buildSettings.getValue(BuildSettings.SETTING_NO_HTTP_LEGACY, BuildSettings.SETTING_GENERIC_VALUE_FALSE)
                .equals(BuildSettings.SETTING_GENERIC_VALUE_FALSE)) {
            writeLegacyLibrary(applicationTag);
        }
        if (FileUtil.isExistFile(fpu.getManifestService(c.sc_id))) {
            for (String serviceName : frc.getServiceManifestList()) {
                writeService(applicationTag, serviceName);
            }
        }
        if (FileUtil.isExistFile(fpu.getManifestBroadcast(c.sc_id))) {
            for (String receiverName : frc.getBroadcastManifestList()) {
                writeBroadcast(applicationTag, receiverName);
            }
        }
        a.addChildNode(applicationTag);
        // Needed, as crashing on my SM-A526B with Android 12 / One UI 4.1 / firmware build A526BFXXS1CVD1 otherwise
        //noinspection RegExpRedundantEscape
        String manifest = AndroidManifestInjector.mHolder(a.toCode(), c.sc_id);
        if (applyCustomManifest) {
            manifest = AndroidManifestInjector.applyCustomManifest(manifest, c.sc_id);
        }
        return manifest.replace("${applicationId}", packageName);
    }

    private void writeJava(XmlBuilder applicationTag, String activityName, ArrayList<HashMap<String, Object>> activityAttrs) {
        XmlBuilder activityTag = new XmlBuilder("activity");
        boolean specifiedActivityName = false;
        boolean specifiedConfigChanges = false;
        for (HashMap<String, Object> hashMap : activityAttrs) {
            if (hashMap.containsKey("name") && hashMap.containsKey("value")) {
                Object nameObject = hashMap.get("name");
                Object valueObject = hashMap.get("value");
                if (nameObject instanceof String && valueObject instanceof String) {
                    String name = nameObject.toString();
                    String value = valueObject.toString();
                    if (name.equals(activityName)) {
                        activityTag.addAttributeValue(value);
                        if (value.contains("android:name=")) {
                            specifiedActivityName = true;
                        } else if (value.contains("android:configChanges=")) {
                            specifiedConfigChanges = true;
                        }
                    }
                }
            }
        }
        if (!specifiedActivityName) {
            activityTag.addAttribute("android", "name", activityName);
        }
        if (!specifiedConfigChanges) {
            activityTag.addAttribute("android", "configChanges", "orientation|screenSize");
        }
        applicationTag.addChildNode(activityTag);
    }

    private ArrayList<HashMap<String, Object>> getActivityAttrs() {
        String activityAttributesPath = FileUtil.getExternalStorageDir().concat("/.sketchware/data/").concat(c.sc_id).concat("/Injection/androidmanifest/attributes.json");
        if (FileUtil.isExistFile(activityAttributesPath)) {
            try {
                return new Gson().fromJson(FileUtil.readFile(activityAttributesPath), Helper.TYPE_MAP_LIST);
            } catch (Exception ignored) {
            }
        }
        return new ArrayList<>();
    }
}
