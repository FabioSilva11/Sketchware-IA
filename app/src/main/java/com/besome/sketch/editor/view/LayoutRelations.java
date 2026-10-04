package com.besome.sketch.editor.view;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.besome.sketch.beans.ViewBean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mod.agus.jcoderz.beans.ViewBeans;

/**
 * Positioning rules of children of RelativeLayout and ConstraintLayout, stored in
 * {@link ViewBean#parentAttributes} (ids without "@id/", "parent" for the parent). The visual editor,
 * the property panel and the AI generator all change positions through these rules.
 */
public final class LayoutRelations {

    public enum Side {TOP, BOTTOM, START, END}

    /** One drawn connection: {@code side} of the view is attached to {@code targetSide} of {@code target}. */
    public static final class Connection {
        public final Side side;
        /** "parent" or a sibling id. */
        public final String target;
        /** Null for centring rules (to the middle of the parent). */
        @Nullable
        public final Side targetSide;
        public final String attribute;

        Connection(Side side, String target, @Nullable Side targetSide, String attribute) {
            this.side = side;
            this.target = target;
            this.targetSide = targetSide;
            this.attribute = attribute;
        }
    }

    public static final String PARENT = "parent";
    private static final String R = "android:layout_";
    private static final String C = "app:layout_constraint";

    private LayoutRelations() {
    }

    public static boolean isRelative(int parentType) {
        return parentType == ViewBean.VIEW_TYPE_LAYOUT_RELATIVE;
    }

    public static boolean isConstraint(int parentType) {
        return parentType == ViewBeans.VIEW_TYPE_LAYOUT_CONSTRAINTLAYOUT;
    }

    public static boolean supports(@Nullable ViewBean bean) {
        return bean != null && bean.id != null && !bean.id.startsWith("_")
                && (isRelative(bean.parentType) || isConstraint(bean.parentType));
    }

    private static boolean isHorizontal(Side side) {
        return side == Side.START || side == Side.END;
    }

    /** Attributes that position {@code side}; any of them present means the side is connected. */
    private static String[] sideAttributes(boolean constraint, Side side) {
        if (constraint) {
            return switch (side) {
                case TOP -> new String[]{C + "Top_toTopOf", C + "Top_toBottomOf", C + "Baseline_toBaselineOf"};
                case BOTTOM -> new String[]{C + "Bottom_toTopOf", C + "Bottom_toBottomOf"};
                case START -> new String[]{C + "Start_toStartOf", C + "Start_toEndOf", C + "Left_toLeftOf", C + "Left_toRightOf"};
                case END -> new String[]{C + "End_toStartOf", C + "End_toEndOf", C + "Right_toLeftOf", C + "Right_toRightOf"};
            };
        }
        return switch (side) {
            case TOP -> new String[]{R + "alignParentTop", R + "below", R + "alignTop", R + "centerVertical",
                    R + "centerInParent", R + "alignBaseline"};
            case BOTTOM -> new String[]{R + "alignParentBottom", R + "above", R + "alignBottom", R + "centerVertical",
                    R + "centerInParent"};
            case START -> new String[]{R + "alignParentStart", R + "alignParentLeft", R + "toEndOf", R + "toRightOf",
                    R + "alignStart", R + "alignLeft", R + "centerHorizontal", R + "centerInParent"};
            case END -> new String[]{R + "alignParentEnd", R + "alignParentRight", R + "toStartOf", R + "toLeftOf",
                    R + "alignEnd", R + "alignRight", R + "centerHorizontal", R + "centerInParent"};
        };
    }

    public static boolean hasSide(@NonNull ViewBean bean, @NonNull Side side) {
        boolean constraint = isConstraint(bean.parentType);
        for (String attribute : sideAttributes(constraint, side)) {
            String value = bean.parentAttributes.get(attribute);
            if (value == null) continue;
            if (constraint || !value.equals("false")) return true;
        }
        return false;
    }

    public static void clearSide(@NonNull ViewBean bean, @NonNull Side side) {
        boolean constraint = isConstraint(bean.parentType);
        boolean centeredInParent = !constraint && "true".equals(bean.parentAttributes.get(R + "centerInParent"));
        for (String attribute : sideAttributes(constraint, side)) {
            bean.parentAttributes.remove(attribute);
        }
        if (centeredInParent) {
            // centerInParent covered both axes; keep the centring of the other one.
            bean.parentAttributes.put(isHorizontal(side) ? R + "centerVertical" : R + "centerHorizontal", "true");
        }
        if (constraint) {
            bean.parentAttributes.remove(isHorizontal(side) ? C + "Horizontal_bias" : C + "Vertical_bias");
        }
    }

    public static void clearAll(@NonNull ViewBean bean) {
        for (Side side : Side.values()) {
            clearSide(bean, side);
        }
        bean.parentAttributes.remove(R + "centerInParent");
    }

    /**
     * Attaches {@code side} of {@code bean} to {@code targetSide} of {@code target} ("parent" or a
     * sibling id). Returns false when the combination makes no sense (e.g. a top edge to a start edge).
     */
    public static boolean connect(@NonNull ViewBean bean, @NonNull Side side, @NonNull String target, @NonNull Side targetSide) {
        if (isHorizontal(side) != isHorizontal(targetSide)) return false;
        boolean constraint = isConstraint(bean.parentType);
        boolean parent = PARENT.equals(target);
        String attribute;
        String value;
        if (constraint) {
            String from = switch (side) {
                case TOP -> "Top";
                case BOTTOM -> "Bottom";
                case START -> "Start";
                case END -> "End";
            };
            String to = switch (targetSide) {
                case TOP -> "Top";
                case BOTTOM -> "Bottom";
                case START -> "Start";
                case END -> "End";
            };
            attribute = C + from + "_to" + to + "Of";
            value = target;
        } else if (parent) {
            if (side != targetSide) return false;
            attribute = switch (side) {
                case TOP -> R + "alignParentTop";
                case BOTTOM -> R + "alignParentBottom";
                case START -> R + "alignParentStart";
                case END -> R + "alignParentEnd";
            };
            value = "true";
        } else {
            attribute = switch (side) {
                case TOP -> targetSide == Side.BOTTOM ? R + "below" : R + "alignTop";
                case BOTTOM -> targetSide == Side.TOP ? R + "above" : R + "alignBottom";
                case START -> targetSide == Side.END ? R + "toEndOf" : R + "alignStart";
                case END -> targetSide == Side.START ? R + "toStartOf" : R + "alignEnd";
            };
            value = target;
        }
        clearSide(bean, side);
        bean.parentAttributes.put(attribute, value);
        return true;
    }

    /** Centres the view in its parent on one axis. */
    public static void center(@NonNull ViewBean bean, boolean horizontal) {
        if (isConstraint(bean.parentType)) {
            Side first = horizontal ? Side.START : Side.TOP;
            Side second = horizontal ? Side.END : Side.BOTTOM;
            connect(bean, first, PARENT, first);
            connect(bean, second, PARENT, second);
            bean.parentAttributes.remove(horizontal ? C + "Horizontal_bias" : C + "Vertical_bias");
            if (horizontal) {
                bean.layout.marginLeft = 0;
                bean.layout.marginRight = 0;
            } else {
                bean.layout.marginTop = 0;
                bean.layout.marginBottom = 0;
            }
        } else {
            clearSide(bean, horizontal ? Side.START : Side.TOP);
            clearSide(bean, horizontal ? Side.END : Side.BOTTOM);
            bean.parentAttributes.put(horizontal ? R + "centerHorizontal" : R + "centerVertical", "true");
        }
    }

    /** Pins the view at a position (dp) from the parent's top-start corner. */
    public static void placeAt(@NonNull ViewBean bean, int leftDp, int topDp) {
        clearAll(bean);
        connect(bean, Side.START, PARENT, Side.START);
        connect(bean, Side.TOP, PARENT, Side.TOP);
        bean.layout.marginLeft = Math.max(0, leftDp);
        bean.layout.marginTop = Math.max(0, topDp);
    }

    /**
     * Moves the view by a distance (dp) while keeping its relations: margins of the connected sides
     * change, or the bias when both sides of a ConstraintLayout child are connected.
     *
     * @param freeSpaceXDp horizontal room left for the view between its two anchors (for the bias)
     * @param freeSpaceYDp vertical room left between its two anchors
     */
    public static void moveBy(@NonNull ViewBean bean, int dxDp, int dyDp, int leftDp, int topDp,
                              int freeSpaceXDp, int freeSpaceYDp) {
        moveAxis(bean, true, dxDp, leftDp, freeSpaceXDp);
        moveAxis(bean, false, dyDp, topDp, freeSpaceYDp);
    }

    private static void moveAxis(ViewBean bean, boolean horizontal, int delta, int absoluteDp, int freeSpaceDp) {
        if (delta == 0) return;
        Side first = horizontal ? Side.START : Side.TOP;
        Side second = horizontal ? Side.END : Side.BOTTOM;
        boolean hasFirst = hasSide(bean, first);
        boolean hasSecond = hasSide(bean, second);
        boolean constraint = isConstraint(bean.parentType);
        boolean centred = !constraint && "true".equals(bean.parentAttributes.get(horizontal ? R + "centerHorizontal" : R + "centerVertical"));
        if (constraint && hasFirst && hasSecond) {
            String biasKey = horizontal ? C + "Horizontal_bias" : C + "Vertical_bias";
            float bias = 0.5f;
            try {
                String current = bean.parentAttributes.get(biasKey);
                if (current != null) bias = Float.parseFloat(current);
            } catch (NumberFormatException ignored) {
            }
            if (freeSpaceDp > 0) {
                bias = Math.max(0f, Math.min(1f, bias + (float) delta / freeSpaceDp));
            }
            bean.parentAttributes.put(biasKey, String.valueOf(Math.round(bias * 100) / 100f));
            return;
        }
        if (centred || (!hasFirst && !hasSecond)) {
            // Not positioned on this axis yet (or centred): pin it to the parent's start/top edge.
            if (centred) {
                bean.parentAttributes.remove(horizontal ? R + "centerHorizontal" : R + "centerVertical");
            }
            connect(bean, first, PARENT, first);
            setMargin(bean, first, absoluteDp + delta);
            return;
        }
        if (hasFirst) {
            setMargin(bean, first, getMargin(bean, first) + delta);
        } else {
            setMargin(bean, second, getMargin(bean, second) - delta);
        }
    }

    private static int getMargin(ViewBean bean, Side side) {
        return switch (side) {
            case TOP -> bean.layout.marginTop;
            case BOTTOM -> bean.layout.marginBottom;
            case START -> bean.layout.marginLeft;
            case END -> bean.layout.marginRight;
        };
    }

    private static void setMargin(ViewBean bean, Side side, int value) {
        int margin = Math.max(0, value);
        switch (side) {
            case TOP -> bean.layout.marginTop = margin;
            case BOTTOM -> bean.layout.marginBottom = margin;
            case START -> bean.layout.marginLeft = margin;
            case END -> bean.layout.marginRight = margin;
        }
    }

    /** Every connection of the view, for drawing. */
    @NonNull
    public static List<Connection> connections(@NonNull ViewBean bean) {
        List<Connection> result = new ArrayList<>();
        Map<String, String> attributes = bean.parentAttributes;
        if (isConstraint(bean.parentType)) {
            for (Map.Entry<String, String> entry : attributes.entrySet()) {
                String key = entry.getKey();
                if (!key.startsWith(C) || !key.endsWith("Of") || entry.getValue() == null) continue;
                String body = key.substring(C.length(), key.length() - 2);
                int separator = body.indexOf("_to");
                if (separator < 0) continue;
                Side side = sideOf(body.substring(0, separator));
                Side targetSide = sideOf(body.substring(separator + 3));
                if (side != null && targetSide != null) {
                    result.add(new Connection(side, entry.getValue(), targetSide, key));
                }
            }
            return result;
        }
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (value == null || "false".equals(value)) continue;
            switch (key.substring(key.indexOf(':') + 1)) {
                case "layout_alignParentTop" -> result.add(new Connection(Side.TOP, PARENT, Side.TOP, key));
                case "layout_alignParentBottom" -> result.add(new Connection(Side.BOTTOM, PARENT, Side.BOTTOM, key));
                case "layout_alignParentStart", "layout_alignParentLeft" -> result.add(new Connection(Side.START, PARENT, Side.START, key));
                case "layout_alignParentEnd", "layout_alignParentRight" -> result.add(new Connection(Side.END, PARENT, Side.END, key));
                case "layout_below" -> result.add(new Connection(Side.TOP, value, Side.BOTTOM, key));
                case "layout_alignTop" -> result.add(new Connection(Side.TOP, value, Side.TOP, key));
                case "layout_above" -> result.add(new Connection(Side.BOTTOM, value, Side.TOP, key));
                case "layout_alignBottom" -> result.add(new Connection(Side.BOTTOM, value, Side.BOTTOM, key));
                case "layout_toEndOf", "layout_toRightOf" -> result.add(new Connection(Side.START, value, Side.END, key));
                case "layout_alignStart", "layout_alignLeft" -> result.add(new Connection(Side.START, value, Side.START, key));
                case "layout_toStartOf", "layout_toLeftOf" -> result.add(new Connection(Side.END, value, Side.START, key));
                case "layout_alignEnd", "layout_alignRight" -> result.add(new Connection(Side.END, value, Side.END, key));
                case "layout_centerHorizontal" -> {
                    result.add(new Connection(Side.START, PARENT, null, key));
                    result.add(new Connection(Side.END, PARENT, null, key));
                }
                case "layout_centerVertical" -> {
                    result.add(new Connection(Side.TOP, PARENT, null, key));
                    result.add(new Connection(Side.BOTTOM, PARENT, null, key));
                }
                case "layout_centerInParent" -> {
                    for (Side side : Side.values()) result.add(new Connection(side, PARENT, null, key));
                }
                default -> {
                }
            }
        }
        return result;
    }

    @Nullable
    private static Side sideOf(String name) {
        return switch (name) {
            case "Top", "Baseline" -> Side.TOP;
            case "Bottom" -> Side.BOTTOM;
            case "Start", "Left" -> Side.START;
            case "End", "Right" -> Side.END;
            default -> null;
        };
    }

    private static final List<String> HORIZONTAL_REFERENCES = java.util.Arrays.asList(
            R + "toEndOf", R + "toRightOf", R + "toStartOf", R + "toLeftOf",
            R + "alignStart", R + "alignLeft", R + "alignEnd", R + "alignRight");
    private static final List<String> VERTICAL_REFERENCES = java.util.Arrays.asList(
            R + "below", R + "above", R + "alignTop", R + "alignBottom", R + "alignBaseline");

    /**
     * Whether the RelativeLayout rules of these siblings form a cycle on either axis, which makes
     * RelativeLayout throw "Circular dependencies cannot exist in RelativeLayout" at runtime.
     */
    public static boolean hasRelativeCycle(@NonNull List<ViewBean> siblings) {
        return hasCycle(siblings, HORIZONTAL_REFERENCES) || hasCycle(siblings, VERTICAL_REFERENCES);
    }

    private static boolean hasCycle(List<ViewBean> siblings, List<String> references) {
        Map<String, List<String>> edges = new HashMap<>();
        for (ViewBean bean : siblings) {
            List<String> targets = new ArrayList<>();
            for (String attribute : references) {
                String target = bean.parentAttributes.get(attribute);
                if (target != null && !target.equals(PARENT)) targets.add(target);
            }
            edges.put(bean.id, targets);
        }
        Map<String, Integer> state = new HashMap<>();
        for (String id : edges.keySet()) {
            if (visit(id, edges, state)) return true;
        }
        return false;
    }

    /** Depth-first search; state 1 = on the current path, 2 = finished. */
    private static boolean visit(String id, Map<String, List<String>> edges, Map<String, Integer> state) {
        Integer current = state.get(id);
        if (current != null) return current == 1;
        state.put(id, 1);
        List<String> targets = edges.get(id);
        if (targets != null) {
            for (String target : targets) {
                if (edges.containsKey(target) && visit(target, edges, state)) return true;
            }
        }
        state.put(id, 2);
        return false;
    }

    /** Copy of the rules, used to restore them when a change is rejected. */
    @NonNull
    public static HashMap<String, String> snapshot(@NonNull ViewBean bean) {
        return new HashMap<>(bean.parentAttributes);
    }
}
