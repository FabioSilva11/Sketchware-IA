package com.besome.sketch.lib.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Bitmap.Config;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.widget.ImageView;
import androidx.appcompat.widget.AppCompatImageView;

public class CircleImageView extends AppCompatImageView {
   public CircleImageView(Context var1) {
      super(var1);
   }

   public CircleImageView(Context var1, AttributeSet var2) {
      super(var1, var2);
   }

   public Bitmap a(Bitmap var1) {
      Bitmap var2 = Bitmap.createBitmap(var1.getWidth(), var1.getHeight(), Config.ARGB_8888);
      Canvas var3 = new Canvas(var2);
      Paint var4 = new Paint();
      Rect var5 = new Rect(0, 0, var1.getWidth(), var1.getHeight());
      var4.setAntiAlias(true);
      var3.drawARGB(0, 0, 0, 0);
      var4.setColor(-16776961);
      var3.drawCircle((float)(var1.getWidth() / 2), (float)(var1.getHeight() / 2), (float)(var1.getHeight() / 2), var4);
      var4.setXfermode(new PorterDuffXfermode(Mode.SRC_IN));
      var3.drawBitmap(var1, var5, var5, var4);
      return var2;
   }

   public void onDraw(Canvas var1) {
      BitmapDrawable var2 = (BitmapDrawable)((ImageView)this).getDrawable();
      if (var2 != null) {
         if (((ImageView)this).getWidth() != 0 && ((ImageView)this).getHeight() != 0) {
            Bitmap var5 = var2.getBitmap();
            int var3 = ((ImageView)this).getMeasuredWidth();
            int var4 = ((ImageView)this).getMeasuredHeight();
            if (var5 != null) {
               if (var3 != var5.getWidth() || var4 != var5.getHeight()) {
                  var5 = Bitmap.createScaledBitmap(var5, var3, var4, true);
               }

               var1.drawBitmap(this.a(var5), 0.0F, 0.0F, (Paint)null);
            }
         }

      }
   }
}
