package com.besome.sketch.editor.property;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.besome.sketch.beans.ViewBean;

import java.util.ArrayList;
import java.util.List;

import a.a.a.Gx;
import mod.agus.jcoderz.beans.ViewBeans;
import pro.sketchware.R;

/**
 * Android attributes the property panel offers for each widget, beyond the ones ViewBean stores in
 * its own fields. Values live in the widget's {@code inject} attributes (or its parent attributes for
 * layout parameters), which is exactly what the XML generator already writes, so every attribute here
 * reaches the generated layout and the APK.
 */
public final class WidgetAttributes {

    public static final String KEY_PREFIX = "attr:";

    public static final String GROUP_BEHAVIOR = "Behavior";
    public static final String GROUP_ACCESSIBILITY = "Accessibility";
    public static final String GROUP_APPEARANCE = "Appearance";
    public static final String GROUP_SIZE = "Size limits";
    public static final String GROUP_TEXT = "Text style";
    public static final String GROUP_INPUT = "Input";
    public static final String GROUP_IMAGE = "Image";
    public static final String GROUP_CONTAINER = "Container";
    public static final String GROUP_POSITION = "Position in parent";

    public enum Kind {TEXT, STRING, DIMEN, FLOAT, INT, BOOLEAN, ENUM, FLAGS, COLOR, DRAWABLE, FONT, LAYOUT}

    public static final class Spec {
        public final String name;
        public final String label;
        public final String group;
        public final Kind kind;
        public final String[] options;
        @DrawableRes
        public final int icon;
        public final String help;
        /** Stored in the widget's parent attributes (layout parameters) instead of inject. */
        public final boolean layoutParam;

        Spec(String name, String label, String group, Kind kind, String[] options, int icon, String help, boolean layoutParam) {
            this.name = name;
            this.label = label;
            this.group = group;
            this.kind = kind;
            this.options = options;
            this.icon = icon;
            this.help = help;
            this.layoutParam = layoutParam;
        }

        public String key() {
            return KEY_PREFIX + name;
        }

        /** Field used for the string resource of STRING attributes, e.g. textview1_description. */
        public String stringField() {
            return name.substring(name.indexOf(':') + 1).toLowerCase(java.util.Locale.ROOT);
        }
    }

    private WidgetAttributes() {
    }

    private static Spec spec(String name, String label, String group, Kind kind, int icon, String help, String... options) {
        return new Spec(name, label, group, kind, options, icon, help, false);
    }

    private static Spec layoutSpec(String name, String label, Kind kind, int icon, String help, String... options) {
        return new Spec(name, label, GROUP_POSITION, kind, options, icon, help, true);
    }

    @Nullable
    public static Spec find(@NonNull ViewBean bean, @NonNull String key) {
        for (Spec spec : forBean(bean)) {
            if (spec.key().equals(key)) return spec;
        }
        return null;
    }

    /** Attributes that make sense for this widget, following the Android documentation of each class. */
    @NonNull
    public static List<Spec> forBean(@NonNull ViewBean bean) {
        List<Spec> specs = new ArrayList<>();
        if (bean.id == null || bean.id.startsWith("_")) return specs;
        if ("include".equals(bean.convert)) {
            // <include> only takes the layout, visibility and layout parameters.
            specs.add(spec("layout", "Included layout", GROUP_BEHAVIOR, Kind.LAYOUT, R.drawable.ic_mtrl_frame_source,
                    "Layout of this project shown in place of the include."));
            specs.add(spec("android:visibility", "Visibility", GROUP_BEHAVIOR, Kind.ENUM, R.drawable.ic_mtrl_visibility,
                    "gone also removes the space the view takes.", "visible", "invisible", "gone"));
            addConstraintParams(bean, specs);
            return specs;
        }
        Gx info = bean.getClassInfo();
        boolean textView = info.a("TextView");
        boolean editText = info.a("EditText");
        boolean imageView = info.a("ImageView");
        boolean viewGroup = info.a("ViewGroup") || info.a("LinearLayout") || info.a("RelativeLayout")
                || info.a("FrameLayout") || info.a("ScrollView") || info.a("HorizontalScrollView");

        // Behavior
        specs.add(spec("android:visibility", "Visibility", GROUP_BEHAVIOR, Kind.ENUM, R.drawable.ic_mtrl_visibility,
                "gone also removes the space the view takes.", "visible", "invisible", "gone"));
        specs.add(spec("android:focusable", "Focusable", GROUP_BEHAVIOR, Kind.BOOLEAN, R.drawable.ic_mtrl_touch,
                "Whether the view can take focus (keyboard, D-pad, accessibility)."));
        specs.add(spec("android:longClickable", "Long clickable", GROUP_BEHAVIOR, Kind.BOOLEAN, R.drawable.ic_mtrl_touch,
                "Whether the view reacts to long presses."));
        specs.add(spec("android:foreground", "Touch feedback", GROUP_BEHAVIOR, Kind.ENUM, R.drawable.ic_mtrl_touch,
                "Ripple drawn over the view when it's pressed.",
                "?attr/selectableItemBackground", "?attr/selectableItemBackgroundBorderless"));

        // Accessibility
        specs.add(spec("android:contentDescription", "Content description", GROUP_ACCESSIBILITY, Kind.STRING, R.drawable.ic_mtrl_bulb,
                "Read by screen readers. Required for images and icon buttons. Saved in strings.xml."));
        specs.add(spec("android:importantForAccessibility", "Important for accessibility", GROUP_ACCESSIBILITY, Kind.ENUM, R.drawable.ic_mtrl_bulb,
                "no hides a decorative view from screen readers.", "auto", "yes", "no", "noHideDescendants"));
        specs.add(spec("android:tooltipText", "Tooltip", GROUP_ACCESSIBILITY, Kind.STRING, R.drawable.ic_mtrl_bulb,
                "Shown on long press (Android 8.0+). Saved in strings.xml."));

        // Appearance
        specs.add(spec("android:elevation", "Elevation", GROUP_APPEARANCE, Kind.DIMEN, R.drawable.ic_kelivo_layers,
                "Shadow depth, e.g. 4dp."));
        specs.add(spec("android:backgroundTint", "Background tint", GROUP_APPEARANCE, Kind.COLOR, R.drawable.ic_mtrl_palette,
                "Tints the background drawable."));

        // Size limits
        specs.add(spec("android:minWidth", "Minimum width", GROUP_SIZE, Kind.DIMEN, R.drawable.ic_mtrl_width, "e.g. 48dp"));
        specs.add(spec("android:minHeight", "Minimum height", GROUP_SIZE, Kind.DIMEN, R.drawable.ic_mtrl_height, "e.g. 48dp"));
        if (textView || imageView) {
            specs.add(spec("android:maxWidth", "Maximum width", GROUP_SIZE, Kind.DIMEN, R.drawable.ic_mtrl_width, "e.g. 320dp"));
            specs.add(spec("android:maxHeight", "Maximum height", GROUP_SIZE, Kind.DIMEN, R.drawable.ic_mtrl_height, "e.g. 240dp"));
        }

        if (textView) {
            specs.add(spec("android:fontFamily", "Font family", GROUP_TEXT, Kind.FONT, R.drawable.ic_mtrl_font,
                    "System font or a font of the project."));
            specs.add(spec("android:textAllCaps", "All caps", GROUP_TEXT, Kind.BOOLEAN, R.drawable.ic_mtrl_formattext, null));
            specs.add(spec("android:maxLines", "Maximum lines", GROUP_TEXT, Kind.INT, R.drawable.ic_mtrl_formattext, null));
            specs.add(spec("android:minLines", "Minimum lines", GROUP_TEXT, Kind.INT, R.drawable.ic_mtrl_formattext, null));
            specs.add(spec("android:ellipsize", "Ellipsize", GROUP_TEXT, Kind.ENUM, R.drawable.ic_mtrl_formattext,
                    "Where … goes when the text doesn't fit. Needs maximum lines.", "none", "start", "middle", "end", "marquee"));
            specs.add(spec("android:textAlignment", "Text alignment", GROUP_TEXT, Kind.ENUM, R.drawable.ic_mtrl_formattext, null,
                    "inherit", "gravity", "textStart", "textEnd", "center", "viewStart", "viewEnd"));
            specs.add(spec("android:letterSpacing", "Letter spacing", GROUP_TEXT, Kind.FLOAT, R.drawable.ic_mtrl_formattext,
                    "In em, e.g. 0.05"));
            specs.add(spec("android:lineSpacingExtra", "Line spacing extra", GROUP_TEXT, Kind.DIMEN, R.drawable.ic_mtrl_formattext, "e.g. 4dp"));
            specs.add(spec("android:lineSpacingMultiplier", "Line spacing multiplier", GROUP_TEXT, Kind.FLOAT, R.drawable.ic_mtrl_formattext, "e.g. 1.2"));
            specs.add(spec("android:includeFontPadding", "Include font padding", GROUP_TEXT, Kind.BOOLEAN, R.drawable.ic_mtrl_formattext, null));
            specs.add(spec("android:textIsSelectable", "Selectable text", GROUP_TEXT, Kind.BOOLEAN, R.drawable.ic_mtrl_formattext, null));
            specs.add(spec("android:autoLink", "Auto link", GROUP_TEXT, Kind.FLAGS, R.drawable.ic_mtrl_web,
                    "Turns URLs, e-mails or phone numbers into links.", "web", "email", "phone", "map", "all"));
            specs.add(spec("android:drawableStart", "Drawable start", GROUP_TEXT, Kind.DRAWABLE, R.drawable.ic_mtrl_image, null));
            specs.add(spec("android:drawableEnd", "Drawable end", GROUP_TEXT, Kind.DRAWABLE, R.drawable.ic_mtrl_image, null));
            specs.add(spec("android:drawableTop", "Drawable top", GROUP_TEXT, Kind.DRAWABLE, R.drawable.ic_mtrl_image, null));
            specs.add(spec("android:drawableBottom", "Drawable bottom", GROUP_TEXT, Kind.DRAWABLE, R.drawable.ic_mtrl_image, null));
            specs.add(spec("android:drawablePadding", "Drawable padding", GROUP_TEXT, Kind.DIMEN, R.drawable.ic_mtrl_image, "e.g. 8dp"));
            specs.add(spec("android:drawableTint", "Drawable tint", GROUP_TEXT, Kind.COLOR, R.drawable.ic_mtrl_palette, null));
        }

        if (editText) {
            specs.add(spec("android:maxLength", "Maximum length", GROUP_INPUT, Kind.INT, R.drawable.ic_mtrl_edittext, null));
            specs.add(spec("android:autofillHints", "Autofill hint", GROUP_INPUT, Kind.ENUM, R.drawable.ic_mtrl_edittext,
                    "Lets password managers and autofill fill this field.",
                    "emailAddress", "password", "newPassword", "username", "name", "phone", "postalAddress", "postalCode", "creditCardNumber"));
            specs.add(spec("android:importantForAutofill", "Important for autofill", GROUP_INPUT, Kind.ENUM, R.drawable.ic_mtrl_edittext, null,
                    "auto", "yes", "no"));
            specs.add(spec("android:selectAllOnFocus", "Select all on focus", GROUP_INPUT, Kind.BOOLEAN, R.drawable.ic_mtrl_edittext, null));
        }

        if (imageView) {
            specs.add(spec("android:tint", "Image tint", GROUP_IMAGE, Kind.COLOR, R.drawable.ic_mtrl_palette, null));
            specs.add(spec("android:adjustViewBounds", "Adjust view bounds", GROUP_IMAGE, Kind.BOOLEAN, R.drawable.ic_mtrl_image,
                    "Keeps the image's aspect ratio when one side is wrap_content."));
        }

        if (info.a("CompoundButton")) {
            specs.add(spec("android:buttonTint", "Button tint", GROUP_APPEARANCE, Kind.COLOR, R.drawable.ic_mtrl_palette, null));
        }
        if (info.b("Switch")) {
            specs.add(spec("android:thumbTint", "Thumb tint", GROUP_APPEARANCE, Kind.COLOR, R.drawable.ic_mtrl_palette, null));
            specs.add(spec("android:trackTint", "Track tint", GROUP_APPEARANCE, Kind.COLOR, R.drawable.ic_mtrl_palette, null));
        }
        if (info.b("SeekBar") || info.b("ProgressBar")) {
            specs.add(spec("android:progressTint", "Progress tint", GROUP_APPEARANCE, Kind.COLOR, R.drawable.ic_mtrl_palette, null));
            specs.add(spec("android:progressBackgroundTint", "Progress background tint", GROUP_APPEARANCE, Kind.COLOR, R.drawable.ic_mtrl_palette, null));
        }
        if (info.b("ProgressBar")) {
            specs.add(spec("android:indeterminateTint", "Indeterminate tint", GROUP_APPEARANCE, Kind.COLOR, R.drawable.ic_mtrl_palette, null));
        }
        if (info.b("SeekBar")) {
            specs.add(spec("android:thumbTint", "Thumb tint", GROUP_APPEARANCE, Kind.COLOR, R.drawable.ic_mtrl_palette, null));
            specs.add(spec("android:min", "Minimum", GROUP_BEHAVIOR, Kind.INT, R.drawable.ic_mtrl_seekbar, "Android 8.0+"));
        }

        if (info.a("LinearLayout")) {
            specs.add(spec("android:baselineAligned", "Baseline aligned", GROUP_CONTAINER, Kind.BOOLEAN, R.drawable.ic_mtrl_view_horizontal,
                    "false avoids extra measuring when children use weights."));
            specs.add(spec("android:divider", "Divider", GROUP_CONTAINER, Kind.DRAWABLE, R.drawable.ic_mtrl_view_horizontal, null));
            specs.add(spec("android:showDividers", "Show dividers", GROUP_CONTAINER, Kind.FLAGS, R.drawable.ic_mtrl_view_horizontal, null,
                    "none", "beginning", "middle", "end"));
            specs.add(spec("android:dividerPadding", "Divider padding", GROUP_CONTAINER, Kind.DIMEN, R.drawable.ic_mtrl_view_horizontal, null));
        }
        if (info.a("ScrollView") || info.a("HorizontalScrollView") || bean.type == ViewBean.VIEW_TYPE_LAYOUT_VSCROLLVIEW
                || bean.type == ViewBean.VIEW_TYPE_LAYOUT_HSCROLLVIEW) {
            specs.add(spec("android:fillViewport", "Fill viewport", GROUP_CONTAINER, Kind.BOOLEAN, R.drawable.ic_mtrl_swap_vertical,
                    "Stretches the content to fill the screen when it's shorter."));
        }
        if (viewGroup || info.b("ListView") || info.b("GridView") || info.b("RecyclerView")) {
            specs.add(spec("android:clipToPadding", "Clip to padding", GROUP_CONTAINER, Kind.BOOLEAN, R.drawable.ic_mtrl_view_horizontal,
                    "false lets scrolling content run under the padding."));
            specs.add(spec("android:clipChildren", "Clip children", GROUP_CONTAINER, Kind.BOOLEAN, R.drawable.ic_mtrl_view_horizontal, null));
            specs.add(spec("android:animateLayoutChanges", "Animate layout changes", GROUP_CONTAINER, Kind.BOOLEAN, R.drawable.ic_mtrl_view_horizontal, null));
        }
        if (info.a("ScrollView") || info.a("HorizontalScrollView") || info.b("ListView") || info.b("GridView")
                || info.b("RecyclerView") || bean.type == ViewBean.VIEW_TYPE_LAYOUT_VSCROLLVIEW || bean.type == ViewBean.VIEW_TYPE_LAYOUT_HSCROLLVIEW) {
            specs.add(spec("android:scrollbars", "Scrollbars", GROUP_CONTAINER, Kind.ENUM, R.drawable.ic_mtrl_swap_vertical, null,
                    "none", "vertical", "horizontal"));
            specs.add(spec("android:overScrollMode", "Overscroll mode", GROUP_CONTAINER, Kind.ENUM, R.drawable.ic_mtrl_swap_vertical, null,
                    "always", "ifContentScrolls", "never"));
        }

        addConstraintParams(bean, specs);
        return specs;
    }

    private static void addConstraintParams(@NonNull ViewBean bean, @NonNull List<Spec> specs) {
        if (bean.parentType == ViewBeans.VIEW_TYPE_LAYOUT_CONSTRAINTLAYOUT) {
            specs.add(layoutSpec("app:layout_constraintHorizontal_bias", "Horizontal bias", Kind.FLOAT, R.drawable.ic_mtrl_view_relative,
                    "0 = start, 0.5 = centre, 1 = end. Needs start and end constraints."));
            specs.add(layoutSpec("app:layout_constraintVertical_bias", "Vertical bias", Kind.FLOAT, R.drawable.ic_mtrl_view_relative,
                    "0 = top, 0.5 = centre, 1 = bottom. Needs top and bottom constraints."));
            specs.add(layoutSpec("app:layout_constraintDimensionRatio", "Dimension ratio", Kind.TEXT, R.drawable.ic_mtrl_view_relative,
                    "e.g. 16:9, with width or height set to 0dp (match constraint)."));
            specs.add(layoutSpec("app:layout_constraintHorizontal_chainStyle", "Horizontal chain style", Kind.ENUM, R.drawable.ic_mtrl_view_relative,
                    "Applies to the first view of a horizontal chain.", "spread", "spread_inside", "packed"));
            specs.add(layoutSpec("app:layout_constraintVertical_chainStyle", "Vertical chain style", Kind.ENUM, R.drawable.ic_mtrl_view_relative,
                    "Applies to the first view of a vertical chain.", "spread", "spread_inside", "packed"));
            specs.add(layoutSpec("app:layout_constraintWidth_percent", "Width percent", Kind.FLOAT, R.drawable.ic_mtrl_width,
                    "0..1 of the parent, with width 0dp."));
            specs.add(layoutSpec("app:layout_constraintHeight_percent", "Height percent", Kind.FLOAT, R.drawable.ic_mtrl_height,
                    "0..1 of the parent, with height 0dp."));
        }
    }
}
