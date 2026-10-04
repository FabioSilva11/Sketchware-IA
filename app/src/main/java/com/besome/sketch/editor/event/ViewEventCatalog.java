package com.besome.sketch.editor.event;

import androidx.annotation.NonNull;

import com.besome.sketch.beans.EventBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;

import java.util.ArrayList;
import java.util.List;

import a.a.a.Ox;
import a.a.a.jC;
import a.a.a.jq;
import a.a.a.oq;
import mod.agus.jcoderz.beans.ViewBeans;

/**
 * The events a widget can have. Every screen that lists or adds widget events goes through here, so
 * they all follow the rules of the Java generator (Jx): an {@code <include>} and a widget whose id is
 * replaced with {@code tools:replace="android:id"} have no field in the activity, so they get no
 * events, and onBindCustomView only exists for a list with a custom item layout.
 */
public final class ViewEventCatalog {

    private ViewEventCatalog() {
    }

    /** Whether the generated activity has a field for this widget that events can attach to. */
    public static boolean hasEvents(@NonNull ProjectFileBean file, ViewBean view) {
        if (view == null || view.id == null || view.id.isEmpty()) return false;
        if (view.type == ViewBeans.VIEW_TYPE_LAYOUT_INCLUDE || "include".equals(view.convert)) return false;
        return !new Ox(new jq(), file).readAttributesToReplace(view).contains("android:id");
    }

    /** Event names of the widget's real class that apply to this widget. */
    @NonNull
    public static List<String> eventNames(@NonNull ProjectFileBean file, ViewBean view) {
        List<String> names = new ArrayList<>();
        if (!hasEvents(file, view)) return names;
        for (String event : oq.getEventsForClass(view.getClassInfo())) {
            if (event.equals("onBindCustomView")
                    && (view.customView == null || view.customView.isEmpty() || view.customView.equals("none"))) {
                continue;
            }
            names.add(event);
        }
        return names;
    }

    /** Whether {@code event} of {@code view} is already in the activity of {@code file}. */
    public static boolean isAdded(@NonNull String scId, @NonNull ProjectFileBean file, @NonNull ViewBean view,
                                  @NonNull String event, int eventType) {
        ArrayList<EventBean> events = jC.a(scId).g(file.getJavaName());
        if (events == null) return false;
        for (EventBean existing : events) {
            if (existing.eventType == eventType && view.id.equals(existing.targetId) && event.equals(existing.eventName)) {
                return true;
            }
        }
        return false;
    }

    /** Events of {@code view} that aren't in the activity yet, ready to be added. */
    @NonNull
    public static List<EventBean> addable(@NonNull String scId, @NonNull ProjectFileBean file, ViewBean view, int eventType) {
        List<EventBean> result = new ArrayList<>();
        for (String event : eventNames(file, view)) {
            if (!isAdded(scId, file, view, event, eventType)) {
                result.add(new EventBean(eventType, view.type, view.id, event));
            }
        }
        return result;
    }
}
