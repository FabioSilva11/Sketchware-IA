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
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.editor.view.item.ItemFrameLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import pro.sketchware.utility.ProjectStrings;

/**
 * Drawn on top of the layout editor, it only shows things; the layout itself comes from the
 * widgets' LayoutParams. It draws:
 * <ul>
 * <li>in Design mode, the relations and the anchors of the selected child of a RelativeLayout or
 * ConstraintLayout (or of every child, when "show all relations" is on);</li>
 * <li>in Blueprint mode, the outline of every widget and every relation, without the rendering;</li>
 * <li>while dragging, the container that will receive the widget (and the gravity zones of a
 * FrameLayout);</li>
 * <li>while moving a widget, short alignment guides to the parent and the siblings.</li>
 * </ul>
 * A touch is taken only when it starts on an anchor of the selected widget; every other touch goes to
 * the widgets below, so selecting, moving and dropping work the same in both modes.
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

    private static final int BLUEPRINT_BACKGROUND = 0xFF1A3A5C;
    private static final int BLUEPRINT_LINE = 0xFF8AB4F8;
    private static final int BLUEPRINT_TEXT = 0xFFD2E3FC;
    private static final int RELATION_COLOR = 0xFF1E88E5;
    private static final int CANDIDATE_COLOR = 0xFF00C853;
    private static final int GUIDE_COLOR = 0xFFE91E63;
    private static final int DROP_COLOR = 0xFF1E88E5;

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
    private boolean showAllRelations;
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

    // Drop target while a widget is dragged
    @Nullable
    private ViewGroup dropTarget;
    private int dropGravity = -1;

    // Alignment guides while a widget is moved: x1, y1, x2, y2 per line
    private final List<float[]> guides = new ArrayList<>();

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
        setClickable(false);
        setFocusable(false);
    }

    public void setCallback(@Nullable Callback callback) {
        this.callback = callback;
    }

    public void setSelected(@Nullable ItemView item) {
        selected = item;
        resetDrag();
        invalidate();
    }

    public boolean isBlueprint() {
        return blueprint;
    }

    public void setBlueprint(boolean blueprint) {
        this.blueprint = blueprint;
        invalidate();
    }

    public boolean isShowingAllRelations() {
        return showAllRelations;
    }

    public void setShowAllRelations(boolean showAllRelations) {
        this.showAllRelations = showAllRelations;
        invalidate();
    }

    /** The container a dragged widget will be dropped into (null when none) and, for a FrameLayout, the gravity. */
    public void setDropTarget(@Nullable ViewGroup target, int gravity) {
        if (dropTarget == target && dropGravity == gravity) return;
        dropTarget = target;
        dropGravity = gravity;
        invalidate();
    }

    public void clearGuides() {
        if (guides.isEmpty()) return;
        guides.clear();
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

    /** The sibling of {@code view} with this widget id: relations only resolve between siblings. */
    @Nullable
    private static View findSibling(View view, String id) {
        if (!(view.getParent() instanceof ViewGroup parent) || id == null) return null;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child != view && id.equals(child.getTag())) return child;
        }
        return null;
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
            drawAllRelations(canvas, root, BLUEPRINT_LINE);
        } else if (showAllRelations) {
            drawAllRelations(canvas, root, 0x991E88E5);
            drawSelectedRelations(canvas);
        } else {
            drawSelectedRelations(canvas);
        }
        drawDropTarget(canvas);
        drawGuides(canvas);
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
                float textWidth = labelPaint.measureText(text, 0, text.length());
                if (view instanceof ViewGroup group && hasWidgetChildren(group)) {
                    // Containers: name in the bottom-left corner, clear of their children's labels.
                    canvas.drawText(text, 0, text.length(), rect.left + screenDp(3f), rect.bottom - screenDp(4f), labelPaint);
                } else {
                    // Widgets: name in the middle of their box, as in Android Studio's blueprint.
                    canvas.drawText(text, 0, text.length(), rect.centerX() - textWidth / 2f,
                            rect.centerY() + screenDp(3f), labelPaint);
                }
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

    private static boolean hasWidgetChildren(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            if (group.getChildAt(i) instanceof ItemView) return true;
        }
        return false;
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

    private void drawAllRelations(Canvas canvas, ViewGroup group, int color) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (!(child instanceof ItemView item)) continue;
            ViewBean bean = item.getBean();
            if (LayoutRelations.supports(bean)) {
                drawRelations(canvas, child, bean, child == selected ? (blueprint ? 0xFFFFFFFF : RELATION_COLOR) : color);
            }
            if (child instanceof ViewGroup childGroup) {
                drawAllRelations(canvas, childGroup, color);
            }
        }
    }

    /** Only the relations of the selected widget: lines don't cross the canvas for nothing. */
    private void drawSelectedRelations(Canvas canvas) {
        ViewBean bean = selectedBean();
        if (bean == null) return;
        drawRelations(canvas, (View) selected, bean, RELATION_COLOR);
    }

    /**
     * Draws the connections of a widget the way Android Studio does: to a parent edge a straight line
     * with an arrow; to another widget a curve that leaves the anchor at a right angle and enters the
     * target's anchor; a widget held on both sides of an axis (centred, or with a bias) gets a zigzag
     * spring on each side.
     */
    private void drawRelations(Canvas canvas, View view, ViewBean bean, int color) {
        RectF rect = rectOf(view);
        RectF parent = view.getParent() instanceof View parentView ? contentRectOf(parentView) : null;
        if (rect == null || parent == null) return;
        for (LayoutRelations.Connection connection : LayoutRelations.connections(bean)) {
            PointF from = anchorPoint(rect, connection.side);
            linePaint.setColor(color);
            linePaint.setStrokeWidth(screenDp(1.4f));
            linePaint.setPathEffect(null);
            if (LayoutRelations.PARENT.equals(connection.target)) {
                PointF to = parentPoint(parent, connection.side, connection.targetSide, from);
                boolean spring = connection.targetSide == null
                        || LayoutRelations.hasSide(bean, LayoutRelations.opposite(connection.side));
                if (spring) {
                    drawSpring(canvas, from, to, connection.side);
                } else {
                    canvas.drawLine(from.x, from.y, to.x, to.y, linePaint);
                }
                drawArrow(canvas, to, outward(connection.side), color);
            } else {
                RectF target = rectOf(findSibling(view, LayoutRelations.referenceId(connection.target)));
                if (target == null || connection.targetSide == null) continue;
                PointF to = anchorPoint(target, connection.targetSide);
                drawCurve(canvas, from, connection.side, to, connection.targetSide);
                PointF inward = outward(connection.targetSide);
                drawArrow(canvas, to, new PointF(-inward.x, -inward.y), color);
            }
        }
        linePaint.setPathEffect(null);
    }

    /** Where a connection meets the parent: the edge (or, for centring, the edge on that side) in line with the anchor. */
    private static PointF parentPoint(RectF parent, LayoutRelations.Side side, @Nullable LayoutRelations.Side targetSide,
                                      PointF from) {
        LayoutRelations.Side edge = targetSide == null ? side : targetSide;
        return switch (edge) {
            case TOP -> new PointF(clamp(from.x, parent.left, parent.right), parent.top);
            case BOTTOM -> new PointF(clamp(from.x, parent.left, parent.right), parent.bottom);
            case START -> new PointF(parent.left, clamp(from.y, parent.top, parent.bottom));
            case END -> new PointF(parent.right, clamp(from.y, parent.top, parent.bottom));
        };
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Unit vector pointing out of a side of a box. */
    private static PointF outward(LayoutRelations.Side side) {
        return switch (side) {
            case TOP -> new PointF(0, -1);
            case BOTTOM -> new PointF(0, 1);
            case START -> new PointF(-1, 0);
            case END -> new PointF(1, 0);
        };
    }

    /** Cubic curve from one anchor to another, leaving and entering each side at a right angle. */
    private void drawCurve(Canvas canvas, PointF from, LayoutRelations.Side fromSide, PointF to,
                           LayoutRelations.Side toSide) {
        float distance = (float) Math.hypot(to.x - from.x, to.y - from.y);
        float pull = Math.max(screenDp(18f), distance * 0.45f);
        PointF out = outward(fromSide);
        PointF in = outward(toSide);
        path.reset();
        path.moveTo(from.x, from.y);
        path.cubicTo(from.x + out.x * pull, from.y + out.y * pull, to.x + in.x * pull, to.y + in.y * pull, to.x, to.y);
        canvas.drawPath(path, linePaint);
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

    /** Arrow head with its tip at {@code tip}, pointing along the unit vector {@code direction}. */
    private void drawArrow(Canvas canvas, PointF tip, PointF direction, int color) {
        float size = screenDp(5f);
        float ux = direction.x;
        float uy = direction.y;
        path.reset();
        path.moveTo(tip.x, tip.y);
        path.lineTo(tip.x - ux * size - uy * size * 0.6f, tip.y - uy * size + ux * size * 0.6f);
        path.lineTo(tip.x - ux * size + uy * size * 0.6f, tip.y - uy * size - ux * size * 0.6f);
        path.close();
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(color);
        canvas.drawPath(path, fillPaint);
    }

    private float handleRadius() {
        return screenDp(5f);
    }

    private void drawHandles(Canvas canvas) {
        ViewBean bean = selectedBean();
        if (bean == null || !guides.isEmpty()) return;
        RectF rect = rectOf((View) selected);
        if (rect == null) return;
        float radius = handleRadius();
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

    /**
     * While an anchor is dragged: small dots on every edge it can attach to (the middle of the
     * siblings' sides and the parent's edges in line with it), the line following the finger and,
     * over a target, the connection it would make ending on a filled dot, as in Android Studio.
     */
    private void drawDrag(Canvas canvas) {
        if (dragSide == null || !dragMoved || !(selected instanceof View selectedView)) return;
        RectF rect = rectOf(selectedView);
        if (rect == null) return;
        PointF from = anchorPoint(rect, dragSide);
        boolean vertical = dragSide == LayoutRelations.Side.TOP || dragSide == LayoutRelations.Side.BOTTOM;
        float dot = screenDp(3.5f);
        fillPaint.setStyle(Paint.Style.FILL);
        linePaint.setPathEffect(null);
        linePaint.setStrokeWidth(screenDp(1.2f));
        linePaint.setColor(RELATION_COLOR);
        if (selectedView.getParent() instanceof ViewGroup parent) {
            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                if (child == selectedView || !(child instanceof ItemView)) continue;
                RectF other = rectOf(child);
                if (other == null) continue;
                for (LayoutRelations.Side side : vertical
                        ? new LayoutRelations.Side[]{LayoutRelations.Side.TOP, LayoutRelations.Side.BOTTOM}
                        : new LayoutRelations.Side[]{LayoutRelations.Side.START, LayoutRelations.Side.END}) {
                    PointF p = anchorPoint(other, side);
                    fillPaint.setColor(0xFFFFFFFF);
                    canvas.drawCircle(p.x, p.y, dot, fillPaint);
                    canvas.drawCircle(p.x, p.y, dot, linePaint);
                }
            }
            RectF content = contentRectOf(parent);
            if (content != null) {
                for (LayoutRelations.Side side : vertical
                        ? new LayoutRelations.Side[]{LayoutRelations.Side.TOP, LayoutRelations.Side.BOTTOM}
                        : new LayoutRelations.Side[]{LayoutRelations.Side.START, LayoutRelations.Side.END}) {
                    PointF p = parentPoint(content, side, side, from);
                    fillPaint.setColor(0xFFFFFFFF);
                    canvas.drawCircle(p.x, p.y, dot, fillPaint);
                    canvas.drawCircle(p.x, p.y, dot, linePaint);
                }
            }
        }
        if (candidate == null) {
            linePaint.setPathEffect(new DashPathEffect(new float[]{screenDp(5), screenDp(3)}, 0));
            linePaint.setColor(RELATION_COLOR);
            linePaint.setStrokeWidth(screenDp(1.5f));
            canvas.drawLine(from.x, from.y, dragPoint.x, dragPoint.y, linePaint);
            linePaint.setPathEffect(null);
            return;
        }
        PointF to = candidatePoint(candidate, from, vertical);
        linePaint.setPathEffect(null);
        linePaint.setColor(CANDIDATE_COLOR);
        linePaint.setStrokeWidth(screenDp(1.8f));
        if (LayoutRelations.PARENT.equals(candidate.id) || candidate.side == null) {
            canvas.drawLine(from.x, from.y, to.x, to.y, linePaint);
        } else {
            drawCurve(canvas, from, dragSide, to, candidate.side);
        }
        fillPaint.setColor(CANDIDATE_COLOR);
        canvas.drawCircle(to.x, to.y, screenDp(5f), fillPaint);
    }

    /** The point a candidate connection ends on: a sibling's anchor, the parent edge in line, or the parent's middle. */
    private PointF candidatePoint(Target target, PointF from, boolean vertical) {
        RectF r = target.rect;
        if (target.side == null) {
            return vertical ? new PointF(from.x, r.centerY()) : new PointF(r.centerX(), from.y);
        }
        if (LayoutRelations.PARENT.equals(target.id)) {
            return parentPoint(r, target.side, target.side, from);
        }
        return anchorPoint(r, target.side);
    }

    /** Outline of the container that will receive the dragged widget; nine zones on a FrameLayout. */
    private void drawDropTarget(Canvas canvas) {
        RectF rect = rectOf(dropTarget);
        if (rect == null) return;
        linePaint.setPathEffect(new DashPathEffect(new float[]{screenDp(6), screenDp(4)}, 0));
        linePaint.setColor(DROP_COLOR);
        linePaint.setStrokeWidth(screenDp(2f));
        canvas.drawRect(rect, linePaint);
        linePaint.setPathEffect(null);
        if (!(dropTarget instanceof ItemFrameLayout) || dropGravity < 0) return;
        RectF content = contentRectOf(dropTarget);
        if (content == null) return;
        float w = content.width() / 3f;
        float h = content.height() / 3f;
        linePaint.setColor(0x661E88E5);
        linePaint.setStrokeWidth(screenDp(1f));
        for (int i = 1; i < 3; i++) {
            canvas.drawLine(content.left + w * i, content.top, content.left + w * i, content.bottom, linePaint);
            canvas.drawLine(content.left, content.top + h * i, content.right, content.top + h * i, linePaint);
        }
        int gravity = dropGravity == 0 ? Gravity.LEFT | Gravity.TOP : dropGravity;
        int horizontal = gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
        int vertical = gravity & Gravity.VERTICAL_GRAVITY_MASK;
        int column = horizontal == Gravity.CENTER_HORIZONTAL ? 1 : horizontal == Gravity.RIGHT ? 2 : 0;
        int row = vertical == Gravity.CENTER_VERTICAL ? 1 : vertical == Gravity.BOTTOM ? 2 : 0;
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(0x221E88E5);
        canvas.drawRect(content.left + w * column, content.top + h * row,
                content.left + w * (column + 1), content.top + h * (row + 1), fillPaint);
    }

    private void drawGuides(Canvas canvas) {
        if (guides.isEmpty()) return;
        linePaint.setPathEffect(new DashPathEffect(new float[]{screenDp(4), screenDp(3)}, 0));
        linePaint.setColor(GUIDE_COLOR);
        linePaint.setStrokeWidth(screenDp(1f));
        for (float[] line : guides) {
            canvas.drawLine(line[0], line[1], line[2], line[3], linePaint);
        }
        linePaint.setPathEffect(null);
    }

    // ---------------------------------------------------------------------------------------------
    // Alignment guides

    /**
     * Snaps a widget being moved by ({@code dx}, {@code dy}) pane pixels to the nearest edge or centre
     * of its parent and siblings, records the guides to draw and returns the snapped offset. Guides
     * only span the widget and what it aligns to.
     */
    @NonNull
    public PointF snapMove(@NonNull View moving, float dx, float dy) {
        guides.clear();
        RectF rect = rectOf(moving);
        RectF parent = moving.getParent() instanceof View parentView ? contentRectOf(parentView) : null;
        if (rect == null || parent == null) {
            invalidate();
            return new PointF(dx, dy);
        }
        RectF moved = new RectF(rect);
        moved.offset(dx, dy);
        float threshold = screenDp(6f);
        List<RectF> references = new ArrayList<>();
        references.add(parent);
        if (moving.getParent() instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child == moving || !(child instanceof ItemView)) continue;
                RectF sibling = rectOf(child);
                if (sibling != null) references.add(sibling);
            }
        }
        // Horizontal: left, centre and right of the widget against those of every reference.
        float bestX = threshold + 1;
        float snapX = 0;
        RectF guideX = null;
        float lineX = 0;
        float[] movingXs = {moved.left, moved.centerX(), moved.right};
        for (RectF reference : references) {
            for (float target : new float[]{reference.left, reference.centerX(), reference.right}) {
                for (float edge : movingXs) {
                    float distance = Math.abs(target - edge);
                    if (distance < bestX) {
                        bestX = distance;
                        snapX = target - edge;
                        guideX = reference;
                        lineX = target;
                    }
                }
            }
        }
        float bestY = threshold + 1;
        float snapY = 0;
        RectF guideY = null;
        float lineY = 0;
        float[] movingYs = {moved.top, moved.centerY(), moved.bottom};
        for (RectF reference : references) {
            for (float target : new float[]{reference.top, reference.centerY(), reference.bottom}) {
                for (float edge : movingYs) {
                    float distance = Math.abs(target - edge);
                    if (distance < bestY) {
                        bestY = distance;
                        snapY = target - edge;
                        guideY = reference;
                        lineY = target;
                    }
                }
            }
        }
        if (guideX != null) {
            moved.offset(snapX, 0);
            dx += snapX;
        }
        if (guideY != null) {
            moved.offset(0, snapY);
            dy += snapY;
        }
        if (guideX != null) {
            guides.add(new float[]{lineX, Math.min(moved.top, guideX.top), lineX, Math.max(moved.bottom, guideX.bottom)});
        }
        if (guideY != null) {
            guides.add(new float[]{Math.min(moved.left, guideY.left), lineY, Math.max(moved.right, guideY.right), lineY});
        }
        if (guides.isEmpty()) {
            // Keep the overlay in "moving" state so the anchors stay hidden while the finger moves.
            guides.add(new float[]{0, 0, 0, 0});
        }
        invalidate();
        return new PointF(dx, dy);
    }

    // ---------------------------------------------------------------------------------------------
    // Touch: dragging anchors

    /**
     * The anchor under the finger. Inside the widget the touch must be right on the anchor so the
     * widget itself can still be selected and moved; outside, a larger margin makes anchors easy to grab.
     */
    @Nullable
    private LayoutRelations.Side hitHandle(float x, float y) {
        if (selectedBean() == null || dropTarget != null) return null;
        RectF rect = rectOf((View) selected);
        if (rect == null) return null;
        boolean inside = rect.contains(x, y);
        float tolerance = inside ? handleRadius() + screenDp(3f) : screenDp(14f);
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
                // Not on an anchor: let the widgets below get the touch.
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
                        callback.applyRelationChange(bean, b -> LayoutRelations.disconnect(b, side));
                    }
                } else if (target != null) {
                    if (target.side == null) {
                        boolean horizontal = side == LayoutRelations.Side.START || side == LayoutRelations.Side.END;
                        callback.applyRelationChange(bean, b -> LayoutRelations.center(b, horizontal));
                    } else {
                        LayoutRelations.Side targetSide = target.side;
                        String id = target.id;
                        int distance = distanceDp(side, target);
                        boolean replaces = LayoutRelations.replacesOppositeAnchor(bean, side, id, targetSide);
                        if (callback.applyRelationChange(bean, b -> LayoutRelations.connect(b, side, id, targetSide, distance))
                                && replaces) {
                            // Android would stretch the widget between the two anchors; say why one was released.
                            android.widget.Toast.makeText(getContext(), pro.sketchware.R.string.design_relations_relative_one_anchor,
                                    android.widget.Toast.LENGTH_LONG).show();
                        }
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

    /**
     * Current distance (dp) between the dragged side of the selected widget and the target edge, so
     * the connection keeps the widget where it is, as Android Studio does. Overlaps become 0.
     */
    private int distanceDp(LayoutRelations.Side side, Target target) {
        RectF rect = rectOf((View) selected);
        if (rect == null || target.side == null) return 0;
        RectF t = target.rect;
        float edge = switch (target.side) {
            case TOP -> t.top;
            case BOTTOM -> t.bottom;
            case START -> t.left;
            case END -> t.right;
        };
        float distance = switch (side) {
            case TOP -> rect.top - edge;
            case BOTTOM -> edge - rect.bottom;
            case START -> rect.left - edge;
            case END -> edge - rect.right;
        };
        return Math.max(0, Math.round(distance / density));
    }

    private void resetDrag() {
        dragSide = null;
        dragMoved = false;
        candidate = null;
    }
}
