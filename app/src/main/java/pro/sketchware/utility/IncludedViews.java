package pro.sketchware.utility;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import a.a.a.eC;
import a.a.a.hC;
import a.a.a.jC;
import mod.agus.jcoderz.beans.ViewBeans;
import mod.pranav.viewbinding.ViewBindingBuilder;

/**
 * The widgets a screen reaches through its {@code <include>} tags, and the one name the logic editor, the event
 * list and the Java generator use for each of them.
 * <p>
 * Modeled on Android Studio's view binding ({@code BindingXmlIndex} and {@code LightBindingClass}): an
 * {@code <include>} with an id becomes a field holding the included layout's views, so its children are reached
 * through that instance ({@code binding.include1.button1}), and an {@code <include>} without an id exposes nothing.
 * Here the instance and the child make one name, {@code include1_button1}: two includes of the same layout never
 * share a widget even when their children have the same ids, and the widget is looked up inside its own instance
 * ({@code findViewById(R.id.include1).findViewById(R.id.button1)}) instead of somewhere in the whole screen.
 * <p>
 * The included layout stays the only owner of its widgets: entries are copies, built again from the project data
 * each time, and never saved.
 */
public final class IncludedViews {

    /** The widgets of a layout of the project, or null when there's no such layout. */
    public interface LayoutSource {
        @Nullable
        List<ViewBean> views(@NonNull String xmlName);
    }

    /** A widget reached through one or more includes. */
    public static final class Entry {
        /** Name of the Java field and of the widget in the logic editor, e.g. {@code include1_button1}. */
        @NonNull
        public final String id;
        /** Ids from the outermost include to the widget, e.g. [include1, include3, textview1]. */
        @NonNull
        public final List<String> path;
        /** Layout each id of {@link #path} belongs to, e.g. [main.xml, cabecalho.xml, rodape.xml]. */
        @NonNull
        public final List<String> layouts;
        /** The widget as it is in its own layout. Read only: it belongs to that layout. */
        @NonNull
        public final ViewBean source;
        /** A copy of {@link #source} named {@link #id}, for the code that works with the screen's widgets. */
        @NonNull
        public final ViewBean view;

        Entry(@NonNull List<String> path, @NonNull List<String> layouts, @NonNull ViewBean source) {
            this.path = Collections.unmodifiableList(new ArrayList<>(path));
            this.layouts = Collections.unmodifiableList(new ArrayList<>(layouts));
            this.id = nameOf(path);
            this.source = source;
            ViewBean copy = source.clone();
            copy.id = id;
            copy.preId = id;
            this.view = copy;
        }

        /** The include instance (the screen's own include) the widget belongs to. */
        @NonNull
        public String includeId() {
            return path.get(0);
        }

        /** Expression finding the widget inside its instance, from {@code root} ("" for the activity). */
        @NonNull
        public String lookupExpression(@NonNull String root) {
            StringBuilder expression = new StringBuilder(root.isEmpty() ? "" : root + ".");
            for (int i = 0; i < path.size(); i++) {
                if (i > 0) expression.append('.');
                expression.append("findViewById(R.id.").append(path.get(i)).append(')');
            }
            return expression.toString();
        }

        /** The same widget through the generated view binding classes, e.g. {@code binding.include1.button1}. */
        @NonNull
        public String bindingExpression() {
            StringBuilder expression = new StringBuilder("binding");
            for (String segment : path) {
                expression.append('.').append(ViewBindingBuilder.generateParameterFromId(segment));
            }
            return expression.toString();
        }

        /**
         * Whether the screen reaches this widget through one of {@code ids} of the layout {@code xml}: an include on
         * the way (at any depth) or the widget itself in its own layout.
         */
        public boolean reachedThrough(@NonNull String xml, @NonNull Set<String> ids) {
            for (int i = 0; i < path.size(); i++) {
                if (layouts.get(i).equals(xml) && ids.contains(path.get(i))) return true;
            }
            return false;
        }

        /** Whether the screen reaches this widget through one of the layouts {@code xmls} (not the screen's own). */
        public boolean reachedThroughLayouts(@NonNull Set<String> xmls) {
            for (int i = 1; i < layouts.size(); i++) {
                if (xmls.contains(layouts.get(i))) return true;
            }
            return false;
        }
    }

    /** Why an include's widgets are left out: shown to the user rather than generating a wrong reference. */
    public static final class Problem {
        public enum Kind {MISSING_LAYOUT, CYCLE, NAME_TAKEN}

        @NonNull
        public final Kind kind;
        /** Ids from the outermost include to where it happened. */
        @NonNull
        public final List<String> path;
        /** The missing or cyclic layout, or the name that was already taken. */
        @NonNull
        public final String detail;

        Problem(@NonNull Kind kind, @NonNull List<String> path, @NonNull String detail) {
            this.kind = kind;
            this.path = Collections.unmodifiableList(new ArrayList<>(path));
            this.detail = detail;
        }
    }

    public static final class Result {
        @NonNull
        public final List<Entry> entries;
        @NonNull
        public final List<Problem> problems;

        Result(@NonNull List<Entry> entries, @NonNull List<Problem> problems) {
            this.entries = Collections.unmodifiableList(entries);
            this.problems = Collections.unmodifiableList(problems);
        }

        @Nullable
        public Entry find(@Nullable String id) {
            if (id == null) return null;
            for (Entry entry : entries) {
                if (entry.id.equals(id)) return entry;
            }
            return null;
        }

        /**
         * How the screen's code refers to the widget {@code id} when it comes from an include: its field, or with
         * view binding the chain of binding fields. Null when {@code id} isn't reached through an include.
         */
        @Nullable
        public String reference(@Nullable String id, boolean viewBinding) {
            Entry entry = find(id);
            if (entry == null) return null;
            return viewBinding ? entry.bindingExpression() : entry.id;
        }

        /** The entry the generated code refers to as {@code reference} (see {@link #reference}). */
        @Nullable
        public Entry findByReference(@Nullable String reference, boolean viewBinding) {
            if (reference == null) return null;
            for (Entry entry : entries) {
                if (reference.equals(viewBinding ? entry.bindingExpression() : entry.id)) return entry;
            }
            return null;
        }

        /** The entries reached through the screen's include {@code includeId}. */
        @NonNull
        public List<Entry> under(@NonNull String includeId) {
            List<Entry> result = new ArrayList<>();
            for (Entry entry : entries) {
                if (entry.includeId().equals(includeId)) result.add(entry);
            }
            return result;
        }
    }

    private IncludedViews() {
    }

    /** The widgets the screen {@code screenXml} reaches through its includes, from the project's own layouts. */
    @NonNull
    public static Result resolve(@NonNull eC data, @NonNull String screenXml) {
        Set<String> layouts = layouts(data.a);
        return resolve(screenXml, data.d(screenXml), xml -> layouts.contains(xml) ? data.d(xml) : null);
    }

    /**
     * Whether the custom view {@code fileName} is only used through includes, as part of a screen and never as the
     * item of a list. Sketchware writes the widgets of an item layout with {@code android:focusable="false"} so that
     * the list gets the item clicks; in an included layout that leaves text fields nobody can type in. Android
     * Studio adds no such attribute: a layout's widgets keep their own focus.
     */
    public static boolean isIncludedOnly(@NonNull eC data, @NonNull String scId, @NonNull String fileName) {
        hC files = jC.b(scId);
        List<String> layouts = new ArrayList<>();
        for (ProjectFileBean file : files.b()) {
            layouts.add(file.getXmlName());
            if (file.hasActivityOption(ProjectFileBean.OPTION_ACTIVITY_DRAWER)) layouts.add(file.getDrawerXmlName());
        }
        for (ProjectFileBean file : files.c()) layouts.add(file.getXmlName());
        boolean included = false;
        for (String xml : layouts) {
            for (ViewBean list : data.f(xml)) {
                if (fileName.equals(list.customView)) return false;
            }
            if (!included) {
                for (ViewBean bean : data.d(xml)) {
                    if (isInclude(bean) && fileName.equals(includedLayout(bean))) {
                        included = true;
                        break;
                    }
                }
            }
        }
        return included;
    }

    /** The layouts of the project an include can point to: of its screens and of its custom views. */
    @NonNull
    public static Set<String> layouts(@NonNull String scId) {
        Set<String> layouts = new HashSet<>();
        hC files = jC.b(scId);
        for (ProjectFileBean file : files.b()) layouts.add(file.getXmlName());
        for (ProjectFileBean file : files.c()) layouts.add(file.getXmlName());
        return layouts;
    }

    /**
     * Call before the widgets {@code removed} leave the layout {@code xml}. A screen that reached a widget through
     * them (an include, an include nested in an included layout, or the widget itself inside an included layout)
     * loses that widget's events and the blocks' references to it, as deleting one of its own widgets does.
     */
    public static void forgetLogicReachedThrough(@NonNull eC data, @NonNull String scId, @NonNull String xml,
                                                 @NonNull List<ViewBean> removed) {
        Set<String> ids = new HashSet<>();
        for (ViewBean bean : removed) {
            if (bean != null && bean.id != null) ids.add(bean.id);
        }
        if (ids.isEmpty()) return;
        for (ProjectFileBean screen : jC.b(scId).b()) {
            for (Entry entry : resolve(data, screen.getXmlName()).entries) {
                if (entry.reachedThrough(xml, ids)) forget(data, screen.getJavaName(), entry);
            }
        }
    }

    /**
     * Call before the layouts {@code removedXmls} are deleted from the project, while they can still be resolved:
     * the screens {@code screens} lose the logic of the widgets they reached through them.
     */
    public static void forgetLogicReachedThroughLayouts(@NonNull eC data, @NonNull List<ProjectFileBean> screens,
                                                        @NonNull Set<String> removedXmls) {
        if (removedXmls.isEmpty()) return;
        for (ProjectFileBean screen : screens) {
            if (removedXmls.contains(screen.getXmlName())) continue;
            for (Entry entry : resolve(data, screen.getXmlName()).entries) {
                if (entry.reachedThroughLayouts(removedXmls)) forget(data, screen.getJavaName(), entry);
            }
        }
    }

    /** What eC does for a widget of the screen itself: its events with their blocks, and blocks pointing at it. */
    private static void forget(eC data, String javaName, Entry entry) {
        data.m(javaName, entry.id);
        data.a(javaName, entry.view.getClassInfo(), entry.id, true);
    }

    /**
     * The widget an event of the screen belongs to: one of the screen, of its drawer, or one reached through an
     * include. Null when there's none, e.g. after the widget was deleted.
     */
    @Nullable
    public static ViewBean eventTarget(@NonNull eC data, @NonNull String screenXml, @NonNull String id) {
        ViewBean view = data.c(screenXml, id);
        if (view == null) view = data.c("_drawer_" + screenXml, id);
        if (view == null) {
            Entry entry = resolve(data, screenXml).find(id);
            if (entry != null) view = entry.view;
        }
        return view;
    }

    /**
     * @param screenXml   the screen's layout, e.g. main.xml
     * @param screenViews the screen's own widgets: their ids are taken, an entry never shadows one
     */
    @NonNull
    public static Result resolve(@NonNull String screenXml, @NonNull List<ViewBean> screenViews,
                                 @NonNull LayoutSource source) {
        List<Entry> entries = new ArrayList<>();
        List<Problem> problems = new ArrayList<>();
        Set<String> taken = new HashSet<>();
        for (ViewBean bean : screenViews) {
            if (bean != null && bean.id != null) taken.add(bean.id);
        }
        Deque<String> stack = new ArrayDeque<>();
        stack.push(screenXml);
        for (ViewBean bean : screenViews) {
            if (isInclude(bean) && hasXmlId(bean)) {
                List<String> path = new ArrayList<>();
                path.add(bean.id);
                List<String> layouts = new ArrayList<>();
                layouts.add(screenXml);
                expand(bean, path, layouts, stack, source, taken, entries, problems);
            }
        }
        return new Result(entries, problems);
    }

    private static void expand(ViewBean include, List<String> path, List<String> layouts, Deque<String> stack,
                               LayoutSource source, Set<String> taken, List<Entry> entries, List<Problem> problems) {
        String layout = includedLayout(include);
        if (layout.isEmpty()) return;
        String xml = layout + ".xml";
        if (stack.contains(xml)) {
            problems.add(new Problem(Problem.Kind.CYCLE, path, xml));
            return;
        }
        List<ViewBean> views = source.views(xml);
        if (views == null) {
            problems.add(new Problem(Problem.Kind.MISSING_LAYOUT, path, xml));
            return;
        }
        stack.push(xml);
        for (ViewBean child : views) {
            if (child == null || child.id == null || child.id.isEmpty() || child.id.startsWith("_")) continue;
            if (!hasXmlId(child)) continue;
            path.add(child.id);
            layouts.add(xml);
            if (isInclude(child)) {
                expand(child, path, layouts, stack, source, taken, entries, problems);
            } else if (!isLegacyInclude(child)) {
                String name = nameOf(path);
                if (taken.add(name)) {
                    entries.add(new Entry(path, layouts, child));
                } else {
                    problems.add(new Problem(Problem.Kind.NAME_TAKEN, path, name));
                }
            }
            path.remove(path.size() - 1);
            layouts.remove(layouts.size() - 1);
        }
        stack.pop();
    }

    /** An {@code <include>} added from the palette, whose children can be reached. */
    public static boolean isInclude(@Nullable ViewBean bean) {
        return bean != null && bean.type == ViewBeans.VIEW_TYPE_LAYOUT_INCLUDE;
    }

    /** The old "convert to include" of a layout widget: no id in the XML, so nothing reachable through it. */
    private static boolean isLegacyInclude(ViewBean bean) {
        return "include".equals(bean.convert) && bean.type != ViewBeans.VIEW_TYPE_LAYOUT_INCLUDE;
    }

    /** The layout an include points to, without {@code @layout/}, or "" when none was chosen. */
    @NonNull
    public static String includedLayout(@NonNull ViewBean include) {
        String layout = include.inject == null ? null : InjectAttributes.get(include, "layout");
        if (layout == null) return "";
        layout = layout.trim();
        return layout.startsWith("@layout/") ? layout.substring("@layout/".length()) : layout;
    }

    /** Whether the widget keeps its {@code android:id} in the XML ({@code tools:replace="android:id"} drops it). */
    public static boolean hasXmlId(@NonNull ViewBean bean) {
        if (bean.inject == null || bean.inject.isEmpty()) return true;
        String replaced = InjectAttributes.get(bean, "tools:replace");
        if (replaced == null) return true;
        for (String attribute : replaced.split(",")) {
            if (attribute.trim().equals("android:id")) return false;
        }
        return true;
    }

    @NonNull
    static String nameOf(@NonNull List<String> path) {
        return String.join("_", path);
    }
}
