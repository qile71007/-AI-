package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class CustomFabBehavior extends FloatingActionButton.Behavior {
    public CustomFabBehavior() {}
    public CustomFabBehavior(Context context, AttributeSet attrs) { super(); }
    @Override
    public boolean onStartNestedScroll(CoordinatorLayout parent, View child, View directTargetChild, View target, int axes, int type) {
        return true;
    }
}
