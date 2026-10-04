package pro.sketchware.widgets;

import android.view.Gravity;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.besome.sketch.beans.LayoutBean;
import com.besome.sketch.beans.ViewBean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import mod.agus.jcoderz.beans.ViewBeans;
import pro.sketchware.R;

/**
 * Standard Android components added to the palette in 9.0. Each entry builds the same ViewBean a
 * user gets by dragging it, so the palette and the AI layout generator create identical widgets.
 */
public final class BuiltInWidgets {

    public enum Requirement {
        /** Plain Android framework class. */
        NONE,
        /** AndroidX/Material libraries, enabled with AppCompat in the project's library settings. */
        APPCOMPAT,
        /** A Material 3 theme (Material 3 enabled in the project's library settings). */
        MATERIAL3
    }

    public enum Section {LAYOUTS, ANDROIDX_LAYOUTS, WIDGETS, MATERIAL_WIDGETS}

    public static final class Preset {
        /** Name shown in the palette and used by the AI generator, e.g. "ConstraintLayout". */
        public final String name;
        /** Prefix of generated ids, e.g. "constraintlayout" for constraintlayout1. */
        public final String idPrefix;
        @DrawableRes
        public final int icon;
        public final Section section;
        public final Requirement requirement;
        public final boolean container;
        public final String description;
        private final Supplier<ViewBean> factory;

        Preset(String name, String idPrefix, int icon, Section section, Requirement requirement,
               boolean container, String description, Supplier<ViewBean> factory) {
            this.name = name;
            this.idPrefix = idPrefix;
            this.icon = icon;
            this.section = section;
            this.requirement = requirement;
            this.container = container;
            this.description = description;
            this.factory = factory;
        }

        @NonNull
        public ViewBean create() {
            return factory.get();
        }

        public boolean isAvailable(boolean appCompatEnabled, boolean material3Enabled) {
            return switch (requirement) {
                case NONE -> true;
                case APPCOMPAT -> appCompatEnabled;
                case MATERIAL3 -> appCompatEnabled && material3Enabled;
            };
        }
    }

    private static final List<Preset> PRESETS;

    static {
        List<Preset> presets = new ArrayList<>();
        presets.add(new Preset("FrameLayout", "framelayout", R.drawable.ic_mtrl_rectangle, Section.LAYOUTS, Requirement.NONE, true,
                "Stacks children on top of each other; place them with layout_gravity.", () -> {
            ViewBean bean = new ViewBean();
            bean.type = ViewBeans.VIEW_TYPE_LAYOUT_FRAMELAYOUT;
            bean.convert = "FrameLayout";
            bean.layout.width = ViewGroup.LayoutParams.MATCH_PARENT;
            padding(bean.layout, 8);
            return bean;
        }));
        presets.add(new Preset("Include", "include", R.drawable.ic_mtrl_frame_source, Section.LAYOUTS, Requirement.NONE, false,
                "Reuses another layout of the project with <include>.", () -> {
            ViewBean bean = new ViewBean();
            bean.type = ViewBeans.VIEW_TYPE_LAYOUT_INCLUDE;
            bean.convert = "include";
            bean.layout.width = ViewGroup.LayoutParams.MATCH_PARENT;
            return bean;
        }));
        presets.add(new Preset("ConstraintLayout", "constraintlayout", R.drawable.ic_mtrl_view_relative, Section.ANDROIDX_LAYOUTS, Requirement.APPCOMPAT, true,
                "Positions children with constraints to the parent and to each other.", () -> {
            ViewBean bean = new ViewBean();
            bean.type = ViewBeans.VIEW_TYPE_LAYOUT_CONSTRAINTLAYOUT;
            bean.convert = "androidx.constraintlayout.widget.ConstraintLayout";
            bean.layout.width = ViewGroup.LayoutParams.MATCH_PARENT;
            bean.layout.height = 240;
            padding(bean.layout, 8);
            return bean;
        }));
        presets.add(new Preset("NestedScrollView", "nestedscroll", R.drawable.ic_mtrl_swap_vertical, Section.ANDROIDX_LAYOUTS, Requirement.APPCOMPAT, true,
                "Vertical scrolling container that cooperates with app bars and nested scrolling.", () -> {
            ViewBean bean = new ViewBean();
            bean.type = ViewBean.VIEW_TYPE_LAYOUT_VSCROLLVIEW;
            bean.convert = "androidx.core.widget.NestedScrollView";
            bean.layout.width = ViewGroup.LayoutParams.MATCH_PARENT;
            bean.layout.height = ViewGroup.LayoutParams.MATCH_PARENT;
            padding(bean.layout, 8);
            return bean;
        }));
        presets.add(new Preset("MaterialCardView", "materialcard", R.drawable.ic_mtrl_rectangle, Section.ANDROIDX_LAYOUTS, Requirement.APPCOMPAT, true,
                "Material card with corner radius, stroke and elevation.", () -> {
            ViewBean bean = new ViewBean();
            bean.type = ViewBeans.VIEW_TYPE_LAYOUT_CARDVIEW;
            bean.convert = "com.google.android.material.card.MaterialCardView";
            bean.layout.width = ViewGroup.LayoutParams.MATCH_PARENT;
            bean.layout.orientation = LayoutBean.ORIENTATION_VERTICAL;
            padding(bean.layout, 12);
            bean.inject = "app:cardCornerRadius=\"12dp\"\napp:cardElevation=\"2dp\"";
            return bean;
        }));
        presets.add(new Preset("ImageButton", "imagebutton", R.drawable.ic_mtrl_image, Section.WIDGETS, Requirement.NONE, false,
                "Clickable icon. Give it a content description for screen readers.", () -> {
            ViewBean bean = new ViewBean();
            bean.type = ViewBean.VIEW_TYPE_WIDGET_IMAGEVIEW;
            bean.convert = "ImageButton";
            bean.image.resName = "default_image";
            bean.layout.width = 48;
            bean.layout.height = 48;
            padding(bean.layout, 12);
            bean.inject = "android:background=\"?attr/selectableItemBackgroundBorderless\"";
            return bean;
        }));
        presets.add(new Preset("View", "view", R.drawable.ic_mtrl_rectangle, Section.WIDGETS, Requirement.NONE, false,
                "Plain rectangle, useful as a background shape or spacer with a color.", () -> {
            ViewBean bean = new ViewBean();
            bean.type = ViewBeans.VIEW_TYPE_WIDGET_VIEW;
            bean.convert = "View";
            bean.layout.width = 48;
            bean.layout.height = 48;
            bean.layout.backgroundColor = 0xFFE0E0E0;
            return bean;
        }));
        presets.add(new Preset("Divider", "divider", R.drawable.ic_mtrl_view_horizontal, Section.WIDGETS, Requirement.NONE, false,
                "Thin horizontal line between groups of content.", () -> {
            ViewBean bean = new ViewBean();
            bean.type = ViewBeans.VIEW_TYPE_WIDGET_VIEW;
            bean.convert = "View";
            bean.layout.width = ViewGroup.LayoutParams.MATCH_PARENT;
            bean.layout.height = 1;
            bean.layout.marginTop = 8;
            bean.layout.marginBottom = 8;
            bean.layout.backgroundColor = 0x1F000000;
            return bean;
        }));
        presets.add(new Preset("Space", "space", R.drawable.ic_mtrl_view_horizontal, Section.WIDGETS, Requirement.NONE, false,
                "Empty gap of a fixed size.", () -> {
            ViewBean bean = new ViewBean();
            bean.type = ViewBeans.VIEW_TYPE_WIDGET_VIEW;
            bean.convert = "Space";
            bean.layout.width = 16;
            bean.layout.height = 16;
            return bean;
        }));
        presets.add(new Preset("TextInputEditText", "textinput", R.drawable.ic_mtrl_edittext, Section.MATERIAL_WIDGETS, Requirement.APPCOMPAT, false,
                "Text field for a TextInputLayout; put it inside one to get the floating hint.", () -> {
            ViewBean bean = new ViewBean();
            bean.type = ViewBean.VIEW_TYPE_WIDGET_EDITTEXT;
            bean.convert = "com.google.android.material.textfield.TextInputEditText";
            bean.layout.width = ViewGroup.LayoutParams.MATCH_PARENT;
            bean.text.hint = "Text";
            bean.text.text = "";
            return bean;
        }));
        presets.add(new Preset("MaterialSwitch", "materialswitch", R.drawable.ic_mtrl_toggle, Section.MATERIAL_WIDGETS, Requirement.MATERIAL3, false,
                "Material 3 switch. Needs Material 3 enabled in the project.", () -> {
            ViewBean bean = new ViewBean();
            bean.type = ViewBean.VIEW_TYPE_WIDGET_SWITCH;
            bean.convert = "com.google.android.material.materialswitch.MaterialSwitch";
            bean.layout.width = ViewGroup.LayoutParams.MATCH_PARENT;
            bean.text.text = "Switch";
            bean.layout.gravity = Gravity.CENTER_VERTICAL;
            return bean;
        }));
        PRESETS = Collections.unmodifiableList(presets);
    }

    private BuiltInWidgets() {
    }

    private static void padding(LayoutBean layout, int dp) {
        layout.paddingLeft = dp;
        layout.paddingTop = dp;
        layout.paddingRight = dp;
        layout.paddingBottom = dp;
    }

    @NonNull
    public static List<Preset> all() {
        return PRESETS;
    }

    @NonNull
    public static List<Preset> inSection(@NonNull Section section) {
        List<Preset> result = new ArrayList<>();
        for (Preset preset : PRESETS) {
            if (preset.section == section) result.add(preset);
        }
        return result;
    }

    @Nullable
    public static Preset find(@NonNull String name) {
        for (Preset preset : PRESETS) {
            if (preset.name.equalsIgnoreCase(name)) return preset;
        }
        return null;
    }
}
