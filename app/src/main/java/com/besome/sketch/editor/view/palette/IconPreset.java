package com.besome.sketch.editor.view.palette;

import android.annotation.SuppressLint;
import android.content.Context;

import com.besome.sketch.beans.ViewBean;

import pro.sketchware.widgets.BuiltInWidgets;

/** Palette entry for a component from {@link BuiltInWidgets}. */
@SuppressLint("ViewConstructor")
public class IconPreset extends IconBase {

    private final BuiltInWidgets.Preset preset;

    public IconPreset(Context context, BuiltInWidgets.Preset preset) {
        super(context);
        this.preset = preset;
        setWidgetImage(preset.icon);
        setText(preset.name);
        setName(preset.name);
    }

    public BuiltInWidgets.Preset getPreset() {
        return preset;
    }

    @Override
    public ViewBean getBean() {
        return preset.create();
    }
}
