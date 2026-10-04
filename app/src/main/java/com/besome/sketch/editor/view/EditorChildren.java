package com.besome.sketch.editor.view;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

/**
 * Child positions in the layout editor containers.
 * <p>
 * A bean's {@code index} is its position among the editor widgets ({@link ItemView}s) of its parent,
 * the same order the children have in the XML. While a widget is dragged it stays in its parent as a
 * GONE view and the drop preview is a plain view, so neither counts. Every container maps the widget
 * index to a real child position with {@link #insertionIndex}, which never returns a position that
 * {@link ViewGroup#addView(View, int)} would reject.
 */
public final class EditorChildren {

    private EditorChildren() {
    }

    /**
     * The child position that puts a view before the {@code index}-th visible widget of {@code parent},
     * or -1 (append) when {@code index} is negative or past the last widget.
     */
    public static int insertionIndex(@NonNull ViewGroup parent, int index) {
        if (index < 0) return -1;
        int widget = 0;
        for (int position = 0; position < parent.getChildCount(); position++) {
            View child = parent.getChildAt(position);
            if (!(child instanceof ItemView) || child.getVisibility() == View.GONE) continue;
            if (widget == index) return position;
            widget++;
        }
        return -1;
    }

    /** Stores in each child bean its position among the widgets of {@code parent}. */
    public static void reindex(@NonNull ViewGroup parent) {
        int index = 0;
        for (int position = 0; position < parent.getChildCount(); position++) {
            if (parent.getChildAt(position) instanceof ItemView item && item.getBean() != null) {
                item.getBean().index = index++;
            }
        }
    }

    /** Number of editor widgets in {@code parent}, ignoring {@code except} (e.g. the dragged widget). */
    public static int widgetCount(@NonNull ViewGroup parent, View except) {
        int count = 0;
        for (int position = 0; position < parent.getChildCount(); position++) {
            View child = parent.getChildAt(position);
            if (child instanceof ItemView && child != except) count++;
        }
        return count;
    }
}
