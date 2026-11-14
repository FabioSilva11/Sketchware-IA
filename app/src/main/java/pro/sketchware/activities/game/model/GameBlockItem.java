package pro.sketchware.activities.game.model;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Visual script block item used inside a {@link GameScriptNode} block tree.
 */
public class GameBlockItem {
    public String id;
    public String opCode;
    public String spec;
    public String type = " ";
    public int color = 0xFF4A6CD4;
    public ArrayList<String> parameters = new ArrayList<>();
    public ArrayList<GameBlockItem> paramBlocks = new ArrayList<>();
    public ArrayList<GameBlockItem> subStack1 = new ArrayList<>();
    public ArrayList<GameBlockItem> subStack2 = new ArrayList<>();
    public int freeX = 0;
    public int freeY = 0;

    public GameBlockItem() {
        this.id = UUID.randomUUID().toString();
        this.opCode = "";
        this.spec = "";
    }

    public GameBlockItem(String id, String opCode, String spec, String type, int color, String... params) {
        this.id = (id == null || id.isEmpty()) ? UUID.randomUUID().toString() : id;
        this.opCode = opCode == null ? "" : opCode;
        this.spec = spec == null ? "" : spec;
        this.type = (type == null || type.isEmpty()) ? " " : type;
        this.color = color;
        if (params != null) {
            for (String p : params) {
                this.parameters.add(p == null ? "" : p);
            }
        }
    }

    /**
     * Ensures nested collections and identifiers are non-null after deserialization.
     */
    public void ensureInitialized() {
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
        }
        if (opCode == null) {
            opCode = "";
        }
        if (spec == null) {
            spec = "";
        }
        if (type == null || type.isEmpty()) {
            type = " ";
        }
        if (parameters == null) {
            parameters = new ArrayList<>();
        }
        if (paramBlocks == null) {
            paramBlocks = new ArrayList<>();
        } else {
            for (GameBlockItem child : paramBlocks) {
                if (child != null) {
                    child.ensureInitialized();
                }
            }
        }
        if (subStack1 == null) {
            subStack1 = new ArrayList<>();
        } else {
            for (GameBlockItem child : subStack1) {
                if (child != null) {
                    child.ensureInitialized();
                }
            }
        }
        if (subStack2 == null) {
            subStack2 = new ArrayList<>();
        } else {
            for (GameBlockItem child : subStack2) {
                if (child != null) {
                    child.ensureInitialized();
                }
            }
        }
    }

    /**
     * Returns the nested reporter or boolean block in parameter slot {@code index}, or null.
     */
    public GameBlockItem getParamBlock(int index) {
        if (paramBlocks == null || index < 0 || index >= paramBlocks.size()) {
            return null;
        }
        return paramBlocks.get(index);
    }

    /**
     * Assigns or clears the nested reporter or boolean block at parameter slot {@code index}.
     */
    public void setParamBlock(int index, GameBlockItem block) {
        if (index < 0) {
            return;
        }
        if (paramBlocks == null) {
            paramBlocks = new ArrayList<>();
        }
        while (paramBlocks.size() <= index) {
            paramBlocks.add(null);
        }
        paramBlocks.set(index, block);
    }

    /**
     * Creates a deep copy of this block and all nested sub-stacks with fresh block IDs.
     */
    public GameBlockItem copy() {
        GameBlockItem clone = new GameBlockItem();
        clone.id = UUID.randomUUID().toString();
        clone.opCode = opCode == null ? "" : opCode;
        clone.spec = spec == null ? "" : spec;
        clone.type = (type == null || type.isEmpty()) ? " " : type;
        clone.color = color;
        clone.freeX = freeX;
        clone.freeY = freeY;
        if (parameters != null) {
            clone.parameters.addAll(parameters);
        }
        if (paramBlocks != null) {
            for (GameBlockItem child : paramBlocks) {
                clone.paramBlocks.add(child != null ? child.copy() : null);
            }
        }
        if (subStack1 != null) {
            for (GameBlockItem child : subStack1) {
                if (child != null) {
                    clone.subStack1.add(child.copy());
                }
            }
        }
        if (subStack2 != null) {
            for (GameBlockItem child : subStack2) {
                if (child != null) {
                    clone.subStack2.add(child.copy());
                }
            }
        }
        return clone;
    }
}
