package pro.sketchware.ia.layout;

import com.besome.sketch.beans.ViewBean;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import pro.sketchware.tools.ViewBeanParser;

/** Component catalog derived from Sketchware's ViewBean type registry. */
public final class LayoutComponentRegistry {
    private final Set<String> components = new LinkedHashSet<>();

    public LayoutComponentRegistry() {
        for (int type = 0; type < 64; type++) {
            try {
                String name = ViewBean.getViewTypeName(type);
                if (name != null && !name.trim().isEmpty() && !"unknown".equalsIgnoreCase(name)) {
                    components.add(normalize(name));
                }
            } catch (Exception ignored) {
            }
        }
        Collections.addAll(components,
                "LinearLayout", "RelativeLayout", "ScrollView", "HorizontalScrollView",
                "CardView", "MaterialCardView", "NestedScrollView", "SwipeRefreshLayout",
                "TextInputLayout", "TextInputEditText", "MaterialButton", "MaterialSwitch",
                "RecyclerView", "ImageButton", "CircleImageView", "LottieAnimationView",
                "TabLayout", "BottomNavigationView", "RadioGroup", "RadioButton", "GridView");
    }

    public boolean supports(String tag) {
        String normalized = normalize(ViewBeanParser.getNameFromTag(tag == null ? "" : tag));
        if (normalized.isEmpty()) return false;
        for (String component : components) {
            if (component.equalsIgnoreCase(normalized)) return true;
        }
        return false;
    }

    public Set<String> all() {
        return Collections.unmodifiableSet(components);
    }

    public String promptCatalog() {
        return String.join(", ", components);
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String result = value.trim();
        if ("VScrollView".equalsIgnoreCase(result)) return "ScrollView";
        if ("HScrollView".equalsIgnoreCase(result)) return "HorizontalScrollView";
        if (result.toLowerCase(Locale.US).startsWith("view_type_")) return "";
        return result;
    }
}
