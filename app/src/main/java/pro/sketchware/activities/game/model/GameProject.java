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
    public ArrayList<GameScriptNode> prefabScripts = new ArrayList<>();
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
        if (prefabScripts == null) {
            prefabScripts = new ArrayList<>();
        } else {
            for (GameScriptNode script : prefabScripts) {
                if (script != null) {
                    script.ensureInitialized();
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
     * Creates an empty starter 2D game project with a blank "Scene 1"
     * so the user can freely choose and place any world/HUD objects.
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

        GameScene scene = new GameScene("scene_1", "Scene 1");
        scene.backgroundColor = 0xFF141A24;
        scene.gravityY = 980f;
        scene.worldWidth = 0;
        scene.worldHeight = 0;
        scene.worldBoundsMode = GameScene.WORLD_BOUNDS_FREE;

        project.scenes.add(scene);
        return project;
    }
}
