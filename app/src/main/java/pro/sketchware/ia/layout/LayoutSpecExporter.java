package pro.sketchware.ia.layout;

import android.view.Gravity;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.besome.sketch.beans.LayoutBean;
import com.besome.sketch.beans.TextBean;
import com.besome.sketch.beans.ViewBean;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import pro.sketchware.utility.InjectAttributes;
import pro.sketchware.utility.ProjectStrings;

/**
 * Writes the current layout in the same spec format the AI answers with, so editing a layout uses
 * one format in both directions and keeps ids (and the logic blocks that use them).
 */
public final class LayoutSpecExporter {

    private final String scId;
    private final LayoutComponentRegistry registry;

    public LayoutSpecExporter(@NonNull String scId, @NonNull LayoutComponentRegistry registry) {
        this.scId = scId;
        this.registry = registry;
    }

    @NonNull
    public String export(@NonNull String rootType, @NonNull Map<String, String> rootAttributes, @NonNull List<ViewBean> beans)
            throws JSONException {
        JSONObject document = new JSONObject();
        JSONObject root = new JSONObject();
        root.put("type", componentName(rootType, null));
        String orientation = rootAttributes.get("android:orientation");
        if (orientation != null) root.put("orientation", orientation);
        String gravity = rootAttributes.get("android:gravity");
        if (gravity != null) root.put("gravity", gravity);
        document.put("root", root);

        JSONArray views = new JSONArray();
        List<ViewBean> ordered = new ArrayList<>();
        addChildren("root", beans, ordered);
        for (ViewBean bean : ordered) {
            views.put(export(bean));
        }
        document.put("views", views);
        return document.toString(1);
    }

    /** Parents before their children, children in their on-screen order. */
    private static void addChildren(String parent, List<ViewBean> beans, List<ViewBean> out) {
        List<ViewBean> children = new ArrayList<>();
        for (ViewBean bean : beans) {
            if (parent.equals(bean.parent == null || bean.parent.isEmpty() ? "root" : bean.parent)) children.add(bean);
        }
        children.sort(Comparator.comparingInt(bean -> bean.index));
        for (ViewBean child : children) {
            out.add(child);
            addChildren(child.id, beans, out);
        }
    }

    private String componentName(String className, ViewBean bean) {
        LayoutComponentRegistry.Component component = registry.find(className);
        if (component != null) return component.name;
        // A component the AI catalog doesn't offer (a library widget): keep it as it is.
        String simple = className.substring(className.lastIndexOf('.') + 1);
        boolean container = bean != null && (bean.getClassInfo().a("ViewGroup") || bean.getClassInfo().a("LinearLayout"));
        registry.allow(simple, className, container ? LayoutComponentRegistry.Kind.CONTAINER : LayoutComponentRegistry.Kind.WIDGET);
        return simple;
    }

    private JSONObject export(ViewBean bean) throws JSONException {
        JSONObject view = new JSONObject();
        view.put("id", bean.id);
        String className = "include".equals(bean.convert) ? "include"
                : bean.convert != null && !bean.convert.isEmpty() ? bean.convert : bean.getClassInfo().getClassName();
        view.put("type", componentName(className, bean));
        view.put("parent", bean.parent == null || bean.parent.isEmpty() ? "root" : bean.parent);
        LayoutBean layout = bean.layout;
        view.put("width", size(layout.width));
        view.put("height", size(layout.height));
        putBox(view, "margin", layout.marginTop, layout.marginBottom, layout.marginLeft, layout.marginRight);
        putBox(view, "padding", layout.paddingTop, layout.paddingBottom, layout.paddingLeft, layout.paddingRight);
        if (layout.orientation == LayoutBean.ORIENTATION_HORIZONTAL) view.put("orientation", "horizontal");
        if (layout.orientation == LayoutBean.ORIENTATION_VERTICAL) view.put("orientation", "vertical");
        if (layout.gravity != 0) view.put("gravity", gravity(layout.gravity));
        if (layout.layoutGravity != 0) view.put("layoutGravity", gravity(layout.layoutGravity));
        if (layout.weight > 0) view.put("weight", layout.weight);
        if (layout.backgroundResource != null && !layout.backgroundResource.isEmpty() && !"NONE".equals(layout.backgroundResource)) {
            view.put("background", "@drawable/" + layout.backgroundResource.replace(".9", ""));
        } else if (layout.backgroundResColor != null) {
            view.put("background", layout.backgroundResColor.startsWith("?") || layout.backgroundResColor.startsWith("@")
                    ? layout.backgroundResColor : "@color/" + layout.backgroundResColor);
        } else if (layout.backgroundColor != 0xffffff && layout.backgroundColor != 0) {
            view.put("background", String.format("#%08X", layout.backgroundColor));
        }
        TextBean text = bean.text;
        if (bean.getClassInfo().a("TextView") && text != null) {
            if (text.text != null && !text.text.isEmpty()) view.put("text", resolve(text.text));
            if (text.hint != null && !text.hint.isEmpty() && bean.getClassInfo().a("EditText")) view.put("hint", resolve(text.hint));
            if (text.textSize > 0) view.put("textSize", text.textSize + "sp");
            if (text.textType == TextBean.TEXT_TYPE_BOLD) view.put("textStyle", "bold");
            if (text.textType == TextBean.TEXT_TYPE_ITALIC) view.put("textStyle", "italic");
            if (text.textType == TextBean.TEXT_TYPE_BOLDITALIC) view.put("textStyle", "bold|italic");
            if (text.resTextColor != null) {
                view.put("textColor", text.resTextColor.startsWith("?") || text.resTextColor.startsWith("@")
                        ? text.resTextColor : "@color/" + text.resTextColor);
            } else if (text.textColor != 0xffffff && text.textColor != 0) {
                view.put("textColor", String.format("#%08X", text.textColor | 0xff000000));
            }
        }
        if (bean.getClassInfo().a("ImageView") && bean.image != null && bean.image.resName != null
                && !bean.image.resName.isEmpty() && !"default_image".equals(bean.image.resName) && !"NONE".equals(bean.image.resName)) {
            view.put("src", "@drawable/" + bean.image.resName);
        }

        JSONObject relative = new JSONObject();
        JSONObject constraints = new JSONObject();
        for (Map.Entry<String, String> rule : bean.parentAttributes.entrySet()) {
            String key = rule.getKey();
            String value = rule.getValue();
            if (key.startsWith("android:layout_")) {
                String name = switch (key.substring("android:layout_".length())) {
                    case "alignParentLeft" -> "alignParentStart";
                    case "alignParentRight" -> "alignParentEnd";
                    case "toLeftOf" -> "toStartOf";
                    case "toRightOf" -> "toEndOf";
                    case "alignLeft" -> "alignStart";
                    case "alignRight" -> "alignEnd";
                    default -> key.substring("android:layout_".length());
                };
                if ("true".equals(value)) {
                    relative.put(name, true);
                } else if (!"false".equals(value)) {
                    relative.put(name, value);
                }
            } else if (key.startsWith("app:layout_constraint")) {
                String body = key.substring("app:layout_constraint".length());
                switch (body) {
                    case "Horizontal_bias" -> constraints.put("horizontalBias", Double.parseDouble(value));
                    case "Vertical_bias" -> constraints.put("verticalBias", Double.parseDouble(value));
                    case "DimensionRatio" -> constraints.put("dimensionRatio", value);
                    default -> {
                        int separator = body.indexOf("_to");
                        if (separator > 0 && body.endsWith("Of")) {
                            String side = body.substring(0, separator).toLowerCase(java.util.Locale.ROOT);
                            String targetSide = body.substring(separator + 3, body.length() - 2).toLowerCase(java.util.Locale.ROOT);
                            if (side.equals("left")) side = "start";
                            if (side.equals("right")) side = "end";
                            if (targetSide.equals("left")) targetSide = "start";
                            if (targetSide.equals("right")) targetSide = "end";
                            constraints.put(side, value + "." + targetSide);
                        }
                    }
                }
            }
        }
        if (relative.length() > 0) view.put("relative", relative);
        if (constraints.length() > 0) view.put("constraints", constraints);

        JSONObject attributes = new JSONObject();
        for (Map.Entry<String, String> attribute : InjectAttributes.parse(bean.inject).entrySet()) {
            String name = attribute.getKey();
            if (name.equals("layout") && "include".equals(bean.convert)) {
                view.put("layout", attribute.getValue().replace("@layout/", ""));
            } else if (name.equals("android:background") && !view.has("background")) {
                // e.g. the ripple of an ImageButton
                view.put("background", attribute.getValue());
            } else if (LayoutSpecCompiler.isAllowedExtraAttribute(name)) {
                attributes.put(name, attribute.getValue());
            }
        }
        if (attributes.length() > 0) view.put("attributes", attributes);
        return view;
    }

    private String resolve(String value) {
        return ProjectStrings.isReference(value) ? ProjectStrings.resolve(scId, value) : value;
    }

    private static String size(int size) {
        if (size == ViewGroup.LayoutParams.MATCH_PARENT) return "match_parent";
        if (size == ViewGroup.LayoutParams.WRAP_CONTENT) return "wrap_content";
        return size + "dp";
    }

    private static void putBox(JSONObject view, String name, int top, int bottom, int start, int end) throws JSONException {
        if (top == 0 && bottom == 0 && start == 0 && end == 0) return;
        if (top == bottom && top == start && top == end) {
            view.put(name, top + "dp");
            return;
        }
        JSONObject box = new JSONObject();
        if (top != 0) box.put("top", top + "dp");
        if (bottom != 0) box.put("bottom", bottom + "dp");
        if (start != 0) box.put("start", start + "dp");
        if (end != 0) box.put("end", end + "dp");
        view.put(name, box);
    }

    static String gravity(int gravity) {
        List<String> parts = new ArrayList<>();
        int horizontal = gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
        int vertical = gravity & Gravity.VERTICAL_GRAVITY_MASK;
        if (horizontal == Gravity.CENTER_HORIZONTAL && vertical == Gravity.CENTER_VERTICAL) return "center";
        if (horizontal == Gravity.CENTER_HORIZONTAL) parts.add("center_horizontal");
        else if ((horizontal & Gravity.RIGHT) == Gravity.RIGHT) parts.add("end");
        else if ((horizontal & Gravity.LEFT) == Gravity.LEFT) parts.add("start");
        if (vertical == Gravity.CENTER_VERTICAL) parts.add("center_vertical");
        else if (vertical == Gravity.BOTTOM) parts.add("bottom");
        else if (vertical == Gravity.TOP) parts.add("top");
        return parts.isEmpty() ? "start" : String.join("|", parts);
    }
}
