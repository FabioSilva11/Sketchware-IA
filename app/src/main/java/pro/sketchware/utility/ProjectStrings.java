package pro.sketchware.utility;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.besome.sketch.beans.ViewBean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import a.a.a.wq;
import pro.sketchware.activities.resourceseditor.components.utils.StringsEditorManager;

/**
 * Keeps widget texts in the project's {@code values/strings.xml}.
 * <p>
 * Every literal text a widget shows (text, hint, content description) is stored as a string resource
 * owned by that widget, named {@code <widget id>_<field>}. Dropping a widget, editing its text and
 * generating a layout with AI all go through here, and listeners (the Strings tab) are told at once.
 * Strings are never deleted with their widget, so undoing a delete can't leave a dangling reference.
 */
public final class ProjectStrings {

    public static final String FIELD_TEXT = "text";
    public static final String FIELD_HINT = "hint";
    public static final String FIELD_DESCRIPTION = "description";
    private static final String REFERENCE_PREFIX = "@string/";

    public interface Listener {
        void onProjectStringsChanged(@NonNull String scId);
    }

    private static final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ProjectStrings() {
    }

    public static void addListener(@NonNull Listener listener) {
        listeners.add(listener);
    }

    public static void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    /** Bumped on every change, so caches of strings.xml know they're stale even within the same second. */
    public static volatile long version;

    public static void notifyChanged(@NonNull String scId) {
        version++;
        mainHandler.post(() -> {
            for (Listener listener : listeners) {
                listener.onProjectStringsChanged(scId);
            }
        });
    }

    @NonNull
    public static String getStringsPath(@NonNull String scId) {
        return wq.b(scId) + "/files/resource/values/strings.xml";
    }

    public static boolean isReference(@Nullable String value) {
        return value != null && value.trim().startsWith(REFERENCE_PREFIX);
    }

    @NonNull
    public static String ownedKey(@NonNull String widgetId, @NonNull String field) {
        String base = (widgetId + "_" + field).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        if (base.isEmpty() || !Character.isLetter(base.charAt(0))) base = "s_" + base;
        return base;
    }

    /**
     * Stores {@code newValue} as the widget's {@code field} string and returns what the widget should
     * reference. References ({@code @string/...}) and empty values are returned unchanged.
     *
     * @param currentValue what the widget references right now; its own string is updated in place
     */
    @NonNull
    public static String assign(@NonNull String scId, @NonNull String widgetId, @NonNull String field,
                                @Nullable String currentValue, @Nullable String newValue) {
        if (newValue == null) return "";
        if (newValue.trim().isEmpty()) return newValue;
        if (isReference(newValue)) return newValue.trim();

        synchronized (ProjectStrings.class) {
            Strings strings = Strings.load(scId);
            // Never rewrite a strings.xml we couldn't read; the widget keeps its literal text instead.
            if (strings.failed) return newValue;
            String owned = ownedKey(widgetId, field);
            String key = null;
            if (isReference(currentValue)) {
                String currentKey = currentValue.trim().substring(REFERENCE_PREFIX.length());
                // Update the widget's own string in place; a string the user picked (shared with other
                // widgets) stays untouched and the widget gets a string of its own.
                if (currentKey.equals(owned) || currentKey.startsWith(owned + "_")) {
                    key = currentKey;
                }
            }
            if (key == null) {
                key = strings.keyFor(owned, newValue);
            }
            if (strings.put(key, newValue)) {
                strings.save();
                notifyChanged(scId);
            }
            return REFERENCE_PREFIX + key;
        }
    }

    /** Moves the literal texts of new widgets (dropped, pasted or generated) into string resources. */
    public static void externalize(@NonNull String scId, @NonNull List<ViewBean> beans) {
        synchronized (ProjectStrings.class) {
            Strings strings = Strings.load(scId);
            if (strings.failed) return;
            boolean changed = false;
            for (ViewBean bean : beans) {
                if (bean.text == null || bean.id == null || !bean.getClassInfo().a("TextView")) continue;
                String text = bean.text.text;
                if (text != null && !text.trim().isEmpty() && !isReference(text)) {
                    String key = strings.keyFor(ownedKey(bean.id, FIELD_TEXT), text);
                    changed |= strings.put(key, text);
                    bean.text.text = REFERENCE_PREFIX + key;
                }
                String hint = bean.text.hint;
                if (hint != null && !hint.trim().isEmpty() && !isReference(hint)) {
                    String key = strings.keyFor(ownedKey(bean.id, FIELD_HINT), hint);
                    changed |= strings.put(key, hint);
                    bean.text.hint = REFERENCE_PREFIX + key;
                }
            }
            if (changed) {
                strings.save();
                notifyChanged(scId);
            }
        }
    }

    public static void externalize(@NonNull String scId, @NonNull ViewBean bean) {
        List<ViewBean> single = new ArrayList<>();
        single.add(bean);
        externalize(scId, single);
    }

    /** The default-variant strings.xml as a list, written back with its notes and the app name. */
    private static final class Strings {
        private final String scId;
        private final StringsEditorManager manager = new StringsEditorManager();
        private final ArrayList<HashMap<String, Object>> list = new ArrayList<>();
        private final HashMap<String, String> notesByKey = new HashMap<>();
        private boolean failed;

        private Strings(String scId) {
            this.scId = scId;
        }

        static Strings load(String scId) {
            Strings strings = new Strings(scId);
            strings.manager.sc_id = scId;
            strings.manager.convertXmlStringsToListMap(FileUtil.readFileIfExist(getStringsPath(scId)), strings.list);
            strings.failed = strings.manager.isDataLoadingFailed;
            for (int i = 0; i < strings.list.size(); i++) {
                String note = strings.manager.notesMap.get(i);
                if (note != null) strings.notesByKey.put(Objects.toString(strings.list.get(i).get("key"), ""), note);
            }
            return strings;
        }

        @Nullable
        String get(String key) {
            for (HashMap<String, Object> map : list) {
                if (key.equals(map.get("key"))) return Objects.toString(map.get("text"), "");
            }
            return null;
        }

        /** {@code preferred} when it's free or already holds {@code value}, otherwise a numbered variant. */
        String keyFor(String preferred, String value) {
            String existing = get(preferred);
            if (existing == null || existing.equals(value)) return preferred;
            int suffix = 2;
            String key = preferred + "_" + suffix;
            while (get(key) != null && !Objects.equals(get(key), value)) key = preferred + "_" + (++suffix);
            return key;
        }

        boolean put(String key, String value) {
            for (HashMap<String, Object> map : list) {
                if (key.equals(map.get("key"))) {
                    if (value.equals(map.get("text"))) return false;
                    map.put("text", value);
                    return true;
                }
            }
            HashMap<String, Object> map = new HashMap<>();
            map.put("key", key);
            map.put("text", value);
            list.add(map);
            return true;
        }

        void save() {
            HashMap<Integer, String> notes = new HashMap<>();
            for (int i = 0; i < list.size(); i++) {
                String note = notesByKey.get(Objects.toString(list.get(i).get("key"), ""));
                if (note != null) notes.put(i, note);
            }
            XmlUtil.saveXml(getStringsPath(scId), manager.convertListMapToXmlStrings(list, notes));
        }
    }
}
