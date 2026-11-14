package pro.sketchware.editor;

import android.content.Context;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.os.Handler;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.animation.Animation;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import java.util.ArrayList;

public class EditorViewUtils {
   public static long a;

   public static SpannableStringBuilder a(Context var0, String var1) {
      TokenColorizer var3 = new TokenColorizer(var0);
      var3.a(var1);
      SpannableStringBuilder var4 = new SpannableStringBuilder(var1);

      while(true) {
         int var2 = var3.d();
         if (var2 == -1) {
            return var4;
         }

         if (var2 == 2 || var2 == 3 || var2 == 4 || var2 == 5 || var2 == 6) {
            var4.setSpan(new ForegroundColorSpan(TokenColorizer.a[var2]), var3.b(), var3.b() + var3.a(), 33);
         }
      }
   }

   public static void a(Context var0, EditText var1) {
      ((InputMethodManager)var0.getSystemService("input_method")).hideSoftInputFromWindow(var1.getWindowToken(), 0);
   }

   public static void a(View var0) {
      var0.setEnabled(false);
      (new Handler()).postDelayed(new DelayedReenableRunnable(var0), 100L);
   }

   public static void a(View var0, int var1, Animation.AnimationListener var2) {
      var0.measure(-1, -2);
      int var3 = var0.getMeasuredHeight();
      var0.getLayoutParams().height = 1;
      var0.setVisibility(0);
      CollapseHeightAnimation var4 = new CollapseHeightAnimation(var0, var3);
      if (var2 != null) {
         ((Animation)var4).setAnimationListener(var2);
      }

      ((Animation)var4).setDuration((long)((int)((float)var3 / var0.getContext().getResources().getDisplayMetrics().density) * var1));
      var0.startAnimation(var4);
   }

   public static void a(View var0, Animation.AnimationListener var1) {
      int var2 = var0.getMeasuredHeight();
      ExpandHeightAnimation var3 = new ExpandHeightAnimation(var0, var2);
      if (var1 != null) {
         ((Animation)var3).setAnimationListener(var1);
      }

      ((Animation)var3).setDuration((long)((int)((float)var2 / var0.getContext().getResources().getDisplayMetrics().density)));
      var0.startAnimation(var3);
   }

   public static void a(ImageView var0, int var1) {
      ColorMatrix var2 = new ColorMatrix();
      var2.setSaturation((float)var1);
      var0.setColorFilter(new ColorMatrixColorFilter(var2));
   }

   public static boolean a() {
      if (SystemClock.elapsedRealtime() - a < 100L) {
         return true;
      } else {
         a = SystemClock.elapsedRealtime();
         return false;
      }
   }

   public static SpannableStringBuilder b(Context var0, String var1) {
      TokenColorizerExtended var2 = new TokenColorizerExtended(var0);
      SpannableStringBuilder var4 = new SpannableStringBuilder(var1);
      var2.b(var1);

      while(true) {
         int var3 = var2.c();
         if (var3 == -1) {
            ArrayList var5 = var2.a(var1);

            for(int var7 = 0; var7 < var5.size(); ++var7) {
               int[] var6 = (int[])var5.get(var7);
               var4.setSpan(new ForegroundColorSpan(TokenColorizerExtended.a[3]), var6[0], var6[1], 33);
            }

            return var4;
         }

         if (var3 == 2 || var3 == 4 || var3 == 5) {
            var4.setSpan(new ForegroundColorSpan(TokenColorizerExtended.a[var3]), var2.b(), var2.b() + var2.a(), 33);
         }
      }
   }

   public static void b(View var0, Animation.AnimationListener var1) {
      a(var0, 1, var1);
   }
}
