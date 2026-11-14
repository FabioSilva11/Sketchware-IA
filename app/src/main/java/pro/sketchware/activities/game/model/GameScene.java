package pro.sketchware.activities.game.model;

import java.util.ArrayList;
import java.util.Objects;

/**
 * Single playable scene/level containing 2D objects, physics settings, and event scripts.
 */
public class GameScene {
    public static final String WORLD_BOUNDS_FREE = "free";
    public static final String WORLD_BOUNDS_SOLID = "solid";
    public static final String WORLD_BOUNDS_RESPAWN_BOTTOM = "respawn_bottom";
    public static final String WORLD_BOUNDS_RESTART_BOTTOM = "restart_bottom";

    public String id;
    public String name;
    public int backgroundColor = 0xFF141A24;
    public String backgroundSprite = "";
    public String bgmSound = "";
    public float gravityY = 980f;
    public int worldWidth = 0;
    public int worldHeight = 0;
    public String worldBoundsMode = WORLD_BOUNDS_FREE;
    public float ambientDarkness = 0f;
    public ArrayList<GameObjectBean> objects = new ArrayList<>();
    public ArrayList<GameScriptNode> scripts = new ArrayList<>();

    public GameScene() {
        this.id = "scene_1";
        this.name = "Scene 1";
    }

    public GameScene(String id, String name) {
        this.id = (id == null || id.isEmpty()) ? "scene_1" : id;
        this.name = (name == null || name.isEmpty()) ? "Scene 1" : name;
    }

    public void ensureInitialized() {
        if (id == null || id.isEmpty()) {
            id = "scene_1";
        }
        if (name == null || name.isEmpty()) {
            name = "Scene 1";
        }
        if (backgroundSprite == null) {
            backgroundSprite = "";
        }
        if (bgmSound == null) {
            bgmSound = "";
        }
        if (worldBoundsMode == null || worldBoundsMode.isEmpty()) {
            worldBoundsMode = WORLD_BOUNDS_FREE;
        }
        if (ambientDarkness < 0f) {
            ambientDarkness = 0f;
        } else if (ambientDarkness > 0.95f) {
            ambientDarkness = 0.95f;
        }
        if (objects == null) {
            objects = new ArrayList<>();
        } else {
            for (GameObjectBean obj : objects) {
                if (obj != null) {
                    obj.ensureInitialized();
                }
            }
        }
        if (scripts == null) {
            scripts = new ArrayList<>();
        } else {
            for (GameScriptNode script : scripts) {
                if (script != null) {
                    script.ensureInitialized();
                }
            }
        }
    }

    public GameObjectBean findObjectById(String id) {
        if (id == null || objects == null) {
            return null;
        }
        for (GameObjectBean obj : objects) {
            if (obj != null && id.equals(obj.id)) {
                return obj;
            }
        }
        return null;
    }

    public GameObjectBean findObjectByName(String name) {
        if (name == null || objects == null) {
            return null;
        }
        for (GameObjectBean obj : objects) {
            if (obj != null && name.equals(obj.name)) {
                return obj;
            }
        }
        return null;
    }

    public GameScriptNode getOrCreateScript(String targetObjectId, String eventType, String eventParam) {
        if (scripts == null) {
            scripts = new ArrayList<>();
        }
        String safeTarget = (targetObjectId == null || targetObjectId.isEmpty())
                ? GameScriptNode.SCENE_TARGET_ID : targetObjectId;
        String safeEvent = (eventType == null || eventType.isEmpty())
                ? GameScriptNode.EVENT_SCENE_START : eventType;
        String safeParam = eventParam == null ? "" : eventParam;

        for (GameScriptNode node : scripts) {
            if (node != null
                    && Objects.equals(safeTarget, node.targetObjectId)
                    && Objects.equals(safeEvent, node.eventType)
                    && Objects.equals(safeParam, node.eventParam == null ? "" : node.eventParam)) {
                return node;
            }
        }
        GameScriptNode created = new GameScriptNode(safeTarget, safeEvent, safeParam);
        scripts.add(created);
        return created;
    }

    public int getEffectiveWorldWidth(GameProject project) {
        int vw = (project != null && project.virtualWidth > 0) ? project.virtualWidth : 1280;
        return worldWidth > 0 ? Math.max(vw, worldWidth) : vw;
    }

    public int getEffectiveWorldHeight(GameProject project) {
        int vh = (project != null && project.virtualHeight > 0) ? project.virtualHeight : 720;
        return worldHeight > 0 ? Math.max(vh, worldHeight) : vh;
    }

    public GameScene copy(String newId, String newName) {
        GameScene clone = new GameScene(newId, newName);
        clone.backgroundColor = backgroundColor;
        clone.backgroundSprite = backgroundSprite == null ? "" : backgroundSprite;
        clone.bgmSound = bgmSound == null ? "" : bgmSound;
        clone.gravityY = gravityY;
        clone.worldWidth = worldWidth;
        clone.worldHeight = worldHeight;
        clone.worldBoundsMode = worldBoundsMode == null ? WORLD_BOUNDS_FREE : worldBoundsMode;
        clone.ambientDarkness = ambientDarkness;
        if (objects != null) {
            for (GameObjectBean obj : objects) {
                if (obj != null) {
                    clone.objects.add(obj.copy());
                }
            }
        }
        if (scripts != null) {
            for (GameScriptNode script : scripts) {
                if (script != null) {
                    clone.scripts.add(script.copy());
                }
            }
        }
        return clone;
    }
}
