package pro.sketchware.activities.game.model;

import java.util.UUID;

/**
 * Represents a 2D game entity or HUD widget inside a {@link GameScene}.
 */
public class GameObjectBean {
    public static final String TYPE_SPRITE = "sprite";
    public static final String TYPE_ANIMATED_SPRITE = "animated_sprite";
    public static final String TYPE_PARALLAX_BG = "parallax_bg";
    public static final String TYPE_WORLD_TEXT = "world_text";
    public static final String TYPE_LIGHT_2D = "light_2d";
    public static final String TYPE_PLATFORM = "platform";
    public static final String TYPE_MOVING_PLATFORM = "moving_platform";
    public static final String TYPE_ONEWAY_PLATFORM = "oneway_platform";
    public static final String TYPE_PHYSICS_BOX = "physics_box";
    public static final String TYPE_PHYSICS_CIRCLE = "physics_circle";
    public static final String TYPE_TRIGGER = "trigger";
    public static final String TYPE_GOAL = "goal";
    public static final String TYPE_CHECKPOINT = "checkpoint";
    public static final String TYPE_DEATH_ZONE = "death_zone";
    public static final String TYPE_HAZARD = "hazard";
    public static final String TYPE_COLLECTIBLE = "collectible";
    public static final String TYPE_BOUNCE_PAD = "bounce_pad";
    public static final String TYPE_SPAWNER = "spawner";
    public static final String TYPE_PROJECTILE = "projectile";
    public static final String TYPE_PARTICLES = "particles";
    public static final String TYPE_CAMERA = "camera";
    public static final String TYPE_HUD_PANEL = "hud_panel";
    public static final String TYPE_HUD_TEXT = "hud_text";
    public static final String TYPE_HUD_IMAGE = "hud_image";
    public static final String TYPE_HUD_BUTTON = "hud_button";
    public static final String TYPE_HUD_DPAD = "hud_dpad";
    public static final String TYPE_HUD_JOYSTICK = "hud_joystick";
    public static final String TYPE_HUD_BAR = "hud_bar";
    public static final String TYPE_HUD_TOUCH_AREA = "hud_touch_area";
    public static final String TYPE_HUD_DIALOG = "hud_dialog";
    public static final String TYPE_HUD_TOGGLE = "hud_toggle";

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
    public boolean circleCollider = false;
    public boolean oneWay = false;

    public float gravityScale = 1f;
    public float bounce = 0f;
    public float friction = 0.2f;
    public float velocityX = 0f;
    public float velocityY = 0f;

    // Extended gameplay, parallax, spawner, animation & progression fields
    public float speed = 120f;
    public float moveX = 0f;
    public float moveY = 0f;
    public float parallaxX = 1f;
    public float parallaxY = 1f;
    public float damage = 1f;
    public float cooldownSec = 1.5f;
    public String prefabName = "";
    public String targetScene = "__next__";
    public String spriteFrames = "";
    public float animFps = 8f;
    public boolean destroyOffscreen = false;
    public boolean activeOnlyNearCamera = false;
    public boolean toggled = false;

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
        if (prefabName == null) prefabName = "";
        if (targetScene == null) targetScene = "__next__";
        if (spriteFrames == null) spriteFrames = "";
        if (animFps <= 0f) animFps = 8f;
        if (TYPE_PHYSICS_CIRCLE.equals(type)) {
            circleCollider = true;
        }
        if (TYPE_ONEWAY_PLATFORM.equals(type)) {
            oneWay = true;
        }
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
        clone.circleCollider = circleCollider;
        clone.oneWay = oneWay;
        clone.gravityScale = gravityScale;
        clone.bounce = bounce;
        clone.friction = friction;
        clone.velocityX = velocityX;
        clone.velocityY = velocityY;
        clone.speed = speed;
        clone.moveX = moveX;
        clone.moveY = moveY;
        clone.parallaxX = parallaxX;
        clone.parallaxY = parallaxY;
        clone.damage = damage;
        clone.cooldownSec = cooldownSec;
        clone.prefabName = prefabName;
        clone.targetScene = targetScene;
        clone.spriteFrames = spriteFrames;
        clone.animFps = animFps;
        clone.destroyOffscreen = destroyOffscreen;
        clone.activeOnlyNearCamera = activeOnlyNearCamera;
        clone.toggled = toggled;
        clone.visible = visible;
        clone.locked = locked;
        return clone;
    }

    public boolean isHudType() {
        return TYPE_HUD_PANEL.equals(type)
                || TYPE_HUD_TEXT.equals(type)
                || TYPE_HUD_IMAGE.equals(type)
                || TYPE_HUD_BUTTON.equals(type)
                || TYPE_HUD_DPAD.equals(type)
                || TYPE_HUD_JOYSTICK.equals(type)
                || TYPE_HUD_BAR.equals(type)
                || TYPE_HUD_TOUCH_AREA.equals(type)
                || TYPE_HUD_DIALOG.equals(type)
                || TYPE_HUD_TOGGLE.equals(type);
    }

    public static String getTypeDisplayName(String type) {
        if (type == null) return "Sprite";
        return switch (type) {
            case TYPE_SPRITE -> "2D Sprite";
            case TYPE_ANIMATED_SPRITE -> "Animated Sprite";
            case TYPE_PARALLAX_BG -> "Tiled / Parallax BG";
            case TYPE_WORLD_TEXT -> "World Text";
            case TYPE_LIGHT_2D -> "Light 2D";
            case TYPE_PLATFORM -> "Platform";
            case TYPE_MOVING_PLATFORM -> "Moving Platform";
            case TYPE_ONEWAY_PLATFORM -> "One-Way Platform";
            case TYPE_PHYSICS_BOX -> "Physics Box";
            case TYPE_PHYSICS_CIRCLE -> "Circle Body";
            case TYPE_TRIGGER -> "Trigger / Sensor";
            case TYPE_GOAL -> "Goal / Portal";
            case TYPE_CHECKPOINT -> "Checkpoint";
            case TYPE_DEATH_ZONE -> "Death Zone";
            case TYPE_HAZARD -> "Hazard";
            case TYPE_COLLECTIBLE -> "Collectible";
            case TYPE_BOUNCE_PAD -> "Bounce Pad";
            case TYPE_SPAWNER -> "Spawner";
            case TYPE_PROJECTILE -> "Projectile";
            case TYPE_PARTICLES -> "Particles";
            case TYPE_CAMERA -> "2D Camera";
            case TYPE_HUD_PANEL -> "HUD Panel";
            case TYPE_HUD_TEXT -> "HUD Text";
            case TYPE_HUD_IMAGE -> "HUD Image";
            case TYPE_HUD_BUTTON -> "HUD Button";
            case TYPE_HUD_DPAD -> "HUD D-Pad";
            case TYPE_HUD_JOYSTICK -> "HUD Joystick";
            case TYPE_HUD_BAR -> "HUD Bar";
            case TYPE_HUD_TOUCH_AREA -> "HUD Touch Area";
            case TYPE_HUD_DIALOG -> "HUD Dialog Box";
            case TYPE_HUD_TOGGLE -> "HUD Toggle";
            default -> type;
        };
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
                obj.animFps = 8f;
                obj.tag = "sprite";
            }
            case TYPE_PARALLAX_BG -> {
                obj.name = "TiledBg" + index;
                obj.x = 0f;
                obj.y = 0f;
                obj.width = 1280f;
                obj.height = 720f;
                obj.color = 0xFF1E293B;
                obj.parallaxX = 0.4f;
                obj.parallaxY = 0.4f;
                obj.moveX = 0f;
                obj.moveY = 0f;
                obj.zIndex = -50;
                obj.tag = "bg";
            }
            case TYPE_WORLD_TEXT -> {
                obj.name = "WorldText" + index;
                obj.width = 220f;
                obj.height = 52f;
                obj.color = 0xFFFFFFFF;
                obj.text = "Sign Text";
                obj.zIndex = 20;
                obj.tag = "text";
            }
            case TYPE_LIGHT_2D -> {
                obj.name = "Light" + index;
                obj.width = 320f;
                obj.height = 320f;
                obj.color = 0xFFFFEB3B;
                obj.alpha = 0.85f;
                obj.zIndex = 80;
                obj.tag = "light";
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
            case TYPE_MOVING_PLATFORM -> {
                obj.name = "MovingPlat" + index;
                obj.width = 220f;
                obj.height = 40f;
                obj.color = 0xFF0EA5E9;
                obj.physicsEnabled = true;
                obj.isStatic = true;
                obj.friction = 0.25f;
                obj.moveX = 240f;
                obj.moveY = 0f;
                obj.speed = 110f;
                obj.tag = "ground";
            }
            case TYPE_ONEWAY_PLATFORM -> {
                obj.name = "OneWayPlat" + index;
                obj.width = 240f;
                obj.height = 28f;
                obj.color = 0xFF2DD4BF;
                obj.physicsEnabled = true;
                obj.isStatic = true;
                obj.oneWay = true;
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
            case TYPE_PHYSICS_CIRCLE -> {
                obj.name = "Ball" + index;
                obj.width = 72f;
                obj.height = 72f;
                obj.color = 0xFFF97316;
                obj.physicsEnabled = true;
                obj.isStatic = false;
                obj.circleCollider = true;
                obj.bounce = 0.55f;
                obj.friction = 0.12f;
                obj.tag = "ball";
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
            case TYPE_GOAL -> {
                obj.name = "Goal" + index;
                obj.width = 84f;
                obj.height = 112f;
                obj.color = 0xFF10B981;
                obj.physicsEnabled = true;
                obj.isStatic = true;
                obj.isSensor = true;
                obj.targetScene = "__next__";
                obj.zIndex = 6;
                obj.tag = "goal";
            }
            case TYPE_CHECKPOINT -> {
                obj.name = "Checkpoint" + index;
                obj.width = 64f;
                obj.height = 88f;
                obj.color = 0xFF38BDF8;
                obj.physicsEnabled = true;
                obj.isStatic = true;
                obj.isSensor = true;
                obj.zIndex = 5;
                obj.tag = "checkpoint";
            }
            case TYPE_DEATH_ZONE -> {
                obj.name = "DeathZone" + index;
                obj.width = 640f;
                obj.height = 48f;
                obj.color = 0xFFEF4444;
                obj.physicsEnabled = true;
                obj.isStatic = true;
                obj.isSensor = true;
                obj.zIndex = 4;
                obj.tag = "death";
            }
            case TYPE_HAZARD -> {
                obj.name = "Hazard" + index;
                obj.width = 72f;
                obj.height = 48f;
                obj.color = 0xFFF43F5E;
                obj.physicsEnabled = true;
                obj.isStatic = true;
                obj.isSensor = true;
                obj.damage = 1f;
                obj.zIndex = 6;
                obj.tag = "enemy";
            }
            case TYPE_COLLECTIBLE -> {
                obj.name = "Coin" + index;
                obj.width = 52f;
                obj.height = 52f;
                obj.color = 0xFFFACC15;
                obj.physicsEnabled = true;
                obj.isStatic = true;
                obj.isSensor = true;
                obj.circleCollider = true;
                obj.damage = 10f;
                obj.zIndex = 5;
                obj.tag = "coin";
            }
            case TYPE_BOUNCE_PAD -> {
                obj.name = "BouncePad" + index;
                obj.width = 96f;
                obj.height = 36f;
                obj.color = 0xFFA855F7;
                obj.physicsEnabled = true;
                obj.isStatic = true;
                obj.speed = 160f;
                obj.zIndex = 4;
                obj.tag = "bounce";
            }
            case TYPE_SPAWNER -> {
                obj.name = "Spawner" + index;
                obj.width = 360f;
                obj.height = 48f;
                obj.color = 0xFF8B5CF6;
                obj.cooldownSec = 1.5f;
                obj.prefabName = "";
                obj.zIndex = 8;
                obj.tag = "spawner";
            }
            case TYPE_PROJECTILE -> {
                obj.name = "Bullet" + index;
                obj.width = 36f;
                obj.height = 24f;
                obj.color = 0xFF38BDF8;
                obj.physicsEnabled = true;
                obj.isStatic = false;
                obj.isSensor = true;
                obj.gravityScale = 0f;
                obj.speed = 520f;
                obj.moveX = 0f;
                obj.moveY = -520f;
                obj.destroyOffscreen = true;
                obj.damage = 1f;
                obj.zIndex = 12;
                obj.tag = "bullet";
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
            case TYPE_HUD_PANEL -> {
                obj.name = "HudPanel" + index;
                obj.isHud = true;
                obj.width = 360f;
                obj.height = 220f;
                obj.color = 0xFF1E293B;
                obj.alpha = 0.88f;
                obj.zIndex = 95;
                obj.tag = "hud";
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
            case TYPE_HUD_IMAGE -> {
                obj.name = "HudImage" + index;
                obj.isHud = true;
                obj.width = 80f;
                obj.height = 80f;
                obj.color = 0xFF60A5FA;
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
            case TYPE_HUD_DPAD -> {
                obj.name = "DPad" + index;
                obj.isHud = true;
                obj.width = 190f;
                obj.height = 190f;
                obj.color = 0xFF64748B;
                obj.actionKey = "MOVE";
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
            case TYPE_HUD_TOUCH_AREA -> {
                obj.name = "TouchArea" + index;
                obj.isHud = true;
                obj.width = 480f;
                obj.height = 360f;
                obj.color = 0xFF06B6D4;
                obj.alpha = 0.15f;
                obj.actionKey = "FIRE";
                obj.zIndex = 98;
                obj.tag = "hud";
            }
            case TYPE_HUD_DIALOG -> {
                obj.name = "DialogBox" + index;
                obj.isHud = true;
                obj.width = 880f;
                obj.height = 170f;
                obj.color = 0xFF0F172A;
                obj.alpha = 0.92f;
                obj.actionKey = "NPC";
                obj.text = "Hello, hero! Tap to continue...";
                obj.visible = false;
                obj.zIndex = 120;
                obj.tag = "hud";
            }
            case TYPE_HUD_TOGGLE -> {
                obj.name = "HudToggle" + index;
                obj.isHud = true;
                obj.width = 180f;
                obj.height = 56f;
                obj.color = 0xFF22C55E;
                obj.text = "SOUND";
                obj.actionKey = "TOGGLE_SOUND";
                obj.toggled = true;
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
