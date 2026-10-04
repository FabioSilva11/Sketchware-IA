package pro.sketchware.ia.layout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates the layout spec returned by the AI and turns it into layout XML for ViewBeanParser.
 * <p>
 * Every rule here is also written in the prompt ({@link LayoutPrompts}), so a rejected spec comes
 * with errors the model can fix: each one names the view, the field and what is allowed.
 */
public final class LayoutSpecCompiler {

    public static final class Result {
        public final String xml;
        public final List<String> errors;

        Result(String xml, List<String> errors) {
            this.xml = xml;
            this.errors = errors;
        }

        public boolean isValid() {
            return errors.isEmpty() && xml != null;
        }
    }

    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_]{0,48}");
    private static final Pattern DIMEN = Pattern.compile("-?\\d+(\\.\\d+)?dp");
    private static final Pattern TEXT_SIZE = Pattern.compile("\\d+(\\.\\d+)?sp");
    private static final Pattern COLOR = Pattern.compile("#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})");
    private static final Pattern RATIO = Pattern.compile("(H,|W,)?\\d+(\\.\\d+)?:\\d+(\\.\\d+)?");
    private static final Pattern ATTRIBUTE_NAME = Pattern.compile("(android|app):[A-Za-z_][A-Za-z0-9_]*");
    private static final Set<String> GRAVITY_PARTS = Set.of("top", "bottom", "start", "end", "left", "right",
            "center", "center_horizontal", "center_vertical", "fill", "fill_horizontal", "fill_vertical");
    private static final Set<String> VIEW_KEYS = Set.of("id", "type", "parent", "width", "height", "margin", "padding",
            "text", "hint", "textSize", "textStyle", "textColor", "gravity", "layoutGravity", "weight", "orientation",
            "background", "src", "layout", "relative", "constraints", "attributes");
    /** Attributes that have a dedicated field in the spec, or that the app sets itself. */
    private static final Set<String> RESERVED_ATTRIBUTES = Set.of("android:id", "android:layout_width",
            "android:layout_height", "android:text", "android:hint", "android:orientation", "android:src",
            "android:background", "android:layout_weight", "android:layout_gravity", "android:gravity");
    private static final Map<String, String> RELATIVE_BOOLEAN = new LinkedHashMap<>();
    private static final Map<String, String> RELATIVE_REFERENCE = new LinkedHashMap<>();

    static {
        for (String rule : new String[]{"alignParentTop", "alignParentBottom", "alignParentStart", "alignParentEnd",
                "centerInParent", "centerHorizontal", "centerVertical"}) {
            RELATIVE_BOOLEAN.put(rule, "android:layout_" + rule);
        }
        for (String rule : new String[]{"below", "above", "toStartOf", "toEndOf", "alignTop", "alignBottom",
                "alignStart", "alignEnd", "alignBaseline"}) {
            RELATIVE_REFERENCE.put(rule, "android:layout_" + rule);
        }
    }

    private final LayoutComponentRegistry registry;
    private final LayoutProjectContext project;

    public LayoutSpecCompiler(@NonNull LayoutComponentRegistry registry, @NonNull LayoutProjectContext project) {
        this.registry = registry;
        this.project = project;
    }

    private static final class View {
        final String id;
        final LayoutComponentRegistry.Component component;
        final JSONObject json;
        final String parent;
        final List<View> children = new ArrayList<>();

        View(String id, LayoutComponentRegistry.Component component, JSONObject json, String parent) {
            this.id = id;
            this.component = component;
            this.json = json;
            this.parent = parent;
        }
    }

    @NonNull
    public Result compile(@Nullable String response) {
        List<String> errors = new ArrayList<>();
        JSONObject document;
        try {
            document = new JSONObject(extractJson(response));
        } catch (JSONException e) {
            errors.add("The answer is not a JSON object: " + e.getMessage() + ". Reply with the JSON object only.");
            return new Result(null, errors);
        }

        JSONObject rootJson = document.optJSONObject("root");
        if (rootJson == null) {
            errors.add("Missing \"root\" object.");
            return new Result(null, errors);
        }
        LayoutComponentRegistry.Component rootComponent = registry.find(rootJson.optString("type", "LinearLayout"));
        if (rootComponent == null || !rootComponent.isContainer() || rootComponent.name.equals("include")) {
            errors.add("root.type must be one of the containers in the catalog, got \"" + rootJson.optString("type") + "\".");
            return new Result(null, errors);
        }
        View root = new View("root", rootComponent, rootJson, null);

        JSONArray viewsJson = document.optJSONArray("views");
        if (viewsJson == null) {
            errors.add("Missing \"views\" array.");
            return new Result(null, errors);
        }
        if (viewsJson.length() > 120) {
            errors.add("Too many views (" + viewsJson.length() + "); keep the layout under 120 views.");
        }

        Map<String, View> views = new LinkedHashMap<>();
        views.put("root", root);
        for (int i = 0; i < viewsJson.length(); i++) {
            JSONObject json = viewsJson.optJSONObject(i);
            String where = "views[" + i + "]";
            if (json == null) {
                errors.add(where + " is not an object.");
                continue;
            }
            String id = json.optString("id", "").trim();
            if (!ID.matcher(id).matches()) {
                errors.add(where + ": id \"" + id + "\" must be snake_case (a-z, 0-9, _) and start with a letter.");
                continue;
            }
            where = "view \"" + id + "\"";
            if (views.containsKey(id)) {
                errors.add(where + ": the id is used twice; ids must be unique.");
                continue;
            }
            for (Iterator<String> keys = json.keys(); keys.hasNext(); ) {
                String key = keys.next();
                if (!VIEW_KEYS.contains(key)) errors.add(where + ": unknown field \"" + key + "\".");
            }
            LayoutComponentRegistry.Component component = registry.find(json.optString("type"));
            if (component == null) {
                errors.add(where + ": type \"" + json.optString("type") + "\" is not in the catalog of this project.");
                continue;
            }
            String parentId = json.optString("parent", "root").trim();
            View parent = views.get(parentId);
            if (parent == null) {
                errors.add(where + ": parent \"" + parentId + "\" must be \"root\" or the id of a container listed before it.");
                continue;
            }
            if (!parent.component.isContainer()) {
                errors.add(where + ": parent \"" + parentId + "\" is a " + parent.component.name + ", which can't have children.");
                continue;
            }
            View view = new View(id, component, json, parentId);
            parent.children.add(view);
            views.put(id, view);
        }

        for (View view : views.values()) {
            if (view.component.kind == LayoutComponentRegistry.Kind.SINGLE_CHILD && view.children.size() > 1) {
                errors.add("view \"" + view.id + "\": a " + view.component.name + " takes exactly one child, it has "
                        + view.children.size() + ". Wrap them in a LinearLayout.");
            }
            if (view.component.name.equals("TextInputLayout")) {
                for (View child : view.children) {
                    if (!child.component.name.equals("TextInputEditText") && !child.component.name.equals("EditText")) {
                        errors.add("view \"" + child.id + "\": a TextInputLayout child must be a TextInputEditText.");
                    }
                }
            }
        }

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        appendView(xml, root, views, errors, 0);
        if (root.component.isRelative()) checkRelativeCycles(root, errors);
        return new Result(errors.isEmpty() ? xml.toString() : null, errors);
    }

    private void appendView(StringBuilder xml, View view, Map<String, View> views, List<String> errors, int depth) {
        String indent = "    ".repeat(depth);
        boolean isRoot = depth == 0;
        String where = isRoot ? "root" : "view \"" + view.id + "\"";
        View parent = view.parent == null ? null : views.get(view.parent);
        JSONObject json = view.json;
        Map<String, String> attributes = new LinkedHashMap<>();
        if (isRoot) {
            attributes.put("xmlns:android", "http://schemas.android.com/apk/res/android");
            attributes.put("xmlns:app", "http://schemas.android.com/apk/res-auto");
            attributes.put("android:layout_width", "match_parent");
            attributes.put("android:layout_height", "match_parent");
        } else if (view.component.name.equals("include")) {
            String layout = json.optString("layout", "").trim();
            if (layout.startsWith("@layout/")) layout = layout.substring("@layout/".length());
            if (!project.layouts.contains(layout)) {
                errors.add(where + ": include needs \"layout\" set to one of " + project.layouts + ", got \"" + layout + "\".");
            } else if (layout.equals(project.currentLayout)) {
                errors.add(where + ": a layout can't include itself.");
            }
            attributes.put("layout", "@layout/" + layout);
            attributes.put("android:id", "@+id/" + view.id);
        } else {
            attributes.put("android:id", "@+id/" + view.id);
        }

        if (!isRoot) {
            attributes.put("android:layout_width", size(json.opt("width"), "wrap_content", where + ".width", parent, errors));
            attributes.put("android:layout_height", size(json.opt("height"), "wrap_content", where + ".height", parent, errors));
            box(json.opt("margin"), "android:layout_margin", where + ".margin", attributes, errors);
        }
        if (!view.component.name.equals("include")) {
            box(json.opt("padding"), "android:padding", where + ".padding", attributes, errors);
        }

        if (json.has("orientation")) {
            String orientation = json.optString("orientation");
            if (!view.component.isLinear()) {
                errors.add(where + ": orientation only applies to LinearLayout and RadioGroup.");
            } else if (!orientation.equals("vertical") && !orientation.equals("horizontal")) {
                errors.add(where + ": orientation must be \"vertical\" or \"horizontal\".");
            } else {
                attributes.put("android:orientation", orientation);
            }
        } else if (view.component.isLinear()) {
            attributes.put("android:orientation", "vertical");
        }

        text(json, "text", "android:text", view, where, attributes, errors);
        text(json, "hint", "android:hint", view, where, attributes, errors);
        if (json.has("textSize")) {
            String size = json.optString("textSize");
            if (!TEXT_SIZE.matcher(size).matches()) errors.add(where + ".textSize must look like 16sp.");
            else attributes.put("android:textSize", size);
        }
        if (json.has("textStyle")) {
            String style = json.optString("textStyle");
            if (!Set.of("normal", "bold", "italic", "bold|italic").contains(style)) {
                errors.add(where + ".textStyle must be normal, bold, italic or bold|italic.");
            } else if (!style.equals("normal")) {
                attributes.put("android:textStyle", style);
            }
        }
        if (json.has("textColor")) color(json.optString("textColor"), where + ".textColor", "android:textColor", attributes, errors);
        if (json.has("gravity")) gravity(json.optString("gravity"), where + ".gravity", "android:gravity", attributes, errors);
        if (json.has("layoutGravity")) {
            if (parent == null || !(parent.component.isLinear() || parent.component.name.endsWith("FrameLayout")
                    || parent.component.name.contains("Card") || parent.component.kind == LayoutComponentRegistry.Kind.SINGLE_CHILD)) {
                errors.add(where + ": layoutGravity only applies inside LinearLayout, FrameLayout, cards and scroll views.");
            } else {
                gravity(json.optString("layoutGravity"), where + ".layoutGravity", "android:layout_gravity", attributes, errors);
            }
        }
        if (json.has("weight")) {
            double weight = json.optDouble("weight", -1);
            if (parent == null || !parent.component.isLinear()) {
                errors.add(where + ": weight only applies inside a LinearLayout.");
            } else if (weight < 0) {
                errors.add(where + ".weight must be a number >= 0.");
            } else {
                attributes.put("android:layout_weight", weight == Math.floor(weight) ? String.valueOf((int) weight) : String.valueOf(weight));
            }
        }
        if (json.has("background")) {
            String background = json.optString("background");
            if (background.startsWith("@drawable/")) {
                if (!project.drawables.contains(background)) {
                    errors.add(where + ".background: " + background + " doesn't exist; use a color or one of " + project.drawables + ".");
                } else {
                    attributes.put("android:background", background);
                }
            } else {
                color(background, where + ".background", "android:background", attributes, errors);
            }
        }
        if (json.has("src")) {
            String src = json.optString("src");
            if (!view.component.isImage()) {
                errors.add(where + ": src only applies to ImageView, ImageButton and CircleImageView.");
            } else if (!project.drawables.contains(src)) {
                errors.add(where + ".src: " + src + " doesn't exist; omit src or use one of " + project.drawables + ".");
            } else {
                attributes.put("android:src", src);
            }
        }

        if (json.has("relative")) {
            if (parent == null || !parent.component.isRelative()) {
                errors.add(where + ": \"relative\" rules only apply to children of a RelativeLayout.");
            } else {
                relative(json.optJSONObject("relative"), view, parent, where, attributes, errors);
            }
        }
        if (parent != null && parent.component.isConstraint()) {
            constraints(json.optJSONObject("constraints"), view, parent, where, attributes, errors);
        } else if (json.has("constraints")) {
            errors.add(where + ": \"constraints\" only apply to children of a ConstraintLayout.");
        }

        JSONObject extra = json.optJSONObject("attributes");
        if (extra != null) {
            for (Iterator<String> keys = extra.keys(); keys.hasNext(); ) {
                String name = keys.next();
                String value = extra.optString(name);
                if (!ATTRIBUTE_NAME.matcher(name).matches()) {
                    errors.add(where + ".attributes: \"" + name + "\" must be an android: or app: attribute.");
                } else if (!isAllowedExtraAttribute(name)) {
                    errors.add(where + ".attributes: use the dedicated field instead of \"" + name + "\".");
                } else if (!resourceExists(value)) {
                    errors.add(where + ".attributes." + name + ": " + value + " doesn't exist in the project.");
                } else {
                    attributes.put(name, value);
                }
            }
        }

        String tag = view.component.tag;
        xml.append(indent).append('<').append(tag);
        for (Map.Entry<String, String> attribute : attributes.entrySet()) {
            xml.append('\n').append(indent).append("    ").append(attribute.getKey()).append("=\"")
                    .append(escape(attribute.getValue())).append('"');
        }
        if (view.children.isEmpty()) {
            xml.append(" />\n");
            return;
        }
        xml.append(">\n");
        for (View child : view.children) {
            appendView(xml, child, views, errors, depth + 1);
        }
        xml.append(indent).append("</").append(tag).append(">\n");
    }

    /** Extra attributes the spec may carry; the others have their own field. */
    public static boolean isAllowedExtraAttribute(@NonNull String name) {
        return ATTRIBUTE_NAME.matcher(name).matches() && !RESERVED_ATTRIBUTES.contains(name)
                && !name.startsWith("android:layout_") && !name.startsWith("app:layout_");
    }

    private String size(Object value, String fallback, String where, View parent, List<String> errors) {
        if (value == null) return fallback;
        String size = String.valueOf(value).trim();
        if (size.equals("match_parent") || size.equals("wrap_content")) return size;
        if (DIMEN.matcher(size).matches()) {
            if (size.equals("0dp") && (parent == null || !(parent.component.isConstraint() || parent.component.isLinear()))) {
                errors.add(where + ": 0dp only makes sense inside a ConstraintLayout (match constraints) or with weight in a LinearLayout.");
            }
            return size;
        }
        errors.add(where + " must be match_parent, wrap_content or a size like 48dp, got \"" + size + "\".");
        return fallback;
    }

    private void box(Object value, String prefix, String where, Map<String, String> attributes, List<String> errors) {
        if (value == null) return;
        if (value instanceof String all) {
            if (!DIMEN.matcher(all).matches()) errors.add(where + " must look like 16dp.");
            else attributes.put(prefix, all);
            return;
        }
        if (!(value instanceof JSONObject sides)) {
            errors.add(where + " must be \"16dp\" or an object like {\"top\":\"8dp\",\"start\":\"16dp\"}.");
            return;
        }
        for (Iterator<String> keys = sides.keys(); keys.hasNext(); ) {
            String side = keys.next();
            String size = sides.optString(side);
            String suffix = switch (side) {
                case "top" -> "Top";
                case "bottom" -> "Bottom";
                case "start", "left" -> "Left";
                case "end", "right" -> "Right";
                case "horizontal" -> "Horizontal";
                case "vertical" -> "Vertical";
                default -> null;
            };
            if (suffix == null) {
                errors.add(where + ": unknown side \"" + side + "\" (use top, bottom, start, end, horizontal, vertical).");
            } else if (!DIMEN.matcher(size).matches()) {
                errors.add(where + "." + side + " must look like 16dp.");
            } else {
                attributes.put(prefix + suffix, size);
            }
        }
    }

    private void text(JSONObject json, String field, String attribute, View view, String where,
                      Map<String, String> attributes, List<String> errors) {
        if (!json.has(field)) return;
        String value = json.optString(field);
        boolean allowed = field.equals("hint") ? view.component.acceptsHint() : view.component.isTextual();
        if (!allowed) {
            errors.add(where + ": " + view.component.name + " has no " + field + ".");
        } else if (value.startsWith("@string/") && !project.strings.contains(value.substring(8))) {
            errors.add(where + "." + field + ": " + value + " doesn't exist; write the text itself, the app creates the string resource.");
        } else if (!value.isEmpty()) {
            String escaped = value.replace("\n", "\\n");
            if (!value.startsWith("@string/") && (value.startsWith("@") || value.startsWith("?"))) {
                // Literal text starting with @ or ? would be read as a resource reference.
                escaped = "\\" + escaped;
            }
            attributes.put(attribute, escaped);
        }
    }

    private void color(String value, String where, String attribute, Map<String, String> attributes, List<String> errors) {
        if (COLOR.matcher(value).matches()) {
            attributes.put(attribute, value);
        } else if (value.startsWith("@color/") && project.colors.contains(value.substring(7))) {
            attributes.put(attribute, value);
        } else if (value.startsWith("?attr/") || value.startsWith("?android:attr/")) {
            attributes.put(attribute, value);
        } else {
            errors.add(where + " must be #RRGGBB, #AARRGGBB, ?attr/... or one of the project colors " + project.colorReferences() + ", got \"" + value + "\".");
        }
    }

    private void gravity(String value, String where, String attribute, Map<String, String> attributes, List<String> errors) {
        for (String part : value.split("\\|")) {
            if (!GRAVITY_PARTS.contains(part.trim())) {
                errors.add(where + ": \"" + part + "\" isn't a gravity; use combinations of " + GRAVITY_PARTS + " joined with |.");
                return;
            }
        }
        attributes.put(attribute, value.replace(" ", ""));
    }

    private void relative(@Nullable JSONObject rules, View view, View parent, String where,
                          Map<String, String> attributes, List<String> errors) {
        if (rules == null) {
            errors.add(where + ": \"relative\" must be an object.");
            return;
        }
        for (Iterator<String> keys = rules.keys(); keys.hasNext(); ) {
            String rule = keys.next();
            Object value = rules.opt(rule);
            if (RELATIVE_BOOLEAN.containsKey(rule)) {
                if (!(value instanceof Boolean)) {
                    errors.add(where + ".relative." + rule + " must be true or false.");
                } else if ((Boolean) value) {
                    attributes.put(RELATIVE_BOOLEAN.get(rule), "true");
                }
            } else if (RELATIVE_REFERENCE.containsKey(rule)) {
                String target = String.valueOf(value);
                if (!isSibling(parent, target, view)) {
                    errors.add(where + ".relative." + rule + ": \"" + target + "\" must be the id of another child of \"" + parent.id + "\".");
                } else {
                    attributes.put(RELATIVE_REFERENCE.get(rule), "@id/" + target);
                }
            } else {
                errors.add(where + ".relative: unknown rule \"" + rule + "\". Allowed: " + RELATIVE_BOOLEAN.keySet() + " " + RELATIVE_REFERENCE.keySet() + ".");
            }
        }
    }

    private void constraints(@Nullable JSONObject constraints, View view, View parent, String where,
                             Map<String, String> attributes, List<String> errors) {
        if (constraints == null) {
            errors.add(where + ": children of a ConstraintLayout need \"constraints\" with at least one horizontal (start/end) and one vertical (top/bottom) side.");
            return;
        }
        boolean horizontal = false;
        boolean vertical = false;
        for (Iterator<String> keys = constraints.keys(); keys.hasNext(); ) {
            String side = keys.next();
            Object value = constraints.opt(side);
            switch (side) {
                case "horizontalBias", "verticalBias" -> {
                    double bias = constraints.optDouble(side, -1);
                    if (bias < 0 || bias > 1) {
                        errors.add(where + ".constraints." + side + " must be a number from 0 to 1.");
                    } else {
                        attributes.put(side.equals("horizontalBias") ? "app:layout_constraintHorizontal_bias" : "app:layout_constraintVertical_bias",
                                String.valueOf(bias));
                    }
                }
                case "dimensionRatio" -> {
                    String ratio = String.valueOf(value);
                    if (!RATIO.matcher(ratio).matches()) errors.add(where + ".constraints.dimensionRatio must look like 16:9.");
                    else attributes.put("app:layout_constraintDimensionRatio", ratio);
                }
                case "top", "bottom", "start", "end", "baseline" -> {
                    String target = String.valueOf(value).trim();
                    int dot = target.lastIndexOf('.');
                    String targetId = dot > 0 ? target.substring(0, dot) : "";
                    String targetSide = dot > 0 ? target.substring(dot + 1) : "";
                    Set<String> allowedSides = switch (side) {
                        case "top", "bottom" -> Set.of("top", "bottom");
                        case "start", "end" -> Set.of("start", "end");
                        default -> Set.of("baseline");
                    };
                    if (!allowedSides.contains(targetSide)) {
                        errors.add(where + ".constraints." + side + ": \"" + target + "\" must be \"parent." + side + "\" or \"<sibling id>.<" + String.join("|", allowedSides) + ">\".");
                        continue;
                    }
                    if (!targetId.equals("parent") && !isSibling(parent, targetId, view)) {
                        errors.add(where + ".constraints." + side + ": \"" + targetId + "\" must be \"parent\" or another child of \"" + parent.id + "\".");
                        continue;
                    }
                    String from = capitalize(side);
                    String to = capitalize(targetSide);
                    attributes.put("app:layout_constraint" + from + "_to" + to + "Of", targetId.equals("parent") ? "parent" : "@id/" + targetId);
                    if (side.equals("start") || side.equals("end")) horizontal = true;
                    else vertical = true;
                }
                default -> errors.add(where + ".constraints: unknown key \"" + side + "\". Allowed: top, bottom, start, end, baseline, horizontalBias, verticalBias, dimensionRatio.");
            }
        }
        if (!horizontal) errors.add(where + ".constraints: add \"start\" and/or \"end\"; without a horizontal constraint the view sticks to the left edge.");
        if (!vertical) errors.add(where + ".constraints: add \"top\" and/or \"bottom\"; without a vertical constraint the view sticks to the top edge.");
    }

    private static boolean isSibling(View parent, String id, View self) {
        if (id.equals(self.id)) return false;
        for (View child : parent.children) {
            if (child.id.equals(id)) return true;
        }
        return false;
    }

    /** RelativeLayout throws at runtime when rules depend on each other in a circle. */
    private void checkRelativeCycles(View root, List<String> errors) {
        checkRelativeCyclesIn(root, errors);
    }

    private void checkRelativeCyclesIn(View container, List<String> errors) {
        if (container.component.isRelative()) {
            for (String axis : new String[]{"horizontal", "vertical"}) {
                Set<String> rules = axis.equals("horizontal")
                        ? Set.of("toStartOf", "toEndOf", "alignStart", "alignEnd")
                        : Set.of("below", "above", "alignTop", "alignBottom", "alignBaseline");
                Map<String, List<String>> edges = new HashMap<>();
                for (View child : container.children) {
                    List<String> targets = new ArrayList<>();
                    JSONObject relative = child.json.optJSONObject("relative");
                    if (relative != null) {
                        for (String rule : rules) {
                            if (relative.has(rule)) targets.add(relative.optString(rule));
                        }
                    }
                    edges.put(child.id, targets);
                }
                Map<String, Integer> state = new HashMap<>();
                for (String id : edges.keySet()) {
                    if (visit(id, edges, state)) {
                        errors.add("children of \"" + container.id + "\": " + axis + " relative rules form a circle around \"" + id
                                + "\"; RelativeLayout can't resolve that.");
                        break;
                    }
                }
            }
        }
        for (View child : container.children) {
            checkRelativeCyclesIn(child, errors);
        }
    }

    private static boolean visit(String id, Map<String, List<String>> edges, Map<String, Integer> state) {
        Integer current = state.get(id);
        if (current != null) return current == 1;
        state.put(id, 1);
        for (String target : edges.getOrDefault(id, List.of())) {
            if (edges.containsKey(target) && visit(target, edges, state)) return true;
        }
        state.put(id, 2);
        return false;
    }

    private boolean resourceExists(String value) {
        if (value.startsWith("@drawable/")) return project.drawables.contains(value);
        if (value.startsWith("@color/")) return project.colors.contains(value.substring(7));
        if (value.startsWith("@string/")) return project.strings.contains(value.substring(8));
        if (value.startsWith("@layout/")) return project.layouts.contains(value.substring(8));
        if (value.startsWith("@+id/") || value.startsWith("@id/")) return false;
        if (value.startsWith("@font/")) return project.fonts.contains(value.substring(6));
        return !value.startsWith("@") || value.startsWith("@android:") || value.startsWith("@null");
    }

    private static String capitalize(String value) {
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** The JSON object of an answer that may be wrapped in markdown fences or prose. */
    @NonNull
    static String extractJson(@Nullable String response) {
        String text = response == null ? "" : response.trim();
        int fence = text.indexOf("```");
        if (fence >= 0) {
            int start = text.indexOf('\n', fence);
            int end = text.indexOf("```", start + 1);
            if (start > 0 && end > start) text = text.substring(start + 1, end).trim();
        }
        int open = text.indexOf('{');
        int close = text.lastIndexOf('}');
        return open >= 0 && close > open ? text.substring(open, close + 1) : text;
    }

    /** Ids used by the spec, for keeping track of which widgets were kept, added or removed. */
    @NonNull
    public static Set<String> ids(@Nullable String response) {
        Set<String> ids = new HashSet<>();
        try {
            JSONArray views = new JSONObject(extractJson(response)).optJSONArray("views");
            for (int i = 0; views != null && i < views.length(); i++) {
                JSONObject view = views.optJSONObject(i);
                if (view != null) ids.add(view.optString("id"));
            }
        } catch (JSONException ignored) {
        }
        return ids;
    }
}
