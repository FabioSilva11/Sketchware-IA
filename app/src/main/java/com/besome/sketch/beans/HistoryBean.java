package com.besome.sketch.beans;

import pro.sketchware.core.ComparisonResult;

public class HistoryBean extends ComparisonResult {
    public static final int ACTION_TYPE_ADD = 0;
    public static final int ACTION_TYPE_REMOVE = 2;
    public static final int ACTION_TYPE_UPDATE = 1;
    public int actionType;
    public ComparisonResult currentData;
    public ComparisonResult prevData;

    public HistoryBean(int i, ComparisonResult nAVar, ComparisonResult nAVar2) {
        actionType = i;
        prevData = nAVar;
        currentData = nAVar2;
    }

    public int getActionType() {
        return actionType;
    }

    public ComparisonResult getCurrentData() {
        return currentData;
    }

    public ComparisonResult getPrevData() {
        return prevData;
    }
}
