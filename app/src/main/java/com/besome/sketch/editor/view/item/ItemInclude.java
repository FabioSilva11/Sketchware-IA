package com.besome.sketch.editor.view.item;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.editor.view.ItemView;

import a.a.a.wB;
import pro.sketchware.utility.IncludedViews;

/**
 * {@code <include>} in the layout editor. Like Android Studio's layout editor, it draws the included layout's
 * widgets, but as one block: the include is what gets selected, moved and deleted, and its widgets are edited in
 * their own layout. The drawing is a detached copy built by the view pane (see {@link #setPreview}), so its views
 * never take touches, drops or the ids of this screen.
 */
public class ItemInclude extends View implements ItemView {

    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint subtitlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float dp;
    private ViewBean viewBean;
    private boolean isSelected;
    private boolean isFixed;
    /** The included layout's widgets, or null when there's nothing to draw (no layout, missing, a cycle). */
    @Nullable
    private View preview;
    /** The layout {@link #preview} was built from. */
    @NonNull
    private String previewLayout = "";
    /** Why there's no preview, shown instead of the layout name. */
    @Nullable
    private String problem;

    public ItemInclude(Context context) {
        super(context);
        dp = wB.a(context, 1.0F);
        setMinimumWidth((int) (dp * 96));
        setMinimumHeight((int) (dp * 56));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp * 1.5f);
        borderPaint.setPathEffect(new DashPathEffect(new float[]{dp * 6, dp * 4}, 0));
        fillPaint.setStyle(Paint.Style.FILL);
        titlePaint.setTextSize(dp * 13);
        titlePaint.setFakeBoldText(true);
        subtitlePaint.setTextSize(dp * 11);
        setClickable(true);
    }

    /** The layout this include points to, or an empty string when none was chosen yet. */
    @NonNull
    public String getIncludedLayout() {
        return viewBean == null ? "" : IncludedViews.includedLayout(viewBean);
    }

    /** The layout the drawn widgets come from. */
    @NonNull
    public String getPreviewLayout() {
        return previewLayout;
    }

    /**
     * @param preview the included layout's root, measured and drawn here but never attached
     * @param layout  the layout it was built from
     * @param problem why there's no preview (a missing layout, a cycle), or null
     */
    public void setPreview(@Nullable View preview, @NonNull String layout, @Nullable String problem) {
        this.preview = preview;
        this.previewLayout = layout;
        this.problem = problem;
        requestLayout();
        invalidate();
        // Images of the copy can load after it's built; a detached view can't ask for a redraw itself.
        postInvalidateDelayed(400);
        postInvalidateDelayed(1500);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int minWidth = getSuggestedMinimumWidth();
        int minHeight = getSuggestedMinimumHeight();
        if (preview == null) {
            setMeasuredDimension(resolveSize(minWidth, widthMeasureSpec), resolveSize(minHeight, heightMeasureSpec));
            return;
        }
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int childWidthSpec = widthMode == MeasureSpec.UNSPECIFIED
                ? MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                : MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), widthMode);
        int childHeightSpec = heightMode == MeasureSpec.EXACTLY
                ? MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY)
                : MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        preview.measure(childWidthSpec, childHeightSpec);
        setMeasuredDimension(resolveSize(Math.max(minWidth, preview.getMeasuredWidth()), widthMeasureSpec),
                resolveSize(Math.max(minHeight, preview.getMeasuredHeight()), heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (preview != null) {
            preview.layout(0, 0, preview.getMeasuredWidth(), preview.getMeasuredHeight());
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        boolean missing = getIncludedLayout().isEmpty() || problem != null;
        int accent = missing ? 0xFFD32F2F : 0xFF2E7D9A;
        rect.set(dp, dp, getWidth() - dp, getHeight() - dp);
        if (preview != null) {
            int save = canvas.save();
            canvas.clipRect(0, 0, getWidth(), getHeight());
            preview.draw(canvas);
            canvas.restoreToCount(save);
        } else {
            fillPaint.setColor(0xFFFFFFFF);
            canvas.drawRoundRect(rect, dp * 6, dp * 6, fillPaint);
            fillPaint.setColor(missing ? 0x14D32F2F : 0x142E7D9A);
            canvas.drawRoundRect(rect, dp * 6, dp * 6, fillPaint);
        }
        if (isSelected) {
            fillPaint.setColor(0x3399d5d0);
            canvas.drawRoundRect(rect, dp * 6, dp * 6, fillPaint);
        }
        borderPaint.setColor(accent);
        // Selected: a solid, thicker border like the other widgets' selection; otherwise dashed.
        borderPaint.setStrokeWidth(isSelected ? dp * 2.5f : dp * 1.5f);
        borderPaint.setPathEffect(isSelected ? null : new DashPathEffect(new float[]{dp * 6, dp * 4}, 0));
        canvas.drawRoundRect(rect, dp * 6, dp * 6, borderPaint);

        String subtitle = getIncludedLayout().isEmpty() ? "Choose a layout in Properties"
                : problem != null ? problem : "@layout/" + getIncludedLayout();
        if (preview != null) {
            drawTag(canvas, "@layout/" + getIncludedLayout(), accent);
            return;
        }
        titlePaint.setColor(accent);
        subtitlePaint.setColor(0xFF616161);
        float available = getWidth() - dp * 16;
        String title = TextUtils.ellipsize("<include>", titlePaint, available, TextUtils.TruncateAt.END).toString();
        subtitle = TextUtils.ellipsize(subtitle, subtitlePaint, available, TextUtils.TruncateAt.END).toString();
        float centerY = getHeight() / 2f;
        canvas.drawText(title, dp * 8, centerY - dp * 2, titlePaint);
        canvas.drawText(subtitle, dp * 8, centerY + dp * 13, subtitlePaint);
    }

    /** A small label in the top right corner naming the included layout, over the drawn widgets. */
    private void drawTag(Canvas canvas, String text, int color) {
        float padding = dp * 4;
        String label = TextUtils.ellipsize(text, subtitlePaint, getWidth() - dp * 24, TextUtils.TruncateAt.END).toString();
        float width = subtitlePaint.measureText(label) + padding * 2;
        float height = subtitlePaint.descent() - subtitlePaint.ascent() + padding * 2;
        rect.set(getWidth() - width - dp * 2, dp * 2, getWidth() - dp * 2, dp * 2 + height);
        fillPaint.setColor(color);
        canvas.drawRoundRect(rect, dp * 4, dp * 4, fillPaint);
        subtitlePaint.setColor(0xFFFFFFFF);
        canvas.drawText(label, rect.left + padding, rect.top + padding - subtitlePaint.ascent(), subtitlePaint);
    }

    @Override
    public ViewBean getBean() {
        return viewBean;
    }

    @Override
    public void setBean(ViewBean viewBean) {
        this.viewBean = viewBean;
        invalidate();
    }

    @Override
    public boolean getFixed() {
        return isFixed;
    }

    @Override
    public void setFixed(boolean fixed) {
        isFixed = fixed;
    }

    @Override
    public void setSelection(boolean selection) {
        isSelected = selection;
        invalidate();
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        super.setPadding((int) wB.a(getContext(), (float) left), (int) wB.a(getContext(), (float) top),
                (int) wB.a(getContext(), (float) right), (int) wB.a(getContext(), (float) bottom));
    }
}
