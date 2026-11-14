package pro.sketchware.activities.game.blocks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import pro.sketchware.activities.game.model.GameBlockItem;

/**
 * Catalog of all Scratch-style visual programming blocks available in the 2D Game Engine.
 * <p>
 * Block opCodes and parameter orders match the runtime interpreter and APK source generator.
 */
public final class GameBlockCatalog {

    public static final String KIND_NUMBER = "number";
    public static final String KIND_TEXT = "text";
    public static final String KIND_EXPR = "expr";
    public static final String KIND_SPRITE = "sprite";
    public static final String KIND_SOUND = "sound";
    public static final String KIND_SCENE = "scene";
    public static final String KIND_OBJECT = "object";
    public static final String KIND_HUD = "hud";
    public static final String KIND_PREFAB = "prefab";
    public static final String KIND_TAG = "tag";
    public static final String KIND_ACTION = "action";
    public static final String KIND_BOOL = "bool";
    public static final String KIND_COND = "cond";
    public static final String KIND_OP = "op";
    public static final String KIND_VAR = "var";
    public static final String KIND_VAR_REF = "var_ref";
    public static final String KIND_COLOR = "color";
    public static final String KIND_MATH_FN = "math_fn";
    public static final String KIND_PROP = "prop";
    public static final String KIND_LAYER = "layer";
    public static final String KIND_MESSAGE = "message";
    public static final String KIND_TRANSITION = "transition";

    public static final int COLOR_MOTION = 0xFF4C97FF;
    public static final int COLOR_LOOKS = 0xFF9966FF;
    public static final int COLOR_PHYSICS = 0xFF0D9488;
    public static final int COLOR_SOUND = 0xFFC04AC0;
    public static final int COLOR_CONTROL = 0xFFE1A91A;
    public static final int COLOR_SENSING = 0xFF5CB1D6;
    public static final int COLOR_OPERATORS = 0xFF59C059;
    public static final int COLOR_VARIABLES = 0xFFEE7D16;
    public static final int COLOR_HUD = 0xFF0288D1;
    public static final int COLOR_SCENES = 0xFFE85D4A;

    public static final class CategoryDef {
        public final String id;
        public final String title;
        public final int color;
        public final List<BlockDef> blocks;

        public CategoryDef(String id, String title, int color, List<BlockDef> blocks) {
            this.id = id;
            this.title = title;
            this.color = color;
            this.blocks = Collections.unmodifiableList(new ArrayList<>(blocks));
        }
    }

    public static final class BlockDef {
        public final String opCode;
        public final String spec;
        public final String type;
        public final int color;
        public final String[] defaultParams;
        public final String[] paramKinds;

        public BlockDef(String opCode, String spec, String type, int color,
                        String[] defaultParams, String[] paramKinds) {
            this.opCode = opCode;
            this.spec = spec;
            this.type = type;
            this.color = color;
            this.defaultParams = defaultParams != null ? defaultParams.clone() : new String[0];
            this.paramKinds = paramKinds != null ? paramKinds.clone() : new String[0];
        }

        public int getParamCount() {
            return defaultParams.length;
        }
    }

    private static final List<CategoryDef> CATEGORIES;
    private static final Map<String, BlockDef> BY_OPCODE = new LinkedHashMap<>();

    static {
        List<CategoryDef> cats = new ArrayList<>();

        cats.add(new CategoryDef("motion", "Motion", COLOR_MOTION, Arrays.asList(
                def("moveSteps", "move %d steps", " ", COLOR_MOTION,
                        arr("10"), arr(KIND_NUMBER)),
                def("rotateBy", "turn ↻ %d degrees", " ", COLOR_MOTION,
                        arr("15"), arr(KIND_NUMBER)),
                def("rotateLeftBy", "turn ↺ %d degrees", " ", COLOR_MOTION,
                        arr("15"), arr(KIND_NUMBER)),
                def("goToTarget", "go to %m.gameObject", " ", COLOR_MOTION,
                        arr("random"), arr(KIND_OBJECT)),
                def("setPos", "go to x: %d y: %d", " ", COLOR_MOTION,
                        arr("0", "0"), arr(KIND_NUMBER, KIND_NUMBER)),
                def("glideToXY", "glide %d secs to x: %d y: %d", " ", COLOR_MOTION,
                        arr("1", "0", "0"), arr(KIND_NUMBER, KIND_NUMBER, KIND_NUMBER)),
                def("moveBy", "move by x: %d y: %d", " ", COLOR_MOTION,
                        arr("0", "0"), arr(KIND_EXPR, KIND_EXPR)),
                def("changeX", "change x by %d", " ", COLOR_MOTION,
                        arr("10"), arr(KIND_NUMBER)),
                def("setX", "set x to %d", " ", COLOR_MOTION,
                        arr("0"), arr(KIND_NUMBER)),
                def("changeY", "change y by %d", " ", COLOR_MOTION,
                        arr("10"), arr(KIND_NUMBER)),
                def("setY", "set y to %d", " ", COLOR_MOTION,
                        arr("0"), arr(KIND_NUMBER)),
                def("setRotation", "point in direction %d", " ", COLOR_MOTION,
                        arr("0"), arr(KIND_NUMBER)),
                def("pointToward", "point towards %m.gameObject", " ", COLOR_MOTION,
                        arr("touch"), arr(KIND_OBJECT)),
                def("bounceOnEdge", "if on edge, bounce", " ", COLOR_MOTION,
                        arr(), arr()),
                def("setVel", "set velocity x: %d y: %d", " ", COLOR_MOTION,
                        arr("0", "0"), arr(KIND_EXPR, KIND_EXPR)),
                def("jumpImpulse", "jump with force %d", " ", COLOR_MOTION,
                        arr("580"), arr(KIND_NUMBER)),
                def("cameraFollow", "camera follow %m.gameObject smoothness %d", " ", COLOR_MOTION,
                        arr("Player", "0.15"), arr(KIND_OBJECT, KIND_NUMBER)),
                def("cameraShake", "shake camera force %d duration %d", " ", COLOR_MOTION,
                        arr("12", "0.25"), arr(KIND_NUMBER, KIND_NUMBER)),
                def("keepInsideWorld", "keep inside world edges", " ", COLOR_MOTION,
                        arr(), arr()),
                def("respawnAtCheckpoint", "respawn at checkpoint", " ", COLOR_MOTION,
                        arr(), arr()),
                def("getPosX", "x position", "d", COLOR_MOTION,
                        arr(), arr()),
                def("getPosY", "y position", "d", COLOR_MOTION,
                        arr(), arr()),
                def("getVelX", "x velocity", "d", COLOR_MOTION,
                        arr(), arr()),
                def("getVelY", "y velocity", "d", COLOR_MOTION,
                        arr(), arr()),
                def("getDirection", "direction", "d", COLOR_MOTION,
                        arr(), arr()),
                def("getWorldWidth", "world width", "d", COLOR_MOTION,
                        arr(), arr()),
                def("getWorldHeight", "world height", "d", COLOR_MOTION,
                        arr(), arr())
        )));

        cats.add(new CategoryDef("looks", "Looks", COLOR_LOOKS, Arrays.asList(
                def("sayForSecs", "say %s for %d seconds", " ", COLOR_LOOKS,
                        arr("Hello!", "2"), arr(KIND_TEXT, KIND_NUMBER)),
                def("sayText", "say %s", " ", COLOR_LOOKS,
                        arr("Hello!"), arr(KIND_TEXT)),
                def("setSprite", "switch costume to %m.sprite", " ", COLOR_LOOKS,
                        arr(""), arr(KIND_SPRITE)),
                def("setBackdrop", "switch backdrop to %m.backdrop", " ", COLOR_LOOKS,
                        arr(""), arr(KIND_SPRITE)),
                def("changeSize", "change size by %d", " ", COLOR_LOOKS,
                        arr("10"), arr(KIND_NUMBER)),
                def("setSize", "set size to %d %", " ", COLOR_LOOKS,
                        arr("100"), arr(KIND_NUMBER)),
                def("setScale", "set scale x: %d y: %d", " ", COLOR_LOOKS,
                        arr("1", "1"), arr(KIND_NUMBER, KIND_NUMBER)),
                def("setColor", "set color %m.color", " ", COLOR_LOOKS,
                        arr("#FF5252"), arr(KIND_COLOR)),
                def("setAlpha", "set opacity %d", " ", COLOR_LOOKS,
                        arr("1.0"), arr(KIND_NUMBER)),
                def("clearEffects", "clear graphic effects", " ", COLOR_LOOKS,
                        arr(), arr()),
                def("show", "show", " ", COLOR_LOOKS,
                        arr(), arr()),
                def("hide", "hide", " ", COLOR_LOOKS,
                        arr(), arr()),
                def("setVisible", "set visible %m.bool", " ", COLOR_LOOKS,
                        arr("true"), arr(KIND_BOOL)),
                def("setZIndex", "go to %m.layer layer", " ", COLOR_LOOKS,
                        arr("front"), arr(KIND_LAYER)),
                def("changeZIndex", "go forward %d layers", " ", COLOR_LOOKS,
                        arr("1"), arr(KIND_NUMBER)),
                def("spawnParticles", "spawn particles color %m.color count %d", " ", COLOR_LOOKS,
                        arr("#FFD54F", "12"), arr(KIND_COLOR, KIND_NUMBER)),
                def("setAnimFrames", "set anim frames %s fps %d", " ", COLOR_LOOKS,
                        arr("frame1,frame2", "8"), arr(KIND_TEXT, KIND_NUMBER)),
                def("getSize", "size", "d", COLOR_LOOKS,
                        arr(), arr()),
                def("getSpriteName", "costume name", "d", COLOR_LOOKS,
                        arr(), arr())
        )));

        cats.add(new CategoryDef("physics", "Physics & Collision", COLOR_PHYSICS, Arrays.asList(
                def("setPhysicsEnabled", "set physics enabled %m.bool", " ", COLOR_PHYSICS,
                        arr("true"), arr(KIND_BOOL)),
                def("setGravityScale", "set gravity scale %d", " ", COLOR_PHYSICS,
                        arr("1.0"), arr(KIND_NUMBER)),
                def("setBounce", "set bounciness %d", " ", COLOR_PHYSICS,
                        arr("0.0"), arr(KIND_NUMBER)),
                def("setCircleCollider", "set circle collider %m.bool", " ", COLOR_PHYSICS,
                        arr("true"), arr(KIND_BOOL)),
                def("setOneWay", "set one-way platform %m.bool", " ", COLOR_PHYSICS,
                        arr("true"), arr(KIND_BOOL)),
                def("ifCollidingTag", "if colliding with tag %m.tag", "c", COLOR_PHYSICS,
                        arr("coin"), arr(KIND_TAG)),
                def("ifOnGround", "if on ground", "c", COLOR_PHYSICS,
                        arr(), arr())
        )));

        cats.add(new CategoryDef("sound", "Sound", COLOR_SOUND, Arrays.asList(
                def("playSound", "play sound %m.sound", " ", COLOR_SOUND,
                        arr(""), arr(KIND_SOUND)),
                def("playBgm", "play music %m.sound loop %m.bool", " ", COLOR_SOUND,
                        arr("", "true"), arr(KIND_SOUND, KIND_BOOL)),
                def("stopBgm", "stop all sounds", " ", COLOR_SOUND,
                        arr(), arr()),
                def("changeVolume", "change volume by %d", " ", COLOR_SOUND,
                        arr("-10"), arr(KIND_NUMBER)),
                def("setVolume", "set volume to %d %", " ", COLOR_SOUND,
                        arr("100"), arr(KIND_NUMBER)),
                def("getVolume", "volume", "d", COLOR_SOUND,
                        arr(), arr())
        )));

        cats.add(new CategoryDef("control", "Control", COLOR_CONTROL, Arrays.asList(
                def("waitSeconds", "wait %d seconds", " ", COLOR_CONTROL,
                        arr("1.0"), arr(KIND_NUMBER)),
                def("repeat", "repeat %d", "c", COLOR_CONTROL,
                        arr("5"), arr(KIND_NUMBER)),
                def("forever", "forever", "c", COLOR_CONTROL,
                        arr(), arr()),
                def("if", "if %b then", "c", COLOR_CONTROL,
                        arr(""), arr(KIND_COND)),
                def("ifElse", "if %b then", "e", COLOR_CONTROL,
                        arr(""), arr(KIND_COND)),
                def("waitUntil", "wait until %b", " ", COLOR_CONTROL,
                        arr(""), arr(KIND_COND)),
                def("repeatUntil", "repeat until %b", "c", COLOR_CONTROL,
                        arr(""), arr(KIND_COND)),
                def("createClone", "create clone of %m.gameObject", " ", COLOR_CONTROL,
                        arr("myself"), arr(KIND_OBJECT)),
                def("deleteClone", "delete this clone", "f", COLOR_CONTROL,
                        arr(), arr()),
                def("stopScript", "stop this script", "f", COLOR_CONTROL,
                        arr(), arr())
        )));

        cats.add(new CategoryDef("sensing", "Sensing", COLOR_SENSING, Arrays.asList(
                def("touchingButton", "button %m.action pressed?", "b", COLOR_SENSING,
                        arr("JUMP"), arr(KIND_ACTION)),
                def("isActionJustPressed", "button %m.action just pressed?", "b", COLOR_SENSING,
                        arr("FIRE"), arr(KIND_ACTION)),
                def("isJoystickActive", "joystick active?", "b", COLOR_SENSING,
                        arr(), arr()),
                def("isTouchingScreen", "screen touched?", "b", COLOR_SENSING,
                        arr(), arr()),
                def("touchingTag", "touching tag %m.tag ?", "b", COLOR_SENSING,
                        arr("coin"), arr(KIND_TAG)),
                def("touchingObject", "touching %m.gameObject ?", "b", COLOR_SENSING,
                        arr("touch"), arr(KIND_OBJECT)),
                def("isOnGround", "on ground?", "b", COLOR_SENSING,
                        arr(), arr()),
                def("isOutsideWorld", "outside world?", "b", COLOR_SENSING,
                        arr(), arr()),
                def("hasLeftScreen", "left screen?", "b", COLOR_SENSING,
                        arr(), arr()),
                def("joyX", "joystick x", "d", COLOR_SENSING,
                        arr(), arr()),
                def("joyY", "joystick y", "d", COLOR_SENSING,
                        arr(), arr()),
                def("joyAngle", "joystick angle", "d", COLOR_SENSING,
                        arr(), arr()),
                def("touchX", "touch x", "d", COLOR_SENSING,
                        arr(), arr()),
                def("touchY", "touch y", "d", COLOR_SENSING,
                        arr(), arr()),
                def("distanceTo", "distance to %m.gameObject", "d", COLOR_SENSING,
                        arr("touch"), arr(KIND_OBJECT)),
                def("timer", "timer", "d", COLOR_SENSING,
                        arr(), arr()),
                def("resetTimer", "reset timer", " ", COLOR_SENSING,
                        arr(), arr()),
                def("propOf", "%m.prop of %m.gameObject", "d", COLOR_SENSING,
                        arr("x", "Player"), arr(KIND_PROP, KIND_OBJECT))
        )));

        cats.add(new CategoryDef("operators", "Operators", COLOR_OPERATORS, Arrays.asList(
                def("boolTrue", "true", "b", COLOR_OPERATORS,
                        arr(), arr()),
                def("boolFalse", "false", "b", COLOR_OPERATORS,
                        arr(), arr()),
                def("opAdd", "%d + %d", "d", COLOR_OPERATORS,
                        arr("0", "0"), arr(KIND_NUMBER, KIND_NUMBER)),
                def("opSub", "%d - %d", "d", COLOR_OPERATORS,
                        arr("0", "0"), arr(KIND_NUMBER, KIND_NUMBER)),
                def("opMul", "%d * %d", "d", COLOR_OPERATORS,
                        arr("1", "1"), arr(KIND_NUMBER, KIND_NUMBER)),
                def("opDiv", "%d / %d", "d", COLOR_OPERATORS,
                        arr("1", "1"), arr(KIND_NUMBER, KIND_NUMBER)),
                def("opRandom", "pick random %d to %d", "d", COLOR_OPERATORS,
                        arr("1", "10"), arr(KIND_NUMBER, KIND_NUMBER)),
                def("opGt", "%d > %d", "b", COLOR_OPERATORS,
                        arr("0", "50"), arr(KIND_EXPR, KIND_EXPR)),
                def("opLt", "%d < %d", "b", COLOR_OPERATORS,
                        arr("0", "50"), arr(KIND_EXPR, KIND_EXPR)),
                def("opEq", "%s = %s", "b", COLOR_OPERATORS,
                        arr("0", "50"), arr(KIND_EXPR, KIND_EXPR)),
                def("opAnd", "%b and %b", "b", COLOR_OPERATORS,
                        arr("true", "true"), arr(KIND_COND, KIND_COND)),
                def("opOr", "%b or %b", "b", COLOR_OPERATORS,
                        arr("true", "false"), arr(KIND_COND, KIND_COND)),
                def("opNot", "not %b", "b", COLOR_OPERATORS,
                        arr("false"), arr(KIND_COND)),
                def("opJoin", "join %s %s", "d", COLOR_OPERATORS,
                        arr("Score: ", "0"), arr(KIND_TEXT, KIND_TEXT)),
                def("opLetterOf", "letter %d of %s", "d", COLOR_OPERATORS,
                        arr("1", "text"), arr(KIND_NUMBER, KIND_TEXT)),
                def("opLengthOf", "length of %s", "d", COLOR_OPERATORS,
                        arr("text"), arr(KIND_TEXT)),
                def("opContains", "%s contains %s ?", "b", COLOR_OPERATORS,
                        arr("text", "a"), arr(KIND_TEXT, KIND_TEXT)),
                def("opMod", "%d mod %d", "d", COLOR_OPERATORS,
                        arr("10", "3"), arr(KIND_NUMBER, KIND_NUMBER)),
                def("opRound", "round %d", "d", COLOR_OPERATORS,
                        arr("0"), arr(KIND_NUMBER)),
                def("opMathFn", "%m.mathfn of %d", "d", COLOR_OPERATORS,
                        arr("abs", "-10"), arr(KIND_MATH_FN, KIND_NUMBER)),
                def("opClamp", "clamp %d between %d and %d", "d", COLOR_OPERATORS,
                        arr("0", "0", "100"), arr(KIND_NUMBER, KIND_NUMBER, KIND_NUMBER))
        )));

        cats.add(new CategoryDef("variables", "Variables", COLOR_VARIABLES, Arrays.asList(
                def("getVar", "%s", "d", COLOR_VARIABLES,
                        arr("score"), arr(KIND_VAR_REF)),
                def("setVar", "set %m.gameVar to %s", " ", COLOR_VARIABLES,
                        arr("score", "0"), arr(KIND_VAR, KIND_EXPR)),
                def("changeVar", "change %m.gameVar by %d", " ", COLOR_VARIABLES,
                        arr("score", "1"), arr(KIND_VAR, KIND_NUMBER))
        )));

        cats.add(new CategoryDef("hud", "HUD & Buttons", COLOR_HUD, Arrays.asList(
                def("ifButtonPressed", "if button %m.action pressed", "c", COLOR_HUD,
                        arr("JUMP"), arr(KIND_ACTION)),
                def("setHudText", "set text of %m.hud to %s", " ", COLOR_HUD,
                        arr("ScoreText", "Score: {score}"), arr(KIND_HUD, KIND_TEXT)),
                def("setHudBar", "set bar %m.hud value to %d", " ", COLOR_HUD,
                        arr("HealthBar", "100"), arr(KIND_HUD, KIND_NUMBER)),
                def("showDialog", "show dialog %s speaker %s", " ", COLOR_HUD,
                        arr("Hello, hero!", "NPC"), arr(KIND_TEXT, KIND_TEXT)),
                def("hideDialog", "hide dialog", " ", COLOR_HUD,
                        arr(), arr())
        )));

        cats.add(new CategoryDef("scenes", "Scenes & Messages", COLOR_SCENES, Arrays.asList(
                def("sendMessage", "broadcast %m.message", " ", COLOR_SCENES,
                        arr("gameover"), arr(KIND_MESSAGE)),
                def("sendMessageWait", "broadcast %m.message and wait", " ", COLOR_SCENES,
                        arr("gameover"), arr(KIND_MESSAGE)),
                def("loadScene", "go to scene %m.scene", " ", COLOR_SCENES,
                        arr("Scene 1"), arr(KIND_SCENE)),
                def("loadSceneWithTransition", "go to scene %m.scene transition %m.transition", " ", COLOR_SCENES,
                        arr("Scene 1", "fade"), arr(KIND_SCENE, KIND_TRANSITION)),
                def("nextScene", "next scene", " ", COLOR_SCENES,
                        arr(), arr()),
                def("previousScene", "previous scene", " ", COLOR_SCENES,
                        arr(), arr()),
                def("restartScene", "restart scene", " ", COLOR_SCENES,
                        arr(), arr()),
                def("hasNextScene", "has next scene?", "b", COLOR_SCENES,
                        arr(), arr()),
                def("getSceneName", "current scene name", "d", COLOR_SCENES,
                        arr(), arr()),
                def("spawnPrefab", "spawn %m.prefab at x: %d y: %d", " ", COLOR_SCENES,
                        arr("CoinPrefab", "100", "100"), arr(KIND_PREFAB, KIND_EXPR, KIND_EXPR)),
                def("setDestroyOffscreen", "destroy offscreen %m.bool", " ", COLOR_SCENES,
                        arr("true"), arr(KIND_BOOL)),
                def("destroySelf", "destroy this object", "f", COLOR_SCENES,
                        arr(), arr()),
                def("destroyOther", "destroy collided object", " ", COLOR_SCENES,
                        arr(), arr()),
                def("saveGameData", "save data key %s value %s", " ", COLOR_SCENES,
                        arr("unlocked_level", "1"), arr(KIND_TEXT, KIND_EXPR)),
                def("loadGameData", "load data key %s default %s", "d", COLOR_SCENES,
                        arr("unlocked_level", "1"), arr(KIND_TEXT, KIND_TEXT))
        )));

        CATEGORIES = Collections.unmodifiableList(cats);
        for (CategoryDef cat : CATEGORIES) {
            for (BlockDef b : cat.blocks) {
                BY_OPCODE.put(b.opCode, b);
            }
        }
    }

    private GameBlockCatalog() {
    }

    private static BlockDef def(String opCode, String spec, String type, int color,
                                String[] defaults, String[] kinds) {
        return new BlockDef(opCode, spec, type, color, defaults, kinds);
    }

    private static String[] arr(String... values) {
        return values != null ? values : new String[0];
    }

    public static List<CategoryDef> getCategories() {
        return CATEGORIES;
    }

    public static BlockDef findByOpCode(String opCode) {
        if (opCode == null) {
            return null;
        }
        return BY_OPCODE.get(opCode);
    }

    public static GameBlockItem createBlockInstance(BlockDef def) {
        if (def == null) {
            return null;
        }
        String id = "blk_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String[] params = def.defaultParams != null ? def.defaultParams.clone() : new String[0];
        GameBlockItem item = new GameBlockItem(id, def.opCode, def.spec, def.type, def.color, params);
        if (item.parameters == null) {
            item.parameters = new ArrayList<>();
        }
        if (item.subStack1 == null) {
            item.subStack1 = new ArrayList<>();
        }
        if (item.subStack2 == null) {
            item.subStack2 = new ArrayList<>();
        }
        return item;
    }

    /**
     * Finds the earliest parameter placeholder (%s, %n, %b, %d, or %m) starting at {@code fromIndex}.
     */
    public static int findNextPlaceholder(String template, int fromIndex) {
        if (template == null || fromIndex >= template.length()) {
            return -1;
        }
        int best = -1;
        String[] tokens = {"%s", "%n", "%b", "%d", "%m"};
        for (String token : tokens) {
            int idx = template.indexOf(token, fromIndex);
            if (idx >= 0 && (best < 0 || idx < best)) {
                best = idx;
            }
        }
        return best;
    }

    public static int placeholderLength(String template, int placeholderStart) {
        if (template == null || placeholderStart < 0 || placeholderStart + 1 >= template.length()) {
            return 2;
        }
        if (template.startsWith("%m.", placeholderStart)) {
            int end = placeholderStart + 3;
            while (end < template.length()) {
                char ch = template.charAt(end);
                if (!Character.isLetterOrDigit(ch) && ch != '_') {
                    break;
                }
                end++;
            }
            return end - placeholderStart;
        }
        return 2;
    }

    public static int countPlaceholders(String template) {
        if (template == null || template.isEmpty()) {
            return 0;
        }
        int count = 0;
        int cursor = 0;
        while (cursor < template.length()) {
            int idx = findNextPlaceholder(template, cursor);
            if (idx < 0) {
                break;
            }
            count++;
            cursor = idx + placeholderLength(template, idx);
        }
        return count;
    }

    public static String formatBlockDisplay(GameBlockItem block) {
        if (block == null) {
            return "";
        }
        BlockDef def = findByOpCode(block.opCode);
        String template = block.spec;
        if (template == null || template.isEmpty()) {
            template = def != null ? def.spec : (block.opCode != null ? block.opCode : "");
        }
        List<String> params = block.parameters;
        StringBuilder out = new StringBuilder();
        int cursor = 0;
        int paramIdx = 0;
        while (cursor < template.length()) {
            int placeholder = findNextPlaceholder(template, cursor);
            if (placeholder < 0) {
                out.append(template.substring(cursor));
                break;
            }
            out.append(template, cursor, placeholder);
            GameBlockItem nested = block.getParamBlock(paramIdx);
            if (nested != null) {
                out.append("(").append(formatBlockDisplay(nested)).append(")");
            } else {
                String val = "";
                if (params != null && paramIdx < params.size() && params.get(paramIdx) != null) {
                    val = params.get(paramIdx);
                } else if (def != null && paramIdx < def.defaultParams.length) {
                    val = def.defaultParams[paramIdx];
                }
                out.append(val.isEmpty() ? "?" : val);
            }
            paramIdx++;
            cursor = placeholder + placeholderLength(template, placeholder);
        }
        return out.toString();
    }

    /**
     * Returns true if {@code block} is a hexagonal boolean reporter ("b").
     */
    public static boolean isBooleanBlock(GameBlockItem block) {
        return block != null && "b".equals(block.type);
    }

    /**
     * Returns true if {@code block} is a value reporter ("d", "n") or boolean ("b") that can nest.
     */
    public static boolean isReporterBlock(GameBlockItem block) {
        return block != null && ("d".equals(block.type) || "n".equals(block.type) || "b".equals(block.type));
    }

    /**
     * Returns true if {@code candidate} may be dropped inside a slot of parameter kind {@code targetKind}.
     */
    public static boolean canAcceptNestedBlock(String targetKind, GameBlockItem candidate) {
        if (!isReporterBlock(candidate)) {
            return false;
        }
        if (KIND_BOOL.equals(targetKind) || KIND_COND.equals(targetKind)) {
            return "b".equals(candidate.type);
        }
        return KIND_NUMBER.equals(targetKind)
                || KIND_EXPR.equals(targetKind)
                || KIND_TEXT.equals(targetKind);
    }

    /**
     * Converts a nested reporter or boolean block tree into an expression string
     * compatible with the runtime expression evaluator.
     */
    public static String toExpression(GameBlockItem block) {
        if (block == null || block.opCode == null) {
            return "0";
        }
        switch (block.opCode) {
            case "getVar": {
                String name = slotExpr(block, 0, "score");
                if (name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                    return name;
                }
                return "var(" + quoteExprStr(name) + ")";
            }
            case "getPosX":
                return "selfX";
            case "getPosY":
                return "selfY";
            case "getVelX":
                return "vx";
            case "getVelY":
                return "vy";
            case "getDirection":
                return "rot";
            case "getWorldWidth":
                return "worldWidth";
            case "getWorldHeight":
                return "worldHeight";
            case "getSceneName":
                return "sceneName";
            case "hasNextScene":
                return "hasNextScene";
            case "isOutsideWorld":
                return "outsideWorld";
            case "hasLeftScreen":
                return "leftScreen";
            case "loadGameData":
                return "loadSave(" + slotTextOrExpr(block, 0, "unlocked_level") + ", "
                        + slotTextOrExpr(block, 1, "1") + ")";
            case "isActionJustPressed":
                return "btnJust(" + quoteExprStr(slotRaw(block, 0, "FIRE")) + ")";
            case "getSize":
                return "size";
            case "getSpriteName":
                return "spriteName";
            case "getVolume":
                return "volume";
            case "joyX":
                return "joyX";
            case "joyY":
                return "joyY";
            case "joyAngle":
                return "joyAngle";
            case "touchX":
                return "touchX";
            case "touchY":
                return "touchY";
            case "timer":
                return "timer";
            case "isJoystickActive":
                return "joyActive";
            case "isTouchingScreen":
                return "touchDown";
            case "isOnGround":
                return "onGround";
            case "touchingButton":
                return "btn(" + quoteExprStr(slotRaw(block, 0, "JUMP")) + ")";
            case "touchingTag":
                return "touchingTag(" + quoteExprStr(slotRaw(block, 0, "coin")) + ")";
            case "touchingObject":
                return "touchingObj(" + quoteExprStr(slotRaw(block, 0, "touch")) + ")";
            case "distanceTo":
                return "dist(" + quoteExprStr(slotRaw(block, 0, "touch")) + ")";
            case "propOf":
                return "prop(" + quoteExprStr(slotRaw(block, 0, "x")) + ", "
                        + quoteExprStr(slotRaw(block, 1, "Player")) + ")";
            case "opAdd":
                return "(" + slotExpr(block, 0, "0") + " + " + slotExpr(block, 1, "0") + ")";
            case "opSub":
                return "(" + slotExpr(block, 0, "0") + " - " + slotExpr(block, 1, "0") + ")";
            case "opMul":
                return "(" + slotExpr(block, 0, "1") + " * " + slotExpr(block, 1, "1") + ")";
            case "opDiv":
                return "(" + slotExpr(block, 0, "1") + " / " + slotExpr(block, 1, "1") + ")";
            case "opMod":
                return "(" + slotExpr(block, 0, "10") + " % " + slotExpr(block, 1, "3") + ")";
            case "opRandom":
                return "random(" + slotExpr(block, 0, "1") + ", " + slotExpr(block, 1, "10") + ")";
            case "opRound":
                return "round(" + slotExpr(block, 0, "0") + ")";
            case "opClamp":
                return "clamp(" + slotExpr(block, 0, "0") + ", "
                        + slotExpr(block, 1, "0") + ", "
                        + slotExpr(block, 2, "100") + ")";
            case "opMathFn": {
                String fn = slotRaw(block, 0, "abs");
                if (fn.isEmpty()) {
                    fn = "abs";
                }
                return fn + "(" + slotExpr(block, 1, "0") + ")";
            }
            case "opGt":
                return "(" + slotExpr(block, 0, "0") + " > " + slotExpr(block, 1, "0") + ")";
            case "opLt":
                return "(" + slotExpr(block, 0, "0") + " < " + slotExpr(block, 1, "0") + ")";
            case "opEq":
                return "(" + slotExpr(block, 0, "0") + " == " + slotExpr(block, 1, "0") + ")";
            case "opAnd":
                return "(" + slotExpr(block, 0, "false") + " && " + slotExpr(block, 1, "false") + ")";
            case "opOr":
                return "(" + slotExpr(block, 0, "false") + " || " + slotExpr(block, 1, "false") + ")";
            case "opNot":
                return "!(" + slotExpr(block, 0, "false") + ")";
            case "boolTrue":
                return "true";
            case "boolFalse":
                return "false";
            case "opJoin":
                return "join(" + slotTextOrExpr(block, 0, "") + ", " + slotTextOrExpr(block, 1, "") + ")";
            case "opLetterOf":
                return "letterOf(" + slotExpr(block, 0, "1") + ", " + slotTextOrExpr(block, 1, "") + ")";
            case "opLengthOf":
                return "lengthOf(" + slotTextOrExpr(block, 0, "") + ")";
            case "opContains":
                return "contains(" + slotTextOrExpr(block, 0, "") + ", " + slotTextOrExpr(block, 1, "") + ")";
            default:
                return slotExpr(block, 0, "0");
        }
    }

    /**
     * Synchronizes {@code parameters} strings from any nested {@code paramBlocks} across
     * the entire block tree so display/export/fallback formulas match nested reporter trees.
     */
    public static void syncNestedExpressions(GameBlockItem block) {
        if (block == null) {
            return;
        }
        block.ensureInitialized();
        if (block.paramBlocks != null) {
            for (int i = 0; i < block.paramBlocks.size(); i++) {
                GameBlockItem child = block.paramBlocks.get(i);
                if (child != null) {
                    syncNestedExpressions(child);
                    while (block.parameters.size() <= i) {
                        block.parameters.add("");
                    }
                    block.parameters.set(i, toExpression(child));
                }
            }
        }
        if (block.subStack1 != null) {
            for (GameBlockItem child : block.subStack1) {
                syncNestedExpressions(child);
            }
        }
        if (block.subStack2 != null) {
            for (GameBlockItem child : block.subStack2) {
                syncNestedExpressions(child);
            }
        }
    }

    private static String slotRaw(GameBlockItem block, int idx, String fallback) {
        if (block.parameters != null && idx < block.parameters.size() && block.parameters.get(idx) != null) {
            return block.parameters.get(idx);
        }
        return fallback;
    }

    private static String slotExpr(GameBlockItem block, int idx, String fallback) {
        GameBlockItem nested = block.getParamBlock(idx);
        if (nested != null) {
            return toExpression(nested);
        }
        String raw = slotRaw(block, idx, fallback).trim();
        return raw.isEmpty() ? fallback : raw;
    }

    private static String slotTextOrExpr(GameBlockItem block, int idx, String fallback) {
        GameBlockItem nested = block.getParamBlock(idx);
        if (nested != null) {
            return toExpression(nested);
        }
        return quoteExprStr(slotRaw(block, idx, fallback));
    }

    private static String quoteExprStr(String value) {
        if (value == null) {
            return "\"\"";
        }
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }
}
