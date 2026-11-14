package com.besome.sketch.lib.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ScrollView;

public class CustomScrollView extends ScrollView {
   public boolean a = true;
   public boolean b = true;

   public CustomScrollView(Context var1) {
      super(var1);
   }

   public CustomScrollView(Context var1, AttributeSet var2) {
      super(var1, var2);
   }

   public void a() {
      this.a = false;
   }

   public void b() {
      this.a = true;
   }

   public boolean onInterceptTouchEvent(MotionEvent var1) {
      return this.b && this.a ? super.onInterceptTouchEvent(var1) : false;
   }

   public boolean onTouchEvent(MotionEvent var1) {
      return this.b && this.a ? super.onTouchEvent(var1) : false;
   }

   public void setUseScroll(boolean var1) {
      this.b = var1;
   }
}
