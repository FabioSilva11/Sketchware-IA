package com.besome.sketch.editor.view.item;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.editor.view.ItemView;

import a.a.a.wB;
import pro.sketchware.R;

/** The Unity Banner widget in the layout editor: a 320x50 placeholder showing the placement ID. */
public class ItemUnityBanner extends LinearLayout implements ItemView {

    private final Rect rect = new Rect();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dip;
    private final TextView placement;
    private ViewBean viewBean;
    private boolean hasSelection;
    private boolean isFixed;

    public ItemUnityBanner(Context context) {
        super(context);
        dip = wB.a(context, 1.0F);
        paint.setColor(0x9599d5d0);
        setWillNotDraw(false);
        setGravity(Gravity.CENTER);

        LinearLayout banner = new LinearLayout(context);
        banner.setOrientation(HORIZONTAL);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFF222C37);
        background.setCornerRadius(4 * dip);
        banner.setBackground(background);
        int padding = (int) (8 * dip);
        banner.setPadding(padding, 0, padding, 0);
        banner.setLayoutParams(new LayoutParams((int) (320 * dip), (int) (50 * dip)));

        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.ic_mtrl_unity_ads);
        icon.setColorFilter(Color.WHITE);
        icon.setLayoutParams(new LayoutParams((int) (28 * dip), (int) (28 * dip)));
        banner.addView(icon);

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(VERTICAL);
        LayoutParams textsParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
        textsParams.leftMargin = padding;
        texts.setLayoutParams(textsParams);
        TextView title = new TextView(context);
        title.setText("Unity Ads banner");
        title.setTextColor(Color.WHITE);
        title.setTextSize(13);
        texts.addView(title);
        placement = new TextView(context);
        placement.setTextColor(0xFFB8C2CC);
        placement.setTextSize(11);
        placement.setSingleLine(true);
        placement.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(placement);
        banner.addView(texts);

        addView(banner);
    }

    public void setPlacementId(String placementId) {
        placement.setText(placementId == null || placementId.isEmpty() ? "320x50" : placementId + " · 320x50");
    }

    @Override
    public ViewBean getBean() {
        return viewBean;
    }

    @Override
    public void setBean(ViewBean viewBean) {
        this.viewBean = viewBean;
        setPlacementId(viewBean == null ? null : viewBean.adUnitId);
    }

    @Override
    public boolean getFixed() {
        return isFixed;
    }

    @Override
    public void setFixed(boolean isFixed) {
        this.isFixed = isFixed;
    }

    @Override
    public void setSelection(boolean selection) {
        hasSelection = selection;
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (hasSelection) {
            rect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
            canvas.drawRect(rect, paint);
        }
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        super.setPadding((int) (left * dip), (int) (top * dip), (int) (right * dip), (int) (bottom * dip));
    }
}
