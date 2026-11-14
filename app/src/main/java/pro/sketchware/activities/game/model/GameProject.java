package pro.sketchware.activities.game.model;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Root 2D game project configuration containing scenes, prefabs, and global variables.
 */
public class GameProject {
    public String scId;
    public int virtualWidth = 1280;
    public int virtualHeight = 720;
    public boolean landscape = true;
    public int targetFps = 60;
    public boolean showDebugHitboxes = false;
    public String startSceneId = "scene_1";
    public ArrayList<GameScene> scenes = new ArrayList<>();
    public ArrayList<GameObjectBean> prefabs = new ArrayList<>();
    public HashMap<String, String> globalVariables = new HashMap<>();

    public GameProject() {
    }

    public void ensureInitialized(String fallbackScId) {
        if (scId == null || scId.isEmpty()) {
            scId = fallbackScId == null ? "" : fallbackScId;
        }
        if (virtualWidth <= 0) {
            virtualWidth = landscape ? 1280 : 720;
        }
        if (virtualHeight <= 0) {
            virtualHeight = landscape ? 720 : 1280;
        }
        if (targetFps <= 0) {
            targetFps = 60;
        }
        if (scenes == null) {
            scenes = new ArrayList<>();
        }
        if (scenes.isEmpty()) {
            GameProject def = createDefault(scId);
            scenes.addAll(def.scenes);
            if (prefabs == null || prefabs.isEmpty()) {
                prefabs = def.prefabs;
            }
            if (globalVariables == null || globalVariables.isEmpty()) {
                globalVariables = def.globalVariables;
            }
            startSceneId = def.startSceneId;
        }
        for (GameScene scene : scenes) {
            if (scene != null) {
                scene.ensureInitialized();
            }
        }
        if (prefabs == null) {
            prefabs = new ArrayList<>();
        } else {
            for (GameObjectBean prefab : prefabs) {
                if (prefab != null) {
                    prefab.ensureInitialized();
                }
            }
        }
        if (globalVariables == null) {
            globalVariables = new HashMap<>();
        }
        if (startSceneId == null || startSceneId.isEmpty()) {
            startSceneId = scenes.get(0).id;
        }
    }

    public GameScene getSceneById(String id) {
        if (id == null || scenes == null) {
            return null;
        }
        for (GameScene scene : scenes) {
            if (scene != null && id.equals(scene.id)) {
                return scene;
            }
        }
        return null;
    }

    public GameScene getStartScene() {
        GameScene start = getSceneById(startSceneId);
        if (start != null) {
            return start;
        }
        if (scenes != null && !scenes.isEmpty()) {
            return scenes.get(0);
        }
        return null;
    }

    /**
     * Creates a ready-to-play default platformer scene ("Fase 1") with Player, Platforms,
     * collectible Coin triggers, HUD score/joystick/jump button, and starter block scripts.
     */
    public static GameProject createDefault(String scId) {
        GameProject project = new GameProject();
        project.scId = scId == null ? "" : scId;
        project.virtualWidth = 1280;
        project.virtualHeight = 720;
        project.landscape = true;
        project.targetFps = 60;
        project.showDebugHitboxes = false;
        project.startSceneId = "scene_1";
        project.globalVariables.put("score", "0");

        GameScene scene = new GameScene("scene_1", "Scene 1");
        scene.backgroundColor = 0xFF141A24;
        scene.gravityY = 980f;

        // Ground platform
        GameObjectBean ground = new GameObjectBean();
        ground.id = "obj_ground";
        ground.name = "Ground";
        ground.type = GameObjectBean.TYPE_PLATFORM;
        ground.x = 0f;
        ground.y = 620f;
        ground.width = 1280f;
        ground.height = 100f;
        ground.color = 0xFF2E3B4E;
        ground.physicsEnabled = true;
        ground.isStatic = true;
        ground.friction = 0.25f;
        ground.tag = "ground";
        ground.zIndex = 1;
        scene.objects.add(ground);

        // Stepping platforms
        GameObjectBean plat1 = new GameObjectBean();
        plat1.id = "obj_plat_1";
        plat1.name = "Platform1";
        plat1.type = GameObjectBean.TYPE_PLATFORM;
        plat1.x = 380f;
        plat1.y = 470f;
        plat1.width = 240f;
        plat1.height = 36f;
        plat1.color = 0xFF3E526A;
        plat1.physicsEnabled = true;
        plat1.isStatic = true;
        plat1.friction = 0.25f;
        plat1.tag = "ground";
        plat1.zIndex = 1;
        scene.objects.add(plat1);

        GameObjectBean plat2 = new GameObjectBean();
        plat2.id = "obj_plat_2";
        plat2.name = "Platform2";
        plat2.type = GameObjectBean.TYPE_PLATFORM;
        plat2.x = 760f;
        plat2.y = 350f;
        plat2.width = 240f;
        plat2.height = 36f;
        plat2.color = 0xFF3E526A;
        plat2.physicsEnabled = true;
        plat2.isStatic = true;
        plat2.friction = 0.25f;
        plat2.tag = "ground";
        plat2.zIndex = 1;
        scene.objects.add(plat2);

        // Player
        GameObjectBean player = new GameObjectBean();
        player.id = "obj_player";
        player.name = "Player";
        player.type = GameObjectBean.TYPE_SPRITE;
        player.x = 160f;
        player.y = 490f;
        player.width = 84f;
        player.height = 84f;
        player.color = 0xFF4CAF50;
        player.physicsEnabled = true;
        player.isStatic = false;
        player.gravityScale = 1f;
        player.friction = 0.2f;
        player.tag = "player";
        player.zIndex = 10;
        scene.objects.add(player);

        // Collectible coins
        GameObjectBean coin1 = new GameObjectBean();
        coin1.id = "obj_coin_1";
        coin1.name = "Coin1";
        coin1.type = GameObjectBean.TYPE_TRIGGER;
        coin1.x = 472f;
        coin1.y = 392f;
        coin1.width = 56f;
        coin1.height = 56f;
        coin1.color = 0xFFFFD54F;
        coin1.physicsEnabled = true;
        coin1.isStatic = true;
        coin1.isSensor = true;
        coin1.tag = "coin";
        coin1.zIndex = 5;
        scene.objects.add(coin1);

        GameObjectBean coin2 = new GameObjectBean();
        coin2.id = "obj_coin_2";
        coin2.name = "Coin2";
        coin2.type = GameObjectBean.TYPE_TRIGGER;
        coin2.x = 852f;
        coin2.y = 272f;
        coin2.width = 56f;
        coin2.height = 56f;
        coin2.color = 0xFFFFD54F;
        coin2.physicsEnabled = true;
        coin2.isStatic = true;
        coin2.isSensor = true;
        coin2.tag = "coin";
        coin2.zIndex = 5;
        scene.objects.add(coin2);

        // HUD: Score text, Virtual Joystick, Jump Button
        GameObjectBean scoreHud = new GameObjectBean();
        scoreHud.id = "obj_hud_score";
        scoreHud.name = "ScoreText";
        scoreHud.type = GameObjectBean.TYPE_HUD_TEXT;
        scoreHud.isHud = true;
        scoreHud.x = 36f;
        scoreHud.y = 28f;
        scoreHud.width = 280f;
        scoreHud.height = 56f;
        scoreHud.color = 0xFFFFFFFF;
        scoreHud.text = "Score: 0";
        scoreHud.zIndex = 100;
        scoreHud.tag = "hud";
        scene.objects.add(scoreHud);

        GameObjectBean joystick = new GameObjectBean();
        joystick.id = "obj_hud_joy";
        joystick.name = "Joystick";
        joystick.type = GameObjectBean.TYPE_HUD_JOYSTICK;
        joystick.isHud = true;
        joystick.x = 48f;
        joystick.y = 496f;
        joystick.width = 176f;
        joystick.height = 176f;
        joystick.color = 0xFF90A4AE;
        joystick.actionKey = "MOVE";
        joystick.zIndex = 100;
        joystick.tag = "hud";
        scene.objects.add(joystick);

        GameObjectBean jumpBtn = new GameObjectBean();
        jumpBtn.id = "obj_hud_jump";
        jumpBtn.name = "JumpBtn";
        jumpBtn.type = GameObjectBean.TYPE_HUD_BUTTON;
        jumpBtn.isHud = true;
        jumpBtn.x = 1084f;
        jumpBtn.y = 516f;
        jumpBtn.width = 144f;
        jumpBtn.height = 144f;
        jumpBtn.color = 0xFF42A5F5;
        jumpBtn.text = "JUMP";
        jumpBtn.actionKey = "JUMP";
        jumpBtn.zIndex = 100;
        jumpBtn.tag = "hud";
        scene.objects.add(jumpBtn);

        // Starter prefab for coin
        GameObjectBean coinPrefab = coin1.copy();
        coinPrefab.id = "prefab_coin";
        coinPrefab.name = "CoinPrefab";
        project.prefabs.add(coinPrefab);

        // Player scripts
        GameScriptNode onStart = scene.getOrCreateScript(player.id, GameScriptNode.EVENT_SCENE_START, "");
        onStart.blocks.add(new GameBlockItem(null, "cameraFollow", "camera follow %s smoothness %s", " ", 0xFF4A6CD4, "Player", "0.15"));

        GameScriptNode onUpdate = scene.getOrCreateScript(player.id, GameScriptNode.EVENT_UPDATE, "");
        onUpdate.blocks.add(new GameBlockItem(null, "moveBy", "move by X %s Y %s", " ", 0xFF4A6CD4, "joyX * 6", "0"));

        GameBlockItem coinCheck = new GameBlockItem(null, "ifCollidingTag", "if colliding with tag %s", "c", 0xFFE6A100, "coin");
        coinCheck.subStack1.add(new GameBlockItem(null, "changeVar", "change %s by %s", " ", 0xFFEE7D16, "score", "10"));
        coinCheck.subStack1.add(new GameBlockItem(null, "setHudText", "set %s text to %s", " ", 0xFF00A699, "ScoreText", "\"Score: \" + score"));
        coinCheck.subStack1.add(new GameBlockItem(null, "spawnParticles", "particles color %s count %s", " ", 0xFF9966FF, "#FFD54F", "14"));
        coinCheck.subStack1.add(new GameBlockItem(null, "destroyOther", "destroy collided object", " ", 0xFF5CB1D6));
        onUpdate.blocks.add(coinCheck);

        GameScriptNode onJump = scene.getOrCreateScript(player.id, GameScriptNode.EVENT_BUTTON, "JUMP");
        GameBlockItem groundCheck = new GameBlockItem(null, "if", "if %b then", "c", 0xFFE1A91A, "");
        groundCheck.setParamBlock(0, new GameBlockItem(null, "isOnGround", "is on ground?", "b", 0xFF5CB1D6));
        groundCheck.subStack1.add(new GameBlockItem(null, "jumpImpulse", "jump force %s", " ", 0xFF4A6CD4, "580"));
        onJump.blocks.add(groundCheck);

        project.scenes.add(scene);
        return project;
    }
}
