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

import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.editor.view.ItemView;

import a.a.a.wB;

/**
 * {@code <include>} in the layout editor: a placeholder that names the included layout. The included
 * views aren't drawn here because their ids belong to another layout file.
 */
public class ItemInclude extends View implements ItemView {

    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint subtitlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private ViewBean viewBean;
    private boolean isSelected;
    private boolean isFixed;

    public ItemInclude(Context context) {
        super(context);
        float dp = wB.a(context, 1.0F);
        setMinimumWidth((int) (dp * 96));
        setMinimumHeight((int) (dp * 56));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp * 1.5f);
        borderPaint.setPathEffect(new DashPathEffect(new float[]{dp * 6, dp * 4}, 0));
        fillPaint.setStyle(Paint.Style.FILL);
        titlePaint.setTextSize(dp * 13);
        titlePaint.setFakeBoldText(true);
        subtitlePaint.setTextSize(dp * 11);
        // A light card shadow, so the include reads as a block you can pick and drop like any widget.
        setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp * 6);
                outline.setAlpha(0.6f);
            }
        });
        setElevation(dp * 2);
        setClickable(true);
    }

    /** The layout this include points to, or an empty string when none was chosen yet. */
    @NonNull
    public String getIncludedLayout() {
        if (viewBean == null) return "";
        String layout = pro.sketchware.utility.InjectAttributes.get(viewBean, "layout");
        if (layout == null || layout.isEmpty()) return "";
        return layout.startsWith("@layout/") ? layout.substring("@layout/".length()) : layout;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float dp = wB.a(getContext(), 1.0F);
        boolean missing = getIncludedLayout().isEmpty();
        int accent = missing ? 0xFFD32F2F : 0xFF2E7D9A;
        // Opaque base so the shadow doesn't show through, tinted with the accent.
        fillPaint.setColor(0xFFFFFFFF);
        rect.set(dp, dp, getWidth() - dp, getHeight() - dp);
        canvas.drawRoundRect(rect, dp * 6, dp * 6, fillPaint);
        fillPaint.setColor(isSelected ? 0x5599d5d0 : (missing ? 0x14D32F2F : 0x142E7D9A));
        canvas.drawRoundRect(rect, dp * 6, dp * 6, fillPaint);
        borderPaint.setColor(accent);
        // Selected: a solid, thicker border like the other widgets' selection; otherwise dashed.
        borderPaint.setStrokeWidth(isSelected ? dp * 2.5f : dp * 1.5f);
        borderPaint.setPathEffect(isSelected ? null : new DashPathEffect(new float[]{dp * 6, dp * 4}, 0));
        canvas.drawRoundRect(rect, dp * 6, dp * 6, borderPaint);

        titlePaint.setColor(accent);
        subtitlePaint.setColor(0xFF616161);
        float available = getWidth() - dp * 16;
        String title = TextUtils.ellipsize("<include>", titlePaint, available, TextUtils.TruncateAt.END).toString();
        String subtitle = missing ? "Choose a layout in Properties" : "@layout/" + getIncludedLayout();
        subtitle = TextUtils.ellipsize(subtitle, subtitlePaint, available, TextUtils.TruncateAt.END).toString();
        float centerY = getHeight() / 2f;
        canvas.drawText(title, dp * 8, centerY - dp * 2, titlePaint);
        canvas.drawText(subtitle, dp * 8, centerY + dp * 13, subtitlePaint);
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
