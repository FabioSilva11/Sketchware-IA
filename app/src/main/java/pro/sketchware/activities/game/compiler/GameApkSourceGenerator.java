package pro.sketchware.activities.game.compiler;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pro.sketchware.activities.game.model.GameProject;
import pro.sketchware.activities.game.model.GameProjectRepository;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.workspace.ProjectWorkspaceManager;

/**
 * Injects the standalone 2D game runtime, serialized game project JSON, user assets,
 * and manifest orientation into a Sketchware {@link ProjectWorkspaceManager} before compilation.
 */
public final class GameApkSourceGenerator {

    private static final Pattern ACTIVITY_TAG_PATTERN = Pattern.compile("<activity\\b([^>]*)>", Pattern.DOTALL);
    private static final Pattern SCREEN_ORIENTATION_PATTERN = Pattern.compile("android:screenOrientation\\s*=\\s*\"[^\"]*\"");

    private GameApkSourceGenerator() {
    }

    public static void injectGameRuntimeIntoWorkspace(
            String scId,
            ProjectWorkspaceManager workspace,
            GameProject project
    ) {
        if (workspace == null) {
            return;
        }
        String safeScId = scId == null ? workspace.sc_id : scId;
        GameProject effectiveProject = project != null
                ? project
                : GameProjectRepository.loadOrCreate(safeScId);
        effectiveProject.ensureInitialized(safeScId);

        // 1. Copy any user-imported assets into workspace.assetsPath first
        try {
            String userAssetsPath = new FilePathUtil().getPathAssets(safeScId);
            File userAssetsDir = new File(userAssetsPath);
            if (userAssetsDir.exists() && userAssetsDir.isDirectory()) {
                File[] children = userAssetsDir.listFiles();
                if (children != null && children.length > 0) {
                    FileUtil.copyDirectory(userAssetsDir, new File(workspace.assetsPath));
                }
            }
        } catch (Throwable ignored) {
        }

        // 2. Write game_project.json into assets/game/game_project.json
        File gameAssetsDir = new File(workspace.assetsPath, "game");
        if (!gameAssetsDir.exists()) {
            gameAssetsDir.mkdirs();
        }
        File projectJsonFile = new File(gameAssetsDir, "game_project.json");
        writeUtf8File(projectJsonFile, GameProjectRepository.toJson(effectiveProject));

        // 3. Generate standalone MainActivity.java with no external dependencies
        String pkgName = workspace.packageName == null ? "" : workspace.packageName.trim();
        File mainJavaDir = (workspace.packageNameAsFolders == null || workspace.packageNameAsFolders.isEmpty())
                ? new File(workspace.javaFilesPath)
                : new File(workspace.javaFilesPath, workspace.packageNameAsFolders);
        if (!mainJavaDir.exists()) {
            mainJavaDir.mkdirs();
        }
        File mainActivityFile = new File(mainJavaDir, "MainActivity.java");
        writeUtf8File(mainActivityFile, buildMainActivitySource(pkgName, effectiveProject.landscape));

        // 4. Update AndroidManifest.xml screenOrientation on .MainActivity
        updateManifestOrientation(workspace.androidManifestPath, pkgName, effectiveProject.landscape);
    }

    private static void updateManifestOrientation(String manifestPath, String pkgName, boolean landscape) {
        if (manifestPath == null || manifestPath.isEmpty()) {
            return;
        }
        File manifestFile = new File(manifestPath);
        if (!manifestFile.exists() || !manifestFile.isFile()) {
            return;
        }
        String xml = readUtf8File(manifestFile);
        if (xml == null || xml.isEmpty()) {
            return;
        }

        String targetOrientation = landscape ? "landscape" : "portrait";
        String fqMain = pkgName.isEmpty() ? "" : (pkgName + ".MainActivity");
        Matcher matcher = ACTIVITY_TAG_PATTERN.matcher(xml);
        StringBuffer out = new StringBuffer();
        boolean updated = false;

        while (matcher.find()) {
            String attrs = matcher.group(1);
            boolean isMain = containsMainActivityName(attrs, fqMain);
            if (!isMain) {
                matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            updated = true;
            String updatedAttrs;
            Matcher orientMatcher = SCREEN_ORIENTATION_PATTERN.matcher(attrs);
            if (orientMatcher.find()) {
                updatedAttrs = orientMatcher.replaceAll("android:screenOrientation=\"" + targetOrientation + "\"");
            } else {
                String trimmed = attrs;
                boolean selfClosing = false;
                if (trimmed.endsWith("/")) {
                    selfClosing = true;
                    trimmed = trimmed.substring(0, trimmed.length() - 1);
                }
                updatedAttrs = trimmed + " android:screenOrientation=\"" + targetOrientation + "\"" + (selfClosing ? "/" : "");
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement("<activity" + updatedAttrs + ">"));
        }
        matcher.appendTail(out);

        if (updated) {
            writeUtf8File(manifestFile, out.toString());
        }
    }

    private static boolean containsMainActivityName(String attrs, String fqMain) {
        if (attrs == null) {
            return false;
        }
        if (attrs.contains("android:name=\".MainActivity\"")
                || attrs.contains("android:name=\"MainActivity\"")) {
            return true;
        }
        if (!fqMain.isEmpty() && attrs.contains("android:name=\"" + fqMain + "\"")) {
            return true;
        }
        return false;
    }

    private static String readUtf8File(File file) {
        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = fis.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            return bos.toString(StandardCharsets.UTF_8.name());
        } catch (IOException ignored) {
            return "";
        }
    }

    private static void writeUtf8File(File file, String content) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (FileOutputStream fos = new FileOutputStream(file, false)) {
            fos.write((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
            fos.flush();
        } catch (IOException ignored) {
        }
    }

    private static String buildMainActivitySource(String packageName, boolean defaultLandscape) {
        String pkgHeader = packageName.isEmpty() ? "" : ("package " + packageName + ";\n\n");
        StringBuilder rawTemplate = new StringBuilder(163840);
        rawTemplate.append(MAIN_ACTIVITY_BODY_TEMPLATE_PART1);
        rawTemplate.append(MAIN_ACTIVITY_BODY_TEMPLATE_PART2);
        rawTemplate.append(MAIN_ACTIVITY_BODY_TEMPLATE_PART2B);
        rawTemplate.append(MAIN_ACTIVITY_BODY_TEMPLATE_PART3);
        rawTemplate.append(MAIN_ACTIVITY_BODY_TEMPLATE_PART4);
        rawTemplate.append(MAIN_ACTIVITY_BODY_TEMPLATE_PART5);
        String body = rawTemplate.toString()
                .replace("__DEFAULT_LANDSCAPE__", defaultLandscape ? "true" : "false");
        return pkgHeader + body;
    }

    private static final String MAIN_ACTIVITY_BODY_TEMPLATE_PART1 = """
import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {

    private GameSurfaceView gameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
        } catch (Throwable ignored) {
        }
        super.onCreate(savedInstanceState);

        try {
            ActionBar ab = getActionBar();
            if (ab != null) {
                ab.hide();
            }
        } catch (Throwable ignored) {
        }

        GameProjectData project = loadGameProjectFromAssets();
        boolean isLandscape = project != null ? project.landscape : __DEFAULT_LANDSCAPE__;
        setRequestedOrientation(isLandscape
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);

        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = Build.VERSION.SDK_INT >= 30
                    ? 3 /* LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS */
                    : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                getWindow().setStatusBarColor(Color.TRANSPARENT);
                getWindow().setNavigationBarColor(Color.TRANSPARENT);
            } catch (Throwable ignored) {
            }
        }
        try {
            SceneData startScene = project != null ? project.getStartScene() : null;
            int bgColor = startScene != null ? startScene.backgroundColor : 0xFF141A24;
            getWindow().setBackgroundDrawable(new ColorDrawable(bgColor));
        } catch (Throwable ignored) {
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        applyImmersiveFullscreen();

        gameView = new GameSurfaceView(this, project);
        gameView.setFitsSystemWindows(false);
        setContentView(gameView);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersiveFullscreen();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyImmersiveFullscreen();
        if (gameView != null) {
            gameView.setPaused(false);
        }
    }

    @Override
    protected void onPause() {
        if (gameView != null) {
            gameView.setPaused(true);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (gameView != null) {
            gameView.releaseResources();
            gameView = null;
        }
        super.onDestroy();
    }

    private void applyImmersiveFullscreen() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                getWindow().setDecorFitsSystemWindows(false);
                WindowInsetsController controller = getWindow().getInsetsController();
                if (controller != null) {
                    controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    controller.setSystemBarsBehavior(
                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            } catch (Throwable ignored) {
            }
        }
        View decor = getWindow().getDecorView();
        if (decor != null) {
            decor.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
            );
        }
    }

    private GameProjectData loadGameProjectFromAssets() {
        InputStream is = null;
        try {
            is = getAssets().open("game/game_project.json");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            String json = bos.toString("UTF-8");
            return GameProjectData.fromJson(json);
        } catch (Throwable ignored) {
            return GameProjectData.createFallback();
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    // ==================== Lightweight Game Data Models ====================

    static final class BlockData {
        String id = "";
        String opCode = "";
        String spec = "";
        String type = " ";
        int color = 0xFF4A6CD4;
        final ArrayList<String> parameters = new ArrayList<String>();
        final ArrayList<BlockData> paramBlocks = new ArrayList<BlockData>();
        final ArrayList<BlockData> subStack1 = new ArrayList<BlockData>();
        final ArrayList<BlockData> subStack2 = new ArrayList<BlockData>();

        BlockData getParamBlock(int idx) {
            return (idx >= 0 && idx < paramBlocks.size()) ? paramBlocks.get(idx) : null;
        }

        static BlockData fromJson(JSONObject obj) {
            BlockData b = new BlockData();
            if (obj == null) return b;
            b.id = obj.optString("id", "");
            b.opCode = obj.optString("opCode", "");
            b.spec = obj.optString("spec", "");
            b.type = obj.optString("type", " ");
            b.color = obj.optInt("color", 0xFF4A6CD4);
            JSONArray params = obj.optJSONArray("parameters");
            if (params != null) {
                for (int i = 0; i < params.length(); i++) {
                    b.parameters.add(params.optString(i, ""));
                }
            }
            JSONArray pBlocks = obj.optJSONArray("paramBlocks");
            if (pBlocks != null) {
                for (int i = 0; i < pBlocks.length(); i++) {
                    JSONObject child = pBlocks.optJSONObject(i);
                    b.paramBlocks.add(child != null ? fromJson(child) : null);
                }
            }
            JSONArray s1 = obj.optJSONArray("subStack1");
            if (s1 != null) {
                for (int i = 0; i < s1.length(); i++) {
                    JSONObject child = s1.optJSONObject(i);
                    if (child != null) b.subStack1.add(fromJson(child));
                }
            }
            JSONArray s2 = obj.optJSONArray("subStack2");
            if (s2 != null) {
                for (int i = 0; i < s2.length(); i++) {
                    JSONObject child = s2.optJSONObject(i);
                    if (child != null) b.subStack2.add(fromJson(child));
                }
            }
            return b;
        }
    }

    static final class ObjectData {
        String id = "";
        String name = "Object";
        String type = "sprite";
        boolean isHud = false;
        float x = 100f;
        float y = 100f;
        float width = 96f;
        float height = 96f;
        float rotation = 0f;
        float scaleX = 1f;
        float scaleY = 1f;
        float alpha = 1f;
        int zIndex = 0;
        int color = 0xFF4CAF50;
        String spriteResName = "";
        String soundResName = "";
        String fontResName = "";
        String text = "";
        String actionKey = "JUMP";
        String tag = "default";
        boolean physicsEnabled = false;
        boolean isStatic = false;
        boolean isSensor = false;
        boolean circleCollider = false;
        boolean oneWay = false;
        float gravityScale = 1f;
        float bounce = 0f;
        float friction = 0.2f;
        float velocityX = 0f;
        float velocityY = 0f;
        float speed = 120f;
        float moveX = 0f;
        float moveY = 0f;
        float parallaxX = 0.5f;
        float parallaxY = 0.5f;
        float damage = 1f;
        float cooldownSec = 1.5f;
        String prefabName = "";
        String targetScene = "__next__";
        String spriteFrames = "";
        float animFps = 8f;
        boolean destroyOffscreen = false;
        boolean activeOnlyNearCamera = false;
        boolean toggled = false;
        boolean visible = true;

        ObjectData copy() {
            ObjectData c = new ObjectData();
            c.id = id;
            c.name = name;
            c.type = type;
            c.isHud = isHud;
            c.x = x;
            c.y = y;
            c.width = width;
            c.height = height;
            c.rotation = rotation;
            c.scaleX = scaleX;
            c.scaleY = scaleY;
            c.alpha = alpha;
            c.zIndex = zIndex;
            c.color = color;
            c.spriteResName = spriteResName;
            c.soundResName = soundResName;
            c.fontResName = fontResName;
            c.text = text;
            c.actionKey = actionKey;
            c.tag = tag;
            c.physicsEnabled = physicsEnabled;
            c.isStatic = isStatic;
            c.isSensor = isSensor;
            c.circleCollider = circleCollider;
            c.oneWay = oneWay;
            c.gravityScale = gravityScale;
            c.bounce = bounce;
            c.friction = friction;
            c.velocityX = velocityX;
            c.velocityY = velocityY;
            c.speed = speed;
            c.moveX = moveX;
            c.moveY = moveY;
            c.parallaxX = parallaxX;
            c.parallaxY = parallaxY;
            c.damage = damage;
            c.cooldownSec = cooldownSec;
            c.prefabName = prefabName;
            c.targetScene = targetScene;
            c.spriteFrames = spriteFrames;
            c.animFps = animFps;
            c.destroyOffscreen = destroyOffscreen;
            c.activeOnlyNearCamera = activeOnlyNearCamera;
            c.toggled = toggled;
            c.visible = visible;
            return c;
        }

        boolean isHudType() {
            return "hud_panel".equals(type)
                    || "hud_text".equals(type)
                    || "hud_image".equals(type)
                    || "hud_button".equals(type)
                    || "hud_dpad".equals(type)
                    || "hud_joystick".equals(type)
                    || "hud_bar".equals(type)
                    || "hud_touch_area".equals(type)
                    || "hud_dialog".equals(type)
                    || "hud_toggle".equals(type);
        }

        static ObjectData fromJson(JSONObject obj) {
            ObjectData d = new ObjectData();
            if (obj == null) return d;
            d.id = obj.optString("id", "");
            d.name = obj.optString("name", "Object");
            d.type = obj.optString("type", "sprite");
            d.isHud = obj.optBoolean("isHud", false) || d.isHudType();
            d.x = (float) obj.optDouble("x", 100.0);
            d.y = (float) obj.optDouble("y", 100.0);
            d.width = (float) obj.optDouble("width", 96.0);
            d.height = (float) obj.optDouble("height", 96.0);
            d.rotation = (float) obj.optDouble("rotation", 0.0);
            d.scaleX = (float) obj.optDouble("scaleX", 1.0);
            d.scaleY = (float) obj.optDouble("scaleY", 1.0);
            d.alpha = (float) obj.optDouble("alpha", 1.0);
            d.zIndex = obj.optInt("zIndex", 0);
            d.color = obj.optInt("color", 0xFF4CAF50);
            d.spriteResName = obj.optString("spriteResName", "");
            d.soundResName = obj.optString("soundResName", "");
            d.fontResName = obj.optString("fontResName", "");
            d.text = obj.optString("text", "");
            d.actionKey = obj.optString("actionKey", "JUMP");
            d.tag = obj.optString("tag", "default");
            d.physicsEnabled = obj.optBoolean("physicsEnabled", false);
            d.isStatic = obj.optBoolean("isStatic", false);
            d.isSensor = obj.optBoolean("isSensor", false);
            d.circleCollider = obj.optBoolean("circleCollider", "physics_circle".equals(d.type));
            d.oneWay = obj.optBoolean("oneWay", "oneway_platform".equals(d.type));
            d.gravityScale = (float) obj.optDouble("gravityScale", 1.0);
            d.bounce = (float) obj.optDouble("bounce", 0.0);
            d.friction = (float) obj.optDouble("friction", 0.2);
            d.velocityX = (float) obj.optDouble("velocityX", 0.0);
            d.velocityY = (float) obj.optDouble("velocityY", 0.0);
            d.speed = (float) obj.optDouble("speed", 120.0);
            d.moveX = (float) obj.optDouble("moveX", 0.0);
            d.moveY = (float) obj.optDouble("moveY", 0.0);
            d.parallaxX = (float) obj.optDouble("parallaxX", 0.5);
            d.parallaxY = (float) obj.optDouble("parallaxY", 0.5);
            d.damage = (float) obj.optDouble("damage", 1.0);
            d.cooldownSec = (float) obj.optDouble("cooldownSec", 1.5);
            d.prefabName = obj.optString("prefabName", "");
            d.targetScene = obj.optString("targetScene", "__next__");
            d.spriteFrames = obj.optString("spriteFrames", "");
            d.animFps = (float) obj.optDouble("animFps", 8.0);
            d.destroyOffscreen = obj.optBoolean("destroyOffscreen", false);
            d.activeOnlyNearCamera = obj.optBoolean("activeOnlyNearCamera", false);
            d.toggled = obj.optBoolean("toggled", false);
            d.visible = obj.optBoolean("visible", true);
            return d;
        }
    }

    static final class ScriptData {
        String id = "";
        String targetObjectId = "__scene__";
        String eventType = "on_scene_start";
        String eventParam = "";
        final ArrayList<BlockData> blocks = new ArrayList<BlockData>();

        static ScriptData fromJson(JSONObject obj) {
            ScriptData s = new ScriptData();
            if (obj == null) return s;
            s.id = obj.optString("id", "");
            s.targetObjectId = obj.optString("targetObjectId", "__scene__");
            s.eventType = obj.optString("eventType", "on_scene_start");
            s.eventParam = obj.optString("eventParam", "");
            JSONArray arr = obj.optJSONArray("blocks");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject child = arr.optJSONObject(i);
                    if (child != null) s.blocks.add(BlockData.fromJson(child));
                }
            }
            return s;
        }
    }

    static final class SceneData {
        String id = "scene_1";
        String name = "Scene 1";
        int backgroundColor = 0xFF141A24;
        String backgroundSprite = "";
        String bgmSound = "";
        float gravityY = 980f;
        float worldWidth = 0f;
        float worldHeight = 0f;
        String worldBoundsMode = "free";
        float ambientDarkness = 0f;
        final ArrayList<ObjectData> objects = new ArrayList<ObjectData>();
        final ArrayList<ScriptData> scripts = new ArrayList<ScriptData>();

        float getEffectiveWorldWidth(GameProjectData p) {
            int vw = p != null ? Math.max(1, p.virtualWidth) : 1280;
            return worldWidth > 0f ? Math.max(vw, worldWidth) : vw;
        }

        float getEffectiveWorldHeight(GameProjectData p) {
            int vh = p != null ? Math.max(1, p.virtualHeight) : 720;
            return worldHeight > 0f ? Math.max(vh, worldHeight) : vh;
        }

        static SceneData fromJson(JSONObject obj) {
            SceneData sc = new SceneData();
            if (obj == null) return sc;
            sc.id = obj.optString("id", "scene_1");
            sc.name = obj.optString("name", "Scene 1");
            sc.backgroundColor = obj.optInt("backgroundColor", 0xFF141A24);
            sc.backgroundSprite = obj.optString("backgroundSprite", "");
            sc.bgmSound = obj.optString("bgmSound", "");
            sc.gravityY = (float) obj.optDouble("gravityY", 980.0);
            sc.worldWidth = (float) obj.optDouble("worldWidth", 0.0);
            sc.worldHeight = (float) obj.optDouble("worldHeight", 0.0);
            sc.worldBoundsMode = obj.optString("worldBoundsMode", "free");
            sc.ambientDarkness = (float) obj.optDouble("ambientDarkness", 0.0);
            JSONArray objs = obj.optJSONArray("objects");
            if (objs != null) {
                for (int i = 0; i < objs.length(); i++) {
                    JSONObject child = objs.optJSONObject(i);
                    if (child != null) sc.objects.add(ObjectData.fromJson(child));
                }
            }
            JSONArray scrs = obj.optJSONArray("scripts");
            if (scrs != null) {
                for (int i = 0; i < scrs.length(); i++) {
                    JSONObject child = scrs.optJSONObject(i);
                    if (child != null) sc.scripts.add(ScriptData.fromJson(child));
                }
            }
            return sc;
        }
    }

    static final class GameProjectData {
        int virtualWidth = 1280;
        int virtualHeight = 720;
        boolean landscape = true;
        int targetFps = 60;
        boolean showDebugHitboxes = false;
        String startSceneId = "scene_1";
        final ArrayList<SceneData> scenes = new ArrayList<SceneData>();
        final ArrayList<ObjectData> prefabs = new ArrayList<ObjectData>();
        final ArrayList<ScriptData> prefabScripts = new ArrayList<ScriptData>();
        final HashMap<String, String> globalVariables = new HashMap<String, String>();

        static GameProjectData createFallback() {
            GameProjectData p = new GameProjectData();
            p.scenes.add(new SceneData());
            return p;
        }

        static GameProjectData fromJson(String json) {
            try {
                JSONObject obj = new JSONObject(json);
                GameProjectData p = new GameProjectData();
                p.landscape = obj.optBoolean("landscape", true);
                p.virtualWidth = Math.max(1, obj.optInt("virtualWidth", p.landscape ? 1280 : 720));
                p.virtualHeight = Math.max(1, obj.optInt("virtualHeight", p.landscape ? 720 : 1280));
                p.targetFps = Math.max(15, obj.optInt("targetFps", 60));
                p.showDebugHitboxes = obj.optBoolean("showDebugHitboxes", false);
                p.startSceneId = obj.optString("startSceneId", "scene_1");

                JSONArray scenesArr = obj.optJSONArray("scenes");
                if (scenesArr != null) {
                    for (int i = 0; i < scenesArr.length(); i++) {
                        JSONObject scObj = scenesArr.optJSONObject(i);
                        if (scObj != null) p.scenes.add(SceneData.fromJson(scObj));
                    }
                }
                JSONArray prefabsArr = obj.optJSONArray("prefabs");
                if (prefabsArr != null) {
                    for (int i = 0; i < prefabsArr.length(); i++) {
                        JSONObject pfObj = prefabsArr.optJSONObject(i);
                        if (pfObj != null) p.prefabs.add(ObjectData.fromJson(pfObj));
                    }
                }
                JSONArray prefabScriptsArr = obj.optJSONArray("prefabScripts");
                if (prefabScriptsArr != null) {
                    for (int i = 0; i < prefabScriptsArr.length(); i++) {
                        JSONObject psObj = prefabScriptsArr.optJSONObject(i);
                        if (psObj != null) p.prefabScripts.add(ScriptData.fromJson(psObj));
                    }
                }
                JSONObject varsObj = obj.optJSONObject("globalVariables");
                if (varsObj != null) {
                    Iterator<String> keys = varsObj.keys();
                    while (keys.hasNext()) {
                        String k = keys.next();
                        p.globalVariables.put(k, varsObj.optString(k, ""));
                    }
                }
                if (p.scenes.isEmpty()) {
                    p.scenes.add(new SceneData());
                }
                return p;
            } catch (Throwable ignored) {
                return createFallback();
            }
        }

        SceneData getSceneByIdOrName(String idOrName) {
            if (idOrName != null) {
                for (SceneData s : scenes) {
                    if (idOrName.equals(s.id) || idOrName.equalsIgnoreCase(s.name)) {
                        return s;
                    }
                }
            }
            return null;
        }

        SceneData getStartScene() {
            SceneData start = getSceneByIdOrName(startSceneId);
            if (start != null) return start;
            return scenes.isEmpty() ? new SceneData() : scenes.get(0);
        }
    }

    // ==================== Standalone 2D Runtime SurfaceView ====================

    static final class GameSurfaceView extends SurfaceView implements SurfaceHolder.Callback {

        private static final float FIXED_DT = 1f / 60f;
        private static final int MAX_SUBSTEPS = 4;
        private static final int MAX_PARTICLES = 320;
        private static final int MAX_FIBER_OPS = 400;
        private static final float MAX_FALL_SPEED = 1600f;
        private static final float TRANSITION_DURATION = 0.28f;

        private final Context appContext;
        private final GameProjectData project;
        private final Object stateLock = new Object();
        private final Random random = new Random();

        private volatile int surfaceWidth = 1;
        private volatile int surfaceHeight = 1;
        private volatile float viewScale = 1f;
        private volatile float viewOffsetX = 0f;
        private volatile float viewOffsetY = 0f;

        private LoopThread loopThread;
        private volatile boolean surfaceReady = false;
        private volatile boolean paused = false;
        private volatile boolean released = false;
        private volatile int currentFps = 60;

        private SceneData currentScene;
        private final ArrayList<RuntimeEntity> objects = new ArrayList<RuntimeEntity>();
        private final ArrayList<ScriptData> sceneScripts = new ArrayList<ScriptData>();
        private final HashMap<String, String> variables = new HashMap<String, String>();
        private final ArrayList<Fiber> activeFibers = new ArrayList<Fiber>();
        private final ArrayList<ParticleData> particles = new ArrayList<ParticleData>();
        private final ArrayList<QueuedEvent> pendingEvents = new ArrayList<QueuedEvent>();
        private String pendingSceneLoadId = null;
        private boolean pendingSceneRestart = false;

        private String transitionPhase = "";
        private String transitionType = "";
        private String transitionTargetSceneId = "";
        private float transitionProgress = 0f;

        private float camX = 0f;
        private float camY = 0f;
        private String camFollowTargetId = "";
        private float camFollowLerp = 0.15f;
        private float shakeIntensity = 0f;
        private float shakeDuration = 0f;
        private float shakeRemaining = 0f;
        private float gameTimerSeconds = 0f;
        private float masterVolumePercent = 100f;

        private int joystickPointerId = -1;
        private String joystickObjectId = "";
        private int dpadPointerId = -1;
        private String dpadObjectId = "";
        private String dpadDirectionKey = "";
        private volatile float joyX = 0f;
        private volatile float joyY = 0f;
        private volatile float touchX = 0f;
        private volatile float touchY = 0f;
        private volatile boolean isTouchingWorld = false;
        private int worldTouchPointerId = -1;
        private final SparseArray<String> pointerButtons = new SparseArray<String>();
        private final Set<String> pressedButtons = new HashSet<String>();
        private final Set<String> latchedButtons = new HashSet<String>();
        private final Set<String> justPressedButtons = new HashSet<String>();

        private final HashMap<String, Bitmap> bitmapCache = new HashMap<String, Bitmap>();
        private final HashMap<String, Typeface> fontCache = new HashMap<String, Typeface>();
        private final HashMap<String, Integer> soundIdMap = new HashMap<String, Integer>();
        private final Set<Integer> loadedSoundIds = new HashSet<Integer>();
        private final Set<Integer> pendingPlaySoundIds = new HashSet<Integer>();
        private SoundPool soundPool;
        private MediaPlayer bgmPlayer;
        private String currentBgmResName = "";
        private boolean bgmWantedPlaying = false;
        private String[] cachedAssetFonts = null;

        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint lightCutPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF tempRect = new RectF();
        private final Path speechBubblePath = new Path();
        private final Path tempPath = new Path();
        private final ArrayList<RuntimeEntity> drawWorldScratch = new ArrayList<RuntimeEntity>();
        private final ArrayList<RuntimeEntity> drawHudScratch = new ArrayList<RuntimeEntity>();
        private int ambientCounter = 0;
        private int nextSpawnOrder = 0;

        GameSurfaceView(Context context, GameProjectData project) {
            super(context);
            this.appContext = context.getApplicationContext();
            this.project = project != null ? project : GameProjectData.createFallback();
            initSoundPool();
            getHolder().addCallback(this);
            setFocusable(true);
            setFocusableInTouchMode(true);
            synchronized (stateLock) {
                loadSceneInternal(this.project.startSceneId);
            }
        }

        private void initSoundPool() {
            if (Build.VERSION.SDK_INT >= 21) {
                AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                soundPool = new SoundPool.Builder()
                        .setMaxStreams(8)
                        .setAudioAttributes(attrs)
                        .build();
            } else {
                soundPool = new SoundPool(8, android.media.AudioManager.STREAM_MUSIC, 0);
            }
            soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
                @Override
                public void onLoadComplete(SoundPool pool, int sampleId, int status) {
                    if (status == 0) {
                        synchronized (stateLock) {
                            loadedSoundIds.add(sampleId);
                            if (pendingPlaySoundIds.remove(sampleId)) {
                                float vol = clamp(masterVolumePercent / 100f, 0f, 1f);
                                pool.play(sampleId, vol, vol, 1, 0, 1f);
                            }
                        }
                    }
                }
            });
        }

        void setPaused(boolean paused) {
            this.paused = paused;
            synchronized (stateLock) {
                if (bgmPlayer != null) {
                    try {
                        if (paused && bgmPlayer.isPlaying()) {
                            bgmPlayer.pause();
                        } else if (!paused && bgmWantedPlaying && !bgmPlayer.isPlaying()) {
                            bgmPlayer.start();
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        }

        void releaseResources() {
            released = true;
            stopLoopThread();
            synchronized (stateLock) {
                stopBgmInternal();
                if (soundPool != null) {
                    try {
                        soundPool.release();
                    } catch (Throwable ignored) {
                    }
                    soundPool = null;
                }
                soundIdMap.clear();
                loadedSoundIds.clear();
                pendingPlaySoundIds.clear();
                for (Bitmap bmp : bitmapCache.values()) {
                    if (bmp != null && !bmp.isRecycled()) {
                        bmp.recycle();
                    }
                }
                bitmapCache.clear();
                fontCache.clear();
            }
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            surfaceReady = true;
            if (!released) {
                startLoopThread();
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            surfaceWidth = Math.max(1, width);
            surfaceHeight = Math.max(1, height);
            int vw = Math.max(1, project.virtualWidth);
            int vh = Math.max(1, project.virtualHeight);
            float scale = Math.min(surfaceWidth / (float) vw, surfaceHeight / (float) vh);
            if (scale <= 0f) scale = 1f;
            viewScale = scale;
            viewOffsetX = (surfaceWidth - vw * scale) * 0.5f;
            viewOffsetY = (surfaceHeight - vh * scale) * 0.5f;
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            surfaceReady = false;
            stopLoopThread();
        }

        private void startLoopThread() {
            if (loopThread != null && loopThread.isAlive()) return;
            loopThread = new LoopThread();
            loopThread.running = true;
            loopThread.start();
        }

        private void stopLoopThread() {
            LoopThread t = loopThread;
            if (t != null) {
                t.running = false;
                try {
                    t.join(600);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                loopThread = null;
            }
        }

        private final class LoopThread extends Thread {
            volatile boolean running = false;

            @Override
            public void run() {
                long lastNs = System.nanoTime();
                float accumulator = 0f;
                long fpsWindowStart = lastNs;
                int frames = 0;

                while (running && surfaceReady && !released) {
                    long startNs = System.nanoTime();
                    float dt = (startNs - lastNs) / 1000000000f;
                    lastNs = startNs;
                    if (dt < 0f) dt = 0f;
                    if (dt > 0.05f) dt = 0.05f;

                    if (!paused) {
                        accumulator += dt;
                        int steps = 0;
                        synchronized (stateLock) {
                            while (accumulator >= FIXED_DT && steps < MAX_SUBSTEPS) {
                                stepFixed(FIXED_DT);
                                accumulator -= FIXED_DT;
                                steps++;
                            }
                            if (steps >= MAX_SUBSTEPS) accumulator = 0f;
                        }
                    } else {
                        accumulator = 0f;
                    }

                    Canvas canvas = null;
                    SurfaceHolder holder = getHolder();
                    try {
                        canvas = holder.lockCanvas();
                        if (canvas != null) {
                            synchronized (stateLock) {
                                renderScene(canvas);
                            }
                        }
                    } catch (Throwable ignored) {
                    } finally {
                        if (canvas != null) {
                            try {
                                holder.unlockCanvasAndPost(canvas);
                            } catch (Throwable ignored) {
                            }
                        }
                    }

                    frames++;
                    long nowNs = System.nanoTime();
                    long fpsElapsed = nowNs - fpsWindowStart;
                    if (fpsElapsed >= 500000000L) {
                        currentFps = Math.round(frames * 1000000000f / fpsElapsed);
                        frames = 0;
                        fpsWindowStart = nowNs;
                    }

                    int targetFps = Math.max(15, Math.min(120, project.targetFps));
                    long targetNs = 1000000000L / targetFps;
                    long sleepNs = targetNs - (nowNs - startNs);
                    if (sleepNs > 1000000L) {
                        try {
                            Thread.sleep(sleepNs / 1000000L, (int) (sleepNs % 1000000L));
                        } catch (InterruptedException ignored) {
                            break;
                        }
                    }
                }
            }
        }

        private float getWorldWidth() {
            return currentScene != null
                    ? currentScene.getEffectiveWorldWidth(project)
                    : Math.max(1, project.virtualWidth);
        }

        private float getWorldHeight() {
            return currentScene != null
                    ? currentScene.getEffectiveWorldHeight(project)
                    : Math.max(1, project.virtualHeight);
        }

        private void loadSceneInternal(String sceneId) {
            SceneData target = project.getSceneByIdOrName(sceneId);
            if (target == null) target = project.getStartScene();
            currentScene = target;

            objects.clear();
            for (ObjectData bean : target.objects) {
                if (bean != null) objects.add(new RuntimeEntity(bean));
            }
            sceneScripts.clear();
            sceneScripts.addAll(target.scripts);
            if (project.prefabScripts != null) {
                sceneScripts.addAll(project.prefabScripts);
            }

            if (variables.isEmpty() || (currentScene.id.equals(project.startSceneId) && pendingSceneLoadId == null && transitionTargetSceneId.isEmpty())) {
                variables.clear();
                variables.putAll(project.globalVariables);
                gameTimerSeconds = 0f;
            }

            activeFibers.clear();
            particles.clear();
            pendingEvents.clear();
            pendingSceneLoadId = null;
            pendingSceneRestart = false;

            camX = 0f;
            camY = 0f;
            camFollowTargetId = "";
            camFollowLerp = 0.15f;
            shakeIntensity = 0f;
            shakeDuration = 0f;
            shakeRemaining = 0f;

            joystickPointerId = -1;
            joystickObjectId = "";
            dpadPointerId = -1;
            dpadObjectId = "";
            dpadDirectionKey = "";
            joyX = 0f;
            joyY = 0f;
            isTouchingWorld = false;
            worldTouchPointerId = -1;
            pointerButtons.clear();
            pressedButtons.clear();
            latchedButtons.clear();
            justPressedButtons.clear();

            float maxCamX = Math.max(0f, getWorldWidth() - project.virtualWidth);
            float maxCamY = Math.max(0f, getWorldHeight() - project.virtualHeight);
            for (RuntimeEntity obj : objects) {
                if ("camera".equals(obj.bean.type)) {
                    camX = clamp(obj.bean.x - project.virtualWidth * 0.5f + obj.bean.width * 0.5f, 0f, maxCamX);
                    camY = clamp(obj.bean.y - project.virtualHeight * 0.5f + obj.bean.height * 0.5f, 0f, maxCamY);
                    break;
                }
            }
            for (RuntimeEntity obj : objects) {
                if (!obj.isHud() && isInCameraViewport(obj, 0f)) {
                    obj.enteredScreen = true;
                }
            }

            if (target.bgmSound != null && !target.bgmSound.isEmpty()) {
                playBgmInternal(target.bgmSound, true);
            } else {
                stopBgmInternal();
            }

            enqueueScriptsForEvent("on_scene_start", "", null, null);
            stepFibers(0f);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (released) return false;
            int action = event.getActionMasked();
            int idx = event.getActionIndex();
            synchronized (stateLock) {
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_POINTER_DOWN: {
                        int pid = event.getPointerId(idx);
                        float vx = (event.getX(idx) - viewOffsetX) / Math.max(0.0001f, viewScale);
                        float vy = (event.getY(idx) - viewOffsetY) / Math.max(0.0001f, viewScale);
                        handlePointerDown(pid, vx, vy);
                        break;
                    }
                    case MotionEvent.ACTION_MOVE: {
                        int count = event.getPointerCount();
                        for (int i = 0; i < count; i++) {
                            int pid = event.getPointerId(i);
                            float vx = (event.getX(i) - viewOffsetX) / Math.max(0.0001f, viewScale);
                            float vy = (event.getY(i) - viewOffsetY) / Math.max(0.0001f, viewScale);
                            handlePointerMove(pid, vx, vy);
                        }
                        break;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_POINTER_UP: {
                        handlePointerUp(event.getPointerId(idx));
                        break;
                    }
                    case MotionEvent.ACTION_CANCEL: {
                        joystickPointerId = -1;
                        joystickObjectId = "";
                        dpadPointerId = -1;
                        dpadObjectId = "";
                        dpadDirectionKey = "";
                        joyX = 0f;
                        joyY = 0f;
                        worldTouchPointerId = -1;
                        isTouchingWorld = false;
                        pointerButtons.clear();
                        rebuildPressedButtons();
                        break;
                    }
                    default:
                        break;
                }
            }
            return true;
        }
""";

    private static final String MAIN_ACTIVITY_BODY_TEMPLATE_PART2 = """
        private void handlePointerDown(int pointerId, float vx, float vy) {
            ArrayList<RuntimeEntity> hudList = getHudObjectsSorted();
            for (int i = hudList.size() - 1; i >= 0; i--) {
                RuntimeEntity hud = hudList.get(i);
                if (!hud.bean.visible || hud.destroyed || hud.bean.alpha <= 0f) continue;
                if ("hud_joystick".equals(hud.bean.type)) {
                    float cx = hud.bean.x + hud.bean.width * 0.5f;
                    float cy = hud.bean.y + hud.bean.height * 0.5f;
                    float hitR = Math.max(hud.bean.width, hud.bean.height) * 0.65f;
                    float dx = vx - cx;
                    float dy = vy - cy;
                    if (dx * dx + dy * dy <= hitR * hitR && joystickPointerId == -1) {
                        joystickPointerId = pointerId;
                        joystickObjectId = hud.bean.id;
                        updateJoystick(hud, vx, vy);
                        return;
                    }
                } else if ("hud_dpad".equals(hud.bean.type)) {
                    if (vx >= hud.bean.x - 8f && vx <= hud.bean.x + hud.bean.width + 8f
                            && vy >= hud.bean.y - 8f && vy <= hud.bean.y + hud.bean.height + 8f && dpadPointerId == -1) {
                        dpadPointerId = pointerId;
                        dpadObjectId = hud.bean.id;
                        updateDpadFromPoint(hud, vx, vy, true);
                        pendingEvents.add(new QueuedEvent("on_touch", "", hud.bean.id));
                        return;
                    }
                } else if ("hud_dialog".equals(hud.bean.type)) {
                    if (vx >= hud.bean.x && vx <= hud.bean.x + hud.bean.width
                            && vy >= hud.bean.y && vy <= hud.bean.y + hud.bean.height) {
                        int totalLen = hud.bean.text != null ? hud.bean.text.length() : 0;
                        if (hud.dialogVisibleChars < totalLen) {
                            hud.dialogVisibleChars = totalLen;
                        } else {
                            hud.bean.visible = false;
                        }
                        pendingEvents.add(new QueuedEvent("on_touch", "", hud.bean.id));
                        return;
                    }
                } else if ("hud_toggle".equals(hud.bean.type)) {
                    float pad = 10f;
                    if (vx >= hud.bean.x - pad && vx <= hud.bean.x + hud.bean.width + pad
                            && vy >= hud.bean.y - pad && vy <= hud.bean.y + hud.bean.height + pad) {
                        hud.bean.toggled = !hud.bean.toggled;
                        String key = normalizeActionKey(hud.bean.actionKey);
                        if (!key.isEmpty()) {
                            if (hud.bean.toggled) {
                                justPressedButtons.add(key);
                                latchedButtons.add(key);
                            }
                            pendingEvents.add(new QueuedEvent("on_button", key, null));
                        }
                        rebuildPressedButtons();
                        pendingEvents.add(new QueuedEvent("on_touch", "", hud.bean.id));
                        return;
                    }
                } else if ("hud_button".equals(hud.bean.type) || "hud_touch_area".equals(hud.bean.type)) {
                    float pad = "hud_button".equals(hud.bean.type) ? 12f : 0f;
                    if (vx >= hud.bean.x - pad && vx <= hud.bean.x + hud.bean.width + pad
                            && vy >= hud.bean.y - pad && vy <= hud.bean.y + hud.bean.height + pad) {
                        String key = normalizeActionKey(hud.bean.actionKey);
                        pointerButtons.put(pointerId, key);
                        rebuildPressedButtons();
                        if (!key.isEmpty()) {
                            latchedButtons.add(key);
                            justPressedButtons.add(key);
                            pendingEvents.add(new QueuedEvent("on_button", key, null));
                        }
                        pendingEvents.add(new QueuedEvent("on_touch", "", hud.bean.id));
                        return;
                    }
                }
            }

            float wx = vx + camX;
            float wy = vy + camY;
            touchX = wx;
            touchY = wy;
            isTouchingWorld = true;
            worldTouchPointerId = pointerId;

            RuntimeEntity hitWorld = findTopWorldObjectAt(wx, wy);
            if (hitWorld != null) {
                pendingEvents.add(new QueuedEvent("on_touch", "", hitWorld.bean.id));
            }
            pendingEvents.add(new QueuedEvent("on_touch", "", "__scene__"));
        }

        private void handlePointerMove(int pointerId, float vx, float vy) {
            if (pointerId == joystickPointerId) {
                RuntimeEntity joy = findById(joystickObjectId);
                if (joy != null) updateJoystick(joy, vx, vy);
            } else if (pointerId == dpadPointerId) {
                RuntimeEntity dpad = findById(dpadObjectId);
                if (dpad != null) updateDpadFromPoint(dpad, vx, vy, false);
            } else if (pointerId == worldTouchPointerId) {
                touchX = vx + camX;
                touchY = vy + camY;
            }
        }

        private void handlePointerUp(int pointerId) {
            if (pointerId == joystickPointerId) {
                joystickPointerId = -1;
                joystickObjectId = "";
                joyX = 0f;
                joyY = 0f;
            }
            if (pointerId == dpadPointerId) {
                dpadPointerId = -1;
                dpadObjectId = "";
                dpadDirectionKey = "";
                if (joystickPointerId == -1) {
                    joyX = 0f;
                    joyY = 0f;
                }
                rebuildPressedButtons();
            }
            if (pointerButtons.indexOfKey(pointerId) >= 0) {
                pointerButtons.remove(pointerId);
                rebuildPressedButtons();
            }
            if (pointerId == worldTouchPointerId) {
                worldTouchPointerId = -1;
                isTouchingWorld = false;
            }
        }

        private void rebuildPressedButtons() {
            pressedButtons.clear();
            for (int i = 0; i < pointerButtons.size(); i++) {
                String k = pointerButtons.valueAt(i);
                if (k != null && !k.isEmpty()) pressedButtons.add(k);
            }
            if (!dpadDirectionKey.isEmpty()) {
                pressedButtons.add(dpadDirectionKey);
            }
            for (RuntimeEntity o : objects) {
                if (!o.destroyed && "hud_toggle".equals(o.bean.type) && o.bean.toggled) {
                    String tk = normalizeActionKey(o.bean.actionKey);
                    if (!tk.isEmpty()) pressedButtons.add(tk);
                }
            }
        }

        private void updateDpadFromPoint(RuntimeEntity dpadObj, float vx, float vy, boolean initial) {
            float cx = dpadObj.bean.x + dpadObj.bean.width * 0.5f;
            float cy = dpadObj.bean.y + dpadObj.bean.height * 0.5f;
            float dx = vx - cx;
            float dy = vy - cy;
            float dead = Math.min(dpadObj.bean.width, dpadObj.bean.height) * 0.14f;
            String nextDir = "";
            float jx = 0f;
            float jy = 0f;
            if (Math.hypot(dx, dy) > dead) {
                if (Math.abs(dx) >= Math.abs(dy)) {
                    nextDir = dx < 0f ? "LEFT" : "RIGHT";
                    jx = dx < 0f ? -1f : 1f;
                } else {
                    nextDir = dy < 0f ? "UP" : "DOWN";
                    jy = dy < 0f ? -1f : 1f;
                }
            }
            if (joystickPointerId == -1) {
                joyX = jx;
                joyY = jy;
            }
            boolean changed = !nextDir.equals(dpadDirectionKey);
            dpadDirectionKey = nextDir;
            rebuildPressedButtons();
            if (!nextDir.isEmpty() && (changed || initial)) {
                latchedButtons.add(nextDir);
                justPressedButtons.add(nextDir);
                pendingEvents.add(new QueuedEvent("on_button", nextDir, null));
            }
        }

        private void updateJoystick(RuntimeEntity joyObj, float vx, float vy) {
            float cx = joyObj.bean.x + joyObj.bean.width * 0.5f;
            float cy = joyObj.bean.y + joyObj.bean.height * 0.5f;
            float maxR = Math.max(16f, Math.min(joyObj.bean.width, joyObj.bean.height) * 0.42f);
            float dx = vx - cx;
            float dy = vy - cy;
            float dist = (float) Math.hypot(dx, dy);
            if (dist > maxR) {
                dx = dx / dist * maxR;
                dy = dy / dist * maxR;
            }
            joyX = clamp(dx / maxR, -1f, 1f);
            joyY = clamp(dy / maxR, -1f, 1f);
        }

        private RuntimeEntity findTopWorldObjectAt(float wx, float wy) {
            ArrayList<RuntimeEntity> world = getWorldObjectsSorted();
            for (int i = world.size() - 1; i >= 0; i--) {
                RuntimeEntity o = world.get(i);
                if (!o.bean.visible || o.destroyed || o.bean.alpha <= 0f) continue;
                if (wx >= o.bean.x && wx <= o.bean.x + o.bean.width
                        && wy >= o.bean.y && wy <= o.bean.y + o.bean.height) {
                    return o;
                }
            }
            return null;
        }

        private void stepFixed(float dt) {
            if (currentScene == null) return;

            if (!transitionPhase.isEmpty()) {
                stepTransition(dt);
                if ("out".equals(transitionPhase)) {
                    return;
                }
            }

            gameTimerSeconds += dt;

            if (!pendingEvents.isEmpty()) {
                for (QueuedEvent qe : pendingEvents) {
                    if (qe.targetObjectId != null) {
                        RuntimeEntity targetObj = findById(qe.targetObjectId);
                        enqueueScriptsForTarget(qe.eventType, qe.eventParam, qe.targetObjectId, targetObj, null);
                    } else {
                        enqueueScriptsForEvent(qe.eventType, qe.eventParam, null, null);
                    }
                }
                pendingEvents.clear();
            }

            for (RuntimeEntity o : objects) {
                o.prevX = o.bean.x;
                o.prevY = o.bean.y;
                if (!o.enteredScreen && !o.isHud() && isInCameraViewport(o, 0f)) {
                    o.enteredScreen = true;
                }
                if (o.hitCooldown > 0f) {
                    o.hitCooldown = Math.max(0f, o.hitCooldown - dt);
                }
                if (o.speechTimer > 0f) {
                    o.speechTimer = Math.max(0f, o.speechTimer - dt);
                    if (o.speechTimer <= 0f) {
                        o.speechText = "";
                    }
                }
                if (o.glideActive) {
                    o.glideElapsed += dt;
                    float t = o.glideDuration > 0.0001f ? clamp(o.glideElapsed / o.glideDuration, 0f, 1f) : 1f;
                    o.bean.x = o.glideStartX + (o.glideTargetX - o.glideStartX) * t;
                    o.bean.y = o.glideStartY + (o.glideTargetY - o.glideStartY) * t;
                    if (t >= 1f) {
                        o.glideActive = false;
                    }
                }
            }

            stepObjectBehaviors(dt);
            enqueueUpdateScripts();
            stepFibers(dt);
            latchedButtons.clear();
            justPressedButtons.clear();
            stepPhysics(dt);
            stepParticles(dt);
            stepCamera(dt);

            for (int i = objects.size() - 1; i >= 0; i--) {
                if (objects.get(i).destroyed) {
                    objects.remove(i);
                }
            }

            if (pendingSceneRestart) {
                String id = currentScene.id;
                pendingSceneRestart = false;
                pendingSceneLoadId = null;
                loadSceneInternal(id);
            } else if (pendingSceneLoadId != null) {
                String next = pendingSceneLoadId;
                pendingSceneLoadId = null;
                loadSceneInternal(next);
            }
        }

        private void stepTransition(float dt) {
            float step = dt / Math.max(0.05f, TRANSITION_DURATION);
            if ("out".equals(transitionPhase)) {
                transitionProgress = Math.min(1f, transitionProgress + step);
                if (transitionProgress >= 1f) {
                    String nextId = transitionTargetSceneId;
                    transitionTargetSceneId = "";
                    transitionPhase = "in";
                    if (nextId != null && !nextId.isEmpty()) {
                        loadSceneInternal(nextId);
                    }
                }
            } else if ("in".equals(transitionPhase)) {
                transitionProgress = Math.max(0f, transitionProgress - step);
                if (transitionProgress <= 0f) {
                    transitionPhase = "";
                    transitionType = "";
                    transitionProgress = 0f;
                }
            } else {
                transitionPhase = "";
                transitionProgress = 0f;
            }
        }

        private void stepObjectBehaviors(float dt) {
            ArrayList<RuntimeEntity> snapshot = new ArrayList<RuntimeEntity>(objects);
            for (RuntimeEntity obj : snapshot) {
                if (obj.destroyed) continue;
                ObjectData b = obj.bean;
                if (!obj.isHud() && b.activeOnlyNearCamera && !isInCameraViewport(obj, 360f)) {
                    continue;
                }
                if ("moving_platform".equals(b.type)) {
                    float spd = Math.max(1f, b.speed);
                    float rangeX = Math.abs(b.moveX);
                    float rangeY = Math.abs(b.moveY);
                    if (rangeX > 0.5f || rangeY > 0.5f) {
                        float oldX = b.x;
                        float oldY = b.y;
                        float dist = (float) Math.hypot(b.moveX, b.moveY);
                        float fracStep = (spd * dt) / Math.max(1f, dist);
                        float nextT = clamp(obj.patrolDir * fracStep, -1f, 1f);
                        b.x += b.moveX * nextT;
                        b.y += b.moveY * nextT;
                        float dxFromStart = b.x - obj.startX;
                        float dyFromStart = b.y - obj.startY;
                        float dot = dxFromStart * b.moveX + dyFromStart * b.moveY;
                        float lenSq = b.moveX * b.moveX + b.moveY * b.moveY;
                        if (obj.patrolDir > 0f && dot >= lenSq) {
                            b.x = obj.startX + b.moveX;
                            b.y = obj.startY + b.moveY;
                            obj.patrolDir = -1f;
                        } else if (obj.patrolDir < 0f && dot <= 0f) {
                            b.x = obj.startX;
                            b.y = obj.startY;
                            obj.patrolDir = 1f;
                        }
                        float platDx = b.x - oldX;
                        float platDy = b.y - oldY;
                        if (platDx != 0f || platDy != 0f) {
                            carryRidersOnPlatform(obj, oldX, oldY, platDx, platDy);
                        }
                    }
                } else if ("parallax_bg".equals(b.type)) {
                    obj.scrollOffsetX += b.moveX * dt;
                    obj.scrollOffsetY += b.moveY * dt;
                } else if ("animated_sprite".equals(b.type)) {
                    if (b.spriteFrames != null && !b.spriteFrames.trim().isEmpty()) {
                        String[] parts = b.spriteFrames.split(",");
                        ArrayList<String> valid = new ArrayList<String>();
                        for (String p : parts) {
                            String trimmed = p.trim();
                            if (!trimmed.isEmpty()) valid.add(trimmed);
                        }
                        if (!valid.isEmpty()) {
                            float frameDur = 1f / Math.max(0.5f, b.animFps);
                            obj.animTimer += dt;
                            while (obj.animTimer >= frameDur) {
                                obj.animTimer -= frameDur;
                                obj.animFrameIdx = (obj.animFrameIdx + 1) % valid.size();
                            }
                            b.spriteResName = valid.get(obj.animFrameIdx % valid.size());
                        }
                    }
                } else if ("spawner".equals(b.type)) {
                    if (b.cooldownSec > 0f && b.prefabName != null && !b.prefabName.trim().isEmpty()) {
                        obj.spawnTimer -= dt;
                        if (obj.spawnTimer <= 0f) {
                            obj.spawnTimer = Math.max(0.05f, b.cooldownSec);
                            RuntimeEntity spawned = spawnPrefabAt(b.prefabName.trim(), b.x, b.y);
                            if (spawned != null) {
                                float rangeX = Math.max(0f, b.width - spawned.bean.width);
                                float rangeY = Math.max(0f, b.height - spawned.bean.height);
                                spawned.bean.x = b.x + (rangeX > 0f ? random.nextFloat() * rangeX : 0f);
                                spawned.bean.y = b.y + (rangeY > 0f ? random.nextFloat() * rangeY : 0f);
                                spawned.prevX = spawned.bean.x;
                                spawned.prevY = spawned.bean.y;
                                spawned.startX = spawned.bean.x;
                                spawned.startY = spawned.bean.y;
                                if (b.moveX != 0f || b.moveY != 0f) {
                                    spawned.bean.velocityX = b.moveX;
                                    spawned.bean.velocityY = b.moveY;
                                }
                                spawned.enteredScreen = isInCameraViewport(spawned, 0f);
                            }
                        }
                    }
                } else if ("hud_dialog".equals(b.type)) {
                    if (b.visible && b.text != null && obj.dialogVisibleChars < b.text.length()) {
                        obj.dialogVisibleChars = Math.min(b.text.length(), obj.dialogVisibleChars + 45f * dt);
                    }
                }

                if (!obj.isHud() && b.destroyOffscreen) {
                    if ((obj.enteredScreen && !isInCameraViewport(obj, 96f)) || isFarOutsideWorldAndCamera(obj, 900f)) {
                        obj.destroyed = true;
                    }
                }
            }
        }

        private void carryRidersOnPlatform(RuntimeEntity plat, float oldPlatX, float oldPlatY, float platDx, float platDy) {
            for (RuntimeEntity rider : objects) {
                if (rider == plat || rider.destroyed || rider.isHud() || !rider.bean.physicsEnabled
                        || rider.bean.isStatic || rider.bean.isSensor) {
                    continue;
                }
                float riderBottom = rider.bean.y + rider.bean.height;
                boolean horiz = (rider.bean.x + rider.bean.width > oldPlatX + 1f)
                        && (rider.bean.x < oldPlatX + plat.bean.width - 1f);
                boolean standing = Math.abs(riderBottom - oldPlatY) <= 6f && rider.bean.velocityY >= -10f;
                if (horiz && standing) {
                    rider.bean.x += platDx;
                    rider.prevX += platDx;
                    rider.bean.y += platDy;
                    rider.prevY += platDy;
                    rider.onGround = true;
                }
            }
        }

        private void stepPhysics(float dt) {
            float gravityY = currentScene != null ? currentScene.gravityY : 980f;
            float worldW = getWorldWidth();
            float worldH = getWorldHeight();
            boolean solidEdges = currentScene != null && "solid".equals(currentScene.worldBoundsMode);

            for (RuntimeEntity obj : objects) {
                if (obj.destroyed || obj.isHud() || !obj.bean.physicsEnabled || obj.bean.isStatic) {
                    continue;
                }
                if (obj.bean.activeOnlyNearCamera && !isInCameraViewport(obj, 360f)) {
                    continue;
                }
                float scriptDx = obj.bean.x - obj.prevX;
                float scriptDy = obj.bean.y - obj.prevY;
                obj.onGround = false;
                obj.bean.velocityY = clamp(obj.bean.velocityY + gravityY * obj.bean.gravityScale * dt, -2400f, MAX_FALL_SPEED);

                float dx = scriptDx + obj.bean.velocityX * dt;
                obj.bean.x = obj.prevX + dx;
                if (!obj.bean.isSensor) {
                    for (RuntimeEntity other : objects) {
                        if (other == obj || other.destroyed || other.isHud() || !other.bean.physicsEnabled
                                || other.bean.isSensor || other.bean.oneWay) {
                            continue;
                        }
                        if (overlapsXPass(obj, other)) {
                            if (dx > 0f) {
                                obj.bean.x = other.bean.x - obj.bean.width;
                                if (obj.bean.velocityX > 0f) obj.bean.velocityX = -obj.bean.velocityX * obj.bean.bounce;
                            } else if (dx < 0f) {
                                obj.bean.x = other.bean.x + other.bean.width;
                                if (obj.bean.velocityX < 0f) obj.bean.velocityX = -obj.bean.velocityX * obj.bean.bounce;
                            } else {
                                float ol = (obj.bean.x + obj.bean.width) - other.bean.x;
                                float or = (other.bean.x + other.bean.width) - obj.bean.x;
                                obj.bean.x = ol < or ? (other.bean.x - obj.bean.width) : (other.bean.x + other.bean.width);
                            }
                        }
                    }
                }

                float dy = scriptDy + obj.bean.velocityY * dt;
                obj.bean.y = obj.prevY + dy;
                if (!obj.bean.isSensor) {
                    for (RuntimeEntity other : objects) {
                        if (other == obj || other.destroyed || other.isHud() || !other.bean.physicsEnabled || other.bean.isSensor) {
                            continue;
                        }
                        if (other.bean.oneWay) {
                            if (dy < 0f || (obj.prevY + obj.bean.height > other.bean.y + 6f)) {
                                continue;
                            }
                        }
                        if (overlapsYPass(obj, other)) {
                            if (dy >= 0f) {
                                obj.bean.y = other.bean.y - obj.bean.height;
                                obj.onGround = true;
                                if (obj.bean.velocityY > 0f) {
                                    obj.bean.velocityY = (obj.bean.bounce > 0.05f) ? -obj.bean.velocityY * obj.bean.bounce : 0f;
                                }
                                float damp = Math.max(0f, 1f - Math.max(obj.bean.friction, other.bean.friction) * 8f * dt);
                                obj.bean.velocityX *= damp;
                            } else {
                                obj.bean.y = other.bean.y + other.bean.height;
                                if (obj.bean.velocityY < 0f) obj.bean.velocityY = -obj.bean.velocityY * obj.bean.bounce;
                            }
                        }
                    }
                }

                if (solidEdges && !obj.bean.isSensor && !"projectile".equals(obj.bean.type)) {
                    clampInsideWorldRect(obj, worldW, worldH);
                }
            }

            for (RuntimeEntity obj : objects) {
                obj.prevColliders.clear();
                obj.prevColliders.addAll(obj.currentColliders);
                obj.currentColliders.clear();
            }

            int n = objects.size();
            for (int i = 0; i < n; i++) {
                RuntimeEntity a = objects.get(i);
                if (a.destroyed || a.isHud()) continue;
                for (int j = i + 1; j < n; j++) {
                    RuntimeEntity b = objects.get(j);
                    if (b.destroyed || b.isHud()) continue;
                    if (aabbTouching(a, b)) {
                        a.currentColliders.add(b.bean.id);
                        b.currentColliders.add(a.bean.id);
                        if (!a.prevColliders.contains(b.bean.id)) enqueueCollisionScripts(a, b);
                        if (!b.prevColliders.contains(a.bean.id)) enqueueCollisionScripts(b, a);
                    }
                }
            }

            stepWorldBoundsAndBuiltins(worldW, worldH);
        }

        private void clampInsideWorldRect(RuntimeEntity obj, float worldW, float worldH) {
            if (obj.bean.x < 0f) {
                obj.bean.x = 0f;
                if (obj.bean.velocityX < 0f) obj.bean.velocityX = 0f;
            } else if (obj.bean.x + obj.bean.width > worldW) {
                obj.bean.x = Math.max(0f, worldW - obj.bean.width);
                if (obj.bean.velocityX > 0f) obj.bean.velocityX = 0f;
            }
            if (obj.bean.y < 0f) {
                obj.bean.y = 0f;
                if (obj.bean.velocityY < 0f) obj.bean.velocityY = 0f;
            } else if (obj.bean.y + obj.bean.height > worldH) {
                obj.bean.y = Math.max(0f, worldH - obj.bean.height);
                if (obj.bean.velocityY >= 0f) {
                    obj.bean.velocityY = 0f;
                    obj.onGround = true;
                }
            }
        }

        private void respawnObjectAtCheckpoint(RuntimeEntity obj) {
            if (obj == null) return;
            obj.bean.x = obj.hasCheckpoint ? obj.checkpointX : obj.startX;
            obj.bean.y = obj.hasCheckpoint ? obj.checkpointY : obj.startY;
            obj.prevX = obj.bean.x;
            obj.prevY = obj.bean.y;
            obj.bean.velocityX = 0f;
            obj.bean.velocityY = 0f;
            obj.glideActive = false;
            obj.hitCooldown = 0.4f;
            emitParticles(obj.bean.x + obj.bean.width * 0.5f, obj.bean.y + obj.bean.height * 0.5f, 0xFF38BDF8, 12);
        }

        private void stepWorldBoundsAndBuiltins(float worldW, float worldH) {
            String boundsMode = currentScene != null ? currentScene.worldBoundsMode : "free";
            boolean respawnFall = "respawn_bottom".equals(boundsMode);
            boolean restartFall = "restart_bottom".equals(boundsMode);

            for (RuntimeEntity actor : objects) {
                if (actor.destroyed || actor.isHud() || !actor.bean.physicsEnabled || actor.bean.isStatic
                        || actor.bean.isSensor || "projectile".equals(actor.bean.type)) {
                    continue;
                }
                if ((respawnFall || restartFall) && actor.bean.y > worldH + 120f) {
                    if (respawnFall) {
                        respawnObjectAtCheckpoint(actor);
                    } else {
                        pendingSceneRestart = true;
                    }
                    continue;
                }
                for (RuntimeEntity other : objects) {
                    if (other == actor || other.destroyed || other.isHud()) continue;
                    if (!actor.currentColliders.contains(other.bean.id)) continue;

                    String oType = other.bean.type;
                    if ("checkpoint".equals(oType)) {
                        if (!actor.hasCheckpoint || Math.abs(actor.checkpointX - other.bean.x) > 4f
                                || Math.abs(actor.checkpointY - other.bean.y) > 4f) {
                            actor.checkpointX = other.bean.x + (other.bean.width - actor.bean.width) * 0.5f;
                            actor.checkpointY = other.bean.y + other.bean.height - actor.bean.height;
                            actor.hasCheckpoint = true;
                            other.bean.toggled = true;
                            emitParticles(other.bean.x + other.bean.width * 0.5f, other.bean.y + other.bean.height * 0.25f, 0xFF10B981, 10);
                        }
                    } else if ("bounce_pad".equals(oType)) {
                        float actorBottom = actor.bean.y + actor.bean.height;
                        if (actorBottom <= other.bean.y + other.bean.height * 0.65f && actor.bean.velocityY >= -20f) {
                            float impulse = other.bean.speed >= 300f ? other.bean.speed : Math.max(480f, other.bean.speed * 5.2f);
                            actor.bean.velocityY = -impulse;
                            actor.onGround = false;
                        }
                    } else if ("collectible".equals(oType)) {
                        if (!hasCollisionScriptBetween(actor, other)) {
                            int pts = Math.max(1, Math.round(other.bean.damage));
                            double cur = parseDoubleSafe(variables.get("score"), 0.0);
                            variables.put("score", formatNumber(cur + pts));
                            if (other.bean.soundResName != null && !other.bean.soundResName.isEmpty()) {
                                playSoundInternal(other.bean.soundResName);
                            }
                            emitParticles(other.bean.x + other.bean.width * 0.5f, other.bean.y + other.bean.height * 0.5f, other.bean.color, 10);
                            other.destroyed = true;
                        }
                    } else if ("death_zone".equals(oType)) {
                        if (actor.hitCooldown <= 0f && !hasCollisionScriptBetween(actor, other)) {
                            if (actor.hasCheckpoint || respawnFall) {
                                respawnObjectAtCheckpoint(actor);
                            } else {
                                pendingSceneRestart = true;
                            }
                        }
                    } else if ("goal".equals(oType)) {
                        if (actor.hitCooldown <= 0f && !hasCollisionScriptBetween(actor, other)) {
                            String nextSceneId = resolveGoalSceneId(other.bean.targetScene);
                            if (nextSceneId != null && currentScene != null && !nextSceneId.equals(currentScene.id)) {
                                actor.hitCooldown = 1.0f;
                                startSceneTransition(nextSceneId, "fade");
                            }
                        }
                    }
                }
            }
        }

        private boolean hasCollisionScriptBetween(RuntimeEntity actor, RuntimeEntity other) {
            for (ScriptData s : sceneScripts) {
                if (!"on_collision".equals(s.eventType)) continue;
                if (actor.matchesTarget(s.targetObjectId) || other.matchesTarget(s.targetObjectId) || "__scene__".equals(s.targetObjectId)) {
                    String f = s.eventParam == null ? "" : s.eventParam.trim();
                    if (f.isEmpty() || f.equalsIgnoreCase(other.bean.tag) || f.equalsIgnoreCase(other.bean.name)
                            || f.equalsIgnoreCase(actor.bean.tag) || f.equalsIgnoreCase(actor.bean.name)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private String resolveGoalSceneId(String spec) {
            if (project.scenes.isEmpty()) return null;
            if (spec == null || spec.trim().isEmpty() || "__next__".equals(spec.trim())) {
                return resolveAdjacentSceneId(1);
            }
            if ("__prev__".equals(spec.trim())) {
                return resolveAdjacentSceneId(-1);
            }
            SceneData matched = project.getSceneByIdOrName(spec.trim());
            return matched != null ? matched.id : null;
        }

        private String resolveAdjacentSceneId(int delta) {
            if (currentScene == null) return null;
            int curIdx = -1;
            for (int i = 0; i < project.scenes.size(); i++) {
                SceneData s = project.scenes.get(i);
                if (s != null && s.id != null && s.id.equals(currentScene.id)) {
                    curIdx = i;
                    break;
                }
            }
            if (curIdx < 0) return null;
            int targetIdx = curIdx + delta;
            if (targetIdx >= 0 && targetIdx < project.scenes.size()) {
                SceneData next = project.scenes.get(targetIdx);
                return next != null ? next.id : null;
            }
            return null;
        }

        private void startSceneTransition(String sceneIdOrName, String type) {
            SceneData target = project.getSceneByIdOrName(sceneIdOrName);
            String resolvedId = target != null ? target.id : sceneIdOrName;
            if (resolvedId == null || resolvedId.trim().isEmpty()) return;
            if (type == null || type.trim().isEmpty() || "instant".equalsIgnoreCase(type.trim())) {
                pendingSceneLoadId = resolvedId;
                return;
            }
            transitionType = type.trim().toLowerCase(Locale.ROOT);
            transitionTargetSceneId = resolvedId;
            transitionPhase = "out";
            transitionProgress = 0f;
        }

        private boolean overlapsXPass(RuntimeEntity a, RuntimeEntity b) {
            return a.bean.x + a.bean.width > b.bean.x
                    && a.bean.x < b.bean.x + b.bean.width
                    && a.bean.y + a.bean.height > b.bean.y + 0.5f
                    && a.bean.y < b.bean.y + b.bean.height - 0.5f;
        }

        private boolean overlapsYPass(RuntimeEntity a, RuntimeEntity b) {
            return a.bean.x + a.bean.width > b.bean.x + 0.5f
                    && a.bean.x < b.bean.x + b.bean.width - 0.5f
                    && a.bean.y + a.bean.height > b.bean.y
                    && a.bean.y < b.bean.y + b.bean.height;
        }

        private boolean aabbTouching(RuntimeEntity a, RuntimeEntity b) {
            float tol = (a.bean.isSensor || b.bean.isSensor) ? -1f : 0.75f;
            if (a.bean.circleCollider && b.bean.circleCollider) {
                float ax = a.bean.x + a.bean.width * 0.5f;
                float ay = a.bean.y + a.bean.height * 0.5f;
                float ar = Math.min(a.bean.width, a.bean.height) * 0.5f;
                float bx = b.bean.x + b.bean.width * 0.5f;
                float by = b.bean.y + b.bean.height * 0.5f;
                float br = Math.min(b.bean.width, b.bean.height) * 0.5f;
                float distSq = (ax - bx) * (ax - bx) + (ay - by) * (ay - by);
                float rSum = ar + br + tol;
                return distSq <= rSum * rSum;
            }
            if (a.bean.circleCollider != b.bean.circleCollider) {
                RuntimeEntity circ = a.bean.circleCollider ? a : b;
                RuntimeEntity box = a.bean.circleCollider ? b : a;
                float cx = circ.bean.x + circ.bean.width * 0.5f;
                float cy = circ.bean.y + circ.bean.height * 0.5f;
                float r = Math.min(circ.bean.width, circ.bean.height) * 0.5f + tol;
                float nearX = clamp(cx, box.bean.x, box.bean.x + box.bean.width);
                float nearY = clamp(cy, box.bean.y, box.bean.y + box.bean.height);
                float dx = cx - nearX;
                float dy = cy - nearY;
                return dx * dx + dy * dy <= r * r;
            }
            return a.bean.x + a.bean.width > b.bean.x - tol
                    && a.bean.x < b.bean.x + b.bean.width + tol
                    && a.bean.y + a.bean.height > b.bean.y - tol
                    && a.bean.y < b.bean.y + b.bean.height + tol;
        }

        private void enqueueCollisionScripts(RuntimeEntity owner, RuntimeEntity other) {
            for (ScriptData script : sceneScripts) {
                if (!"on_collision".equals(script.eventType)) continue;
                if (!owner.matchesTarget(script.targetObjectId) && !"__scene__".equals(script.targetObjectId)) continue;
                String filter = script.eventParam == null ? "" : script.eventParam.trim();
                if (filter.isEmpty() || filter.equalsIgnoreCase(other.bean.tag) || filter.equalsIgnoreCase(other.bean.name)) {
                    activeFibers.add(new Fiber(script, owner, other));
                }
            }
        }

        private void stepParticles(float dt) {
            ambientCounter++;
            if (ambientCounter % 4 == 0 && particles.size() < MAX_PARTICLES) {
                for (RuntimeEntity obj : objects) {
                    if (!obj.destroyed && obj.bean.visible && "particles".equals(obj.bean.type)) {
                        emitParticles(obj.bean.x + obj.bean.width * 0.5f, obj.bean.y + obj.bean.height * 0.5f, obj.bean.color, 1);
                    }
                }
            }
            for (int i = particles.size() - 1; i >= 0; i--) {
                ParticleData p = particles.get(i);
                p.life -= dt;
                if (p.life <= 0f) {
                    particles.remove(i);
                } else {
                    p.x += p.vx * dt;
                    p.y += p.vy * dt;
                    p.vy += 260f * dt;
                }
            }
        }

        private void emitParticles(float cx, float cy, int color, int count) {
            int c = Math.max(1, Math.min(80, count));
            for (int i = 0; i < c; i++) {
                if (particles.size() >= MAX_PARTICLES) particles.remove(0);
                float angle = random.nextFloat() * (float) (Math.PI * 2.0);
                float speed = 60f + random.nextFloat() * 200f;
                ParticleData p = new ParticleData();
                p.x = cx;
                p.y = cy;
                p.vx = (float) Math.cos(angle) * speed;
                p.vy = (float) Math.sin(angle) * speed - 70f;
                p.maxLife = 0.35f + random.nextFloat() * 0.45f;
                p.life = p.maxLife;
                p.size = 4f + random.nextFloat() * 6f;
                p.color = color;
                particles.add(p);
            }
        }

        private void stepCamera(float dt) {
            if (camFollowTargetId != null && !camFollowTargetId.isEmpty()) {
                RuntimeEntity target = findByIdOrName(camFollowTargetId);
                if (target != null && !target.destroyed) {
                    float targetX = target.bean.x + target.bean.width * 0.5f - project.virtualWidth * 0.5f;
                    float targetY = target.bean.y + target.bean.height * 0.5f - project.virtualHeight * 0.5f;
                    float maxRight = getWorldWidth();
                    float maxBottom = getWorldHeight();
                    if (currentScene == null || currentScene.worldWidth <= 0f || currentScene.worldHeight <= 0f) {
                        for (RuntimeEntity o : objects) {
                            if (!o.destroyed && !o.isHud() && o.bean.isStatic && !"death_zone".equals(o.bean.type)) {
                                if (currentScene == null || currentScene.worldWidth <= 0f) {
                                    maxRight = Math.max(maxRight, o.bean.x + o.bean.width);
                                }
                                if (currentScene == null || currentScene.worldHeight <= 0f) {
                                    maxBottom = Math.max(maxBottom, o.bean.y + o.bean.height);
                                }
                            }
                        }
                    }
                    targetX = clamp(targetX, 0f, Math.max(0f, maxRight - project.virtualWidth));
                    targetY = clamp(targetY, 0f, Math.max(0f, maxBottom - project.virtualHeight));
                    float alpha = clamp(camFollowLerp * 60f * dt, 0.02f, 1f);
                    camX += (targetX - camX) * alpha;
                    camY += (targetY - camY) * alpha;
                }
            }
            if (shakeRemaining > 0f) {
                shakeRemaining = Math.max(0f, shakeRemaining - dt);
            }
        }

        // ==================== Block VM & Expression Engine ====================

        private void enqueueUpdateScripts() {
            Set<String> runningKeys = new HashSet<String>();
            for (Fiber f : activeFibers) {
                if ("on_update".equals(f.script.eventType)) {
                    String ownerKey = f.self != null ? f.self.bean.id : "__scene__";
                    runningKeys.add(f.script.id + "@" + ownerKey);
                }
            }
            for (ScriptData script : sceneScripts) {
                if (!"on_update".equals(script.eventType)) continue;
                if ("__scene__".equals(script.targetObjectId)) {
                    if (!runningKeys.contains(script.id + "@__scene__")) {
                        activeFibers.add(new Fiber(script, null, null));
                    }
                } else {
                    for (RuntimeEntity owner : objects) {
                        if (owner.destroyed || !owner.matchesTarget(script.targetObjectId)) continue;
                        if (owner.bean.activeOnlyNearCamera && !isInCameraViewport(owner, 360f)) continue;
                        if (!runningKeys.contains(script.id + "@" + owner.bean.id)) {
                            activeFibers.add(new Fiber(script, owner, null));
                        }
                    }
                }
            }
        }

        private void enqueueScriptsForEvent(String eventType, String param, RuntimeEntity selfObj, RuntimeEntity otherObj) {
            for (ScriptData script : sceneScripts) {
                if (!eventType.equals(script.eventType)) continue;
                String sp = script.eventParam == null ? "" : script.eventParam.trim();
                String tp = param == null ? "" : param.trim();
                if (!sp.isEmpty() && !tp.isEmpty() && !sp.equalsIgnoreCase(tp)) continue;
                if (!sp.isEmpty() && tp.isEmpty()) continue;
                if (selfObj != null) {
                    if (!selfObj.destroyed && ("__scene__".equals(script.targetObjectId)
                            || selfObj.matchesTarget(script.targetObjectId))) {
                        activeFibers.add(new Fiber(script, selfObj, otherObj));
                    }
                } else if ("__scene__".equals(script.targetObjectId)) {
                    activeFibers.add(new Fiber(script, null, otherObj));
                } else {
                    for (RuntimeEntity owner : objects) {
                        if (!owner.destroyed && owner.matchesTarget(script.targetObjectId)) {
                            activeFibers.add(new Fiber(script, owner, otherObj));
                        }
                    }
                }
            }
        }

        private void enqueueScriptsForTarget(String eventType, String param, String targetId, RuntimeEntity selfObj, RuntimeEntity otherObj) {
            for (ScriptData script : sceneScripts) {
                if (!eventType.equals(script.eventType) || !targetId.equals(script.targetObjectId)) continue;
                String sp = script.eventParam == null ? "" : script.eventParam.trim();
                String tp = param == null ? "" : param.trim();
                if (!sp.isEmpty() && !sp.equalsIgnoreCase(tp)) continue;
                if (selfObj != null && selfObj.destroyed) continue;
                activeFibers.add(new Fiber(script, selfObj, otherObj));
            }
        }

        private void stepFibers(float dt) {
            if (activeFibers.isEmpty()) return;
            ArrayList<Fiber> snap = new ArrayList<Fiber>(activeFibers);
            for (Fiber fiber : snap) {
                if (fiber.finished) {
                    activeFibers.remove(fiber);
                    continue;
                }
                if (fiber.waitTimer > 0f) {
                    fiber.waitTimer -= dt;
                    if (fiber.waitTimer > 0f) continue;
                    fiber.waitTimer = 0f;
                }
                runFiber(fiber);
                if (fiber.finished) {
                    activeFibers.remove(fiber);
                }
            }
        }

        private void runFiber(Fiber fiber) {
            int ops = 0;
            while (!fiber.finished && !fiber.frames.isEmpty() && ops < MAX_FIBER_OPS) {
                if (fiber.self != null && fiber.self.destroyed) {
                    fiber.finished = true;
                    return;
                }
                Frame top = fiber.frames.get(fiber.frames.size() - 1);
                if (top.nextIndex >= top.blocks.size()) {
                    if (top.whileUntilBlock != null) {
                        if (evalSlotBool(top.whileUntilBlock, 0, false, fiber)) {
                            fiber.frames.remove(fiber.frames.size() - 1);
                        } else {
                            top.nextIndex = 0;
                            return;
                        }
                        continue;
                    }
                    if (top.infinite) {
                        top.nextIndex = 0;
                        return;
                    }
                    top.remaining--;
                    if (top.remaining > 0) {
                        top.nextIndex = 0;
                    } else {
                        fiber.frames.remove(fiber.frames.size() - 1);
                    }
                    continue;
                }
                BlockData block = top.blocks.get(top.nextIndex++);
                ops++;
                if (block == null || block.opCode == null) continue;
                if (executeBlock(fiber, block, top) || pendingSceneRestart || pendingSceneLoadId != null) {
                    return;
                }
            }
            if (fiber.frames.isEmpty()) {
                fiber.finished = true;
            }
        }

        private boolean executeBlock(Fiber fiber, BlockData block, Frame currentFrame) {
            String op = block.opCode.trim();
            RuntimeEntity self = fiber.self;

            if ("if".equals(op)) {
                if (evalSlotBool(block, 0, false, fiber)) pushStack(fiber, block.subStack1, 1);
                return false;
            } else if ("ifElse".equals(op)) {
                pushStack(fiber, evalSlotBool(block, 0, false, fiber) ? block.subStack1 : block.subStack2, 1);
                return false;
            } else if ("repeat".equals(op)) {
                int c = Math.max(0, Math.min(10000, Math.round(evalSlotFloat(block, 0, 1f, fiber))));
                if (c > 0) pushStack(fiber, block.subStack1, c);
                return false;
            } else if ("forever".equals(op)) {
                if (block.subStack1 != null && !block.subStack1.isEmpty()) {
                    Frame f = new Frame(block.subStack1, 1);
                    f.infinite = true;
                    fiber.frames.add(f);
                }
                return false;
            } else if ("waitSeconds".equals(op)) {
                float sec = Math.max(0f, evalSlotFloat(block, 0, 0f, fiber));
                if (sec > 0f) {
                    fiber.waitTimer = sec;
                    return true;
                }
                return false;
            } else if ("waitUntil".equals(op)) {
                if (!evalSlotBool(block, 0, false, fiber)) {
                    currentFrame.nextIndex--;
                    return true;
                }
                return false;
            } else if ("repeatUntil".equals(op)) {
                if (!evalSlotBool(block, 0, false, fiber)) {
                    if (block.subStack1 != null && !block.subStack1.isEmpty()) {
                        Frame f = new Frame(block.subStack1, 1);
                        f.whileUntilBlock = block;
                        fiber.frames.add(f);
                    } else {
                        currentFrame.nextIndex--;
                        return true;
                    }
                }
                return false;
            } else if ("stopScript".equals(op) || "stopAll".equals(op)) {
                String mode = evalSlotText(block, 0, "this", fiber).toLowerCase(Locale.ROOT);
                if (mode.contains("all") || mode.contains("tudo")) {
                    activeFibers.clear();
                    fiber.finished = true;
                    return true;
                } else if (mode.contains("other") || mode.contains("outro")) {
                    for (int i = activeFibers.size() - 1; i >= 0; i--) {
                        Fiber otherF = activeFibers.get(i);
                        if (otherF != fiber && otherF.self == self) {
                            otherF.finished = true;
                        }
                    }
                    return false;
                }
                fiber.finished = true;
                return true;
            } else if ("createClone".equals(op)) {
                String targetExpr = evalSlotText(block, 0, "self", fiber);
                RuntimeEntity src = ("myself".equalsIgnoreCase(targetExpr) || "self".equalsIgnoreCase(targetExpr) || targetExpr.isEmpty())
                        ? self : findByIdOrName(targetExpr);
                if (src != null) {
                    ObjectData copy = src.bean.copy();
                    copy.id = "clone_" + UUID.randomUUID().toString().substring(0, 8);
                    RuntimeEntity clone = new RuntimeEntity(copy);
                    clone.isClone = true;
                    clone.templateId = src.matchKey();
                    clone.enteredScreen = isInCameraViewport(clone, 0f);
                    objects.add(clone);
                    enqueueScriptsForTarget("on_clone_start", "", clone.templateId, clone, null);
                    enqueueScriptsForTarget("on_scene_start", "", clone.templateId, clone, null);
                }
                return false;
            } else if ("deleteClone".equals(op)) {
                if (self != null && self.isClone) {
                    self.destroyed = true;
                    fiber.finished = true;
                    return true;
                }
                return false;
            } else if ("ifButtonPressed".equals(op) || "ifKeyPressed".equals(op)) {
                String k = normalizeActionKey(evalSlotText(block, 0, "JUMP", fiber));
                if (pressedButtons.contains(k) || latchedButtons.contains(k)) pushStack(fiber, block.subStack1, 1);
                return false;
            } else if ("ifCollidingTag".equals(op)) {
                if (self != null) {
                    RuntimeEntity match = findCollidingWithTag(self, evalSlotText(block, 0, "", fiber));
                    if (match != null) {
                        fiber.other = match;
                        pushStack(fiber, block.subStack1, 1);
                    }
                }
                return false;
            } else if ("ifOnGround".equals(op)) {
                if (self != null && self.onGround) pushStack(fiber, block.subStack1, 1);
                return false;
            } else if ("moveSteps".equals(op)) {
                if (self != null) {
                    float steps = evalSlotFloat(block, 0, 10f, fiber);
                    double rad = Math.toRadians(self.bean.rotation);
                    self.bean.x += (float) (Math.cos(rad) * steps);
                    self.bean.y += (float) (Math.sin(rad) * steps);
                }
                return false;
            } else if ("goToTarget".equals(op)) {
                if (self != null) {
                    String target = evalSlotText(block, 0, "random", fiber);
                    if ("touch".equalsIgnoreCase(target) || "pointer".equalsIgnoreCase(target) || "ponteiro".equalsIgnoreCase(target)) {
                        self.bean.x = touchX - self.bean.width * 0.5f;
                        self.bean.y = touchY - self.bean.height * 0.5f;
                    } else if ("random".equalsIgnoreCase(target) || "aleatorio".equalsIgnoreCase(target) || target.isEmpty()) {
                        float maxX = Math.max(0f, project.virtualWidth - self.bean.width);
                        float maxY = Math.max(0f, project.virtualHeight - self.bean.height);
                        self.bean.x = random.nextFloat() * maxX;
                        self.bean.y = random.nextFloat() * maxY;
                    } else {
                        RuntimeEntity t = findByIdOrName(target);
                        if (t != null) {
                            self.bean.x = t.bean.x;
                            self.bean.y = t.bean.y;
                        }
                    }
                }
                return false;
            } else if ("glideToXY".equals(op)) {
                float sec = Math.max(0f, evalSlotFloat(block, 0, 1f, fiber));
                float gx = evalSlotFloat(block, 1, 0f, fiber);
                float gy = evalSlotFloat(block, 2, 0f, fiber);
                if (self != null) {
                    if (sec <= 0.001f) {
                        self.bean.x = gx;
                        self.bean.y = gy;
                        self.glideActive = false;
                        return false;
                    }
                    self.glideStartX = self.bean.x;
                    self.glideStartY = self.bean.y;
                    self.glideTargetX = gx;
                    self.glideTargetY = gy;
                    self.glideElapsed = 0f;
                    self.glideDuration = sec;
                    self.glideActive = true;
                    fiber.waitTimer = sec;
                    return true;
                }
                return false;
            } else if ("glideToTarget".equals(op)) {
                float sec = Math.max(0f, evalSlotFloat(block, 0, 1f, fiber));
                String target = evalSlotText(block, 1, "random", fiber);
                if (self != null) {
                    float gx = self.bean.x;
                    float gy = self.bean.y;
                    if ("touch".equalsIgnoreCase(target) || "pointer".equalsIgnoreCase(target) || "ponteiro".equalsIgnoreCase(target)) {
                        gx = touchX - self.bean.width * 0.5f;
                        gy = touchY - self.bean.height * 0.5f;
                    } else if ("random".equalsIgnoreCase(target) || "aleatorio".equalsIgnoreCase(target) || target.isEmpty()) {
                        gx = random.nextFloat() * Math.max(0f, project.virtualWidth - self.bean.width);
                        gy = random.nextFloat() * Math.max(0f, project.virtualHeight - self.bean.height);
                    } else {
                        RuntimeEntity t = findByIdOrName(target);
                        if (t != null) {
                            gx = t.bean.x;
                            gy = t.bean.y;
                        }
                    }
                    if (sec <= 0.001f) {
                        self.bean.x = gx;
                        self.bean.y = gy;
                        self.glideActive = false;
                        return false;
                    }
                    self.glideStartX = self.bean.x;
                    self.glideStartY = self.bean.y;
                    self.glideTargetX = gx;
                    self.glideTargetY = gy;
                    self.glideElapsed = 0f;
                    self.glideDuration = sec;
                    self.glideActive = true;
                    fiber.waitTimer = sec;
                    return true;
                }
                return false;
            } else if ("changeX".equals(op)) {
                if (self != null) self.bean.x += evalSlotFloat(block, 0, 10f, fiber);
                return false;
            } else if ("setX".equals(op)) {
                if (self != null) self.bean.x = evalSlotFloat(block, 0, 0f, fiber);
                return false;
            } else if ("changeY".equals(op)) {
                if (self != null) self.bean.y += evalSlotFloat(block, 0, 10f, fiber);
                return false;
            } else if ("setY".equals(op)) {
                if (self != null) self.bean.y = evalSlotFloat(block, 0, 0f, fiber);
                return false;
            } else if ("keepInsideWorld".equals(op)) {
                if (self != null) clampInsideWorldRect(self, getWorldWidth(), getWorldHeight());
                return false;
            } else if ("respawnAtCheckpoint".equals(op)) {
                if (self != null) respawnObjectAtCheckpoint(self);
                return false;
            } else if ("bounceOnEdge".equals(op)) {
                if (self != null) {
                    float maxX = Math.max(0f, getWorldWidth() - self.bean.width);
                    float maxY = Math.max(0f, getWorldHeight() - self.bean.height);
                    boolean hitX = false;
                    boolean hitY = false;
                    if (self.bean.x < 0f) { self.bean.x = 0f; if (self.bean.velocityX < 0f) self.bean.velocityX = -self.bean.velocityX; hitX = true; }
                    else if (self.bean.x > maxX) { self.bean.x = maxX; if (self.bean.velocityX > 0f) self.bean.velocityX = -self.bean.velocityX; hitX = true; }
                    if (self.bean.y < 0f) { self.bean.y = 0f; if (self.bean.velocityY < 0f) self.bean.velocityY = -self.bean.velocityY; hitY = true; }
                    else if (self.bean.y > maxY) { self.bean.y = maxY; if (self.bean.velocityY > 0f) self.bean.velocityY = -self.bean.velocityY; hitY = true; }
                    if (hitX) self.bean.rotation = 180f - self.bean.rotation;
                    if (hitY) self.bean.rotation = -self.bean.rotation;
                }
                return false;
            } else if ("setPos".equals(op)) {
                if (hasSlot(block, 2)) {
                    RuntimeEntity t = resolveTarget(param(block, 0, ""), self);
                    if (t != null) {
                        t.bean.x = evalSlotFloat(block, 1, 0f, fiber);
                        t.bean.y = evalSlotFloat(block, 2, 0f, fiber);
                    }
                } else if (self != null) {
                    self.bean.x = evalSlotFloat(block, 0, 0f, fiber);
                    self.bean.y = evalSlotFloat(block, 1, 0f, fiber);
                }
                return false;
            } else if ("moveBy".equals(op)) {
                if (hasSlot(block, 2)) {
                    RuntimeEntity t = resolveTarget(param(block, 0, ""), self);
                    if (t != null) {
                        t.bean.x += evalSlotFloat(block, 1, 0f, fiber);
                        t.bean.y += evalSlotFloat(block, 2, 0f, fiber);
                    }
                } else if (self != null) {
                    self.bean.x += evalSlotFloat(block, 0, 0f, fiber);
                    self.bean.y += evalSlotFloat(block, 1, 0f, fiber);
                }
                return false;
            } else if ("setVel".equals(op)) {
                if (hasSlot(block, 2)) {
                    RuntimeEntity t = resolveTarget(param(block, 0, ""), self);
                    if (t != null) {
                        t.bean.velocityX = evalSlotFloat(block, 1, 0f, fiber);
                        t.bean.velocityY = evalSlotFloat(block, 2, 0f, fiber);
                    }
                } else if (self != null) {
                    self.bean.velocityX = evalSlotFloat(block, 0, 0f, fiber);
                    self.bean.velocityY = evalSlotFloat(block, 1, 0f, fiber);
                }
                return false;
            } else if ("jumpImpulse".equals(op)) {
                if (hasSlot(block, 1) && resolveTarget(param(block, 0, ""), null) != null) {
                    RuntimeEntity t = resolveTarget(param(block, 0, ""), self);
                    if (t != null) {
                        float v = evalSlotFloat(block, 1, 550f, fiber);
                        t.bean.velocityY = v > 0f ? -v : v;
                        t.onGround = false;
                    }
                } else if (self != null) {
                    float v = evalSlotFloat(block, 0, 550f, fiber);
                    self.bean.velocityY = v > 0f ? -v : v;
                    self.onGround = false;
                }
                return false;
            } else if ("setRotation".equals(op)) {
                if (self != null) self.bean.rotation = evalSlotFloat(block, 0, 0f, fiber);
                return false;
            } else if ("rotateBy".equals(op)) {
                if (self != null) self.bean.rotation += evalSlotFloat(block, 0, 0f, fiber);
                return false;
            } else if ("rotateLeftBy".equals(op)) {
                if (self != null) self.bean.rotation -= evalSlotFloat(block, 0, 0f, fiber);
                return false;
            } else if ("pointToward".equals(op) || "pointTowards".equals(op)) {
                if (self != null) {
                    float cx = self.bean.x + self.bean.width * 0.5f;
                    float cy = self.bean.y + self.bean.height * 0.5f;
                    float tx = cx;
                    float ty = cy;
                    if (hasSlot(block, 1)) {
                        tx = evalSlotFloat(block, 0, 0f, fiber);
                        ty = evalSlotFloat(block, 1, 0f, fiber);
                    } else {
                        String ts = evalSlotText(block, 0, "touch", fiber).trim();
                        if ("touch".equalsIgnoreCase(ts) || "pointer".equalsIgnoreCase(ts) || "ponteiro".equalsIgnoreCase(ts)) {
                            tx = touchX;
                            ty = touchY;
                        } else {
                            RuntimeEntity t = findByIdOrName(ts);
                            if (t != null) {
                                tx = t.bean.x + t.bean.width * 0.5f;
                                ty = t.bean.y + t.bean.height * 0.5f;
                            }
                        }
                    }
                    self.bean.rotation = (float) Math.toDegrees(Math.atan2(ty - cy, tx - cx));
                }
                return false;
            } else if ("cameraFollow".equals(op)) {
                String p0 = param(block, 0, "").trim();
                String p1 = param(block, 1, "").trim();
                if (p0.isEmpty() || "self".equalsIgnoreCase(p0)) {
                    camFollowTargetId = self != null ? self.bean.id : "";
                    camFollowLerp = hasSlot(block, 1) ? clamp(evalSlotFloat(block, 1, 0.15f, fiber), 0.01f, 1f) : 0.15f;
                } else {
                    RuntimeEntity t = findByIdOrName(p0);
                    if (t != null) {
                        camFollowTargetId = t.bean.id;
                        camFollowLerp = hasSlot(block, 1) ? clamp(evalSlotFloat(block, 1, 0.15f, fiber), 0.01f, 1f) : 0.15f;
                    } else if (isNumericString(p0) && self != null) {
                        camFollowTargetId = self.bean.id;
                        camFollowLerp = clamp(evalSlotFloat(block, 0, 0.15f, fiber), 0.01f, 1f);
                    } else {
                        camFollowTargetId = p0;
                        camFollowLerp = p1.isEmpty() ? 0.15f : clamp(evalSlotFloat(block, 1, 0.15f, fiber), 0.01f, 1f);
                    }
                }
                return false;
            } else if ("cameraShake".equals(op)) {
                shakeIntensity = Math.max(0f, evalSlotFloat(block, 0, 12f, fiber));
                shakeDuration = Math.max(0.01f, evalSlotFloat(block, 1, 0.25f, fiber));
                shakeRemaining = shakeDuration;
                return false;
            } else if ("sayForSecs".equals(op) || "thinkForSecs".equals(op)) {
                String msg = evalSlotText(block, 0, "", fiber);
                float secs = Math.max(0f, evalSlotFloat(block, 1, 2f, fiber));
                if (self != null) {
                    self.speechText = msg;
                    self.speechThink = "thinkForSecs".equals(op);
                    self.speechTimer = secs;
                }
                if (secs > 0f && !msg.isEmpty()) {
                    fiber.waitTimer = secs;
                    return true;
                }
                return false;
            } else if ("sayText".equals(op) || "thinkText".equals(op)) {
                String msg = evalSlotText(block, 0, "", fiber);
                if (self != null) {
                    self.speechText = msg;
                    self.speechThink = "thinkText".equals(op);
                    self.speechTimer = msg.isEmpty() ? 0f : -1f;
                }
                return false;
""";

    private static final String MAIN_ACTIVITY_BODY_TEMPLATE_PART2B = """
            } else if ("setSprite".equals(op)) {
                if (hasSlot(block, 1)) {
                    RuntimeEntity t = resolveTarget(param(block, 0, ""), self);
                    if (t != null) t.bean.spriteResName = evalSlotText(block, 1, "", fiber);
                } else if (self != null) {
                    self.bean.spriteResName = evalSlotText(block, 0, "", fiber);
                }
                return false;
            } else if ("setAnimFrames".equals(op)) {
                if (self != null) {
                    self.bean.spriteFrames = evalSlotText(block, 0, "", fiber);
                    self.bean.animFps = clamp(evalSlotFloat(block, 1, 8f, fiber), 0.5f, 60f);
                    self.animFrameIdx = 0;
                    self.animTimer = 0f;
                }
                return false;
            } else if ("setBackdrop".equals(op) || "switchBackdrop".equals(op)) {
                String target = evalSlotText(block, 0, "", fiber);
                SceneData sc = project.getSceneByIdOrName(target);
                if (sc != null) {
                    pendingSceneLoadId = sc.id;
                    return true;
                } else if (currentScene != null && !target.isEmpty()) {
                    currentScene.backgroundSprite = target;
                }
                return false;
            } else if ("nextBackdrop".equals(op)) {
                if (project.scenes.size() > 1 && currentScene != null) {
                    int idx = project.scenes.indexOf(currentScene);
                    int nextIdx = (idx + 1) % project.scenes.size();
                    pendingSceneLoadId = project.scenes.get(nextIdx).id;
                    return true;
                }
                return false;
            } else if ("setVisible".equals(op)) {
                if (hasSlot(block, 1)) {
                    RuntimeEntity t = resolveTarget(param(block, 0, ""), self);
                    if (t != null) t.bean.visible = evalSlotBool(block, 1, true, fiber);
                } else if (self != null) {
                    self.bean.visible = evalSlotBool(block, 0, true, fiber);
                }
                return false;
            } else if ("show".equals(op)) {
                if (self != null) self.bean.visible = true;
                return false;
            } else if ("hide".equals(op)) {
                if (self != null) self.bean.visible = false;
                return false;
            } else if ("setZIndex".equals(op)) {
                if (self != null) {
                    String arg = evalSlotText(block, 0, "front", fiber).trim().toLowerCase(Locale.ROOT);
                    if ("front".equals(arg) || "frente".equals(arg)) {
                        int maxZ = self.bean.zIndex;
                        for (RuntimeEntity o : objects) maxZ = Math.max(maxZ, o.bean.zIndex);
                        self.bean.zIndex = maxZ + 1;
                    } else if ("back".equals(arg) || "tras".equals(arg)) {
                        int minZ = self.bean.zIndex;
                        for (RuntimeEntity o : objects) minZ = Math.min(minZ, o.bean.zIndex);
                        self.bean.zIndex = minZ - 1;
                    } else {
                        self.bean.zIndex = Math.round(evalSlotFloat(block, 0, self.bean.zIndex, fiber));
                    }
                }
                return false;
            } else if ("changeZIndex".equals(op)) {
                if (self != null) {
                    if (hasSlot(block, 1)) {
                        String dir = evalSlotText(block, 0, "forward", fiber).toLowerCase(Locale.ROOT);
                        int delta = Math.round(evalSlotFloat(block, 1, 1f, fiber));
                        if (dir.contains("back") || dir.contains("tras")) delta = -delta;
                        self.bean.zIndex += delta;
                    } else {
                        self.bean.zIndex += Math.round(evalSlotFloat(block, 0, 1f, fiber));
                    }
                }
                return false;
            } else if ("setAlpha".equals(op)) {
                if (self != null) self.bean.alpha = clamp(evalSlotFloat(block, 0, 1f, fiber), 0f, 1f);
                return false;
            } else if ("clearEffects".equals(op)) {
                if (self != null) self.bean.alpha = 1f;
                return false;
            } else if ("setScale".equals(op)) {
                if (self != null) {
                    float sx = evalSlotFloat(block, 0, 1f, fiber);
                    float sy = hasSlot(block, 1) ? evalSlotFloat(block, 1, 1f, fiber) : sx;
                    self.bean.scaleX = sx;
                    self.bean.scaleY = sy;
                }
                return false;
            } else if ("changeSize".equals(op)) {
                if (self != null) {
                    float d = evalSlotFloat(block, 0, 10f, fiber) / 100f;
                    self.bean.scaleX = Math.max(0.05f, self.bean.scaleX + d);
                    self.bean.scaleY = Math.max(0.05f, self.bean.scaleY + d);
                }
                return false;
            } else if ("setSize".equals(op)) {
                if (self != null) {
                    float s = Math.max(0.05f, evalSlotFloat(block, 0, 100f, fiber) / 100f);
                    self.bean.scaleX = s;
                    self.bean.scaleY = s;
                }
                return false;
            } else if ("setColor".equals(op)) {
                if (self != null) self.bean.color = parseColorValue(param(block, 0, "#4CAF50"), self.bean.color);
                return false;
            } else if ("spawnParticles".equals(op)) {
                float px = self != null ? self.bean.x + self.bean.width * 0.5f : project.virtualWidth * 0.5f;
                float py = self != null ? self.bean.y + self.bean.height * 0.5f : project.virtualHeight * 0.5f;
                int pCol = self != null ? self.bean.color : 0xFFFFD54F;
                int pCnt = 12;
                if (hasSlot(block, 3)) {
                    px = evalSlotFloat(block, 0, px, fiber);
                    py = evalSlotFloat(block, 1, py, fiber);
                    pCol = parseColorValue(param(block, 2, "#FFD54F"), pCol);
                    pCnt = Math.round(evalSlotFloat(block, 3, 12f, fiber));
                } else if (hasSlot(block, 1)) {
                    pCol = parseColorValue(param(block, 0, "#FFD54F"), pCol);
                    pCnt = Math.round(evalSlotFloat(block, 1, 12f, fiber));
                } else if (hasSlot(block, 0)) {
                    pCnt = Math.round(evalSlotFloat(block, 0, 12f, fiber));
                }
                emitParticles(px, py, pCol, pCnt);
                return false;
            } else if ("setGravityScale".equals(op)) {
                if (self != null) self.bean.gravityScale = evalSlotFloat(block, 0, 1f, fiber);
                return false;
            } else if ("setPhysicsEnabled".equals(op)) {
                if (self != null) self.bean.physicsEnabled = evalSlotBool(block, 0, true, fiber);
                return false;
            } else if ("setBounce".equals(op)) {
                if (self != null) self.bean.bounce = clamp(evalSlotFloat(block, 0, 0.5f, fiber), 0f, 1.5f);
                return false;
            } else if ("setFriction".equals(op)) {
                if (self != null) self.bean.friction = clamp(evalSlotFloat(block, 0, 0.2f, fiber), 0f, 1f);
                return false;
            } else if ("setCircleCollider".equals(op)) {
                if (self != null) self.bean.circleCollider = evalSlotBool(block, 0, true, fiber);
                return false;
            } else if ("setOneWay".equals(op)) {
                if (self != null) self.bean.oneWay = evalSlotBool(block, 0, true, fiber);
                return false;
            } else if ("playSound".equals(op)) {
                playSoundInternal(evalSlotText(block, 0, "", fiber));
                return false;
            } else if ("playBgm".equals(op)) {
                String res = evalSlotText(block, 0, "", fiber);
                boolean loop = !hasSlot(block, 1) || evalSlotBool(block, 1, true, fiber);
                playBgmInternal(res, loop);
                return false;
            } else if ("stopBgm".equals(op) || "stopAllSounds".equals(op)) {
                stopBgmInternal();
                return false;
            } else if ("changeVolume".equals(op)) {
                masterVolumePercent = clamp(masterVolumePercent + evalSlotFloat(block, 0, -10f, fiber), 0f, 100f);
                applyVolumeToBgm();
                return false;
            } else if ("setVolume".equals(op)) {
                masterVolumePercent = clamp(evalSlotFloat(block, 0, 100f, fiber), 0f, 100f);
                applyVolumeToBgm();
                return false;
            } else if ("resetTimer".equals(op)) {
                gameTimerSeconds = 0f;
                return false;
            } else if ("setHudText".equals(op)) {
                if (hasSlot(block, 1)) {
                    RuntimeEntity t = findByIdOrName(param(block, 0, ""));
                    String v = evalSlotString(block, 1, "", fiber);
                    if (t != null) t.bean.text = v;
                } else if (self != null) {
                    self.bean.text = evalSlotString(block, 0, "", fiber);
                }
                return false;
            } else if ("setHudBar".equals(op)) {
                RuntimeEntity t = self;
                int valIdx = 0;
                int maxIdx = 1;
                if (hasSlot(block, 1)) {
                    RuntimeEntity named = findByIdOrName(param(block, 0, ""));
                    if (named != null) {
                        t = named;
                        valIdx = 1;
                        maxIdx = 2;
                    }
                }
                if (t != null) {
                    float val = evalSlotFloat(block, valIdx, 100f, fiber);
                    float max = hasSlot(block, maxIdx) ? Math.max(0.0001f, evalSlotFloat(block, maxIdx, 100f, fiber)) : 100f;
                    t.bean.text = formatNumber(val) + "/" + formatNumber(max);
                }
                return false;
            } else if ("showDialog".equals(op)) {
                String msg = evalSlotText(block, 0, "", fiber);
                String speaker = evalSlotText(block, 1, "", fiber);
                RuntimeEntity dlg = findOrCreateHudDialog();
                dlg.bean.text = msg;
                dlg.bean.actionKey = speaker;
                dlg.bean.visible = true;
                dlg.dialogVisibleChars = 0f;
                return false;
            } else if ("hideDialog".equals(op)) {
                for (RuntimeEntity o : objects) {
                    if (!o.destroyed && "hud_dialog".equals(o.bean.type)) {
                        o.bean.visible = false;
                    }
                }
                return false;
            } else if ("loadScene".equals(op)) {
                pendingSceneLoadId = evalSlotText(block, 0, "", fiber);
                return true;
            } else if ("loadSceneWithTransition".equals(op)) {
                String targetScene = evalSlotText(block, 0, "", fiber);
                String trans = evalSlotText(block, 1, "fade", fiber);
                startSceneTransition(targetScene, trans);
                return true;
            } else if ("nextScene".equals(op)) {
                String nextId = resolveAdjacentSceneId(1);
                if (nextId != null) {
                    startSceneTransition(nextId, "fade");
                    return true;
                }
                return false;
            } else if ("previousScene".equals(op)) {
                String prevId = resolveAdjacentSceneId(-1);
                if (prevId != null) {
                    startSceneTransition(prevId, "fade");
                    return true;
                }
                return false;
            } else if ("restartScene".equals(op)) {
                pendingSceneRestart = true;
                return true;
            } else if ("spawnPrefab".equals(op)) {
                String name = evalSlotText(block, 0, "", fiber);
                float sx = hasSlot(block, 1) ? evalSlotFloat(block, 1, 0f, fiber) : (self != null ? self.bean.x : 100f);
                float sy = hasSlot(block, 2) ? evalSlotFloat(block, 2, 0f, fiber) : (self != null ? self.bean.y : 100f);
                spawnPrefabAt(name, sx, sy);
                return false;
            } else if ("setDestroyOffscreen".equals(op)) {
                if (self != null) self.bean.destroyOffscreen = evalSlotBool(block, 0, true, fiber);
                return false;
            } else if ("destroySelf".equals(op)) {
                if (self != null) self.destroyed = true;
                fiber.finished = true;
                return true;
            } else if ("destroyOther".equals(op)) {
                String filter = param(block, 0, "").trim();
                if (!filter.isEmpty()) {
                    if (fiber.other != null && (filter.equalsIgnoreCase(fiber.other.bean.tag)
                            || filter.equalsIgnoreCase(fiber.other.bean.name)
                            || filter.equals(fiber.other.bean.id))) {
                        fiber.other.destroyed = true;
                    } else {
                        RuntimeEntity t = findByIdOrName(filter);
                        if (t != null) t.destroyed = true;
                    }
                } else if (fiber.other != null) {
                    fiber.other.destroyed = true;
                }
                return false;
            } else if ("saveGameData".equals(op)) {
                String key = evalSlotText(block, 0, "", fiber).trim();
                if (!key.isEmpty()) {
                    String val = evalSlotString(block, 1, "", fiber);
                    SharedPreferences prefs = getSavePrefs();
                    if (prefs != null) {
                        prefs.edit().putString(key, val).apply();
                    }
                }
                return false;
            } else if ("sendMessage".equals(op) || "sendMessageWait".equals(op)) {
                enqueueScriptsForEvent("on_message", evalSlotText(block, 0, "", fiber), null, self);
                return false;
            } else if ("setVar".equals(op)) {
                String varName = param(block, 0, "").trim();
                if (!varName.isEmpty()) variables.put(varName, evalSlotString(block, 1, "0", fiber));
                return false;
            } else if ("changeVar".equals(op)) {
                String varName = param(block, 0, "").trim();
                if (!varName.isEmpty()) {
                    double cur = parseDoubleSafe(variables.get(varName), 0.0);
                    double delta = evalSlotDouble(block, 1, 0.0, fiber);
                    variables.put(varName, formatNumber(cur + delta));
                }
                return false;
            }
            return false;
        }

        private RuntimeEntity findOrCreateHudDialog() {
            for (RuntimeEntity o : objects) {
                if (!o.destroyed && "hud_dialog".equals(o.bean.type)) {
                    return o;
                }
            }
            float dw = Math.min(880f, Math.max(260f, project.virtualWidth - 80f));
            float dh = Math.min(180f, Math.max(120f, project.virtualHeight * 0.25f));
            float dx = Math.max(20f, (project.virtualWidth - dw) * 0.5f);
            float dy = Math.max(20f, project.virtualHeight - dh - 28f);
            ObjectData bean = new ObjectData();
            bean.id = "dlg_" + UUID.randomUUID().toString().substring(0, 8);
            bean.name = "Dialog";
            bean.type = "hud_dialog";
            bean.isHud = true;
            bean.x = dx;
            bean.y = dy;
            bean.width = dw;
            bean.height = dh;
            bean.color = 0xFF1E293B;
            bean.zIndex = 200;
            RuntimeEntity created = new RuntimeEntity(bean);
            objects.add(created);
            return created;
        }

        private SharedPreferences getSavePrefs() {
            return appContext != null
                    ? appContext.getSharedPreferences("sketchware_game_save", Context.MODE_PRIVATE)
                    : null;
        }

        private String loadSavedValue(String key, String fallback) {
            if (key == null || key.trim().isEmpty()) return fallback;
            SharedPreferences prefs = getSavePrefs();
            if (prefs == null) return fallback;
            return prefs.getString(key.trim(), fallback);
        }

        private RuntimeEntity spawnPrefabAt(String prefabName, float x, float y) {
            if (prefabName == null || prefabName.isEmpty()) return null;
            ObjectData template = null;
            String resolvedTemplateId = "";
            for (ObjectData p : project.prefabs) {
                if (prefabName.equalsIgnoreCase(p.name) || prefabName.equalsIgnoreCase(p.id)) {
                    template = p;
                    resolvedTemplateId = p.id != null ? p.id : "";
                    break;
                }
            }
            if (template == null) {
                RuntimeEntity existing = findByIdOrName(prefabName);
                if (existing != null) {
                    template = existing.bean;
                    resolvedTemplateId = existing.matchKey();
                }
            }
            if (template == null) return null;
            ObjectData copy = template.copy();
            copy.id = "obj_" + UUID.randomUUID().toString().substring(0, 8);
            copy.x = x;
            copy.y = y;
            RuntimeEntity spawned = new RuntimeEntity(copy);
            spawned.templateId = (resolvedTemplateId != null && !resolvedTemplateId.isEmpty()) ? resolvedTemplateId : template.id;
            spawned.enteredScreen = isInCameraViewport(spawned, 0f);
            objects.add(spawned);
            enqueueScriptsForTarget("on_scene_start", "", spawned.templateId, spawned, null);
            return spawned;
        }

        private void pushStack(Fiber fiber, List<BlockData> list, int iterations) {
            if (list == null || list.isEmpty() || iterations <= 0) return;
            fiber.frames.add(new Frame(list, iterations));
        }

        private RuntimeEntity findCollidingWithTag(RuntimeEntity self, String tag) {
            for (String otherId : self.currentColliders) {
                RuntimeEntity other = findById(otherId);
                if (other != null && !other.destroyed) {
                    if (tag == null || tag.isEmpty() || tag.equalsIgnoreCase(other.bean.tag) || tag.equalsIgnoreCase(other.bean.name)) {
                        return other;
                    }
                }
            }
            return null;
        }

        private boolean hasSlot(BlockData b, int idx) {
            return b.getParamBlock(idx) != null || hasParam(b, idx);
        }

        private float evalSlotFloat(BlockData b, int idx, float fallback, Fiber fiber) {
            return (float) evalSlotDouble(b, idx, fallback, fiber);
        }

        private double evalSlotDouble(BlockData b, int idx, double fallback, Fiber fiber) {
            BlockData nested = b.getParamBlock(idx);
            if (nested != null) return toDouble(evaluateBlockValue(nested, fiber));
            if (!hasParam(b, idx)) return fallback;
            return evalDouble(param(b, idx, String.valueOf(fallback)), fiber);
        }

        private boolean evalSlotBool(BlockData b, int idx, boolean fallback, Fiber fiber) {
            BlockData nested = b.getParamBlock(idx);
            if (nested != null) return toBool(evaluateBlockValue(nested, fiber));
            if (!hasParam(b, idx)) return fallback;
            return evalBool(param(b, idx, fallback ? "true" : "false"), fiber);
        }

        private String evalSlotString(BlockData b, int idx, String fallback, Fiber fiber) {
            BlockData nested = b.getParamBlock(idx);
            if (nested != null) return valueToString(evaluateBlockValue(nested, fiber));
            if (!hasParam(b, idx)) return fallback;
            return evalString(param(b, idx, fallback), fiber);
        }

        private String evalSlotText(BlockData b, int idx, String fallback, Fiber fiber) {
            BlockData nested = b.getParamBlock(idx);
            if (nested != null) return valueToString(evaluateBlockValue(nested, fiber));
            if (!hasParam(b, idx)) return fallback;
            return evalStringOrRaw(param(b, idx, fallback), fiber);
        }

        private Object evalSlotValue(BlockData b, int idx, Fiber fiber) {
            BlockData nested = b.getParamBlock(idx);
            if (nested != null) return evaluateBlockValue(nested, fiber);
            String raw = param(b, idx, "");
            if (raw.isEmpty()) return "";
            if (isNumericString(raw)) return Double.parseDouble(raw);
            if ("true".equalsIgnoreCase(raw)) return true;
            if ("false".equalsIgnoreCase(raw)) return false;
            try {
                if (raw.contains("+") || raw.contains("-") || raw.contains("*") || raw.contains("/") || raw.contains("(")) {
                    return evaluateExpression(raw, fiber);
                }
            } catch (Throwable ignored) {
            }
            if (variables.containsKey(raw)) return resolveBuiltInOrVar(raw, fiber);
            return raw;
        }

        private Object evaluateBlockValue(BlockData b, Fiber fiber) {
            if (b == null || b.opCode == null) return 0.0;
            String op = b.opCode.trim();
            RuntimeEntity self = fiber != null ? fiber.self : null;
            if ("opAdd".equals(op)) return evalSlotDouble(b, 0, 0.0, fiber) + evalSlotDouble(b, 1, 0.0, fiber);
            if ("opSub".equals(op)) return evalSlotDouble(b, 0, 0.0, fiber) - evalSlotDouble(b, 1, 0.0, fiber);
            if ("opMul".equals(op)) return evalSlotDouble(b, 0, 0.0, fiber) * evalSlotDouble(b, 1, 0.0, fiber);
            if ("opDiv".equals(op)) {
                double d = evalSlotDouble(b, 1, 1.0, fiber);
                return d == 0.0 ? 0.0 : evalSlotDouble(b, 0, 0.0, fiber) / d;
            }
            if ("opMod".equals(op)) {
                double d = evalSlotDouble(b, 1, 1.0, fiber);
                return d == 0.0 ? 0.0 : evalSlotDouble(b, 0, 0.0, fiber) % d;
            }
            if ("opRandom".equals(op)) {
                double lo = evalSlotDouble(b, 0, 1.0, fiber);
                double hi = evalSlotDouble(b, 1, 10.0, fiber);
                if (lo > hi) { double t = lo; lo = hi; hi = t; }
                boolean intMode = b.getParamBlock(0) == null && b.getParamBlock(1) == null
                        && !param(b, 0, "1").contains(".") && !param(b, 1, "10").contains(".");
                if (intMode) {
                    int il = (int) Math.round(lo);
                    int ih = (int) Math.round(hi);
                    return (double) (il + random.nextInt(Math.max(1, ih - il + 1)));
                }
                return lo + random.nextDouble() * (hi - lo);
            }
            if ("opGt".equals(op)) return evalSlotDouble(b, 0, 0.0, fiber) > evalSlotDouble(b, 1, 0.0, fiber);
            if ("opLt".equals(op)) return evalSlotDouble(b, 0, 0.0, fiber) < evalSlotDouble(b, 1, 0.0, fiber);
            if ("opEq".equals(op)) return valuesEqual(evalSlotValue(b, 0, fiber), evalSlotValue(b, 1, fiber));
            if ("opAnd".equals(op)) return evalSlotBool(b, 0, false, fiber) && evalSlotBool(b, 1, false, fiber);
            if ("opOr".equals(op)) return evalSlotBool(b, 0, false, fiber) || evalSlotBool(b, 1, false, fiber);
            if ("opNot".equals(op)) return !evalSlotBool(b, 0, false, fiber);
            if ("boolTrue".equals(op)) return true;
            if ("boolFalse".equals(op)) return false;
            if ("opJoin".equals(op)) return evalSlotText(b, 0, "", fiber) + evalSlotText(b, 1, "", fiber);
            if ("opLetterOf".equals(op)) {
                int idx = (int) Math.round(evalSlotDouble(b, 0, 1.0, fiber));
                String text = evalSlotText(b, 1, "", fiber);
                return (idx >= 1 && idx <= text.length()) ? text.substring(idx - 1, idx) : "";
            }
            if ("opLengthOf".equals(op)) return (double) evalSlotText(b, 0, "", fiber).length();
            if ("opContains".equals(op)) return evalSlotText(b, 0, "", fiber).contains(evalSlotText(b, 1, "", fiber));
            if ("opRound".equals(op)) return (double) Math.round(evalSlotDouble(b, 0, 0.0, fiber));
            if ("opMathFn".equals(op)) {
                String fn = evalSlotText(b, 0, "abs", fiber).toLowerCase(Locale.ROOT);
                double x = evalSlotDouble(b, 1, 0.0, fiber);
                return evalMathFn(fn, x);
            }
            if ("opClamp".equals(op)) {
                double val = evalSlotDouble(b, 0, 0.0, fiber);
                double lo = evalSlotDouble(b, 1, 0.0, fiber);
                double hi = evalSlotDouble(b, 2, 100.0, fiber);
                if (lo > hi) { double t = lo; lo = hi; hi = t; }
                return Math.max(lo, Math.min(hi, val));
            }
            if ("getVar".equals(op)) return resolveBuiltInOrVar(param(b, 0, "").trim(), fiber);
            if ("getX".equals(op) || "getPosX".equals(op)) return self != null ? (double) self.bean.x : 0.0;
            if ("getY".equals(op) || "getPosY".equals(op)) return self != null ? (double) self.bean.y : 0.0;
            if ("getVelX".equals(op)) return self != null ? (double) self.bean.velocityX : 0.0;
            if ("getVelY".equals(op)) return self != null ? (double) self.bean.velocityY : 0.0;
            if ("getSpriteName".equals(op)) return self != null && self.bean.spriteResName != null ? self.bean.spriteResName : "";
            if ("getDirection".equals(op) || "getRotation".equals(op)) return self != null ? (double) self.bean.rotation : 0.0;
            if ("getSize".equals(op)) return self != null ? (double) (self.bean.scaleX * 100f) : 100.0;
            if ("getBackdrop".equals(op)) return currentScene != null ? currentScene.name : "";
            if ("getVolume".equals(op)) return (double) masterVolumePercent;
            if ("getTimer".equals(op) || "timer".equals(op)) return (double) gameTimerSeconds;
            if ("getJoyX".equals(op) || "joyX".equals(op)) return (double) joyX;
            if ("getJoyY".equals(op) || "joyY".equals(op)) return (double) joyY;
            if ("getJoyAngle".equals(op) || "joyAngle".equals(op)) return (joyX == 0f && joyY == 0f) ? 0.0 : Math.toDegrees(Math.atan2(joyY, joyX));
            if ("isJoyActive".equals(op) || "isJoystickActive".equals(op)) return joystickPointerId != -1 || Math.hypot(joyX, joyY) > 0.05;
            if ("getTouchX".equals(op) || "touchX".equals(op)) return (double) touchX;
            if ("getTouchY".equals(op) || "touchY".equals(op)) return (double) touchY;
            if ("isTouchDown".equals(op) || "isTouchingScreen".equals(op)) return isTouchingWorld;
            if ("isButtonDown".equals(op) || "isButtonPressed".equals(op) || "ifButtonPressed".equals(op) || "touchingButton".equals(op)) {
                String k = normalizeActionKey(evalSlotText(b, 0, "JUMP", fiber));
                return pressedButtons.contains(k) || latchedButtons.contains(k);
            }
            if ("isOnGround".equals(op)) return self != null && self.onGround;
            if ("getWorldWidth".equals(op)) return (double) getWorldWidth();
            if ("getWorldHeight".equals(op)) return (double) getWorldHeight();
            if ("getSceneName".equals(op)) return currentScene != null ? currentScene.name : "";
            if ("hasNextScene".equals(op)) return resolveAdjacentSceneId(1) != null;
            if ("isOutsideWorld".equals(op)) return isOutsideWorld(self);
            if ("hasLeftScreen".equals(op)) return hasLeftScreen(self);
            if ("isActionJustPressed".equals(op)) {
                String k = normalizeActionKey(evalSlotText(b, 0, "FIRE", fiber));
                return justPressedButtons.contains(k);
            }
            if ("loadGameData".equals(op)) {
                String key = evalSlotText(b, 0, "unlocked_level", fiber);
                String def = evalSlotText(b, 1, "1", fiber);
                return loadSavedValue(key, def);
            }
            if ("isTouchingTarget".equals(op) || "isTouchingObj".equals(op) || "isCollidingTag".equals(op) || "touchingTag".equals(op) || "touchingObject".equals(op)) {
                return checkTouchingTarget(self, evalSlotText(b, 0, "", fiber), fiber);
            }
            if ("distanceTo".equals(op)) {
                return evalDistanceTo(self, evalSlotText(b, 0, "touch", fiber));
            }
            if ("getObjectProp".equals(op) || "getProperty".equals(op) || "propOf".equals(op)) {
                String prop = evalSlotText(b, 0, "x", fiber);
                String target = evalSlotText(b, 1, "self", fiber);
                return evalObjectProperty(target, prop, self);
            }
            if ("getCurrentTime".equals(op)) {
                return evalCurrentTimeField(evalSlotText(b, 0, "second", fiber));
            }
            return 0.0;
        }

        private double evalMathFn(String fn, double x) {
            if (fn.contains("abs")) return Math.abs(x);
            if (fn.contains("floor") || fn.contains("chao")) return Math.floor(x);
            if (fn.contains("ceil") || fn.contains("teto")) return Math.ceil(x);
            if (fn.contains("sqrt") || fn.contains("raiz")) return Math.sqrt(Math.max(0.0, x));
            if ("asin".equals(fn)) return Math.toDegrees(Math.asin(clamp((float) x, -1f, 1f)));
            if ("acos".equals(fn)) return Math.toDegrees(Math.acos(clamp((float) x, -1f, 1f)));
            if ("atan".equals(fn)) return Math.toDegrees(Math.atan(x));
            if ("sin".equals(fn) || "sen".equals(fn)) return Math.sin(Math.toRadians(x));
            if ("cos".equals(fn)) return Math.cos(Math.toRadians(x));
            if ("tan".equals(fn)) return Math.tan(Math.toRadians(x));
            if ("ln".equals(fn)) return x > 0.0 ? Math.log(x) : 0.0;
            if (fn.contains("log")) return x > 0.0 ? Math.log10(x) : 0.0;
            if (fn.contains("e^") || fn.contains("e ^") || "exp".equals(fn)) return Math.exp(x);
            if (fn.contains("10^") || fn.contains("10 ^") || "pow10".equals(fn)) return Math.pow(10.0, x);
            return x;
        }

        private boolean checkTouchingTarget(RuntimeEntity self, String target, Fiber fiber) {
            if (self == null || self.destroyed) return false;
            String t = target == null ? "" : target.trim();
            if ("edge".equalsIgnoreCase(t) || "borda".equalsIgnoreCase(t)) {
                return self.bean.x <= 0.5f || self.bean.y <= 0.5f
                        || self.bean.x + self.bean.width >= project.virtualWidth - 0.5f
                        || self.bean.y + self.bean.height >= project.virtualHeight - 0.5f;
            }
            if ("touch".equalsIgnoreCase(t) || "pointer".equalsIgnoreCase(t) || "ponteiro".equalsIgnoreCase(t)) {
                return isTouchingWorld
                        && touchX >= self.bean.x && touchX <= self.bean.x + self.bean.width
                        && touchY >= self.bean.y && touchY <= self.bean.y + self.bean.height;
            }
            RuntimeEntity match = findCollidingWithTag(self, t);
            if (match != null) {
                if (fiber != null) fiber.other = match;
                return true;
            }
            return false;
        }

        private double evalDistanceTo(RuntimeEntity self, String target) {
            if (self == null) return 0.0;
            float cx = self.bean.x + self.bean.width * 0.5f;
            float cy = self.bean.y + self.bean.height * 0.5f;
            String t = target == null ? "" : target.trim();
            if ("touch".equalsIgnoreCase(t) || "pointer".equalsIgnoreCase(t) || "ponteiro".equalsIgnoreCase(t)) {
                return Math.hypot(touchX - cx, touchY - cy);
            }
            RuntimeEntity other = findByIdOrName(t);
            if (other == null) return 0.0;
            float ox = other.bean.x + other.bean.width * 0.5f;
            float oy = other.bean.y + other.bean.height * 0.5f;
            return Math.hypot(ox - cx, oy - cy);
        }

        private Object evalObjectProperty(String target, String prop, RuntimeEntity self) {
            String p = prop == null ? "" : prop.trim().toLowerCase(Locale.ROOT);
            if ("backdrop".equals(p) || "cenario".equals(p) || "stage".equalsIgnoreCase(target) || "palco".equalsIgnoreCase(target)) {
                return currentScene != null ? currentScene.name : "";
            }
            RuntimeEntity obj = resolveTarget(target, self);
            if (obj == null) return 0.0;
            if ("x".equals(p)) return (double) obj.bean.x;
            if ("y".equals(p)) return (double) obj.bean.y;
            if ("vx".equals(p)) return (double) obj.bean.velocityX;
            if ("vy".equals(p)) return (double) obj.bean.velocityY;
            if (p.contains("dir") || p.contains("rot")) return (double) obj.bean.rotation;
            if (p.contains("size") || p.contains("tam")) return (double) (obj.bean.scaleX * 100f);
            if (p.contains("name") || p.contains("nome")) return obj.bean.name;
            if (p.contains("vis")) return obj.bean.visible;
            return (double) obj.bean.x;
        }

        private double evalCurrentTimeField(String field) {
            String f = field == null ? "" : field.trim().toLowerCase(Locale.ROOT);
            Calendar cal = Calendar.getInstance();
            if (f.contains("year") || f.contains("ano")) return cal.get(Calendar.YEAR);
            if (f.contains("month") || f.contains("mes")) return cal.get(Calendar.MONTH) + 1;
            if (f.contains("day") && f.contains("week") || f.contains("semana")) return cal.get(Calendar.DAY_OF_WEEK);
            if (f.contains("date") || f.contains("dia")) return cal.get(Calendar.DAY_OF_MONTH);
            if (f.contains("hour") || f.contains("hora")) return cal.get(Calendar.HOUR_OF_DAY);
            if (f.contains("min")) return cal.get(Calendar.MINUTE);
            return cal.get(Calendar.SECOND);
        }

        private float evalFloat(String expr, Fiber fiber) {
            return (float) evalDouble(expr, fiber);
        }

        private double evalDouble(String expr, Fiber fiber) {
            return toDouble(evaluateExpression(expr, fiber));
        }

        private boolean evalBool(String expr, Fiber fiber) {
            return toBool(evaluateExpression(expr, fiber));
        }

        private String evalString(String expr, Fiber fiber) {
            if (expr == null) return "";
            String trimmed = expr.trim();
            if (trimmed.isEmpty()) return "";
            char first = trimmed.charAt(0);
            if (trimmed.contains("{") && trimmed.contains("}") && first != '"' && first != 39) {
                return interpolateTemplate(trimmed);
            }
            try {
                Object val = new ExprParser(trimmed, fiber).parse();
                if (val instanceof Double) return formatNumber((Double) val);
                if (val instanceof Boolean) return ((Boolean) val) ? "true" : "false";
                return val == null ? "" : val.toString();
            } catch (Throwable ignored) {
                return interpolateTemplate(trimmed);
            }
        }

        private String evalStringOrRaw(String expr, Fiber fiber) {
            if (expr == null) return "";
            String trimmed = expr.trim();
            if (trimmed.length() >= 2) {
                char f = trimmed.charAt(0);
                char l = trimmed.charAt(trimmed.length() - 1);
                if ((f == '"' && l == '"') || (f == 39 && l == 39)) {
                    return trimmed.substring(1, trimmed.length() - 1);
                }
            }
            if (variables.containsKey(trimmed)) return variables.get(trimmed);
            if (trimmed.contains("+") || trimmed.contains("(")) return evalString(trimmed, fiber);
            return trimmed;
        }

        private Object evaluateExpression(String expr, Fiber fiber) {
            if (expr == null || expr.trim().isEmpty()) return 0.0;
            try {
                return new ExprParser(expr.trim(), fiber).parse();
            } catch (Throwable ignored) {
                return 0.0;
            }
        }

        private String interpolateTemplate(String text) {
            StringBuilder sb = new StringBuilder();
            int i = 0;
            while (i < text.length()) {
                char c = text.charAt(i);
                if (c == '{') {
                    int end = text.indexOf('}', i + 1);
                    if (end > i + 1) {
                        String key = text.substring(i + 1, end).trim();
                        Object o = resolveBuiltInOrVar(key, null);
                        sb.append(o instanceof Double ? formatNumber((Double) o) : (o == null ? "0" : o.toString()));
                        i = end + 1;
                        continue;
                    }
                }
                sb.append(c);
                i++;
            }
            return sb.toString();
        }

        private Object resolveBuiltInOrVar(String name, Fiber fiber) {
            if (name == null) return 0.0;
            if ("joyX".equals(name) || "joystickX".equals(name)) return (double) joyX;
            if ("joyY".equals(name) || "joystickY".equals(name)) return (double) joyY;
            if ("joyAngle".equals(name) || "joystickAngle".equals(name)) {
                return (joyX == 0f && joyY == 0f) ? 0.0 : Math.toDegrees(Math.atan2(joyY, joyX));
            }
            if ("joyActive".equals(name) || "joystickActive".equals(name)) {
                return joystickPointerId != -1 || Math.hypot(joyX, joyY) > 0.05;
            }
            if ("touchX".equals(name)) return (double) touchX;
            if ("touchY".equals(name)) return (double) touchY;
            if ("isTouching".equals(name) || "touchDown".equals(name)) return isTouchingWorld;
            if ("timer".equals(name)) return (double) gameTimerSeconds;
            if ("volume".equals(name)) return (double) masterVolumePercent;
            if ("fps".equals(name)) return (double) currentFps;
            if ("dt".equals(name)) return (double) FIXED_DT;
            if ("camX".equals(name)) return (double) camX;
            if ("camY".equals(name)) return (double) camY;
            if ("virtualWidth".equals(name) || "screenWidth".equals(name)) return (double) project.virtualWidth;
            if ("virtualHeight".equals(name) || "screenHeight".equals(name)) return (double) project.virtualHeight;
            if ("worldWidth".equals(name)) return (double) getWorldWidth();
            if ("worldHeight".equals(name)) return (double) getWorldHeight();
            if ("sceneName".equals(name) || "backdrop".equals(name)) return currentScene != null ? currentScene.name : "";
            if ("hasNextScene".equals(name)) return resolveAdjacentSceneId(1) != null;
            if ("outsideWorld".equals(name)) return isOutsideWorld(fiber != null ? fiber.self : null);
            if ("leftScreen".equals(name)) return hasLeftScreen(fiber != null ? fiber.self : null);
            if ("isOnGround".equals(name) || "onGround".equals(name)) return fiber != null && fiber.self != null && fiber.self.onGround;
            if ("true".equals(name)) return true;
            if ("false".equals(name)) return false;
            if ("selfX".equals(name) || "x".equals(name)) return fiber != null && fiber.self != null ? (double) fiber.self.bean.x : 0.0;
            if ("selfY".equals(name) || "y".equals(name)) return fiber != null && fiber.self != null ? (double) fiber.self.bean.y : 0.0;
            if ("selfVX".equals(name) || "vx".equals(name)) return fiber != null && fiber.self != null ? (double) fiber.self.bean.velocityX : 0.0;
            if ("selfVY".equals(name) || "vy".equals(name)) return fiber != null && fiber.self != null ? (double) fiber.self.bean.velocityY : 0.0;
            if ("selfRot".equals(name) || "rotation".equals(name) || "direction".equals(name)) return fiber != null && fiber.self != null ? (double) fiber.self.bean.rotation : 0.0;
            if ("size".equals(name)) return fiber != null && fiber.self != null ? (double) (fiber.self.bean.scaleX * 100f) : 100.0;
            if ("spriteName".equals(name)) return fiber != null && fiber.self != null ? fiber.self.bean.spriteResName : "";
            if ("selfWidth".equals(name)) return fiber != null && fiber.self != null ? (double) fiber.self.bean.width : 0.0;
            if ("selfHeight".equals(name)) return fiber != null && fiber.self != null ? (double) fiber.self.bean.height : 0.0;
            if (variables.containsKey(name)) {
                String raw = variables.get(name);
                if (isNumericString(raw)) return Double.parseDouble(raw);
                return raw == null ? "" : raw;
            }
            return 0.0;
        }

        private final class ExprParser {
            private final String src;
            private final Fiber fiber;
            private int pos = 0;

            ExprParser(String src, Fiber fiber) {
                this.src = src;
                this.fiber = fiber;
            }

            Object parse() {
                Object val = parseOr();
                skipSpaces();
                if (pos < src.length()) throw new IllegalArgumentException("Trailing char");
                return val;
            }

            private void skipSpaces() {
                while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
            }

            private boolean match(String token) {
                skipSpaces();
                if (src.startsWith(token, pos)) {
                    pos += token.length();
                    return true;
                }
                return false;
            }

            private boolean matchKeyword(String kw) {
                skipSpaces();
                if (src.regionMatches(true, pos, kw, 0, kw.length())) {
                    int end = pos + kw.length();
                    if (end >= src.length() || (!Character.isLetterOrDigit(src.charAt(end)) && src.charAt(end) != '_')) {
                        pos = end;
                        return true;
                    }
                }
                return false;
            }
""";

    private static final String MAIN_ACTIVITY_BODY_TEMPLATE_PART3 = """
            private Object parseOr() {
                Object left = parseAnd();
                while (match("||") || matchKeyword("or")) {
                    Object right = parseAnd();
                    left = toBool(left) || toBool(right);
                }
                return left;
            }

            private Object parseAnd() {
                Object left = parseEquality();
                while (match("&&") || matchKeyword("and")) {
                    Object right = parseEquality();
                    left = toBool(left) && toBool(right);
                }
                return left;
            }

            private Object parseEquality() {
                Object left = parseRelational();
                while (true) {
                    if (match("==") || match("=")) {
                        left = valuesEqual(left, parseRelational());
                    } else if (match("!=") || match("<>")) {
                        left = !valuesEqual(left, parseRelational());
                    } else {
                        return left;
                    }
                }
            }

            private Object parseRelational() {
                Object left = parseAdditive();
                while (true) {
                    if (match("<=")) left = toDouble(left) <= toDouble(parseAdditive());
                    else if (match(">=")) left = toDouble(left) >= toDouble(parseAdditive());
                    else if (match("<")) left = toDouble(left) < toDouble(parseAdditive());
                    else if (match(">")) left = toDouble(left) > toDouble(parseAdditive());
                    else return left;
                }
            }

            private Object parseAdditive() {
                Object left = parseMultiplicative();
                while (true) {
                    if (match("+")) {
                        Object right = parseMultiplicative();
                        if (left instanceof String || right instanceof String) {
                            left = valueToString(left) + valueToString(right);
                        } else {
                            left = toDouble(left) + toDouble(right);
                        }
                    } else if (match("-")) {
                        left = toDouble(left) - toDouble(parseMultiplicative());
                    } else {
                        return left;
                    }
                }
            }

            private Object parseMultiplicative() {
                Object left = parseUnary();
                while (true) {
                    if (match("*")) left = toDouble(left) * toDouble(parseUnary());
                    else if (match("/")) {
                        double d = toDouble(parseUnary());
                        left = d == 0.0 ? 0.0 : toDouble(left) / d;
                    } else if (match("%")) {
                        double d = toDouble(parseUnary());
                        left = d == 0.0 ? 0.0 : toDouble(left) % d;
                    } else return left;
                }
            }

            private Object parseUnary() {
                if (match("!") || matchKeyword("not")) return !toBool(parseUnary());
                if (match("-")) return -toDouble(parseUnary());
                if (match("+")) return toDouble(parseUnary());
                return parsePrimary();
            }

            private Object parsePrimary() {
                skipSpaces();
                if (pos >= src.length()) throw new IllegalArgumentException("EOF");
                char c = src.charAt(pos);
                if (c == '(') {
                    pos++;
                    Object inner = parseOr();
                    if (!match(")")) throw new IllegalArgumentException("Missing ')'");
                    return inner;
                }
                if (c == '"' || c == 39) {
                    return parseQuoted(c);
                }
                if (Character.isDigit(c) || (c == '.' && pos + 1 < src.length() && Character.isDigit(src.charAt(pos + 1)))) {
                    int start = pos;
                    boolean dot = false;
                    while (pos < src.length()) {
                        char ch = src.charAt(pos);
                        if (Character.isDigit(ch)) pos++;
                        else if (ch == '.' && !dot) {
                            dot = true;
                            pos++;
                        } else break;
                    }
                    return Double.parseDouble(src.substring(start, pos));
                }
                if (Character.isLetter(c) || c == '_') {
                    int start = pos;
                    while (pos < src.length()) {
                        char ch = src.charAt(pos);
                        if (Character.isLetterOrDigit(ch) || ch == '_') pos++;
                        else break;
                    }
                    String ident = src.substring(start, pos);
                    skipSpaces();
                    if (pos < src.length() && src.charAt(pos) == '(') return parseCall(ident);
                    return resolveBuiltInOrVar(ident, fiber);
                }
                throw new IllegalArgumentException("Bad char");
            }

            private String parseQuoted(char quote) {
                pos++;
                StringBuilder sb = new StringBuilder();
                while (pos < src.length()) {
                    char ch = src.charAt(pos++);
                    if (ch == 92 && pos < src.length()) sb.append(src.charAt(pos++));
                    else if (ch == quote) return sb.toString();
                    else sb.append(ch);
                }
                return sb.toString();
            }

            private Object parseCall(String fn) {
                pos++;
                ArrayList<String> rawArgs = new ArrayList<String>();
                ArrayList<Object> evalArgs = new ArrayList<Object>();
                skipSpaces();
                if (!match(")")) {
                    while (true) {
                        skipSpaces();
                        int argStart = pos;
                        if (isSingleBareIdentArg()) {
                            int idStart = pos;
                            while (pos < src.length() && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_')) pos++;
                            String rawIdent = src.substring(idStart, pos);
                            rawArgs.add(rawIdent);
                            evalArgs.add(variables.containsKey(rawIdent) ? resolveBuiltInOrVar(rawIdent, fiber) : rawIdent);
                        } else {
                            Object val = parseOr();
                            rawArgs.add(src.substring(argStart, pos).trim());
                            evalArgs.add(val);
                        }
                        if (match(",")) continue;
                        if (!match(")")) throw new IllegalArgumentException("Missing ')'");
                        break;
                    }
                }
                if ("random".equals(fn)) {
                    double a = evalArgs.size() >= 1 ? toDouble(evalArgs.get(0)) : 0.0;
                    double b = evalArgs.size() >= 2 ? toDouble(evalArgs.get(1)) : 1.0;
                    if (a > b) { double t = a; a = b; b = t; }
                    boolean intRange = rawArgs.size() >= 2 && !rawArgs.get(0).contains(".") && !rawArgs.get(1).contains(".");
                    if (intRange) {
                        int lo = (int) Math.round(a);
                        int hi = (int) Math.round(b);
                        return (double) (lo + random.nextInt(Math.max(1, hi - lo + 1)));
                    }
                    return a + random.nextDouble() * (b - a);
                } else if ("abs".equals(fn)) return evalArgs.isEmpty() ? 0.0 : Math.abs(toDouble(evalArgs.get(0)));
                else if ("sqrt".equals(fn)) return evalArgs.isEmpty() ? 0.0 : Math.sqrt(Math.max(0.0, toDouble(evalArgs.get(0))));
                else if ("sin".equals(fn)) return evalArgs.isEmpty() ? 0.0 : Math.sin(Math.toRadians(toDouble(evalArgs.get(0))));
                else if ("cos".equals(fn)) return evalArgs.isEmpty() ? 0.0 : Math.cos(Math.toRadians(toDouble(evalArgs.get(0))));
                else if ("tan".equals(fn)) return evalArgs.isEmpty() ? 0.0 : Math.tan(Math.toRadians(toDouble(evalArgs.get(0))));
                else if ("asin".equals(fn)) return evalArgs.isEmpty() ? 0.0 : Math.toDegrees(Math.asin(clamp((float) toDouble(evalArgs.get(0)), -1f, 1f)));
                else if ("acos".equals(fn)) return evalArgs.isEmpty() ? 0.0 : Math.toDegrees(Math.acos(clamp((float) toDouble(evalArgs.get(0)), -1f, 1f)));
                else if ("atan".equals(fn)) return evalArgs.isEmpty() ? 0.0 : Math.toDegrees(Math.atan(toDouble(evalArgs.get(0))));
                else if ("ln".equals(fn)) {
                    double v = evalArgs.isEmpty() ? 0.0 : toDouble(evalArgs.get(0));
                    return v > 0.0 ? Math.log(v) : 0.0;
                } else if ("log10".equals(fn) || "log".equals(fn)) {
                    double v = evalArgs.isEmpty() ? 0.0 : toDouble(evalArgs.get(0));
                    return v > 0.0 ? Math.log10(v) : 0.0;
                } else if ("exp".equals(fn)) return evalArgs.isEmpty() ? 1.0 : Math.exp(toDouble(evalArgs.get(0)));
                else if ("pow10".equals(fn)) return evalArgs.isEmpty() ? 1.0 : Math.pow(10.0, toDouble(evalArgs.get(0)));
                else if ("floor".equals(fn)) return evalArgs.isEmpty() ? 0.0 : Math.floor(toDouble(evalArgs.get(0)));
                else if ("ceil".equals(fn)) return evalArgs.isEmpty() ? 0.0 : Math.ceil(toDouble(evalArgs.get(0)));
                else if ("round".equals(fn)) return evalArgs.isEmpty() ? 0.0 : (double) Math.round(toDouble(evalArgs.get(0)));
                else if ("min".equals(fn)) return evalArgs.size() < 2 ? 0.0 : Math.min(toDouble(evalArgs.get(0)), toDouble(evalArgs.get(1)));
                else if ("max".equals(fn)) return evalArgs.size() < 2 ? 0.0 : Math.max(toDouble(evalArgs.get(0)), toDouble(evalArgs.get(1)));
                else if ("clamp".equals(fn)) {
                    if (evalArgs.size() < 3) return 0.0;
                    return Math.max(toDouble(evalArgs.get(1)), Math.min(toDouble(evalArgs.get(2)), toDouble(evalArgs.get(0))));
                } else if ("join".equals(fn)) {
                    String a = evalArgs.size() >= 1 ? valueToString(evalArgs.get(0)) : "";
                    String b = evalArgs.size() >= 2 ? valueToString(evalArgs.get(1)) : "";
                    return a + b;
                } else if ("letterOf".equals(fn)) {
                    int idx = evalArgs.size() >= 1 ? (int) Math.round(toDouble(evalArgs.get(0))) : 1;
                    String s = evalArgs.size() >= 2 ? valueToString(evalArgs.get(1)) : "";
                    return (idx >= 1 && idx <= s.length()) ? s.substring(idx - 1, idx) : "";
                } else if ("lengthOf".equals(fn)) {
                    String s = evalArgs.isEmpty() ? "" : valueToString(evalArgs.get(0));
                    return (double) s.length();
                } else if ("contains".equals(fn)) {
                    String a = evalArgs.size() >= 1 ? valueToString(evalArgs.get(0)) : "";
                    String b = evalArgs.size() >= 2 ? valueToString(evalArgs.get(1)) : "";
                    return a.contains(b);
                } else if ("btn".equals(fn) || "isButtonPressed".equals(fn) || "buttonPressed".equals(fn) || "ifButtonPressed".equals(fn) || "ifKeyPressed".equals(fn) || "keyPressed".equals(fn)) {
                    String k = evalArgs.isEmpty() ? "JUMP" : normalizeActionKey(valueToString(evalArgs.get(0)));
                    return pressedButtons.contains(k) || latchedButtons.contains(k);
                } else if ("touchingTag".equals(fn) || "touchingObj".equals(fn) || "isCollidingTag".equals(fn) || "collidingTag".equals(fn) || "ifCollidingTag".equals(fn)) {
                    if (fiber == null || fiber.self == null) return false;
                    String target = evalArgs.isEmpty() ? "" : valueToString(evalArgs.get(0));
                    return checkTouchingTarget(fiber.self, target, fiber);
                } else if ("dist".equals(fn)) {
                    if (fiber == null || fiber.self == null) return 0.0;
                    String target = evalArgs.isEmpty() ? "touch" : valueToString(evalArgs.get(0));
                    return evalDistanceTo(fiber.self, target);
                } else if ("prop".equals(fn)) {
                    String prop = evalArgs.size() >= 1 ? valueToString(evalArgs.get(0)) : "x";
                    String target = evalArgs.size() >= 2 ? valueToString(evalArgs.get(1)) : "self";
                    return evalObjectProperty(target, prop, fiber != null ? fiber.self : null);
                } else if ("isOnGround".equals(fn) || "onGround".equals(fn)) {
                    return fiber != null && fiber.self != null && fiber.self.onGround;
                } else if ("joystickX".equals(fn) || "joyX".equals(fn)) return (double) joyX;
                else if ("joystickY".equals(fn) || "joyY".equals(fn)) return (double) joyY;
                else if ("worldWidth".equals(fn)) return (double) getWorldWidth();
                else if ("worldHeight".equals(fn)) return (double) getWorldHeight();
                else if ("sceneName".equals(fn)) return currentScene != null ? currentScene.name : "";
                else if ("hasNextScene".equals(fn)) return resolveAdjacentSceneId(1) != null;
                else if ("outsideWorld".equals(fn)) return isOutsideWorld(fiber != null ? fiber.self : null);
                else if ("leftScreen".equals(fn)) return hasLeftScreen(fiber != null ? fiber.self : null);
                else if ("btnJust".equals(fn) || "justPressed".equals(fn)) {
                    String k = evalArgs.isEmpty() ? "FIRE" : valueToString(evalArgs.get(0));
                    return justPressedButtons.contains(normalizeActionKey(k));
                } else if ("loadSave".equals(fn)) {
                    String k = evalArgs.size() >= 1 ? valueToString(evalArgs.get(0)) : "unlocked_level";
                    String def = evalArgs.size() >= 2 ? valueToString(evalArgs.get(1)) : "1";
                    return loadSavedValue(k, def);
                } else if ("joyAngle".equals(fn)) return (joyX == 0f && joyY == 0f) ? 0.0 : Math.toDegrees(Math.atan2(joyY, joyX));
                else if ("joyActive".equals(fn)) return joystickPointerId != -1 || Math.hypot(joyX, joyY) > 0.05;
                else if ("touchDown".equals(fn)) return isTouchingWorld;
                else if ("timer".equals(fn)) return (double) gameTimerSeconds;
                else if ("volume".equals(fn)) return (double) masterVolumePercent;
                else if ("size".equals(fn)) return fiber != null && fiber.self != null ? (double) (fiber.self.bean.scaleX * 100f) : 100.0;
                else if ("spriteName".equals(fn)) return fiber != null && fiber.self != null ? fiber.self.bean.spriteResName : "";
                else if ("getVar".equals(fn) || "var".equals(fn)) return resolveBuiltInOrVar(evalArgs.isEmpty() ? "" : valueToString(evalArgs.get(0)), fiber);
                return 0.0;
            }

            private boolean isSingleBareIdentArg() {
                int p = pos;
                if (p >= src.length() || (!Character.isLetter(src.charAt(p)) && src.charAt(p) != '_')) return false;
                while (p < src.length() && (Character.isLetterOrDigit(src.charAt(p)) || src.charAt(p) == '_')) p++;
                while (p < src.length() && Character.isWhitespace(src.charAt(p))) p++;
                return p < src.length() && (src.charAt(p) == ',' || src.charAt(p) == ')');
            }
        }
""";

    private static final String MAIN_ACTIVITY_BODY_TEMPLATE_PART4 = """
        // ==================== Rendering ====================

        private void renderScene(Canvas canvas) {
            if (currentScene == null) {
                canvas.drawColor(0xFF0B0E14);
                return;
            }
            int vw = Math.max(1, project.virtualWidth);
            int vh = Math.max(1, project.virtualHeight);

            // Full-bleed background across the whole screen (zero black borders)
            canvas.drawColor(currentScene.backgroundColor);
            if (currentScene.backgroundSprite != null && !currentScene.backgroundSprite.isEmpty()) {
                Bitmap bg = loadBitmap(currentScene.backgroundSprite);
                if (bg != null && !bg.isRecycled()) {
                    drawBitmapCover(canvas, bg, surfaceWidth, surfaceHeight);
                }
            }

            canvas.save();
            canvas.translate(viewOffsetX, viewOffsetY);
            canvas.scale(viewScale, viewScale);

            float safeScale = viewScale > 0.0001f ? viewScale : 1f;
            float extraX = viewOffsetX / safeScale;
            float extraY = viewOffsetY / safeScale;
            float visLeft = -extraX;
            float visTop = -extraY;
            float visRight = vw + extraX;
            float visBottom = vh + extraY;

            float shakeX = 0f;
            float shakeY = 0f;
            if (shakeRemaining > 0f && shakeDuration > 0f) {
                float decay = shakeRemaining / shakeDuration;
                float mag = shakeIntensity * decay;
                shakeX = (random.nextFloat() * 2f - 1f) * mag;
                shakeY = (random.nextFloat() * 2f - 1f) * mag;
            }

            canvas.save();
            canvas.translate(-camX + shakeX, -camY + shakeY);
            ArrayList<RuntimeEntity> world = getWorldObjectsSorted();
            for (RuntimeEntity obj : world) {
                if ("parallax_bg".equals(obj.bean.type)) {
                    drawParallaxBackground(canvas, obj, visLeft, visTop, visRight, visBottom, vh);
                } else {
                    drawWorldObject(canvas, obj);
                }
            }
            drawParticles(canvas);
            drawLighting2DPass(canvas, world, camX + visLeft, camY + visTop, camX + visRight, camY + visBottom);
            for (RuntimeEntity obj : world) drawSpeechBubble(canvas, obj);
            canvas.restore();

            ArrayList<RuntimeEntity> hud = getHudObjectsSorted();
            for (RuntimeEntity obj : hud) drawHudObject(canvas, obj);

            canvas.restore();

            if (!transitionPhase.isEmpty() && transitionProgress > 0.001f
                    && !"instant".equalsIgnoreCase(transitionType)) {
                int ovAlpha = Math.round(clamp(transitionProgress, 0f, 1f) * 255f);
                int rgb = "flash".equalsIgnoreCase(transitionType) ? 0x00FFFFFF : 0x00000000;
                fillPaint.setStyle(Paint.Style.FILL);
                fillPaint.setColor((ovAlpha << 24) | rgb);
                canvas.drawRect(0f, 0f, surfaceWidth, surfaceHeight, fillPaint);
                fillPaint.setAlpha(255);
            }
        }

        private void drawBitmapCover(Canvas canvas, Bitmap bmp, float targetW, float targetH) {
            int bw = Math.max(1, bmp.getWidth());
            int bh = Math.max(1, bmp.getHeight());
            float s = Math.max(targetW / bw, targetH / bh);
            float dw = bw * s;
            float dh = bh * s;
            float left = (targetW - dw) * 0.5f;
            float top = (targetH - dh) * 0.5f;
            tempRect.set(left, top, left + dw, top + dh);
            bitmapPaint.setAlpha(255);
            canvas.drawBitmap(bmp, null, tempRect, bitmapPaint);
        }

        private void drawParallaxBackground(
                Canvas canvas,
                RuntimeEntity obj,
                float visLeft,
                float visTop,
                float visRight,
                float visBottom,
                int vh
        ) {
            ObjectData b = obj.bean;
            if (obj.destroyed || !b.visible || b.alpha <= 0f) return;
            int alphaInt = Math.max(0, Math.min(255, Math.round(b.alpha * 255f)));
            float tileW = Math.max(64f, b.width);
            float tileH = Math.max(64f, b.height);
            float px = b.parallaxX;
            float py = b.parallaxY;

            float effX = camX * px - obj.scrollOffsetX;
            float effY = camY * py - obj.scrollOffsetY;
            float modX = ((effX % tileW) + tileW) % tileW;
            float modY = ((effY % tileH) + tileH) % tileH;

            float startX = camX + visLeft - modX - tileW;
            float endX = camX + visRight + tileW;
            boolean tileVert = b.moveY != 0f || tileH >= vh * 0.65f;
            float startY = tileVert ? (camY + visTop - modY - tileH) : (b.y + camY * (1f - py) + obj.scrollOffsetY);
            float endY = tileVert ? (camY + visBottom + tileH) : (startY + 1f);

            Bitmap bmp = loadBitmap(b.spriteResName);
            if (bmp != null && !bmp.isRecycled()) {
                bitmapPaint.setAlpha(alphaInt);
                for (float ty = startY; ty < endY; ty += tileH) {
                    for (float tx = startX; tx < endX; tx += tileW) {
                        tempRect.set(tx, ty, tx + tileW, ty + tileH);
                        canvas.drawBitmap(bmp, null, tempRect, bitmapPaint);
                    }
                }
                bitmapPaint.setAlpha(255);
            } else {
                fillPaint.setStyle(Paint.Style.FILL);
                fillPaint.setColor(b.color);
                fillPaint.setAlpha(alphaInt);
                strokePaint.setStyle(Paint.Style.STROKE);
                strokePaint.setStrokeWidth(2f);
                strokePaint.setColor(0xFFFFFFFF);
                strokePaint.setAlpha(Math.round(alphaInt * 0.14f));
                for (float ty = startY; ty < endY; ty += tileH) {
                    for (float tx = startX; tx < endX; tx += tileW) {
                        tempRect.set(tx, ty, tx + tileW, ty + tileH);
                        canvas.drawRect(tempRect, fillPaint);
                        canvas.drawLine(tx, ty, tx, ty + tileH, strokePaint);
                    }
                }
                fillPaint.setAlpha(255);
            }
        }

        private void drawLighting2DPass(
                Canvas canvas,
                ArrayList<RuntimeEntity> world,
                float left,
                float top,
                float right,
                float bottom
        ) {
            if (currentScene == null || currentScene.ambientDarkness <= 0.01f) return;
            float darkAlpha = clamp(currentScene.ambientDarkness, 0f, 0.96f);
            int saveId = canvas.saveLayer(left, top, right, bottom, null);
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(Color.argb(Math.round(darkAlpha * 255f), 0, 0, 0));
            canvas.drawRect(left, top, right, bottom, fillPaint);

            lightCutPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
            for (RuntimeEntity obj : world) {
                if (obj.destroyed || !obj.bean.visible || obj.bean.alpha <= 0f
                        || !"light_2d".equals(obj.bean.type)) {
                    continue;
                }
                float cx = obj.bean.x + obj.bean.width * 0.5f;
                float cy = obj.bean.y + obj.bean.height * 0.5f;
                float radius = Math.max(24f, Math.max(obj.bean.width, obj.bean.height) * 0.5f);
                int centerCut = Math.round(clamp(obj.bean.alpha, 0f, 1f) * 255f);
                lightCutPaint.setShader(new RadialGradient(
                        cx, cy, radius,
                        Color.argb(centerCut, 0, 0, 0),
                        0x00000000,
                        Shader.TileMode.CLAMP
                ));
                canvas.drawCircle(cx, cy, radius, lightCutPaint);
            }
            lightCutPaint.setShader(null);
            lightCutPaint.setXfermode(null);
            canvas.restoreToCount(saveId);

            for (RuntimeEntity obj : world) {
                if (obj.destroyed || !obj.bean.visible || obj.bean.alpha <= 0f
                        || !"light_2d".equals(obj.bean.type)) {
                    continue;
                }
                float cx = obj.bean.x + obj.bean.width * 0.5f;
                float cy = obj.bean.y + obj.bean.height * 0.5f;
                float radius = Math.max(24f, Math.max(obj.bean.width, obj.bean.height) * 0.5f);
                int c = obj.bean.color;
                int tintAlpha = Math.round(clamp(obj.bean.alpha, 0f, 1f) * 52f);
                fillPaint.setStyle(Paint.Style.FILL);
                fillPaint.setShader(new RadialGradient(
                        cx, cy, radius,
                        Color.argb(tintAlpha, Color.red(c), Color.green(c), Color.blue(c)),
                        0x00000000,
                        Shader.TileMode.CLAMP
                ));
                canvas.drawCircle(cx, cy, radius, fillPaint);
                fillPaint.setShader(null);
            }
            fillPaint.setAlpha(255);
        }

        private void drawWorldObject(Canvas canvas, RuntimeEntity obj) {
            ObjectData b = obj.bean;
            if (obj.destroyed || !b.visible || b.alpha <= 0f) return;
            if ("camera".equals(b.type) || "particles".equals(b.type)
                    || "spawner".equals(b.type) || "light_2d".equals(b.type)) {
                if (project.showDebugHitboxes) drawDebugHitbox(canvas, obj);
                return;
            }

            float w = Math.max(1f, b.width);
            float h = Math.max(1f, b.height);
            float cx = b.x + w * 0.5f;
            float cy = b.y + h * 0.5f;
            int alphaInt = Math.max(0, Math.min(255, Math.round(b.alpha * 255f)));

            canvas.save();
            canvas.translate(cx, cy);
            if (b.rotation != 0f) canvas.rotate(b.rotation);
            if (b.scaleX != 1f || b.scaleY != 1f) canvas.scale(b.scaleX, b.scaleY);

            Bitmap bmp = loadBitmap(b.spriteResName);
            if (bmp != null && !bmp.isRecycled()) {
                bitmapPaint.setAlpha(alphaInt);
                tempRect.set(-w * 0.5f, -h * 0.5f, w * 0.5f, h * 0.5f);
                canvas.drawBitmap(bmp, null, tempRect, bitmapPaint);
                bitmapPaint.setAlpha(255);
            } else if ("world_text".equals(b.type)) {
                Typeface tf = loadFont(b.fontResName);
                textPaint.setTypeface(tf != null ? tf : Typeface.DEFAULT_BOLD);
                textPaint.setTextSize(Math.max(18f, h * 0.6f));
                textPaint.setTextAlign(Paint.Align.CENTER);
                Paint.FontMetrics fm = textPaint.getFontMetrics();
                float base = -(fm.ascent + fm.descent) * 0.5f;
                String txt = b.text == null ? "" : b.text;
                textPaint.setColor(0xAA000000);
                canvas.drawText(txt, 2f, base + 2f, textPaint);
                textPaint.setColor(b.color);
                textPaint.setAlpha(alphaInt);
                canvas.drawText(txt, 0f, base, textPaint);
            } else {
                tempRect.set(-w * 0.5f, -h * 0.5f, w * 0.5f, h * 0.5f);
                fillPaint.setStyle(Paint.Style.FILL);
                fillPaint.setColor(b.color);
                fillPaint.setAlpha(alphaInt);

                if (b.circleCollider || "physics_circle".equals(b.type)) {
                    float r = Math.min(w, h) * 0.5f;
                    canvas.drawCircle(0f, 0f, r, fillPaint);
                    strokePaint.setStyle(Paint.Style.STROKE);
                    strokePaint.setStrokeWidth(2.5f);
                    strokePaint.setColor(0xFFFFFFFF);
                    strokePaint.setAlpha(Math.round(alphaInt * 0.35f));
                    canvas.drawCircle(0f, 0f, r - 1.5f, strokePaint);
                } else if ("trigger".equals(b.type)) {
                    float r = Math.min(w, h) * 0.5f;
                    canvas.drawCircle(0f, 0f, r, fillPaint);
                    strokePaint.setStyle(Paint.Style.STROKE);
                    strokePaint.setStrokeWidth(3f);
                    strokePaint.setColor(0xFFFFFFFF);
                    strokePaint.setAlpha(Math.round(alphaInt * 0.7f));
                    canvas.drawCircle(0f, 0f, r - 2f, strokePaint);
                } else if ("goal".equals(b.type)) {
                    float r = Math.min(w, h) * 0.48f;
                    fillPaint.setAlpha(Math.round(alphaInt * 0.28f));
                    canvas.drawCircle(0f, 0f, r, fillPaint);
                    strokePaint.setStyle(Paint.Style.STROKE);
                    strokePaint.setStrokeWidth(4f);
                    strokePaint.setColor(b.color);
                    strokePaint.setAlpha(alphaInt);
                    canvas.drawCircle(0f, 0f, r - 2f, strokePaint);
                    fillPaint.setColor(0xFFFFFFFF);
                    fillPaint.setAlpha(Math.round(alphaInt * 0.85f));
                    canvas.drawCircle(0f, 0f, r * 0.38f, fillPaint);
                } else if ("checkpoint".equals(b.type)) {
                    fillPaint.setColor(0xFFE2E8F0);
                    fillPaint.setAlpha(alphaInt);
                    canvas.drawRoundRect(-w * 0.14f, -h * 0.46f, -w * 0.02f, h * 0.46f, 4f, 4f, fillPaint);
                    fillPaint.setColor(b.toggled ? 0xFF4CAF50 : b.color);
                    fillPaint.setAlpha(alphaInt);
                    tempPath.reset();
                    tempPath.moveTo(-w * 0.02f, -h * 0.44f);
                    tempPath.lineTo(w * 0.42f, -h * 0.24f);
                    tempPath.lineTo(-w * 0.02f, -h * 0.04f);
                    tempPath.close();
                    canvas.drawPath(tempPath, fillPaint);
                } else if ("death_zone".equals(b.type)) {
                    fillPaint.setAlpha(Math.round(alphaInt * 0.32f));
                    canvas.drawRect(tempRect, fillPaint);
                    strokePaint.setStyle(Paint.Style.STROKE);
                    strokePaint.setStrokeWidth(2.5f);
                    strokePaint.setColor(b.color);
                    strokePaint.setAlpha(Math.round(alphaInt * 0.85f));
                    canvas.drawRect(tempRect, strokePaint);
                    float step = Math.max(24f, h * 0.7f);
                    for (float sx = -w * 0.5f; sx < w * 0.5f; sx += step) {
                        float ex = Math.min(w * 0.5f, sx + h * 0.6f);
                        canvas.drawLine(sx, h * 0.5f, ex, -h * 0.5f, strokePaint);
                    }
                } else if ("hazard".equals(b.type)) {
                    int count = Math.max(1, Math.round(w / Math.max(20f, h)));
                    float sw = w / count;
                    tempPath.reset();
                    for (int i = 0; i < count; i++) {
                        float x0 = -w * 0.5f + i * sw;
                        tempPath.moveTo(x0, h * 0.5f);
                        tempPath.lineTo(x0 + sw * 0.5f, -h * 0.5f);
                        tempPath.lineTo(x0 + sw, h * 0.5f);
                    }
                    canvas.drawPath(tempPath, fillPaint);
                } else if ("collectible".equals(b.type)) {
                    float r = Math.min(w, h) * 0.46f;
                    canvas.drawCircle(0f, 0f, r, fillPaint);
                    strokePaint.setStyle(Paint.Style.STROKE);
                    strokePaint.setStrokeWidth(3f);
                    strokePaint.setColor(0xFFFFF8E1);
                    strokePaint.setAlpha(Math.round(alphaInt * 0.85f));
                    canvas.drawCircle(0f, 0f, r * 0.68f, strokePaint);
                } else if ("bounce_pad".equals(b.type)) {
                    canvas.drawRoundRect(-w * 0.5f, 0f, w * 0.5f, h * 0.5f, 8f, 8f, fillPaint);
                    fillPaint.setColor(0xFFFFFFFF);
                    fillPaint.setAlpha(Math.round(alphaInt * 0.75f));
                    canvas.drawRoundRect(-w * 0.44f, -h * 0.25f, w * 0.44f, 2f, 6f, 6f, fillPaint);
                } else if ("projectile".equals(b.type)) {
                    float r = Math.min(w, h) * 0.5f;
                    canvas.drawRoundRect(tempRect, r, r, fillPaint);
                    fillPaint.setColor(0xFFFFFFFF);
                    fillPaint.setAlpha(Math.round(alphaInt * 0.65f));
                    canvas.drawCircle(w * 0.16f, 0f, Math.max(2f, r * 0.45f), fillPaint);
                } else if ("oneway_platform".equals(b.type)) {
                    fillPaint.setAlpha(Math.round(alphaInt * 0.75f));
                    canvas.drawRoundRect(tempRect, 6f, 6f, fillPaint);
                    fillPaint.setColor(0xFFFFFFFF);
                    fillPaint.setAlpha(Math.round(alphaInt * 0.85f));
                    canvas.drawRect(-w * 0.5f, -h * 0.5f, w * 0.5f, -h * 0.5f + Math.min(5f, h * 0.3f), fillPaint);
                } else if ("platform".equals(b.type) || "moving_platform".equals(b.type)) {
                    canvas.drawRoundRect(tempRect, 10f, 10f, fillPaint);
                    fillPaint.setColor(0xFFFFFFFF);
                    fillPaint.setAlpha(Math.round(alphaInt * 0.18f));
                    tempRect.set(-w * 0.5f, -h * 0.5f, w * 0.5f, -h * 0.5f + Math.min(8f, h * 0.25f));
                    canvas.drawRoundRect(tempRect, 8f, 8f, fillPaint);
                    if ("moving_platform".equals(b.type)) {
                        strokePaint.setStyle(Paint.Style.STROKE);
                        strokePaint.setStrokeWidth(2.5f);
                        strokePaint.setColor(0xFFFFFFFF);
                        strokePaint.setAlpha(Math.round(alphaInt * 0.6f));
                        canvas.drawLine(-w * 0.2f, 0f, w * 0.2f, 0f, strokePaint);
                    }
                } else {
                    canvas.drawRoundRect(tempRect, 14f, 14f, fillPaint);
                    strokePaint.setStyle(Paint.Style.STROKE);
                    strokePaint.setStrokeWidth(2.5f);
                    strokePaint.setColor(0xFFFFFFFF);
                    strokePaint.setAlpha(Math.round(alphaInt * 0.25f));
                    canvas.drawRoundRect(tempRect, 14f, 14f, strokePaint);
                }
            }
            canvas.restore();
            if (project.showDebugHitboxes) drawDebugHitbox(canvas, obj);
        }

        private void drawDebugHitbox(Canvas canvas, RuntimeEntity obj) {
            ObjectData b = obj.bean;
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(2f);
            strokePaint.setColor(b.isSensor ? 0xFFFFD54F : (b.isStatic ? 0xFF4FC3F7 : 0xFF69F0AE));
            strokePaint.setAlpha(220);
            tempRect.set(b.x, b.y, b.x + b.width, b.y + b.height);
            canvas.drawRect(tempRect, strokePaint);
        }

        private void drawSpeechBubble(Canvas canvas, RuntimeEntity obj) {
            if (obj == null || obj.destroyed || !obj.bean.visible || obj.speechTimer == 0f
                    || obj.speechText == null || obj.speechText.isEmpty()) {
                return;
            }
            ObjectData b = obj.bean;
            String text = obj.speechText;
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setTextSize(26f);
            textPaint.setTextAlign(Paint.Align.LEFT);
            textPaint.setColor(0xFF1E293B);
            float padH = 18f;
            float padV = 12f;
            float textW = Math.min(360f, Math.max(48f, textPaint.measureText(text)));
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float textH = fm.descent - fm.ascent;
            float bw = textW + padH * 2f;
            float bh = textH + padV * 2f;
            float cx = b.x + b.width * 0.5f;
            float left = cx - bw * 0.5f;
            float top = b.y - bh - 18f;

            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(0xFFFDFDFD);
            fillPaint.setAlpha(245);
            tempRect.set(left, top, left + bw, top + bh);
            canvas.drawRoundRect(tempRect, 14f, 14f, fillPaint);

            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(2.5f);
            strokePaint.setColor(0xFF94A3B8);
            strokePaint.setAlpha(255);
            canvas.drawRoundRect(tempRect, 14f, 14f, strokePaint);

            if (obj.speechThink) {
                canvas.drawCircle(cx - 4f, b.y - 12f, 5.5f, fillPaint);
                canvas.drawCircle(cx - 4f, b.y - 12f, 5.5f, strokePaint);
                canvas.drawCircle(cx - 10f, b.y - 3f, 3.5f, fillPaint);
                canvas.drawCircle(cx - 10f, b.y - 3f, 3.5f, strokePaint);
            } else {
                speechBubblePath.reset();
                speechBubblePath.moveTo(cx - 8f, top + bh - 1f);
                speechBubblePath.lineTo(cx, top + bh + 12f);
                speechBubblePath.lineTo(cx + 8f, top + bh - 1f);
                speechBubblePath.close();
                canvas.drawPath(speechBubblePath, fillPaint);
                canvas.drawLine(cx - 8f, top + bh, cx, top + bh + 12f, strokePaint);
                canvas.drawLine(cx, top + bh + 12f, cx + 8f, top + bh, strokePaint);
            }

            canvas.drawText(text, left + padH, top + padV - fm.ascent, textPaint);
        }

        private void drawParticles(Canvas canvas) {
            if (particles.isEmpty()) return;
            fillPaint.setStyle(Paint.Style.FILL);
            for (ParticleData p : particles) {
                float ratio = p.maxLife > 0f ? clamp(p.life / p.maxLife, 0f, 1f) : 0f;
                fillPaint.setColor(p.color);
                fillPaint.setAlpha(Math.round(255f * ratio));
                canvas.drawCircle(p.x, p.y, p.size * (0.4f + 0.6f * ratio), fillPaint);
            }
            fillPaint.setAlpha(255);
        }

        private void drawHudObject(Canvas canvas, RuntimeEntity obj) {
            ObjectData b = obj.bean;
            if (obj.destroyed || !b.visible || b.alpha <= 0f) return;
            int alphaInt = Math.max(0, Math.min(255, Math.round(b.alpha * 255f)));

            if ("hud_text".equals(b.type)) {
                Typeface tf = loadFont(b.fontResName);
                textPaint.setTypeface(tf != null ? tf : Typeface.DEFAULT_BOLD);
                textPaint.setTextSize(Math.max(18f, b.height * 0.56f));
                textPaint.setTextAlign(Paint.Align.LEFT);
                String txt = b.text == null ? "" : b.text;
                Paint.FontMetrics fm = textPaint.getFontMetrics();
                float baseline = b.y + b.height * 0.5f - (fm.ascent + fm.descent) * 0.5f;
                textPaint.setColor(0xAA000000);
                canvas.drawText(txt, b.x + 2f, baseline + 2f, textPaint);
                textPaint.setColor(b.color);
                textPaint.setAlpha(alphaInt);
                canvas.drawText(txt, b.x, baseline, textPaint);
            } else if ("hud_button".equals(b.type)) {
                String key = normalizeActionKey(b.actionKey);
                boolean isPressed = pressedButtons.contains(key);
                float scale = isPressed ? 0.93f : 1f;
                float cx = b.x + b.width * 0.5f;
                float cy = b.y + b.height * 0.5f;

                canvas.save();
                canvas.translate(cx, cy);
                canvas.scale(scale, scale);

                float w = b.width;
                float h = b.height;
                boolean isCircle = Math.abs(w - h) <= 12f;
                fillPaint.setStyle(Paint.Style.FILL);
                fillPaint.setColor(b.color);
                fillPaint.setAlpha(Math.round(alphaInt * (isPressed ? 0.62f : 0.34f)));
                strokePaint.setStyle(Paint.Style.STROKE);
                strokePaint.setStrokeWidth(3f);
                strokePaint.setColor(b.color);
                strokePaint.setAlpha(Math.round(alphaInt * 0.9f));

                if (isCircle) {
                    float r = Math.min(w, h) * 0.5f;
                    canvas.drawCircle(0f, 0f, r, fillPaint);
                    canvas.drawCircle(0f, 0f, r - 1.5f, strokePaint);
                } else {
                    tempRect.set(-w * 0.5f, -h * 0.5f, w * 0.5f, h * 0.5f);
                    canvas.drawRoundRect(tempRect, 24f, 24f, fillPaint);
                    canvas.drawRoundRect(tempRect, 24f, 24f, strokePaint);
                }

                String label = (b.text != null && !b.text.isEmpty()) ? b.text : b.actionKey;
                if (label != null && !label.isEmpty()) {
                    Typeface tf = loadFont(b.fontResName);
                    textPaint.setTypeface(tf != null ? tf : Typeface.DEFAULT_BOLD);
                    textPaint.setTextSize(Math.max(16f, Math.min(h * 0.28f, 34f)));
                    textPaint.setTextAlign(Paint.Align.CENTER);
                    textPaint.setColor(0xFFFFFFFF);
                    textPaint.setAlpha(alphaInt);
                    Paint.FontMetrics fm = textPaint.getFontMetrics();
                    canvas.drawText(label, 0f, -(fm.ascent + fm.descent) * 0.5f, textPaint);
                }
                canvas.restore();
            } else if ("hud_joystick".equals(b.type)) {
                float cx = b.x + b.width * 0.5f;
                float cy = b.y + b.height * 0.5f;
                float outerR = Math.min(b.width, b.height) * 0.48f;
                float maxR = Math.max(16f, Math.min(b.width, b.height) * 0.42f);
                float knobR = outerR * 0.42f;
                boolean active = !joystickObjectId.isEmpty() && joystickObjectId.equals(b.id);

                fillPaint.setStyle(Paint.Style.FILL);
                fillPaint.setColor(0xFF000000);
                fillPaint.setAlpha(Math.round(alphaInt * 0.25f));
                canvas.drawCircle(cx, cy, outerR, fillPaint);

                strokePaint.setStyle(Paint.Style.STROKE);
                strokePaint.setStrokeWidth(3f);
                strokePaint.setColor(b.color);
                strokePaint.setAlpha(Math.round(alphaInt * 0.55f));
                canvas.drawCircle(cx, cy, outerR, strokePaint);

                float kx = cx + (active ? joyX * maxR : 0f);
                float ky = cy + (active ? joyY * maxR : 0f);
                fillPaint.setColor(b.color);
                fillPaint.setAlpha(Math.round(alphaInt * (active ? 0.85f : 0.6f)));
                canvas.drawCircle(kx, ky, knobR, fillPaint);

                strokePaint.setColor(0xFFFFFFFF);
                strokePaint.setAlpha(Math.round(alphaInt * 0.65f));
                canvas.drawCircle(kx, ky, knobR - 1.5f, strokePaint);
            } else if ("hud_bar".equals(b.type)) {
                float ratio = parseProgressRatio(b.text);
                float radius = Math.min(14f, b.height * 0.45f);
                tempRect.set(b.x, b.y, b.x + b.width, b.y + b.height);
                fillPaint.setStyle(Paint.Style.FILL);
                fillPaint.setColor(0xFF0F141D);
                fillPaint.setAlpha(Math.round(alphaInt * 0.8f));
                canvas.drawRoundRect(tempRect, radius, radius, fillPaint);

                if (ratio > 0f) {
                    tempRect.set(b.x + 2f, b.y + 2f, b.x + 2f + Math.max(0f, (b.width - 4f) * ratio), b.y + b.height - 2f);
                    fillPaint.setColor(b.color);
                    fillPaint.setAlpha(alphaInt);
                    canvas.drawRoundRect(tempRect, Math.max(2f, radius - 2f), Math.max(2f, radius - 2f), fillPaint);
                }

                tempRect.set(b.x, b.y, b.x + b.width, b.y + b.height);
                strokePaint.setStyle(Paint.Style.STROKE);
                strokePaint.setStrokeWidth(2f);
                strokePaint.setColor(0xFFFFFFFF);
                strokePaint.setAlpha(Math.round(alphaInt * 0.45f));
                canvas.drawRoundRect(tempRect, radius, radius, strokePaint);
            } else if ("hud_panel".equals(b.type)) {
                float radius = Math.min(20f, Math.min(b.width, b.height) * 0.25f);
                tempRect.set(b.x, b.y, b.x + b.width, b.y + b.height);
                Bitmap bmp = loadBitmap(b.spriteResName);
                if (bmp != null && !bmp.isRecycled()) {
                    bitmapPaint.setAlpha(alphaInt);
                    canvas.drawBitmap(bmp, null, tempRect, bitmapPaint);
                    bitmapPaint.setAlpha(255);
                } else {
                    fillPaint.setStyle(Paint.Style.FILL);
                    fillPaint.setColor(b.color);
                    fillPaint.setAlpha(Math.round(alphaInt * 0.85f));
                    canvas.drawRoundRect(tempRect, radius, radius, fillPaint);

                    strokePaint.setStyle(Paint.Style.STROKE);
                    strokePaint.setStrokeWidth(2f);
                    strokePaint.setColor(0xFFFFFFFF);
                    strokePaint.setAlpha(Math.round(alphaInt * 0.28f));
                    canvas.drawRoundRect(tempRect, radius, radius, strokePaint);
                }
            } else if ("hud_image".equals(b.type)) {
                tempRect.set(b.x, b.y, b.x + b.width, b.y + b.height);
                Bitmap bmp = loadBitmap(b.spriteResName);
                if (bmp != null && !bmp.isRecycled()) {
                    bitmapPaint.setAlpha(alphaInt);
                    canvas.drawBitmap(bmp, null, tempRect, bitmapPaint);
                    bitmapPaint.setAlpha(255);
                } else {
                    fillPaint.setStyle(Paint.Style.FILL);
                    fillPaint.setColor(b.color);
                    fillPaint.setAlpha(alphaInt);
                    canvas.drawRoundRect(tempRect, 12f, 12f, fillPaint);
                }
            } else if ("hud_dpad".equals(b.type)) {
                float cx = b.x + b.width * 0.5f;
                float cy = b.y + b.height * 0.5f;
                float r = Math.min(b.width, b.height) * 0.48f;
                float armW = r * 0.54f;
                String activeDir = dpadObjectId.equals(b.id) ? dpadDirectionKey : "";

                fillPaint.setStyle(Paint.Style.FILL);
                fillPaint.setColor(0xFF000000);
                fillPaint.setAlpha(Math.round(alphaInt * 0.22f));
                canvas.drawCircle(cx, cy, r, fillPaint);

                fillPaint.setColor(b.color);
                fillPaint.setAlpha(Math.round(alphaInt * 0.45f));
                tempRect.set(cx - armW * 0.5f, cy - r * 0.9f, cx + armW * 0.5f, cy + r * 0.9f);
                canvas.drawRoundRect(tempRect, 12f, 12f, fillPaint);
                tempRect.set(cx - r * 0.9f, cy - armW * 0.5f, cx + r * 0.9f, cy + armW * 0.5f);
                canvas.drawRoundRect(tempRect, 12f, 12f, fillPaint);

                if (!activeDir.isEmpty()) {
                    fillPaint.setColor(0xFFFFFFFF);
                    fillPaint.setAlpha(Math.round(alphaInt * 0.55f));
                    if ("UP".equals(activeDir)) {
                        tempRect.set(cx - armW * 0.5f, cy - r * 0.9f, cx + armW * 0.5f, cy - armW * 0.35f);
                    } else if ("DOWN".equals(activeDir)) {
                        tempRect.set(cx - armW * 0.5f, cy + armW * 0.35f, cx + armW * 0.5f, cy + r * 0.9f);
                    } else if ("LEFT".equals(activeDir)) {
                        tempRect.set(cx - r * 0.9f, cy - armW * 0.5f, cx - armW * 0.35f, cy + armW * 0.5f);
                    } else {
                        tempRect.set(cx + armW * 0.35f, cy - armW * 0.5f, cx + r * 0.9f, cy + armW * 0.5f);
                    }
                    canvas.drawRoundRect(tempRect, 10f, 10f, fillPaint);
                }

                strokePaint.setStyle(Paint.Style.STROKE);
                strokePaint.setStrokeWidth(2.5f);
                strokePaint.setColor(0xFFFFFFFF);
                strokePaint.setAlpha(Math.round(alphaInt * 0.4f));
                canvas.drawCircle(cx, cy, r, strokePaint);
            } else if ("hud_touch_area".equals(b.type)) {
                tempRect.set(b.x, b.y, b.x + b.width, b.y + b.height);
                fillPaint.setStyle(Paint.Style.FILL);
                fillPaint.setColor(b.color);
                fillPaint.setAlpha(Math.round(alphaInt * 0.14f));
                canvas.drawRoundRect(tempRect, 16f, 16f, fillPaint);

                strokePaint.setStyle(Paint.Style.STROKE);
                strokePaint.setStrokeWidth(2f);
                strokePaint.setColor(b.color);
                strokePaint.setAlpha(Math.round(alphaInt * 0.45f));
                canvas.drawRoundRect(tempRect, 16f, 16f, strokePaint);
            } else if ("hud_dialog".equals(b.type)) {
                tempRect.set(b.x, b.y, b.x + b.width, b.y + b.height);
                fillPaint.setStyle(Paint.Style.FILL);
                fillPaint.setColor(b.color);
                fillPaint.setAlpha(Math.round(alphaInt * 0.92f));
                canvas.drawRoundRect(tempRect, 18f, 18f, fillPaint);

                strokePaint.setStyle(Paint.Style.STROKE);
                strokePaint.setStrokeWidth(3f);
                strokePaint.setColor(0xFFFFFFFF);
                strokePaint.setAlpha(Math.round(alphaInt * 0.65f));
                canvas.drawRoundRect(tempRect, 18f, 18f, strokePaint);

                float pad = 20f;
                float tyStart = b.y + pad;
                if (b.actionKey != null && !b.actionKey.trim().isEmpty()) {
                    textPaint.setTypeface(Typeface.DEFAULT_BOLD);
                    textPaint.setTextSize(Math.max(16f, Math.min(24f, b.height * 0.18f)));
                    textPaint.setTextAlign(Paint.Align.LEFT);
                    textPaint.setColor(0xFFFACC15);
                    textPaint.setAlpha(alphaInt);
                    float spBase = tyStart - textPaint.getFontMetrics().ascent;
                    canvas.drawText(b.actionKey.trim(), b.x + pad, spBase, textPaint);
                    tyStart = spBase + textPaint.getTextSize() * 0.65f;
                }

                String raw = b.text == null ? "" : b.text;
                int visLen = Math.max(0, Math.min(raw.length(), (int) Math.ceil(obj.dialogVisibleChars)));
                String shown = raw.substring(0, visLen);
                Typeface tf = loadFont(b.fontResName);
                textPaint.setTypeface(tf != null ? tf : Typeface.DEFAULT_BOLD);
                textPaint.setTextSize(Math.max(18f, Math.min(30f, b.height * 0.22f)));
                textPaint.setTextAlign(Paint.Align.LEFT);
                textPaint.setColor(0xFFFFFFFF);
                textPaint.setAlpha(alphaInt);

                float maxTextW = Math.max(40f, b.width - pad * 2f);
                float lineStep = textPaint.getTextSize() * 1.32f;
                float ty = tyStart - textPaint.getFontMetrics().ascent;
                int start = 0;
                while (start < shown.length() && ty < b.y + b.height - 12f) {
                    int count = textPaint.breakText(shown, start, shown.length(), true, maxTextW, null);
                    if (count <= 0) break;
                    int end = start + count;
                    if (end < shown.length()) {
                        int sp = shown.lastIndexOf(' ', end - 1);
                        if (sp > start) end = sp + 1;
                    }
                    canvas.drawText(shown.substring(start, end).trim(), b.x + pad, ty, textPaint);
                    start = end;
                    ty += lineStep;
                }
            } else if ("hud_toggle".equals(b.type)) {
                float trackH = Math.min(b.height, 56f);
                float trackW = Math.min(b.width, trackH * 1.9f);
                float tx = b.x;
                float ty = b.y + (b.height - trackH) * 0.5f;
                float rad = trackH * 0.5f;

                tempRect.set(tx, ty, tx + trackW, ty + trackH);
                fillPaint.setStyle(Paint.Style.FILL);
                fillPaint.setColor(b.toggled ? b.color : 0xFF334155);
                fillPaint.setAlpha(alphaInt);
                canvas.drawRoundRect(tempRect, rad, rad, fillPaint);

                strokePaint.setStyle(Paint.Style.STROKE);
                strokePaint.setStrokeWidth(2.5f);
                strokePaint.setColor(0xFFFFFFFF);
                strokePaint.setAlpha(Math.round(alphaInt * 0.55f));
                canvas.drawRoundRect(tempRect, rad, rad, strokePaint);

                float knobR = rad - 5f;
                float kx = b.toggled ? (tx + trackW - rad) : (tx + rad);
                fillPaint.setColor(0xFFFFFFFF);
                fillPaint.setAlpha(alphaInt);
                canvas.drawCircle(kx, ty + rad, knobR, fillPaint);

                if (b.text != null && !b.text.trim().isEmpty() && b.width > trackW + 16f) {
                    Typeface tf = loadFont(b.fontResName);
                    textPaint.setTypeface(tf != null ? tf : Typeface.DEFAULT_BOLD);
                    textPaint.setTextSize(Math.max(16f, Math.min(28f, trackH * 0.52f)));
                    textPaint.setTextAlign(Paint.Align.LEFT);
                    textPaint.setColor(0xFFFFFFFF);
                    textPaint.setAlpha(alphaInt);
                    Paint.FontMetrics fm = textPaint.getFontMetrics();
                    float base = b.y + b.height * 0.5f - (fm.ascent + fm.descent) * 0.5f;
                    canvas.drawText(b.text.trim(), tx + trackW + 12f, base, textPaint);
                }
            } else {
                drawWorldObject(canvas, obj);
            }
        }

        // ==================== Resource & Media Loading ====================

        private static String sanitizeResName(String raw) {
            if (raw == null) return "";
            String s = raw.trim();
            String lower = s.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".9.png")) {
                s = s.substring(0, s.length() - 6);
            } else {
                int dot = s.lastIndexOf('.');
                if (dot > 0) s = s.substring(0, dot);
            }
            return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        }

        private Bitmap loadBitmap(String resName) {
            if (resName == null || resName.trim().isEmpty()) return null;
            String key = resName.trim();
            if (bitmapCache.containsKey(key)) return bitmapCache.get(key);
            Bitmap bmp = null;
            try {
                String sanitized = sanitizeResName(key);
                int resId = appContext.getResources().getIdentifier(sanitized, "drawable", appContext.getPackageName());
                if (resId != 0) {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inJustDecodeBounds = true;
                    BitmapFactory.decodeResource(appContext.getResources(), resId, opts);
                    int sample = 1;
                    while (opts.outWidth / sample > 2048 || opts.outHeight / sample > 2048) sample *= 2;
                    opts.inJustDecodeBounds = false;
                    opts.inSampleSize = sample;
                    bmp = BitmapFactory.decodeResource(appContext.getResources(), resId, opts);
                }
                if (bmp == null) {
                    InputStream is = null;
                    try {
                        is = appContext.getAssets().open(key);
                        bmp = BitmapFactory.decodeStream(is);
                    } catch (Throwable ignored) {
                    } finally {
                        if (is != null) try { is.close(); } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable ignored) {
            }
            bitmapCache.put(key, bmp);
            return bmp;
        }

        private Typeface loadFont(String resName) {
            if (resName == null || resName.trim().isEmpty()) return null;
            String key = resName.trim();
            if (fontCache.containsKey(key)) return fontCache.get(key);
            Typeface tf = null;
            try {
                if (cachedAssetFonts == null) {
                    cachedAssetFonts = appContext.getAssets().list("fonts");
                }
                if (cachedAssetFonts != null) {
                    String targetSan = sanitizeResName(key);
                    for (String entry : cachedAssetFonts) {
                        if (entry.equalsIgnoreCase(key) || sanitizeResName(entry).equals(targetSan)) {
                            tf = Typeface.createFromAsset(appContext.getAssets(), "fonts/" + entry);
                            break;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            fontCache.put(key, tf);
            return tf;
        }

        private void playSoundInternal(String resName) {
            if (resName == null || resName.trim().isEmpty() || soundPool == null) return;
            String key = resName.trim();
            Integer existing = soundIdMap.get(key);
            if (existing != null) {
                if (loadedSoundIds.contains(existing)) {
                    float vol = clamp(masterVolumePercent / 100f, 0f, 1f);
                    soundPool.play(existing, vol, vol, 1, 0, 1f);
                } else {
                    pendingPlaySoundIds.add(existing);
                }
                return;
            }
            try {
                String sanitized = sanitizeResName(key);
                int resId = appContext.getResources().getIdentifier(sanitized, "raw", appContext.getPackageName());
                if (resId != 0) {
                    int sid = soundPool.load(appContext, resId, 1);
                    if (sid > 0) {
                        soundIdMap.put(key, sid);
                        pendingPlaySoundIds.add(sid);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
""";

    private static final String MAIN_ACTIVITY_BODY_TEMPLATE_PART5 = """
        private void playBgmInternal(String resName, boolean loop) {
            if (resName == null || resName.trim().isEmpty()) {
                stopBgmInternal();
                return;
            }
            String key = resName.trim();
            if (key.equals(currentBgmResName) && bgmPlayer != null) {
                try {
                    bgmPlayer.setLooping(loop);
                    applyVolumeToBgm();
                    bgmWantedPlaying = true;
                    if (!paused && !bgmPlayer.isPlaying()) bgmPlayer.start();
                    return;
                } catch (Throwable ignored) {
                }
            }
            stopBgmInternal();
            try {
                String sanitized = sanitizeResName(key);
                int resId = appContext.getResources().getIdentifier(sanitized, "raw", appContext.getPackageName());
                MediaPlayer mp = null;
                if (resId != 0) {
                    mp = MediaPlayer.create(appContext, resId);
                } else {
                    AssetFileDescriptor afd = null;
                    try {
                        afd = appContext.getAssets().openFd(key);
                        mp = new MediaPlayer();
                        mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                        mp.prepare();
                    } finally {
                        if (afd != null) try { afd.close(); } catch (Throwable ignored) {}
                    }
                }
                if (mp != null) {
                    mp.setLooping(loop);
                    bgmPlayer = mp;
                    applyVolumeToBgm();
                    currentBgmResName = key;
                    bgmWantedPlaying = true;
                    if (!paused) mp.start();
                }
            } catch (Throwable ignored) {
                stopBgmInternal();
            }
        }

        private void applyVolumeToBgm() {
            if (bgmPlayer != null) {
                try {
                    float v = clamp(masterVolumePercent / 100f, 0f, 1f);
                    bgmPlayer.setVolume(v, v);
                } catch (Throwable ignored) {
                }
            }
        }

        private void stopBgmInternal() {
            bgmWantedPlaying = false;
            currentBgmResName = "";
            if (bgmPlayer != null) {
                try { if (bgmPlayer.isPlaying()) bgmPlayer.stop(); } catch (Throwable ignored) {}
                try { bgmPlayer.release(); } catch (Throwable ignored) {}
                bgmPlayer = null;
            }
        }

        // ==================== Helpers ====================

        private ArrayList<RuntimeEntity> getWorldObjectsSorted() {
            drawWorldScratch.clear();
            for (RuntimeEntity o : objects) {
                if (!o.destroyed && !o.isHud()) drawWorldScratch.add(o);
            }
            Collections.sort(drawWorldScratch, Z_COMPARATOR);
            return drawWorldScratch;
        }

        private ArrayList<RuntimeEntity> getHudObjectsSorted() {
            drawHudScratch.clear();
            for (RuntimeEntity o : objects) {
                if (!o.destroyed && o.isHud()) drawHudScratch.add(o);
            }
            Collections.sort(drawHudScratch, Z_COMPARATOR);
            return drawHudScratch;
        }

        private RuntimeEntity findById(String id) {
            if (id == null || id.isEmpty()) return null;
            for (RuntimeEntity o : objects) {
                if (!o.destroyed && id.equals(o.bean.id)) return o;
            }
            return null;
        }

        private RuntimeEntity findByIdOrName(String key) {
            if (key == null || key.trim().isEmpty()) return null;
            String k = key.trim();
            for (RuntimeEntity o : objects) {
                if (!o.destroyed && (k.equals(o.bean.id) || k.equalsIgnoreCase(o.bean.name))) return o;
            }
            return null;
        }

        private RuntimeEntity resolveTarget(String expr, RuntimeEntity fallback) {
            if (expr == null || expr.trim().isEmpty() || "self".equalsIgnoreCase(expr.trim())) return fallback;
            return findByIdOrName(expr.trim());
        }

        private static String normalizeActionKey(String key) {
            return key == null ? "" : key.trim().toUpperCase(Locale.ROOT);
        }

        private static String param(BlockData b, int idx, String fallback) {
            if (b.parameters == null || idx < 0 || idx >= b.parameters.size()) return fallback;
            String v = b.parameters.get(idx);
            return v == null ? fallback : v;
        }

        private static boolean hasParam(BlockData b, int idx) {
            return b.parameters != null && idx >= 0 && idx < b.parameters.size()
                    && b.parameters.get(idx) != null && !b.parameters.get(idx).trim().isEmpty();
        }

        private static float clamp(float v, float min, float max) {
            return Math.max(min, Math.min(max, v));
        }

        private static float parseProgressRatio(String text) {
            if (text == null || text.trim().isEmpty()) return 1f;
            String t = text.trim();
            int slash = t.indexOf('/');
            if (slash > 0 && slash < t.length() - 1) {
                float cur = (float) parseDoubleSafe(t.substring(0, slash), 100.0);
                float max = (float) parseDoubleSafe(t.substring(slash + 1), 100.0);
                if (max <= 0f) return 0f;
                return clamp(cur / max, 0f, 1f);
            }
            double val = parseDoubleSafe(t, 100.0);
            if (t.contains(".") && val >= 0.0 && val <= 1.0) return (float) val;
            return clamp((float) (val / 100.0), 0f, 1f);
        }

        private static int parseColorValue(String raw, int fallback) {
            if (raw == null || raw.trim().isEmpty()) return fallback;
            String s = raw.trim();
            try {
                if (s.startsWith("#")) return Color.parseColor(s);
                if (s.startsWith("0x") || s.startsWith("0X")) return (int) Long.parseLong(s.substring(2), 16);
                return Integer.parseInt(s);
            } catch (Throwable ignored) {
                return fallback;
            }
        }

        private static boolean isNumericString(String s) {
            if (s == null) return false;
            String t = s.trim();
            if (t.isEmpty()) return false;
            try {
                Double.parseDouble(t);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        private static double parseDoubleSafe(String s, double fallback) {
            if (s == null || s.trim().isEmpty()) return fallback;
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }

        private static double toDouble(Object o) {
            if (o instanceof Number) return ((Number) o).doubleValue();
            if (o instanceof Boolean) return ((Boolean) o) ? 1.0 : 0.0;
            if (o instanceof String) return parseDoubleSafe((String) o, 0.0);
            return 0.0;
        }

        private static boolean toBool(Object o) {
            if (o instanceof Boolean) return (Boolean) o;
            if (o instanceof Number) return Math.abs(((Number) o).doubleValue()) > 1e-9;
            if (o instanceof String) {
                String s = ((String) o).trim();
                return !s.isEmpty() && !"false".equalsIgnoreCase(s) && !"0".equals(s);
            }
            return o != null;
        }

        private static String valueToString(Object o) {
            if (o == null) return "";
            if (o instanceof Double) return formatNumber((Double) o);
            if (o instanceof Boolean) return ((Boolean) o) ? "true" : "false";
            return o.toString();
        }

        private static boolean valuesEqual(Object a, Object b) {
            if ((a instanceof Number || isNumericString(valueToString(a)))
                    && (b instanceof Number || isNumericString(valueToString(b)))) {
                return Math.abs(toDouble(a) - toDouble(b)) < 1e-6;
            }
            return valueToString(a).equals(valueToString(b));
        }

        private static String formatNumber(double d) {
            if (Double.isNaN(d) || Double.isInfinite(d)) return "0";
            if (Math.abs(d - Math.rint(d)) < 1e-6 && Math.abs(d) < 1e15) {
                return Long.toString(Math.round(d));
            }
            String formatted = String.format(Locale.US, "%.2f", d);
            if (formatted.contains(".")) {
                while (formatted.endsWith("0")) {
                    formatted = formatted.substring(0, formatted.length() - 1);
                }
                if (formatted.endsWith(".")) {
                    formatted = formatted.substring(0, formatted.length() - 1);
                }
            }
            return formatted;
        }

        private boolean isInCameraViewport(RuntimeEntity obj, float margin) {
            if (obj == null) return false;
            int vw = Math.max(1, project.virtualWidth);
            int vh = Math.max(1, project.virtualHeight);
            float left = camX - margin;
            float top = camY - margin;
            float right = camX + vw + margin;
            float bottom = camY + vh + margin;
            return obj.bean.x + obj.bean.width >= left
                    && obj.bean.x <= right
                    && obj.bean.y + obj.bean.height >= top
                    && obj.bean.y <= bottom;
        }

        private boolean isOutsideWorld(RuntimeEntity obj) {
            if (obj == null) return false;
            float ww = getWorldWidth();
            float wh = getWorldHeight();
            return obj.bean.x + obj.bean.width < 0f
                    || obj.bean.x > ww
                    || obj.bean.y + obj.bean.height < 0f
                    || obj.bean.y > wh;
        }

        private boolean hasLeftScreen(RuntimeEntity obj) {
            if (obj == null || !obj.enteredScreen) return false;
            return !isInCameraViewport(obj, 24f);
        }

        private boolean isFarOutsideWorldAndCamera(RuntimeEntity obj, float margin) {
            if (obj == null) return false;
            float minX = Math.min(0f, camX) - margin;
            float minY = Math.min(0f, camY) - margin;
            float maxX = Math.max(getWorldWidth(), camX + project.virtualWidth) + margin;
            float maxY = Math.max(getWorldHeight(), camY + project.virtualHeight) + margin;
            return obj.bean.x + obj.bean.width < minX
                    || obj.bean.x > maxX
                    || obj.bean.y + obj.bean.height < minY
                    || obj.bean.y > maxY;
        }

        private static final Comparator<RuntimeEntity> Z_COMPARATOR = new Comparator<RuntimeEntity>() {
            @Override
            public int compare(RuntimeEntity a, RuntimeEntity b) {
                int cmp = Integer.compare(a.bean.zIndex, b.bean.zIndex);
                return cmp != 0 ? cmp : Integer.compare(a.spawnOrder, b.spawnOrder);
            }
        };

        private final class RuntimeEntity {
            final ObjectData bean;
            final int spawnOrder;
            float prevX;
            float prevY;
            float startX;
            float startY;
            float checkpointX;
            float checkpointY;
            boolean hasCheckpoint = false;
            boolean enteredScreen = false;
            boolean onGround = false;
            boolean destroyed = false;
            boolean isClone = false;
            String templateId = "";
            String speechText = "";
            boolean speechThink = false;
            float speechTimer = 0f;
            boolean glideActive = false;
            float glideStartX = 0f;
            float glideStartY = 0f;
            float glideTargetX = 0f;
            float glideTargetY = 0f;
            float glideElapsed = 0f;
            float glideDuration = 0f;
            int animFrameIdx = 0;
            float animTimer = 0f;
            float spawnTimer = 0f;
            float patrolDir = 1f;
            float hitCooldown = 0f;
            float scrollOffsetX = 0f;
            float scrollOffsetY = 0f;
            float dialogVisibleChars = 0f;
            final Set<String> prevColliders = new HashSet<String>();
            final Set<String> currentColliders = new HashSet<String>();

            RuntimeEntity(ObjectData source) {
                this.bean = source.copy();
                this.spawnOrder = nextSpawnOrder++;
                this.templateId = this.bean.id != null ? this.bean.id : "";
                this.prevX = this.bean.x;
                this.prevY = this.bean.y;
                this.startX = this.bean.x;
                this.startY = this.bean.y;
                this.checkpointX = this.bean.x;
                this.checkpointY = this.bean.y;
                if ("spawner".equals(this.bean.type)) {
                    this.spawnTimer = 0.15f;
                }
                if ("projectile".equals(this.bean.type)
                        && this.bean.velocityX == 0f && this.bean.velocityY == 0f
                        && (this.bean.moveX != 0f || this.bean.moveY != 0f)) {
                    this.bean.velocityX = this.bean.moveX;
                    this.bean.velocityY = this.bean.moveY;
                }
            }

            boolean isHud() {
                return bean.isHud || bean.isHudType();
            }

            String matchKey() {
                return (templateId != null && !templateId.isEmpty())
                        ? templateId
                        : (bean.id != null ? bean.id : "");
            }

            boolean matchesTarget(String targetId) {
                if (targetId == null || targetId.isEmpty()) return false;
                return targetId.equals(bean.id) || (templateId != null && targetId.equals(templateId));
            }
        }

        private static final class Frame {
            final List<BlockData> blocks;
            int nextIndex = 0;
            int remaining;
            boolean infinite = false;
            BlockData whileUntilBlock = null;

            Frame(List<BlockData> blocks, int remaining) {
                this.blocks = blocks;
                this.remaining = remaining;
            }
        }

        private static final class Fiber {
            final ScriptData script;
            final RuntimeEntity self;
            RuntimeEntity other;
            final ArrayList<Frame> frames = new ArrayList<Frame>();
            float waitTimer = 0f;
            boolean finished = false;

            Fiber(ScriptData script, RuntimeEntity self, RuntimeEntity other) {
                this.script = script;
                this.self = self;
                this.other = other;
                if (script.blocks != null && !script.blocks.isEmpty()) {
                    this.frames.add(new Frame(script.blocks, 1));
                } else {
                    this.finished = true;
                }
            }
        }

        private static final class QueuedEvent {
            final String eventType;
            final String eventParam;
            final String targetObjectId;

            QueuedEvent(String eventType, String eventParam, String targetObjectId) {
                this.eventType = eventType;
                this.eventParam = eventParam;
                this.targetObjectId = targetObjectId;
            }
        }

        private static final class ParticleData {
            float x;
            float y;
            float vx;
            float vy;
            float life;
            float maxLife;
            float size;
            int color;
        }
    }
}
""";
}
