package pro.sketchware.activities.game.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Root 2D game project configuration containing scenes, prefabs, and global variables.
 */
public class GameProject {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public int schemaVersion = CURRENT_SCHEMA_VERSION;
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
        if (schemaVersion <= 0) {
            schemaVersion = CURRENT_SCHEMA_VERSION;
        }
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
        if (startSceneId == null || startSceneId.isEmpty() || getSceneById(startSceneId) == null) {
            startSceneId = scenes.get(0).id;
        }
    }

    /**
     * Removes a saved prefab by ID and cascades deletion to matching {@link #prefabScripts}.
     *
     * @return {@code true} if a prefab was removed.
     */
    public boolean removePrefab(String prefabId) {
        if (prefabId == null || prefabId.isEmpty() || prefabs == null) {
            return false;
        }
        boolean removed = false;
        for (int i = prefabs.size() - 1; i >= 0; i--) {
            GameObjectBean p = prefabs.get(i);
            if (p != null && prefabId.equals(p.id)) {
                prefabs.remove(i);
                removed = true;
            }
        }
        if (prefabScripts != null) {
            for (int i = prefabScripts.size() - 1; i >= 0; i--) {
                GameScriptNode s = prefabScripts.get(i);
                if (s != null && prefabId.equals(s.targetObjectId)) {
                    prefabScripts.remove(i);
                }
            }
        }
        return removed;
    }

    /**
     * Repairs {@link #startSceneId} if it pointed to the deleted scene and resets Goal targets
     * that referenced the deleted scene (by ID or display name) back to {@code "__next__"}.
     */
    public void cleanupDeletedSceneReferences(String deletedSceneId, String deletedSceneName) {
        String idTrim = deletedSceneId == null ? "" : deletedSceneId.trim();
        String nameTrim = deletedSceneName == null ? "" : deletedSceneName.trim();
        if (!idTrim.isEmpty() && idTrim.equals(startSceneId) || getSceneById(startSceneId) == null) {
            startSceneId = (scenes != null && !scenes.isEmpty() && scenes.get(0) != null)
                    ? scenes.get(0).id
                    : "";
        }
        if (idTrim.isEmpty() && nameTrim.isEmpty()) {
            return;
        }
        if (scenes != null) {
            for (GameScene s : scenes) {
                if (s != null) {
                    resetDeletedSceneTargets(s.objects, idTrim, nameTrim);
                }
            }
        }
        resetDeletedSceneTargets(prefabs, idTrim, nameTrim);
    }

    private static void resetDeletedSceneTargets(List<GameObjectBean> objs, String deletedId, String deletedName) {
        if (objs == null) {
            return;
        }
        for (GameObjectBean obj : objs) {
            if (obj == null || obj.targetScene == null) {
                continue;
            }
            String ref = obj.targetScene.trim();
            if ((!deletedId.isEmpty() && deletedId.equalsIgnoreCase(ref))
                    || (!deletedName.isEmpty() && deletedName.equalsIgnoreCase(ref))) {
                obj.targetScene = "__next__";
            }
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
     * Checks whether any scene (excluding {@code excludeSceneId} if provided) already uses {@code name}.
     */
    public boolean hasSceneWithName(String name, String excludeSceneId) {
        if (name == null || scenes == null) {
            return false;
        }
        String candidate = name.trim();
        if (candidate.isEmpty()) {
            return false;
        }
        for (GameScene scene : scenes) {
            if (scene == null || scene.name == null) {
                continue;
            }
            if (excludeSceneId != null && excludeSceneId.equals(scene.id)) {
                continue;
            }
            if (candidate.equalsIgnoreCase(scene.name.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Renames {@code targetScene} after validating non-empty unique name, and propagates the name
     * change to all Goal targets and scene-loading blocks across the project.
     *
     * @return {@code true} if the scene name is valid and applied (or unchanged); {@code false} if invalid or duplicate.
     */
    public boolean renameScene(GameScene targetScene, String rawNewName) {
        if (targetScene == null || rawNewName == null) {
            return false;
        }
        String newName = rawNewName.trim();
        if (newName.isEmpty()) {
            return false;
        }
        String oldName = targetScene.name == null ? "" : targetScene.name.trim();
        if (newName.equals(oldName)) {
            targetScene.name = newName;
            return true;
        }
        if (hasSceneWithName(newName, targetScene.id)) {
            return false;
        }
        targetScene.name = newName;
        if (!oldName.isEmpty()) {
            propagateSceneNameChange(oldName, newName);
        }
        return true;
    }

    private void propagateSceneNameChange(String oldName, String newName) {
        if (scenes != null) {
            for (GameScene s : scenes) {
                if (s == null) {
                    continue;
                }
                updateSceneRefsInObjects(s.objects, oldName, newName);
                updateSceneRefsInScripts(s.scripts, oldName, newName);
            }
        }
        updateSceneRefsInObjects(prefabs, oldName, newName);
        updateSceneRefsInScripts(prefabScripts, oldName, newName);
    }

    private static void updateSceneRefsInObjects(List<GameObjectBean> objs, String oldName, String newName) {
        if (objs == null) {
            return;
        }
        for (GameObjectBean obj : objs) {
            if (obj != null && obj.targetScene != null
                    && oldName.equalsIgnoreCase(obj.targetScene.trim())) {
                obj.targetScene = newName;
            }
        }
    }

    private static void updateSceneRefsInScripts(List<GameScriptNode> scripts, String oldName, String newName) {
        if (scripts == null) {
            return;
        }
        for (GameScriptNode node : scripts) {
            if (node != null) {
                updateSceneRefsInBlocks(node.blocks, oldName, newName);
                if (node.floatingStacks != null) {
                    for (ArrayList<GameBlockItem> stack : node.floatingStacks) {
                        updateSceneRefsInBlocks(stack, oldName, newName);
                    }
                }
            }
        }
    }

    private static void updateSceneRefsInBlocks(List<GameBlockItem> blocks, String oldName, String newName) {
        if (blocks == null) {
            return;
        }
        for (GameBlockItem b : blocks) {
            if (b == null) {
                continue;
            }
            if (("loadScene".equals(b.opCode) || "loadSceneWithTransition".equals(b.opCode))
                    && b.parameters != null && !b.parameters.isEmpty()) {
                String currentRef = b.parameters.get(0);
                if (currentRef != null && oldName.equalsIgnoreCase(currentRef.trim())) {
                    b.parameters.set(0, newName);
                }
            }
            updateSceneRefsInBlocks(b.paramBlocks, oldName, newName);
            updateSceneRefsInBlocks(b.subStack1, oldName, newName);
            updateSceneRefsInBlocks(b.subStack2, oldName, newName);
        }
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
