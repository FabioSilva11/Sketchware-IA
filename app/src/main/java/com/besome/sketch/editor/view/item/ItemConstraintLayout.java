package com.besome.sketch.editor.view.item;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.editor.view.EditorChildren;
import com.besome.sketch.editor.view.ItemView;
import com.besome.sketch.editor.view.ScrollContainer;

import a.a.a.wB;

/** ConstraintLayout in the layout editor; its children get ConstraintLayout.LayoutParams from ViewPane. */
public class ItemConstraintLayout extends ConstraintLayout implements ItemView, ScrollContainer {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ViewBean viewBean;
    private boolean isSelected;
    private boolean isFixed;

    public ItemConstraintLayout(Context context) {
        super(context);
        setWillNotDraw(false);
        setMinimumWidth((int) wB.a(context, 32.0F));
        setMinimumHeight((int) wB.a(context, 32.0F));
        paint.setStrokeWidth(wB.a(context, 2.0F));
    }

    @Override
    public void reindexChildren() {
        EditorChildren.reindex(this);
    }

    @Override
    public void addView(View child, int index) {
        // index counts editor widgets, not raw children (see EditorChildren).
        super.addView(child, EditorChildren.insertionIndex(this, index));
    }

    @Override
    public ViewBean getBean() {
        return viewBean;
    }

    @Override
    public void setBean(ViewBean viewBean) {
        this.viewBean = viewBean;
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
    protected void onDraw(@NonNull Canvas canvas) {
        if (!isFixed) {
            int width = getMeasuredWidth();
            int height = getMeasuredHeight();
            if (isSelected) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0x9599d5d0);
                canvas.drawRect(0, 0, width, height, paint);
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(0x60000000);
            canvas.drawRect(0, 0, width, height, paint);
        }
        super.onDraw(canvas);
    }

    @Override
    public void setChildScrollEnabled(boolean scrollEnabled) {
        for (int i = 0; i < getChildCount(); ++i) {
            View child = getChildAt(i);
            if (child instanceof ScrollContainer container) {
                container.setChildScrollEnabled(scrollEnabled);
            }
            if (child instanceof ItemHorizontalScrollView scrollView) {
                scrollView.setScrollEnabled(scrollEnabled);
            }
            if (child instanceof ItemVerticalScrollView scrollView) {
                scrollView.setScrollEnabled(scrollEnabled);
            }
        }
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        super.setPadding((int) wB.a(getContext(), (float) left), (int) wB.a(getContext(), (float) top),
                (int) wB.a(getContext(), (float) right), (int) wB.a(getContext(), (float) bottom));
    }
}
