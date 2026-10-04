package pro.sketchware.ia.layout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import pro.sketchware.widgets.BuiltInWidgets;

/**
 * Components the AI may use, with the XML tag the editor's palette uses for each one, so generated
 * widgets become the same ViewBeans as dragged ones. Built from the palette and
 * {@link BuiltInWidgets}, filtered by the libraries the project has enabled.
 */
public final class LayoutComponentRegistry {

    public enum Kind {
        /** Has no children. */
        WIDGET,
        /** Any number of children. */
        CONTAINER,
        /** Exactly one child (scroll views). */
        SINGLE_CHILD
    }

    public static final class Component {
        public final String name;
        public final String tag;
        public final Kind kind;
        public final BuiltInWidgets.Requirement requirement;
        public final String notes;

        Component(String name, String tag, Kind kind, BuiltInWidgets.Requirement requirement, String notes) {
            this.name = name;
            this.tag = tag;
            this.kind = kind;
            this.requirement = requirement;
            this.notes = notes;
        }

        public boolean isContainer() {
            return kind != Kind.WIDGET;
        }

        public boolean isLinear() {
            return name.equals("LinearLayout") || name.equals("RadioGroup");
        }

        public boolean isRelative() {
            return name.equals("RelativeLayout");
        }

        public boolean isConstraint() {
            return name.equals("ConstraintLayout");
        }

        public boolean isTextual() {
            return switch (name) {
                case "TextView", "Button", "MaterialButton", "EditText", "TextInputEditText", "CheckBox",
                     "RadioButton", "Switch", "MaterialSwitch", "AutoCompleteTextView", "MultiAutoCompleteTextView" -> true;
                default -> false;
            };
        }

        public boolean acceptsHint() {
            return switch (name) {
                case "EditText", "TextInputEditText", "AutoCompleteTextView", "MultiAutoCompleteTextView" -> true;
                default -> false;
            };
        }

        public boolean isImage() {
            return switch (name) {
                case "ImageView", "ImageButton", "CircleImageView" -> true;
                default -> false;
            };
        }
    }

    private static final BuiltInWidgets.Requirement NONE = BuiltInWidgets.Requirement.NONE;
    private static final BuiltInWidgets.Requirement APPCOMPAT = BuiltInWidgets.Requirement.APPCOMPAT;

    private final Map<String, Component> components = new LinkedHashMap<>();

    public LayoutComponentRegistry(boolean appCompatEnabled, boolean material3Enabled) {
        List<Component> all = new ArrayList<>();
        // Layouts
        all.add(new Component("LinearLayout", "LinearLayout", Kind.CONTAINER, NONE, "stacks children; set orientation vertical or horizontal"));
        all.add(new Component("RelativeLayout", "RelativeLayout", Kind.CONTAINER, NONE, "children use \"relative\" rules"));
        all.add(new Component("ConstraintLayout", "androidx.constraintlayout.widget.ConstraintLayout", Kind.CONTAINER, APPCOMPAT, "children use \"constraints\""));
        all.add(new Component("FrameLayout", "FrameLayout", Kind.CONTAINER, NONE, "children overlap; place them with layoutGravity"));
        all.add(new Component("ScrollView", "ScrollView", Kind.SINGLE_CHILD, NONE, "vertical scrolling, exactly one child (usually a vertical LinearLayout)"));
        all.add(new Component("NestedScrollView", "androidx.core.widget.NestedScrollView", Kind.SINGLE_CHILD, APPCOMPAT, "like ScrollView, exactly one child"));
        all.add(new Component("HorizontalScrollView", "HorizontalScrollView", Kind.SINGLE_CHILD, NONE, "horizontal scrolling, exactly one child"));
        all.add(new Component("CardView", "androidx.cardview.widget.CardView", Kind.CONTAINER, APPCOMPAT, "card; usually one LinearLayout child"));
        all.add(new Component("MaterialCardView", "com.google.android.material.card.MaterialCardView", Kind.CONTAINER, APPCOMPAT, "Material card; usually one LinearLayout child"));
        all.add(new Component("RadioGroup", "RadioGroup", Kind.CONTAINER, NONE, "holds RadioButtons; set orientation"));
        all.add(new Component("TextInputLayout", "com.google.android.material.textfield.TextInputLayout", Kind.SINGLE_CHILD, APPCOMPAT, "floating label; exactly one TextInputEditText child; put the hint on the child"));
        all.add(new Component("SwipeRefreshLayout", "androidx.swiperefreshlayout.widget.SwipeRefreshLayout", Kind.SINGLE_CHILD, APPCOMPAT, "pull to refresh around one scrolling child"));
        // Widgets
        all.add(new Component("TextView", "TextView", Kind.WIDGET, NONE, "text"));
        all.add(new Component("Button", "Button", Kind.WIDGET, NONE, "text"));
        all.add(new Component("MaterialButton", "com.google.android.material.button.MaterialButton", Kind.WIDGET, APPCOMPAT, "text"));
        all.add(new Component("EditText", "EditText", Kind.WIDGET, NONE, "hint, inputType through attributes"));
        all.add(new Component("TextInputEditText", "com.google.android.material.textfield.TextInputEditText", Kind.WIDGET, APPCOMPAT, "child of TextInputLayout; hint"));
        all.add(new Component("ImageView", "ImageView", Kind.WIDGET, NONE, "src"));
        all.add(new Component("ImageButton", "ImageButton", Kind.WIDGET, NONE, "src, needs a contentDescription attribute"));
        all.add(new Component("CircleImageView", "de.hdodenhof.circleimageview.CircleImageView", Kind.WIDGET, APPCOMPAT, "round image; src"));
        all.add(new Component("CheckBox", "CheckBox", Kind.WIDGET, NONE, "text"));
        all.add(new Component("RadioButton", "RadioButton", Kind.WIDGET, NONE, "text; inside a RadioGroup"));
        all.add(new Component("Switch", "Switch", Kind.WIDGET, NONE, "text"));
        all.add(new Component("SeekBar", "SeekBar", Kind.WIDGET, NONE, ""));
        all.add(new Component("ProgressBar", "ProgressBar", Kind.WIDGET, NONE, ""));
        all.add(new Component("RatingBar", "RatingBar", Kind.WIDGET, NONE, ""));
        all.add(new Component("Spinner", "Spinner", Kind.WIDGET, NONE, "items are set in code"));
        all.add(new Component("ListView", "ListView", Kind.WIDGET, NONE, "items are set in code"));
        all.add(new Component("GridView", "GridView", Kind.WIDGET, NONE, "items are set in code"));
        all.add(new Component("RecyclerView", "androidx.recyclerview.widget.RecyclerView", Kind.WIDGET, APPCOMPAT, "items are set in code"));
        all.add(new Component("WebView", "WebView", Kind.WIDGET, NONE, ""));
        all.add(new Component("SearchView", "SearchView", Kind.WIDGET, NONE, ""));
        all.add(new Component("CalendarView", "CalendarView", Kind.WIDGET, NONE, ""));
        all.add(new Component("AutoCompleteTextView", "AutoCompleteTextView", Kind.WIDGET, NONE, "hint"));
        all.add(new Component("TabLayout", "com.google.android.material.tabs.TabLayout", Kind.WIDGET, APPCOMPAT, "tabs are set in code"));
        all.add(new Component("BottomNavigationView", "com.google.android.material.bottomnavigation.BottomNavigationView", Kind.WIDGET, APPCOMPAT, "menu is set in code"));
        all.add(new Component("View", "View", Kind.WIDGET, NONE, "plain rectangle or divider; give it a background"));
        all.add(new Component("Space", "Space", Kind.WIDGET, NONE, "empty gap"));
        all.add(new Component("include", "include", Kind.WIDGET, NONE, "reuses another layout of the project: set \"layout\""));
        if (material3Enabled) {
            all.add(new Component("MaterialSwitch", "com.google.android.material.materialswitch.MaterialSwitch", Kind.WIDGET, APPCOMPAT, "text"));
        }
        for (Component component : all) {
            if (component.requirement == APPCOMPAT && !appCompatEnabled) continue;
            components.put(component.name.toLowerCase(Locale.ROOT), component);
        }
    }

    /** Lets the AI keep a component of the current layout that the catalog doesn't offer. */
    public void allow(@NonNull String name, @NonNull String tag, @NonNull Kind kind) {
        components.putIfAbsent(name.toLowerCase(Locale.ROOT), new Component(name, tag, kind, NONE, "already in the current layout; keep it"));
    }

    @Nullable
    public Component find(@Nullable String name) {
        if (name == null) return null;
        String key = name.trim();
        int dot = key.lastIndexOf('.');
        if (dot >= 0) key = key.substring(dot + 1);
        key = key.toLowerCase(Locale.ROOT);
        if (key.equals("vscrollview")) key = "scrollview";
        if (key.equals("hscrollview")) key = "horizontalscrollview";
        return components.get(key);
    }

    @NonNull
    public List<Component> all() {
        return Collections.unmodifiableList(new ArrayList<>(components.values()));
    }

    /** One line per component for the prompt. */
    @NonNull
    public String promptCatalog() {
        StringBuilder out = new StringBuilder();
        for (Component component : components.values()) {
            out.append("- ").append(component.name);
            switch (component.kind) {
                case CONTAINER -> out.append(" [container]");
                case SINGLE_CHILD -> out.append(" [container, exactly 1 child]");
                default -> {
                }
            }
            if (!component.notes.isEmpty()) out.append(": ").append(component.notes);
            out.append('\n');
        }
        return out.toString();
    }
}
