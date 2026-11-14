package pro.sketchware.activities.game.runtime;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import pro.sketchware.activities.game.model.GameBlockItem;
import pro.sketchware.activities.game.model.GameObjectBean;
import pro.sketchware.activities.game.model.GameProject;
import pro.sketchware.activities.game.model.GameProjectRepository;
import pro.sketchware.activities.game.model.GameScene;
import pro.sketchware.activities.game.model.GameScriptNode;

/**
 * 60 FPS 2D game runtime SurfaceView with fixed-step AABB physics, multi-touch HUD,
 * 2D camera, particle effects, audio playback, and visual block VM.
 */
public class GameRuntimeView extends SurfaceView implements SurfaceHolder.Callback {

    private static final float FIXED_DT = 1f / 60f;
    private static final int MAX_SUBSTEPS_PER_FRAME = 4;
    private static final int MAX_PARTICLES = 320;
    private static final int MAX_FIBER_OPS_PER_TICK = 400;

    private final String scId;
    private final GameProject project;
    private final String initialSceneId;

    private final Object stateLock = new Object();
    private final Random random = new Random();

    // Render & viewport state
    private volatile int surfaceWidth = 1;
    private volatile int surfaceHeight = 1;
    private volatile float viewScale = 1f;
    private volatile float viewOffsetX = 0f;
    private volatile float viewOffsetY = 0f;

    // Loop & lifecycle state
    private GameLoopThread loopThread;
    private volatile boolean surfaceReady = false;
    private volatile boolean paused = false;
    private volatile boolean released = false;
    private volatile int currentFps = 60;

    // Active scene state
    private GameScene currentScene;
    private final ArrayList<RuntimeObject> objects = new ArrayList<>();
    private final ArrayList<GameScriptNode> sceneScripts = new ArrayList<>();
    private final HashMap<String, String> variables = new HashMap<>();
    private final ArrayList<ScriptFiber> activeFibers = new ArrayList<>();
    private final ArrayList<Particle> particles = new ArrayList<>();
    private final ArrayList<PendingEvent> pendingEvents = new ArrayList<>();
    private String pendingSceneLoadId = null;
    private boolean pendingSceneRestart = false;

    // Camera state
    private float camX = 0f;
    private float camY = 0f;
    private String camFollowTargetId = "";
    private float camFollowLerp = 0.15f;
    private float shakeIntensity = 0f;
    private float shakeDuration = 0f;
    private float shakeRemaining = 0f;

    // Multi-touch & input state
    private int joystickPointerId = -1;
    private String joystickObjectId = "";
    private volatile float joyX = 0f;
    private volatile float joyY = 0f;
    private volatile float touchX = 0f;
    private volatile float touchY = 0f;
    private volatile boolean isTouchingWorld = false;
    private int worldTouchPointerId = -1;
    private float gameTimerSeconds = 0f;
    private float masterVolumePercent = 100f;
    private final SparseArray<String> pointerButtons = new SparseArray<>();
    private final Set<String> pressedButtons = new HashSet<>();
    private final Set<String> latchedButtons = new HashSet<>();

    // Media & asset caches
    private final HashMap<String, Bitmap> bitmapCache = new HashMap<>();
    private final HashMap<String, Typeface> fontCache = new HashMap<>();
    private final HashMap<String, Integer> soundIdMap = new HashMap<>();
    private final Set<Integer> loadedSoundIds = new HashSet<>();
    private final Set<Integer> pendingPlaySoundIds = new HashSet<>();
    private SoundPool soundPool;
    private MediaPlayer bgmPlayer;
    private String currentBgmResName = "";
    private boolean bgmWantedPlaying = false;

    // Reusable Paints
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF tempRect = new RectF();
    private final ArrayList<RuntimeObject> drawWorldScratch = new ArrayList<>();
    private final ArrayList<RuntimeObject> drawHudScratch = new ArrayList<>();
    private int ambientParticleCounter = 0;

    public GameRuntimeView(Context context, String scId, GameProject project, String initialSceneId) {
        super(context);
        this.scId = scId == null ? "" : scId;
        this.project = project != null ? project : GameProject.createDefault(this.scId);
        this.project.ensureInitialized(this.scId);
        this.initialSceneId = initialSceneId;

        initSoundPool();
        getHolder().addCallback(this);
        setFocusable(true);
        setFocusableInTouchMode(true);

        synchronized (stateLock) {
            String startId = (initialSceneId != null && !initialSceneId.isEmpty())
                    ? initialSceneId : this.project.startSceneId;
            loadSceneInternal(startId);
        }
    }

    private void initSoundPool() {
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder()
                .setMaxStreams(8)
                .setAudioAttributes(attrs)
                .build();
        soundPool.setOnLoadCompleteListener((pool, sampleId, status) -> {
            if (status == 0) {
                synchronized (stateLock) {
                    loadedSoundIds.add(sampleId);
                    if (pendingPlaySoundIds.remove(sampleId)) {
                        float vol = clamp(masterVolumePercent / 100f, 0f, 1f);
                        pool.play(sampleId, vol, vol, 1, 0, 1f);
                    }
                }
            }
        });
    }

    public void setPaused(boolean paused) {
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

    public boolean isPaused() {
        return paused;
    }

    public void restartCurrentScene() {
        synchronized (stateLock) {
            String id = currentScene != null ? currentScene.id : project.startSceneId;
            loadSceneInternal(id);
        }
    }

    public int getCurrentFps() {
        return currentFps;
    }

    public void releaseResources() {
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
        updateViewportTransform();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        stopLoopThread();
    }

    private void updateViewportTransform() {
        int vw = Math.max(1, project.virtualWidth);
        int vh = Math.max(1, project.virtualHeight);
        float sx = surfaceWidth / (float) vw;
        float sy = surfaceHeight / (float) vh;
        float scale = Math.min(sx, sy);
        if (scale <= 0f) {
            scale = 1f;
        }
        viewScale = scale;
        viewOffsetX = (surfaceWidth - vw * scale) * 0.5f;
        viewOffsetY = (surfaceHeight - vh * scale) * 0.5f;
    }

    private void startLoopThread() {
        if (loopThread != null && loopThread.isAlive()) {
            return;
        }
        loopThread = new GameLoopThread();
        loopThread.running = true;
        loopThread.start();
    }

    private void stopLoopThread() {
        GameLoopThread t = loopThread;
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

    private final class GameLoopThread extends Thread {
        volatile boolean running = false;

        GameLoopThread() {
            super("SketchwareGameLoop");
        }

        @Override
        public void run() {
            long lastNs = System.nanoTime();
            float accumulator = 0f;
            long fpsWindowStartNs = lastNs;
            int frameCount = 0;

            while (running && surfaceReady && !released) {
                long frameStartNs = System.nanoTime();
                float dt = (frameStartNs - lastNs) / 1_000_000_000f;
                lastNs = frameStartNs;
                if (dt < 0f) dt = 0f;
                if (dt > 0.05f) dt = 0.05f;

                if (!paused) {
                    accumulator += dt;
                    int steps = 0;
                    synchronized (stateLock) {
                        while (accumulator >= FIXED_DT && steps < MAX_SUBSTEPS_PER_FRAME) {
                            stepFixed(FIXED_DT);
                            accumulator -= FIXED_DT;
                            steps++;
                        }
                        if (steps >= MAX_SUBSTEPS_PER_FRAME) {
                            accumulator = 0f;
                        }
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

                frameCount++;
                long nowNs = System.nanoTime();
                long elapsedFpsNs = nowNs - fpsWindowStartNs;
                if (elapsedFpsNs >= 500_000_000L) {
                    currentFps = Math.round(frameCount * 1_000_000_000f / elapsedFpsNs);
                    frameCount = 0;
                    fpsWindowStartNs = nowNs;
                }

                int targetFps = Math.max(15, Math.min(120, project.targetFps));
                long targetFrameNs = 1_000_000_000L / targetFps;
                long workNs = nowNs - frameStartNs;
                long sleepNs = targetFrameNs - workNs;
                if (sleepNs > 1_000_000L) {
                    try {
                        Thread.sleep(sleepNs / 1_000_000L, (int) (sleepNs % 1_000_000L));
                    } catch (InterruptedException ignored) {
                        break;
                    }
                }
            }
        }
    }

    // ==================== Scene Management ====================

    private void loadSceneInternal(String sceneId) {
        GameScene target = project.getSceneById(sceneId);
        if (target == null) {
            target = findSceneByName(sceneId);
        }
        if (target == null) {
            target = project.getStartScene();
        }
        if (target == null) {
            target = new GameScene("scene_1", "Scene 1");
        }
        target.ensureInitialized();
        currentScene = target;

        objects.clear();
        for (GameObjectBean bean : target.objects) {
            if (bean != null) {
                objects.add(new RuntimeObject(bean));
            }
        }

        sceneScripts.clear();
        for (GameScriptNode script : target.scripts) {
            if (script != null) {
                sceneScripts.add(script.copy());
            }
        }

        // Reset global variables only when loading the initial/start scene or keep persistent vars?
        // Resetting from project.globalVariables on scene restart / start scene ensures clean replay!
        if (variables.isEmpty() || (currentScene.id.equals(project.startSceneId) && pendingSceneLoadId == null)) {
            variables.clear();
            if (project.globalVariables != null) {
                variables.putAll(project.globalVariables);
            }
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
        joyX = 0f;
        joyY = 0f;
        isTouchingWorld = false;
        worldTouchPointerId = -1;
        pointerButtons.clear();
        pressedButtons.clear();
        latchedButtons.clear();

        // Check if scene contains a camera object for initial offset
        for (RuntimeObject obj : objects) {
            if (GameObjectBean.TYPE_CAMERA.equals(obj.bean.type)) {
                camX = Math.max(0f, obj.bean.x - project.virtualWidth * 0.5f + obj.bean.width * 0.5f);
                camY = Math.max(0f, obj.bean.y - project.virtualHeight * 0.5f + obj.bean.height * 0.5f);
                break;
            }
        }

        if (target.bgmSound != null && !target.bgmSound.isEmpty()) {
            playBgmInternal(target.bgmSound, true);
        } else {
            stopBgmInternal();
        }

        // Dispatch SCENE_START scripts immediately
        enqueueScriptsForEvent(GameScriptNode.EVENT_SCENE_START, "", null, null);
        stepFibers(0f);
    }

    private GameScene findSceneByName(String name) {
        if (name == null || project.scenes == null) {
            return null;
        }
        for (GameScene s : project.scenes) {
            if (s != null && (name.equalsIgnoreCase(s.name) || name.equalsIgnoreCase(s.id))) {
                return s;
            }
        }
        return null;
    }

    // ==================== Touch / Multi-Touch Handling ====================

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (released) {
            return false;
        }
        int action = event.getActionMasked();
        int actionIndex = event.getActionIndex();

        synchronized (stateLock) {
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN: {
                    int pointerId = event.getPointerId(actionIndex);
                    float vx = screenToVirtualX(event.getX(actionIndex));
                    float vy = screenToVirtualY(event.getY(actionIndex));
                    handlePointerDown(pointerId, vx, vy);
                    break;
                }
                case MotionEvent.ACTION_MOVE: {
                    int count = event.getPointerCount();
                    for (int i = 0; i < count; i++) {
                        int pointerId = event.getPointerId(i);
                        float vx = screenToVirtualX(event.getX(i));
                        float vy = screenToVirtualY(event.getY(i));
                        handlePointerMove(pointerId, vx, vy);
                    }
                    break;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP: {
                    int pointerId = event.getPointerId(actionIndex);
                    handlePointerUp(pointerId);
                    break;
                }
                case MotionEvent.ACTION_CANCEL: {
                    joystickPointerId = -1;
                    joystickObjectId = "";
                    joyX = 0f;
                    joyY = 0f;
                    worldTouchPointerId = -1;
                    isTouchingWorld = false;
                    pointerButtons.clear();
                    pressedButtons.clear();
                    break;
                }
                default:
                    break;
            }
        }
        return true;
    }

    private float screenToVirtualX(float sx) {
        return (sx - viewOffsetX) / Math.max(0.0001f, viewScale);
    }

    private float screenToVirtualY(float sy) {
        return (sy - viewOffsetY) / Math.max(0.0001f, viewScale);
    }

    private void handlePointerDown(int pointerId, float vx, float vy) {
        // 1. Hit-test HUD controls in reverse z-order
        ArrayList<RuntimeObject> hudList = getHudObjectsSorted();
        for (int i = hudList.size() - 1; i >= 0; i--) {
            RuntimeObject hud = hudList.get(i);
            if (!hud.bean.visible || hud.destroyed || hud.bean.alpha <= 0f) {
                continue;
            }
            if (GameObjectBean.TYPE_HUD_JOYSTICK.equals(hud.bean.type)) {
                float cx = hud.bean.x + hud.bean.width * 0.5f;
                float cy = hud.bean.y + hud.bean.height * 0.5f;
                float hitR = Math.max(hud.bean.width, hud.bean.height) * 0.65f;
                float dx = vx - cx;
                float dy = vy - cy;
                if (dx * dx + dy * dy <= hitR * hitR && joystickPointerId == -1) {
                    joystickPointerId = pointerId;
                    joystickObjectId = hud.bean.id;
                    updateJoystickFromPoint(hud, vx, vy);
                    return;
                }
            } else if (GameObjectBean.TYPE_HUD_BUTTON.equals(hud.bean.type)) {
                float pad = 12f;
                if (vx >= hud.bean.x - pad && vx <= hud.bean.x + hud.bean.width + pad
                        && vy >= hud.bean.y - pad && vy <= hud.bean.y + hud.bean.height + pad) {
                    String key = normalizeActionKey(hud.bean.actionKey);
                    pointerButtons.put(pointerId, key);
                    rebuildPressedButtons();
                    latchedButtons.add(key);
                    pendingEvents.add(new PendingEvent(GameScriptNode.EVENT_BUTTON, key, null, null));
                    pendingEvents.add(new PendingEvent(GameScriptNode.EVENT_TOUCH, "", hud.bean.id, null));
                    return;
                }
            }
        }

        // 2. World touch
        float worldX = vx + camX;
        float worldY = vy + camY;
        touchX = worldX;
        touchY = worldY;
        isTouchingWorld = true;
        worldTouchPointerId = pointerId;

        RuntimeObject hitWorld = findTopWorldObjectAt(worldX, worldY);
        if (hitWorld != null) {
            pendingEvents.add(new PendingEvent(GameScriptNode.EVENT_TOUCH, "", hitWorld.bean.id, null));
        }
        pendingEvents.add(new PendingEvent(GameScriptNode.EVENT_TOUCH, "", GameScriptNode.SCENE_TARGET_ID, null));
    }

    private void handlePointerMove(int pointerId, float vx, float vy) {
        if (pointerId == joystickPointerId) {
            RuntimeObject joyObj = findRuntimeObjectById(joystickObjectId);
            if (joyObj != null) {
                updateJoystickFromPoint(joyObj, vx, vy);
            }
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
            String key = pointerButtons.valueAt(i);
            if (key != null && !key.isEmpty()) {
                pressedButtons.add(key);
            }
        }
    }

    private void updateJoystickFromPoint(RuntimeObject joyObj, float vx, float vy) {
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

    private RuntimeObject findTopWorldObjectAt(float wx, float wy) {
        ArrayList<RuntimeObject> worldList = getWorldObjectsSorted();
        for (int i = worldList.size() - 1; i >= 0; i--) {
            RuntimeObject obj = worldList.get(i);
            if (!obj.bean.visible || obj.destroyed || obj.bean.alpha <= 0f) {
                continue;
            }
            if (wx >= obj.bean.x && wx <= obj.bean.x + obj.bean.width
                    && wy >= obj.bean.y && wy <= obj.bean.y + obj.bean.height) {
                return obj;
            }
        }
        return null;
    }

    // ==================== Fixed-Step Simulation & Physics ====================

    private void stepFixed(float dt) {
        if (currentScene == null) {
            return;
        }
        gameTimerSeconds += dt;

        // 1. Drain pending touch/button events into fibers
        if (!pendingEvents.isEmpty()) {
            for (PendingEvent pe : pendingEvents) {
                if (pe.targetObjectId != null) {
                    RuntimeObject targetObj = findRuntimeObjectById(pe.targetObjectId);
                    enqueueScriptsForTarget(pe.eventType, pe.eventParam, pe.targetObjectId, targetObj, null);
                } else {
                    enqueueScriptsForEvent(pe.eventType, pe.eventParam, null, null);
                }
            }
            pendingEvents.clear();
        }

        // 2. Save previous positions & advance active glides / speech bubbles
        for (RuntimeObject obj : objects) {
            obj.prevX = obj.bean.x;
            obj.prevY = obj.bean.y;
            if (obj.speechTimer > 0f) {
                obj.speechTimer -= dt;
                if (obj.speechTimer <= 0f) {
                    obj.speechTimer = 0f;
                    obj.speechText = "";
                }
            }
            if (obj.glideActive) {
                obj.glideElapsed += dt;
                float u = obj.glideDuration <= 0.0001f ? 1f : clamp(obj.glideElapsed / obj.glideDuration, 0f, 1f);
                obj.bean.x = obj.glideStartX + (obj.glideTargetX - obj.glideStartX) * u;
                obj.bean.y = obj.glideStartY + (obj.glideTargetY - obj.glideStartY) * u;
                if (u >= 1f) {
                    obj.glideActive = false;
                }
            }
        }

        // 3. Enqueue EVENT_UPDATE scripts (skip if same script is currently yielded in waitSeconds)
        enqueueUpdateScripts();

        // 4. Advance all active script fibers
        stepFibers(dt);

        // 5. Clear latched single-frame button presses after update scripts processed them
        latchedButtons.clear();

        // 6. Physics integration & AABB collision resolution
        stepPhysics(dt);

        // 7. Ambient particles & active particles update
        stepParticles(dt);

        // 8. Camera follow & screen shake
        stepCamera(dt);

        // 9. Purge destroyed objects
        for (int i = objects.size() - 1; i >= 0; i--) {
            if (objects.get(i).destroyed) {
                objects.remove(i);
            }
        }

        // 10. Handle deferred scene switch / restart
        if (pendingSceneRestart) {
            String id = currentScene.id;
            pendingSceneRestart = false;
            pendingSceneLoadId = null;
            loadSceneInternal(id);
        } else if (pendingSceneLoadId != null) {
            String nextId = pendingSceneLoadId;
            pendingSceneLoadId = null;
            loadSceneInternal(nextId);
        }
    }

    private void stepPhysics(float dt) {
        float gravityY = currentScene != null ? currentScene.gravityY : 980f;

        // First move & resolve dynamic non-sensor objects
        for (RuntimeObject obj : objects) {
            if (obj.destroyed || obj.isHud() || !obj.bean.physicsEnabled || obj.bean.isStatic || obj.bean.isSensor) {
                continue;
            }
            float scriptDx = obj.bean.x - obj.prevX;
            float scriptDy = obj.bean.y - obj.prevY;

            obj.onGround = false;
            obj.bean.velocityY += gravityY * obj.bean.gravityScale * dt;

            // Integrate X
            float dx = scriptDx + obj.bean.velocityX * dt;
            obj.bean.x = obj.prevX + dx;
            for (RuntimeObject other : objects) {
                if (other == obj || other.destroyed || other.isHud() || !other.bean.physicsEnabled || other.bean.isSensor) {
                    continue;
                }
                if (overlapsXPass(obj, other)) {
                    if (dx > 0f) {
                        obj.bean.x = other.bean.x - obj.bean.width;
                        if (obj.bean.velocityX > 0f) {
                            obj.bean.velocityX = -obj.bean.velocityX * obj.bean.bounce;
                        }
                    } else if (dx < 0f) {
                        obj.bean.x = other.bean.x + other.bean.width;
                        if (obj.bean.velocityX < 0f) {
                            obj.bean.velocityX = -obj.bean.velocityX * obj.bean.bounce;
                        }
                    } else {
                        float overlapLeft = (obj.bean.x + obj.bean.width) - other.bean.x;
                        float overlapRight = (other.bean.x + other.bean.width) - obj.bean.x;
                        if (overlapLeft < overlapRight) {
                            obj.bean.x = other.bean.x - obj.bean.width;
                        } else {
                            obj.bean.x = other.bean.x + other.bean.width;
                        }
                    }
                }
            }

            // Integrate Y
            float dy = scriptDy + obj.bean.velocityY * dt;
            obj.bean.y = obj.prevY + dy;
            for (RuntimeObject other : objects) {
                if (other == obj || other.destroyed || other.isHud() || !other.bean.physicsEnabled || other.bean.isSensor) {
                    continue;
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
                        if (obj.bean.velocityY < 0f) {
                            obj.bean.velocityY = -obj.bean.velocityY * obj.bean.bounce;
                        }
                    }
                }
            }
        }

        // Update current collision sets for ALL world objects (including triggers & static solids)
        for (RuntimeObject obj : objects) {
            obj.prevColliders.clear();
            obj.prevColliders.addAll(obj.currentColliders);
            obj.currentColliders.clear();
        }

        int n = objects.size();
        for (int i = 0; i < n; i++) {
            RuntimeObject a = objects.get(i);
            if (a.destroyed || a.isHud()) continue;
            for (int j = i + 1; j < n; j++) {
                RuntimeObject b = objects.get(j);
                if (b.destroyed || b.isHud()) continue;
                if (aabbTouchingOrOverlapping(a, b)) {
                    a.currentColliders.add(b.bean.id);
                    b.currentColliders.add(a.bean.id);

                    if (!a.prevColliders.contains(b.bean.id)) {
                        enqueueCollisionScripts(a, b);
                    }
                    if (!b.prevColliders.contains(a.bean.id)) {
                        enqueueCollisionScripts(b, a);
                    }
                }
            }
        }
    }

    private boolean overlapsXPass(RuntimeObject a, RuntimeObject b) {
        return a.bean.x + a.bean.width > b.bean.x
                && a.bean.x < b.bean.x + b.bean.width
                && a.bean.y + a.bean.height > b.bean.y + 0.5f
                && a.bean.y < b.bean.y + b.bean.height - 0.5f;
    }

    private boolean overlapsYPass(RuntimeObject a, RuntimeObject b) {
        return a.bean.x + a.bean.width > b.bean.x + 0.5f
                && a.bean.x < b.bean.x + b.bean.width - 0.5f
                && a.bean.y + a.bean.height > b.bean.y
                && a.bean.y < b.bean.y + b.bean.height;
    }

    private boolean aabbTouchingOrOverlapping(RuntimeObject a, RuntimeObject b) {
        float tol = (a.bean.isSensor || b.bean.isSensor) ? -1f : 0.75f;
        return a.bean.x + a.bean.width > b.bean.x - tol
                && a.bean.x < b.bean.x + b.bean.width + tol
                && a.bean.y + a.bean.height > b.bean.y - tol
                && a.bean.y < b.bean.y + b.bean.height + tol;
    }

    private void enqueueCollisionScripts(RuntimeObject owner, RuntimeObject other) {
        for (GameScriptNode script : sceneScripts) {
            if (!GameScriptNode.EVENT_COLLISION.equals(script.eventType)) {
                continue;
            }
            if (!owner.bean.id.equals(script.targetObjectId) && !GameScriptNode.SCENE_TARGET_ID.equals(script.targetObjectId)) {
                continue;
            }
            String filter = script.eventParam == null ? "" : script.eventParam.trim();
            if (filter.isEmpty() || filter.equalsIgnoreCase(other.bean.tag) || filter.equalsIgnoreCase(other.bean.name)) {
                RuntimeObject selfObj = GameScriptNode.SCENE_TARGET_ID.equals(script.targetObjectId) ? owner : owner;
                activeFibers.add(new ScriptFiber(script, selfObj, other));
            }
        }
    }

    private void stepParticles(float dt) {
        ambientParticleCounter++;
        if (ambientParticleCounter % 4 == 0 && particles.size() < MAX_PARTICLES) {
            for (RuntimeObject obj : objects) {
                if (!obj.destroyed && obj.bean.visible && GameObjectBean.TYPE_PARTICLES.equals(obj.bean.type)) {
                    emitParticleBurst(
                            obj.bean.x + obj.bean.width * 0.5f,
                            obj.bean.y + obj.bean.height * 0.5f,
                            obj.bean.color,
                            1
                    );
                }
            }
        }

        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
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

    private void emitParticleBurst(float cx, float cy, int color, int count) {
        int clampedCount = Math.max(1, Math.min(80, count));
        for (int i = 0; i < clampedCount; i++) {
            if (particles.size() >= MAX_PARTICLES) {
                particles.remove(0);
            }
            float angle = random.nextFloat() * (float) (Math.PI * 2.0);
            float speed = 60f + random.nextFloat() * 200f;
            Particle p = new Particle();
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
            RuntimeObject target = findRuntimeObjectByIdOrName(camFollowTargetId);
            if (target != null && !target.destroyed) {
                float targetCamX = target.bean.x + target.bean.width * 0.5f - project.virtualWidth * 0.5f;
                float targetCamY = target.bean.y + target.bean.height * 0.5f - project.virtualHeight * 0.5f;

                float maxWorldRight = project.virtualWidth;
                float maxWorldBottom = project.virtualHeight;
                for (RuntimeObject obj : objects) {
                    if (!obj.destroyed && !obj.isHud()) {
                        maxWorldRight = Math.max(maxWorldRight, obj.bean.x + obj.bean.width);
                        maxWorldBottom = Math.max(maxWorldBottom, obj.bean.y + obj.bean.height);
                    }
                }
                float maxCamX = Math.max(0f, maxWorldRight - project.virtualWidth);
                float maxCamY = Math.max(0f, maxWorldBottom - project.virtualHeight);
                targetCamX = clamp(targetCamX, 0f, maxCamX);
                targetCamY = clamp(targetCamY, 0f, maxCamY);

                float alpha = clamp(camFollowLerp * 60f * dt, 0.02f, 1f);
                camX += (targetCamX - camX) * alpha;
                camY += (targetCamY - camY) * alpha;
            }
        }
        if (shakeRemaining > 0f) {
            shakeRemaining = Math.max(0f, shakeRemaining - dt);
        }
    }

    // ==================== Block VM / Fiber Interpreter ====================

    private void enqueueUpdateScripts() {
        Set<String> runningUpdateScriptIds = new HashSet<>();
        for (ScriptFiber f : activeFibers) {
            if (GameScriptNode.EVENT_UPDATE.equals(f.script.eventType)) {
                runningUpdateScriptIds.add(f.script.id);
            }
        }
        for (GameScriptNode script : sceneScripts) {
            if (!GameScriptNode.EVENT_UPDATE.equals(script.eventType)) {
                continue;
            }
            if (runningUpdateScriptIds.contains(script.id)) {
                continue;
            }
            if (GameScriptNode.SCENE_TARGET_ID.equals(script.targetObjectId)) {
                activeFibers.add(new ScriptFiber(script, null, null));
            } else {
                RuntimeObject owner = findRuntimeObjectById(script.targetObjectId);
                if (owner != null && !owner.destroyed) {
                    activeFibers.add(new ScriptFiber(script, owner, null));
                }
            }
        }
    }

    private void enqueueScriptsForEvent(String eventType, String param, RuntimeObject selfObj, RuntimeObject otherObj) {
        for (GameScriptNode script : sceneScripts) {
            if (!eventType.equals(script.eventType)) {
                continue;
            }
            String sp = script.eventParam == null ? "" : script.eventParam.trim();
            String targetParam = param == null ? "" : param.trim();
            if (!sp.isEmpty() && !targetParam.isEmpty() && !sp.equalsIgnoreCase(targetParam)) {
                continue;
            }
            if (!sp.isEmpty() && targetParam.isEmpty()) {
                continue;
            }
            RuntimeObject owner = selfObj;
            if (owner == null && !GameScriptNode.SCENE_TARGET_ID.equals(script.targetObjectId)) {
                owner = findRuntimeObjectById(script.targetObjectId);
                if (owner == null || owner.destroyed) {
                    continue;
                }
            }
            activeFibers.add(new ScriptFiber(script, owner, otherObj));
        }
    }

    private void enqueueScriptsForTarget(String eventType, String param, String targetId, RuntimeObject selfObj, RuntimeObject otherObj) {
        for (GameScriptNode script : sceneScripts) {
            if (!eventType.equals(script.eventType) || !targetId.equals(script.targetObjectId)) {
                continue;
            }
            String sp = script.eventParam == null ? "" : script.eventParam.trim();
            String targetParam = param == null ? "" : param.trim();
            if (!sp.isEmpty() && !sp.equalsIgnoreCase(targetParam)) {
                continue;
            }
            if (selfObj != null && selfObj.destroyed) {
                continue;
            }
            activeFibers.add(new ScriptFiber(script, selfObj, otherObj));
        }
    }

    private void stepFibers(float dt) {
        if (activeFibers.isEmpty()) {
            return;
        }
        ArrayList<ScriptFiber> snapshot = new ArrayList<>(activeFibers);
        for (ScriptFiber fiber : snapshot) {
            if (fiber.finished) {
                activeFibers.remove(fiber);
                continue;
            }
            if (fiber.waitTimer > 0f) {
                fiber.waitTimer -= dt;
                if (fiber.waitTimer > 0f) {
                    continue;
                }
                fiber.waitTimer = 0f;
            }
            runFiberUntilYieldOrEnd(fiber);
            if (fiber.finished) {
                activeFibers.remove(fiber);
            }
        }
    }

    private void runFiberUntilYieldOrEnd(ScriptFiber fiber) {
        int ops = 0;
        while (!fiber.finished && !fiber.frames.isEmpty() && ops < MAX_FIBER_OPS_PER_TICK) {
            if (fiber.self != null && fiber.self.destroyed) {
                fiber.finished = true;
                return;
            }
            StackFrame top = fiber.frames.get(fiber.frames.size() - 1);
            if (top.nextIndex >= top.blocks.size()) {
                if (top.whileUntilBlock != null) {
                    if (!evalSlotBool(top.whileUntilBlock, 0, "true", fiber) && top.remainingIterations > 1) {
                        top.remainingIterations--;
                        top.nextIndex = 0;
                        return;
                    } else {
                        fiber.frames.remove(fiber.frames.size() - 1);
                    }
                    continue;
                }
                if (top.infinite) {
                    top.nextIndex = 0;
                    return;
                }
                top.remainingIterations--;
                if (top.remainingIterations > 0) {
                    top.nextIndex = 0;
                } else {
                    fiber.frames.remove(fiber.frames.size() - 1);
                }
                continue;
            }
            GameBlockItem block = top.blocks.get(top.nextIndex);
            int executedIndex = top.nextIndex;
            top.nextIndex++;
            ops++;

            if (block == null || block.opCode == null) {
                continue;
            }
            boolean yielded = executeBlock(fiber, block);
            if ("waitUntil".equals(block.opCode) && yielded) {
                top.nextIndex = executedIndex;
            }
            if (yielded || pendingSceneRestart || pendingSceneLoadId != null) {
                return;
            }
        }
        if (fiber.frames.isEmpty()) {
            fiber.finished = true;
        }
    }

    private boolean executeBlock(ScriptFiber fiber, GameBlockItem block) {
        String op = block.opCode.trim();
        RuntimeObject self = fiber.self;

        switch (op) {
            // ---- Control & Logic ----
            case "if": {
                if (evalSlotBool(block, 0, "false", fiber)) {
                    pushSubStack(fiber, block.subStack1, 1);
                }
                return false;
            }
            case "ifElse": {
                if (evalSlotBool(block, 0, "false", fiber)) {
                    pushSubStack(fiber, block.subStack1, 1);
                } else {
                    pushSubStack(fiber, block.subStack2, 1);
                }
                return false;
            }
            case "repeat": {
                int count = Math.max(0, Math.min(10000, Math.round(evalSlotFloat(block, 0, "1", fiber))));
                if (count > 0) {
                    pushSubStack(fiber, block.subStack1, count);
                }
                return false;
            }
            case "forever": {
                if (block.subStack1 != null && !block.subStack1.isEmpty()) {
                    StackFrame f = new StackFrame(block.subStack1, 1);
                    f.infinite = true;
                    fiber.frames.add(f);
                }
                return false;
            }
            case "waitUntil": {
                return !evalSlotBool(block, 0, "false", fiber);
            }
            case "repeatUntil": {
                if (block.subStack1 != null && !block.subStack1.isEmpty()
                        && !evalSlotBool(block, 0, "true", fiber)) {
                    StackFrame f = new StackFrame(block.subStack1, 10000);
                    f.whileUntilBlock = block;
                    fiber.frames.add(f);
                }
                return false;
            }
            case "createClone": {
                String targetSpec = evalSlotText(block, 0, "myself", fiber).trim();
                RuntimeObject srcObj = (targetSpec.isEmpty() || "myself".equalsIgnoreCase(targetSpec)
                        || "self".equalsIgnoreCase(targetSpec)) ? self : findRuntimeObjectByIdOrName(targetSpec);
                if (srcObj != null && !srcObj.destroyed) {
                    spawnCloneOf(srcObj);
                }
                return false;
            }
            case "deleteClone": {
                if (self != null && self.isClone) {
                    self.destroyed = true;
                    fiber.finished = true;
                    return true;
                }
                return false;
            }
            case "waitSeconds": {
                float sec = Math.max(0f, evalSlotFloat(block, 0, "0", fiber));
                if (sec > 0f) {
                    fiber.waitTimer = sec;
                    return true;
                }
                return false;
            }
            case "stopScript": {
                fiber.finished = true;
                return true;
            }
            case "ifButtonPressed":
            case "ifKeyPressed": {
                String key = normalizeActionKey(evalSlotText(block, 0, "JUMP", fiber));
                if (pressedButtons.contains(key) || latchedButtons.contains(key)) {
                    pushSubStack(fiber, block.subStack1, 1);
                }
                return false;
            }
            case "ifCollidingTag": {
                if (self != null) {
                    String tag = evalSlotText(block, 0, "", fiber);
                    RuntimeObject match = findCollidingWithTag(self, tag);
                    if (match != null) {
                        fiber.other = match;
                        pushSubStack(fiber, block.subStack1, 1);
                    }
                }
                return false;
            }
            case "ifOnGround": {
                if (self != null && self.onGround) {
                    pushSubStack(fiber, block.subStack1, 1);
                }
                return false;
            }

            // ---- Movement & Camera ----
            case "moveSteps": {
                if (self != null) {
                    float steps = evalSlotFloat(block, 0, "10", fiber);
                    double rad = Math.toRadians(self.bean.rotation);
                    self.bean.x += (float) (Math.cos(rad) * steps);
                    self.bean.y += (float) (Math.sin(rad) * steps);
                }
                return false;
            }
            case "goToTarget": {
                if (self != null) {
                    String targetStr = evalSlotText(block, 0, "random", fiber).trim();
                    if ("random".equalsIgnoreCase(targetStr)) {
                        int vw = Math.max(1, project.virtualWidth);
                        int vh = Math.max(1, project.virtualHeight);
                        self.bean.x = random.nextFloat() * Math.max(0f, vw - self.bean.width);
                        self.bean.y = random.nextFloat() * Math.max(0f, vh - self.bean.height);
                    } else if ("touch".equalsIgnoreCase(targetStr) || "pointer".equalsIgnoreCase(targetStr)) {
                        self.bean.x = touchX - self.bean.width * 0.5f;
                        self.bean.y = touchY - self.bean.height * 0.5f;
                    } else if ("joystick".equalsIgnoreCase(targetStr)) {
                        self.bean.x += joyX * 8f;
                        self.bean.y += joyY * 8f;
                    } else {
                        RuntimeObject t = findRuntimeObjectByIdOrName(targetStr);
                        if (t != null) {
                            self.bean.x = t.bean.x;
                            self.bean.y = t.bean.y;
                        }
                    }
                }
                return false;
            }
            case "glideToXY": {
                if (self != null) {
                    float dur = Math.max(0f, evalSlotFloat(block, 0, "1", fiber));
                    float tx = evalSlotFloat(block, 1, "0", fiber);
                    float ty = evalSlotFloat(block, 2, "0", fiber);
                    if (dur <= 0.01f) {
                        self.glideActive = false;
                        self.bean.x = tx;
                        self.bean.y = ty;
                        return false;
                    }
                    self.glideActive = true;
                    self.glideElapsed = 0f;
                    self.glideDuration = dur;
                    self.glideStartX = self.bean.x;
                    self.glideStartY = self.bean.y;
                    self.glideTargetX = tx;
                    self.glideTargetY = ty;
                    fiber.waitTimer = dur;
                    return true;
                }
                return false;
            }
            case "changeX": {
                if (self != null) {
                    self.bean.x += evalSlotFloat(block, 0, "10", fiber);
                }
                return false;
            }
            case "setX": {
                if (self != null) {
                    self.bean.x = evalSlotFloat(block, 0, "0", fiber);
                }
                return false;
            }
            case "changeY": {
                if (self != null) {
                    self.bean.y += evalSlotFloat(block, 0, "10", fiber);
                }
                return false;
            }
            case "setY": {
                if (self != null) {
                    self.bean.y = evalSlotFloat(block, 0, "0", fiber);
                }
                return false;
            }
            case "bounceOnEdge": {
                if (self != null) {
                    int vw = Math.max(1, project.virtualWidth);
                    int vh = Math.max(1, project.virtualHeight);
                    boolean hitX = false;
                    boolean hitY = false;
                    if (self.bean.x < 0f) {
                        self.bean.x = 0f;
                        self.bean.velocityX = Math.abs(self.bean.velocityX);
                        hitX = true;
                    } else if (self.bean.x + self.bean.width > vw) {
                        self.bean.x = Math.max(0f, vw - self.bean.width);
                        self.bean.velocityX = -Math.abs(self.bean.velocityX);
                        hitX = true;
                    }
                    if (self.bean.y < 0f) {
                        self.bean.y = 0f;
                        self.bean.velocityY = Math.abs(self.bean.velocityY);
                        hitY = true;
                    } else if (self.bean.y + self.bean.height > vh) {
                        self.bean.y = Math.max(0f, vh - self.bean.height);
                        self.bean.velocityY = -Math.abs(self.bean.velocityY);
                        hitY = true;
                    }
                    if (hitX) {
                        self.bean.rotation = 180f - self.bean.rotation;
                    }
                    if (hitY) {
                        self.bean.rotation = -self.bean.rotation;
                    }
                }
                return false;
            }
            case "setPos": {
                if (hasParam(block, 2) && block.getParamBlock(2) == null && resolveTarget(param(block, 0, ""), null) != null) {
                    RuntimeObject t = resolveTarget(param(block, 0, ""), self);
                    if (t != null) {
                        t.bean.x = evalSlotFloat(block, 1, "0", fiber);
                        t.bean.y = evalSlotFloat(block, 2, "0", fiber);
                    }
                } else if (self != null) {
                    self.bean.x = evalSlotFloat(block, 0, "0", fiber);
                    self.bean.y = evalSlotFloat(block, 1, "0", fiber);
                }
                return false;
            }
            case "moveBy": {
                if (hasParam(block, 2)) {
                    RuntimeObject t = resolveTarget(param(block, 0, ""), self);
                    if (t != null) {
                        t.bean.x += evalSlotFloat(block, 1, "0", fiber);
                        t.bean.y += evalSlotFloat(block, 2, "0", fiber);
                    }
                } else if (self != null) {
                    self.bean.x += evalSlotFloat(block, 0, "0", fiber);
                    self.bean.y += evalSlotFloat(block, 1, "0", fiber);
                }
                return false;
            }
            case "setVel": {
                if (hasParam(block, 2)) {
                    RuntimeObject t = resolveTarget(param(block, 0, ""), self);
                    if (t != null) {
                        t.bean.velocityX = evalSlotFloat(block, 1, "0", fiber);
                        t.bean.velocityY = evalSlotFloat(block, 2, "0", fiber);
                    }
                } else if (self != null) {
                    self.bean.velocityX = evalSlotFloat(block, 0, "0", fiber);
                    self.bean.velocityY = evalSlotFloat(block, 1, "0", fiber);
                }
                return false;
            }
            case "jumpImpulse": {
                if (hasParam(block, 1) && resolveTarget(param(block, 0, ""), null) != null) {
                    RuntimeObject t = resolveTarget(param(block, 0, ""), self);
                    if (t != null) {
                        float v = evalSlotFloat(block, 1, "550", fiber);
                        t.bean.velocityY = v > 0f ? -v : v;
                        t.onGround = false;
                    }
                } else if (self != null) {
                    float v = evalSlotFloat(block, 0, "550", fiber);
                    self.bean.velocityY = v > 0f ? -v : v;
                    self.onGround = false;
                }
                return false;
            }
            case "setRotation": {
                if (self != null) {
                    self.bean.rotation = evalSlotFloat(block, 0, "0", fiber);
                }
                return false;
            }
            case "rotateBy": {
                if (self != null) {
                    self.bean.rotation += evalSlotFloat(block, 0, "15", fiber);
                }
                return false;
            }
            case "rotateLeftBy": {
                if (self != null) {
                    self.bean.rotation -= evalSlotFloat(block, 0, "15", fiber);
                }
                return false;
            }
            case "pointToward": {
                if (self != null) {
                    float cx = self.bean.x + self.bean.width * 0.5f;
                    float cy = self.bean.y + self.bean.height * 0.5f;
                    float tx = cx;
                    float ty = cy;
                    if (hasParam(block, 1)) {
                        tx = evalSlotFloat(block, 0, "0", fiber);
                        ty = evalSlotFloat(block, 1, "0", fiber);
                    } else {
                        String targetStr = evalSlotText(block, 0, "touch", fiber).trim();
                        if ("touch".equalsIgnoreCase(targetStr) || "pointer".equalsIgnoreCase(targetStr)) {
                            tx = touchX;
                            ty = touchY;
                        } else if ("joystick".equalsIgnoreCase(targetStr)) {
                            if (Math.hypot(joyX, joyY) > 0.05f) {
                                self.bean.rotation = (float) Math.toDegrees(Math.atan2(joyY, joyX));
                            }
                            return false;
                        } else if ("random".equalsIgnoreCase(targetStr)) {
                            tx = random.nextFloat() * Math.max(1, project.virtualWidth);
                            ty = random.nextFloat() * Math.max(1, project.virtualHeight);
                        } else {
                            RuntimeObject t = findRuntimeObjectByIdOrName(targetStr);
                            if (t != null) {
                                tx = t.bean.x + t.bean.width * 0.5f;
                                ty = t.bean.y + t.bean.height * 0.5f;
                            }
                        }
                    }
                    self.bean.rotation = (float) Math.toDegrees(Math.atan2(ty - cy, tx - cx));
                }
                return false;
            }
            case "cameraFollow": {
                String p0 = param(block, 0, "").trim();
                String p1 = param(block, 1, "").trim();
                if (p0.isEmpty() || "self".equalsIgnoreCase(p0)) {
                    camFollowTargetId = self != null ? self.bean.id : "";
                    camFollowLerp = hasParam(block, 1) ? clamp(evalSlotFloat(block, 1, "0.15", fiber), 0.01f, 1f) : 0.15f;
                } else {
                    RuntimeObject t = findRuntimeObjectByIdOrName(p0);
                    if (t != null) {
                        camFollowTargetId = t.bean.id;
                        camFollowLerp = hasParam(block, 1) ? clamp(evalSlotFloat(block, 1, "0.15", fiber), 0.01f, 1f) : 0.15f;
                    } else if (isNumericString(p0) && self != null) {
                        camFollowTargetId = self.bean.id;
                        camFollowLerp = clamp(evalSlotFloat(block, 0, "0.15", fiber), 0.01f, 1f);
                    } else {
                        camFollowTargetId = p0;
                        camFollowLerp = p1.isEmpty() ? 0.15f : clamp(evalSlotFloat(block, 1, "0.15", fiber), 0.01f, 1f);
                    }
                }
                return false;
            }
            case "cameraShake": {
                shakeIntensity = Math.max(0f, evalSlotFloat(block, 0, "12", fiber));
                shakeDuration = Math.max(0.01f, evalSlotFloat(block, 1, "0.25", fiber));
                shakeRemaining = shakeDuration;
                return false;
            }

            // ---- Appearance & Effects ----
            case "sayForSecs": {
                if (self != null) {
                    self.speechText = evalSlotText(block, 0, "", fiber);
                    float secs = Math.max(0f, evalSlotFloat(block, 1, "2", fiber));
                    self.speechTimer = secs;
                    if (secs > 0f && !self.speechText.isEmpty()) {
                        fiber.waitTimer = secs;
                        return true;
                    }
                }
                return false;
            }
            case "sayText": {
                if (self != null) {
                    self.speechText = evalSlotText(block, 0, "", fiber);
                    self.speechTimer = self.speechText.isEmpty() ? 0f : -1f;
                }
                return false;
            }
            case "setSprite": {
                if (hasParam(block, 1)) {
                    RuntimeObject t = resolveTarget(param(block, 0, ""), self);
                    if (t != null) {
                        t.bean.spriteResName = evalSlotText(block, 1, "", fiber);
                    }
                } else if (self != null) {
                    self.bean.spriteResName = evalSlotText(block, 0, "", fiber);
                }
                return false;
            }
            case "setBackdrop": {
                if (currentScene != null) {
                    currentScene.backgroundSprite = evalSlotText(block, 0, "", fiber);
                }
                return false;
            }
            case "changeSize": {
                if (self != null) {
                    float d = evalSlotFloat(block, 0, "10", fiber) / 100f;
                    self.bean.scaleX = clamp(self.bean.scaleX + d, 0.05f, 10f);
                    self.bean.scaleY = clamp(self.bean.scaleY + d, 0.05f, 10f);
                }
                return false;
            }
            case "setSize": {
                if (self != null) {
                    float s = clamp(evalSlotFloat(block, 0, "100", fiber) / 100f, 0.05f, 10f);
                    self.bean.scaleX = s;
                    self.bean.scaleY = s;
                }
                return false;
            }
            case "clearEffects": {
                if (self != null) {
                    self.bean.alpha = 1f;
                    self.bean.scaleX = 1f;
                    self.bean.scaleY = 1f;
                }
                return false;
            }
            case "show": {
                if (self != null) {
                    self.bean.visible = true;
                }
                return false;
            }
            case "hide": {
                if (self != null) {
                    self.bean.visible = false;
                }
                return false;
            }
            case "setZIndex": {
                if (self != null) {
                    String layer = evalSlotText(block, 0, "front", fiber).trim();
                    if ("front".equalsIgnoreCase(layer)) {
                        int maxZ = self.bean.zIndex;
                        for (RuntimeObject o : objects) {
                            if (!o.isHud() && o.bean.zIndex > maxZ) maxZ = o.bean.zIndex;
                        }
                        self.bean.zIndex = maxZ + 1;
                    } else if ("back".equalsIgnoreCase(layer)) {
                        int minZ = self.bean.zIndex;
                        for (RuntimeObject o : objects) {
                            if (!o.isHud() && o.bean.zIndex < minZ) minZ = o.bean.zIndex;
                        }
                        self.bean.zIndex = minZ - 1;
                    } else {
                        self.bean.zIndex = Math.round(evalSlotFloat(block, 0, "0", fiber));
                    }
                }
                return false;
            }
            case "changeZIndex": {
                if (self != null) {
                    self.bean.zIndex += Math.round(evalSlotFloat(block, 0, "1", fiber));
                }
                return false;
            }
            case "setVisible": {
                if (hasParam(block, 1)) {
                    RuntimeObject t = resolveTarget(param(block, 0, ""), self);
                    if (t != null) {
                        t.bean.visible = evalSlotBool(block, 1, "true", fiber);
                    }
                } else if (self != null) {
                    self.bean.visible = evalSlotBool(block, 0, "true", fiber);
                }
                return false;
            }
            case "setAlpha": {
                if (self != null) {
                    self.bean.alpha = clamp(evalSlotFloat(block, 0, "1", fiber), 0f, 1f);
                }
                return false;
            }
            case "setScale": {
                if (self != null) {
                    float sx = evalSlotFloat(block, 0, "1", fiber);
                    float sy = hasParam(block, 1) || block.getParamBlock(1) != null
                            ? evalSlotFloat(block, 1, "1", fiber) : sx;
                    self.bean.scaleX = sx;
                    self.bean.scaleY = sy;
                }
                return false;
            }
            case "setColor": {
                if (self != null) {
                    self.bean.color = parseColorValue(param(block, 0, "#4CAF50"), self.bean.color);
                }
                return false;
            }
            case "spawnParticles": {
                float px = self != null ? self.bean.x + self.bean.width * 0.5f : project.virtualWidth * 0.5f;
                float py = self != null ? self.bean.y + self.bean.height * 0.5f : project.virtualHeight * 0.5f;
                int pColor = self != null ? self.bean.color : 0xFFFFD54F;
                int pCount = 12;
                if (hasParam(block, 3)) {
                    px = evalSlotFloat(block, 0, String.valueOf(px), fiber);
                    py = evalSlotFloat(block, 1, String.valueOf(py), fiber);
                    pColor = parseColorValue(param(block, 2, "#FFD54F"), pColor);
                    pCount = Math.round(evalSlotFloat(block, 3, "12", fiber));
                } else if (hasParam(block, 1) || block.getParamBlock(1) != null) {
                    pColor = parseColorValue(param(block, 0, "#FFD54F"), pColor);
                    pCount = Math.round(evalSlotFloat(block, 1, "12", fiber));
                } else if (hasParam(block, 0) || block.getParamBlock(0) != null) {
                    pCount = Math.round(evalSlotFloat(block, 0, "12", fiber));
                }
                emitParticleBurst(px, py, pColor, pCount);
                return false;
            }

            // ---- Physics ----
            case "setGravityScale": {
                if (self != null) {
                    self.bean.gravityScale = evalSlotFloat(block, 0, "1", fiber);
                }
                return false;
            }
            case "setPhysicsEnabled": {
                if (self != null) {
                    self.bean.physicsEnabled = evalSlotBool(block, 0, "true", fiber);
                }
                return false;
            }
            case "setBounce": {
                if (self != null) {
                    self.bean.bounce = clamp(evalSlotFloat(block, 0, "0", fiber), 0f, 1.5f);
                }
                return false;
            }

            // ---- Audio ----
            case "playSound": {
                playSoundInternal(evalSlotText(block, 0, "", fiber));
                return false;
            }
            case "playBgm": {
                String res = evalSlotText(block, 0, "", fiber);
                boolean loop = !hasParam(block, 1) || evalSlotBool(block, 1, "true", fiber);
                playBgmInternal(res, loop);
                return false;
            }
            case "stopBgm": {
                stopBgmInternal();
                return false;
            }
            case "changeVolume": {
                masterVolumePercent = clamp(masterVolumePercent + evalSlotFloat(block, 0, "-10", fiber), 0f, 100f);
                applyVolumeToBgm();
                return false;
            }
            case "setVolume": {
                masterVolumePercent = clamp(evalSlotFloat(block, 0, "100", fiber), 0f, 100f);
                applyVolumeToBgm();
                return false;
            }
            case "resetTimer": {
                gameTimerSeconds = 0f;
                return false;
            }

            // ---- HUD ----
            case "setHudText": {
                if (hasParam(block, 1) || block.getParamBlock(1) != null) {
                    RuntimeObject target = findRuntimeObjectByIdOrName(param(block, 0, ""));
                    String val = block.getParamBlock(1) != null
                            ? evalSlotText(block, 1, "", fiber)
                            : evalString(param(block, 1, ""), fiber);
                    if (target != null) {
                        target.bean.text = val;
                    }
                } else if (self != null) {
                    self.bean.text = block.getParamBlock(0) != null
                            ? evalSlotText(block, 0, "", fiber)
                            : evalString(param(block, 0, ""), fiber);
                }
                return false;
            }
            case "setHudBar": {
                RuntimeObject target = self;
                int valIdx = 0;
                int maxIdx = 1;
                if (hasParam(block, 1) || block.getParamBlock(1) != null) {
                    RuntimeObject named = findRuntimeObjectByIdOrName(param(block, 0, ""));
                    if (named != null) {
                        target = named;
                        valIdx = 1;
                        maxIdx = 2;
                    }
                }
                if (target != null) {
                    float val = evalSlotFloat(block, valIdx, "100", fiber);
                    float max = (hasParam(block, maxIdx) || block.getParamBlock(maxIdx) != null)
                            ? Math.max(0.0001f, evalSlotFloat(block, maxIdx, "100", fiber)) : 100f;
                    target.bean.text = formatNumber(val) + "/" + formatNumber(max);
                }
                return false;
            }

            // ---- Scene & Objects ----
            case "loadScene": {
                pendingSceneLoadId = evalSlotText(block, 0, "", fiber);
                return true;
            }
            case "restartScene": {
                pendingSceneRestart = true;
                return true;
            }
            case "spawnPrefab": {
                String name = evalSlotText(block, 0, "", fiber);
                float sx = (hasParam(block, 1) || block.getParamBlock(1) != null)
                        ? evalSlotFloat(block, 1, "0", fiber) : (self != null ? self.bean.x : 100f);
                float sy = (hasParam(block, 2) || block.getParamBlock(2) != null)
                        ? evalSlotFloat(block, 2, "0", fiber) : (self != null ? self.bean.y : 100f);
                spawnPrefabAt(name, sx, sy);
                return false;
            }
            case "destroySelf": {
                if (self != null) {
                    self.destroyed = true;
                }
                fiber.finished = true;
                return true;
            }
            case "destroyOther": {
                String filter = param(block, 0, "").trim();
                if (!filter.isEmpty()) {
                    if (fiber.other != null && (filter.equalsIgnoreCase(fiber.other.bean.tag)
                            || filter.equalsIgnoreCase(fiber.other.bean.name)
                            || filter.equals(fiber.other.bean.id))) {
                        fiber.other.destroyed = true;
                    } else {
                        RuntimeObject t = findRuntimeObjectByIdOrName(filter);
                        if (t != null) {
                            t.destroyed = true;
                        }
                    }
                } else if (fiber.other != null) {
                    fiber.other.destroyed = true;
                }
                return false;
            }
            case "sendMessage":
            case "sendMessageWait": {
                String msg = evalSlotText(block, 0, "", fiber);
                enqueueScriptsForEvent(GameScriptNode.EVENT_MESSAGE, msg, null, self);
                return false;
            }

            // ---- Variables ----
            case "setVar": {
                String varName = param(block, 0, "").trim();
                if (!varName.isEmpty()) {
                    String val = block.getParamBlock(1) != null
                            ? valueToString(evaluateBlockValue(block.getParamBlock(1), fiber))
                            : evalString(param(block, 1, "0"), fiber);
                    variables.put(varName, val);
                }
                return false;
            }
            case "changeVar": {
                String varName = param(block, 0, "").trim();
                if (!varName.isEmpty()) {
                    double cur = parseDoubleSafe(variables.get(varName), 0.0);
                    double delta = evalSlotDouble(block, 1, "0", fiber);
                    variables.put(varName, formatNumber(cur + delta));
                }
                return false;
            }

            default:
                return false;
        }
    }

    private void spawnCloneOf(RuntimeObject source) {
        if (source == null || source.bean == null) {
            return;
        }
        GameObjectBean copy = source.bean.copy();
        copy.id = "obj_" + UUID.randomUUID().toString().substring(0, 8);
        RuntimeObject spawned = new RuntimeObject(copy);
        spawned.isClone = true;
        objects.add(spawned);
        enqueueScriptsForTarget(GameScriptNode.EVENT_SCENE_START, "", source.bean.id, spawned, null);
    }

    private void spawnPrefabAt(String prefabName, float x, float y) {
        if (prefabName == null || prefabName.isEmpty()) {
            return;
        }
        GameObjectBean template = null;
        if (project.prefabs != null) {
            for (GameObjectBean p : project.prefabs) {
                if (p != null && (prefabName.equalsIgnoreCase(p.name) || prefabName.equalsIgnoreCase(p.id))) {
                    template = p;
                    break;
                }
            }
        }
        if (template == null) {
            RuntimeObject existing = findRuntimeObjectByIdOrName(prefabName);
            if (existing != null) {
                template = existing.bean;
            }
        }
        if (template == null) {
            return;
        }
        GameObjectBean copy = template.copy();
        copy.id = "obj_" + UUID.randomUUID().toString().substring(0, 8);
        copy.x = x;
        copy.y = y;
        RuntimeObject spawned = new RuntimeObject(copy);
        objects.add(spawned);
        enqueueScriptsForTarget(GameScriptNode.EVENT_SCENE_START, "", template.id, spawned, null);
    }

    private void pushSubStack(ScriptFiber fiber, ArrayList<GameBlockItem> subStack, int repeatCount) {
        if (subStack == null || subStack.isEmpty() || repeatCount <= 0) {
            return;
        }
        fiber.frames.add(new StackFrame(subStack, repeatCount));
    }

    private RuntimeObject findCollidingWithTag(RuntimeObject self, String tag) {
        if (self == null || self.currentColliders.isEmpty()) {
            return null;
        }
        for (String otherId : self.currentColliders) {
            RuntimeObject other = findRuntimeObjectById(otherId);
            if (other != null && !other.destroyed) {
                if (tag == null || tag.isEmpty() || tag.equalsIgnoreCase(other.bean.tag) || tag.equalsIgnoreCase(other.bean.name)) {
                    return other;
                }
            }
        }
        return null;
    }

    private float evalSlotFloat(GameBlockItem block, int idx, String fallback, ScriptFiber fiber) {
        return (float) evalSlotDouble(block, idx, fallback, fiber);
    }

    private double evalSlotDouble(GameBlockItem block, int idx, String fallback, ScriptFiber fiber) {
        GameBlockItem nested = block != null ? block.getParamBlock(idx) : null;
        if (nested != null) {
            return toDouble(evaluateBlockValue(nested, fiber));
        }
        return evalDouble(param(block, idx, fallback), fiber);
    }

    private boolean evalSlotBool(GameBlockItem block, int idx, String fallback, ScriptFiber fiber) {
        GameBlockItem nested = block != null ? block.getParamBlock(idx) : null;
        if (nested != null) {
            return toBool(evaluateBlockValue(nested, fiber));
        }
        return evalBool(param(block, idx, fallback), fiber);
    }

    private String evalSlotText(GameBlockItem block, int idx, String fallback, ScriptFiber fiber) {
        GameBlockItem nested = block != null ? block.getParamBlock(idx) : null;
        if (nested != null) {
            return valueToString(evaluateBlockValue(nested, fiber));
        }
        return evalStringOrRaw(param(block, idx, fallback), fiber);
    }

    private Object evaluateBlockValue(GameBlockItem b, ScriptFiber fiber) {
        if (b == null || b.opCode == null) {
            return 0.0;
        }
        RuntimeObject self = fiber != null ? fiber.self : null;
        switch (b.opCode.trim()) {
            case "getVar": {
                String name = param(b, 0, "score").trim();
                return resolveBuiltInOrVar(name, fiber);
            }
            case "getPosX":
                return self != null ? (double) self.bean.x : 0.0;
            case "getPosY":
                return self != null ? (double) self.bean.y : 0.0;
            case "getVelX":
                return self != null ? (double) self.bean.velocityX : 0.0;
            case "getVelY":
                return self != null ? (double) self.bean.velocityY : 0.0;
            case "getDirection":
                return self != null ? (double) self.bean.rotation : 0.0;
            case "getSize":
                return self != null ? (double) (self.bean.scaleX * 100f) : 100.0;
            case "getSpriteName":
                return (self != null && self.bean.spriteResName != null) ? self.bean.spriteResName : "";
            case "getVolume":
                return (double) masterVolumePercent;
            case "joyX":
                return (double) joyX;
            case "joyY":
                return (double) joyY;
            case "joyAngle":
                return (Math.hypot(joyX, joyY) > 0.05)
                        ? Math.toDegrees(Math.atan2(joyY, joyX)) : 0.0;
            case "touchX":
                return (double) touchX;
            case "touchY":
                return (double) touchY;
            case "timer":
                return (double) gameTimerSeconds;
            case "isJoystickActive":
                return !joystickObjectId.isEmpty() || Math.hypot(joyX, joyY) > 0.05;
            case "isTouchingScreen":
                return isTouchingWorld;
            case "isOnGround":
                return self != null && self.onGround;
            case "boolTrue":
                return true;
            case "boolFalse":
                return false;
            case "touchingButton": {
                String key = normalizeActionKey(evalSlotText(b, 0, "JUMP", fiber));
                return pressedButtons.contains(key) || latchedButtons.contains(key);
            }
            case "touchingTag": {
                if (self == null) return false;
                String tag = evalSlotText(b, 0, "coin", fiber);
                RuntimeObject m = findCollidingWithTag(self, tag);
                if (m != null) {
                    if (fiber != null) fiber.other = m;
                    return true;
                }
                return false;
            }
            case "touchingObject": {
                if (self == null) return false;
                String targetName = evalSlotText(b, 0, "touch", fiber).trim();
                if ("touch".equalsIgnoreCase(targetName) || "pointer".equalsIgnoreCase(targetName)) {
                    return isTouchingWorld
                            && touchX >= self.bean.x && touchX <= self.bean.x + self.bean.width
                            && touchY >= self.bean.y && touchY <= self.bean.y + self.bean.height;
                }
                RuntimeObject other = findRuntimeObjectByIdOrName(targetName);
                return other != null && !other.destroyed && aabbOverlap(self.bean, other.bean);
            }
            case "distanceTo": {
                if (self == null) return 0.0;
                float cx = self.bean.x + self.bean.width * 0.5f;
                float cy = self.bean.y + self.bean.height * 0.5f;
                String targetName = evalSlotText(b, 0, "touch", fiber).trim();
                float tx = touchX;
                float ty = touchY;
                if (!"touch".equalsIgnoreCase(targetName) && !"pointer".equalsIgnoreCase(targetName)) {
                    RuntimeObject t = findRuntimeObjectByIdOrName(targetName);
                    if (t == null) return 0.0;
                    tx = t.bean.x + t.bean.width * 0.5f;
                    ty = t.bean.y + t.bean.height * 0.5f;
                }
                return Math.hypot(tx - cx, ty - cy);
            }
            case "propOf": {
                String prop = evalSlotText(b, 0, "x", fiber).trim();
                String objName = evalSlotText(b, 1, "self", fiber).trim();
                RuntimeObject t = "self".equalsIgnoreCase(objName) ? self : findRuntimeObjectByIdOrName(objName);
                return readObjectProperty(t, prop);
            }
            case "opAdd":
                return evalSlotDouble(b, 0, "0", fiber) + evalSlotDouble(b, 1, "0", fiber);
            case "opSub":
                return evalSlotDouble(b, 0, "0", fiber) - evalSlotDouble(b, 1, "0", fiber);
            case "opMul":
                return evalSlotDouble(b, 0, "1", fiber) * evalSlotDouble(b, 1, "1", fiber);
            case "opDiv": {
                double d = evalSlotDouble(b, 1, "1", fiber);
                return d == 0.0 ? 0.0 : evalSlotDouble(b, 0, "1", fiber) / d;
            }
            case "opMod": {
                double d = evalSlotDouble(b, 1, "1", fiber);
                return d == 0.0 ? 0.0 : evalSlotDouble(b, 0, "0", fiber) % d;
            }
            case "opRandom": {
                double a = evalSlotDouble(b, 0, "1", fiber);
                double c = evalSlotDouble(b, 1, "10", fiber);
                if (a > c) {
                    double tmp = a;
                    a = c;
                    c = tmp;
                }
                boolean isInt = b.getParamBlock(0) == null && b.getParamBlock(1) == null
                        && !param(b, 0, "1").contains(".") && !param(b, 1, "10").contains(".");
                if (isInt) {
                    int lo = (int) Math.round(a);
                    int hi = (int) Math.round(c);
                    return (double) (lo + random.nextInt(Math.max(1, hi - lo + 1)));
                }
                return a + random.nextDouble() * (c - a);
            }
            case "opRound":
                return (double) Math.round(evalSlotDouble(b, 0, "0", fiber));
            case "opClamp": {
                double v = evalSlotDouble(b, 0, "0", fiber);
                double lo = evalSlotDouble(b, 1, "0", fiber);
                double hi = evalSlotDouble(b, 2, "100", fiber);
                if (lo > hi) {
                    double t = lo; lo = hi; hi = t;
                }
                return Math.max(lo, Math.min(hi, v));
            }
            case "opMathFn": {
                String fn = evalSlotText(b, 0, "abs", fiber).trim();
                double x = evalSlotDouble(b, 1, "0", fiber);
                return evalMathFn(fn, x);
            }
            case "opGt":
                return evalSlotDouble(b, 0, "0", fiber) > evalSlotDouble(b, 1, "50", fiber);
            case "opLt":
                return evalSlotDouble(b, 0, "0", fiber) < evalSlotDouble(b, 1, "50", fiber);
            case "opEq": {
                Object left = b.getParamBlock(0) != null
                        ? evaluateBlockValue(b.getParamBlock(0), fiber)
                        : evalStringOrRaw(param(b, 0, "0"), fiber);
                Object right = b.getParamBlock(1) != null
                        ? evaluateBlockValue(b.getParamBlock(1), fiber)
                        : evalStringOrRaw(param(b, 1, "50"), fiber);
                return valuesEqual(left, right);
            }
            case "opAnd":
                return evalSlotBool(b, 0, "false", fiber) && evalSlotBool(b, 1, "false", fiber);
            case "opOr":
                return evalSlotBool(b, 0, "false", fiber) || evalSlotBool(b, 1, "false", fiber);
            case "opNot":
                return !evalSlotBool(b, 0, "false", fiber);
            case "opJoin":
                return evalSlotText(b, 0, "", fiber) + evalSlotText(b, 1, "", fiber);
            case "opLetterOf": {
                int idx = Math.round(evalSlotFloat(b, 0, "1", fiber)) - 1;
                String s = evalSlotText(b, 1, "", fiber);
                return (idx >= 0 && idx < s.length()) ? String.valueOf(s.charAt(idx)) : "";
            }
            case "opLengthOf":
                return (double) evalSlotText(b, 0, "", fiber).length();
            case "opContains": {
                String haystack = evalSlotText(b, 0, "", fiber);
                String needle = evalSlotText(b, 1, "", fiber);
                return haystack.contains(needle);
            }
            default:
                return evaluateExpression(param(b, 0, "0"), fiber);
        }
    }

    private static double evalMathFn(String fn, double x) {
        switch (fn) {
            case "abs": return Math.abs(x);
            case "floor": return Math.floor(x);
            case "ceil": return Math.ceil(x);
            case "sqrt": return Math.sqrt(Math.max(0.0, x));
            case "sin": return Math.sin(Math.toRadians(x));
            case "cos": return Math.cos(Math.toRadians(x));
            case "tan": return Math.tan(Math.toRadians(x));
            case "asin": return Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, x))));
            case "acos": return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, x))));
            case "atan": return Math.toDegrees(Math.atan(x));
            case "ln": return x > 0.0 ? Math.log(x) : 0.0;
            case "log10": return x > 0.0 ? Math.log10(x) : 0.0;
            case "exp": return Math.exp(x);
            default: return Math.abs(x);
        }
    }

    private Object readObjectProperty(RuntimeObject t, String prop) {
        if (t == null || t.bean == null || prop == null) {
            return 0.0;
        }
        switch (prop) {
            case "x": return (double) t.bean.x;
            case "y": return (double) t.bean.y;
            case "vx": return (double) t.bean.velocityX;
            case "vy": return (double) t.bean.velocityY;
            case "rotation": return (double) t.bean.rotation;
            case "size": return (double) (t.bean.scaleX * 100f);
            case "alpha": return (double) t.bean.alpha;
            case "spriteName": return t.bean.spriteResName != null ? t.bean.spriteResName : "";
            case "visible": return t.bean.visible;
            default: return 0.0;
        }
    }

    private static boolean aabbOverlap(GameObjectBean a, GameObjectBean b) {
        return a.x < b.x + b.width && a.x + a.width > b.x
                && a.y < b.y + b.height && a.y + a.height > b.y;
    }

    // ==================== Expression Evaluator ====================

    private float evalFloat(String expr, ScriptFiber fiber) {
        return (float) evalDouble(expr, fiber);
    }

    private double evalDouble(String expr, ScriptFiber fiber) {
        Object val = evaluateExpression(expr, fiber);
        return toDouble(val);
    }

    private boolean evalBool(String expr, ScriptFiber fiber) {
        Object val = evaluateExpression(expr, fiber);
        return toBool(val);
    }

    private String evalString(String expr, ScriptFiber fiber) {
        if (expr == null) {
            return "";
        }
        String trimmed = expr.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.contains("{") && trimmed.contains("}") && !trimmed.startsWith("\"") && !trimmed.startsWith("'")) {
            return interpolateTemplate(trimmed);
        }
        try {
            Object val = new ExprParser(trimmed, fiber).parse();
            if (val instanceof Double) {
                return formatNumber((Double) val);
            }
            if (val instanceof Boolean) {
                return ((Boolean) val) ? "true" : "false";
            }
            return val == null ? "" : val.toString();
        } catch (Throwable ignored) {
            return interpolateTemplate(trimmed);
        }
    }

    private String evalStringOrRaw(String expr, ScriptFiber fiber) {
        if (expr == null) {
            return "";
        }
        String trimmed = expr.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2)
                || (trimmed.startsWith("'") && trimmed.endsWith("'") && trimmed.length() >= 2)) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        if (variables.containsKey(trimmed)) {
            return variables.get(trimmed);
        }
        if (trimmed.contains("+") || trimmed.contains("(")) {
            return evalString(trimmed, fiber);
        }
        return trimmed;
    }

    private Object evaluateExpression(String expr, ScriptFiber fiber) {
        if (expr == null || expr.trim().isEmpty()) {
            return 0.0;
        }
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
                    String val = resolveBuiltInOrVarString(key, null);
                    sb.append(val);
                    i = end + 1;
                    continue;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private String resolveBuiltInOrVarString(String name, ScriptFiber fiber) {
        Object o = resolveBuiltInOrVar(name, fiber);
        if (o instanceof Double) {
            return formatNumber((Double) o);
        }
        return o == null ? "0" : o.toString();
    }

    private Object resolveBuiltInOrVar(String name, ScriptFiber fiber) {
        if (name == null) return 0.0;
        switch (name) {
            case "joyX":
            case "joystickX":
                return (double) joyX;
            case "joyY":
            case "joystickY":
                return (double) joyY;
            case "joyAngle":
                return (Math.hypot(joyX, joyY) > 0.05)
                        ? Math.toDegrees(Math.atan2(joyY, joyX)) : 0.0;
            case "joyActive":
                return !joystickObjectId.isEmpty() || Math.hypot(joyX, joyY) > 0.05;
            case "touchX":
                return (double) touchX;
            case "touchY":
                return (double) touchY;
            case "isTouching":
            case "touchDown":
                return isTouchingWorld;
            case "timer":
                return (double) gameTimerSeconds;
            case "volume":
                return (double) masterVolumePercent;
            case "size":
                return fiber != null && fiber.self != null ? (double) (fiber.self.bean.scaleX * 100f) : 100.0;
            case "spriteName":
                return (fiber != null && fiber.self != null && fiber.self.bean.spriteResName != null)
                        ? fiber.self.bean.spriteResName : "";
            case "fps":
                return (double) currentFps;
            case "dt":
                return (double) FIXED_DT;
            case "camX":
                return (double) camX;
            case "camY":
                return (double) camY;
            case "virtualWidth":
            case "screenWidth":
                return (double) project.virtualWidth;
            case "virtualHeight":
            case "screenHeight":
                return (double) project.virtualHeight;
            case "isOnGround":
            case "onGround":
                return fiber != null && fiber.self != null && fiber.self.onGround;
            case "true":
                return true;
            case "false":
                return false;
            case "selfX":
            case "x":
                return fiber != null && fiber.self != null ? (double) fiber.self.bean.x : 0.0;
            case "selfY":
            case "y":
                return fiber != null && fiber.self != null ? (double) fiber.self.bean.y : 0.0;
            case "selfVX":
            case "vx":
                return fiber != null && fiber.self != null ? (double) fiber.self.bean.velocityX : 0.0;
            case "selfVY":
            case "vy":
                return fiber != null && fiber.self != null ? (double) fiber.self.bean.velocityY : 0.0;
            case "selfRot":
            case "rotation":
            case "rot":
                return fiber != null && fiber.self != null ? (double) fiber.self.bean.rotation : 0.0;
            case "selfWidth":
                return fiber != null && fiber.self != null ? (double) fiber.self.bean.width : 0.0;
            case "selfHeight":
                return fiber != null && fiber.self != null ? (double) fiber.self.bean.height : 0.0;
            default: {
                if (variables.containsKey(name)) {
                    String raw = variables.get(name);
                    if (isNumericString(raw)) {
                        return Double.parseDouble(raw);
                    }
                    return raw == null ? "" : raw;
                }
                return 0.0;
            }
        }
    }

    private final class ExprParser {
        private final String src;
        private final ScriptFiber fiber;
        private int pos = 0;

        ExprParser(String src, ScriptFiber fiber) {
            this.src = src;
            this.fiber = fiber;
        }

        Object parse() {
            Object val = parseOr();
            skipSpaces();
            if (pos < src.length()) {
                throw new IllegalArgumentException("Unexpected trailing token at " + pos);
            }
            return val;
        }

        private void skipSpaces() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
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
                if (end >= src.length() || !Character.isLetterOrDigit(src.charAt(end)) && src.charAt(end) != '_') {
                    pos = end;
                    return true;
                }
            }
            return false;
        }

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
                    Object right = parseRelational();
                    left = valuesEqual(left, right);
                } else if (match("!=") || match("<>")) {
                    Object right = parseRelational();
                    left = !valuesEqual(left, right);
                } else {
                    return left;
                }
            }
        }

        private Object parseRelational() {
            Object left = parseAdditive();
            while (true) {
                if (match("<=")) {
                    left = toDouble(left) <= toDouble(parseAdditive());
                } else if (match(">=")) {
                    left = toDouble(left) >= toDouble(parseAdditive());
                } else if (match("<")) {
                    left = toDouble(left) < toDouble(parseAdditive());
                } else if (match(">")) {
                    left = toDouble(left) > toDouble(parseAdditive());
                } else {
                    return left;
                }
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
                    Object right = parseMultiplicative();
                    left = toDouble(left) - toDouble(right);
                } else {
                    return left;
                }
            }
        }

        private Object parseMultiplicative() {
            Object left = parseUnary();
            while (true) {
                if (match("*")) {
                    left = toDouble(left) * toDouble(parseUnary());
                } else if (match("/")) {
                    double denom = toDouble(parseUnary());
                    left = denom == 0.0 ? 0.0 : toDouble(left) / denom;
                } else if (match("%")) {
                    double denom = toDouble(parseUnary());
                    left = denom == 0.0 ? 0.0 : toDouble(left) % denom;
                } else {
                    return left;
                }
            }
        }

        private Object parseUnary() {
            if (match("!") || matchKeyword("not")) {
                return !toBool(parseUnary());
            }
            if (match("-")) {
                return -toDouble(parseUnary());
            }
            if (match("+")) {
                return toDouble(parseUnary());
            }
            return parsePrimary();
        }

        private Object parsePrimary() {
            skipSpaces();
            if (pos >= src.length()) {
                throw new IllegalArgumentException("Unexpected end of expression");
            }
            char c = src.charAt(pos);
            if (c == '(') {
                pos++;
                Object inner = parseOr();
                if (!match(")")) {
                    throw new IllegalArgumentException("Missing ')'");
                }
                return inner;
            }
            if (c == '"' || c == '\'') {
                return parseQuotedString(c);
            }
            if (Character.isDigit(c) || (c == '.' && pos + 1 < src.length() && Character.isDigit(src.charAt(pos + 1)))) {
                int start = pos;
                boolean seenDot = false;
                while (pos < src.length()) {
                    char ch = src.charAt(pos);
                    if (Character.isDigit(ch)) {
                        pos++;
                    } else if (ch == '.' && !seenDot) {
                        seenDot = true;
                        pos++;
                    } else {
                        break;
                    }
                }
                return Double.parseDouble(src.substring(start, pos));
            }
            if (Character.isLetter(c) || c == '_') {
                int start = pos;
                while (pos < src.length()) {
                    char ch = src.charAt(pos);
                    if (Character.isLetterOrDigit(ch) || ch == '_') {
                        pos++;
                    } else {
                        break;
                    }
                }
                String ident = src.substring(start, pos);
                skipSpaces();
                if (pos < src.length() && src.charAt(pos) == '(') {
                    return parseFunctionCall(ident);
                }
                return resolveBuiltInOrVar(ident, fiber);
            }
            throw new IllegalArgumentException("Unexpected char '" + c + "'");
        }

        private String parseQuotedString(char quote) {
            pos++;
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char ch = src.charAt(pos++);
                if (ch == '\\' && pos < src.length()) {
                    sb.append(src.charAt(pos++));
                } else if (ch == quote) {
                    return sb.toString();
                } else {
                    sb.append(ch);
                }
            }
            return sb.toString();
        }

        private Object parseFunctionCall(String fnName) {
            pos++; // '('
            ArrayList<String> rawArgs = new ArrayList<>();
            ArrayList<Object> evalArgs = new ArrayList<>();
            skipSpaces();
            if (!match(")")) {
                while (true) {
                    skipSpaces();
                    int argStart = pos;
                    if (isSingleBareIdentifierArg()) {
                        int idStart = pos;
                        while (pos < src.length() && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_')) {
                            pos++;
                        }
                        String rawIdent = src.substring(idStart, pos);
                        rawArgs.add(rawIdent);
                        if (variables.containsKey(rawIdent)) {
                            evalArgs.add(resolveBuiltInOrVar(rawIdent, fiber));
                        } else {
                            evalArgs.add(rawIdent);
                        }
                    } else {
                        Object val = parseOr();
                        rawArgs.add(src.substring(argStart, pos).trim());
                        evalArgs.add(val);
                    }
                    if (match(",")) {
                        continue;
                    }
                    if (!match(")")) {
                        throw new IllegalArgumentException("Expected ')' in function call");
                    }
                    break;
                }
            }

            switch (fnName) {
                case "random": {
                    double a = evalArgs.size() >= 1 ? toDouble(evalArgs.get(0)) : 0.0;
                    double b = evalArgs.size() >= 2 ? toDouble(evalArgs.get(1)) : 1.0;
                    if (a > b) {
                        double tmp = a;
                        a = b;
                        b = tmp;
                    }
                    boolean intRange = rawArgs.size() >= 2
                            && !rawArgs.get(0).contains(".")
                            && !rawArgs.get(1).contains(".");
                    if (intRange) {
                        int lo = (int) Math.round(a);
                        int hi = (int) Math.round(b);
                        return (double) (lo + random.nextInt(Math.max(1, hi - lo + 1)));
                    }
                    return a + random.nextDouble() * (b - a);
                }
                case "abs":
                    return evalArgs.isEmpty() ? 0.0 : Math.abs(toDouble(evalArgs.get(0)));
                case "sqrt":
                    return evalArgs.isEmpty() ? 0.0 : Math.sqrt(Math.max(0.0, toDouble(evalArgs.get(0))));
                case "sin":
                    return evalArgs.isEmpty() ? 0.0 : Math.sin(Math.toRadians(toDouble(evalArgs.get(0))));
                case "cos":
                    return evalArgs.isEmpty() ? 0.0 : Math.cos(Math.toRadians(toDouble(evalArgs.get(0))));
                case "floor":
                    return evalArgs.isEmpty() ? 0.0 : Math.floor(toDouble(evalArgs.get(0)));
                case "ceil":
                    return evalArgs.isEmpty() ? 0.0 : Math.ceil(toDouble(evalArgs.get(0)));
                case "round":
                    return evalArgs.isEmpty() ? 0.0 : (double) Math.round(toDouble(evalArgs.get(0)));
                case "min":
                    return evalArgs.size() < 2 ? 0.0 : Math.min(toDouble(evalArgs.get(0)), toDouble(evalArgs.get(1)));
                case "max":
                    return evalArgs.size() < 2 ? 0.0 : Math.max(toDouble(evalArgs.get(0)), toDouble(evalArgs.get(1)));
                case "clamp": {
                    if (evalArgs.size() < 3) return 0.0;
                    double v = toDouble(evalArgs.get(0));
                    double lo = toDouble(evalArgs.get(1));
                    double hi = toDouble(evalArgs.get(2));
                    return Math.max(lo, Math.min(hi, v));
                }
                case "isButtonPressed":
                case "buttonPressed":
                case "ifButtonPressed":
                case "btn":
                case "ifKeyPressed":
                case "keyPressed": {
                    String key = evalArgs.isEmpty() ? "JUMP" : normalizeActionKey(valueToString(evalArgs.get(0)));
                    return pressedButtons.contains(key) || latchedButtons.contains(key);
                }
                case "isCollidingTag":
                case "collidingTag":
                case "touchingTag":
                case "ifCollidingTag": {
                    if (fiber == null || fiber.self == null) return false;
                    String tag = evalArgs.isEmpty() ? "" : valueToString(evalArgs.get(0));
                    RuntimeObject match = findCollidingWithTag(fiber.self, tag);
                    if (match != null) {
                        fiber.other = match;
                        return true;
                    }
                    return false;
                }
                case "touchingObj": {
                    if (fiber == null || fiber.self == null) return false;
                    String targetName = evalArgs.isEmpty() ? "touch" : valueToString(evalArgs.get(0)).trim();
                    if ("touch".equalsIgnoreCase(targetName) || "pointer".equalsIgnoreCase(targetName)) {
                        return isTouchingWorld
                                && touchX >= fiber.self.bean.x && touchX <= fiber.self.bean.x + fiber.self.bean.width
                                && touchY >= fiber.self.bean.y && touchY <= fiber.self.bean.y + fiber.self.bean.height;
                    }
                    RuntimeObject other = findRuntimeObjectByIdOrName(targetName);
                    return other != null && !other.destroyed && aabbOverlap(fiber.self.bean, other.bean);
                }
                case "dist": {
                    if (fiber == null || fiber.self == null) return 0.0;
                    float cx = fiber.self.bean.x + fiber.self.bean.width * 0.5f;
                    float cy = fiber.self.bean.y + fiber.self.bean.height * 0.5f;
                    String targetName = evalArgs.isEmpty() ? "touch" : valueToString(evalArgs.get(0)).trim();
                    float tx = touchX;
                    float ty = touchY;
                    if (!"touch".equalsIgnoreCase(targetName) && !"pointer".equalsIgnoreCase(targetName)) {
                        RuntimeObject t = findRuntimeObjectByIdOrName(targetName);
                        if (t == null) return 0.0;
                        tx = t.bean.x + t.bean.width * 0.5f;
                        ty = t.bean.y + t.bean.height * 0.5f;
                    }
                    return Math.hypot(tx - cx, ty - cy);
                }
                case "prop": {
                    String prop = evalArgs.size() >= 1 ? valueToString(evalArgs.get(0)) : "x";
                    String objName = evalArgs.size() >= 2 ? valueToString(evalArgs.get(1)) : "self";
                    RuntimeObject t = "self".equalsIgnoreCase(objName)
                            ? (fiber != null ? fiber.self : null)
                            : findRuntimeObjectByIdOrName(objName);
                    return readObjectProperty(t, prop);
                }
                case "tan":
                case "asin":
                case "acos":
                case "atan":
                case "ln":
                case "log10":
                case "exp":
                    return evalArgs.isEmpty() ? 0.0 : evalMathFn(fnName, toDouble(evalArgs.get(0)));
                case "join": {
                    String a = evalArgs.size() >= 1 ? valueToString(evalArgs.get(0)) : "";
                    String c = evalArgs.size() >= 2 ? valueToString(evalArgs.get(1)) : "";
                    return a + c;
                }
                case "letterOf": {
                    int idx = (evalArgs.size() >= 1 ? (int) Math.round(toDouble(evalArgs.get(0))) : 1) - 1;
                    String s = evalArgs.size() >= 2 ? valueToString(evalArgs.get(1)) : "";
                    return (idx >= 0 && idx < s.length()) ? String.valueOf(s.charAt(idx)) : "";
                }
                case "lengthOf": {
                    String s = evalArgs.isEmpty() ? "" : valueToString(evalArgs.get(0));
                    return (double) s.length();
                }
                case "contains": {
                    String h = evalArgs.size() >= 1 ? valueToString(evalArgs.get(0)) : "";
                    String n = evalArgs.size() >= 2 ? valueToString(evalArgs.get(1)) : "";
                    return h.contains(n);
                }
                case "isOnGround":
                case "onGround":
                    return fiber != null && fiber.self != null && fiber.self.onGround;
                case "joystickX":
                case "joyX":
                    return (double) joyX;
                case "joystickY":
                case "joyY":
                    return (double) joyY;
                case "var":
                case "getVar": {
                    String varName = evalArgs.isEmpty() ? "" : valueToString(evalArgs.get(0));
                    return resolveBuiltInOrVar(varName, fiber);
                }
                default:
                    return 0.0;
            }
        }

        private boolean isSingleBareIdentifierArg() {
            int p = pos;
            if (p >= src.length() || (!Character.isLetter(src.charAt(p)) && src.charAt(p) != '_')) {
                return false;
            }
            while (p < src.length() && (Character.isLetterOrDigit(src.charAt(p)) || src.charAt(p) == '_')) {
                p++;
            }
            while (p < src.length() && Character.isWhitespace(src.charAt(p))) {
                p++;
            }
            return p < src.length() && (src.charAt(p) == ',' || src.charAt(p) == ')');
        }
    }

    // ==================== Rendering ====================

    private void renderScene(Canvas canvas) {
        canvas.drawColor(0xFF0B0E14);
        if (currentScene == null) {
            return;
        }

        int vw = Math.max(1, project.virtualWidth);
        int vh = Math.max(1, project.virtualHeight);

        canvas.save();
        canvas.translate(viewOffsetX, viewOffsetY);
        canvas.clipRect(0f, 0f, vw * viewScale, vh * viewScale);
        canvas.scale(viewScale, viewScale);

        // Background color & background sprite
        canvas.drawColor(currentScene.backgroundColor);
        if (currentScene.backgroundSprite != null && !currentScene.backgroundSprite.isEmpty()) {
            Bitmap bg = loadBitmap(currentScene.backgroundSprite);
            if (bg != null && !bg.isRecycled()) {
                tempRect.set(0f, 0f, vw, vh);
                canvas.drawBitmap(bg, null, tempRect, bitmapPaint);
            }
        }

        // Camera offset + screen shake
        float shakeX = 0f;
        float shakeY = 0f;
        if (shakeRemaining > 0f && shakeDuration > 0f) {
            float decay = shakeRemaining / shakeDuration;
            float mag = shakeIntensity * decay;
            shakeX = (random.nextFloat() * 2f - 1f) * mag;
            shakeY = (random.nextFloat() * 2f - 1f) * mag;
        }

        // Draw World objects
        canvas.save();
        canvas.translate(-camX + shakeX, -camY + shakeY);
        ArrayList<RuntimeObject> world = getWorldObjectsSorted();
        for (RuntimeObject obj : world) {
            drawWorldObject(canvas, obj);
        }
        drawParticles(canvas);
        canvas.restore();

        // Draw HUD objects in virtual screen space
        ArrayList<RuntimeObject> hud = getHudObjectsSorted();
        for (RuntimeObject obj : hud) {
            drawHudObject(canvas, obj);
        }

        canvas.restore();
    }

    private void drawWorldObject(Canvas canvas, RuntimeObject obj) {
        GameObjectBean b = obj.bean;
        if (obj.destroyed || !b.visible || b.alpha <= 0f) {
            return;
        }
        if (GameObjectBean.TYPE_CAMERA.equals(b.type) || GameObjectBean.TYPE_PARTICLES.equals(b.type)) {
            if (project.showDebugHitboxes) {
                drawDebugHitbox(canvas, obj);
            }
            return;
        }

        float w = Math.max(1f, b.width);
        float h = Math.max(1f, b.height);
        float cx = b.x + w * 0.5f;
        float cy = b.y + h * 0.5f;
        int alphaInt = Math.max(0, Math.min(255, Math.round(b.alpha * 255f)));

        canvas.save();
        canvas.translate(cx, cy);
        if (b.rotation != 0f) {
            canvas.rotate(b.rotation);
        }
        if (b.scaleX != 1f || b.scaleY != 1f) {
            canvas.scale(b.scaleX, b.scaleY);
        }

        Bitmap bmp = loadBitmap(b.spriteResName);
        if (bmp != null && !bmp.isRecycled()) {
            bitmapPaint.setAlpha(alphaInt);
            tempRect.set(-w * 0.5f, -h * 0.5f, w * 0.5f, h * 0.5f);
            canvas.drawBitmap(bmp, null, tempRect, bitmapPaint);
            bitmapPaint.setAlpha(255);
        } else {
            tempRect.set(-w * 0.5f, -h * 0.5f, w * 0.5f, h * 0.5f);
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(b.color);
            fillPaint.setAlpha(alphaInt);

            if (GameObjectBean.TYPE_TRIGGER.equals(b.type)) {
                float r = Math.min(w, h) * 0.5f;
                canvas.drawCircle(0f, 0f, r, fillPaint);
                strokePaint.setStyle(Paint.Style.STROKE);
                strokePaint.setStrokeWidth(3f);
                strokePaint.setColor(0xFFFFFFFF);
                strokePaint.setAlpha(Math.round(alphaInt * 0.7f));
                canvas.drawCircle(0f, 0f, r - 2f, strokePaint);
            } else if (GameObjectBean.TYPE_PLATFORM.equals(b.type)) {
                canvas.drawRoundRect(tempRect, 10f, 10f, fillPaint);
                // Top bevel highlight
                fillPaint.setColor(0xFFFFFFFF);
                fillPaint.setAlpha(Math.round(alphaInt * 0.16f));
                tempRect.set(-w * 0.5f, -h * 0.5f, w * 0.5f, -h * 0.5f + Math.min(8f, h * 0.25f));
                canvas.drawRoundRect(tempRect, 8f, 8f, fillPaint);
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

        if (obj.speechText != null && !obj.speechText.isEmpty()) {
            drawSpeechBubble(canvas, obj);
        }

        if (project.showDebugHitboxes) {
            drawDebugHitbox(canvas, obj);
        }
    }

    private void drawSpeechBubble(Canvas canvas, RuntimeObject obj) {
        GameObjectBean b = obj.bean;
        String text = obj.speechText;
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setTextSize(22f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        float textW = textPaint.measureText(text);
        float boxW = Math.max(60f, textW + 28f);
        float boxH = 38f;
        float bx = b.x + b.width * 0.5f - boxW * 0.5f;
        float by = b.y - boxH - 12f;

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(0xFFFFFFFF);
        fillPaint.setAlpha(245);
        tempRect.set(bx, by, bx + boxW, by + boxH);
        canvas.drawRoundRect(tempRect, 14f, 14f, fillPaint);

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(2f);
        strokePaint.setColor(0xFF94A3B8);
        strokePaint.setAlpha(220);
        canvas.drawRoundRect(tempRect, 14f, 14f, strokePaint);

        textPaint.setColor(0xFF0F172A);
        textPaint.setAlpha(255);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textY = by + boxH * 0.5f - (fm.ascent + fm.descent) * 0.5f;
        canvas.drawText(text, bx + boxW * 0.5f, textY, textPaint);
    }

    private void drawDebugHitbox(Canvas canvas, RuntimeObject obj) {
        GameObjectBean b = obj.bean;
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(2f);
        strokePaint.setColor(b.isSensor ? 0xFFFFD54F : (b.isStatic ? 0xFF4FC3F7 : 0xFF69F0AE));
        strokePaint.setAlpha(220);
        tempRect.set(b.x, b.y, b.x + b.width, b.y + b.height);
        canvas.drawRect(tempRect, strokePaint);
    }

    private void drawParticles(Canvas canvas) {
        if (particles.isEmpty()) {
            return;
        }
        fillPaint.setStyle(Paint.Style.FILL);
        for (Particle p : particles) {
            float ratio = p.maxLife > 0f ? clamp(p.life / p.maxLife, 0f, 1f) : 0f;
            fillPaint.setColor(p.color);
            fillPaint.setAlpha(Math.round(255f * ratio));
            canvas.drawCircle(p.x, p.y, p.size * (0.4f + 0.6f * ratio), fillPaint);
        }
        fillPaint.setAlpha(255);
    }

    private void drawHudObject(Canvas canvas, RuntimeObject obj) {
        GameObjectBean b = obj.bean;
        if (obj.destroyed || !b.visible || b.alpha <= 0f) {
            return;
        }
        int alphaInt = Math.max(0, Math.min(255, Math.round(b.alpha * 255f)));

        switch (b.type) {
            case GameObjectBean.TYPE_HUD_TEXT: {
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
                break;
            }
            case GameObjectBean.TYPE_HUD_BUTTON: {
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
                int fillAlpha = Math.round(alphaInt * (isPressed ? 0.62f : 0.34f));

                fillPaint.setStyle(Paint.Style.FILL);
                fillPaint.setColor(b.color);
                fillPaint.setAlpha(fillAlpha);

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
                    float base = -(fm.ascent + fm.descent) * 0.5f;
                    canvas.drawText(label, 0f, base, textPaint);
                }
                canvas.restore();
                break;
            }
            case GameObjectBean.TYPE_HUD_JOYSTICK: {
                float cx = b.x + b.width * 0.5f;
                float cy = b.y + b.height * 0.5f;
                float outerR = Math.min(b.width, b.height) * 0.48f;
                float maxR = Math.max(16f, Math.min(b.width, b.height) * 0.42f);
                float knobR = outerR * 0.42f;
                boolean active = !joystickObjectId.isEmpty() && joystickObjectId.equals(b.id);

                // Outer ring
                fillPaint.setStyle(Paint.Style.FILL);
                fillPaint.setColor(0xFF000000);
                fillPaint.setAlpha(Math.round(alphaInt * 0.25f));
                canvas.drawCircle(cx, cy, outerR, fillPaint);

                strokePaint.setStyle(Paint.Style.STROKE);
                strokePaint.setStrokeWidth(3f);
                strokePaint.setColor(b.color);
                strokePaint.setAlpha(Math.round(alphaInt * 0.55f));
                canvas.drawCircle(cx, cy, outerR, strokePaint);

                // Inner thumbstick knob
                float kx = cx + (active ? joyX * maxR : 0f);
                float ky = cy + (active ? joyY * maxR : 0f);
                fillPaint.setColor(b.color);
                fillPaint.setAlpha(Math.round(alphaInt * (active ? 0.85f : 0.6f)));
                canvas.drawCircle(kx, ky, knobR, fillPaint);

                strokePaint.setColor(0xFFFFFFFF);
                strokePaint.setAlpha(Math.round(alphaInt * 0.65f));
                canvas.drawCircle(kx, ky, knobR - 1.5f, strokePaint);
                break;
            }
            case GameObjectBean.TYPE_HUD_BAR: {
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
                break;
            }
            default: {
                drawWorldObject(canvas, obj);
                break;
            }
        }
    }

    // ==================== Asset & Audio Loading ====================

    private Bitmap loadBitmap(String resName) {
        if (resName == null || resName.trim().isEmpty()) {
            return null;
        }
        String key = resName.trim();
        if (bitmapCache.containsKey(key)) {
            return bitmapCache.get(key);
        }
        Bitmap bmp = null;
        try {
            File file = GameProjectRepository.getImageFile(scId, key);
            if (file != null && file.exists() && file.isFile()) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
                int sample = 1;
                while (opts.outWidth / sample > 2048 || opts.outHeight / sample > 2048) {
                    sample *= 2;
                }
                opts.inJustDecodeBounds = false;
                opts.inSampleSize = sample;
                bmp = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            }
        } catch (Throwable ignored) {
        }
        bitmapCache.put(key, bmp);
        return bmp;
    }

    private Typeface loadFont(String resName) {
        if (resName == null || resName.trim().isEmpty()) {
            return null;
        }
        String key = resName.trim();
        if (fontCache.containsKey(key)) {
            return fontCache.get(key);
        }
        Typeface tf = null;
        try {
            File file = GameProjectRepository.getFontFile(scId, key);
            if (file != null && file.exists() && file.isFile()) {
                tf = Typeface.createFromFile(file);
            }
        } catch (Throwable ignored) {
        }
        fontCache.put(key, tf);
        return tf;
    }

    private void playSoundInternal(String resName) {
        if (resName == null || resName.trim().isEmpty() || soundPool == null) {
            return;
        }
        String key = resName.trim();
        Integer id = soundIdMap.get(key);
        if (id != null) {
            if (loadedSoundIds.contains(id)) {
                float vol = clamp(masterVolumePercent / 100f, 0f, 1f);
                soundPool.play(id, vol, vol, 1, 0, 1f);
            } else {
                pendingPlaySoundIds.add(id);
            }
            return;
        }
        File file = GameProjectRepository.getSoundFile(scId, key);
        if (file != null && file.exists() && file.isFile()) {
            try {
                int loadedId = soundPool.load(file.getAbsolutePath(), 1);
                if (loadedId > 0) {
                    soundIdMap.put(key, loadedId);
                    pendingPlaySoundIds.add(loadedId);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private void playBgmInternal(String resName, boolean loop) {
        if (resName == null || resName.trim().isEmpty()) {
            stopBgmInternal();
            return;
        }
        String key = resName.trim();
        if (key.equals(currentBgmResName) && bgmPlayer != null) {
            try {
                bgmPlayer.setLooping(loop);
                bgmWantedPlaying = true;
                if (!paused && !bgmPlayer.isPlaying()) {
                    bgmPlayer.start();
                }
                return;
            } catch (Throwable ignored) {
            }
        }
        stopBgmInternal();
        File file = GameProjectRepository.getSoundFile(scId, key);
        if (file == null || !file.exists() || !file.isFile()) {
            return;
        }
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setDataSource(file.getAbsolutePath());
            mp.setLooping(loop);
            mp.prepare();
            bgmPlayer = mp;
            currentBgmResName = key;
            bgmWantedPlaying = true;
            applyVolumeToBgm();
            if (!paused) {
                mp.start();
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
            try {
                if (bgmPlayer.isPlaying()) {
                    bgmPlayer.stop();
                }
            } catch (Throwable ignored) {
            }
            try {
                bgmPlayer.release();
            } catch (Throwable ignored) {
            }
            bgmPlayer = null;
        }
    }

    // ==================== Helpers & Data Structures ====================

    private ArrayList<RuntimeObject> getWorldObjectsSorted() {
        drawWorldScratch.clear();
        for (RuntimeObject o : objects) {
            if (!o.destroyed && !o.isHud()) {
                drawWorldScratch.add(o);
            }
        }
        Collections.sort(drawWorldScratch, Z_COMPARATOR);
        return drawWorldScratch;
    }

    private ArrayList<RuntimeObject> getHudObjectsSorted() {
        drawHudScratch.clear();
        for (RuntimeObject o : objects) {
            if (!o.destroyed && o.isHud()) {
                drawHudScratch.add(o);
            }
        }
        Collections.sort(drawHudScratch, Z_COMPARATOR);
        return drawHudScratch;
    }

    private RuntimeObject findRuntimeObjectById(String id) {
        if (id == null || id.isEmpty()) return null;
        for (RuntimeObject o : objects) {
            if (!o.destroyed && id.equals(o.bean.id)) {
                return o;
            }
        }
        return null;
    }

    private RuntimeObject findRuntimeObjectByIdOrName(String idOrName) {
        if (idOrName == null || idOrName.trim().isEmpty()) return null;
        String key = idOrName.trim();
        for (RuntimeObject o : objects) {
            if (!o.destroyed && (key.equals(o.bean.id) || key.equalsIgnoreCase(o.bean.name))) {
                return o;
            }
        }
        return null;
    }

    private RuntimeObject resolveTarget(String targetExpr, RuntimeObject fallback) {
        if (targetExpr == null || targetExpr.trim().isEmpty() || "self".equalsIgnoreCase(targetExpr.trim())) {
            return fallback;
        }
        return findRuntimeObjectByIdOrName(targetExpr.trim());
    }

    private static String normalizeActionKey(String key) {
        return key == null ? "" : key.trim().toUpperCase(Locale.ROOT);
    }

    private static String param(GameBlockItem block, int idx, String fallback) {
        if (block.parameters == null || idx < 0 || idx >= block.parameters.size()) {
            return fallback;
        }
        String v = block.parameters.get(idx);
        return v == null ? fallback : v;
    }

    private static boolean hasParam(GameBlockItem block, int idx) {
        return block.parameters != null && idx >= 0 && idx < block.parameters.size()
                && block.parameters.get(idx) != null && !block.parameters.get(idx).trim().isEmpty();
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static float parseProgressRatio(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 1f;
        }
        String trimmed = text.trim();
        int slash = trimmed.indexOf('/');
        if (slash > 0 && slash < trimmed.length() - 1) {
            float cur = (float) parseDoubleSafe(trimmed.substring(0, slash), 100.0);
            float max = (float) parseDoubleSafe(trimmed.substring(slash + 1), 100.0);
            if (max <= 0f) return 0f;
            return clamp(cur / max, 0f, 1f);
        }
        double val = parseDoubleSafe(trimmed, 100.0);
        if (trimmed.contains(".") && val >= 0.0 && val <= 1.0) {
            return (float) val;
        }
        return clamp((float) (val / 100.0), 0f, 1f);
    }

    private static int parseColorValue(String raw, int fallback) {
        if (raw == null || raw.trim().isEmpty()) return fallback;
        String s = raw.trim();
        try {
            if (s.startsWith("#")) {
                return Color.parseColor(s);
            }
            if (s.startsWith("0x") || s.startsWith("0X")) {
                return (int) Long.parseLong(s.substring(2), 16);
            }
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
            if (s.isEmpty() || "false".equalsIgnoreCase(s) || "0".equals(s)) return false;
            return true;
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
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            return "0";
        }
        if (Math.abs(d - Math.rint(d)) < 1e-6 && Math.abs(d) < 1e15) {
            return Long.toString(Math.round(d));
        }
        String formatted = String.format(Locale.US, "%.2f", d);
        if (formatted.contains(".")) {
            formatted = formatted.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return formatted;
    }

    private static final Comparator<RuntimeObject> Z_COMPARATOR = (a, b) -> {
        int cmp = Integer.compare(a.bean.zIndex, b.bean.zIndex);
        return cmp != 0 ? cmp : Integer.compare(a.spawnOrder, b.spawnOrder);
    };

    private int nextSpawnOrder = 0;

    private final class RuntimeObject {
        final GameObjectBean bean;
        final int spawnOrder;
        float prevX;
        float prevY;
        boolean onGround = false;
        boolean destroyed = false;
        boolean isClone = false;
        String speechText = "";
        float speechTimer = 0f;
        boolean glideActive = false;
        float glideElapsed = 0f;
        float glideDuration = 0f;
        float glideStartX = 0f;
        float glideStartY = 0f;
        float glideTargetX = 0f;
        float glideTargetY = 0f;
        final Set<String> prevColliders = new HashSet<>();
        final Set<String> currentColliders = new HashSet<>();

        RuntimeObject(GameObjectBean source) {
            this.bean = source.copy();
            this.spawnOrder = nextSpawnOrder++;
            this.prevX = this.bean.x;
            this.prevY = this.bean.y;
        }

        boolean isHud() {
            return bean.isHud || bean.isHudType();
        }
    }

    private static final class StackFrame {
        final List<GameBlockItem> blocks;
        int nextIndex = 0;
        int remainingIterations;
        boolean infinite = false;
        GameBlockItem whileUntilBlock = null;

        StackFrame(List<GameBlockItem> blocks, int iterations) {
            this.blocks = blocks;
            this.remainingIterations = iterations;
        }
    }

    private static final class ScriptFiber {
        final GameScriptNode script;
        final RuntimeObject self;
        RuntimeObject other;
        final ArrayList<StackFrame> frames = new ArrayList<>();
        float waitTimer = 0f;
        boolean finished = false;

        ScriptFiber(GameScriptNode script, RuntimeObject self, RuntimeObject other) {
            this.script = script;
            this.self = self;
            this.other = other;
            if (script.blocks != null && !script.blocks.isEmpty()) {
                this.frames.add(new StackFrame(script.blocks, 1));
            } else {
                this.finished = true;
            }
        }
    }

    private static final class PendingEvent {
        final String eventType;
        final String eventParam;
        final String targetObjectId;
        final RuntimeObject other;

        PendingEvent(String eventType, String eventParam, String targetObjectId, RuntimeObject other) {
            this.eventType = eventType;
            this.eventParam = eventParam;
            this.targetObjectId = targetObjectId;
            this.other = other;
        }
    }

    private static final class Particle {
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
