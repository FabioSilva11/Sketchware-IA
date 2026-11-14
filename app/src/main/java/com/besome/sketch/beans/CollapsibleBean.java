package com.besome.sketch.beans;

import pro.sketchware.core.ComparisonResult;

public class CollapsibleBean extends ComparisonResult {
    public int buttonPressed = -1;
    public boolean isCollapsed = true;
    public boolean isConfirmation = false;
    public boolean isSelected = false;

    public void initValue() {
        isCollapsed = true;
        isConfirmation = false;
        isSelected = false;
        buttonPressed = -1;
    }
}
