package pro.sketchware.activities.game.model;

import java.util.UUID;

/**
 * Represents a 2D game entity or HUD widget inside a {@link GameScene}.
 */
public class GameObjectBean {
    public static final String TYPE_SPRITE = "sprite";
    public static final String TYPE_ANIMATED_SPRITE = "animated_sprite";
    public static final String TYPE_PLATFORM = "platform";
    public static final String TYPE_PHYSICS_BOX = "physics_box";
    public static final String TYPE_TRIGGER = "trigger";
    public static final String TYPE_PARTICLES = "particles";
    public static final String TYPE_CAMERA = "camera";
    public static final String TYPE_HUD_TEXT = "hud_text";
    public static final String TYPE_HUD_BUTTON = "hud_button";
    public static final String TYPE_HUD_JOYSTICK = "hud_joystick";
    public static final String TYPE_HUD_BAR = "hud_bar";

    public String id;
    public String name;
    public String type = TYPE_SPRITE;
    public boolean isHud = false;

    public float x = 100f;
    public float y = 100f;
    public float width = 96f;
    public float height = 96f;
    public float rotation = 0f;
    public float scaleX = 1f;
    public float scaleY = 1f;
    public float alpha = 1f;

    public int zIndex = 0;
    public int color = 0xFF4CAF50;

    public String spriteResName = "";
    public String soundResName = "";
    public String fontResName = "";

    public String text = "";
    public String actionKey = "JUMP";
    public String tag = "default";

    public boolean physicsEnabled = false;
    public boolean isStatic = false;
    public boolean isSensor = false;

    public float gravityScale = 1f;
    public float bounce = 0f;
    public float friction = 0.2f;
    public float velocityX = 0f;
    public float velocityY = 0f;

    public boolean visible = true;
    public boolean locked = false;

    public GameObjectBean() {
        this.id = "obj_" + UUID.randomUUID().toString().substring(0, 8);
        this.name = "Object";
    }

    /**
     * Ensures optional string fields are non-null after deserialization.
     */
    public void ensureInitialized() {
        if (id == null || id.isEmpty()) {
            id = "obj_" + UUID.randomUUID().toString().substring(0, 8);
        }
        if (name == null || name.isEmpty()) {
            name = "Object";
        }
        if (type == null || type.isEmpty()) {
            type = TYPE_SPRITE;
        }
        if (spriteResName == null) spriteResName = "";
        if (soundResName == null) soundResName = "";
        if (fontResName == null) fontResName = "";
        if (text == null) text = "";
        if (actionKey == null) actionKey = "JUMP";
        if (tag == null) tag = "default";
        if (isHudType()) {
            isHud = true;
        }
    }

    /**
     * Returns a full copy of this object, preserving its {@code id} so scene scripts remain linked.
     * Callers duplicating objects inside the same scene should assign a fresh {@code id}.
     */
    public GameObjectBean copy() {
        GameObjectBean clone = new GameObjectBean();
        clone.id = id;
        clone.name = name;
        clone.type = type;
        clone.isHud = isHud;
        clone.x = x;
        clone.y = y;
        clone.width = width;
        clone.height = height;
        clone.rotation = rotation;
        clone.scaleX = scaleX;
        clone.scaleY = scaleY;
        clone.alpha = alpha;
        clone.zIndex = zIndex;
        clone.color = color;
        clone.spriteResName = spriteResName;
        clone.soundResName = soundResName;
        clone.fontResName = fontResName;
        clone.text = text;
        clone.actionKey = actionKey;
        clone.tag = tag;
        clone.physicsEnabled = physicsEnabled;
        clone.isStatic = isStatic;
        clone.isSensor = isSensor;
        clone.gravityScale = gravityScale;
        clone.bounce = bounce;
        clone.friction = friction;
        clone.velocityX = velocityX;
        clone.velocityY = velocityY;
        clone.visible = visible;
        clone.locked = locked;
        return clone;
    }

    public boolean isHudType() {
        return TYPE_HUD_TEXT.equals(type)
                || TYPE_HUD_BUTTON.equals(type)
                || TYPE_HUD_JOYSTICK.equals(type)
                || TYPE_HUD_BAR.equals(type);
    }

    public static GameObjectBean createDefaultForType(String type, float x, float y, int index) {
        String safeType = (type == null || type.isEmpty()) ? TYPE_SPRITE : type;
        GameObjectBean obj = new GameObjectBean();
        obj.id = "obj_" + UUID.randomUUID().toString().substring(0, 8);
        obj.type = safeType;
        obj.x = x;
        obj.y = y;

        switch (safeType) {
            case TYPE_ANIMATED_SPRITE -> {
                obj.name = "AnimSprite" + index;
                obj.width = 96f;
                obj.height = 96f;
                obj.color = 0xFF00BCD4;
                obj.tag = "sprite";
            }
            case TYPE_PLATFORM -> {
                obj.name = "Platform" + index;
                obj.width = 320f;
                obj.height = 48f;
                obj.color = 0xFF455A64;
                obj.physicsEnabled = true;
                obj.isStatic = true;
                obj.friction = 0.25f;
                obj.tag = "ground";
            }
            case TYPE_PHYSICS_BOX -> {
                obj.name = "Box" + index;
                obj.width = 80f;
                obj.height = 80f;
                obj.color = 0xFFFF9800;
                obj.physicsEnabled = true;
                obj.isStatic = false;
                obj.bounce = 0.2f;
                obj.friction = 0.2f;
                obj.tag = "box";
            }
            case TYPE_TRIGGER -> {
                obj.name = "Trigger" + index;
                obj.width = 64f;
                obj.height = 64f;
                obj.color = 0xFFFFD54F;
                obj.physicsEnabled = true;
                obj.isStatic = true;
                obj.isSensor = true;
                obj.tag = "trigger";
            }
            case TYPE_PARTICLES -> {
                obj.name = "Particles" + index;
                obj.width = 80f;
                obj.height = 80f;
                obj.color = 0xFFE040FB;
                obj.tag = "fx";
            }
            case TYPE_CAMERA -> {
                obj.name = "Camera" + index;
                obj.width = 120f;
                obj.height = 80f;
                obj.color = 0xFF7C4DFF;
                obj.zIndex = 10;
                obj.tag = "camera";
            }
            case TYPE_HUD_TEXT -> {
                obj.name = "HudText" + index;
                obj.isHud = true;
                obj.width = 240f;
                obj.height = 56f;
                obj.color = 0xFFFFFFFF;
                obj.text = "Score: 0";
                obj.zIndex = 100;
                obj.tag = "hud";
            }
            case TYPE_HUD_BUTTON -> {
                obj.name = "HudButton" + index;
                obj.isHud = true;
                obj.width = 136f;
                obj.height = 136f;
                obj.color = 0xFF42A5F5;
                obj.text = "JUMP";
                obj.actionKey = "JUMP";
                obj.zIndex = 100;
                obj.tag = "hud";
            }
            case TYPE_HUD_JOYSTICK -> {
                obj.name = "Joystick" + index;
                obj.isHud = true;
                obj.width = 170f;
                obj.height = 170f;
                obj.color = 0xFF90A4AE;
                obj.actionKey = "MOVE";
                obj.zIndex = 100;
                obj.tag = "hud";
            }
            case TYPE_HUD_BAR -> {
                obj.name = "HealthBar" + index;
                obj.isHud = true;
                obj.width = 220f;
                obj.height = 32f;
                obj.color = 0xFF66BB6A;
                obj.text = "100/100";
                obj.zIndex = 100;
                obj.tag = "hud";
            }
            default -> {
                obj.type = TYPE_SPRITE;
                obj.name = "Sprite" + index;
                obj.width = 96f;
                obj.height = 96f;
                obj.color = 0xFF4CAF50;
                obj.tag = "sprite";
            }
        }
        return obj;
    }
}
