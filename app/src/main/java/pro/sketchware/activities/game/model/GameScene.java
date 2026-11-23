package pro.sketchware.activities.game.model;

import java.util.ArrayList;
import java.util.List;
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

    /**
     * Removes the object with {@code objectId} from {@link #objects} and cascades deletion
     * to any {@link GameScriptNode} in {@link #scripts} targeting that object.
     *
     * @return the list of removed scripts (never null).
     */
    public List<GameScriptNode> removeObjectCascade(String objectId) {
        List<GameScriptNode> removedScripts = new ArrayList<>();
        if (objectId == null || objectId.isEmpty()) {
            return removedScripts;
        }
        if (objects != null) {
            for (int i = objects.size() - 1; i >= 0; i--) {
                GameObjectBean obj = objects.get(i);
                if (obj != null && objectId.equals(obj.id)) {
                    objects.remove(i);
                }
            }
        }
        if (scripts != null) {
            for (int i = scripts.size() - 1; i >= 0; i--) {
                GameScriptNode node = scripts.get(i);
                if (node != null && objectId.equals(node.targetObjectId)) {
                    removedScripts.add(scripts.remove(i));
                }
            }
        }
        return removedScripts;
    }

    /**
     * Computes the highest {@code zIndex} scoped to either the HUD layer or the World layer,
     * preventing World sprites from climbing above HUD widgets.
     */
    public int computeTopZIndex(boolean forHud) {
        int maxZ = forHud ? 99 : 0;
        if (objects != null) {
            for (GameObjectBean obj : objects) {
                if (obj == null) {
                    continue;
                }
                boolean objIsHud = obj.isHud || obj.isHudType();
                if (objIsHud == forHud && obj.zIndex > maxZ) {
                    maxZ = obj.zIndex;
                }
            }
        }
        return maxZ;
    }

    /**
     * Ensures {@code candidate} has a unique {@code id} and {@code name} within this scene.
     */
    public void ensureUniqueIdentity(GameObjectBean candidate) {
        if (candidate == null) {
            return;
        }
        candidate.ensureInitialized();
        String baseId = candidate.id;
        String candidateId = baseId;
        int suffix = 1;
        while (hasObjectWithId(candidateId, candidate)) {
            candidateId = baseId + "_" + suffix;
            suffix++;
        }
        candidate.id = candidateId;

        String baseName = candidate.name;
        String candidateName = baseName;
        int nameSuffix = 2;
        while (hasObjectWithName(candidateName, candidate)) {
            candidateName = baseName + "_" + nameSuffix;
            nameSuffix++;
        }
        candidate.name = candidateName;
    }

    /**
     * Clones {@code source} with unique scene ID/name, layer-scoped Z-index, and optional
     * {@code prefabId} link (when instantiating a saved prefab at design time).
     */
    public GameObjectBean cloneObjectWithUniqueIdentity(
            GameObjectBean source, float offsetX, float offsetY, boolean linkToPrefab) {
        GameObjectBean copy = source.copy();
        copy.ensureInitialized();
        if (linkToPrefab && source.id != null && !source.id.isEmpty()) {
            copy.prefabId = source.id;
        }
        copy.x += offsetX;
        copy.y += offsetY;
        boolean forHud = copy.isHud || copy.isHudType();
        if (!GameObjectBean.TYPE_PARALLAX_BG.equals(copy.type)) {
            copy.zIndex = computeTopZIndex(forHud) + 1;
        }
        // Force a fresh ID/name distinct from existing scene objects and source
        String baseId = copy.id;
        int idIdx = 1;
        String candId = baseId + "_" + idIdx;
        while (hasObjectWithId(candId, null) || candId.equals(source.id)) {
            idIdx++;
            candId = baseId + "_" + idIdx;
        }
        copy.id = candId;

        String baseName = copy.name;
        int nameIdx = 1;
        String candName = baseName;
        while (hasObjectWithName(candName, null)) {
            candName = baseName + "_" + nameIdx;
            nameIdx++;
        }
        copy.name = candName;
        return copy;
    }

    private boolean hasObjectWithId(String id, GameObjectBean ignore) {
        if (objects == null || id == null) {
            return false;
        }
        for (GameObjectBean o : objects) {
            if (o != null && o != ignore && id.equals(o.id)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasObjectWithName(String name, GameObjectBean ignore) {
        if (objects == null || name == null) {
            return false;
        }
        for (GameObjectBean o : objects) {
            if (o != null && o != ignore && o.name != null && name.equalsIgnoreCase(o.name)) {
                return true;
            }
        }
        return false;
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
