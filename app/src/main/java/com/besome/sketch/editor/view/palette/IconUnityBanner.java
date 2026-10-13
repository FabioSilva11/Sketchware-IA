package com.besome.sketch.editor.view.palette;

import android.content.Context;

import com.besome.sketch.beans.LayoutBean;
import com.besome.sketch.beans.ViewBean;

import mod.agus.jcoderz.beans.ViewBeans;
import pro.sketchware.R;

/** The Unity Banner widget of the palette, next to AdMob's AdView: a 320x50 Unity Ads banner. */
public class IconUnityBanner extends IconBase {
    public IconUnityBanner(Context context) {
        super(context);
        setWidgetImage(R.drawable.ic_mtrl_unity_ads);
        setWidgetName("UnityBanner");
    }

    @Override
    public ViewBean getBean() {
        ViewBean viewBean = new ViewBean();
        viewBean.type = ViewBeans.VIEW_TYPE_WIDGET_UNITY_BANNER;
        LayoutBean layoutBean = viewBean.layout;
        layoutBean.paddingLeft = 0;
        layoutBean.paddingTop = 0;
        layoutBean.paddingRight = 0;
        layoutBean.paddingBottom = 0;
        layoutBean.width = -1;
        layoutBean.height = -2;
        // The placement new Unity projects have; the widget's properties change it
        viewBean.adUnitId = "Banner_Android";
        viewBean.convert = "UnityBannerView";
        return viewBean;
    }
}
