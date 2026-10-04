package com.besome.sketch.editor.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.besome.sketch.beans.ViewBean;

import java.util.function.Consumer;

import pro.sketchware.utility.ProjectStrings;

/**
 * Drawn on top of the layout editor. Shows how children of RelativeLayout and ConstraintLayout are
 * attached (to the parent or to each other), draws anchors on the selected widget that can be dragged
 * to a parent edge, to the middle of the parent or to another widget, and can switch the whole canvas
 * to a blueprint of the hierarchy. Touches that don't start on an anchor go to the widgets below.
 */
@SuppressLint("ViewConstructor")
public class LayoutRelationsOverlay extends View {

    public interface Callback {
        /**
         * Applies {@code change} to {@code bean}; the editor records it in the history, refreshes the
         * widget and may reject it (returning false), e.g. for a circular RelativeLayout dependency.
         */
        boolean applyRelationChange(@NonNull ViewBean bean, @NonNull Consumer<ViewBean> change);
    }

    private static final int BLUEPRINT_BACKGROUND = 0xFF1F5566;
    private static final int BLUEPRINT_LINE = 0xFF8FD3E8;
    private static final int BLUEPRINT_TEXT = 0xFFCDEFF8;
    private static final int RELATION_COLOR = 0xFF1E88E5;
    private static final int RELATION_MUTED = 0x991E88E5;
    private static final int CANDIDATE_COLOR = 0xFF00C853;

    private final ViewPane pane;
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float density;
    private final int touchSlop;

    @Nullable
    private ItemView selected;
    private boolean blueprint;
    @Nullable
    private Callback callback;

    // Anchor drag
    @Nullable
    private LayoutRelations.Side dragSide;
    private final PointF dragStart = new PointF();
    private final PointF dragPoint = new PointF();
    private boolean dragMoved;
    @Nullable
    private Target candidate;

    /** Where a dragged anchor would attach. */
    private static final class Target {
        final String id;
        @Nullable
        final LayoutRelations.Side side;
        final RectF rect;

        Target(String id, @Nullable LayoutRelations.Side side, RectF rect) {
            this.id = id;
            this.side = side;
            this.rect = rect;
        }
    }

    public LayoutRelationsOverlay(Context context, ViewPane pane) {
        super(context);
        this.pane = pane;
        density = context.getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        linePaint.setStyle(Paint.Style.STROKE);
        labelPaint.setTextSize(10 * density);
        setWillNotDraw(false);
    }

    public void setCallback(@Nullable Callback callback) {
        this.callback = callback;
    }

    public void setSelected(@Nullable ItemView item) {
        selected = item;
        invalidate();
    }

    public boolean isBlueprint() {
        return blueprint;
    }

    public void setBlueprint(boolean blueprint) {
        this.blueprint = blueprint;
        invalidate();
    }

    /** Size in overlay pixels that looks like {@code dp} on screen, whatever the canvas zoom. */
    private float screenDp(float dp) {
        float scale = pane.getScaleX() <= 0 ? 1f : pane.getScaleX();
        return dp * density / scale;
    }

    // ---------------------------------------------------------------------------------------------
    // Geometry

    @Nullable
    private RectF rectOf(@Nullable View view) {
        if (view == null || view.getVisibility() != VISIBLE || view.getParent() == null) return null;
        Rect rect = new Rect(0, 0, view.getWidth(), view.getHeight());
        try {
            pane.offsetDescendantRectToMyCoords(view, rect);
        } catch (IllegalArgumentException e) {
            return null;
        }
        RectF result = new RectF(rect);
        result.offset(view.getTranslationX(), view.getTranslationY());
        return result;
    }

    /** The parent's content area: the edges RelativeLayout and ConstraintLayout align to. */
    @Nullable
    private RectF contentRectOf(@Nullable View parent) {
        RectF rect = rectOf(parent);
        if (rect == null) return null;
        rect.left += parent.getPaddingLeft();
        rect.top += parent.getPaddingTop();
        rect.right -= parent.getPaddingRight();
        rect.bottom -= parent.getPaddingBottom();
        return rect;
    }

    @Nullable
    private View findView(String id) {
        ViewGroup root = pane.getRootLayout();
        return root == null ? null : root.findViewWithTag(id);
    }

    private static PointF anchorPoint(RectF rect, LayoutRelations.Side side) {
        return switch (side) {
            case TOP -> new PointF(rect.centerX(), rect.top);
            case BOTTOM -> new PointF(rect.centerX(), rect.bottom);
            case START -> new PointF(rect.left, rect.centerY());
            case END -> new PointF(rect.right, rect.centerY());
        };
    }

    @Nullable
    private ViewBean selectedBean() {
        ItemView item = selected;
        if (item == null) return null;
        ViewBean bean = item.getBean();
        return LayoutRelations.supports(bean) ? bean : null;
    }

    // ---------------------------------------------------------------------------------------------
    // Drawing

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        ViewGroup root = pane.getRootLayout();
        if (root == null) return;
        if (blueprint) {
            canvas.drawColor(BLUEPRINT_BACKGROUND);
            drawBlueprint(canvas, root, 0);
            drawAllRelations(canvas, root);
        } else {
            drawRelationsAroundSelection(canvas);
        }
        drawHandles(canvas);
        drawDrag(canvas);
    }

    private void drawBlueprint(Canvas canvas, View view, int depth) {
        RectF rect = rectOf(view);
        if (rect == null) return;
        boolean isSelected = selected != null && selected == view;
        linePaint.setPathEffect(null);
        linePaint.setColor(isSelected ? 0xFFFFFFFF : BLUEPRINT_LINE);
        linePaint.setStrokeWidth(screenDp(isSelected ? 2f : 1f));
        canvas.drawRect(rect, linePaint);
        if (view instanceof ItemView item && item.getBean() != null && depth > 0) {
            labelPaint.setColor(isSelected ? 0xFFFFFFFF : BLUEPRINT_TEXT);
            labelPaint.setTextSize(screenDp(9f));
            String label = blueprintLabel(item.getBean());
            float available = rect.width() - screenDp(6f);
            if (available > screenDp(12f) && rect.height() > screenDp(10f)) {
                CharSequence text = TextUtils.ellipsize(label, labelPaint, available, TextUtils.TruncateAt.END);
                canvas.drawText(text, 0, text.length(), rect.left + screenDp(3f), rect.top + screenDp(11f), labelPaint);
            }
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child instanceof ItemView) {
                    drawBlueprint(canvas, child, depth + 1);
                }
            }
        }
    }

    private String blueprintLabel(ViewBean bean) {
        String type = bean.convert == null || bean.convert.isEmpty()
                ? ViewBean.getViewTypeName(bean.type)
                : bean.convert.substring(bean.convert.lastIndexOf('.') + 1);
        if ("include".equals(type)) {
            String layout = pro.sketchware.utility.InjectAttributes.get(bean, "layout");
            return "<include> " + (layout == null ? "" : layout);
        }
        String text = bean.text == null ? null : bean.text.text;
        if (text != null && !text.isEmpty() && bean.getClassInfo().a("TextView")) {
            if (ProjectStrings.isReference(text)) text = pane.getXmlString(text);
            return type + " · " + text.replace('\n', ' ');
        }
        return type + " · " + bean.id;
    }

    private void drawAllRelations(Canvas canvas, ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (!(child instanceof ItemView item)) continue;
            ViewBean bean = item.getBean();
            if (LayoutRelations.supports(bean)) {
                drawRelations(canvas, child, bean, child == selected ? 0xFFFFFFFF : BLUEPRINT_LINE);
            }
            if (child instanceof ViewGroup childGroup) {
                drawAllRelations(canvas, childGroup);
            }
        }
    }

    private void drawRelationsAroundSelection(Canvas canvas) {
        ItemView item = selected;
        if (!(item instanceof View selectedView)) return;
        // A selected RelativeLayout/ConstraintLayout shows how its children are placed; a selected child
        // shows its own relations and, lighter, the ones of its siblings.
        ViewGroup group = null;
        if (LayoutRelations.supports(item.getBean()) && selectedView.getParent() instanceof ViewGroup parent) {
            group = parent;
        } else if (selectedView instanceof ViewGroup container && item.getBean() != null
                && (LayoutRelations.isRelative(item.getBean().type) || LayoutRelations.isConstraint(item.getBean().type))) {
            group = container;
        }
        if (group == null) return;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof ItemView childItem && LayoutRelations.supports(childItem.getBean())) {
                drawRelations(canvas, child, childItem.getBean(), child == selectedView ? RELATION_COLOR : RELATION_MUTED);
            }
        }
    }

    private void drawRelations(Canvas canvas, View view, ViewBean bean, int color) {
        RectF rect = rectOf(view);
        RectF parent = view.getParent() instanceof View parentView ? contentRectOf(parentView) : null;
        if (rect == null || parent == null) return;
        boolean constraint = LayoutRelations.isConstraint(bean.parentType);
        for (LayoutRelations.Connection connection : LayoutRelations.connections(bean)) {
            PointF from = anchorPoint(rect, connection.side);
            PointF to = targetPoint(connection, from, parent);
            if (to == null) continue;
            boolean centring = connection.targetSide == null;
            linePaint.setColor(color);
            linePaint.setStrokeWidth(screenDp(1.4f));
            linePaint.setPathEffect(centring ? new DashPathEffect(new float[]{screenDp(4), screenDp(3)}, 0) : null);
            if (constraint && !centring) {
                drawSpring(canvas, from, to, connection.side);
            } else {
                canvas.drawLine(from.x, from.y, to.x, to.y, linePaint);
            }
            drawArrow(canvas, from, to, color);
        }
        linePaint.setPathEffect(null);
    }

    @Nullable
    private PointF targetPoint(LayoutRelations.Connection connection, PointF from, RectF parent) {
        boolean vertical = connection.side == LayoutRelations.Side.TOP || connection.side == LayoutRelations.Side.BOTTOM;
        RectF target;
        LayoutRelations.Side targetSide = connection.targetSide;
        if (LayoutRelations.PARENT.equals(connection.target)) {
            target = parent;
            if (targetSide == null) targetSide = connection.side;
        } else {
            target = rectOf(findView(connection.target));
            if (target == null) return null;
            if (targetSide == null) targetSide = connection.side;
        }
        if (vertical) {
            float y = targetSide == LayoutRelations.Side.TOP ? target.top : target.bottom;
            return new PointF(Math.max(target.left, Math.min(target.right, from.x)), y);
        }
        float x = targetSide == LayoutRelations.Side.START ? target.left : target.right;
        return new PointF(x, Math.max(target.top, Math.min(target.bottom, from.y)));
    }

    /** The zigzag ConstraintLayout uses for constraints (as in Android Studio's blueprint). */
    private void drawSpring(Canvas canvas, PointF from, PointF to, LayoutRelations.Side side) {
        boolean vertical = side == LayoutRelations.Side.TOP || side == LayoutRelations.Side.BOTTOM;
        float length = vertical ? to.y - from.y : to.x - from.x;
        float amplitude = screenDp(3f);
        float step = screenDp(5f);
        int teeth = (int) Math.max(1, Math.abs(length) / step);
        path.reset();
        path.moveTo(from.x, from.y);
        for (int i = 1; i <= teeth; i++) {
            float t = (float) i / teeth;
            float offset = (i == teeth) ? 0 : ((i % 2 == 0) ? amplitude : -amplitude);
            if (vertical) {
                path.lineTo(from.x + offset, from.y + length * t);
            } else {
                path.lineTo(from.x + length * t, from.y + offset);
            }
        }
        canvas.drawPath(path, linePaint);
        // Short straight connector when the target edge isn't in line with the anchor.
        PointF end = vertical ? new PointF(from.x, to.y) : new PointF(to.x, from.y);
        if (Math.abs(end.x - to.x) > 1 || Math.abs(end.y - to.y) > 1) {
            canvas.drawLine(end.x, end.y, to.x, to.y, linePaint);
        }
    }

    private void drawArrow(Canvas canvas, PointF from, PointF to, int color) {
        float dx = to.x - from.x;
        float dy = to.y - from.y;
        float length = (float) Math.hypot(dx, dy);
        if (length < screenDp(4)) return;
        float ux = dx / length;
        float uy = dy / length;
        float size = screenDp(5f);
        path.reset();
        path.moveTo(to.x, to.y);
        path.lineTo(to.x - ux * size - uy * size * 0.6f, to.y - uy * size + ux * size * 0.6f);
        path.lineTo(to.x - ux * size + uy * size * 0.6f, to.y - uy * size - ux * size * 0.6f);
        path.close();
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(color);
        canvas.drawPath(path, fillPaint);
    }

    private void drawHandles(Canvas canvas) {
        ViewBean bean = selectedBean();
        if (bean == null) return;
        RectF rect = rectOf((View) selected);
        if (rect == null) return;
        float radius = screenDp(6f);
        for (LayoutRelations.Side side : LayoutRelations.Side.values()) {
            PointF point = anchorPoint(rect, side);
            boolean connected = LayoutRelations.hasSide(bean, side);
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(connected ? RELATION_COLOR : 0xFFFFFFFF);
            canvas.drawCircle(point.x, point.y, radius, fillPaint);
            linePaint.setPathEffect(null);
            linePaint.setColor(RELATION_COLOR);
            linePaint.setStrokeWidth(screenDp(1.5f));
            canvas.drawCircle(point.x, point.y, radius, linePaint);
        }
    }

    private void drawDrag(Canvas canvas) {
        if (dragSide == null || !dragMoved || !(selected instanceof View selectedView)) return;
        RectF rect = rectOf(selectedView);
        if (rect == null) return;
        PointF from = anchorPoint(rect, dragSide);
        linePaint.setPathEffect(new DashPathEffect(new float[]{screenDp(5), screenDp(3)}, 0));
        linePaint.setColor(candidate == null ? RELATION_COLOR : CANDIDATE_COLOR);
        linePaint.setStrokeWidth(screenDp(1.5f));
        canvas.drawLine(from.x, from.y, dragPoint.x, dragPoint.y, linePaint);
        linePaint.setPathEffect(null);
        if (candidate != null) {
            linePaint.setStrokeWidth(screenDp(3f));
            linePaint.setColor(CANDIDATE_COLOR);
            RectF r = candidate.rect;
            if (candidate.side == null) {
                boolean vertical = dragSide == LayoutRelations.Side.TOP || dragSide == LayoutRelations.Side.BOTTOM;
                if (vertical) {
                    canvas.drawLine(r.left, r.centerY(), r.right, r.centerY(), linePaint);
                } else {
                    canvas.drawLine(r.centerX(), r.top, r.centerX(), r.bottom, linePaint);
                }
            } else {
                switch (candidate.side) {
                    case TOP -> canvas.drawLine(r.left, r.top, r.right, r.top, linePaint);
                    case BOTTOM -> canvas.drawLine(r.left, r.bottom, r.right, r.bottom, linePaint);
                    case START -> canvas.drawLine(r.left, r.top, r.left, r.bottom, linePaint);
                    case END -> canvas.drawLine(r.right, r.top, r.right, r.bottom, linePaint);
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Touch: dragging anchors

    @Nullable
    private LayoutRelations.Side hitHandle(float x, float y) {
        if (selectedBean() == null) return null;
        RectF rect = rectOf((View) selected);
        if (rect == null) return null;
        float tolerance = screenDp(16f);
        LayoutRelations.Side best = null;
        float bestDistance = Float.MAX_VALUE;
        for (LayoutRelations.Side side : LayoutRelations.Side.values()) {
            PointF point = anchorPoint(rect, side);
            float distance = (float) Math.hypot(point.x - x, point.y - y);
            if (distance < tolerance && distance < bestDistance) {
                best = side;
                bestDistance = distance;
            }
        }
        return best;
    }

    @Nullable
    private Target findTarget(float x, float y) {
        View selectedView = (View) selected;
        ViewBean bean = selectedBean();
        if (bean == null || dragSide == null || !(selectedView.getParent() instanceof ViewGroup parent)) return null;
        boolean vertical = dragSide == LayoutRelations.Side.TOP || dragSide == LayoutRelations.Side.BOTTOM;
        float tolerance = screenDp(14f);
        // Another widget of the same parent under the finger: attach to its nearest edge on this axis.
        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            View child = parent.getChildAt(i);
            if (child == selectedView || !(child instanceof ItemView item) || item.getBean() == null) continue;
            RectF rect = rectOf(child);
            if (rect == null) continue;
            RectF hit = new RectF(rect);
            hit.inset(-tolerance, -tolerance);
            if (!hit.contains(x, y)) continue;
            LayoutRelations.Side side = vertical
                    ? (Math.abs(y - rect.top) < Math.abs(y - rect.bottom) ? LayoutRelations.Side.TOP : LayoutRelations.Side.BOTTOM)
                    : (Math.abs(x - rect.left) < Math.abs(x - rect.right) ? LayoutRelations.Side.START : LayoutRelations.Side.END);
            return new Target(item.getBean().id, side, rect);
        }
        RectF content = contentRectOf(parent);
        if (content == null) return null;
        // The middle of the parent: centre on this axis.
        float middle = vertical ? content.centerY() : content.centerX();
        float position = vertical ? y : x;
        if (Math.abs(position - middle) < tolerance) {
            return new Target(LayoutRelations.PARENT, null, content);
        }
        boolean constraint = LayoutRelations.isConstraint(bean.parentType);
        LayoutRelations.Side side;
        if (vertical) {
            side = position < middle ? LayoutRelations.Side.TOP : LayoutRelations.Side.BOTTOM;
        } else {
            side = position < middle ? LayoutRelations.Side.START : LayoutRelations.Side.END;
        }
        // RelativeLayout can only align an edge to the same edge of its parent.
        if (!constraint && side != dragSide) return null;
        return new Target(LayoutRelations.PARENT, side, content);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                LayoutRelations.Side side = hitHandle(event.getX(), event.getY());
                if (side == null) return false;
                dragSide = side;
                dragMoved = false;
                candidate = null;
                dragStart.set(event.getX(), event.getY());
                dragPoint.set(event.getX(), event.getY());
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                if (dragSide == null) return false;
                dragPoint.set(event.getX(), event.getY());
                if (!dragMoved && Math.hypot(dragPoint.x - dragStart.x, dragPoint.y - dragStart.y) > touchSlop / Math.max(0.1f, pane.getScaleX())) {
                    dragMoved = true;
                }
                if (dragMoved) candidate = findTarget(dragPoint.x, dragPoint.y);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP -> {
                if (dragSide == null) return false;
                LayoutRelations.Side side = dragSide;
                Target target = candidate;
                boolean moved = dragMoved;
                resetDrag();
                ViewBean bean = selectedBean();
                if (bean == null || callback == null) return true;
                if (!moved) {
                    // Tapping an anchor removes what it's attached to, as in Android Studio.
                    if (LayoutRelations.hasSide(bean, side)) {
                        callback.applyRelationChange(bean, b -> LayoutRelations.clearSide(b, side));
                    }
                } else if (target != null) {
                    if (target.side == null) {
                        boolean horizontal = side == LayoutRelations.Side.START || side == LayoutRelations.Side.END;
                        callback.applyRelationChange(bean, b -> LayoutRelations.center(b, horizontal));
                    } else {
                        LayoutRelations.Side targetSide = target.side;
                        String id = target.id;
                        callback.applyRelationChange(bean, b -> LayoutRelations.connect(b, side, id, targetSide));
                    }
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_CANCEL -> {
                if (dragSide == null) return false;
                resetDrag();
                invalidate();
                return true;
            }
        }
        return dragSide != null;
    }

    private void resetDrag() {
        dragSide = null;
        dragMoved = false;
        candidate = null;
    }
}
