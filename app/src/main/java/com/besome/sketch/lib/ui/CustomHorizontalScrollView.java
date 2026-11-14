package com.besome.sketch.lib.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.HorizontalScrollView;

public class CustomHorizontalScrollView extends HorizontalScrollView {
   public a a;
   public boolean b = true;
   public boolean c = true;

   public CustomHorizontalScrollView(Context var1) {
      super(var1);
   }

   public CustomHorizontalScrollView(Context var1, AttributeSet var2) {
      super(var1, var2);
   }

   public void a() {
      this.b = false;
   }

   public void b() {
      this.b = true;
   }

   public boolean onInterceptTouchEvent(MotionEvent var1) {
      return this.c && this.b ? super.onInterceptTouchEvent(var1) : false;
   }

   public void onScrollChanged(int var1, int var2, int var3, int var4) {
      super.onScrollChanged(var1, var2, var3, var4);
      a var5 = this.a;
      if (var5 != null) {
         var5.a(var1, var2, var3, var4);
      }

   }

   public boolean onTouchEvent(MotionEvent var1) {
      return this.c && this.b ? super.onTouchEvent(var1) : false;
   }

   public void setOnScrollChangedListener(a var1) {
      this.a = var1;
   }

   public void setUseScroll(boolean var1) {
      this.c = var1;
   }

   public interface a {
      void a(int var1, int var2, int var3, int var4);
   }
}
