package com.besome.sketch.editor.view.item;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.view.View;

import androidx.annotation.NonNull;

import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.editor.view.ItemView;

import a.a.a.wB;

/** A plain View, Space or divider in the layout editor. Spaces get a dashed outline so they can be found. */
public class ItemPlainView extends View implements ItemView {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ViewBean viewBean;
    private boolean isSelected;
    private boolean isFixed;

    public ItemPlainView(Context context) {
        super(context);
        float dp = wB.a(context, 1.0F);
        setMinimumWidth((int) (dp * 4));
        setMinimumHeight((int) dp);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp);
        paint.setPathEffect(new DashPathEffect(new float[]{dp * 4, dp * 3}, 0));
    }

    private boolean isSpace() {
        return viewBean != null && viewBean.convert != null && viewBean.convert.endsWith("Space");
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (isSelected) {
            canvas.drawColor(0x5599d5d0);
        }
        if (isSpace() || isSelected) {
            paint.setColor(0x80000000);
            canvas.drawRect(0.5f, 0.5f, getWidth() - 0.5f, getHeight() - 0.5f, paint);
        }
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
