package pro.sketchware.utility;

import androidx.annotation.NonNull;

import com.besome.sketch.beans.ViewBean;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tree order of the widgets of a layout, as the editor, the XML and Java generators and the
 * collections need it: the children of "root" sorted by index, then the children of each widget
 * (sorted by index) after it.
 * <p>
 * It replaces {@code eC.a(ArrayList)} and {@code eC.b(String, ViewBean)}, which only walked into the
 * container types Sketchware had at first (LinearLayout, RelativeLayout, ScrollViews, CardView,
 * TextInputLayout...). Children of any other container, such as FrameLayout or ConstraintLayout, were
 * left out: they disappeared from the canvas after reopening, from the property list, from the
 * generated XML and Java, and stayed behind as orphans when their parent was deleted. Here every
 * widget that has children is walked into.
 */
public final class ViewHierarchy {

    private ViewHierarchy() {
    }

    /** The widgets in tree order; widgets whose parent doesn't exist are left out, as before. */
    @NonNull
    public static ArrayList<ViewBean> sorted(List<ViewBean> views) {
        ArrayList<ViewBean> result = new ArrayList<>();
        if (views == null) return result;
        Map<String, List<ViewBean>> children = childrenByParent(views);
        List<ViewBean> roots = children.getOrDefault("root", new ArrayList<>());
        Set<String> visited = new HashSet<>();
        visited.add("root");
        result.addAll(roots);
        for (ViewBean root : roots) visited.add(root.id);
        for (ViewBean root : roots) {
            addDescendants(root, children, visited, result);
        }
        return result;
    }

    /** {@code view} followed by all its descendants in tree order (what deleting or saving it takes along). */
    @NonNull
    public static ArrayList<ViewBean> withDescendants(List<ViewBean> views, @NonNull ViewBean view) {
        ArrayList<ViewBean> result = new ArrayList<>();
        result.add(view);
        if (views == null) return result;
        Set<String> visited = new HashSet<>();
        visited.add(view.id);
        addDescendants(view, childrenByParent(views), visited, result);
        return result;
    }

    private static Map<String, List<ViewBean>> childrenByParent(List<ViewBean> views) {
        Map<String, List<ViewBean>> children = new HashMap<>();
        for (ViewBean view : views) {
            if (view == null || view.parent == null) continue;
            children.computeIfAbsent(view.parent, key -> new ArrayList<>()).add(view);
        }
        // Stable: widgets with the same index keep their list order.
        Comparator<ViewBean> byIndex = Comparator.comparingInt(view -> view.index);
        for (List<ViewBean> list : children.values()) {
            list.sort(byIndex);
        }
        return children;
    }

    /** Children first, then each child's subtree; a widget is never added twice, even with a broken parent chain. */
    private static void addDescendants(ViewBean parent, Map<String, List<ViewBean>> children, Set<String> visited,
                                       List<ViewBean> out) {
        List<ViewBean> direct = children.get(parent.id);
        if (direct == null) return;
        List<ViewBean> added = new ArrayList<>();
        for (ViewBean child : direct) {
            if (child.id != null && visited.add(child.id)) {
                out.add(child);
                added.add(child);
            }
        }
        for (ViewBean child : added) {
            addDescendants(child, children, visited, out);
        }
    }
}
