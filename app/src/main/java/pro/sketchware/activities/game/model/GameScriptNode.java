package pro.sketchware.activities.game.model;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Event-bound visual script attached to a scene or a specific {@link GameObjectBean}.
 */
public class GameScriptNode {
    public static final String EVENT_SCENE_START = "on_scene_start";
    public static final String EVENT_UPDATE = "on_update";
    public static final String EVENT_TOUCH = "on_touch";
    public static final String EVENT_COLLISION = "on_collision";
    public static final String EVENT_BUTTON = "on_button";
    public static final String EVENT_MESSAGE = "on_message";
    public static final String SCENE_TARGET_ID = "__scene__";

    public String id;
    public String targetObjectId = SCENE_TARGET_ID;
    public String eventType = EVENT_SCENE_START;
    public String eventParam = "";
    public ArrayList<GameBlockItem> blocks = new ArrayList<>();
    public transient ArrayList<ArrayList<GameBlockItem>> floatingStacks = new ArrayList<>();

    public GameScriptNode() {
        this.id = "scr_" + UUID.randomUUID().toString().substring(0, 8);
    }

    public GameScriptNode(String targetObjectId, String eventType, String eventParam) {
        this();
        this.targetObjectId = (targetObjectId == null || targetObjectId.isEmpty()) ? SCENE_TARGET_ID : targetObjectId;
        this.eventType = (eventType == null || eventType.isEmpty()) ? EVENT_SCENE_START : eventType;
        this.eventParam = eventParam == null ? "" : eventParam;
    }

    /**
     * Ensures nested collections and strings are non-null after deserialization.
     */
    public void ensureInitialized() {
        if (id == null || id.isEmpty()) {
            id = "scr_" + UUID.randomUUID().toString().substring(0, 8);
        }
        if (targetObjectId == null || targetObjectId.isEmpty()) {
            targetObjectId = SCENE_TARGET_ID;
        }
        if (eventType == null || eventType.isEmpty()) {
            eventType = EVENT_SCENE_START;
        }
        if (eventParam == null) {
            eventParam = "";
        }
        if (blocks == null) {
            blocks = new ArrayList<>();
        } else {
            for (GameBlockItem block : blocks) {
                if (block != null) {
                    block.ensureInitialized();
                }
            }
        }
        if (floatingStacks == null) {
            floatingStacks = new ArrayList<>();
        } else {
            ArrayList<ArrayList<GameBlockItem>> cleaned = new ArrayList<>();
            for (ArrayList<GameBlockItem> stack : floatingStacks) {
                if (stack != null && !stack.isEmpty()) {
                    ArrayList<GameBlockItem> stackClean = new ArrayList<>();
                    for (GameBlockItem block : stack) {
                        if (block != null) {
                            block.ensureInitialized();
                            stackClean.add(block);
                        }
                    }
                    if (!stackClean.isEmpty()) {
                        cleaned.add(stackClean);
                    }
                }
            }
            floatingStacks = cleaned;
        }
    }

    public GameScriptNode copy() {
        GameScriptNode clone = new GameScriptNode();
        clone.id = "scr_" + UUID.randomUUID().toString().substring(0, 8);
        clone.targetObjectId = targetObjectId;
        clone.eventType = eventType;
        clone.eventParam = eventParam;
        if (blocks != null) {
            for (GameBlockItem block : blocks) {
                if (block != null) {
                    clone.blocks.add(block.copy());
                }
            }
        }
        if (floatingStacks != null) {
            for (ArrayList<GameBlockItem> stack : floatingStacks) {
                if (stack != null && !stack.isEmpty()) {
                    ArrayList<GameBlockItem> stackCopy = new ArrayList<>();
                    for (GameBlockItem block : stack) {
                        if (block != null) {
                            stackCopy.add(block.copy());
                        }
                    }
                    if (!stackCopy.isEmpty()) {
                        clone.floatingStacks.add(stackCopy);
                    }
                }
            }
        }
        return clone;
    }
}
